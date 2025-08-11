# Node.js DSQL Connectivity Implementation

This Node.js implementation demonstrates connecting to AWS DSQL clusters through existing SSH or SSM tunnels using the PostgreSQL client library (pg).

## Prerequisites

- Node.js 16.0 or higher (with ES modules support)
- npm or yarn package manager
- Active SSH or SSM tunnel to DSQL cluster
- Valid DSQL authentication token

## Dependencies

The project uses the following npm packages:

```json
{
  "dependencies": {
    "pg": "^8.11.3"
  }
}
```

## Project Structure

```
nodejs/
├── package.json        # npm configuration and dependencies
├── index.mjs          # Main implementation (ES module)
└── README.md          # This file
```

## Environment Variables

Set these environment variables before running:

```bash
export HOSTNAME="a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws"
export PGHOSTADDR="127.0.0.1"
export PGPASSWORD="your-dsql-token"
export PGSSLMODE="require"
```

## Installation and Execution

### Using npm

```bash
# Navigate to nodejs directory
cd nodejs

# Install dependencies
npm install

# Run the application
node index.mjs

# Alternative: make executable and run
chmod +x index.mjs
./index.mjs
```

### Using yarn

```bash
# Install dependencies
yarn install

# Run the application
yarn start
# or
node index.mjs
```

### Development Commands

```bash
# Install development dependencies
npm install --save-dev

# Run with debugging
node --inspect index.mjs

# Run with ES module debugging
node --experimental-modules --inspect index.mjs
```

## Sample Output

```text
DSQL Connectivity Test - Node.js Implementation
================================================
Parsing environment variables...
Connecting to DSQL cluster: a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws
Via tunnel: 127.0.0.1:5432
Establishing connection...
Connection established successfully!
Executing connection info query...

Connection Information:
----------------------
Database: postgres
User: admin
Host: 127.0.0.1 (via tunnel to a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws)
Port: 5432
SSL Status: SSL connection (required by DSQL)
Server Version: PostgreSQL 16

DSQL connectivity test completed successfully!
Connection closed.
```

## Implementation Details

### SNI (Server Name Indication) Configuration

**IMPORTANT**: This implementation configures SSL with proper SNI for DSQL connections:

```javascript
function parseEnvironmentVariables() {
    // ... environment parsing ...
    
    // Configure SSL with proper SNI hostname
    const sslConfig = config.pgsslmode === 'require' ? {
        rejectUnauthorized: false,
        servername: config.hostname  // This sets the SNI hostname
    } : false;
    
    return {
        host: config.pghostaddr,  // Connect to tunnel IP
        port: 5432,
        database: 'postgres',
        user: 'admin',
        password: config.pgpassword,
        ssl: sslConfig,
        connectionTimeoutMillis: 10000,
        query_timeout: 30000
    };
}
```

**How it works:**
- `host`: Set to the tunnel IP address (127.0.0.1) for the actual TCP connection
- `ssl.servername`: Set to the actual DSQL hostname for proper SSL/SNI negotiation

This approach ensures that the SSL handshake includes the correct Server Name Indication (SNI) that DSQL requires, while the actual connection goes through the tunnel.

### ES Modules Configuration

The implementation uses ES modules (`.mjs` extension) for modern JavaScript features:

```javascript
import pkg from 'pg';
const { Client } = pkg;
```

### Error Handling

The application provides comprehensive error handling:

- **Environment Validation**: Checks for required environment variables
- **Connection Errors**: Specific handling for connection failures
- **SSL Errors**: Proper SSL configuration error messages
- **Authentication Errors**: Clear messages for auth token issues
- **Cleanup**: Ensures proper connection cleanup in all scenarios

### Query Execution

Executes DSQL-compatible connection info query using async/await:

```javascript
const query = `
    SELECT 
        current_database() as database,
        current_user as user,
        version() as server_version;
`;

const result = await client.query(query);
```

**Note**: The query has been updated to work with DSQL, which doesn't support `inet_server_addr()` and `inet_server_port()` functions.

## Troubleshooting

### Common Issues

#### "SNI was not received" Error
```
Error: unable to accept connection, sni was not received
```
**Cause**: SSL connection not sending proper Server Name Indication (SNI) for DSQL.

**Solution**: The current implementation fixes this by setting `ssl.servername` to the DSQL hostname. If you encounter this error, ensure you're using the latest version of the code.

#### Module Resolution Error
```
Error [ERR_MODULE_NOT_FOUND]: Cannot resolve module 'pg'
```
**Solution**: Install dependencies and ensure you're using `.mjs` extension:
```bash
npm install
node index.mjs
```

#### ES Module Import Error
```
SyntaxError: Cannot use import statement outside a module
```
**Solution**: Use `.mjs` extension or add `"type": "module"` to package.json.

#### Connection Refused
```
Error: connect ECONNREFUSED 127.0.0.1:5432
```
**Solution**: Verify your SSH/SSM tunnel is active and forwarding to port 5432.

#### SSL Certificate Error
```
Error: self signed certificate
```
**Solution**: The implementation uses `rejectUnauthorized: false` for tunneled connections.

#### Authentication Failed
```
Error: password authentication failed for user "admin"
```
**Solution**: Generate a new DSQL auth token and update `PGPASSWORD`.

### Node.js Specific Issues

#### Version Compatibility
```bash
# Check Node.js version
node --version

# Update Node.js if needed (using nvm)
nvm install 18
nvm use 18
```

#### Package Installation Issues
```bash
# Clear npm cache
npm cache clean --force

# Delete node_modules and reinstall
rm -rf node_modules package-lock.json
npm install
```

## Configuration Options

### Connection Pooling

For production applications, use connection pooling with proper SNI configuration:

```javascript
import pkg from 'pg';
const { Pool } = pkg;

const hostname = process.env.HOSTNAME;
const sslConfig = {
    rejectUnauthorized: false,
    servername: hostname  // SNI hostname
};

const pool = new Pool({
    host: '127.0.0.1',          // Tunnel IP
    port: 5432,
    database: 'postgres',
    user: 'admin',
    password: process.env.PGPASSWORD,
    ssl: sslConfig,
    max: 20,                    // Maximum connections
    idleTimeoutMillis: 30000,   // Close idle connections after 30s
    connectionTimeoutMillis: 2000,
});
```

### Environment Configuration

Using dotenv for environment management:

```bash
npm install dotenv
```

```javascript
import dotenv from 'dotenv';
dotenv.config();
```

### Logging Configuration

Add structured logging:

```bash
npm install winston
```

```javascript
import winston from 'winston';

const logger = winston.createLogger({
    level: 'info',
    format: winston.format.json(),
    transports: [
        new winston.transports.Console(),
        new winston.transports.File({ filename: 'dsql-connectivity.log' })
    ]
});
```

## Performance Considerations

- **Connection Pooling**: Use `Pool` instead of `Client` for multiple queries
- **Query Timeouts**: Set appropriate timeout values
- **Memory Management**: Properly close connections and clean up resources
- **Async/Await**: Use modern async patterns for better performance
- **DSQL Connection Limits**: Remember DSQL's 60-minute connection time limit

## Security Best Practices

- Never hardcode authentication tokens
- Use environment variables for sensitive configuration
- Implement proper SSL/TLS configuration with SNI
- Set connection timeouts to prevent hanging connections
- Monitor connection duration (DSQL 60-minute limit)
- Validate input parameters
- Use parameterized queries to prevent SQL injection

## DSQL-Specific Considerations

### Connection Limits and Timeouts
- **60-Minute Connection Limit**: DSQL automatically closes connections after 60 minutes
- **Connection Pooling**: For production applications, implement connection pooling with proper timeout handling
- **Idle Connections**: DSQL may close idle connections before the 60-minute limit

### Authentication and Security
- **Token-Based Authentication**: DSQL uses generated tokens instead of traditional passwords
- **Token Expiration**: Auth tokens have configurable expiration times (typically 1-12 hours)
- **Token Rotation**: Implement automatic token refresh for long-running applications
- **SSL/TLS Mandatory**: All DSQL connections require SSL encryption with proper SNI

### Performance Characteristics
- **Cold Start Latency**: Initial connections may have higher latency
- **Query Execution**: DSQL is optimized for analytical workloads
- **Connection Overhead**: Minimize connection establishment overhead through pooling

## Testing

### Unit Testing with Jest

```bash
npm install --save-dev jest
```

Create test file `index.test.mjs`:

```javascript
import { jest } from '@jest/globals';

describe('DSQL Connectivity', () => {
    test('should parse environment variables correctly', () => {
        // Mock environment variables
        process.env.HOSTNAME = 'test-host';
        process.env.PGHOSTADDR = '127.0.0.1';
        process.env.PGPASSWORD = 'test-token';
        process.env.PGSSLMODE = 'require';
        
        // Test parseEnvironmentVariables function
        // Implementation depends on your code structure
    });
});
```

### Integration Testing

For testing against actual DSQL cluster:

```javascript
describe('DSQL Integration Tests', () => {
    test('should connect to DSQL cluster', async () => {
        // Test implementation
    });
});
```

Run tests:
```bash
npm test
```

## Package.json Configuration

Example complete `package.json`:

```json
{
  "name": "dsql-connectivity-nodejs",
  "version": "1.0.0",
  "description": "Node.js DSQL connectivity experiment using PostgreSQL driver through SSH/SSM tunnel",
  "main": "index.mjs",
  "type": "module",
  "scripts": {
    "start": "node index.mjs",
    "test": "jest",
    "dev": "node --inspect index.mjs"
  },
  "dependencies": {
    "pg": "^8.11.3"
  },
  "devDependencies": {
    "jest": "^29.0.0",
    "dotenv": "^16.0.0",
    "winston": "^3.8.0"
  },
  "engines": {
    "node": ">=16.0.0"
  },
  "keywords": [
    "dsql",
    "postgresql",
    "aws",
    "connectivity",
    "tunnel"
  ],
  "author": "",
  "license": "MIT"
}
```

## Alternative PostgreSQL Libraries

### Using node-postgres with TypeScript

```bash
npm install --save-dev typescript @types/node @types/pg
```

```typescript
import { Client } from 'pg';

interface ConnectionConfig {
    host: string;
    port: number;
    database: string;
    user: string;
    password: string;
    ssl: {
        rejectUnauthorized: boolean;
        servername: string;  // SNI hostname
    };
}

const config: ConnectionConfig = {
    host: '127.0.0.1',  // Tunnel IP
    port: 5432,
    database: 'postgres',
    user: 'admin',
    password: process.env.PGPASSWORD!,
    ssl: {
        rejectUnauthorized: false,
        servername: process.env.HOSTNAME!  // DSQL hostname for SNI
    }
};
```

### Using Prisma (ORM)

```bash
npm install prisma @prisma/client
```

**Note**: When using ORMs like Prisma with DSQL, ensure the underlying connection configuration includes proper SNI settings. The direct `pg` library approach shown in this implementation provides the most control over SNI configuration.
