package com.grocery.onlinegrocerymanagement.controller;

import com.grocery.onlinegrocerymanagement.service.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // READ - Display inventory dashboard
    @GetMapping
    public String viewInventory(Model model) {

        model.addAttribute(
                "products",
                inventoryService.getAllInventoryProducts()
        );

        model.addAttribute(
                "lowStockCount",
                inventoryService.getLowStockProducts().size()
        );

        model.addAttribute(
                "outOfStockCount",
                inventoryService.getOutOfStockProducts().size()
        );

        return "inventory";
    }

    // UPDATE - Change product stock quantity
    @PostMapping("/update/{id}")
    public String updateStock(
            @PathVariable int id,
            @RequestParam("quantity") int quantity,
            RedirectAttributes redirectAttributes) {

        try {

            inventoryService.updateStockQuantity(id, quantity);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Stock quantity updated successfully"
            );

        } catch (IllegalArgumentException e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage()
            );
        }

        return "redirect:/inventory";
    }
}

