# E-Commerce System Design (Order & Inventory Management)

## 1. Clarifying Questions

1. Cart vs. Direct Checkout:
    - Does checkout happen through a persistent shopping cart, or directly on items?
    - Should items be reserved when added to the cart, or only during checkout?

2. Concurrency & Overselling:
    - Can multiple users buy the last available item at the exact same millisecond?
    - How should the system handle race conditions during inventory reduction?

3. Payment Integration:
    - Is payment processed synchronously during checkout or handled via asynchronous webhooks?
    - What happens to inventory if payment fails?

4. Order Cancellation & Returns:
    - Can orders be cancelled, and does cancellation automatically replenish product stock?

---

## 2. Requirements

### Functional Requirements
- Browse products and check real-time stock availability.
- Add items with specified quantities to a customer's shopping cart.
- Place an order and atomically deduct the purchased quantity from inventory.
- Prevent checkout if inventory is insufficient (prevent overselling).
- Calculate total order amount including per-item price and quantity.

### Non-Functional Requirements
- Simplicity: Clear models, enums, and single-controller orchestration.
- Thread Safety: Protect stock decrements against concurrent purchase race conditions.
- Maintainability: Strict separation of Product, Cart, Order, and Payment statuses.

---

## 3. Enums

public enum OrderStatus {
PENDING,
CONFIRMED,
FAILED,
CANCELLED
}

public enum PaymentStatus {
UNPAID,
PAID,
FAILED
}

---

## 4. Models

import java.util.ArrayList;
import java.util.List;

public class Product {
private String productId;
private String name;
private double price;
private int stockQuantity;

    public Product(String productId, String name, double price, int stockQuantity) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void reduceStock(int quantity) {
        this.stockQuantity -= quantity;
    }

    public void addStock(int quantity) {
        this.stockQuantity += quantity;
    }
}

public class OrderItem {
private Product product;
private int quantity;
private double priceAtPurchase;

    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.priceAtPurchase = product.getPrice();
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getSubtotal() {
        return priceAtPurchase * quantity;
    }
}

public class Cart {
private String cartId;
private String customerId;
private List<OrderItem> items;

    public Cart(String cartId, String customerId) {
        this.cartId = cartId;
        this.customerId = customerId;
        this.items = new ArrayList<>();
    }

    public String getCartId() {
        return cartId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void addItem(Product product, int quantity) {
        items.add(new OrderItem(product, quantity));
    }

    public void clear() {
        items.clear();
    }

    public double calculateTotal() {
        double sum = 0.0;
        for (OrderItem item : items) {
            sum += item.getSubtotal();
        }
        return sum;
    }
}

import java.time.LocalDateTime;

public class Order {
private String orderId;
private String customerId;
private List<OrderItem> items;
private double totalAmount;
private OrderStatus status;
private PaymentStatus paymentStatus;
private LocalDateTime createdAt;

    public Order(String orderId, String customerId, List<OrderItem> items, double totalAmount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.items = new ArrayList<>(items);
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
        this.paymentStatus = PaymentStatus.UNPAID;
        this.createdAt = LocalDateTime.now();
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}

---

## 5. Controller

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EcommerceController {
private final Map<String, Product> productCatalog;
private final Map<String, Cart> userCarts;
private final Map<String, Order> orders;

    public EcommerceController() {
        this.productCatalog = new HashMap<>();
        this.userCarts = new HashMap<>();
        this.orders = new HashMap<>();
    }

    public void addProduct(Product product) {
        productCatalog.put(product.getProductId(), product);
    }

    public Product getProduct(String productId) {
        return productCatalog.get(productId);
    }

    public Cart getOrCreateCart(String customerId) {
        if (!userCarts.containsKey(customerId)) {
            String cartId = UUID.randomUUID().toString().substring(0, 8);
            userCarts.put(customerId, new Cart(cartId, customerId));
        }
        return userCarts.get(customerId);
    }

    public synchronized boolean addToCart(String customerId, String productId, int quantity) {
        Product product = productCatalog.get(productId);
        if (product == null || product.getStockQuantity() < quantity) {
            return false;
        }

        Cart cart = getOrCreateCart(customerId);
        cart.addItem(product, quantity);
        return true;
    }

    // synchronized prevents race conditions on inventory deduction
    public synchronized Order checkout(String customerId) {
        Cart cart = userCarts.get(customerId);
        if (cart == null || cart.getItems().isEmpty()) {
            return null;
        }

        // Step 1: Verify all products still have sufficient stock
        for (OrderItem item : cart.getItems()) {
            Product liveProduct = productCatalog.get(item.getProduct().getProductId());
            if (liveProduct == null || liveProduct.getStockQuantity() < item.getQuantity()) {
                return null; // Out of stock, abort checkout
            }
        }

        // Step 2: Deduct inventory atomically
        for (OrderItem item : cart.getItems()) {
            Product liveProduct = productCatalog.get(item.getProduct().getProductId());
            liveProduct.reduceStock(item.getQuantity());
        }

        // Step 3: Create order
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = new Order(orderId, customerId, cart.getItems(), cart.calculateTotal());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);

        orders.put(orderId, order);
        cart.clear(); // Clear cart after successful checkout

        return order;
    }

    public synchronized boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || order.getStatus() == OrderStatus.CANCELLED) {
            return false;
        }

        // Restock items
        for (OrderItem item : order.getItems()) {
            Product liveProduct = productCatalog.get(item.getProduct().getProductId());
            if (liveProduct != null) {
                liveProduct.addStock(item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        return true;
    }
}

---

## 6. Main Class

public class Main {
public static void main(String[] args) {
EcommerceController store = new EcommerceController();

        // 1. Setup product catalog
        Product laptop = new Product("P101", "MacBook Air", 999.99, 5);
        Product mouse = new Product("P102", "Wireless Mouse", 25.50, 2);
        store.addProduct(laptop);
        store.addProduct(mouse);

        System.out.println("Initial Stock - Laptop: " + laptop.getStockQuantity() + ", Mouse: " + mouse.getStockQuantity());

        // 2. Customer adds items to cart
        String customerA = "cust_001";
        store.addToCart(customerA, "P101", 1);
        store.addToCart(customerA, "P102", 2);

        // 3. Checkout process
        Order placedOrder = store.checkout(customerA);
        if (placedOrder != null) {
            System.out.println("\nOrder Placed Successfully!");
            System.out.println("Order ID: " + placedOrder.getOrderId());
            System.out.println("Status: " + placedOrder.getStatus());
            System.out.println("Total Amount: $" + placedOrder.getTotalAmount());
        }

        System.out.println("\nStock After Order - Laptop: " + laptop.getStockQuantity() + ", Mouse: " + mouse.getStockQuantity());

        // 4. Attempt to buy more mouse units than available (Stock is now 0)
        String customerB = "cust_002";
        store.addToCart(customerB, "P102", 1);
        Order failedOrder = store.checkout(customerB);
        System.out.println("Customer B checkout result: " + (failedOrder == null ? "FAILED (Out of Stock)" : "SUCCESS"));

        // 5. Cancel Customer A's order to test restock
        store.cancelOrder(placedOrder.getOrderId());
        System.out.println("\nOrder cancelled. Restocked Mouse: " + mouse.getStockQuantity());
    }
}