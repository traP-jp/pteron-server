package domain

import "time"

type Account struct {
	ID           AccountID
	Balance      int64
	CanOverdraft bool
}

type User struct {
	ID        UserID
	Name      Username
	AccountID AccountID
}

type Project struct {
	ID         ProjectID
	Name       ProjectName
	OwnerID    UserID
	AdminIDs   []UserID
	AccountID  AccountID
	APIClients []APIClient
	URL        *ProjectURL
}

func (p Project) IsOwner(userID UserID) bool {
	return p.OwnerID == userID
}

func (p Project) IsAdmin(userID UserID) bool {
	for _, adminID := range p.AdminIDs {
		if adminID == userID {
			return true
		}
	}
	return false
}

func (p Project) CanManageAdmins(userID UserID) bool {
	return p.IsOwner(userID)
}

func (p Project) CanManageAPIClients(userID UserID) bool {
	return p.IsAdmin(userID)
}

func (p Project) URLString() *string {
	if p.URL == nil {
		return nil
	}
	value := p.URL.String()
	return &value
}

type APIClient struct {
	ClientID           ID
	ClientSecretHashed string
	CreatedAt          time.Time
}

type TransactionType string

const (
	TransactionTypeTransfer    TransactionType = "TRANSFER"
	TransactionTypeBillPayment TransactionType = "BILL_PAYMENT"
	TransactionTypeSystem      TransactionType = "SYSTEM"
)

type Transaction struct {
	ID          TransactionID
	Type        TransactionType
	Amount      int64
	ProjectID   *ProjectID
	UserID      *UserID
	Description *string
	CreatedAt   time.Time
}

type TransactionQueryOptions struct {
	Limit  *int
	Cursor *string
	Since  *time.Time
}

type TransactionQueryResult struct {
	Items      []Transaction
	NextCursor *string
}

type BalanceChangeData struct {
	InAmount  int64
	OutAmount int64
}

func (d BalanceChangeData) NetChange() int64 {
	return d.InAmount - d.OutAmount
}

type BillStatus string

const (
	BillStatusPending    BillStatus = "PENDING"
	BillStatusProcessing BillStatus = "PROCESSING"
	BillStatusCompleted  BillStatus = "COMPLETED"
	BillStatusRejected   BillStatus = "REJECTED"
	BillStatusFailed     BillStatus = "FAILED"
)

type Bill struct {
	ID          BillID
	Amount      int64
	UserID      UserID
	ProjectID   ProjectID
	Description *string
	Status      BillStatus
	SuccessURL  *string
	CancelURL   *string
	CreatedAt   time.Time
}

type BillQueryOptions struct {
	Limit  *int
	Cursor *string
	Status *BillStatus
}

type BillQueryResult struct {
	Items      []Bill
	NextCursor *string
}

type RankingQueryResult[T any] struct {
	Items      []T
	NextCursor *string
}

func (b Bill) IsPending() bool {
	return b.Status == BillStatusPending
}

func (b Bill) IsProcessing() bool {
	return b.Status == BillStatusProcessing
}

func (b Bill) Approve() (Bill, bool) {
	if !b.IsPending() {
		return Bill{}, false
	}
	b.Status = BillStatusProcessing
	return b, true
}

func (b Bill) Complete() (Bill, bool) {
	if !b.IsProcessing() {
		return Bill{}, false
	}
	b.Status = BillStatusCompleted
	return b, true
}

func (b Bill) Decline() (Bill, bool) {
	if !b.IsPending() {
		return Bill{}, false
	}
	b.Status = BillStatusRejected
	return b, true
}

func (b Bill) MarkAsFailed() (Bill, bool) {
	if !b.IsProcessing() {
		return Bill{}, false
	}
	b.Status = BillStatusFailed
	return b, true
}
