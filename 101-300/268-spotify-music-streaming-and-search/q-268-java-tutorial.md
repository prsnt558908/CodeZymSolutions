# Design Spotify Music Streaming in Java

#### Problem Statement
 [https://codezym.com/question/268-spotify-music-streaming-and-search](https://codezym.com/question/268-spotify-music-streaming-and-search)

Keep song details in one shared catalogue and keep each user's playback state separately. Two `HashMap` objects give us quick lookups, and a list lets us collect and sort search results. The most important detail is to copy a song's duration when playback starts, so later catalogue updates cannot change that session's duration.

## 1. Understand What We Need to Store

The service has two kinds of information.

**Song details belong to the catalogue.** Each song has an ID, title, artist, album, and duration. Adding an existing song ID replaces its catalogue details.

**Playback state belongs to a user.** Each user has a selected song, a status, a position, and the duration captured when that playback started. One user's actions must never change another user's playback.

The output formats are fixed:

- A search result is `songId,title,artist,album,durationSeconds`.
- A playback result is `status,songId,positionSeconds`.
- An empty playback session is `STOPPED,NO_SONG,0`.

The position does not move by itself. Only `playSong`, `SEEK`, and `STOP` change it. There is no clock, background player, or automatic end-of-song event to implement.

All inputs and action sequences are valid, so we do not need to invent results for unknown users, missing songs, or invalid pause and resume requests.

## 2. Start with Lists, Then Improve the Lookups

A simple first approach would keep all songs in a list and all users' playback states in another list.

To update or play a song, we would search the song list for its ID. To control playback, we would search the user list for that user. With `N` songs and `U` users, these lookups can take `O(N)` and `O(U)` time.

Most requests already provide an exact ID. We can use that ID as a map key:

- `Map<String, Song>` stores songs by song ID.
- `Map<String, PlaybackSession>` stores playback state by user ID.

These lookups take expected `O(1)` time. Replacing an existing song is also a single map update.

Search is different. A query can occur anywhere inside a title, artist, or album. A map keyed by song ID cannot directly answer that request. We still scan the songs, collect the matches in a list, and sort that list by song ID.

This keeps the design familiar: maps for exact lookups, lists for search results, and ordinary sorting when needed. It improves the ID-based operations without pretending that arbitrary substring search has become constant time.

## 3. Choose Small Classes with Clear Jobs

### `Song`: One Version of the Catalogue Details

`Song` holds the five catalogue fields. Its fields are `final`, so a `Song` object cannot change after creation.

An update creates a replacement `Song` object and puts it under the same ID. This matters when calls overlap: a search can safely read one complete version of a song without seeing a new title mixed with an old album.

We also store lowercase copies of the title, artist, and album. Search can reuse them instead of converting every song's text again on every request. The original strings remain available for correctly formatted output.

### `MusicCatalogue`: Shared Songs and Search

`MusicCatalogue` owns the song map. It handles adding, replacing, finding, and searching songs.

Keeping this work together gives us one place to protect the shared map when multiple threads access it.

### `PlaybackSession`: One User's Playback State

`PlaybackSession` stores the selected song ID, status, position, and captured duration.

It does **not** copy the title, artist, or album. Those details belong to the shared catalogue. If catalogue details change, we do not need to visit or rebuild every user's session.

The required playback response contains only status, song ID, and position. Whenever metadata is needed, the song ID identifies the current catalogue entry. The required `searchSongs` method returns those current details. The session duration is the deliberate exception: it stays fixed until the next `playSong` call or until playback is cleared.

### `SpotifyMusicService`: The Facade

The public service class exposes exactly the methods required by the problem. Its methods delegate to the appropriate component.

This is a small use of the Facade pattern: callers use one class while catalogue logic and playback logic remain separate. It helps organize the design. The maps, rather than the pattern itself, improve lookup time.

The **State pattern** may also seem suitable because playback has three statuses. However, the supplied actions have simple behavior: change a status, change a position, or clear the session. An enum and a short `switch` express those rules clearly. Separate classes for playing, paused, and stopped states would add unnecessary work for this problem.

## 4. Keep Catalogue Duration and Session Duration Separate

Suppose a song originally lasts `240` seconds.

1. User A starts it. A's playback duration becomes `240`.
2. User A seeks to position `200`.
3. The catalogue is updated: the song now lasts `60` seconds and has a new title.
4. User A pauses. The result is still `PAUSED,song-1,200`.
5. User B starts the song. B's playback duration becomes `60`.
6. User A starts the song again. A's position becomes `0`, and A's new playback duration becomes `60`.

Position `200` is valid for A's original session even after the catalogue duration becomes `60`. The old session still uses its captured duration of `240`.

This leads to three implementation rules:

- `addOrUpdateSong` changes only the catalogue.
- `playSong` reads the current catalogue duration and copies it into that user's playback state.
- `SEEK` uses the session's stored duration. It does not fetch the catalogue duration again or clamp the position to it.

Our seek guard checks the captured duration. Valid inputs never fail this guard, including a valid seek beyond a song's newly shortened catalogue duration.

The Java object used for a user's playback state remains in the user map. Starting another song replaces all the selected playback values inside it. This creates fresh logical playback while retaining a stable object to lock for that user.

## 5. Add and Update Songs

Create a complete `Song` object from the supplied values, then put it into the catalogue map under `songId`.

If the key is new, this adds the song. If the key already exists, `put` replaces the previous entry.

The user map is untouched. No position changes, no paused song resumes, and no captured duration changes.

## 6. Search Songs

Search follows these steps:

1. Convert the query to lowercase using `Locale.ROOT`.
2. Copy the catalogue's current song references into a local list while holding the catalogue lock.
3. Check the title, artist, and album of each song separately.
4. Add a song once if any of those three fields contains the query.
5. Sort the matching `Song` objects by `songId`.
6. Convert them into the required result strings.

`Locale.ROOT` makes lowercase conversion independent of the server's default locale. The query and the stored search fields use the same conversion.

We check the fields separately so a query cannot accidentally match text formed by joining the end of a title to the beginning of an artist name. Song IDs are not searchable fields.

The sort is lexicographic. For example, `song-10` comes before `song-2`. We do not interpret the numeric part of an ID as a number.

The local copy contains references, not deep copies of all song text. Because `Song` objects are immutable, a concurrent update can replace a catalogue entry without modifying an object already being searched. Each search therefore uses the catalogue contents captured when its list was copied.

No matches naturally produces an empty list.

## 7. Start and Control Playback

### Start or Restart

`playSong` gets the user's playback object, reads the requested song, and sets:

- The selected ID to the requested song ID.
- The captured duration to the song's current catalogue duration.
- The position to `0`.
- The status to `PLAYING`.

Calling `playSong` with the already selected song still restarts it. It does not behave like `RESUME`.

### Pause and Resume

`PAUSE` sets the status to `PAUSED`. `RESUME` sets it to `PLAYING`.

Both preserve the selected song, position, and captured duration. The `positionSeconds` argument is `0` for these actions, but that does not mean the stored playback position should become `0`.

### Seek

`SEEK` updates only the position. A paused song stays paused, and a playing song stays playing.

### Stop

`STOP` clears the selected song and resets the position and captured duration. It returns exactly `STOPPED,NO_SONG,0`.

These operations do not touch any other user's playback object.

## 8. Make Concurrent Calls Safe

A plain `HashMap` must not be read while another thread modifies it without proper synchronization. We therefore protect every access to the catalogue map with the same catalogue lock.

Search holds that lock only while copying the song references. Scanning, sorting, and formatting happen after the lock is released. An add or update does not have to wait for the entire search to finish, although it may briefly wait for the copy.

Each `PlaybackSession` has synchronized `play` and `control` methods. Calls for the same user run one at a time, including the construction of the returned state string. Calls for different users can control their playback independently.

Starting a song locks that user's session before reading the catalogue. This keeps the duration lookup and playback reset within one serialized operation for that user. Concurrent starts for different users may briefly share the catalogue lock while looking up songs.

The lock order is consistent: playback can acquire its user lock and then the catalogue lock. Catalogue operations never acquire a user lock, so these operations cannot form a circular lock dependency.

Finally, the user map is built during construction and wrapped with `Collections.unmodifiableMap`. It is never changed afterward. Threads only read that map. Changes to the playback objects it contains are protected by their own locks.

## 9. Walk Through an Example

Suppose the catalogue contains these songs:

- `song-20,Silent Road,Mira Sen,Journeys,210`
- `song-05,Road to Dawn,Kian Roy,First Light,195`

Searching for `ROAD` matches both titles, but the output places `song-05` first because results are sorted by ID.

User 1 starts `song-20` and gets `PLAYING,song-20,0`. Seeking to `45` returns `PLAYING,song-20,45`. Pausing returns `PAUSED,song-20,45`.

Now update `song-20` to `song-20,Silent Highway,Mira Sen,New Journeys,30`.

Searching for `road` now returns only `song-05`. Searching for `highway` returns the updated details of `song-20`, including its catalogue duration of `30`.

User 1 can still seek to `100` and receive `PAUSED,song-20,100`, because this playback captured a duration of `210`. Resuming returns `PLAYING,song-20,100`.

If User 2 starts `song-20` now, their captured duration is `30`. Stopping User 1 returns `STOPPED,NO_SONG,0` and leaves User 2's playback unchanged.

## 10. Why the Solution Is Correct

**The catalogue has one current entry per song ID.** Adding or updating a song replaces exactly that key. Search checks those current entries, includes a song once if a required field matches, and sorts all matches by ID.

**Each user has independent state.** The constructor creates a different playback object for every user. Playback methods locate only the requested user's object, so another user's selected song, status, position, and duration remain unchanged.

**Every action changes exactly the required values.** Starting playback resets position and captures duration. Pause and resume change only status. Seek changes only position. Stop clears all selected playback values. Nothing advances the position between calls.

**Catalogue updates preserve existing playback.** A session stores its own duration and no copied title, artist, or album. Replacing a catalogue entry neither resets that session nor changes its duration. A later start reads the replacement entry and captures the new duration.

**Concurrent calls preserve these rules.** Catalogue accesses use one lock, and each user's playback changes use that user's lock. Immutable song objects keep copied search data stable even when the catalogue is updated.

## 11. Time and Space Complexity

Let `N` be the number of songs, `U` the number of users, and `M` the number of matching songs.

Using the problem's fixed limits on text lengths:

- **Construction:** `O(U)` time to create the playback objects.
- **Add or update:** expected `O(1)` time for the map operation.
- **Play a song:** expected `O(1)` time for user and song lookups.
- **Control playback:** expected `O(1)` time.
- **Search:** `O(N + M log M)` time to scan the catalogue and sort the matches.
- **Stored data:** `O(N + U)` space.
- **One search:** `O(N + M)` additional space for the catalogue snapshot, matches, sorting, and output.

The text work is included as a bounded cost above. With unbounded strings, adding a song also costs time and space to normalize its text, while search includes substring comparisons, ID comparisons during sorting, and output construction.

These operation costs exclude time spent waiting for a lock. Several overlapping searches each keep their own temporary lists.

## 12. Java Implementation


```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SpotifyMusicService {
    private final MusicCatalogue catalogue = new MusicCatalogue();
    private final Map<String, PlaybackSession> sessionsByUserId;

    public SpotifyMusicService(String[] userIds) {
        Map<String, PlaybackSession> sessions = new HashMap<>();
        for (String userId : userIds) {
            sessions.put(userId, new PlaybackSession());
        }

        // Users never change, so this map needs only concurrent reads.
        sessionsByUserId = Collections.unmodifiableMap(sessions);
    }

    public void addOrUpdateSong(String songId, String title, String artist,
                                String album, int durationSeconds) {
        catalogue.addOrUpdate(
                new Song(songId, title, artist, album, durationSeconds));
    }

    public List<String> searchSongs(String query) {
        return catalogue.search(query);
    }

    public String playSong(String userId, String songId) {
        return sessionsByUserId.get(userId).play(songId, catalogue);
    }

    public String controlPlayback(String userId, String action,
                                  int positionSeconds) {
        return sessionsByUserId.get(userId).control(action, positionSeconds);
    }

    /** One complete, immutable version of a song's catalogue details. */
    private static final class Song {
        private final String songId;
        private final String title;
        private final String artist;
        private final String album;
        private final int durationSeconds;
        private final String normalizedTitle;
        private final String normalizedArtist;
        private final String normalizedAlbum;

        private Song(String songId, String title, String artist,
                     String album, int durationSeconds) {
            this.songId = songId;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.durationSeconds = durationSeconds;
            this.normalizedTitle = title.toLowerCase(Locale.ROOT);
            this.normalizedArtist = artist.toLowerCase(Locale.ROOT);
            this.normalizedAlbum = album.toLowerCase(Locale.ROOT);
        }

        private boolean matches(String normalizedQuery) {
            return normalizedTitle.contains(normalizedQuery)
                    || normalizedArtist.contains(normalizedQuery)
                    || normalizedAlbum.contains(normalizedQuery);
        }

        private String format() {
            return songId + "," + title + "," + artist + "," + album
                    + "," + durationSeconds;
        }
    }

    /** Owns the shared catalogue and protects every access to its map. */
    private static final class MusicCatalogue {
        private final Map<String, Song> songsById = new HashMap<>();

        private synchronized void addOrUpdate(Song song) {
            songsById.put(song.songId, song);
        }

        private synchronized Song getSong(String songId) {
            return songsById.get(songId);
        }

        private List<String> search(String query) {
            String normalizedQuery = query.toLowerCase(Locale.ROOT);
            List<Song> snapshot;

            synchronized (this) {
                snapshot = new ArrayList<>(songsById.values());
            }

            // Immutable songs let us search after releasing the catalogue lock.
            List<Song> matches = new ArrayList<>();
            for (Song song : snapshot) {
                if (song.matches(normalizedQuery)) {
                    matches.add(song);
                }
            }

            // Sort by the ID itself, before building the output strings.
            matches.sort(Comparator.comparing(song -> song.songId));

            List<String> result = new ArrayList<>(matches.size());
            for (Song song : matches) {
                result.add(song.format());
            }
            return result;
        }
    }

    private enum PlaybackStatus {
        PLAYING, PAUSED, STOPPED
    }

    /** Holds one user's playback state; its methods lock only this user. */
    private static final class PlaybackSession {
        private String songId = "NO_SONG";
        private int durationSeconds = 0;
        private int positionSeconds = 0;
        private PlaybackStatus status = PlaybackStatus.STOPPED;

        private synchronized String play(String requestedSongId,
                                         MusicCatalogue catalogue) {
            // Read the catalogue while holding this user's playback lock.
            Song song = catalogue.getSong(requestedSongId);
            songId = song.songId;

            // Capture duration only when starting or restarting playback.
            durationSeconds = song.durationSeconds;
            positionSeconds = 0;
            status = PlaybackStatus.PLAYING;
            return format();
        }

        private synchronized String control(String action,
                                            int requestedPosition) {
            switch (action) {
                case "PAUSE":
                    status = PlaybackStatus.PAUSED;
                    break;
                case "RESUME":
                    status = PlaybackStatus.PLAYING;
                    break;
                case "SEEK":
                    // The bound is this session's duration, not the catalogue's.
                    // Valid problem inputs never trigger this guard.
                    if (requestedPosition < 0
                            || requestedPosition >= durationSeconds) {
                        throw new IllegalArgumentException(
                                "Seek position is outside the session duration");
                    }
                    positionSeconds = requestedPosition;
                    break;
                case "STOP":
                    songId = "NO_SONG";
                    durationSeconds = 0;
                    positionSeconds = 0;
                    status = PlaybackStatus.STOPPED;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown action: " + action);
            }
            return format();
        }

        // Called only while this session's lock is held.
        private String format() {
            return status + "," + songId + "," + positionSeconds;
        }
    }
}
```
