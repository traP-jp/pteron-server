package mysql

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/traP-jp/pteron-server/internal/domain"
)

type ProjectRepository struct {
	db *sqlx.DB
}

func NewProjectRepository(db *sqlx.DB) *ProjectRepository {
	return &ProjectRepository{db: db}
}

func (r *ProjectRepository) FindByID(ctx context.Context, id domain.ProjectID) (*domain.Project, error) {
	projects, err := r.findByQuery(ctx, "SELECT id, name, owner_id, account_id, url FROM projects WHERE id = ?", id.Bytes())
	if err != nil || len(projects) == 0 {
		return nil, err
	}
	return &projects[0], nil
}

func (r *ProjectRepository) FindByName(ctx context.Context, name domain.ProjectName) (*domain.Project, error) {
	projects, err := r.findByQuery(ctx, "SELECT id, name, owner_id, account_id, url FROM projects WHERE LOWER(name) = ?", name.Normalized())
	if err != nil || len(projects) == 0 {
		return nil, err
	}
	return &projects[0], nil
}

func (r *ProjectRepository) FindAll(ctx context.Context) ([]domain.Project, error) {
	return r.findByQuery(ctx, "SELECT id, name, owner_id, account_id, url FROM projects")
}

func (r *ProjectRepository) FindByAPIClientID(ctx context.Context, clientID domain.ID) (*domain.Project, error) {
	var projectIDBytes []byte
	if err := r.db.GetContext(ctx, &projectIDBytes, "SELECT project_id FROM api_clients WHERE client_id = ?", clientID.Bytes()); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	projectID, err := domain.IDFromBytes(projectIDBytes)
	if err != nil {
		return nil, err
	}
	return r.FindByID(ctx, domain.ProjectID(projectID))
}

func (r *ProjectRepository) FindByUserID(ctx context.Context, userID domain.UserID) ([]domain.Project, error) {
	var idBytes [][]byte
	if err := r.db.SelectContext(ctx, &idBytes, `
SELECT DISTINCT project_id FROM (
    SELECT id AS project_id FROM projects WHERE owner_id = ?
    UNION
    SELECT project_id FROM project_admins WHERE user_id = ?
) AS target_projects`,
		userID.Bytes(),
		userID.Bytes(),
	); err != nil {
		return nil, err
	}
	if len(idBytes) == 0 {
		return nil, nil
	}
	query, args, err := sqlx.In("SELECT id, name, owner_id, account_id, url FROM projects WHERE id IN (?)", idBytes)
	if err != nil {
		return nil, err
	}
	return r.findByQuery(ctx, r.db.Rebind(query), args...)
}

func (r *ProjectRepository) Save(ctx context.Context, project domain.Project) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	var urlValue any
	if project.URL != nil {
		urlValue = project.URL.String()
	}
	if _, err := tx.ExecContext(ctx, `
INSERT INTO projects (id, name, owner_id, account_id, url)
VALUES (?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    owner_id = VALUES(owner_id),
    account_id = VALUES(account_id),
    url = VALUES(url)`,
		project.ID.Bytes(),
		project.Name.String(),
		project.OwnerID.Bytes(),
		project.AccountID.Bytes(),
		urlValue,
	); err != nil {
		return err
	}

	if _, err := tx.ExecContext(ctx, "DELETE FROM project_admins WHERE project_id = ?", project.ID.Bytes()); err != nil {
		return err
	}
	for _, adminID := range project.AdminIDs {
		if _, err := tx.ExecContext(ctx, "INSERT INTO project_admins (project_id, user_id) VALUES (?, ?)", project.ID.Bytes(), adminID.Bytes()); err != nil {
			return err
		}
	}

	if _, err := tx.ExecContext(ctx, "DELETE FROM api_clients WHERE project_id = ?", project.ID.Bytes()); err != nil {
		return err
	}
	for _, client := range project.APIClients {
		if _, err := tx.ExecContext(ctx, `
INSERT INTO api_clients (client_id, project_id, client_secret, created_at)
VALUES (?, ?, ?, ?)`,
			client.ClientID.Bytes(),
			project.ID.Bytes(),
			client.ClientSecretHashed,
			client.CreatedAt.UTC(),
		); err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (r *ProjectRepository) Delete(ctx context.Context, id domain.ProjectID) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM projects WHERE id = ?", id.Bytes())
	return err
}

func (r *ProjectRepository) findByQuery(ctx context.Context, query string, args ...any) ([]domain.Project, error) {
	var rows []projectRow
	if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return nil, err
	}
	if len(rows) == 0 {
		return nil, nil
	}

	projectIDs := make([]domain.ProjectID, 0, len(rows))
	for _, row := range rows {
		id, err := domain.IDFromBytes(row.ID)
		if err != nil {
			return nil, err
		}
		projectIDs = append(projectIDs, domain.ProjectID(id))
	}
	admins, err := r.adminsByProject(ctx, projectIDs)
	if err != nil {
		return nil, err
	}
	clients, err := r.clientsByProject(ctx, projectIDs)
	if err != nil {
		return nil, err
	}

	projects := make([]domain.Project, 0, len(rows))
	for _, row := range rows {
		project, err := row.toDomain(admins, clients)
		if err != nil {
			return nil, err
		}
		projects = append(projects, project)
	}
	return projects, nil
}

func (r *ProjectRepository) adminsByProject(ctx context.Context, projectIDs []domain.ProjectID) (map[domain.ProjectID][]domain.UserID, error) {
	args := idBytes(projectIDs)
	query, queryArgs, err := sqlx.In("SELECT project_id, user_id FROM project_admins WHERE project_id IN (?)", args)
	if err != nil {
		return nil, err
	}
	var rows []projectAdminRow
	if err := r.db.SelectContext(ctx, &rows, r.db.Rebind(query), queryArgs...); err != nil {
		return nil, err
	}
	result := make(map[domain.ProjectID][]domain.UserID)
	for _, row := range rows {
		projectID, err := domain.IDFromBytes(row.ProjectID)
		if err != nil {
			return nil, err
		}
		userID, err := domain.IDFromBytes(row.UserID)
		if err != nil {
			return nil, err
		}
		result[domain.ProjectID(projectID)] = append(result[domain.ProjectID(projectID)], domain.UserID(userID))
	}
	return result, nil
}

func (r *ProjectRepository) clientsByProject(ctx context.Context, projectIDs []domain.ProjectID) (map[domain.ProjectID][]domain.APIClient, error) {
	args := idBytes(projectIDs)
	query, queryArgs, err := sqlx.In("SELECT client_id, project_id, client_secret, created_at FROM api_clients WHERE project_id IN (?)", args)
	if err != nil {
		return nil, err
	}
	var rows []apiClientRow
	if err := r.db.SelectContext(ctx, &rows, r.db.Rebind(query), queryArgs...); err != nil {
		return nil, err
	}
	result := make(map[domain.ProjectID][]domain.APIClient)
	for _, row := range rows {
		projectID, err := domain.IDFromBytes(row.ProjectID)
		if err != nil {
			return nil, err
		}
		clientID, err := domain.IDFromBytes(row.ClientID)
		if err != nil {
			return nil, err
		}
		result[domain.ProjectID(projectID)] = append(result[domain.ProjectID(projectID)], domain.APIClient{
			ClientID:           clientID,
			ClientSecretHashed: row.ClientSecret,
			CreatedAt:          row.CreatedAt.UTC(),
		})
	}
	return result, nil
}

type projectRow struct {
	ID        []byte         `db:"id"`
	Name      string         `db:"name"`
	OwnerID   []byte         `db:"owner_id"`
	AccountID []byte         `db:"account_id"`
	URL       sql.NullString `db:"url"`
}

func (r projectRow) toDomain(
	admins map[domain.ProjectID][]domain.UserID,
	clients map[domain.ProjectID][]domain.APIClient,
) (domain.Project, error) {
	id, err := domain.IDFromBytes(r.ID)
	if err != nil {
		return domain.Project{}, err
	}
	ownerID, err := domain.IDFromBytes(r.OwnerID)
	if err != nil {
		return domain.Project{}, err
	}
	accountID, err := domain.IDFromBytes(r.AccountID)
	if err != nil {
		return domain.Project{}, err
	}
	name, err := domain.NewProjectName(r.Name)
	if err != nil {
		return domain.Project{}, err
	}
	var projectURL *domain.ProjectURL
	if r.URL.Valid {
		urlValue, err := domain.NewProjectURL(r.URL.String)
		if err != nil {
			return domain.Project{}, err
		}
		projectURL = &urlValue
	}
	projectID := domain.ProjectID(id)
	return domain.Project{
		ID:         projectID,
		Name:       name,
		OwnerID:    domain.UserID(ownerID),
		AdminIDs:   admins[projectID],
		AccountID:  domain.AccountID(accountID),
		APIClients: clients[projectID],
		URL:        projectURL,
	}, nil
}

type projectAdminRow struct {
	ProjectID []byte `db:"project_id"`
	UserID    []byte `db:"user_id"`
}

type apiClientRow struct {
	ClientID     []byte    `db:"client_id"`
	ProjectID    []byte    `db:"project_id"`
	ClientSecret string    `db:"client_secret"`
	CreatedAt    time.Time `db:"created_at"`
}

func idBytes[T ~[16]byte](ids []T) [][]byte {
	out := make([][]byte, 0, len(ids))
	for _, id := range ids {
		domainID := domain.ID(id)
		out = append(out, domainID.Bytes())
	}
	return out
}
