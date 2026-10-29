# Design Food Order Management System using Commands in Java

#### Problem Statement
[https://codezym.com/question/74-design-food-order-management-system-commands](https://codezym.com/question/74-design-food-order-management-system-commands)


The commands can come in any time order. So we first turn every text command into a small **command object** that remembers its timestamp and its position in the input. Then we sort these objects by timestamp, run them one by one and write each output back at the original position.

A combination of the **Command pattern** and the **Strategy pattern** works best here. Command objects can be stored, sorted and run later, which is exactly what this problem needs. The two restaurant selection rules become two small strategy classes. A tiny **Simple Factory** turns each text line into the right command object.


## Rules in Short

- Commands run in increasing timestamp order. Equal timestamps keep their input order.
- The output list follows the input order, not the run order.
- A restaurant can hold at most `capacity` open orders.
- An order goes to the best restaurant that has every item and a free slot.
- Dispatching an order frees one slot and adds its items to the served counts.


## Simple Solution: One Big Method

The first idea is to do everything inside `processCommands()`.

To run the commands in time order, we sort the input positions by timestamp. Equal timestamps are sorted by position, so the input order is kept.

```java
List<Integer> runOrder = new ArrayList<>();
for (int i = 0; i < commands.size(); i++) {
    runOrder.add(i);
}
runOrder.sort((x, y) -> {
    long tx = Long.parseLong(commands.get(x).split("\\|")[0]);
    long ty = Long.parseLong(commands.get(y).split("\\|")[0]);
    if (tx != ty) return Long.compare(tx, ty);
    return Integer.compare(x, y);
});
```

Then one long if-else chain handles every command type.

```java
String[] outputs = new String[commands.size()];
for (int i : runOrder) {
    String[] parts = commands.get(i).split("\\|", -1);
    String type = parts[1];

    if (type.equals("ADD_RESTAURANT")) {
        // check duplicate id, create restaurant, read the menu
    } else if (type.equals("UPDATE_MENU")) {
        // find the restaurant, add, update or remove items
    } else if (type.equals("PLACE_ORDER")) {
        // check strategy, scan restaurants, pick the best one
    } else if (type.equals("DISPATCH_ORDER")) {
        // find the order, free the slot, count served items
    }
}
```

Inside `PLACE_ORDER`, both strategies are mixed into the search loop.

```java
boolean better;
if (best == null) {
    better = true;
} else if (strategy.equals("LOWEST_TOTAL_PRICE")) {
    if (price != bestPrice) better = price < bestPrice;
    else if (free != bestFree) better = free > bestFree;
    else better = r.id.compareTo(best.id) < 0;
} else {
    if (free != bestFree) better = free > bestFree;
    else if (price != bestPrice) better = price < bestPrice;
    else better = r.id.compareTo(best.id) < 0;
}
```

This works, but it has real problems.

- `processCommands()` becomes a huge method. Sorting, text parsing and every business rule live in one place.
- A new command, like `CANCEL_ORDER`, means editing this long if-else chain.
- A new strategy means editing the search loop itself, which can break the old strategies.
- While sorting, the comparator splits the same text again and again.


## Better Solution: Command + Strategy Pattern

### Why Command pattern?

The Command pattern wraps a request inside an object. Once a request is an object, we can keep it in a list, sort it and run it later.

That is exactly what this problem asks. We read all commands first, sort them by time and run them after that.

Each command type gets its own small class with one `execute()` method. `FoodCart` does not know what a command does. It only sorts the commands and calls `execute()`.

Each text line is now split only once, when its command object is created.

Adding `CANCEL_ORDER` later means a new command class, a new `case` in the factory and a new method in `FoodOrderSystem`. The existing commands stay untouched.

### Why Strategy pattern?

Both strategies do the same job: compare two restaurants and pick the better one. Only the rule is different.

So the search loop is written once, and the comparison rule is plugged into it. Each rule lives in its own class.

We keep the strategies in a map from name to object. This also gives us the `INVALID_STRATEGY` check for free. If the name is not in the map, the strategy does not exist.

### Patterns that look useful but are not needed

**State pattern for orders.** An order is either open or dispatched. That is just two states and one move between them. A `dispatched` flag is much simpler than separate state classes.

**Observer pattern for dispatch.** On dispatch, we update the served counts and the dispatched list. Both live inside the same restaurant object. There is nobody outside to notify, so a direct method call is enough.

### Classes and their jobs

- **`FoodCart`**: the entry point. It turns text into commands, sorts them, runs them and places each output at its input position.
- **`CommandFactory`**: the Simple Factory. It splits one text line and creates the matching command object. All text parsing stays in this one class.
- **`Command`**: the base class. It stores `timestamp` and `position` and declares `execute()`.
- **`AddRestaurantCommand`**, **`UpdateMenuCommand`**, **`PlaceOrderCommand`**, **`DispatchOrderCommand`**: one class per command. Each one only calls the matching method of `FoodOrderSystem`.
- **`FoodOrderSystem`**: owns restaurants, orders and strategies. All business rules live here.
- **`Restaurant`**: its menu, capacity, open orders and what it has served.
- **`Order`**: one accepted order and the restaurant preparing it.
- **`RestaurantSelectionStrategy`**: the strategy interface with `isBetter()`. `LowestTotalPriceStrategy` and `MaxRemainingCapacityStrategy` implement it.

Why keep `FoodOrderSystem` apart from `FoodCart`? `FoodCart` decides **when** a command runs. `FoodOrderSystem` decides **what** it does. In Command pattern words, `FoodCart` is the invoker and `FoodOrderSystem` is the receiver.

### Important data structures

- **`restaurants`** (`Map<String, Restaurant>`): fast lookup by restaurant id.
- **`orders`** (`Map<String, Order>`): `DISPATCH_ORDER` only gives us an order id. Only accepted orders are saved, so dispatching a rejected order gives `INVALID_ORDER`.
- **`strategies`** (`Map<String, RestaurantSelectionStrategy>`): strategy name to strategy object.
- **`Restaurant.menu`** (`Map<String, Long>`): item to price. We use `long` because a price can be up to 10^9 and we add several prices together.
- **`Restaurant.servedCounts`** (`Map<String, Integer>`): item to how many times it was served. `getRestaurantItemCounts()` turns these into `"restaurantId|itemName|count"` rows and sorts the row strings, as the problem asks.
- **`Restaurant.dispatchedOrders`** (`List<String>`): a list keeps the rows in the exact order they were dispatched.

### How PLACE_ORDER works

1. Find the strategy by name. If it is missing, return `INVALID_STRATEGY`.
2. The problem does not say what happens when an order id repeats. We treat order ids as unique, so a repeated id gets `REJECTED`.
3. Look at every restaurant. Skip it if it is full or misses any item.
4. Find its total price and ask the strategy if it beats the best restaurant so far.
5. If no restaurant qualifies, return `REJECTED`. Otherwise add one open order to the winner, save the order and return `ACCEPTED`.

### One command, step by step

Let us follow `"210|PLACE_ORDER|O2|C2|LOWEST_TOTAL_PRICE|burger"` from the example. It sits at position 3 in the input.

1. `CommandFactory` splits the text and creates a `PlaceOrderCommand` with timestamp 210 and position 3.
2. After sorting, it runs after the commands at 100, 150, 200 and 205.
3. `execute()` calls `system.placeOrder("O2", "C2", "LOWEST_TOTAL_PRICE", [burger])`.
4. R1 sells burger for 120 and has 1 free slot. R2 sells it for 110 and also has 1 free slot.
5. `LowestTotalPriceStrategy` prefers the lower price, so R2 wins.
6. The output `ACCEPTED` is written to `outputs[3]`.

### Code

```java
import java.util.*;

/**
 * Entry point of the system.
 * It turns every text command into a command object,
 * runs the commands in timestamp order and
 * returns the outputs in the original input order.
 */
public class FoodCart {
    FoodOrderSystem system = new FoodOrderSystem();
    CommandFactory commandFactory = new CommandFactory();

    public FoodCart() {
    }

    public List<String> processCommands(List<String> commands) {
        List<Command> commandList = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            commandList.add(commandFactory.create(commands.get(i), i));
        }

        // smaller timestamp runs first. Same timestamp: keep the input order.
        commandList.sort((a, b) -> {
            if (a.timestamp != b.timestamp) {
                return Long.compare(a.timestamp, b.timestamp);
            }
            return Integer.compare(a.position, b.position);
        });

        // every output goes back to the input position of its command
        String[] outputs = new String[commands.size()];
        for (Command command : commandList) {
            outputs[command.position] = command.execute(system);
        }
        return new ArrayList<>(Arrays.asList(outputs));
    }

    public List<String> getRestaurantItemCounts() {
        return system.getRestaurantItemCounts();
    }

    public List<String> getDispatchedOrders(String restaurantId) {
        return system.getDispatchedOrders(restaurantId);
    }
}

/**
 * Simple Factory: reads one text command and creates the matching command object.
 */
class CommandFactory {

    Command create(String text, int position) {
        // -1 keeps empty fields, for example an empty menu at the end
        String[] parts = text.split("\\|", -1);
        long timestamp = Long.parseLong(parts[0]);

        switch (parts[1]) {
            case "ADD_RESTAURANT":
                return new AddRestaurantCommand(timestamp, position,
                        parts[2], Integer.parseInt(parts[3]), parseMenu(parts[4]));
            case "UPDATE_MENU":
                return new UpdateMenuCommand(timestamp, position,
                        parts[2], parseMenu(parts[3]));
            case "PLACE_ORDER":
                return new PlaceOrderCommand(timestamp, position,
                        parts[2], parts[3], parts[4], Arrays.asList(parts[5].split(",")));
            case "DISPATCH_ORDER":
                return new DispatchOrderCommand(timestamp, position, parts[2]);
            default:
                throw new IllegalArgumentException("Unknown command: " + text);
        }
    }

    /** "burger:120,pizza:200" becomes {burger=120, pizza=200} */
    Map<String, Long> parseMenu(String text) {
        Map<String, Long> menu = new HashMap<>();
        for (String entry : text.split(",")) {
            if (entry.isEmpty()) {
                continue;
            }
            int colon = entry.lastIndexOf(':');
            menu.put(entry.substring(0, colon), Long.parseLong(entry.substring(colon + 1)));
        }
        return menu;
    }
}

/**
 * Command pattern: base class of all commands.
 * Every command remembers its timestamp and its position in the input list.
 */
abstract class Command {
    long timestamp;
    int position;

    Command(long timestamp, int position) {
        this.timestamp = timestamp;
        this.position = position;
    }

    /** Runs this command on the system and returns its output. */
    abstract String execute(FoodOrderSystem system);
}

class AddRestaurantCommand extends Command {
    String restaurantId;
    int capacity;
    Map<String, Long> menu;

    AddRestaurantCommand(long timestamp, int position,
                         String restaurantId, int capacity, Map<String, Long> menu) {
        super(timestamp, position);
        this.restaurantId = restaurantId;
        this.capacity = capacity;
        this.menu = menu;
    }

    String execute(FoodOrderSystem system) {
        return system.addRestaurant(restaurantId, capacity, menu);
    }
}

class UpdateMenuCommand extends Command {
    String restaurantId;
    Map<String, Long> menuUpdates;

    UpdateMenuCommand(long timestamp, int position,
                      String restaurantId, Map<String, Long> menuUpdates) {
        super(timestamp, position);
        this.restaurantId = restaurantId;
        this.menuUpdates = menuUpdates;
    }

    String execute(FoodOrderSystem system) {
        return system.updateMenu(restaurantId, menuUpdates);
    }
}

class PlaceOrderCommand extends Command {
    String orderId;
    String customerId;
    String strategyName;
    List<String> items;

    PlaceOrderCommand(long timestamp, int position, String orderId,
                      String customerId, String strategyName, List<String> items) {
        super(timestamp, position);
        this.orderId = orderId;
        this.customerId = customerId;
        this.strategyName = strategyName;
        this.items = items;
    }

    String execute(FoodOrderSystem system) {
        return system.placeOrder(orderId, customerId, strategyName, items);
    }
}

class DispatchOrderCommand extends Command {
    String orderId;

    DispatchOrderCommand(long timestamp, int position, String orderId) {
        super(timestamp, position);
        this.orderId = orderId;
    }

    String execute(FoodOrderSystem system) {
        // the dispatch time is saved with the order, so we pass the timestamp
        return system.dispatchOrder(orderId, timestamp);
    }
}

/**
 * Holds all restaurants and orders and applies the business rules.
 * Commands call these methods.
 */
class FoodOrderSystem {
    // restaurantId -> restaurant
    Map<String, Restaurant> restaurants = new HashMap<>();
    // orderId -> order. Only accepted orders are stored.
    Map<String, Order> orders = new HashMap<>();
    // strategy name -> strategy object
    Map<String, RestaurantSelectionStrategy> strategies = new HashMap<>();

    FoodOrderSystem() {
        strategies.put("LOWEST_TOTAL_PRICE", new LowestTotalPriceStrategy());
        strategies.put("MAX_REMAINING_CAPACITY", new MaxRemainingCapacityStrategy());
    }

    String addRestaurant(String restaurantId, int capacity, Map<String, Long> menu) {
        if (restaurants.containsKey(restaurantId)) {
            return "RESTAURANT_ALREADY_EXISTS";
        }
        Restaurant restaurant = new Restaurant(restaurantId, capacity);
        restaurant.updateMenu(menu);
        restaurants.put(restaurantId, restaurant);
        return "OK";
    }

    String updateMenu(String restaurantId, Map<String, Long> menuUpdates) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) {
            return "RESTAURANT_NOT_FOUND";
        }
        restaurant.updateMenu(menuUpdates);
        return "OK";
    }

    String placeOrder(String orderId, String customerId, String strategyName, List<String> items) {
        RestaurantSelectionStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            return "INVALID_STRATEGY";
        }
        // order ids are unique, so a repeated id is not accepted again
        if (orders.containsKey(orderId)) {
            return "REJECTED";
        }

        // check every restaurant and keep the best one as per the strategy
        Restaurant best = null;
        long bestPrice = 0;
        for (Restaurant restaurant : restaurants.values()) {
            if (!restaurant.canServe(items)) {
                continue;
            }
            long price = restaurant.totalPrice(items);
            if (best == null || strategy.isBetter(restaurant, price, best, bestPrice)) {
                best = restaurant;
                bestPrice = price;
            }
        }
        if (best == null) {
            return "REJECTED";
        }

        best.openOrders++;
        orders.put(orderId, new Order(orderId, customerId, items, best));
        return "ACCEPTED";
    }

    String dispatchOrder(String orderId, long timestamp) {
        Order order = orders.get(orderId);
        if (order == null) {
            return "INVALID_ORDER";
        }
        if (order.dispatched) {
            return "ALREADY_DISPATCHED";
        }
        order.dispatched = true;
        order.restaurant.completeOrder(order, timestamp);
        return "DISPATCHED";
    }

    List<String> getRestaurantItemCounts() {
        List<String> rows = new ArrayList<>();
        for (Restaurant restaurant : restaurants.values()) {
            for (Map.Entry<String, Integer> entry : restaurant.servedCounts.entrySet()) {
                rows.add(restaurant.id + "|" + entry.getKey() + "|" + entry.getValue());
            }
        }
        // the rows themselves are sorted as plain strings
        Collections.sort(rows);
        return rows;
    }

    List<String> getDispatchedOrders(String restaurantId) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(restaurant.dispatchedOrders);
    }
}

/**
 * One restaurant: its menu, its capacity and what it has served so far.
 */
class Restaurant {
    String id;
    int capacity;
    int openOrders = 0;
    // item name -> price
    Map<String, Long> menu = new HashMap<>();
    // item name -> how many times it was served
    Map<String, Integer> servedCounts = new HashMap<>();
    // "dispatchTimestamp|orderId|customerId|items" rows, in dispatch order
    List<String> dispatchedOrders = new ArrayList<>();

    Restaurant(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    int remainingCapacity() {
        return capacity - openOrders;
    }

    /** price >= 0 adds or updates the item. price < 0 removes it. */
    void updateMenu(Map<String, Long> updates) {
        for (Map.Entry<String, Long> entry : updates.entrySet()) {
            if (entry.getValue() >= 0) {
                menu.put(entry.getKey(), entry.getValue());
            } else {
                menu.remove(entry.getKey());
            }
        }
    }

    /** True if there is a free slot and every item is on the menu. */
    boolean canServe(List<String> items) {
        if (openOrders >= capacity) {
            return false;
        }
        for (String item : items) {
            if (!menu.containsKey(item)) {
                return false;
            }
        }
        return true;
    }

    long totalPrice(List<String> items) {
        long total = 0;
        for (String item : items) {
            total += menu.get(item);
        }
        return total;
    }

    /** Frees one slot, counts the served items and saves the dispatch row. */
    void completeOrder(Order order, long timestamp) {
        openOrders--;
        for (String item : order.items) {
            servedCounts.put(item, servedCounts.getOrDefault(item, 0) + 1);
        }
        dispatchedOrders.add(timestamp + "|" + order.orderId + "|"
                + order.customerId + "|" + String.join(",", order.items));
    }
}

/**
 * An accepted order and the restaurant that is preparing it.
 */
class Order {
    String orderId;
    String customerId;
    List<String> items;
    Restaurant restaurant;
    boolean dispatched = false;

    Order(String orderId, String customerId, List<String> items, Restaurant restaurant) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.items = items;
        this.restaurant = restaurant;
    }
}

/**
 * Strategy pattern: decides which of two restaurants is better for an order.
 */
interface RestaurantSelectionStrategy {
    /** True if restaurant a (total priceA) is a better pick than restaurant b (total priceB). */
    boolean isBetter(Restaurant a, long priceA, Restaurant b, long priceB);
}

/** Lower total price, then more remaining capacity, then smaller restaurant id. */
class LowestTotalPriceStrategy implements RestaurantSelectionStrategy {
    public boolean isBetter(Restaurant a, long priceA, Restaurant b, long priceB) {
        if (priceA != priceB) {
            return priceA < priceB;
        }
        if (a.remainingCapacity() != b.remainingCapacity()) {
            return a.remainingCapacity() > b.remainingCapacity();
        }
        return a.id.compareTo(b.id) < 0;
    }
}

/** More remaining capacity, then lower total price, then smaller restaurant id. */
class MaxRemainingCapacityStrategy implements RestaurantSelectionStrategy {
    public boolean isBetter(Restaurant a, long priceA, Restaurant b, long priceB) {
        if (a.remainingCapacity() != b.remainingCapacity()) {
            return a.remainingCapacity() > b.remainingCapacity();
        }
        if (priceA != priceB) {
            return priceA < priceB;
        }
        return a.id.compareTo(b.id) < 0;
    }
}
```


## Complexity

Let `n` be the number of commands, `R` the number of restaurants and `k` the number of items in one order.

| Operation | Time |
|---|---|
| Sorting the commands | O(n log n) |
| `ADD_RESTAURANT`, `UPDATE_MENU` | O(m), m = menu entries in the command |
| `PLACE_ORDER` | O(R * k) |
| `DISPATCH_ORDER` | O(k) |
| `getRestaurantItemCounts()` | O(P log P), P = number of rows |
| `getDispatchedOrders()` | O(d), d = rows of that restaurant |

`PLACE_ORDER` checks every restaurant, which is fine here. With a huge number of restaurants, we could also keep a map from each item to the restaurants that sell it, and check only those.