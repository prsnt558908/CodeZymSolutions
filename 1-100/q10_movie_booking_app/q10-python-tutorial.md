# Design Movie Ticket Booking System in Python

## Problem Statement

https://codezym.com/question/10-design-movie-ticket-booking-system

The core idea of this solution is to separate the system into small managers and listers. `CinemaManager` stores cinema details, `ShowManager` stores shows, and `TicketBookingManager` handles seat booking and cancellation. The solution also uses the **Observer Design Pattern** so that whenever a new show is added, dependent listing caches like `CinemaLister` and `ShowLister` are updated automatically.

## High Level Design

In a movie ticket booking system, we mainly need to support these operations:

- Add a cinema in a city.
- Add a movie show in a cinema screen.
- Book tickets for a show.
- Cancel tickets.
- Get free seat count for a show.
- List cinemas running a movie in a city.
- List shows of a movie in a cinema.

Instead of keeping all logic inside one large class, this solution divides responsibilities across multiple classes. This makes the code easier to understand, test, and extend.

## Main Classes

### `Solution`

`Solution` is the entry point used by the platform. It initializes all managers and delegates each API call to the correct class.

For example:

- `add_cinema()` delegates to `CinemaManager`.
- `add_show()` delegates to `ShowManager`.
- `book_ticket()` delegates to `TicketBookingManager`.
- `list_cinemas()` delegates to `CinemaLister`.
- `list_shows()` delegates to `ShowLister`.

This keeps the platform-facing class simple.

### `Cinema`

`Cinema` stores basic cinema information:

- `cinema_id`
- `city_id`
- number of screens
- number of rows in each screen
- number of columns in each screen

Here, `@dataclass` is used because `Cinema` is mainly a data object.

### `Show`

`Show` represents one movie show. It stores:

- `show_id`
- `movie_id`
- `screen_index`
- `start_time`
- `end_time`
- cinema where the show is running

Each show has its own seat booking state in `TicketBookingManager`.

### `CinemaManager`

`CinemaManager` stores cinemas using a dictionary:

```python
self.cache = {}
```

The key is `cinema_id`, and the value is the `Cinema` object.

This gives fast lookup when we need to find a cinema while adding a show.

### `ShowManager`

`ShowManager` stores shows using another dictionary:

```python
self.cache = {}
```

The key is `show_id`, and the value is the `Show` object.

It also maintains a list of observers. Whenever a new show is added, it notifies all observers so that listing caches can be updated.

## Observer Design Pattern

The Observer Pattern is useful when one event should update multiple dependent components.

Here, adding a show should update:

1. The list of cinemas running a movie in a city.
2. The list of shows running a movie in a cinema.

So `ShowManager` acts as the subject, and these classes act as observers:

- `CinemaLister`
- `ShowLister`

Whenever `ShowManager.add_show()` is called, it creates the show and calls:

```python
self.notify_all(show)
```

Then both listers update their internal caches.

This avoids scanning all shows again and again during list operations.

## Listing Cinemas

`CinemaLister` stores this mapping:

```python
movie_id-city_id -> set of cinema_ids
```

A `set` is used because the same cinema can have multiple shows of the same movie, but while listing cinemas we should return each cinema only once.

When `list_cinemas(movie_id, city_id)` is called, the cinema IDs are returned in sorted order.

## Listing Shows

`ShowLister` stores this mapping:

```python
movie_id-cinema_id -> list of shows
```

A list is used because the same movie can have multiple shows in the same cinema.

When `list_shows(movie_id, cinema_id)` is called, shows are sorted by:

1. Descending `start_time`
2. Ascending `show_id` if start time is the same

Then only show IDs are returned.

## Ticket Booking Logic

`TicketBookingManager` handles all seat-related operations.

It maintains three important dictionaries:

```python
self.seats = {}
self.free_seats_count = {}
self.bookings = {}
```

### `seats`

This stores the seat layout for each show:

```python
show_id -> 2D seat matrix
```

Each seat is represented by a boolean value:

- `False` means the seat is free.
- `True` means the seat is booked.

The seat matrix is created lazily. This means seats are initialized only when the first booking happens for that show.

### `free_seats_count`

This stores how many seats are currently free for each show.

This helps us answer `get_free_seats_count()` in O(1) time.

### `bookings`

This stores ticket details:

```python
ticket_id -> Booking
```

This is needed because while cancelling a ticket, we need to know which seats were booked using that ticket.

## Seat Allocation Strategy

When booking tickets, the solution first tries to allocate continuous seats in the same row.

For example, if the user wants 3 tickets, the system first tries to find 3 adjacent free seats.

If continuous seats are not available, then it books any available seats one by one.

This is a practical strategy because users usually prefer adjacent seats, but booking should still succeed if enough non-adjacent seats are available.

## Cancellation Logic

When a ticket is cancelled:

1. The booking is found using `ticket_id`.
2. If the booking does not exist or is already cancelled, return `False`.
3. All seats belonging to the booking are marked free again.
4. Free seat count is increased.
5. Booking status is marked as cancelled.

This prevents the same ticket from being cancelled multiple times.

## Complete Python Code

```python
from collections import defaultdict
from dataclasses import dataclass
from typing import List


class Solution:
    """
    Main class exposed to the platform.

    It creates all managers/listers and delegates every API call
    to the correct component.
    """

    def __init__(self):
        self.helper = None
        self.cinema_manager = None
        self.show_manager = None
        self.cinema_lister = None
        self.show_lister = None
        self.booking_manager = None

    def init(self, helper):
        self.helper = helper

        # Listers maintain fast lookup caches for list queries.
        self.cinema_lister = CinemaLister()
        self.show_lister = ShowLister()

        # Managers store core entities and booking state.
        self.cinema_manager = CinemaManager()
        self.show_manager = ShowManager()

        # Register listers as observers of ShowManager.
        # Whenever a show is added, both listers get updated automatically.
        self.show_manager.add_observer(self.cinema_lister)
        self.show_manager.add_observer(self.show_lister)

        self.booking_manager = TicketBookingManager()

    def add_cinema(self, cinema_id, city_id, screen_count, screen_row, screen_column):
        self.cinema_manager.add_cinema(
            cinema_id,
            city_id,
            screen_count,
            screen_row,
            screen_column,
        )

    def add_show(self, show_id, movie_id, cinema_id, screen_index, start_time, end_time):
        cinema = self.cinema_manager.get_cinema(cinema_id)
        self.show_manager.add_show(
            show_id,
            movie_id,
            cinema,
            screen_index,
            start_time,
            end_time,
        )

    def book_ticket(self, ticket_id, show_id, tickets_count):
        show = self.show_manager.get_show(show_id)
        if show is None:
            return []
        return self.booking_manager.book_ticket(ticket_id, show, tickets_count)

    def cancel_ticket(self, ticket_id):
        return self.booking_manager.cancel_ticket(ticket_id)

    def get_free_seats_count(self, show_id):
        show = self.show_manager.get_show(show_id)
        if show is None:
            return 0
        return self.booking_manager.get_free_seats_count(show)

    def list_cinemas(self, movie_id, city_id):
        return self.cinema_lister.list_cinemas(movie_id, city_id)

    def list_shows(self, movie_id, cinema_id):
        return self.show_lister.list_shows(movie_id, cinema_id)


class ShowObserver:
    """
    Base observer class.

    Any class interested in show updates can implement update().
    """

    def update(self, show: 'Show'):
        pass


class ShowSubject:
    """
    Base subject class.

    A subject maintains observers and notifies them when something changes.
    """

    def add_observer(self, observer: 'ShowObserver'):
        pass

    def notify_all(self, show: 'Show'):
        pass


@dataclass
class Cinema:
    """
    Stores cinema details.
    """

    cinema_id: int
    city_id: int
    screen_count: int
    screen_row: int
    screen_column: int

    def get_cinema_id(self) -> int:
        return self.cinema_id

    def get_city_id(self) -> int:
        return self.city_id

    def get_screen_count(self) -> int:
        return self.screen_count

    def get_screen_row(self) -> int:
        return self.screen_row

    def get_screen_column(self) -> int:
        return self.screen_column


@dataclass
class Show:
    """
    Stores one movie show running inside a cinema screen.
    """

    show_id: int
    movie_id: int
    screen_index: int
    start_time: int
    end_time: int
    cinema: 'Cinema'

    def __str__(self) -> str:
        return (
            f"Show(show_id={self.show_id}, movie_id={self.movie_id}, "
            f"cinema_id={self.cinema.get_cinema_id()}, "
            f"screen_index={self.screen_index})"
        )

    def get_show_id(self) -> int:
        return self.show_id

    def get_movie_id(self) -> int:
        return self.movie_id

    def get_screen_index(self) -> int:
        return self.screen_index

    def get_start_time(self) -> int:
        return self.start_time

    def get_cinema(self) -> 'Cinema':
        return self.cinema


class CinemaManager:
    """
    Stores and retrieves cinemas by cinema_id.
    """

    def __init__(self):
        # cinema_id -> Cinema
        self.cache = {}

    def add_cinema(self, cinema_id, city_id, screen_count, screen_row, screen_column):
        cinema = Cinema(cinema_id, city_id, screen_count, screen_row, screen_column)
        self.cache[cinema_id] = cinema

    def get_cinema(self, cinema_id):
        return self.cache.get(cinema_id)


class ShowManager:
    """
    Stores shows and notifies observers whenever a new show is added.
    """

    def __init__(self):
        # List of observers such as CinemaLister and ShowLister.
        self.observers = []

        # show_id -> Show
        self.cache = {}

    def add_show(self, show_id, movie_id, cinema, screen_index, start_time, end_time):
        show = Show(show_id, movie_id, screen_index, start_time, end_time, cinema)
        self.cache[show_id] = show

        # Update all dependent listing caches.
        self.notify_all(show)
        return show

    def get_show(self, show_id):
        return self.cache.get(show_id)

    def add_observer(self, observer):
        self.observers.append(observer)

    def notify_all(self, show):
        for observer in self.observers:
            observer.update(show)


@dataclass
class Booking:
    """
    Stores ticket booking details.
    """

    ticket_id: str
    show_id: int
    seats: List[str]
    booking_status: int = 0  # 0 means booked, 1 means cancelled

    def is_cancelled(self) -> bool:
        return self.booking_status != 0

    def cancel_booking(self):
        self.booking_status = 1

    def get_ticket_id(self) -> str:
        return self.ticket_id

    def get_show_id(self) -> int:
        return self.show_id

    def get_seats(self) -> List[str]:
        return self.seats


class ShowLister:
    """
    Maintains movie/cinema based show listing cache.
    """

    def __init__(self):
        # "movie_id-cinema_id" -> List[Show]
        self.cache = defaultdict(list)

    def list_shows(self, movie_id, cinema_id):
        """
        Returns all show IDs of a movie in a cinema.

        Ordering:
        1. Descending start_time
        2. Ascending show_id
        """
        show_list = []
        key = f"{movie_id}-{cinema_id}"
        shows = self.cache.get(key)

        if shows is not None:
            shows.sort(key=lambda show: (-show.get_start_time(), show.get_show_id()))
            for show in shows:
                show_list.append(show.get_show_id())

        return show_list

    def update(self, show):
        """
        Called automatically when a new show is added.
        """
        key = f"{show.get_movie_id()}-{show.get_cinema().get_cinema_id()}"
        self.cache[key].append(show)


class CinemaLister:
    """
    Maintains movie/city based cinema listing cache.
    """

    def __init__(self):
        # "movie_id-city_id" -> Set[cinema_id]
        self.cache = defaultdict(set)

    def update(self, show):
        """
        Called automatically when a new show is added.
        """
        key = f"{show.get_movie_id()}-{show.get_cinema().get_city_id()}"
        self.cache[key].add(show.get_cinema().get_cinema_id())

    def list_cinemas(self, movie_id, city_id):
        """
        Returns cinema IDs of all cinemas running the movie in the city.

        Cinema IDs are returned in ascending order.
        """
        cinema_list = []
        key = f"{movie_id}-{city_id}"
        cinemas = self.cache.get(key)

        if cinemas is not None:
            cinema_list.extend(sorted(cinemas))

        return cinema_list


class TicketBookingManager:
    """
    Handles ticket booking, cancellation and free seat count.
    """

    def __init__(self):
        # show_id -> 2D matrix of seats
        # False means free, True means booked.
        self.seats = {}

        # show_id -> number of free seats
        self.free_seats_count = {}

        # ticket_id -> Booking
        self.bookings = {}

    def book_ticket(self, ticket_id: str, show: 'Show', tickets_count: int) -> List[str]:
        ans = []
        cinema = show.get_cinema()
        show_id = show.get_show_id()

        # Lazy initialization of seat layout for the show.
        if show_id not in self.seats:
            self.seats[show_id] = [
                [False for _ in range(cinema.get_screen_column())]
                for _ in range(cinema.get_screen_row())
            ]
            self.free_seats_count[show_id] = (
                cinema.get_screen_row() * cinema.get_screen_column()
            )

        # If enough seats are not available, booking fails.
        if self.free_seats_count[show_id] < tickets_count:
            return ans

        # Reserve the count first because we already know enough seats exist.
        self.free_seats_count[show_id] -= tickets_count
        show_seats = self.seats[show_id]

        # First try to book continuous seats in the same row.
        for row in range(cinema.get_screen_row()):
            for column in range(cinema.get_screen_column()):
                ans = self.lock_continuous_free_seats(
                    show_seats[row],
                    column,
                    tickets_count,
                    row,
                )
                if len(ans) > 0:
                    break
            if len(ans) > 0:
                break

        # If continuous seats are not available, book any free seats.
        if len(ans) == 0:
            for row in range(cinema.get_screen_row()):
                for column in range(cinema.get_screen_column()):
                    if tickets_count <= 0:
                        break
                    if show_seats[row][column]:
                        continue
                    tickets_count -= 1
                    show_seats[row][column] = True
                    ans.append(f"{row}-{column}")
                if tickets_count <= 0:
                    break

        booking = Booking(ticket_id, show_id, ans)
        self.bookings[ticket_id] = booking
        return ans

    def lock_continuous_free_seats(
        self,
        booked_seats: List[bool],
        start: int,
        seats_count: int,
        row: int,
    ) -> List[str]:
        """
        Tries to book seats_count continuous seats from index start.
        """
        booked = []

        if start + seats_count > len(booked_seats):
            return booked

        has_seats = all(
            not booked_seats[i]
            for i in range(start, start + seats_count)
        )

        if not has_seats:
            return booked

        for i in range(start, start + seats_count):
            booked_seats[i] = True
            booked.append(f"{row}-{i}")

        return booked

    def cancel_ticket(self, ticket_id: str) -> bool:
        if ticket_id is None:
            return False

        booking = self.bookings.get(ticket_id)
        if booking is None or booking.is_cancelled():
            return False

        booked = self.seats.get(booking.get_show_id())
        if booked is None:
            return False

        booking.cancel_booking()

        # Mark every seat of this booking as free again.
        for seat in booking.get_seats():
            row, column = map(int, seat.split('-'))
            booked[row][column] = False

        self.free_seats_count[booking.get_show_id()] += len(booking.get_seats())
        return True

    def get_free_seats_count(self, show: 'Show') -> int:
        cinema = show.get_cinema()
        return self.free_seats_count.get(
            show.get_show_id(),
            cinema.get_screen_row() * cinema.get_screen_column(),
        )
```

## Complexity Analysis

Let:

- `R` = number of rows in the screen
- `C` = number of columns in the screen
- `S` = number of shows stored for a movie in a cinema

### Booking Tickets

In the worst case, the booking logic may scan the full seat matrix.

```text
Time Complexity: O(R * C)
Space Complexity: O(R * C) per show
```

### Cancelling Tickets

Cancellation only iterates over the seats booked in that ticket.

```text
Time Complexity: O(number of seats in the ticket)
Space Complexity: O(1)
```

### Listing Cinemas

Cinema IDs are stored in a set and sorted while returning.

```text
Time Complexity: O(K log K)
```

where `K` is the number of cinemas running the movie in the given city.

### Listing Shows

Shows are sorted by start time and show ID while returning.

```text
Time Complexity: O(S log S)
```

where `S` is the number of shows for the movie in the given cinema.

## Summary

This solution is simple and modular. The most important part is the separation of responsibilities:

- `CinemaManager` manages cinemas.
- `ShowManager` manages shows.
- `CinemaLister` and `ShowLister` maintain listing caches using the Observer Pattern.
- `TicketBookingManager` handles booking, cancellation and seat count.

The Observer Pattern makes the listing logic efficient because caches are updated when shows are added, instead of recalculating everything on every list query.
