package com.example.inventory;

import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.service.OrderService;
import com.example.inventory.strategy.ConcurrencyStrategy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;

public class ConcurrencyDemo {
    private static final int THREADS = 50;
    private static final int REPETITIONS = 20;

    public static void main(String[] args) throws Exception {
        try (Database database = new Database()) {
            System.out.println("strategy          successes failures retries time(ms)");
            for (ConcurrencyStrategy strategy : ConcurrencyStrategy.values()) {
                int successes = 0;
                int failures = 0;
                int retries = 0;
                long started = System.nanoTime();
                for (int repetition = 0; repetition < REPETITIONS; repetition++) {
                    int productId = resetData(database, repetition);
                    OrderService service = new OrderService(database);
                    CountDownLatch ready = new CountDownLatch(THREADS);
                    CountDownLatch start = new CountDownLatch(1);
                    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
                    List<Future<Boolean>> futures = new ArrayList<>();
                    for (int i = 0; i < THREADS; i++) {
                        int customerId = 1 + i;
                        futures.add(executor.submit(() -> {
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
                    ready.await();
                    start.countDown();
                    for (Future<Boolean> future : futures) if (future.get()) successes++; else failures++;
                    retries += service.getRetryCount();
                    executor.shutdown();
                    executor.awaitTermination(10, TimeUnit.SECONDS);
                }
                System.out.printf("%-17s %9d %8d %7d %8d%n", strategy, successes, failures, retries,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            }
        }
    }

    private static int resetData(Database database, int repetition) throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement clearData = connection.prepareStatement(
                     "TRUNCATE order_items, orders, products, customers RESTART IDENTITY CASCADE");
             PreparedStatement addProduct = connection.prepareStatement(
                     "INSERT INTO products (name, price, stock_quantity) VALUES (?, ?, 1) RETURNING product_id");
             PreparedStatement addCustomer = connection.prepareStatement(
                     "INSERT INTO customers (name, email) VALUES (?, ?)")) {
            clearData.executeUpdate();
            addProduct.setString(1, "Last item " + repetition); addProduct.setBigDecimal(2, new java.math.BigDecimal("1.00"));
            var results = addProduct.executeQuery(); results.next(); int productId = results.getInt(1);
            for (int i = 0; i < THREADS; i++) {
                addCustomer.setString(1, "Customer " + i); addCustomer.setString(2, "customer" + i + "@example.com");
                addCustomer.executeUpdate();
            }
            return productId;
        }
    }
}
