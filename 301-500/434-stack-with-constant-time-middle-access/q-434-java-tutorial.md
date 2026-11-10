# Stack with Constant-Time Middle Access in Java

#### Problem Statement
[https://codezym.com/question/434-stack-with-constant-time-middle-access](https://codezym.com/question/434-stack-with-constant-time-middle-access)

A normal stack only lets us look at the top. To find the middle, we would have to walk through half of the elements, and that is slow.

The good news is that the middle is always at a spot we can compute from the size alone. So we just need a way to reach that spot without walking.

We will see two ways to do it. The first keeps the elements in an `ArrayList` and reads the middle directly by its index. The second uses a doubly linked list and keeps a `middle` pointer that moves at most one step on every push or pop.


## Where Is the Middle?

The problem says the middle is at position `floor(n / 2) + 1` from the bottom, counting from 1.

If we count from 0 instead, that is simply index `n / 2` (integer division).

For example, in `[8, 3, 14, 6]` we have `n = 4`, so the middle is at index `2`, which is `14`. When `n` is even, index `n / 2` picks the middle element closer to the top, exactly as the problem asks.


## Basic Solution: Walk to the Middle

With a normal stack like `ArrayDeque`, `push`, `pop` and `getTop` are easy because they only touch the top.

The hard part is `getMiddle`. We have to start at the bottom and skip `size / 2` elements.

```java
Deque<Integer> stack = new ArrayDeque<>();   // the front of the deque is the top

public int getMiddle() {
    // descendingIterator() walks from the bottom to the top
    Iterator<Integer> it = stack.descendingIterator();
    for (int i = 0; i < stack.size() / 2; i++) {
        it.next();   // skip the elements below the middle
    }
    return it.next();
}
```

**The problem:** every `getMiddle` call walks through half of the stack, so it takes `O(n)` time.

With up to 100,000 method calls, this can add up to more than a billion steps. We need to reach the middle without walking.


## Solution 1: ArrayList with Direct Index Access

The basic solution is slow only because `ArrayDeque` cannot jump to a position. An `ArrayList` can.

So we keep the elements from bottom to top in an `ArrayList`:

- `push` adds the value at the end of the list.
- `pop` removes and returns the last element.
- `getTop` reads the last element.
- `getMiddle` reads the element at index `size / 2`.

**Why an `ArrayList`?** It keeps the elements in order and reads any index in `O(1)`. Adding and removing at the end is also cheap, and that is all a stack needs.

### Java Code

```java
import java.util.*;

public class MiddleStack {

    // Elements from bottom (index 0) to top (last index)
    List<Integer> items;

    public MiddleStack() {
        items = new ArrayList<>();
    }

    public void push(int value) {
        items.add(value);
    }

    public int pop() {
        // remove(int index) removes and returns the element at that index
        return items.remove(items.size() - 1);
    }

    public int getMiddle() {
        // Position floor(n / 2) + 1 (from 1) is index n / 2 (from 0)
        return items.get(items.size() / 2);
    }

    public int getTop() {
        return items.get(items.size() - 1);
    }
}
```

### Complexity

- `pop`, `getMiddle`, `getTop`: `O(1)`.
- `push`: `O(1)` amortized. Once in a while the list runs out of room and copies every element into a bigger array, so that one push is slow.
- Space: `O(n)`.

That occasional slow push is the only weak spot. The next solution never copies elements.


## Solution 2: Doubly Linked List with a Middle Pointer

Instead of searching for the middle when asked, we can **keep a pointer on the middle node at all times**.

Each push or pop changes the size by one, so the middle moves by at most one step. Moving one step is just `middle.next` (up) or `middle.prev` (down).

**Why a doubly linked list?** Each `Node` holds one value and two links: `prev` points to the node below and `next` points to the node above.

- On `pop`, the `top` pointer moves down, so we need `prev`.
- The `middle` pointer moves up on some pushes and down on some pops, so it needs both `next` and `prev`.

A singly linked list only has one direction, so it is not enough.

### When Does the Middle Move?

Let's trace Example 1 and add one extra `pop()` at the end.

| Operation | Stack (bottom to top) | Size | Middle index | Middle pointer |
|---|---|---|---|---|
| `push(8)` | `[8]` | 1 | 0 | set to 8 |
| `push(3)` | `[8, 3]` | 2 | 1 | moves up to 3 |
| `push(14)` | `[8, 3, 14]` | 3 | 1 | stays on 3 |
| `push(6)` | `[8, 3, 14, 6]` | 4 | 2 | moves up to 14 |
| `pop()` | `[8, 3, 14]` | 3 | 1 | moves down to 3 |
| `pop()` | `[8, 3]` | 2 | 1 | stays on 3 |

The middle index is `size / 2`. It goes up only when a push makes the size even, and it goes down only when a pop starts from an even size.

So the rules are:

- **push:** put the new node on top. If the new size is even, move `middle` one step up.
- **pop:** if the current size is even, move `middle` one step down. Then remove the top node.
- The first push makes the new node the middle. When the last node is popped, `middle` becomes `null`.

This also works when the stack has 2 elements and the middle is the top itself. The middle steps down to the bottom node before the top is removed.

### Java Code

```java
// One element of the stack
class Node {
    int value;
    Node prev;   // element just below
    Node next;   // element just above

    Node(int value) {
        this.value = value;
    }
}

public class MiddleStack {

    Node top;      // most recently pushed element
    Node middle;   // element at index size / 2, counted from the bottom
    int size;

    public MiddleStack() {
        top = null;
        middle = null;
        size = 0;
    }

    public void push(int value) {
        Node node = new Node(value);
        if (top == null) {
            middle = node;  // the first element is also the middle
        } else {
            top.next = node;
            node.prev = top;
        }
        top = node;
        size++;

        // The size just became even, so the middle moves one step up
        if (size % 2 == 0) {
            middle = middle.next;
        }
    }

    public int pop() {
        int value = top.value;

        // The size is even and will become odd, so the middle moves down
        if (size % 2 == 0) {
            middle = middle.prev;
        }

        top = top.prev;
        if (top == null) {
            middle = null;      // the stack is empty now
        } else {
            top.next = null;    // unlink the removed node
        }
        size--;
        return value;
    }

    public int getMiddle() {
        return middle.value;
    }

    public int getTop() {
        return top.value;
    }
}
```

### Complexity

- `push`, `pop`, `getMiddle`, `getTop`: `O(1)` in the worst case. Each call only changes a few pointers.
- Space: `O(n)` for the nodes.


## Which One Should You Use?

**Solution 1** is the shortest and the easiest to explain. It is a perfectly good answer for this problem.

**Solution 2** is the better pick when every single call must be `O(1)`, or when the interviewer adds a follow-up like "also delete the middle element". The linked list can unlink the middle node in `O(1)`, while an `ArrayList` would have to shift elements in `O(n)`.