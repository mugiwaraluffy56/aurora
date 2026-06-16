package oauth

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

// GitHubUser holds the user info returned by GitHub.
type GitHubUser struct {
	ID        int64  `json:"id"`
	Login     string `json:"login"`
	Name      string `json:"name"`
	Email     string `json:"email"`
	AvatarURL string `json:"avatar_url"`
}

// GitHubProvider handles the GitHub OAuth2 flow.
type GitHubProvider struct {
	ClientID     string
	ClientSecret string
	RedirectURL  string
}

// AuthURL returns the GitHub OAuth authorization URL.
func (g *GitHubProvider) AuthURL(state string) string {
	params := url.Values{
		"client_id":    {g.ClientID},
		"redirect_uri": {g.RedirectURL},
		"scope":        {"user:email"},
		"state":        {state},
	}
	return "https://github.com/login/oauth/authorize?" + params.Encode()
}

// Exchange swaps the authorization code for a GitHub access token and user info.
func (g *GitHubProvider) Exchange(ctx context.Context, code string) (*GitHubUser, string, error) {
	token, err := g.exchangeCode(ctx, code)
	if err != nil {
		return nil, "", fmt.Errorf("github exchange code: %w", err)
	}

	user, err := g.fetchUser(ctx, token)
	if err != nil {
		return nil, "", fmt.Errorf("github fetch user: %w", err)
	}

	// If the public profile has no email, fetch the primary verified email.
	if user.Email == "" {
		email, err := g.fetchPrimaryEmail(ctx, token)
		if err != nil {
			return nil, "", fmt.Errorf("github fetch email: %w", err)
		}
		user.Email = email
	}

	return user, token, nil
}

func (g *GitHubProvider) exchangeCode(ctx context.Context, code string) (string, error) {
	body := url.Values{
		"client_id":     {g.ClientID},
		"client_secret": {g.ClientSecret},
		"code":          {code},
		"redirect_uri":  {g.RedirectURL},
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://github.com/login/oauth/access_token", strings.NewReader(body.Encode()))
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	var result struct {
		AccessToken string `json:"access_token"`
		Error       string `json:"error"`
		ErrorDesc   string `json:"error_description"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("decode response: %w", err)
	}
	if result.Error != "" {
		return "", fmt.Errorf("github oauth error: %s — %s", result.Error, result.ErrorDesc)
	}
	return result.AccessToken, nil
}

func (g *GitHubProvider) fetchUser(ctx context.Context, token string) (*GitHubUser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://api.github.com/user", nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Accept", "application/vnd.github.v3+json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("github user API returned %d: %s", resp.StatusCode, b)
	}

	var user GitHubUser
	if err := json.NewDecoder(resp.Body).Decode(&user); err != nil {
		return nil, fmt.Errorf("decode user: %w", err)
	}
	return &user, nil
}

func (g *GitHubProvider) fetchPrimaryEmail(ctx context.Context, token string) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://api.github.com/user/emails", nil)
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Accept", "application/vnd.github.v3+json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	var emails []struct {
		Email    string `json:"email"`
		Primary  bool   `json:"primary"`
		Verified bool   `json:"verified"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&emails); err != nil {
		return "", fmt.Errorf("decode emails: %w", err)
	}

	for _, e := range emails {
		if e.Primary && e.Verified {
			return e.Email, nil
		}
	}
	if len(emails) > 0 {
		return emails[0].Email, nil
	}
	return "", fmt.Errorf("no email found for github user")
}
