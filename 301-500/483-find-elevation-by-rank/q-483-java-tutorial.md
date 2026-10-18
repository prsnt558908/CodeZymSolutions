# Find Elevation by Rank in Java

#### Problem Statement

[https://codezym.com/question/483-find-elevation-by-rank](https://codezym.com/question/483-find-elevation-by-rank)

Each row is already sorted. Keep its next available reading in a min-heap, which lets us quickly find the smallest reading. Remove the first `position - 1` readings and return the smallest one left. Read numbers directly from the row strings to avoid storing the entire number grid.

![Example of ranking survey elevations from lowest to highest](https://d3jug84e56mcss.cloudfront.net/question_images/0/q-483-img-1-sm-50-80.jpg)

## A simple starting idea

We could collect all readings in a list, sort them, and return the value at index `position - 1`.

For a field with `n` rows, this takes `O(n² log(n²))` time and `O(n²)` additional space. That space usage breaks the problem's requirement.

## Better approach: merge the sorted rows

A min-heap keeps the smallest stored value at the top. Java provides it through `PriorityQueue`.

Only the first unused reading from each row can be the next answer. Every later reading in that row is at least as large. Therefore, we need at most one heap entry per row.

Each entry is an integer array containing `[value, rowIndex, nextStart]`. The first field orders the heap. The other two tell us which row to read and where its next number starts.

The `rows` array holds references to the existing strings. It gives constant-time row lookup and uses only `O(n)` additional space.

### Steps

1. Read the first number from every row and add an entry to the heap.
2. Remove the smallest entry `position - 1` times.
3. After each removal, add the next number from the same row, if one remains.
4. Return the value at the top of the heap.

The `readEntry` helper finds the next comma and parses only that number. We never split every row or build a second grid.

Equal readings stay as separate entries, so they occupy separate ranks. Negative values work the same way.

### Walk through an example

For `elevations = ["-9,-1,6", "-4,3,10", "2,8,15"]` and `position = 5`, the heap starts with `-9, -4, 2`.

We remove `-9` and add `-1`. Then we remove `-4` and add `3`. Next, we remove `-1` and add `6`. Finally, we remove `2` and add `8`.

Four readings are gone. The smallest remaining reading is `3`, so it has rank `5`.

## Why this works

The heap always contains the first unused reading from every row that still has values. Since each row is sorted, its later readings cannot be smaller than that reading.

This means the heap's smallest reading is also the smallest unused reading in the entire field. Adding the next reading from the same row preserves this rule after each removal.

After removing exactly `position - 1` readings, the smallest remaining reading has the requested rank. The same argument includes duplicate values.

## Complexity

Let `n` be the number of rows and `k = position`.

There are `n` initial insertions and at most `k - 1` removals and replacements. Each heap operation takes `O(log(n + 1))` time. Each number has at most 11 characters under the given limits, so parsing one number takes constant time.

The time bound is `O((n + k) log(n + 1))`. When `k = n²`, this becomes `O(n² log(n + 1))`.

Additional space is `O(n)` for the heap and row references. The input strings are not copied, and no state is kept between calls.

## Java code

```java
import java.util.List;
import java.util.PriorityQueue;

public class ElevationRank {

    public ElevationRank() {
    }

    public int findElevation(List<String> elevations, int position) {
        // Copy references only, so row lookup is fast for any input List.
        String[] rows = elevations.toArray(new String[0]);

        // Each entry contains: value, row index, next number's start index.
        PriorityQueue<int[]> heap = new PriorityQueue<>(
            (first, second) -> Integer.compare(first[0], second[0])
        );

        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            heap.offer(readEntry(rows[rowIndex], rowIndex, 0));
        }

        // Skip the readings that come before the requested rank.
        for (int rank = 1; rank < position; rank++) {
            int[] entry = heap.poll();
            int rowIndex = entry[1];
            int nextStart = entry[2];

            if (nextStart < rows[rowIndex].length()) {
                heap.offer(readEntry(rows[rowIndex], rowIndex, nextStart));
            }
        }

        return heap.peek()[0];
    }

    public int[] readEntry(String row, int rowIndex, int start) {
        int end = row.indexOf(',', start);

        if (end == -1) {
            end = row.length();
        }

        int value = Integer.parseInt(row.substring(start, end));

        // After the last value, nextStart is beyond the end of the row.
        return new int[] {value, rowIndex, end + 1};
    }
}
```
