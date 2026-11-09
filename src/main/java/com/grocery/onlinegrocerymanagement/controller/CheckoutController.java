
package com.grocery.onlinegrocerymanagement.controller;

import com.grocery.onlinegrocerymanagement.model.Order;
import com.grocery.onlinegrocerymanagement.model.Product;
import com.grocery.onlinegrocerymanagement.model.UserAccount;

import com.grocery.onlinegrocerymanagement.service.CustomerCheckoutLinkService;
import com.grocery.onlinegrocerymanagement.service.OrderService;
import com.grocery.onlinegrocerymanagement.service.ProductService;
import com.grocery.onlinegrocerymanagement.service.RegistrationService;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private static final String CHECKOUT_TOKEN_KEY = "greenmart.checkout.token";

    private final OrderService orderService;
    private final ProductService productService;
    private final RegistrationService registrationService;
    private final CustomerCheckoutLinkService customerLinkService;

    private final Path deliveryFile =
            Paths.get("data", "checkout-deliveries.txt");

    public CheckoutController(
            OrderService orderService,
            ProductService productService,
            RegistrationService registrationService,
            CustomerCheckoutLinkService customerLinkService) {

        this.orderService = orderService;
        this.productService = productService;
        this.registrationService = registrationService;
        this.customerLinkService = customerLinkService;
    }

    // Display Checkout Page
    @GetMapping
    public String checkoutPage(
            Authentication authentication,
            Model model,
            HttpSession session) {

        UserAccount account =
                registrationService.findByUsername(
                        authentication.getName()
                );

        if (account == null) {
            return "redirect:/";
        }

        String token = (String) session.getAttribute(CHECKOUT_TOKEN_KEY);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(CHECKOUT_TOKEN_KEY, token);
        }
        model.addAttribute("checkoutToken", token);

        model.addAttribute("username", account.getUsername());
        model.addAttribute("fullName", account.getFullName());
        model.addAttribute("phone", account.getPhone());

        return "checkout";
    }

    // Place Order
    @PostMapping("/place")
    public synchronized String placeOrder(
            @RequestParam(value = "productId", required = false)
            List<Integer> productIds,

            @RequestParam(value = "quantity", required = false)
            List<Integer> quantities,

            @RequestParam("checkoutToken")
            String checkoutToken,

            @RequestParam("deliveryPhone")
            String deliveryPhone,

            @RequestParam("deliveryAddress")
            String deliveryAddress,

            Authentication authentication,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // Check and consume the token while holding the controller lock.
        String expectedToken = (String) session.getAttribute(CHECKOUT_TOKEN_KEY);
        if (expectedToken == null || !expectedToken.equals(checkoutToken)) {
            redirectAttributes.addFlashAttribute("error",
                    "Checkout was already submitted or expired. Review your orders before trying again.");
            return "redirect:/checkout";
        }
        session.removeAttribute(CHECKOUT_TOKEN_KEY);

        try {
            if (productIds == null ||
                    quantities == null ||
                    productIds.isEmpty() ||
                    productIds.size() != quantities.size() ||
                    productIds.size() > 30) {

                throw new IllegalArgumentException(
                        "Invalid shopping cart."
                );
            }

            String phone = clean(deliveryPhone);
            String address = clean(deliveryAddress);

            if (phone.length() < 7 || phone.length() > 25) {
                throw new IllegalArgumentException(
                        "Please enter a valid phone number."
                );
            }

            if (address.length() < 10 || address.length() > 250) {
                throw new IllegalArgumentException(
                        "Address must contain 10 to 250 characters."
                );
            }

            // Validate every item before saving orders
            Map<Integer, Integer> cart = new LinkedHashMap<>();

            for (int i = 0; i < productIds.size(); i++) {

                int productId = productIds.get(i);
                int quantity = quantities.get(i);

                if (quantity <= 0 ||
                        cart.containsKey(productId)) {

                    throw new IllegalArgumentException(
                            "Invalid product quantity or duplicate item."
                    );
                }

                Product product =
                        productService.getProductById(productId);

                if (product == null ||
                        !Double.isFinite(product.getPrice()) ||
                        product.getPrice() <= 0 ||
                        quantity > product.getQuantity()) {

                    throw new IllegalArgumentException(
                            "A product is unavailable or has insufficient stock."
                    );
                }

                cart.put(productId, quantity);
            }

            // Get Customer ID using logged-in account
            int customerId =
                    customerLinkService.getCustomerId(
                            authentication.getName()
                    );

            List<Order> createdOrders = new ArrayList<>();
            double total = 0;

            // Existing OrderService supports one product per order
            for (Map.Entry<Integer, Integer> item :
                    cart.entrySet()) {

                Order order = new Order();

                order.setCustomerId(customerId);
                order.setProductId(item.getKey());
                order.setQuantity(item.getValue());

                // Recalculates price and sets Pending
                orderService.addOrder(order);

                createdOrders.add(order);
                total += order.getTotalPrice();
            }

            // Save separate delivery details
            saveDeliveryDetails(
                    customerId,
                    createdOrders,
                    phone,
                    address
            );

            // Deduct all ordered products after order and delivery records are saved.
            // Text-file writes are not transactional across files.
            productService.deductStock(cart);

            redirectAttributes.addFlashAttribute(
                    "orderCount", createdOrders.size()
            );

            redirectAttributes.addFlashAttribute(
                    "grandTotal", total
            );

            redirectAttributes.addFlashAttribute(
                    "username", authentication.getName()
            );

            return "redirect:/checkout/success";

        } catch (IllegalArgumentException |
                 IllegalStateException e) {

            redirectAttributes.addFlashAttribute(
                    "error", e.getMessage()
            );

            return "redirect:/checkout";

        } catch (IOException e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Delivery details could not be saved. " +
                            "Please contact support before trying again."
            );

            return "redirect:/checkout";
        }
    }

    // Order Success Page
    @GetMapping("/success")
    public String checkoutSuccess(Model model) {

        if (!model.containsAttribute("orderCount")) {
            return "redirect:/";
        }

        return "checkout-success";
    }

    // Remove characters that break text-file format
    private String clean(String input) {

        return input == null
                ? ""
                : input.replaceAll("[\\r\\n|\\t]", " ").trim();
    }

    // Keep delivery details without modifying Order.java
    private void saveDeliveryDetails(
            int customerId,
            List<Order> orders,
            String phone,
            String address) throws IOException {

        Files.createDirectories(deliveryFile.getParent());

        List<String> lines = new ArrayList<>();

        for (Order order : orders) {

            lines.add(
                    order.getId() + "|" +
                            customerId + "|" +
                            phone + "|" +
                            address
            );
        }

        Files.write(
                deliveryFile,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
