import java.util.*;

public class TradingService {

    // Preserve insertion order for showStocks()
    private final LinkedHashMap<String, Integer> stocks = new LinkedHashMap<>();
    private final Map<String, User> users = new HashMap<>();
    // Track OPEN LIMIT orders per stock for instant re-eval on price updates
    private final Map<String, List<OrderRef>> openLimitByStock = new HashMap<>();

    public TradingService() { }

    // Create a user with an initial cash balance
    public void addUser(String userId, int openingCash) {
        users.putIfAbsent(userId, new User(userId, openingCash));
    }

    // Fetch user's current account balance
    public int getAccountBalance(String userId) {
        User u = users.get(userId);
        return (u == null) ? 0 : u.cash;
    }

    // Add/update a stock price; on update, auto-fill favorable OPEN LIMIT orders
    public void addStock(String stockName, int stockPrice) {
        stocks.putIfAbsent(stockName, stockPrice); // preserve first-add order
        stocks.put(stockName, stockPrice);         // update latest price
        autoFillOpenLimits(stockName, stockPrice);
    }

    // Show all stocks as "name price" in first-added order
    public List<String> showStocks() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : stocks.entrySet()) {
            out.add(e.getKey() + " " + e.getValue());
        }
        return out;
    }

    // Place an order; returns FILLED, OPEN, or REJECTED
    public String submitOrder(String userId, String stockName, String orderKind, String action, int qty, int limitPrice) {
        User user = users.get(userId);
        Integer currentPrice = stocks.get(stockName);

        // Basic existence/qty check; if missing, reject (record if user exists)
        if (user == null) {
            return Status.REJECTED.name();
        }
        if (currentPrice == null || qty <= 0) {
            OrderKind kind = OrderKind.valueOf(orderKind);
            Action act = Action.valueOf(action);
            Order ord = new Order(stockName, act, qty, kind, Status.REJECTED, limitPrice);
            user.orders.add(ord);
            return ord.status.name();
        }

        OrderKind kind = OrderKind.valueOf(orderKind);
        Action act = Action.valueOf(action);
        int priceAtSubmit = currentPrice;

        if (kind == OrderKind.MARKET) {
            return handleMarket(user, stockName, act, qty, priceAtSubmit);
        } else { // LIMIT
            return handleLimit(user, stockName, act, qty, limitPrice, priceAtSubmit);
        }
    }

    // Show holdings for a user as "stockName count" in the order first acquired (ever)
    public List<String> viewStockHoldings(String userId) {
        User user = users.get(userId);
        if (user == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String sym : user.firstAcquired) { // stable “first-ever” acquisition order
            Holding h = user.holdings.get(sym);
            if (h != null && h.qty > 0) {
                out.add(sym + " " + h.qty);
            }
        }
        return out;
    }

    // Show order history as "stockName action quantity orderKind status"
    public List<String> viewOrders(String userId) {
        User user = users.get(userId);
        if (user == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Order o : user.orders) {
            out.add(o.stockName + " " + o.action.name() + " " + o.qty + " " + o.kind.name() + " " + o.status.name());
        }
        return out;
    }

    // --- Helpers ------------------------------------------------------------

    private String handleMarket(User user, String stock, Action act, int qty, int priceAtSubmit) {
        Status status;
        if (act == Action.BUY) {
            long cost = 1L * qty * priceAtSubmit;
            if (user.cash >= cost) {
                applyBuy(user, stock, qty, priceAtSubmit);
                status = Status.FILLED;
            } else {
                status = Status.REJECTED;
            }
        } else { // SELL
            int held = user.getHoldingQty(stock);
            if (held >= qty) {
                applySell(user, stock, qty, priceAtSubmit);
                status = Status.FILLED;
            } else {
                status = Status.REJECTED;
            }
        }
        Order ord = new Order(stock, act, qty, OrderKind.MARKET, status, 0);
        user.orders.add(ord);
        return status.name();
    }

    private String handleLimit(User user, String stock, Action act, int qty, int limitPrice, int priceAtSubmit) {
        Status status;

        if (act == Action.BUY) {
            // Validation requires enough cash at CURRENT price even if not favorable
            long costAtCurrent = 1L * qty * priceAtSubmit;
            if (user.cash < costAtCurrent) {
                status = Status.REJECTED;
            } else if (priceAtSubmit <= limitPrice) {
                applyBuy(user, stock, qty, priceAtSubmit);
                status = Status.FILLED;
            } else {
                status = Status.OPEN;
            }
        } else { // SELL
            int held = user.getHoldingQty(stock);
            if (held < qty) {
                status = Status.REJECTED;
            } else if (priceAtSubmit >= limitPrice) {
                applySell(user, stock, qty, priceAtSubmit);
                status = Status.FILLED;
            } else {
                status = Status.OPEN;
            }
        }

        Order ord = new Order(stock, act, qty, OrderKind.LIMIT, status, limitPrice);
        user.orders.add(ord);
        if (status == Status.OPEN) {
            openLimitByStock.computeIfAbsent(stock, k -> new ArrayList<>())
                            .add(new OrderRef(user.userId, ord));
        }
        return status.name();
    }

    // Auto-fill OPEN LIMIT orders for this stock if the new price is favorable
    private void autoFillOpenLimits(String stockName, int newPrice) {
        List<OrderRef> refs = openLimitByStock.get(stockName);
        if (refs == null || refs.isEmpty()) return;

        Iterator<OrderRef> it = refs.iterator();
        while (it.hasNext()) {
            OrderRef ref = it.next();
            User user = users.get(ref.userId);
            Order ord = ref.order;
            if (user == null || ord.status != Status.OPEN || ord.kind != OrderKind.LIMIT) {
                it.remove();
                continue;
            }

            boolean favorable = (ord.action == Action.BUY) ? (newPrice <= ord.limitPrice)
                                                           : (newPrice >= ord.limitPrice);
            if (!favorable) continue;

            if (ord.action == Action.BUY) {
                long cost = 1L * ord.qty * newPrice;
                if (user.cash >= cost) {
                    applyBuy(user, stockName, ord.qty, newPrice);
                    ord.status = Status.FILLED;
                    it.remove();
                }
                // else: keep OPEN (insufficient cash at fill time)
            } else { // SELL
                int held = user.getHoldingQty(stockName);
                if (held >= ord.qty) {
                    applySell(user, stockName, ord.qty, newPrice);
                    ord.status = Status.FILLED;
                    it.remove();
                }
                // else: keep OPEN (insufficient qty at fill time)
            }
        }
        if (refs.isEmpty()) openLimitByStock.remove(stockName);
    }

    private void applyBuy(User user, String stock, int qty, int price) {
        long cost = 1L * qty * price;
        user.cash -= (int) cost;

        Holding h = user.holdings.computeIfAbsent(stock, s -> new Holding());
        // Record first-ever acquisition order (stable, even if later sold to zero)
        if (!user.firstAcquired.contains(stock)) {
            user.firstAcquired.add(stock);
        }

        long totalCostBefore = 1L * h.avgPrice * h.qty;
        int newQty = h.qty + qty;
        int newAvg = (int) ((totalCostBefore + cost) / newQty);
        h.qty = newQty;
        h.avgPrice = newAvg;
    }

    private void applySell(User user, String stock, int qty, int price) {
        long proceeds = 1L * qty * price;
        user.cash += (int) proceeds;
        Holding h = user.holdings.get(stock);
        if (h == null || h.qty < qty) return; // should not happen after validation
        h.qty -= qty;
        if (h.qty == 0) {
            user.holdings.remove(stock);
            // do NOT remove from firstAcquired: order must remain “first-ever”
        }
        // avgPrice remains for remaining shares
    }

    // --- Data types ---------------------------------------------------------

    private enum OrderKind { MARKET, LIMIT }
    private enum Action { BUY, SELL }
    private enum Status { FILLED, OPEN, REJECTED }

    private static final class Holding {
        int qty = 0;
        int avgPrice = 0; // maintained for completeness
    }

    private static final class Order {
        final String stockName;
        final Action action;
        final int qty;
        final OrderKind kind;
        Status status;
        final int limitPrice; // for OPEN limits; 0 for MARKET

        Order(String stockName, Action action, int qty, OrderKind kind, Status status, int limitPrice) {
            this.stockName = stockName;
            this.action = action;
            this.qty = qty;
            this.kind = kind;
            this.status = status;
            this.limitPrice = limitPrice;
        }
    }

    private static final class User {
        final String userId;
        int cash;

        // Current holdings by symbol (contents change as user trades)
        final Map<String, Holding> holdings = new HashMap<>();

        // Stable “first-ever acquisition” order per symbol for this user
        final LinkedHashSet<String> firstAcquired = new LinkedHashSet<>();

        // Orders in placement order
        final List<Order> orders = new ArrayList<>();

        User(String userId, int openingCash) {
            this.userId = userId;
            this.cash = openingCash;
        }

        int getHoldingQty(String stock) {
            Holding h = holdings.get(stock);
            return (h == null) ? 0 : h.qty;
        }
    }

    private static final class OrderRef {
        final String userId;
        final Order order;

        OrderRef(String userId, Order order) {
            this.userId = userId;
            this.order = order;
        }
    }
}
