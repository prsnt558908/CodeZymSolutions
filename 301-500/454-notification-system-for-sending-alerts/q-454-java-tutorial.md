# Design an Extensible Notification System in Java

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

## Data Structures

The `AlertingPlatform` class owns all persistent state. Each platform instance gets its own collections.

### Supported categories: a set

`supportedCategories` is a `HashSet<String>`.

We only need to check whether a category exists and add new categories. Category order does not affect the output. A set also makes duplicate registration easy: `add` returns `false` when the value is already present.

### Supported channels: a set and a list

`supportedChannels` is a `HashSet<String>` for fast membership checks.

`channelOrder` is an `ArrayList<String>` that remembers registration order. The constructor copies the initial channel list, and a successful registration appends the new channel.

These collections have different jobs. The set answers whether `PUSH` exists. The list tells us where `PUSH` belongs in the output. We never rely on a `HashSet` to give us an order.

A duplicate channel registration returns `false` without appending anything, so its original position stays unchanged.

### Successfully used alert IDs: a set

`usedAlertIds` is another `HashSet<String>`.

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

The constructor uses the valid initial lists described in the problem. The limits on total registrations and successful alerts describe The supported workload. they are not additional eviction or reset rules.

We check whether strings are blank, but we do not trim or change them. For example, `EMAIL` and `email` are different names. A non-blank message keeps its original spaces.

### 2. Collect recipients in first-occurrence order

Create a local `seenRecipients` set and a local `uniqueRecipients` list.

Visit the input recipients from left to right. If a recipient is invalid, reject the entire request. Otherwise, add it to the list only when it is newly added to the set.

For the input `[USER-2, USER-1, USER-2]`, the list becomes `[USER-2, USER-1]`.

The set removes duplicates. The list preserves order. These are temporary collections, so building them does not change platform state.

### 3. Validate and collect requested channels

Visit every requested channel. If it is invalid or unregistered, reject the entire request.

Put valid names into a local `requestedChannels` set. Repeated names disappear automatically.

Do not silently ignore an unknown channel. A request containing both `EMAIL` and an unregistered `FAX` must fail completely.

### 4. Arrange the requested channels

Scan `channelOrder` once. Keep each channel that also appears in `requestedChannels`.

If the registered order is `[EMAIL, SMS, PUSH]` and the request is `[PUSH, EMAIL, PUSH]`, the resulting list is `[EMAIL, PUSH]`.

This is registration order, not alphabetical order and not the order supplied by the request. No sorting is needed.

### 5. Generate the logs, then remember the ID

Loop over `uniqueRecipients`. For each recipient, loop over the ordered requested channels and append one formatted log.

Only after all logs have been built do we add the alert ID to `usedAlertIds`.

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

## Why This Produces the Correct Result

**Only valid requests produce logs.** Every recipient and requested channel is checked before the output loops run. Every validation failure returns an empty list before the persistent state changes.

**Each required pair appears exactly once.** The recipient list contains each valid recipient once, and the ordered channel list contains each requested channel once. The nested loops visit every combination exactly once.

**Both ordering rules are preserved.** The outer loop uses first-occurrence recipient order. The inner loop uses registration order for channels.

**IDs are consumed only by successful calls.** Failed requests never update `usedAlertIds`. A successful call adds its ID, and later calls with that ID are rejected.

**Registration preserves the same rules.** A new category becomes available through the category set. A new channel is added to both the membership set and the end of the order list. Duplicate registrations leave the existing order unchanged.

## Time and Space Complexity

Let `R` be the number of input recipient entries, `D` the number of input channel entries, and `C` the total number of registered channels. Let `U` be the number of unique recipients and `K` the number of unique requested channels.

With the fixed string-length limits in this problem, a successful `sendAlert` takes **expected O(R + D + C + U × K) time**. Set lookups are expected constant time. We read both input lists, scan the registered channels once, and create `U × K` logs.

Producing `U × K` logs necessarily takes at least that much work. The additional scan of at most 1,000 registered channels keeps channel ordering straightforward. This design does not claim that scanning all channels is unavoidable.

If string-copying cost is counted separately, writing the output takes O(B) time and space, where `B` is the total number of characters across all returned logs.

Temporary space excluding the returned logs is **O(U + K)**. The output has **O(U × K)** entries, plus the characters stored in those strings.

If `A` categories and `S` successful alert IDs are stored, persistent platform space is **O(A + C + S)** under the bounded string lengths. Each registration takes expected O(1) time. The constructor takes expected O(A₀ + C₀) time and space for its initial category and channel counts.


## Complete Java Solution

The code uses Java 11 or later because it uses `String.isBlank()`. It keeps the public class and method signatures from the supplied Java stub.

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores supported names and returns logs for valid alert requests. */
public class AlertingPlatform {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_RECIPIENTS_PER_ALERT = 1_000;
    private static final int MAX_CHANNELS_PER_ALERT = 100;

    private final Set<String> supportedCategories;
    private final Set<String> supportedChannels;
    private final List<String> channelOrder;
    private final Set<String> usedAlertIds = new HashSet<>();

    public AlertingPlatform(
            List<String> alertCategories,
            List<String> deliveryChannels) {
        // Constructor inputs satisfy the stated constraints. Copy them so
        // later changes to the caller's lists cannot change this platform.
        supportedCategories = new HashSet<>(alertCategories);
        supportedChannels = new HashSet<>(deliveryChannels);
        channelOrder = new ArrayList<>(deliveryChannels);
    }

    public boolean registerAlertCategory(String alertCategory) {
        if (!isValidName(alertCategory)) {
            return false;
        }

        return supportedCategories.add(alertCategory);
    }

    public boolean registerDeliveryChannel(String deliveryChannel) {
        if (!isValidName(deliveryChannel)
                || !supportedChannels.add(deliveryChannel)) {
            return false;
        }

        // Only a newly registered channel is appended to the order.
        channelOrder.add(deliveryChannel);
        return true;
    }

    /** An invalid request returns no logs and does not consume the alert ID. */
    public List<String> sendAlert(
            String alertId,
            String alertCategory,
            String message,
            List<String> recipientIds,
            List<String> deliveryChannels) {
        List<String> logs = new ArrayList<>();

        if (!isValidName(alertId)
                || !isValidName(alertCategory)
                || !supportedCategories.contains(alertCategory)
                || usedAlertIds.contains(alertId)
                || !isValidMessage(message)
                || !hasValidSize(recipientIds, MAX_RECIPIENTS_PER_ALERT)
                || !hasValidSize(deliveryChannels, MAX_CHANNELS_PER_ALERT)) {
            return logs;
        }

        Set<String> seenRecipients = new HashSet<>();
        List<String> uniqueRecipients = new ArrayList<>();

        for (String recipientId : recipientIds) {
            if (!isValidName(recipientId)) {
                return logs;
            }

            if (seenRecipients.add(recipientId)) {
                uniqueRecipients.add(recipientId);
            }
        }

        Set<String> requestedChannels = new HashSet<>();

        for (String channel : deliveryChannels) {
            if (!isValidName(channel) || !supportedChannels.contains(channel)) {
                return logs;
            }

            requestedChannels.add(channel);
        }

        // Find the requested channels in registration order just once.
        List<String> orderedRequestedChannels = new ArrayList<>();
        for (String channel : channelOrder) {
            if (requestedChannels.contains(channel)) {
                orderedRequestedChannels.add(channel);
            }
        }

        // Every input has been validated before any log is generated.
        for (String recipientId : uniqueRecipients) {
            for (String channel : orderedRequestedChannels) {
                logs.add("ALERT - alertId=" + alertId
                        + " - category=" + alertCategory
                        + " - recipientId=" + recipientId
                        + " - channel=" + channel
                        + " - message=\"" + message + "\"");
            }
        }

        // This is the only persistent change made by a successful send.
        usedAlertIds.add(alertId);
        return logs;
    }

    private static boolean isValidName(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_NAME_LENGTH;
    }

    private static boolean isValidMessage(String message) {
        return message != null
                && !message.isBlank()
                && message.length() <= MAX_MESSAGE_LENGTH
                && message.indexOf('"') == -1;
    }

    private static boolean hasValidSize(List<?> values, int maximumSize) {
        return values != null
                && !values.isEmpty()
                && values.size() <= maximumSize;
    }
}
```
