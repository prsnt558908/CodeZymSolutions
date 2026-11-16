# Design Stock Trading Platform in Java

#### Problem Statement
[https://codezym.com/question/20-design-stock-trading-platform](https://codezym.com/question/20-design-stock-trading-platform)


## Core Idea

We are building a small in-memory trading service. Users hold cash and shares, stocks have prices, and every order takes the same path: **validate** it, **fill** it right away if the price allows, otherwise keep it **OPEN** until a price update makes it fillable.

The only thing that changes between order kinds is the rule *"can this order fill at the current price?"*. That is exactly what the **Strategy pattern** is for, so MARKET and LIMIT each get one small class.

Auto-filling OPEN orders does not need a second pattern: each stock just keeps a list of the orders waiting on it. So one Strategy plus plain maps and lists is the best fit here.


## How an Order Ends Up

| Situation | Status |
|---|---|
| Not enough cash (BUY), not enough shares (SELL), or unknown stock | `REJECTED` |
| Valid and the price is favorable | `FILLED` |
| Valid but the price is not favorable yet | `OPEN` |

The rules behind this table:

- **Valid** means a BUY needs `cash >= qty * currentPrice` and a SELL needs `ownedShares >= qty`.
- **Favorable** means always for MARKET, `currentPrice <= limitPrice` for LIMIT BUY, and `currentPrice >= limitPrice` for LIMIT SELL.
- A fill always happens at the **current price**, not at the limit price. In Example 3, a LIMIT SELL at 110 fills at 120.
- `addStock()` is not just a price setter. In Example 3, when ACME rises to 160, the OPEN LIMIT SELL at 150 fills on its own. So every price update must re-check the OPEN orders of that stock.


## Step 1: A Simple Version

The most direct approach is to keep every order in one big list and use `if/else` for the order kinds.

```java
// one method handles every order kind
boolean canFill(Order order, int price) {
    if (order.orderKind.equals("MARKET")) {
        return true;
    }
    if (order.action.equals("BUY")) {
        return price <= order.limitPrice;
    }
    return price >= order.limitPrice;
}
```

On a price update, we scan every order ever placed to find the OPEN ones for this stock.

```java
// inside addStock(), after updating the price
for (Order order : allOrders) {
    if (order.status.equals("OPEN") && order.stockName.equals(stockName)) {
        tryFill(order, stockPrice);   // fills it if the price is favorable now
    }
}
```

This works, but it has two problems.

**1. Slow price updates.** Every update walks through the full order history of all users, even though most of those orders are already FILLED or REJECTED.

**2. Hard to extend.** Real platforms have more order kinds like STOP or STOP-LIMIT. Each new kind adds one more branch to the same `if/else`, and one wrong edit can break the old rules.


## Step 2: Better Solution

### Fix 1: A waiting list of OPEN orders for each stock

```java
// stockName -> OPEN orders waiting on that stock, oldest first
Map<String, List<Order>> openOrders = new HashMap<>();
```

When an order goes OPEN, we add it to its stock's list. When that stock's price is updated, we check **only this list**. Filled orders leave the list and the rest stay.

Now a price update only touches the orders that are really waiting on that stock.

The list keeps orders in the sequence they were placed, so older orders get the first chance to fill.


### Fix 2: Strategy pattern for order kinds

We move the *"can it fill?"* rule into one small class per order kind.

```java
interface ExecutionStrategy {
    boolean canFill(Order order, int currentPrice);
}
```

`MarketExecution` always returns `true`. `LimitExecution` compares the current price with the limit price, based on BUY or SELL.

The service keeps a map from order kind to strategy, like `"LIMIT" -> LimitExecution`. Each order gets its strategy when it is created, so the service never checks the order kind again.

Adding a new kind like STOP now means one new class and one new map entry. The old rules are never touched.

Why not make BUY and SELL strategies too? There will only ever be two sides, so a simple `if/else` on the action is easier to read.


### Why not other patterns?

**Observer** sounds like a natural fit for *"tell the waiting orders when the price changes"*. But only one kind of thing ever waits on a price (an OPEN order), and only the service reacts to it.

Listener interfaces with subscribe and unsubscribe code would add more code for no real gain. The waiting list from Fix 1 already does this job.

**State** sounds good because orders have statuses. But the statuses have no behavior of their own, and the only change after creation is OPEN to FILLED. A simple status field is enough.


## Important Classes and Data Structures

**`User`** holds one account: cash, holdings and order history. It also has small helpers like `canBuy()` and `sell()`, so the service never changes cash or shares directly.

**`Order`** stores the request and its status. The user's history keeps the same `Order` object, so when an OPEN order fills later, its status changes in place and `viewOrders()` shows FILLED.

**`stocks`** is a `LinkedHashMap`. It works like a normal map but also remembers insertion order, which `showStocks()` needs. A price update only changes the `Stock` object, so the stock never moves in the list.

**`holdings`** is a `LinkedHashMap` inside `User`, from stock name to `Holding`. A `Holding` is created on the **first FILLED buy** of that stock, so the map order is exactly the "first acquired" order that `viewStockHoldings()` needs.

An OPEN order that fills later is added later too, just as the problem expects.

When a user sells all shares of a stock, we keep its `Holding` with quantity 0 and skip it while printing. If the user buys that stock again, it keeps its old position, because that is when it was first acquired.

**`Holding`** also tracks the average buy price, as the requirements ask. It is not part of any output, but it is cheap to keep.


## Tricky Details

**OPEN orders do not lock cash or shares.** While an order waits, the user can still spend that cash or sell those shares through other orders.

So when a price update makes an OPEN order fillable, we validate it again. If the user no longer has enough, the order stays OPEN and gets another chance on the next price update. This way cash and shares never go below zero.

**Overflow.** `qty * price` can go beyond the `int` limit, so `canBuy()` compares using a `long`.


## Complexity

| Method | Time |
|---|---|
| `addUser`, `getAccountBalance`, `submitOrder` | O(1) |
| `addStock` for a new stock | O(1) |
| `addStock` for a price update | O(k), k = OPEN orders on that stock |
| `showStocks`, `viewStockHoldings`, `viewOrders` | O(n), n = rows to scan |


## Final Code

```java
import java.util.*;

/**
 * A tradable stock and its latest price.
 */
class Stock {
    String name;
    int price;

    Stock(String name, int price) {
        this.name = name;
        this.price = price;
    }
}

/**
 * Shares of one stock owned by a user, with the average buy price.
 */
class Holding {
    String stockName;
    int quantity;
    double averagePrice;

    Holding(String stockName) {
        this.stockName = stockName;
    }

    void add(int qty, int price) {
        // weighted average of the old shares and the new shares
        averagePrice = (averagePrice * quantity + (double) qty * price) / (quantity + qty);
        quantity += qty;
    }

    void remove(int qty) {
        quantity -= qty;
    }
}

/**
 * One BUY or SELL request. Its status is updated in place,
 * so an OPEN order that fills later shows FILLED in the history.
 */
class Order {
    String userId;
    String stockName;
    String orderKind;   // MARKET or LIMIT
    String action;      // BUY or SELL
    int qty;
    int limitPrice;
    String status;      // FILLED, OPEN or REJECTED
    ExecutionStrategy strategy;

    Order(String userId, String stockName, String orderKind, String action,
          int qty, int limitPrice, ExecutionStrategy strategy) {
        this.userId = userId;
        this.stockName = stockName;
        this.orderKind = orderKind;
        this.action = action;
        this.qty = qty;
        this.limitPrice = limitPrice;
        this.strategy = strategy;
    }

    boolean canFill(int currentPrice) {
        return strategy.canFill(this, currentPrice);
    }

    String toRow() {
        return stockName + " " + action + " " + qty + " " + orderKind + " " + status;
    }
}

/**
 * One account: cash, holdings and order history.
 */
class User {
    String userId;
    int cash;
    // LinkedHashMap keeps stocks in the order they were first FILLED
    Map<String, Holding> holdings = new LinkedHashMap<>();
    // every order in the order it was placed
    List<Order> orders = new ArrayList<>();

    User(String userId, int cash) {
        this.userId = userId;
        this.cash = cash;
    }

    int getQuantity(String stockName) {
        Holding holding = holdings.get(stockName);
        return holding == null ? 0 : holding.quantity;
    }

    boolean canBuy(int qty, int price) {
        // long avoids int overflow when qty * price is very large
        return cash >= (long) qty * price;
    }

    boolean canSell(String stockName, int qty) {
        return getQuantity(stockName) >= qty;
    }

    void buy(String stockName, int qty, int price) {
        cash -= qty * price;
        Holding holding = holdings.get(stockName);
        if (holding == null) {
            holding = new Holding(stockName);
            holdings.put(stockName, holding);
        }
        holding.add(qty, price);
    }

    void sell(String stockName, int qty, int price) {
        cash += qty * price;
        // the entry stays even at 0 shares, so the stock keeps its first acquired position
        holdings.get(stockName).remove(qty);
    }
}

/**
 * Strategy: decides if an order can fill at the current price.
 * Every order kind has its own rule.
 */
interface ExecutionStrategy {
    boolean canFill(Order order, int currentPrice);
}

/**
 * MARKET orders always fill at the current price.
 */
class MarketExecution implements ExecutionStrategy {
    public boolean canFill(Order order, int currentPrice) {
        return true;
    }
}

/**
 * LIMIT orders fill only when the current price is favorable.
 */
class LimitExecution implements ExecutionStrategy {
    public boolean canFill(Order order, int currentPrice) {
        if (order.action.equals("BUY")) {
            return currentPrice <= order.limitPrice;
        }
        return currentPrice >= order.limitPrice;
    }
}

public class TradingService {
    Map<String, User> users = new HashMap<>();
    // LinkedHashMap keeps stocks in the order they were first added
    Map<String, Stock> stocks = new LinkedHashMap<>();
    // stockName -> OPEN orders waiting on that stock, oldest first
    Map<String, List<Order>> openOrders = new HashMap<>();
    // orderKind -> its execution strategy
    Map<String, ExecutionStrategy> strategies = new HashMap<>();

    public TradingService() {
        strategies.put("MARKET", new MarketExecution());
        strategies.put("LIMIT", new LimitExecution());
    }

    public void addUser(String userId, int openingCash) {
        if (!users.containsKey(userId)) {
            users.put(userId, new User(userId, openingCash));
        }
    }

    public int getAccountBalance(String userId) {
        User user = users.get(userId);
        return user == null ? 0 : user.cash;
    }

    public void addStock(String stockName, int stockPrice) {
        Stock stock = stocks.get(stockName);
        if (stock == null) {
            stocks.put(stockName, new Stock(stockName, stockPrice));
            openOrders.put(stockName, new ArrayList<>());
            return;
        }
        stock.price = stockPrice;
        // the new price may make some waiting orders fillable
        retryOpenOrders(stock);
    }

    public List<String> showStocks() {
        List<String> rows = new ArrayList<>();
        for (Stock stock : stocks.values()) {
            rows.add(stock.name + " " + stock.price);
        }
        return rows;
    }

    public String submitOrder(String userId, String stockName, String orderKind,
                              String action, int qty, int limitPrice) {
        User user = users.get(userId);
        if (user == null) {
            return "REJECTED";
        }
        Order order = new Order(userId, stockName, orderKind, action, qty, limitPrice,
                strategies.get(orderKind));
        user.orders.add(order);

        Stock stock = stocks.get(stockName);
        if (stock == null || !isValid(user, order, stock.price)) {
            order.status = "REJECTED";
        } else if (order.canFill(stock.price)) {
            fill(user, order, stock.price);
        } else {
            order.status = "OPEN";
            openOrders.get(stockName).add(order);
        }
        return order.status;
    }

    public List<String> viewStockHoldings(String userId) {
        List<String> rows = new ArrayList<>();
        User user = users.get(userId);
        if (user == null) {
            return rows;
        }
        for (Holding holding : user.holdings.values()) {
            // stocks that were fully sold are kept but not shown
            if (holding.quantity > 0) {
                rows.add(holding.stockName + " " + holding.quantity);
            }
        }
        return rows;
    }

    public List<String> viewOrders(String userId) {
        List<String> rows = new ArrayList<>();
        User user = users.get(userId);
        if (user == null) {
            return rows;
        }
        for (Order order : user.orders) {
            rows.add(order.toRow());
        }
        return rows;
    }

    /**
     * BUY needs enough cash at the current price. SELL needs enough shares.
     */
    boolean isValid(User user, Order order, int price) {
        if (order.action.equals("BUY")) {
            return user.canBuy(order.qty, price);
        }
        return user.canSell(order.stockName, order.qty);
    }

    /**
     * Executes the order at the stock's current price.
     */
    void fill(User user, Order order, int price) {
        if (order.action.equals("BUY")) {
            user.buy(order.stockName, order.qty, price);
        } else {
            user.sell(order.stockName, order.qty, price);
        }
        order.status = "FILLED";
    }

    /**
     * Re-checks the waiting orders of one stock after its price changed.
     * An order fills only if the price is favorable and the user still
     * has enough cash or shares. Every other order stays OPEN.
     */
    void retryOpenOrders(Stock stock) {
        List<Order> stillOpen = new ArrayList<>();
        for (Order order : openOrders.get(stock.name)) {
            User user = users.get(order.userId);
            if (order.canFill(stock.price) && isValid(user, order, stock.price)) {
                fill(user, order, stock.price);
            } else {
                stillOpen.add(order);
            }
        }
        openOrders.put(stock.name, stillOpen);
    }
}
```