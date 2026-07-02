# Low Level Design of a Parking Lot in Java using Strategy Pattern

**Problem Statement:** https://codezym.com/question/7-design-a-parking-lot

## Video Explanation

[![Low Level Design of a Parking Lot using Strategy Pattern](https://img.youtube.com/vi/fi_IWW1Ay0o/hqdefault.jpg)](https://www.youtube.com/watch?v=fi_IWW1Ay0o)

YouTube Video : https://www.youtube.com/watch?v=fi_IWW1Ay0o

Above YouTube video explains low level design of a parking lot using strategy design pattern for a single threaded environment.


Basically we have a parking lot with multiple floors and it can be used to park either two-wheeler or four-wheeler vehicles.

## Requirements

```java
init(Helper helper, int [][][] parking)
park(int vehicleType, int parkingStrategy, String vehicleNumber, String ticketId)
removeVehicle(String spotId)
String searchVehicle(String query)
int getFreeSpotsCount(int floor, int vehicleType)
```

## init(Helper helper, int [][][] parking)

Our parking lot is initialized with the init() method,

the parking[][][] array represents the individual parking spots on all floors, rows and columns of the parking lot.

Below parking[][][] array is for a parking lot with 1 floor, 4 rows on each floor and 4 columns in each row.

it has 7 free two wheeler parking spots,  
6 free four wheeler parking spots and  
3 inactive spots, you can’t park your vehicle in an inactive spot.

```java
[[ 
[4, 4, 2, 2], 
[2, 4, 2, 0], 
[0, 2, 2, 2], 
[4, 4, 4, 0]
]]
```

## 2. park (int vehicleType, int parkingStrategy, String vehicleNumber, String ticketId)

You will notice that park() method has a parameter parkingStrategy.

parkingStrategy determines which algorithm we will use for parking the vehicle.

If parkingStrategy = 0, assign the parking spot at lowest index i.e. lowest floor, row and column.

If parkingStrategy = 1, Get the floor with maximum number of free spots for the given vehicle type. If multiple floors have maximum free spots then choose the floor at lowest index from them.


It makes sense to use strategy design pattern here. Using strategy pattern will enable us to keep different algorithms in different classes. Also adding more strategies/algorithms will be easier.

As of now parkingStrategy=0 is implemented by class NearestParkingStrategy,
parkingStrategy=1 is implemented by class MostFreeSpotsParkingStrategy
class ParkManager keeps instances of both these strategy classes and uses them to park the vehicle according to the value of parkingStrategy parameter.

```java
class ParkManager{
       private ParkingStrategy algorithms[];

       ParkManager(){
           algorithms=new ParkingStrategy[]{
                   new NearestParkingStrategy(),
                   new MostFreeSpotsParkingStrategy()};
       }

       String park(ParkingFloor floors[], 
                int vehicleType, int parkingStrategy){
             if(parkingStrategy>=0 && parkingStrategy<algorithms.length)
                 return algorithms[parkingStrategy].park(
                         floors, vehicleType);
             return "";
       }
}
```

Since parking lot has multiple floors so we have class ParkingFloor to take care of park() removeVehicle() and getFreeSpotsCount() for a given floor.

We have kept reserved[][] array to mark parking spot in a given row and column as reserved.
also we keep free spots count for each vehicle type in a map freeSpotsCount. We update freeSpotsCount after parking and removing vehicle.
park() method is called from the strategy algorithm class that we saw above.
Press enter or click to view image in full size

For searching a vehicle we have class SearchManager,

it indexes the parking details like spotId, vehicleNumber and ticketId in a map.
Once indexed, we can find the spotId using vehicleNumber or ticketId.
Press enter or click to view image in full size

## Complete Java Code

```java
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


interface ParkingStrategy{
    String park(ParkingFloor floors[], int vehicleType);
}

public class Solution implements Q07ParkingLotInterface {
    private ParkingFloor floors[];
    private Helper07 helper;
    private ParkManager parkManager = new ParkManager();
    private int vehicleTypes[]={2, 4};
    private final SearchManager searchManager = new SearchManager();

    public void init(Helper07 helper, Integer [][][] parking) {
        this.helper=helper;
        floors=new ParkingFloor[parking.length];
        for (int i = 0; i < parking.length; i++) {
            floors[i]=new ParkingFloor(i, parking[i], vehicleTypes);
        }
    }
    // returns spotId, e.g. 2-0-11 which is
    // parking spot at parking[2][0][11]
    public String park(int vehicleType, String vehicleNumber,
                       String ticketId, int parkingStrategy) {
        String spotId = parkManager.park(
                floors, vehicleType, parkingStrategy);

        if (!spotId.isEmpty())
            searchManager.index(spotId, vehicleNumber, ticketId);
        return spotId;
    }

    // spotId : 2-0-11 --> parking spot at parking[2][0][11]
    public boolean removeVehicle(String spotId) {
        String[] d = spotId.split("-");
        int floorIndex = Integer.parseInt(d[0]);
        return floors[floorIndex].removeVehicle(
                Integer.parseInt(d[1]), Integer.parseInt(d[2]));
    }

    public int getFreeSpotsCount(
            int floor, int vehicleType) {
        return floors[floor].getFreeSpotsCount(
                vehicleType);
    }

    // query is either vehicleNumber or ticketId
    public String searchVehicle(String query) {
        return searchManager.search(query);
    }
    
}

class ParkingFloor {
    private final HashMap<Integer, AtomicInteger>
            freeSpotsCount = new HashMap<>();
    private final int floor, row, column;
    private final Integer [][] parking;
    private boolean reserved[][];
    
    public ParkingFloor(int floor,
            Integer [][] parking, int[]  vehicleTypes) {
        this.floor = floor;
        this.parking = parking;
        this.row = parking.length;
        this.column = parking[0].length;
        reserved=new boolean[row][column];
        for(int vehicleType: vehicleTypes)
            freeSpotsCount.put(vehicleType, new AtomicInteger(0));

        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                int vehicleType= parking[i][j];
                if (vehicleType != 0)
                    freeSpotsCount.get(vehicleType).addAndGet(1);
            }
        }
    }

    public String park(int vehicleType) {
        for (int i = 0; i < row; i++)
            for (int j = 0; j < column; j++) {
                if (parking[i][j] == vehicleType && !reserved[i][j]) {
                    reserved[i][j] = true;
                    freeSpotsCount.get(vehicleType).addAndGet(-1);
                    return floor + "-" + i + "-" + j;
                }
            }
        return "";
    }
    
    public boolean removeVehicle(int row, int col) {
        int vehicleType=parking[row][col];
        if (!reserved[row][col] || vehicleType == 0) return false;
        reserved[row][col] = false;
        freeSpotsCount.get(vehicleType).addAndGet(1);
        return true;
    }

    public int getFreeSpotsCount(int vehicleType) {
        return freeSpotsCount.getOrDefault(
                vehicleType,
                new AtomicInteger(0)).get();
    }
    
}

class SearchManager {
    private final HashMap<String, String> cache
            = new HashMap<>();
    public String search(String query) {
        return cache.getOrDefault(
                query, "");
    }

    public void index(String spotId,
        String vehicleNumber, String ticketId) {
        cache.put(vehicleNumber, spotId);
        cache.put(ticketId, spotId);
    }
}

class ParkManager{
       private ParkingStrategy algorithms[];

       ParkManager(){
           algorithms=new ParkingStrategy[]{
                   new NearestParkingStrategy(),
                   new MostFreeSpotsParkingStrategy()};
       }
       
       String park(ParkingFloor floors[],
               int vehicleType, int parkingStrategy){

             if(parkingStrategy>=0 && parkingStrategy<algorithms.length)
                 return algorithms[parkingStrategy].park(
                         floors, vehicleType);

             return "";
       }
}

class NearestParkingStrategy implements ParkingStrategy{

    public String park(
            ParkingFloor floors[], int vehicleType) {

        for (ParkingFloor floor : floors) {
            String spotId = floor.park(vehicleType);
            if (!spotId.isEmpty()) return spotId;
        }

        return "";
    }
}

class MostFreeSpotsParkingStrategy implements ParkingStrategy{
    public String park(ParkingFloor floors[], int vehicleType) {
        int freeSpotsCount = 0;
        int floorIndex = -1;
        for (int i = 0; i < floors.length; i++) {
            int temp = floors[i].getFreeSpotsCount(vehicleType);
            if (temp <= freeSpotsCount) continue;
            freeSpotsCount = temp;
            floorIndex = i;
        }
        if (floorIndex >= 0) {
            String spotId= floors[floorIndex].park(vehicleType);
            return spotId;
        }
        return "";
    }
}
```
