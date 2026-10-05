# Restaurant Customer Tokens in Python


#### Problem Statement
[https://codezym.com/question/402-restaurant-customer-tokens](https://codezym.com/question/402-restaurant-customer-tokens)


The core idea is to remember which tokens are free. We can start with a boolean list and scan it from left to right. To issue tokens faster, keep the free numbers in a min-heap and use the list to check availability and ignore repeated returns. One class is enough for these three operations.


## What the counter must do


- Initially, all tokens from `0` to `tokenCount - 1` are available.
- `issueToken()` takes the smallest available token, or returns `-1` when none is free.
- `isTokenAvailable(tokenNumber)` reports whether that token is free.
- `returnToken(tokenNumber)` makes a token free again. Returning an already free token changes nothing.


## Solution 1: Scan a boolean list


Use `available[token]` to store whether a token is free. The list index is the token number, so availability checks and returns need only one list access.


For `issueToken()`, scan from token `0` upwards. Stop at the first `True`, change it to `False`, and return its number. The first free token we find must be the smallest one. If the scan finds nothing, return `-1`.


Returning a token simply sets its value to `True`. Repeating that assignment has no effect, which handles repeated returns automatically.


The dataclass supplies a constructor that accepts `tokenCount`. Its `__post_init__()` method sets up the availability list.


### Cost


Let `n` be the total number of tokens. Initialization takes `O(n)` time. Issuing a token takes `O(n)` time in the worst case. Availability checks and returns take `O(1)` time. Space usage is `O(n)`.


This solution is easy to follow, but each issue call may scan the whole list. With many calls, repeated scans become the main cost.


### Code


```python
from dataclasses import dataclass


@dataclass
class RestaurantTokenCounter:
    """Manages customer tokens by checking them in increasing order."""

    tokenCount: int

    def __post_init__(self):
        self.available = [True] * self.tokenCount

    def issueToken(self) -> int:
        # The first free token is the smallest free token.
        for token in range(self.tokenCount):
            if self.available[token]:
                self.available[token] = False
                return token
        return -1

    def isTokenAvailable(self, tokenNumber: int) -> bool:
        return self.available[tokenNumber]

    def returnToken(self, tokenNumber: int) -> None:
        # Setting an already free token to true makes no change.
        self.available[tokenNumber] = True
```


## Solution 2: Use a min-heap and a boolean list


A min-heap keeps its smallest number at the front. Python's `heapq` functions maintain a min-heap inside a normal list, so we can get the smallest free token without scanning all tokens.


Keep two pieces of information:

- `availableTokens` holds each free token exactly once. The heap chooses the next token to issue.
- `available` gives direct availability checks and tells us whether a return should be ignored.


The availability list matters because a heap does not provide fast lookups for an arbitrary token. It also prevents us from adding the same free token twice.


### How the methods work


The constructor accepts `tokenCount`, and `__post_init__()` marks every token as free. It creates a list containing `0` through `tokenCount - 1` and calls `heapify()` to build the heap in `O(n)` time.


`issueToken()` returns `-1` when the heap is empty. Otherwise, `heappop()` removes the smallest free token. We mark it unavailable in the availability list and return it.


`isTokenAvailable()` reads the availability list directly. `returnToken()` first checks that list. If the token is already free, it stops. Otherwise, it marks the token free and adds it back with `heappush()`.


### A short example


With three tokens:

1. The free numbers are `{0, 1, 2}`.
2. Two issue calls return `0` and `1`. Only `2` remains free.
3. Returning `0` twice leaves the free numbers as `{0, 2}`. There is only one heap entry for `0`.
4. The next two issue calls return `0` and `2`.
5. Another issue call returns `-1`.


Every successful issue removes a number from the heap and marks it unavailable. Every accepted return adds it once and marks it available. This keeps the heap and availability list in agreement, so the heap always chooses the smallest valid token.


### Cost


Initialization takes `O(n)` time. Issuing an available token or returning an issued token takes `O(log n)` time. Availability checks take `O(1)` time.


An issue call on an empty heap and a repeated return both take `O(1)` time. Total space usage is `O(n)`. Token numbers are valid according to the problem constraints.


### Code


```python
from dataclasses import dataclass
from heapq import heapify, heappop, heappush


@dataclass
class RestaurantTokenCounter:
    """Issues the smallest free token and safely accepts returned tokens."""

    tokenCount: int

    def __post_init__(self):
        self.available = [True] * self.tokenCount
        self.availableTokens = list(range(self.tokenCount))

        # Build the min-heap from all initially available tokens.
        heapify(self.availableTokens)

    def issueToken(self) -> int:
        if not self.availableTokens:
            return -1

        token = heappop(self.availableTokens)
        self.available[token] = False
        return token

    def isTokenAvailable(self, tokenNumber: int) -> bool:
        return self.available[tokenNumber]

    def returnToken(self, tokenNumber: int) -> None:
        # Ignore repeated returns so the heap never contains duplicates.
        if self.available[tokenNumber]:
            return

        self.available[tokenNumber] = True
        heappush(self.availableTokens, tokenNumber)
```
