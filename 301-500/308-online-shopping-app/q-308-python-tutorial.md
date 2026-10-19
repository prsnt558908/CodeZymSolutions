# Design an Online Shopping Application in Python

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
- `customer_orders` stores each customer's orders in placement order.

A regular dictionary preserves insertion order. Updating an item's quantity does not move it. Lists preserve the order in which a customer's orders were placed.

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


## Complete Python implementation

Use dataclasses for the three small data objects. Python integers handle the required totals exactly, and integer cents avoid rounding. The public method names match the required API. Save the code as `online_shopping_application.py`. This code uses Python 3.9 or later.

```python
from dataclasses import dataclass, field
import re


@dataclass
class Product:
    id: str
    name: str
    category: str
    price: int

    def to_row(self):
        return f"{self.id},{self.name},{self.category},{self.price}"


@dataclass
class ShoppingItem:
    product_id: str
    product_name: str
    quantity: int
    unit_price: int

    def total(self):
        return self.quantity * self.unit_price

    def copy(self):
        return ShoppingItem(
            self.product_id, self.product_name, self.quantity, self.unit_price
        )

    def to_line(self):
        return (
            f"ITEM,{self.product_id},{self.product_name},{self.quantity},"
            f"{self.unit_price},{self.total()}"
        )


@dataclass
class Order:
    id: str
    customer_id: str
    items: list[ShoppingItem]
    total_in_cents: int = field(init=False)
    payment_status: str = "PENDING"

    def __post_init__(self):
        # Copy the items so later cart changes cannot change this order.
        self.items = [item.copy() for item in self.items]
        self.total_in_cents = sum(item.total() for item in self.items)

    def order_status(self):
        if self.payment_status == "SUCCESSFUL":
            return "CONFIRMED"
        if self.payment_status == "FAILED":
            return "PAYMENT_FAILED"
        return "PENDING_PAYMENT"

    def summary(self):
        return (
            f"ORDER,{self.id},{self.customer_id},{self.total_in_cents},"
            f"{self.payment_status},{self.order_status()}"
        )

    def to_lines(self):
        return [self.summary()] + [item.to_line() for item in self.items]


class OnlineShoppingApplication:
    def __init__(self, productDetails):
        # Keep valid products in their original catalogue order.
        self.products = {}
        self.carts = {}
        self.orders = {}
        self.customer_orders = {}

        for row in productDetails:
            # Preserve empty fields so malformed rows are detected.
            parts = row.split(",")
            if len(parts) != 4 or not all(parts[:3]):
                continue
            if parts[0] in self.products:
                continue
            if re.fullmatch(r"[+-]?[0-9]+", parts[3]) is None:
                continue

            try:
                price = int(parts[3])
            except ValueError:
                # Ignore prices that cannot be parsed safely.
                continue
            if price < 0 or price > 1_000_000_000:
                continue
            self.products[parts[0]] = Product(parts[0], parts[1], parts[2], price)

    def searchProducts(self, keyword):
        result = []
        query = keyword.lower()

        for product in self.products.values():
            if query in product.name.lower() or query in product.category.lower():
                result.append(product.to_row())
        return result

    def addToCart(self, customerId, productId, quantity):
        # Validate in the required priority order before changing anything.
        if not customerId:
            return self.error("EMPTY_CUSTOMER_ID")
        product = self.products.get(productId)
        if product is None:
            return self.error("PRODUCT_NOT_FOUND")
        if quantity < 1 or quantity > 1_000_000:
            return self.error("INVALID_QUANTITY")

        cart = self.carts.get(customerId)
        if cart is None:
            cart = {}
            self.carts[customerId] = cart

        item = cart.get(productId)
        if item is None:
            cart[productId] = ShoppingItem(
                productId, product.name, quantity, product.price
            )
        else:
            item.quantity += quantity
        return self.cart_lines(customerId, cart)

    def placeOrder(self, customerId, orderId):
        if not customerId:
            return self.error("EMPTY_CUSTOMER_ID")
        if not orderId:
            return self.error("EMPTY_ORDER_ID")
        if orderId in self.orders:
            return self.error("DUPLICATE_ORDER_ID")
        cart = self.carts.get(customerId)
        if not cart:
            return self.error("EMPTY_CART")

        order = Order(orderId, customerId, list(cart.values()))
        self.orders[orderId] = order

        history = self.customer_orders.get(customerId)
        if history is None:
            history = []
            self.customer_orders[customerId] = history
        history.append(order)

        # Clear the cart only after the order has been stored.
        del self.carts[customerId]
        return order.to_lines()

    def updatePaymentStatus(self, orderId, paymentStatus):
        order = self.orders.get(orderId)
        if order is None:
            return self.error("ORDER_NOT_FOUND")
        if paymentStatus not in ("PENDING", "SUCCESSFUL", "FAILED"):
            return self.error("UNSUPPORTED_PAYMENT_STATUS")
        if order.payment_status == "SUCCESSFUL":
            return self.error("PAYMENT_ALREADY_SUCCESSFUL")

        order.payment_status = paymentStatus
        return order.to_lines()

    def getRecentOrders(self, customerId):
        result = []
        history = self.customer_orders.get(customerId)
        if not customerId or history is None:
            return result

        # Placement order stays unchanged when a payment is updated.
        for order in reversed(history[-10:]):
            result.append(order.summary())
        return result

    def cart_lines(self, customer_id, cart):
        total = sum(item.total() for item in cart.values())
        result = [f"CART,{customer_id},{total}"]
        for item in cart.values():
            result.append(item.to_line())
        return result

    def error(self, reason):
        return [f"ERROR,{reason}"]
```
