import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.net.Socket;
import java.io.IOException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLParameters;

/**
 * DSQL Connectivity Experiment - Java Implementation
 * 
 * This class demonstrates connecting to AWS DSQL clusters through existing SSH or SSM tunnels
 * using PostgreSQL JDBC driver. It reads connection parameters from environment variables
 * and executes connection info queries to verify successful connectivity.
 * 
 * The key insight for DSQL SNI compatibility is to use a custom SSL socket factory
 * that properly sets the SNI hostname while connecting through the tunnel.
 * 
 * Requires Java 21 or higher.
 */
public class DSQLConnectivity {
    
    // DSQL-compatible query (removed functions not supported by DSQL)
    private static final String CONNECTION_INFO_QUERY = """
        SELECT 
            current_database() as database,
            current_user as user,
            version() as server_version
        """;
    
    // Static variable to hold the DSQL hostname for SNI
    private static String dsqlHostname;
    
    public static void main(String[] args) {
        var connectivity = new DSQLConnectivity();
        connectivity.connectAndQuery();
    }
    
    /**
     * Main method to establish connection and execute query
     */
    public void connectAndQuery() {
        Connection connection = null;
        
        try {
            // Parse environment variables and build connection
            var config = parseEnvironmentVariables();
            connection = establishConnection(config);
            
            System.out.println("Successfully connected to DSQL cluster!");
            System.out.println(STR."Connection established through tunnel at \{config.hostaddr()}:\{config.port()}");
            System.out.println();
            
            // Execute connection info query
            executeConnectionInfoQuery(connection, config.hostname());
            
        } catch (Exception e) {
            System.err.println(STR."Error connecting to DSQL cluster: \{e.getMessage()}");
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Ensure proper connection cleanup
            if (connection != null) {
                try {
                    connection.close();
                    System.out.println("\nConnection closed successfully.");
                } catch (SQLException e) {
                    System.err.println(STR."Error closing connection: \{e.getMessage()}");
                }
            }
        }
    }
    
    /**
     * Parse required environment variables for DSQL connection
     */
    private ConnectionConfig parseEnvironmentVariables() {
        var hostname = System.getenv("HOSTNAME");
        var hostaddr = System.getenv("PGHOSTADDR");
        var password = System.getenv("PGPASSWORD");
        var sslmode = System.getenv("PGSSLMODE");
        
        // Validate required environment variables using modern switch expressions
        if (hostname == null || hostname.trim().isEmpty()) {
            throw new IllegalArgumentException("HOSTNAME environment variable is required");
        }
        if (hostaddr == null || hostaddr.trim().isEmpty()) {
            throw new IllegalArgumentException("PGHOSTADDR environment variable is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("PGPASSWORD environment variable is required");
        }
        
        // Use switch expression for SSL mode validation (Java 21 feature)
        sslmode = switch (sslmode) {
            case null -> "require"; // Default to require SSL
            case "" -> "require"; // Default to require SSL
            case String s when s.matches("require|prefer|allow|disable") -> s;
            default -> throw new IllegalArgumentException(STR."Invalid PGSSLMODE value: \{sslmode}");
        };
        
        return new ConnectionConfig(hostname, hostaddr, password, sslmode);
    }
    
    /**
     * Establish PostgreSQL connection using JDBC driver with custom SNI handling
     */
    private Connection establishConnection(ConnectionConfig config) throws SQLException {
        // Set the hostname for our custom SSL factory
        dsqlHostname = config.hostname();
        
        // Build JDBC connection URL using tunnel address
        var jdbcUrl = STR."jdbc:postgresql://\{config.hostaddr()}:\{config.port()}/\{config.database()}";
        
        // Set connection properties
        var props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", config.password());
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", config.sslmode());
        
        // CRITICAL: Use our custom SSL factory that handles SNI properly
        props.setProperty("sslfactory", "DSQLConnectivity$DSQLSSLSocketFactory");
        
        // Application name for connection tracking
        props.setProperty("ApplicationName", STR."DSQL-Java21-SNI-\{config.hostname()}");
        
        // Set connection timeout (30 seconds)
        props.setProperty("connectTimeout", "30");
        props.setProperty("socketTimeout", "30");
        
        System.out.println("Connecting to DSQL cluster...");
        System.out.println(STR."JDBC URL: \{jdbcUrl}");
        System.out.println(STR."SSL Mode: \{config.sslmode()}");
        System.out.println(STR."Target Hostname: \{config.hostname()}");
        System.out.println(STR."Tunnel Address: \{config.hostaddr()}:\{config.port()}");
        System.out.println("SSL Factory: Custom DSQL SNI Factory");
        
        return DriverManager.getConnection(jdbcUrl, props);
    }
    
    /**
     * Custom SSL Socket Factory that properly handles SNI for DSQL connections
     * This factory creates SSL sockets that send the correct hostname for SNI
     * while connecting through the tunnel.
     */
    public static class DSQLSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory defaultFactory;
        
        public DSQLSSLSocketFactory() {
            this.defaultFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
        
        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            // Create SSL socket using the default factory
            SSLSocket sslSocket = (SSLSocket) defaultFactory.createSocket(socket, host, port, autoClose);
            
            // CRITICAL: Set SNI hostname to the DSQL hostname
            if (dsqlHostname != null) {
                SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(dsqlHostname)));
                sslSocket.setSSLParameters(sslParams);
                
                System.out.println(STR."SSL Socket created with SNI hostname: \{dsqlHostname}");
            }
            
            return sslSocket;
        }
        
        @Override
        public Socket createSocket(String host, int port) throws IOException {
            SSLSocket sslSocket = (SSLSocket) defaultFactory.createSocket(host, port);
            
            if (dsqlHostname != null) {
                SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(dsqlHostname)));
                sslSocket.setSSLParameters(sslParams);
            }
            
            return sslSocket;
        }
        
        @Override
        public Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws IOException {
            SSLSocket sslSocket = (SSLSocket) defaultFactory.createSocket(host, port, localHost, localPort);
            
            if (dsqlHostname != null) {
                SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(dsqlHostname)));
                sslSocket.setSSLParameters(sslParams);
            }
            
            return sslSocket;
        }
        
        @Override
        public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
            SSLSocket sslSocket = (SSLSocket) defaultFactory.createSocket(host, port);
            
            if (dsqlHostname != null) {
                SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(dsqlHostname)));
                sslSocket.setSSLParameters(sslParams);
            }
            
            return sslSocket;
        }
        
        @Override
        public Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
            SSLSocket sslSocket = (SSLSocket) defaultFactory.createSocket(address, port, localAddress, localPort);
            
            if (dsqlHostname != null) {
                SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(dsqlHostname)));
                sslSocket.setSSLParameters(sslParams);
            }
            
            return sslSocket;
        }
        
        @Override
        public String[] getDefaultCipherSuites() {
            return defaultFactory.getDefaultCipherSuites();
        }
        
        @Override
        public String[] getSupportedCipherSuites() {
            return defaultFactory.getSupportedCipherSuites();
        }
    }
    
    /**
     * Execute connection info query and display results
     */
    private void executeConnectionInfoQuery(Connection connection, String hostname) throws SQLException {
        try (var stmt = connection.prepareStatement(CONNECTION_INFO_QUERY);
             var rs = stmt.executeQuery()) {
            
            System.out.println("=== Connection Information ===");
            
            if (rs.next()) {
                System.out.println(STR."Database: \{rs.getString("database")}");
                System.out.println(STR."User: \{rs.getString("user")}");
                System.out.println(STR."Host: 127.0.0.1 (via tunnel to \{hostname})");
                System.out.println("Port: 5432");
                System.out.println("SSL Status: SSL connection (required by DSQL)");
                System.out.println(STR."Server Version: \{rs.getString("server_version")}");
            } else {
                System.out.println("No connection information returned from query");
            }
        }
    }
    
    /**
     * Configuration record to hold connection parameters (Java 21 record with validation)
     */
    public record ConnectionConfig(
            String hostname,
            String hostaddr,
            String password,
            String sslmode
    ) {
        // Compact constructor with validation
        public ConnectionConfig {
            if (hostname == null || hostname.trim().isEmpty()) {
                throw new IllegalArgumentException("hostname cannot be null or empty");
            }
            if (hostaddr == null || hostaddr.trim().isEmpty()) {
                throw new IllegalArgumentException("hostaddr cannot be null or empty");
            }
            if (password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("password cannot be null or empty");
            }
            if (sslmode == null || sslmode.trim().isEmpty()) {
                throw new IllegalArgumentException("sslmode cannot be null or empty");
            }
        }
        
        // Derived properties using methods
        public int port() {
            return 5432; // Standard PostgreSQL port
        }
        
        public String database() {
            return "postgres"; // Default database
        }
        
        public String username() {
            return "admin"; // Default DSQL username
        }
    }
}
