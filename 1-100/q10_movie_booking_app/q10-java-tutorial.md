# Low Level Design of a Movie Ticket Booking System in Java

Problem Statement: https://codezym.com/question/10-design-movie-ticket-booking-system

The core idea of this solution is to divide the movie ticket booking system into small manager classes. `CinemaManager` stores cinemas, `ShowManager` stores shows, `TicketBookingManager` handles seat booking and cancellation, while `CinemaLister` and `ShowLister` maintain fast lookup lists. The solution also uses the **Observer Design Pattern**: whenever a new show is added, the listers are automatically updated, so listing cinemas and shows becomes simple and efficient.

## Requirements Covered

This design supports the following operations:

- Add a cinema with screens and seat layout.
- Add a movie show in a cinema screen.
- Book tickets for a show.
- Cancel an existing ticket.
- Get free seats count for a show.
- List cinemas running a movie in a city.
- List shows for a movie in a cinema.

## High Level Design

At a high level, the system has three main responsibilities:

1. **Cinema and show management**
   - Store cinemas by `cinemaId`.
   - Store shows by `showId`.

2. **Seat booking management**
   - Maintain booked seats for each show.
   - Maintain free seat count for each show.
   - Store ticket booking details using `ticketId`.

3. **Search/listing management**
   - Quickly list cinemas for a movie in a city.
   - Quickly list shows for a movie in a cinema.

The important design decision is that listing data is updated when a show is added. This avoids scanning all shows again and again during list queries.

## Why Observer Pattern Is Used

When a new show is added, two different listing structures need to be updated:

- `CinemaLister` should know that a cinema is running a movie in a city.
- `ShowLister` should know that a cinema has a particular show for a movie.

Instead of writing this update logic directly inside `ShowManager`, we make `CinemaLister` and `ShowLister` observers.

So the flow becomes:

```text
addShow()
   -> ShowManager creates the Show
   -> ShowManager notifies all observers
   -> CinemaLister updates movie-city to cinema list
   -> ShowLister updates movie-cinema to show list
```

This keeps `ShowManager` clean and makes the design extensible. If tomorrow we need another cache, for example movie-wise statistics, we can add one more observer without changing the main show creation logic.

## Important Classes

### Solution

`Solution` is the main class exposed to the platform. It does not contain much business logic directly. It simply delegates work to different manager classes.

This keeps the API layer simple and clean.

### CinemaManager

`CinemaManager` stores all cinemas in a `HashMap<Integer, Cinema>`.

```text
cinemaId -> Cinema
```

This allows fetching cinema details in `O(1)` average time.

### Cinema

`Cinema` is a simple data class that stores:

- cinema id
- city id
- number of screens
- number of rows in each screen
- number of columns in each screen

The row and column values are later used to create the seat matrix for a show.

### ShowManager

`ShowManager` stores all shows in a `HashMap<Integer, Show>`.

```text
showId -> Show
```

It also acts as the subject in the Observer Pattern. After creating a show, it notifies all registered observers.

### TicketBookingManager

This is the main class for booking and cancelling tickets.

It uses three important maps:

```text
showId -> booked seat matrix
showId -> free seats count
ticketId -> Booking
```

For every show, seats are represented using a `boolean[][]` matrix.

```text
false = seat is free
true  = seat is booked
```

When a ticket is booked, the system first tries to find continuous seats in the same row. If continuous seats are not available, it books any available seats.

### Booking

`Booking` stores the ticket id, show id, booked seats, and booking status.

When a ticket is cancelled, the booking status is marked cancelled and all its seats are freed again.

### CinemaLister

`CinemaLister` maintains this mapping:

```text
movieId-cityId -> sorted set of cinemaIds
```

A `TreeSet` is used because cinema ids must be returned in ascending order.

### ShowLister

`ShowLister` maintains this mapping:

```text
movieId-cinemaId -> list of shows
```

When `listShows()` is called, the list is sorted by:

1. descending start time
2. ascending show id, if start time is same

## Seat Booking Flow

When `bookTicket(ticketId, showId, ticketsCount)` is called:

1. Fetch the show using `showId`.
2. Initialize the seat matrix for the show if it does not already exist.
3. Check if enough free seats are available.
4. Try to book continuous seats in one row.
5. If continuous seats are not found, book any available seats.
6. Store the booking details using `ticketId`.
7. Return booked seats as `row-column` strings.

Example:

```text
0-0, 0-1, 0-2
```

means row `0`, columns `0`, `1`, and `2` are booked.

## Cancellation Flow

When `cancelTicket(ticketId)` is called:

1. Find the booking using `ticketId`.
2. If the booking does not exist or is already cancelled, return `false`.
3. Mark the booking as cancelled.
4. Free all seats stored in that booking.
5. Increase the free seat count for the show.
6. Return `true`.

## Java Code

```java
import java.util.*;

interface ShowObserver {
    void update(Show show);
}

interface ShowSubject {
    void addObserver(ShowObserver observer);

    void notifyAll(Show show);
}

public class Solution implements Q10MovieBookingInterface {
    private Helper10 helper;
    private CinemaManager cinemaManager;
    private ShowManager showManager;
    private CinemaLister cinemaLister;
    private ShowLister showLister;
    private TicketBookingManager bookingManager;

    public Solution() {}

    public void init(Helper10 helper) {
        this.helper = helper;

        cinemaLister = new CinemaLister();
        showLister = new ShowLister();

        cinemaManager = new CinemaManager();
        showManager = new ShowManager();

        // Whenever a new show is added, both listers should update their caches.
        showManager.addObserver(cinemaLister);
        showManager.addObserver(showLister);

        bookingManager = new TicketBookingManager();
    }

    public void addCinema(
            int cinemaId,
            int cityId,
            int screenCount,
            int screenRow,
            int screenColumn
    ) {
        cinemaManager.addCinema(cinemaId, cityId, screenCount, screenRow, screenColumn);
    }

    public void addShow(
            int showId,
            int movieId,
            int cinemaId,
            int screenIndex,
            long startTime,
            long endTime
    ) {
        Cinema cinema = cinemaManager.getCinema(cinemaId);
        showManager.addShow(showId, movieId, cinema, screenIndex, startTime, endTime);
    }

    // Returns list of booked ticket seats as row-column strings.
    public List<String> bookTicket(String ticketId, int showId, int ticketsCount) {
        Show show = showManager.getShow(showId);
        if (show == null) {
            return new ArrayList<>();
        }
        return bookingManager.bookTicket(ticketId, show, ticketsCount);
    }

    public boolean cancelTicket(String ticketId) {
        return bookingManager.cancelTicket(ticketId);
    }

    public int getFreeSeatsCount(int showId) {
        Show show = showManager.getShow(showId);
        if (show == null) {
            return 0;
        }
        return bookingManager.getFreeSeatsCount(show);
    }

    // Returns cinemaIds of all cinemas that are running a show for the given movie.
    // Cinema ids are returned in ascending order.
    public List<Integer> listCinemas(int movieId, int cityId) {
        return cinemaLister.listCinemas(movieId, cityId);
    }

    // Returns showIds of all shows displaying the movie in the given cinema.
    // Shows are returned by descending start time and then ascending show id.
    public List<Integer> listShows(int movieId, int cinemaId) {
        return showLister.listShows(movieId, cinemaId);
    }
}

class CinemaManager {
    private final HashMap<Integer, Cinema> cache = new HashMap<>();

    public void addCinema(
            int cinemaId,
            int cityId,
            int screenCount,
            int screenRow,
            int screenColumn
    ) {
        Cinema cinema = new Cinema(cinemaId, cityId, screenCount, screenRow, screenColumn);
        cache.put(cinemaId, cinema);
    }

    public Cinema getCinema(int cinemaId) {
        return cache.get(cinemaId);
    }
}

class Cinema {
    private final int cinemaId;
    private final int cityId;
    private final int screenCount;
    private final int screenRow;
    private final int screenColumn;

    public Cinema(int cinemaId, int cityId, int screenCount, int screenRow, int screenColumn) {
        this.cinemaId = cinemaId;
        this.cityId = cityId;
        this.screenCount = screenCount;
        this.screenRow = screenRow;
        this.screenColumn = screenColumn;
    }

    public int getCinemaId() {
        return cinemaId;
    }

    public int getCityId() {
        return cityId;
    }

    public int getScreenCount() {
        return screenCount;
    }

    public int getScreenRow() {
        return screenRow;
    }

    public int getScreenColumn() {
        return screenColumn;
    }
}

class ShowManager implements ShowSubject {
    private final ArrayList<ShowObserver> observers = new ArrayList<>();
    private final HashMap<Integer, Show> cache = new HashMap<>();

    Show addShow(
            int showId,
            int movieId,
            Cinema cinema,
            int screenIndex,
            long startTime,
            long endTime
    ) {
        Show show = new Show(showId, movieId, screenIndex, startTime, endTime, cinema);
        cache.put(showId, show);

        // Notify all observers so their derived lookup data stays updated.
        notifyAll(show);
        return show;
    }

    Show getShow(int showId) {
        return cache.get(showId);
    }

    public void addObserver(ShowObserver observer) {
        observers.add(observer);
    }

    public void notifyAll(Show show) {
        for (ShowObserver observer : observers) {
            observer.update(show);
        }
    }
}

class Show {
    private final int showId;
    private final int movieId;
    private final int screenIndex;
    private final long startTime;
    private final long endTime;
    private final Cinema cinema;

    public Show(
            int showId,
            int movieId,
            int screenIndex,
            long startTime,
            long endTime,
            Cinema cinema
    ) {
        this.showId = showId;
        this.movieId = movieId;
        this.screenIndex = screenIndex;
        this.startTime = startTime;
        this.endTime = endTime;
        this.cinema = cinema;
    }

    @Override
    public String toString() {
        return "Show(" +
                "showId=" + showId +
                ", movieId=" + movieId +
                ", cinemaId=" + cinema.getCinemaId() +
                ", screenIndex=" + screenIndex +
                ')';
    }

    public int getShowId() {
        return showId;
    }

    public int getMovieId() {
        return movieId;
    }

    public int getScreenIndex() {
        return screenIndex;
    }

    public long getStartTime() {
        return startTime;
    }

    public Cinema getCinema() {
        return cinema;
    }
}

class TicketBookingManager {
    // showId -> seat matrix
    // false means free seat, true means booked seat.
    private final HashMap<Integer, boolean[][]> seats = new HashMap<>();

    // showId -> number of free seats
    private final HashMap<Integer, Integer> freeSeatsCount = new HashMap<>();

    // ticketId -> booking details
    private final HashMap<String, Booking> bookings = new HashMap<>();

    public List<String> bookTicket(String ticketId, Show show, int ticketsCount) {
        ArrayList<String> ans = new ArrayList<>();

        if (ticketId == null || show == null || ticketsCount <= 0) {
            return ans;
        }

        Cinema cinema = show.getCinema();
        int showId = show.getShowId();

        // Initialize seats and free seat count lazily for this show.
        if (!seats.containsKey(showId)) {
            seats.put(showId, new boolean[cinema.getScreenRow()][cinema.getScreenColumn()]);
            freeSeatsCount.put(showId, cinema.getScreenRow() * cinema.getScreenColumn());
        }

        if (freeSeatsCount.get(showId) < ticketsCount) {
            return ans;
        }

        boolean[][] showSeats = seats.get(showId);

        // First try to book continuous seats in the same row.
        outer:
        for (int row = 0; row < cinema.getScreenRow(); row++) {
            for (int column = 0; column < cinema.getScreenColumn(); column++) {
                ans = lockContinuousFreeSeats(showSeats[row], column, ticketsCount, row);
                if (!ans.isEmpty()) {
                    break outer;
                }
            }
        }

        // If continuous seats are not available, book any available seats.
        if (ans.isEmpty()) {
            int remainingTickets = ticketsCount;

            outer2:
            for (int row = 0; row < cinema.getScreenRow(); row++) {
                for (int column = 0; column < cinema.getScreenColumn(); column++) {
                    if (remainingTickets <= 0) {
                        break outer2;
                    }

                    if (showSeats[row][column]) {
                        continue;
                    }

                    remainingTickets--;
                    showSeats[row][column] = true;
                    ans.add(row + "-" + column);
                }
            }
        }

        freeSeatsCount.put(showId, freeSeatsCount.get(showId) - ans.size());

        Booking booking = new Booking(ticketId, showId, ans);
        bookings.put(ticketId, booking);

        return ans;
    }

    private ArrayList<String> lockContinuousFreeSeats(
            boolean[] bookedSeats,
            int start,
            int seatsCount,
            int row
    ) {
        ArrayList<String> booked = new ArrayList<>();

        if (start + seatsCount > bookedSeats.length) {
            return booked;
        }

        for (int i = start; i < start + seatsCount; i++) {
            if (bookedSeats[i]) {
                return booked;
            }
        }

        for (int i = start; i < start + seatsCount; i++) {
            bookedSeats[i] = true;
            booked.add(row + "-" + i);
        }

        return booked;
    }

    public boolean cancelTicket(String ticketId) {
        if (ticketId == null) {
            return false;
        }

        Booking booking = bookings.get(ticketId);
        if (booking == null || booking.isCancelled()) {
            return false;
        }

        boolean[][] booked = seats.get(booking.getShowId());
        if (booked == null) {
            return false;
        }

        booking.cancelBooking();

        for (String seat : booking.getSeats()) {
            String[] position = seat.split("-");
            int row = Integer.parseInt(position[0]);
            int column = Integer.parseInt(position[1]);
            booked[row][column] = false;
        }

        freeSeatsCount.put(
                booking.getShowId(),
                freeSeatsCount.get(booking.getShowId()) + booking.getSeats().size()
        );

        return true;
    }

    public int getFreeSeatsCount(Show show) {
        Cinema cinema = show.getCinema();
        return freeSeatsCount.getOrDefault(
                show.getShowId(),
                cinema.getScreenRow() * cinema.getScreenColumn()
        );
    }
}

class Booking {
    private final String ticketId;
    private final int showId;

    // bookingStatus = 0 means booked, 1 means cancelled.
    private int bookingStatus = 0;

    private final ArrayList<String> seats;

    public Booking(String ticketId, int showId, ArrayList<String> seats) {
        this.ticketId = ticketId;
        this.showId = showId;
        this.seats = seats;
    }

    boolean isCancelled() {
        return bookingStatus != 0;
    }

    void cancelBooking() {
        bookingStatus = 1;
    }

    public String getTicketId() {
        return ticketId;
    }

    public int getShowId() {
        return showId;
    }

    public ArrayList<String> getSeats() {
        return seats;
    }
}

class ShowLister implements ShowObserver {
    private final HashMap<String, ArrayList<Show>> cache = new HashMap<>();

    // Returns all showIds of all shows displaying the movie in the given cinema.
    // Ordered by descending start time and then ascending show id.
    public List<Integer> listShows(int movieId, int cinemaId) {
        ArrayList<Integer> list = new ArrayList<>();
        ArrayList<Show> shows = cache.get(movieId + "-" + cinemaId);

        if (shows != null) {
            shows.sort((a, b) -> {
                int timeCompare = Long.compare(b.getStartTime(), a.getStartTime());
                if (timeCompare != 0) {
                    return timeCompare;
                }
                return Integer.compare(a.getShowId(), b.getShowId());
            });

            for (Show show : shows) {
                list.add(show.getShowId());
            }
        }

        return list;
    }

    public void update(Show show) {
        String key = show.getMovieId() + "-" + show.getCinema().getCinemaId();
        cache.putIfAbsent(key, new ArrayList<>());
        cache.get(key).add(show);
    }
}

class CinemaLister implements ShowObserver {
    private final HashMap<String, TreeSet<Integer>> cache = new HashMap<>();

    public void update(Show show) {
        String key = show.getMovieId() + "-" + show.getCinema().getCityId();
        cache.putIfAbsent(key, new TreeSet<>());
        cache.get(key).add(show.getCinema().getCinemaId());
    }

    // Returns cinemaIds of all cinemas running a show for the given movie.
    // Cinema ids are ordered in ascending order because TreeSet is used.
    public List<Integer> listCinemas(int movieId, int cityId) {
        ArrayList<Integer> list = new ArrayList<>();
        TreeSet<Integer> cinemas = cache.get(movieId + "-" + cityId);

        if (cinemas != null) {
            list.addAll(cinemas);
        }

        return list;
    }
}
```

## Complexity Analysis

Let:

- `R` = number of rows in a screen
- `C` = number of columns in a screen
- `S` = number of shows for a movie in a cinema

### Booking Tickets

In the worst case, we may scan all seats once to find continuous seats and again to find any available seats.

```text
Time Complexity: O(R * C)
Space Complexity: O(R * C) per show for the seat matrix
```

### Cancelling Ticket

If a booking has `K` seats, we only visit those `K` seats.

```text
Time Complexity: O(K)
Space Complexity: O(1)
```

### Listing Cinemas

`CinemaLister` uses a `TreeSet`, so cinema ids are already sorted.

```text
Time Complexity: O(number of matching cinemas)
```

### Listing Shows

The shows are sorted during query time.

```text
Time Complexity: O(S log S)
```
