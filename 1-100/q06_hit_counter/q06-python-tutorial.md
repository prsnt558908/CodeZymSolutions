# Design Hit Counter in Python

Problem Statement: https://codezym.com/question/6-design-hit-counter-multithreaded

The core idea of this solution is very simple: keep one counter for every webpage and increase that counter whenever the page is visited. This is an in-memory counter design where a dictionary stores `page_index -> visit_count`. The solution uses Python's `defaultdict(int)` so that every new page automatically starts with count `0`. At a high level, this is similar to a basic repository or manager class that owns the visit-count data and exposes simple methods to update and read it.

## High Level Design

We need to support two main operations:

1. Increment the visit count of a webpage.
2. Get the current visit count of a webpage.

For this, we maintain a dictionary:

```python
page_index -> visit_count
```

Example:

```python
{
    0: 5,
    1: 2,
    2: 0
}
```

This means:

- Page `0` was visited `5` times.
- Page `1` was visited `2` times.
- Page `2` was not visited yet.

## Why `defaultdict(int)`?

We use:

```python
defaultdict(int)
```

because `int()` gives `0` by default.

So if we access a page that is not already present in the dictionary, it will automatically return `0` instead of throwing an error.

This keeps the code simple and avoids extra checks like:

```python
if page_index not in page_visit_counts:
    page_visit_counts[page_index] = 0
```

## Important Classes and Data Structures

### `Solution`

The `Solution` class is the main class that manages all page visit counts.

It has three responsibilities:

- Initialize visit counts for all pages.
- Increment the visit count for a page.
- Return the visit count for a page.

### `page_visit_counts`

```python
self.page_visit_counts = defaultdict(int)
```

This is the main data structure used in the solution.

It stores the visit count for each page index.

Example:

```python
self.page_visit_counts[3] = 10
```

This means page `3` has been visited `10` times.

## Method Explanation

### `__init__`

```python
def __init__(self):
    self.helper = None
    self.page_visit_counts = defaultdict(int)
```

This constructor initializes:

- `helper` as `None`
- `page_visit_counts` as an empty `defaultdict(int)`

The helper object is stored because the platform may provide it during initialization.

### `init`

```python
def init(self, total_pages, helper):
    self.helper = helper
    for _ in range(total_pages):
        self.page_visit_counts[_] = 0
```

This method initializes visit counts for all pages from `0` to `total_pages - 1`.

For example, if `total_pages = 3`, then the dictionary becomes:

```python
{
    0: 0,
    1: 0,
    2: 0
}
```

All pages start with visit count `0`.

### `increment_visit_count`

```python
def increment_visit_count(self, page_index):
    self.page_visit_counts[page_index] += 1
```

This method increases the visit count of the given page by `1`.

For example, if page `2` has count `5`, after calling:

```python
increment_visit_count(2)
```

the count becomes `6`.

### `get_visit_count`

```python
def get_visit_count(self, page_index):
    return self.page_visit_counts[page_index]
```

This method returns the current visit count of the given page.

If the page was initialized but never visited, it returns `0`.


## Python Code

```python
from collections import defaultdict


class Solution:
    def __init__(self):
        self.helper = None

        # Stores visit count for each page.
        # Key: page index
        # Value: number of visits for that page
        self.page_visit_counts = defaultdict(int)

    def init(self, total_pages, helper):
        self.helper = helper

        # Initialize all page visit counts to 0.
        # Pages are indexed from 0 to total_pages - 1.
        for page_index in range(total_pages):
            self.page_visit_counts[page_index] = 0

    def increment_visit_count(self, page_index):
        # Increase visit count of the given page by 1.
        self.page_visit_counts[page_index] += 1

    def get_visit_count(self, page_index):
        # Return current visit count of the given page.
        return self.page_visit_counts[page_index]
```

## Complexity Analysis

### `increment_visit_count`

Time Complexity: `O(1)`

Dictionary update is average `O(1)`.

### `get_visit_count`

Time Complexity: `O(1)`

Dictionary lookup is average `O(1)`.

### Space Complexity

Space Complexity: `O(total_pages)`

We store one counter for each page.

## Final Thoughts

This solution uses a very simple dictionary-based design. Each page has one counter, and every visit increments that counter. The most important part of the solution is choosing a data structure that gives fast update and lookup. Here, `defaultdict(int)` keeps the implementation clean and easy to understand.
