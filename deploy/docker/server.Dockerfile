FROM golang:1.23-alpine AS builder
WORKDIR /app
RUN apk add --no-cache git ca-certificates
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /tap-guard-server ./cmd/server

FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /app
COPY --from=builder /tap-guard-server /app/tap-guard-server
COPY --from=builder /app/migrations /app/migrations
EXPOSE 8080
ENTRYPOINT ["/app/tap-guard-server"]
