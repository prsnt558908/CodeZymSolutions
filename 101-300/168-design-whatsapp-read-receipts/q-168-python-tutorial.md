# Design WhatsApp Read Receipts in Python

#### Problem Statement
[https://codezym.com/question/168-design-whatsapp-read-receipts](https://codezym.com/question/168-design-whatsapp-read-receipts)


## Core Idea

Every message keeps two small dictionaries: who has **received** it and who has **read** it, with the time of each event. When the sender asks for the ticks, we work them out from these two dictionaries.


The only part that changes from one chat to another is the **tick rule**. A direct chat looks at read receipt settings. A group chat ignores them. So we use the **Strategy pattern**, with one small class per conversation type.


Strategy alone is the best fit here. The event system (the Observer part) is already built and simply calls our methods. Everything else is plain classes with dictionaries and sets.


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

```python
# First attempt: walk every recipient on every status check
allDelivered = True
allRead = True
for user in message.conversation.users:
    if user == message.senderId:
        continue
    if user not in message.deliveredAt:
        allDelivered = False
    if user not in message.readAt:
        allRead = False

if message.conversation.type == "DIRECT":
    recipient = message.recipients()[0]
    bothOn = (message.senderId not in self.readReceiptsOff
              and recipient not in self.readReceiptsOff)
    if not allDelivered:
        return "SENT_ONE_GRAY_TICK"
    return "READ_TWO_BLUE_TICKS" if allRead and bothOn else "DELIVERED_TWO_GRAY_TICKS"

# GROUP
if allRead:
    return "READ_TWO_BLUE_TICKS"
return "DELIVERED_TWO_GRAY_TICKS" if allDelivered else "SENT_ONE_GRAY_TICK"
```

It gives correct answers, but it has two problems:

1. **It is slow for big groups.** Every status check walks the whole group. With 1,000 members and 100,000 checks, that is 100 million steps.
2. **The rules are tangled.** Direct and group logic sit in one method. A new chat type, like a broadcast list, means editing this method again and risking the old rules.

Let us fix them one at a time.


## Improvement 1: Count, Do Not Scan

Look at who can enter the two dictionaries:

- Only real recipients. Anyone else is rejected before we touch the dictionaries.
- When someone reads a message, we also add them to `deliveredAt`.

So `len(message.deliveredAt)` is the number of people who got the message, and `len(message.readAt)` is the number who read it. Comparing them with the number of recipients gives the group ticks in O(1).

```python
total = message.recipientCount()   # members minus the sender
if len(message.readAt) == total:
    return "READ_TWO_BLUE_TICKS"
if len(message.deliveredAt) == total:
    return "DELIVERED_TWO_GRAY_TICKS"
return "SENT_ONE_GRAY_TICK"
```

Here is Example 3, where alice sends `g1` to bob and charlie (2 recipients):

| Event | `len(deliveredAt)` | `len(readAt)` | Ticks alice sees |
|---|---|---|---|
| bob receives it | 1 | 0 | one gray tick |
| charlie receives it | 2 | 0 | two gray ticks |
| bob reads it | 2 | 1 | two gray ticks |
| charlie reads it | 2 | 2 | two blue ticks |

Bob has read receipts `OFF`, but the group still turns blue, because group ticks ignore settings.


Why dictionaries and not sets? The group summary needs the **time** of each delivery and read. A dictionary from user to time gives us the count (its length) and the time in one place.


An entry is added only when an event happens. A message that nobody opens costs almost nothing, even in a huge group.


## Improvement 2: Strategy Pattern for the Tick Rules

We move each tick rule into its own class behind one base class:

```python
class ReceiptStrategy:
    def getStatus(self, message) -> str:
        raise NotImplementedError
```

- `GroupReceiptStrategy` is the counting code above.
- `DirectReceiptStrategy` looks at the single recipient. It returns blue ticks only if the recipient read the message and both users have read receipts `ON`.

We create one object of each type and keep them in a dictionary. Each conversation picks its strategy once, when it is loaded:

```python
self.strategies["DIRECT"] = DirectReceiptStrategy(self.readReceiptsOff)
self.strategies["GROUP"] = GroupReceiptStrategy()
```

Now `getReceiptStatus` needs no if/else for chat types:

```python
return message.conversation.strategy.getStatus(message)
```

**Why Strategy and not a plain if/else?** With two chat types, if/else works fine. But the tick rule is the part most likely to grow: broadcast lists, communities, new privacy options.


With Strategy, a new chat type is one new class and one new entry in `self.strategies`. The old rules stay untouched. The dictionary also tells us which types are valid, so a config with an unknown type is simply skipped.


## Patterns That Sound Good but Are Not Needed

**State pattern.** A receipt moves from sent to delivered to read, so State looks like a natural fit. But it only moves forward, and each answer (`NO_CHANGE` or not) is a single dictionary lookup.


State would add three classes for what two dictionaries already do. Also, the sender's ticks do not come from one object's state. They come from all recipients together, plus the settings.


**Observer pattern.** Delivered and read events sound like a classic Observer case. But the problem says that event system is already built. Our class is simply where those events land, so building Observer again would only add empty plumbing.


## Classes and Data Structures

| Class or field | Why we need it |
|---|---|
| `self.conversations` (dict) | Find a chat by id in O(1) |
| `self.messages` (dict) | Find a message by id in O(1), and reject a reused message id |
| `self.readReceiptsOff` (set) | Users who turned read receipts `OFF`. Anyone not in the set is `ON`, which is exactly the default rule |
| `Conversation.users` (set) | Check in O(1) whether a user belongs to the chat |
| `Conversation.sortedUsers` (list) | The same members, sorted once at load time, so the summary never sorts again |
| `Message.deliveredAt` and `Message.readAt` (dicts) | User to time. Their lengths are the counts, and their values fill the summary |


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

```python
from typing import List


class ReceiptStrategy:
    """Strategy: each conversation type has its own rule for the ticks."""

    def getStatus(self, message) -> str:
        raise NotImplementedError


class DirectReceiptStrategy(ReceiptStrategy):
    """DIRECT: one recipient. Blue ticks need read receipts ON for both people."""

    def __init__(self, readReceiptsOff):
        self.readReceiptsOff = readReceiptsOff

    def getStatus(self, message) -> str:
        recipient = message.recipients()[0]
        if recipient not in message.deliveredAt:
            return "SENT_ONE_GRAY_TICK"
        bothOn = (message.senderId not in self.readReceiptsOff
                  and recipient not in self.readReceiptsOff)
        if recipient in message.readAt and bothOn:
            return "READ_TWO_BLUE_TICKS"
        return "DELIVERED_TWO_GRAY_TICKS"


class GroupReceiptStrategy(ReceiptStrategy):
    """GROUP: compare counts with the number of recipients. Settings do not matter."""

    def getStatus(self, message) -> str:
        total = message.recipientCount()
        if len(message.readAt) == total:
            return "READ_TWO_BLUE_TICKS"
        if len(message.deliveredAt) == total:
            return "DELIVERED_TWO_GRAY_TICKS"
        return "SENT_ONE_GRAY_TICK"


class Conversation:
    """A DIRECT or GROUP chat. Its members never change."""

    def __init__(self, conversationId, conversationType, users, strategy):
        self.id = conversationId
        self.type = conversationType
        self.users = users                # fast "is this user in the chat?" check
        self.sortedUsers = sorted(users)  # same users, sorted once for the group summary
        self.strategy = strategy          # tick rule for this conversation type


class Message:
    """One sent message and what happened to it for each recipient."""

    def __init__(self, messageId, senderId, conversation):
        self.id = messageId
        self.senderId = senderId
        self.conversation = conversation
        self.deliveredAt = {}  # recipient -> time the message was delivered (readers are here too)
        self.readAt = {}       # recipient -> time the message was read

    def isRecipient(self, userId) -> bool:
        return userId != self.senderId and userId in self.conversation.users

    def recipientCount(self) -> int:
        return len(self.conversation.users) - 1

    def recipients(self) -> List[str]:
        """Everyone in the conversation except the sender, sorted by user id."""
        return [user for user in self.conversation.sortedUsers if user != self.senderId]


class WhatsAppReadReceipts:
    """
    Read receipt feature of WhatsApp.

    The event system is already built. It calls sendMessage, markDelivered
    and markRead. We store those events and turn them into ticks for the sender.
    """

    def __init__(self, conversationConfigs: List[str], userSettings: List[str]):
        self.conversations = {}
        self.messages = {}
        self.readReceiptsOff = set()  # users who turned read receipts OFF, everyone else is ON
        self.strategies = {}          # conversation type -> tick rule for that type
        self.lastTime = 0             # largest currentTime seen so far

        for setting in userSettings:
            self.addSetting(setting)
        self.strategies["DIRECT"] = DirectReceiptStrategy(self.readReceiptsOff)
        self.strategies["GROUP"] = GroupReceiptStrategy()
        for config in conversationConfigs:
            self.addConversation(config)

    def sendMessage(self, messageId: str, conversationId: str, senderUserId: str, currentTime: int) -> str:
        if (not self.isTimeValid(currentTime) or self.isEmpty(messageId)
                or self.isEmpty(conversationId) or self.isEmpty(senderUserId)):
            return "INVALID_REQUEST"
        conversation = self.conversations.get(conversationId)
        if (conversation is None or senderUserId not in conversation.users
                or messageId in self.messages):
            return "INVALID_REQUEST"
        self.messages[messageId] = Message(messageId, senderUserId, conversation)
        return "MESSAGE_SENT"

    def markDelivered(self, messageId: str, userId: str, currentTime: int) -> str:
        message = self.findMessageForRecipient(messageId, userId, currentTime)
        if message is None:
            return "INVALID_REQUEST"
        # Readers are also in deliveredAt, so this covers "already read" too
        if userId in message.deliveredAt:
            return "NO_CHANGE"
        message.deliveredAt[userId] = currentTime
        return "DELIVERY_RECORDED"

    def markRead(self, messageId: str, userId: str, currentTime: int) -> str:
        message = self.findMessageForRecipient(messageId, userId, currentTime)
        if message is None:
            return "INVALID_REQUEST"
        if userId in message.readAt:
            return "NO_CHANGE"
        message.readAt[userId] = currentTime
        # Reading a message also means it was delivered
        message.deliveredAt.setdefault(userId, currentTime)
        return "READ_RECORDED"

    def getReceiptStatus(self, messageId: str, viewerUserId: str, currentTime: int) -> str:
        if not self.isTimeValid(currentTime) or self.isEmpty(messageId) or self.isEmpty(viewerUserId):
            return "INVALID_REQUEST"
        message = self.messages.get(messageId)
        if message is None:
            return "INVALID_REQUEST"
        if message.senderId != viewerUserId:
            return "NOT_VISIBLE"
        return message.conversation.strategy.getStatus(message)

    def getGroupReadSummary(self, messageId: str, viewerUserId: str, currentTime: int) -> List[str]:
        summary = []
        if not self.isTimeValid(currentTime) or self.isEmpty(messageId) or self.isEmpty(viewerUserId):
            return summary
        message = self.messages.get(messageId)
        if (message is None or message.senderId != viewerUserId
                or message.conversation.type != "GROUP"):
            return summary
        # recipients() is already sorted by user id
        for user in message.recipients():
            if user in message.deliveredAt:
                delivered = "DELIVERED," + str(message.deliveredAt[user])
            else:
                delivered = "NOT_DELIVERED,-1"
            if user in message.readAt:
                read = "READ," + str(message.readAt[user])
            else:
                read = "NOT_READ,-1"
            summary.append(user + "," + delivered + "," + read)
        return summary

    def findMessageForRecipient(self, messageId, userId, currentTime):
        """Common checks of markDelivered and markRead. Returns None if the request is invalid."""
        if not self.isTimeValid(currentTime) or self.isEmpty(messageId) or self.isEmpty(userId):
            return None
        message = self.messages.get(messageId)
        if message is None or not message.isRecipient(userId):
            return None
        return message

    def isTimeValid(self, currentTime) -> bool:
        """
        Time can never go back. This check runs first in every method,
        so every call (even one that fails later for another reason) moves lastTime forward.
        """
        if currentTime < self.lastTime:
            return False
        self.lastTime = currentTime
        return True

    def isEmpty(self, value) -> bool:
        return value.strip() == ""

    def addSetting(self, setting):
        """Parses "userId,ON" or "userId,OFF". Broken lines are skipped."""
        parts = setting.split(",")
        if len(parts) < 2 or self.isEmpty(parts[0]):
            return
        userId = parts[0].strip()
        value = parts[1].strip()
        if value == "OFF":
            self.readReceiptsOff.add(userId)
        elif value == "ON":
            self.readReceiptsOff.discard(userId)

    def addConversation(self, config):
        """Parses "conversationId,TYPE,user1|user2|...". Broken configs are skipped."""
        parts = config.split(",")
        if len(parts) < 3:
            return
        conversationId = parts[0].strip()
        conversationType = parts[1].strip()
        strategy = self.strategies.get(conversationType)
        if conversationId == "" or strategy is None or conversationId in self.conversations:
            return
        users = set()
        for user in parts[2].split("|"):
            if not self.isEmpty(user):
                users.add(user.strip())
        # DIRECT needs exactly 2 distinct users, GROUP needs at least 2
        if conversationType == "DIRECT":
            validSize = len(users) == 2
        else:
            validSize = len(users) >= 2
        if validSize:
            self.conversations[conversationId] = Conversation(
                conversationId, conversationType, users, strategy)
```