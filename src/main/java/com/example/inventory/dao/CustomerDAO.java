package com.example.inventory.dao;

import com.example.inventory.model.Customer;
import java.sql.*;

public class CustomerDAO {
    public Customer findById(Connection connection, int customerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT customer_id, name, email FROM customers WHERE customer_id = ?")) {
            statement.setInt(1, customerId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? new Customer(results.getInt(1), results.getString(2), results.getString(3)) : null;
            }
        }
    }

    public int insert(Connection connection, String name, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO customers (name, email) VALUES (?, ?) RETURNING customer_id")) {
            statement.setString(1, name);
            statement.setString(2, email);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }
}
