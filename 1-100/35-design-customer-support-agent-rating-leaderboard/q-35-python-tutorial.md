# Design a Customer Support Agent Rating Leaderboard in Python

#### Problem Statement
[https://codezym.com/question/35-design-customer-support-agent-rating-leaderboard](https://codezym.com/question/35-design-customer-support-agent-rating-leaderboard)

The core idea is simple: **do the math when a rating comes in, not when someone asks for the leaderboard.**

To find an average we never need the individual ratings. We only need two numbers per agent: the **sum** of the ratings and the **count** of ratings. So we keep these two numbers for every agent overall, and once more for every agent in every month. When a leaderboard is asked for, we turn each sum and count into a rounded average and sort.

No design pattern is needed here. This is a data structure problem, and patterns like Observer or Strategy would only add extra classes without making the code faster or easier to follow. One small helper class and two dictionaries are enough.

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

- **Observer**: every new rating could notify two listeners, an overall board and a monthly board. But both boards live in the same class and are always updated together. Listener classes would only hide two simple dictionary updates.
- **Strategy**: we could plug in different rules for ranking or rounding. But the problem has exactly one rule for each, and a separate strategy class with only one version is just extra code.

So we keep the design plain: one small `AgentStats` class and two dictionaries. If more board types (like weekly or yearly) are added later, Observer would start to pay off.

## Two Small Details That Matter

### 1. Rounding to one decimal

Averages are rounded to one decimal, and a 5 in the second decimal rounds up. So 4.33 becomes 4.3, while 4.35 and 4.37 become 4.4.

Python's built-in `round()` does not do this:

- `round(4.25, 1)` gives 4.2, because Python rounds an exact tie to the nearest even digit.
- `round(4.35, 1)` gives 4.3, because a float cannot store 4.35 exactly. It is really stored as 4.34999999999999964...

Formatting with `f"{x:.1f}"` has the same two problems.

To stay safe, we round using whole numbers only, which are always exact:

```
average_in_tenths = (sum * 10 + count // 2) // count
```

`//` always rounds down. Adding half of `count` before dividing pushes anything at .5 or above up to the next number. The result is the average in tenths, so 43 means 4.3.

| Ratings | Exact average | `(sum * 10 + count // 2) // count` | Result |
|---|---|---|---|
| 5, 3 | 4.0 | (80 + 1) // 2 = 40 | 4.0 |
| 5, 4, 4 | 4.333... | (130 + 1) // 3 = 43 | 4.3 |
| 5, 4, 4, 4 | 4.25 | (170 + 2) // 4 = 43 | 4.3 |
| 7 fives, 13 fours | 4.35 | (870 + 10) // 20 = 44 | 4.4 |

To print it, `tenths // 10` gives the part before the dot and `tenths % 10` gives the digit after it, so 43 becomes `"4.3"`.

### 2. Sorting

We sort by the **rounded** average, highest first, because that is the number we print.

If two agents show the same rounded average, the name that comes first in normal string order goes first. For example, Bob (4.33) and Alice (4.25) both show 4.3, so Alice comes before Bob.

One `sorted` call handles both rules with the key `(-average_in_tenths, agent_name)`. The minus sign puts higher averages first, and the name breaks ties in ascending order.

Names are case-sensitive, so we compare them as they are. In this order capital letters come before small letters, so `"Zoe"` comes before `"adam"`.

## Solution 1: Store Every Rating (Brute Force)

The most direct idea: save every rating in a list. When a leaderboard is asked for, walk through the whole list, add up the sum and count for each agent, then round and sort.

For a month, we only count ratings whose date starts with that month, because `"2025-03-12"` starts with `"2025-03"`. For the overall board we pass an empty prefix `""`, which every date starts with. This lets one method serve both queries.

- **`Rating`**: one rating event, stored exactly as it came in.
- **`AgentStats`**: the sum and count of one agent's ratings, plus the rounding logic from above.

### Code

```python
class Rating:
    """One rating event, exactly as it was received."""

    def __init__(self, agent_name, rating, date):
        self.agent_name = agent_name
        self.rating = rating
        self.date = date


class AgentStats:
    """Sum and count of an agent's ratings, enough to find the average."""

    def __init__(self, agent_name):
        self.agent_name = agent_name
        self.rating_sum = 0
        self.rating_count = 0

    def add_rating(self, rating):
        self.rating_sum += rating
        self.rating_count += 1

    def average_in_tenths(self):
        """
        Average x 10, rounded half up, using whole numbers only.
        Adding half of the count before dividing turns "round down" into "round to nearest".
        Example: sum = 13, count = 3 gives (130 + 1) // 3 = 43, which means 4.3
        """
        return (self.rating_sum * 10 + self.rating_count // 2) // self.rating_count

    def average_text(self):
        """Average as text with exactly one decimal, like "4.3" or "5.0"."""
        tenths = self.average_in_tenths()
        return f"{tenths // 10}.{tenths % 10}"


class AgentRatingLeaderboard:
    def __init__(self):
        # every rating ever given, stored exactly as it came in
        self.all_ratings = []

    def rateAgent(self, agentName, rating, date):
        self.all_ratings.append(Rating(agentName, rating, date))

    def getAverageRatings(self):
        # every date starts with "", so all ratings are counted
        return self.build_leaderboard("")

    def getBestAgentsByMonth(self, month):
        # "2025-03-12" starts with "2025-03", so only that month is counted
        return self.build_leaderboard(month)

    def build_leaderboard(self, date_prefix):
        """
        Scans ALL stored ratings on every call.
        Only ratings whose date starts with date_prefix are counted.
        """
        # agentName -> sum and count of the matching ratings
        stats_by_agent = {}
        for r in self.all_ratings:
            if r.date.startswith(date_prefix):
                if r.agent_name not in stats_by_agent:
                    stats_by_agent[r.agent_name] = AgentStats(r.agent_name)
                stats_by_agent[r.agent_name].add_rating(r.rating)

        # higher average first, same average: name in ascending order
        ordered = sorted(stats_by_agent.values(),
                         key=lambda s: (-s.average_in_tenths(), s.agent_name))
        return [f"{s.agent_name},{s.average_text()}" for s in ordered]
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
overall_stats : agentName -> AgentStats
monthly_stats : month -> {agentName -> AgentStats}
```

- **`AgentStats`**: holds an agent's name, rating sum and rating count. Both leaderboards need exactly the same thing, so the rounding logic is written once. Keeping the name inside lets us sort a plain list of `AgentStats`.
- **`overall_stats` (dict)**: finds an agent's totals in O(1) whenever a rating comes in.
- **`monthly_stats` (dict of dicts)**: first find the month, then the agent. A monthly query reads only the agents rated in that month and nothing else.

### How each method works

- **`rateAgent`**: take the month from the date with `date[:7]`. Add the rating to the agent's overall totals and to the agent's totals for that month. The helper `get_stats` creates an entry the first time an agent shows up.
- **`getAverageRatings`**: sort all values of `overall_stats` and format each entry as `"name,average"`.
- **`getBestAgentsByMonth`**: same steps, using the inner dict of that month. If nobody was rated in that month, `.get(month, {})` gives an empty dict, so the result is an empty list.

### Walkthrough with Example 2

After the five ratings, `monthly_stats` looks like this:

```
"2025-02" -> Alice (sum 5, count 1), Bob (sum 3, count 1), Charlie (sum 4, count 1)
"2025-03" -> Bob (sum 5, count 1), Alice (sum 2, count 1)
```

`getBestAgentsByMonth("2025-02")` reads only the first inner dict and returns `["Alice,5.0", "Charlie,4.0", "Bob,3.0"]`. The March ratings are never touched.

### Code

```python
class AgentStats:
    """
    Running totals of one agent's ratings.
    We never keep individual ratings, only their sum and count.
    """

    def __init__(self, agent_name):
        self.agent_name = agent_name
        self.rating_sum = 0
        self.rating_count = 0

    def add_rating(self, rating):
        self.rating_sum += rating
        self.rating_count += 1

    def average_in_tenths(self):
        """
        Average x 10, rounded half up, using whole numbers only.
        Adding half of the count before dividing turns "round down" into "round to nearest".
        Example: sum = 13, count = 3 gives (130 + 1) // 3 = 43, which means 4.3
        """
        return (self.rating_sum * 10 + self.rating_count // 2) // self.rating_count

    def average_text(self):
        """Average as text with exactly one decimal, like "4.3" or "5.0"."""
        tenths = self.average_in_tenths()
        return f"{tenths // 10}.{tenths % 10}"


class AgentRatingLeaderboard:
    def __init__(self):
        # agentName -> totals of all ratings of that agent
        self.overall_stats = {}
        # month ("YYYY-MM") -> {agentName -> totals of that agent in that month}
        self.monthly_stats = {}

    def rateAgent(self, agentName, rating, date):
        """O(1): the rating is added to two running totals and then thrown away."""
        month = date[:7]  # "2025-03-12" -> "2025-03"
        if month not in self.monthly_stats:
            self.monthly_stats[month] = {}

        self.get_stats(self.overall_stats, agentName).add_rating(rating)
        self.get_stats(self.monthly_stats[month], agentName).add_rating(rating)

    def getAverageRatings(self):
        return self.build_leaderboard(self.overall_stats.values())

    def getBestAgentsByMonth(self, month):
        # a month nobody was rated in gives an empty leaderboard
        stats_of_month = self.monthly_stats.get(month, {})
        return self.build_leaderboard(stats_of_month.values())

    def get_stats(self, stats_by_agent, agent_name):
        """Returns the agent's stats from the given dict, creating them on first use."""
        if agent_name not in stats_by_agent:
            stats_by_agent[agent_name] = AgentStats(agent_name)
        return stats_by_agent[agent_name]

    def build_leaderboard(self, agents):
        """
        Sorts agents by rounded average (highest first), then by name (ascending),
        and formats each one as "agentName,average".
        """
        ordered = sorted(agents, key=lambda s: (-s.average_in_tenths(), s.agent_name))
        return [f"{s.agent_name},{s.average_text()}" for s in ordered]
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

**Can we skip sorting on every call?** The answer itself has A lines, so any query takes at least O(A) time, and sorting adds only a small log A factor. If queries were far more frequent than ratings, we could keep agents in an always-sorted structure and move an agent to its new place on every rating. That makes every rating slower and the code harder to follow, so sorting on demand is the better choice here.