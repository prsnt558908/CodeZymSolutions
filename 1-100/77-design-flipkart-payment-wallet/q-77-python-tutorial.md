# Design Flipkart Payment Wallet with Transaction History in Python


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


A dictionary maps each user ID to a `Wallet`. Its keys also tell us which users are registered, so we do not need a separate set.


The data classes keep these records short. `field(default_factory=list)` gives every wallet its own transaction list.


Use `int` for balances, amounts, and timestamps. It also handles large balances built from many successful loads.


A Strategy pattern could separate sorting rules, but two fixed choices do not need separate classes. A Factory for payment sources adds little here because UPI and cards are only stored labels. The problem does not require payment integration.


## Loading and sending money


For `loadMoney`, check that the user exists, `0 < amount < 10^9`, and `timestamp > 0`. If any check fails, return `False` without changing the wallet. Otherwise, increase the balance and append one `LOAD` record. Store the source exactly as passed.


For `sendMoney`, check both users, the amount range, `timestamp > 0`, and the sender's balance first. If any check fails, return `False` without changing balances or history. Both methods must reject zero and negative timestamps.


After validation, subtract from the sender and add to the receiver. Append a `SEND` record to the sender and a `RECEIVE` record to the receiver. Both use the passed timestamp and identify the other user as the counterparty.


The statement does not forbid sending to yourself. With sufficient funds, the same wallet is debited and credited, leaving its balance unchanged. Append `SEND` first, then `RECEIVE`.


## Reading balance and history


`getBalance` returns the stored balance, or `-1` for an unregistered user. An unregistered user's history is an empty list.


For history, copy matching transactions into a new list. `send` selects only `SEND`, `receive` selects only `RECEIVE`, and `all` includes every type. A load is a credit, but it is not a `RECEIVE` record.


For valid operations, the statement guarantees timestamps that never decrease, with ties allowed. Appending records therefore already keeps time ascending and preserves insertion order for equal timestamps. Filtering keeps that order, so a time query needs no extra sort.


For amount order, sort the copied list from highest to lowest. Never reorder the stored history, because a later time query must still see the original order.


Python's list sort is stable, including with `reverse=True`, so equal amounts also keep their insertion order.


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


## Python implementation


```python
from dataclasses import dataclass, field
from typing import Dict, List


@dataclass
class Transaction:
    timestamp: int
    type: str
    counterparty: str
    amount: int

    def encode(self) -> str:
        return (
            f"time={self.timestamp}"
            f"|type={self.type}"
            f"|counterparty={self.counterparty}"
            f"|amount={self.amount}"
        )


@dataclass
class Wallet:
    # Keep the balance equal to all credits minus all debits.
    balance: int = 0
    transactions: List[Transaction] = field(default_factory=list)


class FlipkartWallet:
    def __init__(self, registeredUserIds: List[str]):
        self.wallets: Dict[str, Wallet] = {
            userId: Wallet() for userId in registeredUserIds
        }

    def loadMoney(self, userId: str, amount: int, source: str, timestamp: int) -> bool:
        wallet = self.wallets.get(userId)
        if (
            wallet is None
            or amount <= 0
            or amount >= 1_000_000_000
            or timestamp <= 0
        ):
            return False

        wallet.balance += amount
        wallet.transactions.append(Transaction(timestamp, "LOAD", source, amount))
        return True

    def sendMoney(self, fromUserId: str, toUserId: str, amount: int, timestamp: int) -> bool:
        sender = self.wallets.get(fromUserId)
        receiver = self.wallets.get(toUserId)

        # Validate everything before changing either wallet.
        if (
            sender is None
            or receiver is None
            or amount <= 0
            or amount >= 1_000_000_000
            or timestamp <= 0
            or sender.balance < amount
        ):
            return False

        sender.balance -= amount
        receiver.balance += amount
        sender.transactions.append(Transaction(timestamp, "SEND", toUserId, amount))
        receiver.transactions.append(Transaction(timestamp, "RECEIVE", fromUserId, amount))
        return True

    def getBalance(self, userId: str) -> int:
        wallet = self.wallets.get(userId)
        return -1 if wallet is None else wallet.balance

    def getTransactionHistory(self, userId: str, sortBy: str, filterBy: str) -> List[str]:
        wallet = self.wallets.get(userId)
        if wallet is None:
            return []

        # Copy matching records so queries cannot reorder the stored history.
        selected = []
        for transaction in wallet.transactions:
            if filterBy == "send" and transaction.type != "SEND":
                continue
            if filterBy == "receive" and transaction.type != "RECEIVE":
                continue
            selected.append(transaction)

        if sortBy == "amount":
            # Stable sorting keeps insertion order when amounts are equal.
            selected.sort(key=lambda transaction: transaction.amount, reverse=True)
        # Timestamps arrive in nondecreasing order, so time order is already correct.

        return [transaction.encode() for transaction in selected]
```
