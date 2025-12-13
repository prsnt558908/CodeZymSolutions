import java.util.HashMap;
import java.util.Map;

public class PaymentWallet {

    private static class FixedDeposit {
        int thresholdAmount;   // "amount" passed at FD creation
        int debitCount;        // number of successful debit txns counted
        boolean active;        // true until it either completes or fails

        FixedDeposit(int thresholdAmount) {
            this.thresholdAmount = thresholdAmount;
            this.debitCount = 0;
            this.active = true;
        }
    }

    private static class UserAccount {
        int balance;
        FixedDeposit fixedDeposit;
    }

    private final Map<String, UserAccount> users;

    public PaymentWallet() {
        this.users = new HashMap<>();
    }

    public void registerUser(String userId) {
        // userId is guaranteed to be non-blank and unique as per problem statement.
        // Still guard against duplicates to be safe.
        if (!users.containsKey(userId)) {
            UserAccount account = new UserAccount();
            account.balance = 0;
            account.fixedDeposit = null;
            users.put(userId, account);
        }
    }

    public String addMoneyToWallet(String userId, int amount) {
        UserAccount user = users.get(userId);
        if (user == null) {
            return "user does not exist";
        }

        user.balance += amount;
        // Adding money is not a "debit transaction", so it does not affect FD.
        return "success";
    }

    public String spendMoney(String userId, int amount) {
        UserAccount user = users.get(userId);
        if (user == null) {
            return "user does not exist";
        }
        if (user.balance < amount) {
            return "insufficient balance";
        }

        // Successful debit
        user.balance -= amount;
        handleSuccessfulDebit(user);
        return "success";
    }

    public String transferMoney(String fromUser, String toUser, int amount) {
        UserAccount from = users.get(fromUser);
        if (from == null) {
            return "sender does not exist";
        }

        UserAccount to = users.get(toUser);
        if (to == null) {
            return "receiver does not exist";
        }

        if (from.balance < amount) {
            return "insufficient balance";
        }

        // Successful debit for fromUser
        from.balance -= amount;
        handleSuccessfulDebit(from);

        // Credit to toUser (not a debit for them)
        to.balance += amount;

        return "success";
    }

    public String createFixedDeposit(String userId, int amount) {
        UserAccount user = users.get(userId);
        if (user == null) {
            return "user does not exist";
        }

        // User must have at least 'amount' balance at the time of FD creation
        if (user.balance < amount) {
            return "insufficient balance";
        }

        // Only one active FD at a time for this user
        if (user.fixedDeposit != null && user.fixedDeposit.active) {
            return "an active fixed deposit already exists";
        }

        // Create new FD (amount acts as threshold; not deducted)
        user.fixedDeposit = new FixedDeposit(amount);
        return "success";
    }

    public int getAccountBalance(String userId) {
        UserAccount user = users.get(userId);
        if (user == null) {
            return -1;
        }
        return user.balance;
    }

    /**
     * Called after each successful debit transaction (spendMoney / transferMoney as fromUser).
     * Applies the FD rules for that user if an FD is active.
     */
    private void handleSuccessfulDebit(UserAccount user) {
        FixedDeposit fd = user.fixedDeposit;
        if (fd == null || !fd.active) {
            return; // No active FD, nothing to do
        }

        // Check if FD breaks due to balance going below threshold
        if (user.balance < fd.thresholdAmount) {
            // FD fails/breaks: no interest added
            fd.active = false;
            return;
        }

        // Count this successful debit
        fd.debitCount++;

        // After 5 successful debits, if never broken and balance >= threshold, credit interest
        if (fd.debitCount >= 5) {
            double interestExact = fd.thresholdAmount * 0.05; // 5%
            int interest = (int) Math.floor(interestExact + 0.5); // round to nearest integer
            user.balance += interest;

            // FD has successfully completed
            fd.active = false;
        }
    }
}
