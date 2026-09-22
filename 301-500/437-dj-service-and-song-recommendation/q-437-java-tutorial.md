# Mix Songs from DJ Service and Recommendation Service

Problem Statement: [https://codezym.com/question/437-dj-service-and-song-recommendation](https://codezym.com/question/437-dj-service-and-song-recommendation)

### Hint
Store the song portions from the DJ service and the recommendation service in two separate lists. When a playlist is requested, first keep only the portions that match the user's preferred tags. Then choose one portion at a time from the two lists by using the given weight rule. Two indexes and two counters are enough to keep the correct order and mix.

## What Do We Need to Do?

Each item is a portion of a song. For example, `[90, 220)` means that the song plays from `1:30` until `3:40`.

The playlist must follow these rules:

1. A portion can be used only if it has every preferred tag.
2. DJ portions must stay in the order in which they were added.
3. Recommendation portions must also stay in their original order.
4. The two sources must be mixed using their given weights.
5. If the weight comparison is equal, choose DJ.
6. Calling `getPlaylist` must not remove any stored portion.

The weights count portions, not the number of seconds. A 20-second portion and a 200-second portion both count as one portion.

## First Idea: Take Fixed Groups

For a DJ-to-recommendation weight of `2:1`, a simple idea is to take:

```text
DJ, DJ, RECOMMENDATION, DJ, DJ, RECOMMENDATION, ...
```

This looks reasonable, but it does not follow the rule in the problem.

At the start, both used counts are `0`, so DJ is selected because the comparison is equal. After that, one DJ portion and no recommendation portion have been used. The required rule now selects a recommendation portion.

Therefore, a `2:1` playlist starts like this:

```text
DJ, RECOMMENDATION, DJ, DJ, RECOMMENDATION, DJ, ...
```

So we cannot simply take fixed groups. We must make the choice again after every selected portion.

## Better Solution: Choose One Portion at a Time

First, make two new lists:

- Eligible DJ portions
- Eligible recommendation portions

A portion is eligible when its tags contain every preferred tag.

Next, keep these four numbers:

- `djIndex`: the next DJ portion to use
- `recommendationIndex`: the next recommendation portion to use
- `djPortionsUsed`: how many DJ portions have been selected
- `recommendationPortionsUsed`: how many recommendation portions have been selected

When both lists still have portions, choose DJ if:

```text
djPortionsUsed * recommendationWeight
    <= recommendationPortionsUsed * djWeight
```

Otherwise, choose a recommendation portion.

The `<=` is important because DJ must be chosen when both sides are equal.

The code uses `long` for the two multiplied values. This keeps the comparison safe even if the limits are increased later.

If one list becomes empty, keep taking portions from the other list. Stop when the answer reaches `maximumSongPortions` or both lists become empty.

## Why Keep the Used Counts?

A slower version could count the DJ and recommendation entries in the answer again before every choice. That would repeat the same work many times.

Instead, we keep the two counts in variables and increase the correct count whenever we add a portion. This makes every choice take constant time.

## Example with Weights `2:1`

The values start as:

```text
DJ used = 0
Recommendation used = 0
```

The choices are:

1. `0 * 1 <= 0 * 2` is true, so choose DJ.
2. `1 * 1 <= 0 * 2` is false, so choose recommendation.
3. `1 * 1 <= 1 * 2` is true, so choose DJ.
4. `2 * 1 <= 1 * 2` is true, so choose DJ again.
5. `3 * 1 <= 1 * 2` is false, so choose recommendation.

This gives:

```text
DJ, RECOMMENDATION, DJ, DJ, RECOMMENDATION, ...
```

## Data Structures

### `SongPortion`

This small class keeps the song ID, start time, end time, and tags of one portion together.

### Two `ArrayList` Objects

One list stores DJ portions and the other stores recommendation portions. `ArrayList` keeps items in the order in which they were added.

### `HashSet` for Tags

The tags of every portion and the preferred tags are stored in `HashSet` objects. We can then use `containsAll` to check whether a portion has every preferred tag.

If `preferredTags` is empty, `containsAll` returns `true`, so every portion is allowed.

Tag matching is case-sensitive. For example, `"rock"` and `"Rock"` are different tags.

## Step-by-Step Algorithm

1. Add DJ portions to `djPortions`.
2. Add recommendation portions to `recommendationPortions`.
3. In `getPlaylist`, filter both lists using `preferredTags`.
4. Start both indexes and both used counts at `0`.
5. While more output is needed:
   - If both sources are available, use the weight comparison.
   - If only one source is available, use that source.
   - Add the chosen portion in the required string format.
   - Move its index forward and increase its used count.
6. Return the playlist.

## Why the Solution Works

Every returned portion has all preferred tags because we filter the two lists before mixing them.

The indexes only move forward. Therefore, DJ portions keep their DJ order, and recommendation portions keep their recommendation order.

Whenever both sources are available, the code uses the exact comparison given in the problem. The `<=` also gives DJ the required tie-break.

The loop stops after returning the requested maximum number of portions or after using every eligible portion. The stored lists are never changed, so later calls can build the playlist again.

## Complexity Analysis

Let:

- `n` be the total number of stored portions.
- `p` be the number of preferred tags.
- `k` be the number of returned portions.
- `t` be the number of tags on a newly added portion.

Adding a portion takes `O(t)` time because its tags are copied into a `HashSet`.

Checking all stored portions takes `O(n * (p + 1))` time on average. Building the final answer takes `O(k)` time.

The total average time for `getPlaylist` is:

```text
O(n * (p + 1) + k)
```

The eligible lists use `O(n)` extra space. The returned answer uses `O(k)` space.

## Java Solution

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MusicPlaylist {

    // Stores one playable part of a song.
    private static class SongPortion {
        private final String songId;
        private final int startSecond;
        private final int endSecond;
        private final Set<String> tags;

        private SongPortion(
                String songId,
                int startSecond,
                int endSecond,
                List<String> tags) {
            this.songId = songId;
            this.startSecond = startSecond;
            this.endSecond = endSecond;
            this.tags = new HashSet<>(tags);
        }

        // Creates the string format required in the answer.
        private String toInstruction(String source) {
            return source + "," + songId + "," + startSecond + "," + endSecond;
        }
    }

    private final int djWeight;
    private final int recommendationWeight;
    private final Set<String> preferredTags;
    private final List<SongPortion> djPortions;
    private final List<SongPortion> recommendationPortions;

    public MusicPlaylist(
            String mixType,
            int djWeight,
            int recommendationWeight,
            List<String> preferredTags) {
        // The weights already contain the information needed for mixing.
        // For EQUAL, both weights are 1. For CUSTOM, the given weights are used.
        this.djWeight = djWeight;
        this.recommendationWeight = recommendationWeight;
        this.preferredTags = new HashSet<>(preferredTags);
        this.djPortions = new ArrayList<>();
        this.recommendationPortions = new ArrayList<>();
    }

    public void addDjSong(
            String songId,
            int startSecond,
            int endSecond,
            List<String> tags) {
        djPortions.add(new SongPortion(songId, startSecond, endSecond, tags));
    }

    public void addRecommendedSong(
            String songId,
            int startSecond,
            int endSecond,
            List<String> tags) {
        recommendationPortions.add(
                new SongPortion(songId, startSecond, endSecond, tags));
    }

    public List<String> getPlaylist(int maximumSongPortions) {
        List<SongPortion> eligibleDjPortions = getEligiblePortions(djPortions);
        List<SongPortion> eligibleRecommendationPortions =
                getEligiblePortions(recommendationPortions);

        int resultSize = Math.min(
                maximumSongPortions,
                eligibleDjPortions.size() + eligibleRecommendationPortions.size());
        List<String> playlist = new ArrayList<>(resultSize);

        int djIndex = 0;
        int recommendationIndex = 0;
        int djPortionsUsed = 0;
        int recommendationPortionsUsed = 0;

        while (playlist.size() < maximumSongPortions
                && (djIndex < eligibleDjPortions.size()
                || recommendationIndex < eligibleRecommendationPortions.size())) {

            boolean djAvailable = djIndex < eligibleDjPortions.size();
            boolean recommendationAvailable =
                    recommendationIndex < eligibleRecommendationPortions.size();

            if (djAvailable && recommendationAvailable) {
                long djSide = (long) djPortionsUsed * recommendationWeight;
                long recommendationSide =
                        (long) recommendationPortionsUsed * djWeight;

                if (djSide <= recommendationSide) {
                    playlist.add(
                            eligibleDjPortions.get(djIndex++).toInstruction("DJ"));
                    djPortionsUsed++;
                } else {
                    playlist.add(
                            eligibleRecommendationPortions
                                    .get(recommendationIndex++)
                                    .toInstruction("RECOMMENDATION"));
                    recommendationPortionsUsed++;
                }
            } else if (djAvailable) {
                playlist.add(
                        eligibleDjPortions.get(djIndex++).toInstruction("DJ"));
                djPortionsUsed++;
            } else {
                playlist.add(
                        eligibleRecommendationPortions
                                .get(recommendationIndex++)
                                .toInstruction("RECOMMENDATION"));
                recommendationPortionsUsed++;
            }
        }

        return playlist;
    }

    // Keeps only portions that have every preferred tag.
    private List<SongPortion> getEligiblePortions(List<SongPortion> portions) {
        List<SongPortion> eligiblePortions = new ArrayList<>();

        for (SongPortion portion : portions) {
            if (portion.tags.containsAll(preferredTags)) {
                eligiblePortions.add(portion);
            }
        }

        return eligiblePortions;
    }
}
```

## Important Cases Handled

- No portion matches the preferred tags.
- The preferred-tag list is empty.
- Only DJ portions are available.
- Only recommendation portions are available.
- One source becomes empty before the other source.
- The requested maximum is smaller than the number of eligible portions.
- Fewer eligible portions exist than the requested maximum.
- Different portions of the same song are stored separately.
- The weight comparison is equal, so DJ is selected.
- `getPlaylist` is called more than once.
