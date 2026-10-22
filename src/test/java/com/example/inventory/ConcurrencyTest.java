package com.example.inventory;

import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.service.OrderService;
import com.example.inventory.strategy.ConcurrencyStrategy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrencyTest {
    private static final int THREADS = 50;
    private Database database;

    @BeforeEach
    void connectIfConfigured() throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/inventory");
        String user = System.getenv().getOrDefault("DB_USER", "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");
        try {
            database = new Database(url, user, password);
            try (Connection connection = database.getConnection()) {
                assertNotNull(connection);
            }
        } catch (Exception exception) {
            Assumptions.assumeTrue(false, "PostgreSQL is not available: " + exception.getMessage());
        }
    }

    @AfterEach
    void close() {
        if (database != null) database.close();
    }

    @Test
    void exactlyOneCustomerBuysTheLastItemForEveryStrategy() throws Exception {
        for (ConcurrencyStrategy strategy : ConcurrencyStrategy.values()) {
            for (int repetition = 0; repetition < 20; repetition++) {
                int productId = resetData(repetition);
                ExecutorService executor = Executors.newFixedThreadPool(THREADS);
                CountDownLatch ready = new CountDownLatch(THREADS);
                CountDownLatch start = new CountDownLatch(1);
                OrderService service = new OrderService(database);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 1; i <= THREADS; i++) {
                    int customerId = i;
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            service.placeOrder(customerId, Map.of(productId, 1), strategy);
                            return true;
                        } catch (InsufficientStockException exception) {
                            return false;
                        }
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                int successes = 0;
                for (Future<Boolean> result : results) if (result.get()) successes++;
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
                assertEquals(1, successes, strategy.toString());
                assertEquals(1, countOrders());
                assertEquals(0, stock(productId));
            }
        }
    }

    private int resetData(int repetition) throws SQLException {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             PreparedStatement addProduct = connection.prepareStatement(
                     "INSERT INTO products (name, price, stock_quantity) VALUES (?, 1.00, 1)");
             PreparedStatement addCustomer = connection.prepareStatement(
                     "INSERT INTO customers (name, email) VALUES (?, ?)")) {
            statement.executeUpdate("TRUNCATE order_items, orders, products, customers RESTART IDENTITY CASCADE");
            addProduct.setString(1, "Last item " + repetition);
            addProduct.executeUpdate();
            for (int i = 1; i <= THREADS; i++) {
                addCustomer.setString(1, "Customer " + i);
                addCustomer.setString(2, "test" + i + "@example.com");
                addCustomer.executeUpdate();
            }
            try (ResultSet results = statement.executeQuery("SELECT product_id FROM products")) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    private int stock(int productId) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT stock_quantity FROM products WHERE product_id = ?")) {
            statement.setInt(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    private int countOrders() throws SQLException {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM orders")) {
            results.next();
            return results.getInt(1);
        }
    }
}
