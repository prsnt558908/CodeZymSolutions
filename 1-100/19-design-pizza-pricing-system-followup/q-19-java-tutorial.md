# Design Pizza Pricing System: Follow-up in Java

#### Problem Statement

[https://codezym.com/question/19-design-pizza-pricing-system-followup](https://codezym.com/question/19-design-pizza-pricing-system-followup)

Think of a pizza as a base price with toppings added around it. The **Decorator pattern** fits this problem because each accepted topping can wrap the existing pizza and add its own cost and tax rules. A small `HashMap` remembers how many servings have already been accepted. Each wrapper stores the updated subtotal and tax rate, so reading the price stays quick even after many additions. Decorator alone is enough for these requirements. We use `BigDecimal` to keep decimal calculations exact and round only the final answer.

## Start with a simple solution

We could put everything inside `PizzaPricing`.

Store a subtotal, a tax rate, and the serving counts. Whenever `addTopping` is called, use conditions to check the topping's restrictions, calculate its cost, and update those values.

This can produce the correct answer with constant work per call. The difficulty appears when more rules arrive. The same method starts handling size limits, incompatible toppings, special prices, and tax changes. A change to one topping becomes harder to review among all the other rules.

The statement also explicitly asks for Decorator. We will keep the same calculations while moving the topping-specific behavior into small wrapper classes. This improves the organization and makes the rules easier to extend. It does not make the basic arithmetic faster.

## Understand the rules before choosing classes

The ordinary per-serving costs are 100 for cheeseburst, 50 for corn, 30 for onion, 50 for capsicum, 60 for pineapple, and 40 for mushroom.

Some toppings add extra rules.

- **Cheeseburst** increases the entire pizza's tax rate by 30 percent of that rate, once. A rate of 10 becomes 13. Small pizzas allow at most one serving in total, medium pizzas allow at most two, and large pizzas have no stated cheeseburst cap. It is rejected if mushroom is already present.
- **Mushroom** decreases the entire pizza's tax rate by 10 percent of that rate, once. A rate of 10 becomes 9. It is rejected if cheeseburst is already present.
- **Pineapple** is allowed only on medium and large pizzas.
- **Corn** costs 50 per serving on small pizzas. On medium pizzas, the first serving costs 50 and every later serving costs 40. On large pizzas, every serving costs 20.
- **Onion and capsicum** use their ordinary prices and do not change the tax rate.

The serving limits apply across all successful calls. If a medium pizza already has one cheeseburst serving, requesting two more must fail completely. We cannot accept just one of the requested servings.

Tax applies to the whole subtotal, including toppings added before and after the tax-changing topping.

## Why Decorator fits

A decorator is an object that wraps another object with the same interface and adds behavior to it.

Our `Pizza` interface exposes two values, the subtotal before tax and the current tax rate. `PlainPizza` provides these values for a pizza without toppings. A topping decorator also implements `Pizza`, so the rest of the program can use either object in exactly the same way.

For example, adding corn creates a corn wrapper around the plain pizza. Adding cheeseburst next creates a cheeseburst wrapper around that corn wrapper. The outermost object represents the complete accepted pizza.

There is no need for a separate class for every combination, such as corn with onion or corn with onion and cheeseburst. We compose the individual additions as calls arrive.

Strategy could be useful when selecting one complete pricing algorithm from several alternatives. Here, multiple toppings accumulate on the same pizza, and Decorator directly represents that behavior. Adding a separate Strategy layer is unnecessary for the given rules.

### The important classes and data structure

**`PizzaPricing`** is the public class required by the judge. It chooses a topping wrapper, checks whether the addition is allowed, and commits successful changes. Its three public method signatures match the supplied Java stub.

**`Pizza` and `PlainPizza`** give us a shared view of the price and a starting object. `PlainPizza` starts with the constructor's base price and tax percentage.

**`ToppingDecorator`** holds the pizza it wraps. It adds the new topping cost to the previous subtotal and multiplies the previous tax rate by the appropriate factor. It stores both results without rounding them. Its `isAllowed()` method returns `true` by default, while toppings with restrictions override that method.

**The concrete decorators** keep the special behavior close to the topping. Cheeseburst handles its cap, incompatibility, and tax increase. Mushroom handles its incompatibility and tax decrease. Pineapple handles its size restriction. Corn calculates its size-dependent cost. Onion and capsicum share `FixedPriceDecorator` because only their unit prices differ.

**`Map<String, Long> servings`** remembers accepted quantities. We need quantities rather than just a set of topping names because cheeseburst has cumulative serving limits. The same map also answers whether corn, mushroom, or cheeseburst is already present. There are at most six entries. Using `long` for accumulated counts avoids overflowing an `int` when quantities are added across calls.

The small switch in `createDecorator` only selects and constructs the appropriate wrapper. The special pricing and acceptance rules live in the wrapper classes.

## Add a topping without changing state on failure

The order of work in `addTopping` matters.

1. Read the existing serving counts and build a candidate wrapper around the current pizza.
2. Ask the candidate whether the entire addition is allowed.
3. If it fails, return `false` without changing the serving map or the current pizza.
4. If it succeeds, increase the accepted count and make the candidate the current pizza.

Creating a candidate calculates new values inside a separate object. It does not modify the wrapped pizza. Even if a rejected cheeseburst candidate calculated a higher tax rate, that candidate is discarded and the accepted pizza keeps its original rate.

This also prevents a failed request from affecting later decisions. For example, after two cheeseburst servings are rejected on a small pizza, mushroom can still be added because the pizza has no accepted cheeseburst.

The constructor inputs and positive serving counts are guaranteed by the statement. The implementation also returns `false` for unknown topping names, a null name, or a nonpositive count, without changing the pizza.

## Handle the rules that depend on earlier calls

### Apply each tax change once

Before building a cheeseburst decorator, check the previously accepted cheeseburst count.

If that count is zero, the decorator multiplies the tax rate by `1.30`. Otherwise, it multiplies by `1`, leaving the rate unchanged.

Mushroom follows the same idea with a factor of `0.90`. Adding three mushroom servings in one call still changes the tax rate only once. Adding more mushroom later does not change it again.

Cheeseburst and mushroom cannot coexist, so a valid pizza never receives both adjustments.

### Remember the first corn serving across calls

For a medium pizza, charging 40 for every serving leaves the first serving 10 units short of its required price of 50.

Therefore, the cost of a new batch is `40 × servingsCount`, plus 10 only if the pizza has no corn yet.

For example, adding one corn serving and later adding two more costs `50 + 80 = 130`. Adding three corn servings in a single call also costs `120 + 10 = 130`.

The map is updated only after the addition succeeds, so the decorator receives the count from before the current request.

## Keep decimal calculations exact

The price calculation has three steps.

1. Subtotal is the base price plus all accepted topping costs.
2. Tax amount is `subtotal × taxRate / 100`.
3. Final price is subtotal plus tax amount, rounded half up to an integer.

For a base tax rate of 12, cheeseburst changes the rate to `12 × 1.30 = 15.6`. Keeping the tax rate as an integer would lose the fractional part.

We use `BigDecimal`, Java's standard decimal number class. Factors such as `1.30` and `0.90` are created from strings, which preserves those decimal values exactly. Multiplication and addition use their exact forms without a rounding context.

The code uses `movePointLeft(2)` to divide a percentage by 100. Only `getFinalPrice()` calls `setScale(0, RoundingMode.HALF_UP)`.

For example, the large-pizza example has subtotal 1130 and tax rate 15. Its tax is 169.5, giving a final value of 1299.5. Half-up rounding returns **1300**.

Topping cost calculations also convert the unit price and count to `BigDecimal` before multiplying, which avoids overflowing an intermediate `int` product.

For an exceptionally large total, the statement's displayed Java cast returns `Integer.MAX_VALUE`. The final conversion preserves that behavior. Directly calling `BigDecimal.intValue()` on an out-of-range value could instead wrap around.

## Keep price queries fast

A basic decorator implementation might ask its wrapped object to recalculate the price on every query. After many additions, that would repeatedly walk through all earlier wrappers.

Our accepted wrapper objects never change. When a wrapper is created, it reads the previous wrapper's stored subtotal and tax rate, computes its own values, and stores them too.

As a result, `getFinalPrice()` reads only the outermost wrapper. It never recursively walks the chain and never modifies the pizza. Repeated calls return the same result until a topping is successfully added.

## Walk through the medium-pizza sample

Start with base price 500, tax rate 12, and size `"medium"`. Without toppings, the final price is `500 + 60 = 560`.

1. Add one corn serving. It costs 50, so the subtotal becomes 550 and the final price is **616**.
2. Add two more corn servings. Corn is already present, so these cost 80 together. The subtotal becomes 630. The final value is 705.6, which rounds to **706**.
3. Add one cheeseburst serving. The subtotal becomes 730 and the tax rate becomes 15.6. The final value is 843.88, which rounds to **844**.
4. Add another cheeseburst serving. The subtotal becomes 830, while the tax rate stays 15.6. The final value is 959.48, which rounds to **959**.
5. Request a third cheeseburst serving. The medium-size cap rejects it, so the price stays **959**.
6. Add one pineapple serving. It costs 60, making the subtotal 890. The final value is 1028.84, which rounds to **1029**.
7. Request mushroom. Existing cheeseburst blocks it, so the price stays **1029**.

The sample continues with another corn serving costing 40 and three capsicum servings costing 150. The final subtotal is 1080, and the final value is `1080 × 1.156 = 1248.48`, giving **1248**.

## Why the solution is correct

Initially, the serving map is empty, the subtotal equals the base price, and the tax rate equals the constructor's percentage. These values describe the pizza correctly.

Assume they are correct before a topping request. The candidate uses the accepted counts to check the size cap and incompatibility rules. It calculates the correct batch cost, including the medium-pizza corn adjustment, and applies a tax multiplier only when the relevant topping was previously absent.

If the request is rejected, neither the current pizza nor its accepted counts change. If it is accepted, the new wrapper stores exactly the updated subtotal and tax rate, and the map records the accepted servings. The stored values therefore remain correct after either outcome.

Finally, `getFinalPrice()` applies the current rate to the entire accepted subtotal and rounds once using the required half-up rule. Therefore, it returns the required price without changing future behavior.

## Extending the rules

To add a new topping, create a decorator and add a construction case in `createDecorator`. The shared commit logic and final-price calculation stay the same.

To add a pineapple serving cap, pass its existing count into `PineappleDecorator` and extend that decorator's acceptance check. A future rule involving another topping can similarly receive the relevant accepted count from the map.

This keeps new business rules close to the topping they affect. A future discount or promotion must also define when it applies and whether it changes the subtotal or the tax rate before its calculation is added.

## Complexity

Each `addTopping` call performs a fixed number of map lookups, rule checks, and decimal calculations. Its expected time is **O(1)**. Each `getFinalPrice` call also takes **O(1)** because the current wrapper already stores the subtotal and tax rate.

Let `A` be the number of successful topping additions. Every successful call creates one wrapper, even if that call adds many servings. The wrapper chain uses **O(A)** space. The serving map uses **O(1)** space because the catalog has six toppings. A rejected candidate adds no retained wrapper.

These time bounds count arithmetic operations. The cost of `BigDecimal` arithmetic also depends on the number of digits in its values.


## Complete Java solution


```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class PizzaPricing {
    private final String size;
    private final Map<String, Long> servings = new HashMap<>();
    private Pizza pizza;

    public PizzaPricing(int basePrice, int taxPercentage, String size) {
        this.size = size;
        this.pizza = new PlainPizza(basePrice, taxPercentage);
    }

    public boolean addTopping(String topping, int servingsCount) {
        if (topping == null || servingsCount <= 0) {
            return false;
        }

        ToppingDecorator candidate = createDecorator(topping, servingsCount);
        if (candidate == null || !candidate.isAllowed()) {
            return false;
        }

        // Commit only after every rule has passed. Never apply a partial batch.
        servings.put(topping, servingsOf(topping) + servingsCount);
        pizza = candidate;
        return true;
    }

    public int getFinalPrice() {
        BigDecimal taxAmount = pizza.getSubtotal()
                .multiply(pizza.getTaxRate())
                .movePointLeft(2);
        BigDecimal rounded = pizza.getSubtotal()
                .add(taxAmount)
                .setScale(0, RoundingMode.HALF_UP);

        // Preserve the stated Java int cast behavior for very large totals.
        return rounded.min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
    }

    private long servingsOf(String topping) {
        return servings.getOrDefault(topping, 0L);
    }

    private ToppingDecorator createDecorator(String topping, int count) {
        switch (topping) {
            case "cheeseburst":
                return new CheeseburstDecorator(
                        pizza, count, size, servingsOf("cheeseburst"),
                        servingsOf("mushroom") > 0);
            case "mushroom":
                return new MushroomDecorator(
                        pizza, count, servingsOf("mushroom") == 0,
                        servingsOf("cheeseburst") > 0);
            case "pineapple":
                return new PineappleDecorator(pizza, count, size);
            case "corn":
                return new CornDecorator(
                        pizza, count, size, servingsOf("corn") == 0);
            case "onion":
                return new FixedPriceDecorator(pizza, count, 30);
            case "capsicum":
                return new FixedPriceDecorator(pizza, count, 50);
            default:
                return null;
        }
    }

    private static BigDecimal cost(int unitPrice, int count) {
        // Convert before multiplying so int multiplication cannot overflow.
        return BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(count));
    }

    // Both the original pizza and every wrapper expose the same operations.
    private interface Pizza {
        BigDecimal getSubtotal();

        BigDecimal getTaxRate();
    }

    private static final class PlainPizza implements Pizza {
        private final BigDecimal subtotal;
        private final BigDecimal taxRate;

        private PlainPizza(int basePrice, int taxPercentage) {
            this.subtotal = BigDecimal.valueOf(basePrice);
            this.taxRate = BigDecimal.valueOf(taxPercentage);
        }

        @Override
        public BigDecimal getSubtotal() {
            return subtotal;
        }

        @Override
        public BigDecimal getTaxRate() {
            return taxRate;
        }
    }

    private abstract static class ToppingDecorator implements Pizza {
        private final Pizza wrapped;
        private final BigDecimal subtotal;
        private final BigDecimal taxRate;

        protected ToppingDecorator(Pizza wrapped, BigDecimal extraCost,
                                   BigDecimal taxMultiplier) {
            this.wrapped = wrapped;
            // Cache exact results once. Reads never walk the wrapper chain.
            this.subtotal = this.wrapped.getSubtotal().add(extraCost);
            this.taxRate = this.wrapped.getTaxRate().multiply(taxMultiplier);
        }

        public boolean isAllowed() {
            return true;
        }

        @Override
        public BigDecimal getSubtotal() {
            return subtotal;
        }

        @Override
        public BigDecimal getTaxRate() {
            return taxRate;
        }
    }

    private static final class FixedPriceDecorator extends ToppingDecorator {
        private FixedPriceDecorator(Pizza wrapped, int count, int unitPrice) {
            super(wrapped, cost(unitPrice, count), BigDecimal.ONE);
        }
    }

    private static final class CheeseburstDecorator extends ToppingDecorator {
        private static final BigDecimal TAX_MULTIPLIER = new BigDecimal("1.30");
        private final boolean allowed;

        private CheeseburstDecorator(Pizza wrapped, int count, String size,
                                    long previousCount, boolean mushroomPresent) {
            super(wrapped, cost(100, count),
                    previousCount == 0 ? TAX_MULTIPLIER : BigDecimal.ONE);

            long totalCount = previousCount + count;
            boolean withinCap = true;
            if ("small".equals(size)) {
                withinCap = totalCount <= 1;
            } else if ("medium".equals(size)) {
                withinCap = totalCount <= 2;
            }
            this.allowed = !mushroomPresent && withinCap;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }
    }

    private static final class MushroomDecorator extends ToppingDecorator {
        private static final BigDecimal TAX_MULTIPLIER = new BigDecimal("0.90");
        private final boolean allowed;

        private MushroomDecorator(Pizza wrapped, int count, boolean firstAddition,
                                  boolean cheeseburstPresent) {
            super(wrapped, cost(40, count),
                    firstAddition ? TAX_MULTIPLIER : BigDecimal.ONE);
            this.allowed = !cheeseburstPresent;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }
    }

    private static final class PineappleDecorator extends ToppingDecorator {
        private final boolean allowed;

        private PineappleDecorator(Pizza wrapped, int count, String size) {
            super(wrapped, cost(60, count), BigDecimal.ONE);
            this.allowed = !"small".equals(size);
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }
    }

    private static final class CornDecorator extends ToppingDecorator {
        private CornDecorator(Pizza wrapped, int count, String size,
                              boolean firstAddition) {
            super(wrapped, cornCost(count, size, firstAddition), BigDecimal.ONE);
        }

        private static BigDecimal cornCost(int count, String size,
                                           boolean firstAddition) {
            if ("large".equals(size)) {
                return cost(20, count);
            }
            if ("medium".equals(size)) {
                // Only the first corn serving on the whole pizza costs 10 extra.
                BigDecimal extraForFirst = firstAddition
                        ? BigDecimal.TEN : BigDecimal.ZERO;
                return cost(40, count).add(extraForFirst);
            }
            return cost(50, count);
        }
    }
}
```
