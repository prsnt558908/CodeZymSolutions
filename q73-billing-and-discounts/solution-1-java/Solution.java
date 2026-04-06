import java.util.*;

public class BillingSystem {

    private static final String ERROR = "ERROR";

    private enum Level {
        BRONZE, SILVER, GOLD, PLATINUM
    }

    private static class Bill {
        final String billId;
        final String customerId;
        final long subtotal;
        boolean paid;
        final Set<String> appliedCodes; // idempotent per code

        Bill(String billId, String customerId, long subtotal) {
            this.billId = billId;
            this.customerId = customerId;
            this.subtotal = subtotal;
            this.paid = false;
            this.appliedCodes = new HashSet<>();
        }
    }

    private long nextBillNum;
    private final Map<String, Bill> billsById;
    private final Map<String, Long> customerPoints;

    public BillingSystem() {
        this.nextBillNum = 1;
        this.billsById = new HashMap<>();
        this.customerPoints = new HashMap<>();
    }

    public String createBill(String customerId, List<String> cartItems) {
        if (customerId == null || customerId.isEmpty()) return ERROR;
        if (cartItems == null || cartItems.isEmpty()) return ERROR;

        long subtotal = 0;
        for (String s : cartItems) {
            CartItem parsed = parseCartItem(s);
            if (parsed == null) return ERROR;
            // subtotal fits in 64-bit signed long as per constraints
            subtotal += (long) parsed.unitPrice * (long) parsed.quantity;
        }

        String billId = "B" + nextBillNum++;
        // initialize customer points if new
        customerPoints.putIfAbsent(customerId, 0L);

        billsById.put(billId, new Bill(billId, customerId, subtotal));
        return billId;
    }

    public long applyDiscount(String billId, String discountCode) {
        Bill bill = billsById.get(billId);
        if (bill == null || bill.paid) return -1;

        if (discountCode != null && isSupportedCode(discountCode)) {
            bill.appliedCodes.add(discountCode); // idempotent via Set
        }
        // unknown code: ignore, still return current payable
        return computePayable(bill).finalPayable;
    }

    public String payBill(String billId, long amountPaid) {
        Bill bill = billsById.get(billId);
        if (bill == null || bill.paid) return ERROR;

        PayableResult pr = computePayable(bill);
        long payable = pr.finalPayable;

        if (amountPaid != payable) return ERROR;

        // success: transition to paid
        bill.paid = true;

        long currentPoints = customerPoints.getOrDefault(bill.customerId, 0L);

        // deduct redeemed points only on successful payment
        if (bill.appliedCodes.contains("REDEEM")) {
            long toDeduct = Math.min(currentPoints, pr.redeemUsed);
            currentPoints -= toDeduct;
            if (currentPoints < 0) currentPoints = 0; // safety
        }

        long pointsEarned = payable / 100L; // floor
        long totalPoints = currentPoints + pointsEarned;
        customerPoints.put(bill.customerId, totalPoints);

        Level level = levelFromPoints(totalPoints);

        return "PAID|final=" + payable
                + "|pointsEarned=" + pointsEarned
                + "|totalPoints=" + totalPoints
                + "|level=" + level.name();
    }

    // ----------------- Helpers -----------------

    private static class CartItem {
        final int unitPrice;
        final int quantity;

        CartItem(int unitPrice, int quantity) {
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }
    }

    private static CartItem parseCartItem(String s) {
        if (s == null) return null;
        String[] parts = s.split("\\|", -1);
        if (parts.length != 3) return null;

        String itemName = parts[0];
        if (itemName == null || itemName.isEmpty()) return null;

        int unitPrice;
        int quantity;
        try {
            unitPrice = Integer.parseInt(parts[1]);
            quantity = Integer.parseInt(parts[2]);
        } catch (Exception e) {
            return null;
        }

        if (unitPrice < 0) return null;
        if (quantity <= 0) return null;

        return new CartItem(unitPrice, quantity);
    }

    private static boolean isSupportedCode(String code) {
        return "P10".equals(code) || "P20".equals(code) || "FLAT100".equals(code) || "REDEEM".equals(code);
    }

    private static class PayableResult {
        final long finalPayable;
        final long redeemUsed;

        PayableResult(long finalPayable, long redeemUsed) {
            this.finalPayable = finalPayable;
            this.redeemUsed = redeemUsed;
        }
    }

    private PayableResult computePayable(Bill bill) {
        long subtotal = bill.subtotal;

        // effective percentage: max(P10, P20) if present
        int percent = 0;
        if (bill.appliedCodes.contains("P10")) percent = Math.max(percent, 10);
        if (bill.appliedCodes.contains("P20")) percent = Math.max(percent, 20);

        long percentDiscount = (subtotal * percent) / 100L; // integer floor
        long payable = subtotal - percentDiscount;

        // apply FLAT100 only if subtotal >= 500
        if (bill.appliedCodes.contains("FLAT100") && subtotal >= 500L) {
            payable -= 100L;
        }

        if (payable < 0) payable = 0;

        long redeemUsed = 0;
        if (bill.appliedCodes.contains("REDEEM")) {
            long cap = (payable * 20L) / 100L; // floor
            long points = customerPoints.getOrDefault(bill.customerId, 0L);
            redeemUsed = Math.min(points, cap);
            payable -= redeemUsed;
            if (payable < 0) payable = 0;
        }

        return new PayableResult(payable, redeemUsed);
    }

    private static Level levelFromPoints(long points) {
        if (points >= 2000L) return Level.PLATINUM;
        if (points >= 500L) return Level.GOLD;
        if (points >= 100L) return Level.SILVER;
        return Level.BRONZE;
    }
}
