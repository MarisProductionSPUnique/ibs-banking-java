package com.ibs;

import java.sql.*;

public class OracleProbe {
    public static void main(String[] args) {
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        if (user == null || password == null) {
            System.out.println("IBS Oracle probe: credentials not configured");
            return;
        }
        try {
            DriverManager.setLoginTimeout(10);
            try (Connection c = DriverManager.getConnection(
                    "jdbc:oracle:thin:@//127.0.0.1:11521/XEPDB1", user, password);
                 Statement s = c.createStatement()) {
                s.setQueryTimeout(10);
                try (ResultSet r = s.executeQuery("SELECT USER, (SELECT COUNT(*) FROM user_tables) FROM dual")) {
                    if (r.next()) System.out.println("IBS Oracle probe: CONNECTED user=" + r.getString(1) + " tables=" + r.getInt(2));
                }
            }
        } catch (SQLException e) {
            System.out.println("IBS Oracle probe: FAILED SQLState=" + e.getSQLState() + " code=" + e.getErrorCode());
        }
    }
}
