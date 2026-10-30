# Design Flipkart Delivery Service in Java

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

Each agent stores `freeAt`, the time when it becomes free. A new agent has `freeAt = ""`. An empty string is smaller than any time, so the agent counts as free from the start.

An order can be picked up only when the order is ready **and** the agent is free. So with a given agent, the pickup time is just the later of the two:

```java
// An order can be picked up only when the order is ready and the agent is free.
String earliestPickupTime(String orderingTime) {
    return freeAt.compareTo(orderingTime) > 0 ? freeAt : orderingTime;
}
```

After a pickup, `freeAt` becomes the completion time of that order.

Can a later order fit into an idle gap between two jobs of an agent? No. A gap only appears when the next job's ordering time had not come yet. Orders are planned in increasing ordering time, so every later order becomes ready only when that gap is already over.

Here is Example 3 from the problem, step by step:

| Order | Ordering time | AgentA free at | Pickup | Completion | AgentA free at (after) |
|---|---|---|---|---|---|
| Order A | 10:30 | free | 10:30 | 10:50 | 10:50 |
| Order B | 10:30 | 10:50 | 10:50 | 11:20 | 11:20 |

### 2. A normal order is a scheduled order with 0 minutes

A normal order is completed at its pickup time, so it never keeps the agent busy. The only other difference is the completion log text: `has delivered` instead of `has completed delivery of`.

So one `Order` class handles both types. A normal order simply has `durationMinutes = 0`.

### 3. Compare times as strings

The format `"yyyy-MM-dd, HH:mm"` has a fixed width, so string order is the same as time order. We compare times with `compareTo()` directly.

We parse a time only when we add minutes, because that can cross midnight, a month end or a year end. `LocalDateTime` handles all of that for us.

## Basic Solution

The most direct approach: sort the pending orders, check every agent for every order, pick the best agent and add both logs right away.

```java
for (Order order : sortedPendingOrders) {
    Agent bestAgent = null;
    String bestTime = "";
    for (Agent agent : agents.values()) {             // checks every agent in the system
        if (!agent.pincodes.contains(order.pincode)) continue;
        String time = agent.earliestPickupTime(order.orderingTime);
        if (bestAgent == null || time.compareTo(bestTime) < 0
                || (time.equals(bestTime) && agent.name.compareTo(bestAgent.name) < 0)) {
            bestAgent = agent;
            bestTime = time;
        }
    }
    if (bestAgent == null) continue;                 // no agent for this pincode

    String completionTime = addMinutes(bestTime, order.durationMinutes);
    bestAgent.freeAt = completionTime;
    logs.add(order.pickupLog(bestAgent.name, bestTime));           // added right away
    logs.add(order.completionLog(bestAgent.name, completionTime)); // added right away
}
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

Every log becomes a `LogEvent`. It keeps the message and the three values we sort by: `time`, `assignmentIndex` and `isPickup`.

```java
// 1) earlier time first  2) smaller assignment index first  3) pickup before completion
public int compareTo(LogEvent other) {
    if (!time.equals(other.time)) return time.compareTo(other.time);
    if (assignmentIndex != other.assignmentIndex) return assignmentIndex - other.assignmentIndex;
    if (isPickup == other.isPickup) return 0;
    return isPickup ? -1 : 1;
}
```

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

We keep `Map<String, List<Agent>> agentsByPincode`. One lookup gives all eligible agents of an order.

`addAgentPincode()` adds the **same** `Agent` object to the new pincode's list. So when an agent is busy with an order in one pincode, it is busy for its other pincodes too. That is exactly what the multi-pincode bonus needs.

### Fix 3: Strategy pattern for picking an agent

```java
interface AgentSelectionStrategy {
    Agent selectAgent(List<Agent> eligibleAgents, String orderingTime);
}
```

`EarliestFreeAgentStrategy` implements the rule of this problem: the earliest pickup time wins, and for the same time the smaller name wins. The main loop only calls `agentSelector.selectAgent(...)`.

**Why Strategy?** Delivery apps change this rule often: nearest agent, best rated agent, agent with the fewest deliveries today. With Strategy, a new rule is just a new class. Planning, events and sorting don't change at all. The cost is one small interface, so it is worth it.

**Patterns that sound good but don't fit here:**

- **Observer for logs.** "Notify a logger on every pickup and delivery" sounds natural. But we plan orders in a different order than the events happen, so a listener would get events in the wrong order and would still have to collect and sort them. More classes, same work.
- **Factory with one class per order type.** A normal order is just a scheduled order with 0 minutes, and only one log sentence differs. Two order classes plus a factory add code with no gain. A `scheduled` flag is enough.

## Final Design

- `Order`: name, pincode, ordering time, duration (0 for normal orders) and its own log texts.
- `Agent`: name, its pincodes (a `Set`, so the duplicate check is quick) and `freeAt`.
- `LogEvent`: one log line plus the values used to sort it.
- `AgentSelectionStrategy` and `EarliestFreeAgentStrategy`: the rule for picking an agent.
- `FKDeliveryService`: keeps orders and agents in hash maps and plans everything in `execute()`.

`execute()` works in three steps:

1. Collect the pending orders and sort them by ordering time, then by name.
2. For each order, get its eligible agents and let the strategy pick one. Compute the pickup and completion time, update the agent's `freeAt`, give the order the next assignment index and create its two events. An order with no eligible agent is skipped and stays pending.
3. Sort all events and return their messages.

## Complete Java Code

```java
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Main service. Stores orders and agents, and plans all pending deliveries in execute().
 */
public class FKDeliveryService {

    // orderName -> order. Also used to reject duplicate order names.
    Map<String, Order> orders = new HashMap<>();

    // agentName -> agent. Also used to reject duplicate agent names.
    Map<String, Agent> agents = new HashMap<>();

    // pincode -> agents who deliver to that pincode.
    // Gives all eligible agents of an order in a single lookup.
    Map<String, List<Agent>> agentsByPincode = new HashMap<>();

    // Rule used to pick an agent for an order (Strategy pattern).
    AgentSelectionStrategy agentSelector = new EarliestFreeAgentStrategy();

    DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm");

    public FKDeliveryService() {
    }

    public String createOrder(String orderName, String pincode, String creationTime) {
        if (orders.containsKey(orderName)) return "ORDER_ALREADY_EXISTS";
        // A normal order is just an order which takes 0 minutes to deliver.
        orders.put(orderName, new Order(orderName, pincode, creationTime, 0, false));
        return "ORDER_CREATED";
    }

    public String createOrderScheduled(String orderName, String pincode, String scheduledStartTime, int durationMinutes) {
        if (orders.containsKey(orderName)) return "ORDER_ALREADY_EXISTS";
        if (durationMinutes <= 0) return "INVALID_SCHEDULE";
        orders.put(orderName, new Order(orderName, pincode, scheduledStartTime, durationMinutes, true));
        return "ORDER_CREATED";
    }

    public String createAgent(String agentName, String pincode) {
        if (agents.containsKey(agentName)) return "AGENT_ALREADY_EXISTS";
        Agent agent = new Agent(agentName);
        agents.put(agentName, agent);
        linkAgentToPincode(agent, pincode);
        return "AGENT_CREATED";
    }

    public String addAgentPincode(String agentName, String pincode) {
        Agent agent = agents.get(agentName);
        if (agent == null) return "AGENT_NOT_FOUND";
        if (agent.pincodes.contains(pincode)) return "PINCODE_ALREADY_PRESENT";
        linkAgentToPincode(agent, pincode);
        return "PINCODE_ADDED";
    }

    public List<String> execute() {
        // Step 1: collect pending orders and sort them by orderingTime, then by orderName.
        List<Order> pendingOrders = new ArrayList<>();
        for (Order order : orders.values()) {
            if (!order.delivered) pendingOrders.add(order);
        }
        pendingOrders.sort((a, b) -> {
            if (!a.orderingTime.equals(b.orderingTime)) return a.orderingTime.compareTo(b.orderingTime);
            return a.name.compareTo(b.name);
        });

        // Step 2: plan orders one by one. Every planned order creates a pickup and a completion event.
        List<LogEvent> events = new ArrayList<>();
        int assignmentIndex = 0;
        for (Order order : pendingOrders) {
            List<Agent> eligibleAgents = agentsByPincode.get(order.pincode);
            if (eligibleAgents == null) continue; // no agent for this pincode, order stays pending

            Agent agent = agentSelector.selectAgent(eligibleAgents, order.orderingTime);
            String pickupTime = agent.earliestPickupTime(order.orderingTime);
            String completionTime = addMinutes(pickupTime, order.durationMinutes);

            agent.freeAt = completionTime; // agent stays busy till this order is completed
            order.delivered = true;
            assignmentIndex++;

            events.add(new LogEvent(pickupTime, assignmentIndex, true,
                    order.pickupLog(agent.name, pickupTime)));
            events.add(new LogEvent(completionTime, assignmentIndex, false,
                    order.completionLog(agent.name, completionTime)));
        }

        // Step 3: events were created in planning order. Sort them in the order they happen.
        Collections.sort(events);
        List<String> logs = new ArrayList<>();
        for (LogEvent event : events) logs.add(event.message);
        return logs;
    }

    void linkAgentToPincode(Agent agent, String pincode) {
        agent.pincodes.add(pincode);
        agentsByPincode.putIfAbsent(pincode, new ArrayList<>());
        agentsByPincode.get(pincode).add(agent);
    }

    // "2025-03-22, 23:50" + 20 minutes -> "2025-03-23, 00:10". Handles day, month and year change.
    String addMinutes(String time, int minutes) {
        if (minutes == 0) return time;
        return LocalDateTime.parse(time, timeFormat).plusMinutes(minutes).format(timeFormat);
    }
}

/**
 * A delivery order. Normal orders have durationMinutes = 0.
 */
class Order {
    String name;
    String pincode;
    String orderingTime;  // creationTime or scheduledStartTime. Also the earliest allowed pickup time.
    int durationMinutes;
    boolean scheduled;
    boolean delivered = false;

    Order(String name, String pincode, String orderingTime, int durationMinutes, boolean scheduled) {
        this.name = name;
        this.pincode = pincode;
        this.orderingTime = orderingTime;
        this.durationMinutes = durationMinutes;
        this.scheduled = scheduled;
    }

    String pickupLog(String agentName, String time) {
        return agentName + " has picked up " + name + " at " + time;
    }

    String completionLog(String agentName, String time) {
        if (scheduled) return agentName + " has completed delivery of " + name + " to " + pincode + " at " + time;
        return agentName + " has delivered " + name + " to " + pincode + " at " + time;
    }
}

/**
 * A delivery agent. One agent object is shared by all of its pincodes,
 * so the agent's busy time is the same whichever pincode it delivers to.
 */
class Agent {
    String name;
    Set<String> pincodes = new HashSet<>();
    // Time when the agent becomes free. "" means free from the start, as "" is smaller than any time.
    String freeAt = "";

    Agent(String name) {
        this.name = name;
    }

    // An order can be picked up only when the order is ready and the agent is free.
    String earliestPickupTime(String orderingTime) {
        return freeAt.compareTo(orderingTime) > 0 ? freeAt : orderingTime;
    }
}

/**
 * Strategy pattern: decides which eligible agent gets an order.
 */
interface AgentSelectionStrategy {
    Agent selectAgent(List<Agent> eligibleAgents, String orderingTime);
}

/**
 * Picks the agent who can pick up the order the earliest.
 * If many agents can pick it up at the same time, picks the smallest agent name.
 */
class EarliestFreeAgentStrategy implements AgentSelectionStrategy {
    public Agent selectAgent(List<Agent> eligibleAgents, String orderingTime) {
        Agent bestAgent = null;
        String bestTime = "";
        for (Agent agent : eligibleAgents) {
            String time = agent.earliestPickupTime(orderingTime);
            // earlier pickup time wins, for the same pickup time smaller name wins
            if (bestAgent == null || time.compareTo(bestTime) < 0
                    || (time.equals(bestTime) && agent.name.compareTo(bestAgent.name) < 0)) {
                bestAgent = agent;
                bestTime = time;
            }
        }
        return bestAgent;
    }
}

/**
 * One log line with the values used to sort it.
 */
class LogEvent implements Comparable<LogEvent> {
    String time;
    int assignmentIndex;
    boolean isPickup;
    String message;

    LogEvent(String time, int assignmentIndex, boolean isPickup, String message) {
        this.time = time;
        this.assignmentIndex = assignmentIndex;
        this.isPickup = isPickup;
        this.message = message;
    }

    // 1) earlier time first  2) smaller assignment index first  3) pickup before completion
    public int compareTo(LogEvent other) {
        if (!time.equals(other.time)) return time.compareTo(other.time);
        if (assignmentIndex != other.assignmentIndex) return assignmentIndex - other.assignmentIndex;
        if (isPickup == other.isPickup) return 0;
        return isPickup ? -1 : 1;
    }
}
```

## Complexity

Let `N` be the number of orders and `K` the number of agents serving an order's pincode.

- `createOrder()`, `createOrderScheduled()`, `createAgent()` and `addAgentPincode()` take O(1) on average.
- `execute()` takes O(N log N) to sort the orders, O(N × K) to pick agents and O(N log N) to sort the 2N events.