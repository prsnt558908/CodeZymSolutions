# Find Number at Position in Java

#### Problem Statement
[https://codezym.com/question/427-find-number-at-position](https://codezym.com/question/427-find-number-at-position)


## Core Idea

We do not need to build every number to find just one of them.

If we fix the first card, the other cards can be arranged in `(digitCount - 1)!` ways. So the sorted numbers form equal sized groups, one group for each first card.

By counting how many full groups come before our position, we know the first digit right away. Then we remove that card and repeat the same step with the cards that are left.


## Approach 1: Brute Force

The simplest idea is to build all the numbers in sorted order and return the one at `position`.

If we always try the smaller cards first, the numbers are created already in sorted order. So we only need a counter.

```java
int count = 0;
String answer = "";

public String findNumber(int digitCount, int position) {
    count = 0;
    build(digitCount, position, new boolean[digitCount + 1], "");
    return answer;
}

// Builds numbers card by card. Smaller cards are tried first,
// so the numbers are created in sorted order.
void build(int digitCount, int position, boolean[] used, String current) {
    if (current.length() == digitCount) {
        count++;
        if (count == position) {
            answer = current;
        }
        return;
    }
    for (int card = 1; card <= digitCount; card++) {
        if (!used[card]) {
            used[card] = true;
            build(digitCount, position, used, current + card);
            used[card] = false;
        }
    }
}
```

### Why is this slow?

With 9 cards there are `9! = 362,880` numbers. This approach builds all of them, just to return one.

Even if we stop early, a position near the end still needs almost all of them.

Time complexity is `O(digitCount! * digitCount)`. We need a way to skip numbers instead of building them.


## Key Observation: Numbers Come in Groups

Here are all the numbers for `digitCount = 3`:

```
123  132  |  213  231  |  312  321
```

They form groups by their first card. Each group has `2! = 2` numbers, because the 2 other cards can be arranged in 2 ways.

In general, when `k` cards are left, each group has `(k - 1)!` numbers.

For `digitCount = 4`, each group has `3! = 6` numbers:

| First card | Positions |
|---|---|
| 1 | 1 to 6 |
| 2 | 7 to 12 |
| 3 | 13 to 18 |
| 4 | 19 to 24 |

Position `6` is in the first group, so the answer starts with `1`.

Inside that group, the same pattern repeats with cards `2, 3, 4`. So we can find every digit this way, one at a time.


## Approach 2: Pick One Digit at a Time (Optimal)

### Why a 0-based index?

The division works cleanly only when counting starts from 0. So we use `index = position - 1`.

Position `6` is the last number of the first group. But `6 / 6 = 1` points to the second group. With `index = 5`, we get `5 / 6 = 0`, which is the first group.

### Steps

1. Put cards `1` to `digitCount` in a list, in increasing order.
2. Find the group size: `(cards left - 1)!`
3. `index / groupSize` tells us which card in the list comes next.
4. Remove that card from the list and add it to the answer.
5. `index % groupSize` is our new index inside that group.
6. Repeat until the list is empty.

### Dry Run

`digitCount = 4`, `position = 6`, so `index = 5`.

| Cards left | index | groupSize | index / groupSize | Card picked | index % groupSize |
|---|---|---|---|---|---|
| 1 2 3 4 | 5 | 3! = 6 | 0 | 1 | 5 |
| 2 3 4 | 5 | 2! = 2 | 2 | 4 | 1 |
| 2 3 | 1 | 1! = 1 | 1 | 3 | 0 |
| 2 | 0 | 0! = 1 | 0 | 2 | 0 |

The answer is `"1432"`.

In example 2, `index = 72` and the first group size is `4! = 24`. Since `72 / 24 = 3`, the first card is at index `3` of `[1, 2, 3, 4, 5]`, which is `4`. That is why the answer starts with `4`.

### Data Structures Used

**`List<Integer> cards`:** holds the unused cards in increasing order. We pick a card by its index and remove it. The rest of the cards stay in order, so the same step works again for the next digit.

**`int[] factorial`:** `factorial[i]` stores `i!`. We fill it once at the start, so every step can read its group size directly.

### Code

```java
import java.util.ArrayList;
import java.util.List;

public class NumberGame {

    public NumberGame() {
    }

    /**
     * Returns the number at the given 1-based position when all
     * arrangements of cards 1 to digitCount are sorted in increasing order.
     */
    public String findNumber(int digitCount, int position) {
        // factorial[i] = i! = how many numbers can be formed with i cards
        int[] factorial = new int[digitCount + 1];
        factorial[0] = 1;
        for (int i = 1; i <= digitCount; i++) {
            factorial[i] = factorial[i - 1] * i;
        }

        // unused cards, always kept in increasing order
        List<Integer> cards = new ArrayList<>();
        for (int card = 1; card <= digitCount; card++) {
            cards.add(card);
        }

        // a 0-based index makes the division math simple
        int index = position - 1;
        StringBuilder result = new StringBuilder();

        while (!cards.isEmpty()) {
            // each card that can come next starts a group of this many numbers
            int groupSize = factorial[cards.size() - 1];

            // the group that holds our index tells us the next card
            int groupNumber = index / groupSize;
            int card = cards.remove(groupNumber); // remove by index
            result.append(card);

            // our index inside the chosen group
            index = index % groupSize;
        }
        return result.toString();
    }
}
```

### Complexity

**Time:** `O(digitCount^2)`. We pick `digitCount` cards, and removing a card from the list can shift up to `digitCount` cards. With at most 9 cards, this is tiny.

**Space:** `O(digitCount)` for the list, the factorial array and the answer.