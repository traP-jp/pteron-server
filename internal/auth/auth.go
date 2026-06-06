package auth

import (
	"context"
	"encoding/base64"
	"net/http"
	"strings"

	"github.com/labstack/echo/v4"

	"github.com/traP-jp/pteron-server/internal/domain"
)

const (
	forwardedUserHeader = "X-Forwarded-User"
	bearerPrefix        = "Bearer "
)

type ProjectValidator func(ctx context.Context, clientID domain.ID, clientSecret string) (*domain.Project, error)
type UserEnsurer func(ctx context.Context, username domain.Username) error

func Forward(debugMode bool, ensure ...UserEnsurer) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			value := c.Request().Header.Get(forwardedUserHeader)
			if value == "" && debugMode {
				value = "traP"
			}
			if value == "" {
				return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
			}
			username, err := domain.NewUsername(value)
			if err != nil {
				return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
			}
			if len(ensure) > 0 && ensure[0] != nil {
				if err := ensure[0](c.Request().Context(), username); err != nil {
					return err
				}
			}
			c.Set("username", username)
			return next(c)
		}
	}
}

func Username(c echo.Context) (domain.Username, bool) {
	value, ok := c.Get("username").(domain.Username)
	return value, ok
}

func Bearer(validate ProjectValidator) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			header := c.Request().Header.Get(echo.HeaderAuthorization)
			if !strings.HasPrefix(strings.ToLower(header), strings.ToLower(bearerPrefix)) {
				return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
			}
			clientID, clientSecret, ok := decodeCredentials(strings.TrimSpace(header[len(bearerPrefix):]))
			if !ok || validate == nil {
				return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
			}
			project, err := validate(c.Request().Context(), clientID, clientSecret)
			if err != nil || project == nil {
				return echo.NewHTTPError(http.StatusUnauthorized, "Unauthorized")
			}
			c.Set("project", *project)
			return next(c)
		}
	}
}

func Project(c echo.Context) (domain.Project, bool) {
	value, ok := c.Get("project").(domain.Project)
	return value, ok
}

func decodeCredentials(token string) (domain.ID, string, bool) {
	decoded, err := base64.StdEncoding.DecodeString(token)
	if err != nil {
		return domain.ID{}, "", false
	}
	parts := strings.SplitN(string(decoded), ":", 2)
	if len(parts) != 2 {
		return domain.ID{}, "", false
	}
	clientID, err := domain.ParseID(parts[0])
	if err != nil {
		return domain.ID{}, "", false
	}
	return clientID, parts[1], true
}
