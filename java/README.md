# Java DSQL Connectivity Implementation

This Java implementation demonstrates connecting to AWS DSQL clusters through existing SSH or SSM tunnels using the PostgreSQL JDBC driver. This implementation leverages modern Java 21 features for improved code readability and performance.

## Prerequisites

- **Java 21 or higher** (required for modern language features)
- Maven 3.9 or higher
- Active SSH or SSM tunnel to DSQL cluster
- Valid DSQL authentication token

### Java 21 Features Used

This implementation takes advantage of several Java 21 features:

- **Text Blocks**: Multi-line SQL queries with proper formatting
- **String Templates (Preview)**: STR templates for string interpolation
- **Records**: Immutable data classes for configuration
- **Pattern Matching**: Enhanced switch expressions with pattern matching
- **var keyword**: Local variable type inference
- **Enhanced try-with-resources**: Automatic resource management

## Dependencies

The project uses Maven for dependency management with the following key dependencies:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.4</version>
</dependency>
```

## Project Structure

```
java/
├── pom.xml                           # Maven configuration
├── README.md                         # This file
└── src/main/java/
    └── DSQLConnectivity.java         # Main implementation
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

### Using Maven

```bash
# Navigate to java directory
cd java

# Clean and compile (with Java 21 preview features)
mvn clean compile

# Run the application
mvn exec:java -Dexec.mainClass="DSQLConnectivity"

# Alternative: Run with explicit preview features
mvn exec:java -Dexec.mainClass="DSQLConnectivity" -Dexec.args="--enable-preview"
```

### Manual Compilation

```bash
# Compile with dependencies and preview features
mvn compile

# Run with classpath and preview features
java --enable-preview -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) DSQLConnectivity
```

### Creating Executable JAR

```bash
# Build JAR with dependencies
mvn clean package

# Run the JAR with preview features enabled
java --enable-preview -jar target/dsql-connectivity-1.0.0-jar-with-dependencies.jar
```

## Sample Output

```text
DSQL Connectivity Test - Java Implementation
Connecting to DSQL cluster...
JDBC URL: jdbc:postgresql://127.0.0.1:5432/postgres
SSL Mode: require
Target Hostname: a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws
Tunnel Address: 127.0.0.1:5432
SSL Factory: Custom DSQL SNI Factory
SSL Socket created with SNI hostname: a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws
Successfully connected to DSQL cluster!
Connection established through tunnel at 127.0.0.1:5432

=== Connection Information ===
Database: postgres
User: admin
Host: 127.0.0.1 (via tunnel to a-dsql-cluster-id.dsql-fnh4.us-east-1.on.aws)
Port: 5432
SSL Status: SSL connection (required by DSQL)
Server Version: PostgreSQL 16

Connection closed successfully.
```

## Implementation Details

### Modern Java 21 Features

This implementation showcases several Java 21 features:

#### 1. Text Blocks for SQL Queries
```java
private static final String CONNECTION_INFO_QUERY = """
    SELECT 
        current_database() as database,
        current_user as user,
        version() as server_version
    """;
```

#### 2. String Templates (Preview Feature)
```java
var jdbcUrl = STR."jdbc:postgresql://\{config.hostaddr()}:\{config.port()}/\{config.database()}";
System.out.println(STR."Database: \{rs.getString("database")}");
```

#### 3. Records for Data Classes
```java
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
        // ... more validation
    }
}
```

#### 4. Enhanced Switch Expressions with Pattern Matching
```java
sslmode = switch (sslmode) {
    case null, "" -> "require"; // Default to require SSL
    case String s when s.matches("require|prefer|allow|disable") -> s;
    default -> throw new IllegalArgumentException(STR."Invalid PGSSLMODE value: \{sslmode}");
};
```

#### 5. Local Variable Type Inference (var)
```java
var connectivity = new DSQLConnectivity();
var config = parseEnvironmentVariables();
var props = new Properties();
```

### SNI (Server Name Indication) Configuration

**SOLUTION IMPLEMENTED**: The Java implementation successfully handles DSQL's SNI requirement through a custom SSL socket factory approach.

**How it works**:
- **Custom SSL Socket Factory**: `DSQLSSLSocketFactory` extends the default SSL socket factory
- **SNI Configuration**: Uses `SSLParameters.setServerNames()` to set the correct DSQL hostname for SNI
- **Tunnel Compatibility**: Connects to `127.0.0.1` (tunnel) while sending the DSQL hostname for SNI
- **Automatic Handling**: The custom factory is automatically used by the JDBC driver

**Key Implementation Details**:
```java
// Custom SSL factory configured via JDBC properties
props.setProperty("sslfactory", "DSQLConnectivity$DSQLSSLSocketFactory");

// SNI hostname set in the custom factory
SSLParameters sslParams = sslSocket.getSSLParameters();
sslParams.setServerNames(List.of(new SNIHostName(dsqlHostname)));
sslSocket.setSSLParameters(sslParams);
```

**Result**: The Java implementation now successfully connects to DSQL through tunnels with proper SNI handling, matching the functionality of other language implementations.

### Connection Configuration

The implementation uses a modern record-based `ConnectionConfig` to manage connection parameters:

- **JDBC URL**: `jdbc:postgresql://127.0.0.1:5432/postgres` (connects to tunnel)
- **SSL Mode**: Enforced as "require" for DSQL compliance with validation
- **Connection Timeout**: 30 seconds for both connect and socket operations
- **User**: Fixed as "admin" (DSQL default)
- **Custom SSL Factory**: `DSQLSSLSocketFactory` for proper SNI handling

### Custom SSL Socket Factory

The implementation includes a custom `DSQLSSLSocketFactory` that extends the default SSL socket factory to properly handle SNI for DSQL connections:

```java
public static class DSQLSSLSocketFactory extends SSLSocketFactory {
    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        SSLSocket sslSocket = (SSLSocket) defaultFactory.createSocket(socket, host, port, autoClose);
        
        // Set SNI hostname to the DSQL hostname
        if (dsqlHostname != null) {
            SSLParameters sslParams = sslSocket.getSSLParameters();
            sslParams.setServerNames(List.of(new SNIHostName(dsqlHostname)));
            sslSocket.setSSLParameters(sslParams);
        }
        
        return sslSocket;
    }
}
```

**Key Features**:
- **SNI Support**: Automatically sets the correct hostname for SNI during SSL handshake
- **Tunnel Compatibility**: Works with tunnel connections while preserving security
- **Transparent Integration**: Seamlessly integrates with the PostgreSQL JDBC driver
- **Static Configuration**: Uses a static variable to pass the DSQL hostname to the factory

### Error Handling

The application handles several error scenarios with modern Java features:

- **Missing Environment Variables**: Validates all required variables at startup using records
- **Connection Failures**: Provides detailed error messages using string templates
- **SQL Exceptions**: Proper exception handling with try-with-resources
- **Resource Management**: Ensures connections are properly closed

### Query Execution

**IMPORTANT**: The query has been updated to work with DSQL, which doesn't support certain PostgreSQL functions:

```java
private static final String CONNECTION_INFO_QUERY = """
    SELECT 
        current_database() as database,
        current_user as user,
        version() as server_version
    """;
```

**Note**: This query was simplified because DSQL doesn't support `inet_server_addr()`, `inet_server_port()`, or `ssl_is_used()` functions.

## Troubleshooting

### Common Issues

#### Java Version Compatibility
```
Error: Preview feature STRING_TEMPLATES is disabled
```
**Solution**: Ensure you're using Java 21 with preview features enabled:
```bash
# Check Java version
java --version

# Compile with preview features
mvn clean compile

# Run with preview features
java --enable-preview -cp target/classes DSQLConnectivity
```

#### "Function not supported" Error
```
ERROR: function inet_server_addr not supported
```
**Cause**: DSQL doesn't support all PostgreSQL functions.

**Solution**: The current implementation has been updated to use DSQL-compatible queries. The query now only uses supported functions:

```java
private static final String CONNECTION_INFO_QUERY = """
    SELECT 
        current_database() as database,
        current_user as user,
        version() as server_version
    """;
```

#### ClassNotFoundException
```
Error: Could not find or load main class DSQLConnectivity
```
**Solution**: Ensure Maven compilation completed successfully:
```bash
mvn clean compile
```

#### Connection Refused
```
Connection Error: Connection to 127.0.0.1:5432 refused
```
**Solution**: Verify your SSH/SSM tunnel is active and forwarding to port 5432.

#### Authentication Failed
```
Authentication Error: password authentication failed
```
**Solution**: Generate a new DSQL auth token and update `PGPASSWORD`.

#### SNI Issues (Resolved)
The Java implementation now successfully handles SNI through a custom SSL socket factory. If you encounter SNI-related errors, ensure you're using the latest version of the implementation with the `DSQLSSLSocketFactory`.

### Maven Issues

#### Dependency Resolution
```bash
# Clear local repository and re-download
mvn dependency:purge-local-repository
mvn clean compile
```

#### Plugin Execution
```bash
# Skip tests if needed
mvn compile -DskipTests

# Verbose output for debugging
mvn compile -X
```

## Configuration Options

### JVM Options

For production use, consider these JVM options for Java 21:

```bash
java --enable-preview \
     -Xmx512m -Xms256m \
     -Dfile.encoding=UTF-8 \
     -Djava.net.preferIPv4Stack=true \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) \
     DSQLConnectivity
```

### Connection Pool Configuration

For applications requiring connection pooling, consider using HikariCP with Java 21 features:

```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
```

Example HikariCP configuration using modern Java:

```java
public record HikariConfigBuilder(
    String jdbcUrl,
    String username,
    String password,
    String sslMode
) {
    public HikariConfig build() {
        var config = new HikariConfig();
        config.setJdbcUrl(STR."jdbc:postgresql://127.0.0.1:5432/postgres");
        config.setUsername("admin");
        config.setPassword(password);
        config.addDataSourceProperty("ssl", "true");
        config.addDataSourceProperty("sslmode", sslMode);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000); // 30 minutes (less than DSQL's 60-minute limit)
        return config;
    }
}

// Usage
var configBuilder = new HikariConfigBuilder(
    "jdbc:postgresql://127.0.0.1:5432/postgres",
    "admin",
    System.getenv("PGPASSWORD"),
    "require"
);
var dataSource = new HikariDataSource(configBuilder.build());
```

## DSQL-Specific Considerations

### Connection Limits and Timeouts
- **60-Minute Connection Limit**: DSQL automatically closes connections after 60 minutes
- **Connection Pooling**: For production applications, set `maxLifetime` to less than 60 minutes
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

## Security Best Practices

- Never hardcode authentication tokens in source code
- Use environment variables for all sensitive configuration
- Implement proper connection timeout handling
- Monitor connection usage to stay within DSQL's 60-minute limit
- Regularly rotate DSQL authentication tokens
- Use prepared statements to prevent SQL injection
- Validate input parameters
- Set appropriate connection pool limits

## Testing

### Unit Testing with JUnit 5

Add JUnit dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>
```

Example test using Java 21 features:

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DSQLConnectivityTest {
    
    @Test
    void testConnectionConfigValidation() {
        // Test record validation
        assertThrows(IllegalArgumentException.class, () -> 
            new DSQLConnectivity.ConnectionConfig("", "127.0.0.1", "password", "require")
        );
        
        // Test valid configuration
        var config = new DSQLConnectivity.ConnectionConfig(
            "test-host", "127.0.0.1", "password", "require"
        );
        assertEquals("test-host", config.hostname());
        assertEquals(5432, config.port());
    }
    
    @Test
    void testSSLModeValidation() {
        // Test using modern switch expressions
        var connectivity = new DSQLConnectivity();
        
        // This would be tested by refactoring the validation logic into a separate method
        // Implementation depends on refactoring the main class
    }
}
```

### Integration Testing

For testing against actual DSQL cluster:

```java
@Test
@EnabledIfEnvironmentVariable(named = "DSQL_INTEGRATION_TEST", matches = "true")
void testDSQLConnectivity() {
    // Integration test implementation
}
```

Run tests:
```bash
# Run tests with preview features
mvn test

# Run with verbose output
mvn test -Dtest.verbose=true

# Run specific test
mvn test -Dtest=DSQLConnectivityTest
```

## Maven Configuration

Complete `pom.xml` example for Java 21:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.example</groupId>
    <artifactId>dsql-connectivity</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <postgresql.version>42.7.4</postgresql.version>
        <junit.version>5.10.1</junit.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>${postgresql.version}</version>
        </dependency>
        
        <!-- Optional: Connection pooling -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <version>5.1.0</version>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <release>21</release>
                    <compilerArgs>
                        <arg>--enable-preview</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <argLine>--enable-preview</argLine>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.1</version>
                <configuration>
                    <mainClass>DSQLConnectivity</mainClass>
                    <args>
                        <arg>--enable-preview</arg>
                    </args>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Alternative JDBC Drivers

### Using R2DBC (Reactive)

For reactive applications:

```xml
<dependency>
    <groupId>io.r2dbc</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
    <version>0.8.13.RELEASE</version>
</dependency>
```

**Note**: When using alternative drivers, ensure they properly handle SNI for DSQL connections through tunnels.
