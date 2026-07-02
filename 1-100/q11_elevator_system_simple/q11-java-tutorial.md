# Simple explanation for design of an Elevator System in Java using state pattern for low level design interviews

Elevator system design is one of the common but complex problems that you can be asked in low level design interviews.
There are too many situations to deal with and if you try to do everything in same class then your code will become very complex very quickly.

Please go through problem statement before reading ahead

https://codezym.com/question/11-design-simple-elevator-system-multiple-lifts

Our goal is to keep the explanation simple and short enough to fit in a 45–60 minutes face to face interview.

We will discuss the requirements,
Then build an easy to understand solution using state design pattern.
Finally, we will have complete code in both Java.
We have an elevator system managing multiple lifts in a building with multiple floors. Each lift will have the same capacity.

## Requirements

### 1. User can press Up/Down button outside any lift in corridor

User can press Up/Down button outside any lift in corridor. They will be assigned a liftIndex. You go and stand in front of that lift and after some time it will come to your floor.

```java
String requestLift(startFloor, direction)
```

direction: ‘U’ = going Up, ‘D’ = going Down
users may request a lift but may not eventually enter it when it comes to their floor, because lift me be full or they too another lift.
It may also happen that one person requested the lift but multiple people enter it when it comes up.

### 2. When your assigned lift comes to your floor, you enter inside it and press the destination floor button

```java
pressFloorButtonInLift(liftIndex, destinationFloor)
```

every person entering the lift will press their destination floor exactly once. This assumption is made to accurately track number of people inside lift.
Parameters liftIndex and destinationFloor will always be valid. i.e. you can’t enter a lift if it is already full. Also, you can’t enter a floor which will change move direction of lift. e.g. if lift is going up and you entered at floor 4, then at this point you can’t press a destination floor button less than 4.

### 3. Lift management system may also want to track state of a lift

Lift management system may also want to track state of a lift. State of a lift includes

- floor on which lift is currently at, between 0 and floorsCount-1
- direction in which lift is moving. ‘U’ for Up, ‘D’ for down and ‘I’ for standing idle.
- number of people inside lift, between 0 and liftCapacity

```java
String getLiftState(liftIndex)
```

e.g. getLiftState(0) returns “4-U-8”. It means Lift 0 is at floor 4, it is moving up and there are 8 people inside it.

### 4. Finally, we need to simulate working of lift

Finally, we need to simulate working of lift. In real world there will generally be at least two events/method calls to track all the lifts.

- first when lift stops on any floor.
- second when people have gone out or/and entered, lift door closes and it starts moving to next floor.

But we will simplify things a bit so that our discussion fits in a 60-minute interview duration.

Lets assume that lift takes exactly one second to move from one floor to next whether it is moving up or down.

Also let’s assume time taken for all people to come inside or move out of lift when it stops on any floor is zero seconds.

```java
tick()
```

Every time tick() is called, it means one second has passed in our system and so we need to update state of all lifts.
In other words, tick() means lift has reached next floor and its gates have opened, so people can more out of lift and new passengers can come in.
Use tick() to track seconds passed rather than system time.

## Breaking our solution in multiple Classes

After clearing requirements with the interviewer, next is class diagram/class design/UML diagram section.

Lift system is nothing but a collection of lifts. So the most important class in our system will be class Lift.

Each lift will be responsible tracking its state, floors it is going to stop at and number of people inside and their destination floor.

```java
class Lift {
    private int currentFloor, floors, capacity;

    /* floors on which lift is going to stop */
    HashSet<Integer> incomingRequestsCount;

    /** people who are already inside lift and 
     have chosen on which floor they will get off */
    HashMap<Integer, Integer> outgoingRequestsCount;
    
    /** - Lift will operate between floor 0 and floors-1
        - capacity is maximum number of people that lift can carry
    */
    Lift(int floors, int capacity){}
    
    public int getCurrentPeopleCount(){}

    int getTimeToReachFloor(int floor, char direction){}

    void tick(){}
    
    // it assumes that parameters will always be valid and
    // floor will always be reachable in the given direction
    void addIncomingRequest(int floor, char direction){}
    
    void addOutGoingRequest(int floor, char direction){}

    /** count number of people who will be on given floor
     in given direction, returns -1 if direction is invalid for lift
     */
    public int countPeople(int floor, char direction){}
    
    char getMoveDirection(){}
    
    int getCurrentFloor(){}

   
}
```

To assign a lift to a user you will need to choose a lift which will take the minimum time to reach user’s floor in given direction.
So we have the method int getTimeToReachFloor(floor, direction) inside class Lift. Lift manager class will call this for all lifts and choose the lift which takes minimum time and has capacity available for that request.

## Analyzing different scenarios in Lift class

Behavior and return value of different methods inside class Lift depends on state of the lift.

For example, let's assume a lift with capacity for 4 people and a request comes to go up from floor 4: requestLift(startFloor=4, direction=’U’)

- if the lift was at floor 2 and moving up then it would take 2 seconds to reach there
- If lift was at floor 5 and going up then this lift can’t be assigned to this request because it has already passed the floor so return value will be -1
- Again if lift was at floor 5 but going down, still it can’t be assigned to this request because it is moving in opposite direction.
- If lift is at floor 1 and moving up but already has 4 people all of whom will get off after floor 4 only even then this lift can’t be assigned this request because it is of full capacity
- However if lift was at floor 1 and already has 4 people inside but atleast one of them has destination floor as floor 4 or floor 3 or floor 2 then this lift would be return a valid time i.e. 4–1 = 3 seconds for this request. Because on floor 4 there will be space.

Similarly tick() method will also behave differently based on lift state.

- if lift is going up then tick() will increase lift’s current floor by 1
- if lift is going down then it will decrease lift’s current floor by 1 and so on..

## Using State design pattern

As of now you would have guessed, since the behavior of methods in lift class changes with lift’s state, so it makes sense the identify the different states and put logic of each state in a separate class. This would simplify our overall solution. This is an ideal use case for state design pattern.


All of the state classes will follow a common interface/superclass.

Lets call it class LiftState.

```java
abstract class LiftState {
    protected Lift lift;
    LiftState(Lift lift){
        this.lift=lift;
    }
    public abstract char getDirection();
    public abstract int getTimeToReachFloor(int floor, char direction);
    
    /** counts how many people will be inside lift when it reaches 'floor' 
        counts only from people who are already in 
        and their destination floor is after floor */
    public int countPeople(int floor, char direction){
        return 0;
    }

    public abstract void tick();
}
```

Methods of the LiftState class will vary with each state.

## Identifying different states

Now intuitively you can guess the three states you lift will be in based on its move direction.

- MovingUpState: When the lift is going up
- MovingDownState: When the lift is going down
- IdleState: when the lift is standing idle on any floor.

But if you analyze a bit more, there will be two more states.

Lets suppose a lift is at floor 4 and it has no passengers inside it. It is moving up to pick its first passenger at floor 8 who will event go down. So even though lift is going up still it can be assigned only to requests in down direction.
This is MovingUpToPickFirstState, where a lift ig going up to pick it first passenger who will go down.

Just opposite to above a lift may be going down to pick its first passenger who will eventually go up. This MovingDownToPickFirst.
After first person enters the lift, lift will convert to MovingUpState.

Let's go through the different states one by one.

## Class MovingUpState

This state is for lift going up.

In tick() method we first remove requests from previous floor before increasing currentFloor. We don’t remove incoming requests entry for current floor because lift has just reached this floor and they are yet to come (i.e. call pressFloorButton method).

```java
class MovingUpState extends LiftState{
    MovingUpState(Lift lift) {
        super(lift);
    }

    public int getTimeToReachFloor(int floor, char direction) {
        // if lift left the floor or request is in opposite direction
        // will be invalid then it can't be assigned
        if(direction!=getDirection() ||
          floor<lift.getCurrentFloor()) return -1;
        return floor-lift.getCurrentFloor();
    }
    
    // people who are already inside lift and will get off after 'floor'
    public int countPeople(int floor, char direction){
       if(direction!='U') return 0;
       int peopleCount=0;
       for(int floorItr: lift.outgoingRequestsCount.keySet())
           if(floorItr>floor) peopleCount+=
              lift.outgoingRequestsCount.getOrDefault(floorItr,0);
       return peopleCount;
    }

    public char getDirection() {
        return 'U';
    }

    public void tick(){
       // remove requests from old floor
       lift.incomingRequestsCount.remove(lift.getCurrentFloor());
       // check if idle state has been achieved
       if(lift.incomingRequestsCount.isEmpty() && 
          lift.outgoingRequestsCount.isEmpty()) return;
       lift.setCurrentFloor(lift.getCurrentFloor()+1);
       lift.outgoingRequestsCount.remove(lift.getCurrentFloor());
    }
}
```

## Class MovingDownState

This state is for lift going down.

```java
class MovingDownState extends LiftState{
    MovingDownState(Lift lift) {
        super(lift);
    }

    public int getTimeToReachFloor(int floor, char direction) {
        if(direction!=getDirection() ||
           floor> lift.getCurrentFloor()) return -1;
        return lift.getCurrentFloor()-floor;
    }

    // people who are already in and will get off after 'floor'
    public int countPeople(int floor, char direction){
        if(direction!='D') return 0;
        int peopleCount=0;
        for(int floorItr: lift.outgoingRequestsCount.keySet())
            if(floorItr<floor) peopleCount+=
              lift.outgoingRequestsCount.getOrDefault(floorItr,0);
        return peopleCount;
    }
    
    public char getDirection() {
        return 'D';
    }

    public void tick(){
        // remove request for previous floor
        lift.incomingRequestsCount.remove(lift.getCurrentFloor());
        // check if idle state has been achieved, 
        // if there were requests on previous floor but no one entered
        if(lift.incomingRequestsCount.isEmpty() && 
            lift.outgoingRequestsCount.isEmpty()) return;
        lift.setCurrentFloor(lift.getCurrentFloor()-1);
        lift.outgoingRequestsCount.remove(lift.getCurrentFloor());
    }

}
```

## Class IdleState

When lift is standing idle on some floor.

```java
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
    // nothing happens, lift remains wherever it is
    public void tick(){}
}
```

## Class MovingUpToPickFirstState

Lift is moving up to pick its first passenger who will then go down. So even though lift is going up still it can be assigned only to requests in down direction.

This state changes to MovingDownState once it reaches its topmost floor which is requested by first passenger.
Also the highest floor it will take requests is the floor of first passenger’s request. e.g. if the first passenger’s request is requestFloor(8, ‘D’) then it can be assigned only to down requests below floor it, it can’t be assigned to requestLift(10, ‘D’) because that will increase wait time for first passenger.

```java
class MovingUpToPickFirstState extends LiftState {
    MovingUpToPickFirstState(Lift lift) {
        super(lift);
    }

    public char getDirection() {
        return 'U';
    }

   // lift will travel to floor of topmost request and 
  // while coming down it will pick all other requests
    public int getTimeToReachFloor(int floor, char direction) {
        // don't keep moving up else it will increase
        // waiting time for existing requests and its irritating
        int nextStop = nextStop();
        if (direction != 'D' || floor > nextStop) return -1;
        // go and pick first passenger and while coming down pick this request
        return nextStop - lift.getCurrentFloor() + nextStop - floor;
    }
    
    public void tick() {
        lift.setCurrentFloor(lift.getCurrentFloor() + 1);
        int nextFloor = nextStop();
        if (lift.getCurrentFloor() == nextFloor) {
            lift.setState('D');
        }
    }

    // topmost floor till which will lift go up to, 
    // it won't serve requests above this floor
    int nextStop() {
        int nextStop = -1;
        for (int floor : lift.incomingRequestsCount)
            if (nextStop < floor) nextStop = floor;
        return nextStop;
    }
}
```

## Class MovingDownToPickFirstState

This state is for lift which is moving down to pick the first passenger’s request and after reaching there its state will change to MovingUpState.

```java
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

    public void tick(){
        lift.setCurrentFloor(lift.getCurrentFloor()-1);
        int nextFloor=nextStop();
        if(lift.getCurrentFloor()==nextFloor){
            lift.setState('U');
        }
    }

     // lowest floor lift will go down to
    private int nextStop(){
        int nextStop=-1;
        for(int floor: lift.incomingRequestsCount)
            if(nextStop<0||nextStop>floor) nextStop=floor;
        return nextStop;
    }
}
```

## Class Lift

All these state classes will be used by our Lift class. Lift class directly calls methods of state classes.

```java
class Lift {
    private int currentFloor, floors, capacity;
    /* people who will get in lift later if space is there */

    HashSet<Integer> incomingRequestsCount;
    /** people who are already inside lift and 
      have chosen on which floor they will get off */
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
        // if there are no people inside lift and
        // no incoming requests then change lift state to idle
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
```

If you remember earlier is mentioned that a lift management system is nothing but a collection of lifts.

## Class Solution

It glues everything. It acts as lifts manager and It keeps a list of lifts. It assigns the optimal lifts for each request.

It initializes all the lifts in its constructor.
It doesn’t knows about any state classes and deals only with lift class.

```java
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
      // initializes all lifts
        for(int i=0;i<lifts;i++) 
          this.lifts[i]=new Lift(floors, liftsCapacity);
        // helper.println("Lift system initialized ...");
    }
    
    /** maximum count of people inside lift liftCapacity 
     * user press the outside UP or DOWN button outside lift,
     returns lift index or -1 */
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
```

## Complete Java Code

```java
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
```
