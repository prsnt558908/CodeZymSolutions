# Minimum Cost Binary Search Tree From Words in Java

#### Problem Statement
[https://codezym.com/question/214-minimum-cost-binary-search-tree-from-words](https://codezym.com/question/214-minimum-cost-binary-search-tree-from-words)


The inorder traversal of a binary search tree is always sorted. So once we sort the words, their left to right order in the tree is fixed. The only real choice left is **which word becomes the root**.


Picking a root splits the other words into a left group and a right group. Each group is the same problem again, just smaller. This makes it a classic **dynamic programming on ranges** problem, also known as the **Optimal Binary Search Tree** problem.


We will start with a brute force that tries every root. Then we will save the answer of every range to get an `O(n³)` solution. Finally, a neat property of the best root brings it down to `O(n²)`.


---


## Understanding the Problem

Take Example 2. After sorting, the words and their costs are:

| Word | grape | kiwi | mango | orange |
|------|-------|------|-------|--------|
| Cost | 2     | 3    | 8     | 6      |


One optimal tree is:

```
            mango (8)              level 0
           /         \
      kiwi (3)     orange (6)      level 1
       /
  grape (2)                        level 2
```


A word at level `L` pays `(L + 1) * cost`. So the total is `1 * 8 + 2 * 3 + 2 * 6 + 3 * 2 = 32`.


Heavy words should sit close to the root. But the tree must also keep the sorted order, so we cannot place words freely. That is the whole challenge.


---


## The Key Idea: Pushing a Tree One Level Down

Say a group of words already forms a tree with total cost `X`. Now we hang this whole tree below a new root.


Every word in it moves one level down. A word that paid `(L + 1) * cost` now pays `(L + 2) * cost`, which is **exactly one extra cost**. So the group now costs `X + (sum of its costs)`.


For example, `kiwi` with `grape` below it costs `1 * 3 + 2 * 2 = 7`. Below `mango`, the same two words cost `2 * 3 + 3 * 2 = 12`, which is `7 + (3 + 2)`.


Now let `best(i, j)` be the minimum cost of a tree built from the sorted words `i` to `j`. If word `r` is the root:

- the root pays `cost[r]` once
- the left part pays `best(i, r - 1)` plus one extra cost per word
- the right part pays `best(r + 1, j)` plus one extra cost per word


The root's cost and all the extra costs together are simply the sum of all costs from `i` to `j`. So we get one clean formula:

```
best(i, j) = sum(i, j) + minimum over every root r in i..j of ( best(i, r - 1) + best(r + 1, j) )

best of an empty range = 0
```


---


## Brute Force: Try Every Root

We can code the formula directly as a recursion on the sorted costs.

```java
// minimum cost of a BST built from sorted words i..j
long solve(long[] cost, int i, int j) {
    if (i > j) {
        return 0; // no words, no cost
    }

    long sum = 0;
    for (int k = i; k <= j; k++) {
        sum += cost[k];
    }

    // try every word as the root, both sides are solved the same way
    long best = Long.MAX_VALUE;
    for (int root = i; root <= j; root++) {
        best = Math.min(best, solve(cost, i, root - 1) + solve(cost, root + 1, j));
    }
    return sum + best;
}
```

The answer is `solve(cost, 0, n - 1)`.


### Why it is slow

The same range is solved again and again. For example, range `2..4` is solved inside `0..4`, `1..4`, `2..5`, `2..6` and many more.


The recursion makes about `3^n` calls. Even `n = 20` needs about 3.5 billion calls, so `n = 300` will never finish.


---


## Solution 1: Dynamic Programming on Ranges, O(n³)

There are only about `n² / 2` different ranges. So we solve each range **only once** and store its answer in a table `dp[i][j]`.


A range only needs the answers of shorter ranges inside it. So we fill the table **by length**: first all ranges of length 1, then length 2, and so on. When we reach a range, everything it needs is already in the table.


Two simple helpers keep the code clean:

- **`HashMap` from word to cost:** sorting moves the words around. The map remembers which cost belongs to which word, so each cost stays attached to its word.
- **Prefix sums:** every range needs `sum(i, j)`. With `prefix[k] = cost[0] + ... + cost[k - 1]`, we get it in `O(1)` as `prefix[j + 1] - prefix[i]`.


### Dry Run on Example 1

After sorting: `ant (2)`, `cat (5)`, `dog (4)`.

| Range | Words         | Sum | Best root | Left + right | dp |
|-------|---------------|-----|-----------|--------------|----|
| 0..0  | ant           | 2   | ant       | 0            | 2  |
| 1..1  | cat           | 5   | cat       | 0            | 5  |
| 2..2  | dog           | 4   | dog       | 0            | 4  |
| 0..1  | ant, cat      | 7   | cat       | 2 + 0 = 2    | 9  |
| 1..2  | cat, dog      | 9   | cat       | 0 + 4 = 4    | 13 |
| 0..2  | ant, cat, dog | 11  | cat       | 2 + 4 = 6    | 17 |


The answer is `dp[0][2] = 17`.


### Complexity

- **Time: `O(n³)`.** There are about `n² / 2` ranges and each one tries up to `n` roots. For `n = 300` this is about 4.5 million steps, which runs in milliseconds.
- **Space: `O(n²)`** for the `dp` table.


### Code

```java
import java.util.*;

public class MinimumCostBSTFromWords {

    public MinimumCostBSTFromWords() {
    }

    /**
     * Returns the minimum total cost of a BST built from all the words.
     * A word at level L adds (L + 1) * cost to the total.
     */
    public long minimumCostBST(List<String> words, List<Integer> costs) {
        long[] cost = getSortedCosts(words, costs);
        int n = cost.length;

        // prefix[k] = cost[0] + ... + cost[k - 1]
        // so the sum of cost[i..j] is prefix[j + 1] - prefix[i]
        long[] prefix = new long[n + 1];
        for (int k = 0; k < n; k++) {
            prefix[k + 1] = prefix[k] + cost[k];
        }

        // dp[i][j] = minimum cost of a BST built from sorted words i..j
        long[][] dp = new long[n][n];

        // short ranges first, because a long range needs the answers of shorter ones
        for (int length = 1; length <= n; length++) {
            for (int i = 0; i + length - 1 < n; i++) {
                int j = i + length - 1;

                // try every word in the range as the root
                long best = Long.MAX_VALUE;
                for (int root = i; root <= j; root++) {
                    long left = (root > i) ? dp[i][root - 1] : 0; // empty side costs 0
                    long right = (root < j) ? dp[root + 1][j] : 0;
                    best = Math.min(best, left + right);
                }

                // the root pays its cost once and every word below it pays one
                // extra cost. Together that is the sum of all costs in i..j
                dp[i][j] = best + (prefix[j + 1] - prefix[i]);
            }
        }
        return dp[0][n - 1];
    }

    /**
     * Returns the costs in the lexicographical order of their words.
     */
    long[] getSortedCosts(List<String> words, List<Integer> costs) {
        // remember the cost of each word, because sorting moves the words around
        Map<String, Integer> costOfWord = new HashMap<>();
        for (int i = 0; i < words.size(); i++) {
            costOfWord.put(words.get(i), costs.get(i));
        }

        List<String> sortedWords = new ArrayList<>(words);
        Collections.sort(sortedWords);

        long[] cost = new long[sortedWords.size()];
        for (int i = 0; i < sortedWords.size(); i++) {
            cost[i] = costOfWord.get(sortedWords.get(i));
        }
        return cost;
    }
}
```


---


## Solution 2: Knuth's Optimization, O(n²)

Solution 1 tries every root for every range. But the best root does not jump around randomly.


Suppose we add one more word at the right end of a range. The right side gets heavier, so the best root stays where it is or moves right, but it never moves left. Removing a word from the left end works the same way.


This is a proven property of optimal BSTs, known as **Knuth's optimization**:

```
bestRoot(i, j - 1)  <=  bestRoot(i, j)  <=  bestRoot(i + 1, j)
```


So for range `i..j` we only try the roots inside this window. Both ends of the window come from shorter ranges, so they are already known. We keep them in a second table `bestRoot[i][j]`.


In Example 1, ranges `0..1` and `1..2` both have `cat` as their best root. So for range `0..2` the window holds only `cat`, and we skip `ant` and `dog`.


### Why this is O(n²)

Look at all ranges of one fixed length. The window of range `i..j` ends at `bestRoot(i + 1, j)`. The window of the next range `i+1..j+1` starts at exactly this same root.


So the windows join end to end, like steps along one road. Together they cover at most about `2n` roots for each length. With `n` lengths, the total work is `O(n²)`.


For `n = 300`, this is about 100 thousand steps instead of 4.5 million.


### Complexity

- **Time: `O(n²)`.**
- **Space: `O(n²)`** for the `dp` and `bestRoot` tables.


### Code

```java
import java.util.*;

public class MinimumCostBSTFromWords {

    public MinimumCostBSTFromWords() {
    }

    /**
     * Returns the minimum total cost of a BST built from all the words.
     * Knuth's optimization: the best root of i..j always lies between
     * the best root of i..j-1 and the best root of i+1..j.
     */
    public long minimumCostBST(List<String> words, List<Integer> costs) {
        long[] cost = getSortedCosts(words, costs);
        int n = cost.length;

        // prefix[k] = cost[0] + ... + cost[k - 1]
        long[] prefix = new long[n + 1];
        for (int k = 0; k < n; k++) {
            prefix[k + 1] = prefix[k] + cost[k];
        }

        // dp[i][j] = minimum cost of a BST built from sorted words i..j
        // bestRoot[i][j] = the root that gives dp[i][j]
        long[][] dp = new long[n][n];
        int[][] bestRoot = new int[n][n];

        // a single word is the root of its own tree
        for (int i = 0; i < n; i++) {
            dp[i][i] = cost[i];
            bestRoot[i][i] = i;
        }

        for (int length = 2; length <= n; length++) {
            for (int i = 0; i + length - 1 < n; i++) {
                int j = i + length - 1;

                // the best root of i..j is always inside this window
                int low = bestRoot[i][j - 1];
                int high = bestRoot[i + 1][j];

                long best = Long.MAX_VALUE;
                for (int root = low; root <= high; root++) {
                    long left = (root > i) ? dp[i][root - 1] : 0;
                    long right = (root < j) ? dp[root + 1][j] : 0;
                    if (left + right < best) {
                        best = left + right;
                        bestRoot[i][j] = root;
                    }
                }
                dp[i][j] = best + (prefix[j + 1] - prefix[i]);
            }
        }
        return dp[0][n - 1];
    }

    /**
     * Returns the costs in the lexicographical order of their words.
     */
    long[] getSortedCosts(List<String> words, List<Integer> costs) {
        // remember the cost of each word, because sorting moves the words around
        Map<String, Integer> costOfWord = new HashMap<>();
        for (int i = 0; i < words.size(); i++) {
            costOfWord.put(words.get(i), costs.get(i));
        }

        List<String> sortedWords = new ArrayList<>(words);
        Collections.sort(sortedWords);

        long[] cost = new long[sortedWords.size()];
        for (int i = 0; i < sortedWords.size(); i++) {
            cost[i] = costOfWord.get(sortedWords.get(i));
        }
        return cost;
    }
}
```