# Design a Product Plan Cost Explorer in Python

#### Problem Statement
[https://codezym.com/question/38-design-product-plan-cost-explorer](https://codezym.com/question/38-design-product-plan-cost-explorer)


## Core Idea

The whole solution rests on one rule: **keep each price in only one place**. A product keeps the prices of its plans. A subscription only remembers the product name, the plan id and the start year and month. It never stores a price.


Whenever we compute a bill, we read the current price from the product. So when a plan is removed, the lookup simply returns 0, and every subscriber is billed correctly without changing a single subscription.


Billing is per full month, so the day in the start date never matters. We only keep the start year and month.


No design pattern is needed for this problem. Two small classes (`Product` and `Subscription`) and two dictionaries inside `CostExplorer` are enough. Observer and Strategy may look useful at first, but here they would only add extra code. We explain why below.


## The Rules in Simple Words

- Each product has a list of plans, and each plan has a monthly price.
- Re-adding a product replaces its whole plan list. A plan that is no longer in the list costs 0.
- Subscribing again to the same product replaces the old subscription (new start date and new plan).
- `annualCost` is the sum of the 12 monthly costs.


For a given year, each subscription is charged like this:

| Subscription starts in | Months charged in that year |
|---|---|
| an earlier year | all 12 months |
| the same year | from the start month to December |
| a later year | none |


## First Idea: Save the Price Inside the Subscription

The first thing that comes to mind is to copy the plan's price into the subscription when the customer subscribes. Then `monthlyCost` only needs to add up the saved prices.


This breaks as soon as a product is re-added:

```python
ce.addProduct("jira", ["BASIC,100"])
ce.subscribe("acme-corp", "2025-03-10", "jira", "BASIC")  # subscription saves 100

ce.addProduct("jira", ["PREMIUM,200"])                    # BASIC is removed

ce.monthlyCost("acme-corp", 2025)
# the saved copy still says 100, but the right answer is 0 for every month
```


To fix this, every `addProduct` call would have to find every subscription of that product and correct its saved price. That makes `addProduct` slow, and keeping two copies of the same price in sync is easy to get wrong.


## Better Idea: Read the Price When You Need It

Store only the plan id in the subscription, and look up the price from the product every time we compute a cost.

- **Plan removed**: the lookup finds nothing and returns 0.
- **Price changed**: the lookup returns the new price.
- **Product re-added**: `addProduct` replaces one dictionary entry. Nothing else needs fixing.


## Do We Need a Design Pattern?

**Observer** sounds like a natural fit: a product could notify all its subscriptions whenever its plans change. But that is only needed when subscriptions keep their own copy of the price. With the "read when needed" approach there is nothing to notify. Observer would only add extra bookkeeping (register and unregister subscriptions) and make `addProduct` slower.


**Strategy** could make the billing rule swappable, for example full month billing or per-day billing. But this problem has exactly one rule: charge the full month. A base class with a single implementation just adds extra classes. It can be added later if a second billing rule ever shows up.


So plain classes with dictionaries are the simplest and cleanest choice here.


## Classes and Data Structures

### `Product`
Keeps `plan_prices`, a dictionary of planId → monthly price.


A dictionary gives us a plan's price in O(1). `get_price(plan_id)` returns 0 when the plan is not in the dictionary, which is exactly the "removed plan costs 0" rule.


### `Subscription`
Keeps `product_name`, `plan_id`, `start_year` and `start_month`.


The date is parsed only once, when the subscription is created, so `monthlyCost` never has to parse strings. `is_billed_in(year, month)` answers one simple question: is this subscription charged in this month? It is the table above written as code.


It stores the product **name**, not the `Product` object. Re-adding a product creates a brand new `Product` object, so a saved object would hold old prices. Looking up by name always gives the latest one.


### `CostExplorer`
- `products`: productName → `Product`
- `subscriptions`: customerId → {productName → `Subscription`}


The inner dictionary is keyed by product name because subscribing again to the same product must update that subscription. With this key, a simple assignment overwrites the old one. No searching needed.


## How Each Method Works

- **addProduct**: build a new `Product` from the plan strings and store it in `products`. An old product with the same name is replaced.
- **subscribe**: create a `Subscription` and store it in the customer's dictionary under the product name.
- **monthlyCost**: for each month from 1 to 12, add the current price of every subscription that is billed in that month.
- **annualCost**: add up the 12 values returned by `monthlyCost`.


## Dry Run (Example 2)

jira BASIC (50) starts in January 2025. confluence STANDARD (80) starts in July 2025.

| Months | jira | confluence | Total |
|---|---|---|---|
| Jan to Jun | 50 | 0 | 50 |
| Jul to Dec | 50 | 80 | 130 |


Annual cost = 6 × 50 + 6 × 130 = **1080**.


## Complexity

`P` = number of plans passed to `addProduct`, `S` = number of products the customer has subscribed to.

| Method | Time |
|---|---|
| `addProduct` | O(P) |
| `subscribe` | O(1) |
| `monthlyCost` | O(12 × S) = O(S) |
| `annualCost` | O(S) |


Space is O(total plans + total subscriptions).


## Python Code

```python
class Product:
    """A product and the monthly price of each of its plans."""

    def __init__(self, name, plans):
        self.name = name
        self.plan_prices = {}  # planId -> monthly price

        # each plan looks like "PLANID,monthlyPrice", for example "BASIC,100"
        for plan in plans:
            plan_id, price = plan.split(",")
            self.plan_prices[plan_id.strip()] = int(price)

    def get_price(self, plan_id):
        """Returns the monthly price of the plan, or 0 if the plan was removed."""
        return self.plan_prices.get(plan_id, 0)


class Subscription:
    """
    One customer's subscription to one product.
    It keeps the planId, never the price, so the price is always read fresh from the product.
    """

    def __init__(self, product_name, plan_id, start_date):
        self.product_name = product_name
        self.plan_id = plan_id

        # "YYYY-MM-DD": the day is ignored because the full month is charged
        parts = start_date.strip().split("-")
        self.start_year = int(parts[0])
        self.start_month = int(parts[1])  # 1 = Jan, ..., 12 = Dec

    def is_billed_in(self, year, month):
        """Is this subscription charged in the given month (1 to 12) of the given year?"""
        if self.start_year < year:
            return True  # started in an earlier year: every month is charged
        if self.start_year > year:
            return False  # starts in a later year: nothing is charged
        return self.start_month <= month  # same year: charged from the start month onward


class CostExplorer:
    def __init__(self):
        self.products = {}  # productName -> Product
        self.subscriptions = {}  # customerId -> {productName -> Subscription}

    def addProduct(self, productName, plans):
        """Adds a new product, or replaces the whole plan list of an existing one."""
        self.products[productName] = Product(productName, plans)

    def subscribe(self, customerId, startDate, productName, planId):
        """Subscribing again to the same product replaces the old subscription."""
        if customerId not in self.subscriptions:
            self.subscriptions[customerId] = {}
        self.subscriptions[customerId][productName] = Subscription(productName, planId, startDate)

    def monthlyCost(self, customerId, year):
        """Returns 12 values: index 0 = Jan, ..., index 11 = Dec."""
        customer_subs = self.subscriptions.get(customerId, {})
        costs = []
        for month in range(1, 13):
            total = 0
            for sub in customer_subs.values():
                if sub.is_billed_in(year, month):
                    total += self.current_price(sub)
            costs.append(total)
        return costs

    def annualCost(self, customerId, year):
        """Sum of the 12 monthly costs."""
        return sum(self.monthlyCost(customerId, year))

    def current_price(self, sub):
        """Reads the current price from the product, so removed or re-priced plans are handled."""
        product = self.products.get(sub.product_name)
        if product is None:
            return 0
        return product.get_price(sub.plan_id)
```