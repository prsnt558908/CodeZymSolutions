# Design Pizza Pricing System in Python

#### Problem Statement: 

[https://codezym.com/question/18-design-pizza-pricing-system](https://codezym.com/question/18-design-pizza-pricing-system)

The core idea is to store the number of servings of each topping in a dictionary, then use the **Decorator design pattern** to add pricing rules around a basic pizza. The basic pizza calculates ordinary topping costs and the original tax rate. Separate decorators handle the cheeseburst discount, the one-time tax uplift, and the mushroom restriction. Decorator is required by this problem and fits these rules because they must all work together. We do not need to combine it with another design pattern.

## What Does the System Need to Do?

The main class is `PizzaPricing`. Its constructor receives a base price, a tax percentage, and a size.

It supports two operations:

- `addTopping(topping, servingsCount)` adds the requested servings when all rules allow it. It returns `False` without changing anything when the addition is rejected.
- `getFinalPrice()` returns the price of the current pizza, including tax, rounded using Round Half Up.

The ordinary per-serving prices are:

- `cheeseburst` costs 100.
- `corn` costs 50.
- `onion` costs 30.
- `capsicum` costs 50.
- `pineapple` costs 60.
- `mushroom` costs 40.

Constructor inputs are guaranteed to be valid. The base price is between 100 and 10000 and is a multiple of 100. The original tax percentage is between 0 and 1000. The size is `small`, `medium`, or `large`, and each requested serving count is positive.

Size has no effect on the current price or topping rules. We keep the supplied size in the pizza's state so a future size rule can use it. We do not invent a size surcharge or serving limit.

## Understand the Three Special Rules First

### 1. Only the First Cheeseburst Serving Costs 100

Every later cheeseburst serving on the same pizza costs 70.

For example, adding three servings together costs:

```text
100 + 70 + 70 = 240
```

Adding one serving now and two later must have exactly the same total cost. The first-serving price does not restart with each `addTopping` call.

If the pizza contains `c` cheeseburst servings, its cheeseburst cost is:

```text
0                         when c = 0
100 + 70 × (c - 1)        when c > 0
```

### 2. Cheeseburst Raises the Entire Tax Rate Once

The uplift is 30% of the original tax rate. A 15% tax rate becomes:

```text
15 × 1.30 = 19.5%
```

It does not become 45%. Additional cheeseburst servings do not multiply the rate again.

The new rate applies to the entire subtotal, including the base pizza and all other toppings. For example, onions added before cheeseburst are also taxed at the raised rate when the final price is requested.

### 3. Mushroom and Cheeseburst Cannot Coexist

The restriction works in both directions:

- If cheeseburst is present, reject mushroom.
- If mushroom is present, reject cheeseburst.

A rejected addition must not change the serving counts, subtotal, or effective tax rate.

## Start With a Simple Approach

We could put everything directly inside `PizzaPricing`.

One method could check incompatible toppings and update a dictionary. Another could loop over that dictionary, apply the cheeseburst discount, choose the tax rate, and round the result.

For three fixed rules, that approach can be short and correct. It can also have the same running time as the solution below. The difficulty appears when we add more rules. A size limit changes validation, a combo changes the subtotal, and a tax rule changes the rate. These different responsibilities collect in the same methods.

The statement explicitly requires Decorator. Our improvement is therefore about separating rules while keeping the implementation small. We are not using Decorator to claim a speed improvement.

## Build the Pizza From a Base and Rule Wrappers

A decorator follows the same method contract as the object it wraps. It forwards the ordinary work to that object and changes only the behavior it is responsible for.

Our internal `Pizza` abstract base class exposes three questions:

- Can this topping addition be accepted?
- What is the current subtotal before tax?
- What is the current tax rate, measured in tenths of a percent?

`BasePizza` answers these questions using the ordinary catalog and the constructor's tax rate. Each decorator wraps another `Pizza` object and changes one answer.

The constructor builds a `BasePizza`, wraps it with `CheeseburstDiscountDecorator`, then `CheeseburstTaxDecorator`, and finally `HealthConstraintDecorator`. The public `PizzaPricing` object keeps the outermost wrapper.

All of these objects read the same `PizzaState`. We build this chain only once. An `addTopping` call changes the serving dictionary rather than adding another wrapper.

This keeps the number of wrappers fixed even after many topping additions. It also avoids creating one object for every individual serving.

### Why Decorator Fits Better Than Strategy Here

Strategy is useful when we want to select one of several alternative ways to do the same job, such as choosing between different delivery-price algorithms.

Here, the health restriction, volume discount, and tax uplift must all apply together. Selecting a single strategy would not express that combination on its own. A strategy could contain all the rules, but that would bring their logic back into one place. Decorators let each rule add its behavior while preserving the others.

## The Important Classes and Data Structures

### `PizzaState` Holds the Data for One Pizza

`PizzaState` is a data class that stores the base price, original tax percentage, size, and a dictionary of topping counts.

For example, after adding one onion and three cheeseburst servings, the dictionary contains:

```python
{
    "onion": 1,
    "cheeseburst": 3,
}
```

The `@dataclass` decorator generates the initializer for these related fields. `field(default_factory=dict)` gives every pizza its own new dictionary. This is essential because using one dictionary as a shared default could let one pizza's toppings appear in another pizza.

We store total counts rather than the history of individual additions. These rules depend on what is currently on the pizza, so the full history is unnecessary.

The helper `servings_of(topping)` returns the current count, or zero when the topping is absent. Both the discount and health rules use this helper.

There is no separate `has_cheeseburst` flag or stored effective tax rate. Both values can be derived from the dictionary. Keeping one source for this information prevents related fields from getting out of step.

### The Catalog Dictionary Holds Ordinary Prices

`TOPPING_PRICES` maps each supported topping name to its ordinary price.

A dictionary gives average `O(1)` lookup by topping name. Sorting is unnecessary. The catalog is created once and shared because every pizza uses the same ordinary prices.

Each pizza still has its own serving dictionary, so adding a topping to one pizza does not affect another.

### `BasePizza` Handles Ordinary Behavior

`BasePizza` accepts supported topping names with positive serving counts.

To calculate the ordinary subtotal, it starts with the base price and adds each topping's catalog price multiplied by its count. At this stage, every cheeseburst serving is counted at 100. The discount decorator will adjust that amount.

The subtotal loops over at most six dictionary entries, regardless of how many servings have been added. Recalculating these few entries is simple and avoids maintaining an extra cached price.

### `PizzaDecorator` Forwards Unchanged Behavior

The `PizzaDecorator` class stores a wrapped `Pizza` and forwards all three operations to it.

Each concrete decorator overrides only the method it changes. The health decorator changes validation, the discount decorator changes the subtotal, and the tax decorator changes the tax rate.

This forwarding is what makes the implementation a Decorator design. The wrappers follow the same contract as `BasePizza` and preserve its remaining behavior through delegation.

Python does not require every class to declare an interface. We still use an abstract base class because it clearly documents the three methods that the base pizza and every decorator must provide.

## How Each Decorator Works

### `CheeseburstDiscountDecorator`

The base calculation charges `100 × c` for `c` cheeseburst servings. We need to reduce every serving after the first by 30.

The decorator therefore subtracts:

```text
30 × max(0, c - 1)
```

For three servings, the base calculation charges 300. Subtracting 60 gives 240, which is the required `100 + 70 + 70`.

For zero or one serving, the discount is zero.

Because `c` comes from the total count in the dictionary, this works whether servings arrived in one call or several calls.

### `CheeseburstTaxDecorator`

The decorator first asks the wrapped pizza for its tax rate.

If there is no cheeseburst, it returns that rate unchanged. Otherwise, it multiplies the rate by 1.3.

The original rate is never changed. Every price request starts from that original rate and passes through exactly one tax-uplift decorator. Calling `getFinalPrice()` repeatedly or adding more cheeseburst cannot compound the uplift.

### `HealthConstraintDecorator`

This decorator first asks the wrapped pizza whether the request is valid. This preserves the catalog and positive-count checks.

It then checks the two forbidden combinations using the serving dictionary. If the requested topping conflicts with an existing topping, it returns `False`.

The decorator only reads the state. The public `addTopping` method updates the dictionary only after the complete validation succeeds.

## Calculate Tax Exactly With Integers

The original tax rate is an integer. After the one-time uplift, it can have one decimal place. For example, 15 becomes 19.5 and 7 becomes 9.1.

We can represent these rates exactly by storing **tenths of a percent**:

```text
15% becomes 150
19.5% becomes 195
9.1% becomes 91
```

The base rate is `tax_percentage * 10`. The uplift multiplies this stored value by 13 and uses integer division by 10.

This division is exact for the current rules because the base value is always a multiple of 10. For example, `150 * 13 // 10` gives 195. We never truncate the real rate from 19.5% to 19%.

Let `subtotal` be the price before tax and `rate_tenths` be the stored tax rate. Then:

```text
Tax amount = subtotal × rate_tenths / 1000

Final price before rounding = subtotal × (1000 + rate_tenths) / 1000
```

Define the integer numerator as:

```text
numerator = subtotal × (1000 + rate_tenths)
```

For these nonnegative prices, Round Half Up is exactly:

```text
rounded_price = (numerator + 500) // 1000
```

The `//` operator performs integer division. A remainder below 500 rounds down. A remainder of 500 or more rounds up.

For example, a subtotal of 530 at 15% tax gives a numerator of 609500. Adding 500 and dividing by 1000 produces 610, correctly rounding 609.5 upward.

This avoids floating-point rounding errors. It also avoids Python's built-in `round`, which uses ties-to-even rounding rather than the Round Half Up rule required here.

Python integers can grow as needed, so a large intermediate numerator does not overflow a fixed 32-bit or 64-bit integer type.

This representation covers every tax rate produced by the stated rules. If a future rule introduces more decimal places, its precision must also be reflected in the representation.

## How the Public Methods Work

### Constructor

Create a `PizzaState` with an empty serving dictionary. Then build the base pizza and its three decorators.

Each `PizzaPricing` instance owns its state and wrapper chain. The constructor parameters keep the exact camelCase names supplied in the starter code.

### `addTopping`

1. Ask the outermost pizza wrapper whether the addition is allowed.
2. If validation fails, return `False` immediately.
3. Otherwise, increase that topping's total count in the dictionary and return `True`.

Only the third step changes state. There is no need to undo a discount or reset a tax rate after rejection because neither is stored as mutable state.

The problem guarantees positive serving counts. The base check also rejects nonpositive counts and topping names outside the catalog. It does not change capitalization or trim names because the catalog defines exact names.

### `getFinalPrice`

Ask the decorated pizza for its subtotal and effective tax rate. Calculate the numerator with integers, round once at the end, and return the result.

This method changes nothing. Repeated calls return the same result until a topping is successfully added.

## Walk Through the First Attached Test Case

Start with base price 500, tax 15%, and size `medium`.

**With no toppings**, the final price is `500 × 1.15 = 575`.

**Add one onion.** The subtotal becomes `500 + 30 = 530`. The final price is `530 × 1.15 = 609.5`, which rounds to 610.

**Add one cheeseburst serving.** The subtotal becomes `500 + 30 + 100 = 630`. The rate becomes 19.5%, so the final price is `630 × 1.195 = 752.85`, which rounds to 753.

**Add two more cheeseburst servings.** There are now three in total. The base calculation gives `500 + 30 + 300 = 830`. The discount decorator subtracts `30 × 2 = 60`, giving a subtotal of 770. The rate stays at 19.5%, so the final price is `770 × 1.195 = 920.15`, which rounds to 920.

**Try to add mushroom.** The health decorator finds existing cheeseburst and rejects the request. The serving dictionary is unchanged, so the price remains 920.

## Why the Solution Is Correct

**The serving dictionary contains exactly the accepted additions.** It starts empty. Each accepted request increases one count by the requested amount. Rejected requests return before any count changes.

**Forbidden toppings never coexist.** The health decorator checks both directions before an addition is committed. Once one topping is present, an attempt to add the other is rejected.

**The subtotal charges every topping correctly.** `BasePizza` includes the base price and ordinary costs for every accepted serving. The discount decorator then reduces each cheeseburst serving after the first by exactly 30. The result is one serving at 100 and every later serving at 70.

**The effective tax rate has exactly one uplift when required.** The base pizza always returns the constructor's original rate. The single tax decorator increases it only when the dictionary contains cheeseburst. Neither price requests nor later additions change the original rate.

**Rounding matches the statement.** All amounts are nonnegative, and the integer formula adds exactly half the denominator before division. It therefore implements Round Half Up without floating-point error.

Together, these properties ensure that every price reflects all accepted toppings, the correct tax rate, and the required rounding.

## Time and Space Complexity

Let `k` be the number of distinct accepted topping names and `r` the number of decorators.

`addTopping` takes `O(r)` expected time because validation passes through the wrappers and uses dictionary lookups. It does not loop over the number of servings.

`getFinalPrice` takes `O(k + r)` time. The base pizza scans the distinct topping entries, and the decorators do a constant amount of work each.

Per-pizza space is `O(k + r)` for the serving dictionary and wrappers. The catalog is shared. There are at most six distinct toppings and exactly three decorators in this problem, so both operations and per-pizza storage are `O(1)` with respect to the number of method calls or servings.

## Adding a Future Rule

A size-based serving cap could be another decorator that overrides `can_add_topping`. It would read the stored size and existing counts, delegate the existing checks, and reject additions that exceed its cap.

A promotion could override `get_subtotal`, ask the wrapped pizza for its subtotal, and apply its adjustment. The new rule would then be added when the constructor assembles the wrappers.

New rules still need a clear order. For example, a percentage promotion applied before a fixed discount can produce a different result from applying it afterward. Decorator separates the code for each rule, but the business rules must still define how interacting adjustments combine.


## Complete Python Solution


```python
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Dict


TOPPING_PRICES = {
    "cheeseburst": 100,
    "corn": 50,
    "onion": 30,
    "capsicum": 50,
    "pineapple": 60,
    "mushroom": 40,
}


@dataclass
class PizzaState:
    """Stores all mutable and fixed data for one pizza."""

    base_price: int
    tax_percentage: int
    size: str
    servings: Dict[str, int] = field(default_factory=dict)

    def servings_of(self, topping: str) -> int:
        return self.servings.get(topping, 0)


class Pizza(ABC):
    """Common contract followed by the base pizza and every decorator."""

    @abstractmethod
    def can_add_topping(self, topping: str, servings_count: int) -> bool:
        pass

    @abstractmethod
    def get_subtotal(self) -> int:
        pass

    @abstractmethod
    def get_tax_rate_tenths(self) -> int:
        pass


class BasePizza(Pizza):
    def __init__(self, state: PizzaState):
        self._state = state

    def can_add_topping(self, topping: str, servings_count: int) -> bool:
        return servings_count > 0 and topping in TOPPING_PRICES

    def get_subtotal(self) -> int:
        subtotal = self._state.base_price

        for topping, count in self._state.servings.items():
            subtotal += TOPPING_PRICES[topping] * count

        return subtotal

    def get_tax_rate_tenths(self) -> int:
        return self._state.tax_percentage * 10


class PizzaDecorator(Pizza):
    """Forwards behavior that a concrete decorator does not change."""

    def __init__(self, wrapped: Pizza, state: PizzaState):
        self._wrapped = wrapped
        self._state = state

    def can_add_topping(self, topping: str, servings_count: int) -> bool:
        return self._wrapped.can_add_topping(topping, servings_count)

    def get_subtotal(self) -> int:
        return self._wrapped.get_subtotal()

    def get_tax_rate_tenths(self) -> int:
        return self._wrapped.get_tax_rate_tenths()


class CheeseburstDiscountDecorator(PizzaDecorator):
    def get_subtotal(self) -> int:
        count = self._state.servings_of("cheeseburst")

        # BasePizza charges 100 each. Later servings need 30 off each.
        discount = 30 * max(0, count - 1)
        return self._wrapped.get_subtotal() - discount


class CheeseburstTaxDecorator(PizzaDecorator):
    def get_tax_rate_tenths(self) -> int:
        rate_tenths = self._wrapped.get_tax_rate_tenths()
        if self._state.servings_of("cheeseburst") == 0:
            return rate_tenths

        # The base rate is a multiple of 10, so this division is exact.
        return rate_tenths * 13 // 10


class HealthConstraintDecorator(PizzaDecorator):
    def can_add_topping(self, topping: str, servings_count: int) -> bool:
        if not self._wrapped.can_add_topping(topping, servings_count):
            return False

        if (
            topping == "mushroom"
            and self._state.servings_of("cheeseburst") > 0
        ):
            return False

        if (
            topping == "cheeseburst"
            and self._state.servings_of("mushroom") > 0
        ):
            return False

        return True


class PizzaPricing:
    def __init__(self, basePrice: int, taxPercentage: int, size: str):
        self._state = PizzaState(basePrice, taxPercentage, size)

        decorated: Pizza = BasePizza(self._state)
        decorated = CheeseburstDiscountDecorator(decorated, self._state)
        decorated = CheeseburstTaxDecorator(decorated, self._state)
        decorated = HealthConstraintDecorator(decorated, self._state)
        self._pizza = decorated

    def addTopping(self, topping: str, servingsCount: int) -> bool:
        if not self._pizza.can_add_topping(topping, servingsCount):
            return False

        # Commit only after every validation rule has accepted the request.
        updated_count = self._state.servings_of(topping) + servingsCount
        self._state.servings[topping] = updated_count
        return True

    def getFinalPrice(self) -> int:
        subtotal = self._pizza.get_subtotal()
        rate_tenths = self._pizza.get_tax_rate_tenths()
        numerator = subtotal * (1000 + rate_tenths)

        # Exact Round Half Up for a nonnegative value divided by 1000.
        return (numerator + 500) // 1000
```
