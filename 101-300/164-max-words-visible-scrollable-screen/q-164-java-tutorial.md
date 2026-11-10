# Max Words Visible on Scrollable Screen in Java

#### Problem Statement
[https://codezym.com/question/164-max-words-visible-scrollable-screen](https://codezym.com/question/164-max-words-visible-scrollable-screen)


## Core Idea

The screen always shows only the latest `rows` rows. So we can think of it as a **window** that slides down over the rows as we keep writing.


Each new word changes the screen very little. It adds one word at the bottom. Sometimes it also starts a new row, which can push the oldest row off the top.


So there is no need to recount the whole screen again and again. We keep a **running count** of visible words instead: add 1 for every new word, and subtract a whole row's words when that row scrolls away.


The largest value this count ever reaches is the answer. This is the classic **sliding window** technique, and it solves the problem in a single pass over the words.


---


## Understanding the Problem

Two simple rules decide everything.


**Rule 1: Filling rows.** A word goes on the current row if it fits after one space. If it does not fit, it starts a new row.


A word fits on the current row when:

```
rowLength + 1 + word.length() <= cols
```

`rowLength` is the number of characters already used on that row. The `+ 1` is the space before the new word. The first word of a row needs no space.


**Rule 2: Scrolling.** The screen shows at most `rows` rows. When a new row makes the count go above `rows`, the oldest row disappears from the top.


Here is Example 1 with `rows = 2` and `cols = 10`:

```
row 0:  hello          1 word
row 1:  world a b      3 words   ("world a b c" would need 11 characters)
row 2:  c              1 word

screen before "c":  row 0 + row 1  =  1 + 3  =  4 words
screen after  "c":  row 1 + row 2  =  3 + 1  =  4 words
```

So the answer is `4`.


---


## Solution 1: Simulate the Screen (Brute Force)

The most direct idea is to copy exactly what the screen does.


We keep a list of all rows, where each row is a list of words. After writing each word, we look at the last `rows` rows and count their words.

```java
List<List<String>> allRows = new ArrayList<>(); // every row written so far
int rowLength = 0;
int maxVisible = 0;

for (String word : words) {
    // 1. put the word on the last row if it fits, otherwise on a new row
    if (!allRows.isEmpty() && rowLength + 1 + word.length() <= cols) {
        rowLength += 1 + word.length();
    } else {
        allRows.add(new ArrayList<>());
        rowLength = word.length();
    }
    allRows.get(allRows.size() - 1).add(word);

    // 2. recount every word on the visible screen (the last `rows` rows)
    int visibleWords = 0;
    for (int r = Math.max(0, allRows.size() - rows); r < allRows.size(); r++) {
        visibleWords += allRows.get(r).size();
    }
    maxVisible = Math.max(maxVisible, visibleWords);
}
```

This gives the correct answer, but it has two problems.


**It is slow.** After every single word, we recount up to `rows` rows. With `100,000` words and `10,000` rows, that can be `1,000,000,000` steps.


**It stores more than it needs.** We keep every word, but we only ever use the number of words in each row.


- **Time:** `O(n × rows)`, where `n` is the number of words
- **Space:** `O(n)`


---


## Solution 2: Sliding Window with a Running Count (Optimal)

### Key Observation

Compare the screen just before and just after writing one word. Only two things can change:

1. The new word adds **1** word at the bottom.
2. If the word starts a new row and the screen now has more than `rows` rows, the oldest row leaves. This removes **all the words of that one row**.


So instead of recounting, we keep a running total and apply these two small changes. Each change takes constant time.


### What We Keep Track Of

**`wordsInRow` (an int array).** It stores how many words each row has. When a row scrolls off, we look up its count here to know how much to subtract. We store counts, not words, because counts are all we need.


Every row has at least one word, so there can never be more rows than words. That is why an array of size `words.size()` is always big enough.


**`firstVisibleRow` (an index).** It points to the oldest row still on the screen. To scroll, we just move it forward by one. Nothing is ever deleted, so scrolling is instant.


**`visibleWords` (the running count).** It is the number of words on the screen right now. After every word, we compare it with the best answer so far.


`firstVisibleRow` and the last row are the two edges of our sliding window. We also keep `rowLength` (characters used on the current row) and `totalRows` (rows written so far).


### Steps

For each word:

1. If the word fits on the current row, add it there.
2. If it does not fit, start a new row. If the screen now has more than `rows` rows, subtract the words of `firstVisibleRow` and move `firstVisibleRow` forward by one.
3. Add 1 to the current row's count and to `visibleWords`.
4. Update `maxVisible`.


Scrolling happens in step 2, before we update the answer in step 4. So we never count `rows + 1` rows by mistake.


### Dry Run on Example 2

`words = ["a", "bb", "ccc", "dd", "e"]`, `rows = 2`, `cols = 6`

| Word | What happens | Screen | `visibleWords` | `maxVisible` |
|---|---|---|---|---|
| `a` | starts row 0 | `a` | 1 | 1 |
| `bb` | fits on row 0 | `a bb` | 2 | 2 |
| `ccc` | starts row 1 | `a bb`<br>`ccc` | 3 | 3 |
| `dd` | fits on row 1 | `a bb`<br>`ccc dd` | 4 | 4 |
| `e` | starts row 2, row 0 (2 words) scrolls off | `ccc dd`<br>`e` | 4 - 2 + 1 = 3 | 4 |


The screen drops to 3 words at the end, but `maxVisible` remembers the best moment. The answer is `4`.


### Complexity

- **Time: `O(n)`.** Each word is handled once. Each row scrolls off at most once, so all the subtractions together take at most `n` steps.
- **Space: `O(n)`** for the `wordsInRow` array.


### Code

```java
import java.util.*;

public class ScrollableScreen {

    public ScrollableScreen() {
    }

    /**
     * Writes the words on the screen one by one and returns the
     * largest number of words that are visible at the same time.
     */
    public int maxWordsVisible(List<String> words, int rows, int cols) {
        // wordsInRow[r] = number of words written on row r.
        // Every row has at least one word, so there are never more rows than words.
        int[] wordsInRow = new int[words.size()];

        int totalRows = 0;        // rows written so far
        int rowLength = 0;        // characters used on the last row
        int firstVisibleRow = 0;  // oldest row still on the screen
        int visibleWords = 0;     // words currently on the screen
        int maxVisible = 0;

        for (String word : words) {
            if (totalRows > 0 && rowLength + 1 + word.length() <= cols) {
                // the word fits on the current row, after one space
                rowLength += 1 + word.length();
            } else {
                // the word starts a new row
                totalRows++;
                rowLength = word.length();

                // more rows than the screen can show: the oldest row scrolls off
                if (totalRows - firstVisibleRow > rows) {
                    visibleWords -= wordsInRow[firstVisibleRow];
                    firstVisibleRow++;
                }
            }

            // count the new word on its row and on the screen
            wordsInRow[totalRows - 1]++;
            visibleWords++;
            maxVisible = Math.max(maxVisible, visibleWords);
        }

        return maxVisible;
    }
}
```