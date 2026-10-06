# Design a Customer Support Agent Rating Leaderboard in Python

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

Python's `round()` is not safe here, for two reasons:

- On an exact tie it rounds to the even digit, so `round(4.25, 1)` gives 4.2, not 4.3.
- A float cannot store 4.35 exactly. It is really stored as 4.3499999999999996..., so `round(4.35, 1)` gives 4.3, not 4.4.

To stay safe, we round using whole numbers only, which are always exact:

```
average_in_tenths = (sum * 10 + count // 2) // count
```

`//` is integer division, which always rounds down. Adding half of `count` before dividing pushes anything at .5 or above up to the next number.

The result is the average in tenths, so 43 means 4.3.

| Ratings | Exact average | `(sum * 10 + count // 2) // count` | Result |
|---|---|---|---|
| 5, 3 | 4.0 | (80 + 1) // 2 = 40 | 4.0 |
| 5, 4, 4 | 4.333... | (130 + 1) // 3 = 43 | 4.3 |
| 5, 4, 4, 4 | 4.25 | (170 + 2) // 4 = 43 | 4.3 |
| 7 fives, 13 fours | 4.35 | (870 + 10) // 20 = 44 | 4.4 |

To print it, `tenths // 10` gives the part before the dot and `tenths % 10` gives the digit after it. So 43 becomes `"4.3"`.

### 2. Sorting

We sort by the **rounded** average, highest first, because that is the number we print.

If two agents show the same rounded average, the name that comes first lexicographically goes first. For example, Bob (4.33) and Alice (4.25) both show 4.3, so Alice comes before Bob.

Names are case-sensitive, so we compare them exactly as they are. In this order capital letters come before small letters, so `"Zoe"` comes before `"adam"`.

Both rules fit in one sort key: `(-average_in_tenths, name)`. Python compares tuples item by item, and the minus sign puts higher averages first.

## Solution: Keep Running Totals

`rateAgent` adds the rating to the agent's running totals and then forgets it. The leaderboard methods only sort what is already there.

No design pattern is needed for these three methods. One small helper class and two dictionaries are enough.

### Data structures

```
overall_stats : agent name -> AgentStats
monthly_stats : month -> (agent name -> AgentStats)
```

- **`AgentStats`**: an agent's rating sum and rating count, plus the rounding logic.
- **`overall_stats`**: finds an agent's totals in O(1) whenever a rating comes in.
- **`monthly_stats`**: first find the month, then the agent. A monthly query reads only the agents rated in that month.

Both are `defaultdict`s, which create a missing entry the first time a key is used. So we never check whether an agent or a month already exists.

### How each method works

- **`rateAgent`**: get the month with `date[:7]`. Add the rating to the agent's overall totals and to the agent's totals for that month.
- **`getAverageRatings`**: sort the agents in `overall_stats` and format each one as `"name,average"`.
- **`getBestAgentsByMonth`**: same steps, using the agents of that month. If nobody was rated in that month, `.get(month, {})` gives an empty dictionary, so the result is an empty list.

### Walkthrough with Example 2

After the five ratings, `monthly_stats` looks like this:

```
"2025-02" -> Alice (sum 5, count 1), Bob (sum 3, count 1), Charlie (sum 4, count 1)
"2025-03" -> Bob (sum 5, count 1), Alice (sum 2, count 1)
```

`getBestAgentsByMonth("2025-02")` reads only the first inner dictionary and returns `["Alice,5.0", "Charlie,4.0", "Bob,3.0"]`. The March ratings are never touched.

### Code

```python
from collections import defaultdict


class AgentRatingLeaderboard:
    def __init__(self):
        # agent name -> totals of all ratings of that agent
        self.overall_stats = defaultdict(AgentStats)

        # month ("YYYY-MM") -> (agent name -> totals of that agent in that month)
        # a new month starts with its own empty defaultdict of agents
        self.monthly_stats = defaultdict(lambda: defaultdict(AgentStats))

    def rateAgent(self, agentName, rating, date):
        """O(1): the rating is added to two running totals and then thrown away."""
        month = date[:7]  # "2025-03-12" -> "2025-03"

        # 1. overall totals of this agent
        self.overall_stats[agentName].add_rating(rating)

        # 2. totals of this agent for this month
        self.monthly_stats[month][agentName].add_rating(rating)

    def getAverageRatings(self):
        return self.build_leaderboard(self.overall_stats)

    def getBestAgentsByMonth(self, month):
        # .get() does not create a new entry, an unknown month gives an empty leaderboard
        return self.build_leaderboard(self.monthly_stats.get(month, {}))

    def build_leaderboard(self, stats_by_agent):
        """
        Sorts agents by rounded average (highest first), then by name (ascending),
        and formats each one as "agentName,average".
        """
        # the minus sign puts higher averages first, equal averages fall back to the name
        names = sorted(
            stats_by_agent.keys(),
            key=lambda name: (-stats_by_agent[name].get_average_in_tenths(), name)
        )

        leaderboard = []
        for name in names:
            average_text = stats_by_agent[name].get_average_text()
            leaderboard.append(f"{name},{average_text}")
        return leaderboard


class AgentStats:
    """
    Running totals of one agent's ratings.
    We never keep individual ratings, only their sum and count.
    """

    def __init__(self):
        self.rating_sum = 0
        self.rating_count = 0

    def add_rating(self, rating):
        self.rating_sum += rating
        self.rating_count += 1

    def get_average_in_tenths(self):
        """
        Average x 10, rounded half up, using whole numbers only.
        Example: sum = 13, count = 3 gives (130 + 1) // 3 = 43, which means 4.3
        """
        return (self.rating_sum * 10 + self.rating_count // 2) // self.rating_count

    def get_average_text(self):
        """Average as text with exactly one decimal, like 4.3 or 5.0"""
        tenths = self.get_average_in_tenths()
        return f"{tenths // 10}.{tenths % 10}"
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

**Can we skip sorting on every call?** The answer itself has A lines, so a query is at least O(A) anyway. Keeping agents always in sorted order would slow down every rating and make the code harder to follow.

## Follow Up: More Leaderboards with the Observer Pattern

Now suppose we need two more leaderboards:

- `getBestAgentsByYear(year)`: best agents of a year like `"2026"`. Passing the current year shows the best agents of this year.
- `getBestAgentsInLast7Days(today)`: best agents over today and the 6 days before it.

### Why not just add more dictionaries?

We could add a `yearly_stats` and a `daily_stats` dictionary, and update both inside `rateAgent`. That works for now.

But every future leaderboard would again add a dictionary and edit `rateAgent`. Soon `rateAgent` becomes a long list of unrelated updates, and each new feature risks breaking code that already works.

### The Observer pattern

In the Observer pattern, one object (the subject) keeps a list of observers and notifies all of them whenever something happens.

Here the subject is `AgentRatingLeaderboard`, the event is a new rating, and every leaderboard is an observer.

- **`RatingObserver`**: an abstract class with one method, `on_rating(agent_name, rating, date)`. Python has no `interface` keyword, so we use `ABC` and `@abstractmethod`.
- **Boards**: every leaderboard extends `RatingObserver` and keeps its own totals in its own way.
- **`rateAgent`**: passes the new rating to every board in the `observers` list.

```
rateAgent("Alice", 5, "2025-03-12")
  -> overall_board.on_rating(...)      adds to Alice's overall totals
  -> monthly_board.on_rating(...)      adds to Alice's totals in "2025-03"
  -> yearly_board.on_rating(...)       adds to Alice's totals in "2025"
  -> last_7_days_board.on_rating(...)  adds to Alice's totals on "2025-03-12"
```

Adding a leaderboard is now one new class plus one entry in `observers`, and `rateAgent` never changes. This is the Open/Closed principle: we add new features without modifying working code.

Sorting and formatting stay in `build_leaderboard`, so every leaderboard follows the same rules.

### The boards

- **`OverallBoard`**: `agent name -> AgentStats`, same as `overall_stats` before.
- **`PeriodBoard`**: `period -> (agent name -> AgentStats)`. The period is the first few characters of the date: `PeriodBoard(7)` groups by month (`"2025-03"`) and `PeriodBoard(4)` groups by year (`"2025"`).
- **`LastDaysBoard`**: keeps totals per day and adds up the days of the window when asked.

The monthly and yearly leaderboards are the same class. So "this year" needed no new class at all.

### Why last 7 days is different

A month or a year is a fixed bucket. Once a rating lands in March, it stays in March, so one running total per bucket works.

Last 7 days is a moving window. Every day, the oldest day leaves the window and a new one joins it. A single running total cannot remove the ratings of the day that left.

So `LastDaysBoard` keeps totals per day, using a `PeriodBoard` with the whole date as the period. A query adds up each agent's daily totals over the 7 days of the window.

That is at most 7 small dictionaries, no matter how many ratings came in.

Our system has no clock. Every date comes from the caller, so the caller also passes `today`.

To step back one day at a time, we subtract a `timedelta` from a `datetime.date`, which handles month and year changes. For example, one day before `"2024-03-01"` is `"2024-02-29"`.

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

```python
import datetime
from abc import ABC, abstractmethod
from collections import defaultdict


class AgentRatingLeaderboard:
    def __init__(self):
        # each board keeps its own totals and answers its own query
        self.overall_board = OverallBoard()
        self.monthly_board = PeriodBoard(7)        # "2025-03-12" -> month "2025-03"
        self.yearly_board = PeriodBoard(4)         # "2025-03-12" -> year "2025"
        self.last_7_days_board = LastDaysBoard(7)  # today and the 6 days before it

        # every board that wants to hear about new ratings
        self.observers = [
            self.overall_board,
            self.monthly_board,
            self.yearly_board,
            self.last_7_days_board
        ]

    def rateAgent(self, agentName, rating, date):
        """
        Passes the new rating to every board.
        Adding a new board never changes this method.
        """
        for observer in self.observers:
            observer.on_rating(agentName, rating, date)

    def getAverageRatings(self):
        return self.build_leaderboard(self.overall_board.get_stats())

    def getBestAgentsByMonth(self, month):
        return self.build_leaderboard(self.monthly_board.get_stats(month))

    def getBestAgentsByYear(self, year):
        """year is "YYYY". Pass the current year to get the best agents of this year."""
        return self.build_leaderboard(self.yearly_board.get_stats(year))

    def getBestAgentsInLast7Days(self, today):
        """today is "YYYY-MM-DD". Counts ratings from today and the 6 days before it."""
        return self.build_leaderboard(self.last_7_days_board.get_stats(today))

    def build_leaderboard(self, stats_by_agent):
        """
        Sorts agents by rounded average (highest first), then by name (ascending),
        and formats each one as "agentName,average". Shared by every leaderboard.
        """
        # the minus sign puts higher averages first, equal averages fall back to the name
        names = sorted(
            stats_by_agent.keys(),
            key=lambda name: (-stats_by_agent[name].get_average_in_tenths(), name)
        )

        leaderboard = []
        for name in names:
            average_text = stats_by_agent[name].get_average_text()
            leaderboard.append(f"{name},{average_text}")
        return leaderboard


class RatingObserver(ABC):
    """Every board extends this to hear about each new rating"""

    @abstractmethod
    def on_rating(self, agent_name, rating, date):
        pass


class OverallBoard(RatingObserver):
    """Totals of each agent over all time"""

    def __init__(self):
        # agent name -> totals of all ratings of that agent
        self.stats_by_agent = defaultdict(AgentStats)

    def on_rating(self, agent_name, rating, date):
        self.stats_by_agent[agent_name].add_rating(rating)

    def get_stats(self):
        return self.stats_by_agent


class PeriodBoard(RatingObserver):
    """
    Totals of each agent in fixed periods, like months or years.
    The period is the first period_length characters of the date.
    """

    def __init__(self, period_length):
        # 4 -> year "2025", 7 -> month "2025-03", 10 -> day "2025-03-12"
        self.period_length = period_length

        # period -> (agent name -> totals of that agent in that period)
        self.stats_by_period = defaultdict(lambda: defaultdict(AgentStats))

    def on_rating(self, agent_name, rating, date):
        period = date[:self.period_length]
        self.stats_by_period[period][agent_name].add_rating(rating)

    def get_stats(self, period):
        # .get() does not create a new entry, an unknown period gives an empty dictionary
        return self.stats_by_period.get(period, {})


class LastDaysBoard(RatingObserver):
    """
    Totals of each agent over the last N days.
    The window moves every day, so we keep totals per day
    and add up the days of the window when asked.
    """

    def __init__(self, number_of_days):
        self.number_of_days = number_of_days

        # a day is also a period: the whole date "2025-03-12"
        self.daily_board = PeriodBoard(10)

    def on_rating(self, agent_name, rating, date):
        self.daily_board.on_rating(agent_name, rating, date)

    def get_stats(self, today):
        """
        Adds up each agent's daily totals over the window:
        today and the (number_of_days - 1) days before it.
        """
        window_stats = defaultdict(AgentStats)
        today_date = datetime.date.fromisoformat(today)

        for days_back in range(self.number_of_days):
            # date arithmetic handles month and year changes
            day = today_date - datetime.timedelta(days=days_back)
            stats_of_day = self.daily_board.get_stats(day.isoformat())  # "YYYY-MM-DD"
            for agent_name, stats in stats_of_day.items():
                window_stats[agent_name].add_stats(stats)

        return window_stats


class AgentStats:
    """
    Running totals of one agent's ratings.
    We never keep individual ratings, only their sum and count.
    """

    def __init__(self):
        self.rating_sum = 0
        self.rating_count = 0

    def add_rating(self, rating):
        self.rating_sum += rating
        self.rating_count += 1

    def add_stats(self, other):
        """
        New: adds the totals of the same agent from another day.
        Used by LastDaysBoard.
        """
        self.rating_sum += other.rating_sum
        self.rating_count += other.rating_count

    def get_average_in_tenths(self):
        """
        Average x 10, rounded half up, using whole numbers only.
        Example: sum = 13, count = 3 gives (130 + 1) // 3 = 43, which means 4.3
        """
        return (self.rating_sum * 10 + self.rating_count // 2) // self.rating_count

    def get_average_text(self):
        """Average as text with exactly one decimal, like 4.3 or 5.0"""
        tenths = self.get_average_in_tenths()
        return f"{tenths // 10}.{tenths % 10}"
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