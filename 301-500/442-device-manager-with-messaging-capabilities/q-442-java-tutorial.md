# Design Device Manager With Message Capabilities

#### Problem Statement

[https://codezym.com/question/442-device-manager-with-messaging-capabilities](https://codezym.com/question/442-device-manager-with-messaging-capabilities)

The core idea is to keep everything about one device inside one small `Device` object, then store all devices in a map by their IDs. Each device has common power information and a set of optional message capabilities. This uses **composition**: a device is built from its state and capabilities instead of creating a different subclass for every possible combination. The manager can find a device quickly, update its power state, and send a message through only the capabilities that it supports.

## What Do We Need to Manage?

Every registered device has three pieces of information:

- Whether it is plugged in.
- Its charging percentage.
- Its supported message capabilities: `DISPLAY`, `SPEAKER`, both, or neither.

The device ID is used to find this information later.

There is one especially important messaging rule: if a device supports both capabilities, the display result must come before the speaker result. This order is required even when the capabilities were registered as `SPEAKER` first and `DISPLAY` second.

## A Simple First Idea

We could store the data in three separate maps:

- One map from device ID to plugged-in state.
- One map from device ID to charging percentage.
- One map from device ID to capabilities.

This can work, but the state of one device is spread across several places. Registering or updating a device requires us to keep those maps synchronized. Forgetting one update can leave the manager with inconsistent data.

Another possibility is to create separate classes such as `DisplayDevice`, `SpeakerDevice`, and `DisplaySpeakerDevice`. That creates unnecessary classes for simple combinations of optional features. It also becomes harder to extend if more capabilities are added later.

## Better Design: One Object Per Device

We create a private `Device` class that stores:

- `pluggedIn`
- `chargingPercentage`
- `capabilities`

The power fields can change, so they are mutable. The capability set does not change after registration, so its reference is `final`.

The manager then uses:

```java
Map<String, Device> devices
```

This lets us find a device by its ID in average `O(1)` time.

## Why Use a Set for Capabilities?

A `HashSet<String>` answers questions such as “does this device support `DISPLAY`?” directly and in average `O(1)` time.

The constructor of `Device` copies the supplied list into a new set:

```java
this.capabilities = new HashSet<>(capabilities);
```

This copy keeps the stored capabilities independent of the caller's list. If that list is changed after registration, the device inside the manager is not affected.

## How Each Method Works

### `registerDevice`

Create a new `Device` with the supplied power data and capabilities, then store it under `deviceId`.

### `updatePowerStatus`

Find the device and replace its plugged-in state and charging percentage. Do not touch its capabilities.

### `isPluggedIn`

Find the device and return its current `pluggedIn` value.

### `getChargingPercentage`

Find the device and return its current `chargingPercentage` value.

### `sendMessage`

Find the device and build a new result list.

Check `DISPLAY` first and `SPEAKER` second. This gives the required output order without depending on the order of the input capability list.

For each supported capability, add its name, a comma, and the original message:

```java
outputs.add(DISPLAY + "," + message);
```

The message is not trimmed, split, or otherwise changed. Its commas, spaces, line breaks, backslashes, Unicode characters, and other text remain part of the message.

Power information is not checked by this method because messaging depends only on the registered capabilities.

## Example Walk-Through

Suppose a device is registered with these capabilities:

```java
List.of("SPEAKER", "DISPLAY")
```

Now we call:

```java
sendMessage("travel-tablet", "Navigation ready")
```

The method checks for `DISPLAY` first, so it adds:

```text
DISPLAY,Navigation ready
```

It then checks for `SPEAKER` and adds:

```text
SPEAKER,Navigation ready
```

The final result is:

```java
List.of("DISPLAY,Navigation ready", "SPEAKER,Navigation ready")
```

The registration order does not affect the result order.


## Complexity Analysis

Let `n` be the number of registered devices, `c` be the number of capabilities supplied for a device, and `m` be the message length. Here, `c` is at most `2`.

- `registerDevice` takes `O(c)` time to copy the capabilities. Because `c <= 2`, this is effectively constant time.
- `updatePowerStatus`, `isPluggedIn`, and `getChargingPercentage` take average `O(1)` time.
- `sendMessage` performs constant-time capability checks. Creating up to two output strings takes `O(m)` time.
- The manager uses `O(n)` space because every device stores only a constant amount of data and at most two capabilities.
- The list returned by `sendMessage` uses `O(m)` space for its output strings.

## Java Solution

```java
import java.util.*;

public class DeviceCapabilityManager {

    private static final String DISPLAY = "DISPLAY";
    private static final String SPEAKER = "SPEAKER";

    private static class Device {
        private boolean pluggedIn;
        private int chargingPercentage;
        private final Set<String> capabilities;

        private Device(boolean pluggedIn, int chargingPercentage, List<String> capabilities) {
            this.pluggedIn = pluggedIn;
            this.chargingPercentage = chargingPercentage;
            this.capabilities = new HashSet<>(capabilities);
        }
    }

    private final Map<String, Device> devices;

    public DeviceCapabilityManager() {
        devices = new HashMap<>();
    }

    public void registerDevice(String deviceId, boolean pluggedIn, int chargingPercentage,
                               List<String> capabilities) {
        devices.put(deviceId, new Device(pluggedIn, chargingPercentage, capabilities));
    }

    public void updatePowerStatus(String deviceId, boolean pluggedIn, int chargingPercentage) {
        Device device = devices.get(deviceId);
        device.pluggedIn = pluggedIn;
        device.chargingPercentage = chargingPercentage;
    }

    public boolean isPluggedIn(String deviceId) {
        return devices.get(deviceId).pluggedIn;
    }

    public int getChargingPercentage(String deviceId) {
        return devices.get(deviceId).chargingPercentage;
    }

    public List<String> sendMessage(String deviceId, String message) {
        Device device = devices.get(deviceId);
        List<String> outputs = new ArrayList<>(2);

        // Check in the required output order, not the registration order.
        if (device.capabilities.contains(DISPLAY)) {
            outputs.add(DISPLAY + "," + message);
        }
        if (device.capabilities.contains(SPEAKER)) {
            outputs.add(SPEAKER + "," + message);
        }

        return outputs;
    }
}
```

## Common Mistakes

- Iterating through the original capability list to build the result. That can produce `SPEAKER` before `DISPLAY`.
- Changing or trimming the message before adding it to the output.
- Clearing or replacing capabilities inside `updatePowerStatus`.
- Refusing to send messages when a device is unplugged or has `0%` charge. Power state does not control message output in this problem.
- Returning `null` for a device with no message capabilities. The required result is an empty list.
