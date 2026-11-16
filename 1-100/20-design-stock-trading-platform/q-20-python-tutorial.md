# Design Stock Trading Platform in Python

#### Problem Statement
[https://codezym.com/question/20-design-stock-trading-platform](https://codezym.com/question/20-design-stock-trading-platform)


## Core Idea

We are building a small in-memory trading service. Users hold cash and shares, stocks have prices, and every order takes the same path: **validate** it, **fill** it right away if the price allows, otherwise keep it **OPEN** until a price update makes it fillable.

The only thing that changes between order kinds is the rule *"can this order fill at the current price?"*. That is exactly what the **Strategy pattern** is for, so MARKET and LIMIT each get one small class.

Auto-filling OPEN orders does not need a second pattern: each stock just keeps a list of the orders waiting on it. So one Strategy plus plain dicts and lists is the best fit here.


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

```python
# one method handles every order kind
def canFill(self, order, price):
    if order.orderKind == "MARKET":
        return True
    if order.action == "BUY":
        return price <= order.limitPrice
    return price >= order.limitPrice
```

On a price update, we scan every order ever placed to find the OPEN ones for this stock.

```python
# inside addStock(), after updating the price
for order in self.allOrders:
    if order.status == "OPEN" and order.stockName == stockName:
        self.tryFill(order, stockPrice)   # fills it if the price is favorable now
```

This works, but it has two problems.

**1. Slow price updates.** Every update walks through the full order history of all users, even though most of those orders are already FILLED or REJECTED.

**2. Hard to extend.** Real platforms have more order kinds like STOP or STOP-LIMIT. Each new kind adds one more branch to the same `if/else`, and one wrong edit can break the old rules.


## Step 2: Better Solution

### Fix 1: A waiting list of OPEN orders for each stock

```python
# stockName -> OPEN orders waiting on that stock, oldest first
self.openOrders: Dict[str, List[Order]] = {}
```

When an order goes OPEN, we add it to its stock's list. When that stock's price is updated, we check **only this list**. Filled orders leave the list and the rest stay.

Now a price update only touches the orders that are really waiting on that stock.

The list keeps orders in the sequence they were placed, so older orders get the first chance to fill.


### Fix 2: Strategy pattern for order kinds

We move the *"can it fill?"* rule into one small class per order kind.

```python
class ExecutionStrategy(ABC):
    @abstractmethod
    def canFill(self, order: Order, currentPrice: int) -> bool:
        pass
```

`MarketExecution` always returns `True`. `LimitExecution` compares the current price with the limit price, based on BUY or SELL.

The service keeps a dict from order kind to strategy, like `"LIMIT": LimitExecution()`. Each order gets its strategy when it is created, so the service never checks the order kind again.

Adding a new kind like STOP now means one new class and one new dict entry. The old rules are never touched.

Why not make BUY and SELL strategies too? There will only ever be two sides, so a simple `if/else` on the action is easier to read.


### Why not other patterns?

**Observer** sounds like a natural fit for *"tell the waiting orders when the price changes"*. But only one kind of thing ever waits on a price (an OPEN order), and only the service reacts to it.

Listener classes with subscribe and unsubscribe code would add more code for no real gain. The waiting list from Fix 1 already does this job.

**State** sounds good because orders have statuses. But the statuses have no behavior of their own, and the only change after creation is OPEN to FILLED. A simple status field is enough.


## Important Classes and Data Structures

**`User`** holds one account: cash, holdings and order history. It also has small helpers like `canBuy()` and `sell()`, so the service never changes cash or shares directly.

**`Order`** stores the request and its status. The user's history keeps the same `Order` object, so when an OPEN order fills later, its status changes in place and `viewOrders()` shows FILLED.

**`stocks`** is a plain `dict`. Python dicts remember insertion order, which `showStocks()` needs. A price update only changes the `Stock` object, so the stock never moves in the list.

**`holdings`** is a `dict` inside `User`, from stock name to `Holding`. A `Holding` is created on the **first FILLED buy** of that stock, so the dict order is exactly the "first acquired" order that `viewStockHoldings()` needs.

An OPEN order that fills later is added later too, just as the problem expects.

When a user sells all shares of a stock, we keep its `Holding` with quantity 0 and skip it while printing. If the user buys that stock again, it keeps its old position, because that is when it was first acquired.

**`Holding`** also tracks the average buy price, as the requirements ask. It is not part of any output, but it is cheap to keep.


## Tricky Details

**OPEN orders do not lock cash or shares.** While an order waits, the user can still spend that cash or sell those shares through other orders.

So when a price update makes an OPEN order fillable, we validate it again. If the user no longer has enough, the order stays OPEN and gets another chance on the next price update. This way cash and shares never go below zero.


## Complexity

| Method | Time |
|---|---|
| `addUser`, `getAccountBalance`, `submitOrder` | O(1) |
| `addStock` for a new stock | O(1) |
| `addStock` for a price update | O(k), k = OPEN orders on that stock |
| `showStocks`, `viewStockHoldings`, `viewOrders` | O(n), n = rows to scan |


## Final Code

```python
from abc import ABC, abstractmethod
from typing import Dict, List


class Stock:
    """A tradable stock and its latest price."""

    def __init__(self, name: str, price: int):
        self.name = name
        self.price = price


class Holding:
    """Shares of one stock owned by a user, with the average buy price."""

    def __init__(self, stockName: str):
        self.stockName = stockName
        self.quantity = 0
        self.averagePrice = 0.0

    def add(self, qty: int, price: int) -> None:
        # weighted average of the old shares and the new shares
        self.averagePrice = (self.averagePrice * self.quantity + qty * price) / (self.quantity + qty)
        self.quantity += qty

    def remove(self, qty: int) -> None:
        self.quantity -= qty


class Order:
    """
    One BUY or SELL request. Its status is updated in place,
    so an OPEN order that fills later shows FILLED in the history.
    """

    def __init__(self, userId: str, stockName: str, orderKind: str, action: str,
                 qty: int, limitPrice: int, strategy: "ExecutionStrategy"):
        self.userId = userId
        self.stockName = stockName
        self.orderKind = orderKind    # MARKET or LIMIT
        self.action = action          # BUY or SELL
        self.qty = qty
        self.limitPrice = limitPrice
        self.status = ""              # FILLED, OPEN or REJECTED
        self.strategy = strategy

    def canFill(self, currentPrice: int) -> bool:
        return self.strategy.canFill(self, currentPrice)

    def toRow(self) -> str:
        return f"{self.stockName} {self.action} {self.qty} {self.orderKind} {self.status}"


class User:
    """One account: cash, holdings and order history."""

    def __init__(self, userId: str, cash: int):
        self.userId = userId
        self.cash = cash
        # dict keeps insertion order, so stocks stay in the order they were first FILLED
        self.holdings: Dict[str, Holding] = {}
        # every order in the order it was placed
        self.orders: List[Order] = []

    def getQuantity(self, stockName: str) -> int:
        holding = self.holdings.get(stockName)
        return 0 if holding is None else holding.quantity

    def canBuy(self, qty: int, price: int) -> bool:
        return self.cash >= qty * price

    def canSell(self, stockName: str, qty: int) -> bool:
        return self.getQuantity(stockName) >= qty

    def buy(self, stockName: str, qty: int, price: int) -> None:
        self.cash -= qty * price
        if stockName not in self.holdings:
            self.holdings[stockName] = Holding(stockName)
        self.holdings[stockName].add(qty, price)

    def sell(self, stockName: str, qty: int, price: int) -> None:
        self.cash += qty * price
        # the entry stays even at 0 shares, so the stock keeps its first acquired position
        self.holdings[stockName].remove(qty)


class ExecutionStrategy(ABC):
    """
    Strategy: decides if an order can fill at the current price.
    Every order kind has its own rule.
    """

    @abstractmethod
    def canFill(self, order: Order, currentPrice: int) -> bool:
        pass


class MarketExecution(ExecutionStrategy):
    """MARKET orders always fill at the current price."""

    def canFill(self, order: Order, currentPrice: int) -> bool:
        return True


class LimitExecution(ExecutionStrategy):
    """LIMIT orders fill only when the current price is favorable."""

    def canFill(self, order: Order, currentPrice: int) -> bool:
        if order.action == "BUY":
            return currentPrice <= order.limitPrice
        return currentPrice >= order.limitPrice


class TradingService:
    def __init__(self):
        self.users: Dict[str, User] = {}
        # dict keeps insertion order, so stocks stay in the order they were first added
        self.stocks: Dict[str, Stock] = {}
        # stockName -> OPEN orders waiting on that stock, oldest first
        self.openOrders: Dict[str, List[Order]] = {}
        # orderKind -> its execution strategy
        self.strategies: Dict[str, ExecutionStrategy] = {
            "MARKET": MarketExecution(),
            "LIMIT": LimitExecution(),
        }

    def addUser(self, userId: str, openingCash: int) -> None:
        if userId not in self.users:
            self.users[userId] = User(userId, openingCash)

    def getAccountBalance(self, userId: str) -> int:
        user = self.users.get(userId)
        return 0 if user is None else user.cash

    def addStock(self, stockName: str, stockPrice: int) -> None:
        stock = self.stocks.get(stockName)
        if stock is None:
            self.stocks[stockName] = Stock(stockName, stockPrice)
            self.openOrders[stockName] = []
            return
        stock.price = stockPrice
        # the new price may make some waiting orders fillable
        self.retryOpenOrders(stock)

    def showStocks(self) -> List[str]:
        return [f"{stock.name} {stock.price}" for stock in self.stocks.values()]

    def submitOrder(self, userId: str, stockName: str, orderKind: str,
                    action: str, qty: int, limitPrice: int) -> str:
        user = self.users.get(userId)
        if user is None:
            return "REJECTED"
        order = Order(userId, stockName, orderKind, action, qty, limitPrice,
                      self.strategies[orderKind])
        user.orders.append(order)

        stock = self.stocks.get(stockName)
        if stock is None or not self.isValid(user, order, stock.price):
            order.status = "REJECTED"
        elif order.canFill(stock.price):
            self.fill(user, order, stock.price)
        else:
            order.status = "OPEN"
            self.openOrders[stockName].append(order)
        return order.status

    def viewStockHoldings(self, userId: str) -> List[str]:
        user = self.users.get(userId)
        if user is None:
            return []
        # stocks that were fully sold are kept but not shown
        return [f"{holding.stockName} {holding.quantity}"
                for holding in user.holdings.values() if holding.quantity > 0]

    def viewOrders(self, userId: str) -> List[str]:
        user = self.users.get(userId)
        if user is None:
            return []
        return [order.toRow() for order in user.orders]

    def isValid(self, user: User, order: Order, price: int) -> bool:
        """BUY needs enough cash at the current price. SELL needs enough shares."""
        if order.action == "BUY":
            return user.canBuy(order.qty, price)
        return user.canSell(order.stockName, order.qty)

    def fill(self, user: User, order: Order, price: int) -> None:
        """Executes the order at the stock's current price."""
        if order.action == "BUY":
            user.buy(order.stockName, order.qty, price)
        else:
            user.sell(order.stockName, order.qty, price)
        order.status = "FILLED"

    def retryOpenOrders(self, stock: Stock) -> None:
        """
        Re-checks the waiting orders of one stock after its price changed.
        An order fills only if the price is favorable and the user still
        has enough cash or shares. Every other order stays OPEN.
        """
        stillOpen = []
        for order in self.openOrders[stock.name]:
            user = self.users[order.userId]
            if order.canFill(stock.price) and self.isValid(user, order, stock.price):
                self.fill(user, order, stock.price)
            else:
                stillOpen.append(order)
        self.openOrders[stock.name] = stillOpen
```