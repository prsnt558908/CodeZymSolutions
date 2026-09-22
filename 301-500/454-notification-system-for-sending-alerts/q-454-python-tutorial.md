# Design an Extensible Notification System in Python

#### Problem Statement

[https://codezym.com/question/454-notification-system-for-sending-alerts](https://codezym.com/question/454-notification-system-for-sending-alerts)

The core idea is to keep a small **registry** of supported categories and channels, validate the entire request, and then create one log for each unique recipient and requested channel. Sets make membership checks fast, while lists preserve the required order. This simple registry approach fits the problem well: adding a category or channel means adding a name to a collection. A Strategy or Factory layer is unnecessary here because every channel produces the same kind of log, and actual delivery is outside the problem's scope.

## What Does the Platform Need to Remember?

Imagine a platform that supports the categories `SECURITY` and `PAYMENT`, with channels registered in this order: `EMAIL`, `SMS`, `PUSH`.

A request supplies an alert ID, category, message, recipients, and requested channels. The platform returns strings describing the dispatch requests.

The important rules are:

- The category and every requested channel must already be registered.
- Each recipient appears once, in the order of their first occurrence.
- Each requested channel appears once per recipient, in **registration order**.
- A successful alert ID cannot be used again, even with a different category or message.
- An invalid request returns an empty list and leaves the platform unchanged.

New categories and channels can be registered later. A new channel goes after all previously registered channels.

## Start with a Simple List-Based Approach

We could store categories, channels, and successful alert IDs in separate lists. For each request, we would search those lists, remove duplicates, and build the logs.

This can work, but repeated searches become expensive. For example, checking whether an alert ID was used before could scan as many as 100,000 earlier IDs. Removing duplicate recipients by repeatedly searching another list also repeats work.

We can improve this without changing the overall idea. Use a **set when we need to ask whether a value exists**, and a **list when we need to preserve order**.

There is one more useful improvement. We should choose the requested channels in registration order once per alert. Every recipient can then reuse that list. Otherwise, we would scan all registered channels again for every recipient.

## Choose Simple Data Structures

The `AlertingPlatform` class owns all persistent state. Each platform instance gets its own collections. It is declared as a dataclass so these state fields are described together, while its explicit `__init__` keeps the required constructor API.

### Supported categories: a set

`_supported_categories` is a Python `set`.

We only need to check whether a category exists and add new categories. Category order does not affect the output. A set makes duplicate registration easy: the method checks membership before calling `add`, so it returns `False` when the value is already present.

### Supported channels: a set and a list

`_supported_channels` is a Python `set` for fast membership checks.

`_channel_order` is a Python `list` that remembers registration order. The constructor copies the initial channel list, and a successful registration appends the new channel.

These collections have different jobs. The set answers whether `PUSH` exists. The list tells us where `PUSH` belongs in the output. We never rely on a `set` to give us an order.

A duplicate channel registration returns `false` without appending anything, so its original position stays unchanged.

### Successfully used alert IDs: a set

`_used_alert_ids` is another Python `set`.

An ID enters this set only after a valid request has produced all its logs. We do not need to keep the old messages or returned logs because no method asks for them later.

## Do We Need More Design Patterns?

Here, the registry is simply the collections that record supported names. We do not need a separate registry class or one class per category or channel.

For example, registering `WEBHOOK` immediately makes it usable. The dispatch loop already knows how to include its name in the required log, so no `if` or `switch` needs to change.

**Strategy would become useful if channels had different sending behavior.** A real email sender, SMS sender, and push sender could implement a shared interface, with a map connecting channel names to sender objects. The platform could then call the appropriate sender through that interface. Those provider calls are outside this problem, so introducing that layer here would add classes without adding required behavior.

**Factory is useful when creating different kinds of objects.** This problem only registers names, so there are no different sender objects to construct.

**Observer is useful when subscribers are stored and notified when an event occurs.** Here, every call explicitly supplies its recipients and channels. We do not need subscription management.

The single platform class, small validation helpers, and familiar collections cover the required behavior directly.

## Build `sendAlert` Step by Step

### 1. Check the request before changing state

Reject the request if the alert ID or category is invalid, the category is unknown, or the ID was already used successfully.

Also check the message and input list sizes:

- Names and IDs must be non-blank and at most 100 characters long.
- The message must be non-blank, at most 500 characters long, and contain no double quotation mark.
- The recipient list must contain between 1 and 1,000 entries.
- The requested channel list must contain between 1 and 100 entries.

The list limits apply to the original inputs, before duplicates are removed.

The constructor uses the valid initial lists described in the problem. The limits on total registrations and successful alerts describe the supported workload. They are not additional eviction or reset rules.

The helper uses `str.isspace()` to detect whitespace-only strings, but it does not trim or change non-blank values. For example, `EMAIL` and `email` are different names. A non-blank message keeps its original spaces.

### 2. Collect recipients in first-occurrence order

Create a local `seen_recipients` set and a local `unique_recipients` list.

Visit the input recipients from left to right. If a recipient is invalid, reject the entire request. Otherwise, add it to the list only when it is newly added to the set.

For the input `[USER-2, USER-1, USER-2]`, the list becomes `[USER-2, USER-1]`.

The set removes duplicates. The list preserves order. These are temporary collections, so building them does not change platform state.

### 3. Validate and collect requested channels

Visit every requested channel. If it is invalid or unregistered, reject the entire request.

Put valid names into a local `requested_channels` set. Repeated names disappear automatically.

Do not silently ignore an unknown channel. A request containing both `EMAIL` and an unregistered `FAX` must fail completely.

### 4. Arrange the requested channels

Scan `_channel_order` once. Keep each channel that also appears in `requested_channels`.

If the registered order is `[EMAIL, SMS, PUSH]` and the request is `[PUSH, EMAIL, PUSH]`, the resulting list is `[EMAIL, PUSH]`.

This is registration order, not alphabetical order and not the order supplied by the request. No sorting is needed.

### 5. Generate the logs, then remember the ID

Loop over `uniqueRecipients`. For each recipient, loop over the ordered requested channels and append one formatted log.

Only after all logs have been built do we add the alert ID to `_used_alert_ids`.

This placement matters. Suppose alert `A-7` contains an unknown channel and fails. A corrected request using `A-7` must still be allowed. Recording the ID before validation would incorrectly block that retry.

All validation finishes before log generation starts. Consequently, an invalid recipient or channel appearing late in the input cannot produce a partial result.

## Walk Through an Example

Start with category `SECURITY` and registered channels `[EMAIL, SMS, PUSH]`.

Now call `sendAlert` with `alertId = "A-1"`, `alertCategory = "SECURITY"`, `message = "New sign-in"`, `recipientIds = ["USER-2", "USER-1", "USER-2"]`, and `deliveryChannels = ["PUSH", "EMAIL", "PUSH"]`.

The unique recipients are `[USER-2, USER-1]`. The selected channels in registration order are `[EMAIL, PUSH]`.

The returned list contains these four strings, in this order:

```text
ALERT - alertId=A-1 - category=SECURITY - recipientId=USER-2 - channel=EMAIL - message="New sign-in"
ALERT - alertId=A-1 - category=SECURITY - recipientId=USER-2 - channel=PUSH - message="New sign-in"
ALERT - alertId=A-1 - category=SECURITY - recipientId=USER-1 - channel=EMAIL - message="New sign-in"
ALERT - alertId=A-1 - category=SECURITY - recipientId=USER-1 - channel=PUSH - message="New sign-in"
```

Both channels for `USER-2` appear before either channel for `USER-1`. A later call using `A-1` returns an empty list.

If we next register `WEBHOOK`, the platform order becomes `[EMAIL, SMS, PUSH, WEBHOOK]`. A new valid alert requesting `[WEBHOOK, EMAIL]` produces `EMAIL` before `WEBHOOK` for every recipient.


## Time and Space Complexity

Let `R` be the number of input recipient entries, `D` the number of input channel entries, and `C` the total number of registered channels. Let `U` be the number of unique recipients and `K` the number of unique requested channels.

With the fixed string-length limits in this problem, a successful `sendAlert` takes **expected O(R + D + C + U × K) time**. Set lookups are expected constant time. We read both input lists, scan the registered channels once, and create `U × K` logs.

Producing `U × K` logs necessarily takes at least that much work. The additional scan of at most 1,000 registered channels keeps channel ordering straightforward. This design does not claim that scanning all channels is unavoidable.

If string-copying cost is counted separately, writing the output takes O(B) time and space, where `B` is the total number of characters across all returned logs.

Temporary space excluding the returned logs is **O(U + K)**. The output has **O(U × K)** entries, plus the characters stored in those strings.

If `A` categories and `S` successful alert IDs are stored, persistent platform space is **O(A + C + S)** under the bounded string lengths. Each registration takes expected O(1) time. The constructor takes expected O(A₀ + C₀) time and space for its initial category and channel counts.

## Verification

The implementation below was syntax-checked and run with Python 3.12. It passed **all 40 calls across the two blocks** in the attached `sample-tests-454.txt`, including both constructor calls.

It also passed **104 additional checks** covering blank and oversized inputs, retries after failed requests, unknown names, duplicate registration, case sensitivity, preserved spaces, unchanged caller lists, independent platform instances, and the stated size limits. These checks included every entry in a 100,000-log result and a workload of 100,000 successful alerts.

## Complete Python Solution

The code uses Python 3.7 or later because `dataclasses` is part of the standard library from that version onward. It preserves the supplied `AlertingPlatform` API and its camelCase public method names.

```python
from dataclasses import dataclass, field
from typing import List, Set


@dataclass(init=False)
class AlertingPlatform:
    """Stores supported names and returns logs for valid alert requests."""

    MAX_NAME_LENGTH = 100
    MAX_MESSAGE_LENGTH = 500
    MAX_RECIPIENTS_PER_ALERT = 1_000
    MAX_CHANNELS_PER_ALERT = 100

    _supported_categories: Set[str] = field(default_factory=set)
    _supported_channels: Set[str] = field(default_factory=set)
    _channel_order: List[str] = field(default_factory=list)
    _used_alert_ids: Set[str] = field(default_factory=set)

    def __init__(self, alertCategories: List[str], deliveryChannels: List[str]):
        # Copy the constructor inputs so later caller changes cannot change
        # this platform's supported values or channel order.
        self._supported_categories = set(alertCategories)
        self._supported_channels = set(deliveryChannels)
        self._channel_order = list(deliveryChannels)
        self._used_alert_ids = set()

    def registerAlertCategory(self, alertCategory: str) -> bool:
        if not self._is_valid_name(alertCategory):
            return False

        if alertCategory in self._supported_categories:
            return False

        self._supported_categories.add(alertCategory)
        return True

    def registerDeliveryChannel(self, deliveryChannel: str) -> bool:
        if not self._is_valid_name(deliveryChannel):
            return False

        if deliveryChannel in self._supported_channels:
            return False

        self._supported_channels.add(deliveryChannel)
        # Only a newly registered channel is appended to the order.
        self._channel_order.append(deliveryChannel)
        return True

    def sendAlert(
        self,
        alertId: str,
        alertCategory: str,
        message: str,
        recipientIds: List[str],
        deliveryChannels: List[str],
    ) -> List[str]:
        """Return logs for a valid request without partially changing state."""
        logs: List[str] = []

        if (
            not self._is_valid_name(alertId)
            or not self._is_valid_name(alertCategory)
            or alertCategory not in self._supported_categories
            or alertId in self._used_alert_ids
            or not self._is_valid_message(message)
            or not self._has_valid_size(recipientIds, self.MAX_RECIPIENTS_PER_ALERT)
            or not self._has_valid_size(deliveryChannels, self.MAX_CHANNELS_PER_ALERT)
        ):
            return logs

        seen_recipients: Set[str] = set()
        unique_recipients: List[str] = []

        for recipient_id in recipientIds:
            if not self._is_valid_name(recipient_id):
                return logs

            if recipient_id not in seen_recipients:
                seen_recipients.add(recipient_id)
                unique_recipients.append(recipient_id)

        requested_channels: Set[str] = set()

        for channel in deliveryChannels:
            if (
                not self._is_valid_name(channel)
                or channel not in self._supported_channels
            ):
                return logs

            requested_channels.add(channel)

        # Find the requested channels in registration order just once.
        ordered_requested_channels = [
            channel
            for channel in self._channel_order
            if channel in requested_channels
        ]

        # Every input has been validated before any log is generated.
        for recipient_id in unique_recipients:
            for channel in ordered_requested_channels:
                logs.append(
                    f"ALERT - alertId={alertId}"
                    f" - category={alertCategory}"
                    f" - recipientId={recipient_id}"
                    f" - channel={channel}"
                    f' - message="{message}"'
                )

        # This is the only persistent change made by a successful send.
        self._used_alert_ids.add(alertId)
        return logs

    @classmethod
    def _is_valid_name(cls, value: str) -> bool:
        return (
            isinstance(value, str)
            and bool(value)
            and not value.isspace()
            and len(value) <= cls.MAX_NAME_LENGTH
        )

    @classmethod
    def _is_valid_message(cls, message: str) -> bool:
        return (
            isinstance(message, str)
            and bool(message)
            and not message.isspace()
            and len(message) <= cls.MAX_MESSAGE_LENGTH
            and '"' not in message
        )

    @staticmethod
    def _has_valid_size(values: List[str], maximum_size: int) -> bool:
        return values is not None and 0 < len(values) <= maximum_size
```
