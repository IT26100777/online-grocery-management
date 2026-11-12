
package com.grocery.onlinegrocerymanagement.service;

import com.grocery.onlinegrocerymanagement.model.Product;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private final Path filePath =
            Paths.get("data", "products.txt");

    // READ - View All Products
    public synchronized List<Product> getAllProducts() {
        return loadProducts();
    }

    // CREATE - Add New Product
    public synchronized void addProduct(Product product) {

        List<Product> products = loadProducts();

        int newId = products.stream()
                .mapToInt(Product::getId)
                .max()
                .orElse(0) + 1;

        product.setId(newId);

        products.add(product);

        saveProducts(products);
    }

    // READ - Find Product By ID
    public synchronized Product getProductById(int id) {

        for (Product product : loadProducts()) {

            if (product.getId() == id) {
                return product;
            }
        }

        return null;
    }

    // UPDATE - Edit Product
    public synchronized void updateProduct(
            int id, Product updatedProduct) {

        List<Product> products = loadProducts();

        for (int i = 0; i < products.size(); i++) {

            Product existingProduct = products.get(i);

            if (existingProduct.getId() == id) {

                updatedProduct.setId(id);

                // Keep existing image if no new image supplied
                if (updatedProduct.getImageFileName() == null
                        || updatedProduct.getImageFileName().isBlank()) {

                    updatedProduct.setImageFileName(
                            existingProduct.getImageFileName()
                    );
                }

                products.set(i, updatedProduct);

                saveProducts(products);
                return;
            }
        }

        throw new IllegalArgumentException(
                "Product not found"
        );
    }

    // DELETE - Delete Product
    public synchronized void deleteProduct(int id) {

        List<Product> products = loadProducts();

        boolean removed = products.removeIf(
                product -> product.getId() == id
        );

        if (removed) {
            saveProducts(products);
        }
    }

    // CHECKOUT - Deduct quantities in one products-file update.
    // Caller must coordinate this with order persistence.
    public synchronized void deductStock(Map<Integer, Integer> quantities) {
        if (quantities == null || quantities.isEmpty()) {
            throw new IllegalArgumentException("No products to deduct.");
        }

        List<Product> products = loadProducts();
        for (Map.Entry<Integer, Integer> item : quantities.entrySet()) {
            Integer requested = item.getValue();
            if (requested == null || requested <= 0) {
                throw new IllegalArgumentException("Invalid product quantity.");
            }
            Product matching = null;
            for (Product product : products) {
                if (product.getId() == item.getKey()) {
                    matching = product;
                    break;
                }
            }
            if (matching == null || matching.getQuantity() < requested) {
                throw new IllegalArgumentException(
                        "A product is unavailable or has insufficient stock.");
            }
            matching.setQuantity(matching.getQuantity() - requested);
        }
        saveProducts(products);
    }

    // Restore stock when an order is cancelled
    public synchronized void restoreStock(int productId, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        List<Product> products = loadProducts();

        for (Product product : products) {

            if (product.getId() == productId) {

                product.setQuantity(
                        Math.addExact(product.getQuantity(), quantity)
                );

                saveProducts(products);
                return;
            }
        }

        throw new IllegalArgumentException("Product not found");
    }

    // LOAD PRODUCTS FROM TEXT FILE
    private List<Product> loadProducts() {

        List<Product> products = new ArrayList<>();

        if (!Files.exists(filePath)) {
            return products;
        }

        try {

            List<String> lines = Files.readAllLines(
                    filePath,
                    StandardCharsets.UTF_8
            );

            for (String line : lines) {

                if (line.isBlank()) {
                    continue;
                }

                String[] data = line.split("\\|", -1);

                // Support old 5 fields and new 6 fields
                if (data.length != 5 && data.length != 6) {

                    throw new IllegalStateException(
                            "Invalid product data: " + line
                    );
                }

                Product product = new Product(
                        Integer.parseInt(data[0]),
                        data[1],
                        data[2],
                        Double.parseDouble(data[3]),
                        Integer.parseInt(data[4])
                );

                // Load image filename if available
                if (data.length == 6 && !data[5].isBlank()) {

                    product.setImageFileName(data[5]);
                }

                products.add(product);
            }

        } catch (IOException e) {

            throw new UncheckedIOException(
                    "Error reading products file", e
            );
        }

        return products;
    }

    // SAVE PRODUCTS TO TEXT FILE
    private void saveProducts(List<Product> products) {

        try {

            Files.createDirectories(filePath.getParent());

            List<String> lines = new ArrayList<>();

            for (Product product : products) {

                String line =
                        product.getId() + "|" +
                                clean(product.getName()) + "|" +
                                clean(product.getCategory()) + "|" +
                                product.getPrice() + "|" +
                                product.getQuantity() + "|" +
                                clean(product.getImageFileName());

                lines.add(line);
            }

            Files.write(
                    filePath,
                    lines,
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {

            throw new UncheckedIOException(
                    "Error saving products file", e
            );
        }
    }

    // Prevent separator characters in saved fields
    private String clean(String value) {

        if (value == null) {
            return "";
        }

        return value.replace("|", " ")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
