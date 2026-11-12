package com.grocery.onlinegrocerymanagement.service;

import com.grocery.onlinegrocerymanagement.model.Order;
import com.grocery.onlinegrocerymanagement.model.Product;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final Path filePath =
            Paths.get("data", "orders.txt");

    private final Path sequenceFilePath =
            Paths.get("data", "order-id-sequence.txt");

    private final ProductService productService;
    private final CustomerService customerService;

    // Constructor
    public OrderService(ProductService productService,
                        CustomerService customerService) {

        this.productService = productService;
        this.customerService = customerService;
    }

    // CREATE - Add Order
    public synchronized void addOrder(Order order) {

        List<Order> orders = loadOrders();

        // Read last allocated Order ID
        int lastId = 0;

        if (Files.exists(sequenceFilePath)) {
            try {
                String content = Files.readString(sequenceFilePath).trim();

                if (!content.isEmpty()) {
                    lastId = Integer.parseInt(content);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error reading order ID sequence", e);
            }
        }

        // Never reuse an ID present in current orders
        for (Order existing : orders) {
            lastId = Math.max(lastId, existing.getId());
        }

        int newId = Math.addExact(lastId, 1);

        order.setId(newId);
        order.setStatus("Pending");

        calculateTotal(order);

        // Reserve ID before saving order
        try {
            Files.createDirectories(sequenceFilePath.getParent());
            Files.writeString(sequenceFilePath, String.valueOf(newId));
        } catch (IOException e) {
            throw new RuntimeException("Error saving order ID sequence", e);
        }

        orders.add(order);
        saveOrders(orders);
    }

    // READ - View All Orders
    public synchronized List<Order> getAllOrders() {

        return loadOrders();
    }

    // READ - Find Order By ID
    public synchronized Order getOrderById(int id) {

        for (Order order : loadOrders()) {

            if (order.getId() == id) {
                return order;
            }
        }

        return null;
    }

    // UPDATE - Update Order
    public synchronized void updateOrder(int id, Order updatedOrder) {

        List<Order> orders = loadOrders();

        for (int i = 0; i < orders.size(); i++) {

            Order existingOrder = orders.get(i);

            if (existingOrder.getId() == id) {

                String oldStatus = existingOrder.getStatus();
                String newStatus = updatedOrder.getStatus();

                // Prevent changing product or quantity during status update
                if (existingOrder.getProductId() != updatedOrder.getProductId()
                        || existingOrder.getQuantity() != updatedOrder.getQuantity()
                        || existingOrder.getCustomerId() != updatedOrder.getCustomerId()) {

                    throw new IllegalArgumentException(
                            "Cannot change customer, product or quantity during status update"
                    );
                }

                if (newStatus == null || newStatus.isBlank()) {
                    throw new IllegalArgumentException(
                            "Order status is required"
                    );
                }

                boolean wasCancelled =
                        "Cancelled".equalsIgnoreCase(oldStatus);

                boolean isCancelled =
                        "Cancelled".equalsIgnoreCase(newStatus);

                // Prevent reactivating an already cancelled order
                if (wasCancelled && !isCancelled) {
                    throw new IllegalArgumentException(
                            "Cancelled orders cannot be reactivated"
                    );
                }

                updatedOrder.setId(id);

                calculateTotal(updatedOrder);

                // Restore stock only on first cancellation
                if (!wasCancelled && isCancelled) {
                    productService.restoreStock(
                            existingOrder.getProductId(),
                            existingOrder.getQuantity()
                    );
                }

                orders.set(i, updatedOrder);
                saveOrders(orders);

                return;
            }
        }

        throw new IllegalArgumentException("Order not found");
    }

    // DELETE - Delete Order
    public synchronized void deleteOrder(int id) {

        List<Order> orders = loadOrders();

        boolean removed = orders.removeIf(
                order -> order.getId() == id
        );

        if (removed) {
            saveOrders(orders);
        }
    }

    // Calculate Total Price
    private void calculateTotal(Order order) {

        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        if (customerService.getCustomerById(
                order.getCustomerId()) == null) {

            throw new IllegalArgumentException(
                    "Customer not found"
            );
        }

        Product product = productService.getProductById(
                order.getProductId()
        );

        if (product == null) {
            throw new IllegalArgumentException(
                    "Product not found"
            );
        }

        double total =
                product.getPrice() * order.getQuantity();

        order.setTotalPrice(total);

        if (order.getStatus() == null ||
                order.getStatus().isBlank()) {

            order.setStatus("Pending");
        }
    }

    // READ - Load Orders From File
    private List<Order> loadOrders() {

        List<Order> orders = new ArrayList<>();

        if (!Files.exists(filePath)) {
            return orders;
        }

        try {

            List<String> lines =
                    Files.readAllLines(filePath);

            for (String line : lines) {

                if (line.isBlank()) {
                    continue;
                }

                String[] data = line.split("\\|", -1);

                if (data.length != 6) {
                    throw new IllegalStateException(
                            "Invalid order data"
                    );
                }

                Order order = new Order(
                        Integer.parseInt(data[0]),
                        Integer.parseInt(data[1]),
                        Integer.parseInt(data[2]),
                        Integer.parseInt(data[3]),
                        Double.parseDouble(data[4]),
                        data[5]
                );

                orders.add(order);
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Error reading orders", e
            );
        }

        return orders;
    }

    // WRITE - Save Orders To File
    private void saveOrders(List<Order> orders) {

        try {

            Files.createDirectories(filePath.getParent());

            List<String> lines = new ArrayList<>();

            for (Order order : orders) {

                String line =
                        order.getId() + "|" +
                                order.getCustomerId() + "|" +
                                order.getProductId() + "|" +
                                order.getQuantity() + "|" +
                                order.getTotalPrice() + "|" +
                                order.getStatus().replace("|", " ");

                lines.add(line);
            }

            Files.write(filePath, lines);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Error saving orders", e
            );
        }
    }
}