package handler

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/labstack/echo/v4"

	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/service"
)

func TestGetProjectApiClientsAcceptsProjectName(t *testing.T) {
	user := domain.User{
		ID:        domain.MustNewID(),
		Name:      mustUsername(t, "alice"),
		AccountID: domain.MustNewID(),
	}
	project := domain.Project{
		ID:        domain.MustNewID(),
		Name:      mustProjectName(t, "project_1"),
		OwnerID:   user.ID,
		AdminIDs:  []domain.UserID{user.ID},
		AccountID: domain.MustNewID(),
		APIClients: []domain.APIClient{
			{
				ClientID:           domain.MustNewID(),
				ClientSecretHashed: "hashed-secret",
				CreatedAt:          time.Date(2026, 6, 8, 12, 0, 0, 0, time.UTC),
			},
		},
	}
	api := NewInternalAPI(
		service.NewUserService(&fakeUserStore{users: []domain.User{user}}, nil, nil),
		service.NewProjectService(&fakeProjectStore{projects: []domain.Project{project}}, nil, nil),
		nil,
		nil,
		nil,
		nil,
	)

	e := echo.New()
	req := httptest.NewRequest(http.MethodGet, "/api/internal/projects/project_1/clients", nil)
	rec := httptest.NewRecorder()
	ctx := e.NewContext(req, rec)
	ctx.Set("username", user.Name)

	if err := api.GetProjectApiClients(ctx, "project_1"); err != nil {
		t.Fatalf("GetProjectApiClients returned error: %v", err)
	}
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body=%s", rec.Code, http.StatusOK, rec.Body.String())
	}
	var clients []struct {
		ClientID     string  `json:"clientId"`
		ClientSecret *string `json:"clientSecret"`
		CreatedAt    string  `json:"createdAt"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &clients); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(clients) != 1 {
		t.Fatalf("client count = %d, want 1", len(clients))
	}
	if clients[0].ClientID != project.APIClients[0].ClientID.String() {
		t.Fatalf("clientId = %q, want %q", clients[0].ClientID, project.APIClients[0].ClientID.String())
	}
	if clients[0].ClientSecret != nil {
		t.Fatalf("clientSecret = %q, want omitted", *clients[0].ClientSecret)
	}
}

type fakeUserStore struct {
	users []domain.User
}

func (s *fakeUserStore) FindAll(context.Context) ([]domain.User, error) {
	return s.users, nil
}

func (s *fakeUserStore) FindByID(_ context.Context, id domain.UserID) (*domain.User, error) {
	for _, user := range s.users {
		if user.ID == id {
			return &user, nil
		}
	}
	return nil, nil
}

func (s *fakeUserStore) FindByIDs(_ context.Context, ids []domain.UserID) ([]domain.User, error) {
	out := make([]domain.User, 0, len(ids))
	for _, id := range ids {
		for _, user := range s.users {
			if user.ID == id {
				out = append(out, user)
				break
			}
		}
	}
	return out, nil
}

func (s *fakeUserStore) FindByUsername(_ context.Context, username domain.Username) (*domain.User, error) {
	for _, user := range s.users {
		if user.Name == username {
			return &user, nil
		}
	}
	return nil, nil
}

func (s *fakeUserStore) Save(_ context.Context, user domain.User) error {
	s.users = append(s.users, user)
	return nil
}

type fakeProjectStore struct {
	projects []domain.Project
}

func (s *fakeProjectStore) FindAll(context.Context) ([]domain.Project, error) {
	return s.projects, nil
}

func (s *fakeProjectStore) FindByID(_ context.Context, id domain.ProjectID) (*domain.Project, error) {
	for _, project := range s.projects {
		if project.ID == id {
			return &project, nil
		}
	}
	return nil, nil
}

func (s *fakeProjectStore) FindByIDs(_ context.Context, ids []domain.ProjectID) ([]domain.Project, error) {
	out := make([]domain.Project, 0, len(ids))
	for _, id := range ids {
		for _, project := range s.projects {
			if project.ID == id {
				out = append(out, project)
				break
			}
		}
	}
	return out, nil
}

func (s *fakeProjectStore) FindByName(_ context.Context, name domain.ProjectName) (*domain.Project, error) {
	for _, project := range s.projects {
		if project.Name.Normalized() == name.Normalized() {
			return &project, nil
		}
	}
	return nil, nil
}

func (s *fakeProjectStore) FindByUserID(_ context.Context, userID domain.UserID) ([]domain.Project, error) {
	out := make([]domain.Project, 0)
	for _, project := range s.projects {
		if project.OwnerID == userID || project.IsAdmin(userID) {
			out = append(out, project)
		}
	}
	return out, nil
}

func (s *fakeProjectStore) FindByAPIClientID(_ context.Context, clientID domain.ID) (*domain.Project, error) {
	for _, project := range s.projects {
		for _, client := range project.APIClients {
			if client.ClientID == clientID {
				return &project, nil
			}
		}
	}
	return nil, nil
}

func (s *fakeProjectStore) Save(_ context.Context, project domain.Project) error {
	for i, existing := range s.projects {
		if existing.ID == project.ID {
			s.projects[i] = project
			return nil
		}
	}
	s.projects = append(s.projects, project)
	return nil
}

func (s *fakeProjectStore) Delete(_ context.Context, id domain.ProjectID) error {
	next := make([]domain.Project, 0, len(s.projects))
	for _, project := range s.projects {
		if project.ID != id {
			next = append(next, project)
		}
	}
	s.projects = next
	return nil
}

func mustUsername(t *testing.T, value string) domain.Username {
	t.Helper()
	username, err := domain.NewUsername(value)
	if err != nil {
		t.Fatal(err)
	}
	return username
}

func mustProjectName(t *testing.T, value string) domain.ProjectName {
	t.Helper()
	name, err := domain.NewProjectName(value)
	if err != nil {
		t.Fatal(err)
	}
	return name
}
