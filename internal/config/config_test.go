package config

import (
	"strings"
	"testing"
)

func TestLoadDefaults(t *testing.T) {
	clearEnv(t)
	t.Setenv("GRPC_TOKEN", "token")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.HTTP.Address() != "0.0.0.0:8080" {
		t.Fatalf("unexpected HTTP address: %s", cfg.HTTP.Address())
	}
	if cfg.Database.Host != "localhost" || cfg.Database.Port != 3306 {
		t.Fatalf("unexpected database config: %+v", cfg.Database)
	}
	if cfg.GRPC.Address() != "localhost:50051" || cfg.GRPC.Token != "token" {
		t.Fatalf("unexpected grpc config: %+v", cfg.GRPC)
	}
}

func TestLoadCustomValues(t *testing.T) {
	clearEnv(t)
	t.Setenv("HOST", "127.0.0.1")
	t.Setenv("PORT", "9090")
	t.Setenv("DB_HOST", "db")
	t.Setenv("DB_PORT", "3307")
	t.Setenv("DB_NAME", "pteron_test")
	t.Setenv("DB_USER", "user")
	t.Setenv("DB_PASSWORD", "pass")
	t.Setenv("GRPC_HOST", "cornucopia")
	t.Setenv("GRPC_PORT", "6000")
	t.Setenv("GRPC_TOKEN", "token")
	t.Setenv("DEBUG_MODE", "true")
	t.Setenv("WELCOME_BONUS_USER", "11")
	t.Setenv("WELCOME_BONUS_PROJECT", "22")
	t.Setenv("PUBLIC_URL", "https://example.com")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.HTTP.Address() != "127.0.0.1:9090" {
		t.Fatalf("unexpected HTTP address: %s", cfg.HTTP.Address())
	}
	if cfg.Database.Host != "db" || cfg.Database.Port != 3307 || cfg.Database.Name != "pteron_test" || cfg.Database.User != "user" || cfg.Database.Password != "pass" {
		t.Fatalf("unexpected database config: %+v", cfg.Database)
	}
	if cfg.GRPC.Address() != "cornucopia:6000" || cfg.GRPC.Token != "token" {
		t.Fatalf("unexpected grpc config: %+v", cfg.GRPC)
	}
	if !cfg.App.DebugMode || cfg.App.WelcomeBonusUser != 11 || cfg.App.WelcomeBonusProject != 22 || cfg.App.PublicURL != "https://example.com" {
		t.Fatalf("unexpected app config: %+v", cfg.App)
	}
}

func TestLoadLegacyDatabaseValues(t *testing.T) {
	clearEnv(t)
	t.Setenv("GRPC_TOKEN", "token")
	t.Setenv("DATABASE_URL", "jdbc:mariadb://database.example:3307/pteron_prod")
	t.Setenv("DATABASE_USER", "legacy_user")
	t.Setenv("DATABASE_PASSWORD", "legacy_pass")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.Database.Host != "database.example" || cfg.Database.Port != 3307 || cfg.Database.Name != "pteron_prod" || cfg.Database.User != "legacy_user" || cfg.Database.Password != "legacy_pass" {
		t.Fatalf("unexpected database config: %+v", cfg.Database)
	}
}

func TestLoadDatabaseValuesPreferSplitEnv(t *testing.T) {
	clearEnv(t)
	t.Setenv("GRPC_TOKEN", "token")
	t.Setenv("DATABASE_URL", "jdbc:mariadb://legacy:3307/legacy_db")
	t.Setenv("DATABASE_USER", "legacy_user")
	t.Setenv("DATABASE_PASSWORD", "legacy_pass")
	t.Setenv("DB_HOST", "split")
	t.Setenv("DB_PORT", "3308")
	t.Setenv("DB_NAME", "split_db")
	t.Setenv("DB_USER", "split_user")
	t.Setenv("DB_PASSWORD", "split_pass")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.Database.Host != "split" || cfg.Database.Port != 3308 || cfg.Database.Name != "split_db" || cfg.Database.User != "split_user" || cfg.Database.Password != "split_pass" {
		t.Fatalf("unexpected database config: %+v", cfg.Database)
	}
}

func TestLoadRequiresGRPCToken(t *testing.T) {
	clearEnv(t)
	t.Setenv("GRPC_TOKEN", "")

	_, err := Load()
	if err == nil || !strings.Contains(err.Error(), "GRPC_TOKEN is required") {
		t.Fatalf("expected GRPC_TOKEN error, got %v", err)
	}
}

func TestLoadRejectsInvalidNumbers(t *testing.T) {
	clearEnv(t)
	t.Setenv("GRPC_TOKEN", "token")
	t.Setenv("DB_PORT", "mysql")

	_, err := Load()
	if err == nil || !strings.Contains(err.Error(), "DB_PORT must be an integer") {
		t.Fatalf("expected DB_PORT error, got %v", err)
	}
}

func clearEnv(t *testing.T) {
	t.Helper()
	for _, key := range []string{
		"HOST",
		"PORT",
		"DB_HOST",
		"DB_PORT",
		"DB_NAME",
		"DB_USER",
		"DB_PASSWORD",
		"DATABASE_URL",
		"DATABASE_USER",
		"DATABASE_PASSWORD",
		"GRPC_HOST",
		"GRPC_PORT",
		"GRPC_TOKEN",
		"DEBUG_MODE",
		"WELCOME_BONUS_USER",
		"WELCOME_BONUS_PROJECT",
		"PUBLIC_URL",
	} {
		t.Setenv(key, "")
	}
}
