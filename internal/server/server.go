package server

import (
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"

	"github.com/traP-jp/pteron-server/internal/app"
)

type errorResponse struct {
	Message string `json:"message"`
}

func New(logger *slog.Logger) *echo.Echo {
	e := echo.New()
	e.HideBanner = true
	e.HidePort = true
	e.HTTPErrorHandler = func(err error, c echo.Context) {
		status := http.StatusInternalServerError
		message := "Internal server error"
		if he, ok := err.(*echo.HTTPError); ok {
			status = he.Code
			if m, ok := he.Message.(string); ok && m != "" {
				message = m
			} else {
				message = http.StatusText(status)
			}
		} else {
			var appErr *app.Error
			if errors.As(err, &appErr) {
				status = statusFromAppCode(appErr.Code)
				message = appErr.Message
			}
		}
		if !c.Response().Committed {
			_ = c.JSON(status, errorResponse{Message: message})
		}
	}
	e.Use(middleware.Recover())
	e.Use(requestLogger(logger))
	return e
}

func requestLogger(logger *slog.Logger) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			start := time.Now()
			err := next(c)
			if err != nil {
				c.Error(err)
			}
			req := c.Request()
			res := c.Response()
			logger.Info(
				"http request",
				"method", req.Method,
				"path", req.URL.Path,
				"status", res.Status,
				"latency_ms", time.Since(start).Milliseconds(),
				"remote_ip", c.RealIP(),
			)
			return nil
		}
	}
}

func statusFromAppCode(code app.Code) int {
	switch code {
	case app.CodeBadRequest:
		return http.StatusBadRequest
	case app.CodeUnauthorized:
		return http.StatusUnauthorized
	case app.CodeForbidden:
		return http.StatusForbidden
	case app.CodeNotFound:
		return http.StatusNotFound
	case app.CodeConflict:
		return http.StatusConflict
	default:
		return http.StatusInternalServerError
	}
}
