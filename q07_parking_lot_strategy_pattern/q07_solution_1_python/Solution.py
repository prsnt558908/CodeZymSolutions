

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


class ParkingFloor:
    """
    class ParkingFloor keeps track of spots on each floor
    """
    def __init__(self, floor: int, parking: list[list[int]], vehicle_types: list[int], helper):
        """
        Initialize a parking floor.
        
        Args:
            floor (int): The floor number.
            parking (list[list[int]]): The parking structure of the floor.
            vehicle_types (list[int]): The types of vehicles allowed on the floor.
        """
        self.free_spots_count = {vt: 0 for vt in vehicle_types}
        #helper.println(f"free spots count {self.free_spots_count}")
        self.floor = floor
        self.row = len(parking)
        self.column = len(parking[0])
        self.parking = parking
        self.reserved = [[False] * self.column for _ in range(self.row)]
        #helper.println(f"initializing floor {floor}, ")
        for i in range(self.row):
            for j in range(self.column):
                vehicle_type = parking[i][j]
                #helper.println(f"floor[{i}][{j}]= {vehicle_type}")
                if vehicle_type != 0:
                    self.free_spots_count[vehicle_type] += 1

    def park(self, vehicle_type: int) -> str:
        """
        Park a vehicle on the floor.
        
        Args:
            vehicle_type (int): The type of vehicle.

        Returns:
            str: The spot ID where the vehicle is parked.
        """
        for i in range(self.row):
            for j in range(self.column):
                if self.parking[i][j] == vehicle_type and not self.reserved[i][j]:
                    self.reserved[i][j] = True
                    self.free_spots_count[vehicle_type] -= 1
                    return f"{self.floor}-{i}-{j}"
        return ""

    def remove_vehicle(self, row: int, col: int) -> bool:
        """
        Remove a vehicle from the floor.
        
        Args:
            row (int): The row number.
            col (int): The column number.

        Returns:
            bool: True if the vehicle was removed, False otherwise.
        """
        vehicle_type = self.parking[row][col]
        if not self.reserved[row][col] or vehicle_type == 0:
            return False
        self.reserved[row][col] = False
        self.free_spots_count[vehicle_type] += 1
        return True

    def get_free_spots_count(self, vehicle_type: int) -> int:
        """
        Get the count of free spots for a given vehicle type.
        
        Args:
            vehicle_type (int): The vehicle type.

        Returns:
            int: The number of free spots available.
        """
        return self.free_spots_count.get(vehicle_type, 0)
