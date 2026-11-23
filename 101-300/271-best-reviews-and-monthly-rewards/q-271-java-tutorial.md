# Best Reviews and Monthly Rewards in Java

#### Problem Statement
[https://codezym.com/question/271-best-reviews-and-monthly-rewards](https://codezym.com/question/271-best-reviews-and-monthly-rewards)


## Core Idea


This looks like a design problem, but it does not need any design pattern. The hard part is ranking. Ratings keep changing, and at any moment we must return the best reviews of a month in the correct order.


So the whole solution is about picking the right data structures:


- Each `Review` keeps a running **sum** and **count** of its ratings. So its average is always ready.
- Each month keeps its reviews in a **sorted set** that is always in rank order. So the best reviews are simply the first few items.
- A **set of rewarded months** tells us which months are frozen. New ratings for those months are ignored.


We will start with a simple version that sorts a month every time it is asked. Then we will improve it so that every month stays sorted all the time.


## Do We Need a Design Pattern?


Two patterns look useful at first. But neither is a good fit for this problem.


**State Pattern**


A month has a small lifecycle. First it is open for ratings, then it is rewarded. With the State pattern we would create an `OpenMonth` class and a `RewardedMonth` class, and each one would decide how ratings and rewards behave.


But there are only two states. A month moves from open to rewarded once and never goes back. The whole difference in behavior is one check: is this month in `rewardedMonths`?


An interface and two classes to replace one `if` check would only add code.


**Strategy Pattern**


The ranking rule could be a strategy, so that we can switch to other orders like "most ratings first".


But the problem has exactly one ranking rule, and both `getBestReviews` and `rewardBestReviews` use it. So we write it once, as `compareTo` inside `Review`. Strategy makes sense only when there are many ranking rules to choose from.


So plain classes with the right data structures are the cleanest choice.


## The Review Class


Both approaches use the same `Review` class. It holds one review and everything about its ratings.


**`ratings` (map)**


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


There is no division and no decimal number, so the comparison is always exact. `ratingSum` is a `long` because the products can go past the `int` limit (500,000 * 100,000).


For example, review X has one rating of 4 (4 / 1). Review Y has ratings 5 and 3 (8 / 2). Cross multiplying gives 4 * 2 = 8 and 8 * 1 = 8. The averages are equal, so the next rule decides: Y has more ratings, so Y comes first.


If the averages and the counts are both equal, the smaller review ID (dictionary order) comes first.


What about an unrated review? Its sum and count are both 0, so both products are 0 and the averages look equal. The next rule (more ratings first) then puts it after every rated review. That is exactly right, because a rated review always has an average of at least 1.


**Rounding (`averageAsText`)**


We round only when we build the output string, never while ranking.


A quick trick like `Math.round(average * 100) / 100.0` fails here. For 201 / 200 = 1.005 it gives `1.0`, but half-up rounding needs `1.01`. The reason is that a `double` stores 1.005 as 1.00499999...


So for this one step we use `BigDecimal`. It divides and rounds half-up exactly.


## Approach 1: Sort on Every Call


The simplest idea: keep each month's reviews in a plain list. When someone asks for the best reviews, sort the list and take the first `limit` items.


The main class needs just a few simple structures:


- `viewers` (set): checks if a viewer exists in O(1).
- `reviews` (map): finds a review by its ID in O(1).
- `reviewsByMonth` (map): yearMonth to the list of reviews written in that month.
- `rewardedMonths` (set): months whose rewards are already issued.


`rateReview` only updates the review, which is O(1). All the work happens when we read:


```java
// yearMonth -> reviews of that month, in the order they were written
Map<Integer, List<Review>> reviewsByMonth = new HashMap<>();

// Sorts the whole month on every call
List<Review> sortedReviews(int yearMonth) {
    List<Review> monthReviews = new ArrayList<>(reviewsByMonth.getOrDefault(yearMonth, new ArrayList<>()));
    Collections.sort(monthReviews); // uses Review.compareTo
    return monthReviews;
}

public List<String> getBestReviews(int yearMonth, int limit, int currentYearMonth) {
    List<String> result = new ArrayList<>();
    for (Review review : sortedReviews(yearMonth)) {
        if (result.size() == limit) break;
        result.add(review.reviewId + "," + review.authorId + ","
                + review.averageAsText() + "," + review.ratingCount);
    }
    return result;
}
```


`rewardBestReviews` uses `sortedReviews` in the same way.


**What is wrong with it?**


Every call to `getBestReviews` or `rewardBestReviews` sorts the whole month. That is O(m log m), where m is the number of reviews in that month.


We sort everything even when we need only the top 3. We sort again even when no rating changed since the last call. A page that shows the best reviews may be opened many times, and each time we pay for a full sort.


## Approach 2: Keep Every Month Always Sorted


Instead of sorting when asked, we keep each month sorted all the time. We replace the list with a `TreeSet`, a sorted set that uses `Review.compareTo` to keep reviews in rank order.


```java
// yearMonth -> reviews of that month, always kept in rank order
Map<Integer, TreeSet<Review>> rankedReviewsByMonth = new HashMap<>();
```


Why a sorted set? Adding or removing one review costs only O(log m). And reading the best reviews needs no sorting at all: we walk from the front of the set and stop after `limit` items.


### Updating a Rating


This is the one tricky part.


A sorted set finds a review by comparing it with other reviews, and that comparison uses the review's sum and count. If we change the sum first, the set searches in the wrong place. It may miss the review, and the old entry stays behind at its old rank.


So the order of steps matters:


1. Remove the review from the set, while it still has its old sum and count.
2. Add or update the rating.
3. Add the review back. The set puts it at its new rank.


```java
TreeSet<Review> ranked = rankedReviewsByMonth.get(review.yearMonth);
ranked.remove(review);
boolean isNewRating = review.addOrUpdateRating(viewerId, rating);
ranked.add(review);
```


### Issuing Rewards


`rewardBestReviews` first checks `rewardedMonths`. If the month is already there, it returns an empty list.


Otherwise it adds the month right away. Now the month is frozen, even if nobody wins.


Then it walks the sorted set. Rated reviews always come before unrated ones, so we stop at the first unrated review, or as soon as we have `rewardCount` winners.


Once a month is frozen, `rateReview` returns `RATING_IGNORED` for its reviews. This check runs before the own-review check, so even the author gets `RATING_IGNORED`.


### Where Is currentYearMonth Used?


Only in `writeReview`, where it decides the month a review belongs to.


The other methods can safely ignore it. Months never go back in time, and rewards are only issued for past months. So a frozen month can never get a new review.


## Complexity


m is the number of reviews in the requested month.


| Method | Approach 1 | Approach 2 |
|---|---|---|
| `writeReview` | O(1) | O(log m) |
| `rateReview` | O(1) | O(log m) |
| `getBestReviews` | O(m log m) | O(log m + limit) |
| `rewardBestReviews` | O(m log m) | O(log m + rewardCount) |


Space is O(number of reviews + number of ratings) for both approaches.


## Final Java Code


```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class ReviewRewardSystem {

    Set<String> viewers = new HashSet<>();

    // reviewId -> Review
    Map<String, Review> reviews = new HashMap<>();

    // yearMonth -> reviews of that month, always kept in rank order
    Map<Integer, TreeSet<Review>> rankedReviewsByMonth = new HashMap<>();

    // months whose rewards are already issued (ratings for them are frozen)
    Set<Integer> rewardedMonths = new HashSet<>();

    public ReviewRewardSystem(List<String> viewerIds) {
        viewers.addAll(viewerIds);
    }

    public String writeReview(String reviewId, String viewerId, String reviewText, int currentYearMonth) {
        if (!viewers.contains(viewerId)) return "VIEWER_NOT_FOUND";
        if (reviews.containsKey(reviewId)) return "REVIEW_ALREADY_EXISTS";

        Review review = new Review(reviewId, viewerId, reviewText, currentYearMonth);
        reviews.put(reviewId, review);
        rankedReviewsByMonth.computeIfAbsent(currentYearMonth, month -> new TreeSet<>()).add(review);
        return "REVIEW_ADDED";
    }

    public String rateReview(String viewerId, String reviewId, int rating, int currentYearMonth) {
        if (!viewers.contains(viewerId)) return "VIEWER_NOT_FOUND";
        Review review = reviews.get(reviewId);
        if (review == null) return "REVIEW_NOT_FOUND";
        if (rewardedMonths.contains(review.yearMonth)) return "RATING_IGNORED";
        if (review.authorId.equals(viewerId)) return "CANNOT_RATE_OWN_REVIEW";

        // The sorted set finds a review using its current sum and count.
        // So we take it out BEFORE changing them, and put it back after.
        TreeSet<Review> ranked = rankedReviewsByMonth.get(review.yearMonth);
        ranked.remove(review);
        boolean isNewRating = review.addOrUpdateRating(viewerId, rating);
        ranked.add(review);

        return isNewRating ? "RATING_ADDED" : "RATING_UPDATED";
    }

    public List<String> getBestReviews(int yearMonth, int limit, int currentYearMonth) {
        List<String> result = new ArrayList<>();
        TreeSet<Review> ranked = rankedReviewsByMonth.getOrDefault(yearMonth, new TreeSet<>());

        for (Review review : ranked) {
            if (result.size() == limit) break;
            result.add(review.reviewId + "," + review.authorId + ","
                    + review.averageAsText() + "," + review.ratingCount);
        }
        return result;
    }

    public List<String> rewardBestReviews(int yearMonth, int rewardCount, int currentYearMonth) {
        List<String> winners = new ArrayList<>();

        // Rewards are issued only once per month
        if (rewardedMonths.contains(yearMonth)) return winners;
        rewardedMonths.add(yearMonth); // the month is final now, even if nobody wins

        TreeSet<Review> ranked = rankedReviewsByMonth.getOrDefault(yearMonth, new TreeSet<>());
        for (Review review : ranked) {
            // Unrated reviews always sit at the end, so we stop at the first one
            if (winners.size() == rewardCount || review.ratingCount == 0) break;
            winners.add(review.reviewId + "," + review.authorId);
        }
        return winners;
    }
}

class Review implements Comparable<Review> {
    String reviewId;
    String authorId;   // viewer who wrote this review
    String reviewText;
    int yearMonth;     // the review belongs to this month forever

    Map<String, Integer> ratings = new HashMap<>(); // raterId -> latest rating
    long ratingSum = 0;
    int ratingCount = 0;

    Review(String reviewId, String authorId, String reviewText, int yearMonth) {
        this.reviewId = reviewId;
        this.authorId = authorId;
        this.reviewText = reviewText;
        this.yearMonth = yearMonth;
    }

    // Returns true if this is the rater's first rating for this review
    boolean addOrUpdateRating(String raterId, int rating) {
        Integer oldRating = ratings.put(raterId, rating);
        if (oldRating == null) {
            ratingSum += rating;
            ratingCount++;
            return true;
        }
        ratingSum += rating - oldRating; // swap the old rating for the new one
        return false;
    }

    // Average rounded half-up to 2 decimals. Example: 11 / 3 -> "3.67"
    String averageAsText() {
        if (ratingCount == 0) return "0.00";
        return BigDecimal.valueOf(ratingSum)
                .divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP)
                .toString();
    }

    // Rank order: higher average, then more ratings, then smaller reviewId.
    // A negative result means this review ranks before the other one.
    public int compareTo(Review other) {
        // sumA / countA > sumB / countB  is the same as  sumA * countB > sumB * countA
        long mine = ratingSum * other.ratingCount;
        long theirs = other.ratingSum * ratingCount;
        if (mine != theirs) return mine > theirs ? -1 : 1;

        if (ratingCount != other.ratingCount) return ratingCount > other.ratingCount ? -1 : 1;

        return reviewId.compareTo(other.reviewId);
    }
}
```