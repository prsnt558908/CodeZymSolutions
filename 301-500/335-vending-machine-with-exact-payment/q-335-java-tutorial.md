# Design Vending Machine With Exact Payment

#### Problem Statement

[https://codezym.com/question/335-vending-machine-with-exact-payment](https://codezym.com/question/335-vending-machine-with-exact-payment)

The core idea is to keep product data separate from the current customer's transaction. For the requirements in this problem, simple classes and short checks are the best fit. No specific design pattern or combination of patterns is needed. The State pattern is the closest candidate because allowed actions depend on whether a transaction is active, but separate state classes would add complexity to this small workflow. A `Product` class, a map, a set, and two lists let us find products quickly, display them in order, validate cash, and preserve the exact refund order.

## Does This Problem Need a Design Pattern?

The choice depends on how much behavior needs to vary. 

Here, there is one payment rule: accept supported cash only up to the exact price. A selected product tells us whether a transaction is active, and the inserted total tells us how much payment remains. Short checks express these rules directly.

The recommendation is based on simplicity and ease of maintaining the stated behavior. Design patterns organize responsibilities. Choosing one does not automatically make product lookup or cash insertion faster.

### Why Simple State Checks Fit These Requirements

The machine needs to distinguish two transaction states:

- **Idle:** No product is selected. The customer may select a product, and the operator may restock.
- **Active:** A product is selected. The customer may insert cash, try to complete the purchase, or cancel. Selection and restocking are blocked.

Within an active transaction, comparing `insertedTotal` with the price tells us whether payment is complete. Exact payment still requires a call to `completePurchase()`, and the customer may cancel before that call. A separate paid-state object would not simplify these rules.

`selectedProduct == null` represents the idle state. A non-null value represents the active state and also identifies the product being purchased. This avoids keeping a separate state field that could disagree with the selected product. One `clearTransaction()` helper resets all transaction fields after purchase or cancellation.

This is a simple state machine implemented with conditions. It does **not** implement the object-oriented State design pattern, which delegates behavior to separate state objects. Keeping fields private is encapsulation, an object-oriented principle rather than a design pattern on its own.

### When the State Pattern Would Help

With the State pattern, a `State` interface and classes such as `IdleState` and `ActiveState` would decide how each operation behaves. The machine would delegate calls to its current state object and change that object when a transition occurs.

For this problem, those classes would mostly move a few short checks into more files or nested classes. They would also need access to the selected product, cash, and inventory. That extra structure offers little benefit for the current behavior.

State would become a stronger choice if dispensing took time, hardware failures required recovery, or maintenance mode introduced different rules for many operations. Those states could then own substantial behavior. The useful signal is growing differences in behavior, not just the number of state names.

A combination such as State and Strategy design pattern would be useful only if both the workflow and payment policies needed independent variation. Neither requirement exists here, so the solution below keeps the behavior in one `VendingMachine` class and stores product details in `Product`.

## What Does the Machine Need to Remember?

The vending machine has two kinds of data.

The permanent machine data is:

- Every product's code, name, price, quantity, and capacity.
- The cash denominations accepted by the machine.

The temporary transaction data is:

- The currently selected product.
- The total amount inserted.
- Every accepted cash denomination in insertion order.

After a successful purchase or cancellation, only the temporary transaction data is cleared. The product catalog remains available for future customers.

## Start With a Simple Approach

A simple solution could store all products in one list. Whenever a customer selects or restocks a product, we could scan the complete list to find its code. We could also keep accepted denominations in another list and scan it for every cash insertion.

This works, but it has three avoidable costs:

- Finding a product takes `O(n)` time.
- Checking whether a denomination is supported takes `O(d)` time.
- Sorting the products on every `getProducts()` call takes `O(n log n)` time.

Here, `n` is the number of products and `d` is the number of accepted denominations.

## Improve the Design With Simple Data Structures

We can make the common operations faster without using complicated data structures.

### 1. Product Map

`Map<String, Product> productsByCode` stores each product using its code as the key.

This lets `selectProduct()` and `restockProduct()` find a product in average `O(1)` time.

### 2. Sorted Product-Code List

`List<String> sortedProductCodes` stores all product codes and is sorted once in the constructor.

Product codes never get added or removed, so their order never changes. `getProducts()` can simply walk through this list. There is no need for a `TreeMap` or repeated sorting.

### 3. Accepted-Denomination Set

`Set<Integer> acceptedDenominations` checks whether a cash denomination is supported in average `O(1)` time.

### 4. Inserted-Cash List

`List<Integer> insertedCash` stores only the cash units accepted in the current transaction. A list is important because cancellation must return those denominations in their original insertion order.

### 5. Current Transaction Fields

`selectedProduct`, `insertedTotal`, and `insertedCash` together represent the active transaction.

When `selectedProduct` is `null`, the machine is idle. Otherwise, a transaction is in progress. This makes the machine's current state easy to check in every method.

## How Each Operation Works

### Get Products

Walk through `sortedProductCodes`, get each product from the map, and build the required output string. Products with quantity `0` are also included.

### Select a Product

First reject the request if a transaction is already active. Otherwise:

1. Find the product in the map.
2. Return `INVALID_PRODUCT` if it does not exist.
3. Return `OUT_OF_STOCK` if its quantity is zero.
4. Store it as the selected product.

No stock is removed at this point. Quantity is reduced only after exact payment is completed.

### Insert Cash

Cash is accepted only when:

1. A product is selected.
2. The denomination is supported.
3. Adding it would not exceed the selected price.

Only after all checks pass do we update `insertedTotal` and append the denomination to `insertedCash`. Therefore, a rejected denomination cannot accidentally change the transaction.

### Complete a Purchase

If no transaction exists, return `NO_ACTIVE_TRANSACTION`.

If the inserted total is smaller than the price, report the remaining amount and keep the transaction active. This allows the customer to insert more cash.

The machine rejects every insertion that would exceed the price. Therefore, once the total is not smaller than the price, it must be exactly equal. We can safely dispense one unit, reduce the quantity, and clear the transaction.

### Cancel a Transaction

Make a copy of `insertedCash` before clearing the transaction. Returning the original list would be incorrect because `clearTransaction()` empties it.

Cancellation does not change product quantity.

### Restock a Product

Restocking is allowed only when no customer transaction is active. We first validate every condition:

- The quantity to add must be between `1` and `1,000,000`, inclusive.
- The new price must be between `1` and `1,000,000,000`, inclusive.
- The product must exist.
- The added quantity must fit within the slot's capacity.

Only after all checks pass do we update both quantity and price. This makes the operation atomic: if any check fails, neither value changes.

Checking only whether the price is positive is not enough. For example, a new price of `1,000,000,001` must return `false`, even if the product exists and has room for the added stock. Both its previous price and quantity must remain unchanged.

## Java Solution

```java
import java.util.*;

public class VendingMachine {

    private static class Product {
        private final String code;
        private final String name;
        private int price;
        private int quantity;
        private final int capacity;

        private Product(String code, String name, int price, int quantity, int capacity) {
            this.code = code;
            this.name = name;
            this.price = price;
            this.quantity = quantity;
            this.capacity = capacity;
        }
    }

    private final Map<String, Product> productsByCode;
    private final List<String> sortedProductCodes;
    private final Set<Integer> acceptedDenominations;

    private Product selectedProduct;
    private int insertedTotal;
    private final List<Integer> insertedCash;

    public VendingMachine(List<String> products, List<Integer> acceptedDenominations) {
        productsByCode = new HashMap<>();
        sortedProductCodes = new ArrayList<>();
        this.acceptedDenominations = new HashSet<>(acceptedDenominations);
        insertedCash = new ArrayList<>();

        for (String record : products) {
            String[] fields = record.split(",", -1);
            Product product = new Product(
                    fields[0],
                    fields[1],
                    Integer.parseInt(fields[2]),
                    Integer.parseInt(fields[3]),
                    Integer.parseInt(fields[4])
            );

            productsByCode.put(product.code, product);
            sortedProductCodes.add(product.code);
        }

        Collections.sort(sortedProductCodes);
    }

    public List<String> getProducts() {
        List<String> result = new ArrayList<>();

        for (String productCode : sortedProductCodes) {
            Product product = productsByCode.get(productCode);
            result.add(product.code + "," + product.name + ","
                    + product.price + "," + product.quantity);
        }

        return result;
    }

    public String selectProduct(String productCode) {
        if (selectedProduct != null) {
            return "TRANSACTION_IN_PROGRESS";
        }

        Product product = productsByCode.get(productCode);
        if (product == null) {
            return "INVALID_PRODUCT";
        }
        if (product.quantity == 0) {
            return "OUT_OF_STOCK";
        }

        selectedProduct = product;
        return "SELECTED," + product.code + "," + product.price;
    }

    public String insertCash(int denomination) {
        if (selectedProduct == null) {
            return "NO_PRODUCT_SELECTED";
        }
        if (!acceptedDenominations.contains(denomination)) {
            return "UNSUPPORTED_DENOMINATION";
        }
        if (insertedTotal + denomination > selectedProduct.price) {
            return "EXCEEDS_PRICE";
        }

        insertedTotal += denomination;
        insertedCash.add(denomination);
        return "ACCEPTED," + insertedTotal;
    }

    public String completePurchase() {
        if (selectedProduct == null) {
            return "NO_ACTIVE_TRANSACTION";
        }
        if (insertedTotal < selectedProduct.price) {
            return "INSUFFICIENT_PAYMENT," + (selectedProduct.price - insertedTotal);
        }

        String productCode = selectedProduct.code;
        selectedProduct.quantity--;
        clearTransaction();
        return "DISPENSED," + productCode;
    }

    public List<Integer> cancelTransaction() {
        List<Integer> refund = new ArrayList<>(insertedCash);
        clearTransaction();
        return refund;
    }

    public boolean restockProduct(String productCode, int quantity, int newPrice) {
        // Check both ends of each allowed range before changing the product.
        if (selectedProduct != null
                || quantity < 1 || quantity > 1_000_000
                || newPrice < 1 || newPrice > 1_000_000_000) {
            return false;
        }

        Product product = productsByCode.get(productCode);
        if (product == null || quantity > product.capacity - product.quantity) {
            return false;
        }

        product.quantity += quantity;
        product.price = newPrice;
        return true;
    }

    private void clearTransaction() {
        selectedProduct = null;
        insertedTotal = 0;
        insertedCash.clear();
    }
}
```


## Complexity Analysis

Let `n` be the number of products, `d` the number of accepted denominations, and `k` the number of accepted cash units in the current transaction. Product codes and names have bounded lengths under the stated constraints.

- Constructor: expected `O(n log n + d)` time to build the map and set and sort product codes, with `O(n + d)` storage.
- `getProducts()`: average `O(n)` time and `O(n)` space for the returned list.
- `selectProduct()`: average `O(1)` time.
- `insertCash()`: average amortized `O(1)` time. Appending to an `ArrayList` occasionally requires resizing its internal array.
- `completePurchase()`: `O(1)` for an unsuccessful attempt and `O(k + 1)` for a successful purchase because `ArrayList.clear()` removes all stored cash references.
- `cancelTransaction()`: `O(k + 1)` time and `O(k + 1)` space for the returned refund list, including the empty-list case.
- `restockProduct()`: average `O(1)` time.

The map, sorted code list, and denomination set occupy `O(n + d)` space. The cash list grows with the accepted insertions. Java's `ArrayList.clear()` removes its elements but retains its backing array, so the machine's retained space is `O(n + d + K)`, where `K` is the largest number of cash units accepted in any one transaction so far.
