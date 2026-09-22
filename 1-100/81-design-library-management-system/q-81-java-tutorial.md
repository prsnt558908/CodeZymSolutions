

# Design a Library Management System, Java Solution

#### Problem Statement
[https://codezym.com/question/81-design-library-management-system](https://codezym.com/question/81-design-library-management-system)

A library system sounds big, but it is really just bookkeeping. We only need to track three things well: which books exist, who currently holds a copy, and who is waiting in line for one.

Once you see it that way, the design becomes two small classes, `Book` and `User`, that each keep track of their own side of the relationship, plus a simple FIFO queue for the waitlist.

This problem does not need a heavyweight design pattern. It is tempting to reach for the Strategy pattern for the fine calculation, so different fine policies could be swapped in later, or the State pattern for a book copy's life cycle: available, issued, held. Both sound reasonable on paper, but this problem defines exactly one fine rule and exactly three simple states that only need a membership check, not different behavior per state. Adding a class hierarchy for either would add files without adding clarity.

The one pattern that does show up naturally, without being forced, is a Facade. The `LibraryManagementSystem` class exposes seven simple methods while the actual bookkeeping happens inside `Book` and `User`, so a caller never has to know how the internals are wired.

## Starting With The Obvious Approach

A first attempt might keep everything in plain lists. Books go into a `List<Book>`, users into a `List<User>`, and every borrow gets appended to one big list of records such as `(userId, bookId, borrowDay)`.

Finding a book by its id then means scanning the whole list of books. Checking if a user is already waiting for a particular book means scanning the whole waitlist. Answering "which books does this user currently have" means scanning every record ever created, including the ones for books that were already returned long ago.

This works fine for a handful of books, but it gets slow as the library grows. Every call, adding a book, borrowing, returning, or running an audit, pays the cost of scanning a list from the front. The waitlist is the worst case: a FIFO queue that also needs a fast "is this user already in it" check cannot be a plain list without scanning it on every single borrow request.

The fix is not a cleverer algorithm. It is picking the right data structure for each question the system needs to answer quickly.

## A Better Design

Every method in this problem is really just a lookup or a membership check in disguise. So instead of lists, we use hash maps and hash sets, and we let `Book` and `User` each remember their own side of a relationship.

**Two lookup maps for direct access**

`booksById` maps a `bookId` to its `Book` object, and `users` maps a `userId` to its `User` object. A third map, `catalogByTitleAuthor`, remembers books by their `(title, author)` pair so `addBook` can tell instantly whether a book already exists and just needs more copies.

**Bidirectional bookkeeping instead of one shared log**

Instead of one giant list of "who has what", each `Book` keeps a small map of the users who currently hold it, called `issuedTo`, mapping `userId` to the day they borrowed it. Each `User` keeps a small set of the books it currently holds, called `issuedBooks`. The two mirror each other.

This is the single most useful idea in this design. `usersHavingBook` only has to read one `Book`'s own map. `booksIssuedToUser` only has to read one `User`'s own set. Neither one has to scan every book or every user in the system, and neither result needs to be filtered to remove stale, already-returned entries, because an entry is removed the moment it stops being true.

**A queue and a set, combined, for the waitlist**

A waitlist needs two things at once: a strict first-in-first-out order, and a fast way to check if someone is already on it. A plain `Queue` gives the first but not the second, so it is paired with a plain `HashSet` that always holds the same userIds. Adding or removing a user updates both together, and checking "is this user already waitlisted" becomes a plain set lookup instead of a scan.

This is a good example of combining two very ordinary structures to get the behavior of a fancier one, instead of reaching for something like a `LinkedHashSet` or a custom ordered structure.

**Held copies as a plain set**

When a copy is returned while a waitlist exists, that copy is not free for anyone, it is reserved for one specific person. A `Set<String>` of userIds on the `Book` is enough to represent this: if a user's id is in that set, a copy is waiting for them. There is no need to track individual physical copies by number, since the system only ever cares about counts and who a copy belongs to.

Put together, the number of copies truly available to anyone is just:

```
available = totalCopies - issuedTo.size() - heldForUsers.size()
```

## Generating Book IDs

Every distinct `(title, author)` pair needs a unique id shaped like `<PREFIX><NUMBER>`, for example `ROW1000`. The prefix comes from the first three letters of the author's last name, uppercased, or the whole last name if it has fewer than three letters. So `Rowling` becomes `ROW`, while `Li` stays `LI` and `O` stays `O`.

The number is a 4-digit counter that starts at `1000` and is shared by every book whose author's last name produces that same prefix. A small map, `nextSequenceForPrefix`, remembers the next number to hand out for each prefix.

Adding a book with a brand-new `(title, author)` pair reads the next number for its prefix, uses it, and increments it for the next book that shares the same prefix. Adding a book with a pair that already exists just increases its copy count and returns the id it already has.

## Walking Through Each Operation

**addBook(title, author, copies)**

Checks that `title` and `author` are non-empty and `copies` is positive. If the `(title, author)` pair already exists, it adds to the existing copy count and returns the existing id. Otherwise it builds a new id from the author's last name and stores a new `Book`.

**registerUser(userId, name) and unregisterUser(userId)**

Registration is a simple insert into the `users` map after checking for empty input and duplicates. Unregistering first checks the user's own `issuedBooks` and `waitlistedBooks` sets, both direct O(1) checks, so a user with anything outstanding is rejected before anything is removed.

**requestBorrow(userId, bookId, requestDay)**

After the basic existence and day checks, the order of checks follows the priority the problem describes:

1. If the user already holds this book, reject with `ALREADY_ISSUED_TO_USER`.
2. If a copy is being held for this exact user, hand it to them right away. This takes priority over everything else, including their own place in the waitlist, since a held copy already belongs to them.
3. If the user is already waiting for this book, reject with `ALREADY_WAITLISTED`.
4. If a copy is genuinely available, issue it.
5. Otherwise, add the user to the waitlist and report their position.

**returnBook(userId, bookId, returnDay)**

Looks up the day the user borrowed the book, which is why `issuedTo` stores a day and not just a flag, validates the return day, then removes the issue record on both the `Book` and the `User` side. The fine follows the rule directly: no charge for the first 14 days, then 20 rupees for each day after that.

If the waitlist is not empty, the FIFO head is dequeued and the returned copy becomes held for that person. More than one copy of the same book can end up held for different people at the same time, if multiple copies are returned while a waitlist is building up. This is handled naturally since `heldForUsers` is a set, not a single value.

**usersHavingBook(bookId) and booksIssuedToUser(userId)**

Both are direct reads of the bidirectional bookkeeping described above, followed by a sort for a deterministic order. An unknown id simply returns an empty list.

## Final Java Solution

```java
import java.util.*;

/**
 * In-memory Library Management System.
 *
 * Design summary:
 * - Book and User each remember their own side of the borrow relationship,
 *   so audits and validations are O(1) lookups instead of scans.
 * - A copy's state (available, issued, held) is tracked with plain counters
 *   and sets, not a class-per-state hierarchy.
 * - The waitlist pairs a Queue (for FIFO order) with a HashSet (for fast
 *   "already waiting" checks).
 */
public class LibraryManagementSystem {

    /** One catalog entry per distinct (title, author) pair. */
    private static class Book {
        final String bookId;
        final String title;
        final String author;
        int totalCopies;

        // userId -> day borrowed, only for users who currently hold a copy.
        final Map<String, Integer> issuedTo = new HashMap<>();

        // userIds for whom a returned copy is being held.
        final Set<String> heldForUsers = new HashSet<>();

        // FIFO waitlist: queue keeps order, set gives O(1) membership checks.
        final Queue<String> waitlistQueue = new LinkedList<>();
        final Set<String> waitlistSet = new HashSet<>();

        Book(String bookId, String title, String author, int copies) {
            this.bookId = bookId;
            this.title = title;
            this.author = author;
            this.totalCopies = copies;
        }

        int availableCopies() {
            return totalCopies - issuedTo.size() - heldForUsers.size();
        }
    }

    /** A registered library member. */
    private static class User {
        final String userId;
        final String name;

        // bookIds currently issued to this user.
        final Set<String> issuedBooks = new HashSet<>();

        // bookIds this user is currently waiting for.
        final Set<String> waitlistedBooks = new HashSet<>();

        User(String userId, String name) {
            this.userId = userId;
            this.name = name;
        }
    }

    private static final int BORROW_LIMIT_DAYS = 14;
    private static final int FINE_PER_DAY = 20;

    private final Map<String, Book> catalogByTitleAuthor = new HashMap<>();
    private final Map<String, Book> booksById = new HashMap<>();
    private final Map<String, Integer> nextSequenceForPrefix = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();

    public LibraryManagementSystem() {
    }

    public String addBook(String title, String author, int copies) {
        if (title == null || title.isEmpty() || author == null || author.isEmpty()) {
            return "INVALID_INPUT";
        }
        if (copies <= 0) {
            return "INVALID_COPIES";
        }

        String catalogKey = title + "\u0001" + author;
        Book existing = catalogByTitleAuthor.get(catalogKey);
        if (existing != null) {
            existing.totalCopies += copies;
            return "BOOK_ID," + existing.bookId;
        }

        String prefix = idPrefixFor(author);
        int sequence = nextSequenceForPrefix.getOrDefault(prefix, 1000);
        nextSequenceForPrefix.put(prefix, sequence + 1);
        String bookId = prefix + sequence;

        Book book = new Book(bookId, title, author, copies);
        catalogByTitleAuthor.put(catalogKey, book);
        booksById.put(bookId, book);
        return "BOOK_ID," + bookId;
    }

    public String registerUser(String userId, String name) {
        if (userId == null || userId.isEmpty() || name == null || name.isEmpty()) {
            return "INVALID_INPUT";
        }
        if (users.containsKey(userId)) {
            return "USER_ALREADY_EXISTS";
        }
        users.put(userId, new User(userId, name));
        return "SUCCESS";
    }

    public String unregisterUser(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return "USER_NOT_FOUND";
        }
        if (!user.issuedBooks.isEmpty()) {
            return "USER_HAS_ISSUED_BOOKS";
        }
        if (!user.waitlistedBooks.isEmpty()) {
            return "USER_IN_WAITLIST";
        }
        users.remove(userId);
        return "SUCCESS";
    }

    public String requestBorrow(String userId, String bookId, int requestDay) {
        User user = users.get(userId);
        if (user == null) {
            return "USER_NOT_FOUND";
        }
        Book book = booksById.get(bookId);
        if (book == null) {
            return "BOOK_NOT_FOUND";
        }
        if (requestDay < 0) {
            return "INVALID_DAY";
        }
        if (user.issuedBooks.contains(bookId)) {
            return "ALREADY_ISSUED_TO_USER";
        }

        // A copy already held for this exact user beats everything else,
        // including their own place in line.
        if (book.heldForUsers.contains(userId)) {
            issueCopy(book, user, requestDay);
            return "ISSUED";
        }
        if (user.waitlistedBooks.contains(bookId)) {
            return "ALREADY_WAITLISTED";
        }
        if (book.availableCopies() > 0) {
            issueCopy(book, user, requestDay);
            return "ISSUED";
        }

        book.waitlistQueue.offer(userId);
        book.waitlistSet.add(userId);
        user.waitlistedBooks.add(bookId);
        return "WAITLISTED," + book.waitlistQueue.size();
    }

    public String returnBook(String userId, String bookId, int returnDay) {
        User user = users.get(userId);
        if (user == null) {
            return "USER_NOT_FOUND";
        }
        Book book = booksById.get(bookId);
        if (book == null) {
            return "BOOK_NOT_FOUND";
        }
        Integer borrowDay = book.issuedTo.get(userId);
        if (borrowDay == null) {
            return "NOT_ISSUED_TO_USER";
        }
        if (returnDay < 0 || returnDay < borrowDay) {
            return "INVALID_DAY";
        }

        book.issuedTo.remove(userId);
        user.issuedBooks.remove(bookId);

        int fine = calculateFine(borrowDay, returnDay);

        // Hand the copy straight to the next person in line, if any.
        if (!book.waitlistQueue.isEmpty()) {
            String nextUserId = book.waitlistQueue.poll();
            book.waitlistSet.remove(nextUserId);
            User nextUser = users.get(nextUserId);
            if (nextUser != null) {
                nextUser.waitlistedBooks.remove(bookId);
            }
            book.heldForUsers.add(nextUserId);
        }

        return "RETURNED," + fine;
    }

    public List<String> usersHavingBook(String bookId) {
        Book book = booksById.get(bookId);
        if (book == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(book.issuedTo.keySet());
        Collections.sort(result);
        return result;
    }

    public List<String> booksIssuedToUser(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(user.issuedBooks);
        Collections.sort(result);
        return result;
    }

    private void issueCopy(Book book, User user, int day) {
        book.heldForUsers.remove(user.userId);
        book.issuedTo.put(user.userId, day);
        user.issuedBooks.add(book.bookId);
    }

    private int calculateFine(int borrowDay, int returnDay) {
        int borrowDuration = returnDay - borrowDay;
        if (borrowDuration <= BORROW_LIMIT_DAYS) {
            return 0;
        }
        int delayDays = borrowDuration - BORROW_LIMIT_DAYS;
        return delayDays * FINE_PER_DAY;
    }

    private String idPrefixFor(String author) {
        String[] parts = author.trim().split("\\s+");
        String lastName = parts[parts.length - 1].toUpperCase();
        return lastName.length() <= 3 ? lastName : lastName.substring(0, 3);
    }
}
```

## Complexity

`addBook`, `registerUser`, `unregisterUser`, `requestBorrow`, and `returnBook` each run in O(1) time on average, since they only touch hash maps, hash sets, and a queue. The two audit methods, `usersHavingBook` and `booksIssuedToUser`, run in O(k log k) time, where k is the number of matching entries, because of the final sort that makes their output order deterministic.