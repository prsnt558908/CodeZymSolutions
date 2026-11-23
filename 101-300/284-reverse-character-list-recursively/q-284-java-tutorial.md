# Reverse a Character List/Array Recursively in Java

#### Problem Statement
[https://codezym.com/question/284-reverse-character-list-recursively](https://codezym.com/question/284-reverse-character-list-recursively)


## Core Idea

To reverse a list, the first character swaps places with the last one, the second swaps with the second last one, and so on until we reach the middle.


We must change the given list itself. We cannot use loops, create a new list or call a built-in reverse method. So recursion has to do the walking for us.


Each recursive call swaps one outer pair and then hands the smaller inner part to the next call. When the two ends meet in the middle, nothing is left to swap, so the recursion stops. This is the two pointer technique, with recursion in place of a loop.


We will start with a simple recursive idea, see why it is slow, and then improve it.


## Simple Approach: Remove the First Character, Reverse the Rest

A common way to think recursively is to split the list into "the first character" and "the rest".


The reverse of `[c, o, d, e]` is the reverse of `[o, d, e]` followed by `c`.


So we take out the first character, reverse what is left, and then add that character at the end.

```java
public void reverseRecursively(List<Character> characters) {
    if (characters.size() <= 1) {
        return;                          // nothing to reverse
    }
    char first = characters.remove(0);   // take out the first character
    reverseRecursively(characters);      // reverse the rest
    characters.add(first);               // put it at the end
}
```


This follows all the rules, but it has three problems:

- **Slow:** in an `ArrayList`, `remove(0)` shifts every remaining character one step to the left. We do this for every character, so the total time becomes O(n²).
- **Changes the list size:** the list shrinks and then grows back. It even fails on a fixed-size list, like one made with `Arrays.asList`.
- **More calls than needed:** each call fixes only one character, so 1,000 characters means 1,000 nested calls.


The fix: never remove anything. Just swap characters where they already are, and fix two characters (one from each end) in every call.


## Better Approach: Two Pointers With Recursion

We keep two indexes:

- `left` starts at the first character.
- `right` starts at the last character.


Each call does just three things:

1. If `left >= right`, stop. We have reached the middle.
2. Swap the characters at `left` and `right`.
3. Call itself with `left + 1` and `right - 1` to reverse the inner part.


### Dry Run

```text
[ c, o, d, e ]
  L        R       left = 0, right = 3  ->  swap c and e

[ e, o, d, c ]
     L  R          left = 1, right = 2  ->  swap o and d

[ e, d, o, c ]
     R  L          left = 2, right = 1  ->  pointers crossed, stop
```


For an odd length list like `['R', 'a', 'c', 'e', 'c', 'a', 'r']`, the pointers meet at the middle `'e'`, so `left == right`. A character does not need to swap with itself, so we stop there.


### Why do we need a helper method?

`reverseRecursively` only receives the list. But every call needs to know which pair it should swap.


So we add a helper method `reverse(characters, left, right)` that carries the two indexes. The main method just starts it with `left = 0` and `right = size - 1`.


### Complexity

- **Time:** O(n). We make about n/2 calls and each call does one swap.
- **Extra space:** O(1), apart from the recursion stack, which is about n/2 calls deep.


## Java Code

```java
import java.util.*;

public class ReverseCharacterListRecursively {

    public ReverseCharacterListRecursively() {
    }

    public void reverseRecursively(List<Character> characters) {
        reverse(characters, 0, characters.size() - 1);
    }

    /**
     * Reverses the characters from index left to index right (both inclusive).
     * Swaps the outer pair first, then reverses the inner part recursively.
     */
    void reverse(List<Character> characters, int left, int right) {
        // Base case: pointers met or crossed, so nothing is left to swap
        if (left >= right) {
            return;
        }

        // Swap the two ends
        char temp = characters.get(left);
        characters.set(left, characters.get(right));
        characters.set(right, temp);

        // Reverse the remaining inner part
        reverse(characters, left + 1, right - 1);
    }
}
```