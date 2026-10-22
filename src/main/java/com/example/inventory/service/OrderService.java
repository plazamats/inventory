package com.example.inventory.service;

import com.example.inventory.Database;
import com.example.inventory.dao.CustomerDAO;
import com.example.inventory.dao.OrderDAO;
import com.example.inventory.dao.ProductDAO;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.model.Order;
import com.example.inventory.model.Product;
import com.example.inventory.strategy.ConcurrencyStrategy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderService {
    private final Database database;
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private int retryCount;

    public OrderService(Database database) {
        this.database = database;
    }

    public synchronized int getRetryCount() {
        return retryCount;
    }

    public Order placeOrder(int customerId, Map<Integer, Integer> requestedItems, ConcurrencyStrategy strategy)
            throws SQLException, InsufficientStockException {
        validateItems(requestedItems);
        int attempts = strategy == ConcurrencyStrategy.OPTIMISTIC ? 3 : 1;
        SQLException lastSerializationFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Order order = placeOrderAttempt(customerId, requestedItems, strategy);
                if (attempt > 1) {
                    synchronized (this) {
                        retryCount += attempt - 1;
                    }
                }
                return order;
            } catch (SQLException exception) {
                if (strategy == ConcurrencyStrategy.OPTIMISTIC && isSerializationFailure(exception)
                        && attempt < attempts) {
                    synchronized (this) {
                        retryCount++;
                    }
                    lastSerializationFailure = exception;
                    continue;
                }
                throw exception;
            }
        }
        throw lastSerializationFailure;
    }

    private Order placeOrderAttempt(int customerId, Map<Integer, Integer> requestedItems,
                                    ConcurrencyStrategy strategy)
            throws SQLException, InsufficientStockException {
        Connection connection = null;
        boolean originalAutoCommit = true;
        try {
            connection = database.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(strategy == ConcurrencyStrategy.OPTIMISTIC
                    ? Connection.TRANSACTION_REPEATABLE_READ : Connection.TRANSACTION_READ_COMMITTED);
            if (customerDAO.findById(connection, customerId) == null) {
                throw new SQLException("Customer does not exist: " + customerId);
            }
            List<Integer> productIds = new ArrayList<>(requestedItems.keySet());
            Collections.sort(productIds);
            Map<Integer, Product> products = new LinkedHashMap<>();
            for (int productId : productIds) {
                Product product = productDAO.findById(connection, productId,
                        strategy == ConcurrencyStrategy.PESSIMISTIC);
                if (product == null) throw new SQLException("Product does not exist: " + productId);
                products.put(productId, product);
            }
            for (int productId : productIds) {
                int quantity = requestedItems.get(productId);
                Product product = products.get(productId);
                if (strategy == ConcurrencyStrategy.PESSIMISTIC) {
                    if (product.getStockQuantity() < quantity) {
                        throw new InsufficientStockException("Insufficient stock for product " + productId);
                    }
                    if (productDAO.reduceStock(connection, productId, quantity) != 1) {
                        throw new SQLException("Stock update failed for product " + productId);
                    }
                } else if (strategy == ConcurrencyStrategy.ATOMIC_UPDATE) {
                    if (productDAO.reduceStock(connection, productId, quantity) != 1) {
                        throw new InsufficientStockException("Insufficient stock for product " + productId);
                    }
                } else if (productDAO.reduceStockOptimistically(connection, productId, quantity,
                        product.getVersion()) != 1) {
                    Product current = productDAO.findById(connection, productId, false);
                    if (current == null || current.getStockQuantity() < quantity) {
                        throw new InsufficientStockException("Insufficient stock for product " + productId);
                    }
                    throw new SQLTransactionRollbackException("Product changed during optimistic update", "40001");
                }
            }
            Order order = orderDAO.insert(connection, customerId, "PLACED");
            for (int productId : productIds) {
                orderDAO.insertItem(connection, order.getOrderId(), productId, requestedItems.get(productId),
                        products.get(productId).getPrice());
            }
            connection.commit();
            return order;
        } catch (SQLException | InsufficientStockException exception) {
            if (connection != null) connection.rollback();
            throw exception;
        } finally {
            if (connection != null) {
                connection.setAutoCommit(originalAutoCommit);
                connection.close();
            }
        }
    }

    private void validateItems(Map<Integer, Integer> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("At least one item is required");
        for (Map.Entry<Integer, Integer> item : items.entrySet()) {
            if (item.getKey() == null || item.getValue() == null || item.getValue() <= 0) {
                throw new IllegalArgumentException("Product ids and quantities must be positive");
            }
        }
    }

    private boolean isSerializationFailure(SQLException exception) {
        String state = exception.getSQLState();
        return "40001".equals(state) || exception instanceof SQLTransactionRollbackException;
    }

    public List<Product> listProducts() throws SQLException {
        try (Connection connection = database.getConnection()) {
            return productDAO.findAll(connection);
        }
    }

    public List<com.example.inventory.model.Order> listOrders() throws SQLException {
        try (Connection connection = database.getConnection()) {
            return orderDAO.findAll(connection);
        }
    }
}
