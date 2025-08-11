package main

import (
	"context"
	"fmt"
	"log"
	"net/url"
	"os"

	"github.com/jackc/pgx/v5"
)

func main() {
	fmt.Println("DSQL Connectivity Test - Golang")
	fmt.Println("================================")

	// Parse environment variables
	hostname := os.Getenv("HOSTNAME")
	hostaddr := os.Getenv("PGHOSTADDR")
	password := os.Getenv("PGPASSWORD")
	sslmode := os.Getenv("PGSSLMODE")

	// Validate required environment variables
	if hostname == "" {
		log.Fatal("HOSTNAME environment variable is required")
	}
	if hostaddr == "" {
		log.Fatal("PGHOSTADDR environment variable is required")
	}
	if password == "" {
		log.Fatal("PGPASSWORD environment variable is required")
	}
	if sslmode == "" {
		sslmode = "require" // Default to require SSL
	}

	// URL encode the password to handle special characters
	encodedPassword := url.QueryEscape(password)

	// Construct connection string
	connStr := fmt.Sprintf("postgres://admin:%s@%s:5432/postgres?sslmode=%s",
		encodedPassword, hostaddr, sslmode)

	fmt.Printf("Connecting to DSQL cluster: %s\n", hostname)
	fmt.Printf("Through tunnel address: %s:5432\n", hostaddr)

	// Parse config and set SNI hostname
	config, err := pgx.ParseConfig(connStr)
	if err != nil {
		log.Fatalf("Failed to parse connection config: %v", err)
	}

	// Set the SNI hostname to the actual DSQL hostname - this is crucial for DSQL
	if config.TLSConfig != nil {
		config.TLSConfig.ServerName = hostname
	}

	// Connect to database
	ctx := context.Background()
	conn, err := pgx.ConnectConfig(ctx, config)
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer conn.Close(ctx)

	fmt.Println("Connection established successfully!")

	// Execute DSQL-compatible connection info query
	query := `
		SELECT 
			current_database() as database,
			current_user as user,
			version() as server_version
	`

	row := conn.QueryRow(ctx, query)

	var database, user, serverVersion string
	err = row.Scan(&database, &user, &serverVersion)
	if err != nil {
		log.Fatalf("Failed to execute connection info query: %v", err)
	}

	// Display connection information
	fmt.Println("\nConnection Information:")
	fmt.Println("======================")
	fmt.Printf("Database: %s\n", database)
	fmt.Printf("User: %s\n", user)
	fmt.Printf("Host: %s (via tunnel to %s)\n", hostaddr, hostname)
	fmt.Printf("Port: 5432\n")
	fmt.Printf("SSL Status: SSL connection (required by DSQL)\n")
	fmt.Printf("Server Version: %s\n", serverVersion)

	fmt.Println("\nConnection test completed successfully!")
}
