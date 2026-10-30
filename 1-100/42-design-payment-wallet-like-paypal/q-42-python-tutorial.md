# Design a Payment Wallet like PayPal in Python

#### Problem Statement

[https://codezym.com/question/42-design-payment-wallet-like-paypal](https://codezym.com/question/42-design-payment-wallet-like-paypal)

Keep each user's balance and active fixed deposit in one account object, and find accounts through a dictionary. After a successful debit, check whether the FD breaks or earns interest. No named design pattern is needed here. Small classes and one shared FD check keep the fixed rules easy to follow.

## Start with a simple approach

We could store balances, FD amounts, and debit counts in separate dictionaries.

```python
balances = {}
fd_amounts = {}
debit_counts = {}
```

Lookups are already fast, but every FD creation, failure, and completion must keep these dictionaries in sync. Copying the FD logic into both spending and transferring also makes changes easier to miss.

Group related data into an account instead, and reuse one method for the FD check.

## Classes and data structure

- `PaymentWallet` keeps a dictionary from user IDs to `WalletAccount` objects. It finds users, validates requests, and updates balances.
- `WalletAccount` stores the balance and an optional `FixedDeposit`. Its `after_successful_debit()` method contains the shared FD rules.
- `FixedDeposit` stores the threshold amount and the number of successful debits since creation. A `None` reference means there is no active FD.

The dictionary key already stores the user ID, so the account does not need another copy. A transaction history is unnecessary because only the current balance and FD progress affect future operations. Dataclasses keep the two small data objects concise.

The **State pattern** would introduce classes for an FD's lifecycle, but completed and broken deposits have no further behavior to maintain. The **Strategy pattern** would help with interchangeable interest rules, but this problem has just one. Neither improves this implementation.

## Handle a debit once

Creating an FD records its amount without deducting money. From that point, only successful spending and outgoing transfers advance its counter.

After each successful debit:

1. If there is no active FD, finish.
2. If the updated balance is below the FD amount, remove the FD immediately.
3. Otherwise, increase its successful debit count.
4. On the fifth debit, add the rounded interest and remove the FD.

Check the balance before awarding interest. Interest must not rescue an FD that falls below its threshold on the fifth debit. A later top-up also cannot revive an FD that already broke.

Adding money, receiving a transfer, checking the balance, and failed requests do not advance the counter. Once an FD breaks or completes, the user can create another one.

## Validation and rounding

Validate the whole request before changing anything. For transfers, check the sender, then the receiver, then the sender's balance. For FD creation, check the user, then sufficient balance, then an existing active FD, following the statement's listed order.

The code updates both transfer balances before checking the sender's FD. The statement does not specify a separate self-transfer rule. Here, a successful self-transfer counts once and the FD sees the unchanged net balance.

Five percent of an integer amount is `amount / 20`. To round halves upward without floating-point arithmetic:

```python
interest = (amount + 10) // 20
```

For `82`, the interest is `4`. For `90`, it is `5`. Avoid `round()` here because it rounds exact halfway values toward the nearest even integer, while this problem requires rounding them upward.

## Small walkthrough

Suppose a user has `200` and creates an FD for `82`. The balance remains `200`.

Five successful debits of `10`, `10`, `20`, `30`, and `30` leave balances of `190`, `180`, `160`, `130`, and `100`. Each balance is at least `82`, so the fifth debit adds `4` interest. The final balance is `104`, and the FD is cleared.

If any of those debits had left less than `82`, the FD would have broken at that point. The debit itself would still return `"success"`.

## Complexity

Each operation takes expected **O(1)** time using the dictionary, treating user ID length and integer sizes as bounded. Total space is **O(U)** for `U` registered users, with at most one active FD per user.

## Complete Python solution

The public method names match the supplied starter code.

```python
from dataclasses import dataclass
from typing import Dict, Optional


@dataclass
class FixedDeposit:
    amount: int
    successful_debits: int = 0


@dataclass
class WalletAccount:
    balance: int = 0
    fixed_deposit: Optional[FixedDeposit] = None

    def after_successful_debit(self) -> None:
        if self.fixed_deposit is None:
            return

        # Check failure first, including on the fifth debit.
        if self.balance < self.fixed_deposit.amount:
            self.fixed_deposit = None
            return

        self.fixed_deposit.successful_debits += 1
        if self.fixed_deposit.successful_debits == 5:
            interest = (self.fixed_deposit.amount + 10) // 20
            self.balance += interest
            self.fixed_deposit = None


class PaymentWallet:
    def __init__(self):
        self.accounts: Dict[str, WalletAccount] = {}

    def registerUser(self, userId):
        # The statement guarantees unique, non-blank user IDs.
        self.accounts[userId] = WalletAccount()

    def addMoneyToWallet(self, userId, amount):
        account = self.accounts.get(userId)
        if account is None:
            return "user does not exist"

        account.balance += amount
        return "success"

    def spendMoney(self, userId, amount):
        account = self.accounts.get(userId)
        if account is None:
            return "user does not exist"
        if account.balance < amount:
            return "insufficient balance"

        account.balance -= amount
        account.after_successful_debit()
        return "success"

    def transferMoney(self, fromUser, toUser, amount):
        sender = self.accounts.get(fromUser)
        if sender is None:
            return "sender does not exist"

        receiver = self.accounts.get(toUser)
        if receiver is None:
            return "receiver does not exist"
        if sender.balance < amount:
            return "insufficient balance"

        # Finish both balance changes before checking the sender's FD.
        sender.balance -= amount
        receiver.balance += amount
        sender.after_successful_debit()
        return "success"

    def createFixedDeposit(self, userId, amount):
        account = self.accounts.get(userId)
        if account is None:
            return "user does not exist"
        if account.balance < amount:
            return "insufficient balance"
        if account.fixed_deposit is not None:
            return "an active fixed deposit already exists"

        # The amount is a balance threshold, not a deduction.
        account.fixed_deposit = FixedDeposit(amount)
        return "success"

    def getAccountBalance(self, userId):
        account = self.accounts.get(userId)
        return -1 if account is None else account.balance
```
