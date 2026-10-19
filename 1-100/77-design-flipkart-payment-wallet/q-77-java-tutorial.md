# Design Flipkart Payment Wallet with Transaction History in Java


#### Problem Statement

[https://codezym.com/question/77-design-flipkart-payment-wallet](https://codezym.com/question/77-design-flipkart-payment-wallet)


Keep one wallet for each registered user. Each wallet stores its current balance and transaction history. Check a transfer before changing either wallet, then record both sides. Simple classes, a map, and lists fit these fixed rules. No extra design pattern is needed.


## Start with transaction history


A simple solution stores every user's transactions and calculates the balance whenever needed. Add `LOAD` and `RECEIVE` amounts, then subtract `SEND` amounts.


That works, but each balance query and sufficient-funds check scans the user's history. With `n` records, each check costs `O(n)`.


Store the current balance alongside the history instead. Update it only when an operation succeeds. It always stays equal to total credits minus total debits, and balance queries become `O(1)` on average.


## Classes and data structures


- `Transaction` stores the timestamp, type, counterparty, and amount. Its `encode()` method builds the exact output string.
- `Wallet` keeps one user's balance and transaction list together.
- `FlipkartWallet` finds wallets, checks requests, and applies successful operations.


A `HashMap<String, Wallet>` finds a user's wallet quickly. Its keys also tell us which users are registered, so we do not need a separate set.


Each wallet gets its own `ArrayList<Transaction>`.


Use `long` for balances, amounts, and timestamps. Several valid loads can make a balance exceed the range of `int`.


A Strategy pattern could separate sorting rules, but two fixed choices do not need separate classes. A Factory for payment sources adds little here because UPI and cards are only stored labels. The problem does not require payment integration.


## Loading and sending money


For `loadMoney`, check that the user exists, `0 < amount < 10^9`, and `timestamp > 0`. If any check fails, return `false` without changing the wallet. Otherwise, increase the balance and append one `LOAD` record. Store the source exactly as passed.


For `sendMoney`, check both users, the amount range, `timestamp > 0`, and the sender's balance first. If any check fails, return `false` without changing balances or history. Both methods must reject zero and negative timestamps.


After validation, subtract from the sender and add to the receiver. Append a `SEND` record to the sender and a `RECEIVE` record to the receiver. Both use the passed timestamp and identify the other user as the counterparty.


The statement does not forbid sending to yourself. With sufficient funds, the same wallet is debited and credited, leaving its balance unchanged. Append `SEND` first, then `RECEIVE`.


## Reading balance and history


`getBalance` returns the stored balance, or `-1` for an unregistered user. An unregistered user's history is an empty list.


For history, copy matching transactions into a new list. `send` selects only `SEND`, `receive` selects only `RECEIVE`, and `all` includes every type. A load is a credit, but it is not a `RECEIVE` record.


For valid operations, the statement guarantees timestamps that never decrease, with ties allowed. Appending records therefore already keeps time ascending and preserves insertion order for equal timestamps. Filtering keeps that order, so a time query needs no extra sort.


For amount order, sort the copied list from highest to lowest. Never reorder the stored history, because a later time query must still see the original order.


Java's list sort is stable, so equal amounts also keep their insertion order. `Long.compare` compares amounts without subtracting them.


Finally, encode each selected transaction as `time=<timestamp>|type=<type>|counterparty=<value>|amount=<amount>`. Return only these strings, without a balance line.


## Small walkthrough


Load `500` into `user-1` at time `1000`, then send `200` to `user-2` at time `1010`. Their balances become `300` and `200`.


Trying to send another `400` fails and adds no records. Loading `50` at time `1030` makes the first balance `350`. Its time history has `LOAD 500`, `SEND 200`, `LOAD 50`. A later load of `1000` appears last by time and first by amount.


## Complexity


Let `U` be the number of registered IDs, `T` the total stored records, `n` one user's history size, and `k` the number of matching records. Ignoring string lengths:


- Constructor: `O(U)` time.
- Loading, sending, and fetching balance: `O(1)` average time, with amortized list appends.
- History ordered by time: `O(n)` time.
- History ordered by amount: `O(n + k log k)` time.
- Stored space: `O(U + T)`. A history query uses `O(k)` extra space.


## Java implementation


```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Transaction {
    long timestamp;
    String type;
    String counterparty;
    long amount;

    Transaction(long timestamp, String type, String counterparty, long amount) {
        this.timestamp = timestamp;
        this.type = type;
        this.counterparty = counterparty;
        this.amount = amount;
    }

    String encode() {
        return "time=" + timestamp
                + "|type=" + type
                + "|counterparty=" + counterparty
                + "|amount=" + amount;
    }
}

class Wallet {
    // Keep the balance equal to all credits minus all debits.
    long balance;
    List<Transaction> transactions;

    Wallet() {
        balance = 0;
        transactions = new ArrayList<>();
    }
}

public class FlipkartWallet {
    Map<String, Wallet> wallets;

    public FlipkartWallet(List<String> registeredUserIds) {
        wallets = new HashMap<>();
        for (String userId : registeredUserIds) {
            wallets.put(userId, new Wallet());
        }
    }

    public boolean loadMoney(String userId, long amount, String source, long timestamp) {
        Wallet wallet = wallets.get(userId);
        if (wallet == null || amount <= 0 || amount >= 1_000_000_000L
                || timestamp <= 0) {
            return false;
        }

        wallet.balance += amount;
        wallet.transactions.add(new Transaction(timestamp, "LOAD", source, amount));
        return true;
    }

    public boolean sendMoney(String fromUserId, String toUserId, long amount, long timestamp) {
        Wallet sender = wallets.get(fromUserId);
        Wallet receiver = wallets.get(toUserId);

        // Validate everything before changing either wallet.
        if (sender == null || receiver == null || amount <= 0
                || amount >= 1_000_000_000L || timestamp <= 0 || sender.balance < amount) {
            return false;
        }

        sender.balance -= amount;
        receiver.balance += amount;
        sender.transactions.add(new Transaction(timestamp, "SEND", toUserId, amount));
        receiver.transactions.add(new Transaction(timestamp, "RECEIVE", fromUserId, amount));
        return true;
    }

    public long getBalance(String userId) {
        Wallet wallet = wallets.get(userId);
        return wallet == null ? -1 : wallet.balance;
    }

    public List<String> getTransactionHistory(String userId, String sortBy, String filterBy) {
        Wallet wallet = wallets.get(userId);
        if (wallet == null) {
            return new ArrayList<>();
        }

        // Copy matching records so queries cannot reorder the stored history.
        List<Transaction> selected = new ArrayList<>();
        for (Transaction transaction : wallet.transactions) {
            if ("send".equals(filterBy) && !"SEND".equals(transaction.type)) {
                continue;
            }
            if ("receive".equals(filterBy) && !"RECEIVE".equals(transaction.type)) {
                continue;
            }
            selected.add(transaction);
        }

        if ("amount".equals(sortBy)) {
            // Stable sorting keeps insertion order when amounts are equal.
            selected.sort((first, second) -> Long.compare(second.amount, first.amount));
        }
        // Timestamps arrive in nondecreasing order, so time order is already correct.

        List<String> result = new ArrayList<>();
        for (Transaction transaction : selected) {
            result.add(transaction.encode());
        }
        return result;
    }
}
```
