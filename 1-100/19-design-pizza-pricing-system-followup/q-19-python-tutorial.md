# Design Pizza Pricing System - Followup in Python

#### Problem Statement
[https://codezym.com/question/19-design-pizza-pricing-system-followup](https://codezym.com/question/19-design-pizza-pricing-system-followup)

A pizza only needs to remember a few things: its base price, tax percentage, size and how many servings of each topping it has. Every business rule in this problem is one of just three kinds: a **check before adding** a topping, a **special price** for a topping, or a **change to the tax rate**.

So we give every topping its own small class that answers these three questions for itself. This is the **Strategy pattern**. A **Simple Factory** (a dictionary from topping name to its object) gives us the right topping object for a name. This combination of Strategy + Simple Factory is the best fit here.

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
- A failed `addTopping()` returns `False` and changes nothing. An unknown topping name also returns `False`.

The final price is:

```
subtotal    = base_price + cost of all toppings
tax_amount  = subtotal * tax_rate / 100
final_price = int(subtotal + tax_amount + 0.5)     # round half up
```

## Solution 1: Simple Approach (One Class)

The simplest approach puts everything inside `PizzaPricing`.

**Why a dictionary for servings?** Almost every rule asks "how many servings of X are on the pizza?". A dictionary from topping name to total servings answers that in O(1).

**Why build the price only in `getFinalPrice()`?** `addTopping()` only checks the rules with if-else and updates the servings dictionary. `getFinalPrice()` then builds the whole price from scratch: topping costs, tax change for cheeseburst or mushroom, and rounding. This keeps adding simple, and corn's tiered price always sees the full corn count.

```python
class PizzaPricing:
    def __init__(self, basePrice: int, taxPercentage: int, size: str):
        self.base_price = basePrice
        self.tax_percentage = taxPercentage
        self.size = size

        # topping name -> price of one serving
        self.menu = {
            "cheeseburst": 100,
            "corn": 50,
            "onion": 30,
            "capsicum": 50,
            "pineapple": 60,
            "mushroom": 40,
        }

        # topping name -> total servings added to this pizza so far
        self.servings = {}

    def addTopping(self, topping: str, servingsCount: int) -> bool:
        if topping not in self.menu:
            return False

        # every rule of every topping is checked here
        if topping == "cheeseburst":
            if self.get_servings("mushroom") > 0:
                return False
            total = self.get_servings("cheeseburst") + servingsCount
            if self.size == "small" and total > 1:
                return False
            if self.size == "medium" and total > 2:
                return False
        if topping == "mushroom" and self.get_servings("cheeseburst") > 0:
            return False
        if topping == "pineapple" and self.size == "small":
            return False

        self.servings[topping] = self.get_servings(topping) + servingsCount
        return True

    def getFinalPrice(self) -> int:
        subtotal = self.base_price
        for topping, count in self.servings.items():
            if topping == "corn" and self.size == "medium":
                subtotal += 50 + 40 * (count - 1)
            elif topping == "corn" and self.size == "large":
                subtotal += 20 * count
            else:
                subtotal += self.menu[topping] * count

        tax_rate = self.tax_percentage
        if self.get_servings("cheeseburst") > 0:
            tax_rate = tax_rate * 1.3  # +30%
        if self.get_servings("mushroom") > 0:
            tax_rate = tax_rate * 0.9  # -10%

        final_price = subtotal + subtotal * tax_rate / 100
        return int(final_price + 0.5)

    def get_servings(self, topping: str) -> int:
        return self.servings.get(topping, 0)
```

### Problems with this approach

It works, but the rules of all toppings are mixed together in two big methods.

- Every new rule (a new size cap, a combo, a promo) means editing the same two methods, and one wrong edit can break a rule that was already working.
- The rules of one topping are spread across both methods, so you have to read everything to understand a single topping.
- You cannot test one topping's rules on their own.

The problem asks us to make new rules easy to add, so let's pick a design pattern.

## Choosing the Right Design Pattern

### Why not Decorator?

With Decorator, every `addTopping()` call wraps the pizza in one more layer, like `Onion(Cheeseburst(BasePizza()))`. Each layer adds its own cost and can change the tax rate.

It sounds natural for "adding toppings", but these rules make it awkward:

- **Rules need to see the whole pizza.** "No cheeseburst with mushroom", "max 2 cheeseburst on medium" and "first corn serving costs 50" all need topping counts. A layer only knows the layer it wraps, so it has to ask every layer below it. We end up rebuilding a dictionary of topping counts through the chain anyway.
- **Tax must change only once.** A second cheeseburst layer has to search the layers below it to find out whether tax was already raised.
- **The chain keeps growing.** Every `addTopping()` adds a layer, and every price or rule check walks through all of them.

Decorator shines when each layer is independent and just adds something on top. Here the rules depend on what is already on the pizza, so a plain dictionary of servings is much simpler.

### Why not Chain of Responsibility?

We could build a chain of rule checkers where any checker can reject a topping. But every rule here is tied to the topping being added, so each checker would first ask "is this my topping?". A dictionary lookup takes us straight to the right rules instead. A chain would also cover only the checks, not the special prices or tax effects.

### Why Strategy works best

All toppings answer the same three questions, each in its own way. That is exactly what the Strategy pattern is for.

- `PizzaPricing` calls `can_add()`, `get_cost()` and `apply_tax()` without knowing which topping it is talking to.
- Each topping's rules live in one small class, so they are easy to read, change and test.
- A new topping is one new class plus one line in the factory. `PizzaPricing` does not change.

## Solution 2: Strategy Pattern + Simple Factory

### Classes

**`Topping` (the strategy)**

The base class of every topping. It stores the price per serving and has three methods with default behavior:

- `can_add(pizza, new_servings)`: returns `True`, no restriction.
- `get_cost(pizza, total_servings)`: returns `price_per_serving * total_servings`.
- `apply_tax(tax_rate)`: returns `tax_rate` unchanged.

Onion and capsicum have no special rules, so they are simply `Topping(30)` and `Topping(50)`.

The whole pizza is passed to `can_add()` and `get_cost()`, so a topping can look at the size or at other toppings. That is what turns a rule like "no cheeseburst with mushroom" into a single line.

**`Cheeseburst`, `Mushroom`, `Pineapple`, `Corn`**

Each one extends `Topping` and overrides only the rules it needs.

| Class | Overrides | Rule |
|---|---|---|
| `Cheeseburst` | `can_add()`, `apply_tax()` | blocked by mushroom, size caps, +30% tax |
| `Mushroom` | `can_add()`, `apply_tax()` | blocked by cheeseburst, -10% tax |
| `Pineapple` | `can_add()` | not allowed on small |
| `Corn` | `get_cost()` | tiered price on medium and large |

**`ToppingFactory` (simple factory)**

Holds a dictionary from topping name to its object. `get_topping(name)` returns the right object, or `None` if the topping is not on the menu. The whole menu lives in one place, so adding a topping never touches `PizzaPricing`.

**`PizzaPricing`**

Holds the pizza's state: base price, tax percentage, size and the `servings` dictionary. It has no topping-specific rule inside it.

- `addTopping()`: gets the topping from the factory and asks `can_add()`. Only if that passes, it updates `servings`.
- `getFinalPrice()`: loops over the toppings on the pizza, adds each `get_cost()` to the subtotal and passes the tax rate through each `apply_tax()`. The loop visits each topping type once, so cheeseburst and mushroom change the tax only once, no matter how many servings there are.

### Walkthrough of Example B

`PizzaPricing(350, 8, "medium")`

1. `addTopping("mushroom", 1)`: `Mushroom.can_add()` finds no cheeseburst and returns `True`. servings = {mushroom: 1}.
2. `addTopping("corn", 3)`: `Corn` uses the default `can_add()`, which returns `True`. servings = {mushroom: 1, corn: 3}.
3. `addTopping("cheeseburst", 1)`: `Cheeseburst.can_add()` finds mushroom and returns `False`. Nothing changes.
4. `getFinalPrice()`: subtotal = 350 + 40 + (50 + 40 + 40) = 520. `Mushroom.apply_tax()` turns tax 8 into 7.2. Final = 520 + 520 × 7.2 / 100 = 557.44, which rounds to **557**.

### A small floating point trap

Compute the tax as `subtotal * tax_rate / 100`, not `(tax_rate / 100) * subtotal`.

Dividing first can leave a tiny error. With base price 100, tax 201% and one capsicum, the subtotal is 150 and the exact final price is 451.5, which should round up to 452. Dividing first gives `451.49999999999994`, which rounds down to 451. Multiplying first gives exactly `451.5`.

### Adding new rules later

- **New topping with no special rules:** one line in `ToppingFactory`, like `"olive": Topping(45),`.
- **New size cap or restriction:** override `can_add()` in that topping's class.
- **New special price or combo:** override `get_cost()`. It gets the whole pizza, so it can check other toppings too. Prices are built only in `getFinalPrice()`, so a combo works no matter which topping was added first.
- **New tax effect:** override `apply_tax()`.

### Code

```python
class Topping:
    """
    A topping with a fixed price per serving and no special rules.
    Special toppings extend this class and override only the rules they need.
    """

    def __init__(self, price_per_serving):
        self.price_per_serving = price_per_serving

    def can_add(self, pizza, new_servings):
        """Can these new servings be added to the pizza? By default, yes."""
        return True

    def get_cost(self, pizza, total_servings):
        """Cost of all servings of this topping. By default, each costs the same."""
        return self.price_per_serving * total_servings

    def apply_tax(self, tax_rate):
        """Tax rate after this topping's effect. By default, tax does not change."""
        return tax_rate


class Cheeseburst(Topping):
    def __init__(self):
        super().__init__(100)

    def can_add(self, pizza, new_servings):
        if pizza.get_servings("mushroom") > 0:
            return False

        total = pizza.get_servings("cheeseburst") + new_servings
        if pizza.size == "small":
            return total <= 1
        if pizza.size == "medium":
            return total <= 2
        return True  # no cap on large

    def apply_tax(self, tax_rate):
        return tax_rate * 1.3  # +30% of current tax


class Mushroom(Topping):
    def __init__(self):
        super().__init__(40)

    def can_add(self, pizza, new_servings):
        return pizza.get_servings("cheeseburst") == 0

    def apply_tax(self, tax_rate):
        return tax_rate * 0.9  # -10% of current tax


class Pineapple(Topping):
    def __init__(self):
        super().__init__(60)

    def can_add(self, pizza, new_servings):
        return pizza.size != "small"


class Corn(Topping):
    def __init__(self):
        super().__init__(50)

    def get_cost(self, pizza, total_servings):
        if pizza.size == "medium":
            return 50 + 40 * (total_servings - 1)
        if pizza.size == "large":
            return 20 * total_servings
        return super().get_cost(pizza, total_servings)  # small: normal price


class ToppingFactory:
    """
    Simple factory: gives back the topping object for a name.
    The whole menu lives here, so a new topping is one new line.
    """

    def __init__(self):
        self.toppings = {
            "cheeseburst": Cheeseburst(),
            "corn": Corn(),
            "onion": Topping(30),
            "capsicum": Topping(50),
            "pineapple": Pineapple(),
            "mushroom": Mushroom(),
        }

    def get_topping(self, name):
        """Returns None if the topping is not on the menu."""
        return self.toppings.get(name)


class PizzaPricing:
    """Stores the pizza's state and lets each topping apply its own rules."""

    def __init__(self, basePrice: int, taxPercentage: int, size: str):
        self.base_price = basePrice
        self.tax_percentage = taxPercentage
        self.size = size

        # topping name -> total servings added to this pizza so far
        self.servings = {}

        self.topping_factory = ToppingFactory()

    def addTopping(self, name: str, servingsCount: int) -> bool:
        topping = self.topping_factory.get_topping(name)
        if topping is None or not topping.can_add(self, servingsCount):
            return False

        self.servings[name] = self.get_servings(name) + servingsCount
        return True

    def getFinalPrice(self) -> int:
        subtotal = self.base_price
        tax_rate = self.tax_percentage

        for name, count in self.servings.items():
            topping = self.topping_factory.get_topping(name)
            subtotal += topping.get_cost(self, count)
            # runs once per topping type, so extra servings never change tax again
            tax_rate = topping.apply_tax(tax_rate)

        # multiply first, divide last: avoids results like 451.4999 instead of 451.5
        final_price = subtotal + subtotal * tax_rate / 100
        return int(final_price + 0.5)

    def get_servings(self, name: str) -> int:
        return self.servings.get(name, 0)
```

### Complexity

- `addTopping()`: O(1), one dictionary lookup and a quick rule check.
- `getFinalPrice()`: O(T), where T is the number of different toppings on the pizza (at most 6 here).