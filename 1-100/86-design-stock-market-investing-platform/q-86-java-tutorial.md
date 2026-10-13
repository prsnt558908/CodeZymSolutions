# Design Stock Market Investing Platform in Java

#### Problem Statement
[https://codezym.com/question/86-design-stock-market-investing-platform](https://codezym.com/question/86-design-stock-market-investing-platform)


In this problem, we build **BuyStocks**, a small stock trading platform. Users sign up with some money in their wallet. Admins add stocks and set their prices. Users buy and sell those stocks.


The core idea is simple: **give every idea in the problem its own small class**, and put one **Facade** class in front of them.


A `User` has a `Wallet` (money) and a `Portfolio` (shares owned). A `StockMarket` keeps the available stocks and their prices.


`BuyStocks` is the Facade. It is the single entry point. It checks the inputs, then asks the right objects to do the work.


A Facade over a few small classes is the best fit here. Heavier patterns like Observer or Command would only add extra classes, without solving any real problem in this question.


For speed, every lookup uses a hash map. For the sorted outputs, we keep the symbols in a sorted set. So we never sort when someone asks for a list.


## Key Rules

Let us first note the rules that are easy to miss.

- If a method returns `false`, nothing in the system should change.
- `listAvailableStocks()` and `getPortfolio()` must be sorted by symbol.
- A trade always uses the **current** price. If the admin changes a price, the next trade uses the new price.
- A portfolio shows only stocks with quantity more than 0.


The problem also gives a valid range for every input. If any input breaks its rule, the method returns `false`.

| Input | Valid when |
|---|---|
| `userId` | not empty, not only spaces, at most 30 characters |
| `name` | 1 to 60 characters |
| `initialWalletAmount` | more than 1 and less than 900,000 |
| `symbol` | 1 to 10 characters, only `A` to `Z` and `-` |
| `price` | more than 0 and less than 100,000 |
| `quantity` in `buyStock` | from 1 to 9,999 |
| `quantity` in `sellStock` | more than 0 |


For example, `adminAddOrUpdateStock("AAPL$", 100)` returns `false`, because `$` is not allowed in a symbol.


## Approach 1: Simple Solution (Everything in One Class)

The first idea that comes to mind is to keep everything inside `BuyStocks`, using a few hash maps.

| Map | Stores |
|---|---|
| `names` | userId → name |
| `wallets` | userId → wallet balance |
| `portfolios` | userId → (symbol → quantity) |
| `prices` | symbol → current price |


Hash maps give us O(1) lookups. For the two list methods, we copy the keys into a list and sort it on every call.


Every method that changes data first checks its inputs against the rules above. Three small helper methods check the text inputs: `isValidUserId`, `isValidName` and `isValidSymbol`. The number checks are simple comparisons inside each method.


For the symbol, we use the pattern `[A-Z-]{1,10}`. It means 1 to 10 characters, where each character is a capital letter or a hyphen.


**Buying a stock**

1. Check that `quantity` is from 1 to 9,999, the user exists and the stock exists.
2. Find `cost = price × quantity`.
3. If `cost` is more than the balance, return `false`.
4. Take the money from the wallet and add the shares to the portfolio.


**Selling a stock**

1. Check that `quantity > 0` and the user exists.
2. Check that the user owns at least `quantity` shares.
3. Reduce the shares. If they become 0, remove the symbol from the portfolio.
4. Add `price × quantity` to the wallet.


Notice one important habit: **we do every check first, and change the data only at the end.** So a method that returns `false` never leaves the system half changed.

```java
import java.util.*;

public class BuyStocks {
    // userId -> name
    Map<String, String> names = new HashMap<>();
    // userId -> wallet balance
    Map<String, Integer> wallets = new HashMap<>();
    // userId -> (symbol -> quantity owned)
    Map<String, Map<String, Integer>> portfolios = new HashMap<>();
    // symbol -> current price
    Map<String, Integer> prices = new HashMap<>();

    public BuyStocks() {
    }

    public boolean signUp(String userId, String name, int initialWalletAmount) {
        if (!isValidUserId(userId) || !isValidName(name) || wallets.containsKey(userId)) {
            return false;
        }
        // valid range: 1 < initialWalletAmount < 900,000
        if (initialWalletAmount <= 1 || initialWalletAmount >= 900000) {
            return false;
        }
        names.put(userId, name);
        wallets.put(userId, initialWalletAmount);
        portfolios.put(userId, new HashMap<>());
        return true;
    }

    public boolean adminAddOrUpdateStock(String symbol, int price) {
        // valid range: 0 < price < 100,000
        if (!isValidSymbol(symbol) || price <= 0 || price >= 100000) {
            return false;
        }
        prices.put(symbol, price);
        return true;
    }

    public List<String> listAvailableStocks() {
        // sort the symbols on every call
        List<String> symbols = new ArrayList<>(prices.keySet());
        Collections.sort(symbols);
        List<String> result = new ArrayList<>();
        for (String symbol : symbols) {
            result.add(symbol + " " + prices.get(symbol));
        }
        return result;
    }

    public boolean buyStock(String userId, String symbol, int quantity) {
        // valid range: 1 <= quantity < 10,000
        if (quantity < 1 || quantity >= 10000) {
            return false;
        }
        if (!wallets.containsKey(userId) || !prices.containsKey(symbol)) {
            return false;
        }
        int cost = prices.get(symbol) * quantity;
        int balance = wallets.get(userId);
        if (cost > balance) {
            return false;
        }
        wallets.put(userId, balance - cost);
        Map<String, Integer> holdings = portfolios.get(userId);
        holdings.put(symbol, holdings.getOrDefault(symbol, 0) + quantity);
        return true;
    }

    public boolean sellStock(String userId, String symbol, int quantity) {
        if (quantity <= 0 || !wallets.containsKey(userId)) {
            return false;
        }
        Map<String, Integer> holdings = portfolios.get(userId);
        int owned = holdings.getOrDefault(symbol, 0);
        if (owned < quantity) {
            return false;
        }
        if (owned == quantity) {
            holdings.remove(symbol);
        } else {
            holdings.put(symbol, owned - quantity);
        }
        wallets.put(userId, wallets.get(userId) + prices.get(symbol) * quantity);
        return true;
    }

    public int getWalletBalance(String userId) {
        return wallets.getOrDefault(userId, -1);
    }

    public List<String> getPortfolio(String userId) {
        List<String> result = new ArrayList<>();
        if (!portfolios.containsKey(userId)) {
            return result;
        }
        Map<String, Integer> holdings = portfolios.get(userId);
        // sort the symbols on every call
        List<String> symbols = new ArrayList<>(holdings.keySet());
        Collections.sort(symbols);
        for (String symbol : symbols) {
            result.add(symbol + " " + holdings.get(symbol));
        }
        return result;
    }

    // not blank and at most 30 characters
    boolean isValidUserId(String userId) {
        return userId != null && !userId.trim().isEmpty() && userId.length() <= 30;
    }

    // 1 to 60 characters
    boolean isValidName(String name) {
        return name != null && name.length() >= 1 && name.length() <= 60;
    }

    // 1 to 10 characters, each one is a capital letter (A to Z) or a hyphen
    boolean isValidSymbol(String symbol) {
        return symbol != null && symbol.matches("[A-Z-]{1,10}");
    }
}
```


### Problems With This Approach

The code works, but it has some problems.


1. **One user is spread across three maps.** The name, wallet and portfolio of a user live in three different maps. Every new user field needs one more map. It is easy to update one map and forget another.

2. **Hard to read.** A type like `Map<String, Map<String, Integer>>` makes the reader stop and think.

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

- **`User`** keeps all the data of one user in one object. Now `BuyStocks` needs just one map: `userId → User`. This fixes problems 1 and 2.
- **`Wallet`** and **`Portfolio`** each own their data and their math. `BuyStocks` no longer changes balances or quantities by hand. This fixes problem 3.
- **`StockMarket`** keeps all stock data in one place. A stock is just a symbol and a price, so a map inside `StockMarket` is enough. We do not need a separate `Stock` class.


### Step 2: Keep Symbols Sorted All the Time

Instead of sorting on every call, we keep the symbols in a **sorted set** (`TreeSet`). A sorted set always keeps its items in order. So a list method just walks through the set, and the output comes out already sorted.


We pair the sorted set with a hash map:

- `HashMap` gives the price (or quantity) of a symbol in O(1).
- `TreeSet` gives all the symbols in sorted order.


Together they behave like a sorted map, while each part stays simple.


`StockMarket` uses this pair for prices. `Portfolio` uses the same pair for quantities. When a user sells all shares of a stock, we remove that symbol from both. So the portfolio only ever shows stocks with quantity more than 0.


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

1. Return `false` if `quantity` is not from 1 to 9,999, the user does not exist or the stock is not available.
2. Find `cost = price × quantity`.
3. Ask the wallet `canAfford(cost)`. If not, return `false`.
4. All checks passed. Only now change the state: `wallet.withdraw(cost)` and `portfolio.addShares(symbol, quantity)`.


We already made sure that the price is below 100,000 and the quantity is below 10,000. So the cost is always below 1,000,000,000. An `int` can hold up to about 2.1 billion, so the cost fits safely.


### How a Sell Works

1. Return `false` if `quantity <= 0` or the user does not exist.
2. Ask the portfolio `hasShares(symbol, quantity)`. If not, return `false`.
3. All checks passed. Change the state: `portfolio.removeShares(symbol, quantity)` and `wallet.deposit(price × quantity)`.


We do not need to check that the stock is available. A user can only own a stock that was bought from the market, and stocks are never removed from the market.


### Dry Run

| Call | Returns | Wallet | Portfolio |
|---|---|---|---|
| `adminAddOrUpdateStock("AAPL", 100)` | `true` | | |
| `signUp("u1", "Asha", 1000)` | `true` | 1000 | `[]` |
| `buyStock("u1", "AAPL", 5)` | `true` | 500 | `["AAPL 5"]` |
| `adminAddOrUpdateStock("AAPL", 120)` | `true` | 500 | `["AAPL 5"]` |
| `sellStock("u1", "AAPL", 2)` | `true` | 740 | `["AAPL 3"]` |
| `sellStock("u1", "AAPL", 4)` | `false` | 740 | `["AAPL 3"]` |


The sell after the price change uses the new price: 500 + 2 × 120 = 740. The last call fails because the user owns only 3 shares, and nothing changes.


### Code

```java
import java.util.*;

/**
 * Facade: the only class the caller talks to.
 * It checks every input, then asks the small classes below to do the real work.
 */
public class BuyStocks {
    // userId -> User, for O(1) user lookup
    Map<String, User> users = new HashMap<>();
    // all the stocks added by admins
    StockMarket market = new StockMarket();

    public BuyStocks() {
    }

    public boolean signUp(String userId, String name, int initialWalletAmount) {
        if (!isValidUserId(userId) || !isValidName(name) || users.containsKey(userId)) {
            return false;
        }
        // valid range: 1 < initialWalletAmount < 900,000
        if (initialWalletAmount <= 1 || initialWalletAmount >= 900000) {
            return false;
        }
        users.put(userId, new User(userId, name, initialWalletAmount));
        return true;
    }

    public boolean adminAddOrUpdateStock(String symbol, int price) {
        // valid range: 0 < price < 100,000
        if (!isValidSymbol(symbol) || price <= 0 || price >= 100000) {
            return false;
        }
        market.addOrUpdateStock(symbol, price);
        return true;
    }

    public List<String> listAvailableStocks() {
        return market.listStocks();
    }

    public boolean buyStock(String userId, String symbol, int quantity) {
        // valid range: 1 <= quantity < 10,000
        if (quantity < 1 || quantity >= 10000) {
            return false;
        }
        User user = users.get(userId);
        if (user == null || !market.hasStock(symbol)) {
            return false;
        }
        // price < 100,000 and quantity < 10,000, so the cost always fits in an int
        int cost = market.getPrice(symbol) * quantity;
        if (!user.wallet.canAfford(cost)) {
            return false;
        }
        // every check passed, only now we change the state
        user.wallet.withdraw(cost);
        user.portfolio.addShares(symbol, quantity);
        return true;
    }

    public boolean sellStock(String userId, String symbol, int quantity) {
        User user = users.get(userId);
        if (quantity <= 0 || user == null || !user.portfolio.hasShares(symbol, quantity)) {
            return false;
        }
        // every check passed, only now we change the state
        user.portfolio.removeShares(symbol, quantity);
        user.wallet.deposit(market.getPrice(symbol) * quantity);
        return true;
    }

    public int getWalletBalance(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return -1;
        }
        return user.wallet.balance;
    }

    public List<String> getPortfolio(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }
        return user.portfolio.listHoldings();
    }

    // not blank and at most 30 characters
    boolean isValidUserId(String userId) {
        return userId != null && !userId.trim().isEmpty() && userId.length() <= 30;
    }

    // 1 to 60 characters
    boolean isValidName(String name) {
        return name != null && name.length() >= 1 && name.length() <= 60;
    }

    // 1 to 10 characters, each one is a capital letter (A to Z) or a hyphen
    boolean isValidSymbol(String symbol) {
        return symbol != null && symbol.matches("[A-Z-]{1,10}");
    }
}

/**
 * Keeps the available stocks and their current prices.
 */
class StockMarket {
    // symbol -> current price, for O(1) price lookup
    Map<String, Integer> prices = new HashMap<>();
    // all symbols, always kept in sorted order
    Set<String> sortedSymbols = new TreeSet<>();

    void addOrUpdateStock(String symbol, int price) {
        prices.put(symbol, price);
        // a set ignores duplicates, so a price update does not add the symbol twice
        sortedSymbols.add(symbol);
    }

    boolean hasStock(String symbol) {
        return prices.containsKey(symbol);
    }

    int getPrice(String symbol) {
        return prices.get(symbol);
    }

    // "SYMBOL PRICE" for every stock, already sorted by symbol
    List<String> listStocks() {
        List<String> result = new ArrayList<>();
        for (String symbol : sortedSymbols) {
            result.add(symbol + " " + prices.get(symbol));
        }
        return result;
    }
}

/**
 * A user has exactly one wallet and one portfolio.
 */
class User {
    String userId;
    String name;
    Wallet wallet;
    Portfolio portfolio;

    User(String userId, String name, int initialWalletAmount) {
        this.userId = userId;
        this.name = name;
        this.wallet = new Wallet(initialWalletAmount);
        this.portfolio = new Portfolio();
    }
}

/**
 * Holds the user's money. Every money change happens here.
 */
class Wallet {
    int balance;

    Wallet(int balance) {
        this.balance = balance;
    }

    boolean canAfford(int amount) {
        return amount <= balance;
    }

    void withdraw(int amount) {
        balance -= amount;
    }

    void deposit(int amount) {
        balance += amount;
    }
}

/**
 * Holds the shares a user owns. Only symbols with quantity > 0 are kept.
 */
class Portfolio {
    // symbol -> quantity owned, for O(1) lookup
    Map<String, Integer> holdings = new HashMap<>();
    // owned symbols, always kept in sorted order
    Set<String> sortedSymbols = new TreeSet<>();

    void addShares(String symbol, int quantity) {
        holdings.put(symbol, holdings.getOrDefault(symbol, 0) + quantity);
        sortedSymbols.add(symbol);
    }

    boolean hasShares(String symbol, int quantity) {
        return holdings.getOrDefault(symbol, 0) >= quantity;
    }

    void removeShares(String symbol, int quantity) {
        int left = holdings.get(symbol) - quantity;
        if (left == 0) {
            // sold everything, so forget this symbol
            holdings.remove(symbol);
            sortedSymbols.remove(symbol);
        } else {
            holdings.put(symbol, left);
        }
    }

    // "SYMBOL QUANTITY" for every owned stock, already sorted by symbol
    List<String> listHoldings() {
        List<String> result = new ArrayList<>();
        for (String symbol : sortedSymbols) {
            result.add(symbol + " " + holdings.get(symbol));
        }
        return result;
    }
}
```


## Complexity

Here S is the number of available stocks, and H is the number of different stocks one user holds.

| Method | Approach 1 | Approach 2 |
|---|---|---|
| `signUp` | O(1) | O(1) |
| `adminAddOrUpdateStock` | O(1) | O(log S) |
| `listAvailableStocks` | O(S log S) | O(S) |
| `buyStock` | O(1) | O(log H) |
| `sellStock` | O(1) | O(log H) |
| `getWalletBalance` | O(1) | O(1) |
| `getPortfolio` | O(H log H) | O(H) |


Approach 2 pays a small O(log) cost when a symbol goes into or out of a sorted set. In return, the list methods never sort.


## Key Takeaways

- Turn each idea in the problem into a small class with one job.
- Use a Facade as the single entry point, so the caller stays simple.
- Check everything first and change the state last. Then a `false` result never leaves half done changes behind.
- A hash map plus a sorted set gives fast lookups and sorted output, using only simple data structures.