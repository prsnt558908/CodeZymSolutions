# Design Splitwise Expense Sharing App in Python

Problem Statement: https://codezym.com/question/12-design-splitwise-expense-sharing-app

The core idea behind this solution is to avoid storing every pairwise transaction between users. Instead, we maintain one **net balance** for every user. If a user's net balance is positive, that user should receive money. If it is negative, that user owes money. This keeps the design simple, efficient, and easy to reason about. The solution uses a lightweight bookkeeping approach with integer cents to avoid floating-point precision issues.

## High Level Design

In a Splitwise-like expense sharing app, users can register themselves and record expenses. Whenever an expense is recorded, the total paid amount is split equally among all members. Then each user's net contribution is updated.

For example, if three users are part of an expense and one user paid the full amount, that payer becomes a creditor, while the other users become debtors.

Instead of immediately creating multiple transactions between users, we only update each user's net balance.

Later, when `listBalances()` is called, we calculate who should pay whom by matching debtors with creditors.

## Why Store Net Balance Instead Of All Transactions?

A simple but inefficient approach would be to store every expense and calculate balances from scratch every time.

This solution uses a better approach:

- Store only the final net amount for each user.
- Positive balance means the user should receive money.
- Negative balance means the user owes money.
- Zero balance means the user is already settled.

This makes balance listing much easier and avoids unnecessary repeated calculations.

## Important Data Structures

### `_users`

```python
self._users: Set[str] = set()
```

This set stores all registered user ids.

We use a set because checking whether a user is registered should be fast.

### `_net_cents`

```python
self._net_cents: Dict[str, int] = {}
```

This dictionary stores the net balance of every user in cents.

Example:

```text
A:  500   means A should receive 5.00
B: -300   means B owes 3.00
C: -200   means C owes 2.00
```

Using cents instead of floating-point numbers avoids issues like `10.10 + 20.20` producing inaccurate decimal results.

### `_seen_expense_ids`

```python
self._seen_expense_ids: Set[int] = set()
```

This set stores expense ids that have already been processed.

It prevents duplicate expenses from being recorded multiple times.

### `_Entry`

```python
@dataclass
class _Entry:
    user_id: str
    cents: int
```

This small helper class is used inside `listBalances()`.

It helps represent a debtor or creditor with:

- `user_id`
- amount in cents

The `@dataclass` decorator reduces boilerplate code and makes the helper class clean.

## Expense Recording Logic

When an expense is recorded:

1. Duplicate expense ids are ignored.
2. Invalid input is ignored.
3. All members must already be registered.
4. Paid amounts are converted into cents.
5. The total amount is split equally.
6. If the amount cannot be divided exactly, the remaining cents are given to lexicographically smaller user ids.
7. Each user's net balance is updated as:

```text
net change = amount paid - fair share
```

So:

- If a user paid more than their share, their net balance increases.
- If a user paid less than their share, their net balance decreases.

## Why Distribute Remaining Cents Lexicographically?

Money may not always split perfectly.

For example, if `10.00` is split among `3` users:

```text
1000 cents / 3 = 333 cents each
remainder = 1 cent
```

One user must pay one extra cent.

To keep the result deterministic, this solution gives extra cents to users with lexicographically smaller user ids.

That means the same input will always produce the same output.

## Balance Listing Logic

When `listBalances()` is called:

1. Users with negative net balance become debtors.
2. Users with positive net balance become creditors.
3. Debtors and creditors are sorted by user id.
4. A greedy two-pointer matching is used.
5. Each debtor is matched with creditors until their debt becomes zero.

The output is formatted like this:

```text
user1 owes user2: 10.50
```

## Why Greedy Matching Works Here

The total amount owed by all debtors is equal to the total amount receivable by all creditors.

So we can greedily match the smallest debtor id with the smallest creditor id, settle as much as possible, and then move forward.

This produces a clean and deterministic list of balances.

## Code

```python
from dataclasses import dataclass
from typing import List, Dict, Set


@dataclass
class _Entry:
    # Helper class for matching debtors and creditors in listBalances()
    user_id: str
    cents: int  # non-negative amount in cents


class SplitBook:
    """
    SplitBook is a lightweight Splitwise-like tracker.

    Core idea:
    - Store only the net balance of each user.
    - Positive balance means the user should receive money.
    - Negative balance means the user owes money.
    - Use integer cents to avoid floating-point precision errors.
    """

    def __init__(self):
        # Stores all registered user ids
        self._users: Set[str] = set()

        # Net balance per user in cents
        # Positive value => user should receive money
        # Negative value => user owes money
        self._net_cents: Dict[str, int] = {}

        # Stores processed expense ids to avoid duplicate expense recording
        self._seen_expense_ids: Set[int] = set()

    def registerUser(self, userId: str, displayName: str):
        # Idempotent user registration.
        # If the same user is registered again, nothing changes.
        if userId is None:
            return

        if userId not in self._users:
            self._users.add(userId)

            # Initialize user's net balance with 0 cents
            if userId not in self._net_cents:
                self._net_cents[userId] = 0

    def recordExpense(self, expenseId: int, members: List[str], paid: List[int]):
        # Ignore duplicate expense ids
        if expenseId in self._seen_expense_ids:
            return

        self._seen_expense_ids.add(expenseId)

        # Basic validation
        if not members or not paid or len(members) != len(paid):
            return

        n = len(members)

        # All members must be registered before recording the expense
        for uid in members:
            if uid not in self._users:
                return

        # Convert paid amounts to cents and validate the input
        total_cents = 0
        any_positive = False
        paid_cents = [0] * n

        for i, amt in enumerate(paid):
            if amt is None or amt < 0:
                return  # Invalid expense, so ignore it completely

            if amt > 0:
                any_positive = True

            cents = amt * 100
            paid_cents[i] = cents
            total_cents += cents

        # At least one member should have paid something
        if not any_positive:
            return

        # Split total amount equally among all members
        share_floor = total_cents // n
        remainder = total_cents % n

        # Extra 1-cent shares are given to lexicographically smallest user ids
        indices = list(range(n))
        indices.sort(key=lambda i: members[i])

        plus_one = set(indices[:remainder])

        # Update each user's net balance
        for i, uid in enumerate(members):
            fair_share = share_floor + (1 if i in plus_one else 0)

            # If paid more than fair share, user should receive money.
            # If paid less than fair share, user owes money.
            delta = paid_cents[i] - fair_share

            self._net_cents[uid] = self._net_cents.get(uid, 0) + delta

    def listBalances(self) -> List[str]:
        # Separate users into debtors and creditors
        debtors: List[_Entry] = []
        creditors: List[_Entry] = []

        for uid, value in self._net_cents.items():
            if value == 0:
                continue

            if value < 0:
                # Negative net balance means this user owes money
                debtors.append(_Entry(uid, -value))
            else:
                # Positive net balance means this user should receive money
                creditors.append(_Entry(uid, value))

        # Sort by user id to keep output deterministic
        debtors.sort(key=lambda entry: entry.user_id)
        creditors.sort(key=lambda entry: entry.user_id)

        result: List[str] = []

        # Greedily match debtors with creditors
        i = 0
        j = 0

        while i < len(debtors) and j < len(creditors):
            debtor = debtors[i]
            creditor = creditors[j]

            pay = min(debtor.cents, creditor.cents)

            result.append(self._format_line(debtor.user_id, creditor.user_id, pay))

            debtor.cents -= pay
            creditor.cents -= pay

            if debtor.cents == 0:
                i += 1

            if creditor.cents == 0:
                j += 1

        return result

    @staticmethod
    def _format_line(debtor: str, creditor: str, cents: int) -> str:
        # Format as "<debtor> owes <creditor>: X.YY"
        units = cents // 100
        pennies = cents % 100

        return f"{debtor} owes {creditor}: {units}.{pennies:02d}"
```

## Complexity Analysis

Let:

```text
n = number of members in one expense
u = number of registered users
d = number of debtors
c = number of creditors
```

### `registerUser()`

```text
Time Complexity: O(1)
Space Complexity: O(1)
```

User registration uses set and dictionary operations, which are average `O(1)`.

### `recordExpense()`

```text
Time Complexity: O(n log n)
Space Complexity: O(n)
```

The main cost comes from sorting members to distribute the remaining cents deterministically.

### `listBalances()`

```text
Time Complexity: O(u log u)
Space Complexity: O(u)
```

We scan all users, split them into debtors and creditors, sort them, and then greedily match balances.

## Final Thoughts

This solution keeps the design simple by using a single net balance per user instead of storing every transaction separately.

The most important design choices are:

- Use integer cents instead of floating-point numbers.
- Maintain net balance per user.
- Ignore duplicate expenses.
- Distribute extra cents deterministically.
- Use greedy matching to generate final settlement lines.

This makes the solution easy to implement, easy to test, and suitable for a low-level design machine coding round.
