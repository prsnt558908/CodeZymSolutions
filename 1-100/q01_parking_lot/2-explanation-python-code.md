# Design a Simple Parking Lot in Python for Low Level Design Interviews

Problem Statement: https://codezym.com/question/1-design-parking-lot-multithreaded

Design of a Parking Lot is the most common LLD interview question there is. Most people have started their low level design interview preparation with this question. Let’s see how we will approach its design in a LLD interview.



We will discuss the requirements, then build our solution using multiple classes. Finally, we will have complete Python code which you can test using above CodeZym link.

We have a parking lot with multiple floors and has spots for two types of vehicles: 2-wheelers and 4-wheelers. Parking Spots are arranged on all floors in rows and columns.

## Requirements:

You want a Parking Lot to provide these functionalities.

### 1. Park your vehicle

Given a vehicle type, find a free parking spot. And then return a spot id. spot_id is floor-row-column . If no free spot is found return empty string. Ticket Id will already be generated for you.

```python
park(vehicle_type: int, vehicle_number: str, ticket_id: str)
```

### 2. Unpark or remove vehicle

```python
remove_vehicle(spot_id: str, vehicle_number: str, ticket_id: str)
```

### 3. Display empty spots count

Display the number of free spots for each vehicle type on each floor.

```python
get_free_spots_count(floor: int, vehicle_type: int)
```

### 4. Search Vehicle

When the customer comes to unpark their vehicle or they have lost their ticket then they should be able to find which spot it is parked on using vehicle_number or ticket_id. Even after removing the vehicle this method can be used to search historical data as to where a vehicle was parked.

```python
search_vehicle(self, vehicle_number: str, ticket_id: str)
```

## How to approach

Now there are going to be many other functionalities like Payments, User Management, Notifications, Analytics in a Parking Lot system.

In real world, this can be quite a large system with tens of functionalities and hundreds of classes. Hence in a 60 minute interview round, nobody is expecting you to write down all the classes in all the components/functionalities that may exist in these systems.

A better approach is to figure out the features that your interviewer wants to discuss with you. For a parking lot system almost every time these will be the core features that we discussed above. For a LLD interview, it is important to know what topics to discuss and which other features to leave out of discussion. Let’s start working on the design.

## Breaking the solution in multiple classes

Let’s list down entity classes first. A parking lot will have parking spots arranged in rows and columns on each floor. Lets start with a ParkingSpot class. It will have spot_id, type of vehicle that can be parked and whether spot is parked or empty.

```python
class ParkingSpot:
    def __init__(self, spot_id: str, vehicle_type: int):
        self.spot_id = spot_id
        self.vehicle_type = vehicle_type
        self.is_spot_parked = False

    def is_parked(self) -> bool:
        return self.is_spot_parked

    def park_vehicle(self):
        self.is_spot_parked = True

    def remove_vehicle(self):
        self.is_spot_parked = False

    def get_spot_id(self) -> str:
        return self.spot_id

    def get_vehicle_type(self) -> int:
        return self.vehicle_type
```

Next we will have class ParkingFloor which will store all the ParkingSpot objects in a 2-d array or list parking_spots in rows and columns.

Each floor will track number of free spots for each vehicle type separately in a map free_spots_count .

```python
class ParkingFloor:
    def __init__(self, floor: int, parking_floor: list, vehicle_types: list, helper):
        self.parking_spots = [[None for _ in range(len(parking_floor[0]))] for _ in range(len(parking_floor))]
        self.free_spots_count = {vehicle_type: 0 for vehicle_type in vehicle_types}

        for row in range(len(parking_floor)):
            for col in range(len(parking_floor[row])):
                if parking_floor[row][col].endswith("1"):
                    vehicle_type = int(parking_floor[row][col].split("-")[0])
                    self.parking_spots[row][col] = ParkingSpot(helper.get_spot_id(floor, row, col), vehicle_type)
                    self.free_spots_count[vehicle_type] += 1

    def get_free_spots_count(self, vehicle_type: int) -> int:
       return self.free_spots_count.get(vehicle_type, 0)

    def remove_vehicle(self, row: int, col: int) -> int:
        if row < 0 or row >= len(self.parking_spots) or col < 0 or col >= len(self.parking_spots[0]) or not self.parking_spots[row][col].is_parked():
            return 404
        vehicle_type=self.parking_spots[row][col].get_vehicle_type()    
        self.parking_spots[row][col].remove_vehicle()
        self.free_spots_count[vehicle_type] += 1
        return 201

    def park(self, vehicle_type: int, vehicle_number: str, ticket_id: str) -> str:
        if self.free_spots_count.get(vehicle_type, 0) == 0:
            return ""
        for row in self.parking_spots:
            for spot in row:
                if spot is not None and not spot.is_parked() and spot.get_vehicle_type() == vehicle_type:
                    self.free_spots_count[vehicle_type] -= 1
                    spot.park_vehicle()
                    return spot.get_spot_id()
        return ""
```

## Searching the vehicle

We have class SearchManager which provides this functionality to index and search vehicles. This class simply uses a map/dictionary to cache vehicle_number and ticket_id.

```python
class SearchManager:
    def __init__(self):
        self.cache = {}

    def search_vehicle(self, vehicle_number: str, ticket_id: str) -> str:
        if vehicle_number.strip():
            return self.cache.get(vehicle_number)
        if ticket_id.strip():
            return self.cache.get(ticket_id)
        return ""

    def index(self, spot_id, vehicle_number, ticket_id):
        self.cache[vehicle_number] = spot_id
        self.cache[ticket_id] = spot_id
```

## Connecting it all

class Solution which is the class where code execution will begin. It initializes list of vehicle types and class SearchManager

It also keeps a list of ParkingFloor objects, initializes them and use them for all functionalities.

After parking vehicle in a suitable spot, inside park() method ,parking details are also indexed using class SearchManager’s index() method.

```python
class Solution:
    
    def init(self, helper, parking: list):
        self.helper = helper
        #self.helper.println(f"solution class initialized, number of floors {len(parking)}")
        self.vehicle_types = [2, 4]
        self.floors = [ParkingFloor(i, parking[i], self.vehicle_types, helper) for i in range(len(parking))]
        self.search_manager = SearchManager()

    def park(self, vehicle_type: int, vehicle_number: str, ticket_id: str) -> str:
        for floor in self.floors:
            result_spot_id = floor.park(vehicle_type, vehicle_number, ticket_id)
            if  result_spot_id != "" :
                self.search_manager.index( result_spot_id, vehicle_number, ticket_id)
                #print(f"vehicle parked {vehicle_number} in spot {result_spot_id}")
                return result_spot_id
        return ""

    def remove_vehicle(self, spot_id: str, vehicle_number: str, ticket_id: str) -> int:
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
        if floor < 0 or floor >= len(self.floors):
            return 0
        return self.floors[floor].get_free_spots_count(vehicle_type)

    def search_vehicle(self, vehicle_number: str, ticket_id: str) -> str:
        return self.search_manager.search_vehicle(vehicle_number, ticket_id)
```

## Video Explanation

[![Design a Simple Parking Lot in Python for Low Level Design Interviews](https://img.youtube.com/vi/7FPnJppdIZM/hqdefault.jpg)](https://www.youtube.com/watch?v=7FPnJppdIZM)

YouTube Video : https://www.youtube.com/watch?v=7FPnJppdIZM
