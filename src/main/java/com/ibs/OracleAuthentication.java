package com.ibs;

import java.sql.*;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class OracleAuthentication {
    public record Customer(long userId, long customerId, String username) {}
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final String dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());

    public Optional<Customer> authenticate(String username, String password) throws SQLException {
        if (username == null || password == null || username.length() > 40 ||
            password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 ||
            username.isBlank() || password.isEmpty()) return Optional.empty();
        String url = System.getenv("AUTH_DB_URL");
        String user = System.getenv("AUTH_DB_USER");
        String secret = System.getenv("AUTH_DB_PASSWORD");
        if (url == null || user == null || secret == null || secret.isBlank())
            throw new SQLException("Authentication database not configured");
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", user);
        props.setProperty("password", secret);
        props.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        props.setProperty("oracle.jdbc.ReadTimeout", "10000");
        String sql = "SELECT user_id, customer_id, username, password_hash FROM IBS_USER_APP.V_IBS_LOGIN WHERE username = ? AND role_code = 'CUSTOMER' AND account_status = 'ACTIVE'";
        Customer customer = null;
        String hash = dummyHash;
        try (Connection conn = DriverManager.getConnection(url, props);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setQueryTimeout(5);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password_hash");
                    if (stored != null && stored.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}")) {
                        hash = stored;
                        customer = new Customer(rs.getLong("user_id"), rs.getLong("customer_id"), rs.getString("username"));
                    }
                    if (rs.next()) throw new SQLException("Ambiguous authentication record");
                }
            }
        }
        boolean valid = encoder.matches(password, hash);
        return valid ? Optional.ofNullable(customer) : Optional.empty();
    }
}
