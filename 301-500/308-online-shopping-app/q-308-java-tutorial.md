# Design an Online Shopping Application in Java

#### Problem Statement

[https://codezym.com/question/308-online-shopping-app](https://codezym.com/question/308-online-shopping-app)

Keep products and orders in maps for quick lookup. Preserve the order of cart items, and copy those items when placing an order. Small classes and direct checks are enough for this problem. A separate design pattern would add little value to these fixed rules.


## Start with a simple solution

We could keep everything in lists. That works, but adding a product would scan the catalogue and cart. Updating a payment would scan all orders.

Maps avoid these repeated searches by ID. A list is still useful for each customer's order history. Product search still scans the catalogue because the keyword can appear anywhere in a name or category.


## Choose small classes and simple storage

`Product` holds the catalogue details. `ShoppingItem` holds a product's ID, name, quantity, and unit price. `Order` holds its own copied items, total, and payment status.

The application stores four collections:

- `products` finds a product by its ID and preserves catalogue order.
- `carts` finds a customer's active cart. Each cart maps product IDs to items in first-added order.
- `orders` finds an order by its globally unique ID.
- `customerOrders` stores each customer's orders in placement order.

`LinkedHashMap` is a hash map that also remembers insertion order. Updating an item's quantity does not move it. Ordinary `HashMap` is enough for collections whose iteration order does not matter.

The State pattern could give each payment status its own class. Here, statuses only change an output label and apply one rule after success. A few conditions are simpler.


## Follow the rules in the right order

### 1. Load and search the catalogue

Split each row into exactly four fields. Reject empty IDs, names, or categories, malformed prices, and prices outside the allowed range. An invalid row must not reserve its product ID. Keep the first valid row for each ID.

Search the name and category in lowercase, using a lowercase keyword. Visit products in catalogue order. An empty keyword matches every valid product.

### 2. Add to the cart

Validate the customer ID, then the product ID, then the quantity. Return the first applicable error before changing any data.

Create a cart after validation succeeds. Increase an existing item's quantity without moving it, or append a new item. The quantity limit applies to each call, so accumulated quantities may be larger.

Build the response from all items. Each item costs `quantity * unitPrice`, and the cart total is the sum of these amounts.

### 3. Place an order

Check the customer ID, empty order ID, duplicate order ID, and empty cart, in that order. A duplicate ID is invalid even if another customer owns it.

Copy each item into the order. Copying only the list would still share mutable items with the cart. Separate item objects protect the purchase details.

Store the order with payment status `PENDING`, append it to the customer's history, and then remove the cart. Any rejected checkout leaves the cart unchanged.

### 4. Update payment status

Check that the order exists, then validate the exact status string. Finally, reject changes to an already `SUCCESSFUL` payment.

- `PENDING` produces order status `PENDING_PAYMENT`.
- `SUCCESSFUL` produces order status `CONFIRMED`.
- `FAILED` produces order status `PAYMENT_FAILED`.

Derive the order status from the payment status when building the response. This keeps the two values consistent without storing both.

A failed payment can return to `PENDING` or become `SUCCESSFUL`. Repeating `PENDING` or `FAILED` is allowed. After success, even another `SUCCESSFUL` update is rejected. An unsupported status still takes priority over the already-successful error.

### 5. Return recent orders

Read at most ten orders from the end of the customer's history, newest first. Return summaries only. The history shares order objects with the order map, so it shows current statuses without changing placement order.


## A small walkthrough

Suppose `P20` costs `7500` cents and `P40` costs `1200` cents. Adding two of `P20` and one of `P40` gives a total of `16200` cents. The items stay in that order.

Placing order `O700` for customer `C7` copies both items and returns this summary before its item lines:

```text
ORDER,O700,C7,16200,PENDING,PENDING_PAYMENT
```

Updating its payment to `SUCCESSFUL` changes the summary to:

```text
ORDER,O700,C7,16200,SUCCESSFUL,CONFIRMED
```

The next addition starts a fresh cart. Recent orders still show `O700` with its saved total and current status.


## Why this works

Validation happens before changes, ordered collections preserve output order, and copied items protect saved purchases. Shared order objects keep history current. Reading history backward gives the newest orders first, while the success check protects completed payments.


## Complexity

Let `p` be the number of valid products, `k` the number of items in the affected cart or order, and `h` the customer's order count. Map lookups take average `O(1)` time.

Loading processes each input row once. Search scans `p` products, with additional work for matching strings. Adding to a cart, placing an order, and updating a payment each take `O(k)` because they return every item. Recent orders take `O(min(10, h))`.

Stored space is linear in the products, active cart items, orders, and saved order items. Each customer's history stores references to its orders, without duplicating their items.


## Complete Java implementation

Use `long` for prices, accumulated quantities, and totals. This also makes multiplication use 64-bit arithmetic. Integer cents avoid rounding. Save the code as `OnlineShoppingApplication.java`. The helper classes are separate top-level classes in the same file.

```java
import java.util.*;

public class OnlineShoppingApplication {
    // Keep valid products in their original catalogue order.
    Map<String, Product> products = new LinkedHashMap<>();
    Map<String, Map<String, ShoppingItem>> carts = new HashMap<>();
    Map<String, Order> orders = new HashMap<>();
    Map<String, List<Order>> customerOrders = new HashMap<>();

    public OnlineShoppingApplication(List<String> productDetails) {
        for (String row : productDetails) {
            // Preserve empty fields so malformed rows are detected.
            String[] parts = row.split(",", -1);
            if (parts.length != 4 || parts[0].isEmpty()
                    || parts[1].isEmpty() || parts[2].isEmpty()) {
                continue;
            }
            if (products.containsKey(parts[0])) {
                continue;
            }
            if (!parts[3].matches("[+-]?[0-9]+")) {
                continue;
            }

            try {
                long price = Long.parseLong(parts[3]);
                if (price < 0 || price > 1_000_000_000L) {
                    continue;
                }
                products.put(parts[0],
                        new Product(parts[0], parts[1], parts[2], price));
            } catch (NumberFormatException e) {
                // Ignore prices that cannot be parsed safely.
                continue;
            }
        }
    }

    public List<String> searchProducts(String keyword) {
        List<String> result = new ArrayList<>();
        String query = keyword.toLowerCase(Locale.ROOT);

        for (Product product : products.values()) {
            if (product.name.toLowerCase(Locale.ROOT).contains(query)
                    || product.category.toLowerCase(Locale.ROOT).contains(query)) {
                result.add(product.toRow());
            }
        }
        return result;
    }

    public List<String> addToCart(String customerId, String productId, int quantity) {
        // Validate in the required priority order before changing anything.
        if (customerId.isEmpty()) {
            return error("EMPTY_CUSTOMER_ID");
        }
        Product product = products.get(productId);
        if (product == null) {
            return error("PRODUCT_NOT_FOUND");
        }
        if (quantity < 1 || quantity > 1_000_000) {
            return error("INVALID_QUANTITY");
        }

        Map<String, ShoppingItem> cart = carts.get(customerId);
        if (cart == null) {
            cart = new LinkedHashMap<>();
            carts.put(customerId, cart);
        }

        ShoppingItem item = cart.get(productId);
        if (item == null) {
            cart.put(productId,
                    new ShoppingItem(productId, product.name, quantity, product.price));
        } else {
            item.quantity += quantity;
        }
        return cartLines(customerId, cart);
    }

    public List<String> placeOrder(String customerId, String orderId) {
        if (customerId.isEmpty()) {
            return error("EMPTY_CUSTOMER_ID");
        }
        if (orderId.isEmpty()) {
            return error("EMPTY_ORDER_ID");
        }
        if (orders.containsKey(orderId)) {
            return error("DUPLICATE_ORDER_ID");
        }
        Map<String, ShoppingItem> cart = carts.get(customerId);
        if (cart == null || cart.isEmpty()) {
            return error("EMPTY_CART");
        }

        Order order = new Order(orderId, customerId, new ArrayList<>(cart.values()));
        orders.put(orderId, order);

        List<Order> history = customerOrders.get(customerId);
        if (history == null) {
            history = new ArrayList<>();
            customerOrders.put(customerId, history);
        }
        history.add(order);

        // Clear the cart only after the order has been stored.
        carts.remove(customerId);
        return order.toLines();
    }

    public List<String> updatePaymentStatus(String orderId, String paymentStatus) {
        Order order = orders.get(orderId);
        if (order == null) {
            return error("ORDER_NOT_FOUND");
        }
        if (!paymentStatus.equals("PENDING")
                && !paymentStatus.equals("SUCCESSFUL")
                && !paymentStatus.equals("FAILED")) {
            return error("UNSUPPORTED_PAYMENT_STATUS");
        }
        if (order.paymentStatus.equals("SUCCESSFUL")) {
            return error("PAYMENT_ALREADY_SUCCESSFUL");
        }

        order.paymentStatus = paymentStatus;
        return order.toLines();
    }

    public List<String> getRecentOrders(String customerId) {
        List<String> result = new ArrayList<>();
        List<Order> history = customerOrders.get(customerId);
        if (customerId.isEmpty() || history == null) {
            return result;
        }

        // Placement order stays unchanged when a payment is updated.
        for (int i = history.size() - 1; i >= 0 && result.size() < 10; i--) {
            result.add(history.get(i).summary());
        }
        return result;
    }

    List<String> cartLines(String customerId, Map<String, ShoppingItem> cart) {
        long total = 0;
        for (ShoppingItem item : cart.values()) {
            total += item.total();
        }

        List<String> result = new ArrayList<>();
        result.add("CART," + customerId + "," + total);
        for (ShoppingItem item : cart.values()) {
            result.add(item.toLine());
        }
        return result;
    }

    List<String> error(String reason) {
        List<String> result = new ArrayList<>();
        result.add("ERROR," + reason);
        return result;
    }
}

class Product {
    String id;
    String name;
    String category;
    long price;

    Product(String id, String name, String category, long price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
    }

    String toRow() {
        return id + "," + name + "," + category + "," + price;
    }
}

class ShoppingItem {
    String productId;
    String productName;
    long quantity;
    long unitPrice;

    ShoppingItem(String productId, String productName, long quantity, long unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    long total() {
        return quantity * unitPrice;
    }

    ShoppingItem copy() {
        return new ShoppingItem(productId, productName, quantity, unitPrice);
    }

    String toLine() {
        return "ITEM," + productId + "," + productName + "," + quantity
                + "," + unitPrice + "," + total();
    }
}

class Order {
    String id;
    String customerId;
    List<ShoppingItem> items = new ArrayList<>();
    long totalInCents = 0;
    String paymentStatus = "PENDING";

    Order(String id, String customerId, List<ShoppingItem> cartItems) {
        this.id = id;
        this.customerId = customerId;

        // Copy the items so later cart changes cannot change this order.
        for (ShoppingItem item : cartItems) {
            ShoppingItem copy = item.copy();
            items.add(copy);
            totalInCents += copy.total();
        }
    }

    String orderStatus() {
        if (paymentStatus.equals("SUCCESSFUL")) {
            return "CONFIRMED";
        }
        if (paymentStatus.equals("FAILED")) {
            return "PAYMENT_FAILED";
        }
        return "PENDING_PAYMENT";
    }

    String summary() {
        return "ORDER," + id + "," + customerId + "," + totalInCents
                + "," + paymentStatus + "," + orderStatus();
    }

    List<String> toLines() {
        List<String> result = new ArrayList<>();
        result.add(summary());
        for (ShoppingItem item : items) {
            result.add(item.toLine());
        }
        return result;
    }
}
```
