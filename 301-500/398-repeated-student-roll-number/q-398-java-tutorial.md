# Combine Road Repair Zones in Java

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

Use a `List<int[]>` to store the parsed sections. Each two-element array holds a start and an end. This lets us sort numeric markers without changing the input list.


Two variables, `currentStart` and `currentEnd`, describe the zone being built. A `List<String>` stores the finished zones in the required format. The `CombineRoadRepairZones` class provides the required method and does not need stored fields.


## Merge in one pass

1. Parse the strings and sort the sections by their starts.
2. Use the first section as the current zone. The input is guaranteed to be nonempty.
3. For each remaining section, check whether `start <= currentEnd`.
4. If true, set `currentEnd = Math.max(currentEnd, end)`.
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


## Java solution

```java
import java.util.ArrayList;
import java.util.List;

public class CombineRoadRepairZones {

    public CombineRoadRepairZones() {
    }

    public List<String> combineRepairZones(List<String> repairZones) {
        List<int[]> sections = new ArrayList<>();

        // Parse once so the sort compares numeric markers.
        for (String zone : repairZones) {
            String[] markers = zone.split(",");
            int start = Integer.parseInt(markers[0]);
            int end = Integer.parseInt(markers[1]);
            sections.add(new int[] {start, end});
        }

        sections.sort((first, second) -> Integer.compare(first[0], second[0]));

        List<String> result = new ArrayList<>();
        int currentStart = sections.get(0)[0];
        int currentEnd = sections.get(0)[1];

        for (int i = 1; i < sections.size(); i++) {
            int start = sections.get(i)[0];
            int end = sections.get(i)[1];

            if (start <= currentEnd) {
                // Shared endpoints merge. Nested sections must not shrink the zone.
                currentEnd = Math.max(currentEnd, end);
            } else {
                // A gap means the current zone is complete.
                result.add(currentStart + "," + currentEnd);
                currentStart = start;
                currentEnd = end;
            }
        }

        // The last zone has no following section to trigger a save.
        result.add(currentStart + "," + currentEnd);
        return result;
    }
}
```
