package domain

import (
	"fmt"
	"net/url"
	"regexp"
	"strings"
)

var projectNamePattern = regexp.MustCompile(`^[a-zA-Z0-9_]+$`)

type Username string

func NewUsername(value string) (Username, error) {
	if value == "" {
		return "", fmt.Errorf("username must not be empty")
	}
	return Username(value), nil
}

func (u Username) String() string {
	return string(u)
}

type ProjectName string

func NewProjectName(value string) (ProjectName, error) {
	if len(value) > 32 {
		return "", fmt.Errorf("project name must be 32 characters or less")
	}
	if !projectNamePattern.MatchString(value) {
		return "", fmt.Errorf("project name must contain only alphanumeric characters and underscores")
	}
	return ProjectName(value), nil
}

func (n ProjectName) String() string {
	return string(n)
}

func (n ProjectName) Normalized() string {
	return strings.ToLower(string(n))
}

type ProjectURL string

func NewProjectURL(value string) (ProjectURL, error) {
	if len(value) > 2048 {
		return "", fmt.Errorf("url must be 2048 characters or less")
	}
	parsed, err := url.Parse(value)
	if err != nil {
		return "", fmt.Errorf("url must be valid: %w", err)
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return "", fmt.Errorf("url must be a valid http or https url")
	}
	if parsed.Host == "" {
		return "", fmt.Errorf("url host must not be empty")
	}
	return ProjectURL(value), nil
}

func (u ProjectURL) String() string {
	return string(u)
}
