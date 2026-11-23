# Serialize Nearly Sorted Stream of Numbers in Java

#### Problem Statement
[https://codezym.com/question/251-serialize-nearly-sorted-stream](https://codezym.com/question/251-serialize-nearly-sorted-stream)


The stream is **almost sorted**: every number is at most `bufferSize` steps away from its correct place. So the next number of the sorted output is always close by, never far ahead. We keep up to `bufferSize + 1` waiting numbers in a small **min heap**, and the smallest number in it is always the next one to output.

We pull that number out, read one more number from the stream, and repeat. This sorts everything in a single pass, without waiting for the whole stream to arrive.


## Understanding the Problem

Think of messages arriving over a network. Most arrive in order, but a few come slightly early or slightly late. `bufferSize` tells us the most any number can be out of place.

**Example:** `stream = [3, 1, 2, 5, 4, 6]`, `bufferSize = 2`

The sorted output is `[1, 2, 3, 4, 5, 6]`.

Number `3` sits at index `0`, but its correct index is `2`. It is 2 steps away, which is allowed. Every other number is even closer to its correct spot.

This is a streaming version of the classic **sort a k-sorted array** problem.


## Approach 1: Brute Force (Sort Everything)

The first idea is simple: copy all the numbers and sort them.

```java
List<Integer> result = new ArrayList<>(stream);
Collections.sort(result);
return result;
```

`Collections.sort` is stable, so equal numbers also keep their input order.

- **Time:** O(n log n)
- **Space:** O(n)

### Problems with this approach

1. **It ignores the hint.** The stream is almost sorted, but we sort it as if it were completely random.
2. **It must wait for the whole stream.** We cannot output even the first number until every number has arrived. A real stream can be huge or never ending.

We can do better by using the fact that numbers are only slightly out of place.


## Key Observation

Let `k = bufferSize`.

Where can the **smallest** number be? Its correct index is `0`, and it is at most `k` steps away. So it must be somewhere in the first `k + 1` numbers of the stream.

So after reading just `k + 1` numbers, the smallest among them is surely the first number of the answer.

The same logic works for every position. The number that belongs at sorted index `i` is somewhere between input index `0` and `i + k`. So once we have read `i + k + 1` numbers, it has surely arrived. All numbers smaller than it are already in the answer, so it is the smallest number still waiting.

This means we never need the whole stream. A buffer of `k + 1` numbers is enough.


## Approach 2: Min Heap Buffer (Optimal)

Our buffer must support two actions again and again:

- add a new number
- remove the smallest number

Finding the smallest by scanning a plain list costs O(k) for every number we output. That is O(n * k) in total, which is too slow when `k` is large.

A **min heap** is made for exactly this job. In Java it is the `PriorityQueue` class. Adding a number and removing the smallest both take only O(log k) time, because our heap never holds more than `k + 1` numbers.

### Steps

1. Read the stream one number at a time and add each number to the heap.
2. If the heap now holds more than `k` numbers, remove the smallest one and append it to the answer.
3. When the stream ends, keep removing the smallest number until the heap is empty.

### Dry Run

`stream = [3, 1, 2, 5, 4, 6]`, `bufferSize = 2`. We remove a number whenever the heap holds 3 numbers.

| Read | Heap after adding | Removed | Answer so far |
|:----:|:-----------------:|:-------:|:--------------|
| 3 | 3 | - | [] |
| 1 | 1, 3 | - | [] |
| 2 | 1, 2, 3 | 1 | [1] |
| 5 | 2, 3, 5 | 2 | [1, 2] |
| 4 | 3, 4, 5 | 3 | [1, 2, 3] |
| 6 | 4, 5, 6 | 4 | [1, 2, 3, 4] |
| end | 5, 6 | 5, 6 | [1, 2, 3, 4, 5, 6] |

Heap contents are shown in sorted order just to make them easy to read.

### What About Equal Numbers?

The problem says equal numbers must keep their input order. Our output holds plain integers, and two equal integers look exactly the same. So their order can never change the output, and no extra work is needed.

If the stream carried objects instead (like messages with an id), we would store `(value, index)` pairs in the heap and break ties using the index.

### Code

```java
import java.util.*;

public class SerializeNearlySortedStream {

    public SerializeNearlySortedStream() {

    }

    /**
     * Returns all numbers of the stream in non-decreasing order.
     * Every number is at most bufferSize positions away from its sorted position,
     * so a min heap holding bufferSize + 1 numbers is enough to find the next number.
     */
    public List<Integer> serializeNumbers(List<Integer> stream, int bufferSize) {
        List<Integer> result = new ArrayList<>();

        // min heap, always gives the smallest number waiting in the buffer
        PriorityQueue<Integer> buffer = new PriorityQueue<>();

        for (int num : stream) {
            buffer.add(num);

            // once the buffer holds bufferSize + 1 numbers,
            // its smallest number is surely the next one in sorted order
            if (buffer.size() > bufferSize) {
                result.add(buffer.poll());
            }
        }

        // stream has ended, so empty the buffer in sorted order
        while (!buffer.isEmpty()) {
            result.add(buffer.poll());
        }

        return result;
    }
}
```

### Complexity

- **Time:** O(n log k). Each number goes into the heap once and comes out once, and the heap holds at most `k + 1` numbers.
- **Space:** O(k) for the heap, apart from the output list.

When `k` is small, this is almost linear time. And even when `k` is close to `n`, it is no slower than sorting everything.