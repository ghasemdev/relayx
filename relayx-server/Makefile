.PHONY: all build build-all build-linux-amd64 build-linux-arm64 build-darwin-amd64 build-darwin-arm64 test clean run

BINARY_NAME=relayx-server
CMD_DIR=./cmd/server
BIN_DIR=./bin
LDFLAGS=-s -w

all: build

build:
	CGO_ENABLED=0 go build -ldflags="$(LDFLAGS)" -o $(BIN_DIR)/$(BINARY_NAME) $(CMD_DIR)

build-linux-amd64:
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="$(LDFLAGS)" -o $(BIN_DIR)/$(BINARY_NAME)-linux-amd64 $(CMD_DIR)

build-linux-arm64:
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags="$(LDFLAGS)" -o $(BIN_DIR)/$(BINARY_NAME)-linux-arm64 $(CMD_DIR)

build-darwin-amd64:
	CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 go build -ldflags="$(LDFLAGS)" -o $(BIN_DIR)/$(BINARY_NAME)-darwin-amd64 $(CMD_DIR)

build-darwin-arm64:
	CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 go build -ldflags="$(LDFLAGS)" -o $(BIN_DIR)/$(BINARY_NAME)-darwin-arm64 $(CMD_DIR)

build-all: build-linux-amd64 build-linux-arm64 build-darwin-amd64 build-darwin-arm64

test:
	go test -v ./...

clean:
	rm -rf $(BIN_DIR) data/

run:
	go run $(CMD_DIR)
