package handler

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/labstack/echo/v4"

	"github.com/traP-jp/pteron-server/internal/app"
	"github.com/traP-jp/pteron-server/internal/auth"
	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/generated/internalapi"
	"github.com/traP-jp/pteron-server/internal/service"
)

type InternalAPI struct {
	users        *service.UserService
	projects     *service.ProjectService
	transactions *service.TransactionService
	bills        *service.BillService
	stats        *service.StatsService
	accounts     *service.AccountService
}

func NewInternalAPI(users *service.UserService, projects *service.ProjectService, transactions *service.TransactionService, bills *service.BillService, stats *service.StatsService, accounts *service.AccountService) *InternalAPI {
	return &InternalAPI{
		users:        users,
		projects:     projects,
		transactions: transactions,
		bills:        bills,
		stats:        stats,
		accounts:     accounts,
	}
}

func RegisterInternalAPI(e *echo.Echo, api internalapi.ServerInterface, middleware ...echo.MiddlewareFunc) {
	group := e.Group("/api/internal", middleware...)
	internalapi.RegisterHandlers(group, api)
}

func (a *InternalAPI) GetCurrentUser(ctx echo.Context) error {
	username, ok := auth.Username(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	user, err := a.users.GetUserByName(ctx.Request().Context(), username)
	if err != nil {
		return err
	}
	account, err := a.accounts.GetAccountByID(ctx.Request().Context(), user.AccountID)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, userDTO(user, account))
}
func (a *InternalAPI) GetBill(ctx echo.Context, billId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	id, err := domain.ParseID(billId)
	if err != nil {
		return err
	}
	bill, err := a.bills.GetBill(ctx.Request().Context(), domain.BillID(id))
	if err != nil {
		return err
	}
	if bill.UserID != currentUser.ID {
		return echo.NewHTTPError(http.StatusNotFound, "Not found")
	}
	dto, err := a.billDTO(ctx.Request().Context(), bill)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, dto)
}
func (a *InternalAPI) ApproveBill(ctx echo.Context, billId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	id, err := domain.ParseID(billId)
	if err != nil {
		return err
	}
	bill, err := a.bills.GetBill(ctx.Request().Context(), domain.BillID(id))
	if err != nil {
		return err
	}
	if bill.UserID != currentUser.ID {
		return echo.NewHTTPError(http.StatusNotFound, "Not found")
	}
	project, _ := a.projects.GetProjectByID(ctx.Request().Context(), bill.ProjectID)
	_, err = a.bills.ApproveBill(ctx.Request().Context(), domain.BillID(id), currentUser.ID)
	if err != nil {
		var appErr *app.Error
		if errors.As(err, &appErr) && appErr.Code == app.CodeConflict {
			return err
		}
		baseURL := firstString(bill.CancelURL, bill.SuccessURL, project.URLString(), ptr("/"))
		return ctx.JSON(http.StatusOK, struct {
			RedirectURL string `json:"redirectUrl"`
		}{RedirectURL: buildRedirectURL(*baseURL, domain.BillID(id), "failed")})
	}
	baseURL := firstString(bill.SuccessURL, bill.CancelURL, project.URLString(), ptr("/"))
	return ctx.JSON(http.StatusOK, struct {
		RedirectURL string `json:"redirectUrl"`
	}{RedirectURL: buildRedirectURL(*baseURL, domain.BillID(id), "success")})
}
func (a *InternalAPI) DeclineBill(ctx echo.Context, billId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	id, err := domain.ParseID(billId)
	if err != nil {
		return err
	}
	bill, err := a.bills.DeclineBill(ctx.Request().Context(), domain.BillID(id), currentUser.ID)
	if err != nil {
		return err
	}
	project, _ := a.projects.GetProjectByID(ctx.Request().Context(), bill.ProjectID)
	baseURL := firstString(bill.CancelURL, bill.SuccessURL, project.URLString(), ptr("/"))
	return ctx.JSON(http.StatusOK, struct {
		RedirectURL string `json:"redirectUrl"`
	}{RedirectURL: buildRedirectURL(*baseURL, domain.BillID(id), "declined")})
}
func (a *InternalAPI) GetProjects(ctx echo.Context) error {
	projects, err := a.projects.GetProjects(ctx.Request().Context())
	if err != nil {
		return err
	}
	items, err := a.projectDTOs(ctx.Request().Context(), projects)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, struct {
		Items []internalapi.Project `json:"items"`
	}{Items: items})
}
func (a *InternalAPI) CreateProject(ctx echo.Context) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	var body internalapi.CreateProjectJSONBody
	if err := ctx.Bind(&body); err != nil {
		return err
	}
	name, err := domain.NewProjectName(body.Name)
	if err != nil {
		return err
	}
	var projectURL *domain.ProjectURL
	if body.Url != nil {
		value, err := domain.NewProjectURL(*body.Url)
		if err != nil {
			return err
		}
		projectURL = &value
	}
	project, err := a.projects.CreateProject(ctx.Request().Context(), name, currentUser.ID, projectURL)
	if err != nil {
		return err
	}
	items, err := a.projectDTOs(ctx.Request().Context(), []domain.Project{project})
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusCreated, items[0])
}
func (a *InternalAPI) GetProject(ctx echo.Context, projectId string) error {
	project, err := a.projects.GetProject(ctx.Request().Context(), projectId)
	if err != nil {
		return err
	}
	items, err := a.projectDTOs(ctx.Request().Context(), []domain.Project{project})
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, items[0])
}
func (a *InternalAPI) UpdateProject(ctx echo.Context, projectId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	projectID, err := domain.ParseID(projectId)
	if err != nil {
		return err
	}
	var body internalapi.UpdateProjectJSONBody
	if err := ctx.Bind(&body); err != nil {
		return err
	}
	projectURL, err := domain.NewProjectURL(body.Url)
	if err != nil {
		return err
	}
	project, err := a.projects.UpdateProjectURL(ctx.Request().Context(), domain.ProjectID(projectID), projectURL, currentUser.ID)
	if err != nil {
		return err
	}
	items, err := a.projectDTOs(ctx.Request().Context(), []domain.Project{project})
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusCreated, items[0])
}
func (a *InternalAPI) RemoveProjectAdmin(ctx echo.Context, projectId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	projectID, targetUser, err := a.projectAdminRequest(ctx, projectId)
	if err != nil {
		return err
	}
	if err := a.projects.DeleteProjectAdmin(ctx.Request().Context(), projectID, targetUser.ID, currentUser.ID); err != nil {
		return err
	}
	return ctx.NoContent(http.StatusNoContent)
}
func (a *InternalAPI) GetProjectAdmins(ctx echo.Context, projectId string) error {
	project, err := a.projects.GetProject(ctx.Request().Context(), projectId)
	if err != nil {
		return err
	}
	users, err := a.users.GetUsersByIDs(ctx.Request().Context(), project.AdminIDs)
	if err != nil {
		return err
	}
	accountIDs := make([]domain.AccountID, 0, len(users))
	for _, user := range users {
		accountIDs = append(accountIDs, user.AccountID)
	}
	accounts, err := a.accounts.GetAccountsByIDs(ctx.Request().Context(), accountIDs)
	if err != nil {
		return err
	}
	accountByID := make(map[domain.AccountID]domain.Account, len(accounts))
	for _, account := range accounts {
		accountByID[account.ID] = account
	}
	items := make([]internalapi.User, 0, len(users))
	for _, user := range users {
		items = append(items, userDTO(user, accountByID[user.AccountID]))
	}
	return ctx.JSON(http.StatusOK, items)
}
func (a *InternalAPI) AddProjectAdmin(ctx echo.Context, projectId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	projectID, targetUser, err := a.projectAdminRequest(ctx, projectId)
	if err != nil {
		return err
	}
	if err := a.projects.AddProjectAdmin(ctx.Request().Context(), projectID, targetUser.ID, currentUser.ID); err != nil {
		return err
	}
	return ctx.NoContent(http.StatusNoContent)
}
func (a *InternalAPI) GetProjectBalance(ctx echo.Context, projectId string, params internalapi.GetProjectBalanceParams) error {
	id, err := domain.ParseID(projectId)
	if err != nil {
		return err
	}
	at := time.Now().UTC()
	if params.Date != nil {
		at = params.Date.UTC()
	}
	balance, err := a.stats.GetProjectBalanceAt(ctx.Request().Context(), domain.ProjectID(id), at)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, struct {
		Balance int64 `json:"balance"`
	}{Balance: balance})
}
func (a *InternalAPI) GetProjectApiClients(ctx echo.Context, projectId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	project, err := a.projects.GetProject(ctx.Request().Context(), projectId)
	if err != nil {
		return err
	}
	clients, err := a.projects.GetProjectAPIClients(ctx.Request().Context(), project.ID, currentUser.ID)
	if err != nil {
		return err
	}
	items := make([]internalapi.APIClient, 0, len(clients))
	for _, client := range clients {
		items = append(items, apiClientDTO(client, nil))
	}
	return ctx.JSON(http.StatusOK, items)
}
func (a *InternalAPI) CreateProjectApiClient(ctx echo.Context, projectId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	project, err := a.projects.GetProject(ctx.Request().Context(), projectId)
	if err != nil {
		return err
	}
	result, err := a.projects.CreateAPIClient(ctx.Request().Context(), project.ID, currentUser.ID)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusCreated, apiClientDTO(result.APIClient, &result.PlainSecret))
}
func (a *InternalAPI) DeleteProjectApiClient(ctx echo.Context, projectId string, clientId string) error {
	currentUser, err := a.currentUser(ctx)
	if err != nil {
		return err
	}
	project, err := a.projects.GetProject(ctx.Request().Context(), projectId)
	if err != nil {
		return err
	}
	clientID, err := domain.ParseID(clientId)
	if err != nil {
		return err
	}
	if err := a.projects.DeleteAPIClient(ctx.Request().Context(), project.ID, clientID, currentUser.ID); err != nil {
		return err
	}
	return ctx.NoContent(http.StatusNoContent)
}
func (a *InternalAPI) GetProjectStats(ctx echo.Context, projectId string, params internalapi.GetProjectStatsParams) error {
	id, err := domain.ParseID(projectId)
	if err != nil {
		return err
	}
	stats, err := a.stats.GetProjectStats(ctx.Request().Context(), domain.ProjectID(id), domain.Term(params.Term))
	if err != nil {
		return err
	}
	project, err := a.projects.GetProjectByID(ctx.Request().Context(), domain.ProjectID(id))
	if err != nil {
		return err
	}
	projects, err := a.projectDTOs(ctx.Request().Context(), []domain.Project{project})
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, individualProjectStatsResponse(stats, projects[0]))
}
func (a *InternalAPI) GetSystemStats(ctx echo.Context, params internalapi.GetSystemStatsParams) error {
	stats, err := a.stats.GetSystemStats(ctx.Request().Context(), domain.Term(params.Term))
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, systemStatsResponse(stats))
}
func (a *InternalAPI) GetProjectsStats(ctx echo.Context, params internalapi.GetProjectsStatsParams) error {
	stats, err := a.stats.GetProjectsAggregateStats(ctx.Request().Context(), domain.Term(params.Term))
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, aggregateStatsResponse(stats))
}
func (a *InternalAPI) GetProjectRankings(ctx echo.Context, projectName internalapi.GetProjectRankingsParamsProjectName, params internalapi.GetProjectRankingsParams) error {
	limit := 20
	if params.Limit != nil {
		limit = *params.Limit
	}
	ascending := params.Order != nil && *params.Order == internalapi.GetProjectRankingsParamsOrderAsc
	result, err := a.stats.GetProjectRankings(ctx.Request().Context(), domain.RankingType(projectName), domain.Term(params.Term), ascending, limit, params.Cursor)
	if err != nil {
		return err
	}
	items := make([]projectRankingItemResponse, 0, len(result.Items))
	for _, item := range result.Items {
		projects, err := a.projectDTOs(ctx.Request().Context(), []domain.Project{item.Project})
		if err != nil {
			return err
		}
		items = append(items, projectRankingItemResponse{Rank: item.Rank, Value: item.Value, Difference: item.Difference, Project: projects[0]})
	}
	return ctx.JSON(http.StatusOK, struct {
		Items      []projectRankingItemResponse `json:"items"`
		NextCursor *string                      `json:"nextCursor,omitempty"`
	}{Items: items, NextCursor: result.NextCursor})
}
func (a *InternalAPI) GetUsersStats(ctx echo.Context, params internalapi.GetUsersStatsParams) error {
	stats, err := a.stats.GetUsersAggregateStats(ctx.Request().Context(), domain.Term(params.Term))
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, aggregateStatsResponse(stats))
}
func (a *InternalAPI) GetUserRankings(ctx echo.Context, rankingName internalapi.GetUserRankingsParamsRankingName, params internalapi.GetUserRankingsParams) error {
	limit := 20
	if params.Limit != nil {
		limit = *params.Limit
	}
	ascending := params.Order != nil && *params.Order == internalapi.GetUserRankingsParamsOrderAsc
	result, err := a.stats.GetUserRankings(ctx.Request().Context(), domain.RankingType(rankingName), domain.Term(params.Term), ascending, limit, params.Cursor)
	if err != nil {
		return err
	}
	items := make([]userRankingItemResponse, 0, len(result.Items))
	for _, item := range result.Items {
		items = append(items, userRankingItemResponse{
			Rank:       item.Rank,
			Value:      item.Value,
			Difference: item.Difference,
			User:       userDTO(item.User, domain.Account{ID: item.User.AccountID, Balance: item.Balance}),
		})
	}
	return ctx.JSON(http.StatusOK, struct {
		Items      []userRankingItemResponse `json:"items"`
		NextCursor *string                   `json:"nextCursor,omitempty"`
	}{Items: items, NextCursor: result.NextCursor})
}
func (a *InternalAPI) GetTransactions(ctx echo.Context, params internalapi.GetTransactionsParams) error {
	result, err := a.transactions.GetTransactions(ctx.Request().Context(), transactionOptions(params.Term, params.Limit, params.Cursor))
	if err != nil {
		return err
	}
	items, err := a.transactionDTOs(ctx.Request().Context(), result.Items)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, struct {
		Items      []internalapi.Transaction `json:"items"`
		NextCursor *string                   `json:"nextCursor,omitempty"`
	}{Items: items, NextCursor: result.NextCursor})
}
func (a *InternalAPI) GetProjectTransactions(ctx echo.Context, projectId string, params internalapi.GetProjectTransactionsParams) error {
	project, err := a.projects.GetProject(ctx.Request().Context(), projectId)
	if err != nil {
		return err
	}
	result, err := a.transactions.GetProjectTransactions(ctx.Request().Context(), project.ID, transactionOptions(params.Term, params.Limit, params.Cursor))
	if err != nil {
		return err
	}
	items, err := a.transactionDTOs(ctx.Request().Context(), result.Items)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, struct {
		Items      []internalapi.Transaction `json:"items"`
		NextCursor *string                   `json:"nextCursor,omitempty"`
	}{Items: items, NextCursor: result.NextCursor})
}
func (a *InternalAPI) GetUserTransactions(ctx echo.Context, userId string, params internalapi.GetUserTransactionsParams) error {
	user, err := a.users.GetUser(ctx.Request().Context(), userId)
	if err != nil {
		return err
	}
	result, err := a.transactions.GetUserTransactions(ctx.Request().Context(), user.ID, transactionOptions(params.Term, params.Limit, params.Cursor))
	if err != nil {
		return err
	}
	items, err := a.transactionDTOs(ctx.Request().Context(), result.Items)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, struct {
		Items      []internalapi.Transaction `json:"items"`
		NextCursor *string                   `json:"nextCursor,omitempty"`
	}{Items: items, NextCursor: result.NextCursor})
}
func (a *InternalAPI) GetUsers(ctx echo.Context) error {
	users, err := a.users.GetAllUsers(ctx.Request().Context())
	if err != nil {
		return err
	}
	accountIDs := make([]domain.AccountID, 0, len(users))
	for _, user := range users {
		accountIDs = append(accountIDs, user.AccountID)
	}
	accounts, err := a.accounts.GetAccountsByIDs(ctx.Request().Context(), accountIDs)
	if err != nil {
		return err
	}
	accountByID := make(map[domain.AccountID]domain.Account, len(accounts))
	for _, account := range accounts {
		accountByID[account.ID] = account
	}
	items := make([]internalapi.User, 0, len(users))
	for _, user := range users {
		items = append(items, userDTO(user, accountByID[user.AccountID]))
	}
	return ctx.JSON(http.StatusOK, struct {
		Items []internalapi.User `json:"items"`
	}{Items: items})
}
func (a *InternalAPI) GetUser(ctx echo.Context, userId string) error {
	user, err := a.users.GetUser(ctx.Request().Context(), userId)
	if err != nil {
		return err
	}
	account, err := a.accounts.GetAccountByID(ctx.Request().Context(), user.AccountID)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, userDTO(user, account))
}
func (a *InternalAPI) GetUserBalance(ctx echo.Context, userId string, params internalapi.GetUserBalanceParams) error {
	user, err := a.users.GetUser(ctx.Request().Context(), userId)
	if err != nil {
		return err
	}
	at := time.Now().UTC()
	if params.Date != nil {
		at = params.Date.UTC()
	}
	balance, err := a.stats.GetUserBalanceAt(ctx.Request().Context(), user.ID, at)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, struct {
		Balance int64 `json:"balance"`
	}{Balance: balance})
}
func (a *InternalAPI) GetUserProjects(ctx echo.Context, userId string) error {
	user, err := a.users.GetUser(ctx.Request().Context(), userId)
	if err != nil {
		return err
	}
	projects, err := a.projects.GetProjectsByUser(ctx.Request().Context(), user.ID)
	if err != nil {
		return err
	}
	items, err := a.projectDTOs(ctx.Request().Context(), projects)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, struct {
		Items []internalapi.Project `json:"items"`
	}{Items: items})
}
func (a *InternalAPI) GetUserStats(ctx echo.Context, userId string, params internalapi.GetUserStatsParams) error {
	user, err := a.users.GetUser(ctx.Request().Context(), userId)
	if err != nil {
		return err
	}
	stats, err := a.stats.GetUserStats(ctx.Request().Context(), user.ID, domain.Term(params.Term))
	if err != nil {
		return err
	}
	account, err := a.accounts.GetAccountByID(ctx.Request().Context(), user.AccountID)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, individualUserStatsResponse(stats, userDTO(user, account)))
}

func userDTO(user domain.User, account domain.Account) internalapi.User {
	return internalapi.User{
		Id:      user.ID.UUID(),
		Name:    user.Name.String(),
		Balance: account.Balance,
	}
}

func apiClientDTO(client domain.APIClient, plainSecret *string) internalapi.APIClient {
	return internalapi.APIClient{
		ClientId:     client.ClientID.String(),
		ClientSecret: plainSecret,
		CreatedAt:    client.CreatedAt.UTC(),
	}
}

func (a *InternalAPI) transactionDTOs(ctx context.Context, transactions []domain.Transaction) ([]internalapi.Transaction, error) {
	if len(transactions) == 0 {
		return []internalapi.Transaction{}, nil
	}

	userIDs := make([]domain.UserID, 0, len(transactions))
	projectIDs := make([]domain.ProjectID, 0, len(transactions))
	for _, transaction := range transactions {
		if transaction.UserID != nil {
			userIDs = append(userIDs, *transaction.UserID)
		}
		if transaction.ProjectID != nil {
			projectIDs = append(projectIDs, *transaction.ProjectID)
		}
	}

	users, err := a.users.GetUsersByIDs(ctx, uniqueIDs(userIDs))
	if err != nil {
		return nil, err
	}
	userByID := make(map[domain.UserID]domain.User, len(users))
	accountIDs := make([]domain.AccountID, 0, len(users))
	for _, user := range users {
		userByID[user.ID] = user
		accountIDs = append(accountIDs, user.AccountID)
	}
	accounts, err := a.accounts.GetAccountsByIDs(ctx, uniqueIDs(accountIDs))
	if err != nil {
		return nil, err
	}
	accountByID := make(map[domain.AccountID]domain.Account, len(accounts))
	for _, account := range accounts {
		accountByID[account.ID] = account
	}

	projects, err := a.projects.GetProjectsByIDs(ctx, uniqueIDs(projectIDs))
	if err != nil {
		return nil, err
	}
	projectDTOItems, err := a.projectDTOs(ctx, projects)
	if err != nil {
		return nil, err
	}
	projectDTOByID := make(map[domain.ProjectID]internalapi.Project, len(projectDTOItems))
	for i, dto := range projectDTOItems {
		projectDTOByID[projects[i].ID] = dto
	}

	items := make([]internalapi.Transaction, 0, len(transactions))
	for _, transaction := range transactions {
		var userDTOValue *internalapi.User
		if transaction.UserID != nil {
			user, ok := userByID[*transaction.UserID]
			if ok {
				if account, ok := accountByID[user.AccountID]; ok {
					dto := userDTO(user, account)
					userDTOValue = &dto
				}
			}
		}
		var projectDTOValue *internalapi.Project
		if transaction.ProjectID != nil {
			dto, ok := projectDTOByID[*transaction.ProjectID]
			if ok {
				projectDTOValue = &dto
			}
		}
		items = append(items, internalapi.Transaction{
			Id:          transaction.ID.UUID(),
			Type:        internalapi.TransactionType(transaction.Type),
			Amount:      transaction.Amount,
			Project:     projectDTOValue,
			User:        userDTOValue,
			Description: transaction.Description,
			CreatedAt:   transaction.CreatedAt.UTC(),
		})
	}
	return items, nil
}

func (a *InternalAPI) billDTO(ctx context.Context, bill domain.Bill) (internalapi.Bill, error) {
	user, err := a.users.GetUserByID(ctx, bill.UserID)
	if err != nil {
		return internalapi.Bill{}, err
	}
	userAccount, err := a.accounts.GetAccountByID(ctx, user.AccountID)
	if err != nil {
		return internalapi.Bill{}, err
	}
	project, err := a.projects.GetProjectByID(ctx, bill.ProjectID)
	if err != nil {
		return internalapi.Bill{}, err
	}
	projects, err := a.projectDTOs(ctx, []domain.Project{project})
	if err != nil {
		return internalapi.Bill{}, err
	}
	return internalapi.Bill{
		Id:          bill.ID.UUID(),
		Amount:      bill.Amount,
		User:        userDTO(user, userAccount),
		Project:     projects[0],
		Status:      internalapi.BillStatus(bill.Status),
		Description: bill.Description,
		CreatedAt:   bill.CreatedAt.UTC(),
	}, nil
}

func (a *InternalAPI) currentUser(ctx echo.Context) (domain.User, error) {
	username, ok := auth.Username(ctx)
	if !ok {
		return domain.User{}, echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	return a.users.GetUserByName(ctx.Request().Context(), username)
}

func (a *InternalAPI) projectAdminRequest(ctx echo.Context, projectIDValue string) (domain.ProjectID, domain.User, error) {
	projectID, err := domain.ParseID(projectIDValue)
	if err != nil {
		return domain.ProjectID{}, domain.User{}, err
	}
	var body internalapi.AddProjectAdminJSONBody
	if err := ctx.Bind(&body); err != nil {
		return domain.ProjectID{}, domain.User{}, err
	}
	targetUser, err := a.users.GetUser(ctx.Request().Context(), body.UserId)
	if err != nil {
		return domain.ProjectID{}, domain.User{}, err
	}
	return domain.ProjectID(projectID), targetUser, nil
}

func (a *InternalAPI) projectDTOs(ctx context.Context, projects []domain.Project) ([]internalapi.Project, error) {
	if len(projects) == 0 {
		return []internalapi.Project{}, nil
	}

	userIDs := make([]domain.UserID, 0)
	accountIDs := make([]domain.AccountID, 0, len(projects))
	for _, project := range projects {
		accountIDs = append(accountIDs, project.AccountID)
		userIDs = append(userIDs, project.OwnerID)
		userIDs = append(userIDs, project.AdminIDs...)
	}
	users, err := a.users.GetUsersByIDs(ctx, uniqueIDs(userIDs))
	if err != nil {
		return nil, err
	}
	for _, user := range users {
		accountIDs = append(accountIDs, user.AccountID)
	}
	accounts, err := a.accounts.GetAccountsByIDs(ctx, uniqueIDs(accountIDs))
	if err != nil {
		return nil, err
	}

	userByID := make(map[domain.UserID]domain.User, len(users))
	for _, user := range users {
		userByID[user.ID] = user
	}
	accountByID := make(map[domain.AccountID]domain.Account, len(accounts))
	for _, account := range accounts {
		accountByID[account.ID] = account
	}

	items := make([]internalapi.Project, 0, len(projects))
	for _, project := range projects {
		owner := userByID[project.OwnerID]
		admins := make([]internalapi.User, 0, len(project.AdminIDs))
		for _, adminID := range project.AdminIDs {
			admin := userByID[adminID]
			admins = append(admins, userDTO(admin, accountByID[admin.AccountID]))
		}
		var url *string
		if project.URL != nil {
			value := project.URL.String()
			url = &value
		}
		items = append(items, internalapi.Project{
			Id:      project.ID.UUID(),
			Name:    project.Name.String(),
			Owner:   userDTO(owner, accountByID[owner.AccountID]),
			Admins:  admins,
			Balance: accountByID[project.AccountID].Balance,
			Url:     url,
		})
	}
	return items, nil
}

func uniqueIDs[T comparable](values []T) []T {
	seen := make(map[T]struct{}, len(values))
	out := make([]T, 0, len(values))
	for _, value := range values {
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		out = append(out, value)
	}
	return out
}

func transactionOptions[T ~string](term *T, limit *int, cursor *string) domain.TransactionQueryOptions {
	if term != nil {
		var duration time.Duration
		switch string(*term) {
		case "24hours":
			duration = 24 * time.Hour
		case "7days":
			duration = 7 * 24 * time.Hour
		case "30days":
			duration = 30 * 24 * time.Hour
		case "365days":
			duration = 365 * 24 * time.Hour
		}
		if duration > 0 {
			since := time.Now().UTC().Add(-duration)
			return domain.TransactionQueryOptions{Since: &since}
		}
	}
	if limit == nil {
		defaultLimit := 20
		limit = &defaultLimit
	}
	return domain.TransactionQueryOptions{Limit: limit, Cursor: cursor}
}

func buildRedirectURL(baseURL string, billID domain.BillID, status string) string {
	separator := "?"
	if strings.Contains(baseURL, "?") {
		separator = "&"
	}
	return baseURL + separator + "billId=" + billID.String() + "&status=" + status
}

func firstString(values ...*string) *string {
	for _, value := range values {
		if value != nil {
			return value
		}
	}
	return nil
}

func ptr(value string) *string {
	return &value
}

type systemStatsResponse struct {
	Balance    int64 `json:"balance"`
	Difference int64 `json:"difference"`
	Count      int64 `json:"count"`
	Total      int64 `json:"total"`
	Ratio      int64 `json:"ratio"`
}

func aggregateStatsResponse(stats domain.AggregateStats) struct {
	Number     int64 `json:"number"`
	Balance    int64 `json:"balance"`
	Difference int64 `json:"difference"`
	Count      int64 `json:"count"`
	Total      int64 `json:"total"`
	Ratio      int64 `json:"ratio"`
} {
	return struct {
		Number     int64 `json:"number"`
		Balance    int64 `json:"balance"`
		Difference int64 `json:"difference"`
		Count      int64 `json:"count"`
		Total      int64 `json:"total"`
		Ratio      int64 `json:"ratio"`
	}{stats.Number, stats.Balance, stats.Difference, stats.Count, stats.Total, stats.Ratio}
}

type userRankingItemResponse struct {
	Rank       int64            `json:"rank"`
	Value      int64            `json:"value"`
	Difference int64            `json:"difference"`
	User       internalapi.User `json:"user"`
}

type projectRankingItemResponse struct {
	Rank       int64               `json:"rank"`
	Value      int64               `json:"value"`
	Difference int64               `json:"difference"`
	Project    internalapi.Project `json:"project"`
}

func individualUserStatsResponse(stats domain.IndividualStats, user internalapi.User) struct {
	Balance    userRankingItemResponse `json:"balance"`
	Difference userRankingItemResponse `json:"difference"`
	In         userRankingItemResponse `json:"in"`
	Out        userRankingItemResponse `json:"out"`
	Count      userRankingItemResponse `json:"count"`
	Total      userRankingItemResponse `json:"total"`
	Ratio      userRankingItemResponse `json:"ratio"`
} {
	item := func(position domain.RankingPosition) userRankingItemResponse {
		return userRankingItemResponse{Rank: position.Rank, Value: position.Value, Difference: position.Difference, User: user}
	}
	return struct {
		Balance    userRankingItemResponse `json:"balance"`
		Difference userRankingItemResponse `json:"difference"`
		In         userRankingItemResponse `json:"in"`
		Out        userRankingItemResponse `json:"out"`
		Count      userRankingItemResponse `json:"count"`
		Total      userRankingItemResponse `json:"total"`
		Ratio      userRankingItemResponse `json:"ratio"`
	}{item(stats.Balance), item(stats.Difference), item(stats.InAmount), item(stats.OutAmount), item(stats.Count), item(stats.Total), item(stats.Ratio)}
}

func individualProjectStatsResponse(stats domain.IndividualStats, project internalapi.Project) struct {
	Balance    projectRankingItemResponse `json:"balance"`
	Difference projectRankingItemResponse `json:"difference"`
	In         projectRankingItemResponse `json:"in"`
	Out        projectRankingItemResponse `json:"out"`
	Count      projectRankingItemResponse `json:"count"`
	Total      projectRankingItemResponse `json:"total"`
	Ratio      projectRankingItemResponse `json:"ratio"`
} {
	item := func(position domain.RankingPosition) projectRankingItemResponse {
		return projectRankingItemResponse{Rank: position.Rank, Value: position.Value, Difference: position.Difference, Project: project}
	}
	return struct {
		Balance    projectRankingItemResponse `json:"balance"`
		Difference projectRankingItemResponse `json:"difference"`
		In         projectRankingItemResponse `json:"in"`
		Out        projectRankingItemResponse `json:"out"`
		Count      projectRankingItemResponse `json:"count"`
		Total      projectRankingItemResponse `json:"total"`
		Ratio      projectRankingItemResponse `json:"ratio"`
	}{item(stats.Balance), item(stats.Difference), item(stats.InAmount), item(stats.OutAmount), item(stats.Count), item(stats.Total), item(stats.Ratio)}
}
