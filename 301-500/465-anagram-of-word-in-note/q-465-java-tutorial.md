# Check If Anagram of a Word Can Be Found in Note in Java

#### Problem Statement
[https://codezym.com/question/465-anagram-of-word-in-note](https://codezym.com/question/465-anagram-of-word-in-note)


The core idea is simple: **positions do not matter, only counts do**.


The letters of a word can come from anywhere in the note, in any order. So we only need to answer one question: does the note have enough copies of every letter the word needs?


We count each letter of the note once, using an array of 26 numbers. Then for each word, we count its letters and compare. If the note has enough of every letter for even one word, the answer is `true`.


We will start with a simple brute force solution, see why it is slow, and then improve it.


## Understanding the Problem

We get a list of `words` and a string `note`.


We need to check if **any one word** can be built using letters from the note.


Rules:

- Letters can be taken from anywhere in the note. They do not need to be next to each other.
- The order of letters does not matter.
- Each letter of the note can be used only once. So if a word needs two `o`, the note must have at least two `o`.


**Example:** `words = ["moon", "star"]`, `note = "monday"` returns `false`.


"moon" needs two `o`, but "monday" has only one. "star" needs an `s`, but "monday" has none.


**Limits to keep in mind:** the note can have up to 100,000 letters. There can be up to 1000 words, each with up to 100 letters. All letters are lowercase English letters.


## Solution 1: Brute Force (Cross Out Used Letters)

### Idea

This is how we would solve it with pen and paper.


Take a word. For each of its letters, search the note for a copy that is not used yet. When we find one, cross it out so it cannot be used again.


If some letter has no free copy left, this word fails. We move to the next word and start again with a fresh note.


If every letter of a word is found, we return `true`.


### Why a `boolean[] used` Array?

Each letter of the note can be used only once. So we must remember which positions are already taken.


`used[i]` is `true` when the letter at position `i` of the note is crossed out. We make a new array for every word, because every word gets the full note.


### Code

```java
import java.util.*;

public class AnagramFinder {

    public AnagramFinder() {
    }

    public boolean canFindAnagram(List<String> words, String note) {
        for (String word : words) {
            if (canBuild(word, note)) {
                return true; // one word is enough
            }
        }
        return false;
    }

    /**
     * Tries to take every letter of the word from the note.
     * A note letter is crossed out once we take it, so it is never used twice.
     */
    boolean canBuild(String word, String note) {
        // used[i] is true when the letter at position i of the note is already taken.
        // Every word starts with a fresh note, so we make a new array for each word.
        boolean[] used = new boolean[note.length()];

        for (char letter : word.toCharArray()) {
            int position = findFreeLetter(note, used, letter);
            if (position == -1) {
                return false; // no free copy of this letter is left
            }
            used[position] = true; // cross it out
        }
        return true;
    }

    /**
     * Returns the position of a copy of the letter that is not used yet.
     * Returns -1 if there is no such copy in the note.
     */
    int findFreeLetter(String note, boolean[] used, char letter) {
        for (int i = 0; i < note.length(); i++) {
            if (!used[i] && note.charAt(i) == letter) {
                return i;
            }
        }
        return -1;
    }
}
```


### Complexity

Let `N` be the length of the note, `W` the number of words and `L` the length of a word.

- **Time:** `O(W × L × N)`. Every letter search can walk through the whole note.
- **Space:** `O(N)` for the `used` array.


### Why Is This Slow?

There can be 1000 words with 100 letters each. That is up to 100,000 letter searches.


Each search can walk through all 100,000 letters of the note.


100,000 × 100,000 = **10 billion steps** in the worst case. That is far too slow.


We also do work that is not needed. We search for the exact position of each letter. But the answer does not depend on positions at all.


## Solution 2: Count Letters Once (Optimal)

### Key Insight

We do not care **where** a letter is in the note. We only care **how many** copies of it the note has.


A word can be built from the note if, for every letter, the word needs no more copies than the note has.


One more thing: the note never changes. So there is no need to read it again for every word. We count its letters **once**, before checking any word.


### Why an Array of Size 26?

We need to store a count for each letter. A `HashMap<Character, Integer>` would work.


But the input has only lowercase letters, so there are only 26 possible keys. An `int[26]` array does the same job in a simpler and faster way.


`count[0]` is for `'a'`, `count[1]` is for `'b'`, and so on. To turn a letter into its index we use `letter - 'a'`. For example, `'c' - 'a'` is `2`.


### Why Not a Set?

A set only remembers **if** a letter is present. It forgets **how many** times.


"moon" needs two `o`, but "monday" has only one. A set would see that `o` is present and wrongly accept "moon". So we need counts, not just presence.


### Steps

1. Count the letters of the note into `noteCount`. Do this only once.
2. For each word, count its letters into `wordCount`.
3. Compare the two arrays. If `wordCount[i] > noteCount[i]` for any letter, this word does not fit.
4. If a word fits, return `true` right away.
5. If no word fits, return `false`.


A word longer than the note needs no special check. Some letter count will be too high, so the comparison fails on its own.


### Dry Run

`words = ["moon", "star"]`, `note = "monday"`


The note has: `m:1, o:1, n:1, d:1, a:1, y:1`

| Word | Letters needed | Note has | Fits? |
|------|----------------|----------|-------|
| moon | m:1, n:1, o:2 | m:1, n:1, o:1 | No, one `o` is missing |
| star | a:1, r:1, s:1, t:1 | a:1, r:0, s:0, t:0 | No, `r`, `s` and `t` are missing |


No word fits, so the answer is `false`.


### Code

```java
import java.util.*;

public class AnagramFinder {

    public AnagramFinder() {
    }

    public boolean canFindAnagram(List<String> words, String note) {
        // The note never changes, so we count its letters only once
        int[] noteCount = countLetters(note);

        for (String word : words) {
            if (canBuild(word, noteCount)) {
                return true; // one word is enough
            }
        }
        return false;
    }

    /**
     * Returns true if the note has enough copies of every letter of the word.
     */
    boolean canBuild(String word, int[] noteCount) {
        int[] wordCount = countLetters(word);

        for (int i = 0; i < 26; i++) {
            // the word needs more of this letter than the note has
            if (wordCount[i] > noteCount[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Counts how many times each letter appears in the text.
     * count[0] is for 'a', count[1] is for 'b', and so on till count[25] for 'z'.
     */
    int[] countLetters(String text) {
        int[] count = new int[26];
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            count[letter - 'a']++;
        }
        return count;
    }
}
```


### Complexity

- **Time:** `O(N + W × L)`. We count the note once in `N` steps. Each word takes `L` steps to count and 26 steps to compare.
- **Space:** `O(1)`. We only keep arrays of 26 numbers.


In the worst case that is about 100,000 + 1000 × (100 + 26), roughly **226,000 steps**. Much better than 10 billion.


## Summary

| Solution | Time | Extra Space |
|----------|------|-------------|
| Brute force (cross out letters) | `O(W × L × N)` | `O(N)` |
| Count letters once | `O(N + W × L)` | `O(1)` |


Whenever the order of letters does not matter, think about **counting** instead of **searching**.