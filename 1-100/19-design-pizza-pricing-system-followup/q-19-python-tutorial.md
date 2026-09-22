# Design Pizza Pricing System Follow-up in Python

#### Problem Statement

[https://codezym.com/question/19-design-pizza-pricing-system-followup](https://codezym.com/question/19-design-pizza-pricing-system-followup)

Think of a pizza as a base price with toppings added around it. The **Decorator pattern** fits this problem because each accepted topping can wrap the existing pizza and add its own cost and tax rules. A small dictionary remembers how many servings have already been accepted. Each wrapper stores the updated subtotal and tax rate, so reading the price stays quick even after many additions. Decorator alone is enough for these requirements. We use Python's `Decimal` class to keep decimal calculations exact and round only the final answer.

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

Our `Pizza` base class exposes two values, the subtotal before tax and the current tax rate. `PlainPizza` provides these values for a pizza without toppings. A topping decorator also extends `Pizza`, so the rest of the program can use either object in exactly the same way.

For example, adding corn creates a corn wrapper around the plain pizza. Adding cheeseburst next creates a cheeseburst wrapper around that corn wrapper. The outermost object represents the complete accepted pizza.

There is no need for a separate class for every combination, such as corn with onion or corn with onion and cheeseburst. We compose the individual additions as calls arrive.

Strategy could be useful when selecting one complete pricing algorithm from several alternatives. Here, multiple toppings accumulate on the same pizza, and Decorator directly represents that behavior. Adding a separate Strategy layer is unnecessary for the given rules.

### The important classes and data structure

**`PizzaPricing`** is the public class required by the judge. It chooses a topping wrapper, checks whether the addition is allowed, and commits successful changes. Its three public method signatures match the supplied Python stub.

**`Pizza` and `PlainPizza`** give us a shared view of the price and a starting object. `PlainPizza` starts with the constructor's base price and tax percentage. It is a frozen dataclass because these values should not change after creation.

**`ToppingDecorator`** holds the pizza it wraps. It adds the new topping cost to the previous subtotal and multiplies the previous tax rate by the appropriate factor. It stores both results without rounding them. Its `is_allowed()` method returns the stored decision for that addition. This class is also a frozen dataclass, which makes every wrapper an immutable snapshot.

**The concrete decorators** keep the special behavior close to the topping. Cheeseburst handles its cap, incompatibility, and tax increase. Mushroom handles its incompatibility and tax decrease. Pineapple handles its size restriction. Corn calculates its size-dependent cost. Onion and capsicum share `FixedPriceDecorator` because only their unit prices differ.

**The `servings` dictionary** remembers accepted quantities. We need quantities rather than just a set of topping names because cheeseburst has cumulative serving limits. The same dictionary also answers whether corn, mushroom, or cheeseburst is already present. There are at most six entries. Python integers grow when needed, so counts do not overflow when quantities are added across calls.

The small set of conditions in `_create_decorator` only selects and constructs the appropriate wrapper. The special pricing and acceptance rules live in the wrapper classes.

## Add a topping without changing state on failure

The order of work in `addTopping` matters.

1. Read the existing serving counts and build a candidate wrapper around the current pizza.
2. Ask the candidate whether the entire addition is allowed.
3. If it fails, return `False` without changing the serving dictionary or the current pizza.
4. If it succeeds, increase the accepted count and make the candidate the current pizza.

Creating a candidate calculates new values inside a separate object. It does not modify the wrapped pizza. Even if a rejected cheeseburst candidate calculated a higher tax rate, that candidate is discarded and the accepted pizza keeps its original rate.

This also prevents a failed request from affecting later decisions. For example, after two cheeseburst servings are rejected on a small pizza, mushroom can still be added because the pizza has no accepted cheeseburst.

The constructor inputs and positive serving counts are guaranteed by the statement. The implementation also returns `False` for an unknown topping name, `None`, or a nonpositive count, without changing the pizza.

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

The dictionary is updated only after the addition succeeds, so the decorator receives the count from before the current request.

## Keep decimal calculations exact

The price calculation has three steps.

1. Subtotal is the base price plus all accepted topping costs.
2. Tax amount is `subtotal × taxRate / 100`.
3. Final price is subtotal plus tax amount, rounded half up to an integer.

For a base tax rate of 12, cheeseburst changes the rate to `12 × 1.30 = 15.6`. Keeping the tax rate as an integer would lose the fractional part.

Normal Python division produces a binary floating-point value. Most decimal fractions cannot be stored exactly in that form, and a tiny error near a `.5` boundary can produce the wrong rounded answer. Python's standard `Decimal` class avoids that problem.

Factors such as `1.30` and `0.90` are created from strings, which preserves those decimal values exactly. Multiplication and addition keep their decimal form.

Only `getFinalPrice()` calls `quantize(Decimal("1"), rounding=ROUND_HALF_UP)`. Python's built-in `round()` is not used because it follows round-to-even behavior for exact ties, while this problem requires round half up.

For example, the large-pizza example has subtotal 1130 and tax rate 15. Its tax is 169.5, giving a final value of 1299.5. Half-up rounding returns **1300**.

Python integers can hold values larger than 32 bits. The final result is capped at `2_147_483_647` only to preserve the behavior of the problem's Java-style `int` return value for an exceptionally large total.

## Keep price queries fast

A basic decorator implementation might ask its wrapped object to recalculate the price on every query. After many additions, that would repeatedly walk through all earlier wrappers and could eventually reach Python's recursion limit.

Our accepted wrapper objects never change. In `__post_init__`, a wrapper reads the previous wrapper's stored subtotal and tax rate, computes its own values, and stores them too.

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

Initially, the serving dictionary is empty, the subtotal equals the base price, and the tax rate equals the constructor's percentage. These values describe the pizza correctly.

Assume they are correct before a topping request. The candidate uses the accepted counts to check the size cap and incompatibility rules. It calculates the correct batch cost, including the medium-pizza corn adjustment, and applies a tax multiplier only when the relevant topping was previously absent.

If the request is rejected, neither the current pizza nor its accepted counts change. If it is accepted, the new wrapper stores exactly the updated subtotal and tax rate, and the dictionary records the accepted servings. The stored values therefore remain correct after either outcome.

Finally, `getFinalPrice()` applies the current rate to the entire accepted subtotal and rounds once using the required half-up rule. Therefore, it returns the required price without changing future behavior.

## Extending the rules

To add a new topping, create a decorator and add a construction condition in `_create_decorator`. The shared commit logic and final-price calculation stay the same.

To add a pineapple serving cap, pass its existing count into `PineappleDecorator` and extend that decorator's acceptance check. A future rule involving another topping can similarly receive the relevant accepted count from the dictionary.

This keeps new business rules close to the topping they affect. A future discount or promotion must also define when it applies and whether it changes the subtotal or the tax rate before its calculation is added.

## Complexity

Each `addTopping` call performs a fixed number of dictionary lookups, rule checks, and decimal calculations. Its expected time is **O(1)**. Each `getFinalPrice` call also takes **O(1)** because the current wrapper already stores the subtotal and tax rate.

Let `A` be the number of successful topping additions. Every successful call creates one wrapper, even if that call adds many servings. The wrapper chain uses **O(A)** space. The serving dictionary uses **O(1)** space because the catalog has six toppings. A rejected candidate adds no retained wrapper.

These time bounds count arithmetic operations. The cost of `Decimal` arithmetic also depends on the number of digits in its values.


## Complete Python solution

```python
from dataclasses import dataclass, field
from decimal import Decimal, ROUND_HALF_UP
from typing import Dict, Optional


def _cost(unit_price: int, count: int) -> Decimal:
    return Decimal(unit_price) * Decimal(count)


class Pizza:
    """Common operations exposed by the base pizza and every decorator."""

    def get_subtotal(self) -> Decimal:
        raise NotImplementedError

    def get_tax_rate(self) -> Decimal:
        raise NotImplementedError


@dataclass(frozen=True)
class PlainPizza(Pizza):
    subtotal: Decimal
    tax_rate: Decimal

    def get_subtotal(self) -> Decimal:
        return self.subtotal

    def get_tax_rate(self) -> Decimal:
        return self.tax_rate


@dataclass(frozen=True)
class ToppingDecorator(Pizza):
    wrapped: Pizza
    extra_cost: Decimal
    tax_multiplier: Decimal = Decimal("1")
    allowed: bool = True
    subtotal: Decimal = field(init=False)
    tax_rate: Decimal = field(init=False)

    def __post_init__(self) -> None:
        # Cache exact results once. Reads never walk the wrapper chain.
        object.__setattr__(
            self,
            "subtotal",
            self.wrapped.get_subtotal() + self.extra_cost,
        )
        object.__setattr__(
            self,
            "tax_rate",
            self.wrapped.get_tax_rate() * self.tax_multiplier,
        )

    def is_allowed(self) -> bool:
        return self.allowed

    def get_subtotal(self) -> Decimal:
        return self.subtotal

    def get_tax_rate(self) -> Decimal:
        return self.tax_rate


class FixedPriceDecorator(ToppingDecorator):
    def __init__(self, wrapped: Pizza, count: int, unit_price: int):
        super().__init__(wrapped, _cost(unit_price, count))


class CheeseburstDecorator(ToppingDecorator):
    TAX_MULTIPLIER = Decimal("1.30")

    def __init__(
        self,
        wrapped: Pizza,
        count: int,
        size: str,
        previous_count: int,
        mushroom_present: bool,
    ):
        total_count = previous_count + count

        within_cap = True
        if size == "small":
            within_cap = total_count <= 1
        elif size == "medium":
            within_cap = total_count <= 2

        tax_multiplier = (
            self.TAX_MULTIPLIER if previous_count == 0 else Decimal("1")
        )
        super().__init__(
            wrapped,
            _cost(100, count),
            tax_multiplier,
            not mushroom_present and within_cap,
        )


class MushroomDecorator(ToppingDecorator):
    TAX_MULTIPLIER = Decimal("0.90")

    def __init__(
        self,
        wrapped: Pizza,
        count: int,
        first_addition: bool,
        cheeseburst_present: bool,
    ):
        tax_multiplier = (
            self.TAX_MULTIPLIER if first_addition else Decimal("1")
        )
        super().__init__(
            wrapped,
            _cost(40, count),
            tax_multiplier,
            not cheeseburst_present,
        )


class PineappleDecorator(ToppingDecorator):
    def __init__(self, wrapped: Pizza, count: int, size: str):
        super().__init__(
            wrapped,
            _cost(60, count),
            allowed=size != "small",
        )


class CornDecorator(ToppingDecorator):
    def __init__(
        self,
        wrapped: Pizza,
        count: int,
        size: str,
        first_addition: bool,
    ):
        if size == "large":
            extra_cost = _cost(20, count)
        elif size == "medium":
            # Only the first corn serving on the whole pizza costs 10 extra.
            extra_for_first = Decimal(10) if first_addition else Decimal(0)
            extra_cost = _cost(40, count) + extra_for_first
        else:
            extra_cost = _cost(50, count)

        super().__init__(wrapped, extra_cost)


class PizzaPricing:
    MAX_INT = 2_147_483_647

    def __init__(self, basePrice, taxPercentage, size):
        self._size = size
        self._servings: Dict[str, int] = {}
        self._pizza: Pizza = PlainPizza(
            Decimal(basePrice),
            Decimal(taxPercentage),
        )

    def addTopping(self, topping, servingsCount):
        if topping is None or servingsCount <= 0:
            return False

        candidate = self._create_decorator(topping, servingsCount)
        if candidate is None or not candidate.is_allowed():
            return False

        # Commit only after every rule has passed. Never apply a partial batch.
        self._servings[topping] = self._servings_of(topping) + servingsCount
        self._pizza = candidate
        return True

    def getFinalPrice(self):
        subtotal = self._pizza.get_subtotal()
        tax_amount = subtotal * self._pizza.get_tax_rate() / Decimal(100)
        rounded = (subtotal + tax_amount).quantize(
            Decimal("1"),
            rounding=ROUND_HALF_UP,
        )

        # Preserve the stated Java-style int behavior for very large totals.
        return min(int(rounded), self.MAX_INT)

    def _servings_of(self, topping: str) -> int:
        return self._servings.get(topping, 0)

    def _create_decorator(
        self,
        topping: str,
        count: int,
    ) -> Optional[ToppingDecorator]:
        if topping == "cheeseburst":
            return CheeseburstDecorator(
                self._pizza,
                count,
                self._size,
                self._servings_of("cheeseburst"),
                self._servings_of("mushroom") > 0,
            )

        if topping == "mushroom":
            return MushroomDecorator(
                self._pizza,
                count,
                self._servings_of("mushroom") == 0,
                self._servings_of("cheeseburst") > 0,
            )

        if topping == "pineapple":
            return PineappleDecorator(self._pizza, count, self._size)

        if topping == "corn":
            return CornDecorator(
                self._pizza,
                count,
                self._size,
                self._servings_of("corn") == 0,
            )

        if topping == "onion":
            return FixedPriceDecorator(self._pizza, count, 30)

        if topping == "capsicum":
            return FixedPriceDecorator(self._pizza, count, 50)

        return None
```
