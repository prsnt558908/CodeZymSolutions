# Design Stack With Peek And Pop Maximum Element in Java

#### Problem Statement
[https://codezym.com/question/186-design-stack-peek-pop-maximum-element](https://codezym.com/question/186-design-stack-peek-pop-maximum-element)


A normal stack already handles `push()`, `pop()` and `top()` in O(1). The hard part is the maximum.


The core idea is to keep every pushed value in **two places**: a **stack** that remembers the push order, and a **max heap** that always knows the biggest value.


When a value is removed from one of them, we don't search for it in the other one. We just **mark it as removed** and throw it away later, when it reaches the top. This simple trick is called **lazy deletion**, and it brings `peekMax()` and `popMax()` down from O(n) to O(log n).


We will first build a simple brute force solution, see where it is slow, and then fix it step by step.


## Solution 1: Brute Force With a Single List


Keep all values in one list. The end of the list is the top of the stack.


`push()`, `pop()` and `top()` only touch the end of the list, so they are O(1). For the maximum, we scan the whole list.

```java
List<Integer> stack = new ArrayList<>();

// O(n): check every value
public int peekMax() {
    int max = stack.get(0);
    for (int value : stack) {
        max = Math.max(max, value);
    }
    return max;
}

// O(n): find the max closest to the top and remove it
public int popMax() {
    int max = peekMax();
    // walk from the top, so the first match is the one closest to the top
    for (int i = stack.size() - 1; i >= 0; i--) {
        if (stack.get(i) == max) {
            stack.remove(i);
            break;
        }
    }
    return max;
}
```


### Problems With This Approach


- `peekMax()` and `popMax()` look at every value, so each call is O(n).
- `popMax()` also removes from the middle of the list, which shifts all values after it.


You may know the **Min Stack** trick of keeping a second stack of running maximums. It makes `peekMax()` O(1). But `popMax()` still has to dig the max out from the middle of the stack, so it stays O(n).


## Solution 2: Stack + Max Heap With Lazy Deletion


### Step 1: A Max Heap for the Maximum


A max heap (priority queue) always keeps the biggest value on top. Reading it is O(1) and removing it is O(log n).


We still need a stack for `push()`, `pop()` and `top()`. So every pushed value goes into **both** the stack and the heap.


### Step 2: The Removal Problem


Now each value lives in two places.

- `pop()` removes a value from the stack, but the same value is still somewhere inside the heap.
- `popMax()` removes a value from the heap, but the same value is still somewhere inside the stack.


Finding it in the middle of the other structure is O(n), which is exactly what we wanted to avoid.


### Step 3: Lazy Deletion


Notice that the stack only ever looks at its **top** item. The heap also only looks at its **top** item.


So we don't need to delete the other copy right away. We just **mark it as removed**.


Later, whenever a removed item shows up on top of the stack or the heap, we throw it away and look at the next one. Each item is thrown away at most once from each structure, so this cleanup stays cheap.


### Step 4: Equal Maximum Values


If the max appears many times, `popMax()` must remove the one closest to the top.


So every pushed item gets an `id`: 0, 1, 2 and so on. An item pushed later has a bigger `id`, which means it is closer to the top.


The heap orders items like this:

1. Bigger value comes out first.
2. For equal values, bigger `id` comes out first.


### Why We Need the `StackItem` Class


`StackItem` holds three things:

- `value`: the pushed number.
- `id`: the push order, used to break ties in the heap.
- `removed`: becomes `true` when the item is removed by `pop()` or `popMax()`.


The **same object** is added to both the stack and the heap. So when `pop()` sets `removed = true`, the heap sees it too, and the other way round. We don't need any extra set or map to remember removed items.


### Dry Run (Example 1)


`4#2` means value `4` with id `2`. A ~~crossed~~ item is marked removed but not thrown away yet.

| Operation | Stack (bottom → top) | Heap (first out → last out) | Output |
|---|---|---|---|
| `push(4)` | 4#0 | 4#0 | |
| `push(2)` | 4#0, 2#1 | 4#0, 2#1 | |
| `push(4)` | 4#0, 2#1, 4#2 | 4#2, 4#0, 2#1 | |
| `top()` | 4#0, 2#1, 4#2 | 4#2, 4#0, 2#1 | 4 |
| `popMax()` | 4#0, 2#1, ~~4#2~~ | 4#0, 2#1 | 4 |
| `top()` | 4#0, 2#1 | 4#0, 2#1 | 2 |
| `peekMax()` | 4#0, 2#1 | 4#0, 2#1 | 4 |
| `pop()` | 4#0 | 4#0, ~~2#1~~ | 2 |
| `top()` | 4#0 | 4#0, ~~2#1~~ | 4 |


`popMax()` picks `4#2` over `4#0` because it was pushed later. The crossed `4#2` is thrown away from the stack only when the next `top()` needs the real top.


### How Each Operation Works


- `push(x)`: create a `StackItem` with the next id. Add it to the stack and the heap.
- `pop()`: clean the stack top, pop the item and mark it removed.
- `top()`: clean the stack top and return its value.
- `peekMax()`: clean the heap top and return its value.
- `popMax()`: clean the heap top, poll the item and mark it removed.


"Clean the top" means: while the top item is marked removed, throw it away.


### Code

```java
import java.util.*;

/**
 * One pushed value.
 * The same object is stored in both the stack and the heap,
 * so marking it removed in one place is seen by the other.
 */
class StackItem {
    int value;
    int id;          // push order, a bigger id means closer to the top
    boolean removed; // true once removed by pop() or popMax()

    StackItem(int value, int id) {
        this.value = value;
        this.id = id;
        this.removed = false;
    }
}

public class MaxStack {

    // Items in push order. The last pushed item is on top.
    Deque<StackItem> stack;

    // The same items, ordered so that the max value is on top.
    PriorityQueue<StackItem> maxHeap;

    // id for the next pushed item
    int nextId;

    public MaxStack() {
        stack = new ArrayDeque<>();
        maxHeap = new PriorityQueue<>((a, b) -> {
            // bigger value comes out first
            if (a.value != b.value) {
                return Integer.compare(b.value, a.value);
            }
            // same value: the item pushed later is closer to the top, so it comes out first
            return Integer.compare(b.id, a.id);
        });
        nextId = 0;
    }

    public void push(int x) {
        StackItem item = new StackItem(x, nextId);
        nextId++;
        stack.push(item);
        maxHeap.add(item);
    }

    public int pop() {
        cleanStackTop();
        StackItem item = stack.pop();
        item.removed = true; // the heap will throw it away later
        return item.value;
    }

    public int top() {
        cleanStackTop();
        return stack.peek().value;
    }

    public int peekMax() {
        cleanHeapTop();
        return maxHeap.peek().value;
    }

    public int popMax() {
        cleanHeapTop();
        StackItem item = maxHeap.poll();
        item.removed = true; // the stack will throw it away later
        return item.value;
    }

    // Throws away items on top of the stack that were already removed by popMax().
    void cleanStackTop() {
        while (!stack.isEmpty() && stack.peek().removed) {
            stack.pop();
        }
    }

    // Throws away items on top of the heap that were already removed by pop().
    void cleanHeapTop() {
        while (!maxHeap.isEmpty() && maxHeap.peek().removed) {
            maxHeap.poll();
        }
    }
}
```


## Complexity


Let n be the number of pushed values.

| Operation | Time |
|---|---|
| `push()` | O(log n) |
| `pop()` | O(1) amortized |
| `top()` | O(1) amortized |
| `peekMax()` | O(log n) amortized |
| `popMax()` | O(log n) amortized |


**Amortized** means a single call can sometimes do extra cleanup. But each item is thrown away at most once from each structure, so the total cleanup work stays small when spread over all calls.


**Space:** O(n). A removed item may stay inside the other structure for a while, but each item is stored at most once in each.