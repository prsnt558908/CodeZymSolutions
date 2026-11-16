# Design Vending Machine Leasing System in Python

#### Problem Statement
[https://codezym.com/question/181-design-vending-machine-leasing-system](https://codezym.com/question/181-design-vending-machine-leasing-system)

## Core Idea

The system keeps two dictionaries, one for vending machines and one for lease agreements. Every method looks up what it needs, runs a few checks, and then reads or updates these objects.

Two parts of this system are meant to grow over time: payment strategies and machine states. So the best fit here is a combination of two design patterns:

- **Strategy pattern** for payments. `MONTHLY` and `ONE_TIME` each become a small class that knows how to calculate the total amount.
- **State pattern** for machine states. `Idle`, `OutOfStock` and `Maintenance` each become a small class that knows if the machine can take a new lease.

Now a new payment plan or a new machine state is just one new class, plus one line to register it. The rest of the code does not change.

Lease status (`ACTIVE`, `UPCOMING`, `EXPIRED`, `ENDED`) is never stored. It is calculated from `currentMonth` every time we need it. We only store whether `endLease` was called.

## A Simple First Version

Let us start with the most direct approach. We store the machine state and the payment strategy as plain text, and use `if` checks on them.

```python
class VendingMachine:
    def __init__(self, machineId, location):
        self.machineId = machineId
        self.location = location
        self.state = "Idle"  # plain text


class LeaseAgreement:
    def __init__(self, leaseId, machineId, customerId, startMonth, endMonth, amount, paymentStrategy):
        # ... store all the fields as they are ...
        self.paymentStrategy = paymentStrategy  # plain text like "MONTHLY"
        self.ended = False
```

To create a lease, we scan every lease in the system to find an overlap, and then check the state text:

```python
for other in self.leases.values():
    sameMachine = other.machineId == machineId
    overlaps = max(startMonth, other.startMonth) <= min(endMonth, other.endMonth)
    if sameMachine and not other.ended and overlaps:
        return "Machine already leased for this period"

if machine.state != "Idle":
    return "Machine not available"
```

The payment is an `if` on the strategy name:

```python
if lease.paymentStrategy == "MONTHLY":
    return lease.amount * (lease.endMonth - lease.startMonth + 1)
return lease.amount  # ONE_TIME
```

This works, but it has three problems.

1. **Payment logic is hard-coded.** Every new payment plan adds one more `if` to `calculatePayment`. We keep editing code that already works.
2. **State rules are hard-coded.** The state is just text, and the rule "only Idle can be leased" lives inside `createLease`. A new state means finding and updating every such check. A typo like `"idle"` also runs without any error.
3. **Slow lookups.** To find an overlap or an active lease, we scan every lease in the whole system. This includes leases of other machines and leases that are already ended.

Let us fix these one by one.

## Step 1: Strategy Pattern for Payments

Each payment plan becomes its own class. All of them extend one abstract base class, so they all have the same methods.

```python
class PaymentStrategy(ABC):
    @abstractmethod
    def getName(self) -> str:
        pass

    @abstractmethod
    def calculate(self, amount: int, numberOfMonths: int) -> int:
        pass


class MonthlyPaymentStrategy(PaymentStrategy):
    def getName(self) -> str:
        return "MONTHLY"

    def calculate(self, amount: int, numberOfMonths: int) -> int:
        return amount * numberOfMonths
```

`OneTimePaymentStrategy` looks the same, but its `calculate` simply returns `amount`.

Each lease stores the strategy object it was created with. The lease does not care which plan it has. It just asks the strategy:

```python
def calculatePayment(self) -> int:
    numberOfMonths = self.endMonth - self.startMonth + 1
    return self.paymentStrategy.calculate(self.amount, numberOfMonths)
```

This always uses the original `startMonth` and `endMonth`. So ending a lease early does not change its payment, just like the problem asks.

To turn the input text `"MONTHLY"` into the right object, the system keeps a dictionary from name to strategy. This dictionary works like a simple factory. To add a new plan like `QUARTERLY`, we write one new class and register it in `__init__`.

## Step 2: State Pattern for Machine States

A machine is always in exactly one state, and the state decides what the machine is allowed to do. Right now there is one such rule: can the machine take a new lease?

```python
class MachineState(ABC):
    @abstractmethod
    def getName(self) -> str:
        pass

    @abstractmethod
    def canBeLeased(self) -> bool:
        pass


class IdleState(MachineState):
    def getName(self) -> str:
        return "Idle"

    def canBeLeased(self) -> bool:
        return True
```

`OutOfStockState` and `MaintenanceState` look the same, but return `False` from `canBeLeased()`.

Now `createLease` just asks the current state:

```python
if not machine.state.canBeLeased():
    return "Machine not available"
```

`changeMachineState` simply swaps the state object. A new state like `Cleaning` is added the same way as a new payment plan, and `createLease` does not change at all.

**State vs Strategy:** both look the same in code. Each is a base class with a few small subclasses. The difference is in how they are used. A lease picks its payment strategy once and keeps it forever. A machine keeps moving from one state to another during its life.

## Step 3: Each Machine Keeps Its Own Open Leases

Instead of scanning every lease in the system, each machine keeps a list of its own leases that were not manually ended. Let us call them open leases.

- `createLease` adds the new lease to this list.
- `endLease` removes it. An ended lease can never block a new lease or be active again, so the machine does not need it anymore.
- Expired leases stay in the list. The rules say they still block overlapping leases until they are manually ended.

Both checks now only look at this short list:

```python
def hasOverlappingLease(self, startMonth: int, endMonth: int) -> bool:
    for lease in self.openLeases:
        if lease.overlaps(startMonth, endMonth):
            return True
    return False
```

`hasActiveLease(currentMonth)` works the same way, but uses `lease.isActive(currentMonth)`. An upcoming lease is not active, so it does not make the machine `LEASED`.

Here is a nice bonus. `createLease` never lets two open leases of a machine overlap, and months only go from 1 to 120. So a machine can never have more than 120 open leases, and every scan stays small.

## Why Not Use State or Observer for Leases?

It is tempting to use the State pattern for leases too, with classes like `ActiveLeaseState` and `ExpiredLeaseState`. Another idea is the Observer pattern, where a lease tells its machine when it starts or ends.

Both sound good on paper, but they do not fit here. A lease moves from upcoming to active to expired only because time passes. The system never gets a "month has changed" event. Each method call simply brings its own `currentMonth`.

So a stored status would quickly go out of date, and there is no event to notify anyone about. The simple fix is to calculate the status every time we need it:

```python
def getStatus(self, currentMonth: int) -> str:
    if self.ended:
        return "ENDED"
    if currentMonth < self.startMonth:
        return "UPCOMING"
    if currentMonth > self.endMonth:
        return "EXPIRED"
    return "ACTIVE"
```

`ENDED` is checked first, because a manually ended lease stays `ENDED` no matter which month we ask about.

## Key Classes

| Class | Why we need it |
| --- | --- |
| `VendingMachineLeasingSystem` | Entry point. Holds two dictionaries, machines by id and leases by id, so every lookup is fast. |
| `VendingMachine` | Holds location, current state and the list of open leases. Checks for overlapping leases and active leases. |
| `LeaseAgreement` | Holds the lease data and one `ended` flag. Calculates its own status and payment. |
| `PaymentStrategy` | One class per payment plan. |
| `MachineState` | One class per machine state. Each state says if a new lease is allowed. |

## Order of Checks Matters

Every method runs its checks in the exact order the problem lists them.

For example, in `createLease` the overlap check comes before the state check. So a machine in `Maintenance` that also has an overlapping lease returns `"Machine already leased for this period"`, not `"Machine not available"`.

## Final Python Code

```python
from abc import ABC, abstractmethod


class PaymentStrategy(ABC):
    """Strategy pattern: each payment plan knows how to calculate the total amount."""

    @abstractmethod
    def getName(self) -> str:
        pass

    @abstractmethod
    def calculate(self, amount: int, numberOfMonths: int) -> int:
        pass


class MonthlyPaymentStrategy(PaymentStrategy):
    # amount is the monthly rent
    def getName(self) -> str:
        return "MONTHLY"

    def calculate(self, amount: int, numberOfMonths: int) -> int:
        return amount * numberOfMonths


class OneTimePaymentStrategy(PaymentStrategy):
    # amount is the fixed price for the whole lease
    def getName(self) -> str:
        return "ONE_TIME"

    def calculate(self, amount: int, numberOfMonths: int) -> int:
        return amount


class MachineState(ABC):
    """State pattern: each machine state decides what the machine is allowed to do."""

    @abstractmethod
    def getName(self) -> str:
        pass

    @abstractmethod
    def canBeLeased(self) -> bool:
        pass


class IdleState(MachineState):
    def getName(self) -> str:
        return "Idle"

    def canBeLeased(self) -> bool:
        return True


class OutOfStockState(MachineState):
    def getName(self) -> str:
        return "OutOfStock"

    def canBeLeased(self) -> bool:
        return False


class MaintenanceState(MachineState):
    def getName(self) -> str:
        return "Maintenance"

    def canBeLeased(self) -> bool:
        return False


class LeaseAgreement:
    """One lease between a customer and a vending machine.
    Status is never stored. It is calculated from currentMonth when asked."""

    def __init__(self, leaseId: str, machineId: str, customerId: str, startMonth: int, endMonth: int,
                 amount: int, paymentStrategy: PaymentStrategy):
        self.leaseId = leaseId
        self.machineId = machineId
        self.customerId = customerId
        self.startMonth = startMonth
        self.endMonth = endMonth
        self.amount = amount
        self.paymentStrategy = paymentStrategy
        self.ended = False  # becomes True once endLease() is called

    def overlaps(self, otherStartMonth: int, otherEndMonth: int) -> bool:
        # two month ranges overlap if they share at least one month
        return max(self.startMonth, otherStartMonth) <= min(self.endMonth, otherEndMonth)

    def isActive(self, currentMonth: int) -> bool:
        return self.getStatus(currentMonth) == "ACTIVE"

    def getStatus(self, currentMonth: int) -> str:
        if self.ended:
            return "ENDED"
        if currentMonth < self.startMonth:
            return "UPCOMING"
        if currentMonth > self.endMonth:
            return "EXPIRED"
        return "ACTIVE"

    def calculatePayment(self) -> int:
        # always uses the original period, even if the lease was ended early
        numberOfMonths = self.endMonth - self.startMonth + 1
        return self.paymentStrategy.calculate(self.amount, numberOfMonths)

    def getDetails(self, currentMonth: int) -> str:
        return (f"{self.leaseId},{self.machineId},{self.customerId},"
                f"{self.startMonth},{self.endMonth},{self.paymentStrategy.getName()},"
                f"{self.amount},{self.getStatus(currentMonth)}")


class VendingMachine:
    """A vending machine with its location, current state and open leases."""

    def __init__(self, machineId: str, location: str, state: MachineState):
        self.machineId = machineId
        self.location = location
        self.state = state
        # Leases of this machine that were not manually ended.
        # Open leases never overlap and months go from 1 to 120,
        # so this list never holds more than 120 leases.
        self.openLeases = []

    def hasOverlappingLease(self, startMonth: int, endMonth: int) -> bool:
        for lease in self.openLeases:
            if lease.overlaps(startMonth, endMonth):
                return True
        return False

    def hasActiveLease(self, currentMonth: int) -> bool:
        for lease in self.openLeases:
            if lease.isActive(currentMonth):
                return True
        return False

    def getDetails(self, currentMonth: int) -> str:
        leasedStatus = "LEASED" if self.hasActiveLease(currentMonth) else "AVAILABLE"
        return f"{self.machineId},{self.location},{self.state.getName()},{leasedStatus}"


class VendingMachineLeasingSystem:
    """Entry point. Stores all machines and leases.
    Every method runs its checks in the exact order given in the problem."""

    def __init__(self):
        self.machines = {}  # machineId -> VendingMachine
        self.leases = {}  # leaseId -> LeaseAgreement

        # name -> object lookups, these work like a simple factory
        self.paymentStrategies = {}
        self.machineStates = {}

        # To support a new payment plan or machine state,
        # write its class and register it here. Nothing else changes.
        self.addPaymentStrategy(MonthlyPaymentStrategy())
        self.addPaymentStrategy(OneTimePaymentStrategy())

        self.addMachineState(IdleState())
        self.addMachineState(OutOfStockState())
        self.addMachineState(MaintenanceState())

    def addPaymentStrategy(self, strategy: PaymentStrategy) -> None:
        self.paymentStrategies[strategy.getName()] = strategy

    def addMachineState(self, state: MachineState) -> None:
        self.machineStates[state.getName()] = state

    def registerMachine(self, machineId: str, location: str) -> str:
        if machineId in self.machines:
            return "Machine already exists"
        self.machines[machineId] = VendingMachine(machineId, location, self.machineStates["Idle"])
        return "Machine registered"

    def createLease(self, leaseId: str, machineId: str, customerId: str, startMonth: int, endMonth: int,
                    currentMonth: int, paymentStrategy: str, amount: int) -> str:
        if leaseId in self.leases:
            return "Lease already exists"
        machine = self.machines.get(machineId)
        if machine is None:
            return "Machine not found"
        if endMonth < currentMonth:
            return "Lease period already expired"
        if machine.hasOverlappingLease(startMonth, endMonth):
            return "Machine already leased for this period"
        if not machine.state.canBeLeased():
            return "Machine not available"

        lease = LeaseAgreement(leaseId, machineId, customerId, startMonth, endMonth,
                               amount, self.paymentStrategies[paymentStrategy])
        self.leases[leaseId] = lease
        machine.openLeases.append(lease)
        return "Lease created"

    def changeMachineState(self, machineId: str, newState: str) -> str:
        machine = self.machines.get(machineId)
        if machine is None:
            return "Machine not found"
        machine.state = self.machineStates[newState]
        return "State changed"

    def calculatePayment(self, leaseId: str) -> int:
        lease = self.leases.get(leaseId)
        if lease is None:
            return -1
        return lease.calculatePayment()

    def endLease(self, leaseId: str, currentMonth: int) -> str:
        lease = self.leases.get(leaseId)
        if lease is None:
            return "Lease not found"
        if lease.ended:
            return "Lease already ended"
        if currentMonth < lease.startMonth:
            return "Lease not started"
        lease.ended = True
        # an ended lease can never block or be active again,
        # so the machine does not need to keep it
        self.machines[lease.machineId].openLeases.remove(lease)
        return "Lease ended"

    def getMachineDetails(self, machineId: str, currentMonth: int) -> str:
        machine = self.machines.get(machineId)
        if machine is None:
            return "Machine not found"
        return machine.getDetails(currentMonth)

    def getLeaseDetails(self, leaseId: str, currentMonth: int) -> str:
        lease = self.leases.get(leaseId)
        if lease is None:
            return "Lease not found"
        return lease.getDetails(currentMonth)
```

## Complexity

Let `k` be the number of open leases on one machine. As shown above, `k` is at most 120.

| Method | Time |
| --- | --- |
| `registerMachine`, `changeMachineState`, `calculatePayment`, `getLeaseDetails` | O(1) |
| `createLease`, `endLease`, `getMachineDetails` | O(k) |

Extra space is O(M + L), where M is the number of machines and L is the number of leases.