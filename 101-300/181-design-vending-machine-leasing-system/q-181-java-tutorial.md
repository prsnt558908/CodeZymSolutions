# Design Vending Machine Leasing System in Java

#### Problem Statement
[https://codezym.com/question/181-design-vending-machine-leasing-system](https://codezym.com/question/181-design-vending-machine-leasing-system)

## Core Idea

The system keeps two maps, one for vending machines and one for lease agreements. Every method looks up what it needs, runs a few checks, and then reads or updates these objects.

Two parts of this system are meant to grow over time: payment strategies and machine states. So the best fit here is a combination of two design patterns:

- **Strategy pattern** for payments. `MONTHLY` and `ONE_TIME` each become a small class that knows how to calculate the total amount.
- **State pattern** for machine states. `Idle`, `OutOfStock` and `Maintenance` each become a small class that knows if the machine can take a new lease.

Now a new payment plan or a new machine state is just one new class, plus one line to register it. The rest of the code does not change.

Lease status (`ACTIVE`, `UPCOMING`, `EXPIRED`, `ENDED`) is never stored. It is calculated from `currentMonth` every time we need it. We only store whether `endLease` was called.

## A Simple First Version

Let us start with the most direct approach. We store the machine state and the payment strategy as plain text, and use `if` checks on them.

```java
class VendingMachine {
    String machineId;
    String location;
    String state = "Idle";
}

class LeaseAgreement {
    String leaseId, machineId, customerId, paymentStrategy;
    int startMonth, endMonth, amount;
    boolean ended;
}
```

To create a lease, we scan every lease in the system to find an overlap, and then check the state text:

```java
for (LeaseAgreement other : leases.values()) {
    boolean sameMachine = other.machineId.equals(machineId);
    boolean overlaps = Math.max(startMonth, other.startMonth) <= Math.min(endMonth, other.endMonth);
    if (sameMachine && !other.ended && overlaps) {
        return "Machine already leased for this period";
    }
}
if (!machine.state.equals("Idle")) {
    return "Machine not available";
}
```

The payment is an `if` on the strategy name:

```java
if (lease.paymentStrategy.equals("MONTHLY")) {
    return lease.amount * (lease.endMonth - lease.startMonth + 1);
}
return lease.amount; // ONE_TIME
```

This works, but it has three problems.

1. **Payment logic is hard-coded.** Every new payment plan adds one more `if` to `calculatePayment`. We keep editing code that already works.
2. **State rules are hard-coded.** The state is just text, and the rule "only Idle can be leased" lives inside `createLease`. A new state means finding and updating every such check. A typo like `"idle"` also compiles without any error.
3. **Slow lookups.** To find an overlap or an active lease, we scan every lease in the whole system. This includes leases of other machines and leases that are already ended.

Let us fix these one by one.

## Step 1: Strategy Pattern for Payments

Each payment plan becomes its own class behind one common interface.

```java
interface PaymentStrategy {
    String getName();

    int calculate(int amount, int numberOfMonths);
}

class MonthlyPaymentStrategy implements PaymentStrategy {
    public String getName() {
        return "MONTHLY";
    }

    public int calculate(int amount, int numberOfMonths) {
        return amount * numberOfMonths;
    }
}
```

`OneTimePaymentStrategy` looks the same, but its `calculate` simply returns `amount`.

Each lease stores the strategy object it was created with. The lease does not care which plan it has. It just asks the strategy:

```java
int calculatePayment() {
    int numberOfMonths = endMonth - startMonth + 1;
    return paymentStrategy.calculate(amount, numberOfMonths);
}
```

This always uses the original `startMonth` and `endMonth`. So ending a lease early does not change its payment, just like the problem asks.

The largest possible payment is `1,000,000 * 120 = 120,000,000`, which fits easily in an `int`.

To turn the input text `"MONTHLY"` into the right object, the system keeps a map from name to strategy. This map works like a simple factory. To add a new plan like `QUARTERLY`, we write one new class and register it in the constructor.

## Step 2: State Pattern for Machine States

A machine is always in exactly one state, and the state decides what the machine is allowed to do. Right now there is one such rule: can the machine take a new lease?

```java
interface MachineState {
    String getName();

    boolean canBeLeased();
}

class IdleState implements MachineState {
    public String getName() {
        return "Idle";
    }

    public boolean canBeLeased() {
        return true;
    }
}
```

`OutOfStockState` and `MaintenanceState` look the same, but return `false` from `canBeLeased()`.

Now `createLease` just asks the current state:

```java
if (!machine.state.canBeLeased()) {
    return "Machine not available";
}
```

`changeMachineState` simply swaps the state object. A new state like `Cleaning` is added the same way as a new payment plan, and `createLease` does not change at all.

**State vs Strategy:** both look the same in code. Each is an interface with a few small classes. The difference is in how they are used. A lease picks its payment strategy once and keeps it forever. A machine keeps moving from one state to another during its life.

## Step 3: Each Machine Keeps Its Own Open Leases

Instead of scanning every lease in the system, each machine keeps a list of its own leases that were not manually ended. Let us call them open leases.

- `createLease` adds the new lease to this list.
- `endLease` removes it. An ended lease can never block a new lease or be active again, so the machine does not need it anymore.
- Expired leases stay in the list. The rules say they still block overlapping leases until they are manually ended.

Both checks now only look at this short list:

```java
boolean hasOverlappingLease(int startMonth, int endMonth) {
    for (LeaseAgreement lease : openLeases) {
        if (lease.overlaps(startMonth, endMonth)) {
            return true;
        }
    }
    return false;
}
```

`hasActiveLease(currentMonth)` works the same way, but uses `lease.isActive(currentMonth)`. An upcoming lease is not active, so it does not make the machine `LEASED`.

Here is a nice bonus. `createLease` never lets two open leases of a machine overlap, and months only go from 1 to 120. So a machine can never have more than 120 open leases, and every scan stays small.

## Why Not Use State or Observer for Leases?

It is tempting to use the State pattern for leases too, with classes like `ActiveLeaseState` and `ExpiredLeaseState`. Another idea is the Observer pattern, where a lease tells its machine when it starts or ends.

Both sound good on paper, but they do not fit here. A lease moves from upcoming to active to expired only because time passes. The system never gets a "month has changed" event. Each method call simply brings its own `currentMonth`.

So a stored status would quickly go out of date, and there is no event to notify anyone about. The simple fix is to calculate the status every time we need it:

```java
String getStatus(int currentMonth) {
    if (ended) {
        return "ENDED";
    }
    if (currentMonth < startMonth) {
        return "UPCOMING";
    }
    if (currentMonth > endMonth) {
        return "EXPIRED";
    }
    return "ACTIVE";
}
```

`ENDED` is checked first, because a manually ended lease stays `ENDED` no matter which month we ask about.

## Key Classes

| Class | Why we need it |
| --- | --- |
| `VendingMachineLeasingSystem` | Entry point. Holds two maps, machines by id and leases by id, so every lookup is fast. |
| `VendingMachine` | Holds location, current state and the list of open leases. Checks for overlapping leases and active leases. |
| `LeaseAgreement` | Holds the lease data and one `ended` flag. Calculates its own status and payment. |
| `PaymentStrategy` | One class per payment plan. |
| `MachineState` | One class per machine state. Each state says if a new lease is allowed. |

## Order of Checks Matters

Every method runs its checks in the exact order the problem lists them.

For example, in `createLease` the overlap check comes before the state check. So a machine in `Maintenance` that also has an overlapping lease returns `"Machine already leased for this period"`, not `"Machine not available"`.

## Final Java Code

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy pattern: each payment plan knows how to calculate the total amount.
 */
interface PaymentStrategy {
    String getName();

    int calculate(int amount, int numberOfMonths);
}

// amount is the monthly rent
class MonthlyPaymentStrategy implements PaymentStrategy {
    public String getName() {
        return "MONTHLY";
    }

    public int calculate(int amount, int numberOfMonths) {
        return amount * numberOfMonths;
    }
}

// amount is the fixed price for the whole lease
class OneTimePaymentStrategy implements PaymentStrategy {
    public String getName() {
        return "ONE_TIME";
    }

    public int calculate(int amount, int numberOfMonths) {
        return amount;
    }
}

/**
 * State pattern: each machine state decides what the machine is allowed to do.
 */
interface MachineState {
    String getName();

    boolean canBeLeased();
}

class IdleState implements MachineState {
    public String getName() {
        return "Idle";
    }

    public boolean canBeLeased() {
        return true;
    }
}

class OutOfStockState implements MachineState {
    public String getName() {
        return "OutOfStock";
    }

    public boolean canBeLeased() {
        return false;
    }
}

class MaintenanceState implements MachineState {
    public String getName() {
        return "Maintenance";
    }

    public boolean canBeLeased() {
        return false;
    }
}

/**
 * One lease between a customer and a vending machine.
 * Status is never stored. It is calculated from currentMonth when asked.
 */
class LeaseAgreement {
    String leaseId;
    String machineId;
    String customerId;
    int startMonth;
    int endMonth;
    int amount;
    PaymentStrategy paymentStrategy;
    boolean ended = false; // becomes true once endLease() is called

    LeaseAgreement(String leaseId, String machineId, String customerId, int startMonth, int endMonth,
                   int amount, PaymentStrategy paymentStrategy) {
        this.leaseId = leaseId;
        this.machineId = machineId;
        this.customerId = customerId;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.amount = amount;
        this.paymentStrategy = paymentStrategy;
    }

    // two month ranges overlap if they share at least one month
    boolean overlaps(int otherStartMonth, int otherEndMonth) {
        return Math.max(startMonth, otherStartMonth) <= Math.min(endMonth, otherEndMonth);
    }

    boolean isActive(int currentMonth) {
        return getStatus(currentMonth).equals("ACTIVE");
    }

    String getStatus(int currentMonth) {
        if (ended) {
            return "ENDED";
        }
        if (currentMonth < startMonth) {
            return "UPCOMING";
        }
        if (currentMonth > endMonth) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    // always uses the original period, even if the lease was ended early
    int calculatePayment() {
        int numberOfMonths = endMonth - startMonth + 1;
        return paymentStrategy.calculate(amount, numberOfMonths);
    }

    String getDetails(int currentMonth) {
        return leaseId + "," + machineId + "," + customerId + "," + startMonth + "," + endMonth + ","
                + paymentStrategy.getName() + "," + amount + "," + getStatus(currentMonth);
    }
}

/**
 * A vending machine with its location, current state and open leases.
 */
class VendingMachine {
    String machineId;
    String location;
    MachineState state;

    // Leases of this machine that were not manually ended.
    // Open leases never overlap and months go from 1 to 120,
    // so this list never holds more than 120 leases.
    List<LeaseAgreement> openLeases = new ArrayList<>();

    VendingMachine(String machineId, String location, MachineState state) {
        this.machineId = machineId;
        this.location = location;
        this.state = state;
    }

    boolean hasOverlappingLease(int startMonth, int endMonth) {
        for (LeaseAgreement lease : openLeases) {
            if (lease.overlaps(startMonth, endMonth)) {
                return true;
            }
        }
        return false;
    }

    boolean hasActiveLease(int currentMonth) {
        for (LeaseAgreement lease : openLeases) {
            if (lease.isActive(currentMonth)) {
                return true;
            }
        }
        return false;
    }

    String getDetails(int currentMonth) {
        String leasedStatus = hasActiveLease(currentMonth) ? "LEASED" : "AVAILABLE";
        return machineId + "," + location + "," + state.getName() + "," + leasedStatus;
    }
}

/**
 * Entry point. Stores all machines and leases.
 * Every method runs its checks in the exact order given in the problem.
 */
public class VendingMachineLeasingSystem {
    Map<String, VendingMachine> machines = new HashMap<>();
    Map<String, LeaseAgreement> leases = new HashMap<>();

    // name -> object lookups, these work like a simple factory
    Map<String, PaymentStrategy> paymentStrategies = new HashMap<>();
    Map<String, MachineState> machineStates = new HashMap<>();

    public VendingMachineLeasingSystem() {
        // To support a new payment plan or machine state,
        // write its class and register it here. Nothing else changes.
        addPaymentStrategy(new MonthlyPaymentStrategy());
        addPaymentStrategy(new OneTimePaymentStrategy());

        addMachineState(new IdleState());
        addMachineState(new OutOfStockState());
        addMachineState(new MaintenanceState());
    }

    void addPaymentStrategy(PaymentStrategy strategy) {
        paymentStrategies.put(strategy.getName(), strategy);
    }

    void addMachineState(MachineState state) {
        machineStates.put(state.getName(), state);
    }

    public String registerMachine(String machineId, String location) {
        if (machines.containsKey(machineId)) {
            return "Machine already exists";
        }
        machines.put(machineId, new VendingMachine(machineId, location, machineStates.get("Idle")));
        return "Machine registered";
    }

    public String createLease(String leaseId, String machineId, String customerId, int startMonth, int endMonth,
                              int currentMonth, String paymentStrategy, int amount) {
        if (leases.containsKey(leaseId)) {
            return "Lease already exists";
        }
        VendingMachine machine = machines.get(machineId);
        if (machine == null) {
            return "Machine not found";
        }
        if (endMonth < currentMonth) {
            return "Lease period already expired";
        }
        if (machine.hasOverlappingLease(startMonth, endMonth)) {
            return "Machine already leased for this period";
        }
        if (!machine.state.canBeLeased()) {
            return "Machine not available";
        }

        LeaseAgreement lease = new LeaseAgreement(leaseId, machineId, customerId, startMonth, endMonth,
                amount, paymentStrategies.get(paymentStrategy));
        leases.put(leaseId, lease);
        machine.openLeases.add(lease);
        return "Lease created";
    }

    public String changeMachineState(String machineId, String newState) {
        VendingMachine machine = machines.get(machineId);
        if (machine == null) {
            return "Machine not found";
        }
        machine.state = machineStates.get(newState);
        return "State changed";
    }

    public int calculatePayment(String leaseId) {
        LeaseAgreement lease = leases.get(leaseId);
        if (lease == null) {
            return -1;
        }
        return lease.calculatePayment();
    }

    public String endLease(String leaseId, int currentMonth) {
        LeaseAgreement lease = leases.get(leaseId);
        if (lease == null) {
            return "Lease not found";
        }
        if (lease.ended) {
            return "Lease already ended";
        }
        if (currentMonth < lease.startMonth) {
            return "Lease not started";
        }
        lease.ended = true;
        // an ended lease can never block or be active again,
        // so the machine does not need to keep it
        machines.get(lease.machineId).openLeases.remove(lease);
        return "Lease ended";
    }

    public String getMachineDetails(String machineId, int currentMonth) {
        VendingMachine machine = machines.get(machineId);
        if (machine == null) {
            return "Machine not found";
        }
        return machine.getDetails(currentMonth);
    }

    public String getLeaseDetails(String leaseId, int currentMonth) {
        LeaseAgreement lease = leases.get(leaseId);
        if (lease == null) {
            return "Lease not found";
        }
        return lease.getDetails(currentMonth);
    }
}
```

## Complexity

Let `k` be the number of open leases on one machine. As shown above, `k` is at most 120.

| Method | Time |
| --- | --- |
| `registerMachine`, `changeMachineState`, `calculatePayment`, `getLeaseDetails` | O(1) |
| `createLease`, `endLease`, `getMachineDetails` | O(k) |

Extra space is O(M + L), where M is the number of machines and L is the number of leases.