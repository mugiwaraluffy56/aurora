package crypto

import (
	"fmt"
	"time"
)

// ValidateRequestTimestamp rejects a request timestamp that is older than
// maxAge relative to now. ts is Unix seconds.
func ValidateRequestTimestamp(ts int64, maxAge time.Duration) error {
	now := time.Now().Unix()
	diff := now - ts
	if diff < 0 {
		diff = -diff
	}
	if time.Duration(diff)*time.Second > maxAge {
		return fmt.Errorf("request timestamp %d is too old or too far in the future (diff=%ds, max=%s)", ts, diff, maxAge)
	}
	return nil
}
