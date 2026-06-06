package handler

import (
	"net/http"
	"time"

	"github.com/labstack/echo/v4"
	openapi_types "github.com/oapi-codegen/runtime/types"

	"github.com/traP-jp/pteron-server/internal/auth"
	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/generated/publicapi"
	"github.com/traP-jp/pteron-server/internal/service"
)

type PublicAPI struct {
	projects     *service.ProjectService
	transactions *service.TransactionService
	bills        *service.BillService
	users        *service.UserService
	accounts     *service.AccountService
	publicURL    string
}

func NewPublicAPI(projects *service.ProjectService, transactions *service.TransactionService, bills *service.BillService, users *service.UserService, accounts *service.AccountService, publicURL string) *PublicAPI {
	return &PublicAPI{
		projects:     projects,
		transactions: transactions,
		bills:        bills,
		users:        users,
		accounts:     accounts,
		publicURL:    publicURL,
	}
}

func RegisterPublicAPI(e *echo.Echo, api publicapi.ServerInterface, middleware ...echo.MiddlewareFunc) {
	group := e.Group("/api/v1", middleware...)
	publicapi.RegisterHandlers(group, api)
}

func (a *PublicAPI) CreateBill(ctx echo.Context) error {
	project, ok := auth.Project(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	var body publicapi.CreateBillRequest
	if err := ctx.Bind(&body); err != nil {
		return err
	}
	targetUsername, err := domain.NewUsername(body.TargetUser)
	if err != nil {
		return err
	}
	targetUser, err := a.users.GetUserByName(ctx.Request().Context(), targetUsername)
	if err != nil {
		return err
	}
	bill, err := a.bills.CreateBill(ctx.Request().Context(), project.ID, targetUser.ID, body.Amount, body.Description, &body.SuccessUrl, &body.CancelUrl)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusCreated, publicapi.CreateBillResponse{
		BillId:     bill.ID.UUID(),
		PaymentUrl: a.publicURL + "/checkout?id=" + bill.ID.String(),
		ExpiresAt:  time.Now().UTC().Add(24 * time.Hour),
	})
}
func (a *PublicAPI) GetBill(ctx echo.Context, billId openapi_types.UUID) error {
	project, ok := auth.Project(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	bill, err := a.bills.GetBill(ctx.Request().Context(), domain.BillID(billId))
	if err != nil {
		return err
	}
	if bill.ProjectID != project.ID {
		return echo.NewHTTPError(http.StatusNotFound, "Not found")
	}
	dto, err := a.publicBillDTO(ctx, bill)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, dto)
}
func (a *PublicAPI) CancelBill(ctx echo.Context, billId openapi_types.UUID) error {
	project, ok := auth.Project(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	if _, err := a.bills.CancelBill(ctx.Request().Context(), domain.BillID(billId), project.ID); err != nil {
		return err
	}
	return ctx.NoContent(http.StatusNoContent)
}
func (a *PublicAPI) GetMe(ctx echo.Context) error {
	project, ok := auth.Project(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	account, err := a.accounts.GetAccountByID(ctx.Request().Context(), project.AccountID)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, publicapi.Project{
		Id:      project.ID.UUID(),
		Name:    project.Name.String(),
		Balance: account.Balance,
	})
}
func (a *PublicAPI) GetMyBills(ctx echo.Context, params publicapi.GetMyBillsParams) error {
	project, ok := auth.Project(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	options := domain.BillQueryOptions{Limit: params.Limit, Cursor: params.Cursor}
	if params.Status != nil {
		status := domain.BillStatus(*params.Status)
		options.Status = &status
	}
	result, err := a.bills.GetProjectBills(ctx.Request().Context(), project.ID, options)
	if err != nil {
		return err
	}
	items := make([]publicapi.Bill, 0, len(result.Items))
	for _, bill := range result.Items {
		dto, err := a.publicBillDTO(ctx, bill)
		if err != nil {
			return err
		}
		items = append(items, dto)
	}
	return ctx.JSON(http.StatusOK, struct {
		Items      []publicapi.Bill `json:"items"`
		NextCursor *string          `json:"nextCursor,omitempty"`
	}{Items: items, NextCursor: result.NextCursor})
}
func (a *PublicAPI) GetMyTransactions(ctx echo.Context, params publicapi.GetMyTransactionsParams) error {
	project, ok := auth.Project(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	result, err := a.transactions.GetProjectTransactions(ctx.Request().Context(), project.ID, domain.TransactionQueryOptions{Limit: params.Limit, Cursor: params.Cursor})
	if err != nil {
		return err
	}
	items := make([]publicapi.Transaction, 0, len(result.Items))
	for _, transaction := range result.Items {
		dto, err := a.publicTransactionDTO(ctx, transaction)
		if err != nil {
			return err
		}
		items = append(items, dto)
	}
	return ctx.JSON(http.StatusOK, struct {
		Items      []publicapi.Transaction `json:"items"`
		NextCursor *string                 `json:"nextCursor,omitempty"`
	}{Items: items, NextCursor: result.NextCursor})
}
func (a *PublicAPI) CreateTransaction(ctx echo.Context) error {
	project, ok := auth.Project(ctx)
	if !ok {
		return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
	}
	var body publicapi.CreateTransactionRequest
	if err := ctx.Bind(&body); err != nil {
		return err
	}
	targetUsername, err := domain.NewUsername(body.ToUser)
	if err != nil {
		return err
	}
	targetUser, err := a.users.GetUserByName(ctx.Request().Context(), targetUsername)
	if err != nil {
		return err
	}
	transaction, err := a.transactions.Transfer(ctx.Request().Context(), project.ID, targetUser.ID, body.Amount, body.Description)
	if err != nil {
		return err
	}
	dto, err := a.publicTransactionDTO(ctx, transaction)
	if err != nil {
		return err
	}
	return ctx.JSON(http.StatusOK, dto)
}

func (a *PublicAPI) publicTransactionDTO(ctx echo.Context, transaction domain.Transaction) (publicapi.Transaction, error) {
	var userID *openapi_types.UUID
	var userName *string
	if transaction.UserID != nil {
		value := transaction.UserID.UUID()
		userID = &value
		user, err := a.users.GetUserByID(ctx.Request().Context(), *transaction.UserID)
		if err == nil {
			name := user.Name.String()
			userName = &name
		}
	}
	var projectID *openapi_types.UUID
	if transaction.ProjectID != nil {
		value := transaction.ProjectID.UUID()
		projectID = &value
	}
	return publicapi.Transaction{
		Id:          transaction.ID.UUID(),
		Type:        publicapi.TransactionType(transaction.Type),
		Amount:      transaction.Amount,
		UserId:      userID,
		UserName:    userName,
		ProjectId:   projectID,
		Description: transaction.Description,
		CreatedAt:   transaction.CreatedAt.UTC(),
	}, nil
}

func (a *PublicAPI) publicBillDTO(ctx echo.Context, bill domain.Bill) (publicapi.Bill, error) {
	user, err := a.users.GetUserByID(ctx.Request().Context(), bill.UserID)
	if err != nil {
		return publicapi.Bill{}, err
	}
	userName := user.Name.String()
	return publicapi.Bill{
		Id:          bill.ID.UUID(),
		Amount:      bill.Amount,
		UserId:      bill.UserID.UUID(),
		UserName:    &userName,
		Status:      publicapi.BillStatus(bill.Status),
		Description: bill.Description,
		CreatedAt:   bill.CreatedAt.UTC(),
	}, nil
}
