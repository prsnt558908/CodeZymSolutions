# Design Synchronized Movie Watch Party System in Python

#### Problem Statement
[https://codezym.com/question/254-synchronized-movie-watch-party](https://codezym.com/question/254-synchronized-movie-watch-party)


## Core Idea

A playing movie moves every millisecond, but we never update its position every millisecond. Each room keeps a small **snapshot** taken when its playback last changed: the position, the server time, the status and the speed. Any later position is worked out from that snapshot with one formula.

Each room also has a **version**. A host command must carry the version it knows, so a late or stale command is rejected instead of undoing a newer one.

The best fit is the **Observer pattern**. The room is the subject and its participants are the observers. After every successful host action, the room builds one new snapshot and pushes that same object to every participant, so everyone sees the same state. State and Command patterns also look tempting, but plain code is simpler for those parts, as explained below.


## The Rules in Simple Words

- Only the host can play, pause, seek or change speed. Anyone in the room can read the state.
- A room holds at most 50 participants, and the host is one of them.
- While `PLAYING`, the position is `anchorPositionMs + (serverTimeMs - anchorServerTimeMs) * playbackSpeed`. While `PAUSED`, it does not move.
- Drop the decimal part, and never go past the movie's end.
- A host action with the wrong `expectedVersion` fails. A successful one adds 1 to the version.
- A `movieId` has one duration across the whole system.
- A failed call changes nothing.


## Solution 1: One Class With Many Dictionaries

### Idea

The most direct way is one dictionary per field, all keyed by room id. A helper `refresh()` moves the stored position forward to "now". Reads and host actions call it before they use the position.

```python
def __init__(self):
    self.host_of = {}
    self.movie_of = {}
    self.duration_of = {}
    self.status_of = {}        # "PLAYING" or "PAUSED"
    self.position_of = {}
    self.last_update_of = {}   # when position_of was last moved
    self.speed_of = {}
    self.version_of = {}
    self.users_of = {}         # room id -> set of user ids
    # ... and one dictionary for movie durations

# Moves the stored position forward to "now"
def refresh(self, room_id, server_time_ms):
    if self.status_of[room_id] == "PLAYING":
        elapsed = server_time_ms - self.last_update_of[room_id]
        position = int(self.position_of[room_id] + elapsed * self.speed_of[room_id])
        self.position_of[room_id] = min(position, self.duration_of[room_id])
    self.last_update_of[room_id] = server_time_ms
```

A host action checks the rules, refreshes and updates the dictionaries.

```python
def pause(self, roomId, userId, expectedVersion, serverTimeMs):
    if roomId not in self.host_of:
        return "ERROR:ROOM_NOT_FOUND"
    if userId not in self.users_of[roomId]:
        return "ERROR:USER_NOT_IN_ROOM"
    if self.host_of[roomId] != userId:
        return "ERROR:UNAUTHORIZED"
    if self.version_of[roomId] != expectedVersion:
        return "ERROR:VERSION_MISMATCH"
    self.refresh(roomId, serverTimeMs)
    self.status_of[roomId] = "PAUSED"
    self.version_of[roomId] = expectedVersion + 1
    return self.describe(roomId)
```

`play`, `seek` and `changePlaybackSpeed` look almost the same.


### Problems

This passes every example in the problem statement, but it is still wrong.

- **Positions drift at 0.5x and 1.5x.** `refresh()` drops the decimal part each time it runs, and it runs on every read too. At 0.5x, a read 1 ms later adds 0.5 ms and then throws it away. If someone reads every millisecond, the movie never moves. The right positions at 1, 2, 3 and 4 ms are 0, 1, 1 and 2, but this code prints 0 every time.
- **Nine dictionaries describe one room.** Every method must keep them in step, and missing one is an easy bug.
- **The same four checks are copied** into all four host actions.
- **Participants are just names in a set.** When the host acts, nobody is told. Nothing in the design keeps viewers in sync.


## Solution 2: Snapshots and the Observer Pattern

We fix these problems in three steps.


### Step 1: Store a snapshot, not a live position

Only host actions change how the movie moves. So each host action saves a snapshot, and reads never touch it.

- `anchor_position_ms`: where the movie was at that moment
- `anchor_server_time_ms`: when that moment was
- `status` and `playback_speed`

Any later position comes from the formula in the rules. A read only computes the position, it never saves it. So decimals are lost only when a host action saves a new snapshot, which is exactly what the rules ask for.

Example 2 shows why a speed change must use the old speed first.

| Time | Call | Snapshot after the call | Position shown |
|---|---|---|---|
| 5000 | `play` | 0 at 5000, speed 1.0 | 0 |
| 7000 | `changePlaybackSpeed(2.0)` | 2000 at 7000, speed 2.0 | 2000 |
| 9000 | `getPlaybackState` | no change | 2000 + 2000 × 2.0 = 6000 |

From 5000 to 7000 the movie ran at the old speed, so it reached 2000. Only then does the new speed start.


### Step 2: One class for each idea

| Class | Job |
|---|---|
| `PlaybackSnapshot` | One snapshot. Computes the position and builds the output string. |
| `Room` | The host, the current snapshot and the participants of one room. Runs the four host actions. |
| `Participant` | One user in a room. Keeps the latest snapshot it received. |
| `PlaybackObserver` | The small abstract base class the room uses to notify participants. |
| `WatchPartySystem` | The entry point. Runs the checks and finds the right room. |

The main data structures are three dictionaries.

- **`rooms`** maps a room id to its `Room`, so a room is found in O(1).
- **`movie_durations`** keeps the one duration of every movie. It sits outside the rooms because this rule covers the whole system.
- **`participants`** inside `Room` maps a user id to its `Participant`. It answers "is this user in the room?" in O(1), and its length is the participant count for the 50 limit.

We never change a snapshot after creating it. A host action builds a new one with `next()`, which also adds 1 to the version. So a snapshot that was handed out can never change under anyone's feet.

The four checks of a host action live in one method, `check_host_action()`, in the required order: room, membership, host, version.


### Step 3: The Observer pattern keeps everyone in sync

```text
 host calls play()
         |
         v
       Room  ---- new snapshot ---->  Participant hostA
             ---- new snapshot ---->  Participant userB
             ---- new snapshot ---->  Participant userC
```

`Room` is the subject and each `Participant` is an observer. `publish()` saves the new snapshot and calls `on_playback_changed()` on every participant, so they all hold the same object.

A user who joins late gets the current snapshot the moment they join. `getPlaybackState` then just shows that user their own latest snapshot at the asked time.

**Why Observer and not plain code?** For these methods, reading the room's snapshot directly would give the same answers. But in a real watch party every participant is a separate device, and the server must push each change to all of them. Observer models exactly that push, and the room does not care what a participant does with an update.

Pushing a snapshot only passes a reference, so a host action costs at most 50 tiny calls.


### Patterns we did not use

- **State pattern** for `PLAYING` and `PAUSED`. Every action is allowed in both statuses, and the only difference is whether time moves the position. That is one `if` in `position_at()`. State classes would add code without removing any.
- **Command pattern** for the four host actions. It helps when actions must be queued, undone or replayed. Here each action runs once, right away, and the version check already throws away stale commands.


### Code

```python
from abc import ABC, abstractmethod
from enum import Enum


class WatchPartySystem:
    """Entry point of the system. It keeps all rooms and the one duration of every movie.
    It runs the checks for each call, then lets the room do the real work."""

    def __init__(self):
        self.max_participants = 50
        # room id -> room
        self.rooms = {}
        # movie id -> its duration, the same for every room that plays this movie
        self.movie_durations = {}

    def createRoom(self, roomId: str, hostUserId: str, movieId: str,
                   movieDurationMs: int, serverTimeMs: int) -> str:
        if roomId in self.rooms:
            return "ERROR:ROOM_ALREADY_EXISTS"
        known_duration = self.movie_durations.get(movieId)
        if known_duration is not None and known_duration != movieDurationMs:
            return "ERROR:MOVIE_DURATION_MISMATCH"
        # saved only now, after every check has passed
        self.movie_durations[movieId] = movieDurationMs

        first = PlaybackSnapshot(roomId, movieId, movieDurationMs,
                                 PlaybackStatus.PAUSED, 0, serverTimeMs, 1.0, 1)
        self.rooms[roomId] = Room(hostUserId, first)
        return roomId

    def joinRoom(self, roomId: str, userId: str, serverTimeMs: int) -> str:
        room = self.rooms.get(roomId)
        if room is None:
            return "ERROR:ROOM_NOT_FOUND"
        participant = room.participants.get(userId)
        # a user who is already inside gets the state, even when the room is full
        if participant is None:
            if len(room.participants) >= self.max_participants:
                return "ERROR:ROOM_FULL"
            participant = room.add_participant(userId)
        return participant.view(serverTimeMs)

    def play(self, roomId: str, userId: str, expectedVersion: int, serverTimeMs: int) -> str:
        room = self.rooms.get(roomId)
        error = self.check_host_action(room, userId, expectedVersion)
        if error is not None:
            return error
        room.play(serverTimeMs)
        return room.snapshot.describe(serverTimeMs)

    def pause(self, roomId: str, userId: str, expectedVersion: int, serverTimeMs: int) -> str:
        room = self.rooms.get(roomId)
        error = self.check_host_action(room, userId, expectedVersion)
        if error is not None:
            return error
        room.pause(serverTimeMs)
        return room.snapshot.describe(serverTimeMs)

    def seek(self, roomId: str, userId: str, expectedVersion: int,
             positionMs: int, serverTimeMs: int) -> str:
        room = self.rooms.get(roomId)
        error = self.check_host_action(room, userId, expectedVersion)
        if error is not None:
            return error
        room.seek(positionMs, serverTimeMs)
        return room.snapshot.describe(serverTimeMs)

    def changePlaybackSpeed(self, roomId: str, userId: str, expectedVersion: int,
                            playbackSpeed: float, serverTimeMs: int) -> str:
        room = self.rooms.get(roomId)
        error = self.check_host_action(room, userId, expectedVersion)
        if error is not None:
            return error
        room.change_speed(playbackSpeed, serverTimeMs)
        return room.snapshot.describe(serverTimeMs)

    def getPlaybackState(self, roomId: str, userId: str, serverTimeMs: int) -> str:
        room = self.rooms.get(roomId)
        if room is None:
            return "ERROR:ROOM_NOT_FOUND"
        participant = room.participants.get(userId)
        if participant is None:
            return "ERROR:USER_NOT_IN_ROOM"
        return participant.view(serverTimeMs)

    def check_host_action(self, room, user_id, expected_version):
        """The four checks of every host action, in the required order. Returns None if all pass."""
        if room is None:
            return "ERROR:ROOM_NOT_FOUND"
        if user_id not in room.participants:
            return "ERROR:USER_NOT_IN_ROOM"
        if room.host_user_id != user_id:
            return "ERROR:UNAUTHORIZED"
        if room.snapshot.version != expected_version:
            return "ERROR:VERSION_MISMATCH"
        return None


class PlaybackStatus(Enum):
    PLAYING = "PLAYING"
    PAUSED = "PAUSED"


class PlaybackSnapshot:
    """The room's playback, saved when it last changed (the "anchor").
    A snapshot never changes. Every successful host action creates a new one."""

    def __init__(self, room_id, movie_id, movie_duration_ms, status,
                 anchor_position_ms, anchor_server_time_ms, playback_speed, version):
        self.room_id = room_id
        self.movie_id = movie_id
        self.movie_duration_ms = movie_duration_ms
        self.status = status
        self.anchor_position_ms = anchor_position_ms        # movie position when this snapshot was taken
        self.anchor_server_time_ms = anchor_server_time_ms  # server time when this snapshot was taken
        self.playback_speed = playback_speed
        self.version = version

    def position_at(self, server_time_ms):
        """Movie position at the given time. Decimals are dropped and it never passes the end."""
        position = self.anchor_position_ms
        if self.status == PlaybackStatus.PLAYING:
            position += (server_time_ms - self.anchor_server_time_ms) * self.playback_speed
        return min(int(position), self.movie_duration_ms)

    def next(self, new_status, new_position_ms, server_time_ms, new_speed):
        """The snapshot after a successful host action: same room and movie, version + 1."""
        return PlaybackSnapshot(self.room_id, self.movie_id, self.movie_duration_ms, new_status,
                                new_position_ms, server_time_ms, new_speed, self.version + 1)

    def describe(self, server_time_ms):
        """The playback state string, with the position computed at the given time."""
        return (f"roomId={self.room_id},movieId={self.movie_id},"
                f"movieDurationMs={self.movie_duration_ms},status={self.status.value},"
                f"positionMs={self.position_at(server_time_ms)},"
                f"playbackSpeed={self.playback_speed:.1f},version={self.version}")


class PlaybackObserver(ABC):
    """Observer: anyone who wants to hear about playback changes in a room."""

    @abstractmethod
    def on_playback_changed(self, snapshot):
        pass


class Participant(PlaybackObserver):
    """A user inside a room. Keeps the latest snapshot the room pushed to them."""

    def __init__(self, user_id):
        self.user_id = user_id
        self.latest_snapshot = None

    def on_playback_changed(self, snapshot):
        self.latest_snapshot = snapshot

    def view(self, server_time_ms):
        """What this user sees at the given time."""
        return self.latest_snapshot.describe(server_time_ms)


class Room:
    """Subject: one watch party. Every new snapshot is pushed to all of its participants."""

    def __init__(self, host_user_id, first_snapshot):
        self.host_user_id = host_user_id
        self.snapshot = first_snapshot
        # user id -> participant, for O(1) "is this user in the room?" checks
        self.participants = {}
        self.add_participant(host_user_id)  # the host is also a participant

    def add_participant(self, user_id):
        participant = Participant(user_id)
        self.participants[user_id] = participant
        participant.on_playback_changed(self.snapshot)  # a late joiner is synced right away
        return participant

    def publish(self, new_snapshot):
        """Saves the new snapshot and notifies every participant."""
        self.snapshot = new_snapshot
        for observer in self.participants.values():
            observer.on_playback_changed(new_snapshot)

    def play(self, server_time_ms):
        position = self.snapshot.position_at(server_time_ms)
        self.publish(self.snapshot.next(PlaybackStatus.PLAYING, position, server_time_ms,
                                        self.snapshot.playback_speed))

    def pause(self, server_time_ms):
        position = self.snapshot.position_at(server_time_ms)
        self.publish(self.snapshot.next(PlaybackStatus.PAUSED, position, server_time_ms,
                                        self.snapshot.playback_speed))

    def seek(self, position_ms, server_time_ms):
        self.publish(self.snapshot.next(self.snapshot.status, position_ms, server_time_ms,
                                        self.snapshot.playback_speed))

    def change_speed(self, new_speed, server_time_ms):
        # the position so far is computed with the OLD speed
        position = self.snapshot.position_at(server_time_ms)
        self.publish(self.snapshot.next(self.snapshot.status, position, server_time_ms, new_speed))
```


### Complexity

- `createRoom`, `joinRoom` and `getPlaybackState`: O(1).
- `play`, `pause`, `seek` and `changePlaybackSpeed`: O(P), where P is the number of participants in the room. P is at most 50, so this is O(1) in practice.
- Memory: O(R + U + M) for R rooms, U participants across all rooms and M movies.


## Things That Are Easy to Miss

- A failed `createRoom` must not save the movie's duration.
- A user who has already joined gets the state even when the room is full. So check "already inside" before "full".
- The host counts toward the 50.
- Reaching the end does not pause the movie. The status stays `PLAYING` and the position stays at the duration.
- `play` while playing, `pause` while paused and changing to the same speed still succeed. The version goes up and a new snapshot is taken, which drops any decimal part.
- `joinRoom` and `getPlaybackState` never change the snapshot or the version.
- Drop decimals with `int()`, never `round()`. Print the speed with `:.1f`, so a speed given as `2` still prints as `2.0`.