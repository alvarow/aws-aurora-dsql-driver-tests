# AWS Aurora DSQL Connectivity Experiment

## Overview

This experiment demonstrates how to connect to AWS DSQL clusters from multiple programming languages through existing SSH or AWS SSM tunnels. The project provides working examples in Golang, Java, Node.js, and Python that connect to a DSQL cluster via tunneled connections and execute connection info queries to verify successful connectivity.

All of the implementations rely on setting the DSQL hostname for proper SSL/SNI negotiation, but connecting on the tunnel endpoint, as set by `PGHOSTADDR`. This is the secret sauce.

  How it works:                                                                   
  • host: Set to the tunnel IP address (127.0.0.1) for the actual TCP connection  
  • ssl.servername: Set to the actual DSQL hostname for proper SNI negotiation during the SSL/TLS handshake

**Important**: This experiment assumes you have an existing DSQL cluster and pre-configured SSH or SSM tunnel. The code examples connect through the tunnel, not directly to the DSQL cluster.

## Disclaimer

**Security & completeness:** The example code in this repository is provided **for demonstration purposes only**.  
It is **not intended to be secure or production-ready**, nor is it a complete solution for deploying or securing DSQL access.  
The examples demonstrate *how to connect to AWS DSQL using a tunnel from a local laptop* (SSH or AWS SSM). They **assume** an existing DSQL cluster, an active tunnel forwarding traffic to `127.0.0.1:5432`, and appropriate AWS credentials.

**Admin usage & roles:** Some examples use the `admin` user for simplicity. **Setting up IAM roles, least-privilege permissions, production-grade access controls, or other administrative configuration is out of scope** for these examples. Do not treat the code here as guidance for role- or permission-configurations — the focus is strictly on *how to connect via a tunnel*.

**No operational guarantees:** You remain responsible for validating, securing, and hardening any code before use in real environments. Use at your own risk.

## DSQL Cluster Details

- **Cluster ID**: `a-dsql-cluster-id`
- **Private DNS Endpoint**: `a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws`
- **Connection Method**: Via localhost tunnel (127.0.0.1:5432)

## Prerequisites

Before running any of the examples, ensure you have:

1. **Existing DSQL Cluster**: The cluster `a-dsql-cluster-id` must be running and accessible
2. **VPC Endpoint Configuration**: DSQL cluster must be configured with appropriate VPC endpoints
3. **Active Tunnel**: Either SSH or AWS SSM tunnel must be established and forwarding traffic to `127.0.0.1:5432`
4. **Valid Auth Token**: A current DSQL authentication token for the `PGPASSWORD` environment variable

## Tunnel Setup Examples

**Note**: This experiment assumes you already have a working tunnel. The examples below are for reference only.

### SSH Tunnel Example
```bash
# Example SSH tunnel command (adjust for your environment)
ssh -L 5432:a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws:5432 \
    -i ~/.ssh/your-key.pem \
    ec2-user@your-bastion-host.amazonaws.com
```

### AWS SSM Tunnel Example
```bash
# Example SSM tunnel command (adjust for your environment)
aws ssm start-session \
    --target i-1234567890abcdef0 \
    --document-name AWS-StartPortForwardingSessionToRemoteHost \
    --parameters '{"host":["a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws"],"portNumber":["5432"],"localPortNumber":["5432"]}'
```

### Verifying Tunnel Connectivity
```bash
# Test if the tunnel is working
telnet 127.0.0.1 5432

# Or use netcat
nc -zv 127.0.0.1 5432

# Check if port is listening
netstat -an | grep 5432
```

## Environment Variables Setup

All implementations require these environment variables to be set:

```bash
export HOSTNAME="a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws"
export PGHOSTADDR="127.0.0.1"
export PGPASSWORD="<your-generated-dsql-token>"
export PGSSLMODE="require"
```

### Environment Variable Descriptions

- **HOSTNAME**: The private DNS endpoint of your DSQL cluster
  - Format: `{cluster-id}.dsql-{region-code}.{region}.on.aws`
  - Example: `a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws`
  - This is the actual DSQL cluster endpoint (not used directly due to tunneling)

- **PGHOSTADDR**: The localhost address where your tunnel forwards traffic
  - Always set to `127.0.0.1` for local tunnels
  - This is the address your application connects to
  - The tunnel forwards traffic from this address to the DSQL cluster

- **PGPASSWORD**: Your generated DSQL authentication token
  - Generated using AWS CLI: `aws dsql generate-db-connect-auth-token`
  - Has configurable expiration time (1-12 hours)
  - Must be regenerated when expired

- **PGSSLMODE**: SSL mode for the connection
  - Must be set to `"require"` for DSQL
  - DSQL mandates SSL/TLS encryption for all connections

### Setting Environment Variables

#### Linux/macOS (Bash/Zsh)
```bash
# Set variables for current session
export HOSTNAME="a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws"
export PGHOSTADDR="127.0.0.1"
export PGPASSWORD="your-token-here"
export PGSSLMODE="require"

# Or create a .env file and source it
echo 'export HOSTNAME="a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws"' > .env
echo 'export PGHOSTADDR="127.0.0.1"' >> .env
echo 'export PGPASSWORD="your-token-here"' >> .env
echo 'export PGSSLMODE="require"' >> .env
source .env
```

#### Windows (PowerShell)
```powershell
$env:HOSTNAME="a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws"
$env:PGHOSTADDR="127.0.0.1"
$env:PGPASSWORD="your-token-here"
$env:PGSSLMODE="require"
```

#### Windows (Command Prompt)
```cmd
set HOSTNAME=a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws
set PGHOSTADDR=127.0.0.1
set PGPASSWORD=your-token-here
set PGSSLMODE=require
```

### Generating DSQL Auth Tokens

DSQL uses temporary authentication tokens instead of traditional passwords. Generate tokens using the AWS CLI:

```bash
# Generate a token with default expiration (1 hour)
aws dsql generate-db-connect-auth-token \
    --hostname a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws \
    --region us-east-1

# Generate a token with custom expiration (up to 12 hours)
aws dsql generate-db-connect-auth-token \
    --hostname a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws \
    --region us-east-1 \
    --expires-in 3600

# Set the token directly in environment variable
export PGPASSWORD=$(aws dsql generate-db-connect-auth-token \
    --hostname a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws \
    --region us-east-1 \
    --output text)
```

**Token Requirements**:
- Valid AWS credentials with DSQL permissions
- AWS CLI version 2.13.0 or later
- Appropriate IAM permissions for DSQL cluster access

## Project Structure

```
/
├── README.md                    # This file
├── golang/                      # Go implementation
│   ├── go.mod                  # Go module configuration
│   └── main.go                 # Go source file
├── java/                        # Java implementation
│   ├── pom.xml                 # Maven configuration
│   └── src/main/java/          # Java source files
├── nodejs/                      # Node.js implementation
│   ├── package.json            # npm configuration
│   └── index.mjs               # Node.js source file (ES modules)
└── python/                      # Python implementation
    ├── requirements.txt        # Python dependencies
    └── main.py                 # Python source file
```

## Language Implementations

### Golang

See [README](golang/README.md)

- **Driver**: github.com/jackc/pgx/v5 PostgreSQL driver
- **Module System**: Go modules
- **File**: `golang/main.go`

### Java

See [README](java/README.md)

- **Driver**: PostgreSQL JDBC Driver
- **Build Tool**: Maven
- **File**: `java/src/main/java/DSQLConnectivity.java`

### Node.js

See [README](nodejs/README.md)

- **Library**: pg (node-postgres)
- **Module Format**: ES modules (.mjs)
- **File**: `nodejs/index.mjs`

### Python

See [README](python/README.md)

- **Library**: psycopg2
- **Package Manager**: pip
- **File**: `python/main.py`

## Quick Start

1. **Set up your tunnel** (SSH or SSM) to forward traffic from `127.0.0.1:5432` to your DSQL cluster
2. **Set environment variables** as described above
3. **Choose your language** and follow the specific instructions below
4. **Run the example** to test connectivity

## Expected Output

Each implementation will display connection information similar to:

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

## Troubleshooting

### Common Issues

#### "Connection refused" or "Connection timeout"
- **Cause**: Tunnel is not active or not forwarding to the correct port
- **Solution**: Verify your SSH/SSM tunnel is running and forwarding `127.0.0.1:5432`

#### "Authentication failed"
- **Cause**: Invalid or expired DSQL auth token
- **Solution**: Generate a new DSQL auth token and update the `PGPASSWORD` environment variable

#### "SNI was not received" Error
- **Cause**: SSL connection not sending proper Server Name Indication (SNI) for DSQL
- **Solution**: Ensure your implementation sends the DSQL hostname for SNI while connecting to the tunnel IP
- **Python Fix**: Use both `host` (DSQL hostname) and `hostaddr` (tunnel IP) parameters
- **Node.js Fix**: Set `ssl.servername` to the DSQL hostname in the connection configuration
- **Go Fix**: Set `config.TLSConfig.ServerName` to the DSQL hostname
- **Java Fix**: JDBC driver handles this automatically when using the correct connection URL

#### "SSL connection required"
- **Cause**: SSL mode is not set to "require"
- **Solution**: Ensure `PGSSLMODE=require` is set in your environment

#### "Environment variable not set"
- **Cause**: Required environment variables are missing
- **Solution**: Verify all four environment variables (HOSTNAME, PGHOSTADDR, PGPASSWORD, PGSSLMODE) are set

#### "Connection works but no data returned"
- **Cause**: Connection successful but query execution issues
- **Solution**: Check DSQL cluster status and permissions

### Debugging Steps

1. **Verify tunnel connectivity**:

   ```bash
   telnet 127.0.0.1 5432
   ```

2. **Check environment variables**:

   ```bash
   echo $HOSTNAME
   echo $PGHOSTADDR
   echo $PGPASSWORD
   echo $PGSSLMODE
   ```

3. **Test with psql** (if available):

   ```bash
   psql -h 127.0.0.1 -p 5432 -U admin -d postgres
   ```

4. **Verify tunnel process**:

   ```bash
   # For SSH tunnels
   ps aux | grep ssh
   
   # For SSM tunnels
   ps aux | grep session-manager-plugin
   ```

5. **Check network connectivity**:

   ```bash
   # Test if port is listening
   netstat -an | grep 5432
   
   # Test basic connectivity
   nc -zv 127.0.0.1 5432
   ```

### Language-Specific Troubleshooting

#### Java Issues

- **ClassNotFoundException**: Ensure PostgreSQL JDBC driver is in classpath
- **Maven dependency issues**: Run `mvn clean compile` to refresh dependencies
- **SSL handshake failures**: Verify `PGSSLMODE=require` is set

#### Golang Issues

- **Module not found**: Run `go mod tidy` to download dependencies
- **Import errors**: Ensure `github.com/lib/pq` driver is properly imported
- **Connection string format**: Verify PostgreSQL connection string syntax

#### Node.js Issues

- **Module resolution**: Ensure you're using `.mjs` extension for ES modules
- **Package not found**: Run `npm install` to install dependencies
- **SSL certificate errors**: Check SSL configuration in connection options

#### Python Issues

- **psycopg2 installation**: May require system dependencies (`libpq-dev` on Ubuntu)
- **Import errors**: Ensure psycopg2 is installed with `pip install -r requirements.txt`
- **Connection encoding**: Verify UTF-8 encoding support

## DSQL-Specific Considerations

### Connection Limits and Timeouts

- **60-Minute Connection Limit**: DSQL automatically closes connections after 60 minutes
- **Connection Pooling**: For production applications, implement connection pooling with proper timeout handling
- **Idle Connections**: DSQL may close idle connections before the 60-minute limit

### Authentication and Security

- **Token-Based Authentication**: DSQL uses generated tokens instead of traditional passwords
- **Token Expiration**: Auth tokens have configurable expiration times (typically 1-12 hours)
- **Token Rotation**: Implement automatic token refresh for long-running applications
- **SSL/TLS Mandatory**: All DSQL connections require SSL encryption

### Performance Characteristics

- **Cold Start Latency**: Initial connections may have higher latency
- **Query Execution**: DSQL is optimized for analytical workloads
- **Connection Overhead**: Minimize connection establishment overhead through pooling

## Important Notes

- **Connection Time Limit**: DSQL enforces a 60-minute connection time limit
- **SSL Required**: All connections must use SSL encryption
- **Tunnel Dependency**: These examples require an active tunnel; they do not establish tunnels
- **Token Expiration**: DSQL auth tokens have expiration times and may need periodic renewal
- **VPC Endpoint Required**: DSQL clusters use private endpoints and require VPC connectivity
- **Regional Availability**: Ensure your tunnel and DSQL cluster are in the same AWS region

## Security Considerations

- Never hardcode authentication tokens in source code
- Use environment variables for all sensitive configuration
- Ensure SSL/TLS encryption is enabled for all connections
- Regularly rotate DSQL authentication tokens
- Monitor connection logs for security events

## Support

Sorry, I have a life, you're on your own from here :-)


