# Restaurant Customer Tokens in Java


#### Problem Statement
[https://codezym.com/question/402-restaurant-customer-tokens](https://codezym.com/question/402-restaurant-customer-tokens)


The core idea is to remember which tokens are free. We can start with a boolean array and scan it from left to right. To issue tokens faster, keep the free numbers in a min-heap and use the array to check availability and ignore repeated returns. One class is enough for these three operations.


## What the counter must do


- Initially, all tokens from `0` to `tokenCount - 1` are available.
- `issueToken()` takes the smallest available token, or returns `-1` when none is free.
- `isTokenAvailable(tokenNumber)` reports whether that token is free.
- `returnToken(tokenNumber)` makes a token free again. Returning an already free token changes nothing.


## Solution 1: Scan a boolean array


Use `available[token]` to store whether a token is free. The array index is the token number, so availability checks and returns need only one array access.


For `issueToken()`, scan from token `0` upwards. Stop at the first `true`, change it to `false`, and return its number. The first free token we find must be the smallest one. If the scan finds nothing, return `-1`.


Returning a token simply sets its value to `true`. Repeating that assignment has no effect, which handles repeated returns automatically.


### Cost


Let `n` be the total number of tokens. Initialization takes `O(n)` time. Issuing a token takes `O(n)` time in the worst case. Availability checks and returns take `O(1)` time. Space usage is `O(n)`.


This solution is easy to follow, but each issue call may scan the whole array. With many calls, repeated scans become the main cost.


### Code


```java
import java.util.Arrays;

/** Manages customer tokens by checking them in increasing order. */
public class RestaurantTokenCounter {
    boolean[] available;

    public RestaurantTokenCounter(int tokenCount) {
        available = new boolean[tokenCount];
        Arrays.fill(available, true);
    }

    public int issueToken() {
        // The first free token is the smallest free token.
        for (int token = 0; token < available.length; token++) {
            if (available[token]) {
                available[token] = false;
                return token;
            }
        }
        return -1;
    }

    public boolean isTokenAvailable(int tokenNumber) {
        return available[tokenNumber];
    }

    public void returnToken(int tokenNumber) {
        // Setting an already free token to true makes no change.
        available[tokenNumber] = true;
    }
}
```


## Solution 2: Use a min-heap and a boolean array


A min-heap keeps its smallest number at the front. Java's `PriorityQueue<Integer>` provides this behavior by default, so we can get the smallest free token without scanning all tokens.


Keep two pieces of information:

- `availableTokens` holds each free token exactly once. The heap chooses the next token to issue.
- `available` gives direct availability checks and tells us whether a return should be ignored.


The array matters because a heap does not provide fast lookups for an arbitrary token. It also prevents us from adding the same free token twice.


### How the methods work


The constructor marks every token as free and builds the heap from a list containing `0` through `tokenCount - 1`. Building the heap from the whole list takes `O(n)` time.


`issueToken()` returns `-1` when the heap is empty. Otherwise, `poll()` removes the smallest free token. We mark it unavailable in the array and return it.


`isTokenAvailable()` reads the array directly. `returnToken()` first checks that array. If the token is already free, it stops. Otherwise, it marks the token free and adds it back with `offer()`.


### A short example


With three tokens:

1. The free numbers are `{0, 1, 2}`.
2. Two issue calls return `0` and `1`. Only `2` remains free.
3. Returning `0` twice leaves the free numbers as `{0, 2}`. There is only one heap entry for `0`.
4. The next two issue calls return `0` and `2`.
5. Another issue call returns `-1`.


Every successful issue removes a number from the heap and marks it unavailable. Every accepted return adds it once and marks it available. This keeps the heap and array in agreement, so the heap always chooses the smallest valid token.


### Cost


Initialization takes `O(n)` time. Issuing an available token or returning an issued token takes `O(log n)` time. Availability checks take `O(1)` time.


An issue call on an empty heap and a repeated return both take `O(1)` time. Total space usage is `O(n)`. Token numbers are valid according to the problem constraints.


### Code


```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/** Issues the smallest free token and safely accepts returned tokens. */
public class RestaurantTokenCounter {
    boolean[] available;
    PriorityQueue<Integer> availableTokens;

    public RestaurantTokenCounter(int tokenCount) {
        available = new boolean[tokenCount];
        Arrays.fill(available, true);

        List<Integer> tokens = new ArrayList<>();
        for (int token = 0; token < tokenCount; token++) {
            tokens.add(token);
        }

        // Build the min-heap from all initially available tokens.
        availableTokens = new PriorityQueue<>(tokens);
    }

    public int issueToken() {
        if (availableTokens.isEmpty()) {
            return -1;
        }

        int token = availableTokens.poll();
        available[token] = false;
        return token;
    }

    public boolean isTokenAvailable(int tokenNumber) {
        return available[tokenNumber];
    }

    public void returnToken(int tokenNumber) {
        // Ignore repeated returns so the heap never contains duplicates.
        if (available[tokenNumber]) {
            return;
        }

        available[tokenNumber] = true;
        availableTokens.offer(tokenNumber);
    }
}
```
