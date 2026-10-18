# Single Element in a Sorted Array: Pairs and Triplets in Java

#### Problem Statement
[https://codezym.com/question/117-single-element-sorted-array-pairs-triplets](https://codezym.com/question/117-single-element-sorted-array-pairs-triplets)


The single element shifts the normal positions of every pair or triplet after it. We can find that shift with binary search. The same helper works for both methods by checking blocks of size `2` or `3`.


## Start with a simple scan

Because the list is sorted, equal values sit next to each other. We could walk through the groups and return the value whose group has size one.


This takes `O(n)` time and `O(1)` extra space. To meet the required `O(log n)` time, we need to skip groups using binary search.


## Find where the group positions change

All indexes below start from zero.


For pairs, complete groups before the single element start at indexes `0, 2, 4, ...`. After the single element, their starting indexes shift by one.


For triplets, complete groups before the single element start at indexes `0, 3, 6, ...`. Their starting indexes also shift by one after the single element.


So we check a block that starts at a multiple of the group size.

- Before the single element, its first and last values match.
- At or after the single element, its first and last values differ because the block crosses a group boundary.


For triplets, comparing just the endpoints is enough. In a sorted list, if the first and third values are equal, the middle value must be equal too.

## One binary search for both methods

`singleNonDuplicate` calls the helper with `groupSize = 2`. `singleNonTriplet` calls it with `groupSize = 3`.


Start with `left = 0` and `right = nums.size() - 1`. Both bounds represent possible positions of the single element.


In each step:

1. Find the middle index, then align it with `start = mid - mid % groupSize`.
2. Compare `nums[start]` with `nums[start + groupSize - 1]`.
3. If they match, the block is complete and lies before the answer. Set `left = start + groupSize`.
4. Otherwise, the answer is at `start` or somewhere to its left. Set `right = start`.


When `left == right`, return the value at that index.

## Walk through the examples

### Pairs

Consider `nums = [1, 1, 2, 3, 3, 4, 4, 8, 8]`.

1. With bounds `[0, 8]`, the aligned block starts at index `4`. Its values are `3` and `4`, so move `right` to `4`.
2. With bounds `[0, 4]`, the aligned block starts at index `2`. Its values are `2` and `3`, so move `right` to `2`.
3. With bounds `[0, 2]`, the aligned block starts at index `0`. Its values are `1` and `1`, so move `left` to `2`.


Both bounds are now `2`. Return `nums[2]`, which is `2`.


### Triplets

Consider `nums = [1, 1, 1, 2, 3, 3, 3]`.

1. With bounds `[0, 6]`, check the block from index `3` to `5`. Its endpoints are `2` and `3`, so move `right` to `3`.
2. With bounds `[0, 3]`, align the middle index down to `0`. The endpoints at indexes `0` and `2` are both `1`, so move `left` to `3`.


Return `nums[3]`, which is `2`.

## Why the search works

The single element always sits at a multiple of the group size because every value before it fills a complete group. Each matching aligned block is before that position. Each non-matching aligned block starts at that position or later.


Both search updates therefore keep the answer inside the remaining range. Binary search removes about half the candidate groups each time and stops at the single element.


The bounds stay at multiples of the group size. While `left < right`, the aligned block starts before `right`, so its last index stays inside the list. A one-element list skips the loop and returns its only value.

## Time and space

Both methods take `O(log n)` time and `O(1)` extra space, assuming constant-time index access, as with an `ArrayList`. A linked list would make index access slower.


The required class stores no state. The helper only needs the input list and a few indexes. It does not copy or change the list.


## Java code

Use `Integer.equals` to compare the values returned by `List<Integer>.get`.

```java
import java.util.List;

public class SingleElementInSortedArray {

    public SingleElementInSortedArray() {
    }

    public int singleNonDuplicate(List<Integer> nums) {
        return findSingle(nums, 2);
    }

    public int singleNonTriplet(List<Integer> nums) {
        return findSingle(nums, 3);
    }

    // groupSize is 2 for pairs and 3 for triplets.
    int findSingle(List<Integer> nums, int groupSize) {
        int left = 0;
        int right = nums.size() - 1;

        while (left < right) {
            int mid = left + (right - left) / 2;

            // Align the middle index with the start of a group.
            int start = mid - mid % groupSize;
            int end = start + groupSize - 1;

            // Equal endpoints mean this sorted block is a complete group.
            if (nums.get(start).equals(nums.get(end))) {
                left = start + groupSize;
            } else {
                right = start;
            }
        }

        return nums.get(left);
    }
}
```
