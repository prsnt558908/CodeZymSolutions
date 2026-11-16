# Design WhatsApp Read Receipts in Java

#### Problem Statement
[https://codezym.com/question/168-design-whatsapp-read-receipts](https://codezym.com/question/168-design-whatsapp-read-receipts)


## Core Idea

Every message keeps two small maps: who has **received** it and who has **read** it, with the time of each event. When the sender asks for the ticks, we work them out from these two maps.


The only part that changes from one chat to another is the **tick rule**. A direct chat looks at read receipt settings. A group chat ignores them. So we use the **Strategy pattern**, with one small class per conversation type.


Strategy alone is the best fit here. The event system (the Observer part) is already built and simply calls our methods. Everything else is plain classes with maps and sets.


## The Tick Rules at a Glance

| Status | Direct chat | Group chat |
|---|---|---|
| `SENT_ONE_GRAY_TICK` | The other user has not received it | At least one recipient has not received it |
| `DELIVERED_TWO_GRAY_TICKS` | Received but not read, or read while someone has read receipts `OFF` | All recipients received it, but at least one has not read it |
| `READ_TWO_BLUE_TICKS` | Read, and both users have read receipts `ON` | Every recipient has read it. Settings do not matter |


Three more rules shape the design:

- Reading a message also counts as receiving it.
- Only the sender can see the ticks of a message.
- `currentTime` can never go back. A smaller time makes the call invalid.


## First Attempt: Check Every Recipient

For each message, store when each recipient got it (`deliveredAt`) and when they read it (`readAt`). When the sender asks for the ticks, walk over every member of the chat and decide.

```java
// First attempt: walk every recipient on every status check
boolean allDelivered = true;
boolean allRead = true;
for (String user : message.conversation.users) {
    if (user.equals(message.senderId)) {
        continue;
    }
    if (!message.deliveredAt.containsKey(user)) {
        allDelivered = false;
    }
    if (!message.readAt.containsKey(user)) {
        allRead = false;
    }
}

if (message.conversation.type.equals("DIRECT")) {
    String recipient = message.recipients().get(0);
    boolean bothOn = !readReceiptsOff.contains(message.senderId)
            && !readReceiptsOff.contains(recipient);
    if (!allDelivered) {
        return "SENT_ONE_GRAY_TICK";
    }
    return (allRead && bothOn) ? "READ_TWO_BLUE_TICKS" : "DELIVERED_TWO_GRAY_TICKS";
}

// GROUP
if (allRead) {
    return "READ_TWO_BLUE_TICKS";
}
return allDelivered ? "DELIVERED_TWO_GRAY_TICKS" : "SENT_ONE_GRAY_TICK";
```

It gives correct answers, but it has two problems:

1. **It is slow for big groups.** Every status check walks the whole group. With 1,000 members and 100,000 checks, that is 100 million steps.
2. **The rules are tangled.** Direct and group logic sit in one method. A new chat type, like a broadcast list, means editing this method again and risking the old rules.

Let us fix them one at a time.


## Improvement 1: Count, Do Not Scan

Look at who can enter the two maps:

- Only real recipients. Anyone else is rejected before we touch the maps.
- When someone reads a message, we also add them to `deliveredAt`.

So `deliveredAt.size()` is the number of people who got the message, and `readAt.size()` is the number who read it. Comparing them with the number of recipients gives the group ticks in O(1).

```java
int total = message.recipientCount();   // members minus the sender
if (message.readAt.size() == total) {
    return "READ_TWO_BLUE_TICKS";
}
if (message.deliveredAt.size() == total) {
    return "DELIVERED_TWO_GRAY_TICKS";
}
return "SENT_ONE_GRAY_TICK";
```

Here is Example 3, where alice sends `g1` to bob and charlie (2 recipients):

| Event | `deliveredAt.size()` | `readAt.size()` | Ticks alice sees |
|---|---|---|---|
| bob receives it | 1 | 0 | one gray tick |
| charlie receives it | 2 | 0 | two gray ticks |
| bob reads it | 2 | 1 | two gray ticks |
| charlie reads it | 2 | 2 | two blue ticks |

Bob has read receipts `OFF`, but the group still turns blue, because group ticks ignore settings.


Why maps and not sets? The group summary needs the **time** of each delivery and read. A map from user to time gives us the count (its size) and the time in one place.


An entry is added only when an event happens. A message that nobody opens costs almost nothing, even in a huge group.


## Improvement 2: Strategy Pattern for the Tick Rules

We move each tick rule into its own class behind one interface:

```java
interface ReceiptStrategy {
    String getStatus(Message message);
}
```

- `GroupReceiptStrategy` is the counting code above.
- `DirectReceiptStrategy` looks at the single recipient. It returns blue ticks only if the recipient read the message and both users have read receipts `ON`.

We create one object of each type and keep them in a map. Each conversation picks its strategy once, when it is loaded:

```java
strategies.put("DIRECT", new DirectReceiptStrategy(readReceiptsOff));
strategies.put("GROUP", new GroupReceiptStrategy());
```

Now `getReceiptStatus` needs no if/else for chat types:

```java
return message.conversation.strategy.getStatus(message);
```

**Why Strategy and not a plain if/else?** With two chat types, if/else works fine. But the tick rule is the part most likely to grow: broadcast lists, communities, new privacy options.


With Strategy, a new chat type is one new class and one `put` line. The old rules stay untouched. The map also tells us which types are valid, so a config with an unknown type is simply skipped.


## Patterns That Sound Good but Are Not Needed

**State pattern.** A receipt moves from sent to delivered to read, so State looks like a natural fit. But it only moves forward, and each answer (`NO_CHANGE` or not) is a single map lookup.


State would add three classes for what two maps already do. Also, the sender's ticks do not come from one object's state. They come from all recipients together, plus the settings.


**Observer pattern.** Delivered and read events sound like a classic Observer case. But the problem says that event system is already built. Our class is simply where those events land, so building Observer again would only add empty plumbing.


## Classes and Data Structures

| Class or field | Why we need it |
|---|---|
| `Map<String, Conversation> conversations` | Find a chat by id in O(1) |
| `Map<String, Message> messages` | Find a message by id in O(1), and reject a reused message id |
| `Set<String> readReceiptsOff` | Users who turned read receipts `OFF`. Anyone not in the set is `ON`, which is exactly the default rule |
| `Conversation.users` (set) | Check in O(1) whether a user belongs to the chat |
| `Conversation.sortedUsers` (list) | The same members, sorted once at load time, so the summary never sorts again |
| `Message.deliveredAt` and `Message.readAt` (maps) | User to time. Their sizes are the counts, and their values fill the summary |


`Message` also has three tiny helpers: `isRecipient()` (in the chat and not the sender), `recipientCount()` and `recipients()` (sorted members minus the sender).


## How Each Method Works

Every method checks a request in the same order:

1. **Time.** If `currentTime` is smaller than the largest time seen so far, the call is invalid. Otherwise it becomes the new latest time.
2. **Empty ids.** Any empty id makes the request invalid.
3. **Lookups.** The conversation or message must exist, and the user must have the right role.

The time check runs first, so it saves the time even if the call fails later for another reason. The rule compares with the time used in *any* earlier call, not just the valid ones.


After the checks, each method does one small job:

- `sendMessage` stores a new `Message`. The sender must be in the chat, and the message id must be new.
- `markDelivered` returns `NO_CHANGE` if the user is already in `deliveredAt`. Readers are always in it, so this also covers "already read".
- `markRead` returns `NO_CHANGE` if the user already read it. Otherwise it adds the user to `readAt`, and to `deliveredAt` if they are missing there.
- `getReceiptStatus` returns `NOT_VISIBLE` if the viewer is not the sender. Otherwise it asks the conversation's strategy.
- `getGroupReadSummary` works only for the sender of a group message. It walks the sorted recipients and builds one line per user, with `-1` for a missing time.

`markDelivered` and `markRead` share their checks through `findMessageForRecipient()`. The recipient check also rejects the sender, because the sender is never a recipient of their own message.


## Complexity

| Operation | Time |
|---|---|
| Constructor | O(S + U log U). S is the number of settings, U is the total members of all chats |
| `sendMessage`, `markDelivered`, `markRead` | O(1) |
| `getReceiptStatus` | O(1) |
| `getGroupReadSummary` | O(R), where R is the number of recipients. We must return R lines anyway |

Space is O(U + messages + delivered and read events). Nothing is stored for a recipient until an event happens.


## Final Code

```java
import java.util.*;

/**
 * Read receipt feature of WhatsApp.
 *
 * The event system is already built. It calls sendMessage, markDelivered
 * and markRead. We store those events and turn them into ticks for the sender.
 */
public class WhatsAppReadReceipts {

    Map<String, Conversation> conversations = new HashMap<>();
    Map<String, Message> messages = new HashMap<>();

    // Users who turned read receipts OFF. Everyone else is ON.
    Set<String> readReceiptsOff = new HashSet<>();

    // Conversation type -> tick rule for that type
    Map<String, ReceiptStrategy> strategies = new HashMap<>();

    // Largest currentTime seen so far
    int lastTime = 0;

    public WhatsAppReadReceipts(List<String> conversationConfigs, List<String> userSettings) {
        for (String setting : userSettings) {
            addSetting(setting);
        }
        strategies.put("DIRECT", new DirectReceiptStrategy(readReceiptsOff));
        strategies.put("GROUP", new GroupReceiptStrategy());
        for (String config : conversationConfigs) {
            addConversation(config);
        }
    }

    public String sendMessage(String messageId, String conversationId, String senderUserId, int currentTime) {
        if (!isTimeValid(currentTime) || isEmpty(messageId)
                || isEmpty(conversationId) || isEmpty(senderUserId)) {
            return "INVALID_REQUEST";
        }
        Conversation conversation = conversations.get(conversationId);
        if (conversation == null || !conversation.users.contains(senderUserId)
                || messages.containsKey(messageId)) {
            return "INVALID_REQUEST";
        }
        messages.put(messageId, new Message(messageId, senderUserId, conversation));
        return "MESSAGE_SENT";
    }

    public String markDelivered(String messageId, String userId, int currentTime) {
        Message message = findMessageForRecipient(messageId, userId, currentTime);
        if (message == null) {
            return "INVALID_REQUEST";
        }
        // Readers are also in deliveredAt, so this covers "already read" too
        if (message.deliveredAt.containsKey(userId)) {
            return "NO_CHANGE";
        }
        message.deliveredAt.put(userId, currentTime);
        return "DELIVERY_RECORDED";
    }

    public String markRead(String messageId, String userId, int currentTime) {
        Message message = findMessageForRecipient(messageId, userId, currentTime);
        if (message == null) {
            return "INVALID_REQUEST";
        }
        if (message.readAt.containsKey(userId)) {
            return "NO_CHANGE";
        }
        message.readAt.put(userId, currentTime);
        // Reading a message also means it was delivered
        message.deliveredAt.putIfAbsent(userId, currentTime);
        return "READ_RECORDED";
    }

    public String getReceiptStatus(String messageId, String viewerUserId, int currentTime) {
        if (!isTimeValid(currentTime) || isEmpty(messageId) || isEmpty(viewerUserId)) {
            return "INVALID_REQUEST";
        }
        Message message = messages.get(messageId);
        if (message == null) {
            return "INVALID_REQUEST";
        }
        if (!message.senderId.equals(viewerUserId)) {
            return "NOT_VISIBLE";
        }
        return message.conversation.strategy.getStatus(message);
    }

    public List<String> getGroupReadSummary(String messageId, String viewerUserId, int currentTime) {
        List<String> summary = new ArrayList<>();
        if (!isTimeValid(currentTime) || isEmpty(messageId) || isEmpty(viewerUserId)) {
            return summary;
        }
        Message message = messages.get(messageId);
        if (message == null || !message.senderId.equals(viewerUserId)
                || !message.conversation.type.equals("GROUP")) {
            return summary;
        }
        // recipients() is already sorted by user id
        for (String user : message.recipients()) {
            String delivered = message.deliveredAt.containsKey(user)
                    ? "DELIVERED," + message.deliveredAt.get(user) : "NOT_DELIVERED,-1";
            String read = message.readAt.containsKey(user)
                    ? "READ," + message.readAt.get(user) : "NOT_READ,-1";
            summary.add(user + "," + delivered + "," + read);
        }
        return summary;
    }

    /** Common checks of markDelivered and markRead. Returns null if the request is invalid. */
    Message findMessageForRecipient(String messageId, String userId, int currentTime) {
        if (!isTimeValid(currentTime) || isEmpty(messageId) || isEmpty(userId)) {
            return null;
        }
        Message message = messages.get(messageId);
        if (message == null || !message.isRecipient(userId)) {
            return null;
        }
        return message;
    }

    /**
     * Time can never go back. This check runs first in every method,
     * so every call (even one that fails later for another reason) moves lastTime forward.
     */
    boolean isTimeValid(int currentTime) {
        if (currentTime < lastTime) {
            return false;
        }
        lastTime = currentTime;
        return true;
    }

    boolean isEmpty(String value) {
        return value.trim().isEmpty();
    }

    /** Parses "userId,ON" or "userId,OFF". Broken lines are skipped. */
    void addSetting(String setting) {
        String[] parts = setting.split(",");
        if (parts.length < 2 || isEmpty(parts[0])) {
            return;
        }
        String userId = parts[0].trim();
        String value = parts[1].trim();
        if (value.equals("OFF")) {
            readReceiptsOff.add(userId);
        } else if (value.equals("ON")) {
            readReceiptsOff.remove(userId);
        }
    }

    /** Parses "conversationId,TYPE,user1|user2|...". Broken configs are skipped. */
    void addConversation(String config) {
        String[] parts = config.split(",");
        if (parts.length < 3) {
            return;
        }
        String conversationId = parts[0].trim();
        String type = parts[1].trim();
        ReceiptStrategy strategy = strategies.get(type);
        if (conversationId.isEmpty() || strategy == null || conversations.containsKey(conversationId)) {
            return;
        }
        Set<String> users = new HashSet<>();
        for (String user : parts[2].split("\\|")) {
            if (!isEmpty(user)) {
                users.add(user.trim());
            }
        }
        // DIRECT needs exactly 2 distinct users, GROUP needs at least 2
        boolean validSize = type.equals("DIRECT") ? users.size() == 2 : users.size() >= 2;
        if (validSize) {
            conversations.put(conversationId, new Conversation(conversationId, type, users, strategy));
        }
    }
}

/** A DIRECT or GROUP chat. Its members never change. */
class Conversation {
    String id;
    String type;
    Set<String> users;          // fast "is this user in the chat?" check
    List<String> sortedUsers;   // same users, sorted once for the group summary
    ReceiptStrategy strategy;   // tick rule for this conversation type

    Conversation(String id, String type, Set<String> users, ReceiptStrategy strategy) {
        this.id = id;
        this.type = type;
        this.users = users;
        this.sortedUsers = new ArrayList<>(users);
        Collections.sort(this.sortedUsers);
        this.strategy = strategy;
    }
}

/** One sent message and what happened to it for each recipient. */
class Message {
    String id;
    String senderId;
    Conversation conversation;

    // recipient -> time the message was delivered (readers are here too)
    Map<String, Integer> deliveredAt = new HashMap<>();
    // recipient -> time the message was read
    Map<String, Integer> readAt = new HashMap<>();

    Message(String id, String senderId, Conversation conversation) {
        this.id = id;
        this.senderId = senderId;
        this.conversation = conversation;
    }

    boolean isRecipient(String userId) {
        return !userId.equals(senderId) && conversation.users.contains(userId);
    }

    int recipientCount() {
        return conversation.users.size() - 1;
    }

    /** Everyone in the conversation except the sender, sorted by user id. */
    List<String> recipients() {
        List<String> result = new ArrayList<>();
        for (String user : conversation.sortedUsers) {
            if (!user.equals(senderId)) {
                result.add(user);
            }
        }
        return result;
    }
}

/** Strategy: each conversation type has its own rule for the ticks. */
interface ReceiptStrategy {
    String getStatus(Message message);
}

/** DIRECT: one recipient. Blue ticks need read receipts ON for both people. */
class DirectReceiptStrategy implements ReceiptStrategy {
    Set<String> readReceiptsOff;

    DirectReceiptStrategy(Set<String> readReceiptsOff) {
        this.readReceiptsOff = readReceiptsOff;
    }

    public String getStatus(Message message) {
        String recipient = message.recipients().get(0);
        if (!message.deliveredAt.containsKey(recipient)) {
            return "SENT_ONE_GRAY_TICK";
        }
        boolean bothOn = !readReceiptsOff.contains(message.senderId)
                && !readReceiptsOff.contains(recipient);
        if (message.readAt.containsKey(recipient) && bothOn) {
            return "READ_TWO_BLUE_TICKS";
        }
        return "DELIVERED_TWO_GRAY_TICKS";
    }
}

/** GROUP: compare counts with the number of recipients. Settings do not matter. */
class GroupReceiptStrategy implements ReceiptStrategy {
    public String getStatus(Message message) {
        int total = message.recipientCount();
        if (message.readAt.size() == total) {
            return "READ_TWO_BLUE_TICKS";
        }
        if (message.deliveredAt.size() == total) {
            return "DELIVERED_TWO_GRAY_TICKS";
        }
        return "SENT_ONE_GRAY_TICK";
    }
}
```