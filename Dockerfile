FROM golang:1.26-bookworm AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY cmd cmd
COPY internal internal

RUN go test ./... && CGO_ENABLED=0 GOOS=linux go build -o /out/pteron ./cmd/pteron

FROM gcr.io/distroless/static-debian12

# Labels
LABEL org.opencontainers.image.source="https://github.com/traP-jp/pteron-server"
LABEL org.opencontainers.image.description="Pteron Server"

WORKDIR /app

COPY --from=builder /out/pteron /app/pteron
COPY migrations /app/migrations

USER nonroot:nonroot

# Pteron server port
EXPOSE 8080

# Default command
CMD ["/app/pteron"]
