# Design Pizza Pricing System in Java

#### Problem Statement
[https://codezym.com/question/18-design-pizza-pricing-system](https://codezym.com/question/18-design-pizza-pricing-system)

The core idea is to store the number of servings of each topping in a map, and calculate the price from these counts every time `getFinalPrice()` is called.

We first solve it with plain code in one class. Then we improve it by giving each kind of rule its own small class: validators decide whether a topping can be added, the **Strategy** design pattern prices each topping, and a simple `TaxCalculator` class works out the tax rate.

Strategy is the only design pattern we need. The three rules change three different things, so each one gets its own small class, and most new rules can be added later without touching the old ones. Decorator, the textbook pizza pattern, is not needed here, as explained below.

## Understand the Rules First

**1. The cheeseburst discount counts every serving on the pizza.**

The first serving costs 100 and every later serving costs 70. Adding 1 serving and then 2 more must cost the same as adding 3 at once: `100 + 70 + 70 = 240`.

**2. The cheeseburst tax uplift happens only once.**

If the pizza has any cheeseburst, the tax rate becomes rate + 30% of rate. A 15% tax becomes 19.5% and stays 19.5%, however many cheeseburst servings are added. The new rate applies to the whole subtotal, even to toppings added before the cheeseburst.

**3. Mushroom and cheeseburst block each other.**

Whichever is added first blocks the other. A blocked call returns `false` and changes nothing.

Both cheeseburst rules depend only on what is on the pizza right now, not on the order of calls. This is why we calculate the price from the stored servings instead of keeping a running total.

Here is one pizza going through all three rules:

| Call | Returns | Subtotal | Tax rate | `getFinalPrice()` |
|---|---|---|---|---|
| `new PizzaPricing(500, 15, "medium")` | | 500 | 15% | 575 |
| `addTopping("onion", 1)` | `true` | 530 | 15% | 609.5 rounds to 610 |
| `addTopping("cheeseburst", 1)` | `true` | 630 | 19.5% | 752.85 rounds to 753 |
| `addTopping("cheeseburst", 2)` | `true` | 770 | 19.5% | 920.15 rounds to 920 |
| `addTopping("mushroom", 1)` | `false` | 770 | 19.5% | 920 |

The second cheeseburst call adds `70 + 70`, not `100 + 70`, and the tax rate is raised only once.

## Solution 1: Simple Approach Without Design Patterns

We keep everything inside `PizzaPricing` and use two maps.

**`prices`** maps a topping name to the price of one serving. It also tells us which topping names are valid.

**`servings`** maps a topping name to the total servings added so far. We only need the total for each topping, not the history of calls, so a map is enough. It also tells us instantly whether cheeseburst or mushroom is on the pizza.

### addTopping()

1. Reject unknown toppings and serving counts that are not positive.
2. Reject mushroom if cheeseburst is present, and cheeseburst if mushroom is present.
3. Only now add the servings to the map. A rejected call returns before this step, so nothing changes.

### getFinalPrice()

1. **Subtotal:** the base price plus the cost of each topping. Cheeseburst costs `100 + 70 * (count - 1)`, every other topping costs `price * count`.
2. **Tax rate:** `taxPercentage`, multiplied by 1.3 if cheeseburst is present. It is worked out from the original rate on every call, so the uplift can never be applied twice.
3. **Final price:** `subtotal + subtotal * taxRate / 100`, rounded with `(int) (x + 0.5)` as the problem asks.

**Rounding tip:** multiply by the tax rate first and divide by 100 after. A `double` cannot store a number like 2.01 exactly.

For example, a subtotal of 150 at 201% tax gives a final price of exactly 451.5, which should round up to 452. If we divide first, `201 / 100.0` is stored as 2.00999..., the price becomes 451.49999... and it rounds down to 451.

`addTopping()` runs in O(1) time. `getFinalPrice()` runs in O(k) time, where k is the number of different toppings on the pizza, at most 6. Neither depends on how many servings were added.

### Java Code

```java
import java.util.HashMap;
import java.util.Map;

public class PizzaPricing {
    int basePrice;
    int taxPercentage;

    // topping name -> price of one serving
    Map<String, Integer> prices = new HashMap<>();

    // topping name -> total servings added so far
    Map<String, Integer> servings = new HashMap<>();

    public PizzaPricing(int basePrice, int taxPercentage, String size) {
        // size does not change the price under the current rules
        this.basePrice = basePrice;
        this.taxPercentage = taxPercentage;

        prices.put("cheeseburst", 100);
        prices.put("corn", 50);
        prices.put("onion", 30);
        prices.put("capsicum", 50);
        prices.put("pineapple", 60);
        prices.put("mushroom", 40);
    }

    public boolean addTopping(String topping, int servingsCount) {
        if (servingsCount <= 0 || !prices.containsKey(topping)) {
            return false;
        }
        // mushroom and cheeseburst can never be on the same pizza
        if (topping.equals("mushroom") && servings.containsKey("cheeseburst")) {
            return false;
        }
        if (topping.equals("cheeseburst") && servings.containsKey("mushroom")) {
            return false;
        }
        // reached only when every rule allows it, so a rejected call changes nothing
        servings.put(topping, servings.getOrDefault(topping, 0) + servingsCount);
        return true;
    }

    public int getFinalPrice() {
        int subtotal = basePrice;
        for (String topping : servings.keySet()) {
            int count = servings.get(topping);
            if (topping.equals("cheeseburst")) {
                subtotal += 100 + 70 * (count - 1); // first serving 100, each extra 70
            } else {
                subtotal += prices.get(topping) * count;
            }
        }

        double taxRate = taxPercentage;
        if (servings.containsKey("cheeseburst")) {
            taxRate = taxRate * 1.3; // one-time uplift of 30% of the current rate
        }

        // multiply before dividing by 100, so an exact .5 price is not stored as .4999
        double finalPrice = subtotal + subtotal * taxRate / 100;
        return (int) (finalPrice + 0.5);
    }
}
```

### Pros and Cons

**Pros**

- Short and easy to read. All the rules are visible in two methods.
- Easy to debug, since there is only one place to look.

**Cons**

- Every new rule means editing `addTopping()` or `getFinalPrice()`, code that already works.
- Rules get mixed together. Cheeseburst is already checked in four places. With ten rules, both methods turn into long if-else chains.

## Solution 2: Validators, Price Strategies and a Tax Calculator

### How This Approach Helps

The problem says more rules will come later, like size-based caps, combos and promos. In Solution 1, each of them becomes one more if-else inside methods that already work.

Look closely at the three rules. Each one changes a different thing:

| Rule | What it changes | Class |
|---|---|---|
| Mushroom and cheeseburst block each other | Whether a topping can be added | `HealthValidator` |
| Cheeseburst volume discount | The cost of one topping | `CheeseburstPriceStrategy` |
| Cheeseburst tax uplift | The tax rate | `TaxCalculator` |

So each rule gets its own small class, and `PizzaPricing` only runs them. Most new rules are just a new class plus one line in the constructor. The existing rules stay untouched.

### Validators: Can This Topping Be Added?

`ToppingValidator` has one method, `canAdd()`. `HealthValidator` returns `false` when mushroom meets cheeseburst, in either order.

`addTopping()` asks every validator in the `validators` list. If any of them says no, it returns `false` before touching the `servings` map, so a rejected call changes nothing.

A validator only says yes or no, so a simple list of validators is enough.

### Strategy: What Do the Servings of One Topping Cost?

Pricing a topping is one job that can be done in different ways. Picking the right way at runtime is exactly what the Strategy pattern is for.

- `DefaultPriceStrategy`: servings multiplied by the catalog price.
- `CheeseburstPriceStrategy`: the first serving at the catalog price, every extra serving at 70.

The `priceStrategies` map links a topping name to its strategy, and toppings that are not in the map use the default. So `getFinalPrice()` needs no if-else for each topping.

The strategy always gets the total servings from the `servings` map. That is why adding 1 and then 2 cheeseburst costs the same as adding 3 at once.

### TaxCalculator: What Is the Tax Rate?

`TaxCalculator` starts from the original tax percentage and raises it by 30% if the pizza has cheeseburst. The rate is worked out from the original percentage on every call, so the uplift can never be applied twice.

This is a plain class, not a design pattern. There is only one tax rule, so one small class is enough. Keeping it out of `PizzaPricing` still gives future tax rules an obvious place to go.

### Why Not Other Design Patterns?

**Decorator.** This is the textbook pizza pattern: each topping wraps the pizza and adds its own price. It does not fit here because our rules need to see the whole pizza. The discount needs the total cheeseburst count across all calls, and the health rule needs to know whether mushroom is anywhere on the pizza. A wrapper only sees what is inside it, and every `addTopping()` call would add one more wrapper.

We could wrap just the tax rate instead, with a cheeseburst decorator around a base tax calculator. But with one tax rule, that is two classes doing the job of one. If many tax rules pile up later, `TaxCalculator` can be split into decorators then.

**State.** It is tempting to treat "has cheeseburst" and "has mushroom" as states of the pizza, since they change the tax rate and the allowed toppings. But every new rule like this multiplies the number of states, and each state class would repeat the pricing code. Checking the `servings` map is much simpler.

### Adding New Rules Later

| New rule | What to add |
|---|---|
| Size-based cap, like at most 5 servings on a small pizza | A new `ToppingValidator` that reads `pizza.size`, added to `validators` |
| A new topping deal, like every third corn free | A new `PriceStrategy`, added to `priceStrategies` |
| A new tax rule | One more check in `TaxCalculator.getTaxRate()` |
| A combo or promo on the whole pizza | A `DiscountCalculator` class, like `TaxCalculator`, that adjusts the subtotal before tax is added |

### Time and Space Complexity

- `addTopping()`: O(v), where v is the number of validators. Here v is 1.
- `getFinalPrice()`: O(k), where k is the number of different toppings on the pizza, at most 6.
- Space: O(k) for the `servings` map, plus a few small fixed objects per pizza.

### Java Code

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PizzaPricing {
    int basePrice;
    int taxPercentage;
    String size; // not used by today's rules, kept for future size-based rules

    // topping name -> price of one serving
    Map<String, Integer> prices = new HashMap<>();

    // topping name -> total servings added so far
    Map<String, Integer> servings = new HashMap<>();

    // toppings with special pricing. Every other topping uses defaultStrategy.
    Map<String, PriceStrategy> priceStrategies = new HashMap<>();
    PriceStrategy defaultStrategy = new DefaultPriceStrategy();

    // every validator must allow a topping before it is added
    List<ToppingValidator> validators = new ArrayList<>();

    // works out the tax rate, including the cheeseburst uplift
    TaxCalculator taxCalculator = new TaxCalculator();

    public PizzaPricing(int basePrice, int taxPercentage, String size) {
        this.basePrice = basePrice;
        this.taxPercentage = taxPercentage;
        this.size = size;

        prices.put("cheeseburst", 100);
        prices.put("corn", 50);
        prices.put("onion", 30);
        prices.put("capsicum", 50);
        prices.put("pineapple", 60);
        prices.put("mushroom", 40);

        // special price strategies and validators are registered here
        priceStrategies.put("cheeseburst", new CheeseburstPriceStrategy());
        validators.add(new HealthValidator());
    }

    public boolean addTopping(String topping, int servingsCount) {
        if (servingsCount <= 0 || !prices.containsKey(topping)) {
            return false;
        }
        for (ToppingValidator validator : validators) {
            if (!validator.canAdd(this, topping, servingsCount)) {
                return false; // rejected before anything changed
            }
        }
        servings.put(topping, servings.getOrDefault(topping, 0) + servingsCount);
        return true;
    }

    public int getFinalPrice() {
        int subtotal = basePrice;
        for (String topping : servings.keySet()) {
            // toppings without a special strategy use the default one
            PriceStrategy strategy = priceStrategies.getOrDefault(topping, defaultStrategy);
            subtotal += strategy.getCost(servings.get(topping), prices.get(topping));
        }

        double taxRate = taxCalculator.getTaxRate(this);

        // multiply before dividing by 100, so an exact .5 price is not stored as .4999
        double finalPrice = subtotal + subtotal * taxRate / 100;
        return (int) (finalPrice + 0.5);
    }

    boolean hasTopping(String topping) {
        return servings.containsKey(topping);
    }
}

/** Calculates the cost of all servings of one topping. */
interface PriceStrategy {
    int getCost(int servingsCount, int pricePerServing);
}

/** Every serving costs the catalog price. */
class DefaultPriceStrategy implements PriceStrategy {
    public int getCost(int servingsCount, int pricePerServing) {
        return servingsCount * pricePerServing;
    }
}

/** The first serving costs the catalog price, every extra serving costs 70. */
class CheeseburstPriceStrategy implements PriceStrategy {
    int extraServingPrice = 70;

    public int getCost(int servingsCount, int pricePerServing) {
        if (servingsCount == 0) {
            return 0;
        }
        return pricePerServing + extraServingPrice * (servingsCount - 1);
    }
}

/** Decides whether a topping may be added to the pizza. */
interface ToppingValidator {
    boolean canAdd(PizzaPricing pizza, String topping, int servingsCount);
}

/** Mushroom and cheeseburst can never be on the same pizza. */
class HealthValidator implements ToppingValidator {
    public boolean canAdd(PizzaPricing pizza, String topping, int servingsCount) {
        if (topping.equals("mushroom") && pizza.hasTopping("cheeseburst")) {
            return false;
        }
        if (topping.equals("cheeseburst") && pizza.hasTopping("mushroom")) {
            return false;
        }
        return true;
    }
}

/** Works out the tax rate. Any cheeseburst raises it by 30% of itself, once. */
class TaxCalculator {
    double getTaxRate(PizzaPricing pizza) {
        double taxRate = pizza.taxPercentage;
        if (pizza.hasTopping("cheeseburst")) {
            taxRate = taxRate * 1.3;
        }
        return taxRate;
    }
}
```