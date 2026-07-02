# Low Level Design of a Parking Lot in Python using Strategy Design Pattern

This version of parking lot has multiple parking strategies. Hence, we will be using strategy pattern to solve it.

Personally, I have always felt Python is better a better choice than Java for LLD interviews because it takes fewer lines of code which is crucial in a LLD interview especially in machine coding round where you are always running short of time.

Read complete problem statement, submit and test your Python or Java code here:

**Problem Statement:**  
https://codezym.com/question/7-design-a-parking-lot

---

Our parking lot will have multiple floors and allow only 2 and 4-wheeler vehicles to be parked. On each floor, parking spots of either type 2 or 4 will be arranged in rows and columns. It will support the following features:

## Requirements

- park a vehicle on a spot given its vehicle type and a parking strategy
- remove vehicle from an existing spot
- count the number of free spots on each floor for each vehicle type.
- search a vehicle by its vehicle number or ticket id.
- There will be two parking strategies for parking a vehicle and your code should be such that more strategies can be easily added in future.

---


On reading the problem statement, you will notice that logic for these parking strategies is easy to implement. And that’s the point, low level design interview are not about testing your logic, they are more about testing, how well you arrange your code. Expectation is that your provided solution is easier to understand, maintain and extend.

# Breaking the Problem in Multiple Classes

Our core logic for park, remove vehicle and get free spots count will be in **ParkingFloor** class.

Class **Solution** which is the driver class will have a list of **ParkingFloor** objects.

Search vehicle functionality will be taken care of by **SearchManager** class.

strategy pattern will be implemented using **ParkingManager**, common interface will be **ParkingStrategy**, and implementing classes will be **NearestParkingStrategy** and **MostFreeSpotsParkingStrategy**

Let's see and understand all these classes one by one.

---

# class ParkingFloor

This class which will keep the parking spots data and will have actual implementation’s for parking, removing vehicle, and fetching free spots count. Rest all other classes will call its methods as we will see later in this article.

- free spots count for each vehicle type is tracked using dictionary/hashmap `free_spots_count`
- Reserved spots are tracked using a 2-d array/list : `reserved[][]`
- `park()` method simply find the first available spot and returns it.

```python
class ParkingFloor:

   def __init__(self, floor: int, parking: list[list[int]], vehicle_types: list[int], helper):
        #map to keep count of free spots for each vehicle type
        self.free_spots_count = {vt: 0 for vt in vehicle_types}
        self.floor = floor
        self.row = len(parking)
        self.column = len(parking[0])
        self.parking = parking
        # 2-d array to keep track of reserved spots
        self.reserved = [[False] * self.column for _ in range(self.row)]
        for i in range(self.row):
            for j in range(self.column):
                vehicle_type = parking[i][j]
                if vehicle_type != 0:
                    self.free_spots_count[vehicle_type] += 1

    # finds the first available spot and parks there
    def park(self, vehicle_type: int) -> str:
       for i in range(self.row):
            for j in range(self.column):
                if self.parking[i][j] == vehicle_type and not self.reserved[i][j]:
                    self.reserved[i][j] = True
                    self.free_spots_count[vehicle_type] -= 1
                    return f"{self.floor}-{i}-{j}"
        return ""

    def remove_vehicle(self, row: int, col: int) -> bool:
        vehicle_type = self.parking[row][col]
        if not self.reserved[row][col] or vehicle_type == 0:
            return False
        self.reserved[row][col] = False
        self.free_spots_count[vehicle_type] += 1
        return True

    def get_free_spots_count(self, vehicle_type: int) -> int:
        return self.free_spots_count.get(vehicle_type, 0)
```

---

# Class SearchManager

This class indexes vehicle number and ticket id usinga simple dictionary for searching.

```python
class SearchManager:
    def __init__(self):
        self.cache = {}

    def search(self, query: str) -> str:
        return self.cache.get(query, "")

    def index(self, spot_id: str, vehicle_number: str, ticket_id: str):
        self.cache[vehicle_number] = spot_id
        self.cache[ticket_id] = spot_id
```

---

# Using Strategy Design Pattern

We use strategy pattern to implement the moves. The common interface has the `park()` method.

- It returns the `spot_id` where vehicle is parked or empty string if no spot is found
- `spot_id` is `floor-row-columns`

```python
class ParkingStrategy:
    def park(self, floors: list['ParkingFloor'], vehicle_type: int) -> str:
        pass
```

We have two strategy classes which implement this interface. In future if we need more strategies then we can easily add new classes which implement this interface.

## NearestParkingStrategy and MostFreeSpotsParkingStrategy

```python
class NearestParkingStrategy(ParkingStrategy):
    def park(self, floors: list, vehicle_type: int) -> str:
       for floor in floors:
            spot_id = floor.park(vehicle_type)
            if spot_id:
                return spot_id
        return ""

class MostFreeSpotsParkingStrategy(ParkingStrategy):
    def park(self, floors: list, vehicle_type: int) -> str:
        free_spots_count = 0
        floor_index = -1
        for i, floor in enumerate(floors):
            temp = floor.get_free_spots_count(vehicle_type)
            if temp > free_spots_count:
                free_spots_count = temp
                floor_index = i

        if floor_index >= 0:
            return floors[floor_index].park(vehicle_type)
        return ""
```

Both above strategy class are used by class **ParkManager** to do the actual parking.

If we need more strategies we simple add new strategy object to `algorithms[]` array and `park()` method will pick appropriate strategy object based on the value of `parking_strategy` which is also index in algorithms array.

if `parking_strategy` values are non-continuous then we can use a dictionary instead of an array.

```python
class ParkManager:
    def __init__(self):
       self.algorithms = [NearestParkingStrategy(), MostFreeSpotsParkingStrategy()]

    def park(self, floors: list, vehicle_type: int, parking_strategy: int) -> str:
       if 0 <= parking_strategy < len(self.algorithms):
            return self.algorithms[parking_strategy].park(floors, vehicle_type)
        return ""
```

---

# Bringing everything together

## Class Solution

Solution class binds all of above. It keeps a list of **ParkingFloor(s)**

- It initializes the ParkManager, SearchManager, vehicle_types and all ParkingFloor objects
- For park it uses park_manager to park and get a `spot_id` and then indexes the `vehicle_number` and `ticket_id` using `search_manager`
- For `remove_vehicle` and `get_free_spots_count`, it simply gets the corresponding floor object and call its methods.

```python
class Solution:

    def init(self, helper, parking: list[list[list[int]]]):
        self.vehicle_types = [2, 4]
        self.helper = helper
        self.park_manager = ParkManager()
        self.search_manager = SearchManager()
        helper.println(f"going to initialize floors {len(parking)}")
        self.floors = [ParkingFloor(i, parking[i], self.vehicle_types, helper) for i in range(len(parking))]
        #helper.println(" floors initialized ")

    def park(self, vehicle_type: int, vehicle_number: str, ticket_id: str, parking_strategy: int) -> str:
        spot_id = self.park_manager.park(self.floors, vehicle_type, parking_strategy)
        if spot_id:
            self.search_manager.index(spot_id, vehicle_number, ticket_id)
        return spot_id

    def remove_vehicle(self, spot_id: str) -> bool:
        floor_index, row, col = map(int, spot_id.split('-'))
        return self.floors[floor_index].remove_vehicle(row, col)

    def get_free_spots_count(self, floor: int, vehicle_type: int) -> int:
        return self.floors[floor].get_free_spots_count(vehicle_type)

    def search_vehicle(self, query: str) -> str:
        return self.search_manager.search(query)
```

---

This was the solution, if you don’t understand or don’t like anything, feel free to jump into comments and tell us. And please upvote, that motivates me to keep writing.

---

## Video Explanation

[![Low Level Design of a Parking Lot in Python using Strategy Design Pattern](https://img.youtube.com/vi/ZIK44dj56fk/hqdefault.jpg)](https://www.youtube.com/watch?v=ZIK44dj56fk)

YouTube Video : https://www.youtube.com/watch?v=ZIK44dj56fk

---

# Complete Python Code

```python
class ParkingStrategy:
    def park(self, floors: list['ParkingFloor'], vehicle_type: int) -> str:
        pass

class Solution:

    def init(self, helper, parking: list[list[list[int]]]):
        """
        Initialize the parking lot with the given helper and parking structure.
        """
        self.vehicle_types = [2, 4]
        self.helper = helper
        self.park_manager = ParkManager()
        self.search_manager = SearchManager()
        helper.println(f"going to initialize floors {len(parking)}")
        self.floors = [ParkingFloor(i, parking[i], self.vehicle_types, helper) for i in range(len(parking))]
        #helper.println(" floors initialized ")

    def park(self, vehicle_type: int, vehicle_number: str, ticket_id: str, parking_strategy: int) -> str:
        """
        Park a vehicle in the parking lot.

        Args:
            vehicle_type (int): Type of the vehicle.
            vehicle_number (str): Number of the vehicle.
            ticket_id (str): Ticket ID of the vehicle.
            parking_strategy (int): Strategy for parking the vehicle.

        Returns:
            str: The spot ID where the vehicle is parked.
        """
        spot_id = self.park_manager.park(self.floors, vehicle_type, parking_strategy)
        if spot_id:
            self.search_manager.index(spot_id, vehicle_number, ticket_id)
        return spot_id

    def remove_vehicle(self, spot_id: str) -> bool:
        """
        Remove a vehicle from the parking lot.

        Args:
            spot_id (str): The spot ID where the vehicle is parked.

        Returns:
            bool: True if the vehicle was removed, False otherwise.
        """
        floor_index, row, col = map(int, spot_id.split('-'))
        return self.floors[floor_index].remove_vehicle(row, col)

    def get_free_spots_count(self, floor: int, vehicle_type: int) -> int:
        """
        Get the count of free spots for a given vehicle type on a specific floor.

        Args:
            floor (int): The floor number.
            vehicle_type (int): The vehicle type.

        Returns:
            int: The number of free spots available.
        """
        return self.floors[floor].get_free_spots_count(vehicle_type)

    def search_vehicle(self, query: str) -> str:
        """
        Search for a vehicle by its number or ticket ID.

        Args:
            query (str): The vehicle number or ticket ID.

        Returns:
            str: The spot ID where the vehicle is parked.
        """
        return self.search_manager.search(query)

class SearchManager:
    def __init__(self):
        """
        Initialize the search manager.
        """
        self.cache = {}

    def search(self, query: str) -> str:
        """
        Search for a vehicle by its number or ticket ID.

        Args:
            query (str): The vehicle number or ticket ID.

        Returns:
            str: The spot ID where the vehicle is parked.
        """
        return self.cache.get(query, "")

    def index(self, spot_id: str, vehicle_number: str, ticket_id: str):
        """
        Index a vehicle's spot ID by its number and ticket ID.

        Args:
            spot_id (str): The spot ID.
            vehicle_number (str): The vehicle number.
            ticket_id (str): The ticket ID.
        """
        self.cache[vehicle_number] = spot_id
        self.cache[ticket_id] = spot_id

class ParkingFloor:
    def __init__(self, floor: int, parking_floor: list[list[int]], vehicle_types: list[int], helper):
        """
        Initialize a parking floor.

        :param floor: Floor number.
        :param parking_floor: 2D list representing the parking floor.
        :param vehicle_types: List of vehicle types.
        :param helper: Helper for logging and utility functions.
        """
        self.parking_spots = [[None for _ in range(len(parking_floor[0]))] for _ in range(len(parking_floor))]
        self.free_spots_count = {vehicle_type: 0 for vehicle_type in vehicle_types}

        for row in range(len(parking_floor)):
            for col in range(len(parking_floor[row])):
                if parking_floor[row][col]!=0:
                    vehicle_type = parking_floor[row][col]
                    self.parking_spots[row][col] = ParkingSpot(f"{floor}-{row}-{col}", vehicle_type)
                    self.free_spots_count[vehicle_type] += 1

    def get_free_spots_count(self, vehicle_type: int) -> int:
        """
        Get the count of free spots for a specific vehicle type.

        :param vehicle_type: Type of the vehicle.
        :return: Count of free spots.
        """
        return self.free_spots_count.get(vehicle_type, 0)

    def remove_vehicle(self, row: int, col: int) -> bool:
        """
        Un-park a vehicle.

        :param row: Row number.
        :param col: Column number.
        :return: Status code indicating the result of the operation.
        """
        if row < 0 or row >= len(self.parking_spots) or col < 0 or col >= len(self.parking_spots[0]) or not self.parking_spots[row][col].is_parked():
            return False
        vehicle_type=self.parking_spots[row][col].get_vehicle_type()
        self.parking_spots[row][col].remove_vehicle()
        self.free_spots_count[vehicle_type] += 1
        return True

    def park(self, vehicle_type: int) -> str:
        """
        Assign an empty parking spot to a vehicle.

        :param vehicle_type: Type of the vehicle.
        :param vehicle_number: Vehicle number.
        :param ticket_id: Ticket ID.
        :return: ParkingResult indicating the status of the operation.
        """
        if self.free_spots_count.get(vehicle_type, 0) == 0:
            return ""
        for row in self.parking_spots:
            for spot in row:
                if spot is not None and not spot.is_parked() and spot.get_vehicle_type() == vehicle_type:
                    self.free_spots_count[vehicle_type] -= 1
                    spot.park_vehicle()
                    return spot.get_spot_id()
        return ""

class ParkingSpot:
    def __init__(self, spot_id: str, vehicle_type: int):
        """
        Initialize a parking spot.

        :param spot_id: Spot ID. floor-row-column
        :param vehicle_type: Type of the vehicle 2 or 4 wheeler
        """
        self.spot_id = spot_id
        self.vehicle_type = vehicle_type
        self.is_spot_parked = False

    def is_parked(self) -> bool:
        """
        Check if a vehicle is parked in this spot.

        :return: True if a vehicle is parked, False otherwise.
        """
        return self.is_spot_parked

    def park_vehicle(self):
        """
        Park a vehicle in this spot.
        """
        self.is_spot_parked = True

    def remove_vehicle(self):
        """
        Remove a vehicle from this spot.
        """
        self.is_spot_parked = False

    def get_spot_id(self) -> str:
        """
        Get the spot ID.

        :return: Spot ID.
        """
        return self.spot_id

    def get_vehicle_type(self) -> int:
        """
        Get the vehicle type.

        :return: Vehicle type.
        """
        return self.vehicle_type

class ParkManager:
    def __init__(self):
        """
        Initialize the park manager with parking strategies.
        """
        self.algorithms = [NearestParkingStrategy(), MostFreeSpotsParkingStrategy()]

    def park(self, floors: list, vehicle_type: int, parking_strategy: int) -> str:
        """
        Park a vehicle using the specified strategy.

        Args:
            floors (List[ParkingFloor]): The list of parking floors.
            vehicle_type (int): The vehicle type.
            parking_strategy (int): The parking strategy to use.

        Returns:
            str: The spot ID where the vehicle is parked.
        """
        if 0 <= parking_strategy < len(self.algorithms):
            return self.algorithms[parking_strategy].park(floors, vehicle_type)
        return ""

class NearestParkingStrategy(ParkingStrategy):
    def park(self, floors: list, vehicle_type: int) -> str:
        """
        Park a vehicle in the nearest available spot.

        Args:
            floors (List[ParkingFloor]): The list of parking floors.
            vehicle_type (int): The vehicle type.

        Returns:
            str: The spot ID where the vehicle is parked.
        """
        for floor in floors:
            spot_id = floor.park(vehicle_type)
            if spot_id:
                return spot_id
        return ""

class MostFreeSpotsParkingStrategy(ParkingStrategy):
    def park(self, floors: list, vehicle_type: int) -> str:
        """
        Park a vehicle in the floor with the most free spots.

        Args:
            floors (list]): The list of parking floors.
            vehicle_type (int): The vehicle type.

        Returns:
            str: The spot ID where the vehicle is parked.
        """
        free_spots_count = 0
        floor_index = -1
        for i, floor in enumerate(floors):
            temp = floor.get_free_spots_count(vehicle_type)
            if temp > free_spots_count:
                free_spots_count = temp
                floor_index = i

        if floor_index >= 0:
            return floors[floor_index].park(vehicle_type)
        return ""
```