#!/usr/bin/env python3
"""
AWS DSQL Connectivity Test - Python Implementation

This script demonstrates connecting to an AWS DSQL cluster through an existing
SSH or SSM tunnel using psycopg2 PostgreSQL adapter.

Prerequisites:
- DSQL cluster with private endpoint configured
- Active SSH or SSM tunnel to the DSQL cluster
- Required environment variables set

Environment Variables:
- HOSTNAME: DSQL cluster private DNS endpoint
- PGHOSTADDR: Tunnel localhost address (127.0.0.1)
- PGPASSWORD: Generated auth token
- PGSSLMODE: SSL mode (require)
"""

import os
import sys
import re
import psycopg2
from psycopg2.extras import RealDictCursor


def validate_hostname(hostname):
    """Validate hostname format to prevent injection attacks."""
    if not re.match(r'^[a-zA-Z0-9.-]+$', hostname):
        raise ValueError(f"Invalid hostname format: {hostname}")
    if len(hostname) > 253:  # RFC 1035 limit
        raise ValueError("Hostname too long")
    return hostname


def validate_ssl_mode(sslmode):
    """Validate SSL mode to prevent injection."""
    valid_modes = {'require', 'prefer', 'allow', 'disable'}
    if sslmode not in valid_modes:
        raise ValueError(f"Invalid SSL mode: {sslmode}. Must be one of {valid_modes}")
    return sslmode


def get_connection_config():
    """Parse environment variables and build connection configuration."""
    required_vars = ['HOSTNAME', 'PGHOSTADDR', 'PGPASSWORD', 'PGSSLMODE']
    config = {}
    
    for var in required_vars:
        value = os.environ.get(var)
        if not value:
            raise ValueError(f"Required environment variable {var} is not set")
        config[var.lower()] = value
    
    # Validate inputs for security
    config['hostname'] = validate_hostname(config['hostname'])
    config['pgsslmode'] = validate_ssl_mode(config['pgsslmode'])
    
    # Build connection parameters using both host and hostaddr
    # host = DSQL hostname (for SNI)
    # hostaddr = tunnel IP address (for actual connection)
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
    
    return connection_params, config['hostname']


def execute_connection_info_query(cursor):
    """Execute connection info query equivalent to \\conninfo."""
    query = """
    SELECT 
        current_database() as database,
        current_user as user,
        version() as server_version;
    """
    
    cursor.execute(query)
    return cursor.fetchone()


def display_connection_info(conn_info, hostname):
    """Display connection information in a formatted way."""
    print("\nConnection Information:")
    print("-" * 30)
    print(f"Database: {conn_info['database']}")
    print(f"User: {conn_info['user']}")
    print(f"Host: 127.0.0.1 (via tunnel to {hostname})")
    print(f"Port: 5432")
    print(f"SSL Status: SSL connection (required by DSQL)")
    print(f"Server Version: {conn_info['server_version']}")


def main():
    """Main function to test DSQL connectivity."""
    print("AWS DSQL Connectivity Test - Python Implementation")
    print("=" * 50)
    
    try:
        # Parse environment variables and build connection configuration
        print("Reading environment variables...")
        connection_params, hostname = get_connection_config()
        print(f"Connecting to DSQL cluster: {hostname}")
        print(f"Via tunnel: {connection_params['hostaddr']}:5432")
        
        # Connect to PostgreSQL using psycopg2 with context manager
        print("\nEstablishing connection...")
        with psycopg2.connect(**connection_params) as conn:
            with conn.cursor(cursor_factory=RealDictCursor) as cursor:
                print("Connection established successfully!")
                
                # Execute connection info SQL query
                print("Executing connection info query...")
                conn_info = execute_connection_info_query(cursor)
                
                # Display results
                display_connection_info(conn_info, hostname)
                
        print("\nConnection closed successfully!")
        print("DSQL connectivity test completed.")
        
    except ValueError as e:
        print(f"Configuration Error: {e}", file=sys.stderr)
        print("\nRequired environment variables:", file=sys.stderr)
        print("- HOSTNAME: DSQL cluster private DNS endpoint", file=sys.stderr)
        print("- PGHOSTADDR: Tunnel localhost address (127.0.0.1)", file=sys.stderr)
        print("- PGPASSWORD: Generated auth token", file=sys.stderr)
        print("- PGSSLMODE: SSL mode (require)", file=sys.stderr)
        sys.exit(1)
        
    except psycopg2.OperationalError as e:
        print(f"Connection Error: {e}", file=sys.stderr)
        print("\nTroubleshooting tips:", file=sys.stderr)
        print("- Ensure SSH or SSM tunnel is active", file=sys.stderr)
        print("- Verify tunnel is forwarding to 127.0.0.1:5432", file=sys.stderr)
        print("- Check that auth token is valid and not expired", file=sys.stderr)
        sys.exit(1)
        
    except psycopg2.Error as e:
        print(f"Database Error: {e}", file=sys.stderr)
        sys.exit(1)
        
    except Exception as e:
        print(f"Unexpected Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()