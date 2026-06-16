package timeutil

import "time"

// Now returns the current time in UTC.
func Now() time.Time {
	return time.Now().UTC()
}

// NowPtr returns a pointer to the current UTC time.
func NowPtr() *time.Time {
	t := Now()
	return &t
}
