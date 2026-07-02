# Low level design: webpage visits counter

See the problem statement here: https://codezym.com/question/6-design-hit-counter-multithreaded

This is an easy question, yet it gives you a taste of basic data structures to use in a multithreaded environment. This is an ideal question to start your low-level design interview preparation.

Basically, you have the keep count of webpage visits for all webpages of a website and fetch counts.

## Methods

Increment page visit count: `incrementVisitCount(int pageIndex)`

Get visit count of a page: `getVisitCount(int pageIndex)`

## Data Storage

Data storage for this problem can be done in multiple different ways, let’s discuss them one by one.

Let’s assume that total number of webpages on the website is n numbered 0 to n-1

### Keep all the counts in an ArrayList of size n

```java
ArrayList<Integer> pageVisitCounts
```

We can lock the pageVisitCounts list using synchronized keyword for each incrementVisitCount and getVisitCount request.

This solution will work correctly in a multithreaded environment but it’s inefficient.

Since at any time, only one thread can increment or fetch visit count of a webpage. So we are missing the benefits of our multiprocessor hardware.

### Use a ConcurrentHashMap to keep counts

```java
ConcurrentHashMap<Integer, Integer> pageVisitCounts
```

This is basically a page index vs visit count map and it is more efficient than above ArrayList of Integers.

Although ConcurrentHashMap takes more space than an ArrayList of integers but its default concurrency level is 16, which means at max 16 threads can call incrementVisitCount for different webpages at the same time, while using above ArrayList with synchronized, only one thread was able to update page visit count at a time, so this is much more efficient.

### Use an ArrayList of AtomicInteger

```java
ArrayList<AtomicInteger> pageVisitCounts
```

This data structure allows even more parallelism than a ConcurrentHashMap.

Now you can update visit counts for each page parallelly, e.g. if there are 1000 pages in the website then at a time at max 1000 threads can call incrementVisitCount for different webpages.

AtomicInteger takes care of the update in a multi-threaded environment.

In this case, this data structure is simple to use and more efficient than other two data structures we discussed above. We have used this in our final solution.

Please do jump into comments for further discussion on other ways to solve this problem.

## Java Code

```java
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * - All the methods of this class will be tested in a MULTI-THREADED environment.
 * - If you are creating new interfaces, then they have to be declared on the top, even before class Solution, else it will give class not found error for classes implementing them.
 * - use helper.print("") or helper.println("") for printing logs else logs will not be visible.
 */
public class Solution implements Q06WebpageVisitCounterInterface {
    private Helper06 helper;
    private ArrayList<AtomicInteger> pageVisitCounts;

    // constructor must always be public, don't remove the public keyword
    public Solution(){}

    /**
     * Use this method to initialize your instance variables
     */
    public void init(int totalPages, Helper06 helper){
        this.helper=helper;
        pageVisitCounts = new ArrayList<>();
        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for(int i=0;i<totalPages;i++)pageVisitCounts.add(new AtomicInteger(0));
        // helper.println("restaurant rating module initialized");
    }

    /**
     * increment visit count for pageIndex by 1
     */
    public void incrementVisitCount(int pageIndex) {
        pageVisitCounts.get(pageIndex).incrementAndGet();
    }

    /**
     * return total visit count for a given page
     */
    public int getVisitCount(int pageIndex) {
        return pageVisitCounts.get(pageIndex).get();
    }
}
```
