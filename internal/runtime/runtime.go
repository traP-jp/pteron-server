package runtime

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/traP-jp/pteron-server/internal/auth"
	"github.com/traP-jp/pteron-server/internal/config"
	"github.com/traP-jp/pteron-server/internal/db"
	cornucopiagateway "github.com/traP-jp/pteron-server/internal/gateway/cornucopia"
	"github.com/traP-jp/pteron-server/internal/handler"
	mysqlrepo "github.com/traP-jp/pteron-server/internal/repository/mysql"
	"github.com/traP-jp/pteron-server/internal/server"
	"github.com/traP-jp/pteron-server/internal/service"
)

type Server struct {
	cfg    config.Config
	logger *slog.Logger
}

func NewServer(cfg config.Config, logger *slog.Logger) *Server {
	return &Server{cfg: cfg, logger: logger}
}

func (s *Server) Run(ctx context.Context) error {
	conn, err := db.Open(s.cfg.Database)
	if err != nil {
		return fmt.Errorf("open database: %w", err)
	}
	defer conn.Close()

	if err := db.Verify(ctx, conn); err != nil {
		return fmt.Errorf("verify database: %w", err)
	}
	if err := db.Migrate(ctx, conn, "migrations", s.logger); err != nil {
		return fmt.Errorf("migrate database: %w", err)
	}

	grpcConn, economicGateway, err := cornucopiagateway.New(s.cfg.GRPC)
	if err != nil {
		return fmt.Errorf("create cornucopia client: %w", err)
	}
	defer grpcConn.Close()
	if err := economicGateway.Verify(ctx); err != nil {
		return fmt.Errorf("verify cornucopia: %w", err)
	}

	systemConfigRepository := mysqlrepo.NewSystemConfigRepository(conn)
	transactionRepository := mysqlrepo.NewTransactionRepository(conn)
	systemAccountService := service.NewSystemAccountService(
		economicGateway,
		systemConfigRepository,
		transactionRepository,
		s.cfg.App.WelcomeBonusUser,
		s.cfg.App.WelcomeBonusProject,
		s.logger,
	)
	if err := systemAccountService.Initialize(ctx); err != nil {
		return fmt.Errorf("initialize system account: %w", err)
	}

	userRepository := mysqlrepo.NewUserRepository(conn)
	projectRepository := mysqlrepo.NewProjectRepository(conn)
	billRepository := mysqlrepo.NewBillRepository(conn)
	statsCacheRepository := mysqlrepo.NewStatsCacheRepository(conn)

	userService := service.NewUserService(userRepository, economicGateway, systemAccountService)
	projectService := service.NewProjectService(projectRepository, economicGateway, systemAccountService)
	transactionService := service.NewTransactionService(transactionRepository, userRepository, projectRepository, economicGateway)
	billService := service.NewBillService(billRepository, transactionRepository, userRepository, projectRepository, economicGateway)
	accountService := service.NewAccountService(economicGateway)
	statsService := service.NewStatsService(statsCacheRepository, userRepository, projectRepository, transactionRepository, economicGateway)

	statsUpdateJob := service.NewStatsUpdateJob(statsCacheRepository, transactionRepository, userRepository, projectRepository, accountService, s.logger)
	statsUpdateJob.Start(ctx)

	echoServer := server.New(s.logger)
	handler.RegisterHealth(echoServer)
	handler.RegisterInternalAPI(
		echoServer,
		handler.NewInternalAPI(userService, projectService, transactionService, billService, statsService, accountService),
		auth.Forward(s.cfg.App.DebugMode, userService.EnsureUser),
	)
	handler.RegisterPublicAPI(
		echoServer,
		handler.NewPublicAPI(projectService, transactionService, billService, userService, accountService, s.cfg.App.PublicURL),
		auth.Bearer(projectService.AuthenticateAPIClient),
	)

	errCh := make(chan error, 1)
	go func() {
		s.logger.Info("starting pteron server", "address", s.cfg.HTTP.Address())
		errCh <- echoServer.Start(s.cfg.HTTP.Address())
	}()

	select {
	case <-ctx.Done():
		s.logger.Info("shutdown requested")
	case err := <-errCh:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			return fmt.Errorf("serve http: %w", err)
		}
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := echoServer.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("shutdown http: %w", err)
	}
	return nil
}
