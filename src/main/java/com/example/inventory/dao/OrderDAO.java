package com.example.inventory.dao;

import com.example.inventory.model.Order;
import com.example.inventory.model.OrderItem;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {
    public Order insert(Connection connection, int customerId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (customer_id, status) VALUES (?, ?) RETURNING order_id, order_date")) {
            statement.setInt(1, customerId);
            statement.setString(2, status);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return new Order(results.getInt("order_id"), customerId,
                        results.getTimestamp("order_date").toLocalDateTime(), status);
            }
        }
    }

    public void insertItem(Connection connection, int orderId, int productId, int quantity, BigDecimal unitPrice)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, orderId);
            statement.setInt(2, productId);
            statement.setInt(3, quantity);
            statement.setBigDecimal(4, unitPrice);
            statement.executeUpdate();
        }
    }

    public List<Order> findAll(Connection connection) throws SQLException {
        List<Order> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_id, customer_id, order_date, status FROM orders ORDER BY order_date DESC");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) orders.add(new Order(results.getInt(1), results.getInt(2),
                    results.getTimestamp(3).toLocalDateTime(), results.getString(4)));
        }
        return orders;
    }

    public List<OrderItem> findItems(Connection connection, int orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_item_id, order_id, product_id, quantity, unit_price FROM order_items WHERE order_id = ?")) {
            statement.setInt(1, orderId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) items.add(new OrderItem(results.getInt(1), results.getInt(2),
                        results.getInt(3), results.getInt(4), results.getBigDecimal(5)));
            }
        }
        return items;
    }
}
