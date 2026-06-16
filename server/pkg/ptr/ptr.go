package ptr

// To returns a pointer to the given value.
func To[T any](v T) *T {
	return &v
}

// From dereferences a pointer, returning the zero value and false if nil.
func From[T any](p *T) (T, bool) {
	if p == nil {
		var zero T
		return zero, false
	}
	return *p, true
}
