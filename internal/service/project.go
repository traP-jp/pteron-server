package service

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"time"

	"golang.org/x/crypto/bcrypt"

	"github.com/traP-jp/pteron-server/internal/app"
	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/gateway"
)

type ProjectStore interface {
	FindAll(ctx context.Context) ([]domain.Project, error)
	FindByID(ctx context.Context, id domain.ProjectID) (*domain.Project, error)
	FindByName(ctx context.Context, name domain.ProjectName) (*domain.Project, error)
	FindByUserID(ctx context.Context, userID domain.UserID) ([]domain.Project, error)
	FindByAPIClientID(ctx context.Context, clientID domain.ID) (*domain.Project, error)
	Save(ctx context.Context, project domain.Project) error
	Delete(ctx context.Context, id domain.ProjectID) error
}

type ProjectService struct {
	projects ProjectStore
	economic gateway.Economic
	bonus    interface {
		SendWelcomeBonusToProject(ctx context.Context, projectID domain.ProjectID, projectAccountID domain.AccountID)
	}
}

type APIClientCreationResult struct {
	PlainSecret string
	APIClient   domain.APIClient
}

func NewProjectService(projects ProjectStore, economic gateway.Economic, bonus interface {
	SendWelcomeBonusToProject(ctx context.Context, projectID domain.ProjectID, projectAccountID domain.AccountID)
}) *ProjectService {
	return &ProjectService{projects: projects, economic: economic, bonus: bonus}
}

func (s *ProjectService) GetProjects(ctx context.Context) ([]domain.Project, error) {
	return s.projects.FindAll(ctx)
}

func (s *ProjectService) GetProjectsByUser(ctx context.Context, userID domain.UserID) ([]domain.Project, error) {
	return s.projects.FindByUserID(ctx, userID)
}

func (s *ProjectService) CreateProject(ctx context.Context, name domain.ProjectName, ownerID domain.UserID, projectURL *domain.ProjectURL) (domain.Project, error) {
	existing, err := s.projects.FindByName(ctx, name)
	if err != nil {
		return domain.Project{}, err
	}
	if existing != nil {
		return domain.Project{}, app.NewError(app.CodeConflict, "Project already exists")
	}
	account, err := s.economic.CreateAccount(ctx, false)
	if err != nil {
		return domain.Project{}, err
	}
	project := domain.Project{
		ID:         domain.MustNewID(),
		Name:       name,
		OwnerID:    ownerID,
		AdminIDs:   []domain.UserID{ownerID},
		AccountID:  account.ID,
		APIClients: nil,
		URL:        projectURL,
	}
	if err := s.projects.Save(ctx, project); err != nil {
		return domain.Project{}, err
	}
	if s.bonus != nil {
		s.bonus.SendWelcomeBonusToProject(ctx, project.ID, project.AccountID)
	}
	return project, nil
}

func (s *ProjectService) GetProject(ctx context.Context, idOrName string) (domain.Project, error) {
	if id, err := domain.ParseID(idOrName); err == nil {
		return s.GetProjectByID(ctx, domain.ProjectID(id))
	}
	name, err := domain.NewProjectName(idOrName)
	if err != nil {
		return domain.Project{}, app.WrapError(app.CodeBadRequest, "Invalid project", err)
	}
	return s.GetProjectByName(ctx, name)
}

func (s *ProjectService) GetProjectByID(ctx context.Context, id domain.ProjectID) (domain.Project, error) {
	project, err := s.projects.FindByID(ctx, id)
	if err != nil {
		return domain.Project{}, err
	}
	if project == nil {
		return domain.Project{}, app.NewError(app.CodeNotFound, "Project not found")
	}
	return *project, nil
}

func (s *ProjectService) GetProjectByName(ctx context.Context, name domain.ProjectName) (domain.Project, error) {
	project, err := s.projects.FindByName(ctx, name)
	if err != nil {
		return domain.Project{}, err
	}
	if project == nil {
		return domain.Project{}, app.NewError(app.CodeNotFound, "Project not found")
	}
	return *project, nil
}

func (s *ProjectService) UpdateProjectURL(ctx context.Context, projectID domain.ProjectID, projectURL domain.ProjectURL, actorID domain.UserID) (domain.Project, error) {
	project, err := s.GetProjectByID(ctx, projectID)
	if err != nil {
		return domain.Project{}, err
	}
	if !project.IsAdmin(actorID) {
		return domain.Project{}, app.NewError(app.CodeForbidden, "Only admins can update project settings")
	}
	project.URL = &projectURL
	if err := s.projects.Save(ctx, project); err != nil {
		return domain.Project{}, err
	}
	return project, nil
}

func (s *ProjectService) AddProjectAdmin(ctx context.Context, projectID domain.ProjectID, userID domain.UserID, actorID domain.UserID) error {
	project, err := s.GetProjectByID(ctx, projectID)
	if err != nil {
		return err
	}
	if !project.CanManageAdmins(actorID) {
		return app.NewError(app.CodeForbidden, "Only the owner can manage admins")
	}
	if project.IsAdmin(userID) {
		return app.NewError(app.CodeBadRequest, "User is already an admin")
	}
	project.AdminIDs = append(project.AdminIDs, userID)
	return s.projects.Save(ctx, project)
}

func (s *ProjectService) DeleteProjectAdmin(ctx context.Context, projectID domain.ProjectID, userID domain.UserID, actorID domain.UserID) error {
	project, err := s.GetProjectByID(ctx, projectID)
	if err != nil {
		return err
	}
	if !project.CanManageAdmins(actorID) {
		return app.NewError(app.CodeForbidden, "Only the owner can manage admins")
	}
	if project.OwnerID == userID {
		return app.NewError(app.CodeBadRequest, "Owner cannot be removed from admins")
	}
	nextAdmins := make([]domain.UserID, 0, len(project.AdminIDs))
	found := false
	for _, adminID := range project.AdminIDs {
		if adminID == userID {
			found = true
			continue
		}
		nextAdmins = append(nextAdmins, adminID)
	}
	if !found {
		return app.NewError(app.CodeBadRequest, "User is not an admin")
	}
	project.AdminIDs = nextAdmins
	return s.projects.Save(ctx, project)
}

func (s *ProjectService) GetProjectAPIClients(ctx context.Context, projectID domain.ProjectID, actorID domain.UserID) ([]domain.APIClient, error) {
	project, err := s.GetProjectByID(ctx, projectID)
	if err != nil {
		return nil, err
	}
	if !project.CanManageAPIClients(actorID) {
		return nil, app.NewError(app.CodeForbidden, "Only admins can view API clients")
	}
	return project.APIClients, nil
}

func (s *ProjectService) CreateAPIClient(ctx context.Context, projectID domain.ProjectID, actorID domain.UserID) (APIClientCreationResult, error) {
	project, err := s.GetProjectByID(ctx, projectID)
	if err != nil {
		return APIClientCreationResult{}, err
	}
	if !project.CanManageAPIClients(actorID) {
		return APIClientCreationResult{}, app.NewError(app.CodeForbidden, "Only admins can manage API clients")
	}
	plainSecret, err := generateSecret()
	if err != nil {
		return APIClientCreationResult{}, err
	}
	hashed, err := bcrypt.GenerateFromPassword([]byte(plainSecret), 12)
	if err != nil {
		return APIClientCreationResult{}, err
	}
	client := domain.APIClient{
		ClientID:           domain.MustNewID(),
		ClientSecretHashed: string(hashed),
		CreatedAt:          time.Now().UTC(),
	}
	project.APIClients = append(project.APIClients, client)
	if err := s.projects.Save(ctx, project); err != nil {
		return APIClientCreationResult{}, err
	}
	return APIClientCreationResult{PlainSecret: plainSecret, APIClient: client}, nil
}

func (s *ProjectService) DeleteAPIClient(ctx context.Context, projectID domain.ProjectID, clientID domain.ID, actorID domain.UserID) error {
	project, err := s.GetProjectByID(ctx, projectID)
	if err != nil {
		return err
	}
	if !project.CanManageAPIClients(actorID) {
		return app.NewError(app.CodeForbidden, "Only admins can manage API clients")
	}
	nextClients := make([]domain.APIClient, 0, len(project.APIClients))
	found := false
	for _, client := range project.APIClients {
		if client.ClientID == clientID {
			found = true
			continue
		}
		nextClients = append(nextClients, client)
	}
	if !found {
		return app.NewError(app.CodeBadRequest, "Client not found")
	}
	project.APIClients = nextClients
	return s.projects.Save(ctx, project)
}

func (s *ProjectService) AuthenticateAPIClient(ctx context.Context, clientID domain.ID, plainSecret string) (*domain.Project, error) {
	project, err := s.projects.FindByAPIClientID(ctx, clientID)
	if err != nil || project == nil {
		return nil, err
	}
	for _, client := range project.APIClients {
		if client.ClientID != clientID {
			continue
		}
		if bcrypt.CompareHashAndPassword([]byte(client.ClientSecretHashed), []byte(plainSecret)) != nil {
			return nil, nil
		}
		return project, nil
	}
	return nil, nil
}

func generateSecret() (string, error) {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(bytes), nil
}
