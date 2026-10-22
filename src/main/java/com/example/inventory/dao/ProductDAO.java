package com.example.inventory.dao;

import com.example.inventory.model.Product;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {
    public Product findById(Connection connection, int productId, boolean lock) throws SQLException {
        String sql = "SELECT product_id, name, price, stock_quantity, version FROM products WHERE product_id = ?";
        if (lock) sql += " FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? map(results) : null;
            }
        }
    }

    public List<Product> findAll(Connection connection) throws SQLException {
        List<Product> products = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT product_id, name, price, stock_quantity, version FROM products ORDER BY product_id");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) products.add(map(results));
        }
        return products;
    }

    public int reduceStock(Connection connection, int productId, int quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ? AND stock_quantity >= ?")) {
            statement.setInt(1, quantity);
            statement.setInt(2, productId);
            statement.setInt(3, quantity);
            return statement.executeUpdate();
        }
    }

    public int reduceStockOptimistically(Connection connection, int productId, int quantity, long version)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity - ?, version = version + 1 " +
                "WHERE product_id = ? AND stock_quantity >= ? AND version = ?")) {
            statement.setInt(1, quantity);
            statement.setInt(2, productId);
            statement.setInt(3, quantity);
            statement.setLong(4, version);
            return statement.executeUpdate();
        }
    }

    public int naiveReduceStock(Connection connection, int productId, int quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ?")) {
            statement.setInt(1, quantity);
            statement.setInt(2, productId);
            return statement.executeUpdate();
        }
    }

    private Product map(ResultSet results) throws SQLException {
        return new Product(results.getInt("product_id"), results.getString("name"),
                results.getBigDecimal("price"), results.getInt("stock_quantity"),
                results.getLong("version"));
    }
}
