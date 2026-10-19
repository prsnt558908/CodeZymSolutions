# Design Credit Card Payment Processing System in Java

#### Problem Statement
[https://codezym.com/question/291-credit-card-payment-processing-system](https://codezym.com/question/291-credit-card-payment-processing-system)


This is a low level design problem. At its core, the whole system is just two lookups: **gateways by name** and **transactions by id**.

The most important rule is about transaction ids. The first call with an id decides the result, and we store it. Every later call with the same id simply returns what is stored.

This is how real payment systems make sure a customer is never charged twice when a request is retried.

Only one part of this problem really gains from a design pattern: **choosing the gateway**. We put that rule behind the **Strategy pattern**, because routing rules (preferred gateway, cheapest fee, round robin) change often in real payment systems.

Everything else stays plain: a `Gateway` class, a `Transaction` class and a small enum for the status. Patterns like **State** or **Adapter** sound good for payments, but here they would only add extra classes without making anything easier.


## Quick Recap of the Rules

- A gateway has a unique name, a list of currencies and an on/off switch. An empty list means it supports every currency. New gateways start enabled.
- **Picking a gateway:** use the preferred one if it is enabled and supports the currency. Otherwise, use the enabled gateway with the smallest name that supports it.
- **Valid card:** 12 to 19 characters, and every character is a digit.
- **Same transaction id again:** never charge again. Just return the current stored result, even if it was a failure.
- **Refund:** only a `SUCCESS` payment can be refunded, and only once. It then becomes `REFUNDED`.


## Which Design Pattern Fits?

### Strategy Pattern (used)

Picking a gateway is a business rule, and business rules change. Today it is "preferred first, then smallest name". Tomorrow it could be "cheapest fee first" or "spread payments across gateways".

With Strategy, the rule lives in its own small class behind an interface. `processPayment` only asks "which gateway should I use?" and does not care how the answer is found.

Why not a plain `if-else` inside `processPayment`? It works today, but every new rule would mean editing the core payment method again.

Strategy costs us just one interface and one class, and each new rule becomes a new class.

### State Pattern (sounds good, but not needed)

A transaction has statuses: `SUCCESS`, `FAILED` and `REFUNDED`. That looks like a job for the State pattern, where each status becomes its own class.

But there is only one possible move: `SUCCESS` to `REFUNDED`. The other two never change.

An interface plus three classes just to answer "can this be refunded?" is too much. An enum and one `if` check do the same job and are easier to read.

### Adapter Pattern (sounds good, but not needed)

In real life, Stripe and PayPal have very different APIs, and an Adapter would wrap each one behind a common interface.

Here, a gateway is just a name, a currency list and an on/off switch. All gateways behave exactly the same, so there is nothing to adapt.


## Solution 1: Simple Approach with Maps and Strings

Let us first solve it in the most direct way, using only maps and strings.

- `gatewayCurrencies`: gateway name to its set of currencies.
- `gatewayEnabled`: gateway name to `true` or `false`.
- `transactions`: transaction id to its result string, like `"SUCCESS,Stripe"`.

To process a payment, we check the card first and then pick a gateway. We try the preferred gateway. If it cannot be used, we loop over all gateways and remember the smallest name that can be used.

A refund looks at the stored string. If it starts with `SUCCESS,`, we rewrite it as `REFUNDED,` plus the gateway name.

```java
import java.util.*;

/**
 * Simple version: everything is kept in plain maps and strings.
 */
public class PaymentProcessingSystem {
    // gateway name -> supported currencies (empty set means every currency)
    Map<String, Set<String>> gatewayCurrencies = new HashMap<>();
    // gateway name -> is the gateway enabled
    Map<String, Boolean> gatewayEnabled = new HashMap<>();
    // transaction id -> stored result, like "SUCCESS,Stripe"
    Map<String, String> transactions = new HashMap<>();

    public PaymentProcessingSystem() {
    }

    public boolean addPaymentGateway(String gatewayName, List<String> supportedCurrencies) {
        if (gatewayEnabled.containsKey(gatewayName)) {
            return false;
        }
        gatewayCurrencies.put(gatewayName, new HashSet<>(supportedCurrencies));
        gatewayEnabled.put(gatewayName, true);
        return true;
    }

    public boolean setPaymentGatewayEnabled(String gatewayName, boolean enabled) {
        if (!gatewayEnabled.containsKey(gatewayName)) {
            return false;
        }
        gatewayEnabled.put(gatewayName, enabled);
        return true;
    }

    public String processPayment(String transactionId, String cardNumber, int amountInCents,
                                 String currency, String preferredGateway) {
        // Same id again: never charge twice, just return the stored result.
        if (transactions.containsKey(transactionId)) {
            return transactions.get(transactionId);
        }

        String result;
        if (!isValidCard(cardNumber)) {
            result = "FAILED,INVALID_CARD";
        } else {
            String gatewayName = pickGateway(currency, preferredGateway);
            if (gatewayName == null) {
                result = "FAILED,NO_AVAILABLE_GATEWAY";
            } else {
                result = "SUCCESS," + gatewayName;
            }
        }
        transactions.put(transactionId, result);
        return result;
    }

    public boolean refundPayment(String transactionId) {
        String result = transactions.get(transactionId);
        if (result == null || !result.startsWith("SUCCESS,")) {
            return false;
        }
        // "SUCCESS,Stripe" becomes "REFUNDED,Stripe"
        String gatewayName = result.substring("SUCCESS,".length());
        transactions.put(transactionId, "REFUNDED," + gatewayName);
        return true;
    }

    public String getTransactionStatus(String transactionId) {
        return transactions.getOrDefault(transactionId, "NOT_FOUND");
    }

    // Preferred gateway if it can be used, else the smallest name that can be used, else null.
    String pickGateway(String currency, String preferredGateway) {
        if (canUse(preferredGateway, currency)) {
            return preferredGateway;
        }
        String best = null;
        for (String name : gatewayEnabled.keySet()) {
            if (canUse(name, currency) && (best == null || name.compareTo(best) < 0)) {
                best = name;
            }
        }
        return best;
    }

    // True if the gateway exists, is enabled and supports the currency.
    boolean canUse(String gatewayName, String currency) {
        if (!gatewayEnabled.getOrDefault(gatewayName, false)) {
            return false;
        }
        Set<String> currencies = gatewayCurrencies.get(gatewayName);
        return currencies.isEmpty() || currencies.contains(currency);
    }

    // Only the digits 0 to 9, and a length from 12 to 19.
    boolean isValidCard(String cardNumber) {
        if (cardNumber.length() < 12 || cardNumber.length() > 19) {
            return false;
        }
        for (char c : cardNumber.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
```

### Problems with This Approach

This code works, but it has some weak spots.

- **One gateway is split across two maps.** Adding a new detail, like a fee, means yet another map. It is easy to update one map and forget the other.
- **The status is just text.** A refund has to check how the string starts and then rebuild it. One typo in `"SUCCESS,"` and refunds quietly stop working.
- **The gateway rule is stuck inside the system class.** Changing how we pick a gateway means editing the main class.
- **Finding the smallest name scans all gateways each time.** Gateways are added rarely, but payments happen all the time. We can keep the gateways sorted once and simply take the first one that works.


## Solution 2: Clean Design with Classes and the Strategy Pattern

We fix the problems above one step at a time.

### Step 1: A `Gateway` Class

All data about one gateway now lives in one object: its name, its currencies and its enabled flag.

Currencies are stored in a `Set`. The only question we ever ask is "is this currency supported?", and a set answers it fast. Duplicate currencies in the input also stop mattering.

The method `canProcess(currency)` keeps the whole rule in one place: the gateway must be enabled, and its set must be empty or contain the currency.

### Step 2: A `Transaction` Class with a Status Enum

Instead of a raw string, each transaction keeps two things:

- `status`: an enum value, `SUCCESS`, `FAILED` or `REFUNDED`.
- `detail`: the gateway name for `SUCCESS` and `REFUNDED`, or the failure reason for `FAILED`.

Now a refund is a simple check: if the status is `SUCCESS`, change it to `REFUNDED`. The gateway name stays the same. The output is built in one place, `getResult()`, as status plus comma plus detail.

All transactions live in a `HashMap` from id to transaction. This map is what makes retries safe: one lookup tells us if we have seen the id before.

We never store the card number at all. So it can never show up in a returned value, which is one of the rules.

### Step 3: Keep Gateways Sorted by Name

We keep gateways in two structures:

- `gateways`: a `HashMap` from name to gateway. It gives fast lookups when adding, enabling, or finding the preferred gateway.
- `sortedGateways`: a `List` of the same gateways, sorted by name. We sort it again each time a gateway is added.

Sorting on add is cheap, because there are at most 100 gateways and adds are rare. Now finding "the smallest name that works" is easy: walk the list from the start and stop at the first gateway that can process the payment.

Names are compared with plain `compareTo`. It is case-sensitive, so uppercase letters come before lowercase ones. For example, `"Zeta"` comes before `"alpha"`.

### Step 4: The Strategy for Choosing a Gateway

`GatewaySelectionStrategy` is an interface with one method. It gets the currency, the preferred gateway and the sorted list, and returns a gateway or `null`.

`PreferredThenSmallestNameStrategy` is the rule from the problem:

1. If the preferred gateway exists and can process the currency, use it.
2. Otherwise, return the first gateway in the sorted list that can process it.
3. If none can, return `null`.

`PaymentProcessingSystem` creates this strategy in its constructor. To try a different rule later, we only change that one line.

### How `processPayment` Works

1. If the transaction id is already stored, return its current result and ignore the new arguments.
2. If the card is invalid, save `FAILED,INVALID_CARD`.
3. Otherwise, look up the preferred gateway and ask the strategy to pick one. An empty name finds nothing, which simply means "no preference".
4. Save `SUCCESS,<gateway>` if a gateway was found, else `FAILED,NO_AVAILABLE_GATEWAY`.

The helper `saveTransaction` stores every new transaction, failed ones too, and returns its result.

The card is checked before the gateways. Example 3 shows why: PayPal is disabled and the card is bad, and the expected answer is `FAILED,INVALID_CARD`.

We check each character against `'0'` to `'9'` ourselves. `Character.isDigit` would also accept Hindi digits like `१`.

`amountInCents` does not change any result. Refunds are always full, so we do not even need to store it.

### Code

```java
import java.util.*;

/** A payment gateway such as Stripe or PayPal. */
class Gateway {
    String name;
    Set<String> supportedCurrencies; // empty set means every currency is supported
    boolean enabled = true;          // a new gateway starts enabled

    Gateway(String name, List<String> supportedCurrencies) {
        this.name = name;
        this.supportedCurrencies = new HashSet<>(supportedCurrencies);
    }

    /** True when this gateway may charge a new payment in this currency. */
    boolean canProcess(String currency) {
        if (!enabled) {
            return false;
        }
        return supportedCurrencies.isEmpty() || supportedCurrencies.contains(currency);
    }
}

/** The three possible states of a transaction. */
enum TransactionStatus {
    SUCCESS, FAILED, REFUNDED
}

/** One payment attempt and how it ended. */
class Transaction {
    String transactionId;
    TransactionStatus status;
    String detail; // gateway name for SUCCESS and REFUNDED, failure reason for FAILED

    Transaction(String transactionId, TransactionStatus status, String detail) {
        this.transactionId = transactionId;
        this.status = status;
        this.detail = detail;
    }

    /** Full refund. Only a successful payment can be refunded, and only once. */
    boolean refund() {
        if (status != TransactionStatus.SUCCESS) {
            return false;
        }
        status = TransactionStatus.REFUNDED;
        return true;
    }

    /** Result in the expected format, like "SUCCESS,Stripe" or "FAILED,INVALID_CARD". */
    String getResult() {
        return status + "," + detail;
    }
}

/**
 * Strategy pattern: decides which gateway should charge a payment.
 * A new routing rule is just a new class. PaymentProcessingSystem does not change.
 */
interface GatewaySelectionStrategy {
    /** Returns the chosen gateway, or null when no gateway can take this payment. */
    Gateway selectGateway(String currency, Gateway preferred, List<Gateway> sortedGateways);
}

/** The rule from the problem: preferred gateway first, else the smallest name that works. */
class PreferredThenSmallestNameStrategy implements GatewaySelectionStrategy {
    public Gateway selectGateway(String currency, Gateway preferred, List<Gateway> sortedGateways) {
        if (preferred != null && preferred.canProcess(currency)) {
            return preferred;
        }
        // the list is sorted by name, so the first gateway that works has the smallest name
        for (Gateway gateway : sortedGateways) {
            if (gateway.canProcess(currency)) {
                return gateway;
            }
        }
        return null;
    }
}

/**
 * Entry point of the system. It stores gateways and transactions,
 * and asks the selection strategy which gateway should charge a payment.
 */
public class PaymentProcessingSystem {
    // gateway name -> gateway, for fast lookup by name
    Map<String, Gateway> gateways = new HashMap<>();
    // the same gateways, kept sorted by name
    List<Gateway> sortedGateways = new ArrayList<>();
    // transaction id -> transaction, so the same id is never charged twice
    Map<String, Transaction> transactions = new HashMap<>();
    // the rule used to pick a gateway
    GatewaySelectionStrategy selectionStrategy;

    public PaymentProcessingSystem() {
        selectionStrategy = new PreferredThenSmallestNameStrategy();
    }

    public boolean addPaymentGateway(String gatewayName, List<String> supportedCurrencies) {
        if (gateways.containsKey(gatewayName)) {
            return false;
        }
        Gateway gateway = new Gateway(gatewayName, supportedCurrencies);
        gateways.put(gatewayName, gateway);
        sortedGateways.add(gateway);
        // at most 100 gateways and adds are rare, so sorting here is cheap
        sortedGateways.sort((a, b) -> a.name.compareTo(b.name));
        return true;
    }

    public boolean setPaymentGatewayEnabled(String gatewayName, boolean enabled) {
        Gateway gateway = gateways.get(gatewayName);
        if (gateway == null) {
            return false;
        }
        gateway.enabled = enabled;
        return true;
    }

    public String processPayment(String transactionId, String cardNumber, int amountInCents,
                                 String currency, String preferredGateway) {
        // Seen this id before: return its current result and ignore the new arguments.
        Transaction existing = transactions.get(transactionId);
        if (existing != null) {
            return existing.getResult();
        }
        if (!isValidCard(cardNumber)) {
            return saveTransaction(transactionId, TransactionStatus.FAILED, "INVALID_CARD");
        }
        // an empty or unknown name gives null, which means "no preference"
        Gateway preferred = gateways.get(preferredGateway);
        Gateway chosen = selectionStrategy.selectGateway(currency, preferred, sortedGateways);
        if (chosen == null) {
            return saveTransaction(transactionId, TransactionStatus.FAILED, "NO_AVAILABLE_GATEWAY");
        }
        return saveTransaction(transactionId, TransactionStatus.SUCCESS, chosen.name);
    }

    public boolean refundPayment(String transactionId) {
        Transaction transaction = transactions.get(transactionId);
        return transaction != null && transaction.refund();
    }

    public String getTransactionStatus(String transactionId) {
        Transaction transaction = transactions.get(transactionId);
        return transaction == null ? "NOT_FOUND" : transaction.getResult();
    }

    /** Stores a new transaction (failed ones too) and returns its result. */
    String saveTransaction(String transactionId, TransactionStatus status, String detail) {
        Transaction transaction = new Transaction(transactionId, status, detail);
        transactions.put(transactionId, transaction);
        return transaction.getResult();
    }

    /** A valid card has only the digits 0 to 9 and a length from 12 to 19. */
    boolean isValidCard(String cardNumber) {
        if (cardNumber.length() < 12 || cardNumber.length() > 19) {
            return false;
        }
        for (char c : cardNumber.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
```


## Complexity

Let `G` be the number of gateways (at most 100) and `C` the number of currencies in one call.

| Method | Time |
| --- | --- |
| `addPaymentGateway` | O(C + G log G), copy the currencies and sort the list |
| `setPaymentGatewayEnabled` | O(1) |
| `processPayment` | O(G), one walk over the sorted list. The card check reads 19 characters at most |
| `refundPayment` | O(1) |
| `getTransactionStatus` | O(1) |

Map lookups are O(1) on average. Space is O(G * C + T), where `T` is the number of transactions.


## Edge Cases to Remember

- Check the card **before** looking at gateways.
- Store failed payments too. The same id always returns the stored result.
- After a refund, reusing the id returns `REFUNDED,<gateway>`, not a new charge.
- Disabling a gateway does not touch old transactions. They can still be refunded.
- Gateway names and transaction ids are case-sensitive.