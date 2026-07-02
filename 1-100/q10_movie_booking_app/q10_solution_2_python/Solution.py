from collections import defaultdict
from dataclasses import dataclass

class Solution:
    def __init__(self):
        self.helper = None
        self.cinema_manager = None
        self.show_manager = None
        self.cinema_lister = None
        self.show_lister = None
        self.booking_manager = None

    def init(self, helper):
        self.helper = helper
        self.cinema_lister = CinemaLister()
        self.show_lister = ShowLister()
        self.cinema_manager = CinemaManager()
        self.show_manager = ShowManager()
        self.show_manager.add_observer(self.cinema_lister)
        self.show_manager.add_observer(self.show_lister)
        self.booking_manager = TicketBookingManager()

    def add_cinema(self, cinema_id, city_id, screen_count, screen_row, screen_column):
        self.cinema_manager.add_cinema(cinema_id, city_id, screen_count, screen_row, screen_column)

    def add_show(self, show_id, movie_id, cinema_id, screen_index, start_time, end_time):
        cinema = self.cinema_manager.get_cinema(cinema_id)
        self.show_manager.add_show(show_id, movie_id, cinema, screen_index, start_time, end_time)

    def book_ticket(self, ticket_id, show_id, tickets_count):
        show = self.show_manager.get_show(show_id)
        if show is None:
            return []
        return self.booking_manager.book_ticket(ticket_id, show, tickets_count)

    def cancel_ticket(self, ticket_id):
        return self.booking_manager.cancel_ticket(ticket_id)

    def get_free_seats_count(self, show_id):
        #return 1
        show = self.show_manager.get_show(show_id)
        if show is None:
            return 0
        return self.booking_manager.get_free_seats_count(show)

    def list_cinemas(self, movie_id, city_id):
        return self.cinema_lister.list_cinemas(movie_id, city_id)

    def list_shows(self, movie_id, cinema_id):
        return self.show_lister.list_shows(movie_id, cinema_id)

class ShowObserver:
    def update(self, show: 'Show'):
        pass

class ShowSubject:
    def add_observer(self, observer: 'ShowObserver'):
        pass

    def notify_all(self, show: 'Show'):
        pass

@dataclass
class Cinema:
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
    show_id: int
    movie_id: int
    screen_index: int
    start_time: int
    end_time: int
    cinema: 'Cinema'

    def __str__(self) -> str:
        return (f"Show(show_id={self.show_id}, movie_id={self.movie_id}, "
                f"cinema_id={self.cinema.get_cinema_id()}, screen_index={self.screen_index})")

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
    def __init__(self):
        # Dictionary to cache cinema objects, using cinema_id as key
        self.cache = {}  # Equivalent to HashMap<Integer, Cinema>

    def add_cinema(self, cinema_id, city_id, screen_count, screen_row, screen_column):
        """
        Add a cinema to the cache.
        """
        cinema = Cinema(cinema_id, city_id, screen_count, screen_row, screen_column)
        self.cache[cinema_id] = cinema  # Equivalent to cache.put(cinemaId, cinema)

    def get_cinema(self, cinema_id):
        """
        Retrieve a cinema by its ID.
        """
        return self.cache.get(cinema_id)  # Equivalent to cache.get(cinemaId)


class ShowManager:
    def __init__(self):
        # List of observers
        self.observers = []  # Equivalent to ArrayList<ShowObserver>
        # Dictionary to cache show objects, using show_id as key
        self.cache = {}  # Equivalent to HashMap<Integer, Show>

    def add_show(self, show_id, movie_id, cinema, screen_index, start_time, end_time):
        """
        Add a show to the cache and notify all observers.
        """
        show = Show(show_id, movie_id, screen_index, start_time, end_time, cinema)
        self.cache[show_id] = show  # Equivalent to cache.put(showId, show)
        self.notify_all(show)
        return show

    def get_show(self, show_id):
        """
        Retrieve a show by its ID.
        """
        return self.cache.get(show_id)  # Equivalent to cache.get(showId)

    def add_observer(self, observer):
        """
        Add an observer to the observers list.
        """
        self.observers.append(observer)  # Equivalent to observers.add(observer)

    def notify_all(self, show):
        """
        Notify all observers about the new show.
        """
        for observer in self.observers:  # Equivalent to for(ShowObserver observer: observers)
            observer.update(show)  # Call update on each observer

@dataclass
class Booking:
    ticket_id: str
    show_id: int
    seats: List[str]
    booking_status: int = 0  # 0 for booked, 1 for cancelled

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
    def __init__(self):
        self.cache = defaultdict(list)  # HashMap equivalent
    
    def list_shows(self, movie_id, cinema_id):
        """
        Returns all show IDs of all shows displaying the movie in the given cinema.
        Show IDs are ordered in descending order of start_time and then show_id.
        """
        show_list = []
        key = f"{movie_id}-{cinema_id}"
        shows = self.cache.get(key)
        
        if shows is not None:
            shows.sort(key=lambda show: (-show.get_start_time(), show.get_show_id()))
            for show in shows:
                show_list.append(show.get_show_id())
        
        # print(f"movieId {movie_id}, cinemaId {cinema_id}, list of shows {show_list}")
        return show_list
    
    def update(self, show):
        """
        Updates the cache with the given show.
        """
        key = f"{show.get_movie_id()}-{show.get_cinema().get_cinema_id()}"
        # print(f"updating shows list: {show}")
        if key not in self.cache:
            self.cache[key] = []
        self.cache[key].append(show)

class CinemaLister:
    def __init__(self):
        self.cache = defaultdict(set)  # TreeSet equivalent using set in Python
    
    def update(self, show):
        """
        Updates the cache with the given show.
        """
        key = f"{show.get_movie_id()}-{show.get_cinema().get_city_id()}"
        if key not in self.cache:
            self.cache[key] = set()
        self.cache[key].add(show.get_cinema().get_cinema_id())
    
    def list_cinemas(self, movie_id, city_id):
        """
        Returns cinema IDs of all cinemas which are running a show for the given movie.
        Cinema IDs are ordered in ascending order.
        """
        cinema_list = []
        key = f"{movie_id}-{city_id}"
        cinemas = self.cache.get(key)
        
        if cinemas is not None:
            cinema_list.extend(sorted(cinemas))
        
        return cinema_list

class TicketBookingManager:
    def __init__(self):
        # show_id vs seats: row, column
        self.seats = {}  # Dictionary[int, List[List[bool]]]
        # show_id vs free seats
        self.free_seats_count = {}  # Dictionary[int, int]
        # ticket_id vs booking data
        self.bookings = {}  # Dictionary[str, Booking]

    def book_ticket(self, ticket_id: str, show: 'Show', tickets_count: int) -> list[str]:
        ans = []
        # Initializing seats and count for show_id
        cinema = show.get_cinema()
        if show.get_show_id() not in self.seats:
            self.seats[show.get_show_id()] = [
                [False for _ in range(cinema.get_screen_column())]
                for _ in range(cinema.get_screen_row())
            ]
            self.free_seats_count[show.get_show_id()] = (
                cinema.get_screen_row() * cinema.get_screen_column()
            )
        if self.free_seats_count[show.get_show_id()] < tickets_count:
            return ans
        # Update seats count
        self.free_seats_count[show.get_show_id()] -= tickets_count
        show_seats = self.seats[show.get_show_id()]

        # Try to find continuous seats
        for row in range(cinema.get_screen_row()):
            for column in range(cinema.get_screen_column()):
                ans = self.lock_continuous_free_seats(
                    show_seats[row], column, tickets_count, row
                )
                if len(ans) > 0:
                    break
            if len(ans) > 0:
                break

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

        booking = Booking(ticket_id, show.get_show_id(), ans)
        self.bookings[ticket_id] = booking
        return ans

    def lock_continuous_free_seats(
        self, booked_seats: list[bool], start: int, seats_count: int, row: int
    ) -> list[str]:
        booked = []
        if start + seats_count > len(booked_seats):
            return booked
        has_seats = all(not booked_seats[i] for i in range(start, start + seats_count))
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
