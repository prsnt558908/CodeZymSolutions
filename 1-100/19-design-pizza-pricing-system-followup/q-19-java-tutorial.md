# Design Pizza Pricing System - Followup in Java

#### Problem Statement
[https://codezym.com/question/19-design-pizza-pricing-system-followup](https://codezym.com/question/19-design-pizza-pricing-system-followup)

A pizza only needs to remember a few things: its base price, tax percentage, size and how many servings of each topping it has. Every business rule in this problem is one of just three kinds: a **check before adding** a topping, a **special price** for a topping, or a **change to the tax rate**.

So we give every topping its own small class that answers these three questions for itself. This is the **Strategy pattern**. A **Simple Factory** (a map from topping name to its object) gives us the right topping object for a name. This combination of Strategy + Simple Factory is the best fit here.

The problem statement suggests the **Decorator pattern**, but we do not need it. Decorators work best when each layer just adds something on top of the layer below. Here most rules need to look at the whole pizza, which decorators make hard. We explain why below.

We will start with a simple one-class solution, see where it gets messy, and then clean it up with Strategy + Simple Factory.

## Understanding the Rules

| Topping | Price per serving | Can be added only if | Special price | Tax effect |
|---|---|---|---|---|
| cheeseburst | 100 | no mushroom on the pizza, total servings at most 1 on small and 2 on medium | - | +30% of current tax |
| mushroom | 40 | no cheeseburst on the pizza | - | -10% of current tax |
| pineapple | 60 | size is medium or large | - | - |
| corn | 50 | - | medium: 50 for the first serving, 40 for each next one. large: 20 each | - |
| onion | 30 | - | - | - |
| capsicum | 50 | - | - | - |

A few things to keep in mind:

- A tax effect is applied **once per topping type**, no matter how many servings are added.
- Servings add up across calls. On a small pizza, adding 1 cheeseburst works, but adding 1 more later fails because the total becomes 2.
- Corn works the same way. "First serving" means the first corn serving on the pizza, so on a medium pizza, corn added in a later call costs 40 per serving.
- A failed `addTopping()` returns `false` and changes nothing. An unknown topping name also returns `false`.

The final price is:

```
subtotal   = basePrice + cost of all toppings
taxAmount  = subtotal * taxRate / 100
finalPrice = (int) (subtotal + taxAmount + 0.5)     // round half up
```

## Solution 1: Simple Approach (One Class)

The simplest approach puts everything inside `PizzaPricing`.

**Why a map for servings?** Almost every rule asks "how many servings of X are on the pizza?". A `Map<String, Integer>` from topping name to total servings answers that in O(1).

**Why build the price only in `getFinalPrice()`?** `addTopping()` only checks the rules with if-else and updates the servings map. `getFinalPrice()` then builds the whole price from scratch: topping costs, tax change for cheeseburst or mushroom, and rounding. This keeps adding simple, and corn's tiered price always sees the full corn count.

```java
import java.util.HashMap;
import java.util.Map;

public class PizzaPricing {
    int basePrice;
    int taxPercentage;
    String size;

    // topping name -> price of one serving
    Map<String, Integer> menu = new HashMap<>();

    // topping name -> total servings added to this pizza so far
    Map<String, Integer> servings = new HashMap<>();

    public PizzaPricing(int basePrice, int taxPercentage, String size) {
        this.basePrice = basePrice;
        this.taxPercentage = taxPercentage;
        this.size = size;

        menu.put("cheeseburst", 100);
        menu.put("corn", 50);
        menu.put("onion", 30);
        menu.put("capsicum", 50);
        menu.put("pineapple", 60);
        menu.put("mushroom", 40);
    }

    public boolean addTopping(String topping, int servingsCount) {
        if (!menu.containsKey(topping)) return false;

        // every rule of every topping is checked here
        if (topping.equals("cheeseburst")) {
            if (getServings("mushroom") > 0) return false;
            int total = getServings("cheeseburst") + servingsCount;
            if (size.equals("small") && total > 1) return false;
            if (size.equals("medium") && total > 2) return false;
        }
        if (topping.equals("mushroom") && getServings("cheeseburst") > 0) return false;
        if (topping.equals("pineapple") && size.equals("small")) return false;

        servings.put(topping, getServings(topping) + servingsCount);
        return true;
    }

    public int getFinalPrice() {
        double subtotal = basePrice;
        for (String topping : servings.keySet()) {
            int count = servings.get(topping);
            if (topping.equals("corn") && size.equals("medium")) {
                subtotal += 50 + 40 * (count - 1);
            } else if (topping.equals("corn") && size.equals("large")) {
                subtotal += 20 * count;
            } else {
                subtotal += menu.get(topping) * count;
            }
        }

        double taxRate = taxPercentage;
        if (getServings("cheeseburst") > 0) taxRate = taxRate * 1.3; // +30%
        if (getServings("mushroom") > 0) taxRate = taxRate * 0.9;    // -10%

        double finalPrice = subtotal + subtotal * taxRate / 100;
        return (int) (finalPrice + 0.5);
    }

    int getServings(String topping) {
        return servings.getOrDefault(topping, 0);
    }
}
```

### Problems with this approach

It works, but the rules of all toppings are mixed together in two big methods.

- Every new rule (a new size cap, a combo, a promo) means editing the same two methods, and one wrong edit can break a rule that was already working.
- The rules of one topping are spread across both methods, so you have to read everything to understand a single topping.
- You cannot test one topping's rules on their own.

The problem asks us to make new rules easy to add, so let's pick a design pattern.

## Choosing the Right Design Pattern

### Why not Decorator?

With Decorator, every `addTopping()` call wraps the pizza in one more layer, like `new Onion(new Cheeseburst(new BasePizza()))`. Each layer adds its own cost and can change the tax rate.

It sounds natural for "adding toppings", but these rules make it awkward:

- **Rules need to see the whole pizza.** "No cheeseburst with mushroom", "max 2 cheeseburst on medium" and "first corn serving costs 50" all need topping counts. A layer only knows the layer it wraps, so it has to ask every layer below it. We end up rebuilding a map of topping counts through the chain anyway.
- **Tax must change only once.** A second cheeseburst layer has to search the layers below it to find out whether tax was already raised.
- **The chain keeps growing.** Every `addTopping()` adds a layer, and every price or rule check walks through all of them.

Decorator shines when each layer is independent and just adds something on top. Here the rules depend on what is already on the pizza, so a plain map of servings is much simpler.

### Why not Chain of Responsibility?

We could build a chain of rule checkers where any checker can reject a topping. But every rule here is tied to the topping being added, so each checker would first ask "is this my topping?". A map lookup takes us straight to the right rules instead. A chain would also cover only the checks, not the special prices or tax effects.

### Why Strategy works best

All toppings answer the same three questions, each in its own way. That is exactly what the Strategy pattern is for.

- `PizzaPricing` calls `canAdd()`, `getCost()` and `applyTax()` without knowing which topping it is talking to.
- Each topping's rules live in one small class, so they are easy to read, change and test.
- A new topping is one new class plus one line in the factory. `PizzaPricing` does not change.

## Solution 2: Strategy Pattern + Simple Factory

### Classes

**`Topping` (the strategy)**

The base class of every topping. It stores the price per serving and has three methods with default behavior:

- `canAdd(pizza, newServings)`: returns `true`, no restriction.
- `getCost(pizza, totalServings)`: returns `pricePerServing * totalServings`.
- `applyTax(taxRate)`: returns `taxRate` unchanged.

Onion and capsicum have no special rules, so they are simply `new Topping(30)` and `new Topping(50)`.

The whole pizza is passed to `canAdd()` and `getCost()`, so a topping can look at the size or at other toppings. That is what turns a rule like "no cheeseburst with mushroom" into a single line.

**`Cheeseburst`, `Mushroom`, `Pineapple`, `Corn`**

Each one extends `Topping` and overrides only the rules it needs.

| Class | Overrides | Rule |
|---|---|---|
| `Cheeseburst` | `canAdd()`, `applyTax()` | blocked by mushroom, size caps, +30% tax |
| `Mushroom` | `canAdd()`, `applyTax()` | blocked by cheeseburst, -10% tax |
| `Pineapple` | `canAdd()` | not allowed on small |
| `Corn` | `getCost()` | tiered price on medium and large |

**`ToppingFactory` (simple factory)**

Holds a `Map<String, Topping>` from topping name to its object. `getTopping(name)` returns the right object, or `null` if the topping is not on the menu. The whole menu lives in one place, so adding a topping never touches `PizzaPricing`.

**`PizzaPricing`**

Holds the pizza's state: base price, tax percentage, size and the `servings` map. It has no topping-specific rule inside it.

- `addTopping()`: gets the topping from the factory and asks `canAdd()`. Only if that passes, it updates `servings`.
- `getFinalPrice()`: loops over the toppings on the pizza, adds each `getCost()` to the subtotal and passes the tax rate through each `applyTax()`. The loop visits each topping type once, so cheeseburst and mushroom change the tax only once, no matter how many servings there are.

### Walkthrough of Example B

`new PizzaPricing(350, 8, "medium")`

1. `addTopping("mushroom", 1)`: `Mushroom.canAdd()` finds no cheeseburst and returns `true`. servings = {mushroom: 1}.
2. `addTopping("corn", 3)`: `Corn` uses the default `canAdd()`, which returns `true`. servings = {mushroom: 1, corn: 3}.
3. `addTopping("cheeseburst", 1)`: `Cheeseburst.canAdd()` finds mushroom and returns `false`. Nothing changes.
4. `getFinalPrice()`: subtotal = 350 + 40 + (50 + 40 + 40) = 520. `Mushroom.applyTax()` turns tax 8 into 7.2. Final = 520 + 520 × 7.2 / 100 = 557.44, which rounds to **557**.

### A small floating point trap

Compute the tax as `subtotal * taxRate / 100`, not `(taxRate / 100) * subtotal`.

Dividing first can leave a tiny error. With base price 100, tax 201% and one capsicum, the subtotal is 150 and the exact final price is 451.5, which should round up to 452. Dividing first gives `451.49999999999994`, which rounds down to 451. Multiplying first gives exactly `451.5`.

### Adding new rules later

- **New topping with no special rules:** one line in `ToppingFactory`, like `toppings.put("olive", new Topping(45))`.
- **New size cap or restriction:** override `canAdd()` in that topping's class.
- **New special price or combo:** override `getCost()`. It gets the whole pizza, so it can check other toppings too. Prices are built only in `getFinalPrice()`, so a combo works no matter which topping was added first.
- **New tax effect:** override `applyTax()`.

### Code

```java
import java.util.HashMap;
import java.util.Map;

/**
 * A topping with a fixed price per serving and no special rules.
 * Special toppings extend this class and override only the rules they need.
 */
class Topping {
    int pricePerServing;

    Topping(int pricePerServing) {
        this.pricePerServing = pricePerServing;
    }

    /** Can these new servings be added to the pizza? By default, yes. */
    boolean canAdd(PizzaPricing pizza, int newServings) {
        return true;
    }

    /** Cost of all servings of this topping. By default, each costs the same. */
    double getCost(PizzaPricing pizza, int totalServings) {
        return pricePerServing * totalServings;
    }

    /** Tax rate after this topping's effect. By default, tax does not change. */
    double applyTax(double taxRate) {
        return taxRate;
    }
}

class Cheeseburst extends Topping {
    Cheeseburst() {
        super(100);
    }

    @Override
    boolean canAdd(PizzaPricing pizza, int newServings) {
        if (pizza.getServings("mushroom") > 0) return false;

        int total = pizza.getServings("cheeseburst") + newServings;
        if (pizza.getSize().equals("small")) return total <= 1;
        if (pizza.getSize().equals("medium")) return total <= 2;
        return true; // no cap on large
    }

    @Override
    double applyTax(double taxRate) {
        return taxRate * 1.3; // +30% of current tax
    }
}

class Mushroom extends Topping {
    Mushroom() {
        super(40);
    }

    @Override
    boolean canAdd(PizzaPricing pizza, int newServings) {
        return pizza.getServings("cheeseburst") == 0;
    }

    @Override
    double applyTax(double taxRate) {
        return taxRate * 0.9; // -10% of current tax
    }
}

class Pineapple extends Topping {
    Pineapple() {
        super(60);
    }

    @Override
    boolean canAdd(PizzaPricing pizza, int newServings) {
        return !pizza.getSize().equals("small");
    }
}

class Corn extends Topping {
    Corn() {
        super(50);
    }

    @Override
    double getCost(PizzaPricing pizza, int totalServings) {
        if (pizza.getSize().equals("medium")) return 50 + 40 * (totalServings - 1);
        if (pizza.getSize().equals("large")) return 20 * totalServings;
        return super.getCost(pizza, totalServings); // small: normal price
    }
}

/**
 * Simple factory: gives back the topping object for a name.
 * The whole menu lives here, so a new topping is one new line.
 */
class ToppingFactory {
    Map<String, Topping> toppings = new HashMap<>();

    ToppingFactory() {
        toppings.put("cheeseburst", new Cheeseburst());
        toppings.put("corn", new Corn());
        toppings.put("onion", new Topping(30));
        toppings.put("capsicum", new Topping(50));
        toppings.put("pineapple", new Pineapple());
        toppings.put("mushroom", new Mushroom());
    }

    /** Returns null if the topping is not on the menu. */
    Topping getTopping(String name) {
        return toppings.get(name);
    }
}

/**
 * Stores the pizza's state and lets each topping apply its own rules.
 */
public class PizzaPricing {
    int basePrice;
    int taxPercentage;
    String size;

    // topping name -> total servings added to this pizza so far
    Map<String, Integer> servings = new HashMap<>();

    ToppingFactory toppingFactory = new ToppingFactory();

    public PizzaPricing(int basePrice, int taxPercentage, String size) {
        this.basePrice = basePrice;
        this.taxPercentage = taxPercentage;
        this.size = size;
    }

    public boolean addTopping(String name, int servingsCount) {
        Topping topping = toppingFactory.getTopping(name);
        if (topping == null || !topping.canAdd(this, servingsCount)) return false;

        servings.put(name, getServings(name) + servingsCount);
        return true;
    }

    public int getFinalPrice() {
        double subtotal = basePrice;
        double taxRate = taxPercentage;

        for (String name : servings.keySet()) {
            Topping topping = toppingFactory.getTopping(name);
            subtotal += topping.getCost(this, servings.get(name));
            // runs once per topping type, so extra servings never change tax again
            taxRate = topping.applyTax(taxRate);
        }

        // multiply first, divide last: avoids results like 451.4999 instead of 451.5
        double finalPrice = subtotal + subtotal * taxRate / 100;
        return (int) (finalPrice + 0.5);
    }

    int getServings(String name) {
        return servings.getOrDefault(name, 0);
    }

    String getSize() {
        return size;
    }
}
```

### Complexity

- `addTopping()`: O(1), one map lookup and a quick rule check.
- `getFinalPrice()`: O(T), where T is the number of different toppings on the pizza (at most 6 here).