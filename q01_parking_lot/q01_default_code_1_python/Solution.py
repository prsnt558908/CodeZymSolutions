class Solution:
    
    def init(self, helper, parking: list):
        """
        Initialize the parking lot.

        :param helper: use helper.print(""), helper.println("") for logging and utility functions.
        :param parking: 3-d list representing the parking structure.
        """
        self.helper = helper
       

    def park(self, vehicle_type: int, vehicle_number: str, ticket_id: str) -> str:
        """
        Assign an empty parking spot to a vehicle.

        :param vehicle_type: Type of the vehicle (2 or 4 for 2-wheeler or 4-wheeler).
        :return: spot_id assigned to the vehicle or empty string ""
                spot_id= floor-row-column, refer to problem statement for details.
        """
        return ""
    
    # use helper method to get floor, row, column of parked vehicle from spot_id
    #location = self.helper.get_spot_location(spot_id)
    #floor, row, col = location[0], location[1], location[2]
    def remove_vehicle(self, spot_id: str, vehicle_number: str, ticket_id: str) -> int:
        """
        Un-park a vehicle.

        :param spot_id: spot id of the vehicle: floor-row-column.
        :param vehicle_number: Vehicle number.
        :param ticket_id: Ticket ID.
        :return: 201 for success, 404 for failure
        """
        return 404

    def get_free_spots_count(self, floor: int, vehicle_type: int) -> int:
        """
        Get the count of free spots for a specific vehicle type on a specific floor.

        :param floor: Floor number (0-indexed).
        :param vehicle_type: Type of the vehicle (2 or 4 for 2-wheeler or 4-wheeler).
        :return: Count of free spots.
        """
        return 0

    def search_vehicle(self, vehicle_number: str, ticket_id: str) -> str:
        """
        Search for a vehicle.

        :param vehicle_number: Vehicle number.
        :param ticket_id: Ticket ID.
        :return: returns spot id for vehicle_number or ticket_id or empty string "", (returns past spot_id record even after vehicle is removed)
        """
        return ""
   