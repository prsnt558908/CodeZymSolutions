# Design Credit Card Payment Processing System in Python

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

With Strategy, the rule lives in its own small class. `processPayment` only asks "which gateway should I use?" and does not care how the answer is found.

Why not a plain `if`/`else` inside `processPayment`? It works today, but every new rule would mean editing the core payment method again.

Strategy costs us just one base class and one subclass, and each new rule becomes a new subclass.

### State Pattern (sounds good, but not needed)

A transaction has statuses: `SUCCESS`, `FAILED` and `REFUNDED`. That looks like a job for the State pattern, where each status becomes its own class.

But there is only one possible move: `SUCCESS` to `REFUNDED`. The other two never change.

A base class plus three subclasses just to answer "can this be refunded?" is too much. An enum and one `if` check do the same job and are easier to read.

### Adapter Pattern (sounds good, but not needed)

In real life, Stripe and PayPal have very different APIs, and an Adapter would wrap each one behind a common interface.

Here, a gateway is just a name, a currency list and an on/off switch. All gateways behave exactly the same, so there is nothing to adapt.


## Solution 1: Simple Approach with Dicts and Strings

Let us first solve it in the most direct way, using only dicts and strings.

- `gateway_currencies`: gateway name to its set of currencies.
- `gateway_enabled`: gateway name to `True` or `False`.
- `transactions`: transaction id to its result string, like `"SUCCESS,Stripe"`.

To process a payment, we check the card first and then pick a gateway. We try the preferred gateway. If it cannot be used, we loop over all gateways and remember the smallest name that can be used.

A refund looks at the stored string. If it starts with `SUCCESS,`, we rewrite it as `REFUNDED,` plus the gateway name.

```python
class PaymentProcessingSystem:
    """Simple version: everything is kept in plain dicts and strings."""

    def __init__(self):
        # gateway name -> set of supported currencies (empty set means every currency)
        self.gateway_currencies = {}
        # gateway name -> True or False
        self.gateway_enabled = {}
        # transaction id -> stored result, like "SUCCESS,Stripe"
        self.transactions = {}

    def addPaymentGateway(self, gatewayName, supportedCurrencies):
        if gatewayName in self.gateway_enabled:
            return False
        self.gateway_currencies[gatewayName] = set(supportedCurrencies)
        self.gateway_enabled[gatewayName] = True
        return True

    def setPaymentGatewayEnabled(self, gatewayName, enabled):
        if gatewayName not in self.gateway_enabled:
            return False
        self.gateway_enabled[gatewayName] = enabled
        return True

    def processPayment(self, transactionId, cardNumber, amountInCents, currency, preferredGateway):
        # Same id again: never charge twice, just return the stored result.
        if transactionId in self.transactions:
            return self.transactions[transactionId]

        if not self.is_valid_card(cardNumber):
            result = "FAILED,INVALID_CARD"
        else:
            gateway_name = self.pick_gateway(currency, preferredGateway)
            if gateway_name is None:
                result = "FAILED,NO_AVAILABLE_GATEWAY"
            else:
                result = "SUCCESS," + gateway_name
        self.transactions[transactionId] = result
        return result

    def refundPayment(self, transactionId):
        result = self.transactions.get(transactionId)
        if result is None or not result.startswith("SUCCESS,"):
            return False
        # "SUCCESS,Stripe" becomes "REFUNDED,Stripe"
        gateway_name = result[len("SUCCESS,"):]
        self.transactions[transactionId] = "REFUNDED," + gateway_name
        return True

    def getTransactionStatus(self, transactionId):
        return self.transactions.get(transactionId, "NOT_FOUND")

    def pick_gateway(self, currency, preferred_gateway):
        """Preferred gateway if usable, else the smallest usable name, else None."""
        if self.can_use(preferred_gateway, currency):
            return preferred_gateway
        best = None
        for name in self.gateway_enabled:
            if self.can_use(name, currency) and (best is None or name < best):
                best = name
        return best

    def can_use(self, gateway_name, currency):
        """True if the gateway exists, is enabled and supports the currency."""
        if not self.gateway_enabled.get(gateway_name, False):
            return False
        currencies = self.gateway_currencies[gateway_name]
        return len(currencies) == 0 or currency in currencies

    def is_valid_card(self, card_number):
        """Only the digits 0 to 9, and a length from 12 to 19."""
        if len(card_number) < 12 or len(card_number) > 19:
            return False
        return all("0" <= c <= "9" for c in card_number)
```

### Problems with This Approach

This code works, but it has some weak spots.

- **One gateway is split across two dicts.** Adding a new detail, like a fee, means yet another dict. It is easy to update one dict and forget the other.
- **The status is just text.** A refund has to check how the string starts and then rebuild it. One typo in `"SUCCESS,"` and refunds quietly stop working.
- **The gateway rule is stuck inside the system class.** Changing how we pick a gateway means editing the main class.
- **Finding the smallest name scans all gateways each time.** Gateways are added rarely, but payments happen all the time. We can keep the gateways sorted once and simply take the first one that works.


## Solution 2: Clean Design with Classes and the Strategy Pattern

We fix the problems above one step at a time.

### Step 1: A `Gateway` Class

All data about one gateway now lives in one object: its name, its currencies and its enabled flag.

Currencies are stored in a `set`. The only question we ever ask is "is this currency supported?", and a set answers it fast. Duplicate currencies in the input also stop mattering.

The method `can_process(currency)` keeps the whole rule in one place: the gateway must be enabled, and its set must be empty or contain the currency.

### Step 2: A `Transaction` Class with a Status Enum

Instead of a raw string, each transaction keeps two things:

- `status`: an `Enum` value, `SUCCESS`, `FAILED` or `REFUNDED`.
- `detail`: the gateway name for `SUCCESS` and `REFUNDED`, or the failure reason for `FAILED`.

Now a refund is a simple check: if the status is `SUCCESS`, change it to `REFUNDED`. The gateway name stays the same. The output is built in one place, `get_result()`, as the status text plus comma plus detail.

All transactions live in a dict from id to transaction. This dict is what makes retries safe: one lookup tells us if we have seen the id before.

We never store the card number at all. So it can never show up in a returned value, which is one of the rules.

### Step 3: Keep Gateways Sorted by Name

We keep gateways in two structures:

- `gateways`: a dict from name to gateway. It gives fast lookups when adding, enabling, or finding the preferred gateway.
- `sorted_gateways`: a list of the same gateways, sorted by name. We sort it again each time a gateway is added.

Sorting on add is cheap, because there are at most 100 gateways and adds are rare. Now finding "the smallest name that works" is easy: walk the list from the start and stop at the first gateway that can process the payment.

Python compares strings character by character. It is case-sensitive, so uppercase letters come before lowercase ones. For example, `"Zeta"` comes before `"alpha"`.

### Step 4: The Strategy for Choosing a Gateway

`GatewaySelectionStrategy` is a small base class with one method. It gets the currency, the preferred gateway and the sorted list, and returns a gateway or `None`.

`PreferredThenSmallestNameStrategy` is the rule from the problem:

1. If the preferred gateway exists and can process the currency, use it.
2. Otherwise, return the first gateway in the sorted list that can process it.
3. If none can, return `None`.

`PaymentProcessingSystem` creates this strategy in `__init__`. To try a different rule later, we only change that one line.

### How `processPayment` Works

1. If the transaction id is already stored, return its current result and ignore the new arguments.
2. If the card is invalid, save `FAILED,INVALID_CARD`.
3. Otherwise, look up the preferred gateway and ask the strategy to pick one. An empty name finds nothing, which simply means "no preference".
4. Save `SUCCESS,<gateway>` if a gateway was found, else `FAILED,NO_AVAILABLE_GATEWAY`.

The helper `save_transaction` stores every new transaction, failed ones too, and returns its result.

The card is checked before the gateways. Example 3 shows why: PayPal is disabled and the card is bad, and the expected answer is `FAILED,INVALID_CARD`.

We check each character against `"0"` to `"9"` ourselves. `str.isdigit()` would also accept Hindi digits like `१` and even `²`.

`amountInCents` does not change any result. Refunds are always full, so we do not even need to store it.

### Code

```python
from enum import Enum


class Gateway:
    """A payment gateway such as Stripe or PayPal."""

    def __init__(self, name, supported_currencies):
        self.name = name
        self.supported_currencies = set(supported_currencies)  # empty set means every currency
        self.enabled = True  # a new gateway starts enabled

    def can_process(self, currency):
        """True when this gateway may charge a new payment in this currency."""
        if not self.enabled:
            return False
        return len(self.supported_currencies) == 0 or currency in self.supported_currencies


class TransactionStatus(Enum):
    """The three possible states of a transaction."""
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    REFUNDED = "REFUNDED"


class Transaction:
    """One payment attempt and how it ended."""

    def __init__(self, transaction_id, status, detail):
        self.transaction_id = transaction_id
        self.status = status
        self.detail = detail  # gateway name for SUCCESS and REFUNDED, failure reason for FAILED

    def refund(self):
        """Full refund. Only a successful payment can be refunded, and only once."""
        if self.status != TransactionStatus.SUCCESS:
            return False
        self.status = TransactionStatus.REFUNDED
        return True

    def get_result(self):
        """Result in the expected format, like "SUCCESS,Stripe" or "FAILED,INVALID_CARD"."""
        return self.status.value + "," + self.detail


class GatewaySelectionStrategy:
    """Strategy pattern: decides which gateway should charge a payment.
    A new routing rule is just a new subclass. PaymentProcessingSystem does not change."""

    def select_gateway(self, currency, preferred, sorted_gateways):
        """Returns the chosen gateway, or None when no gateway can take this payment."""
        raise NotImplementedError


class PreferredThenSmallestNameStrategy(GatewaySelectionStrategy):
    """The rule from the problem: preferred gateway first, else the smallest name that works."""

    def select_gateway(self, currency, preferred, sorted_gateways):
        if preferred is not None and preferred.can_process(currency):
            return preferred
        # the list is sorted by name, so the first gateway that works has the smallest name
        for gateway in sorted_gateways:
            if gateway.can_process(currency):
                return gateway
        return None


class PaymentProcessingSystem:
    """Entry point of the system. It stores gateways and transactions,
    and asks the selection strategy which gateway should charge a payment."""

    def __init__(self):
        # gateway name -> Gateway, for fast lookup by name
        self.gateways = {}
        # the same gateways, kept sorted by name
        self.sorted_gateways = []
        # transaction id -> Transaction, so the same id is never charged twice
        self.transactions = {}
        # the rule used to pick a gateway
        self.selection_strategy = PreferredThenSmallestNameStrategy()

    def addPaymentGateway(self, gatewayName, supportedCurrencies):
        if gatewayName in self.gateways:
            return False
        gateway = Gateway(gatewayName, supportedCurrencies)
        self.gateways[gatewayName] = gateway
        self.sorted_gateways.append(gateway)
        # at most 100 gateways and adds are rare, so sorting here is cheap
        self.sorted_gateways.sort(key=lambda g: g.name)
        return True

    def setPaymentGatewayEnabled(self, gatewayName, enabled):
        gateway = self.gateways.get(gatewayName)
        if gateway is None:
            return False
        gateway.enabled = enabled
        return True

    def processPayment(self, transactionId, cardNumber, amountInCents, currency, preferredGateway):
        # Seen this id before: return its current result and ignore the new arguments.
        existing = self.transactions.get(transactionId)
        if existing is not None:
            return existing.get_result()
        if not self.is_valid_card(cardNumber):
            return self.save_transaction(transactionId, TransactionStatus.FAILED, "INVALID_CARD")
        # an empty or unknown name gives None, which means "no preference"
        preferred = self.gateways.get(preferredGateway)
        chosen = self.selection_strategy.select_gateway(currency, preferred, self.sorted_gateways)
        if chosen is None:
            return self.save_transaction(transactionId, TransactionStatus.FAILED,
                                         "NO_AVAILABLE_GATEWAY")
        return self.save_transaction(transactionId, TransactionStatus.SUCCESS, chosen.name)

    def refundPayment(self, transactionId):
        transaction = self.transactions.get(transactionId)
        return transaction is not None and transaction.refund()

    def getTransactionStatus(self, transactionId):
        transaction = self.transactions.get(transactionId)
        return "NOT_FOUND" if transaction is None else transaction.get_result()

    def save_transaction(self, transaction_id, status, detail):
        """Stores a new transaction (failed ones too) and returns its result."""
        transaction = Transaction(transaction_id, status, detail)
        self.transactions[transaction_id] = transaction
        return transaction.get_result()

    def is_valid_card(self, card_number):
        """A valid card has only the digits 0 to 9 and a length from 12 to 19."""
        if len(card_number) < 12 or len(card_number) > 19:
            return False
        return all("0" <= c <= "9" for c in card_number)
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

Dict lookups are O(1) on average. Space is O(G * C + T), where `T` is the number of transactions.


## Edge Cases to Remember

- Check the card **before** looking at gateways.
- Store failed payments too. The same id always returns the stored result.
- After a refund, reusing the id returns `REFUNDED,<gateway>`, not a new charge.
- Disabling a gateway does not touch old transactions. They can still be refunded.
- Gateway names and transaction ids are case-sensitive.