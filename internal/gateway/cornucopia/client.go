package cornucopia

import (
	"context"

	"github.com/google/uuid"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"

	"github.com/traP-jp/pteron-server/internal/config"
	"github.com/traP-jp/pteron-server/internal/domain"
	pb "github.com/traP-jp/pteron-server/internal/generated/cornucopia"
)

const apiKeyHeader = "x-api-key"

type Gateway struct {
	client pb.CornucopiaServiceClient
}

func New(cfg config.GRPC) (*grpc.ClientConn, *Gateway, error) {
	conn, err := grpc.NewClient(
		cfg.Address(),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithUnaryInterceptor(apiKeyInterceptor(cfg.Token)),
	)
	if err != nil {
		return nil, nil, err
	}
	return conn, &Gateway{client: pb.NewCornucopiaServiceClient(conn)}, nil
}

func (g *Gateway) Verify(ctx context.Context) error {
	_, err := g.client.ListAccounts(ctx, &pb.ListAccountsRequest{Limit: 1})
	if err != nil {
		return status.Convert(err).Err()
	}
	return nil
}

func (g *Gateway) FindAccountByID(ctx context.Context, accountID domain.AccountID) (*domain.Account, error) {
	res, err := g.client.GetAccount(ctx, &pb.GetAccountRequest{AccountId: accountID.String()})
	if err != nil {
		if status.Code(err) == codes.NotFound {
			return nil, nil
		}
		return nil, err
	}
	account, err := accountFromProto(res.AccountId, res.Balance, res.CanOverdraft)
	return &account, err
}

func (g *Gateway) FindAccountsByIDs(ctx context.Context, accountIDs []domain.AccountID) ([]domain.Account, error) {
	ids := make([]string, 0, len(accountIDs))
	for _, accountID := range accountIDs {
		ids = append(ids, accountID.String())
	}
	res, err := g.client.GetAccounts(ctx, &pb.GetAccountsRequest{AccountIds: ids})
	if err != nil {
		return nil, err
	}
	accounts := make([]domain.Account, 0, len(res.Accounts))
	for _, item := range res.Accounts {
		account, err := accountFromProto(item.AccountId, item.Balance, item.CanOverdraft)
		if err != nil {
			return nil, err
		}
		accounts = append(accounts, account)
	}
	return accounts, nil
}

func (g *Gateway) CreateAccount(ctx context.Context, canOverdraft bool) (domain.Account, error) {
	res, err := g.client.CreateAccount(ctx, &pb.CreateAccountRequest{CanOverdraft: canOverdraft})
	if err != nil {
		return domain.Account{}, err
	}
	return accountFromProto(res.AccountId, res.Balance, res.CanOverdraft)
}

func (g *Gateway) Transfer(ctx context.Context, from domain.AccountID, to domain.AccountID, amount int64) error {
	_, err := g.client.Transfer(ctx, &pb.TransferRequest{
		FromAccountId:  from.String(),
		ToAccountId:    to.String(),
		Amount:         amount,
		Description:    "Transfer from Pteron",
		IdempotencyKey: uuid.NewString(),
	})
	return err
}

func apiKeyInterceptor(apiKey string) grpc.UnaryClientInterceptor {
	return func(ctx context.Context, method string, req any, reply any, cc *grpc.ClientConn, invoker grpc.UnaryInvoker, opts ...grpc.CallOption) error {
		ctx = metadata.AppendToOutgoingContext(ctx, apiKeyHeader, apiKey)
		return invoker(ctx, method, req, reply, cc, opts...)
	}
}

func accountFromProto(accountID string, balance int64, canOverdraft bool) (domain.Account, error) {
	id, err := domain.ParseID(accountID)
	if err != nil {
		return domain.Account{}, err
	}
	return domain.Account{
		ID:           domain.AccountID(id),
		Balance:      balance,
		CanOverdraft: canOverdraft,
	}, nil
}
