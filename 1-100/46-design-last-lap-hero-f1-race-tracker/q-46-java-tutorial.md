# Design Last Lap Hero (F1 Race Tracker) in Java


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


`LapTiming` is a small data class holding `carId`, `lapId`, and `timeTaken`. The `fastestLaps` list holds at most three of these objects between method calls.


`DriverStats` holds one driver's ID, total time, and lap count. Use `long` for total time because adding several valid `int` timings can overflow an `int`.


`driverStats` is a map from car ID to `DriverStats`. Create an entry on the driver's first recorded lap. This naturally excludes drivers who have not raced yet.


### When a lap arrives


Add its time to that driver's total and increase their lap count. Then add the new lap to `fastestLaps`, sort using the lap ranking rules, and remove the last entry if the list has grown to four.


Discarding that lap is safe. Three recorded laps already rank ahead of it, and their times never change. Adding more laps cannot bring the discarded lap back into the top three.


### When driver rankings are requested


Scan every active driver. Keep a temporary list of at most three candidates by adding one driver, sorting the list, and removing its last entry if it has grown to four.


Every active driver must remain in the map. A driver outside the top three can return after recording faster laps. A permanent list of just three drivers would miss that change.


## Averages and design choice


Divide using `(double) totalTime / lapsRecorded`, then apply `Math.round(average * 100.0) / 100.0` before comparing drivers. The cast prevents integer division from losing the fractional part.


For example, averages of `70.004` and `70.000` both become `70.00`. The lower car ID wins this tie, even if its unrounded average is slightly slower.


Strategy would help if callers could choose different scoring rules. Here the rules are fixed, so a small method and a comparator are simpler.


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


## Java implementation


IDs are valid and each car-lap pair is recorded at most once, as guaranteed by the statement. The constructor keeps the required parameters, but we do not need a full car-by-lap table or duplicate-record validation.


```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stores one completed lap. */
class LapTiming {
    int carId;
    int lapId;
    int timeTaken;

    LapTiming(int carId, int lapId, int timeTaken) {
        this.carId = carId;
        this.lapId = lapId;
        this.timeTaken = timeTaken;
    }
}

/** Stores the running totals for one driver. */
class DriverStats {
    int carId;
    long totalTime;
    int lapsRecorded;

    DriverStats(int carId) {
        this.carId = carId;
        totalTime = 0;
        lapsRecorded = 0;
    }

    double roundedAverage() {
        double average = (double) totalTime / lapsRecorded;
        // Apply the required rounding before comparing drivers.
        return Math.round(average * 100.0) / 100.0;
    }
}

public class LastLapHero {
    Map<Integer, DriverStats> driverStats;
    List<LapTiming> fastestLaps;

    public LastLapHero(int carsCount, int lapsCount) {
        // Inputs are valid, so no full car-by-lap table is needed.
        driverStats = new HashMap<>();
        fastestLaps = new ArrayList<>();
    }

    public void recordLapTiming(int carId, int lapId, int timeTaken) {
        DriverStats driver = driverStats.get(carId);
        if (driver == null) {
            driver = new DriverStats(carId);
            driverStats.put(carId, driver);
        }

        driver.totalTime += timeTaken;
        driver.lapsRecorded++;

        // At most four laps need sorting, then keep the best three.
        fastestLaps.add(new LapTiming(carId, lapId, timeTaken));
        fastestLaps.sort((first, second) -> {
            int comparison = Integer.compare(first.timeTaken, second.timeTaken);
            if (comparison != 0) {
                return comparison;
            }

            comparison = Integer.compare(first.carId, second.carId);
            if (comparison != 0) {
                return comparison;
            }

            return Integer.compare(first.lapId, second.lapId);
        });

        if (fastestLaps.size() > 3) {
            fastestLaps.remove(fastestLaps.size() - 1);
        }
    }

    public List<String> getTop3FastestLaps() {
        List<String> result = new ArrayList<>();
        for (LapTiming lap : fastestLaps) {
            result.add(lap.carId + "-" + lap.lapId + "-" + lap.timeTaken);
        }
        return result;
    }

    public List<Integer> getTop3Drivers() {
        List<DriverStats> topDrivers = new ArrayList<>();

        // Rebuild this list because each driver's average can change.
        for (DriverStats driver : driverStats.values()) {
            topDrivers.add(driver);
            topDrivers.sort((first, second) -> {
                int comparison = Double.compare(
                    first.roundedAverage(), second.roundedAverage()
                );
                if (comparison != 0) {
                    return comparison;
                }
                return Integer.compare(first.carId, second.carId);
            });

            if (topDrivers.size() > 3) {
                topDrivers.remove(topDrivers.size() - 1);
            }
        }

        List<Integer> result = new ArrayList<>();
        for (DriverStats driver : topDrivers) {
            result.add(driver.carId);
        }
        return result;
    }
}
```
