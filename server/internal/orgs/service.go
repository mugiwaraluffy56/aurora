package orgs

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/pkg/id"
)

// Org represents an organization.
type Org struct {
	ID        string
	Name      string
	Slug      string
	AvatarURL string
	CreatedAt time.Time
	UpdatedAt time.Time
}

// OrgMember represents a user's membership in an org.
type OrgMember struct {
	ID        string
	OrgID     string
	UserID    string
	Role      string
	CreatedAt time.Time
}

// OrgService manages organization lifecycle.
type OrgService interface {
	Create(ctx context.Context, name, slug, ownerID string) (*Org, error)
	Get(ctx context.Context, id string) (*Org, error)
	ListForUser(ctx context.Context, userID string) ([]*Org, error)
	Update(ctx context.Context, id, name string) error
	Delete(ctx context.Context, id string) error
	AddMember(ctx context.Context, orgID, email, role string) error
	RemoveMember(ctx context.Context, orgID, userID string) error
	UpdateMemberRole(ctx context.Context, orgID, userID, role string) error
	GetMember(ctx context.Context, orgID, userID string) (*OrgMember, error)
}

type orgService struct {
	db  *pgxpool.Pool
	log zerolog.Logger
}

// NewService creates a production OrgService.
func NewService(db *pgxpool.Pool, log zerolog.Logger) OrgService {
	return &orgService{db: db, log: log}
}

func (s *orgService) Create(ctx context.Context, name, slug, ownerID string) (*Org, error) {
	orgID := id.New()
	if _, err := s.db.Exec(ctx,
		`INSERT INTO organizations (id, name, slug) VALUES ($1,$2,$3)`, orgID, name, slug,
	); err != nil {
		return nil, fmt.Errorf("create org: %w", err)
	}

	memberID := id.New()
	if _, err := s.db.Exec(ctx,
		`INSERT INTO org_members (id, org_id, user_id, role) VALUES ($1,$2,$3,'OWNER')`,
		memberID, orgID, ownerID,
	); err != nil {
		return nil, fmt.Errorf("add owner member: %w", err)
	}

	return s.Get(ctx, orgID)
}

func (s *orgService) Get(ctx context.Context, orgID string) (*Org, error) {
	o := &Org{}
	err := s.db.QueryRow(ctx,
		`SELECT id, name, slug, avatar_url, created_at, updated_at FROM organizations WHERE id=$1`, orgID,
	).Scan(&o.ID, &o.Name, &o.Slug, &o.AvatarURL, &o.CreatedAt, &o.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("get org %s: %w", orgID, err)
	}
	return o, nil
}

func (s *orgService) ListForUser(ctx context.Context, userID string) ([]*Org, error) {
	rows, err := s.db.Query(ctx,
		`SELECT o.id, o.name, o.slug, o.avatar_url, o.created_at, o.updated_at
		 FROM organizations o JOIN org_members m ON m.org_id=o.id
		 WHERE m.user_id=$1 ORDER BY o.name`, userID,
	)
	if err != nil {
		return nil, fmt.Errorf("list orgs for user: %w", err)
	}
	defer rows.Close()

	var orgList []*Org
	for rows.Next() {
		o := &Org{}
		if err := rows.Scan(&o.ID, &o.Name, &o.Slug, &o.AvatarURL, &o.CreatedAt, &o.UpdatedAt); err != nil {
			return nil, fmt.Errorf("scan org: %w", err)
		}
		orgList = append(orgList, o)
	}
	return orgList, nil
}

func (s *orgService) Update(ctx context.Context, orgID, name string) error {
	if _, err := s.db.Exec(ctx,
		`UPDATE organizations SET name=$2, updated_at=NOW() WHERE id=$1`, orgID, name,
	); err != nil {
		return fmt.Errorf("update org %s: %w", orgID, err)
	}
	return nil
}

func (s *orgService) Delete(ctx context.Context, orgID string) error {
	if _, err := s.db.Exec(ctx, `DELETE FROM organizations WHERE id=$1`, orgID); err != nil {
		return fmt.Errorf("delete org %s: %w", orgID, err)
	}
	return nil
}

func (s *orgService) AddMember(ctx context.Context, orgID, email, role string) error {
	var userID string
	if err := s.db.QueryRow(ctx, `SELECT id FROM users WHERE email=$1`, email).Scan(&userID); err != nil {
		return fmt.Errorf("user not found: %w", err)
	}
	memberID := id.New()
	if _, err := s.db.Exec(ctx,
		`INSERT INTO org_members (id, org_id, user_id, role) VALUES ($1,$2,$3,$4)
		 ON CONFLICT (org_id, user_id) DO NOTHING`,
		memberID, orgID, userID, role,
	); err != nil {
		return fmt.Errorf("add member: %w", err)
	}
	return nil
}

func (s *orgService) RemoveMember(ctx context.Context, orgID, userID string) error {
	if _, err := s.db.Exec(ctx,
		`DELETE FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, userID,
	); err != nil {
		return fmt.Errorf("remove member: %w", err)
	}
	return nil
}

func (s *orgService) UpdateMemberRole(ctx context.Context, orgID, userID, role string) error {
	if _, err := s.db.Exec(ctx,
		`UPDATE org_members SET role=$3, updated_at=NOW() WHERE org_id=$1 AND user_id=$2`,
		orgID, userID, role,
	); err != nil {
		return fmt.Errorf("update member role: %w", err)
	}
	return nil
}

func (s *orgService) GetMember(ctx context.Context, orgID, userID string) (*OrgMember, error) {
	m := &OrgMember{}
	err := s.db.QueryRow(ctx,
		`SELECT id, org_id, user_id, role, created_at FROM org_members WHERE org_id=$1 AND user_id=$2`,
		orgID, userID,
	).Scan(&m.ID, &m.OrgID, &m.UserID, &m.Role, &m.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("get member: %w", err)
	}
	return m, nil
}
