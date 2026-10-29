# Design a Billing and Discounts System in Java

#### Problem Statement

[https://codezym.com/question/73-design-billing-discounts-system](https://codezym.com/question/73-design-billing-discounts-system)

Keep each bill's original subtotal, remember its selected discount codes, and calculate the payable amount again in a fixed order. Change loyalty points only after payment succeeds. A map, a set, and a few small classes are enough. No extra design pattern is needed for these four fixed discount codes.


## 1. A first attempt: change the payable amount directly

When `P10` arrives, it is tempting to write:

```java
payable -= payable / 10;
```

For a bill of 1000, this gives 900. Applying `P10` again gives 810, which is wrong. Repeating a code must not add another discount.

The fix is to store the selected codes in a set and always calculate from the original subtotal. This also lets `P20` replace the effect of `P10` without trying to undo earlier calculations.


## 2. Store only what the system needs

| Class or structure | Purpose |
| --- | --- |
| `Customer` | Stores the current points balance. Its level is calculated from that balance. |
| `Bill` | Stores the subtotal, a reference to its customer, selected codes, and whether it is paid. |
| `BillTotal` | Holds a calculated payable amount and the points that payment would redeem. |
| Two `HashMap` objects | Find customers by customer ID and bills by bill ID. |
| A `HashSet` per bill | Stores each supported code at most once. |

All bills for the same customer refer to the same `Customer` object. We do not copy the points balance into a bill.

The cart is needed only to calculate the subtotal, so there is no need to keep every item afterward.

**Why skip Strategy?** Strategy can help when many discount rules change independently. Here, it would still need extra logic to choose the winning percentage and enforce the calculation order. One short calculation method is clearer for the given requirements.


## 3. Calculate every bill in the same order

The `getTotal` method follows these steps:

1. Use `P20` if present. Otherwise, use `P10` if present. Subtract the percentage discount from the original subtotal.
2. Subtract 100 if `FLAT100` is present and the original subtotal is at least 500.
3. Keep the amount at least zero. If `REDEEM` is present, redeem the smaller of the customer's current points and `payable / 5`.
4. Return the payable amount and planned redemption without changing the customer.

Floor the **discount**, then subtract it. For subtotal 499 and `P10`, the discount is 49 and the payable amount is 450.

Another bill can change the customer's points after a preview. Always recalculate at payment time. Selecting `REDEEM` does not reserve or spend points.

**Large values.** Use `long` for bill amounts. Compute 10% as `subtotal / 10` and 20% as `subtotal / 5`, so multiplying a large subtotal cannot overflow. Use `BigInteger` only for customer points. Each bill fits in `long`, but the statement gives no limit on points accumulated across many bills.


## 4. Validate first, then change state

**Creating a bill.** Validate the entire cart before storing anything. Only a successful creation consumes the next ID, such as `B1` or `B2`.

**Applying a discount.** Reject a missing or paid bill with `-1`. Ignore unknown codes. Add supported codes to the set and calculate the current amount.

**Paying a bill.** Recalculate the amount and require an exact match. If anything is wrong, return `"ERROR"` without changing points or bill state.

On success, deduct redeemed points, add `payable / 100` earned points, and mark the bill paid. A second payment is rejected, so points are awarded only once.

Calculate the level from the resulting balance: below 100 is `BRONZE`, below 500 is `SILVER`, below 2000 is `GOLD`, and 2000 or more is `PLATINUM`. Redemption can lower a customer's level.


## 5. Walk through one bill

Suppose a customer has 200 points from an earlier payment. A new bill has subtotal 1000, with all four codes selected.

| Step | Calculation | Amount |
| --- | --- | ---: |
| Percentage | `P20` wins over `P10`, subtract 200 | 800 |
| Flat discount | Original subtotal qualifies, subtract 100 | 700 |
| Redemption | Cap is 140, redeem 140 of the available 200 points | 560 |

Paying 560 earns 5 points. The new balance is `200 - 140 + 5 = 65`, so the receipt is:

```text
PAID|final=560|pointsEarned=5|totalPoints=65|level=BRONZE
```


## 6. Cost

Creating a bill scans its cart once, taking **O(n)** time for `n` items of bounded length. More generally, it reads each character of the item strings.

Discount calculation checks at most four codes, so the rule checks take **O(1)** time. Payment adds points arithmetic and receipt formatting. Very large point balances add their arithmetic and formatting costs.

Storage uses **O(B + C)** records for `B` bills and `C` customers, plus the space for identifiers and point values. Each bill stores at most four discount codes.


## 7. Complete code

The helper classes are separate top-level classes. Only `BillingSystem` is public, so the complete code belongs in `BillingSystem.java`.

```java
import java.util.*;
import java.math.BigInteger;

class Customer {
    BigInteger points = BigInteger.ZERO;

    String getLevel() {
        if (points.compareTo(BigInteger.valueOf(2000)) >= 0) {
            return "PLATINUM";
        }
        if (points.compareTo(BigInteger.valueOf(500)) >= 0) {
            return "GOLD";
        }
        if (points.compareTo(BigInteger.valueOf(100)) >= 0) {
            return "SILVER";
        }
        return "BRONZE";
    }
}

class Bill {
    Customer customer;
    long subtotal;
    Set<String> discountCodes = new HashSet<>();
    boolean paid = false;

    Bill(Customer customer, long subtotal) {
        this.customer = customer;
        this.subtotal = subtotal;
    }
}

class BillTotal {
    long payable;
    long redeemedPoints;

    BillTotal(long payable, long redeemedPoints) {
        this.payable = payable;
        this.redeemedPoints = redeemedPoints;
    }
}

public class BillingSystem {
    Map<String, Customer> customers = new HashMap<>();
    Map<String, Bill> bills = new HashMap<>();
    long nextBillNumber = 1;

    public BillingSystem() {
    }

    public String createBill(String customerId, List<String> cartItems) {
        if (customerId == null || customerId.isEmpty()) {
            return "ERROR";
        }

        long subtotal = getSubtotal(cartItems);
        if (subtotal < 0) {
            return "ERROR";
        }

        // Change stored data only after the whole cart is valid.
        Customer customer = customers.get(customerId);
        if (customer == null) {
            customer = new Customer();
            customers.put(customerId, customer);
        }

        String billId = "B" + nextBillNumber;
        nextBillNumber++;
        bills.put(billId, new Bill(customer, subtotal));
        return billId;
    }

    public long applyDiscount(String billId, String discountCode) {
        Bill bill = bills.get(billId);
        if (bill == null || bill.paid) {
            return -1;
        }

        if ("P10".equals(discountCode) || "P20".equals(discountCode)
                || "FLAT100".equals(discountCode)
                || "REDEEM".equals(discountCode)) {
            bill.discountCodes.add(discountCode);
        }

        // Unknown codes are ignored. Repeated codes do not stack.
        return getTotal(bill).payable;
    }

    public String payBill(String billId, long amountPaid) {
        Bill bill = bills.get(billId);
        if (bill == null || bill.paid) {
            return "ERROR";
        }

        // Another bill may have changed this customer's points.
        BillTotal total = getTotal(bill);
        if (amountPaid != total.payable) {
            return "ERROR";
        }

        long pointsEarned = total.payable / 100;
        Customer customer = bill.customer;
        customer.points = customer.points
                .subtract(BigInteger.valueOf(total.redeemedPoints))
                .add(BigInteger.valueOf(pointsEarned));
        bill.paid = true;

        return "PAID|final=" + total.payable
                + "|pointsEarned=" + pointsEarned
                + "|totalPoints=" + customer.points
                + "|level=" + customer.getLevel();
    }

    long getSubtotal(List<String> cartItems) {
        if (cartItems == null || cartItems.isEmpty() || cartItems.size() > 100000) {
            return -1;
        }

        long subtotal = 0;
        for (String item : cartItems) {
            if (item == null) {
                return -1;
            }

            // Keep empty fields so malformed items cannot slip through.
            String[] parts = item.split("\\|", -1);
            if (parts.length != 3 || parts[0].isEmpty()) {
                return -1;
            }
            if (!parts[1].matches("[+-]?[0-9]+")
                    || !parts[2].matches("[+-]?[0-9]+")) {
                return -1;
            }

            long unitPrice;
            long quantity;
            try {
                unitPrice = Long.parseLong(parts[1]);
                quantity = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                return -1;
            }

            if (unitPrice < 0 || unitPrice > 1000000000L
                    || quantity <= 0 || quantity > 1000000L) {
                return -1;
            }

            long itemTotal = unitPrice * quantity;
            if (subtotal > Long.MAX_VALUE - itemTotal) {
                return -1;
            }
            subtotal += itemTotal;
        }
        return subtotal;
    }

    BillTotal getTotal(Bill bill) {
        long percentageDiscount = 0;
        if (bill.discountCodes.contains("P20")) {
            percentageDiscount = bill.subtotal / 5;
        } else if (bill.discountCodes.contains("P10")) {
            percentageDiscount = bill.subtotal / 10;
        }

        // Dividing by 5 or 10 avoids overflow from subtotal * percent.
        long payable = bill.subtotal - percentageDiscount;
        if (bill.discountCodes.contains("FLAT100") && bill.subtotal >= 500) {
            payable -= 100;
        }
        payable = Math.max(0, payable);

        long redeemedPoints = 0;
        if (bill.discountCodes.contains("REDEEM")) {
            redeemedPoints = bill.customer.points
                    .min(BigInteger.valueOf(payable / 5)).longValue();
        }

        // Calculation only. Points are deducted inside payBill.
        return new BillTotal(payable - redeemedPoints, redeemedPoints);
    }
}
```
