# Design Flipkart Delivery Service in Python

#### Problem Statement
[https://codezym.com/question/83-design-flipkart-delivery-service](https://codezym.com/question/83-design-flipkart-delivery-service)

We have to build a small delivery system. Users create orders for a pincode, an admin creates delivery agents for pincodes, and `execute()` assigns pending orders to agents and returns the pickup and delivery logs.

The core idea is simple. We go through the orders from earliest to latest and give each order to the agent who can pick it up first. To find that agent, we only need to remember one thing per agent: **the time when it becomes free**.

The tricky part is the logs. We plan orders one by one, but a long delivery planned early can finish after many later orders. So we don't print logs while planning. We save every log as an event with its time, and sort all events once at the end.

For the design, the **Strategy pattern** is the best fit. The rule that picks an agent for an order is the part most likely to change in a real delivery app, so it gets its own class. Other patterns like Observer or Factory look useful here, but they only add classes without making anything simpler. The rest is a few plain classes: `Order`, `Agent`, `LogEvent` and `FKDeliveryService`.

## Rules in Short

The problem statement is long, so here are the rules that matter:

- Every order has an **ordering time**: `creationTime` for a normal order and `scheduledStartTime` for a scheduled order. It is also the earliest time the order can be picked up.
- Orders are planned in increasing ordering time. For the same time, the smaller order name goes first.
- An agent is **eligible** for an order if it serves the order's pincode. An order with no eligible agent stays pending and creates no logs.
- An order is picked up at the earliest time when any eligible agent is free. If many agents are free at that time, the smaller agent name wins.
- A normal order is delivered instantly. A scheduled order is completed `durationMinutes` after pickup and keeps the agent busy till then.
- Every planned order gets an **assignment index** (1, 2, 3 ...) in planning order.
- Logs are sorted by time, then by assignment index, then pickup before completion.

## Key Ideas

### 1. One value per agent is enough

Each agent stores `free_at`, the time when it becomes free. A new agent has `free_at = ""`. An empty string is smaller than any time, so the agent counts as free from the start.

An order can be picked up only when the order is ready **and** the agent is free. So with a given agent, the pickup time is just the later of the two. For time strings, `max()` gives the later time:

```python
def earliest_pickup_time(self, ordering_time):
    # An order can be picked up only when the order is ready and the agent is free.
    return max(self.free_at, ordering_time)
```

After a pickup, `free_at` becomes the completion time of that order.

Can a later order fit into an idle gap between two jobs of an agent? No. A gap only appears when the next job's ordering time had not come yet. Orders are planned in increasing ordering time, so every later order becomes ready only when that gap is already over.

Here is Example 3 from the problem, step by step:

| Order | Ordering time | AgentA free at | Pickup | Completion | AgentA free at (after) |
|---|---|---|---|---|---|
| Order A | 10:30 | free | 10:30 | 10:50 | 10:50 |
| Order B | 10:30 | 10:50 | 10:50 | 11:20 | 11:20 |

### 2. A normal order is a scheduled order with 0 minutes

A normal order is completed at its pickup time, so it never keeps the agent busy. The only other difference is the completion log text: `has delivered` instead of `has completed delivery of`.

So one `Order` class handles both types. A normal order simply has `duration_minutes = 0`.

### 3. Compare times as strings

The format `"yyyy-MM-dd, HH:mm"` has a fixed width, so string order is the same as time order. We compare times with `<`, `min()` and `max()` directly.

We parse a time only when we add minutes, because that can cross midnight, a month end or a year end. `datetime` and `timedelta` handle all of that for us.

## Basic Solution

The most direct approach: sort the pending orders, check every agent for every order, pick the best agent and add both logs right away.

```python
for order in sorted_pending_orders:
    best_agent = None
    best_time = ""
    for agent in self.agents.values():              # checks every agent in the system
        if order.pincode not in agent.pincodes:
            continue
        time = agent.earliest_pickup_time(order.ordering_time)
        if best_agent is None or time < best_time or (time == best_time and agent.name < best_agent.name):
            best_agent = agent
            best_time = time
    if best_agent is None:
        continue                                    # no agent for this pincode

    completion_time = self.add_minutes(best_time, order.duration_minutes)
    best_agent.free_at = completion_time
    logs.append(order.pickup_log(best_agent.name, best_time))            # added right away
    logs.append(order.completion_log(best_agent.name, completion_time))  # added right away
```

It even passes all four examples of the problem. But it has three problems.

**Problem 1: logs come out in planning order, not in time order.**

Say `AgentA` serves pincode `1` and `AgentB` serves pincode `2`. Order `O1` is scheduled for pincode `1` at 10:00 for 60 minutes. Order `O2` is a normal order for pincode `2` at 10:30.

The basic solution returns:

```
AgentA has picked up O1 at 2025-03-22, 10:00
AgentA has completed delivery of O1 to 1 at 2025-03-22, 11:00
AgentB has picked up O2 at 2025-03-22, 10:30
AgentB has delivered O2 to 2 at 2025-03-22, 10:30
```

The 11:00 log comes before the 10:30 logs. That is wrong.

**Problem 2: every order checks every agent.** Most of them don't even serve that pincode.

**Problem 3: the rule for picking an agent is mixed into the main loop.** A new rule means editing the most important loop of the system.

## Better Solution

### Fix 1: Save logs as events and sort them once

Every log becomes a `LogEvent`. It keeps the message and the three values we sort by: `time`, `assignment_index` and `is_pickup`.

```python
def sort_key(self):
    # 1) earlier time first  2) smaller assignment index first  3) pickup before completion
    return (self.time, self.assignment_index, 0 if self.is_pickup else 1)
```

Python compares tuples item by item, so one `events.sort(key=lambda event: event.sort_key())` applies all three rules.

Why does the index rule work? If an agent completes one order at 11:00 and picks up the next one at 11:00, the completed order was planned first. It has the smaller index, so its completion log comes first.

The problem lists two more tie breakers (order name, then agent name). We never reach them. Every planned order has its own index, so two events with the same time and the same index belong to the same order, and the pickup first rule already separates them.

Now the example above comes out right:

```
AgentA has picked up O1 at 2025-03-22, 10:00
AgentB has picked up O2 at 2025-03-22, 10:30
AgentB has delivered O2 to 2 at 2025-03-22, 10:30
AgentA has completed delivery of O1 to 1 at 2025-03-22, 11:00
```

### Fix 2: Map each pincode to its agents

We keep `agents_by_pincode`, a dictionary from pincode to a list of agents. One lookup gives all eligible agents of an order.

`addAgentPincode()` adds the **same** `Agent` object to the new pincode's list. So when an agent is busy with an order in one pincode, it is busy for its other pincodes too. That is exactly what the multi-pincode bonus needs.

### Fix 3: Strategy pattern for picking an agent

```python
class AgentSelectionStrategy:
    """Strategy pattern: decides which eligible agent gets an order."""

    def select_agent(self, eligible_agents, ordering_time):
        raise NotImplementedError
```

`EarliestFreeAgentStrategy` implements the rule of this problem. It uses `min()` with the key `(pickup time, agent name)`, so the earliest pickup time wins, and for the same time the smaller name wins. The main loop only calls `self.agent_selector.select_agent(...)`.

**Why Strategy?** Delivery apps change this rule often: nearest agent, best rated agent, agent with the fewest deliveries today. With Strategy, a new rule is just a new class. Planning, events and sorting don't change at all. The cost is one small base class, so it is worth it.

**Patterns that sound good but don't fit here:**

- **Observer for logs.** "Notify a logger on every pickup and delivery" sounds natural. But we plan orders in a different order than the events happen, so a listener would get events in the wrong order and would still have to collect and sort them. More classes, same work.
- **Factory with one class per order type.** A normal order is just a scheduled order with 0 minutes, and only one log sentence differs. Two order classes plus a factory add code with no gain. A `scheduled` flag is enough.

## Final Design

- `Order`: name, pincode, ordering time, duration (0 for normal orders) and its own log texts.
- `Agent`: name, its pincodes (a `set`, so the duplicate check is quick) and `free_at`.
- `LogEvent`: one log line plus the values used to sort it.
- `AgentSelectionStrategy` and `EarliestFreeAgentStrategy`: the rule for picking an agent.
- `FKDeliveryService`: keeps orders and agents in dictionaries and plans everything in `execute()`.

`execute()` works in three steps:

1. Collect the pending orders and sort them by ordering time, then by name.
2. For each order, get its eligible agents and let the strategy pick one. Compute the pickup and completion time, update the agent's `free_at`, give the order the next assignment index and create its two events. An order with no eligible agent is skipped and stays pending.
3. Sort all events and return their messages.

## Complete Python Code

```python
from datetime import datetime, timedelta


class Order:
    """A delivery order. Normal orders have duration_minutes = 0."""

    def __init__(self, name, pincode, ordering_time, duration_minutes, scheduled):
        self.name = name
        self.pincode = pincode
        # creationTime or scheduledStartTime. Also the earliest allowed pickup time.
        self.ordering_time = ordering_time
        self.duration_minutes = duration_minutes
        self.scheduled = scheduled
        self.delivered = False

    def pickup_log(self, agent_name, time):
        return f"{agent_name} has picked up {self.name} at {time}"

    def completion_log(self, agent_name, time):
        if self.scheduled:
            return f"{agent_name} has completed delivery of {self.name} to {self.pincode} at {time}"
        return f"{agent_name} has delivered {self.name} to {self.pincode} at {time}"


class Agent:
    """
    A delivery agent. One agent object is shared by all of its pincodes,
    so the agent's busy time is the same whichever pincode it delivers to.
    """

    def __init__(self, name):
        self.name = name
        self.pincodes = set()
        # Time when the agent becomes free. "" means free from the start, as "" is smaller than any time.
        self.free_at = ""

    def earliest_pickup_time(self, ordering_time):
        # An order can be picked up only when the order is ready and the agent is free.
        return max(self.free_at, ordering_time)


class AgentSelectionStrategy:
    """Strategy pattern: decides which eligible agent gets an order."""

    def select_agent(self, eligible_agents, ordering_time):
        raise NotImplementedError


class EarliestFreeAgentStrategy(AgentSelectionStrategy):
    """
    Picks the agent who can pick up the order the earliest.
    If many agents can pick it up at the same time, picks the smallest agent name.
    """

    def select_agent(self, eligible_agents, ordering_time):
        # earlier pickup time wins, for the same pickup time smaller name wins
        return min(eligible_agents,
                   key=lambda agent: (agent.earliest_pickup_time(ordering_time), agent.name))


class LogEvent:
    """One log line with the values used to sort it."""

    def __init__(self, time, assignment_index, is_pickup, message):
        self.time = time
        self.assignment_index = assignment_index
        self.is_pickup = is_pickup
        self.message = message

    def sort_key(self):
        # 1) earlier time first  2) smaller assignment index first  3) pickup before completion
        return (self.time, self.assignment_index, 0 if self.is_pickup else 1)


class FKDeliveryService:
    """Main service. Stores orders and agents, and plans all pending deliveries in execute()."""

    def __init__(self):
        self.orders = {}             # orderName -> Order. Also used to reject duplicate order names.
        self.agents = {}             # agentName -> Agent. Also used to reject duplicate agent names.
        self.agents_by_pincode = {}  # pincode -> list of agents who deliver to that pincode
        self.agent_selector = EarliestFreeAgentStrategy()  # rule used to pick an agent (Strategy pattern)
        self.time_format = "%Y-%m-%d, %H:%M"

    def createOrder(self, orderName, pincode, creationTime):
        if orderName in self.orders:
            return "ORDER_ALREADY_EXISTS"
        # A normal order is just an order which takes 0 minutes to deliver.
        self.orders[orderName] = Order(orderName, pincode, creationTime, 0, False)
        return "ORDER_CREATED"

    def createOrderScheduled(self, orderName, pincode, scheduledStartTime, durationMinutes):
        if orderName in self.orders:
            return "ORDER_ALREADY_EXISTS"
        if durationMinutes <= 0:
            return "INVALID_SCHEDULE"
        self.orders[orderName] = Order(orderName, pincode, scheduledStartTime, durationMinutes, True)
        return "ORDER_CREATED"

    def createAgent(self, agentName, pincode):
        if agentName in self.agents:
            return "AGENT_ALREADY_EXISTS"
        agent = Agent(agentName)
        self.agents[agentName] = agent
        self.link_agent_to_pincode(agent, pincode)
        return "AGENT_CREATED"

    def addAgentPincode(self, agentName, pincode):
        agent = self.agents.get(agentName)
        if agent is None:
            return "AGENT_NOT_FOUND"
        if pincode in agent.pincodes:
            return "PINCODE_ALREADY_PRESENT"
        self.link_agent_to_pincode(agent, pincode)
        return "PINCODE_ADDED"

    def execute(self):
        # Step 1: collect pending orders and sort them by orderingTime, then by orderName.
        pending_orders = [order for order in self.orders.values() if not order.delivered]
        pending_orders.sort(key=lambda order: (order.ordering_time, order.name))

        # Step 2: plan orders one by one. Every planned order creates a pickup and a completion event.
        events = []
        assignment_index = 0
        for order in pending_orders:
            eligible_agents = self.agents_by_pincode.get(order.pincode)
            if not eligible_agents:
                continue  # no agent for this pincode, order stays pending

            agent = self.agent_selector.select_agent(eligible_agents, order.ordering_time)
            pickup_time = agent.earliest_pickup_time(order.ordering_time)
            completion_time = self.add_minutes(pickup_time, order.duration_minutes)

            agent.free_at = completion_time  # agent stays busy till this order is completed
            order.delivered = True
            assignment_index += 1

            events.append(LogEvent(pickup_time, assignment_index, True,
                                   order.pickup_log(agent.name, pickup_time)))
            events.append(LogEvent(completion_time, assignment_index, False,
                                   order.completion_log(agent.name, completion_time)))

        # Step 3: events were created in planning order. Sort them in the order they happen.
        events.sort(key=lambda event: event.sort_key())
        return [event.message for event in events]

    def link_agent_to_pincode(self, agent, pincode):
        agent.pincodes.add(pincode)
        if pincode not in self.agents_by_pincode:
            self.agents_by_pincode[pincode] = []
        self.agents_by_pincode[pincode].append(agent)

    def add_minutes(self, time, minutes):
        # "2025-03-22, 23:50" + 20 minutes -> "2025-03-23, 00:10". Handles day, month and year change.
        if minutes == 0:
            return time
        new_time = datetime.strptime(time, self.time_format) + timedelta(minutes=minutes)
        return new_time.strftime(self.time_format)
```

## Complexity

Let `N` be the number of orders and `K` the number of agents serving an order's pincode.

- `createOrder()`, `createOrderScheduled()`, `createAgent()` and `addAgentPincode()` take O(1) on average.
- `execute()` takes O(N log N) to sort the orders, O(N × K) to pick agents and O(N log N) to sort the 2N events.