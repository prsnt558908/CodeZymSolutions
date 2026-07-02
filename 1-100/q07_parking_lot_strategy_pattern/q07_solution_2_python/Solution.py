

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

