# Passenger Count During Car Trips in Java

#### Problem Statement
[https://codezym.com/question/180-passenger-count-during-car-trips](https://codezym.com/question/180-passenger-count-during-car-trips)


Every trip adds the same number of passengers to a continuous stretch of locations. Adding them to each location one by one is easy, but it becomes very slow when trips are long and there are many of them.


The better idea: a trip changes the passenger count at only **two** places. It goes up where people get in, and goes down where they get out. So we only write down these two changes for each trip.


Then one pass from left to right, keeping a running total, gives the passenger count at every location. This trick is called a **difference array**.


## Understanding the Problem

A trip `"passengers,from,to"` means people get in at `from` and get out at `to`.


Since they get out at `to`, they are counted at `from, from + 1, ..., to - 1`, but **not** at `to`.


Here is Example 1 (`locationCount = 8`, `trips = ["2,1,5","3,3,7"]`) as a table:

| Location | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|---|
| Trip `"2,1,5"` |  | 2 | 2 | 2 | 2 |  |  |  |
| Trip `"3,3,7"` |  |  |  | 3 | 3 | 3 | 3 |  |
| **Total** | **0** | **2** | **2** | **5** | **5** | **3** | **3** | **0** |


The **Total** row is the answer: `[0,2,2,5,5,3,3,0]`.


## Approach 1: Brute Force

Do exactly what the table shows. For every trip, add its passengers to each location from `from` to `to - 1`.

```java
int[] count = new int[locationCount];

for (String trip : trips) {
    // "2,1,5" -> passengers = 2, from = 1, to = 5
    String[] parts = trip.split(",");
    int passengers = Integer.parseInt(parts[0].trim());
    int from = Integer.parseInt(parts[1].trim());
    int to = Integer.parseInt(parts[2].trim());

    // add the passengers to every location they ride through
    for (int i = from; i < to; i++) {
        count[i] += passengers;
    }
}
```


### Why is this slow?

One trip can cover up to 100,000 locations, and there can be 100,000 trips. That is up to 10 billion additions, which is far too slow.


The waste is easy to see. A trip adds the **same** number to a whole stretch, yet we still visit every location in it one by one.


- **Time:** O(T × L), where T is the number of trips and L is `locationCount`
- **Space:** O(L)


## Approach 2: Difference Array (Optimal)

### Key Idea

Read the row of trip `"2,1,5"` in the table from left to right. Its value changes only twice:

- at location `1` it goes up by 2 (2 people get in)
- at location `5` it goes down by 2 (2 people get out)


Everywhere else, the value is the same as at the location just before it. So we don't need to fill every cell. We only need to record **where the count changes**.


Think of a conductor who never counts heads. He only notes how many people got in or out at each stop. To know how many are on board, he adds up all his notes so far.


We keep an `int` array called `change`. `change[i]` tells how much the count goes up or down when the car reaches location `i`. A plain array works well here, because a location number can be used directly as an index.


For each trip `"p,from,to"` we write two notes:

- `change[from] += p`
- `change[to] -= p`


Then we walk from location `0` to the end with a running total. At each location we add `change[i]` to the total, and that total is the passenger count at that location.


Overlapping trips need no extra work. Each trip adds its own two notes, and the running total adds them all up.


### Dry Run (Example 1)

The two trips write four notes:

- Trip `"2,1,5"`: `change[1] += 2` and `change[5] -= 2`
- Trip `"3,3,7"`: `change[3] += 3` and `change[7] -= 3`


| Location | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|---|
| `change` | 0 | +2 | 0 | +3 | 0 | -2 | 0 | -3 |
| Running total | 0 | 2 | 2 | 5 | 5 | 3 | 3 | 0 |


The running total row is exactly the expected answer. Each trip took only 2 updates, no matter how long it was.


### Why does `change` have one extra slot?

`to` can be equal to `locationCount`. In Example 2 (`locationCount = 5`), trip `"1,2,5"` ends at `5`, but the last location is `4`. So its "get out" note goes to `change[5]`.


That is why `change` has size `locationCount + 1`. The extra slot only holds these notes and is never part of the answer.


### Complexity

- **Time:** O(T + L). Each trip is just 2 updates, plus one pass over the locations.
- **Space:** O(L) for the `change` array.


The largest possible count is 100 passengers × 100,000 trips = 10,000,000, so an `int` is big enough.


### Java Code

```java
import java.util.*;

public class PassengerCountDuringCarTrips {

    public PassengerCountDuringCarTrips() {
    }

    /**
     * Returns the number of passengers in the car at each location.
     * A trip "passengers,from,to" is counted at locations from, from + 1, ..., to - 1.
     */
    public List<Integer> getPassengerCountAfterTrips(int locationCount, List<String> trips) {
        // change[i] = how much the passenger count goes up or down at location i.
        // One extra slot, because a trip can end at to = locationCount.
        int[] change = new int[locationCount + 1];

        for (String trip : trips) {
            // "2,1,5" -> passengers = 2, from = 1, to = 5
            String[] parts = trip.split(",");
            int passengers = Integer.parseInt(parts[0].trim());
            int from = Integer.parseInt(parts[1].trim());
            int to = Integer.parseInt(parts[2].trim());

            change[from] += passengers; // passengers get in at 'from'
            change[to] -= passengers;   // and get out at 'to'
        }

        // A running total of the changes gives the count at every location.
        List<Integer> result = new ArrayList<>();
        int current = 0;
        for (int i = 0; i < locationCount; i++) {
            current += change[i];
            result.add(current);
        }
        return result;
    }
}
```