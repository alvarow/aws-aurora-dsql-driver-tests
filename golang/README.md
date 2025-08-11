# Golang DSQL Connectivity Implementation

This Go implementation demonstrates connecting to AWS DSQL clusters through existing SSH or SSM tunnels using the pgx PostgreSQL driver for Go.

## Prerequisites

- Go 1.23 or higher
- Active SSH or SSM tunnel to DSQL cluster
- Valid DSQL authentication token

## Dependencies

The project uses Go modules with the following dependency:

```go
require github.com/jackc/pgx/v5 v5.7.5
```

## Project Structure

```
golang/
├── go.mod          # Go module definition
├── go.sum          # Dependency checksums
├── main.go         # Main implementation
└── README.md       # This file
```

## Environment Variables

Set these environment variables before running:

```bash
export HOSTNAME="a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws"
export PGHOSTADDR="127.0.0.1"
export PGPASSWORD="your-dsql-token"
export PGSSLMODE="require"
```

## Build and Run

### Direct Execution

```bash
# Navigate to golang directory
cd golang

# Download dependencies
go mod tidy

# Run the application
go run main.go
```

### Build Executable

```bash
# Build binary
go build -o dsql-test main.go

# Run the binary
./dsql-test

# Build for different platforms
GOOS=linux GOARCH=amd64 go build -o dsql-test-linux main.go
GOOS=windows GOARCH=amd64 go build -o dsql-test.exe main.go
```

### Development Commands

```bash
# Format code
go fmt

# Vet code for issues
go vet

# Run with race detection
go run -race main.go

# Build with optimizations
go build -ldflags="-s -w" -o dsql-test main.go
```

## Sample Output

```text
DSQL Connectivity Test - Golang
================================
Connecting to DSQL cluster: a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws
Through tunnel address: 127.0.0.1:5432
Connection established successfully!

Connection Information:
======================
Database: postgres
User: admin
Host: 127.0.0.1 (via tunnel to a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws)
Port: 5432
SSL Status: SSL connection (required by DSQL)
Server Version: PostgreSQL 16

Connection test completed successfully!
```

## Implementation Details

### SNI (Server Name Indication) Configuration

**IMPORTANT**: This implementation explicitly configures SNI for DSQL connections:

```go
// Parse config and set SNI hostname
config, err := pgx.ParseConfig(connStr)
if err != nil {
    log.Fatalf("Failed to parse connection config: %v", err)
}

// Set the SNI hostname to the actual DSQL hostname - this is crucial for DSQL
if config.TLSConfig != nil {
    config.TLSConfig.ServerName = hostname
}
```

**How it works:**
- Connection string uses `hostaddr` (127.0.0.1) for the actual TCP connection
- `config.TLSConfig.ServerName` is set to the DSQL hostname for proper SSL/SNI negotiation
- This ensures DSQL receives the correct SNI during the SSL handshake

Without this configuration, you would get the error: `"unable to accept connection, sni was not received"`

### Connection String Format

The implementation uses PostgreSQL connection string format:

```go
connStr := fmt.Sprintf("postgres://admin:%s@%s:5432/postgres?sslmode=%s",
    encodedPassword, hostaddr, sslmode)
```

### Database Operations

- **Connection**: Uses `pgx.ConnectConfig()` with custom TLS configuration
- **Context**: Uses `context.Background()` for connection management
- **Query**: Executes single-row query with `conn.QueryRow()`
- **Cleanup**: Defers connection closure for proper resource management

### Error Handling

The application handles various error scenarios:

- **Environment Variables**: Validates all required variables
- **Connection Errors**: Detailed error messages for connection failures
- **Query Errors**: Proper error handling for SQL operations
- **Resource Cleanup**: Ensures database connections are properly closed

### Query Implementation

**IMPORTANT**: The query has been updated to work with DSQL, which doesn't support certain PostgreSQL functions:

```go
query := `
    SELECT 
        current_database() as database,
        current_user as user,
        version() as server_version
`
```

**Note**: This query was simplified because DSQL doesn't support `inet_server_addr()`, `inet_server_port()`, or `ssl_is_used()` functions.

## Troubleshooting

### Common Issues

#### "SNI was not received" Error
```
unable to accept connection, sni was not received
```
**Cause**: SSL connection not sending proper Server Name Indication (SNI) for DSQL.

**Solution**: The current implementation fixes this by setting `config.TLSConfig.ServerName = hostname`. If you encounter this error, ensure you're using the latest version of the code.

#### Module Not Found
```
go: cannot find module providing package github.com/jackc/pgx/v5
```
**Solution**: Initialize and download dependencies:
```bash
go mod init dsql-connectivity-experiment
go mod tidy
```

#### Connection Refused
```
dial tcp 127.0.0.1:5432: connect: connection refused
```
**Solution**: Verify your SSH/SSM tunnel is active and forwarding to port 5432.

#### SSL Connection Error
```
tls: first record does not look like a TLS handshake
```
**Solution**: Ensure `PGSSLMODE=require` is set and tunnel supports SSL.

#### Authentication Failed
```
password authentication failed for user "admin"
```
**Solution**: Generate a new DSQL auth token and update `PGPASSWORD`.

### Go-Specific Issues

#### Import Path Issues
```bash
# Clean module cache
go clean -modcache

# Re-download dependencies
go mod download
```

#### Build Issues
```bash
# Verbose build output
go build -v

# Check module status
go mod verify
```

## Configuration Options

### Connection Timeouts

For production applications, consider adding connection timeouts:

```go
import (
    "context"
    "time"
)

// Create context with timeout
ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
defer cancel()

// Use context with connection
conn, err := pgx.ConnectConfig(ctx, config)
```

### Connection Pooling

For production applications, be aware of DSQL Limits, especially new connection rate limit. Here's an example to use pgxpool for connection pooling:

```go
import "github.com/jackc/pgx/v5/pgxpool"

// Create connection pool
poolConfig, err := pgxpool.ParseConfig(connStr)
if err != nil {
    log.Fatalf("Failed to parse pool config: %v", err)
}

// Set SNI hostname for pool connections
if poolConfig.ConnConfig.TLSConfig != nil {
    poolConfig.ConnConfig.TLSConfig.ServerName = hostname
}

// Configure pool settings
poolConfig.MaxConns = 10
poolConfig.MinConns = 2
poolConfig.MaxConnLifetime = 30 * time.Minute // Less than DSQL's 60-minute limit
poolConfig.MaxConnIdleTime = 5 * time.Minute

// Create pool
pool, err := pgxpool.NewWithConfig(context.Background(), poolConfig)
if err != nil {
    log.Fatalf("Failed to create connection pool: %v", err)
}
defer pool.Close()
```

## DSQL-Specific Considerations

### Connection Limits and Timeouts
- **60-Minute Connection Limit**: DSQL automatically closes connections after 60 minutes
- **Connection Pooling**: For production applications, set `MaxConnLifetime` to less than 60 minutes
- **Idle Connections**: DSQL may close idle connections before the 60-minute limit
- **Max Connections**: DSQL defaults to 10000

### Authentication and Security
- **Token-Based Authentication**: DSQL uses generated tokens instead of traditional passwords
- **Token Expiration**: Auth tokens have configurable expiration times (typically 1-12 hours)
- **Token Rotation**: Implement automatic token refresh for long-running applications
- **SSL/TLS Mandatory**: All DSQL connections require SSL encryption with proper SNI

### Performance Characteristics
- **Cold Start Latency**: Initial connections may have higher latency
- **Query Execution**: DSQL is optimized for analytical workloads
- **Connection Overhead**: Minimize connection establishment overhead through pooling

## Performance Considerations

- **Connection Reuse**: Use pgxpool for connection pooling in production
- **Prepared Statements**: Use for repeated queries
- **Context Timeouts**: Implement proper timeout handling
- **Resource Management**: Always close connections and statements
- **SNI Configuration**: Ensure proper SNI setup to avoid connection failures

## Security Best Practices

- Use environment variables for sensitive configuration or fetch from secrets management software
- Implement connection timeout limits
- Monitor connection duration (DSQL 60-minute limit)
- Validate input parameters
- Use prepared statements to prevent SQL injection
- Regularly rotate DSQL authentication tokens
- Ensure proper SNI configuration for SSL connections

## Testing

### Unit Tests

Create test files following Go conventions:

```go
package main

import (
    "os"
    "testing"
)

func TestEnvironmentVariables(t *testing.T) {
    // Set test environment variables
    os.Setenv("HOSTNAME", "test-host")
    os.Setenv("PGHOSTADDR", "127.0.0.1")
    os.Setenv("PGPASSWORD", "test-token")
    os.Setenv("PGSSLMODE", "require")
    
    // Test environment variable parsing
    hostname := os.Getenv("HOSTNAME")
    if hostname != "test-host" {
        t.Errorf("Expected hostname 'test-host', got '%s'", hostname)
    }
}
```

Run tests:
```bash
# Run tests
go test

# Run tests with coverage
go test -cover

# Run tests with race detection
go test -race
```

### Integration Tests

For testing against actual DSQL:

```go
//go:build integration
// +build integration

func TestDSQLConnectivity(t *testing.T) {
    // Integration test implementation
}
```

Run integration tests:
```bash
go test -tags=integration
```

## Go Module Configuration

Complete `go.mod` example:

```go
module dsql-connectivity-experiment

go 1.23.0

require (
    github.com/jackc/pgx/v5 v5.7.5
)

require (
    github.com/jackc/pgpassfile v1.0.0 // indirect
    github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
    golang.org/x/crypto v0.37.0 // indirect
    golang.org/x/text v0.24.0 // indirect
)
```
