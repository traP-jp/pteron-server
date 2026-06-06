package service

import (
	"context"
	"time"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/traP-jp/pteron-server/internal/app"
	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/gateway"
)

type BillStore interface {
	FindByID(ctx context.Context, id domain.BillID) (*domain.Bill, error)
	FindByUserID(ctx context.Context, userID domain.UserID, options domain.BillQueryOptions) (domain.BillQueryResult, error)
	FindByProjectID(ctx context.Context, projectID domain.ProjectID, options domain.BillQueryOptions) (domain.BillQueryResult, error)
	Save(ctx context.Context, bill domain.Bill) error
}

type BillApprovalSuccess struct {
	Bill        domain.Bill
	Transaction domain.Transaction
}

type BillService struct {
	bills        BillStore
	transactions TransactionStore
	users        UserStore
	projects     ProjectStore
	economic     gateway.Economic
}

func NewBillService(bills BillStore, transactions TransactionStore, users UserStore, projects ProjectStore, economic gateway.Economic) *BillService {
	return &BillService{
		bills:        bills,
		transactions: transactions,
		users:        users,
		projects:     projects,
		economic:     economic,
	}
}

func (s *BillService) CreateBill(ctx context.Context, projectID domain.ProjectID, targetUserID domain.UserID, amount int64, description *string, successURL *string, cancelURL *string) (domain.Bill, error) {
	if amount <= 0 {
		return domain.Bill{}, app.NewError(app.CodeBadRequest, "Amount must be greater than zero")
	}
	project, err := s.projects.FindByID(ctx, projectID)
	if err != nil {
		return domain.Bill{}, err
	}
	if project == nil {
		return domain.Bill{}, app.NewError(app.CodeNotFound, "Project not found")
	}
	user, err := s.users.FindByID(ctx, targetUserID)
	if err != nil {
		return domain.Bill{}, err
	}
	if user == nil {
		return domain.Bill{}, app.NewError(app.CodeNotFound, "User not found")
	}
	bill := domain.Bill{
		ID:          domain.MustNewID(),
		Amount:      amount,
		UserID:      targetUserID,
		ProjectID:   projectID,
		Description: description,
		Status:      domain.BillStatusPending,
		SuccessURL:  successURL,
		CancelURL:   cancelURL,
		CreatedAt:   time.Now().UTC(),
	}
	if err := s.bills.Save(ctx, bill); err != nil {
		return domain.Bill{}, err
	}
	return bill, nil
}

func (s *BillService) GetBill(ctx context.Context, billID domain.BillID) (domain.Bill, error) {
	bill, err := s.bills.FindByID(ctx, billID)
	if err != nil {
		return domain.Bill{}, err
	}
	if bill == nil {
		return domain.Bill{}, app.NewError(app.CodeNotFound, "Bill not found")
	}
	return *bill, nil
}

func (s *BillService) GetProjectBills(ctx context.Context, projectID domain.ProjectID, options domain.BillQueryOptions) (domain.BillQueryResult, error) {
	return s.bills.FindByProjectID(ctx, projectID, options)
}

func (s *BillService) ApproveBill(ctx context.Context, billID domain.BillID, actorUserID domain.UserID) (BillApprovalSuccess, error) {
	bill, err := s.GetBill(ctx, billID)
	if err != nil {
		return BillApprovalSuccess{}, err
	}
	if bill.UserID != actorUserID {
		return BillApprovalSuccess{}, app.NewError(app.CodeNotFound, "Bill not found")
	}
	processingBill, ok := bill.Approve()
	if !ok {
		return BillApprovalSuccess{}, app.NewError(app.CodeConflict, "Bill has already been processed")
	}
	if err := s.bills.Save(ctx, processingBill); err != nil {
		return BillApprovalSuccess{}, err
	}
	user, err := s.users.FindByID(ctx, bill.UserID)
	if err != nil {
		return BillApprovalSuccess{}, err
	}
	if user == nil {
		return BillApprovalSuccess{}, app.NewError(app.CodeNotFound, "User not found")
	}
	project, err := s.projects.FindByID(ctx, bill.ProjectID)
	if err != nil {
		return BillApprovalSuccess{}, err
	}
	if project == nil {
		return BillApprovalSuccess{}, app.NewError(app.CodeNotFound, "Project not found")
	}
	if err := s.economic.Transfer(ctx, user.AccountID, project.AccountID, bill.Amount); err != nil {
		if failedBill, ok := processingBill.MarkAsFailed(); ok {
			_ = s.bills.Save(ctx, failedBill)
		}
		switch status.Code(err) {
		case codes.FailedPrecondition:
			return BillApprovalSuccess{}, app.NewError(app.CodeBadRequest, "Insufficient balance for user")
		case codes.NotFound:
			return BillApprovalSuccess{}, app.NewError(app.CodeNotFound, "Account not found")
		default:
			return BillApprovalSuccess{}, err
		}
	}
	completedBill, ok := processingBill.Complete()
	if !ok {
		return BillApprovalSuccess{}, app.NewError(app.CodeConflict, "Bill state changed unexpectedly")
	}
	transaction := domain.Transaction{
		ID:          domain.MustNewID(),
		Type:        domain.TransactionTypeBillPayment,
		Amount:      bill.Amount,
		ProjectID:   &bill.ProjectID,
		UserID:      &bill.UserID,
		Description: bill.Description,
		CreatedAt:   time.Now().UTC(),
	}
	if err := s.bills.Save(ctx, completedBill); err != nil {
		return BillApprovalSuccess{}, err
	}
	if err := s.transactions.Save(ctx, transaction); err != nil {
		return BillApprovalSuccess{}, err
	}
	return BillApprovalSuccess{Bill: completedBill, Transaction: transaction}, nil
}

func (s *BillService) DeclineBill(ctx context.Context, billID domain.BillID, actorUserID domain.UserID) (domain.Bill, error) {
	bill, err := s.GetBill(ctx, billID)
	if err != nil {
		return domain.Bill{}, err
	}
	if bill.UserID != actorUserID {
		return domain.Bill{}, app.NewError(app.CodeNotFound, "Bill not found")
	}
	declinedBill, ok := bill.Decline()
	if !ok {
		return domain.Bill{}, app.NewError(app.CodeConflict, "Bill has already been processed")
	}
	return declinedBill, s.bills.Save(ctx, declinedBill)
}

func (s *BillService) CancelBill(ctx context.Context, billID domain.BillID, actorProjectID domain.ProjectID) (domain.Bill, error) {
	bill, err := s.GetBill(ctx, billID)
	if err != nil {
		return domain.Bill{}, err
	}
	if bill.ProjectID != actorProjectID {
		return domain.Bill{}, app.NewError(app.CodeNotFound, "Bill not found")
	}
	declinedBill, ok := bill.Decline()
	if !ok {
		return domain.Bill{}, app.NewError(app.CodeConflict, "Bill has already been processed")
	}
	return declinedBill, s.bills.Save(ctx, declinedBill)
}
