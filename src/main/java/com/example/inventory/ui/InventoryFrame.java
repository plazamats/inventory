package com.example.inventory.ui;

import com.example.inventory.Database;
import com.example.inventory.model.Order;
import com.example.inventory.model.Product;
import com.example.inventory.service.OrderService;
import com.example.inventory.strategy.ConcurrencyStrategy;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;

public class InventoryFrame extends JFrame {
    private final OrderService orderService;
    private final JPanel productPanel = new JPanel(new GridLayout(0, 1));
    private final JButton refreshButton = new JButton("Refresh products");
    private final JButton ordersButton = new JButton("View orders");
    private final JComboBox<ConcurrencyStrategy> strategyBox = new JComboBox<>(ConcurrencyStrategy.values());
    private List<Product> products;

    public InventoryFrame(OrderService orderService) {
        this.orderService = orderService;
        setTitle("Inventory and Orders");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(3, 1));
        add(productPanel);
        JPanel controls = new JPanel(new GridLayout(1, 3));
        controls.add(refreshButton);
        controls.add(strategyBox);
        controls.add(ordersButton);
        add(controls);
        refreshButton.addActionListener(event -> refreshProducts());
        ordersButton.addActionListener(event -> showOrders());
        refreshProducts();
        setSize(600, 400);
        setLocationRelativeTo(null);
    }

    private void refreshProducts() {
        try {
            products = orderService.listProducts();
            productPanel.removeAll();
            for (Product product : products) {
                JButton productButton = new JButton(product.getName() + " | stock: " + product.getStockQuantity()
                        + " | price: " + product.getPrice());
                productButton.addActionListener(event -> placeOrder(product));
                productPanel.add(productButton);
            }
            productPanel.revalidate();
            productPanel.repaint();
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Database error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void placeOrder(Product product) {
        String customerText = JOptionPane.showInputDialog(this, "Customer id:");
        String quantityText = JOptionPane.showInputDialog(this, "Quantity:");
        try {
            int customerId = Integer.parseInt(customerText);
            int quantity = Integer.parseInt(quantityText);
            Map<Integer, Integer> items = new LinkedHashMap<>();
            items.put(product.getProductId(), quantity);
            orderService.placeOrder(customerId, items, (ConcurrencyStrategy) strategyBox.getSelectedItem());
            JOptionPane.showMessageDialog(this, "Order placed.");
            refreshProducts();
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Order error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOrders() {
        try {
            StringBuilder text = new StringBuilder();
            for (Order order : orderService.listOrders()) {
                text.append("Order ").append(order.getOrderId()).append(" customer ")
                        .append(order.getCustomerId()).append(" ").append(order.getStatus()).append("\n");
            }
            JOptionPane.showMessageDialog(this, text.length() == 0 ? "No orders." : text.toString());
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Database error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        Database database = new Database();
        SwingUtilities.invokeLater(() -> new InventoryFrame(new OrderService(database)).setVisible(true));
    }
}
