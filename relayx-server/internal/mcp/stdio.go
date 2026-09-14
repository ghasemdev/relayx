package mcp

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"sync"

	"relayx-server/internal/domain"
)

// RunStdio reads newline-delimited JSON-RPC requests from in, dispatches them
// through the MCP Server, and writes newline-delimited responses to out.
func RunStdio(ctx context.Context, server *Server, in io.Reader, out io.Writer) error {
	scanner := bufio.NewScanner(in)
	// Support messages up to 4MB (default 64KB might truncate large tool schemas)
	buf := make([]byte, 64*1024)
	scanner.Buffer(buf, 4*1024*1024)

	var writeMu sync.Mutex
	writeResponse := func(resp *domain.RPCResponse) error {
		if resp == nil {
			return nil
		}
		data, err := json.Marshal(resp)
		if err != nil {
			return fmt.Errorf("marshaling rpc response: %w", err)
		}
		data = append(data, '\n')

		writeMu.Lock()
		defer writeMu.Unlock()
		_, err = out.Write(data)
		return err
	}

	for scanner.Scan() {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		line := scanner.Bytes()
		if len(line) == 0 {
			continue
		}

		req, parseErr := ParseRequest(line)
		if parseErr != nil {
			if err := writeResponse(parseErr); err != nil {
				return err
			}
			continue
		}

		// Handle request in goroutine or synchronously
		// Standard JSON-RPC calls are processed synchronously or concurrently with safe writes
		go func(r *domain.RPCRequest) {
			resp := server.HandleRequest(ctx, r)
			_ = writeResponse(resp)
		}(req)
	}

	if err := scanner.Err(); err != nil && err != io.EOF {
		return fmt.Errorf("reading stdio: %w", err)
	}

	return nil
}
