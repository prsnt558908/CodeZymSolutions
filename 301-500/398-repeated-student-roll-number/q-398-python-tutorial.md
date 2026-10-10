# Combine Road Repair Zones in Python

#### Problem Statement
[https://codezym.com/question/398-repeated-student-roll-number](https://codezym.com/question/398-repeated-student-roll-number)


Sort the sections by their starting markers. Then scan them once while keeping one current repair zone. Extend this zone when the next section overlaps it or shares its endpoint. Otherwise, save the current zone and begin a new one.


## Understand the merge rule

Each string describes a section as `"startMarker,endMarker"`. Return the combined zones in ascending order of their starting markers.


The road is continuous. Sections `"4,9"` and `"9,14"` touch at marker `9`, so they merge into `"4,14"`. Sections `"4,9"` and `"10,14"` have a gap and stay separate.


## Why sort first?

Checking every pair of sections needs O(n²) comparisons. We also need to track chains of overlapping sections.


Sorting by the starting marker makes the process simpler. If the next section starts after the current zone ends, every later section starts after it too. We can safely save the current zone.


Parse the markers before sorting. Sorting the original strings would put `"30,38"` before `"7,12"`, which is the wrong numeric order.


## Data structures

Use a list of `(start, end)` tuples to store the parsed sections. Each tuple holds two integers. This lets us sort numeric markers without changing the input list.


Two variables, `current_start` and `current_end`, describe the zone being built. A list of strings stores the finished zones in the required format. The `CombineRoadRepairZones` class provides the required method and does not need stored fields.


## Merge in one pass

1. Parse the strings and sort the sections by their starts.
2. Use the first section as the current zone. The input is guaranteed to be nonempty.
3. For each remaining section, check whether `start <= current_end`.
4. If true, set `current_end = max(current_end, end)`.
5. Otherwise, save the current zone and start a new one.
6. Save the last zone after the loop.


The maximum matters for nested sections. If the current zone is `"2,20"` and the next section is `"5,8"`, its end must stay `20`.


Use `<=`, not `<`, because shared endpoints must merge. Do not add `1` to the current end, because that would merge sections with a gap.


## Walk through an example

Input: `["40,45", "6,11", "8,16", "15,22", "21,28", "60,63"]`.


After sorting: `["6,11", "8,16", "15,22", "21,28", "40,45", "60,63"]`.


- Start with `"6,11"`.
- `"8,16"` overlaps, so extend the zone to `"6,16"`.
- `"15,22"` extends it to `"6,22"`.
- `"21,28"` extends it to `"6,28"`.
- `"40,45"` starts after `28`. Save `"6,28"` and begin `"40,45"`.
- `"60,63"` starts after `45`. Save `"40,45"` and begin `"60,63"`.
- Save the final zone after the loop.


Result: `["6,28", "40,45", "60,63"]`.


## Why this works

The current zone always covers exactly the connected sections merged into it. An overlapping or touching section extends that zone without creating a gap.


When a gap appears, sorting guarantees that no later section can fill it. The current zone is complete. Repeating this step combines every connected group and keeps the output sorted.


## Complexity

For `n` sections, sorting takes O(n log n) time and the merge pass takes O(n). Total time is **O(n log n)**.


The parsed sections take **O(n) extra space**. The output also contains at most `n` zones.


## Python solution

```python
from typing import List


class CombineRoadRepairZones:
    def __init__(self):
        pass

    def combineRepairZones(self, repairZones: List[str]) -> List[str]:
        sections = []

        # Parse once so the sort compares numeric markers.
        for zone in repairZones:
            start_text, end_text = zone.split(",")
            sections.append((int(start_text), int(end_text)))

        sections.sort(key=lambda section: section[0])

        result = []
        current_start, current_end = sections[0]

        for i in range(1, len(sections)):
            start, end = sections[i]

            if start <= current_end:
                # Shared endpoints merge. Nested sections must not shrink the zone.
                current_end = max(current_end, end)
            else:
                # A gap means the current zone is complete.
                result.append(f"{current_start},{current_end}")
                current_start = start
                current_end = end

        # The last zone has no following section to trigger a save.
        result.append(f"{current_start},{current_end}")
        return result
```
