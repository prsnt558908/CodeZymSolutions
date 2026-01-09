import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

public class LastLapHero {

    private final int carsCount;
    private final int lapsCount;

    // Top 3 fastest laps (sorted by timeTaken asc, then carId asc, then lapId asc)
    private final TreeSet<LapEntry> topLaps;

    // Driver ranking (sorted by avg asc, then carId asc)
    private final TreeSet<DriverEntry> driverRank;
    private final DriverEntry[] drivers; // one object per carId

    public LastLapHero(int carsCount, int lapsCount) {
        this.carsCount = carsCount;
        this.lapsCount = lapsCount;

        this.topLaps = new TreeSet<>(new Comparator<LapEntry>() {
            @Override
            public int compare(LapEntry a, LapEntry b) {
                if (a.timeTaken != b.timeTaken) {
                    return Integer.compare(a.timeTaken, b.timeTaken);
                }
                if (a.carId != b.carId) {
                    return Integer.compare(a.carId, b.carId);
                }
                // IMPORTANT: numeric lapId tie-breaker (NOT lexicographic string compare)
                if (a.lapId != b.lapId) {
                    return Integer.compare(a.lapId, b.lapId);
                }
                // Same triple => equal
                return 0;
            }
        });

        this.driverRank = new TreeSet<>(new Comparator<DriverEntry>() {
            @Override
            public int compare(DriverEntry a, DriverEntry b) {
                int cmp = Double.compare(a.avg, b.avg);
                if (cmp != 0) return cmp;
                return Integer.compare(a.carId, b.carId);
            }
        });

        this.drivers = new DriverEntry[carsCount];
        for (int carId = 0; carId < carsCount; carId++) {
            drivers[carId] = new DriverEntry(carId);
        }
    }

    public void recordLapTiming(int carId, int lapId, int timeTaken) {
        // 1) Update top 3 fastest laps
        LapEntry newLap = new LapEntry(carId, lapId, timeTaken);

        if (topLaps.size() < 3) {
            topLaps.add(newLap);
        } else {
            LapEntry worst = topLaps.last();
            if (topLaps.comparator().compare(newLap, worst) < 0) {
                topLaps.add(newLap);
                if (topLaps.size() > 3) {
                    topLaps.pollLast();
                }
            }
        }

        // 2) Update driver averages and ranking
        DriverEntry d = drivers[carId];
        if (d.lapsRecorded > 0) {
            driverRank.remove(d); // remove old position before mutating
        }

        d.totalTime += timeTaken;
        d.lapsRecorded += 1;
        d.avg = round2((double) d.totalTime / (double) d.lapsRecorded);

        driverRank.add(d);
    }

    public List<String> getTop3FastestLaps() {
        List<String> res = new ArrayList<>();
        for (LapEntry e : topLaps) {
            res.add(e.asRow());
        }
        return res;
    }

    public List<Integer> getTop3Drivers() {
        List<Integer> res = new ArrayList<>();
        Iterator<DriverEntry> it = driverRank.iterator();
        while (it.hasNext() && res.size() < 3) {
            res.add(it.next().carId);
        }
        return res;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // -------------------- Helpers --------------------

    private static class LapEntry {
        final int carId;
        final int lapId;
        final int timeTaken;

        LapEntry(int carId, int lapId, int timeTaken) {
            this.carId = carId;
            this.lapId = lapId;
            this.timeTaken = timeTaken;
        }

        String asRow() {
            return carId + "-" + lapId + "-" + timeTaken;
        }
    }

    private static class DriverEntry {
        final int carId;
        long totalTime;
        int lapsRecorded;
        double avg;

        DriverEntry(int carId) {
            this.carId = carId;
            this.totalTime = 0L;
            this.lapsRecorded = 0;
            this.avg = 0.0;
        }
    }
}
