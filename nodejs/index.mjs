#!/usr/bin/env node

/**
 * DSQL Connectivity Test - Node.js Implementation
 * 
 * This script demonstrates connecting to AWS DSQL clusters through existing SSH or SSM tunnels
 * using the PostgreSQL client library (pg). It reads connection parameters from environment
 * variables and executes connection info queries to verify connectivity.
 * 
 * Required Environment Variables:
 * - HOSTNAME: DSQL cluster private DNS endpoint
 * - PGHOSTADDR: Tunnel localhost address (127.0.0.1)
 * - PGPASSWORD: Generated auth token
 * - PGSSLMODE: SSL mode (require)
 */

import pkg from 'pg';
const { Client } = pkg;

/**
 * Parse and validate environment variables
 */
function parseEnvironmentVariables() {
    const requiredVars = ['HOSTNAME', 'PGHOSTADDR', 'PGPASSWORD', 'PGSSLMODE'];
    const config = {};
    
    for (const varName of requiredVars) {
        const value = process.env[varName];
        if (!value) {
            throw new Error(`Missing required environment variable: ${varName}`);
        }
        config[varName.toLowerCase()] = value;
    }
    
    // Configure SSL with proper SNI hostname
    // Note: For tunnel connections, we may need to disable strict certificate validation
    // while still maintaining SNI for DSQL compatibility
    const sslConfig = config.pgsslmode === 'require' ? {
        rejectUnauthorized: true,   // Required for tunnel connections
        servername: config.hostname  // This sets the SNI hostname for DSQL
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

/**
 * Execute connection info query equivalent to \conninfo
 */
async function executeConnectionInfoQuery(client) {
    const query = `
        SELECT 
            current_database() as database,
            current_user as user,
            version() as server_version;
    `;
    
    const result = await client.query(query);
    return result.rows[0];
}

/**
 * Display connection information in a formatted way
 */
function displayConnectionInfo(info, hostname) {
    console.log('\nConnection Information:');
    console.log('----------------------');
    console.log(`Database: ${info.database}`);
    console.log(`User: ${info.user}`);
    console.log(`Host: 127.0.0.1 (via tunnel to ${hostname})`);
    console.log(`Port: 5432`);
    console.log(`SSL Status: SSL connection (required by DSQL)`);
    console.log(`Server Version: ${info.server_version}`);
}

/**
 * Main function to test DSQL connectivity
 */
async function main() {
    console.log('DSQL Connectivity Test - Node.js Implementation');
    console.log('================================================');
    
    let client = null;
    
    try {
        // Parse environment variables
        console.log('Parsing environment variables...');
        const config = parseEnvironmentVariables();
        const hostname = process.env.HOSTNAME;
        console.log(`Connecting to DSQL cluster: ${hostname}`);
        console.log(`Via tunnel: ${config.host}:${config.port}`);
        
        // Create PostgreSQL client
        client = new Client(config);
        
        // Connect to database
        console.log('Establishing connection...');
        await client.connect();
        console.log('Connection established successfully!');
        
        // Execute connection info query
        console.log('Executing connection info query...');
        const connectionInfo = await executeConnectionInfoQuery(client);
        
        // Display results
        displayConnectionInfo(connectionInfo, hostname);
        
        console.log('\nDSQL connectivity test completed successfully!');
        
    } catch (error) {
        console.error('\nError during DSQL connectivity test:');
        
        if (error.message.includes('Missing required environment variable')) {
            console.error(`Configuration Error: ${error.message}`);
            console.error('\nRequired environment variables:');
            console.error('- HOSTNAME: DSQL cluster private DNS endpoint');
            console.error('- PGHOSTADDR: Tunnel localhost address (127.0.0.1)');
            console.error('- PGPASSWORD: Generated auth token');
            console.error('- PGSSLMODE: SSL mode (require)');
        } else if (error.code === 'ECONNREFUSED') {
            console.error('Connection Error: Unable to connect to database');
            console.error('Please ensure your SSH/SSM tunnel is active and accessible at 127.0.0.1:5432');
        } else if (error.code === 'ENOTFOUND') {
            console.error('DNS Error: Unable to resolve hostname');
            console.error('Please check your HOSTNAME environment variable');
        } else if (error.message.includes('authentication')) {
            console.error('Authentication Error: Invalid credentials');
            console.error('Please check your PGPASSWORD environment variable');
        } else {
            console.error(`Unexpected Error: ${error.message}`);
            console.error('Stack trace:', error.stack);
        }
        
        process.exit(1);
        
    } finally {
        // Cleanup: Close connection
        if (client) {
            try {
                await client.end();
                console.log('Connection closed.');
            } catch (cleanupError) {
                console.error('Error closing connection:', cleanupError.message);
            }
        }
    }
}

// Execute main function
main().catch(console.error);
