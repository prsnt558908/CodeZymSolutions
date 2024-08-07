class Solution:
    
    def init(self, helper, parking: list):
        """
        Initialize the parking lot.

        :param helper: Helper for logging and utility functions.
        :param parking: 3D list representing the parking structure.
        """
        self.helper = helper
        #self.helper.println(f"solution class initialized, number of floors {len(parking)}")
        self.vehicle_types = [2, 4]
        self.floors = [ParkingFloor(i, parking[i], self.vehicle_types, helper) for i in range(len(parking))]
        self.search_manager = SearchManager()

    def park(self, vehicle_type: int, vehicle_number: str, ticket_id: str) -> str:
        """
        Assign an empty parking spot to a vehicle.

        :param vehicle_type: Type of the vehicle (2 or 4 for 2-wheeler or 4-wheeler).
        :param vehicle_number: Vehicle number.
        :param ticket_id: Ticket ID.
        :return: spot_id assigned to the vehicle
        """
        for floor in self.floors:
            result_spot_id = floor.park(vehicle_type, vehicle_number, ticket_id)
            if  result_spot_id != "" :
                self.search_manager.index( result_spot_id, vehicle_number, ticket_id)
                #print(f"vehicle parked {vehicle_number} in spot {result_spot_id}")
                return result_spot_id
        return ""

    def remove_vehicle(self, spot_id: str, vehicle_number: str, ticket_id: str) -> int:
        """
        Un-park a vehicle.

        :param spot_id: Spot ID of the vehicle.
        :param vehicle_number: Vehicle number.
        :param ticket_id: Ticket ID.
        :return: Status code indicating the result of the operation.
        """
        search_spot_id = spot_id if spot_id != "" else self.search_vehicle(vehicle_number, ticket_id)
        if search_spot_id == "" :
            return 404
            
        location = self.helper.get_spot_location(search_spot_id)
        if location[0]<0:
            return 404
        floor, row, col = location[0], location[1], location[2]
        removed= self.floors[floor].remove_vehicle(row, col)
        #print(f"vehicle {vehicle_number}, {ticket_id} removed from {search_spot_id}")
        return removed

    def get_free_spots_count(self, floor: int, vehicle_type: int) -> int:
        """
        Get the count of free spots for a specific vehicle type on a specific floor.

        :param floor: Floor number (0-indexed).
        :param vehicle_type: Type of the vehicle (2 or 4 for 2-wheeler or 4-wheeler).
        :return: Count of free spots.
        """
        if floor < 0 or floor >= len(self.floors):
            return 0
        return self.floors[floor].get_free_spots_count(vehicle_type)

    def search_vehicle(self, vehicle_number: str, ticket_id: str) -> str:
        """
        Search for a vehicle.

        :param spot_id: Spot ID of the vehicle.
        :param vehicle_number: Vehicle number.
        :param ticket_id: Ticket ID.
        :return: returns spot id for vehicle_number or ticket_id, (keeps and returns past spot_id record even after vehicle is removed)
        """
        return self.search_manager.search_vehicle(vehicle_number, ticket_id)

class ParkingFloor:
    def __init__(self, floor: int, parking_floor: list, vehicle_types: list, helper):
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
                if parking_floor[row][col].endswith("1"):
                    vehicle_type = int(parking_floor[row][col].split("-")[0])
                    self.parking_spots[row][col] = ParkingSpot(helper.get_spot_id(floor, row, col), vehicle_type)
                    self.free_spots_count[vehicle_type] += 1

    def get_free_spots_count(self, vehicle_type: int) -> int:
        """
        Get the count of free spots for a specific vehicle type.

        :param vehicle_type: Type of the vehicle.
        :return: Count of free spots.
        """
        return self.free_spots_count.get(vehicle_type, 0)

    def remove_vehicle(self, row: int, col: int) -> int:
        """
        Un-park a vehicle.

        :param row: Row number.
        :param col: Column number.
        :return: Status code indicating the result of the operation.
        """
        if row < 0 or row >= len(self.parking_spots) or col < 0 or col >= len(self.parking_spots[0]) or not self.parking_spots[row][col].is_parked():
            return 404
        vehicle_type=self.parking_spots[row][col].get_vehicle_type()    
        self.parking_spots[row][col].remove_vehicle()
        self.free_spots_count[vehicle_type] += 1
        return 201

    def park(self, vehicle_type: int, vehicle_number: str, ticket_id: str) -> str:
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

        :param spot_id: Spot ID.
        :param vehicle_type: Type of the vehicle.
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

class SearchManager:
    def __init__(self):
        """
        Initialize the search manager.
        """
        self.cache = {}

    def search_vehicle(self, vehicle_number: str, ticket_id: str) -> str:
        """
        Search for a vehicle.

        :param vehicle_number: Vehicle number.
        :param ticket_id: Ticket ID.
        :return: ParkingResult indicating the result of the search.
        """
        if vehicle_number.strip():
            return self.cache.get(vehicle_number)
        if ticket_id.strip():
            return self.cache.get(ticket_id)
        return ""

    def index(self, spot_id, vehicle_number, ticket_id):
        self.cache[vehicle_number] = spot_id
        self.cache[ticket_id] = spot_id


