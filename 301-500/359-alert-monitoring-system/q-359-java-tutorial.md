# Design Alert Monitoring System in Java

#### Problem Statement
[https://codezym.com/question/359-alert-monitoring-system](https://codezym.com/question/359-alert-monitoring-system)

The system does three jobs: remember the allowed range of every sensor, turn every out-of-range reading into an alert, and let users view alerts in order of their id and change their state.

We model it with three small building blocks: a `Sensor` class that knows its own range, an `Alert` class that stores one alert and prints itself, and an `AlertState` enum for the four possible states.

A design pattern is not needed here. State and Observer look tempting, but alert states do not change any behavior and nobody needs to be notified. Plain classes plus the right data structures give the cleanest solution.

We first build a simple version that keeps alerts in a list, see where it gets slow, and then fix it using a map for direct lookups and a sorted set that keeps alert ids in order.

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

These types stay exactly the same in both solutions below.

**`AlertState` (enum):** holds the four allowed states. An enum is safer than plain strings because a typo becomes a compile error. `AlertState.valueOf(newState)` turns the input string into a state.

**`Sensor`:** stores one sensor's range and owns the check `isOutOfRange(value)`. The rule "a value equal to a threshold is allowed" lives in this one method, so it is easy to read and easy to change.

**`Alert`:** stores the reading that caused the alert (`alertId`, `machineId`, `sensorType`, `value`) plus its current `state`. Only the state changes after creation. Its `toString()` builds the required output format, so `recordSensorValue` and `viewAlerts` always print an alert the same way.

**`Map<String, Sensor> sensors`:** a sensor is identified by two strings, so we join them into one key like `"Machine-A#Temperature"`. This gives us any sensor in O(1).

The `#` separator matters. Without it, machine `"AB"` with sensor `"C"` and machine `"A"` with sensor `"BC"` would both become `"ABC"`. Ids only contain letters, digits, hyphens and underscores, so `#` never appears inside them and every sensor gets a unique key.

## Solution 1: Simple Approach Using a List

The most direct idea is to keep every alert in a plain `List<Alert>`.

- **addSensor:** save the sensor in the map.
- **recordSensorValue:** find the sensor and check the value. If it is out of range, create an `Alert`, add it to the list and return its text. Otherwise return `""`.
- **viewAlerts:** sort the list by `alertId`, then convert each alert to text.
- **updateAlertState:** check alerts one by one until the id matches, then change its state. Return `false` if nothing matches.

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

```java
import java.util.*;

/**
 * The four states an alert can be in.
 * Every new alert starts as TRIGGERED.
 */
enum AlertState {
    TRIGGERED, ACKNOWLEDGED, RESOLVED, IGNORED
}

/**
 * A sensor on a machine along with its inclusive allowed range.
 */
class Sensor {
    String machineId;
    String sensorType;
    double lowerThreshold;
    double upperThreshold;

    Sensor(String machineId, String sensorType, double lowerThreshold, double upperThreshold) {
        this.machineId = machineId;
        this.sensorType = sensorType;
        this.lowerThreshold = lowerThreshold;
        this.upperThreshold = upperThreshold;
    }

    /** A value equal to either threshold is still inside the allowed range. */
    boolean isOutOfRange(double value) {
        return value < lowerThreshold || value > upperThreshold;
    }
}

/**
 * An alert created by one out-of-range reading.
 * Only its state can change after it is created.
 */
class Alert {
    int alertId;
    String machineId;
    String sensorType;
    double value;
    AlertState state;

    Alert(int alertId, String machineId, String sensorType, double value) {
        this.alertId = alertId;
        this.machineId = machineId;
        this.sensorType = sensorType;
        this.value = value;
        this.state = AlertState.TRIGGERED;
    }

    /** Format: alertId,machineId,sensorType,value,state */
    @Override
    public String toString() {
        return alertId + "," + machineId + "," + sensorType + "," + value + "," + state;
    }
}

public class AlertMonitoringSystem {
    // "machineId#sensorType" -> sensor
    Map<String, Sensor> sensors = new HashMap<>();

    // every alert, in the order it was created
    List<Alert> alerts = new ArrayList<>();

    public AlertMonitoringSystem() {
    }

    public void addSensor(String machineId, String sensorType, double lowerThreshold, double upperThreshold) {
        sensors.put(sensorKey(machineId, sensorType),
                new Sensor(machineId, sensorType, lowerThreshold, upperThreshold));
    }

    public String recordSensorValue(int alertId, String machineId, String sensorType, double value) {
        Sensor sensor = sensors.get(sensorKey(machineId, sensorType));

        // value is inside the range: no alert, the alertId is simply discarded
        if (sensor == null || !sensor.isOutOfRange(value)) {
            return "";
        }

        Alert alert = new Alert(alertId, machineId, sensorType, value);
        alerts.add(alert);
        return alert.toString();
    }

    public List<String> viewAlerts() {
        // sort the whole list by alert id on every call
        alerts.sort((a, b) -> Integer.compare(a.alertId, b.alertId));

        List<String> result = new ArrayList<>();
        for (Alert alert : alerts) {
            result.add(alert.toString());
        }
        return result;
    }

    public boolean updateAlertState(int alertId, String newState) {
        // check alerts one by one until the id matches
        for (Alert alert : alerts) {
            if (alert.alertId == alertId) {
                alert.state = AlertState.valueOf(newState);
                return true;
            }
        }
        return false;
    }

    /** Ids never contain '#', so two different sensors can never get the same key. */
    String sensorKey(String machineId, String sensorType) {
        return machineId + "#" + sensorType;
    }
}
```

## Solution 2: Optimized Approach Using a Map and a Sorted Set

Both problems come from using a plain list: it cannot find an alert by id quickly, and it does not keep itself sorted. So we replace the list with two structures that work as a team.

**`Map<Integer, Alert> alertsById`:** jumps straight to an alert using its id. `updateAlertState` becomes a single O(1) lookup instead of a full scan.

**`TreeSet<Integer> sortedAlertIds`:** a sorted set that keeps every alert id in increasing order at all times. Adding an id costs O(log n), and `viewAlerts` simply walks through the set from the smallest id to the largest. No sorting needed.

The map answers "where is alert 72?" and the sorted set answers "which alert comes next?". Every new alert is added to both. Since every `alertId` is unique, an existing alert is never overwritten.

### Dry Run

These are the calls from the problem examples. Assume alert `18` (`Machine-C`, `Temperature`, `-6.0`) was created earlier.

| Call | What happens | Returns |
|---|---|---|
| `addSensor("Machine-A", "Temperature", 10.0, 40.0)` | sensor saved under `"Machine-A#Temperature"` | nothing |
| `recordSensorValue(45, "Machine-A", "Temperature", 28.5)` | 28.5 is between 10.0 and 40.0, so id 45 is thrown away | `""` |
| `addSensor("Machine-B", "Pressure", 20.0, 75.0)` | sensor saved under `"Machine-B#Pressure"` | nothing |
| `recordSensorValue(72, "Machine-B", "Pressure", 81.0)` | 81.0 > 75.0, so alert 72 goes into the map and the sorted set | `"72,Machine-B,Pressure,81.0,TRIGGERED"` |
| `updateAlertState(72, "ACKNOWLEDGED")` | the map finds alert 72 and its state changes | `true` |
| `viewAlerts()` | the sorted set gives 18 first, then 72 | `["18,Machine-C,Temperature,-6.0,TRIGGERED", "72,Machine-B,Pressure,81.0,ACKNOWLEDGED"]` |
| `updateAlertState(45, "RESOLVED")` | 45 was thrown away, so it is not in the map | `false` |

### Complexity

| Method | Time |
|---|---|
| `addSensor` | O(1) |
| `recordSensorValue` | O(log n) |
| `viewAlerts` | O(n) |
| `updateAlertState` | O(1) |

`viewAlerts` has to return all `n` alerts anyway, so O(n) is the best it can do. Space is O(s + n), where `s` is the number of sensors.

### Code

```java
import java.util.*;

/**
 * The four states an alert can be in.
 * Every new alert starts as TRIGGERED.
 */
enum AlertState {
    TRIGGERED, ACKNOWLEDGED, RESOLVED, IGNORED
}

/**
 * A sensor on a machine along with its inclusive allowed range.
 */
class Sensor {
    String machineId;
    String sensorType;
    double lowerThreshold;
    double upperThreshold;

    Sensor(String machineId, String sensorType, double lowerThreshold, double upperThreshold) {
        this.machineId = machineId;
        this.sensorType = sensorType;
        this.lowerThreshold = lowerThreshold;
        this.upperThreshold = upperThreshold;
    }

    /** A value equal to either threshold is still inside the allowed range. */
    boolean isOutOfRange(double value) {
        return value < lowerThreshold || value > upperThreshold;
    }
}

/**
 * An alert created by one out-of-range reading.
 * Only its state can change after it is created.
 */
class Alert {
    int alertId;
    String machineId;
    String sensorType;
    double value;
    AlertState state;

    Alert(int alertId, String machineId, String sensorType, double value) {
        this.alertId = alertId;
        this.machineId = machineId;
        this.sensorType = sensorType;
        this.value = value;
        this.state = AlertState.TRIGGERED;
    }

    /** Format: alertId,machineId,sensorType,value,state */
    @Override
    public String toString() {
        return alertId + "," + machineId + "," + sensorType + "," + value + "," + state;
    }
}

public class AlertMonitoringSystem {
    // "machineId#sensorType" -> sensor
    Map<String, Sensor> sensors = new HashMap<>();

    // alertId -> alert, finds any alert in O(1) for updates
    Map<Integer, Alert> alertsById = new HashMap<>();

    // every alert id, always kept in increasing order
    TreeSet<Integer> sortedAlertIds = new TreeSet<>();

    public AlertMonitoringSystem() {
    }

    public void addSensor(String machineId, String sensorType, double lowerThreshold, double upperThreshold) {
        sensors.put(sensorKey(machineId, sensorType),
                new Sensor(machineId, sensorType, lowerThreshold, upperThreshold));
    }

    public String recordSensorValue(int alertId, String machineId, String sensorType, double value) {
        Sensor sensor = sensors.get(sensorKey(machineId, sensorType));

        // value is inside the range: no alert, the alertId is simply discarded
        if (sensor == null || !sensor.isOutOfRange(value)) {
            return "";
        }

        Alert alert = new Alert(alertId, machineId, sensorType, value);
        alertsById.put(alertId, alert);
        sortedAlertIds.add(alertId);
        return alert.toString();
    }

    public List<String> viewAlerts() {
        List<String> result = new ArrayList<>();

        // the sorted set already gives ids in increasing order, no sorting needed here
        for (int alertId : sortedAlertIds) {
            result.add(alertsById.get(alertId).toString());
        }
        return result;
    }

    public boolean updateAlertState(int alertId, String newState) {
        Alert alert = alertsById.get(alertId);
        if (alert == null) {
            return false;
        }
        alert.state = AlertState.valueOf(newState);
        return true;
    }

    /** Ids never contain '#', so two different sensors can never get the same key. */
    String sensorKey(String machineId, String sensorType) {
        return machineId + "#" + sensorType;
    }
}
```