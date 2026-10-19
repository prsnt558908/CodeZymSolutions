# Design Order Notification System in Java

#### Problem Statement
[https://codezym.com/question/94-design-order-notification-system](https://codezym.com/question/94-design-order-notification-system)

## Core Idea

At its heart, this is a **publish-subscribe** problem. Stakeholders subscribe to order events, and when an event is published for an order, the subscribed stakeholders of that order get notified on their chosen channels. This is exactly the idea behind the **Observer pattern**, and we use it in its lightest form: a `HashMap` of subscriptions instead of a list of observer objects.

Every subscription is stored with the key `stakeholderType|stakeholderId|eventType`, and its value is the set of active channels. Every published event is stored with the key `orderId|eventType`, so we can block duplicates and replay it later.

An order has at most 3 linked stakeholders: a customer, a seller and a delivery partner. So publishing an event is just 3 map lookups, never a scan over all subscribers.

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

```java
// Brute force: every publish scans every subscription in the system
for (Subscription subscription : allSubscriptions) {
    if (subscription.eventType.equals(eventType)
            && isLinkedToOrder(subscription, customerId, sellerId, deliveryPartnerId)) {
        // build notification lines, then sort them at the end
    }
}
```

This works, but it has clear problems.

- **Slow publish:** every publish scans all subscriptions, even though at most 3 stakeholders are linked to an order.
- **Slow updates:** subscribe, unsubscribe and channel changes must first search the list for the existing entry.
- **Slow duplicate check:** to block a repeated `(orderId, eventType)`, we scan every past event.
- **Extra sorting:** matching lines come out in random order, so we must sort them by stakeholder type and channel order.

## Better Approach: Direct Lookups With Maps

Look at how data is searched in this problem. The same few fields are always used together.

- A subscription is always found by `(stakeholderType, stakeholderId, eventType)`.
- A published event is always found by `(orderId, eventType)`.

So we join these fields into one string and use it as a `HashMap` key. Every lookup becomes O(1).

```java
// "CUSTOMER|CUSTOMER-1|ORDER_PLACED" -> {SMS}
Map<String, Set<String>> subscriptions = new HashMap<>();

// "ORDER-001|ORDER_PLACED" -> PublishRecord
Map<String, PublishRecord> publishHistory = new HashMap<>();
```

Publishing is now easy. The method arguments already tell us the 3 linked stakeholders. We look up each of them directly, in the fixed order `CUSTOMER`, `SELLER`, `DELIVERY_PARTNER`.

For channel order, we walk the constructor's channel list and keep only the channels found in the stakeholder's set. The lines come out already in the right order, so no sorting is needed.

## Design Patterns: What We Use and What We Skip

### Observer, in its lightest form (our choice)

Subscribe and unsubscribe register or remove interest in an event. Publish notifies the interested stakeholders of that order. That is the Observer pattern.

Our subscription map plays the role of the observer list, and `publishOrderEvent()` is the notify step. We simply skip the extra classes.

### Why not a textbook Observer?

In the textbook version, every stakeholder is an observer object with an `update()` method, kept in a list per event type. On publish, the system loops over every observer of that event.

That is a poor fit here.

- Only stakeholders **linked to the order** must be notified. So every observer would check if it belongs to this order, and almost all would say no.
- Publish becomes O(number of subscribers) instead of 3 lookups.
- The output needs a fixed stakeholder order, so the results would need sorting.

### Why not Strategy for channels?

It is tempting to write `EmailChannel`, `SmsChannel` and `PushChannel` classes behind one interface. But here every channel produces the same log line, only the channel name changes.

Channels are just names given to the constructor. A brand new channel like `WHATSAPP` already works without writing a single new class.

Strategy becomes useful later, when each channel has its own real sending logic, like calling an email server or an SMS gateway.

## Key Classes and Data Structures

**`PublishRecord`**

Stores one published event: order id, event type, the linked stakeholder ids and the message. Replay needs it, because replay must use the original message and must know who was linked to the order.

Its helper `getLinkedStakeholderId(stakeholderType)` returns the linked id for a stakeholder type. Both publish and replay use it.

**`subscriptions`: `Map<String, Set<String>>`**

Maps `stakeholderType|stakeholderId|eventType` to the active channels. A `Set` fits because channels behave like a set: duplicates disappear on their own and `contains()` is O(1).

The stakeholder type is part of the key because preferences are kept per `(stakeholderId, stakeholderType, eventType)`.

**`publishHistory`: `Map<String, PublishRecord>`**

Maps `orderId|eventType` to its record. One O(1) lookup both blocks a duplicate publish and finds the record for replay.

**`supportedChannels`: `List<String>`**

Keeps channels in constructor order, which decides the order of output lines. A `HashSet` would lose this order.

**`stakeholderTypes`: `List<String>`**

Holds `CUSTOMER`, `SELLER` and `DELIVERY_PARTNER` in output order. Looping over it gives the required stakeholder order for free.

## How Each Method Works

### subscribeUnsubscribeToEvent()

1. Validate the stakeholder id, stakeholder type and event type. Return `false` if anything is invalid.
2. If `subscribe` is `false`, remove the key and return `true`. Channels are ignored, and it is fine if nothing was there.
3. If the channel list is empty, store all supported channels.
4. Otherwise check that every channel is supported, then **replace** the old set with a new set built from the list.

All checks happen before any change, so an invalid call never touches the state.

### addRemoveChannels()

1. Validate the inputs. The channel list must be non-empty, and every channel must be supported.
2. The subscription must already exist. If not, return `false`.
3. Add the channels to the set, or remove them from it.
4. If the set becomes empty, remove the subscription completely.

### publishOrderEvent()

1. Validate the inputs. A blank `deliveryPartnerId` simply means no delivery partner is linked yet.
2. If `orderId|eventType` is already in `publishHistory`, return an empty list.
3. Save a `PublishRecord`. We save it even when nobody is subscribed, so a later duplicate is still blocked and replay still works.
4. For `CUSTOMER`, `SELLER` and `DELIVERY_PARTNER`, in that order, look up the linked stakeholder's subscription and add one line per active channel.

### replayNotifications()

1. Validate the inputs and find the `PublishRecord` for `orderId|eventType`.
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

## Java Code

```java
import java.util.*;

/**
 * One published order event.
 * We keep it to block duplicate publishes and to replay notifications later
 * with the original message and the stakeholders linked to the order.
 */
class PublishRecord {
    String orderId;
    String eventType;
    String customerId;
    String sellerId;
    String deliveryPartnerId; // null when no delivery partner is linked to the order
    String message;

    PublishRecord(String orderId, String eventType, String customerId,
                  String sellerId, String deliveryPartnerId, String message) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.customerId = customerId;
        this.sellerId = sellerId;
        this.deliveryPartnerId = deliveryPartnerId;
        this.message = message;
    }

    /** Returns the id of the stakeholder of this type linked to the order, or null if there is none. */
    String getLinkedStakeholderId(String stakeholderType) {
        if (stakeholderType.equals("CUSTOMER")) return customerId;
        if (stakeholderType.equals("SELLER")) return sellerId;
        if (stakeholderType.equals("DELIVERY_PARTNER")) return deliveryPartnerId;
        return null;
    }
}

public class OrderNotificationSystem {

    // Fixed order in which stakeholders appear in the output of publishOrderEvent()
    List<String> stakeholderTypes = Arrays.asList("CUSTOMER", "SELLER", "DELIVERY_PARTNER");

    Set<String> supportedEventTypes = new HashSet<>();

    // Channels in constructor order. Every output line follows this order.
    List<String> supportedChannels = new ArrayList<>();

    // "stakeholderType|stakeholderId|eventType" -> active channels of that subscription
    Map<String, Set<String>> subscriptions = new HashMap<>();

    // "orderId|eventType" -> the event that was published for that order
    Map<String, PublishRecord> publishHistory = new HashMap<>();

    public OrderNotificationSystem(List<String> eventTypes, List<String> notificationChannels) {
        for (String eventType : eventTypes) {
            if (!isBlank(eventType)) supportedEventTypes.add(eventType);
        }
        for (String channel : notificationChannels) {
            if (!isBlank(channel) && !supportedChannels.contains(channel)) supportedChannels.add(channel);
        }
    }

    public boolean subscribeUnsubscribeToEvent(String stakeholderId, String stakeholderType, String eventType,
                                               List<String> channels, boolean subscribe) {
        if (!isValidStakeholder(stakeholderId, stakeholderType)) return false;
        if (!supportedEventTypes.contains(eventType)) return false;
        String key = subscriptionKey(stakeholderType, stakeholderId, eventType);

        if (!subscribe) {
            subscriptions.remove(key); // channels are ignored while unsubscribing
            return true;
        }
        // An empty channel list means "all supported channels"
        if (channels == null || channels.isEmpty()) {
            subscriptions.put(key, new HashSet<>(supportedChannels));
            return true;
        }
        if (!areValidChannels(channels)) return false;
        subscriptions.put(key, new HashSet<>(channels)); // replaces the old set, the set drops duplicates
        return true;
    }

    public boolean addRemoveChannels(String stakeholderId, String stakeholderType, String eventType,
                                     List<String> channels, boolean add) {
        if (!isValidStakeholder(stakeholderId, stakeholderType)) return false;
        if (!supportedEventTypes.contains(eventType)) return false;
        if (channels == null || channels.isEmpty() || !areValidChannels(channels)) return false;

        String key = subscriptionKey(stakeholderType, stakeholderId, eventType);
        Set<String> activeChannels = subscriptions.get(key);
        if (activeChannels == null) return false; // the subscription must already exist

        if (add) activeChannels.addAll(channels);
        else activeChannels.removeAll(channels);

        if (activeChannels.isEmpty()) subscriptions.remove(key); // no channel left, so no subscription
        return true;
    }

    public List<String> publishOrderEvent(String orderId, String eventType, String customerId, String sellerId,
                                          String deliveryPartnerId, String message) {
        List<String> lines = new ArrayList<>();
        if (isBlank(orderId) || !supportedEventTypes.contains(eventType) || isBlank(customerId)
                || isBlank(sellerId) || isBlank(message)) return lines;

        String publishKey = orderId + "|" + eventType;
        if (publishHistory.containsKey(publishKey)) return lines; // same event is never published twice

        if (isBlank(deliveryPartnerId)) deliveryPartnerId = null; // no delivery partner linked yet
        PublishRecord record = new PublishRecord(orderId, eventType, customerId, sellerId, deliveryPartnerId, message);
        publishHistory.put(publishKey, record);

        // Check only the stakeholders linked to this order: CUSTOMER, then SELLER, then DELIVERY_PARTNER
        for (String stakeholderType : stakeholderTypes) {
            String stakeholderId = record.getLinkedStakeholderId(stakeholderType);
            if (stakeholderId != null) lines.addAll(buildNotifications(record, stakeholderType, stakeholderId));
        }
        return lines;
    }

    public List<String> replayNotifications(String orderId, String eventType, String stakeholderId,
                                            String stakeholderType) {
        List<String> lines = new ArrayList<>();
        if (isBlank(orderId) || !supportedEventTypes.contains(eventType)
                || !isValidStakeholder(stakeholderId, stakeholderType)) return lines;

        PublishRecord record = publishHistory.get(orderId + "|" + eventType);
        if (record == null) return lines; // this event was never published for the order
        if (!stakeholderId.equals(record.getLinkedStakeholderId(stakeholderType))) return lines; // not linked to the order

        // Latest channel set, original message
        List<String> notifications = buildNotifications(record, stakeholderType, stakeholderId);
        if (notifications.isEmpty()) return lines; // no active subscription right now

        lines.add("REPLAY - orderId=" + orderId + " - eventType=" + eventType
                + " - stakeholderType=" + stakeholderType + " - stakeholderId=" + stakeholderId);
        lines.addAll(notifications);
        return lines;
    }

    /** One notification line per active channel of this stakeholder, in constructor channel order. */
    List<String> buildNotifications(PublishRecord record, String stakeholderType, String stakeholderId) {
        List<String> lines = new ArrayList<>();
        Set<String> activeChannels = subscriptions.get(subscriptionKey(stakeholderType, stakeholderId, record.eventType));
        if (activeChannels == null) return lines; // not subscribed to this event type

        for (String channel : supportedChannels) {
            if (activeChannels.contains(channel)) {
                lines.add("NOTIFICATION - orderId=" + record.orderId
                        + " - eventType=" + record.eventType
                        + " - stakeholderType=" + stakeholderType
                        + " - stakeholderId=" + stakeholderId
                        + " - channel=" + channel
                        + " - message=\"" + record.message + "\"");
            }
        }
        return lines;
    }

    String subscriptionKey(String stakeholderType, String stakeholderId, String eventType) {
        return stakeholderType + "|" + stakeholderId + "|" + eventType;
    }

    boolean isValidStakeholder(String stakeholderId, String stakeholderType) {
        return !isBlank(stakeholderId) && stakeholderTypes.contains(stakeholderType);
    }

    /** Every channel must be one of the channels given in the constructor. */
    boolean areValidChannels(List<String> channels) {
        for (String channel : channels) {
            if (!supportedChannels.contains(channel)) return false;
        }
        return true;
    }

    boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
```