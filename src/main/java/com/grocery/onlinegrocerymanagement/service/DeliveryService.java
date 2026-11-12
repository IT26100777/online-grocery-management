
package com.grocery.onlinegrocerymanagement.service;

import com.grocery.onlinegrocerymanagement.model.Delivery;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeliveryService {

    private final Path filePath =
            Paths.get("data", "deliveries.txt");

    private final OrderService orderService;

    // Constructor
    public DeliveryService(OrderService orderService) {
        this.orderService = orderService;
    }

    // CREATE - Add Delivery
    public synchronized void addDelivery(Delivery delivery) {

        List<Delivery> deliveries = loadDeliveries();

        // New deliveries start as Pending
        delivery.setDeliveryStatus("Pending");

        validateDelivery(delivery, deliveries, -1);

        int newId = 1;

        for (Delivery d : deliveries) {
            if (d.getId() >= newId) {
                newId = d.getId() + 1;
            }
        }

        delivery.setId(newId);

        deliveries.add(delivery);

        saveDeliveries(deliveries);
    }

    // READ - View All Deliveries
    public synchronized List<Delivery> getAllDeliveries() {
        return loadDeliveries();
    }

    // READ - Find Delivery By ID
    public synchronized Delivery getDeliveryById(int id) {

        for (Delivery delivery : loadDeliveries()) {

            if (delivery.getId() == id) {
                return delivery;
            }
        }

        return null;
    }

    // Find delivery by Order ID
    public synchronized Delivery getDeliveryByOrderId(int orderId) {

        for (Delivery delivery : loadDeliveries()) {

            if (delivery.getOrderId() == orderId) {
                return delivery;
            }
        }

        return null;
    }

    // UPDATE - Update Delivery
    public synchronized void updateDelivery(
            int id, Delivery updatedDelivery) {

        List<Delivery> deliveries = loadDeliveries();

        for (int i = 0; i < deliveries.size(); i++) {

            if (deliveries.get(i).getId() == id) {

                validateDelivery(updatedDelivery, deliveries, id);

                updatedDelivery.setId(id);

                deliveries.set(i, updatedDelivery);

                saveDeliveries(deliveries);

                return;
            }
        }

        throw new IllegalArgumentException(
                "Delivery not found"
        );
    }

    // DELETE - Delete Delivery
    public synchronized void deleteDelivery(int id) {

        List<Delivery> deliveries = loadDeliveries();

        boolean removed = deliveries.removeIf(
                delivery -> delivery.getId() == id
        );

        if (removed) {
            saveDeliveries(deliveries);
        }
    }

    // Validate Delivery Details
    private void validateDelivery(
            Delivery delivery,
            List<Delivery> deliveries,
            int currentId) {

        // Check Order
        if (orderService.getOrderById(
                delivery.getOrderId()) == null) {

            throw new IllegalArgumentException(
                    "Order not found"
            );
        }

        // Check Address
        String address = delivery.getDeliveryAddress();

        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException(
                    "Delivery address is required"
            );
        }

        if (address.contains("|")
                || address.contains("\n")
                || address.contains("\r")) {

            throw new IllegalArgumentException(
                    "Invalid delivery address"
            );
        }

        // Check Date - yyyy-MM-dd
        try {
            LocalDate.parse(delivery.getDeliveryDate());

        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Invalid delivery date"
            );
        }

        // Check Status
        String status = delivery.getDeliveryStatus();

        if (!"Pending".equals(status)
                && !"Out for Delivery".equals(status)
                && !"Delivered".equals(status)
                && !"Cancelled".equals(status)) {

            throw new IllegalArgumentException(
                    "Invalid delivery status"
            );
        }

        // Only one delivery per order
        for (Delivery d : deliveries) {

            if (d.getOrderId() == delivery.getOrderId()
                    && d.getId() != currentId) {

                throw new IllegalArgumentException(
                        "Delivery already exists for this order"
                );
            }
        }
    }

    // FILE READ - Load Deliveries
    private List<Delivery> loadDeliveries() {

        List<Delivery> deliveries = new ArrayList<>();

        if (!Files.exists(filePath)) {
            return deliveries;
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
                            "Invalid delivery data"
                    );
                }

                Delivery delivery = new Delivery(
                        Integer.parseInt(data[0]),
                        Integer.parseInt(data[1]),
                        data[2],
                        data[3],
                        data[4]
                );

                deliveries.add(delivery);
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Error reading deliveries", e
            );
        }

        return deliveries;
    }

    // FILE WRITE - Save Deliveries
    private void saveDeliveries(List<Delivery> deliveries) {

        try {
            Files.createDirectories(filePath.getParent());

            List<String> lines = new ArrayList<>();

            for (Delivery delivery : deliveries) {

                String line =
                        delivery.getId() + "|" +
                                delivery.getOrderId() + "|" +
                                delivery.getDeliveryAddress() + "|" +
                                delivery.getDeliveryDate() + "|" +
                                delivery.getDeliveryStatus();

                lines.add(line);
            }

            Files.write(filePath, lines);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Error saving deliveries", e
            );
        }
    }
}
