# First Non Repeated Character in a Stream in Java

#### Problem Statement
[https://codezym.com/question/122-first-non-repeated-character-stream](https://codezym.com/question/122-first-non-repeated-character-stream)

---

## Core Idea

The string comes to us in small pieces, and the full string is too big to keep in memory. So we cannot save the whole string and look at it later.


The good news is that we do not need the whole string. For every character, we only need to know two things:

1. How many times it has appeared so far.
2. In which order the characters first appeared.


We keep a **map** for the counts and a **queue** for the order. When a character shows up for the first time, it joins the back of the queue.


When someone asks for the answer, we remove characters from the front of the queue that have become repeated. The character left at the front is the answer.


Memory now depends on how many *different* characters we have seen, not on how long the string is.

---

## Understanding the Problem

Say the chunks are `"sw"` and then `"iss"`. Together they form `"swiss"`.


`s` appears 3 times. `w` and `i` appear once each. `w` comes before `i`, so the answer is `"w"`.


Two simple facts will help us later:

- A character that is unique right now can become repeated when a new chunk arrives.
- A character that is repeated can **never** become unique again. Its count only goes up.


The second fact is what makes the fast solution work.

---

## Solution 1: Brute Force (Store Everything)

### Idea

The simplest thing is to keep adding every chunk to one big string.


When we are asked for the answer:

1. Count how many times each character appears in the full string.
2. Walk the string from the start and return the first character whose count is 1.

### Code

```java
import java.util.*;

public class FirstNonRepeatedCharacter {

    // stores the complete streamed string
    StringBuilder fullString;

    public FirstNonRepeatedCharacter() {
        fullString = new StringBuilder();
    }

    public void appendChunk(String chunk) {
        fullString.append(chunk);
    }

    public String firstNonRepeatedCharacter() {
        // pass 1: count how many times each character appears
        Map<Character, Integer> count = new HashMap<>();
        for (int i = 0; i < fullString.length(); i++) {
            char c = fullString.charAt(i);
            count.put(c, count.getOrDefault(c, 0) + 1);
        }

        // pass 2: the first character with count 1 is the answer
        for (int i = 0; i < fullString.length(); i++) {
            char c = fullString.charAt(i);
            if (count.get(c) == 1) {
                return String.valueOf(c);
            }
        }
        return "";
    }
}
```

### Problems with this approach

- **Too much memory:** We store the whole string. The problem says the full string will not fit in RAM. So this breaks the main rule.
- **Slow queries:** Every query reads the full string two times. If the string has `N` characters, each query costs `O(N)`.
- **Same work again and again:** Most of the string does not change between two queries, but we count it from scratch every time.

| What | Cost |
|---|---|
| `appendChunk` time | `O(L)`, L = chunk length |
| `firstNonRepeatedCharacter` time | `O(N)`, N = full string length |
| Memory | `O(N)` |

---

## Improving the Solution

### Step 1: Count while reading

Instead of storing the string, we can update the counts as each chunk arrives. After that, the chunk can be thrown away.


But counts alone are not enough. For `"swiss"`, the map tells us `w` and `i` both appear once. It does not tell us which one came first.

### Step 2: Remember the order of first appearance

We add a queue. A character is added to the back of the queue **only the first time** we see it.


So the queue holds characters in the same order they first appeared in the string. The answer is the first character in this queue whose count is still 1.

### Step 3: Clean the front only when needed

When a character becomes repeated, we could try to remove it from the queue right away. But it may be sitting in the middle of the queue, and removing from the middle of a queue is slow.


So we leave it there. When someone asks for the answer, we look at the front of the queue:

- If its count is more than 1, it can never be the answer again. Remove it and look at the next one.
- If its count is 1, that is our answer.


This is safe because a repeated character never becomes unique again. Once it is removed, it never needs to come back.

---

## Solution 2: Count Map + Queue (Optimal)

### Data structures and why we need them

| Data structure | What it stores | Why we need it |
|---|---|---|
| `Map<Character, Integer> count` | How many times each character has appeared | Tells us in `O(1)` if a character is unique or repeated |
| `Queue<Character> candidates` | Characters in the order they first appeared | Keeps the order, so the earliest unique character is always at the front after cleanup |


Each character enters the queue only once, the first time it is seen. So the queue never grows bigger than the number of different characters.

### How `appendChunk` works

For every character in the chunk:

1. Increase its count in the map.
2. If the new count is 1, it is a brand new character. Add it to the back of the queue.


The chunk itself is not stored anywhere.

### How `firstNonRepeatedCharacter` works

1. While the character at the front of the queue has count more than 1, remove it.
2. If the queue is now empty, return `""`.
3. Otherwise, return the character at the front.

### Dry Run

Chunks: `"sw"`, then `"iss"`.

| Character read | count map | queue (front first) |
|---|---|---|
| `s` | s=1 | s |
| `w` | s=1, w=1 | s, w |
| `i` | s=1, w=1, i=1 | s, w, i |
| `s` | s=2, w=1, i=1 | s, w, i |
| `s` | s=3, w=1, i=1 | s, w, i |


Now `firstNonRepeatedCharacter()` is called:

- Front is `s` with count 3. Remove it. Queue is now `w, i`.
- Front is `w` with count 1. Stop.
- Answer is `"w"`.

### Code

```java
import java.util.*;

public class FirstNonRepeatedCharacter {

    // how many times each character has appeared in the stream so far
    Map<Character, Integer> count;

    // characters in the order they first appeared.
    // a character here may have become repeated later,
    // such characters are removed only when they reach the front.
    Queue<Character> candidates;

    public FirstNonRepeatedCharacter() {
        count = new HashMap<>();
        candidates = new ArrayDeque<>();
    }

    public void appendChunk(String chunk) {
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            int newCount = count.getOrDefault(c, 0) + 1;
            count.put(c, newCount);

            // first time we see this character, so it can be an answer
            if (newCount == 1) {
                candidates.add(c);
            }
        }
    }

    public String firstNonRepeatedCharacter() {
        // throw away characters at the front which are now repeated
        while (!candidates.isEmpty() && count.get(candidates.peek()) > 1) {
            candidates.poll();
        }

        if (candidates.isEmpty()) {
            return "";
        }
        return String.valueOf(candidates.peek());
    }
}
```

### Why is the query fast?

The `while` loop may look slow at first. But each character is added to the queue only once, so it can be removed only once.


Across **all** queries together, the loop removes at most `K` characters, where `K` is the number of different characters. So on average, each query takes `O(1)` time.

### Complexity

| What | Cost |
|---|---|
| `appendChunk` time | `O(L)`, L = chunk length |
| `firstNonRepeatedCharacter` time | `O(1)` on average |
| Memory | `O(K)`, K = number of different characters |


In Java, a `char` can have at most 65,536 different values. So memory stays small no matter how long the stream becomes.

---

## Edge Cases

- **Spaces, digits, punctuation and symbols** are treated like any other character. In `"a a#"`, the space is the answer.
- **Every character repeated**, like `"!!@@"`: the queue becomes empty after cleanup, so we return `""`.
- **Query before any chunk**: the queue is empty, so we return `""`.
- **A unique character becomes repeated later**: its count goes above 1, and it gets removed the next time it reaches the front.