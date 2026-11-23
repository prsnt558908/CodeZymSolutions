# Maximum Number of Non-Overlapping Palindromic Substrings in Java

#### Problem Statement
[https://codezym.com/question/261-max-non-overlapping-palindromes](https://codezym.com/question/261-max-non-overlapping-palindromes)

We need the maximum number of non-overlapping palindromes, each of length at least `k`. Among all answers with that maximum count, we must return one very specific selection.

The key idea: for every start index `i`, we only care about the **shortest** valid palindrome that starts there. It ends earliest, so it leaves the most room for everything after it. We store its end in `minEnd[i]`.

Once we have `minEnd`, the rest is three simple passes:

1. A small DP from the right finds the maximum count.
2. A greedy pass from the left picks the smallest possible start each time.
3. A final pass stretches each palindrome's end as far right as allowed.

The only hard part is building `minEnd` fast. We first build it the simple O(n²) way by expanding around every center. Then we make it O(n log n) using Manacher's algorithm and a min-heap.

---

## Understanding the Tie-Break

Many selections can reach the maximum count. The rules to pick one boil down to this:

- **Starts first:** compare start indices from left to right. At the first difference, the smaller start wins.
- **Ends second:** only if all starts are equal, compare ends from left to right. At the first difference, the larger end wins.

So every palindrome should **start as early as possible**. Once the starts are fixed, every palindrome should **end as late as possible**.

Example: `s = "aaaaa"`, `k = 2`. The maximum count is 2. A few of the possible selections:

| Selection | Starts | Ends |
|---|---|---|
| `["aaa", "aa"]` | [0, 3] | [2, 4] |
| `["aa", "aa"]` | [0, 2] | [1, 3] |
| `["aa", "aaa"]` | [0, 2] | [1, 4] |

Starts `[0, 2]` beat `[0, 3]`. Between the last two, ends `[1, 4]` beat `[1, 3]`. So the answer is `["aa", "aaa"]`.

---

## Solution 1: Simple Approach (Expand Around Center)

### Step 1: Shortest Palindrome From Every Start

`minEnd[i]` is the end index of the shortest palindrome of length at least `k` that starts at `i`. It is `-1` if no such palindrome exists.

Why only the shortest one? If we start at `i`, ending earlier never hurts. It leaves more room on the right, so the next palindrome can start earlier.

The simple way to find every palindrome is to expand around every center. There are `2n - 1` centers: each character (odd lengths) and each gap between two neighbours (even lengths).

Every time the expansion gives a long enough palindrome `s[left..right]`, we update `minEnd[left]`.

```java
int[] minEnd = new int[n];
Arrays.fill(minEnd, -1);
for (int center = 0; center < 2 * n - 1; center++) {
    int left = center / 2;
    int right = left + center % 2;   // odd length: left == right, even length: right == left + 1
    while (left >= 0 && right < n && s.charAt(left) == s.charAt(right)) {
        boolean longEnough = right - left + 1 >= k;
        if (longEnough && (minEnd[left] == -1 || right < minEnd[left])) {
            minEnd[left] = right;
        }
        left--;
        right++;
    }
}
```

### Step 2: Maximum Count From Every Suffix

`maxCount[i]` is the most palindromes we can pick inside `s[i..n-1]`.

At index `i` we have two choices. Skip it, or start a palindrome here. If we start here, we use the shortest one, because it leaves the most room.

```java
int[] maxCount = new int[n + 1];   // maxCount[n] = 0
for (int i = n - 1; i >= 0; i--) {
    maxCount[i] = maxCount[i + 1];   // skip index i
    if (minEnd[i] != -1) {
        // start a palindrome at i
        maxCount[i] = Math.max(maxCount[i], 1 + maxCount[minEnd[i] + 1]);
    }
}
```

`maxCount[0]` is the size of the answer.

### Step 3: Pick the Smallest Starts

Starts are compared from left to right, so the first start matters most. We take the smallest index `i` that still lets us reach the maximum count. Then we repeat for the next palindrome.

Say we still `need` palindromes. If we take the shortest palindrome at `i`, there is room for `maxCount[minEnd[i] + 1]` more after it. So `i` is a good start when that value is at least `need - 1`.

After picking `i`, we continue from `minEnd[i] + 1`. Ending as early as possible here gives the next start the most choices.

```java
List<Integer> starts = new ArrayList<>();
int need = maxCount[0];
int i = 0;
while (need > 0) {
    if (minEnd[i] != -1 && maxCount[minEnd[i] + 1] >= need - 1) {
        starts.add(i);
        i = minEnd[i] + 1;
        need--;
    } else {
        i++;
    }
}
```

In Example 1 (`"abaccdbbd"`, `k = 2`), this picks start `5` (`"dbbd"`) and not start `6` (`"bb"`). `"bb"` ends earlier, but both still reach the maximum count, so the smaller start wins.

### Step 4: Stretch the Ends

Now the starts are fixed. Each palindrome only has to end before the next start, so the ends don't affect each other. To get the largest end sequence, we push every end as far right as we can.

For each start, we try the furthest allowed end first and walk left until we hit a palindrome. We stop at `minEnd[start]` at the latest.

```java
List<String> result = new ArrayList<>();
for (int j = 0; j < starts.size(); j++) {
    int start = starts.get(j);
    int end = (j + 1 < starts.size()) ? starts.get(j + 1) - 1 : n - 1;
    while (!isPalindrome(s, start, end)) {
        end--;
    }
    result.add(s.substring(start, end + 1));
}
```

Here `isPalindrome` is the usual two-pointer check.

### Dry Run

`s = "aaaaa"`, `k = 2`

| i | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| minEnd | 1 | 2 | 3 | 4 | -1 | |
| maxCount | 2 | 2 | 1 | 1 | 0 | 0 |

- `need = 2`, `i = 0`: `maxCount[2] = 1 >= 1`. Pick `0`, jump to `2`.
- `need = 1`, `i = 2`: `maxCount[4] = 0 >= 0`. Pick `2`.
- Stretch start `0`: it must end before `2`, so `"aa"`.
- Stretch start `2`: it can end at `4`, and `"aaa"` is a palindrome.

Answer: `["aa", "aaa"]`.

### Why Is It Slow?

Steps 2 and 3 are O(n). The problem is finding palindromes.

For `s = "aaaa...a"` with `n = 100,000`, every expansion in Step 1 runs all the way to the border. That is about `n² / 2 = 5 billion` steps. The two-pointer check in Step 4 can also cost O(n) per call.

**Time:** O(n²) **Space:** O(n)

---

## Solution 2: Optimized Approach (Manacher + Min-Heap)

Steps 2, 3 and 4 stay exactly the same. We only need two things to be faster:

- building `minEnd[i]` for every `i`
- checking if `s[i..j]` is a palindrome

### Longest Palindrome Around Every Center

Put a `#` around every character: `"aba"` becomes `t = "#a#b#a#"`. Now every palindrome of `s`, odd or even, has exactly one center in `t`.

`radius[c]` is how far the longest palindrome around `t[c]` reaches on each side. A nice bonus: `radius[c]` is also the length of that palindrome in `s`. For `"abba"`, the center `#` between the two `b`s has radius `4`.

Expanding from every center is the slow part, and Manacher's algorithm fixes it.

We remember the palindrome that reaches furthest to the right. If the current position is inside it, its mirror position on the left side already has a radius. By symmetry, the current position gets at least that much for free (capped at the border). We only expand beyond what we already know.

That border only moves right, so the total work is O(n).

### From Centers to minEnd

In `t`, the character `s[i]` sits at index `2i + 1`. Mirroring it around center `c` lands on index `2c - 2i - 1`, which is `s[c - i - 1]`.

So the palindrome around center `c` that starts at `i`:

- ends at `c - i - 1`
- has length `c - 2i`

For a start `i`, a center `c` is useful when:

1. **It reaches `i`.** Its longest palindrome starts at `(c - radius[c]) / 2`, and this must be `<= i`.
2. **It is long enough.** `c - 2i >= k`, which means `c >= 2i + k`.

Among the useful centers, the **smallest** `c` gives the earliest end.

Now we sweep `i` from left to right and keep the centers in a min-heap:

- Rule 1 only gets easier as `i` grows. So a center **joins** the heap when `i` reaches its start, and it stays.
- Rule 2 only gets harder as `i` grows. So once a center fails it, we **pop** it for good.
- The top of the heap is the best center, so `minEnd[i] = top - i - 1`.

**Why a min-heap?** Centers join in a mixed order, but we always want the smallest one. A min-heap keeps the smallest on top, and adding or removing a center costs only O(log n).

**Why `centersByStart`?** It is a list of lists. `centersByStart.get(i)` holds the centers whose longest palindrome starts at `i`, so we know exactly which centers join at step `i`.

We only add centers with `radius[c] >= k`. Shorter ones can never give a long enough palindrome.

### O(1) Palindrome Check

`s[i..j]` is centered at index `i + j + 1` of `t`. It is a palindrome exactly when the longest palindrome around that center is at least `j - i + 1` long.

```java
boolean isPalindrome(int[] radius, int i, int j) {
    return radius[i + j + 1] >= j - i + 1;
}
```

### Java Code

```java
import java.util.*;

public class MaximumNonOverlappingPalindromicSubstrings {

    public MaximumNonOverlappingPalindromicSubstrings() {

    }

    public List<String> maximumNonOverlappingSubstrings(String s, int k) {
        int n = s.length();
        int[] radius = buildRadius(s);
        int[] minEnd = buildMinEnd(radius, n, k);

        // maxCount[i] = most palindromes we can pick inside s[i..n-1]
        int[] maxCount = new int[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            maxCount[i] = maxCount[i + 1];
            if (minEnd[i] != -1) {
                maxCount[i] = Math.max(maxCount[i], 1 + maxCount[minEnd[i] + 1]);
            }
        }

        // pick the smallest start that still lets us reach the maximum count
        List<Integer> starts = new ArrayList<>();
        int need = maxCount[0];
        int i = 0;
        while (need > 0) {
            if (minEnd[i] != -1 && maxCount[minEnd[i] + 1] >= need - 1) {
                starts.add(i);
                i = minEnd[i] + 1;   // shortest palindrome leaves the most room
                need--;
            } else {
                i++;
            }
        }

        // starts are fixed, now push every end as far right as possible
        List<String> result = new ArrayList<>();
        for (int j = 0; j < starts.size(); j++) {
            int start = starts.get(j);
            int end = (j + 1 < starts.size()) ? starts.get(j + 1) - 1 : n - 1;
            while (!isPalindrome(radius, start, end)) {
                end--;   // stops at minEnd[start] at the latest
            }
            result.add(s.substring(start, end + 1));
        }
        return result;
    }

    /**
     * Manacher's algorithm on t = "#s0#s1#...#".
     * radius[c] = how far the palindrome around t[c] reaches.
     * It is also the length of that palindrome in s.
     */
    int[] buildRadius(String s) {
        int size = 2 * s.length() + 1;
        char[] t = new char[size];
        for (int i = 0; i < size; i++) {
            t[i] = (i % 2 == 0) ? '#' : s.charAt(i / 2);
        }

        int[] radius = new int[size];
        int center = 0;   // center of the palindrome that reaches furthest right
        int right = 0;    // its right border
        for (int i = 0; i < size; i++) {
            int r = 0;
            if (i < right) {
                // free head start copied from the mirror position
                r = Math.min(right - i, radius[2 * center - i]);
            }
            while (i - r - 1 >= 0 && i + r + 1 < size && t[i - r - 1] == t[i + r + 1]) {
                r++;
            }
            radius[i] = r;
            if (i + r > right) {
                center = i;
                right = i + r;
            }
        }
        return radius;
    }

    /**
     * minEnd[i] = end of the shortest palindrome of length >= k that starts at i, or -1.
     * Center c (in t) gives the palindrome s[i .. c-i-1] of length c-2i.
     */
    int[] buildMinEnd(int[] radius, int n, int k) {
        // centersByStart.get(i) = centers whose longest palindrome starts at i
        List<List<Integer>> centersByStart = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            centersByStart.add(new ArrayList<>());
        }
        for (int c = 1; c < 2 * n; c++) {
            if (radius[c] >= k) {
                centersByStart.get((c - radius[c]) / 2).add(c);
            }
        }

        int[] minEnd = new int[n];
        PriorityQueue<Integer> heap = new PriorityQueue<>();
        for (int i = 0; i < n; i++) {
            // these centers now reach back to i, and keep reaching for every bigger i
            heap.addAll(centersByStart.get(i));
            // centers below 2i + k give length < k, and never become useful again
            while (!heap.isEmpty() && heap.peek() < 2 * i + k) {
                heap.poll();
            }
            minEnd[i] = heap.isEmpty() ? -1 : heap.peek() - i - 1;
        }
        return minEnd;
    }

    // s[i..j] is a palindrome if the palindrome around its center is long enough
    boolean isPalindrome(int[] radius, int i, int j) {
        return radius[i + j + 1] >= j - i + 1;
    }
}
```

### Complexity

- **Manacher:** O(n)
- **Heap sweep:** every center is pushed and popped at most once, so O(n log n)
- **maxCount, greedy and stretching:** O(n). The stretch windows `[start, nextStart - 1]` never overlap, so all the `end--` steps add up to at most `n`.

**Time:** O(n log n) **Space:** O(n)