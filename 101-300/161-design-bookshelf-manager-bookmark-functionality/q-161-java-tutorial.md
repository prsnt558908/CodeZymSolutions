# Design BookShelf Manager with Bookmark Functionality in Java

#### Problem Statement
[https://codezym.com/question/161-design-bookshelf-manager-bookmark-functionality](https://codezym.com/question/161-design-bookshelf-manager-bookmark-functionality)


## Core Idea

The shelf is just an ordered list of book names, so an `ArrayList<String>` is enough to store it. The real challenge is the bookmark: after every add, remove or move, it must keep pointing to the same book, even when two books share a name.

The trick is to never search for the bookmarked book. We keep its index in a plain `int` and shift that index with one or two lines of math whenever the shelf changes. Moving books is just a remove followed by an add, so it can reuse the same rules.

No design pattern is needed here. This is a small class with one job, and one list plus one integer is the simplest correct design. Observer looks tempting at first, and we will see why it is overkill.


## Approach 1: Search for the Bookmarked Book (Simple but Slow)

The easiest idea is to remember which book is bookmarked and search for it whenever `getBookmarkIndex()` is called.

But we cannot remember just the name, because names can repeat. On the shelf `["A", "B", "A"]` with the bookmark at index `2`, `indexOf("A")` returns `0`, which is the wrong `"A"`.

So we wrap every name in its own `Book` object. Two books with the same name are still two different objects. We keep a reference to the bookmarked object and compare by reference (`==`), not by name.

```java
class Book {
    String name;

    Book(String name) {
        this.name = name;
    }
}
```

```java
List<Book> shelf = new ArrayList<>();
Book bookmarkedBook = null;

public void setBookmarkIndex(int index) {
    bookmarkedBook = shelf.get(index);
}

public int getBookmarkIndex() {
    // Compare references (==), not names, so duplicates work correctly
    for (int i = 0; i < shelf.size(); i++) {
        if (shelf.get(i) == bookmarkedBook) {
            return i;
        }
    }
    return -1; // not found: the bookmarked book was removed
}
```

Adding, removing and moving are now plain list operations. The bookmark never needs an update, because the object reference stays valid wherever the book goes.

### Problems with this approach

- `getBookmarkIndex()` scans the whole shelf on every call. That is O(n) for a shelf of up to 100,000 books.
- Every book needs an extra wrapper object, only to tell duplicates apart.

But we already know the bookmark index when it is set. If we track how each change shifts it, we never need to search.


## Approach 2: Shift the Bookmark Index with Simple Math (Final Solution)

We store the shelf as an `ArrayList<String>` and the bookmark as `int bookmarkIndex`, where `-1` means no bookmark. This index is all `getBookmarkIndex()` needs, so that call becomes O(1).

Now we only need three small rules.

### Rule 1: Adding books

When `k` books are inserted at `toIndex`, every book from `toIndex` onwards moves `k` places to the right.

So if `toIndex <= bookmarkIndex`, we add `k` to the bookmark. Otherwise it stays the same.

Example: shelf `["A", "B", "C", "D"]`, bookmark on `"C"` at index `2`. Add `["X", "Y"]` at index `1`. Since `1 <= 2`, the bookmark becomes `2 + 2 = 4`, and the shelf is `["A", "X", "Y", "B", "C", "D"]`.

### Rule 2: Removing books

When books in `[fromIndex, toIndex)` are removed, the bookmark falls into one of three cases.

| Bookmark position | New bookmark |
|---|---|
| Before `fromIndex` | No change |
| Inside `[fromIndex, toIndex)` | `-1`, the book was removed |
| At or after `toIndex` | Moves left by `toIndex - fromIndex` |

### Rule 3: Moving books

A move is a remove followed by an add. So `moveBooks()` copies the block, calls `removeBooks()`, then calls `addBooks()`. Rules 1 and 2 then update the bookmark automatically.

There is one special case: the bookmarked book is inside the moving block. Here `removeBooks()` would unset the bookmark. So before removing, we save its position inside the block, `offset = bookmarkIndex - fromIndex`. After the move, the bookmark is `toIndex + offset`.

Let's trace `moveBooks(1, 3, 2)` on `["A", "B", "C", "D", "E"]` with the bookmark on `"D"` at index `3`.

1. Remove `["B", "C"]`. `"D"` was after the removed range, so the bookmark moves left by 2, to index `1`. Shelf: `["A", "D", "E"]`.
2. Add `["B", "C"]` at index `3`. Since `3 > 1`, the bookmark stays at `1`. Shelf: `["A", "D", "E", "B", "C"]`.

### Why ArrayList?

`ArrayList` keeps the books in order and can insert or delete a whole block in one call: `addAll(index, list)` and `subList(from, to).clear()`. Internally, both use a fast bulk memory copy to shift the elements.

These calls are O(n) in the worst case. A complex tree structure could make them faster, but `getBooks()` must return all `n` books anyway, so it is not worth the extra code.

### Why not the Observer pattern?

Observer looks like a fit: the bookmark could listen to the shelf and update itself after every change.

But there is only one bookmark, and it lives inside the shelf manager. A listener interface, a list of listeners and event calls would add extra classes just to update one integer.

Observer starts to pay off only when many separate objects, like bookmarks of many users or a screen showing the shelf, must react to changes.

### Complexity

Here `n` is the number of books on the shelf and `k` is the number of books being added or moved.

| Method | Time |
|---|---|
| `addBooks` | O(n + k) |
| `removeBooks` | O(n) |
| `moveBooks` | O(n + k) |
| `getBooks` | O(n) |
| `setBookmarkIndex` | O(1) |
| `getBookmarkIndex` | O(1) |

### Java Code

```java
import java.util.*;

/**
 * Keeps an ordered shelf of books and one bookmark.
 * The bookmark is stored as an index and shifted with simple math
 * every time books are added, removed or moved.
 */
public class BookShelfManager {

    // Books in shelf order: list index = position on the shelf
    List<String> shelf;

    // Index of the bookmarked book, -1 when no bookmark is set
    int bookmarkIndex;

    public BookShelfManager() {
        shelf = new ArrayList<>();
        bookmarkIndex = -1;
    }

    /**
     * Inserts all books starting at toIndex, keeping their order.
     */
    public void addBooks(int toIndex, List<String> books) {
        shelf.addAll(toIndex, books);

        // Books inserted at or before the bookmark push it to the right
        if (bookmarkIndex != -1 && toIndex <= bookmarkIndex) {
            bookmarkIndex += books.size();
        }
    }

    /**
     * Removes books in the range [fromIndex, toIndex).
     */
    public void removeBooks(int fromIndex, int toIndex) {
        shelf.subList(fromIndex, toIndex).clear();

        if (bookmarkIndex == -1 || bookmarkIndex < fromIndex) {
            return; // no bookmark, or it is before the removed range
        }

        if (bookmarkIndex < toIndex) {
            bookmarkIndex = -1; // the bookmarked book itself was removed
        } else {
            bookmarkIndex -= toIndex - fromIndex; // it was after the range, shift left
        }
    }

    /**
     * Moves size books starting at fromIndex so that they start at toIndex.
     * toIndex is an index in the shelf after the moving books are taken out.
     */
    public void moveBooks(int fromIndex, int toIndex, int size) {
        int endIndex = fromIndex + size;

        // Is the bookmarked book one of the moving books?
        boolean bookmarkIsMoving = bookmarkIndex >= fromIndex && bookmarkIndex < endIndex;
        int offset = bookmarkIndex - fromIndex; // its position inside the moving block

        // Copy the moving books before deleting them
        List<String> movingBooks = new ArrayList<>(shelf.subList(fromIndex, endIndex));

        // A move is a remove followed by an add.
        // These two calls also update the bookmark when it is not moving.
        removeBooks(fromIndex, endIndex);
        addBooks(toIndex, movingBooks);

        // removeBooks() unset the bookmark, so put it at its new position
        if (bookmarkIsMoving) {
            bookmarkIndex = toIndex + offset;
        }
    }

    /**
     * Returns a copy, so callers cannot change the shelf by mistake.
     */
    public List<String> getBooks() {
        return new ArrayList<>(shelf);
    }

    public void setBookmarkIndex(int index) {
        bookmarkIndex = index;
    }

    public int getBookmarkIndex() {
        return bookmarkIndex;
    }
}
```