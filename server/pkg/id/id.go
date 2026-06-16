package id

import "github.com/nrednav/cuid2"

// New generates a new collision-resistant unique ID.
func New() string {
	return cuid2.Generate()
}
