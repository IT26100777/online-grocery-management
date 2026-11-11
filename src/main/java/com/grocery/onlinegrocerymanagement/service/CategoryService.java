package com.grocery.onlinegrocerymanagement.service;

import com.grocery.onlinegrocerymanagement.model.Category;
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

@Service
public class CategoryService {

    private final Path filePath =
            Paths.get("data", "categories.txt");

    private final ProductService productService;

    public CategoryService(ProductService productService) {
        this.productService = productService;
    }

    // READ - View All Categories
    public synchronized List<Category> getAllCategories() {
        return loadCategories();
    }

    // READ - Find Category By ID
    public synchronized Category getCategoryById(int id) {

        for (Category category : loadCategories()) {
            if (category.getId() == id) {
                return category;
            }
        }

        return null;
    }

    // CREATE - Add New Category
    public synchronized void addCategory(Category category) {

        validateCategory(category);

        List<Category> categories = loadCategories();

        for (Category existing : categories) {
            if (existing.getName().equalsIgnoreCase(
                    category.getName())) {
                throw new IllegalArgumentException(
                        "Category name already exists");
            }
        }

        int newId = categories.stream()
                .mapToInt(Category::getId)
                .max()
                .orElse(0) + 1;

        category.setId(newId);
        categories.add(category);

        saveCategories(categories);
    }

    // UPDATE - Edit Category
    public synchronized void updateCategory(
            int id, Category updatedCategory) {

        validateCategory(updatedCategory);

        List<Category> categories = loadCategories();

        for (Category category : categories) {
            if (category.getId() != id &&
                    category.getName().equalsIgnoreCase(
                            updatedCategory.getName())) {
                throw new IllegalArgumentException(
                        "Category name already exists");
            }
        }

        for (int i = 0; i < categories.size(); i++) {

            Category existing = categories.get(i);

            if (existing.getId() == id) {

                // Prevent renaming a category used by products
                if (!existing.getName().equalsIgnoreCase(
                        updatedCategory.getName())
                        && isCategoryInUse(existing.getName())) {
                    throw new IllegalArgumentException(
                            "Cannot rename a category used by products");
                }

                updatedCategory.setId(id);
                categories.set(i, updatedCategory);

                saveCategories(categories);
                return;
            }
        }

        throw new IllegalArgumentException(
                "Category not found");
    }

    // DELETE - Remove Category
    public synchronized void deleteCategory(int id) {

        List<Category> categories = loadCategories();

        Category categoryToDelete = null;

        for (Category category : categories) {
            if (category.getId() == id) {
                categoryToDelete = category;
                break;
            }
        }

        if (categoryToDelete == null) {
            throw new IllegalArgumentException(
                    "Category not found");
        }

        // Protect products linked to this category
        if (isCategoryInUse(categoryToDelete.getName())) {
            throw new IllegalArgumentException(
                    "Cannot delete a category used by products");
        }

        categories.remove(categoryToDelete);
        saveCategories(categories);
    }

    // Check if existing products use this category
    private boolean isCategoryInUse(String categoryName) {

        for (Product product : productService.getAllProducts()) {
            if (product.getCategory() != null &&
                    product.getCategory().equalsIgnoreCase(
                            categoryName)) {
                return true;
            }
        }

        return false;
    }

    // Validate Category Data
    private void validateCategory(Category category) {

        if (category == null ||
                category.getName() == null ||
                category.getName().isBlank()) {
            throw new IllegalArgumentException(
                    "Category name is required");
        }

        category.setName(category.getName().trim());

        if (category.getDescription() == null) {
            category.setDescription("");
        }

        if (category.getName().contains("|") ||
                category.getName().contains("\n") ||
                category.getName().contains("\r") ||
                category.getDescription().contains("|") ||
                category.getDescription().contains("\n") ||
                category.getDescription().contains("\r")) {
            throw new IllegalArgumentException(
                    "Category contains invalid characters");
        }
    }

    // LOAD - Read Categories From Text File
    private List<Category> loadCategories() {

        List<Category> categories = new ArrayList<>();

        if (!Files.exists(filePath)) {
            return categories;
        }

        try {
            List<String> lines = Files.readAllLines(
                    filePath, StandardCharsets.UTF_8);

            for (String line : lines) {

                if (line.isBlank()) {
                    continue;
                }

                String[] data = line.split("\\|", -1);

                if (data.length != 3) {
                    throw new IllegalStateException(
                            "Invalid category data: " + line);
                }

                Category category = new Category(
                        Integer.parseInt(data[0]),
                        data[1],
                        data[2]
                );

                categories.add(category);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Error reading categories file", e);
        }

        return categories;
    }

    // SAVE - Write Categories To Text File
    private void saveCategories(List<Category> categories) {

        try {
            Files.createDirectories(filePath.getParent());

            List<String> lines = new ArrayList<>();

            for (Category category : categories) {

                String line =
                        category.getId() + "|" +
                                category.getName() + "|" +
                                category.getDescription();

                lines.add(line);
            }

            Files.write(
                    filePath,
                    lines,
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Error saving categories file", e);
        }
    }
}
