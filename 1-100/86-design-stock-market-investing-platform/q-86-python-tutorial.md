# Design Stock Market Investing Platform in Python

#### Problem Statement
[https://codezym.com/question/86-design-stock-market-investing-platform](https://codezym.com/question/86-design-stock-market-investing-platform)


In this problem, we build **BuyStocks**, a small stock trading platform. Users sign up with some money in their wallet. Admins add stocks and set their prices. Users buy and sell those stocks.


The core idea is simple: **give every idea in the problem its own small class**, and put one **Facade** class in front of them.


A `User` has a `Wallet` (money) and a `Portfolio` (shares owned). A `StockMarket` keeps the available stocks and their prices.


`BuyStocks` is the Facade. It is the single entry point. It checks the inputs, then asks the right objects to do the work.


A Facade over a few small classes is the best fit here. Heavier patterns like Observer or Command would only add extra classes, without solving any real problem in this question.


For speed, every lookup uses a dictionary. For the sorted outputs, we keep the symbols in a sorted list. So we never sort when someone asks for a list.


## Key Rules

Let us first note the rules that are easy to miss.

- If a method returns `False`, nothing in the system should change.
- `listAvailableStocks()` and `getPortfolio()` must be sorted by symbol.
- A trade always uses the **current** price. If the admin changes a price, the next trade uses the new price.
- A portfolio shows only stocks with quantity more than 0.


The problem also gives a valid range for every input. If any input breaks its rule, the method returns `False`.

| Input | Valid when |
|---|---|
| `userId` | not empty, not only spaces, at most 30 characters |
| `name` | 1 to 60 characters |
| `initialWalletAmount` | more than 1 and less than 900,000 |
| `symbol` | 1 to 10 characters, only `A` to `Z` and `-` |
| `price` | more than 0 and less than 100,000 |
| `quantity` in `buyStock` | from 1 to 9,999 |
| `quantity` in `sellStock` | more than 0 |


For example, `adminAddOrUpdateStock("AAPL$", 100)` returns `False`, because `$` is not allowed in a symbol.


## Approach 1: Simple Solution (Everything in One Class)

The first idea that comes to mind is to keep everything inside `BuyStocks`, using a few dictionaries.

| Dictionary | Stores |
|---|---|
| `names` | userId → name |
| `wallets` | userId → wallet balance |
| `portfolios` | userId → (symbol → quantity) |
| `prices` | symbol → current price |


Dictionaries give us O(1) lookups. For the two list methods, we sort the keys on every call.


Every method that changes data first checks its inputs against the rules above. Three small helper methods check the text inputs: `is_valid_user_id`, `is_valid_name` and `is_valid_symbol`. The number checks are simple comparisons inside each method.


For the symbol, we use the pattern `[A-Z-]{1,10}` with `re.fullmatch`. It means 1 to 10 characters, where each character is a capital letter or a hyphen.


**Buying a stock**

1. Check that `quantity` is from 1 to 9,999, the user exists and the stock exists.
2. Find `cost = price × quantity`.
3. If `cost` is more than the balance, return `False`.
4. Take the money from the wallet and add the shares to the portfolio.


**Selling a stock**

1. Check that `quantity > 0` and the user exists.
2. Check that the user owns at least `quantity` shares.
3. Reduce the shares. If they become 0, remove the symbol from the portfolio.
4. Add `price × quantity` to the wallet.


Notice one important habit: **we do every check first, and change the data only at the end.** So a method that returns `False` never leaves the system half changed.

```python
import re


class BuyStocks:
    def __init__(self):
        self.names = {}        # userId -> name
        self.wallets = {}      # userId -> wallet balance
        self.portfolios = {}   # userId -> {symbol -> quantity owned}
        self.prices = {}       # symbol -> current price

    def signUp(self, userId, name, initialWalletAmount):
        if not self.is_valid_user_id(userId) or not self.is_valid_name(name) or userId in self.wallets:
            return False
        # valid range: 1 < initialWalletAmount < 900,000
        if initialWalletAmount <= 1 or initialWalletAmount >= 900000:
            return False
        self.names[userId] = name
        self.wallets[userId] = initialWalletAmount
        self.portfolios[userId] = {}
        return True

    def adminAddOrUpdateStock(self, symbol, price):
        # valid range: 0 < price < 100,000
        if not self.is_valid_symbol(symbol) or price <= 0 or price >= 100000:
            return False
        self.prices[symbol] = price
        return True

    def listAvailableStocks(self):
        # sort the symbols on every call
        return [f"{symbol} {self.prices[symbol]}" for symbol in sorted(self.prices)]

    def buyStock(self, userId, symbol, quantity):
        # valid range: 1 <= quantity < 10,000
        if quantity < 1 or quantity >= 10000:
            return False
        if userId not in self.wallets or symbol not in self.prices:
            return False
        cost = self.prices[symbol] * quantity
        if cost > self.wallets[userId]:
            return False
        self.wallets[userId] -= cost
        holdings = self.portfolios[userId]
        holdings[symbol] = holdings.get(symbol, 0) + quantity
        return True

    def sellStock(self, userId, symbol, quantity):
        if quantity <= 0 or userId not in self.wallets:
            return False
        holdings = self.portfolios[userId]
        owned = holdings.get(symbol, 0)
        if owned < quantity:
            return False
        if owned == quantity:
            del holdings[symbol]
        else:
            holdings[symbol] = owned - quantity
        self.wallets[userId] += self.prices[symbol] * quantity
        return True

    def getWalletBalance(self, userId):
        return self.wallets.get(userId, -1)

    def getPortfolio(self, userId):
        holdings = self.portfolios.get(userId, {})
        # sort the symbols on every call
        return [f"{symbol} {holdings[symbol]}" for symbol in sorted(holdings)]

    def is_valid_user_id(self, userId):
        # not blank and at most 30 characters
        return userId is not None and userId.strip() != "" and len(userId) <= 30

    def is_valid_name(self, name):
        # 1 to 60 characters
        return name is not None and 1 <= len(name) <= 60

    def is_valid_symbol(self, symbol):
        # 1 to 10 characters, each one is a capital letter (A to Z) or a hyphen
        return symbol is not None and re.fullmatch(r"[A-Z-]{1,10}", symbol) is not None
```


### Problems With This Approach

The code works, but it has some problems.


1. **One user is spread across three dictionaries.** The name, wallet and portfolio of a user live in three different places. Every new user field needs one more dictionary. It is easy to update one and forget another.

2. **Hard to read.** A dictionary inside a dictionary, like `self.portfolios[userId][symbol]`, makes the reader stop and think.

3. **One class does everything.** Input checks, money math, share math and output formatting are all mixed together. A new feature, like a trading fee, means changing this one big class.

4. **Sorting again and again.** Both list methods sort the symbols on every call, even when nothing has changed. Sorting k symbols costs O(k log k) every single time.


## Approach 2: Better Solution (Facade + Small Classes)

We fix these problems in two steps.


### Step 1: Give Each Idea Its Own Class

The problem already names the key ideas: User, Stock, Wallet, Portfolio and Available stocks. Let us turn them into classes.

| Class | Job |
|---|---|
| `BuyStocks` | The Facade. The only class the caller uses. It checks every input and runs each operation step by step. |
| `StockMarket` | Keeps the available stocks and their current prices. |
| `User` | One user. Holds the user's `Wallet` and `Portfolio`. |
| `Wallet` | The user's money. It can check a payment, take money out and put money in. |
| `Portfolio` | The shares a user owns, per symbol. |


Why do we need each class?

- **`User`** keeps all the data of one user in one object. Now `BuyStocks` needs just one dictionary: `userId → User`. This fixes problems 1 and 2.
- **`Wallet`** and **`Portfolio`** each own their data and their math. `BuyStocks` no longer changes balances or quantities by hand. This fixes problem 3.
- **`StockMarket`** keeps all stock data in one place. A stock is just a symbol and a price, so a dictionary inside `StockMarket` is enough. We do not need a separate `Stock` class.


### Step 2: Keep Symbols Sorted All the Time

Instead of sorting on every call, we keep the symbols in a **sorted list**. A list method just walks through it, and the output comes out already sorted.


Python has no built-in sorted set. So we use a plain list and the standard `bisect` module. `bisect.insort(items, x)` finds the right spot for `x` with binary search and inserts it there. The list stays sorted after every insert.


We pair the sorted list with a dictionary:

- The dictionary gives the price (or quantity) of a symbol in O(1).
- The sorted list gives all the symbols in sorted order.


Together they behave like a sorted dictionary, while each part stays simple.


`StockMarket` uses this pair for prices. `Portfolio` uses the same pair for quantities.


The sorted list changes only when a symbol is added or removed. Updating a price or a quantity only touches the dictionary.


When a user sells all shares of a stock, we remove the symbol from both. So the portfolio only ever shows stocks with quantity more than 0.


This fixes problem 4.


### Why the Facade Pattern?

A Facade is one simple front door to a group of classes. The caller does not need to know what happens behind it.


This matches our problem well. The caller only talks to `BuyStocks`. It never touches `User`, `Wallet`, `Portfolio` or `StockMarket`.


The front door is also the one place that checks inputs. So the classes behind it can trust everything they receive, and they stay tiny.


A single trade touches three objects: the market for the price, the wallet for the money and the portfolio for the shares. The Facade runs these steps in the right order, in one place. Each small class only cares about its own data.


### Patterns That Look Useful but Are Not Needed

**Observer.** It sounds natural: when a price changes, notify everyone who owns that stock. But nothing in this problem needs to react to a price change. A trade simply reads the latest price at the moment it happens. Observers would add listener classes for no benefit.


**Command.** We could turn every buy and sell into an order object with an `execute()` method. Command shines when you need undo, queues, scheduling or an order history. This problem needs none of these. Buy and sell are two short methods, and extra classes would only hide the logic.


### How a Buy Works

1. Return `False` if `quantity` is not from 1 to 9,999, the user does not exist or the stock is not available.
2. Find `cost = price × quantity`.
3. Ask the wallet `can_afford(cost)`. If not, return `False`.
4. All checks passed. Only now change the state: `wallet.withdraw(cost)` and `portfolio.add_shares(symbol, quantity)`.


### How a Sell Works

1. Return `False` if `quantity <= 0` or the user does not exist.
2. Ask the portfolio `has_shares(symbol, quantity)`. If not, return `False`.
3. All checks passed. Change the state: `portfolio.remove_shares(symbol, quantity)` and `wallet.deposit(price × quantity)`.


We do not need to check that the stock is available. A user can only own a stock that was bought from the market, and stocks are never removed from the market.


### Dry Run

| Call | Returns | Wallet | Portfolio |
|---|---|---|---|
| `adminAddOrUpdateStock("AAPL", 100)` | `True` | | |
| `signUp("u1", "Asha", 1000)` | `True` | 1000 | `[]` |
| `buyStock("u1", "AAPL", 5)` | `True` | 500 | `["AAPL 5"]` |
| `adminAddOrUpdateStock("AAPL", 120)` | `True` | 500 | `["AAPL 5"]` |
| `sellStock("u1", "AAPL", 2)` | `True` | 740 | `["AAPL 3"]` |
| `sellStock("u1", "AAPL", 4)` | `False` | 740 | `["AAPL 3"]` |


The sell after the price change uses the new price: 500 + 2 × 120 = 740. The last call fails because the user owns only 3 shares, and nothing changes.


### Code

The method names of `BuyStocks` come from the problem, so they stay in camelCase. Our own helper classes and methods use the normal Python snake_case style.

```python
import bisect
import re


class BuyStocks:
    """
    Facade: the only class the caller talks to.
    It checks every input, then asks the small classes below to do the real work.
    """

    def __init__(self):
        self.users = {}               # userId -> User, for O(1) user lookup
        self.market = StockMarket()   # all the stocks added by admins

    def signUp(self, userId, name, initialWalletAmount):
        if not self.is_valid_user_id(userId) or not self.is_valid_name(name) or userId in self.users:
            return False
        # valid range: 1 < initialWalletAmount < 900,000
        if initialWalletAmount <= 1 or initialWalletAmount >= 900000:
            return False
        self.users[userId] = User(userId, name, initialWalletAmount)
        return True

    def adminAddOrUpdateStock(self, symbol, price):
        # valid range: 0 < price < 100,000
        if not self.is_valid_symbol(symbol) or price <= 0 or price >= 100000:
            return False
        self.market.add_or_update_stock(symbol, price)
        return True

    def listAvailableStocks(self):
        return self.market.list_stocks()

    def buyStock(self, userId, symbol, quantity):
        # valid range: 1 <= quantity < 10,000
        if quantity < 1 or quantity >= 10000:
            return False
        user = self.users.get(userId)
        if user is None or not self.market.has_stock(symbol):
            return False
        cost = self.market.get_price(symbol) * quantity
        if not user.wallet.can_afford(cost):
            return False
        # every check passed, only now we change the state
        user.wallet.withdraw(cost)
        user.portfolio.add_shares(symbol, quantity)
        return True

    def sellStock(self, userId, symbol, quantity):
        user = self.users.get(userId)
        if quantity <= 0 or user is None or not user.portfolio.has_shares(symbol, quantity):
            return False
        # every check passed, only now we change the state
        user.portfolio.remove_shares(symbol, quantity)
        user.wallet.deposit(self.market.get_price(symbol) * quantity)
        return True

    def getWalletBalance(self, userId):
        user = self.users.get(userId)
        if user is None:
            return -1
        return user.wallet.balance

    def getPortfolio(self, userId):
        user = self.users.get(userId)
        if user is None:
            return []
        return user.portfolio.list_holdings()

    def is_valid_user_id(self, userId):
        # not blank and at most 30 characters
        return userId is not None and userId.strip() != "" and len(userId) <= 30

    def is_valid_name(self, name):
        # 1 to 60 characters
        return name is not None and 1 <= len(name) <= 60

    def is_valid_symbol(self, symbol):
        # 1 to 10 characters, each one is a capital letter (A to Z) or a hyphen
        return symbol is not None and re.fullmatch(r"[A-Z-]{1,10}", symbol) is not None


class StockMarket:
    """Keeps the available stocks and their current prices."""

    def __init__(self):
        self.prices = {}           # symbol -> current price, for O(1) price lookup
        self.sorted_symbols = []   # all symbols, always kept in sorted order

    def add_or_update_stock(self, symbol, price):
        if symbol not in self.prices:
            # a new symbol: insert it at its sorted position
            bisect.insort(self.sorted_symbols, symbol)
        self.prices[symbol] = price

    def has_stock(self, symbol):
        return symbol in self.prices

    def get_price(self, symbol):
        return self.prices[symbol]

    def list_stocks(self):
        # "SYMBOL PRICE" for every stock, already sorted by symbol
        return [f"{symbol} {self.prices[symbol]}" for symbol in self.sorted_symbols]


class User:
    """A user has exactly one wallet and one portfolio."""

    def __init__(self, user_id, name, initial_wallet_amount):
        self.user_id = user_id
        self.name = name
        self.wallet = Wallet(initial_wallet_amount)
        self.portfolio = Portfolio()


class Wallet:
    """Holds the user's money. Every money change happens here."""

    def __init__(self, balance):
        self.balance = balance

    def can_afford(self, amount):
        return amount <= self.balance

    def withdraw(self, amount):
        self.balance -= amount

    def deposit(self, amount):
        self.balance += amount


class Portfolio:
    """Holds the shares a user owns. Only symbols with quantity > 0 are kept."""

    def __init__(self):
        self.holdings = {}         # symbol -> quantity owned, for O(1) lookup
        self.sorted_symbols = []   # owned symbols, always kept in sorted order

    def add_shares(self, symbol, quantity):
        if symbol not in self.holdings:
            # first time we own this symbol: insert it at its sorted position
            bisect.insort(self.sorted_symbols, symbol)
            self.holdings[symbol] = 0
        self.holdings[symbol] += quantity

    def has_shares(self, symbol, quantity):
        return self.holdings.get(symbol, 0) >= quantity

    def remove_shares(self, symbol, quantity):
        self.holdings[symbol] -= quantity
        if self.holdings[symbol] == 0:
            # sold everything, so forget this symbol
            del self.holdings[symbol]
            self.sorted_symbols.remove(symbol)

    def list_holdings(self):
        # "SYMBOL QUANTITY" for every owned stock, already sorted by symbol
        return [f"{symbol} {self.holdings[symbol]}" for symbol in self.sorted_symbols]
```


## Complexity

Here S is the number of available stocks, and H is the number of different stocks one user holds.

| Method | Approach 1 | Approach 2 |
|---|---|---|
| `signUp` | O(1) | O(1) |
| `adminAddOrUpdateStock` | O(1) | O(1), or O(S) for a new symbol |
| `listAvailableStocks` | O(S log S) | O(S) |
| `buyStock` | O(1) | O(1), or O(H) for a newly owned symbol |
| `sellStock` | O(1) | O(1), or O(H) when a symbol is sold out |
| `getWalletBalance` | O(1) | O(1) |
| `getPortfolio` | O(H log H) | O(H) |


Inserting into or removing from a Python list shifts the items after that spot. So it costs O(n) in the worst case.


But Approach 2 pays this cost only when a symbol is added to or removed from a sorted list. Updating a price or a quantity never pays it, and the list methods never sort.


## Key Takeaways

- Turn each idea in the problem into a small class with one job.
- Use a Facade as the single entry point, so the caller stays simple.
- Check everything first and change the state last. Then a `False` result never leaves half done changes behind.
- A dictionary plus a sorted list gives fast lookups and sorted output, using only simple data structures.