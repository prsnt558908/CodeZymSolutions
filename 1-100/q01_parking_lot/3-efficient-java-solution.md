# Design a Parking Lot in Java for Multithreaded Environment

Problem Statement : https://codezym.com/question/1-design-parking-lot-multithreaded

## Video Explanation

[![Design a Parking Lot Java Solution](https://img.youtube.com/vi/JfMciz7lC3M/hqdefault.jpg)](https://www.youtube.com/watch?v=JfMciz7lC3M)

YouTube Video : https://www.youtube.com/watch?v=JfMciz7lC3M

## Explanation

In this problem, we need to design a parking lot where vehicles can be parked, removed, searched, and free spots can be counted.

The parking lot has multiple floors. Each floor has different parking spots for different vehicle types.

Supported vehicle types:

- `2` for two-wheeler
- `4` for four-wheeler

The main idea is simple:

- Store free spots separately for each vehicle type.
- When a vehicle comes, directly pick one free spot for that vehicle type.
- When a vehicle leaves, add that spot back to the free spot list.
- Maintain a search index so that a vehicle can be found quickly using spot id, vehicle number, or ticket id.

## Important Classes and Data Structures

### `Solution`

This is the main class used by the judge or client code.

It is responsible for:

- Initializing the parking lot.
- Calling the correct floor to park a vehicle.
- Removing a vehicle.
- Searching a parked vehicle.
- Returning free spot count for a floor.

We need this class because it acts as the entry point and coordinates all other classes.

### `ParkingFloor`

This class manages one floor of the parking lot.

Each floor stores all its parking spots and also maintains free spots grouped by vehicle type.

We need this class because parking and removing vehicles are floor-level operations. Keeping this logic inside `ParkingFloor` makes the code cleaner.

### `ParkingSpot`

This class represents one parking spot.

It stores:

- Spot id
- Vehicle type allowed on that spot
- Parked vehicle number
- Ticket id

We need this class because every spot has its own state.

### `SearchManager`

This class helps us search vehicles quickly.

Without this class, we may need to scan every floor and every spot to find a vehicle. That would be slow.

`SearchManager` uses a map so that search can be done in almost `O(1)` time.

### `ConcurrentLinkedDeque`

This is used to store free parking spots for each vehicle type.

We use it because:

- We need fast insertion and removal of free spots.
- It is safe to use in a multithreaded environment.
- Parking can pick a spot from the front in `O(1)` time.

### `AtomicInteger`

This is used to store the number of free spots for each vehicle type.

We use it because multiple threads may try to park vehicles at the same time. `AtomicInteger` helps safely decrease or increase the free spot count.

### `ConcurrentHashMap`

This is used inside `SearchManager`.

We use it because multiple threads may park and search vehicles at the same time. `ConcurrentHashMap` is safer than normal `HashMap` in this case.

## Code Flow

### Parking a vehicle

1. Validate the vehicle type and input.
2. Try to park the vehicle floor by floor.
3. On each floor, check if a free spot exists for that vehicle type.
4. If a spot is found, assign vehicle details to it.
5. Add the vehicle details to the search index.
6. Return success.

### Removing a vehicle

1. Search the vehicle using spot id, vehicle number, or ticket id.
2. Extract the floor, row, and column from the spot id.
3. Clear the vehicle details from that spot.
4. Add the spot back to the free spot queue.
5. Return success.

### Searching a vehicle

1. If spot id is given, search using spot id.
2. If vehicle number is given, search using vehicle number.
3. If ticket id is given, search using ticket id.
4. Return the parking result if found.

## Code

```java


import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class Solution implements Q001ParkingLotInterface {
    private ParkingFloor[] floors;
    private SearchManager searchManager;
    private Helper01 helper;

    // Supported vehicle types.
    // 2 means two-wheeler and 4 means four-wheeler.
    private final Set<Integer> vehicleTypes = new HashSet<>(Arrays.asList(2, 4));

    public Solution() {}

    public void init(Helper01 helper, String[][][] parking) {
        this.helper = helper;
        this.floors = new ParkingFloor[parking.length];

        // Create one ParkingFloor object for each floor in the input.
        for (int floor = 0; floor < parking.length; floor++) {
            ParkingSpot[][] spots = getParkingSpots(floor, parking[floor]);
            floors[floor] = new ParkingFloor(spots, vehicleTypes);
        }

        // SearchManager stores indexes for quick search by spot id,
        // vehicle number, or ticket id.
        this.searchManager = new SearchManager();
    }

    /**
     * ParkingResult status:
     * 201 -> vehicle parked successfully
     * 404 -> parking failed
     *
     * vehicleType:
     * 2 -> two-wheeler
     * 4 -> four-wheeler
     */
    public ParkingResult park(int vehicleType, String vehicleNumber, String ticketId) {
        if (!vehicleTypes.contains(vehicleType)
                || (vehicleNumber.isBlank() && ticketId.isBlank())) {
            return new ParkingResult(404, "", "", "");
        }

        // Try to park on the first floor where a free spot is available.
        for (ParkingFloor floor : floors) {
            ParkingResult result = floor.park(vehicleType, vehicleNumber, ticketId);

            if (result != null && result.getStatus() == 201) {
                // Store this result so that future search is fast.
                searchManager.index(result);
                return result;
            }
        }

        return new ParkingResult(404, "", vehicleNumber, ticketId);
    }

    /**
     * Return status:
     * 201 -> vehicle removed successfully
     * 404 -> vehicle not found or invalid input
     *
     * Exactly one of spotId, vehicleNumber, or ticketId will be non-empty.
     */
    public int removeVehicle(String spotId, String vehicleNumber, String ticketId) {
        // First find where the vehicle is parked.
        ParkingResult search = searchVehicle(spotId, vehicleNumber, ticketId);

        if (search == null || search.getStatus() >= 400) {
            return 404;
        }

        // Convert spot id into floor, row, and column.
        Integer[] location = helper.getSpotLocation(search.getSpotId());
        int floor = location[0];
        int row = location[1];
        int col = location[2];

        if (floor < 0 || floor >= floors.length) {
            return 404;
        }

        return floors[floor].removeVehicle(row, col);
    }

    /**
     * ParkingResult status:
     * 200 -> vehicle found
     * 404 -> vehicle not found
     *
     * Exactly one of spotId, vehicleNumber, or ticketId will be non-empty.
     */
    public ParkingResult searchVehicle(String spotId, String vehicleNumber, String ticketId) {
        return searchManager.searchVehicle(spotId, vehicleNumber, ticketId);
    }

    /**
     * floor is 0-index based.
     * vehicleType = 2 or 4.
     */
    public int getFreeSpotsCount(int floor, int vehicleType) {
        if (floor < 0 || floor >= floors.length) {
            return 0;
        }

        return floors[floor].freeSpotsCount(vehicleType);
    }

    private ParkingSpot[][] getParkingSpots(int floor, String[][] rows) {
        ParkingSpot[][] spots = new ParkingSpot[rows.length][rows[0].length];

        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length; col++) {
                String spot = rows[row][col];

                // "2-1" means this is an available two-wheeler spot.
                if (spot.equals("2-1")) {
                    spots[row][col] = new ParkingSpot(
                            helper.getSpotId(floor, row, col),
                            2
                    );
                }

                // "4-1" means this is an available four-wheeler spot.
                else if (spot.equals("4-1")) {
                    spots[row][col] = new ParkingSpot(
                            helper.getSpotId(floor, row, col),
                            4
                    );
                }
            }
        }

        return spots;
    }
}

class ParkingFloor {
    // vehicleType -> free spots queue
    //
    // Example:
    // 2 -> all free two-wheeler spots on this floor
    // 4 -> all free four-wheeler spots on this floor
    //
    // This improves the park operation from scanning all spots
    // to directly picking one free spot.
    private final HashMap<Integer, ConcurrentLinkedDeque<ParkingSpot>> freeSpots;

    // vehicleType -> number of free spots
    //
    // AtomicInteger is used because multiple threads may try to park
    // vehicles at the same time.
    private final HashMap<Integer, AtomicInteger> freeSpotsSize;

    // Complete parking grid of this floor.
    // This helps us access a spot directly using row and column during removal.
    private final ParkingSpot[][] allSpots;

    ParkingFloor(ParkingSpot[][] spots, Set<Integer> vehicleTypes) {
        this.allSpots = spots;
        this.freeSpots = new HashMap<>();
        this.freeSpotsSize = new HashMap<>();

        // Initialize free spot queue and count for each vehicle type.
        for (int vehicleType : vehicleTypes) {
            freeSpots.put(vehicleType, new ConcurrentLinkedDeque<>());
            freeSpotsSize.put(vehicleType, new AtomicInteger(0));
        }

        // Add every valid parking spot to the corresponding free spot queue.
        for (ParkingSpot[] rowSpots : spots) {
            for (ParkingSpot spot : rowSpots) {
                if (spot == null) {
                    continue;
                }

                int vehicleType = spot.getVehicleType();

                freeSpots.get(vehicleType).add(spot);
                freeSpotsSize.get(vehicleType).incrementAndGet();
            }
        }
    }

    public ParkingResult park(int vehicleType, String vehicleNumber, String ticketId) {
        AtomicInteger size = freeSpotsSize.get(vehicleType);

        // Atomically reserve one free spot count.
        // This prevents two threads from taking the same available count.
        while (true) {
            int oldSize = size.get();

            if (oldSize <= 0) {
                return new ParkingResult(404, "", vehicleNumber, ticketId);
            }

            if (size.compareAndSet(oldSize, oldSize - 1)) {
                break;
            }
        }

        // Remove one free spot from the queue.
        // Since we already decreased the count, this spot is reserved for this request.
        ParkingSpot spot = freeSpots.get(vehicleType).remove();

        spot.setVehicleNumber(vehicleNumber);
        spot.setTicketId(ticketId);

        return new ParkingResult(201, spot.getSpotId(), vehicleNumber, ticketId);
    }

    /**
     * Return status:
     * 201 -> vehicle removed successfully
     * 404 -> vehicle not found or invalid input
     */
    public int removeVehicle(int row, int col) {
        if (row < 0 || row >= allSpots.length
                || col < 0 || col >= allSpots[0].length) {
            return 404;
        }

        ParkingSpot spot = allSpots[row][col];

        if (spot == null) {
            return 404;
        }

        // If both fields are blank, this spot is already empty.
        if (spot.getVehicleNumber().isBlank() && spot.getTicketId().isBlank()) {
            return 404;
        }

        // Clear vehicle details from the spot.
        spot.setTicketId("");
        spot.setVehicleNumber("");

        // Add this spot back to the free spot queue.
        freeSpots.get(spot.getVehicleType()).add(spot);
        freeSpotsSize.get(spot.getVehicleType()).incrementAndGet();

        return 201;
    }

    int freeSpotsCount(int vehicleType) {
        if (!freeSpotsSize.containsKey(vehicleType)) {
            return 0;
        }

        return freeSpotsSize.get(vehicleType).get();
    }
}

class SearchManager {
    // Prefixes are used so that spot id, vehicle number, and ticket id
    // do not conflict with each other.
    //
    // Example:
    // s>A-1-2 means spot id
    // v>BR01AB1234 means vehicle number
    // t>TICKET123 means ticket id
    private final ConcurrentHashMap<String, ParkingResult> cache = new ConcurrentHashMap<>();

    void index(ParkingResult result) {
        // Store result by spot id.
        cache.put("s>" + result.getSpotId(), result);

        // Store result by vehicle number.
        cache.put("v>" + result.getVehicleNumber(), result);

        // Store result by ticket id.
        cache.put("t>" + result.getTicketId(), result);
    }

    public ParkingResult searchVehicle(String spotId, String vehicleNumber, String ticketId) {
        if (spotId.trim().length() > 0) {
            return cache.get("s>" + spotId);
        }

        if (vehicleNumber.trim().length() > 0) {
            return cache.get("v>" + vehicleNumber);
        }

        if (ticketId.trim().length() > 0) {
            return cache.get("t>" + ticketId);
        }

        return new ParkingResult(404, spotId, vehicleNumber, ticketId);
    }
}

class ParkingSpot {
    private final String spotId;
    private final int vehicleType;

    private String vehicleNumber;
    private String ticketId;

    ParkingSpot(String spotId, int vehicleType) {
        this.spotId = spotId;
        this.vehicleType = vehicleType;

        // Empty values mean no vehicle is parked here initially.
        this.vehicleNumber = "";
        this.ticketId = "";
    }

    public String getSpotId() {
        return spotId;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public int getVehicleType() {
        return vehicleType;
    }
}
```
