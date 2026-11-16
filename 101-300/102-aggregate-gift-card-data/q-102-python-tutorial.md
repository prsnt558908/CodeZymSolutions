# Aggregate Gift Card Data in Python

#### Problem Statement
[https://codezym.com/question/102-aggregate-gift-card-data](https://codezym.com/question/102-aggregate-gift-card-data)

## Core Idea

We need a small system that stores users with their gift cards, takes payments from those cards, and lists a user's cards sorted by balance.

Storing the data is easy: users and their gift cards live in dicts (hash maps), so every lookup is fast.

The only tricky rule is that a payment is **all or nothing**. If even one card fails, no balance should change. We handle this by first trying the payment on a **draft** copy of the balances. Only when every card passes do we save the new balances.

No design pattern is needed here. Plain classes and dicts are enough, and we will see below why patterns like Command or Memento would only add extra code.

## Storing Users and Gift Cards

We use two small classes and one dict:

- **`GiftCard`** holds a card's `id` and its current `balance`. One object per card makes updating and sorting cards easy.
- **`User`** holds `giftCards`, a dict of `giftCardId -> GiftCard`. Finding a card is O(1). The same dict is also our ownership check: if a card id is not in this dict, the card does not belong to this user.
- **`users`** inside `GiftCardSystem` is a dict of `userId -> User`. It finds a user in O(1) and tells us if the user already exists, so a repeated `addUser` call is simply ignored.

`addUser` splits each `"giftCardId,balance"` string at the comma and fills these dicts.

It also skips a blank `userId`. Since a blank id is never stored, the other two methods can treat a blank user and an unknown user the same way: the user is just not in the dict.

## Making a Payment

This is the heart of the problem, so let's build it step by step.

In every version below, we first look up the user and return `False` if the user is missing.

### Attempt 1: Charge Each Card as We Go

The most natural idea is to walk through the list, check each card and take the money right away.

```python
for item in giftCardsUsed:
    parts = item.split(",")
    card = user.giftCards.get(parts[0])
    amount = int(parts[1])
    if card is None or card.balance < amount:
        return False  # too late, earlier cards were already charged
    card.balance -= amount
return True
```

**Problem:** say `gc1 = 100` and `gc2 = 40`, and the payment is `["gc1,25", "gc2,50"]`.

`gc1` drops to 75. Then `gc2` fails, because 40 is less than 50. We return `False`, but `gc1` has already lost 25. A failed payment must not change any balance.

### Attempt 2: Check Everything First, Then Charge

Let's split the work into two passes. The first pass only checks. The second pass only deducts.

```python
# Pass 1: only check
for item in giftCardsUsed:
    parts = item.split(",")
    card = user.giftCards.get(parts[0])
    if card is None or card.balance < int(parts[1]):
        return False
# Pass 2: all checks passed, now deduct
for item in giftCardsUsed:
    parts = item.split(",")
    user.giftCards[parts[0]].balance -= int(parts[1])
return True
```

Now a failed check never leaves a half done payment.

**Problem:** nothing stops the same card from appearing twice in one payment. Say `gc1 = 100` and the payment is `["gc1,60", "gc1,50"]`.

Each check passes on its own, because 60 and 50 are both at most 100. But together they take 110, so the balance ends at -10.

### Final Approach: Try the Payment on a Draft

We keep a small dict called `draft`: `giftCardId -> balance left after this payment`. It only holds the cards used in this payment.

Think of it as doing the math on scratch paper before touching the real cards. Inside the loop, after splitting each item into `cardId` and `amount`:

```python
card = user.giftCards.get(cardId)
if card is None:
    return False  # not this user's card
balance = draft.get(cardId, card.balance)
if balance < amount:
    return False  # not enough balance
draft[cardId] = balance - amount
```

The key line is `draft.get(cardId, card.balance)`. The first time we see a card, the draft has nothing for it, so we start from the real balance. If the same card shows up again, we continue from the draft balance, which already includes the earlier deduction.

Let's replay `gc1 = 100` with `["gc1,60", "gc1,50"]`:

- `"gc1,60"`: not in the draft yet, so start from 100. The draft becomes 40.
- `"gc1,50"`: the draft says 40, which is less than 50. Return `False`.

The real `gc1` is still 100.

If every item passes, we copy each draft balance to its real card. If we return `False` early, the draft is simply thrown away. The real cards were never touched, so there is nothing to undo.

## Aggregating Gift Card Data

`aggregateGiftCardData` copies the user's cards into a list and sorts it:

```python
cards.sort(key=lambda card: (-card.balance, card.id))
```

Python compares these pairs item by item. The minus sign makes higher balances come first. If two balances are equal, the id decides.

That tie break uses string order, not number order. So `"gc10"` comes before `"gc2"`.

Then each card becomes a `"giftCardId,balance"` row.

**Why not keep the cards always sorted?** We could keep each user's cards in a sorted list all the time. But then every payment would have to remove a card and add it back whenever its balance changes, which is easy to get wrong. A user has at most 100 cards, so sorting them only when asked is cheap and keeps the code simple.

## Do We Need a Design Pattern?

The rule "a failed payment changes nothing" can make two patterns look like a good fit:

- **Command:** wrap each deduction in an object with `execute()` and `undo()`. If a later card fails, undo the earlier ones.
- **Memento:** save a snapshot of all the user's balances before paying, and restore it if something fails.

Both patterns exist to **undo** changes. With the draft, we never change a real balance until every check has passed, so there is never anything to undo.

Either one would add extra classes and steps without making the solution better. Plain classes and dicts are the cleaner choice here.

## Complexity

Let `k` be the number of cards a user has (at most 100), and `m` be the number of items in a payment.

| Method | Time | Why |
|---|---|---|
| `addUser` | O(k) | one dict insert per card |
| `addPayment` | O(m) | one dict lookup per item |
| `aggregateGiftCardData` | O(k log k) | sorting the user's cards |

Space is O(total gift cards) for the dicts, plus O(m) for the draft during a payment.

## Python Code

```python
from typing import List


class GiftCard:
    """One gift card and its current balance."""

    def __init__(self, id: str, balance: int):
        self.id = id
        self.balance = balance


class User:
    """A user and all the gift cards they own."""

    def __init__(self, id: str):
        self.id = id
        # giftCardId -> GiftCard. Fast lookup, and it also tells us which cards belong to this user.
        self.giftCards = {}


class GiftCardSystem:
    def __init__(self):
        # userId -> User
        self.users = {}

    def addUser(self, userId: str, giftCards: List[str]) -> None:
        """Adds a new user with their gift cards. Each item is "giftCardId,balance"."""
        # ignore blank ids and users that already exist
        if userId is None or userId.strip() == "" or userId in self.users:
            return
        user = User(userId)
        for item in giftCards:
            parts = item.split(",")
            cardId = parts[0]
            balance = int(parts[1])
            user.giftCards[cardId] = GiftCard(cardId, balance)
        self.users[userId] = user

    def addPayment(self, userId: str, giftCardsUsed: List[str]) -> bool:
        """
        Pays using one or more of the user's gift cards. Each item is "giftCardId,amountUsed".
        All or nothing: if any check fails, no balance is changed.
        """
        user = self.users.get(userId)
        if user is None:
            return False  # blank or unknown user (blank ids are never added)

        # Step 1: try the payment on a draft. giftCardId -> balance left after this payment.
        # Real balances are not touched in this step.
        draft = {}
        for item in giftCardsUsed:
            parts = item.split(",")
            cardId = parts[0]
            amount = int(parts[1])

            card = user.giftCards.get(cardId)
            if card is None:
                return False  # card does not exist or belongs to another user
            # if this card was already used earlier in this payment, continue from its draft balance
            balance = draft.get(cardId, card.balance)
            if balance < amount:
                return False  # not enough balance
            draft[cardId] = balance - amount

        # Step 2: every check passed, so save the draft balances to the real cards.
        for cardId, balance in draft.items():
            user.giftCards[cardId].balance = balance
        return True

    def aggregateGiftCardData(self, userId: str) -> List[str]:
        """
        Returns the user's gift cards as "giftCardId,balance".
        Highest balance first. Equal balances are ordered by giftCardId.
        """
        user = self.users.get(userId)
        if user is None:
            return []  # blank or unknown user

        cards = list(user.giftCards.values())
        # the minus sign puts higher balances first, then smaller giftCardId comes first
        cards.sort(key=lambda card: (-card.balance, card.id))
        return [f"{card.id},{card.balance}" for card in cards]
```