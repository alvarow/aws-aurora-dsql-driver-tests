# Python DSQL Connectivity Implementation

This Python implementation demonstrates connecting to AWS DSQL clusters through existing SSH or SSM tunnels using the psycopg2 PostgreSQL adapter.

## Prerequisites

- Python 3.8 or higher
- uv (recommended) or pip package manager
- Active SSH or SSM tunnel to DSQL cluster
- Valid DSQL authentication token

### Installing uv

uv is a fast Python package installer and resolver, written in Rust. It's significantly faster than pip and provides better dependency resolution.

```bash
# Install uv
curl -LsSf https://astral.sh/uv/install.sh | sh

# On macOS with Homebrew
brew install uv

# On Windows with PowerShell
powershell -c "irm https://astral.sh/uv/install.ps1 | iex"

# Verify installation
uv --version
```

## Dependencies

The project uses the following Python packages:

```
psycopg2-binary>=2.9.7
```

## Project Structure

```
python/
├── requirements.txt    # Python dependencies
├── main.py            # Main implementation
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

### Using uv (Recommended)

[uv](https://github.com/astral-sh/uv) is a fast Python package installer and resolver, written in Rust. It's significantly faster than pip and handles virtual environments automatically.

```bash
# Install uv if not already installed
curl -LsSf https://astral.sh/uv/install.sh | sh
# or on macOS: brew install uv
# or on Windows: powershell -c "irm https://astral.sh/uv/install.ps1 | iex"

# Navigate to python directory
cd python

# Create virtual environment and install dependencies (one command!)
uv venv
uv pip install -r requirements.txt

# Activate virtual environment
# On Linux/macOS:
source .venv/bin/activate
# On Windows:
.venv\Scripts\activate

# Run the application
python main.py

# Alternative: Run directly with uv (automatically uses venv)
uv run python main.py

# Alternative: make executable and run
chmod +x main.py
./main.py
```

### Using uv with automatic dependency management

```bash
# Install and run in one command (uv handles venv automatically)
uv run --with psycopg2-binary python main.py

# Or install dependencies and run
uv add psycopg2-binary
uv run python main.py
```

### Using Traditional pip (Alternative)

```bash
# Navigate to python directory
cd python

# Install dependencies
pip install -r requirements.txt

# Run the application
python main.py
```

### Using Virtual Environment with pip (Alternative)

```bash
# Create virtual environment
python -m venv venv

# Activate virtual environment
# On Linux/macOS:
source venv/bin/activate
# On Windows:
venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run the application
python main.py

# Deactivate when done
deactivate
```

### Using Poetry (Alternative)

```bash
# Install poetry if not already installed
pip install poetry

# Install dependencies
poetry install

# Run the application
poetry run python main.py
```

## Sample Output

```text
AWS DSQL Connectivity Test - Python Implementation
==================================================
Reading environment variables...
Connecting to DSQL cluster: a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws
Via tunnel: 127.0.0.1:5432

Establishing connection...
Connection established successfully!
Executing connection info query...

Connection Information:
------------------------------
Database: postgres
User: admin
Host: 127.0.0.1 (via tunnel to a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws)
Port: 5432
SSL Status: SSL connection (required by DSQL)
Server Version: PostgreSQL 16

Connection closed successfully!
DSQL connectivity test completed.
```

## Implementation Details

### SNI (Server Name Indication) Configuration

**IMPORTANT**: This implementation uses both `host` and `hostaddr` parameters to properly handle SNI for DSQL connections:

```python
connection_params = {
    'host': config['hostname'],      # DSQL hostname for SNI
    'hostaddr': config['pghostaddr'], # Tunnel IP for connection
    'port': 5432,
    'database': 'postgres',
    'user': 'admin',
    'password': config['pgpassword'],
    'sslmode': config['pgsslmode'],
    'connect_timeout': 30
}
```

**How it works:**
- `host`: Set to the actual DSQL hostname for proper SSL/SNI negotiation
- `hostaddr`: Set to the tunnel IP address (127.0.0.1) for the actual TCP connection

This approach ensures that the SSL handshake includes the correct Server Name Indication (SNI) that DSQL requires, while the actual connection goes through the tunnel.

### Connection Management

The implementation uses psycopg2 with context managers for proper resource management:

```python
with psycopg2.connect(**connection_params) as conn:
    with conn.cursor(cursor_factory=RealDictCursor) as cursor:
        # Database operations
```

### Error Handling

Comprehensive error handling for various scenarios:

- **Environment Variables**: Validates all required variables
- **Connection Errors**: Specific handling for psycopg2.OperationalError
- **Database Errors**: General psycopg2.Error handling
- **Unexpected Errors**: Catches all other exceptions
- **Resource Cleanup**: Automatic cleanup with context managers

### Query Execution

Uses RealDictCursor for dictionary-like result access and DSQL-compatible queries:

```python
query = """
SELECT 
    current_database() as database,
    current_user as user,
    version() as server_version;
"""

cursor.execute(query)
result = cursor.fetchone()
```

**Note**: The query has been updated to work with DSQL, which doesn't support `inet_server_addr()` and `inet_server_port()` functions.

## Troubleshooting

### Common Issues

#### "SNI was not received" Error
```
FATAL: unable to accept connection, sni was not received
```
**Cause**: SSL connection not sending proper Server Name Indication (SNI) for DSQL.

**Solution**: The current implementation fixes this by using both `host` (DSQL hostname) and `hostaddr` (tunnel IP) parameters. If you encounter this error, ensure you're using the latest version of the code.

#### psycopg2 Installation Error
```
Error: pg_config executable not found
```
**Solution**: Install system dependencies:
```bash
# Ubuntu/Debian
sudo apt-get install libpq-dev python3-dev

# CentOS/RHEL
sudo yum install postgresql-devel python3-devel

# macOS
brew install postgresql

# Or use binary version with uv
uv pip install psycopg2-binary

# Or with pip
pip install psycopg2-binary
```

#### Connection Refused
```
psycopg2.OperationalError: could not connect to server: Connection refused
```
**Solution**: Verify your SSH/SSM tunnel is active and forwarding to port 5432.

#### SSL Connection Error
```
psycopg2.OperationalError: SSL connection has been closed unexpectedly
```
**Solution**: Ensure `PGSSLMODE=require` is set and tunnel supports SSL.

#### Authentication Failed
```
psycopg2.OperationalError: FATAL: password authentication failed for user "admin"
```
**Solution**: Generate a new DSQL auth token and update `PGPASSWORD`.

#### Import Error
```
ModuleNotFoundError: No module named 'psycopg2'
```
**Solution**: Install dependencies:
```bash
# With uv (recommended)
uv pip install -r requirements.txt

# Or with pip
pip install -r requirements.txt
```

### Python-Specific Issues

#### Virtual Environment Issues
```bash
# Recreate virtual environment with uv (recommended)
rm -rf .venv
uv venv
uv pip install -r requirements.txt

# Or with traditional approach
rm -rf venv
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

#### Package Version Conflicts
```bash
# Check installed packages with uv
uv pip list

# Upgrade uv itself
curl -LsSf https://astral.sh/uv/install.sh | sh

# Install specific version with uv
uv pip install psycopg2-binary==2.9.7

# Or with traditional pip
pip list
pip install --upgrade pip
pip install psycopg2-binary==2.9.7
```

## Configuration Options

### Connection Pooling

For production applications, use connection pooling:

```python
from psycopg2 import pool

# Create connection pool
connection_pool = psycopg2.pool.SimpleConnectionPool(
    1, 20,  # min and max connections
    host=hostname,           # DSQL hostname for SNI
    hostaddr='127.0.0.1',    # Tunnel IP for connection
    port=5432,
    database='postgres',
    user='admin',
    password=os.environ['PGPASSWORD'],
    sslmode='require'
)

# Get connection from pool
conn = connection_pool.getconn()
try:
    # Use connection
    pass
finally:
    # Return connection to pool
    connection_pool.putconn(conn)
```

### Environment Configuration

Using python-dotenv for environment management:

```bash
# Install with uv
uv pip install python-dotenv

# Or with pip
pip install python-dotenv
```

```python
from dotenv import load_dotenv
load_dotenv()
```

### Logging Configuration

Add structured logging:

```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('dsql-connectivity.log'),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)
```

## Performance Considerations

- **Connection Pooling**: Use connection pools for multiple operations
- **Context Managers**: Ensure proper resource cleanup
- **Prepared Statements**: Use for repeated queries
- **Batch Operations**: Use executemany() for bulk operations
- **Memory Management**: Be mindful of large result sets
- **DSQL Connection Limits**: Remember DSQL's 60-minute connection time limit

## Security Best Practices

- Never hardcode authentication tokens in source code
- Use environment variables for sensitive configuration
- Implement proper SSL/TLS configuration with SNI
- Set connection timeouts to prevent hanging connections
- Monitor connection duration (DSQL 60-minute limit)
- Use parameterized queries to prevent SQL injection
- Validate input parameters
- Regularly rotate DSQL authentication tokens

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

### Unit Testing with pytest

```bash
# Install with uv
uv pip install pytest pytest-mock

# Or with pip
pip install pytest pytest-mock
```

Create test file `test_main.py`:

```python
import pytest
from unittest.mock import patch, MagicMock
import main

def test_get_connection_config():
    with patch.dict('os.environ', {
        'HOSTNAME': 'test-host',
        'PGHOSTADDR': '127.0.0.1',
        'PGPASSWORD': 'test-token',
        'PGSSLMODE': 'require'
    }):
        config, hostname = main.get_connection_config()
        assert config['host'] == 'test-host'  # SNI hostname
        assert config['hostaddr'] == '127.0.0.1'  # Tunnel IP
        assert hostname == 'test-host'
```

### Integration Testing

For testing against actual DSQL cluster:

```python
import pytest

@pytest.mark.integration
def test_dsql_connectivity():
    # Test implementation
    pass
```

Run tests:
```bash
# Run all tests with uv
uv run pytest

# Run only unit tests
uv run pytest -m "not integration"

# Run with coverage
uv run pytest --cov=main

# Or with traditional approach
pytest
pytest -m "not integration"
pytest --cov=main
```

## Requirements.txt

Complete requirements file:

```
psycopg2-binary>=2.9.7
python-dotenv>=1.0.0
pytest>=7.0.0
pytest-mock>=3.10.0
pytest-cov>=4.0.0
```

### Installing all requirements

```bash
# With uv (recommended)
uv pip install -r requirements.txt

# Or with pip
pip install -r requirements.txt
```

## Alternative PostgreSQL Adapters

### Using psycopg3 (newer version)

```bash
# Install with uv
uv pip install psycopg[binary]

# Or with pip
pip install psycopg[binary]
```

```python
import psycopg

# Note: psycopg3 also supports host/hostaddr for SNI
connection_params = {
    'host': hostname,        # DSQL hostname for SNI
    'hostaddr': '127.0.0.1', # Tunnel IP for connection
    # ... other params
}

with psycopg.connect(**connection_params) as conn:
    with conn.cursor() as cursor:
        cursor.execute(query)
        result = cursor.fetchone()
```

### Using asyncpg (async support)

```bash
# Install with uv
uv pip install asyncpg

# Or with pip
pip install asyncpg
```

**Note**: asyncpg may require different SNI configuration. For DSQL compatibility, psycopg2 with the host/hostaddr approach is recommended.
