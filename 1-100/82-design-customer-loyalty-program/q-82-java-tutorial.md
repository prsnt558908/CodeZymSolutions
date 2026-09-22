# Design a Customer Loyalty Program in Java

#### Problem Statement

[https://codezym.com/question/82-design-customer-loyalty-program](https://codezym.com/question/82-design-customer-loyalty-program)

Store each customer in a map, along with their points and purchase totals. Keep separate counters for purchases since the last discount. Every level follows the same purchase steps, so a small enum of level rules is enough. A separate design pattern hierarchy would add code without simplifying this problem.


## 1. Start With a Simple Approach

We could store every purchase in a list. Before each new purchase, scan the customer's history to calculate spending and count orders since the last discount.

This takes O(P) time per purchase when that customer has P past purchases. The APIs never ask for individual transactions, so we can keep running totals instead.


## 2. Store Only What We Need

Use a `HashMap<String, User>` to find a customer by name in average O(1) time.

The `User` class holds five values:

- `points`: the current points balance.
- `orderCount`: the lifetime number of successful purchases.
- `totalSpent`: the lifetime amount actually paid with money.
- `cycleOrders`: successful purchases since the last discounted purchase.
- `cycleSpent`: money paid on those purchases.

The last two fields let us restart discount eligibility without losing lifetime stats. We derive the level from the current balance whenever needed, so there is no stored level that can become outdated.

The `Level` enum groups each level's rules:

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

Otherwise, add the successful purchase to the current cycle. Asking for a discount while ineligible does not reset anything. Choosing `applyDiscount = false` also preserves and extends the current cycle.

Finally, derive the new level from the updated balance. A customer can move down a level when redemption reduces their balance enough.


## 4. Keep Two-Decimal Values Consistent

Store money and points as integer hundredths using `long`. For example, ₹950.25 is stored as `95025`, and 337.50 points is stored as `33750`.

Inputs can have more than two decimal places. After checking their signs, normalize them with `Math.round(value * 100.0)`. Round the calculated redemption percentage cap, discount, and earned points to whole internal units, which gives two decimal places in the original units. Adding and subtracting these stored values is exact.

The earning formula stays simple because both quantities use the same scale:

```text
earnedPointUnits = round(payableMoneyUnits * earnRate / 100)
```

Round the percentage cap before comparing it with the normalized redemption request. For a Bronze order of ₹999.99, the 5% cap is 49.9995 points, which rounds to 50.00. A request to redeem 49.999 points also rounds to 50.00, so it passes this limit if the customer has enough points.

The formatting helper prints the whole part and exactly two fractional digits. Using `Locale.ROOT` keeps the output independent of the machine's locale.


## 5. Walk Through a Silver Purchase

Suppose a customer has 500 points and buys an order worth ₹3000, redeeming 300 points without a discount.

1. The starting level is Silver. Its percentage limit is 10% of ₹3000, which is 300 points. The request also fits the balance and the 500-point cap.
2. The customer pays ₹2700 after redemption.
3. Earned points are `(2700 / 100) * 12.5 = 337.50`.
4. The new balance is `500 - 300 + 337.50 = 537.50`, so the customer remains Silver.

The successful purchase also adds one order and ₹2700 to both lifetime history and the current discount cycle.


## 6. Complexity

Each API call takes average O(1) time. Total storage is O(U) for U registered users. We keep a fixed number of fields per user and do not store individual purchases.


## 7. Complete Java Code

```java
import java.util.*;

public class EcommerceLoyaltyProgram {
    // Rules differ only by numbers, so each level is one enum entry.
    private enum Level {
        BRONZE("Bronze", 10.0, 5, 200),
        SILVER("Silver", 12.5, 10, 500),
        GOLD("Gold", 15.0, 15, 1000);

        final String label;
        final double earnRate;
        final int maxRedeemPercent;
        final int maxRedeemPoints;

        Level(String label, double earnRate, int maxRedeemPercent,
              int maxRedeemPoints) {
            this.label = label;
            this.earnRate = earnRate;
            this.maxRedeemPercent = maxRedeemPercent;
            this.maxRedeemPoints = maxRedeemPoints;
        }
    }

    private static class User {
        // Money is stored in paise. Points are stored in hundredths.
        long points;
        int orderCount;
        long totalSpent;

        // Only purchases after the last discounted purchase belong here.
        int cycleOrders;
        long cycleSpent;
    }

    private final Map<String, User> users;

    public EcommerceLoyaltyProgram() {
        users = new HashMap<>();
    }

    public String onboard(String userName) {
        if (users.containsKey(userName)) {
            return "USER_ALREADY_EXISTS," + userName;
        }
        users.put(userName, new User());
        return "ONBOARDED," + userName;
    }

    public String purchase(String userName, double orderAmount,
                           double pointsToRedeem, boolean applyDiscount) {
        User user = users.get(userName);

        // Check errors in the required order, before changing any state.
        if (user == null) {
            return "USER_NOT_FOUND";
        }
        if (orderAmount <= 0) {
            return "INVALID_ORDER_AMOUNT";
        }
        if (pointsToRedeem < 0) {
            return "INVALID_REDEEM_POINTS";
        }

        long amount = toUnits(orderAmount);
        long redeemed = toUnits(pointsToRedeem);
        Level startingLevel = levelFor(user.points);

        if (redeemed > user.points) {
            return "NOT_ENOUGH_POINTS";
        }

        // Round the percentage cap to hundredths before comparing it.
        long percentLimit = Math.round(
                amount * startingLevel.maxRedeemPercent / 100.0);
        if (redeemed > startingLevel.maxRedeemPoints * 100L
                || redeemed > percentLimit) {
            return "REDEMPTION_LIMIT_EXCEEDED";
        }

        long afterRedemption = amount - redeemed;
        int discountPercent = applyDiscount ? discountPercentFor(user) : 0;
        long discount = Math.min(500_000L,
                Math.round(afterRedemption * discountPercent / 100.0));
        long payable = afterRedemption - discount;

        // The starting level also decides the earning rate for this purchase.
        long earned = Math.round(payable * startingLevel.earnRate / 100.0);

        user.points = user.points - redeemed + earned;
        user.orderCount++;
        user.totalSpent += payable;

        if (discount > 0) {
            // The discounted purchase is excluded from the new cycle.
            user.cycleOrders = 0;
            user.cycleSpent = 0;
        } else {
            user.cycleOrders++;
            user.cycleSpent += payable;
        }

        return "PURCHASE_SUCCESS," + format(redeemed)
                + "," + format(discount)
                + "," + format(earned)
                + "," + format(payable)
                + "," + format(user.points)
                + "," + levelFor(user.points).label
                + "," + user.orderCount;
    }

    public List<String> getUserStats(String userName) {
        User user = users.get(userName);
        List<String> stats = new ArrayList<>();
        if (user == null) {
            stats.add("USER_NOT_FOUND," + userName);
            return stats;
        }

        stats.add("USER," + userName);
        stats.add("POINTS," + format(user.points));
        stats.add("LEVEL," + levelFor(user.points).label);
        stats.add("ORDERS," + user.orderCount);
        stats.add("TOTAL_SPENT," + format(user.totalSpent));
        return stats;
    }

    private static Level levelFor(long points) {
        if (points >= 100_000L) {
            return Level.GOLD;
        }
        if (points >= 50_000L) {
            return Level.SILVER;
        }
        return Level.BRONZE;
    }

    private static int discountPercentFor(User user) {
        boolean enoughOrders = user.cycleOrders > 3;
        boolean enoughSpending = user.cycleSpent > 1_000_000L;
        if (enoughOrders && enoughSpending) {
            return 12;
        }
        if (enoughSpending) {
            return 10;
        }
        return enoughOrders ? 5 : 0;
    }

    private static long toUnits(double value) {
        return Math.round(value * 100.0);
    }

    private static String format(long units) {
        return String.format(Locale.ROOT, "%d.%02d", units / 100, units % 100);
    }
}
```
