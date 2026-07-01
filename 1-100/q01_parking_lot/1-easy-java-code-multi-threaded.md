# Design a Parking Lot with easy to understand Java Code for multi-threaded environment

## Problem Statement

Problem Statement : https://codezym.com/question/1-design-parking-lot-multithreaded

Below YouTube video explains Java solution to low level design of a parking lot in a multi-threaded environment.  
It is simple and easy to understand for beginners.

## Video Explanation

[![Design a Parking Lot Java Solution](https://img.youtube.com/vi/817XIgbH2yk/hqdefault.jpg)](https://www.youtube.com/watch?v=817XIgbH2yk)

YouTube Video : https://www.youtube.com/watch?v=817XIgbH2yk

```java
// ****** It's better to write code in your local code editor and paste it back here *********

// put all import statements at the top, else it will give compiler error
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Solution implements Q001ParkingLotInterface {

    private Helper01 helper;
    private ParkingFloor floors[];
    private SearchManager searchManager;
    private int[] vehicleTypes={2,4};
    public Solution(){}

    /** use helper.print() and helper.println() for logging
     normal System.out.println() logs won't appear
    */
    public void init(Helper01 helper, String[][][] parking) {
        this.helper=helper;
        floors=new ParkingFloor[parking.length];
        for(int i=0;i<parking.length;i++)
            floors[i]=new ParkingFloor(i,
                    parking[i],vehicleTypes, helper);
        searchManager= new SearchManager();
    }

    /**
     * choose any parking spot of your own choice
     * assign an empty parking spot to vehicle and
     * map vehicleNumber and ticketId to the assigned spotId
     *
     * ParkingResult status 201 for success,
     * 404 : for bad request like invalid input parameters vehicle type not found or
     * both of vehicleNumber and ticketId are blank.
     *
     * vehicleType = 2 or 4 for 2-wheeler or 4-wheeler vehicle
     */
    public ParkingResult park(int vehicleType,
                              String vehicleNumber, String ticketId){
        for(ParkingFloor floor: floors){
            ParkingResult result=floor.park(
                    vehicleType,vehicleNumber, ticketId);
            if(result!=null && result.getStatus()==201){
                searchManager.index(result);
                return result;
            }
        }
        return new ParkingResult(
               404, "", vehicleNumber, ticketId);
    }

    /**
     * This method un-parks a vehicle
     * return status types based on ParkingResult
     * - 201 success, 404 : vehicle not found or any other error,
     * - exactly one of spotId, vehicleNumber or ticketId will be non empty
     * - spotId will be of the format "floor-row-column" and will be 0 based index
     *  e.g 0-4-11 : ParkingSpot on floor 0, row 4 and column 11,
     */
    public int removeVehicle(String spotId,
                             String vehicleNumber, String ticketId){
        // extracting floor, row, column of parking spot where vehicle is parked
        ParkingResult search = searchVehicle(spotId, vehicleNumber, ticketId);
        if(search==null||search.getStatus()>=400)return 404;
        Integer []location=helper.getSpotLocation(search.getSpotId());
        int floor= location[0], row=location[1],col=location[2];
        return floors[floor].removeVehicle(row,col);
    }

    /** floor is 0-index based, i.e.  0<=floor<parking.length
     vehicleType = 2 or 4 for 2-wheeler or 4-wheeler vehicle
     */
    public int getFreeSpotsCount(int floor, int vehicleType){
        if(floor<0||floor>=floors.length) return 0;
        return floors[floor].getFreeSpotsCount(vehicleType);
    }

    /**
     * status = 200 : success, 404 : not found
     * exactly one of spotId, vehicleNumber or ticketId will be non empty
     */
    public ParkingResult searchVehicle(String spotId,
               String vehicleNumber, String ticketId){
        return searchManager.searchVehicle(
                spotId, vehicleNumber, ticketId);
    }

}

class ParkingFloor {
    // vehicleType vs free spots count
   private HashMap<Integer, AtomicInteger> freeSpotsCount;
   private ParkingSpot parkingSpots[][];

    ParkingFloor(int floor, String parkingFloor[][],
                 int[] vehicleTypes, Helper01 helper){
        parkingSpots = new ParkingSpot[parkingFloor.length][parkingFloor[0].length];
        freeSpotsCount=new HashMap<>();

        for(int vehicleType: vehicleTypes)
            freeSpotsCount.put(vehicleType, new AtomicInteger(0));

        for(int row=0;row<parkingFloor.length;row++){
            for(int col=0;col<parkingFloor[row].length;col++)
                // "2-1" and "4-1"
                if(parkingFloor[row][col].endsWith("1")){
                    int vehicleType=Integer.parseInt(
                            parkingFloor[row][col].split("-")[0]);
                    parkingSpots[row][col]=new ParkingSpot(   //0-8-4
                            helper.getSpotId(floor, row, col), vehicleType);
                    freeSpotsCount.get(vehicleType).addAndGet(1);
                }
        }
    }

    int getFreeSpotsCount(int vehicleType){
        return freeSpotsCount.get(vehicleType).get();
    }

    /**
     * This method un-parks a vehicle
     * return status types based on ParkingResult
     * - 201 success, 404 : vehicle not found or any other error,
     */
    public synchronized int removeVehicle(
            int row, int col){
        if(row<0
            ||row>= parkingSpots.length||col<0
            ||col>= parkingSpots[0].length
            || !parkingSpots[row][col].isParked())
            return 404;
        parkingSpots[row][col].removeVehicle();
        freeSpotsCount
                .get(parkingSpots[row][col]
                        .getVehicleType())
                .addAndGet(1);
        return 201;
    }

    public synchronized ParkingResult park(
            int vehicleType, String vehicleNumber, String ticketId){
        if(freeSpotsCount.get(vehicleType).get()==0)
            return new ParkingResult(404,
                    "", vehicleNumber, ticketId);
        for(int row=0;row<parkingSpots.length;row++) {
            for (ParkingSpot spot : parkingSpots[row]) {
                if (spot != null && !spot.isParked()
                        && spot.getVehicleType() == vehicleType) {
                    freeSpotsCount.get(vehicleType).addAndGet(-1);
                    spot.parkVehicle();
                    return new ParkingResult(201,
                            spot.getSpotId(), vehicleNumber, ticketId);
                }
            }
        }
        return new ParkingResult(404,
                "", vehicleNumber, ticketId);
    }
}

class ParkingSpot{
    private String spotId;
    private int vehicleType;
    private boolean isParked;

    ParkingSpot(String spotId, int vehicleType){
        this.spotId=spotId;
        this.vehicleType=vehicleType;
        isParked=false;
    }
    boolean isParked(){
        return isParked;
    }
    void parkVehicle(){
      isParked=true;
    }
    void removeVehicle(){
        isParked=false;
    }
    public String getSpotId() {
        return spotId;
    }
    public int getVehicleType() {
        return vehicleType;
    }
}

class SearchManager{
    private ConcurrentHashMap<String, ParkingResult> cache
            = new ConcurrentHashMap<>();

    public ParkingResult searchVehicle(String spotId,
             String vehicleNumber, String ticketId){

        if(spotId.trim().length()>0)
            return cache.get(spotId);
        if(vehicleNumber.trim().length()>0)
            return cache.get(vehicleNumber);
        if(ticketId.trim().length()>0)
            return cache.get(ticketId);
        return new ParkingResult(
         404, spotId, vehicleNumber, ticketId);
    }

    void index(ParkingResult result){
        cache.put(result.getSpotId(), result);
        cache.put(result.getVehicleNumber(), result);
        cache.put(result.getTicketId(), result);
    }



}
```
