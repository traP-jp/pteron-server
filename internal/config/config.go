package config

import (
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	HTTP     HTTP
	Database Database
	GRPC     GRPC
	App      App
}

type HTTP struct {
	Host string
	Port int
}

func (h HTTP) Address() string {
	return fmt.Sprintf("%s:%d", h.Host, h.Port)
}

type Database struct {
	Host     string
	Port     int
	Name     string
	User     string
	Password string
}

type GRPC struct {
	Host  string
	Port  int
	Token string
}

func (g GRPC) Address() string {
	return fmt.Sprintf("%s:%d", g.Host, g.Port)
}

type App struct {
	DebugMode           bool
	WelcomeBonusUser    int64
	WelcomeBonusProject int64
	PublicURL           string
}

func Load() (Config, error) {
	httpPort, err := getInt("PORT", 8080)
	if err != nil {
		return Config{}, err
	}
	dbPort, err := getInt("DB_PORT", 3306)
	if err != nil {
		return Config{}, err
	}
	grpcPort, err := getInt("GRPC_PORT", 50051)
	if err != nil {
		return Config{}, err
	}
	welcomeBonusUser, err := getInt64("WELCOME_BONUS_USER", 1000)
	if err != nil {
		return Config{}, err
	}
	welcomeBonusProject, err := getInt64("WELCOME_BONUS_PROJECT", 1000)
	if err != nil {
		return Config{}, err
	}
	debugMode, err := getBool("DEBUG_MODE", false)
	if err != nil {
		return Config{}, err
	}
	database, err := loadDatabase(dbPort)
	if err != nil {
		return Config{}, err
	}
	cfg := Config{
		HTTP: HTTP{
			Host: getString("HOST", "0.0.0.0"),
			Port: httpPort,
		},
		Database: database,
		GRPC: GRPC{
			Host:  getString("GRPC_HOST", "localhost"),
			Port:  grpcPort,
			Token: os.Getenv("GRPC_TOKEN"),
		},
		App: App{
			DebugMode:           debugMode,
			WelcomeBonusUser:    welcomeBonusUser,
			WelcomeBonusProject: welcomeBonusProject,
			PublicURL:           getString("PUBLIC_URL", "http://localhost:8080"),
		},
	}
	if cfg.GRPC.Token == "" {
		return Config{}, fmt.Errorf("GRPC_TOKEN is required")
	}
	return cfg, nil
}

func loadDatabase(defaultPort int) (Database, error) {
	db := Database{
		Host:     "localhost",
		Port:     defaultPort,
		Name:     "pteron",
		User:     getString("DATABASE_USER", "pteron"),
		Password: getString("DATABASE_PASSWORD", "pteron_password"),
	}
	if legacyURL := os.Getenv("DATABASE_URL"); legacyURL != "" {
		parsed, err := parseJDBCURL(legacyURL)
		if err != nil {
			return Database{}, err
		}
		db.Host = parsed.Host
		db.Port = parsed.Port
		db.Name = parsed.Name
	}
	db.Host = getString("DB_HOST", db.Host)
	if os.Getenv("DB_PORT") != "" {
		db.Port = defaultPort
	}
	db.Name = getString("DB_NAME", db.Name)
	db.User = getString("DB_USER", db.User)
	db.Password = getString("DB_PASSWORD", db.Password)
	return db, nil
}

func parseJDBCURL(value string) (Database, error) {
	trimmed := strings.TrimPrefix(value, "jdbc:")
	parsed, err := url.Parse(trimmed)
	if err != nil {
		return Database{}, fmt.Errorf("DATABASE_URL must be a valid JDBC URL: %w", err)
	}
	if parsed.Hostname() == "" {
		return Database{}, fmt.Errorf("DATABASE_URL host must not be empty")
	}
	port := 3306
	if parsed.Port() != "" {
		parsedPort, err := strconv.Atoi(parsed.Port())
		if err != nil {
			return Database{}, fmt.Errorf("DATABASE_URL port must be an integer: %w", err)
		}
		port = parsedPort
	}
	name := strings.TrimPrefix(parsed.Path, "/")
	if name == "" {
		return Database{}, fmt.Errorf("DATABASE_URL database name must not be empty")
	}
	return Database{Host: parsed.Hostname(), Port: port, Name: name}, nil
}

func getString(key string, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func getInt(key string, fallback int) (int, error) {
	value := os.Getenv(key)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("%s must be an integer: %w", key, err)
	}
	return parsed, nil
}

func getInt64(key string, fallback int64) (int64, error) {
	value := os.Getenv(key)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("%s must be an integer: %w", key, err)
	}
	return parsed, nil
}

func getBool(key string, fallback bool) (bool, error) {
	value := os.Getenv(key)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return false, fmt.Errorf("%s must be a boolean: %w", key, err)
	}
	return parsed, nil
}
