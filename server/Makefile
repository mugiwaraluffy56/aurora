.PHONY: dev build test migrate-up migrate-down sqlc-gen lint

dev:
	air -c .air.toml

build:
	go build -o bin/server ./cmd/server

test:
	go test -race -count=1 ./...

migrate-up:
	migrate -database "$(DATABASE_URL)" -path migrations up

migrate-down:
	migrate -database "$(DATABASE_URL)" -path migrations down 1

sqlc-gen:
	cd sqlc && sqlc generate

lint:
	golangci-lint run ./...
