# First Sale of a Product Sold Once in Java

#### Problem Statement
[https://codezym.com/question/476-first-single-sale](https://codezym.com/question/476-first-single-sale)


## Core Idea

A product qualifies only if it appears **exactly once** in the whole log.

So before we can say "this sale is the answer", we must know the total count of that product for the entire day.


The simple plan is:

1. Count how many units of each product were sold.
2. Walk the log from left to right and return the first sale whose product count is `1`.


We will start with a brute force solution, see why it is slow, and then improve it with a small counting array.


---


## Problem in Short

- `salesLog` is a string of lowercase letters.
- Each letter is a product. Each occurrence is one unit sold.
- Return the index of the first letter that appears only once in the whole string.
- If no such letter exists, return `-1`.


**Example:** `"ppqrqsr"`

| Product | Units sold |
|---------|-----------|
| p | 2 |
| q | 2 |
| r | 2 |
| s | 1 |

Only `s` was sold once. It sits at index `5`, so the answer is `5`.


---


## Solution 1: Brute Force

### Idea

Pick each sale one by one.

For that sale, scan the full log and count how many times its product appears.

The first sale whose count is `1` is the answer.


### Code

```java
public class FirstSingleSale {

    public FirstSingleSale() {
    }

    /**
     * Brute force: for every sale, count how many times
     * the same product appears in the whole log.
     */
    public int firstSingleSaleIndex(String salesLog) {
        int n = salesLog.length();
        for (int i = 0; i < n; i++) {
            char product = salesLog.charAt(i);
            int count = 0;
            for (int j = 0; j < n; j++) {
                if (salesLog.charAt(j) == product) {
                    count++;
                }
            }
            // first product sold exactly once
            if (count == 1) {
                return i;
            }
        }
        return -1;
    }
}
```


### What is wrong with it?

For every sale we scan the whole log again.

That is `n * n` steps. With `n = 100,000` this becomes `10,000,000,000` steps in the worst case, for example a log like `"aabbaabb..."` with no single product.


We are also counting the same product again and again.

If `a` appears 50,000 times, we count all the `a`s 50,000 separate times. That is wasted work.


| | Value |
|---|---|
| Time | O(n²) |
| Space | O(1) |


---


## Solution 2: Count First, Then Search (Optimal)

### Idea

Instead of counting a product every time we see it, count **all** products once, at the start.

Then the second walk only needs to look up a ready count.


**Pass 1:** go through the log and add 1 to the count of each product.

**Pass 2:** go through the log again from the start. The first sale whose product count is `1` is the answer.


### Why an `int[26]` array?

There are only 26 possible products (`a` to `z`).

So a plain array of size 26 is enough to store all counts. We map each letter to a slot like this:

```
'a' - 'a' = 0
'b' - 'a' = 1
...
'z' - 'a' = 25
```


A `HashMap<Character, Integer>` would also work. But the array is simpler and faster, since it reads and writes counts directly by position.


### Why do we need a second pass?

During the first pass we do not yet know the final count of a product.

A product that looks single at index `0` might appear again at index `90,000`.

So we must finish counting first, and only then search for the answer.


### Why walk the log, not the array?

The array tells us **which** products were sold once. It does not tell us **which came first**.

Walking the log in order gives the earliest sale, which is what the problem asks for.


### Dry Run on `"tggv"`

After pass 1:

| Product | Count |
|---------|-------|
| t | 1 |
| g | 2 |
| v | 1 |


Pass 2:

- Index `0` is `t`. Count is `1`. Return `0`.


### Code

```java
public class FirstSingleSale {

    public FirstSingleSale() {
    }

    /**
     * Two passes over the log.
     * Pass 1: count units sold for every product.
     * Pass 2: return the first sale whose product count is 1.
     */
    public int firstSingleSaleIndex(String salesLog) {
        // counts[0] is for 'a', counts[1] for 'b' ... counts[25] for 'z'
        int[] counts = new int[26];

        for (int i = 0; i < salesLog.length(); i++) {
            counts[salesLog.charAt(i) - 'a']++;
        }

        for (int i = 0; i < salesLog.length(); i++) {
            if (counts[salesLog.charAt(i) - 'a'] == 1) {
                return i;
            }
        }
        return -1;
    }
}
```


### Complexity

| | Value | Why |
|---|---|---|
| Time | O(n) | Two simple walks over the log |
| Space | O(1) | The array always has 26 slots, no matter how long the log is |


---


## Summary

- A sale qualifies only if its product appears exactly once in the **whole** day.
- Brute force recounts each product for every sale, which takes O(n²) time.
- Counting all products first with an `int[26]` array, then scanning the log once more, gives the answer in O(n) time and constant space.