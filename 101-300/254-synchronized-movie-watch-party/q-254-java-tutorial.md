# Design Synchronized Movie Watch Party System in Java

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


## Solution 1: One Class With Many Maps

### Idea

The most direct way is one map per field, all keyed by room id. A helper `refresh()` moves the stored position forward to "now". Reads and host actions call it before they use the position.

```java
Map<String, String> hostOf = new HashMap<>();
Map<String, String> movieOf = new HashMap<>();
Map<String, Long> durationOf = new HashMap<>();
Map<String, String> statusOf = new HashMap<>();       // "PLAYING" or "PAUSED"
Map<String, Long> positionOf = new HashMap<>();
Map<String, Long> lastUpdateOf = new HashMap<>();     // when positionOf was last moved
Map<String, Double> speedOf = new HashMap<>();
Map<String, Integer> versionOf = new HashMap<>();
Map<String, Set<String>> usersOf = new HashMap<>();

// Moves the stored position forward to "now"
void refresh(String roomId, long serverTimeMs) {
    if (statusOf.get(roomId).equals("PLAYING")) {
        long elapsed = serverTimeMs - lastUpdateOf.get(roomId);
        long position = (long) (positionOf.get(roomId) + elapsed * speedOf.get(roomId));
        positionOf.put(roomId, Math.min(position, durationOf.get(roomId)));
    }
    lastUpdateOf.put(roomId, serverTimeMs);
}
```

A host action checks the rules, refreshes and updates the maps.

```java
public String pause(String roomId, String userId, int expectedVersion, long serverTimeMs) {
    if (!hostOf.containsKey(roomId)) return "ERROR:ROOM_NOT_FOUND";
    if (!usersOf.get(roomId).contains(userId)) return "ERROR:USER_NOT_IN_ROOM";
    if (!hostOf.get(roomId).equals(userId)) return "ERROR:UNAUTHORIZED";
    if (versionOf.get(roomId) != expectedVersion) return "ERROR:VERSION_MISMATCH";
    refresh(roomId, serverTimeMs);
    statusOf.put(roomId, "PAUSED");
    versionOf.put(roomId, expectedVersion + 1);
    return describe(roomId);
}
```

`play`, `seek` and `changePlaybackSpeed` look almost the same.


### Problems

This passes every example in the problem statement, but it is still wrong.

- **Positions drift at 0.5x and 1.5x.** `refresh()` drops the decimal part each time it runs, and it runs on every read too. At 0.5x, a read 1 ms later adds 0.5 ms and then throws it away. If someone reads every millisecond, the movie never moves. The right positions at 1, 2, 3 and 4 ms are 0, 1, 1 and 2, but this code prints 0 every time.
- **Nine maps describe one room.** Every method must keep them in step, and missing one is an easy bug.
- **The same four checks are copied** into all four host actions.
- **Participants are just names in a set.** When the host acts, nobody is told. Nothing in the design keeps viewers in sync.


## Solution 2: Snapshots and the Observer Pattern

We fix these problems in three steps.


### Step 1: Store a snapshot, not a live position

Only host actions change how the movie moves. So each host action saves a snapshot, and reads never touch it.

- `anchorPositionMs`: where the movie was at that moment
- `anchorServerTimeMs`: when that moment was
- `status` and `playbackSpeed`

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
| `PlaybackObserver` | The small interface the room uses to notify participants. |
| `WatchPartySystem` | The entry point. Runs the checks and finds the right room. |

The main data structures are three maps.

- **`Map<String, Room> rooms`** finds a room by id in O(1).
- **`Map<String, Long> movieDurations`** keeps the one duration of every movie. It sits outside the rooms because this rule covers the whole system.
- **`Map<String, Participant> participants`** inside `Room` answers "is this user in the room?" in O(1). Its size is the participant count for the 50 limit.

We never change a snapshot after creating it. A host action builds a new one with `next()`, which also adds 1 to the version. So a snapshot that was handed out can never change under anyone's feet.

The four checks of a host action live in one method, `checkHostAction()`, in the required order: room, membership, host, version.


### Step 3: The Observer pattern keeps everyone in sync

```text
 host calls play()
         |
         v
       Room  ---- new snapshot ---->  Participant hostA
             ---- new snapshot ---->  Participant userB
             ---- new snapshot ---->  Participant userC
```

`Room` is the subject and each `Participant` is an observer. `publish()` saves the new snapshot and calls `onPlaybackChanged()` on every participant, so they all hold the same object.

A user who joins late gets the current snapshot the moment they join. `getPlaybackState` then just shows that user their own latest snapshot at the asked time.

**Why Observer and not plain code?** For these methods, reading the room's snapshot directly would give the same answers. But in a real watch party every participant is a separate device, and the server must push each change to all of them. Observer models exactly that push, and the room does not care what a participant does with an update.

Pushing a snapshot only passes a reference, so a host action costs at most 50 tiny calls.


### Patterns we did not use

- **State pattern** for `PLAYING` and `PAUSED`. Every action is allowed in both statuses, and the only difference is whether time moves the position. That is one `if` in `positionAt()`. State classes would add code without removing any.
- **Command pattern** for the four host actions. It helps when actions must be queued, undone or replayed. Here each action runs once, right away, and the version check already throws away stale commands.


### Code

```java
import java.util.*;

/**
 * Entry point of the system. It keeps all rooms and the one duration of every movie.
 * It runs the checks for each call, then lets the room do the real work.
 */
public class WatchPartySystem {
    int maxParticipants = 50;

    // roomId -> room
    Map<String, Room> rooms = new HashMap<>();

    // movieId -> its duration, the same for every room that plays this movie
    Map<String, Long> movieDurations = new HashMap<>();

    public String createRoom(String roomId, String hostUserId, String movieId,
                             long movieDurationMs, long serverTimeMs) {
        if (rooms.containsKey(roomId)) {
            return "ERROR:ROOM_ALREADY_EXISTS";
        }
        Long knownDuration = movieDurations.get(movieId);
        if (knownDuration != null && knownDuration != movieDurationMs) {
            return "ERROR:MOVIE_DURATION_MISMATCH";
        }
        // saved only now, after every check has passed
        movieDurations.put(movieId, movieDurationMs);

        PlaybackSnapshot first = new PlaybackSnapshot(roomId, movieId, movieDurationMs,
                PlaybackStatus.PAUSED, 0, serverTimeMs, 1.0, 1);
        rooms.put(roomId, new Room(hostUserId, first));
        return roomId;
    }

    public String joinRoom(String roomId, String userId, long serverTimeMs) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return "ERROR:ROOM_NOT_FOUND";
        }
        Participant participant = room.participants.get(userId);
        // a user who is already inside gets the state, even when the room is full
        if (participant == null) {
            if (room.participants.size() >= maxParticipants) {
                return "ERROR:ROOM_FULL";
            }
            participant = room.addParticipant(userId);
        }
        return participant.view(serverTimeMs);
    }

    public String play(String roomId, String userId, int expectedVersion, long serverTimeMs) {
        Room room = rooms.get(roomId);
        String error = checkHostAction(room, userId, expectedVersion);
        if (error != null) {
            return error;
        }
        room.play(serverTimeMs);
        return room.snapshot.describe(serverTimeMs);
    }

    public String pause(String roomId, String userId, int expectedVersion, long serverTimeMs) {
        Room room = rooms.get(roomId);
        String error = checkHostAction(room, userId, expectedVersion);
        if (error != null) {
            return error;
        }
        room.pause(serverTimeMs);
        return room.snapshot.describe(serverTimeMs);
    }

    public String seek(String roomId, String userId, int expectedVersion,
                       long positionMs, long serverTimeMs) {
        Room room = rooms.get(roomId);
        String error = checkHostAction(room, userId, expectedVersion);
        if (error != null) {
            return error;
        }
        room.seek(positionMs, serverTimeMs);
        return room.snapshot.describe(serverTimeMs);
    }

    public String changePlaybackSpeed(String roomId, String userId, int expectedVersion,
                                      double playbackSpeed, long serverTimeMs) {
        Room room = rooms.get(roomId);
        String error = checkHostAction(room, userId, expectedVersion);
        if (error != null) {
            return error;
        }
        room.changeSpeed(playbackSpeed, serverTimeMs);
        return room.snapshot.describe(serverTimeMs);
    }

    public String getPlaybackState(String roomId, String userId, long serverTimeMs) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return "ERROR:ROOM_NOT_FOUND";
        }
        Participant participant = room.participants.get(userId);
        if (participant == null) {
            return "ERROR:USER_NOT_IN_ROOM";
        }
        return participant.view(serverTimeMs);
    }

    /** The four checks of every host action, in the required order. Returns null if all pass. */
    String checkHostAction(Room room, String userId, int expectedVersion) {
        if (room == null) {
            return "ERROR:ROOM_NOT_FOUND";
        }
        if (!room.participants.containsKey(userId)) {
            return "ERROR:USER_NOT_IN_ROOM";
        }
        if (!room.hostUserId.equals(userId)) {
            return "ERROR:UNAUTHORIZED";
        }
        if (room.snapshot.version != expectedVersion) {
            return "ERROR:VERSION_MISMATCH";
        }
        return null;
    }
}

enum PlaybackStatus {
    PLAYING, PAUSED
}

/**
 * The room's playback, saved at the moment of the last host action (the "anchor").
 * A snapshot never changes. Every successful host action creates a new one.
 */
class PlaybackSnapshot {
    String roomId;
    String movieId;
    long movieDurationMs;
    PlaybackStatus status;
    long anchorPositionMs;      // movie position when this snapshot was taken
    long anchorServerTimeMs;    // server time when this snapshot was taken
    double playbackSpeed;
    int version;

    PlaybackSnapshot(String roomId, String movieId, long movieDurationMs, PlaybackStatus status,
                     long anchorPositionMs, long anchorServerTimeMs, double playbackSpeed,
                     int version) {
        this.roomId = roomId;
        this.movieId = movieId;
        this.movieDurationMs = movieDurationMs;
        this.status = status;
        this.anchorPositionMs = anchorPositionMs;
        this.anchorServerTimeMs = anchorServerTimeMs;
        this.playbackSpeed = playbackSpeed;
        this.version = version;
    }

    /** Movie position at the given time. Decimals are dropped and it never passes the end. */
    long positionAt(long serverTimeMs) {
        double position = anchorPositionMs;
        if (status == PlaybackStatus.PLAYING) {
            position += (serverTimeMs - anchorServerTimeMs) * playbackSpeed;
        }
        return Math.min((long) position, movieDurationMs);
    }

    /** The snapshot after a successful host action: same room and movie, version + 1. */
    PlaybackSnapshot next(PlaybackStatus newStatus, long newPositionMs, long serverTimeMs,
                          double newSpeed) {
        return new PlaybackSnapshot(roomId, movieId, movieDurationMs, newStatus,
                newPositionMs, serverTimeMs, newSpeed, version + 1);
    }

    /** The playback state string, with the position computed at the given time. */
    String describe(long serverTimeMs) {
        return "roomId=" + roomId
                + ",movieId=" + movieId
                + ",movieDurationMs=" + movieDurationMs
                + ",status=" + status
                + ",positionMs=" + positionAt(serverTimeMs)
                + ",playbackSpeed=" + playbackSpeed   // 0.5, 1.0, 1.5 and 2.0 print with one decimal
                + ",version=" + version;
    }
}

/** Observer: anyone who wants to hear about playback changes in a room. */
interface PlaybackObserver {
    void onPlaybackChanged(PlaybackSnapshot snapshot);
}

/** A user inside a room. Keeps the latest snapshot the room pushed to them. */
class Participant implements PlaybackObserver {
    String userId;
    PlaybackSnapshot latestSnapshot;

    Participant(String userId) {
        this.userId = userId;
    }

    @Override
    public void onPlaybackChanged(PlaybackSnapshot snapshot) {
        latestSnapshot = snapshot;
    }

    /** What this user sees at the given time. */
    String view(long serverTimeMs) {
        return latestSnapshot.describe(serverTimeMs);
    }
}

/** Subject: one watch party. Every new snapshot is pushed to all of its participants. */
class Room {
    String hostUserId;
    PlaybackSnapshot snapshot;

    // userId -> participant, for O(1) "is this user in the room?" checks
    Map<String, Participant> participants = new HashMap<>();

    Room(String hostUserId, PlaybackSnapshot first) {
        this.hostUserId = hostUserId;
        this.snapshot = first;
        addParticipant(hostUserId);   // the host is also a participant
    }

    Participant addParticipant(String userId) {
        Participant participant = new Participant(userId);
        participants.put(userId, participant);
        participant.onPlaybackChanged(snapshot);   // a late joiner is synced right away
        return participant;
    }

    /** Saves the new snapshot and notifies every participant. */
    void publish(PlaybackSnapshot newSnapshot) {
        snapshot = newSnapshot;
        for (PlaybackObserver observer : participants.values()) {
            observer.onPlaybackChanged(newSnapshot);
        }
    }

    void play(long serverTimeMs) {
        long position = snapshot.positionAt(serverTimeMs);
        publish(snapshot.next(PlaybackStatus.PLAYING, position, serverTimeMs, snapshot.playbackSpeed));
    }

    void pause(long serverTimeMs) {
        long position = snapshot.positionAt(serverTimeMs);
        publish(snapshot.next(PlaybackStatus.PAUSED, position, serverTimeMs, snapshot.playbackSpeed));
    }

    void seek(long positionMs, long serverTimeMs) {
        publish(snapshot.next(snapshot.status, positionMs, serverTimeMs, snapshot.playbackSpeed));
    }

    void changeSpeed(double newSpeed, long serverTimeMs) {
        // the position so far is computed with the OLD speed
        long position = snapshot.positionAt(serverTimeMs);
        publish(snapshot.next(snapshot.status, position, serverTimeMs, newSpeed));
    }
}
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
- Use `long` for times and positions. `anchorPositionMs + elapsed * speed` can pass the `int` range before it is clamped.