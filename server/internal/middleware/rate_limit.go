package middleware

import (
	"net"
	"net/http"
	"sync"
	"time"
)

type tokenBucket struct {
	tokens    float64
	maxTokens float64
	rate      float64 // tokens per second
	lastRefil time.Time
	mu        sync.Mutex
}

func newTokenBucket(maxTokens float64, ratePerMin float64) *tokenBucket {
	return &tokenBucket{
		tokens:    maxTokens,
		maxTokens: maxTokens,
		rate:      ratePerMin / 60.0,
		lastRefil: time.Now(),
	}
}

func (b *tokenBucket) allow() bool {
	b.mu.Lock()
	defer b.mu.Unlock()

	now := time.Now()
	elapsed := now.Sub(b.lastRefil).Seconds()
	b.tokens = min(b.maxTokens, b.tokens+elapsed*b.rate)
	b.lastRefil = now

	if b.tokens >= 1 {
		b.tokens--
		return true
	}
	return false
}

func min(a, b float64) float64 {
	if a < b {
		return a
	}
	return b
}

type rateLimiter struct {
	buckets    map[string]*tokenBucket
	mu         sync.Mutex
	maxTokens  float64
	ratePerMin float64
}

func newRateLimiter(maxTokens, ratePerMin float64) *rateLimiter {
	rl := &rateLimiter{
		buckets:    make(map[string]*tokenBucket),
		maxTokens:  maxTokens,
		ratePerMin: ratePerMin,
	}
	// Periodically clean up old buckets.
	go func() {
		ticker := time.NewTicker(5 * time.Minute)
		for range ticker.C {
			rl.mu.Lock()
			rl.buckets = make(map[string]*tokenBucket)
			rl.mu.Unlock()
		}
	}()
	return rl
}

func (rl *rateLimiter) getBucket(ip string) *tokenBucket {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	b, ok := rl.buckets[ip]
	if !ok {
		b = newTokenBucket(rl.maxTokens, rl.ratePerMin)
		rl.buckets[ip] = b
	}
	return b
}

func (rl *rateLimiter) middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip, _, err := net.SplitHostPort(r.RemoteAddr)
		if err != nil {
			ip = r.RemoteAddr
		}
		if !rl.getBucket(ip).allow() {
			http.Error(w, `{"code":"rate_limited","message":"too many requests"}`, http.StatusTooManyRequests)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// RateLimitAuth limits auth endpoints to 100 req/min per IP.
func RateLimitAuth(next http.Handler) http.Handler {
	return newRateLimiter(20, 100).middleware(next)
}

// RateLimitAPI limits API endpoints to 1000 req/min per IP.
func RateLimitAPI(next http.Handler) http.Handler {
	return newRateLimiter(100, 1000).middleware(next)
}
