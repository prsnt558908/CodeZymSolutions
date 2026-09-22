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

- `dj_index`: the next DJ portion to use
- `recommendation_index`: the next recommendation portion to use
- `dj_portions_used`: how many DJ portions have been selected
- `recommendation_portions_used`: how many recommendation portions have been selected

When both lists still have portions, choose DJ if:

```text
dj_portions_used * recommendation_weight
    <= recommendation_portions_used * dj_weight
```

Otherwise, choose a recommendation portion.

The `<=` is important because DJ must be chosen when both sides are equal.

Python integers grow automatically when needed, so these multiplications do not need a special larger integer type.

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

### `_SongPortion` Dataclass

This small dataclass keeps the song ID, start time, end time, and tags of one portion together.

`frozen=True` prevents the stored values from being changed by mistake. The portion's tags are stored in a `frozenset` for the same reason.

### Two Python Lists

One list stores DJ portions and the other stores recommendation portions. Python lists keep items in the order in which they were added.

### Sets for Tags

The preferred tags are stored in a `set`, and each portion's tags are stored in a `frozenset`. We use `issubset` to check whether every preferred tag is present in a portion.

If `preferredTags` is empty, `issubset` returns `True`, so every portion is allowed.

Tag matching is case-sensitive. For example, `"rock"` and `"Rock"` are different tags.

## Public Method Names

The public methods use names such as `addDjSong` and `getPlaylist` because those exact names are required by the problem and its tests. Internal variables use normal Python snake_case names.

## Step-by-Step Algorithm

1. Add DJ portions to `self.dj_portions`.
2. Add recommendation portions to `self.recommendation_portions`.
3. In `getPlaylist`, filter both lists using `self.preferred_tags`.
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

Adding a portion takes `O(t)` time because its tags are copied into a `frozenset`.

Checking all stored portions takes `O(n * (p + 1))` time on average. Building the final answer takes `O(k)` time.

The total average time for `getPlaylist` is:

```text
O(n * (p + 1) + k)
```

The eligible lists use `O(n)` extra space. The returned answer uses `O(k)` space.

## Python Solution

```python
from dataclasses import dataclass
from typing import FrozenSet, List


@dataclass(frozen=True)
class _SongPortion:
    """Stores one playable part of a song."""

    song_id: str
    start_second: int
    end_second: int
    tags: FrozenSet[str]

    def to_instruction(self, source: str) -> str:
        """Creates the string format required in the answer."""

        return f"{source},{self.song_id},{self.start_second},{self.end_second}"


class MusicPlaylist:
    def __init__(
        self,
        mixType: str,
        djWeight: int,
        recommendationWeight: int,
        preferredTags: List[str],
    ):
        # The weights already contain the information needed for mixing.
        # For EQUAL, both weights are 1. For CUSTOM, the given weights are used.
        self.dj_weight = djWeight
        self.recommendation_weight = recommendationWeight
        self.preferred_tags = set(preferredTags)
        self.dj_portions: List[_SongPortion] = []
        self.recommendation_portions: List[_SongPortion] = []

    def addDjSong(
        self,
        songId: str,
        startSecond: int,
        endSecond: int,
        tags: List[str],
    ) -> None:
        self.dj_portions.append(
            _SongPortion(songId, startSecond, endSecond, frozenset(tags))
        )

    def addRecommendedSong(
        self,
        songId: str,
        startSecond: int,
        endSecond: int,
        tags: List[str],
    ) -> None:
        self.recommendation_portions.append(
            _SongPortion(songId, startSecond, endSecond, frozenset(tags))
        )

    def getPlaylist(self, maximumSongPortions: int) -> List[str]:
        eligible_dj_portions = self._get_eligible_portions(self.dj_portions)
        eligible_recommendation_portions = self._get_eligible_portions(
            self.recommendation_portions
        )

        playlist: List[str] = []
        dj_index = 0
        recommendation_index = 0
        dj_portions_used = 0
        recommendation_portions_used = 0

        while len(playlist) < maximumSongPortions and (
            dj_index < len(eligible_dj_portions)
            or recommendation_index < len(eligible_recommendation_portions)
        ):
            dj_available = dj_index < len(eligible_dj_portions)
            recommendation_available = (
                recommendation_index < len(eligible_recommendation_portions)
            )

            if dj_available and recommendation_available:
                dj_side = dj_portions_used * self.recommendation_weight
                recommendation_side = (
                    recommendation_portions_used * self.dj_weight
                )

                if dj_side <= recommendation_side:
                    playlist.append(
                        eligible_dj_portions[dj_index].to_instruction("DJ")
                    )
                    dj_index += 1
                    dj_portions_used += 1
                else:
                    playlist.append(
                        eligible_recommendation_portions[
                            recommendation_index
                        ].to_instruction("RECOMMENDATION")
                    )
                    recommendation_index += 1
                    recommendation_portions_used += 1
            elif dj_available:
                playlist.append(
                    eligible_dj_portions[dj_index].to_instruction("DJ")
                )
                dj_index += 1
                dj_portions_used += 1
            else:
                playlist.append(
                    eligible_recommendation_portions[
                        recommendation_index
                    ].to_instruction("RECOMMENDATION")
                )
                recommendation_index += 1
                recommendation_portions_used += 1

        return playlist

    def _get_eligible_portions(
        self, portions: List[_SongPortion]
    ) -> List[_SongPortion]:
        """Keeps only portions that have every preferred tag."""

        return [
            portion
            for portion in portions
            if self.preferred_tags.issubset(portion.tags)
        ]
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
