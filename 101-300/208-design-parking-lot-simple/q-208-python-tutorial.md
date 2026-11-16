# Design Parking Lot (Simple Version) in Python

#### Problem Statement
[https://codezym.com/question/208-design-parking-lot-simple](https://codezym.com/question/208-design-parking-lot-simple)


## Core Idea

Every parking request asks one question: which is the lowest free spot of this type?

We give every spot an id in the order we prefer it: floor 0 from left to right, then floor 1, and so on. Then we keep the ids of the free spots in a **min-heap**, one heap per spot type. A min-heap always gives back its smallest item first, so it always hands us the lowest free spot.

A car takes from the car heap. A motorcycle takes from the motorcycle heap, and uses the car heap only when the motorcycle heap is empty. A dictionary remembers where each vehicle is parked, so when it leaves, its spot goes straight back into its heap.

No design pattern is needed here. Cars and motorcycles differ only in which spot types they can use, and a small dictionary captures that more simply than Strategy pattern would.


## Solution 1: Brute Force

The most direct idea: when a vehicle arrives, walk the lot floor by floor, left to right, and stop at the first free spot of the right type.

For a motorcycle, we first walk the whole lot looking for a motorcycle spot. If there is none, we walk it again looking for a car spot.

We also keep a dictionary from vehicle id to its spot. It tells us if a vehicle is already parked, and lets `removeVehicle()` find the spot without searching.

```python
# self.floors[f] holds the spots of floor f, from left to right
self.floors = []

# vehicle id -> the spot it is parked in
self.parkedVehicles = {}

# Walk floor by floor, left to right. The first free match is the lowest one.
def findLowestFreeSpot(self, spotType: str):
    for floor in self.floors:
        for spot in floor:
            if spot.isFree and spot.type == spotType:
                return spot
    return None

def parkVehicle(self, vehicleId: str, vehicleType: str) -> str:
    if vehicleId in self.parkedVehicles:
        return "VEHICLE_ALREADY_PARKED"
    spot = None
    if vehicleType == "MOTORCYCLE":
        spot = self.findLowestFreeSpot("MOTORCYCLE")
    if spot is None:
        spot = self.findLowestFreeSpot("CAR")
    if spot is None:
        return "NO_SPOT_AVAILABLE"
    spot.isFree = False
    self.parkedVehicles[vehicleId] = spot
    return spot.describe()
```

`removeVehicle()` looks up the spot in `parkedVehicles`, marks it free again and returns it.


### Problems with this approach

Every `parkVehicle()` call may check every spot in the lot. That is O(S) per call, where S is the total number of spots.

For example, 1000 floors with 100 spots each means up to 100,000 checks for a single call. Over many calls, this adds up fast.

A motorcycle has it even worse. When all motorcycle spots are taken, it scans the full lot twice.

Most of this work is wasted. We keep checking the same taken spots again and again.


## Solution 2: Min-Heap of Free Spots (Optimal)

We remove the scanning in three small steps.


### Step 1: Number the spots in the order we prefer them

The brute force always checks spots in the same order: floor 0 left to right, then floor 1 left to right, and so on.

So while reading the input, we give each spot an id in exactly this order. A smaller id now always means a lower spot.

For Example 1, `["2,4", "4,2"]`:

| id | floor | spot | type |
|----|-------|------|------|
| 0 | 0 | 0 | MOTORCYCLE |
| 1 | 0 | 1 | CAR |
| 2 | 1 | 0 | CAR |
| 3 | 1 | 1 | MOTORCYCLE |

"Lowest free car spot" now simply means "smallest free car spot id".


### Step 2: Keep the free spot ids in a min-heap

We keep one min-heap for each spot type. It holds the ids of the free spots of that type. In Python, the `heapq` module turns a normal list into a min-heap: `heappush()` adds an id and `heappop()` takes out the smallest one.

**Why a min-heap?** We only ever do two things with free spots:

1. Take out the smallest id, when a vehicle parks.
2. Put an id back, when a vehicle leaves.

A min-heap does both in O(log S). We never need to remove a spot from the middle, so a heap is all we need.

The motorcycle heap holds motorcycle spots from **all** floors. So the rule "search the whole lot for a motorcycle spot first" comes for free: if the motorcycle heap is not empty, its smallest id is the answer.


### Step 3: Store the vehicle rules in a dictionary

Vehicle types differ only in which spot types they can use, and in what order.

| Vehicle type | Spot types to try, in order |
|--------------|-----------------------------|
| CAR | CAR |
| MOTORCYCLE | MOTORCYCLE, then CAR |

We keep this in a dictionary called `allowedSpotTypes`. `parkVehicle()` goes through the list and takes the smallest id from the first heap that is not empty.


### Why not Strategy pattern?

Strategy pattern looks like a natural fit at first. We could write a `CarParkingStrategy` and a `MotorcycleParkingStrategy`.

But both classes would run the same loop over the heaps. Only the list of spot types would be different. That is a difference in data, not in behavior, so a dictionary is simpler and just as easy to extend. A new vehicle type is one new dictionary entry, not a new class.

Strategy starts to pay off only when the rules become truly different logic, for example a bus that needs 3 free spots side by side.

We also skip Factory pattern. We never create different kinds of vehicle objects, because a vehicle here is just an id and a type.


### Dry run of Example 1

| Call | What happens | Returns |
|------|--------------|---------|
| `parkVehicle("bike1", "MOTORCYCLE")` | motorcycle heap is 0, 3 → take 0 | `floor=0,type=MOTORCYCLE,spot=0` |
| `parkVehicle("car1", "CAR")` | car heap is 1, 2 → take 1 | `floor=0,type=CAR,spot=1` |
| `parkVehicle("bike2", "MOTORCYCLE")` | motorcycle heap is 3 → take 3 | `floor=1,type=MOTORCYCLE,spot=1` |
| `removeVehicle("bike1")` | put 0 back into the motorcycle heap | `floor=0,type=MOTORCYCLE,spot=0` |


### Key classes and data structures

| Name | What it stores | Why we need it |
|------|----------------|----------------|
| `ParkingSpot` | id, floor, index and type of one spot | Builds the output text and tells us which heap the spot goes back to |
| `spots` (list) | every spot, where `spots[id]` is the spot with that id | Turns an id taken from a heap back into a spot |
| `freeSpotIds` (dict of min-heaps) | spot type → ids of its free spots | Gives the lowest free spot of a type in O(log S) |
| `allowedSpotTypes` (dict) | vehicle type → spot types it can use, in order | Keeps the car and motorcycle rules in one place |
| `parkedVehicles` (dict) | vehicle id → its spot | Detects already parked vehicles and finds a vehicle's spot in O(1) |

Two details are easy to miss:

- Check `parkedVehicles` first. An already parked vehicle must get `VEHICLE_ALREADY_PARKED`, even when the lot is full.
- When a vehicle leaves, push the id back into the heap of the **spot's** type, not the vehicle's type. A motorcycle leaving a car spot frees a car spot.


### Python Code

```python
import heapq
from typing import List


class ParkingSpot:
    """
    One parking spot in the lot.
    The id is the spot's position in the preferred order of the whole lot:
    floor 0 from left to right, then floor 1 from left to right, and so on.
    So a smaller id always means a lower spot.
    """

    def __init__(self, spotId: int, floor: int, index: int, spotType: str):
        self.id = spotId
        self.floor = floor
        self.index = index      # position of the spot on its floor
        self.type = spotType    # "CAR" or "MOTORCYCLE"

    def describe(self) -> str:
        # Example: "floor=0,type=CAR,spot=2"
        return f"floor={self.floor},type={self.type},spot={self.index}"


class ParkingLot:

    def __init__(self, parkingFloors: List[str]):
        # Every spot. self.spots[id] is the spot with that id.
        self.spots = []

        # Spot type -> min-heap with the ids of its free spots.
        self.freeSpotIds = {"CAR": [], "MOTORCYCLE": []}

        # Vehicle type -> spot types it can use, in the order we try them.
        self.allowedSpotTypes = {
            "CAR": ["CAR"],
            "MOTORCYCLE": ["MOTORCYCLE", "CAR"],
        }

        # Vehicle id -> the spot where it is parked right now.
        self.parkedVehicles = {}

        # Create spots floor by floor, left to right,
        # so the id order matches the preferred order.
        for floor, floorText in enumerate(parkingFloors):
            for index, value in enumerate(floorText.split(",")):
                spotType = "MOTORCYCLE" if value == "2" else "CAR"   # 2 = motorcycle, 4 = car
                spot = ParkingSpot(len(self.spots), floor, index, spotType)
                self.spots.append(spot)
                heapq.heappush(self.freeSpotIds[spotType], spot.id)

    def parkVehicle(self, vehicleId: str, vehicleType: str) -> str:
        if vehicleId in self.parkedVehicles:
            return "VEHICLE_ALREADY_PARKED"

        # Try the allowed spot types in order.
        # Take the lowest free spot from the first heap that is not empty.
        for spotType in self.allowedSpotTypes[vehicleType]:
            heap = self.freeSpotIds[spotType]
            if heap:
                spot = self.spots[heapq.heappop(heap)]
                self.parkedVehicles[vehicleId] = spot
                return spot.describe()

        return "NO_SPOT_AVAILABLE"

    def removeVehicle(self, vehicleId: str) -> str:
        spot = self.parkedVehicles.pop(vehicleId, None)
        if spot is None:
            return "VEHICLE_NOT_FOUND"

        # The spot is free again. It goes back to the heap of its own type.
        heapq.heappush(self.freeSpotIds[spot.type], spot.id)
        return spot.describe()
```


### Complexity

S is the total number of spots.

| Operation | Time |
|-----------|------|
| Constructor | O(S log S) |
| `parkVehicle()` | O(log S) |
| `removeVehicle()` | O(log S) |

Space is O(S) for the spots, the heaps and the dictionary of parked vehicles.