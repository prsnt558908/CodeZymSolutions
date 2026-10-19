# Design Order Notification System in Python

#### Problem Statement
[https://codezym.com/question/94-design-order-notification-system](https://codezym.com/question/94-design-order-notification-system)

## Core Idea

At its heart, this is a **publish-subscribe** problem. Stakeholders subscribe to order events, and when an event is published for an order, the subscribed stakeholders of that order get notified on their chosen channels. This is exactly the idea behind the **Observer pattern**, and we use it in its lightest form: a dictionary of subscriptions instead of a list of observer objects.

Every subscription is stored with the key `(stakeholderType, stakeholderId, eventType)`, and its value is the set of active channels. Every published event is stored with the key `(orderId, eventType)`, so we can block duplicates and replay it later.

An order has at most 3 linked stakeholders: a customer, a seller and a delivery partner. So publishing an event is just 3 dictionary lookups, never a scan over all subscribers.

Heavier designs like a textbook Observer or a Strategy class per channel sound good on paper. But here they add classes without making anything simpler. We will see why below.

## Key Rules to Remember

- A subscription is unique per `(stakeholderId, stakeholderType, eventType)`. Its channels behave like a **set**.
- Subscribing with an empty channel list enables **all** channels.
- Subscribing again **replaces** the whole channel set.
- If removing channels leaves the set empty, the subscription is removed.
- An `(orderId, eventType)` pair can be published only **once**.
- Output order is `CUSTOMER`, then `SELLER`, then `DELIVERY_PARTNER`. For one stakeholder, channels follow the **constructor order**.
- Replay uses the **latest** channels but the **original** message.

## A Simple First Approach (Brute Force)

The most direct idea is to keep everything in plain lists.

- A list of all subscriptions. Each entry holds a stakeholder id, stakeholder type, event type and its channels.
- A list of all published events.

To publish, we loop over every subscription and keep the ones that match the event type and belong to one of the order's stakeholders.

```python
# Brute force: every publish scans every subscription in the system
for subscription in allSubscriptions:
    if (subscription.eventType == eventType
            and isLinkedToOrder(subscription, customerId, sellerId, deliveryPartnerId)):
        ...  # build notification lines, then sort them at the end
```

This works, but it has clear problems.

- **Slow publish:** every publish scans all subscriptions, even though at most 3 stakeholders are linked to an order.
- **Slow updates:** subscribe, unsubscribe and channel changes must first search the list for the existing entry.
- **Slow duplicate check:** to block a repeated `(orderId, eventType)`, we scan every past event.
- **Extra sorting:** matching lines come out in random order, so we must sort them by stakeholder type and channel order.

## Better Approach: Direct Lookups With Dictionaries

Look at how data is searched in this problem. The same few fields are always used together.

- A subscription is always found by `(stakeholderType, stakeholderId, eventType)`.
- A published event is always found by `(orderId, eventType)`.

So we use a tuple of these fields as a dictionary key. Python tuples work as keys directly, and every lookup becomes O(1).

```python
# ("CUSTOMER", "CUSTOMER-1", "ORDER_PLACED") -> {"SMS"}
self.subscriptions = {}

# ("ORDER-001", "ORDER_PLACED") -> PublishRecord
self.publishHistory = {}
```

Publishing is now easy. The method arguments already tell us the 3 linked stakeholders. We look up each of them directly, in the fixed order `CUSTOMER`, `SELLER`, `DELIVERY_PARTNER`.

For channel order, we walk the constructor's channel list and keep only the channels found in the stakeholder's set. The lines come out already in the right order, so no sorting is needed.

## Design Patterns: What We Use and What We Skip

### Observer, in its lightest form (our choice)

Subscribe and unsubscribe register or remove interest in an event. Publish notifies the interested stakeholders of that order. That is the Observer pattern.

Our subscription dictionary plays the role of the observer list, and `publishOrderEvent()` is the notify step. We simply skip the extra classes.

### Why not a textbook Observer?

In the textbook version, every stakeholder is an observer object with an `update()` method, kept in a list per event type. On publish, the system loops over every observer of that event.

That is a poor fit here.

- Only stakeholders **linked to the order** must be notified. So every observer would check if it belongs to this order, and almost all would say no.
- Publish becomes O(number of subscribers) instead of 3 lookups.
- The output needs a fixed stakeholder order, so the results would need sorting.

### Why not Strategy for channels?

It is tempting to write `EmailChannel`, `SmsChannel` and `PushChannel` classes behind one common base class. But here every channel produces the same log line, only the channel name changes.

Channels are just names given to the constructor. A brand new channel like `WHATSAPP` already works without writing a single new class.

Strategy becomes useful later, when each channel has its own real sending logic, like calling an email server or an SMS gateway.

## Key Classes and Data Structures

**`PublishRecord`**

Stores one published event: order id, event type, the linked stakeholder ids and the message. Replay needs it, because replay must use the original message and must know who was linked to the order.

Its helper `getLinkedStakeholderId(stakeholderType)` returns the linked id for a stakeholder type. Both publish and replay use it.

**`subscriptions`: dictionary of tuple to set**

Maps `(stakeholderType, stakeholderId, eventType)` to the active channels. A `set` fits because channels behave like a set: duplicates disappear on their own and the `in` check is O(1).

The stakeholder type is part of the key because preferences are kept per `(stakeholderId, stakeholderType, eventType)`.

**`publishHistory`: dictionary of tuple to `PublishRecord`**

Maps `(orderId, eventType)` to its record. One O(1) lookup both blocks a duplicate publish and finds the record for replay.

**`supportedChannels`: list**

Keeps channels in constructor order, which decides the order of output lines. A `set` would lose this order.

**`stakeholderTypes`: list**

Holds `CUSTOMER`, `SELLER` and `DELIVERY_PARTNER` in output order. Looping over it gives the required stakeholder order for free.

## How Each Method Works

### subscribeUnsubscribeToEvent()

1. Validate the stakeholder id, stakeholder type and event type. Return `False` if anything is invalid.
2. If `subscribe` is `False`, remove the key and return `True`. Channels are ignored, and it is fine if nothing was there.
3. If the channel list is empty, store all supported channels.
4. Otherwise check that every channel is supported, then **replace** the old set with a new set built from the list.

All checks happen before any change, so an invalid call never touches the state.

### addRemoveChannels()

1. Validate the inputs. The channel list must be non-empty, and every channel must be supported.
2. The subscription must already exist. If not, return `False`.
3. Add the channels to the set, or remove them from it.
4. If the set becomes empty, remove the subscription completely.

### publishOrderEvent()

1. Validate the inputs. A blank `deliveryPartnerId` simply means no delivery partner is linked yet.
2. If `(orderId, eventType)` is already in `publishHistory`, return an empty list.
3. Save a `PublishRecord`. We save it even when nobody is subscribed, so a later duplicate is still blocked and replay still works.
4. For `CUSTOMER`, `SELLER` and `DELIVERY_PARTNER`, in that order, look up the linked stakeholder's subscription and add one line per active channel.

### replayNotifications()

1. Validate the inputs and find the `PublishRecord` for `(orderId, eventType)`.
2. Check that the stakeholder is linked to that order, since only linked stakeholders may be notified. For example, a `SELLER` replay must use the order's seller id.
3. Build lines with the stakeholder's **current** channels and the record's **original** message.
4. If no line was built, the stakeholder has no active subscription, so return an empty list. Otherwise return the `REPLAY` line followed by the notification lines.

Publish and replay share one helper, `buildNotifications()`, so the line format lives in a single place.

## Dry Run: Example 2

1. `CUSTOMER-1` subscribes to `ORDER_DELIVERED` with `{EMAIL, SMS}`.
2. `ORDER-002` is published for `ORDER_DELIVERED`. Only the customer has a subscription, so we get an `EMAIL` line and an `SMS` line. The record is saved.
3. `EMAIL` is removed. The customer's set is now `{SMS}`.
4. Replay for `CUSTOMER-1`. The record exists, `CUSTOMER-1` is the order's customer, and the current set is `{SMS}`. So we return the `REPLAY` line and one `SMS` line with the original message.

## Complexity

`K` is the number of supported channels, and `C` is the size of the channel list passed in.

| Method | Time |
|---|---|
| `subscribeUnsubscribeToEvent()` | O(C × K) |
| `addRemoveChannels()` | O(C × K) |
| `publishOrderEvent()` | O(K) |
| `replayNotifications()` | O(K) |

`K` is tiny (like 3), so in practice every method is close to O(1) beyond reading its input.

Space is O(S × K + P), where `S` is the number of subscriptions and `P` is the number of published events.

## Python Code

```python
class PublishRecord:
    """One published order event.

    We keep it to block duplicate publishes and to replay notifications later
    with the original message and the stakeholders linked to the order.
    """

    def __init__(self, orderId, eventType, customerId, sellerId, deliveryPartnerId, message):
        self.orderId = orderId
        self.eventType = eventType
        self.customerId = customerId
        self.sellerId = sellerId
        self.deliveryPartnerId = deliveryPartnerId  # None when no delivery partner is linked to the order
        self.message = message

    def getLinkedStakeholderId(self, stakeholderType):
        """Returns the id of the stakeholder of this type linked to the order, or None if there is none."""
        if stakeholderType == "CUSTOMER":
            return self.customerId
        if stakeholderType == "SELLER":
            return self.sellerId
        if stakeholderType == "DELIVERY_PARTNER":
            return self.deliveryPartnerId
        return None


class OrderNotificationSystem:
    def __init__(self, eventTypes, notificationChannels):
        # Fixed order in which stakeholders appear in the output of publishOrderEvent()
        self.stakeholderTypes = ["CUSTOMER", "SELLER", "DELIVERY_PARTNER"]
        self.supportedEventTypes = set()
        # Channels in constructor order. Every output line follows this order.
        self.supportedChannels = []
        # (stakeholderType, stakeholderId, eventType) -> set of active channels of that subscription
        self.subscriptions = {}
        # (orderId, eventType) -> the PublishRecord of the event published for that order
        self.publishHistory = {}

        for eventType in eventTypes:
            if not self.isBlank(eventType):
                self.supportedEventTypes.add(eventType)
        for channel in notificationChannels:
            if not self.isBlank(channel) and channel not in self.supportedChannels:
                self.supportedChannels.append(channel)

    def subscribeUnsubscribeToEvent(self, stakeholderId, stakeholderType, eventType, channels, subscribe):
        if not self.isValidStakeholder(stakeholderId, stakeholderType):
            return False
        if eventType not in self.supportedEventTypes:
            return False
        key = (stakeholderType, stakeholderId, eventType)

        if not subscribe:
            self.subscriptions.pop(key, None)  # channels are ignored while unsubscribing
            return True
        # An empty channel list means "all supported channels"
        if not channels:
            self.subscriptions[key] = set(self.supportedChannels)
            return True
        if not self.areValidChannels(channels):
            return False
        self.subscriptions[key] = set(channels)  # replaces the old set, the set drops duplicates
        return True

    def addRemoveChannels(self, stakeholderId, stakeholderType, eventType, channels, add):
        if not self.isValidStakeholder(stakeholderId, stakeholderType):
            return False
        if eventType not in self.supportedEventTypes:
            return False
        if not channels or not self.areValidChannels(channels):
            return False

        key = (stakeholderType, stakeholderId, eventType)
        activeChannels = self.subscriptions.get(key)
        if activeChannels is None:
            return False  # the subscription must already exist

        if add:
            activeChannels.update(channels)
        else:
            activeChannels.difference_update(channels)

        if not activeChannels:
            del self.subscriptions[key]  # no channel left, so no subscription
        return True

    def publishOrderEvent(self, orderId, eventType, customerId, sellerId, deliveryPartnerId, message):
        if (self.isBlank(orderId) or eventType not in self.supportedEventTypes or self.isBlank(customerId)
                or self.isBlank(sellerId) or self.isBlank(message)):
            return []

        publishKey = (orderId, eventType)
        if publishKey in self.publishHistory:
            return []  # same event is never published twice

        if self.isBlank(deliveryPartnerId):
            deliveryPartnerId = None  # no delivery partner linked yet
        record = PublishRecord(orderId, eventType, customerId, sellerId, deliveryPartnerId, message)
        self.publishHistory[publishKey] = record

        # Check only the stakeholders linked to this order: CUSTOMER, then SELLER, then DELIVERY_PARTNER
        lines = []
        for stakeholderType in self.stakeholderTypes:
            stakeholderId = record.getLinkedStakeholderId(stakeholderType)
            if stakeholderId is not None:
                lines.extend(self.buildNotifications(record, stakeholderType, stakeholderId))
        return lines

    def replayNotifications(self, orderId, eventType, stakeholderId, stakeholderType):
        if (self.isBlank(orderId) or eventType not in self.supportedEventTypes
                or not self.isValidStakeholder(stakeholderId, stakeholderType)):
            return []

        record = self.publishHistory.get((orderId, eventType))
        if record is None:
            return []  # this event was never published for the order
        if record.getLinkedStakeholderId(stakeholderType) != stakeholderId:
            return []  # not linked to the order

        # Latest channel set, original message
        notifications = self.buildNotifications(record, stakeholderType, stakeholderId)
        if not notifications:
            return []  # no active subscription right now

        replayLine = (f"REPLAY - orderId={orderId} - eventType={eventType}"
                      f" - stakeholderType={stakeholderType} - stakeholderId={stakeholderId}")
        return [replayLine] + notifications

    def buildNotifications(self, record, stakeholderType, stakeholderId):
        """One notification line per active channel of this stakeholder, in constructor channel order."""
        activeChannels = self.subscriptions.get((stakeholderType, stakeholderId, record.eventType))
        if activeChannels is None:
            return []  # not subscribed to this event type

        lines = []
        for channel in self.supportedChannels:
            if channel in activeChannels:
                lines.append(f"NOTIFICATION - orderId={record.orderId}"
                             f" - eventType={record.eventType}"
                             f" - stakeholderType={stakeholderType}"
                             f" - stakeholderId={stakeholderId}"
                             f" - channel={channel}"
                             f' - message="{record.message}"')
        return lines

    def isValidStakeholder(self, stakeholderId, stakeholderType):
        return not self.isBlank(stakeholderId) and stakeholderType in self.stakeholderTypes

    def areValidChannels(self, channels):
        """Every channel must be one of the channels given in the constructor."""
        for channel in channels:
            if channel not in self.supportedChannels:
                return False
        return True

    def isBlank(self, value):
        return value is None or value.strip() == ""
```