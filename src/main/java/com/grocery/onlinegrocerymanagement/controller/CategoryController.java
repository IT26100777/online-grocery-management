package com.grocery.onlinegrocerymanagement.controller;

import com.grocery.onlinegrocerymanagement.model.Category;
import com.grocery.onlinegrocerymanagement.service.CategoryService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // READ - Display all categories
    @GetMapping
    public String viewCategories(Model model) {

        model.addAttribute(
                "categories",
                categoryService.getAllCategories()
        );

        return "categories";
    }

    // CREATE - Show add category form
    @GetMapping("/new")
    public String showAddForm(Model model) {

        model.addAttribute("category", new Category());
        model.addAttribute("pageTitle", "Add Category");
        model.addAttribute("formAction", "/categories/add");

        return "category-form";
    }

    // CREATE - Save new category
    @PostMapping("/add")
    public String addCategory(
            @ModelAttribute Category category,
            RedirectAttributes redirectAttributes) {

        try {
            categoryService.addCategory(category);

            redirectAttributes.addFlashAttribute(
                    "success", "Category added successfully"
            );

        } catch (IllegalArgumentException e) {

            redirectAttributes.addFlashAttribute(
                    "error", e.getMessage()
            );
        }

        return "redirect:/categories";
    }

    // UPDATE - Show edit category form
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable int id,
            Model model) {

        Category category =
                categoryService.getCategoryById(id);

        if (category == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Category not found"
            );
        }

        model.addAttribute("category", category);
        model.addAttribute("pageTitle", "Edit Category");
        model.addAttribute(
                "formAction", "/categories/edit/" + id
        );

        return "category-form";
    }

    // UPDATE - Save edited category
    @PostMapping("/edit/{id}")
    public String updateCategory(
            @PathVariable int id,
            @ModelAttribute Category category,
            RedirectAttributes redirectAttributes) {

        try {
            categoryService.updateCategory(id, category);

            redirectAttributes.addFlashAttribute(
                    "success", "Category updated successfully"
            );

        } catch (IllegalArgumentException e) {

            redirectAttributes.addFlashAttribute(
                    "error", e.getMessage()
            );
        }

        return "redirect:/categories";
    }

    // DELETE - Remove category
    @PostMapping("/delete/{id}")
    public String deleteCategory(
            @PathVariable int id,
            RedirectAttributes redirectAttributes) {

        try {
            categoryService.deleteCategory(id);

            redirectAttributes.addFlashAttribute(
                    "success", "Category deleted successfully"
            );

        } catch (IllegalArgumentException e) {

            redirectAttributes.addFlashAttribute(
                    "error", e.getMessage()
            );
        }

        return "redirect:/categories";
    }
}
