package com.grocery.onlinegrocerymanagement.service;

import com.grocery.onlinegrocerymanagement.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductService productService;

    public InventoryService(ProductService productService) {
        this.productService = productService;
    }

    // READ - View all products and stock quantities
    public List<Product> getAllInventoryProducts() {
        return productService.getAllProducts();
    }

    // READ - Find products with low stock (1 to 5)
    public List<Product> getLowStockProducts() {

        List<Product> lowStockProducts = new ArrayList<>();

        for (Product product : productService.getAllProducts()) {

            if (product.getQuantity() > 0 &&
                    product.getQuantity() <= LOW_STOCK_THRESHOLD) {

                lowStockProducts.add(product);
            }
        }

        return lowStockProducts;
    }

    // READ - Find products that are out of stock
    public List<Product> getOutOfStockProducts() {

        List<Product> outOfStockProducts = new ArrayList<>();

        for (Product product : productService.getAllProducts()) {

            if (product.getQuantity() == 0) {
                outOfStockProducts.add(product);
            }
        }

        return outOfStockProducts;
    }

    // UPDATE - Change the stock quantity of a product
    public synchronized void updateStockQuantity(
            int productId, int newQuantity) {

        // Prevent negative stock quantities
        if (newQuantity < 0) {
            throw new IllegalArgumentException(
                    "Stock quantity cannot be negative"
            );
        }

        // Find the existing product
        Product product = productService.getProductById(productId);

        if (product == null) {
            throw new IllegalArgumentException(
                    "Product not found"
            );
        }

        // Update only the quantity
        product.setQuantity(newQuantity);

        // Save using the existing ProductService
        productService.updateProduct(productId, product);
    }
}

