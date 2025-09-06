from __future__ import annotations
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List, Optional
from collections import OrderedDict


class OrderKind(Enum):
    MARKET = "MARKET"
    LIMIT = "LIMIT"


class Action(Enum):
    BUY = "BUY"
    SELL = "SELL"


class Status(Enum):
    FILLED = "FILLED"
    OPEN = "OPEN"
    REJECTED = "REJECTED"


@dataclass
class Holding:
    qty: int = 0
    avgPrice: int = 0  # maintained for completeness


@dataclass
class Order:
    stockName: str
    action: Action
    qty: int
    kind: OrderKind
    status: Status
    limitPrice: int  # 0 for MARKET


@dataclass
class OrderRef:
    userId: str
    order: Order


class User:
    def __init__(self, userId: str, openingCash: int) -> None:
        self.userId = userId
        self.cash: int = openingCash
        # Current holdings by symbol (contents change as user trades)
        self.holdings: Dict[str, Holding] = {}
        # Stable order of symbols by first-ever successful acquisition (first fill)
        self.firstAcquired: List[str] = []
        # Orders recorded in placement order
        self.orders: List[Order] = []

    def getHoldingQty(self, stock: str) -> int:
        h = self.holdings.get(stock)
        return 0 if h is None else h.qty


class TradingService:
    def __init__(self):
        # Preserve first-added order for showStocks()
        self.stocks: "OrderedDict[str, int]" = OrderedDict()
        self.users: Dict[str, User] = {}
        # Track OPEN LIMIT orders per stock for instant re-eval on price updates
        self.openLimitByStock: Dict[str, List[OrderRef]] = {}

    # Create a user with an initial cash balance
    def addUser(self, userId: str, openingCash: int) -> None:
        if userId not in self.users:
            self.users[userId] = User(userId, openingCash)

    # Fetch user's current account balance
    def getAccountBalance(self, userId: str) -> int:
        u = self.users.get(userId)
        return 0 if u is None else u.cash

    # Add/update a stock price; on update, auto-fill favorable OPEN LIMIT orders
    def addStock(self, stockName: str, stockPrice: int) -> None:
        if stockName not in self.stocks:
            # first time: preserve insertion order
            self.stocks[stockName] = stockPrice
        else:
            # update existing price (order preserved)
            self.stocks[stockName] = stockPrice
        self._autoFillOpenLimits(stockName, stockPrice)

    # Show all stocks as "name price" in first-added order
    def showStocks(self) -> List[str]:
        return [f"{name} {price}" for name, price in self.stocks.items()]

    # Place an order; returns FILLED, OPEN, or REJECTED
    def submitOrder(
        self,
        userId: str,
        stockName: str,
        orderKind: str,
        action: str,
        qty: int,
        limitPrice: int,
    ) -> str:
        user = self.users.get(userId)
        currentPrice: Optional[int] = self.stocks.get(stockName)

        # If user doesn't exist, reject without recording
        if user is None:
            return Status.REJECTED.name

        # If stock unknown or invalid qty, record REJECTED for this user
        if currentPrice is None or qty <= 0:
            kind = OrderKind[orderKind]
            act = Action[action]
            ord_ = Order(stockName, act, qty, kind, Status.REJECTED, limitPrice)
            user.orders.append(ord_)
            return ord_.status.name

        kind = OrderKind[orderKind]
        act = Action[action]
        priceAtSubmit = currentPrice

        if kind == OrderKind.MARKET:
            return self._handleMarket(user, stockName, act, qty, priceAtSubmit)
        else:  # LIMIT
            return self._handleLimit(user, stockName, act, qty, limitPrice, priceAtSubmit)

    # Show holdings for a user as "stockName count" ordered by first-ever successful fill time
    def viewStockHoldings(self, userId: str) -> List[str]:
        user = self.users.get(userId)
        if user is None:
            return []
        out: List[str] = []
        for sym in user.firstAcquired:  # stable first-filled order
            h = user.holdings.get(sym)
            if h and h.qty > 0:
                out.append(f"{sym} {h.qty}")
        return out

    # Show order history as "stockName action quantity orderKind status"
    def viewOrders(self, userId: str) -> List[str]:
        user = self.users.get(userId)
        if user is None:
            return []
        return [
            f"{o.stockName} {o.action.name} {o.qty} {o.kind.name} {o.status.name}"
            for o in user.orders
        ]

    # --- Helpers ------------------------------------------------------------

    def _handleMarket(self, user: User, stock: str, act: Action, qty: int, priceAtSubmit: int) -> str:
        if act == Action.BUY:
            cost = qty * priceAtSubmit
            if user.cash >= cost:
                self._applyBuy(user, stock, qty, priceAtSubmit)
                status = Status.FILLED
            else:
                status = Status.REJECTED
        else:  # SELL
            held = user.getHoldingQty(stock)
            if held >= qty:
                self._applySell(user, stock, qty, priceAtSubmit)
                status = Status.FILLED
            else:
                status = Status.REJECTED

        ord_ = Order(stock, act, qty, OrderKind.MARKET, status, 0)
        user.orders.append(ord_)
        return status.name

    def _handleLimit(
        self, user: User, stock: str, act: Action, qty: int, limitPrice: int, priceAtSubmit: int
    ) -> str:
        if act == Action.BUY:
            # Must have enough cash at CURRENT price, even if not favorable
            costAtCurrent = qty * priceAtSubmit
            if user.cash < costAtCurrent:
                status = Status.REJECTED
            elif priceAtSubmit <= limitPrice:
                self._applyBuy(user, stock, qty, priceAtSubmit)
                status = Status.FILLED
            else:
                status = Status.OPEN
        else:  # SELL
            held = user.getHoldingQty(stock)
            if held < qty:
                status = Status.REJECTED
            elif priceAtSubmit >= limitPrice:
                self._applySell(user, stock, qty, priceAtSubmit)
                status = Status.FILLED
            else:
                status = Status.OPEN

        ord_ = Order(stock, act, qty, OrderKind.LIMIT, status, limitPrice)
        user.orders.append(ord_)
        if status == Status.OPEN:
            self.openLimitByStock.setdefault(stock, []).append(OrderRef(user.userId, ord_))
        return status.name

    # Auto-fill OPEN LIMIT orders for this stock if the new price is favorable
    def _autoFillOpenLimits(self, stockName: str, newPrice: int) -> None:
        refs = self.openLimitByStock.get(stockName)
        if not refs:
            return

        i = 0
        while i < len(refs):
            ref = refs[i]
            user = self.users.get(ref.userId)
            ord_ = ref.order

            # Skip/clean invalid references
            if user is None or ord_.status != Status.OPEN or ord_.kind != OrderKind.LIMIT:
                refs.pop(i)
                continue

            favorable = (ord_.action == Action.BUY and newPrice <= ord_.limitPrice) or \
                        (ord_.action == Action.SELL and newPrice >= ord_.limitPrice)

            if not favorable:
                i += 1
                continue

            if ord_.action == Action.BUY:
                cost = ord_.qty * newPrice
                if user.cash >= cost:
                    self._applyBuy(user, stockName, ord_.qty, newPrice)
                    ord_.status = Status.FILLED
                    refs.pop(i)
                    continue
                # else: keep OPEN
                i += 1
            else:  # SELL
                held = user.getHoldingQty(stockName)
                if held >= ord_.qty:
                    self._applySell(user, stockName, ord_.qty, newPrice)
                    ord_.status = Status.FILLED
                    refs.pop(i)
                    continue
                # else: keep OPEN
                i += 1

        if not refs:
            self.openLimitByStock.pop(stockName, None)

    def _applyBuy(self, user: User, stock: str, qty: int, price: int) -> None:
        cost = qty * price
        user.cash -= cost

        h = user.holdings.get(stock)
        if h is None:
            h = Holding()
            user.holdings[stock] = h

        # Record first-ever acquisition order (stable, even if later sold to zero)
        if stock not in user.firstAcquired:
            user.firstAcquired.append(stock)

        totalCostBefore = h.avgPrice * h.qty
        newQty = h.qty + qty
        newAvg = (totalCostBefore + cost) // newQty  # integer average like Java
        h.qty = newQty
        h.avgPrice = newAvg

    def _applySell(self, user: User, stock: str, qty: int, price: int) -> None:
        proceeds = qty * price
        user.cash += proceeds
        h = user.holdings.get(stock)
        if h is None or h.qty < qty:
            return  # should not happen after validation
        h.qty -= qty
        if h.qty == 0:
            # remove empty holding but DO NOT remove from firstAcquired
            user.holdings.pop(stock, None)
        # avgPrice for remaining shares stays as-is
