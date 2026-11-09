package middlewares

import (
	"github.com/SMTM-PH/finexy-ai-bookkeeping/pkg/core"
	"github.com/SMTM-PH/finexy-ai-bookkeeping/pkg/errs"
	"github.com/SMTM-PH/finexy-ai-bookkeeping/pkg/log"
	"github.com/SMTM-PH/finexy-ai-bookkeeping/pkg/utils"
)

// CheckCurrentTokenType checks if the current token type is allowed
func CheckCurrentTokenType(c *core.WebContext, onlyAllowedTokenTypes ...core.TokenType) bool {
	if len(onlyAllowedTokenTypes) > 0 {
		claims := c.GetTokenClaims()

		if claims == nil {
			log.Warnf(c, "[token_restriction.CheckCurrentTokenType] current token is null")
			utils.PrintJsonErrorResult(c, errs.ErrInvalidToken)
			return false
		}

		for i := 0; i < len(onlyAllowedTokenTypes); i++ {
			if claims.Type == onlyAllowedTokenTypes[i] {
				return true
			}
		}

		log.Warnf(c, "[token_restriction.CheckCurrentTokenType] token type \"%v\" is not allowed to access this API", claims.Type)
		utils.PrintJsonErrorResult(c, errs.ErrInvalidToken)
		return false
	}

	return true
}
