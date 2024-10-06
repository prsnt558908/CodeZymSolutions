// problem statement: https://codezym.com/question/11
import java.util.*;

public class Solution implements Q11ElevatorSystemInterface {
    int floorsCount, liftsCount, liftsCapacity;
    Helper11 helper ;
    private Lift lifts[];

    public Solution(){}

    public void init(int floors, int lifts, int liftsCapacity, Helper11 helper) {
        this.floorsCount = floors;
        this.liftsCount =lifts;
        this.liftsCapacity=liftsCapacity;
        this.helper = helper;
        this.lifts = new Lift[lifts];
        for(int i=0;i<lifts;i++) this.lifts[i]=new Lift(floors, liftsCapacity);
        // helper.println("Lift system initialized ...");
    }
    
    /** maximum count of people inside lift liftCapacity 
     * user press the outside UP or DOWN button outside lift, returns lift index or -1 */
    public int requestLift(int floor, char direction) {
        int liftIndex=-1, timeTaken=-1;
        for(int i=0;i<lifts.length;i++){
            Lift lift=lifts[i];
            int time=lift.getTimeToReachFloor(floor, direction);
            if(time<0 || lift.countPeople(floor, direction)>=liftsCapacity) continue;
            if(timeTaken<0||time<timeTaken){
                timeTaken = time;
                liftIndex=i;
            }
        }
        if(liftIndex>=0)lifts[liftIndex].addIncomingRequest(floor, direction);
        return liftIndex;
    }
    
    /** returns true if floor is valid else returns false */
    public void pressFloorButtonInLift(int liftIndex, int floor) {
        Lift lift=lifts[liftIndex];
        lift.addOutGoingRequest(floor, lift.getMoveDirection());
    }
    
    /** 4-U-8 lift at floor 4 going up with 8 people inside */
    public String getLiftState(int liftIndex) {
        if(liftIndex<0||liftIndex>=lifts.length) return "";
        Lift lift=lifts[liftIndex];
        return  ""+lift.getCurrentFloor()+"-"+
                  lift.getMoveDirection()+"-"+lift.getCurrentPeopleCount();
    }

    /**
     * This method is called every second
     * so that lift states can be appropriately updated.
     * we use this time rather than java.util.Date().time
     */
    public void tick() {
        for(int i = 0; i< liftsCount; i++)
            lifts[i].tick();
    }

}

class Lift {
    private int currentFloor, floors, capacity;
    /* people who will get in lift later if space is there */
    HashSet<Integer> incomingRequestsCount;
    /** people who are already inside lift and have chosen on which floor they will get off */
    HashMap<Integer, Integer> outgoingRequestsCount;
    private LiftState movingUpState, movingDownState, idleState,
                      movingUpToPickFirst, movingDownToPickFirst, state;

    Lift(int floors, int capacity){
        this.floors=floors;
        this.capacity=capacity;
        movingUpState = new MovingUpState(this);
        movingDownState = new MovingDownState(this);
        idleState = new IdleState(this);
        movingUpToPickFirst=new MovingUpToPickFirstState(this);
        movingDownToPickFirst=new MovingDownToPickFirstState(this);
        incomingRequestsCount=new HashSet<>();
        outgoingRequestsCount=new HashMap<>();
        state = idleState;
        currentFloor=0;
    }
    
    public int getCurrentPeopleCount(){
       int count=0;
       for(Map.Entry<Integer, Integer> entry: outgoingRequestsCount.entrySet())
           count+=entry.getValue();
       return count;
    }

    int getTimeToReachFloor(int floor, char direction){
        return state.getTimeToReachFloor(floor, direction);
    }
    
    // it assumes that parameters will always be valid and
    // floor will always be reachable in the given direction
    void addIncomingRequest(int floor, char direction){
        // handling Idle state
       if(state.getDirection()=='I')
       {   if(floor==currentFloor)setState(direction);
           else {
            if (floor > currentFloor)
               state = direction == 'U' ? movingUpState : movingUpToPickFirst;
            else state = direction == 'D' ? movingDownState : movingDownToPickFirst;
         }
       }
       incomingRequestsCount.add(floor);
    }
    
    void addOutGoingRequest(int floor, char direction){
        outgoingRequestsCount.put(floor,
                1+outgoingRequestsCount.getOrDefault(floor,0));
    }

    /** count number of people who will be on given floor
     in given direction, returns -1 if direction is invalid for lift
     */
    public int countPeople(int floor, char direction){
        return state.countPeople(floor, direction);
    }
    
    char getMoveDirection(){
        return state.getDirection();
    }
    
    int getCurrentFloor(){
        return currentFloor;
    }

    void tick(){
        state.tick();
        // if there are no people inside lift and no incoming requests then change lift state to idle
        if(outgoingRequestsCount.size()==0 && incomingRequestsCount.size()==0)
            setState('I');
    }
    
    public void setState(char direction){
        if(direction=='U'){
            this.state=movingUpState;
            return;
        }
        if(direction=='D'){
            this.state=movingDownState;
            return;
        }
        this.state = idleState;
    }

    public void setCurrentFloor(int currentFloor){
        this.currentFloor= currentFloor;
    }
}

abstract class LiftState {
    protected Lift lift;
    LiftState(Lift lift){
        this.lift=lift;
    }
    public abstract char getDirection();
    public abstract int getTimeToReachFloor(int floor, char direction);
    /** counts only from people who are already in */
    public int countPeople(int floor, char direction){
        return 0;
    }
    public abstract void tick();
}

class MovingUpState extends LiftState{
    MovingUpState(Lift lift) {
        super(lift);
    }

    public int getTimeToReachFloor(int floor, char direction) {
        // left the floor or request is in opposite direction will be invalid
        if(direction!=getDirection()||floor<lift.getCurrentFloor()) return -1;
        return floor-lift.getCurrentFloor();
    }
    
    // people who will enter on given floor+people who are already in and will get off after floor
    public int countPeople(int floor, char direction){
       if(direction!='U') return 0;
       int peopleCount=0;
       for(int floorItr: lift.outgoingRequestsCount.keySet())
           if(floorItr>floor) peopleCount+=lift.outgoingRequestsCount.getOrDefault(
                   floorItr,0);
       return peopleCount;
    }

    public char getDirection() {
        return 'U';
    }

    public void tick(){
       // remove old floor
       lift.incomingRequestsCount.remove(lift.getCurrentFloor());
       // check if idle state has been achieved
       if(lift.incomingRequestsCount.isEmpty() && lift.outgoingRequestsCount.isEmpty()) return;
       lift.setCurrentFloor(lift.getCurrentFloor()+1);
       lift.outgoingRequestsCount.remove(lift.getCurrentFloor());
    }
}
 
class MovingDownState extends LiftState{
    MovingDownState(Lift lift) {
        super(lift);
    }

    public int getTimeToReachFloor(int floor, char direction) {
        if(direction!=getDirection() || floor> lift.getCurrentFloor()) return -1;
        return lift.getCurrentFloor()-floor;
    }

    // people who will enter on given floor+people who are already in and will get off after floor
    public int countPeople(int floor, char direction){
        if(direction!='D') return 0;
        int peopleCount=0;
        for(int floorItr: lift.outgoingRequestsCount.keySet())
            if(floorItr<floor) peopleCount+=lift.outgoingRequestsCount.getOrDefault(
                    floorItr,0);
        return peopleCount;
    }
    
    public char getDirection() {
        return 'D';
    }

    public void tick(){
        // remove old floor
        lift.incomingRequestsCount.remove(lift.getCurrentFloor());
        // check if idle state has been achieved, if there were requests on previous floor but no one entered
        if(lift.incomingRequestsCount.isEmpty() && lift.outgoingRequestsCount.isEmpty()) return;
        lift.setCurrentFloor(lift.getCurrentFloor()-1);
        lift.outgoingRequestsCount.remove(lift.getCurrentFloor());
    }

}

class IdleState extends LiftState{
    IdleState(Lift lift) {
        super(lift);
    }

    public char getDirection() {
        return 'I';
    }

    public int getTimeToReachFloor(int floor, char direction) {
        return Math.abs(floor-lift.getCurrentFloor());
    }

    public void tick(){}
}

class MovingUpToPickFirstState extends LiftState {
    MovingUpToPickFirstState(Lift lift) {
        super(lift);
    }

    public char getDirection() {
        return 'U';
    }

    public int getTimeToReachFloor(int floor, char direction) {
        // don't keep moving up else it will increase
        // waiting time for existing requests and its irritating
        int nextStop = nextStop();
        if (direction != 'D' || floor > nextStop) return -1;
        // go and pick first passenger and while coming down pick this request
        return nextStop - lift.getCurrentFloor() + nextStop - floor;
    }

    // topmost floor till which will go up to, it won't serve requests above this floor
    int nextStop() {
        int nextStop = -1;
        for (int floor : lift.incomingRequestsCount)
            if (nextStop < floor) nextStop = floor;
        return nextStop;
    }

    public void tick() {
        lift.setCurrentFloor(lift.getCurrentFloor() + 1);
        int nextFloor = nextStop();
        if (lift.getCurrentFloor() == nextFloor) {
            lift.setState('D');
        }
    }
}

class MovingDownToPickFirstState extends LiftState{
    MovingDownToPickFirstState(Lift lift) {
        super(lift);
    }

    public char getDirection() {
        return 'D';
    }

    public int getTimeToReachFloor(int floor, char direction) {
        int nextStop=nextStop();
        // don't keep moving down else it will increase
        // waiting time for existing requests and its irritating
        if(direction!='U' || floor<nextStop) return -1;
        if(nextStop<0) nextStop=floor;
        // go and pick first passenger and while coming down pick others
        return lift.getCurrentFloor()-nextStop+floor-nextStop;
    }

     // lowest floor lift will go down to
    private int nextStop(){
        int nextStop=-1;
        for(int floor: lift.incomingRequestsCount)
            if(nextStop<0||nextStop>floor) nextStop=floor;
        return nextStop;
    }

    public void tick(){
        lift.setCurrentFloor(lift.getCurrentFloor()-1);
        int nextFloor=nextStop();
        if(lift.getCurrentFloor()==nextFloor){
            lift.setState('U');
        }
    }
}

// uncomment below code in case you are using your local ide and
// comment it back again back when you are pasting completed solution in the online codezym editor
// this will help avoid unwanted compilation errors and get method autocomplete in your local code editor.
/**
 interface Q11ElevatorSystemInterface {
     void init(int floors, int lifts, int liftsCapacity, Helper11 helper);

     // press the outside UP or DOWN button, returns lift index or -1 
     int requestLift(int currentFloor, char direction);

     // returns true if floor is valid else returns false 
     void pressFloorButtonInLift(int liftIndex, int floor);

     // 4-U-8 lift at floor 4 going up with 8 people inside 
     String getLiftState(int liftIndex);

     // This method is called every second so that lift states can be appropriately updated.
     // we use this time rather than java.util.Date().time 
     public void tick();
 }

 class Helper11 {
     void print(String s){System.out.print(s);}
     void println(String s){System.out.println(s);}
 }
*/