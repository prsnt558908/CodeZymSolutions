import java.time.LocalDate;
import java.util.*;

/**
 * In-memory CostExplorer that tracks:
 * 1) products + plans (monthly prices)
 * 2) customer subscriptions to a product plan starting from a date
 *
 * Rules recap:
 * - addProduct overwrites the entire plan list for that product.
 * - If a plan is removed later, its price becomes 0 for all subscribers.
 * - If subscription starts on any day of a month, customer pays full month.
 * - monthlyCost(customer, year) returns 12-month costs for that calendar year.
 * - annualCost is the sum of those 12 months.
 */
public class CostExplorer {

    private static class Subscription {
        LocalDate startDate;
        String planId;

        Subscription(LocalDate startDate, String planId) {
            this.startDate = startDate;
            this.planId = planId;
        }
    }

    // productName -> (planId -> monthlyPrice)
    private final Map<String, Map<String, Integer>> productPlans;

    // customerId -> (productName -> Subscription)
    private final Map<String, Map<String, Subscription>> customerSubscriptions;

    public CostExplorer() {
        this.productPlans = new HashMap<>();
        this.customerSubscriptions = new HashMap<>();
    }

    /**
     * Adds/updates a product and its plans.
     * plans are strings: "PLANID,monthlyPrice"
     * Re-adding same product overwrites all old plans.
     */
    public void addProduct(String productName, List<String> plans) {
        Map<String, Integer> newPlans = new HashMap<>();
        if (plans != null) {
            for (String p : plans) {
                if (p == null || p.isBlank()) continue;
                String[] parts = p.split(",");
                if (parts.length != 2) continue; // inputs said valid, but safe-guard
                String planId = parts[0].trim();
                int price = Integer.parseInt(parts[1].trim());
                newPlans.put(planId, price);
            }
        }
        // overwrite existing product's plans
        productPlans.put(productName, newPlans);
    }

    /**
     * Subscribe/update a customer's subscription to a product.
     * If already subscribed to same product, overwrite that subscription
     * (startDate & planId).
     */
    public void subscribe(String customerId, String startDate, String productName, String planId) {
        LocalDate sd = LocalDate.parse(startDate); // YYYY-MM-DD

        Map<String, Subscription> subsForCustomer =
                customerSubscriptions.computeIfAbsent(customerId, k -> new HashMap<>());

        subsForCustomer.put(productName, new Subscription(sd, planId));
    }

    /**
     * For given customer and year, compute cost per month.
     * Returns 12 integers: Jan..Dec.
     */
    public List<Integer> monthlyCost(String customerId, int year) {
        int[] monthly = new int[12];

        Map<String, Subscription> subsForCustomer = customerSubscriptions.get(customerId);
        if (subsForCustomer == null || subsForCustomer.isEmpty()) {
            return toList(monthly);
        }

        for (Map.Entry<String, Subscription> entry : subsForCustomer.entrySet()) {
            String productName = entry.getKey();
            Subscription sub = entry.getValue();

            int price = getMonthlyPrice(productName, sub.planId); // 0 if removed

            if (price == 0) {
                continue; // removed plan or product => no contribution
            }

            LocalDate sd = sub.startDate;
            int startYear = sd.getYear();

            if (startYear > year) {
                continue; // starts after this year => contributes 0
            }

            int startMonthIndex;
            if (startYear < year) {
                startMonthIndex = 0; // charge all months
            } else {
                startMonthIndex = sd.getMonthValue() - 1; // Jan=0
            }

            for (int m = startMonthIndex; m < 12; m++) {
                monthly[m] += price;
            }
        }

        return toList(monthly);
    }

    /**
     * Annual cost is sum of 12 monthly costs for that year.
     */
    public int annualCost(String customerId, int year) {
        List<Integer> months = monthlyCost(customerId, year);
        int sum = 0;
        for (int c : months) sum += c;
        return sum;
    }

    // ---- helpers ----

    private int getMonthlyPrice(String productName, String planId) {
        Map<String, Integer> plans = productPlans.get(productName);
        if (plans == null) return 0;
        Integer price = plans.get(planId);
        return price == null ? 0 : price;
    }

    private List<Integer> toList(int[] arr) {
        List<Integer> out = new ArrayList<>(12);
        for (int v : arr) out.add(v);
        return out;
    }
}
