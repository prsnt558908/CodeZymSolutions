# Design Last Lap Hero (F1 Race Tracker) in Python


#### Problem Statement
[https://codezym.com/question/46-design-last-lap-hero-f1-race-tracker](https://codezym.com/question/46-design-last-lap-hero-f1-race-tracker)


Keep a sorted list of just the three fastest laps, and store a running total and lap count for every driver who has completed a lap. Update these totals when a lap arrives, then scan the drivers when their ranking is requested. A simple tracker with two small data classes is enough, so no additional design pattern is needed.


## What must be ranked?


- **Fastest laps:** increasing lap time, then lower car ID, then lower lap ID. Return strings in the format `carId-lapId-timeTaken`.
- **Drivers:** increasing average lap time rounded to two decimal places, then lower car ID. Include only drivers with at least one recorded lap.


Both methods return at most three results. Before any laps are recorded, both return an empty list. Several fastest laps may belong to the same driver.


## Start with sorting everything


The straightforward approach stores every lap. Each query scans that history, computes the required ranking, sorts it, and returns the first three entries.


This works, but sorting all `N` laps costs `O(N log N)` for a fastest-lap query. Scanning old laps to recompute driver totals also repeats work we can avoid.


## Keep only the information we need


`LapTiming` is a small dataclass holding `carId`, `lapId`, and `timeTaken`. The `fastestLaps` list holds at most three of these objects between method calls.


`DriverStats` is a dataclass holding one driver's ID, total time, and lap count. Integer totals let us update the average without storing the driver's lap history.


`driverStats` is a dictionary from car ID to `DriverStats`. Create an entry on the driver's first recorded lap. This naturally excludes drivers who have not raced yet.


### When a lap arrives


Add its time to that driver's total and increase their lap count. Then add the new lap to `fastestLaps`, sort using the lap ranking rules, and remove the last entry if the list has grown to four.


Discarding that lap is safe. Three recorded laps already rank ahead of it, and their times never change. Adding more laps cannot bring the discarded lap back into the top three.


### When driver rankings are requested


Scan every active driver. Keep a temporary list of at most three candidates by adding one driver, sorting the list, and removing its last entry if it has grown to four.


Every active driver must remain in the map. A driver outside the top three can return after recording faster laps. A permanent list of just three drivers would miss that change.


## Averages and design choice


Divide the total time by the lap count, then round the average to two decimal places before comparing drivers. For positive values, count the whole hundredths and add one when the remaining fraction is at least `0.5`. This follows the required rounding without Python's `round()`, which can round halfway values to the nearest even number.


For example, averages of `70.004` and `70.000` both become `70.00`. The lower car ID wins this tie, even if its unrounded average is slightly slower.


Strategy would help if callers could choose different scoring rules. Here the rules are fixed, so a small method and a sorting key are simpler.


## Walk through an example


For four cars and three laps:


```text
recordLapTiming(1, 0, 70)
recordLapTiming(2, 0, 69)
recordLapTiming(3, 0, 69)

getTop3FastestLaps() -> ["2-0-69", "3-0-69", "1-0-70"]
getTop3Drivers()    -> [2, 3, 1]

recordLapTiming(2, 1, 71)
recordLapTiming(3, 1, 65)
recordLapTiming(1, 1, 66)

getTop3FastestLaps() -> ["3-1-65", "1-1-66", "2-0-69"]
getTop3Drivers()    -> [3, 1, 2]
```


The driver averages are now `68` for car 1, `70` for car 2, and `67` for car 3. Their ranking uses these averages, while the lap ranking still compares individual timings.


## Complexity


Let `A` be the number of drivers with at least one recorded lap.


- `recordLapTiming`: expected `O(1)`. The map update is expected constant time, and sorting at most four laps is constant work.
- `getTop3FastestLaps`: `O(1)`, because it reads at most three entries.
- `getTop3Drivers`: `O(A)`. Each driver is considered once, and each temporary sort contains at most four entries.
- Storage: `O(A)` for driver totals, plus at most three retained laps.


## Python implementation


IDs are valid and each car-lap pair is recorded at most once, as guaranteed by the statement. The constructor keeps the required parameters, but we do not need a full car-by-lap table or duplicate-record validation.


```python
from dataclasses import dataclass


@dataclass
class LapTiming:
    """Stores one completed lap."""

    carId: int
    lapId: int
    timeTaken: int


@dataclass
class DriverStats:
    """Stores the running totals for one driver."""

    carId: int
    totalTime: int = 0
    lapsRecorded: int = 0

    def roundedAverage(self) -> float:
        average = float(self.totalTime) / self.lapsRecorded
        # Apply the required rounding before comparing drivers.
        scaled = average * 100.0
        hundredths = int(scaled)
        if scaled - hundredths >= 0.5:
            hundredths += 1
        return hundredths / 100.0


class LastLapHero:
    def __init__(self, carsCount: int, lapsCount: int):
        # Inputs are valid, so no full car-by-lap table is needed.
        self.driverStats: dict[int, DriverStats] = {}
        self.fastestLaps: list[LapTiming] = []

    def recordLapTiming(self, carId: int, lapId: int, timeTaken: int) -> None:
        driver = self.driverStats.get(carId)
        if driver is None:
            driver = DriverStats(carId)
            self.driverStats[carId] = driver

        driver.totalTime += timeTaken
        driver.lapsRecorded += 1

        # At most four laps need sorting, then keep the best three.
        self.fastestLaps.append(LapTiming(carId, lapId, timeTaken))
        self.fastestLaps.sort(key=lambda lap: (lap.timeTaken, lap.carId, lap.lapId))

        if len(self.fastestLaps) > 3:
            self.fastestLaps.pop()

    def getTop3FastestLaps(self) -> list[str]:
        return [
            f"{lap.carId}-{lap.lapId}-{lap.timeTaken}"
            for lap in self.fastestLaps
        ]

    def getTop3Drivers(self) -> list[int]:
        topDrivers: list[DriverStats] = []

        # Rebuild this list because each driver's average can change.
        for driver in self.driverStats.values():
            topDrivers.append(driver)
            topDrivers.sort(key=lambda item: (item.roundedAverage(), item.carId))

            if len(topDrivers) > 3:
                topDrivers.pop()

        return [driver.carId for driver in topDrivers]
```
