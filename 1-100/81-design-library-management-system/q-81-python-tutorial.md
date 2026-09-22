# Design a Library Management System, Python Solution

#### Problem Statement
[https://codezym.com/question/81-design-library-management-system](https://codezym.com/question/81-design-library-management-system)

A library system sounds big, but it is really just bookkeeping. We only need to track three things well: which books exist, who currently holds a copy, and who is waiting in line for one.

Once you see it that way, the design becomes two small helper classes, `Book` and `User`, that each keep track of their own side of the relationship, plus a simple FIFO queue for the waitlist.

This problem does not need a heavyweight design pattern. It is tempting to reach for the Strategy pattern for the fine calculation, so different fine policies could be swapped in later, or the State pattern for a book copy's life cycle: available, issued, held. Both sound reasonable on paper, but this problem defines exactly one fine rule and exactly three simple states that only need a membership check, not different behavior per state. Adding a class hierarchy for either would add files without adding clarity.

The one pattern that does show up naturally, without being forced, is a Facade. The `LibraryManagementSystem` class exposes seven simple methods while the actual bookkeeping happens inside `Book` and `User`, so a caller never has to know how the internals are wired.

## Starting With The Obvious Approach

A first attempt might keep everything in plain lists. Books go into a list of `Book` objects, users into a list of `User` objects, and every borrow gets appended to one big list of records such as `(userId, bookId, borrowDay)`.

Finding a book by its id then means scanning the whole list of books. Checking if a user is already waiting for a particular book means scanning the whole waitlist. Answering "which books does this user currently have" means scanning every record ever created, including the ones for books that were already returned long ago.

This works fine for a handful of books, but it gets slow as the library grows. Every call, adding a book, borrowing, returning, or running an audit, pays the cost of scanning a list from the front. The waitlist is the worst case: a FIFO queue that also needs a fast "is this user already in it" check cannot be a plain list without scanning it on every single borrow request.

The fix is not a cleverer algorithm. It is picking the right data structure for each question the system needs to answer quickly.

## A Better Design

Every method in this problem is really just a lookup or a membership check in disguise. So instead of lists, we use dictionaries and sets, and we let `Book` and `User` each remember their own side of a relationship.

**Two lookup dictionaries for direct access**

`_books_by_id` maps a `bookId` to its `Book` object, and `_users` maps a `userId` to its `User` object. A third dictionary, `_catalog_by_title_author`, remembers books by their `(title, author)` pair so `addBook` can tell instantly whether a book already exists and just needs more copies. Python lets us use the `(title, author)` tuple itself as the key, so there is no need for any separator trick to combine the two into one string.

**Bidirectional bookkeeping instead of one shared log**

Instead of one giant list of "who has what", each `Book` keeps a small dictionary of the users who currently hold it, called `issued_to`, mapping `userId` to the day they borrowed it. Each `User` keeps a small set of the books it currently holds, called `issued_books`. The two mirror each other.

This is the single most useful idea in this design. `usersHavingBook` only has to read one `Book`'s own dictionary. `booksIssuedToUser` only has to read one `User`'s own set. Neither one has to scan every book or every user in the system, and neither result needs to be filtered to remove stale, already-returned entries, because an entry is removed the moment it stops being true.

**A queue and a set, combined, for the waitlist**

A waitlist needs two things at once: a strict first-in-first-out order, and a fast way to check if someone is already on it. A plain `deque` gives the first but not the second, so it is paired with a plain `set` that always holds the same userIds. Adding or removing a user updates both together, and checking "is this user already waitlisted" becomes a plain set lookup instead of a scan.

This is a good example of combining two very ordinary structures to get the behavior of a fancier one, instead of reaching for something like an ordered-dict used as a set, or a custom ordered-set class.

**Held copies as a plain set**

When a copy is returned while a waitlist exists, that copy is not free for anyone, it is reserved for one specific person. A `set` of userIds on the `Book` is enough to represent this: if a user's id is in that set, a copy is waiting for them. There is no need to track individual physical copies by number, since the system only ever cares about counts and who a copy belongs to.

Put together, the number of copies truly available to anyone is just:

```
available = total_copies - len(issued_to) - len(held_for_users)
```

## Generating Book IDs

Every distinct `(title, author)` pair needs a unique id shaped like `<PREFIX><NUMBER>`, for example `ROW1000`. The prefix comes from the first three letters of the author's last name, uppercased, or the whole last name if it has fewer than three letters. So `Rowling` becomes `ROW`, while `Li` stays `LI` and `O` stays `O`.

Python's `str.split()` with no arguments already splits on any run of whitespace and ignores leading or trailing spaces, so pulling out the last name is a one-liner: `author.split()[-1]`.

The number is a 4-digit counter that starts at `1000` and is shared by every book whose author's last name produces that same prefix. A small dictionary, `_next_sequence_for_prefix`, remembers the next number to hand out for each prefix.

Adding a book with a brand-new `(title, author)` pair reads the next number for its prefix, uses it, and increments it for the next book that shares the same prefix. Adding a book with a pair that already exists just increases its copy count and returns the id it already has.

## Walking Through Each Operation

The public methods keep the exact camelCase names this problem expects, `addBook`, `requestBorrow`, and so on, while everything internal to the class follows normal Python style.

**addBook(title, author, copies)**

Checks that `title` and `author` are non-empty and `copies` is positive. If the `(title, author)` pair already exists, it adds to the existing copy count and returns the existing id. Otherwise it builds a new id from the author's last name and stores a new `Book`.

**registerUser(userId, name) and unregisterUser(userId)**

Registration is a simple insert into the `_users` dictionary after checking for empty input and duplicates. Unregistering first checks the user's own `issued_books` and `waitlisted_books` sets, both direct O(1) checks, so a user with anything outstanding is rejected before anything is removed.

**requestBorrow(userId, bookId, requestDay)**

After the basic existence and day checks, the order of checks follows the priority the problem describes:

1. If the user already holds this book, reject with `ALREADY_ISSUED_TO_USER`.
2. If a copy is being held for this exact user, hand it to them right away. This takes priority over everything else, including their own place in the waitlist, since a held copy already belongs to them.
3. If the user is already waiting for this book, reject with `ALREADY_WAITLISTED`.
4. If a copy is genuinely available, issue it.
5. Otherwise, add the user to the waitlist and report their position.

**returnBook(userId, bookId, returnDay)**

Looks up the day the user borrowed the book, which is why `issued_to` stores a day and not just a flag, validates the return day, then removes the issue record on both the `Book` and the `User` side. The fine follows the rule directly: no charge for the first 14 days, then 20 rupees for each day after that.

If the waitlist is not empty, the FIFO head is dequeued and the returned copy becomes held for that person. More than one copy of the same book can end up held for different people at the same time, if multiple copies are returned while a waitlist is building up. This is handled naturally since `held_for_users` is a set, not a single value.

**usersHavingBook(bookId) and booksIssuedToUser(userId)**

Both are direct reads of the bidirectional bookkeeping described above, followed by `sorted()` for a deterministic order. An unknown id simply returns an empty list.

## Final Python Solution

```python
from collections import deque


class Book:
    """One catalog entry per distinct (title, author) pair."""

    def __init__(self, book_id, title, author, copies):
        self.book_id = book_id
        self.title = title
        self.author = author
        self.total_copies = copies

        # userId -> day borrowed, only for users who currently hold a copy.
        self.issued_to = {}

        # userIds for whom a returned copy is being held.
        self.held_for_users = set()

        # FIFO waitlist: deque keeps order, set gives O(1) membership checks.
        self.waitlist_queue = deque()
        self.waitlist_set = set()

    def available_copies(self):
        return self.total_copies - len(self.issued_to) - len(self.held_for_users)


class User:
    """A registered library member."""

    def __init__(self, user_id, name):
        self.user_id = user_id
        self.name = name

        # bookIds currently issued to this user.
        self.issued_books = set()

        # bookIds this user is currently waiting for.
        self.waitlisted_books = set()


class LibraryManagementSystem:
    """
    In-memory Library Management System.

    Design summary:
    - Book and User each remember their own side of the borrow relationship,
      so audits and validations are O(1) lookups instead of scans.
    - A copy's state (available, issued, held) is tracked with plain counters
      and sets, not a class-per-state hierarchy.
    - The waitlist pairs a deque (for FIFO order) with a set (for fast
      "already waiting" checks).
    """

    BORROW_LIMIT_DAYS = 14
    FINE_PER_DAY = 20

    def __init__(self):
        # (title, author) -> Book, used only to detect "this book already exists".
        self._catalog_by_title_author = {}
        # bookId -> Book, used for all borrow/return/audit lookups.
        self._books_by_id = {}
        # id prefix -> next free sequence number for that prefix.
        self._next_sequence_for_prefix = {}
        # userId -> User
        self._users = {}

    def addBook(self, title, author, copies):
        if not title or not author:
            return "INVALID_INPUT"
        if copies <= 0:
            return "INVALID_COPIES"

        catalog_key = (title, author)
        existing = self._catalog_by_title_author.get(catalog_key)
        if existing is not None:
            existing.total_copies += copies
            return "BOOK_ID," + existing.book_id

        prefix = self._id_prefix_for(author)
        sequence = self._next_sequence_for_prefix.get(prefix, 1000)
        self._next_sequence_for_prefix[prefix] = sequence + 1
        book_id = prefix + str(sequence)

        book = Book(book_id, title, author, copies)
        self._catalog_by_title_author[catalog_key] = book
        self._books_by_id[book_id] = book
        return "BOOK_ID," + book_id

    def registerUser(self, userId, name):
        if not userId or not name:
            return "INVALID_INPUT"
        if userId in self._users:
            return "USER_ALREADY_EXISTS"
        self._users[userId] = User(userId, name)
        return "SUCCESS"

    def unregisterUser(self, userId):
        user = self._users.get(userId)
        if user is None:
            return "USER_NOT_FOUND"
        if user.issued_books:
            return "USER_HAS_ISSUED_BOOKS"
        if user.waitlisted_books:
            return "USER_IN_WAITLIST"
        del self._users[userId]
        return "SUCCESS"

    def requestBorrow(self, userId, bookId, requestDay):
        user = self._users.get(userId)
        if user is None:
            return "USER_NOT_FOUND"
        book = self._books_by_id.get(bookId)
        if book is None:
            return "BOOK_NOT_FOUND"
        if requestDay < 0:
            return "INVALID_DAY"
        if bookId in user.issued_books:
            return "ALREADY_ISSUED_TO_USER"

        # A copy already held for this exact user beats everything else,
        # including their own place in line.
        if userId in book.held_for_users:
            self._issue_copy(book, user, requestDay)
            return "ISSUED"
        if bookId in user.waitlisted_books:
            return "ALREADY_WAITLISTED"
        if book.available_copies() > 0:
            self._issue_copy(book, user, requestDay)
            return "ISSUED"

        book.waitlist_queue.append(userId)
        book.waitlist_set.add(userId)
        user.waitlisted_books.add(bookId)
        return "WAITLISTED," + str(len(book.waitlist_queue))

    def returnBook(self, userId, bookId, returnDay):
        user = self._users.get(userId)
        if user is None:
            return "USER_NOT_FOUND"
        book = self._books_by_id.get(bookId)
        if book is None:
            return "BOOK_NOT_FOUND"
        borrow_day = book.issued_to.get(userId)
        if borrow_day is None:
            return "NOT_ISSUED_TO_USER"
        if returnDay < 0 or returnDay < borrow_day:
            return "INVALID_DAY"

        del book.issued_to[userId]
        user.issued_books.discard(bookId)

        fine = self._calculate_fine(borrow_day, returnDay)

        # Hand the copy straight to the next person in line, if any.
        if book.waitlist_queue:
            next_user_id = book.waitlist_queue.popleft()
            book.waitlist_set.discard(next_user_id)
            next_user = self._users.get(next_user_id)
            if next_user is not None:
                next_user.waitlisted_books.discard(bookId)
            book.held_for_users.add(next_user_id)

        return "RETURNED," + str(fine)

    def usersHavingBook(self, bookId):
        book = self._books_by_id.get(bookId)
        if book is None:
            return []
        return sorted(book.issued_to.keys())

    def booksIssuedToUser(self, userId):
        user = self._users.get(userId)
        if user is None:
            return []
        return sorted(user.issued_books)

    # -- helpers -----------------------------------------------------------

    def _issue_copy(self, book, user, day):
        book.held_for_users.discard(user.user_id)
        book.issued_to[user.user_id] = day
        user.issued_books.add(book.book_id)

    def _calculate_fine(self, borrow_day, return_day):
        borrow_duration = return_day - borrow_day
        if borrow_duration <= self.BORROW_LIMIT_DAYS:
            return 0
        delay_days = borrow_duration - self.BORROW_LIMIT_DAYS
        return delay_days * self.FINE_PER_DAY

    def _id_prefix_for(self, author):
        parts = author.split()
        last_name = parts[-1].upper()
        return last_name if len(last_name) <= 3 else last_name[:3]
```

## Complexity

`addBook`, `registerUser`, `unregisterUser`, `requestBorrow`, and `returnBook` each run in O(1) time on average, since they only touch dictionaries, sets, and a deque. The two audit methods, `usersHavingBook` and `booksIssuedToUser`, run in O(k log k) time, where k is the number of matching entries, because of the final sort that makes their output order deterministic.