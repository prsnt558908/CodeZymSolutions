# Design Alert Monitoring System in Python

#### Problem Statement
[https://codezym.com/question/359-alert-monitoring-system](https://codezym.com/question/359-alert-monitoring-system)

The system does three jobs: remember the allowed range of every sensor, turn every out-of-range reading into an alert, and let users view alerts in order of their id and change their state.

We model it with three small building blocks: a `Sensor` class that knows its own range, an `Alert` class that stores one alert and prints itself, and an `AlertState` enum for the four possible states.

A design pattern is not needed here. State and Observer look tempting, but alert states do not change any behavior and nobody needs to be notified. Plain classes plus the right data structures give the cleanest solution.

We first build a simple version that keeps alerts in a list, see where it gets slow, and then fix it using a dictionary for direct lookups and a sorted list that keeps alert ids in order.

## Understanding the Problem

- A sensor is identified by `machineId` + `sensorType` and has an inclusive range `[lowerThreshold, upperThreshold]`.
- A reading creates an alert only when `value < lowerThreshold` or `value > upperThreshold`. A value equal to a threshold is fine.
- Every reading comes with an `alertId`. It is used only if an alert is created, otherwise it is thrown away.
- A new alert always starts as `TRIGGERED`. Users can later set it to `ACKNOWLEDGED`, `RESOLVED` or `IGNORED`.
- `viewAlerts()` returns all alerts sorted by `alertId`, each in the format `alertId,machineId,sensorType,value,state`.

## Do We Need a Design Pattern?

Two patterns sound like a good fit on paper.

**State pattern:** alerts have states, so it is tempting to create one class per state. But here a state is only a label. It does not change how an alert behaves, and any state can be set at any time with no rules. Four state classes would add code that does nothing, so an enum is enough.

**Observer pattern:** real monitoring systems notify people when an alert fires. But this problem has no listeners and no notifications. Observer would only add classes that nothing uses.

So we go without a design pattern. If notifications are added later, Observer is the natural place to start.

## The Building Blocks

These classes stay exactly the same in both solutions below.

**`AlertState` (Enum):** holds the four allowed states. An enum is safer than plain strings because a typo like `AlertState.RESOLVD` fails right away instead of quietly storing a wrong state. `AlertState(newState)` turns the input string into a state.

**`Sensor`:** stores one sensor's range and owns the check `is_out_of_range(value)`. The rule "a value equal to a threshold is allowed" lives in this one method, so it is easy to read and easy to change.

**`Alert`:** stores the reading that caused the alert (`alert_id`, `machine_id`, `sensor_type`, `value`) plus its current `state`. Only the state changes after creation. Its `__str__` builds the required output format, so `recordSensorValue` and `viewAlerts` always print an alert the same way.

The value is stored as `float(value)`, so a whole number like `81` is still printed as `81.0`, exactly like the expected output.

**`sensors` dictionary:** a sensor is identified by two strings, so we use the tuple `(machineId, sensorType)` as the key. Tuples can be dictionary keys in Python, so we get any sensor in O(1) without building a combined string.

## Solution 1: Simple Approach Using a List

The most direct idea is to keep every alert in a plain list.

- **addSensor:** save the sensor in the dictionary.
- **recordSensorValue:** find the sensor and check the value. If it is out of range, create an `Alert`, append it to the list and return its text. Otherwise return `""`.
- **viewAlerts:** sort the list by alert id, then convert each alert to text.
- **updateAlertState:** check alerts one by one until the id matches, then change its state. Return `False` if nothing matches.

### Problems with this approach

- **Slow updates:** `updateAlertState` may look at every alert before it finds a match, or before it learns that the id does not exist. With 100,000 alerts, a single update can take 100,000 checks.
- **Repeated sorting:** `viewAlerts` sorts all alerts on every call, even when nothing has changed since the last call.

### Complexity

Here `n` is the number of alerts.

| Method | Time |
|---|---|
| `addSensor` | O(1) |
| `recordSensorValue` | O(1) |
| `viewAlerts` | O(n log n) |
| `updateAlertState` | O(n) |

### Code

```python
from typing import List
from enum import Enum


class AlertState(Enum):
    """The four states an alert can be in. Every new alert starts as TRIGGERED."""
    TRIGGERED = "TRIGGERED"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    RESOLVED = "RESOLVED"
    IGNORED = "IGNORED"


class Sensor:
    """A sensor on a machine along with its inclusive allowed range."""

    def __init__(self, machine_id: str, sensor_type: str, lower_threshold: float, upper_threshold: float):
        self.machine_id = machine_id
        self.sensor_type = sensor_type
        self.lower_threshold = lower_threshold
        self.upper_threshold = upper_threshold

    def is_out_of_range(self, value: float) -> bool:
        # a value equal to either threshold is still inside the allowed range
        return value < self.lower_threshold or value > self.upper_threshold


class Alert:
    """An alert created by one out-of-range reading. Only its state can change later."""

    def __init__(self, alert_id: int, machine_id: str, sensor_type: str, value: float):
        self.alert_id = alert_id
        self.machine_id = machine_id
        self.sensor_type = sensor_type
        self.value = float(value)  # float() makes sure 81 is printed as 81.0
        self.state = AlertState.TRIGGERED

    def __str__(self) -> str:
        # format: alertId,machineId,sensorType,value,state
        return f"{self.alert_id},{self.machine_id},{self.sensor_type},{self.value},{self.state.value}"


class AlertMonitoringSystem:
    def __init__(self):
        self.sensors = {}  # (machineId, sensorType) -> Sensor
        self.alerts = []   # every alert, in the order it was created

    def addSensor(self, machineId: str, sensorType: str, lowerThreshold: float, upperThreshold: float):
        self.sensors[(machineId, sensorType)] = Sensor(machineId, sensorType, lowerThreshold, upperThreshold)

    def recordSensorValue(self, alertId: int, machineId: str, sensorType: str, value: float) -> str:
        sensor = self.sensors.get((machineId, sensorType))

        # value is inside the range: no alert, the alertId is simply discarded
        if sensor is None or not sensor.is_out_of_range(value):
            return ""

        alert = Alert(alertId, machineId, sensorType, value)
        self.alerts.append(alert)
        return str(alert)

    def viewAlerts(self) -> List[str]:
        # sort the whole list by alert id on every call
        self.alerts.sort(key=lambda alert: alert.alert_id)
        return [str(alert) for alert in self.alerts]

    def updateAlertState(self, alertId: int, newState: str) -> bool:
        # check alerts one by one until the id matches
        for alert in self.alerts:
            if alert.alert_id == alertId:
                alert.state = AlertState(newState)
                return True
        return False
```

## Solution 2: Optimized Approach Using a Dictionary and a Sorted List

Both problems come from using a plain list of alerts: it cannot find an alert by id quickly, and it does not keep itself sorted. So we replace it with two structures that work as a team.

**`alerts_by_id` dictionary:** jumps straight to an alert using its id. `updateAlertState` becomes a single O(1) lookup instead of a full scan.

**`sorted_alert_ids` list:** Python has no built-in sorted set, so we keep the ids in a plain list and make sure it always stays sorted. `bisect.insort` uses binary search to find where a new id belongs and inserts it right there. `viewAlerts` then simply walks the list from the smallest id to the largest. No sorting needed.

The dictionary answers "where is alert 72?" and the sorted list answers "which alert comes next?". Every new alert is added to both. Since every `alertId` is unique, an existing alert is never overwritten.

Inserting into the middle of a list shifts the ids after it, so `recordSensorValue` is O(n) in the worst case. This shift runs in fast C code, so it stays cheap in practice, and in return updates become O(1) and views never sort.

### Dry Run

These are the calls from the problem examples. Assume alert `18` (`Machine-C`, `Temperature`, `-6.0`) was created earlier.

| Call | What happens | Returns |
|---|---|---|
| `addSensor("Machine-A", "Temperature", 10.0, 40.0)` | sensor saved under the key `("Machine-A", "Temperature")` | nothing |
| `recordSensorValue(45, "Machine-A", "Temperature", 28.5)` | 28.5 is between 10.0 and 40.0, so id 45 is thrown away | `""` |
| `addSensor("Machine-B", "Pressure", 20.0, 75.0)` | sensor saved under the key `("Machine-B", "Pressure")` | nothing |
| `recordSensorValue(72, "Machine-B", "Pressure", 81.0)` | 81.0 > 75.0, so alert 72 goes into the dictionary and the sorted list | `"72,Machine-B,Pressure,81.0,TRIGGERED"` |
| `updateAlertState(72, "ACKNOWLEDGED")` | the dictionary finds alert 72 and its state changes | `True` |
| `viewAlerts()` | the sorted list gives 18 first, then 72 | `["18,Machine-C,Temperature,-6.0,TRIGGERED", "72,Machine-B,Pressure,81.0,ACKNOWLEDGED"]` |
| `updateAlertState(45, "RESOLVED")` | 45 was thrown away, so it is not in the dictionary | `False` |

### Complexity

| Method | Time |
|---|---|
| `addSensor` | O(1) |
| `recordSensorValue` | O(log n) to find the spot, O(n) worst case to shift |
| `viewAlerts` | O(n) |
| `updateAlertState` | O(1) |

`viewAlerts` has to return all `n` alerts anyway, so O(n) is the best it can do. Space is O(s + n), where `s` is the number of sensors.

### Code

```python
from typing import List
from enum import Enum
import bisect


class AlertState(Enum):
    """The four states an alert can be in. Every new alert starts as TRIGGERED."""
    TRIGGERED = "TRIGGERED"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    RESOLVED = "RESOLVED"
    IGNORED = "IGNORED"


class Sensor:
    """A sensor on a machine along with its inclusive allowed range."""

    def __init__(self, machine_id: str, sensor_type: str, lower_threshold: float, upper_threshold: float):
        self.machine_id = machine_id
        self.sensor_type = sensor_type
        self.lower_threshold = lower_threshold
        self.upper_threshold = upper_threshold

    def is_out_of_range(self, value: float) -> bool:
        # a value equal to either threshold is still inside the allowed range
        return value < self.lower_threshold or value > self.upper_threshold


class Alert:
    """An alert created by one out-of-range reading. Only its state can change later."""

    def __init__(self, alert_id: int, machine_id: str, sensor_type: str, value: float):
        self.alert_id = alert_id
        self.machine_id = machine_id
        self.sensor_type = sensor_type
        self.value = float(value)  # float() makes sure 81 is printed as 81.0
        self.state = AlertState.TRIGGERED

    def __str__(self) -> str:
        # format: alertId,machineId,sensorType,value,state
        return f"{self.alert_id},{self.machine_id},{self.sensor_type},{self.value},{self.state.value}"


class AlertMonitoringSystem:
    def __init__(self):
        self.sensors = {}           # (machineId, sensorType) -> Sensor
        self.alerts_by_id = {}      # alertId -> Alert, finds any alert in O(1) for updates
        self.sorted_alert_ids = []  # every alert id, always kept in increasing order

    def addSensor(self, machineId: str, sensorType: str, lowerThreshold: float, upperThreshold: float):
        self.sensors[(machineId, sensorType)] = Sensor(machineId, sensorType, lowerThreshold, upperThreshold)

    def recordSensorValue(self, alertId: int, machineId: str, sensorType: str, value: float) -> str:
        sensor = self.sensors.get((machineId, sensorType))

        # value is inside the range: no alert, the alertId is simply discarded
        if sensor is None or not sensor.is_out_of_range(value):
            return ""

        alert = Alert(alertId, machineId, sensorType, value)
        self.alerts_by_id[alertId] = alert
        # binary search finds the right spot, so the list stays sorted
        bisect.insort(self.sorted_alert_ids, alertId)
        return str(alert)

    def viewAlerts(self) -> List[str]:
        # ids are already in increasing order, no sorting needed here
        return [str(self.alerts_by_id[alert_id]) for alert_id in self.sorted_alert_ids]

    def updateAlertState(self, alertId: int, newState: str) -> bool:
        alert = self.alerts_by_id.get(alertId)
        if alert is None:
            return False
        alert.state = AlertState(newState)
        return True
```