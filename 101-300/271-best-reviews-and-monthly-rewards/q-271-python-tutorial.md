# Best Reviews and Monthly Rewards in Python

#### Problem Statement
[https://codezym.com/question/271-best-reviews-and-monthly-rewards](https://codezym.com/question/271-best-reviews-and-monthly-rewards)


## Core Idea


This looks like a design problem, but it does not need any design pattern. The hard part is ranking. Ratings keep changing, and at any moment we must return the best reviews of a month in the correct order.


So the whole solution is about picking the right data structures:


- Each `Review` keeps a running **sum** and **count** of its ratings. So its average is always ready.
- Each month keeps its reviews in a **sorted list** that is always in rank order. So the best reviews are simply the first few items.
- A **set of rewarded months** tells us which months are frozen. New ratings for those months are ignored.


We will start with a simple version that sorts a month every time it is asked. Then we will improve it so that every month stays sorted all the time.


## Do We Need a Design Pattern?


Two patterns look useful at first. But neither is a good fit for this problem.


**State Pattern**


A month has a small lifecycle. First it is open for ratings, then it is rewarded. With the State pattern we would create an `OpenMonth` class and a `RewardedMonth` class, and each one would decide how ratings and rewards behave.


But there are only two states. A month moves from open to rewarded once and never goes back. The whole difference in behavior is one check: is this month in `rewardedMonths`?


A base class and two subclasses to replace one `if` check would only add code.


**Strategy Pattern**


The ranking rule could be a strategy, so that we can switch to other orders like "most ratings first".


But the problem has exactly one ranking rule, and both `getBestReviews` and `rewardBestReviews` use it. So we write it once, as `compareTo` inside `Review`. Strategy makes sense only when there are many ranking rules to choose from.


So plain classes with the right data structures are the cleanest choice.


## The Review Class


Both approaches use the same `Review` class. It holds one review and everything about its ratings.


**`ratings` (dict)**


Maps each rater to their latest rating. It tells us if a rating is new or an update. For an update, it also gives us the old value.


**`ratingSum` and `ratingCount`**


We update these on every rating, so we never loop over all the ratings to find an average.


- A new rating adds to the sum, and the count goes up by 1.
- An update removes the old value from the sum and adds the new one. The count stays the same.


**Comparing averages exactly (`compareTo`)**


Reviews are ranked by their exact average, like 11 / 3 = 3.666... To compare two averages using only whole numbers, we cross multiply:


```
sumA / countA > sumB / countB   is the same as   sumA * countB > sumB * countA
```


There is no division and no decimal number, so the comparison is always exact. Python integers never overflow, so big products are not a problem.


For example, review X has one rating of 4 (4 / 1). Review Y has ratings 5 and 3 (8 / 2). Cross multiplying gives 4 * 2 = 8 and 8 * 1 = 8. The averages are equal, so the next rule decides: Y has more ratings, so Y comes first.


If the averages and the counts are both equal, the smaller review ID (dictionary order) comes first.


What about an unrated review? Its sum and count are both 0, so both products are 0 and the averages look equal. The next rule (more ratings first) then puts it after every rated review. That is exactly right, because a rated review always has an average of at least 1.


**Plugging the order into Python (`__lt__`)**


`sorted()` and the `bisect` module only use the `<` operator to compare items. So `__lt__` simply calls `compareTo`. Now Python sorts and searches reviews in our rank order.


**Rounding (`averageAsText`)**


We round only when we build the output string, never while ranking.


Python's built-in rounding fails here. Both `round(1.125, 2)` and `f"{1.125:.2f}"` give `1.12`, but half-up rounding needs `1.13`. Python rounds exact halves to the nearest even digit.


So for this one step we use `Decimal`. It works with decimal digits instead of binary fractions, and `ROUND_HALF_UP` rounds halves up, as the problem asks.


## Approach 1: Sort on Every Call


The simplest idea: keep each month's reviews in a plain list. When someone asks for the best reviews, sort the list and take the first `limit` items.


The main class needs just a few simple structures:


- `viewers` (set): checks if a viewer exists in O(1).
- `reviews` (dict): finds a review by its ID in O(1).
- `reviewsByMonth` (dict): yearMonth to the list of reviews written in that month.
- `rewardedMonths` (set): months whose rewards are already issued.


`rateReview` only updates the review, which is O(1). All the work happens when we read:


```python
# in __init__: yearMonth -> reviews of that month, in the order they were written
self.reviewsByMonth = {}

def sortedReviews(self, yearMonth: int) -> List[Review]:
    """Sorts the whole month on every call."""
    return sorted(self.reviewsByMonth.get(yearMonth, []))  # uses Review.__lt__

def getBestReviews(self, yearMonth: int, limit: int, currentYearMonth: int) -> List[str]:
    result = []
    for review in self.sortedReviews(yearMonth)[:limit]:
        average = review.averageAsText()
        result.append(f"{review.reviewId},{review.authorId},{average},{review.ratingCount}")
    return result
```


`rewardBestReviews` uses `sortedReviews` in the same way.


**What is wrong with it?**


Every call to `getBestReviews` or `rewardBestReviews` sorts the whole month. That is O(m log m), where m is the number of reviews in that month.


We sort everything even when we need only the top 3. We sort again even when no rating changed since the last call. A page that shows the best reviews may be opened many times, and each time we pay for a full sort.


## Approach 2: Keep Every Month Always Sorted


Instead of sorting when asked, we keep each month sorted all the time.


Python has no built-in sorted set, so we build one from two simple pieces:


- A plain **list** that we always keep in rank order.
- The **`bisect`** module, which does binary search on a sorted list. `insort(ranked, review)` puts a review at its correct rank, and `bisect_left(ranked, review)` finds where a review sits.


```python
# yearMonth -> list of reviews, always kept in rank order
self.rankedReviewsByMonth = {}
```


Why does this help? Binary search finds the right spot with only O(log m) comparisons. The list then shifts the items after that spot by one place. That shift is O(m), but it is one fast block memory copy, far cheaper than sorting the whole month again.


And reading the best reviews needs no sorting at all: `ranked[:limit]` already holds them.


### Updating a Rating


This is the one tricky part.


Binary search finds a review by comparing it with other reviews, and that comparison uses the review's sum and count. If we change the sum first, `bisect_left` looks in the wrong place, and we could remove the wrong review.


So the order of steps matters:


1. Find and remove the review, while it still has its old sum and count.
2. Add or update the rating.
3. Put the review back with `insort`. It lands at its new rank.


```python
ranked = self.rankedReviewsByMonth[review.yearMonth]
ranked.pop(bisect_left(ranked, review))
isNewRating = review.addOrUpdateRating(viewerId, rating)
insort(ranked, review)
```


### Issuing Rewards


`rewardBestReviews` first checks `rewardedMonths`. If the month is already there, it returns an empty list.


Otherwise it adds the month right away. Now the month is frozen, even if nobody wins.


Then it walks the sorted list. Rated reviews always come before unrated ones, so we stop at the first unrated review, or as soon as we have `rewardCount` winners.


Once a month is frozen, `rateReview` returns `RATING_IGNORED` for its reviews. This check runs before the own-review check, so even the author gets `RATING_IGNORED`.


### Where Is currentYearMonth Used?


Only in `writeReview`, where it decides the month a review belongs to.


The other methods can safely ignore it. Months never go back in time, and rewards are only issued for past months. So a frozen month can never get a new review.


## Complexity


m is the number of reviews in the requested month.


| Method | Approach 1 | Approach 2 |
|---|---|---|
| `writeReview` | O(1) | O(log m) search + O(m) shift |
| `rateReview` | O(1) | O(log m) search + O(m) shift |
| `getBestReviews` | O(m log m) | O(limit) |
| `rewardBestReviews` | O(m log m) | O(rewardCount) |


The O(m) shift is a fast memory copy, so in practice Approach 2 is much faster. Approach 1 needs O(m log m) calls to `__lt__` on every read.


Space is O(number of reviews + number of ratings) for both approaches.


## Final Python Code


```python
from bisect import bisect_left, insort
from decimal import Decimal, ROUND_HALF_UP
from typing import List


class Review:
    def __init__(self, reviewId: str, authorId: str, reviewText: str, yearMonth: int):
        self.reviewId = reviewId
        self.authorId = authorId      # viewer who wrote this review
        self.reviewText = reviewText
        self.yearMonth = yearMonth    # the review belongs to this month forever

        self.ratings = {}             # raterId -> latest rating
        self.ratingSum = 0
        self.ratingCount = 0

    def addOrUpdateRating(self, raterId: str, rating: int) -> bool:
        """Returns True if this is the rater's first rating for this review."""
        oldRating = self.ratings.get(raterId)
        self.ratings[raterId] = rating
        if oldRating is None:
            self.ratingSum += rating
            self.ratingCount += 1
            return True
        self.ratingSum += rating - oldRating  # swap the old rating for the new one
        return False

    def averageAsText(self) -> str:
        """Average rounded half-up to 2 decimals. Example: 11 / 3 -> "3.67"."""
        if self.ratingCount == 0:
            return "0.00"
        average = Decimal(self.ratingSum) / Decimal(self.ratingCount)
        return str(average.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))

    def compareTo(self, other: "Review") -> int:
        """Rank order: higher average, then more ratings, then smaller reviewId.
        A negative result means this review ranks before the other one."""
        # sumA / countA > sumB / countB  is the same as  sumA * countB > sumB * countA
        mine = self.ratingSum * other.ratingCount
        theirs = other.ratingSum * self.ratingCount
        if mine != theirs:
            return -1 if mine > theirs else 1

        if self.ratingCount != other.ratingCount:
            return -1 if self.ratingCount > other.ratingCount else 1

        if self.reviewId == other.reviewId:
            return 0
        return -1 if self.reviewId < other.reviewId else 1

    def __lt__(self, other: "Review") -> bool:
        # sorted() and bisect only use "<", so we plug our rank order in here
        return self.compareTo(other) < 0


class ReviewRewardSystem:
    def __init__(self, viewerIds: List[str]):
        self.viewers = set(viewerIds)
        self.reviews = {}               # reviewId -> Review
        self.rankedReviewsByMonth = {}  # yearMonth -> list of reviews, always kept in rank order
        self.rewardedMonths = set()     # months whose rewards are already issued

    def writeReview(self, reviewId: str, viewerId: str, reviewText: str, currentYearMonth: int) -> str:
        if viewerId not in self.viewers:
            return "VIEWER_NOT_FOUND"
        if reviewId in self.reviews:
            return "REVIEW_ALREADY_EXISTS"

        review = Review(reviewId, viewerId, reviewText, currentYearMonth)
        self.reviews[reviewId] = review
        insort(self.rankedReviewsByMonth.setdefault(currentYearMonth, []), review)
        return "REVIEW_ADDED"

    def rateReview(self, viewerId: str, reviewId: str, rating: int, currentYearMonth: int) -> str:
        if viewerId not in self.viewers:
            return "VIEWER_NOT_FOUND"
        review = self.reviews.get(reviewId)
        if review is None:
            return "REVIEW_NOT_FOUND"
        if review.yearMonth in self.rewardedMonths:
            return "RATING_IGNORED"
        if review.authorId == viewerId:
            return "CANNOT_RATE_OWN_REVIEW"

        # Binary search finds the review using its current sum and count.
        # So we take it out BEFORE changing them, and put it back after.
        ranked = self.rankedReviewsByMonth[review.yearMonth]
        ranked.pop(bisect_left(ranked, review))
        isNewRating = review.addOrUpdateRating(viewerId, rating)
        insort(ranked, review)

        return "RATING_ADDED" if isNewRating else "RATING_UPDATED"

    def getBestReviews(self, yearMonth: int, limit: int, currentYearMonth: int) -> List[str]:
        result = []
        for review in self.rankedReviewsByMonth.get(yearMonth, [])[:limit]:
            average = review.averageAsText()
            result.append(f"{review.reviewId},{review.authorId},{average},{review.ratingCount}")
        return result

    def rewardBestReviews(self, yearMonth: int, rewardCount: int, currentYearMonth: int) -> List[str]:
        # Rewards are issued only once per month
        if yearMonth in self.rewardedMonths:
            return []
        self.rewardedMonths.add(yearMonth)  # the month is final now, even if nobody wins

        winners = []
        for review in self.rankedReviewsByMonth.get(yearMonth, []):
            # Unrated reviews always sit at the end, so we stop at the first one
            if len(winners) == rewardCount or review.ratingCount == 0:
                break
            winners.append(f"{review.reviewId},{review.authorId}")
        return winners
```