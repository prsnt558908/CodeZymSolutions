# Design Food Order Management System using Commands in Python

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

To run the commands in time order, we sort the input positions. The sort key is `(timestamp, position)`, so equal timestamps keep their input order.

```python
run_order = sorted(range(len(commands)),
                   key=lambda i: (int(commands[i].split("|")[0]), i))
```

Then one long if-elif chain handles every command type.

```python
outputs = [""] * len(commands)
for i in run_order:
    parts = commands[i].split("|")
    kind = parts[1]

    if kind == "ADD_RESTAURANT":
        ...  # check duplicate id, create restaurant, read the menu
    elif kind == "UPDATE_MENU":
        ...  # find the restaurant, add, update or remove items
    elif kind == "PLACE_ORDER":
        ...  # check strategy, scan restaurants, pick the best one
    elif kind == "DISPATCH_ORDER":
        ...  # find the order, free the slot, count served items
```

Inside `PLACE_ORDER`, both strategies are mixed into the search loop.

```python
if best is None:
    better = True
elif strategy == "LOWEST_TOTAL_PRICE":
    if price != best_price:
        better = price < best_price
    elif free != best_free:
        better = free > best_free
    else:
        better = r.id < best.id
else:
    if free != best_free:
        better = free > best_free
    elif price != best_price:
        better = price < best_price
    else:
        better = r.id < best.id
```

This works, but it has real problems.

- `processCommands()` becomes a huge method. Sorting, text parsing and every business rule live in one place.
- A new command, like `CANCEL_ORDER`, means editing this long if-elif chain.
- A new strategy means editing the search loop itself, which can break the old strategies.


## Better Solution: Command + Strategy Pattern

### Why Command pattern?

The Command pattern wraps a request inside an object. Once a request is an object, we can keep it in a list, sort it and run it later.

That is exactly what this problem asks. We read all commands first, sort them by time and run them after that.

Each command type gets its own small class with one `execute()` method. `FoodCart` does not know what a command does. It only sorts the commands and calls `execute()`.

Adding `CANCEL_ORDER` later means a new command class, a new `if` in the factory and a new method in `FoodOrderSystem`. The existing commands stay untouched.

### Why Strategy pattern?

Both strategies do the same job: compare two restaurants and pick the better one. Only the rule is different.

So the search loop is written once, and the comparison rule is plugged into it. Each rule lives in its own class.

We keep the strategies in a dict from name to object. This also gives us the `INVALID_STRATEGY` check for free. If the name is not in the dict, the strategy does not exist.

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
- **`RestaurantSelectionStrategy`**: the strategy base class with `is_better()`. `LowestTotalPriceStrategy` and `MaxRemainingCapacityStrategy` extend it.

Why keep `FoodOrderSystem` apart from `FoodCart`? `FoodCart` decides **when** a command runs. `FoodOrderSystem` decides **what** it does. In Command pattern words, `FoodCart` is the invoker and `FoodOrderSystem` is the receiver.

### Important data structures

- **`restaurants`** (dict): restaurant id to `Restaurant`, for fast lookup.
- **`orders`** (dict): `DISPATCH_ORDER` only gives us an order id. Only accepted orders are saved, so dispatching a rejected order gives `INVALID_ORDER`.
- **`strategies`** (dict): strategy name to strategy object.
- **`Restaurant.menu`** (dict): item to price.
- **`Restaurant.served_counts`** (dict): item to how many times it was served. `getRestaurantItemCounts()` turns these into `"restaurantId|itemName|count"` rows and sorts the row strings, as the problem asks.
- **`Restaurant.dispatched_orders`** (list): a list keeps the rows in the exact order they were dispatched.

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
3. `execute()` calls `system.place_order("O2", "C2", "LOWEST_TOTAL_PRICE", ["burger"])`.
4. R1 sells burger for 120 and has 1 free slot. R2 sells it for 110 and also has 1 free slot.
5. `LowestTotalPriceStrategy` prefers the lower price, so R2 wins.
6. The output `ACCEPTED` is written to `outputs[3]`.

### Code

```python
from typing import Dict, List


class FoodCart:
    """Entry point of the system.

    It turns every text command into a command object,
    runs the commands in timestamp order and
    returns the outputs in the original input order.
    """

    def __init__(self):
        self.system = FoodOrderSystem()
        self.command_factory = CommandFactory()

    def processCommands(self, commands: List[str]) -> List[str]:
        command_list = []
        for position, text in enumerate(commands):
            command_list.append(self.command_factory.create(text, position))

        # smaller timestamp runs first. Same timestamp: keep the input order.
        command_list.sort(key=lambda command: (command.timestamp, command.position))

        # every output goes back to the input position of its command
        outputs = [""] * len(commands)
        for command in command_list:
            outputs[command.position] = command.execute(self.system)
        return outputs

    def getRestaurantItemCounts(self) -> List[str]:
        return self.system.get_restaurant_item_counts()

    def getDispatchedOrders(self, restaurantId: str) -> List[str]:
        return self.system.get_dispatched_orders(restaurantId)


class CommandFactory:
    """Simple Factory: reads one text command and creates the matching command object."""

    def create(self, text, position):
        parts = text.split("|")
        timestamp = int(parts[0])
        name = parts[1]

        if name == "ADD_RESTAURANT":
            return AddRestaurantCommand(timestamp, position, parts[2],
                                        int(parts[3]), self.parse_menu(parts[4]))
        if name == "UPDATE_MENU":
            return UpdateMenuCommand(timestamp, position, parts[2],
                                     self.parse_menu(parts[3]))
        if name == "PLACE_ORDER":
            return PlaceOrderCommand(timestamp, position, parts[2], parts[3],
                                     parts[4], parts[5].split(","))
        if name == "DISPATCH_ORDER":
            return DispatchOrderCommand(timestamp, position, parts[2])
        raise ValueError("Unknown command: " + text)

    def parse_menu(self, text) -> Dict[str, int]:
        """'burger:120,pizza:200' becomes {'burger': 120, 'pizza': 200}"""
        menu = {}
        for entry in text.split(","):
            if entry:
                item, price = entry.rsplit(":", 1)
                menu[item] = int(price)
        return menu


class Command:
    """Command pattern: base class of all commands.

    Every command remembers its timestamp and its position in the input list.
    """

    def __init__(self, timestamp, position):
        self.timestamp = timestamp
        self.position = position

    def execute(self, system):
        """Runs this command on the system and returns its output."""
        raise NotImplementedError


class AddRestaurantCommand(Command):
    def __init__(self, timestamp, position, restaurant_id, capacity, menu):
        super().__init__(timestamp, position)
        self.restaurant_id = restaurant_id
        self.capacity = capacity
        self.menu = menu

    def execute(self, system):
        return system.add_restaurant(self.restaurant_id, self.capacity, self.menu)


class UpdateMenuCommand(Command):
    def __init__(self, timestamp, position, restaurant_id, menu_updates):
        super().__init__(timestamp, position)
        self.restaurant_id = restaurant_id
        self.menu_updates = menu_updates

    def execute(self, system):
        return system.update_menu(self.restaurant_id, self.menu_updates)


class PlaceOrderCommand(Command):
    def __init__(self, timestamp, position, order_id, customer_id, strategy_name, items):
        super().__init__(timestamp, position)
        self.order_id = order_id
        self.customer_id = customer_id
        self.strategy_name = strategy_name
        self.items = items

    def execute(self, system):
        return system.place_order(self.order_id, self.customer_id,
                                  self.strategy_name, self.items)


class DispatchOrderCommand(Command):
    def __init__(self, timestamp, position, order_id):
        super().__init__(timestamp, position)
        self.order_id = order_id

    def execute(self, system):
        # the dispatch time is saved with the order, so we pass the timestamp
        return system.dispatch_order(self.order_id, self.timestamp)


class FoodOrderSystem:
    """Holds all restaurants and orders and applies the business rules.

    Commands call these methods.
    """

    def __init__(self):
        self.restaurants = {}  # restaurant_id -> Restaurant
        self.orders = {}       # order_id -> Order. Only accepted orders are stored.
        self.strategies = {    # strategy name -> strategy object
            "LOWEST_TOTAL_PRICE": LowestTotalPriceStrategy(),
            "MAX_REMAINING_CAPACITY": MaxRemainingCapacityStrategy(),
        }

    def add_restaurant(self, restaurant_id, capacity, menu):
        if restaurant_id in self.restaurants:
            return "RESTAURANT_ALREADY_EXISTS"
        restaurant = Restaurant(restaurant_id, capacity)
        restaurant.update_menu(menu)
        self.restaurants[restaurant_id] = restaurant
        return "OK"

    def update_menu(self, restaurant_id, menu_updates):
        restaurant = self.restaurants.get(restaurant_id)
        if restaurant is None:
            return "RESTAURANT_NOT_FOUND"
        restaurant.update_menu(menu_updates)
        return "OK"

    def place_order(self, order_id, customer_id, strategy_name, items):
        strategy = self.strategies.get(strategy_name)
        if strategy is None:
            return "INVALID_STRATEGY"
        # order ids are unique, so a repeated id is not accepted again
        if order_id in self.orders:
            return "REJECTED"

        # check every restaurant and keep the best one as per the strategy
        best = None
        best_price = 0
        for restaurant in self.restaurants.values():
            if not restaurant.can_serve(items):
                continue
            price = restaurant.total_price(items)
            if best is None or strategy.is_better(restaurant, price, best, best_price):
                best = restaurant
                best_price = price
        if best is None:
            return "REJECTED"

        best.open_orders += 1
        self.orders[order_id] = Order(order_id, customer_id, items, best)
        return "ACCEPTED"

    def dispatch_order(self, order_id, timestamp):
        order = self.orders.get(order_id)
        if order is None:
            return "INVALID_ORDER"
        if order.dispatched:
            return "ALREADY_DISPATCHED"
        order.dispatched = True
        order.restaurant.complete_order(order, timestamp)
        return "DISPATCHED"

    def get_restaurant_item_counts(self):
        rows = []
        for restaurant in self.restaurants.values():
            for item, count in restaurant.served_counts.items():
                rows.append(f"{restaurant.id}|{item}|{count}")
        # the rows themselves are sorted as plain strings
        rows.sort()
        return rows

    def get_dispatched_orders(self, restaurant_id):
        restaurant = self.restaurants.get(restaurant_id)
        if restaurant is None:
            return []
        return list(restaurant.dispatched_orders)


class Restaurant:
    """One restaurant: its menu, its capacity and what it has served so far."""

    def __init__(self, restaurant_id, capacity):
        self.id = restaurant_id
        self.capacity = capacity
        self.open_orders = 0
        self.menu = {}               # item name -> price
        self.served_counts = {}      # item name -> how many times it was served
        self.dispatched_orders = []  # "dispatchTimestamp|orderId|customerId|items" rows

    def remaining_capacity(self):
        return self.capacity - self.open_orders

    def update_menu(self, updates):
        """price >= 0 adds or updates the item. price < 0 removes it."""
        for item, price in updates.items():
            if price >= 0:
                self.menu[item] = price
            else:
                self.menu.pop(item, None)

    def can_serve(self, items):
        """True if there is a free slot and every item is on the menu."""
        if self.open_orders >= self.capacity:
            return False
        for item in items:
            if item not in self.menu:
                return False
        return True

    def total_price(self, items):
        total = 0
        for item in items:
            total += self.menu[item]
        return total

    def complete_order(self, order, timestamp):
        """Frees one slot, counts the served items and saves the dispatch row."""
        self.open_orders -= 1
        for item in order.items:
            self.served_counts[item] = self.served_counts.get(item, 0) + 1
        items_text = ",".join(order.items)
        self.dispatched_orders.append(
            f"{timestamp}|{order.order_id}|{order.customer_id}|{items_text}")


class Order:
    """An accepted order and the restaurant that is preparing it."""

    def __init__(self, order_id, customer_id, items, restaurant):
        self.order_id = order_id
        self.customer_id = customer_id
        self.items = items
        self.restaurant = restaurant
        self.dispatched = False


class RestaurantSelectionStrategy:
    """Strategy pattern: decides which of two restaurants is better for an order."""

    def is_better(self, a, price_a, b, price_b):
        """True if restaurant a (total price_a) is a better pick than b (total price_b)."""
        raise NotImplementedError


class LowestTotalPriceStrategy(RestaurantSelectionStrategy):
    """Lower total price, then more remaining capacity, then smaller restaurant id."""

    def is_better(self, a, price_a, b, price_b):
        if price_a != price_b:
            return price_a < price_b
        if a.remaining_capacity() != b.remaining_capacity():
            return a.remaining_capacity() > b.remaining_capacity()
        return a.id < b.id


class MaxRemainingCapacityStrategy(RestaurantSelectionStrategy):
    """More remaining capacity, then lower total price, then smaller restaurant id."""

    def is_better(self, a, price_a, b, price_b):
        if a.remaining_capacity() != b.remaining_capacity():
            return a.remaining_capacity() > b.remaining_capacity()
        if price_a != price_b:
            return price_a < price_b
        return a.id < b.id
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

`PLACE_ORDER` checks every restaurant, which is fine here. With a huge number of restaurants, we could also keep a dict from each item to the restaurants that sell it, and check only those.