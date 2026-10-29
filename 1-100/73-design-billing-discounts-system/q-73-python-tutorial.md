# Design a Billing and Discounts System in Python

#### Problem Statement

[https://codezym.com/question/73-design-billing-discounts-system](https://codezym.com/question/73-design-billing-discounts-system)

Keep each bill's original subtotal, remember its selected discount codes, and calculate the payable amount again in a fixed order. Change loyalty points only after payment succeeds. A map, a set, and a few small classes are enough. No extra design pattern is needed for these four fixed discount codes.


## 1. A first attempt: change the payable amount directly

When `P10` arrives, it is tempting to write:

```python
payable -= payable // 10
```

For a bill of 1000, this gives 900. Applying `P10` again gives 810, which is wrong. Repeating a code must not add another discount.

The fix is to store the selected codes in a set and always calculate from the original subtotal. This also lets `P20` replace the effect of `P10` without trying to undo earlier calculations.


## 2. Store only what the system needs

| Class or structure | Purpose |
| --- | --- |
| `Customer` | Stores the current points balance. Its level is calculated from that balance. |
| `Bill` | Stores the subtotal, a reference to its customer, selected codes, and whether it is paid. |
| `BillTotal` | Holds a calculated payable amount and the points that payment would redeem. |
| Two dictionary objects | Find customers by customer ID and bills by bill ID. |
| A `set` per bill | Stores each supported code at most once. |

All bills for the same customer refer to the same `Customer` object. We do not copy the points balance into a bill.

The cart is needed only to calculate the subtotal, so there is no need to keep every item afterward.

**Why skip Strategy?** Strategy can help when many discount rules change independently. Here, it would still need extra logic to choose the winning percentage and enforce the calculation order. One short calculation method is clearer for the given requirements.


## 3. Calculate every bill in the same order

The `get_total` method follows these steps:

1. Use `P20` if present. Otherwise, use `P10` if present. Subtract the percentage discount from the original subtotal.
2. Subtract 100 if `FLAT100` is present and the original subtotal is at least 500.
3. Keep the amount at least zero. If `REDEEM` is present, redeem the smaller of the customer's current points and `payable // 5`.
4. Return the payable amount and planned redemption without changing the customer.

Floor the **discount**, then subtract it. For subtotal 499 and `P10`, the discount is 49 and the payable amount is 450.

Another bill can change the customer's points after a preview. Always recalculate at payment time. Selecting `REDEEM` does not reserve or spend points.

**Integer arithmetic.** Use `//` for floor division. Python integers can hold large totals and accumulated points without overflow. The input validation still checks the stated limit of `2**63 - 1` for each bill subtotal.


## 4. Validate first, then change state

**Creating a bill.** Validate the entire cart before storing anything. Only a successful creation consumes the next ID, such as `B1` or `B2`.

**Applying a discount.** Reject a missing or paid bill with `-1`. Ignore unknown codes. Add supported codes to the set and calculate the current amount.

**Paying a bill.** Recalculate the amount and require an exact match. If anything is wrong, return `"ERROR"` without changing points or bill state.

On success, deduct redeemed points, add `payable // 100` earned points, and mark the bill paid. A second payment is rejected, so points are awarded only once.

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

The small data holders use dataclasses. `default_factory=set` gives each bill its own discount set. The public method names match the starter code.

```python
from dataclasses import dataclass, field
import re


@dataclass
class Customer:
    points: int = 0

    def get_level(self):
        if self.points >= 2000:
            return "PLATINUM"
        if self.points >= 500:
            return "GOLD"
        if self.points >= 100:
            return "SILVER"
        return "BRONZE"


@dataclass
class Bill:
    customer: Customer
    subtotal: int
    discount_codes: set[str] = field(default_factory=set)
    paid: bool = False


@dataclass
class BillTotal:
    payable: int
    redeemed_points: int


class BillingSystem:
    def __init__(self):
        self.customers = {}
        self.bills = {}
        self.next_bill_number = 1

    def createBill(self, customerId, cartItems):
        if not isinstance(customerId, str) or not customerId:
            return "ERROR"

        subtotal = self.get_subtotal(cartItems)
        if subtotal < 0:
            return "ERROR"

        # Change stored data only after the whole cart is valid.
        customer = self.customers.get(customerId)
        if customer is None:
            customer = Customer()
            self.customers[customerId] = customer

        bill_id = f"B{self.next_bill_number}"
        self.next_bill_number += 1
        self.bills[bill_id] = Bill(customer, subtotal)
        return bill_id

    def applyDiscount(self, billId, discountCode):
        bill = self.bills.get(billId)
        if bill is None or bill.paid:
            return -1

        if discountCode in ("P10", "P20", "FLAT100", "REDEEM"):
            bill.discount_codes.add(discountCode)

        # Unknown codes are ignored. Repeated codes do not stack.
        return self.get_total(bill).payable

    def payBill(self, billId, amountPaid):
        bill = self.bills.get(billId)
        if bill is None or bill.paid:
            return "ERROR"

        # Another bill may have changed this customer's points.
        total = self.get_total(bill)
        if amountPaid != total.payable:
            return "ERROR"

        points_earned = total.payable // 100
        customer = bill.customer
        customer.points = customer.points - total.redeemed_points + points_earned
        bill.paid = True

        return (
            f"PAID|final={total.payable}"
            f"|pointsEarned={points_earned}"
            f"|totalPoints={customer.points}"
            f"|level={customer.get_level()}"
        )

    def get_subtotal(self, cart_items):
        if not isinstance(cart_items, list) or not 1 <= len(cart_items) <= 100000:
            return -1

        subtotal = 0
        for item in cart_items:
            if not isinstance(item, str):
                return -1

            # Keep empty fields so malformed items cannot slip through.
            parts = item.split("|")
            if len(parts) != 3 or not parts[0]:
                return -1
            if not re.fullmatch(r"[+-]?[0-9]+", parts[1]) or not re.fullmatch(
                r"[+-]?[0-9]+", parts[2]
            ):
                return -1

            try:
                unit_price = int(parts[1])
                quantity = int(parts[2])
            except ValueError:
                return -1

            if not 0 <= unit_price <= 10**9 or not 1 <= quantity <= 10**6:
                return -1

            item_total = unit_price * quantity
            if subtotal > (2**63 - 1) - item_total:
                return -1
            subtotal += item_total
        return subtotal

    def get_total(self, bill):
        percentage_discount = 0
        if "P20" in bill.discount_codes:
            percentage_discount = bill.subtotal // 5
        elif "P10" in bill.discount_codes:
            percentage_discount = bill.subtotal // 10

        # Integer division gives the required floor without using floats.
        payable = bill.subtotal - percentage_discount
        if "FLAT100" in bill.discount_codes and bill.subtotal >= 500:
            payable -= 100
        payable = max(0, payable)

        redeemed_points = 0
        if "REDEEM" in bill.discount_codes:
            redeemed_points = min(bill.customer.points, payable // 5)

        # Calculation only. Points are deducted inside payBill.
        return BillTotal(payable - redeemed_points, redeemed_points)
```
