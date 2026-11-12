# Train Route Fare Calculator in Python

#### Problem Statement

[https://codezym.com/question/431-train-route-fare-calculator](https://codezym.com/question/431-train-route-fare-calculator)

The key idea is to give each stop a position when it is added. The distance between two positions tells us how many segments the journey crosses. A dictionary lets us find these positions quickly, then we apply the weekend cap when needed.


## 1. Start with a list

Store stop names in a list in the order they are added. To calculate a fare, find both positions and take their absolute difference.

```python
startPosition = self.stops.index(startStop)
endPosition = self.stops.index(endStop)
fare = abs(startPosition - endPosition)

if isWeekend:
    fare = min(fare, self.weekendFareCap)
return fare
```

This works, but each `index` call can search the entire list. With `n` stops, one fare calculation takes `O(n)` time. Repeating these searches is the part we can improve.


## 2. Store positions in a dictionary

Use a dictionary called `stopPositions`. Its key is a stop name, and its value is that stop's position.


In `addStop`, assign the current dictionary size as the new position. The first stop gets `0`, the next gets `1`, and so on. This works because every stop name is unique.


In `calculateFare`, look up the two positions and calculate their absolute difference. Return that value on weekdays. On weekends, return the smaller of that value and `weekendFareCap`.


We only need one class, `TrainRouteFareCalculator`, to hold the dictionary and the cap. A separate list is unnecessary because fare queries never need to walk through the stops. The stored positions record the route order.


The `dataclass` creates the constructor that accepts `weekendFareCap`. Using `field(default_factory=dict, init=False)` gives each calculator its own empty dictionary and keeps that dictionary out of the constructor parameters.


## 3. Walk through an example

Add `Cedar`, `Lake`, `Museum`, `Market`, `Airport`, and `Harbor`, in that order. Their positions run from `0` to `5`. Set the weekend cap to `3`.


From `Cedar` to `Harbor`, the distance is `abs(0 - 5) = 5`. The weekday fare is `5`, and the weekend fare is `3`.


From `Market` to `Lake`, the distance is `abs(3 - 1) = 2`. Reverse travel works naturally, and the weekend fare stays `2`.


From `Museum` to itself, the distance is `0`. Each call applies the cap independently, so earlier journeys never change a later fare.


## 4. Why this works

Consecutive stops receive consecutive positions. Moving between neighboring positions crosses exactly one segment, so the absolute difference counts all segments between the requested stops.


Adding a stop at the end leaves earlier positions unchanged. The dictionary therefore remains correct even when additions and fare queries are mixed. The problem guarantees that queried stops already exist, so no missing-stop handling is needed.


## Complexity

For `n` stops, `addStop` takes amortized `O(1)` time on average, and `calculateFare` takes `O(1)` time on average. The dictionary uses `O(n)` space. These bounds treat the permitted stop-name length, at most 100 characters, as bounded.


## Final Code

```python
from dataclasses import dataclass, field
from typing import Dict


@dataclass
class TrainRouteFareCalculator:
    weekendFareCap: int
    stopPositions: Dict[str, int] = field(default_factory=dict, init=False)

    def addStop(self, stopName: str) -> None:
        # Stops are unique and are added only at the end.
        self.stopPositions[stopName] = len(self.stopPositions)

    def calculateFare(
        self, startStop: str, endStop: str, isWeekend: bool
    ) -> int:
        startPosition = self.stopPositions[startStop]
        endPosition = self.stopPositions[endStop]

        # Absolute difference supports travel in either direction.
        fare = abs(startPosition - endPosition)

        if isWeekend:
            fare = min(fare, self.weekendFareCap)

        return fare
```
