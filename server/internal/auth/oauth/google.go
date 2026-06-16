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

// GoogleUser holds the user info returned by Google.
type GoogleUser struct {
	Sub       string `json:"sub"`
	Name      string `json:"name"`
	Email     string `json:"email"`
	Picture   string `json:"picture"`
	Verified  bool   `json:"email_verified"`
}

// GoogleProvider handles the Google OAuth2 flow.
type GoogleProvider struct {
	ClientID     string
	ClientSecret string
	RedirectURL  string
}

// AuthURL returns the Google OAuth authorization URL.
func (g *GoogleProvider) AuthURL(state string) string {
	params := url.Values{
		"client_id":     {g.ClientID},
		"redirect_uri":  {g.RedirectURL},
		"response_type": {"code"},
		"scope":         {"openid email profile"},
		"state":         {state},
		"access_type":   {"offline"},
	}
	return "https://accounts.google.com/o/oauth2/v2/auth?" + params.Encode()
}

// Exchange swaps the authorization code for user info via Google's token endpoint.
func (g *GoogleProvider) Exchange(ctx context.Context, code string) (*GoogleUser, string, error) {
	token, err := g.exchangeCode(ctx, code)
	if err != nil {
		return nil, "", fmt.Errorf("google exchange code: %w", err)
	}

	user, err := g.fetchUser(ctx, token)
	if err != nil {
		return nil, "", fmt.Errorf("google fetch user: %w", err)
	}
	return user, token, nil
}

func (g *GoogleProvider) exchangeCode(ctx context.Context, code string) (string, error) {
	body := url.Values{
		"client_id":     {g.ClientID},
		"client_secret": {g.ClientSecret},
		"code":          {code},
		"grant_type":    {"authorization_code"},
		"redirect_uri":  {g.RedirectURL},
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://oauth2.googleapis.com/token", strings.NewReader(body.Encode()))
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("google token endpoint returned %d: %s", resp.StatusCode, b)
	}

	var result struct {
		AccessToken string `json:"access_token"`
		Error       string `json:"error"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("decode token response: %w", err)
	}
	if result.Error != "" {
		return "", fmt.Errorf("google oauth error: %s", result.Error)
	}
	return result.AccessToken, nil
}

func (g *GoogleProvider) fetchUser(ctx context.Context, token string) (*GoogleUser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://openidconnect.googleapis.com/v1/userinfo", nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("google userinfo returned %d: %s", resp.StatusCode, b)
	}

	var user GoogleUser
	if err := json.NewDecoder(resp.Body).Decode(&user); err != nil {
		return nil, fmt.Errorf("decode user: %w", err)
	}
	return &user, nil
}
