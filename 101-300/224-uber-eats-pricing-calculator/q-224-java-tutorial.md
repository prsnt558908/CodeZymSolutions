# Design Uber Eats Pricing Calculator in Java


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


Assume the constructor has already saved the foods, toppings, BOGO food ids and coupons in maps and sets.

```java
public int calculateTotal(List<String> orderItems, String couponCode,
                          boolean isUberOneMember, int surgePercent) {
    long amount = 0;
    Map<String, List<Long>> bogoPrices = new HashMap<>(); // foodId -> item prices

    for (String line : orderItems) {
        String foodId = line.split(",")[0];
        long price = itemPrice(line); // size price + topping prices
        amount += price;
        if (bogoFoodIds.contains(foodId)) {
            bogoPrices.putIfAbsent(foodId, new ArrayList<>());
            bogoPrices.get(foodId).add(price);
        }
    }

    // 1. BOGO: the cheapest half of each BOGO food is free
    for (List<Long> prices : bogoPrices.values()) {
        Collections.sort(prices);
        for (int i = 0; i < prices.size() / 2; i++) {
            amount -= prices.get(i);
        }
    }

    // 2. Surge
    amount = amount + amount * surgePercent / 100;

    // 3. Coupon
    if (!couponCode.equals("NONE")) {
        Coupon coupon = coupons.get(couponCode);
        amount = amount - Math.min(amount * coupon.discountPercent / 100, coupon.maxDiscount);
    }

    // 4. Uber One
    if (isUberOneMember) {
        amount = amount - amount * 5 / 100;
    }
    return (int) Math.max(0, amount);
}
```


This gives the correct answer. But as a design, it has a few problems.


- **One method does everything.** Parsing, BOGO, surge, coupon and membership are all mixed together. You cannot test one rule on its own.

- **Every new offer means editing this method.** Food apps keep adding offers like free delivery or happy hour discounts. Each one makes this method longer, and one small mistake can break the other offers.

- **The order of rules is hidden inside the code.** Running the rules in a different order can give a different price. Here the order depends only on where each block of code sits.


## Solution 2: Strategy Pattern (Recommended)


### One common interface for all rules

```java
interface PricingRule {
    long apply(long amount, Order order);
}
```

`BogoRule`, `SurgeRule`, `CouponRule` and `UberOneRule` each implement this interface with their own logic.


**The list order is the pricing order:**

```java
rules.add(new BogoRule(new HashSet<>(bogoFoodIds)));
rules.add(new SurgeRule());
rules.add(new CouponRule(couponsByCode));
rules.add(new UberOneRule());
```


Now `calculateTotal()` only has to build the order, add up the item prices and run a simple loop:

```java
for (PricingRule rule : rules) {
    amount = rule.apply(amount, order);
}
```


This fixes all 3 problems of Solution 1. Each rule can be tested alone, a new offer is one new class plus one line in the list, and the pricing order is visible in one place.


### Why not Decorator or Chain of Responsibility?

Both of these also sound right for "apply steps one after another". But they are not the best fit here.


**Decorator:** We could wrap each rule around the previous one, like `UberOne(Coupon(Surge(Bogo(basePrice))))`. It works, but the pricing order is now hidden inside nested brackets that you read from the inside out. Every rule must also store the wrapped object and remember to call it. A list does the same job and is much easier to read and reorder.


**Chain of Responsibility:** Here a request moves along a chain until one handler handles it. In our problem every rule must always run, so nothing should ever stop early. Each rule would also need a link to the next rule. A simple loop over a list gives the same result with less code.


### Classes and why we need them

| Class | Why we need it |
|---|---|
| `FoodItem` | A food has 3 prices. It keeps a `size -> price` map, so getting the price for a size is a single lookup. |
| `OrderItem` | One cart item with its full price (size price + toppings). BOGO needs these prices to find the cheapest items. |
| `Order` | Holds everything a rule may need: items, coupon code, membership and surge. A future rule that needs new data (like delivery distance) only adds a field here. The `apply()` method stays the same for all rules. |
| `PricingRule` | The common Strategy interface. |
| `BogoRule`, `SurgeRule`, `CouponRule`, `UberOneRule` | One small class per pricing step. |
| `UberEatsPricingCalculator` | Reads the input, builds the `Order` and runs the rules in order. |


Toppings only have a price, so a plain `Map<String, Long>` (toppingId -> price) is enough. No class is needed for them.


### How BOGO works

BOGO is the only rule with some real logic.


1. Group item prices by food id, only for BOGO foods. We use a `Map<String, List<Long>>` (foodId -> list of item prices).
2. Sort each list, so the cheapest items come first.
3. If a list has `k` prices, the first `k / 2` are free. Add them up and subtract them from the amount.


A sorted list is all we need here. No special data structure is required.


### Example 1, step by step

| Step | Amount |
|---|---|
| Item prices: 800 + 900 + 950 | 2650 |
| BOGO: the cheaper burger (800) is free | 1850 |
| Surge 20%: 1850 + 370 | 2220 |
| Coupon SAVE10: min(222, 300) = 222 off | 1998 |
| Uber One 5%: 99 off | 1899 |


### Why `long` and not `int`?

The final answer fits in an `int`, but values in the middle may not. For example, `amount * surgePercent` with a surge of up to 500% can easily go above the `int` limit.


So all money math is done in `long`, and we convert to `int` only at the very end.


### Complexity

- **Constructor:** one pass over each input list.
- **calculateTotal:** `O(N log N)` for `N` ordered items, because of sorting in BOGO. Toppings of each item are read only once.


## Java Code

```java
import java.util.*;

/** A food on the menu with one price for each size. */
class FoodItem {
    String foodId;
    Map<String, Long> priceBySize = new HashMap<>(); // "small" / "medium" / "large" -> price

    FoodItem(String foodId, long smallPrice, long mediumPrice, long largePrice) {
        this.foodId = foodId;
        priceBySize.put("small", smallPrice);
        priceBySize.put("medium", mediumPrice);
        priceBySize.put("large", largePrice);
    }

    long getPrice(String size) {
        return priceBySize.get(size);
    }
}

/** A coupon gives a percent off, but never more than maxDiscount. */
class Coupon {
    String code;
    int discountPercent;
    long maxDiscount;

    Coupon(String code, int discountPercent, long maxDiscount) {
        this.code = code;
        this.discountPercent = discountPercent;
        this.maxDiscount = maxDiscount;
    }
}

/** One item in the cart. price = size price + all topping prices. */
class OrderItem {
    String foodId;
    long price;

    OrderItem(String foodId, long price) {
        this.foodId = foodId;
        this.price = price;
    }
}

/** Everything a pricing rule may need to know about the current order. */
class Order {
    List<OrderItem> items;
    String couponCode;
    boolean isUberOneMember;
    int surgePercent;

    Order(List<OrderItem> items, String couponCode, boolean isUberOneMember, int surgePercent) {
        this.items = items;
        this.couponCode = couponCode;
        this.isUberOneMember = isUberOneMember;
        this.surgePercent = surgePercent;
    }
}

/**
 * Strategy interface.
 * Every pricing step takes the current amount and returns the new amount.
 */
interface PricingRule {
    long apply(long amount, Order order);
}

/** BOGO: for each BOGO food with k items, the cheapest k / 2 items are free. */
class BogoRule implements PricingRule {
    Set<String> bogoFoodIds;

    BogoRule(Set<String> bogoFoodIds) {
        this.bogoFoodIds = bogoFoodIds;
    }

    public long apply(long amount, Order order) {
        // foodId -> prices of all ordered items of that BOGO food
        Map<String, List<Long>> pricesByFood = new HashMap<>();
        for (OrderItem item : order.items) {
            if (bogoFoodIds.contains(item.foodId)) {
                pricesByFood.putIfAbsent(item.foodId, new ArrayList<>());
                pricesByFood.get(item.foodId).add(item.price);
            }
        }

        long freeAmount = 0;
        for (List<Long> prices : pricesByFood.values()) {
            Collections.sort(prices); // cheapest first
            int freeCount = prices.size() / 2;
            for (int i = 0; i < freeCount; i++) {
                freeAmount += prices.get(i);
            }
        }
        return amount - freeAmount;
    }
}

/** Surge: amount + amount * surgePercent / 100 */
class SurgeRule implements PricingRule {
    public long apply(long amount, Order order) {
        return amount + amount * order.surgePercent / 100;
    }
}

/** Coupon: takes off min(amount * discountPercent / 100, maxDiscount). */
class CouponRule implements PricingRule {
    Map<String, Coupon> coupons; // couponCode -> coupon

    CouponRule(Map<String, Coupon> coupons) {
        this.coupons = coupons;
    }

    public long apply(long amount, Order order) {
        Coupon coupon = coupons.get(order.couponCode);
        if (coupon == null) { // "NONE" means no coupon
            return amount;
        }
        long discount = Math.min(amount * coupon.discountPercent / 100, coupon.maxDiscount);
        return amount - discount;
    }
}

/** Uber One: members get an extra 5% off. */
class UberOneRule implements PricingRule {
    int discountPercent = 5;

    public long apply(long amount, Order order) {
        if (!order.isUberOneMember) {
            return amount;
        }
        return amount - amount * discountPercent / 100;
    }
}

/**
 * Prices an order in two stages:
 * 1. Find the price of each item (size price + toppings).
 * 2. Pass the total through the pricing rules, in a fixed order.
 */
public class UberEatsPricingCalculator {

    Map<String, FoodItem> foods = new HashMap<>(); // foodId -> food
    Map<String, Long> toppings = new HashMap<>();  // toppingId -> price
    List<PricingRule> rules = new ArrayList<>();   // list order = pricing order

    public UberEatsPricingCalculator(List<String> foodItems, List<String> toppingPrices,
                                     List<String> bogoFoodIds, List<String> coupons) {
        // "foodId,smallPrice,mediumPrice,largePrice"
        for (String line : foodItems) {
            String[] parts = line.split(",");
            foods.put(parts[0], new FoodItem(parts[0], Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]), Long.parseLong(parts[3])));
        }

        // "toppingId,price"
        for (String line : toppingPrices) {
            String[] parts = line.split(",");
            toppings.put(parts[0], Long.parseLong(parts[1]));
        }

        // "couponCode,discountPercent,maxDiscount"
        Map<String, Coupon> couponsByCode = new HashMap<>();
        for (String line : coupons) {
            String[] parts = line.split(",");
            couponsByCode.put(parts[0], new Coupon(parts[0], Integer.parseInt(parts[1]),
                    Long.parseLong(parts[2])));
        }

        // BOGO -> Surge -> Coupon -> Uber One
        rules.add(new BogoRule(new HashSet<>(bogoFoodIds)));
        rules.add(new SurgeRule());
        rules.add(new CouponRule(couponsByCode));
        rules.add(new UberOneRule());
    }

    public int calculateTotal(List<String> orderItems, String couponCode,
                              boolean isUberOneMember, int surgePercent) {
        Order order = buildOrder(orderItems, couponCode, isUberOneMember, surgePercent);

        long amount = 0;
        for (OrderItem item : order.items) {
            amount += item.price;
        }
        for (PricingRule rule : rules) {
            amount = rule.apply(amount, order);
        }
        return (int) Math.max(0, amount);
    }

    /** Turns each "foodId,size,topping1|topping2" line into an OrderItem with its full price. */
    Order buildOrder(List<String> orderItems, String couponCode,
                     boolean isUberOneMember, int surgePercent) {
        List<OrderItem> items = new ArrayList<>();
        for (String line : orderItems) {
            String[] parts = line.split(",");
            String foodId = parts[0];
            long price = foods.get(foodId).getPrice(parts[1]);

            if (parts.length > 2 && !parts[2].equals("NONE")) {
                for (String toppingId : parts[2].split("\\|")) { // '|' must be escaped in split()
                    price += toppings.get(toppingId);
                }
            }
            items.add(new OrderItem(foodId, price));
        }
        return new Order(items, couponCode, isUberOneMember, surgePercent);
    }
}
```