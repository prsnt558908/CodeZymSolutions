# Train Route Fare Calculator in Java

#### Problem Statement

[https://codezym.com/question/431-train-route-fare-calculator](https://codezym.com/question/431-train-route-fare-calculator)

The key idea is to give each stop a position when it is added. The distance between two positions tells us how many segments the journey crosses. A map lets us find these positions quickly, then we apply the weekend cap when needed.


## 1. Start with a list

Store stop names in an `ArrayList` in the order they are added. To calculate a fare, find both positions and take their absolute difference.

```java
int startPosition = stops.indexOf(startStop);
int endPosition = stops.indexOf(endStop);
int fare = Math.abs(startPosition - endPosition);

if (isWeekend) {
    fare = Math.min(fare, weekendFareCap);
}
return fare;
```

This works, but each `indexOf` can search the entire list. With `n` stops, one fare calculation takes `O(n)` time. Repeating these searches is the part we can improve.


## 2. Store positions in a map

Use a `HashMap<String, Integer>` called `stopPositions`. Its key is a stop name, and its value is that stop's position.


In `addStop`, assign the current map size as the new position. The first stop gets `0`, the next gets `1`, and so on. This works because every stop name is unique.


In `calculateFare`, look up the two positions and calculate their absolute difference. Return that value on weekdays. On weekends, return the smaller of that value and `weekendFareCap`.


We only need one class, `TrainRouteFareCalculator`, to hold the map and the cap. A separate list is unnecessary because fare queries never need to walk through the stops. The map itself does not need to preserve insertion order. The stored position already records that order.


## 3. Walk through an example

Add `Cedar`, `Lake`, `Museum`, `Market`, `Airport`, and `Harbor`, in that order. Their positions run from `0` to `5`. Set the weekend cap to `3`.


From `Cedar` to `Harbor`, the distance is `abs(0 - 5) = 5`. The weekday fare is `5`, and the weekend fare is `3`.


From `Market` to `Lake`, the distance is `abs(3 - 1) = 2`. Reverse travel works naturally, and the weekend fare stays `2`.


From `Museum` to itself, the distance is `0`. Each call applies the cap independently, so earlier journeys never change a later fare.


## 4. Why this works

Consecutive stops receive consecutive positions. Moving between neighboring positions crosses exactly one segment, so the absolute difference counts all segments between the requested stops.


Adding a stop at the end leaves earlier positions unchanged. The map therefore remains correct even when additions and fare queries are mixed. The problem guarantees that queried stops already exist, so no missing-stop handling is needed.


## Complexity

For `n` stops, `addStop` takes amortized `O(1)` time on average, and `calculateFare` takes `O(1)` time on average. The map uses `O(n)` space. These bounds treat the permitted stop-name length, at most 100 characters, as bounded.


## Final Code

```java
import java.util.HashMap;
import java.util.Map;

public class TrainRouteFareCalculator {
    int weekendFareCap;
    Map<String, Integer> stopPositions;

    public TrainRouteFareCalculator(int weekendFareCap) {
        this.weekendFareCap = weekendFareCap;
        this.stopPositions = new HashMap<>();
    }

    public void addStop(String stopName) {
        // Stops are unique and are added only at the end.
        stopPositions.put(stopName, stopPositions.size());
    }

    public int calculateFare(
            String startStop, String endStop, boolean isWeekend) {
        int startPosition = stopPositions.get(startStop);
        int endPosition = stopPositions.get(endStop);

        // Absolute difference supports travel in either direction.
        int fare = Math.abs(startPosition - endPosition);

        if (isWeekend) {
            fare = Math.min(fare, weekendFareCap);
        }

        return fare;
    }
}
```
