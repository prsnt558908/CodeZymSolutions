import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AgentRatingLeaderboard {

    // Stores total sum of ratings and count of ratings
    private static class Stats {
        long sum;
        long count;

        void add(int rating) {
            sum += rating;
            count++;
        }

        BigDecimal getAverage() {
            if (count == 0) {
                return BigDecimal.ZERO.setScale(1);
            }
            // average = sum / count, rounded to 1 decimal using HALF_UP
            return BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        }
    }

    // Overall stats per agent
    private final Map<String, Stats> overallStats;
    // Monthly stats: month ("YYYY-MM") -> (agentName -> Stats)
    private final Map<String, Map<String, Stats>> monthlyStats;

    public AgentRatingLeaderboard() {
        this.overallStats = new HashMap<String, Stats>();
        this.monthlyStats = new HashMap<String, Map<String, Stats>>();
    }

    public void rateAgent(String agentName, int rating, String date) {
        // Update overall stats
        Stats overall = overallStats.get(agentName);
        if (overall == null) {
            overall = new Stats();
            overallStats.put(agentName, overall);
        }
        overall.add(rating);

        // Extract month "YYYY-MM" from "YYYY-MM-DD"
        String month = date.substring(0, 7);

        // Update monthly stats
        Map<String, Stats> monthMap = monthlyStats.get(month);
        if (monthMap == null) {
            monthMap = new HashMap<String, Stats>();
            monthlyStats.put(month, monthMap);
        }

        Stats monthStats = monthMap.get(agentName);
        if (monthStats == null) {
            monthStats = new Stats();
            monthMap.put(agentName, monthStats);
        }
        monthStats.add(rating);
    }

    public List<String> getAverageRatings() {
        // Local helper class for sorting
        class AgentAverage {
            String name;
            BigDecimal average;

            AgentAverage(String name, BigDecimal average) {
                this.name = name;
                this.average = average;
            }
        }

        List<AgentAverage> list = new ArrayList<AgentAverage>();

        for (Map.Entry<String, Stats> entry : overallStats.entrySet()) {
            String agentName = entry.getKey();
            Stats stats = entry.getValue();
            BigDecimal avg = stats.getAverage();
            list.add(new AgentAverage(agentName, avg));
        }

        // Sort by average rating descending, then agent name ascending
        Collections.sort(list, new Comparator<AgentAverage>() {
            @Override
            public int compare(AgentAverage a1, AgentAverage a2) {
                int cmp = a2.average.compareTo(a1.average); // descending
                if (cmp != 0) {
                    return cmp;
                }
                return a1.name.compareTo(a2.name); // ascending by name
            }
        });

        List<String> result = new ArrayList<String>();
        for (AgentAverage aa : list) {
            result.add(aa.name + "," + aa.average.toString());
        }

        return result;
    }

    public List<String> getBestAgentsByMonth(String month) {
        Map<String, Stats> monthMap = monthlyStats.get(month);
        if (monthMap == null) {
            // No ratings for this month
            return new ArrayList<String>();
        }

        // Local helper class for sorting
        class AgentAverage {
            String name;
            BigDecimal average;

            AgentAverage(String name, BigDecimal average) {
                this.name = name;
                this.average = average;
            }
        }

        List<AgentAverage> list = new ArrayList<AgentAverage>();

        for (Map.Entry<String, Stats> entry : monthMap.entrySet()) {
            String agentName = entry.getKey();
            Stats stats = entry.getValue();
            BigDecimal avg = stats.getAverage();
            list.add(new AgentAverage(agentName, avg));
        }

        // Sort by average rating descending, then agent name ascending
        Collections.sort(list, new Comparator<AgentAverage>() {
            @Override
            public int compare(AgentAverage a1, AgentAverage a2) {
                int cmp = a2.average.compareTo(a1.average); // descending
                if (cmp != 0) {
                    return cmp;
                }
                return a1.name.compareTo(a2.name); // ascending by name
            }
        });

        List<String> result = new ArrayList<String>();
        for (AgentAverage aa : list) {
            result.add(aa.name + "," + aa.average.toString());
        }

        return result;
    }
}
