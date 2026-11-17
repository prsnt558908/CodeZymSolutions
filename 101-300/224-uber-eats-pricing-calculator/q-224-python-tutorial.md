# Design Uber Eats Pricing Calculator in Python


#### Problem Statement
[https://codezym.com/question/224-uber-eats-pricing-calculator](https://codezym.com/question/224-uber-eats-pricing-calculator)


## Core Idea

An order is priced in two simple stages.


**Stage 1:** Find the price of every ordered item.
Item price = food size price + sum of its topping prices.


**Stage 2:** Add up all item prices. Then pass this total through 4 pricing rules, always in this order:

```
BOGO  ->  Surge  ->  Coupon  ->  Uber One
```


Every rule does the same kind of job: take the current amount and return a new amount. Only the logic inside is different.


So the best fit here is the **Strategy pattern**. Each rule becomes its own small class with one common method, `apply(amount, order)`. The calculator keeps these rules in a list, in the correct order, and runs them one by one.


This keeps every rule small and easy to test. A new offer later (like a delivery fee) is just one new class added to the list, without touching the old rules.


## Solution 1: Simple Approach (Everything in One Method)

The most direct way is to write all 4 rules inside `calculateTotal()`, one after another.


Assume the constructor has already saved the foods, toppings, BOGO food ids and coupons in dictionaries and sets.

```python
def calculateTotal(self, orderItems, couponCode, isUberOneMember, surgePercent):
    amount = 0
    bogoPrices = {}  # foodId -> item prices

    for line in orderItems:
        foodId = line.split(",")[0]
        price = self.itemPrice(line)  # size price + topping prices
        amount += price
        if foodId in self.bogoFoodIds:
            bogoPrices.setdefault(foodId, []).append(price)

    # 1. BOGO: the cheapest half of each BOGO food is free
    for prices in bogoPrices.values():
        prices.sort()
        amount -= sum(prices[:len(prices) // 2])

    # 2. Surge
    amount = amount + amount * surgePercent // 100

    # 3. Coupon
    if couponCode != "NONE":
        coupon = self.coupons[couponCode]
        amount = amount - min(amount * coupon.discountPercent // 100, coupon.maxDiscount)

    # 4. Uber One
    if isUberOneMember:
        amount = amount - amount * 5 // 100

    return max(0, amount)
```


This gives the correct answer. But as a design, it has a few problems.


- **One method does everything.** Parsing, BOGO, surge, coupon and membership are all mixed together. You cannot test one rule on its own.

- **Every new offer means editing this method.** Food apps keep adding offers like free delivery or happy hour discounts. Each one makes this method longer, and one small mistake can break the other offers.

- **The order of rules is hidden inside the code.** Running the rules in a different order can give a different price. Here the order depends only on where each block of code sits.


## Solution 2: Strategy Pattern (Recommended)


### One common interface for all rules

Python has no `interface` keyword, so we use an abstract base class (`ABC`).

```python
class PricingRule(ABC):
    @abstractmethod
    def apply(self, amount: int, order: Order) -> int:
        pass
```

`BogoRule`, `SurgeRule`, `CouponRule` and `UberOneRule` each extend this class with their own logic.


**The list order is the pricing order:**

```python
self.rules = [
    BogoRule(set(bogoFoodIds)),
    SurgeRule(),
    CouponRule(couponsByCode),
    UberOneRule(),
]
```


Now `calculateTotal()` only has to build the order, add up the item prices and run a simple loop:

```python
for rule in self.rules:
    amount = rule.apply(amount, order)
```


This fixes all 3 problems of Solution 1. Each rule can be tested alone, a new offer is one new class plus one line in the list, and the pricing order is visible in one place.


### Why not Decorator or Chain of Responsibility?

Both of these also sound right for "apply steps one after another". But they are not the best fit here.


**Decorator:** We could wrap each rule around the previous one, like `UberOne(Coupon(Surge(Bogo(basePrice))))`. It works, but the pricing order is now hidden inside nested brackets that you read from the inside out. Every rule must also store the wrapped object and remember to call it. A list does the same job and is much easier to read and reorder.


**Chain of Responsibility:** Here a request moves along a chain until one handler handles it. In our problem every rule must always run, so nothing should ever stop early. Each rule would also need a link to the next rule. A simple loop over a list gives the same result with less code.


### Classes and why we need them

| Class | Why we need it |
|---|---|
| `FoodItem` | A food has 3 prices. It keeps a `size -> price` dictionary, so getting the price for a size is a single lookup. |
| `OrderItem` | One cart item with its full price (size price + toppings). BOGO needs these prices to find the cheapest items. |
| `Order` | Holds everything a rule may need: items, coupon code, membership and surge. A future rule that needs new data (like delivery distance) only adds a field here. The `apply()` method stays the same for all rules. |
| `PricingRule` | The common Strategy interface. |
| `BogoRule`, `SurgeRule`, `CouponRule`, `UberOneRule` | One small class per pricing step. |
| `UberEatsPricingCalculator` | Reads the input, builds the `Order` and runs the rules in order. |


Toppings only have a price, so a plain dictionary (toppingId -> price) is enough. No class is needed for them.


### How BOGO works

BOGO is the only rule with some real logic.


1. Group item prices by food id, only for BOGO foods. We use a dictionary of lists (foodId -> list of item prices).
2. Sort each list, so the cheapest items come first.
3. If a list has `k` prices, the first `k // 2` are free. Add them up and subtract them from the amount.


A sorted list is all we need here. No special data structure is required.


### Example 1, step by step

| Step | Amount |
|---|---|
| Item prices: 800 + 900 + 950 | 2650 |
| BOGO: the cheaper burger (800) is free | 1850 |
| Surge 20%: 1850 + 370 | 2220 |
| Coupon SAVE10: min(222, 300) = 222 off | 1998 |
| Uber One 5%: 99 off | 1899 |


### Complexity

- **Constructor:** one pass over each input list.
- **calculateTotal:** `O(N log N)` for `N` ordered items, because of sorting in BOGO. Toppings of each item are read only once.


## Python Code

```python
from abc import ABC, abstractmethod
from typing import Dict, List


class FoodItem:
    """A food on the menu with one price for each size."""

    def __init__(self, foodId: str, smallPrice: int, mediumPrice: int, largePrice: int):
        self.foodId = foodId
        self.priceBySize = {"small": smallPrice, "medium": mediumPrice, "large": largePrice}

    def getPrice(self, size: str) -> int:
        return self.priceBySize[size]


class Coupon:
    """A coupon gives a percent off, but never more than maxDiscount."""

    def __init__(self, code: str, discountPercent: int, maxDiscount: int):
        self.code = code
        self.discountPercent = discountPercent
        self.maxDiscount = maxDiscount


class OrderItem:
    """One item in the cart. price = size price + all topping prices."""

    def __init__(self, foodId: str, price: int):
        self.foodId = foodId
        self.price = price


class Order:
    """Everything a pricing rule may need to know about the current order."""

    def __init__(self, items: List[OrderItem], couponCode: str, isUberOneMember: bool, surgePercent: int):
        self.items = items
        self.couponCode = couponCode
        self.isUberOneMember = isUberOneMember
        self.surgePercent = surgePercent


class PricingRule(ABC):
    """
    Strategy interface.
    Every pricing step takes the current amount and returns the new amount.
    """

    @abstractmethod
    def apply(self, amount: int, order: Order) -> int:
        pass


class BogoRule(PricingRule):
    """BOGO: for each BOGO food with k items, the cheapest k // 2 items are free."""

    def __init__(self, bogoFoodIds: set):
        self.bogoFoodIds = bogoFoodIds

    def apply(self, amount: int, order: Order) -> int:
        # foodId -> prices of all ordered items of that BOGO food
        pricesByFood = {}
        for item in order.items:
            if item.foodId in self.bogoFoodIds:
                pricesByFood.setdefault(item.foodId, []).append(item.price)

        freeAmount = 0
        for prices in pricesByFood.values():
            prices.sort()  # cheapest first
            freeCount = len(prices) // 2
            freeAmount += sum(prices[:freeCount])
        return amount - freeAmount


class SurgeRule(PricingRule):
    """Surge: amount + amount * surgePercent // 100"""

    def apply(self, amount: int, order: Order) -> int:
        return amount + amount * order.surgePercent // 100


class CouponRule(PricingRule):
    """Coupon: takes off min(amount * discountPercent // 100, maxDiscount)."""

    def __init__(self, coupons: Dict[str, Coupon]):
        self.coupons = coupons  # couponCode -> Coupon

    def apply(self, amount: int, order: Order) -> int:
        coupon = self.coupons.get(order.couponCode)
        if coupon is None:  # "NONE" means no coupon
            return amount
        discount = min(amount * coupon.discountPercent // 100, coupon.maxDiscount)
        return amount - discount


class UberOneRule(PricingRule):
    """Uber One: members get an extra 5% off."""

    def __init__(self):
        self.discountPercent = 5

    def apply(self, amount: int, order: Order) -> int:
        if not order.isUberOneMember:
            return amount
        return amount - amount * self.discountPercent // 100


class UberEatsPricingCalculator:
    """
    Prices an order in two stages:
    1. Find the price of each item (size price + toppings).
    2. Pass the total through the pricing rules, in a fixed order.
    """

    def __init__(self, foodItems: List[str], toppingPrices: List[str], bogoFoodIds: List[str], coupons: List[str]):
        self.foods = {}     # foodId -> FoodItem
        self.toppings = {}  # toppingId -> price

        # "foodId,smallPrice,mediumPrice,largePrice"
        for line in foodItems:
            foodId, small, medium, large = line.split(",")
            self.foods[foodId] = FoodItem(foodId, int(small), int(medium), int(large))

        # "toppingId,price"
        for line in toppingPrices:
            toppingId, price = line.split(",")
            self.toppings[toppingId] = int(price)

        # "couponCode,discountPercent,maxDiscount"
        couponsByCode = {}
        for line in coupons:
            code, percent, maxDiscount = line.split(",")
            couponsByCode[code] = Coupon(code, int(percent), int(maxDiscount))

        # BOGO -> Surge -> Coupon -> Uber One (list order = pricing order)
        self.rules = [
            BogoRule(set(bogoFoodIds)),
            SurgeRule(),
            CouponRule(couponsByCode),
            UberOneRule(),
        ]

    def calculateTotal(self, orderItems: List[str], couponCode: str, isUberOneMember: bool, surgePercent: int) -> int:
        order = self.buildOrder(orderItems, couponCode, isUberOneMember, surgePercent)

        amount = sum(item.price for item in order.items)
        for rule in self.rules:
            amount = rule.apply(amount, order)
        return max(0, amount)

    def buildOrder(self, orderItems: List[str], couponCode: str, isUberOneMember: bool, surgePercent: int) -> Order:
        """Turns each "foodId,size,topping1|topping2" line into an OrderItem with its full price."""
        items = []
        for line in orderItems:
            parts = line.split(",")
            foodId = parts[0]
            price = self.foods[foodId].getPrice(parts[1])

            if len(parts) > 2 and parts[2] != "NONE":
                for toppingId in parts[2].split("|"):
                    price += self.toppings[toppingId]
            items.append(OrderItem(foodId, price))
        return Order(items, couponCode, isUberOneMember, surgePercent)
```