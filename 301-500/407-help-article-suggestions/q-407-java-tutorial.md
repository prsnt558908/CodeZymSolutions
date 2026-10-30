# Help Article Suggestions in Java

#### Problem Statement
[https://codezym.com/question/407-help-article-suggestions](https://codezym.com/question/407-help-article-suggestions)

---

## Core Idea

Think of every article as a **node**, and every related pair as an **edge** (a link) between two nodes. A **topic group** is then simply a set of articles that are linked together, directly or through other articles. In graph terms, it is a **connected component**.


So each call to `getSuggestions()` asks one question: in the opened article's group, which `k` other articles have the highest scores?


The key point is that **nothing changes after the constructor**. The links are fixed and the scores are fixed. So we do the heavy work only once:

1. Find every group using **BFS** (Breadth First Search).
2. Sort every group from best to worst.


After that, each call just reads the first few items of a ready-made list.

---

## Understanding the Problem

Let us look at Example 1.

```
relatedArticles = ["1,2", "0", "4", "", "2"]
helpfulScores   = [25, 80, 65, 95, 70]
```

Two rules decide the groups:

- **A link works both ways.** Article 0 lists 2, but article 2 does not list 0. They are still linked.
- **Links chain together.** 4 is linked to 2, and 2 is linked to 0. So 4 and 0 are in the same group, even without a direct link.


So the groups are `{0, 1, 2, 4}` and `{3}`.


For `getSuggestions(0, 3)`, the other articles in the group are 1 (score 80), 4 (score 70) and 2 (score 65). Sorted by score, the answer is `[1, 4, 2]`.


Article 3 is alone in its group, so `getSuggestions(3, 4)` returns an empty list.

---

## Solution 1: Brute Force (BFS on Every Call)

### Idea

Build the graph once in the constructor. Then, on every call:

1. Run BFS from the opened article to collect its whole group.
2. Sort the group.
3. Return the first `k` articles.

### Building the graph

`neighbors.get(i)` is the list of articles directly linked to article `i`. When article `i` lists article `j`, we add the link on **both** sides, because a link works both ways.

```java
for (int i = 0; i < n; i++) {
    for (String part : relatedArticles.get(i).split(",")) {
        String id = part.trim();
        if (id.isEmpty()) continue; // empty string means no related article
        int j = Integer.parseInt(id);
        neighbors.get(i).add(j);
        neighbors.get(j).add(i); // relatedness works in both directions
    }
}
```

### Answering a call

BFS starts at the opened article and keeps visiting new neighbors with the help of a queue, until no new article is left. Every article it reaches belongs to the same group. Here `scores` is the `helpfulScores` list saved in the constructor.

```java
public List<Integer> getSuggestions(int articleId, int k) {
    // 1. BFS from the opened article to collect every other article in its group
    List<Integer> group = new ArrayList<>();
    Set<Integer> visited = new HashSet<>();
    Queue<Integer> queue = new ArrayDeque<>();
    queue.add(articleId);
    visited.add(articleId);

    while (!queue.isEmpty()) {
        int article = queue.poll();
        if (article != articleId) group.add(article);
        for (int neighbor : neighbors.get(article)) {
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }
    }

    // 2. Sort: higher score first, smaller id first on a tie
    group.sort((a, b) -> {
        int byScore = Integer.compare(scores.get(b), scores.get(a));
        if (byScore != 0) return byScore;
        return Integer.compare(a, b);
    });

    // 3. Return the first k
    return new ArrayList<>(group.subList(0, Math.min(k, group.size())));
}
```

### What is wrong with it?

The answers are correct, but every call repeats two big jobs:

1. **It finds the same group again.** The groups never change, yet we run BFS on every call.
2. **It sorts the same group again.** The scores never change, yet we sort on every call.


Imagine all 100,000 articles in one big group and 100,000 calls. Each call walks through and sorts 100,000 articles. That is billions of steps, which is far too slow.

---

## Solution 2: Do the Heavy Work Once (Optimal)

### Idea

Since nothing changes after the constructor, we move both repeated jobs into the constructor. The graph is built exactly as before.


**Fix 1: Find every group once.** Go through the articles in order. When an article has no group yet, start a BFS from it. Every article this BFS reaches gets the same group id. This way each article is visited only once in total.


**Fix 2: Sort every group once.** Right after a group is found, sort it: higher score first, smaller id first on a tie. Now every group is a ready-made ranking.


**Answering a call** is now very simple. Go to the opened article's group and walk its sorted list from the start. Skip the opened article and stop as soon as we have `k` articles. So a call takes about `k` steps, no matter how big the group is.

### Example

In Example 1, group `{0, 1, 2, 4}` is stored as `[1, 4, 2, 0]` (scores 80, 70, 65, 25).

- `getSuggestions(0, 3)`: take 1, 4, 2 and stop. Answer `[1, 4, 2]`.
- `getSuggestions(4, 2)`: take 1, skip 4, take 2 and stop. Answer `[1, 2]`.

### Why these data structures?

- **`neighbors` (list of lists):** this is the graph. It lets BFS jump from an article to all its related articles quickly. It is only needed while building the groups, so it is a local variable inside the constructor.
- **`groupOf` (int array):** `groupOf[i]` tells us the group of article `i` in one step. It also works as the "visited" mark during BFS (`-1` means not visited yet), so we do not need a separate visited set.
- **`groups` (list of lists):** `groups.get(g)` holds all articles of group `g`, already sorted best first. A call only reads the front of this list.


> You can also find the groups with **Union Find**. BFS is used here because it is easier to follow.

### Code

```java
import java.util.*;

public class HelpArticleSuggestions {
    // groupOf[i] = id of the topic group that article i belongs to
    int[] groupOf;

    // groups.get(g) = all articles of group g, sorted best suggestion first
    List<List<Integer>> groups = new ArrayList<>();

    public HelpArticleSuggestions(List<String> relatedArticles, List<Integer> helpfulScores) {
        int n = relatedArticles.size();

        // Step 1: build the graph. neighbors.get(i) = articles directly related to article i
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            neighbors.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (String part : relatedArticles.get(i).split(",")) {
                String id = part.trim();
                if (id.isEmpty()) continue; // empty string means no related article
                int j = Integer.parseInt(id);
                neighbors.get(i).add(j);
                neighbors.get(j).add(i); // relatedness works in both directions
            }
        }

        // Step 2: find every topic group once, using BFS
        groupOf = new int[n];
        Arrays.fill(groupOf, -1); // -1 means "not in any group yet"

        for (int start = 0; start < n; start++) {
            if (groupOf[start] != -1) continue; // already found as part of an earlier group

            int groupId = groups.size();
            List<Integer> members = new ArrayList<>();
            Queue<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            groupOf[start] = groupId;

            while (!queue.isEmpty()) {
                int article = queue.poll();
                members.add(article);
                for (int neighbor : neighbors.get(article)) {
                    if (groupOf[neighbor] == -1) {
                        groupOf[neighbor] = groupId;
                        queue.add(neighbor);
                    }
                }
            }

            // Step 3: sort the group once. Higher score first, smaller id first on a tie.
            members.sort((a, b) -> {
                int byScore = Integer.compare(helpfulScores.get(b), helpfulScores.get(a));
                if (byScore != 0) return byScore;
                return Integer.compare(a, b);
            });
            groups.add(members);
        }
    }

    public List<Integer> getSuggestions(int articleId, int k) {
        List<Integer> result = new ArrayList<>();

        // The group is already sorted, so we just take the first k articles,
        // skipping the article the user has opened.
        for (int other : groups.get(groupOf[articleId])) {
            if (result.size() == k) break;
            if (other != articleId) result.add(other);
        }
        return result;
    }
}
```

---

## Complexity

`n` is the number of articles and `E` is the total number of ids listed in `relatedArticles`.

| | Brute Force | Optimal |
|---|---|---|
| Constructor | O(n + E) | O(n + E + n log n) |
| `getSuggestions` | O(n + E + n log n) | O(k) |
| Memory | O(n + E) | O(n + E) |


In the optimal solution, BFS touches each article and each link a fixed number of times. Sorting all the groups together costs at most O(n log n), because the group sizes add up to `n`.