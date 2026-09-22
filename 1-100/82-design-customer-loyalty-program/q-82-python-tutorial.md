# Design a Customer Loyalty Program in Python

#### Problem Statement

[https://codezym.com/question/82-design-customer-loyalty-program](https://codezym.com/question/82-design-customer-loyalty-program)

Store each customer in a map, along with their points and purchase totals. Keep separate counters for purchases since the last discount. Every level follows the same purchase steps, so a few immutable data objects for level rules are enough. A separate design pattern hierarchy would add code without simplifying this problem.


## 1. Start With a Simple Approach

We could store every purchase in a list. Before each new purchase, scan the customer's history to calculate spending and count orders since the last discount.

This takes O(P) time per purchase when that customer has P past purchases. The APIs never ask for individual transactions, so we can keep running totals instead.


## 2. Store Only What We Need

Use a dictionary mapping each username to a `User` object to find a customer in average O(1) time.

The `User` dataclass holds five values:

- `points`: the current points balance.
- `order_count`: the lifetime number of successful purchases.
- `total_spent`: the lifetime amount actually paid with money.
- `cycle_orders`: successful purchases since the last discounted purchase.
- `cycle_spent`: money paid on those purchases.

The last two fields let us restart discount eligibility without losing lifetime stats. We derive the level from the current balance whenever needed, so there is no stored level that can become outdated.

The frozen `Level` dataclass groups the rules into three shared, immutable objects:

- **Bronze:** below 500 points. Earn 10 points per ₹100. Redeem at most 5% of the order and at most 200 points.
- **Silver:** at least 500 but below 1000 points. Earn 12.5 points per ₹100. Redeem at most 10% and at most 500 points.
- **Gold:** at least 1000 points. Earn 15 points per ₹100. Redeem at most 15% and at most 1000 points.

A Strategy class for each level is unnecessary here. The formula and steps are identical, and only these numbers change. Keeping the rules together also avoids repeating level checks throughout the purchase method.


## 3. Process a Purchase in Order

### Validate Before Changing Anything

Return the first applicable error in this order:

1. Unknown user: `USER_NOT_FOUND`.
2. Order amount at or below zero: `INVALID_ORDER_AMOUNT`.
3. Negative redemption: `INVALID_REDEEM_POINTS`.
4. Redemption greater than the available balance: `NOT_ENOUGH_POINTS`.
5. Redemption above either level limit: `REDEMPTION_LIMIT_EXCEEDED`.

For example, requesting 210 points while holding only 90 returns `NOT_ENOUGH_POINTS`, even if 210 also exceeds the level cap. Failed purchases change nothing.

### Use the Starting Level

Capture the customer's level before redeeming any points. Use that same level for both redemption limits and earning new points.

First subtract redeemed points from the order amount. Each point pays for ₹1. If a bonus discount is requested, calculate it on this remaining amount.

The discount depends only on the history in the current cycle before this purchase:

- More than three orders gives 5%.
- Spending above ₹10,000 gives 10%.
- Meeting both conditions gives 12%, not the sum of the two rates.

Cap the discount at ₹5000. Exactly three orders or exactly ₹10,000 does not meet the corresponding condition.

The final payable is the amount after redemption minus the discount. Earn points on that payable amount using the starting level's rate.

### Update the Balance and History

Subtract redeemed points and add newly earned points. Increment the lifetime order count and add the final payable to lifetime spending.

If the rounded discount is positive, reset both cycle counters to zero. The discounted purchase itself does not count toward the next cycle, because that cycle starts after this purchase finishes.

Otherwise, add the successful purchase to the current cycle. Asking for a discount while ineligible does not reset anything. Choosing `applyDiscount = False` also preserves and extends the current cycle.

Finally, derive the new level from the updated balance. A customer can move down a level when redemption reduces their balance enough.


## 4. Keep Two-Decimal Values Consistent

Store money and points as integer hundredths using Python's `int`. For example, ₹950.25 is stored as `95025`, and 337.50 points is stored as `33750`.

Inputs can have more than two decimal places. After checking their signs, normalize them by multiplying by 100 and rounding to an integer. Round the calculated redemption percentage cap, discount, and earned points to whole internal units, which gives two decimal places in the original units. Adding and subtracting these stored values is exact.

Python's built-in `round()` rounds halfway cases to the nearest even integer. Use `floor(value + 0.5)` for the nonnegative calculated values here to match the required half-up behavior. For example, earning 0.005 points produces 0.01 points.

The earning formula stays simple because both quantities use the same scale:

```text
earned_point_units = floor(payable_money_units * earn_rate / 100 + 0.5)
```

Round the percentage cap before comparing it with the normalized redemption request. For a Bronze order of ₹999.99, the 5% cap is 49.9995 points, which rounds to 50.00. A request to redeem 49.999 points also rounds to 50.00, so it passes this limit if the customer has enough points.

The formatting helper prints the whole part and exactly two fractional digits. Integer division, remainder, and an f-string produce the required format without converting the balance back to a float.


## 5. Walk Through a Silver Purchase

Suppose a customer has 500 points and buys an order worth ₹3000, redeeming 300 points without a discount.

1. The starting level is Silver. Its percentage limit is 10% of ₹3000, which is 300 points. The request also fits the balance and the 500-point cap.
2. The customer pays ₹2700 after redemption.
3. Earned points are `(2700 / 100) * 12.5 = 337.50`.
4. The new balance is `500 - 300 + 337.50 = 537.50`, so the customer remains Silver.

The successful purchase also adds one order and ₹2700 to both lifetime history and the current discount cycle.


## 6. Complexity

Each API call takes average O(1) time. Total storage is O(U) for U registered users. We keep a fixed number of fields per user and do not store individual purchases.


## 7. Complete Python Code

```python
from dataclasses import dataclass
from math import floor
from typing import Dict, List


# Rules differ only by numbers, so each level is one immutable data object.
@dataclass(frozen=True)
class Level:
    label: str
    earn_rate: float
    max_redeem_percent: int
    max_redeem_points: int


BRONZE = Level("Bronze", 10.0, 5, 200)
SILVER = Level("Silver", 12.5, 10, 500)
GOLD = Level("Gold", 15.0, 15, 1000)


@dataclass
class User:
    # Money is stored in paise. Points are stored in hundredths.
    points: int = 0
    order_count: int = 0
    total_spent: int = 0

    # Only purchases after the last discounted purchase belong here.
    cycle_orders: int = 0
    cycle_spent: int = 0


class EcommerceLoyaltyProgram:
    def __init__(self):
        self.users: Dict[str, User] = {}

    def onboard(self, userName: str) -> str:
        if userName in self.users:
            return "USER_ALREADY_EXISTS," + userName
        self.users[userName] = User()
        return "ONBOARDED," + userName

    def purchase(
        self,
        userName: str,
        orderAmount: float,
        pointsToRedeem: float,
        applyDiscount: bool,
    ) -> str:
        user = self.users.get(userName)

        # Check errors in the required order, before changing any state.
        if user is None:
            return "USER_NOT_FOUND"
        if orderAmount <= 0:
            return "INVALID_ORDER_AMOUNT"
        if pointsToRedeem < 0:
            return "INVALID_REDEEM_POINTS"

        amount = self._to_units(orderAmount)
        redeemed = self._to_units(pointsToRedeem)
        starting_level = self._level_for(user.points)

        if redeemed > user.points:
            return "NOT_ENOUGH_POINTS"

        # Round the percentage cap to hundredths before comparing it.
        percent_limit = self._round_units(
            amount * starting_level.max_redeem_percent / 100.0
        )
        if (
            redeemed > starting_level.max_redeem_points * 100
            or redeemed > percent_limit
        ):
            return "REDEMPTION_LIMIT_EXCEEDED"

        after_redemption = amount - redeemed
        discount_percent = self._discount_percent_for(user) if applyDiscount else 0
        discount = min(
            500_000,
            self._round_units(after_redemption * discount_percent / 100.0),
        )
        payable = after_redemption - discount

        # The starting level also decides the earning rate for this purchase.
        earned = self._round_units(payable * starting_level.earn_rate / 100.0)

        user.points = user.points - redeemed + earned
        user.order_count += 1
        user.total_spent += payable

        if discount > 0:
            # The discounted purchase is excluded from the new cycle.
            user.cycle_orders = 0
            user.cycle_spent = 0
        else:
            user.cycle_orders += 1
            user.cycle_spent += payable

        return ",".join(
            [
                "PURCHASE_SUCCESS",
                self._format(redeemed),
                self._format(discount),
                self._format(earned),
                self._format(payable),
                self._format(user.points),
                self._level_for(user.points).label,
                str(user.order_count),
            ]
        )

    def getUserStats(self, userName: str) -> List[str]:
        user = self.users.get(userName)
        if user is None:
            return ["USER_NOT_FOUND," + userName]

        return [
            "USER," + userName,
            "POINTS," + self._format(user.points),
            "LEVEL," + self._level_for(user.points).label,
            "ORDERS," + str(user.order_count),
            "TOTAL_SPENT," + self._format(user.total_spent),
        ]

    @staticmethod
    def _level_for(points: int) -> Level:
        if points >= 100_000:
            return GOLD
        if points >= 50_000:
            return SILVER
        return BRONZE

    @staticmethod
    def _discount_percent_for(user: User) -> int:
        enough_orders = user.cycle_orders > 3
        enough_spending = user.cycle_spent > 1_000_000
        if enough_orders and enough_spending:
            return 12
        if enough_spending:
            return 10
        return 5 if enough_orders else 0

    @staticmethod
    def _round_units(value: float) -> int:
        # Calculated values are nonnegative. Round half up, as in Java.
        return floor(value + 0.5)

    @staticmethod
    def _to_units(value: float) -> int:
        return EcommerceLoyaltyProgram._round_units(value * 100.0)

    @staticmethod
    def _format(units: int) -> str:
        return f"{units // 100}.{units % 100:02d}"
```
