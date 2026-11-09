package avatars

import "github.com/SMTM-PH/finexy-ai-bookkeeping/pkg/models"

// AvatarProvider is user avatar provider interface
type AvatarProvider interface {
	NeedAvatarToken() bool
	GetAvatarUrl(user *models.User) string
}
