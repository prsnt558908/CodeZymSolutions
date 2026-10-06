# Design a Customer Support Agent Rating Leaderboard in Java

#### Problem Statement
[https://codezym.com/question/35-design-customer-support-agent-rating-leaderboard](https://codezym.com/question/35-design-customer-support-agent-rating-leaderboard)

The core idea is simple: **do the math when a rating comes in, not when someone asks for the leaderboard.**

To find an average, we never need the individual ratings. We only need two numbers per agent: the **sum** of the ratings and the **count** of ratings.

So we keep these two numbers for every agent overall, and once more for every agent in every month. A leaderboard query only turns each sum and count into a rounded average and sorts.

At the end, we add more leaderboards like **this year** and **last 7 days**, using the **Observer pattern** so that adding them never changes `rateAgent`.

## Understanding the Problem

We need three methods:

- `rateAgent(agentName, rating, date)`: records one rating (1 to 5) for an agent on a date.
- `getAverageRatings()`: returns every agent with their overall average, best first.
- `getBestAgentsByMonth(month)`: same as above, but only ratings from that month count.

Each entry in the result looks like `"Bob,4.5"`.

The system should handle thousands of agents and millions of ratings, so the speed of each method matters.

## Two Small Details That Matter

### 1. Rounding to one decimal

Averages are rounded to one decimal, and a 5 in the second decimal rounds up. So 4.33 becomes 4.3, while 4.35 and 4.37 become 4.4.

A `double` cannot store 4.35 exactly. It is really stored as 4.3499999999999996..., so `new BigDecimal(4.35).setScale(1, RoundingMode.HALF_UP)` gives 4.3, not 4.4.

To stay safe, we round using whole numbers only, which are always exact:

```
averageInTenths = (sum * 10 + count / 2) / count
```

Integer division always rounds down. Adding half of `count` before dividing pushes anything at .5 or above up to the next number.

The result is the average in tenths, so 43 means 4.3.

| Ratings | Exact average | `(sum * 10 + count / 2) / count` | Result |
|---|---|---|---|
| 5, 3 | 4.0 | (80 + 1) / 2 = 40 | 4.0 |
| 5, 4, 4 | 4.333... | (130 + 1) / 3 = 43 | 4.3 |
| 5, 4, 4, 4 | 4.25 | (170 + 2) / 4 = 43 | 4.3 |
| 7 fives, 13 fours | 4.35 | (870 + 10) / 20 = 44 | 4.4 |

To print it, `tenths / 10` gives the part before the dot and `tenths % 10` gives the digit after it. So 43 becomes `"4.3"`.

### 2. Sorting

We sort by the **rounded** average, highest first, because that is the number we print.

If two agents show the same rounded average, the name that comes first lexicographically goes first. For example, Bob (4.33) and Alice (4.25) both show 4.3, so Alice comes before Bob.

Names are case-sensitive, so we use plain `compareTo`. In this order capital letters come before small letters, so `"Zoe"` comes before `"adam"`.

## Solution: Keep Running Totals

`rateAgent` adds the rating to the agent's running totals and then forgets it. The leaderboard methods only sort what is already there.

No design pattern is needed for these three methods. One small helper class and two hash maps are enough.

### Data structures

```
overallStats : agentName -> AgentStats
monthlyStats : month -> (agentName -> AgentStats)
```

- **`AgentStats`**: an agent's name, rating sum and rating count, plus the rounding logic.
- **`overallStats`**: finds an agent's totals in O(1) whenever a rating comes in.
- **`monthlyStats`**: first find the month, then the agent. A monthly query reads only the agents rated in that month.

### How each method works

- **`rateAgent`**: get the month with `date.substring(0, 7)`. Add the rating to the agent's overall totals and to the agent's totals for that month.
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

### Complexity

Here A is the number of agents and M is the number of agents rated in the asked month.

| Operation | Time |
|---|---|
| `rateAgent` | O(1) |
| `getAverageRatings` | O(A log A) |
| `getBestAgentsByMonth` | O(M log M) |
| Memory | O(A + number of (agent, month) pairs) |

A query never looks at individual ratings. No matter how many ratings came in, it only sorts the agents.

**Can we skip sorting on every call?** The answer itself has A lines, so a query is at least O(A) anyway. Keeping agents always sorted in a `TreeSet` would slow down every rating and make the code harder to follow.

## Follow Up: More Leaderboards with the Observer Pattern

Now suppose we need two more leaderboards:

- `getBestAgentsByYear(year)`: best agents of a year like `"2026"`. Passing the current year shows the best agents of this year.
- `getBestAgentsInLast7Days(today)`: best agents over today and the 6 days before it.

### Why not just add more maps?

We could add a `yearlyStats` map and a `dailyStats` map, and update both inside `rateAgent`. That works for now.

But every future leaderboard would again add a map and edit `rateAgent`. Soon `rateAgent` becomes a long list of unrelated updates, and each new feature risks breaking code that already works.

### The Observer pattern

In the Observer pattern, one object (the subject) keeps a list of observers and notifies all of them whenever something happens.

Here the subject is `AgentRatingLeaderboard`, the event is a new rating, and every leaderboard is an observer.

- **`RatingObserver`**: an interface with one method, `onRating(agentName, rating, date)`.
- **Boards**: every leaderboard implements `RatingObserver` and keeps its own totals in its own way.
- **`rateAgent`**: passes the new rating to every board in the `observers` list.

```
rateAgent("Alice", 5, "2025-03-12")
  -> overallBoard.onRating(...)     adds to Alice's overall totals
  -> monthlyBoard.onRating(...)     adds to Alice's totals in "2025-03"
  -> yearlyBoard.onRating(...)      adds to Alice's totals in "2025"
  -> last7DaysBoard.onRating(...)   adds to Alice's totals on "2025-03-12"
```

Adding a leaderboard is now one new class plus one entry in `observers`, and `rateAgent` never changes. This is the Open/Closed principle: we add new features without modifying working code.

Sorting and formatting stay in `buildLeaderboard`, so every leaderboard follows the same rules.

### The boards

- **`OverallBoard`**: `agentName -> AgentStats`, same as `overallStats` before.
- **`PeriodBoard`**: `period -> (agentName -> AgentStats)`. The period is the first few characters of the date: `new PeriodBoard(7)` groups by month (`"2025-03"`) and `new PeriodBoard(4)` groups by year (`"2025"`).
- **`LastDaysBoard`**: keeps totals per day and adds up the days of the window when asked.

The monthly and yearly leaderboards are the same class. So "this year" needed no new class at all.

### Why last 7 days is different

A month or a year is a fixed bucket. Once a rating lands in March, it stays in March, so one running total per bucket works.

Last 7 days is a moving window. Every day, the oldest day leaves the window and a new one joins it. A single running total cannot remove the ratings of the day that left.

So `LastDaysBoard` keeps totals per day, using a `PeriodBoard` with the whole date as the period. A query adds up each agent's daily totals over the 7 days of the window.

That is at most 7 small maps, no matter how many ratings came in.

Our system has no clock. Every date comes from the caller, so the caller also passes `today`.

To step back one day at a time, we use `LocalDate`, which handles month and year changes. For example, one day before `"2024-03-01"` is `"2024-02-29"`.

```
rateAgent("Alice", 5, "2025-03-01")
rateAgent("Alice", 5, "2025-03-02")
rateAgent("Bob", 4, "2025-03-05")
rateAgent("Alice", 3, "2025-03-07")

getAverageRatings()                    -> ["Alice,4.3", "Bob,4.0"]
getBestAgentsInLast7Days("2025-03-09") -> ["Bob,4.0", "Alice,3.0"]
```

The window of `"2025-03-09"` is March 3 to March 9. Alice's two 5s fall outside it, so only her 3 counts.

### Code

```java
import java.time.LocalDate;
import java.util.*;

public class AgentRatingLeaderboard {

    // each board keeps its own totals and answers its own query
    OverallBoard overallBoard = new OverallBoard();
    PeriodBoard monthlyBoard = new PeriodBoard(7);        // "2025-03-12" -> month "2025-03"
    PeriodBoard yearlyBoard = new PeriodBoard(4);         // "2025-03-12" -> year "2025"
    LastDaysBoard last7DaysBoard = new LastDaysBoard(7);  // today and the 6 days before it

    // every board that wants to hear about new ratings
    List<RatingObserver> observers = List.of(overallBoard, monthlyBoard, yearlyBoard, last7DaysBoard);

    public AgentRatingLeaderboard() {
    }

    /** Passes the new rating to every board. Adding a new board never changes this method. */
    public void rateAgent(String agentName, int rating, String date) {
        for (RatingObserver observer : observers) {
            observer.onRating(agentName, rating, date);
        }
    }

    public List<String> getAverageRatings() {
        return buildLeaderboard(overallBoard.getStats());
    }

    public List<String> getBestAgentsByMonth(String month) {
        return buildLeaderboard(monthlyBoard.getStats(month));
    }

    /** year is "YYYY". Pass the current year to get the best agents of this year. */
    public List<String> getBestAgentsByYear(String year) {
        return buildLeaderboard(yearlyBoard.getStats(year));
    }

    /** today is "YYYY-MM-DD". Counts ratings from today and the 6 days before it. */
    public List<String> getBestAgentsInLast7Days(String today) {
        return buildLeaderboard(last7DaysBoard.getStats(today));
    }

    /**
     * Sorts agents by rounded average (highest first), then by name (ascending),
     * and formats each one as "agentName,average". Shared by every leaderboard.
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

/** Every board implements this to hear about each new rating */
interface RatingObserver {
    void onRating(String agentName, int rating, String date);
}

/** Totals of each agent over all time */
class OverallBoard implements RatingObserver {
    // agentName -> totals of all ratings of that agent
    Map<String, AgentStats> statsByAgent = new HashMap<>();

    public void onRating(String agentName, int rating, String date) {
        statsByAgent.computeIfAbsent(agentName, name -> new AgentStats(name))
                .addRating(rating);
    }

    Collection<AgentStats> getStats() {
        return statsByAgent.values();
    }
}

/**
 * Totals of each agent in fixed periods, like months or years.
 * The period is the first periodLength characters of the date.
 */
class PeriodBoard implements RatingObserver {
    int periodLength; // 4 -> year "2025", 7 -> month "2025-03", 10 -> day "2025-03-12"

    // period -> (agentName -> totals of that agent in that period)
    Map<String, Map<String, AgentStats>> statsByPeriod = new HashMap<>();

    PeriodBoard(int periodLength) {
        this.periodLength = periodLength;
    }

    public void onRating(String agentName, int rating, String date) {
        String period = date.substring(0, periodLength);
        statsByPeriod.computeIfAbsent(period, p -> new HashMap<>())
                .computeIfAbsent(agentName, name -> new AgentStats(name))
                .addRating(rating);
    }

    Collection<AgentStats> getStats(String period) {
        // an empty map if nobody was rated in this period
        return statsByPeriod.getOrDefault(period, new HashMap<>()).values();
    }
}

/**
 * Totals of each agent over the last N days.
 * The window moves every day, so we keep totals per day
 * and add up the days of the window when asked.
 */
class LastDaysBoard implements RatingObserver {
    int numberOfDays;

    // a day is also a period: the whole date "2025-03-12"
    PeriodBoard dailyBoard = new PeriodBoard(10);

    LastDaysBoard(int numberOfDays) {
        this.numberOfDays = numberOfDays;
    }

    public void onRating(String agentName, int rating, String date) {
        dailyBoard.onRating(agentName, rating, date);
    }

    /** Adds up each agent's daily totals, from today back to (numberOfDays - 1) days ago */
    Collection<AgentStats> getStats(String today) {
        Map<String, AgentStats> windowStats = new HashMap<>();
        LocalDate day = LocalDate.parse(today);
        for (int i = 0; i < numberOfDays; i++) {
            for (AgentStats dayStats : dailyBoard.getStats(day.toString())) {
                windowStats.computeIfAbsent(dayStats.agentName, name -> new AgentStats(name))
                        .addStats(dayStats);
            }
            day = day.minusDays(1); // LocalDate handles month and year changes
        }
        return windowStats.values();
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

    /** New: adds the totals of the same agent from another day, used by LastDaysBoard */
    void addStats(AgentStats other) {
        ratingSum += other.ratingSum;
        ratingCount += other.ratingCount;
    }

    /**
     * Average x 10, rounded half up, using whole numbers only.
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

### Complexity

Here B is the number of boards, Y is the number of agents rated in the asked year, and W is the number of agents rated in the 7 day window.

| Operation | Time |
|---|---|
| `rateAgent` | O(B), and B is a small constant |
| `getBestAgentsByYear` | O(Y log Y) |
| `getBestAgentsInLast7Days` | O(7W + W log W) |

The other two queries cost the same as before.

Daily totals keep growing over time. If `today` only moves forward, days older than the window can be deleted to save memory.