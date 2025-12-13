from dataclasses import dataclass
from typing import Dict, Optional
import math


@dataclass
class FixedDeposit:
    threshold_amount: int  # "amount" passed at FD creation
    debit_count: int = 0   # number of successful debit txns counted
    active: bool = True    # true until it either completes or fails


@dataclass
class UserAccount:
    balance: int = 0
    fixed_deposit: Optional[FixedDeposit] = None


class PaymentWallet:
    def __init__(self):
        # Map from userId to UserAccount
        self.users: Dict[str, UserAccount] = {}

    def registerUser(self, userId):
        # userId is guaranteed to be non-blank and unique as per problem statement.
        # Still guard against duplicates to be safe.
        if userId not in self.users:
            self.users[userId] = UserAccount()

    def addMoneyToWallet(self, userId, amount):
        user = self.users.get(userId)
        if user is None:
            return "user does not exist"

        user.balance += amount
        # Adding money is not a "debit transaction", so it does not affect FD.
        return "success"

    def spendMoney(self, userId, amount):
        user = self.users.get(userId)
        if user is None:
            return "user does not exist"
        if user.balance < amount:
            return "insufficient balance"

        # Successful debit
        user.balance -= amount
        self._handle_successful_debit(user)
        return "success"

    def transferMoney(self, fromUser, toUser, amount):
        from_account = self.users.get(fromUser)
        if from_account is None:
            return "sender does not exist"

        to_account = self.users.get(toUser)
        if to_account is None:
            return "receiver does not exist"

        if from_account.balance < amount:
            return "insufficient balance"

        # Successful debit for fromUser
        from_account.balance -= amount
        self._handle_successful_debit(from_account)

        # Credit to toUser (not a debit for them)
        to_account.balance += amount

        return "success"

    def createFixedDeposit(self, userId, amount):
        user = self.users.get(userId)
        if user is None:
            return "user does not exist"

        # User must have at least 'amount' balance at the time of FD creation
        if user.balance < amount:
            return "insufficient balance"

        # Only one active FD at a time for this user
        if user.fixed_deposit is not None and user.fixed_deposit.active:
            return "an active fixed deposit already exists"

        # Create new FD (amount acts as threshold; not deducted)
        user.fixed_deposit = FixedDeposit(threshold_amount=amount)
        return "success"

    def getAccountBalance(self, userId):
        user = self.users.get(userId)
        if user is None:
            return -1
        return user.balance

    def _handle_successful_debit(self, user: UserAccount):
        """
        Called after each successful debit transaction (spendMoney / transferMoney as fromUser).
        Applies the FD rules for that user if an FD is active.
        """
        fd = user.fixed_deposit
        if fd is None or not fd.active:
            return  # No active FD, nothing to do

        # Check if FD breaks due to balance going below threshold
        if user.balance < fd.threshold_amount:
            # FD fails/breaks: no interest added
            fd.active = False
            return

        # Count this successful debit
        fd.debit_count += 1

        # After 5 successful debits, if never broken and balance >= threshold, credit interest
        if fd.debit_count >= 5:
            interest_exact = fd.threshold_amount * 0.05  # 5%
            interest = math.floor(interest_exact + 0.5)  # round to nearest integer
            user.balance += interest

            # FD has successfully completed
            fd.active = False
