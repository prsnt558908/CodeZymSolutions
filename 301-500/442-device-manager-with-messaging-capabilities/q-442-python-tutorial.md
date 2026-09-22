# Design Device Manager With Message Capabilities

#### Problem Statement

[https://codezym.com/question/442-device-manager-with-messaging-capabilities](https://codezym.com/question/442-device-manager-with-messaging-capabilities)

The core idea is to keep all information about one device inside one small `Device` dataclass and store the devices in a dictionary by their IDs. Each device contains its common power state and a set of optional message capabilities. This uses **composition**: one device is made from its state and capabilities instead of creating a different class for every capability combination. The manager can then find a device quickly, update its power information, and create message outputs only for the capabilities it supports.

## What Do We Need to Store?

Every device has:

- A plugged-in state.
- A charging percentage.
- Zero, one, or two message capabilities: `DISPLAY` and `SPEAKER`.

We also need the device ID, but it does not have to be repeated inside the `Device` object. It is already the key in the dictionary.

One messaging rule needs special care: when both capabilities are present, the display output must always come before the speaker output. The order in which the capabilities were registered must not change this result.

## A Simple First Idea

We could create three separate dictionaries:

- One for plugged-in states.
- One for charging percentages.
- One for capabilities.

This would work, but the information for one device would be spread across three places. Registration and updates would have to keep all of them synchronized. If one dictionary were missed, the manager could contain inconsistent state.

We could also create separate classes for display-only, speaker-only, both-capability, and no-capability devices. That creates several classes for four very small combinations and becomes harder to extend when a new capability is added.

## Better Design: One Dataclass Per Device

Python's `@dataclass` is a good fit for a small object whose main purpose is to hold related data:

```python
@dataclass
class Device:
    plugged_in: bool
    charging_percentage: int
    capabilities: Set[str]
```

The two power fields may change. The capability set is created during registration and remains unchanged when the power state is updated.

The manager stores these objects in:

```python
self.devices: Dict[str, Device] = {}
```

This keeps each device's information together while allowing average `O(1)` lookup by device ID.

## Why Store Capabilities in a Set?

The public method receives capabilities as a list because that is the required method signature. Internally, we convert it to a set:

```python
capabilities=set(capabilities)
```

A set lets us directly ask whether `"DISPLAY"` or `"SPEAKER"` is supported. These membership checks take average `O(1)` time.

Creating a new set also makes a defensive copy. Changing the original input list after registration cannot change the capabilities stored by the manager.

## How Each Method Works

### `registerDevice`

Create a `Device` from the supplied power information and capabilities. Store it in the dictionary using `deviceId` as the key.

### `updatePowerStatus`

Find the device and replace only `plugged_in` and `charging_percentage`. Its capability set remains unchanged.

### `isPluggedIn`

Find the device and return its current `plugged_in` value.

### `getChargingPercentage`

Find the device and return its current `charging_percentage` value.

### `sendMessage`

Create a new empty output list for every call.

First check whether the device supports `DISPLAY`. If it does, append `"DISPLAY,"` followed by the original message.

Then check whether it supports `SPEAKER`. If it does, append `"SPEAKER,"` followed by the original message.

Checking the capabilities in this fixed order guarantees that display output comes first. We must not iterate over the capability set to choose the result order because a set does not preserve the required ordering.

The message itself is never parsed, stripped, or changed. Spaces, commas, line breaks, tabs, backslashes, Unicode characters, and all other message content remain untouched.

The power state is not checked because message output depends only on capabilities. An unplugged device or a device at `0%` charge still produces its supported message outputs.

## Example Walk-Through

Suppose we register a device using:

```python
manager.registerDevice(
    deviceId="travel-tablet",
    pluggedIn=False,
    chargingPercentage=58,
    capabilities=["SPEAKER", "DISPLAY"],
)
```

Now consider this call:

```python
manager.sendMessage(
    deviceId="travel-tablet",
    message="Navigation ready",
)
```

The method checks `DISPLAY` first and then `SPEAKER`, so it returns:

```python
[
    "DISPLAY,Navigation ready",
    "SPEAKER,Navigation ready",
]
```

The result order is correct even though `SPEAKER` appeared first during registration.


## Complexity Analysis

Let `n` be the number of registered devices, `c` be the number of capabilities supplied for a device, and `m` be the message length. The problem limits `c` to at most `2`.

- `registerDevice` takes `O(c)` time to copy the capabilities into a set. Since `c <= 2`, this is effectively constant time.
- `updatePowerStatus`, `isPluggedIn`, and `getChargingPercentage` take average `O(1)` time.
- `sendMessage` uses constant-time capability checks. Creating up to two output strings takes `O(m)` time.
- The manager uses `O(n)` space because every device stores a constant amount of state and at most two capabilities.
- The result of `sendMessage` uses `O(m)` space for its output strings.

## Python Solution

```python
from dataclasses import dataclass
from typing import Dict, List, Set


@dataclass
class Device:
    plugged_in: bool
    charging_percentage: int
    capabilities: Set[str]


class DeviceCapabilityManager:
    def __init__(self):
        self.devices: Dict[str, Device] = {}

    def registerDevice(
        self,
        deviceId: str,
        pluggedIn: bool,
        chargingPercentage: int,
        capabilities: List[str],
    ) -> None:
        self.devices[deviceId] = Device(
            plugged_in=pluggedIn,
            charging_percentage=chargingPercentage,
            capabilities=set(capabilities),
        )

    def updatePowerStatus(
        self, deviceId: str, pluggedIn: bool, chargingPercentage: int
    ) -> None:
        device = self.devices[deviceId]
        device.plugged_in = pluggedIn
        device.charging_percentage = chargingPercentage

    def isPluggedIn(self, deviceId: str) -> bool:
        return self.devices[deviceId].plugged_in

    def getChargingPercentage(self, deviceId: str) -> int:
        return self.devices[deviceId].charging_percentage

    def sendMessage(self, deviceId: str, message: str) -> List[str]:
        device = self.devices[deviceId]
        outputs: List[str] = []

        # Check in the required output order, not the registration order.
        if "DISPLAY" in device.capabilities:
            outputs.append("DISPLAY," + message)
        if "SPEAKER" in device.capabilities:
            outputs.append("SPEAKER," + message)

        return outputs
```

## Common Mistakes

- Iterating over the input list or capability set to decide the output order. `DISPLAY` must always come first.
- Calling `strip()` or otherwise changing the message before creating an output record.
- Replacing the capabilities inside `updatePowerStatus`.
- Preventing messages when the device is unplugged or has `0%` charge.
- Returning `None` when no message capability is supported. The required result is an empty list.
