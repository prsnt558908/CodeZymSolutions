from dataclasses import dataclass
from typing import List, Dict, Set

@dataclass
class _Entry:
    # helper for matching in listBalances()
    user_id: str
    cents: int  # non-negative


class SplitBook:
    """
    SplitBook — lightweight Splitwise-like tracker with full internal netting.

    Implementation details:
    - All arithmetic is done in integer cents (avoid floating errors).
    - For each expense, the total is split as evenly as possible:
        share_floor = total_cents // n
        remainder   = total_cents % n
      The extra 1-cent remainders are given to the lexicographically smallest userIds
      among the members (deterministic, stable).
    - net_cents[user] keeps a running net:
        > 0  => user is owed (creditor)
        < 0  => user owes   (debtor)
    - listBalances() greedily matches debtors to creditors in lexicographic order and
      emits lines already sorted by (debtor_id, creditor_id).
    """

    def __init__(self):
        # Registered user ids
        self._users: Set[str] = set()
        # Net balance per user in cents: +ve => owed, -ve => owes
        self._net_cents: Dict[str, int] = {}
        # Deduplicate expense ids
        self._seen_expense_ids: Set[int] = set()

    def registerUser(self, userId: str, displayName: str):
        # Idempotent user registration
        if userId is None:
            return
        if userId not in self._users:
            self._users.add(userId)
            # Initialize net balance to 0 cents
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

        # All members must be registered
        for uid in members:
            if uid not in self._users:
                return

        # Convert payments to cents and validate
        total_cents = 0
        any_positive = False
        paid_cents = [0] * n
        for i, amt in enumerate(paid):
            if amt is None or amt < 0:
                return  # invalid, ignore entire expense
            if amt > 0:
                any_positive = True
            c = amt * 100  # integer cents
            paid_cents[i] = c
            total_cents += c

        if not any_positive:
            return  # spec: at least one paid > 0

        # Even split with deterministic remainder distribution
        share_floor = total_cents // n
        remainder = total_cents % n

        # Determine indices that get +1 cent (lexicographically smallest userIds)
        idx = list(range(n))
        idx.sort(key=lambda i: members[i])

        plus_one = set(idx[:remainder])

        # Apply deltas to each member's net: paid - fair_share
        for i, uid in enumerate(members):
            share = share_floor + (1 if i in plus_one else 0)
            delta = paid_cents[i] - share  # +ve => they are owed; -ve => they owe
            self._net_cents[uid] = self._net_cents.get(uid, 0) + delta

    def listBalances(self) -> List[str]:
        # Partition into debtors (negative) and creditors (positive)
        debtors: List[_Entry] = []
        creditors: List[_Entry] = []
        for uid, v in self._net_cents.items():
            if v == 0:
                continue
            if v < 0:
                debtors.append(_Entry(uid, -v))   # amount owed
            else:
                creditors.append(_Entry(uid, v))  # amount to receive

        # Sort lexicographically by user id
        debtors.sort(key=lambda e: e.user_id)
        creditors.sort(key=lambda e: e.user_id)

        # Greedy matching produces lines already sorted by (debtor, creditor)
        out: List[str] = []
        i = j = 0
        while i < len(debtors) and j < len(creditors):
            d = debtors[i]
            c = creditors[j]
            pay = min(d.cents, c.cents)
            out.append(self._format_line(d.user_id, c.user_id, pay))
            d.cents -= pay
            c.cents -= pay
            if d.cents == 0:
                i += 1
            if c.cents == 0:
                j += 1

        return out

    @staticmethod
    def _format_line(debtor: str, creditor: str, cents: int) -> str:
        # Format as "<debtor> owes <creditor>: X.YY"
        units = cents // 100
        pennies = cents % 100
        return f"{debtor} owes {creditor}: {units}.{pennies:02d}"
