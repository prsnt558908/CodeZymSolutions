# Design a Customer Support Agent Rating Leaderboard in Java

#### Problem Statement
[https://codezym.com/question/35-design-customer-support-agent-rating-leaderboard](https://codezym.com/question/35-design-customer-support-agent-rating-leaderboard)

The core idea is simple: **do the math when a rating comes in, not when someone asks for the leaderboard.**

To find an average we never need the individual ratings. We only need two numbers per agent: the **sum** of the ratings and the **count** of ratings. So we keep these two numbers for every agent overall, and once more for every agent in every month. When a leaderboard is asked for, we turn each sum and count into a rounded average and sort.

No design pattern is needed here. This is a data structure problem, and patterns like Observer or Strategy would only add extra classes without making the code faster or easier to follow. One small helper class and two hash maps are enough.

We will start with a brute force solution that stores every rating, see why it is slow, and then improve it.

## Understanding the Problem

We need three methods:

- `rateAgent(agentName, rating, date)`: records one rating (1 to 5) for an agent on a date.
- `getAverageRatings()`: returns every agent with their overall average, best first.
- `getBestAgentsByMonth(month)`: same as above, but only ratings from that month count.

Each entry in the result looks like `"Bob,4.5"`.

The system should handle thousands of agents and millions of ratings, so the speed of each method matters.

## Do We Need a Design Pattern?

Two patterns look like a good fit at first glance:

- **Observer**: every new rating could notify two listeners, an overall board and a monthly board. But both boards live in the same class and are always updated together. Listener interfaces would only hide two simple map updates.
- **Strategy**: we could plug in different rules for ranking or rounding. But the problem has exactly one rule for each, and an interface with a single implementation is just extra code.

So we keep the design plain: one small `AgentStats` class and two maps. If more board types (like weekly or yearly) are added later, Observer would start to pay off.

## Two Small Details That Matter

### 1. Rounding to one decimal

Averages are rounded to one decimal, and a 5 in the second decimal rounds up. So 4.33 becomes 4.3, while 4.35 and 4.37 become 4.4.

A `double` cannot store 4.35 exactly. It is really stored as 4.34999999999999964..., so rounding with doubles is risky. For example `new BigDecimal(4.35).setScale(1, RoundingMode.HALF_UP)` gives 4.3, not 4.4.

To stay safe, we round using whole numbers only, which are always exact:

```
averageInTenths = (sum * 10 + count / 2) / count
```

Integer division always rounds down. Adding half of `count` before dividing pushes anything at .5 or above up to the next number. The result is the average in tenths, so 43 means 4.3.

| Ratings | Exact average | `(sum * 10 + count / 2) / count` | Result |
|---|---|---|---|
| 5, 3 | 4.0 | (80 + 1) / 2 = 40 | 4.0 |
| 5, 4, 4 | 4.333... | (130 + 1) / 3 = 43 | 4.3 |
| 5, 4, 4, 4 | 4.25 | (170 + 2) / 4 = 43 | 4.3 |
| 7 fives, 13 fours | 4.35 | (870 + 10) / 20 = 44 | 4.4 |

To print it, `tenths / 10` gives the part before the dot and `tenths % 10` gives the digit after it, so 43 becomes `"4.3"`.

### 2. Sorting

We sort by the **rounded** average, highest first, because that is the number we print.

If two agents show the same rounded average, the name that comes first in normal string order goes first. For example, Bob (4.33) and Alice (4.25) both show 4.3, so Alice comes before Bob.

Names are case-sensitive, so we use plain `compareTo`. In this order capital letters come before small letters, so `"Zoe"` comes before `"adam"`.

## Solution 1: Store Every Rating (Brute Force)

The most direct idea: save every rating in a list. When a leaderboard is asked for, walk through the whole list, add up the sum and count for each agent, then round and sort.

For a month, we only count ratings whose date starts with that month, because `"2025-03-12"` starts with `"2025-03"`. For the overall board we pass an empty prefix `""`, which every date starts with. This lets one method serve both queries.

- **`Rating`**: one rating event, stored exactly as it came in.
- **`AgentStats`**: the sum and count of one agent's ratings, plus the rounding logic from above.

### Code

```java
import java.util.*;

public class AgentRatingLeaderboard {

    // every rating ever given, stored exactly as it came in
    List<Rating> allRatings = new ArrayList<>();

    public AgentRatingLeaderboard() {
    }

    public void rateAgent(String agentName, int rating, String date) {
        allRatings.add(new Rating(agentName, rating, date));
    }

    public List<String> getAverageRatings() {
        // every date starts with "", so all ratings are counted
        return buildLeaderboard("");
    }

    public List<String> getBestAgentsByMonth(String month) {
        // "2025-03-12" starts with "2025-03", so only that month is counted
        return buildLeaderboard(month);
    }

    /**
     * Scans ALL stored ratings on every call.
     * Only ratings whose date starts with datePrefix are counted.
     */
    List<String> buildLeaderboard(String datePrefix) {
        // agentName -> sum and count of the matching ratings
        Map<String, AgentStats> statsByAgent = new HashMap<>();
        for (Rating r : allRatings) {
            if (r.date.startsWith(datePrefix)) {
                statsByAgent.computeIfAbsent(r.agentName, name -> new AgentStats(name))
                        .addRating(r.rating);
            }
        }

        List<AgentStats> sorted = new ArrayList<>(statsByAgent.values());
        sorted.sort((a, b) -> {
            long avgA = a.getAverageInTenths();
            long avgB = b.getAverageInTenths();
            if (avgA != avgB) {
                return Long.compare(avgB, avgA); // higher average first
            }
            return a.agentName.compareTo(b.agentName); // same average: name in ascending order
        });

        List<String> leaderboard = new ArrayList<>();
        for (AgentStats stats : sorted) {
            leaderboard.add(stats.agentName + "," + stats.getAverageText());
        }
        return leaderboard;
    }
}

/** One rating event, exactly as it was received */
class Rating {
    String agentName;
    int rating;
    String date;

    Rating(String agentName, int rating, String date) {
        this.agentName = agentName;
        this.rating = rating;
        this.date = date;
    }
}

/** Sum and count of an agent's ratings, enough to find the average */
class AgentStats {
    String agentName;
    long ratingSum = 0;
    long ratingCount = 0;

    AgentStats(String agentName) {
        this.agentName = agentName;
    }

    void addRating(int rating) {
        ratingSum += rating;
        ratingCount++;
    }

    /**
     * Average x 10, rounded half up, using whole numbers only.
     * Adding half of the count before dividing turns "round down" into "round to nearest".
     * Example: sum = 13, count = 3 gives (130 + 1) / 3 = 43, which means 4.3
     */
    long getAverageInTenths() {
        return (ratingSum * 10 + ratingCount / 2) / ratingCount;
    }

    /** Average as text with exactly one decimal, like "4.3" or "5.0" */
    String getAverageText() {
        long tenths = getAverageInTenths();
        return (tenths / 10) + "." + (tenths % 10);
    }
}
```

### Problems with this approach

- **Slow reads**: every leaderboard call scans all ratings again. With millions of ratings that is millions of steps per call, even if only one new rating came in since the last call.
- **Wasted work for months**: a monthly query still reads the ratings of every other month, only to skip them.
- **Memory keeps growing**: every rating is stored forever, but in the end we only use its sum and count.

## Solution 2: Keep Running Totals (Optimized)

Look at Solution 1 again. On every call, it rebuilds the same sum and count for each agent from scratch. So why not keep those totals up to date as each rating arrives?

That is the whole improvement. `rateAgent` adds the rating to the totals and forgets it. The two leaderboard methods only sort what is already there.

### Data structures

```
overallStats : agentName -> AgentStats
monthlyStats : month -> (agentName -> AgentStats)
```

- **`AgentStats`**: holds an agent's name, rating sum and rating count. Both leaderboards need exactly the same thing, so the rounding logic is written once. Keeping the name inside lets us sort a plain list of `AgentStats`.
- **`overallStats` (HashMap)**: finds an agent's totals in O(1) whenever a rating comes in.
- **`monthlyStats` (map of maps)**: first find the month, then the agent. A monthly query reads only the agents rated in that month and nothing else.

### How each method works

- **`rateAgent`**: take the month from the date with `date.substring(0, 7)`. Add the rating to the agent's overall totals and to the agent's totals for that month. If an entry does not exist yet, `computeIfAbsent` creates it.
- **`getAverageRatings`**: copy all values of `overallStats` into a list, sort it, and format each entry as `"name,average"`.
- **`getBestAgentsByMonth`**: same steps, using the inner map of that month. If nobody was rated in that month, return an empty list.

### Walkthrough with Example 2

After the five ratings, `monthlyStats` looks like this:

```
"2025-02" -> Alice (sum 5, count 1), Bob (sum 3, count 1), Charlie (sum 4, count 1)
"2025-03" -> Bob (sum 5, count 1), Alice (sum 2, count 1)
```

`getBestAgentsByMonth("2025-02")` reads only the first inner map and returns `["Alice,5.0", "Charlie,4.0", "Bob,3.0"]`. The March ratings are never touched.

### Code

```java
import java.util.*;

public class AgentRatingLeaderboard {

    // agentName -> totals of all ratings of that agent
    Map<String, AgentStats> overallStats = new HashMap<>();

    // month ("YYYY-MM") -> (agentName -> totals of that agent in that month)
    Map<String, Map<String, AgentStats>> monthlyStats = new HashMap<>();

    public AgentRatingLeaderboard() {
    }

    /**
     * O(1): the rating is added to two running totals and then thrown away.
     * computeIfAbsent returns the value for a key, creating and storing it first if missing.
     */
    public void rateAgent(String agentName, int rating, String date) {
        String month = date.substring(0, 7); // "2025-03-12" -> "2025-03"

        // 1. overall totals of this agent
        overallStats.computeIfAbsent(agentName, name -> new AgentStats(name))
                .addRating(rating);

        // 2. totals of this agent for this month
        monthlyStats.computeIfAbsent(month, m -> new HashMap<>())
                .computeIfAbsent(agentName, name -> new AgentStats(name))
                .addRating(rating);
    }

    public List<String> getAverageRatings() {
        return buildLeaderboard(overallStats.values());
    }

    public List<String> getBestAgentsByMonth(String month) {
        Map<String, AgentStats> statsOfMonth = monthlyStats.get(month);
        if (statsOfMonth == null) {
            return new ArrayList<>(); // nobody was rated in this month
        }
        return buildLeaderboard(statsOfMonth.values());
    }

    /**
     * Sorts agents by rounded average (highest first), then by name (ascending),
     * and formats each one as "agentName,average".
     */
    List<String> buildLeaderboard(Collection<AgentStats> agents) {
        List<AgentStats> sorted = new ArrayList<>(agents);
        sorted.sort((a, b) -> {
            long avgA = a.getAverageInTenths();
            long avgB = b.getAverageInTenths();
            if (avgA != avgB) {
                return Long.compare(avgB, avgA); // higher average first
            }
            return a.agentName.compareTo(b.agentName); // same average: name in ascending order
        });

        List<String> leaderboard = new ArrayList<>();
        for (AgentStats stats : sorted) {
            leaderboard.add(stats.agentName + "," + stats.getAverageText());
        }
        return leaderboard;
    }
}

/**
 * Running totals of one agent's ratings.
 * We never keep individual ratings, only their sum and count.
 */
class AgentStats {
    String agentName;
    long ratingSum = 0;
    long ratingCount = 0;

    AgentStats(String agentName) {
        this.agentName = agentName;
    }

    void addRating(int rating) {
        ratingSum += rating;
        ratingCount++;
    }

    /**
     * Average x 10, rounded half up, using whole numbers only.
     * Adding half of the count before dividing turns "round down" into "round to nearest".
     * Example: sum = 13, count = 3 gives (130 + 1) / 3 = 43, which means 4.3
     */
    long getAverageInTenths() {
        return (ratingSum * 10 + ratingCount / 2) / ratingCount;
    }

    /** Average as text with exactly one decimal, like "4.3" or "5.0" */
    String getAverageText() {
        long tenths = getAverageInTenths();
        return (tenths / 10) + "." + (tenths % 10);
    }
}
```

## Complexity

Here R is the total number of ratings, A is the number of agents and M is the number of agents rated in the asked month.

| Operation | Solution 1 (brute force) | Solution 2 (running totals) |
|---|---|---|
| `rateAgent` | O(1) | O(1) |
| `getAverageRatings` | O(R + A log A) | O(A log A) |
| `getBestAgentsByMonth` | O(R + M log M) | O(M log M) |
| Memory | O(R) | O(A + number of (agent, month) pairs) |

In Solution 2 the cost of a query no longer depends on R. No matter how many ratings came in, a query only sorts the agents.

**Can we skip sorting on every call?** The answer itself has A lines, so any query takes at least O(A) time, and sorting adds only a small log A factor. If queries were far more frequent than ratings, we could keep agents in a sorted set like `TreeSet` and remove and re-add an agent on every rating. That makes every rating slower and the code harder to follow, so sorting on demand is the better choice here.