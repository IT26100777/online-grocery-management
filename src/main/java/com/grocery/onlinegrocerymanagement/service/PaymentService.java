package com.grocery.onlinegrocerymanagement.service;

import com.grocery.onlinegrocerymanagement.model.Payment;
import com.grocery.onlinegrocerymanagement.model.Order;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentService {

    // File path
    private final Path filePath =
            Paths.get("data", "payments.txt");

    private final OrderService orderService;

    // Constructor
    public PaymentService(OrderService orderService) {
        this.orderService = orderService;
    }

    // CREATE - Add Payment
    public synchronized void addPayment(Payment payment) {

        List<Payment> payments = loadPayments();

        // New payments start as Pending
        payment.setPaymentStatus("Pending");

        // Validate details and calculate amount
        preparePayment(payment, payments, 0);

        // Generate Payment ID
        int newId = 1;

        for (Payment p : payments) {
            if (p.getId() >= newId) {
                newId = p.getId() + 1;
            }
        }

        payment.setId(newId);

        payments.add(payment);

        savePayments(payments);
    }

    // READ - View All Payments
    public synchronized List<Payment> getAllPayments() {

        return loadPayments();
    }

    // READ - Find Payment By ID
    public synchronized Payment getPaymentById(int id) {

        for (Payment payment : loadPayments()) {

            if (payment.getId() == id) {
                return payment;
            }
        }

        return null;
    }

    // Find payment by Order ID
    public synchronized Payment getPaymentByOrderId(int orderId) {

        for (Payment payment : loadPayments()) {
            if (payment.getOrderId() == orderId) {
                return payment;
            }
        }

        return null;
    }

    // UPDATE - Update Payment
    public synchronized void updatePayment(
            int id, Payment updatedPayment) {

        List<Payment> payments = loadPayments();

        for (int i = 0; i < payments.size(); i++) {

            if (payments.get(i).getId() == id) {

                preparePayment(updatedPayment, payments, id);

                updatedPayment.setId(id);

                payments.set(i, updatedPayment);

                savePayments(payments);

                return;
            }
        }

        throw new IllegalArgumentException(
                "Payment not found"
        );
    }

    // DELETE - Delete Payment
    public synchronized void deletePayment(int id) {

        List<Payment> payments = loadPayments();

        boolean removed = payments.removeIf(
                payment -> payment.getId() == id
        );

        if (removed) {
            savePayments(payments);
        }
    }

    // Validate Payment and Set Amount
    private void preparePayment(
            Payment payment,
            List<Payment> payments,
            int currentId) {

        // Check Order
        Order order = orderService.getOrderById(
                payment.getOrderId()
        );

        if (order == null) {
            throw new IllegalArgumentException(
                    "Order not found"
            );
        }

        // Check Payment Method
        String method = payment.getPaymentMethod();

        if (!"Cash on Delivery".equals(method)
                && !"Bank Transfer".equals(method)) {

            throw new IllegalArgumentException(
                    "Invalid payment method"
            );
        }

        // Check Payment Status
        String status = payment.getPaymentStatus();

        if (!"Pending".equals(status)
                && !"Paid".equals(status)
                && !"Cancelled".equals(status)) {

            throw new IllegalArgumentException(
                    "Invalid payment status"
            );
        }

        // Only one payment record per order
        for (Payment p : payments) {

            if (p.getOrderId() == payment.getOrderId()
                    && p.getId() != currentId) {

                throw new IllegalArgumentException(
                        "Payment already exists for this order"
                );
            }
        }

        // Get amount from Order
        payment.setAmount(order.getTotalPrice());
    }

    // READ - Load Payments From File
    private List<Payment> loadPayments() {

        List<Payment> payments = new ArrayList<>();

        if (!Files.exists(filePath)) {
            return payments;
        }

        try {

            List<String> lines =
                    Files.readAllLines(filePath);

            for (String line : lines) {

                if (line.isBlank()) {
                    continue;
                }

                String[] data = line.split("\\|", -1);

                if (data.length != 5) {
                    throw new IllegalStateException(
                            "Invalid payment data"
                    );
                }

                Payment payment = new Payment(
                        Integer.parseInt(data[0]),
                        Integer.parseInt(data[1]),
                        Double.parseDouble(data[2]),
                        data[3],
                        data[4]
                );

                payments.add(payment);
            }

        } catch (IOException e) {

            throw new RuntimeException(
                    "Error reading payments", e
            );
        }

        return payments;
    }

    // WRITE - Save Payments To File
    private void savePayments(List<Payment> payments) {

        try {

            Files.createDirectories(filePath.getParent());

            List<String> lines = new ArrayList<>();

            for (Payment payment : payments) {

                String line =
                        payment.getId() + "|" +
                                payment.getOrderId() + "|" +
                                payment.getAmount() + "|" +
                                payment.getPaymentMethod() + "|" +
                                payment.getPaymentStatus();

                lines.add(line);
            }

            Files.write(filePath, lines);

        } catch (IOException e) {

            throw new RuntimeException(
                    "Error saving payments", e
            );
        }
    }
}
