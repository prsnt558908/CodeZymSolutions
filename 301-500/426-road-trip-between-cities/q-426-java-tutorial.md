# Road Trip Between Cities in Java

#### Problem Statement
[https://codezym.com/question/426-road-trip-between-cities](https://codezym.com/question/426-road-trip-between-cities)


Think of every city as a dot and every road as a line joining two dots. The question then becomes simple: is the arrival city in the same connected piece of the map as the departure city?


To find out, we start at the departure city and keep visiting every city we can reach, one road at a time. If the arrival city shows up, the trip is possible. If we run out of new cities first, it is not.


The key to making this fast is a **neighbor list**. We read all the roads once and note down, for every city, which cities it is directly connected to. After that, we never search through the full road list again.


We will also see a second approach called **Union-Find**. It puts connected cities into the same group, and then just checks if both cities ended up in the same group.


## Brute Force: Scan All Roads for Every City

Let us start with the most direct way to explore the map.


1. Keep a queue of cities we have reached but not explored yet. Put the departure city in it.
2. Take a city out of the queue. If it is the arrival city, return `true`.
3. Otherwise, find every city that has a road to it, and add the new ones to the queue.
4. If the queue becomes empty, every reachable city has been explored. Return `false`.


We also keep a `visited` array. Roads are two-way, so without it we would keep going back and forth between the same two cities forever.


This way of exploring, level by level using a queue, is called **Breadth First Search (BFS)**.


The slow part is step 3. To find the neighbors of one city, we read the whole road list:


```java
boolean[] visited = new boolean[cityCount];
Queue<Integer> queue = new ArrayDeque<>();
visited[departureCity] = true;
queue.add(departureCity);

while (!queue.isEmpty()) {
    int city = queue.poll();
    if (city == arrivalCity) {
        return true;
    }

    // Slow: read ALL roads just to find the neighbors of one city
    for (String road : roads) {
        String[] parts = road.split(",");
        int firstCity = Integer.parseInt(parts[0].trim());
        int secondCity = Integer.parseInt(parts[1].trim());

        int nextCity = -1;
        if (firstCity == city) {
            nextCity = secondCity;
        } else if (secondCity == city) {
            nextCity = firstCity;
        }

        if (nextCity != -1 && !visited[nextCity]) {
            visited[nextCity] = true;
            queue.add(nextCity);
        }
    }
}
return false;
```


### Why is this slow?

Every city we take out of the queue reads all the roads again. With up to 200,000 reachable cities and 200,000 roads, that is about 200,000 × 200,000 = 40 billion checks.


Most of this work is wasted. A city usually touches only a few roads, but we read all 200,000 of them just to find those few.


## Solution 1: BFS with a Neighbor List

The fix is simple: read the roads only once.


Before exploring, we build a neighbor list where `neighbors.get(city)` holds every city that has a direct road to `city`. A two-way road `"a,b"` adds `b` to the list of `a`, and `a` to the list of `b`.


Now step 3 is instant. The BFS loop stays the same. Only the scan over all roads is replaced by a loop over `neighbors.get(city)`.


### Why these data structures?

- **`List<List<Integer>> neighbors`**: gives the direct neighbors of any city in one step. This is where the whole speedup comes from.
- **`boolean[] visited`**: cities are numbered `0` to `cityCount - 1`, so a boolean array works like a set. `visited[city]` tells us in one step if a city was already seen.
- **`Queue<Integer> queue`**: holds the cities that are reached but not explored yet.


We use a queue instead of recursion on purpose. If the map is one long chain of 200,000 cities, recursive code would go 200,000 calls deep and crash with a `StackOverflowError`.


### Walkthrough (Example 1)

Roads: `0-4, 4-7, 7-2, 1-5, 5-8, 8-6`. Departure is `0`, arrival is `2`.


- Queue `[0]`: take `0`, add its neighbor `4`.
- Queue `[4]`: take `4`, add `7`. City `0` is already visited.
- Queue `[7]`: take `7`, add `2`.
- Queue `[2]`: take `2`. It is the arrival city, so return `true`.


Cities `1, 5, 8, 6` are never touched, because no road leads to them from city `0`.


### Code

```java
import java.util.*;

/**
 * Checks if a traveler can reach one city from another using two-way roads.
 * Idea: build a neighbor list for every city, then explore outward from the
 * departure city with Breadth First Search (BFS).
 */
public class RoadTripPlanner {

    public RoadTripPlanner() {
    }

    public boolean isTripPossible(int cityCount, List<String> roads, int departureCity, int arrivalCity) {
        // Rule: already at the arrival city, no road needed
        if (departureCity == arrivalCity) {
            return true;
        }

        List<List<Integer>> neighbors = buildNeighbors(cityCount, roads);

        // visited[city] becomes true once the city is added to the queue
        boolean[] visited = new boolean[cityCount];
        Queue<Integer> queue = new ArrayDeque<>();

        visited[departureCity] = true;
        queue.add(departureCity);

        while (!queue.isEmpty()) {
            int city = queue.poll();
            if (city == arrivalCity) {
                return true;
            }

            // Only the roads that touch this city, no full scan needed
            for (int nextCity : neighbors.get(city)) {
                if (!visited[nextCity]) {
                    visited[nextCity] = true;
                    queue.add(nextCity);
                }
            }
        }

        // Every reachable city was explored, the arrival city never showed up
        return false;
    }

    /**
     * Converts "a,b" road strings into a neighbor list.
     * neighbors.get(city) holds every city that has a direct road to city.
     */
    List<List<Integer>> buildNeighbors(int cityCount, List<String> roads) {
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int city = 0; city < cityCount; city++) {
            neighbors.add(new ArrayList<>());
        }

        for (String road : roads) {
            String[] parts = road.split(",");
            int firstCity = Integer.parseInt(parts[0].trim());
            int secondCity = Integer.parseInt(parts[1].trim());

            // Two-way road: each city is a neighbor of the other
            neighbors.get(firstCity).add(secondCity);
            neighbors.get(secondCity).add(firstCity);
        }
        return neighbors;
    }
}
```


### Complexity

- **Time:** `O(cityCount + roads.size())`. Every city enters the queue at most once, and every road is checked at most twice, once from each end.
- **Space:** `O(cityCount + roads.size())` for the neighbor list.


## Solution 2: Union-Find (Group the Cities)

Here we stop thinking about paths and think about groups instead.


Two cities are connected only if they belong to the same group of connected cities. So:


1. Start with every city in its own group.
2. For every road, merge the groups of its two cities.
3. Return `true` if the departure and arrival cities end up in the same group.


For Example 2, the roads `0-1, 1-3, 2-4, 4-6` create the groups `{0, 1, 3}`, `{2, 4, 6}` and `{5}`. Cities `3` and `6` are in different groups, so the answer is `false`.


### How are groups stored?

Every group has one **leader** city. `parent[city]` points to another city of the same group, and following these links always ends at the leader. A leader points to itself.


- **`findLeader(city)`**: follows the parent links up to the leader. Two cities are in the same group if they have the same leader.
- **`connect(firstCity, secondCity)`**: finds both leaders. If they are different, one leader starts pointing to the other, and the two groups become one.


Two small tricks keep these links short, so `findLeader` stays fast:


- **Union by size:** `groupSize[leader]` stores how many cities a group has. We always attach the smaller group under the bigger one.
- **Path compression:** after finding a leader, every city we passed on the way is pointed directly to it. Later searches from these cities become much shorter.


Without these tricks, the links could form one long chain, and a single search might walk through all of it.


### Code

```java
import java.util.*;

/**
 * Union-Find solution.
 * Every road merges the groups of its two cities. The trip is possible
 * only if the departure and arrival cities end up in the same group.
 */
public class RoadTripPlanner {

    // parent[city] points towards the leader of the city's group
    int[] parent;
    // groupSize[leader] = number of cities in that leader's group
    int[] groupSize;

    public RoadTripPlanner() {
    }

    public boolean isTripPossible(int cityCount, List<String> roads, int departureCity, int arrivalCity) {
        // Rule: already at the arrival city, no road needed
        if (departureCity == arrivalCity) {
            return true;
        }

        // At the start, every city is its own group and its own leader
        parent = new int[cityCount];
        groupSize = new int[cityCount];
        for (int city = 0; city < cityCount; city++) {
            parent[city] = city;
            groupSize[city] = 1;
        }

        for (String road : roads) {
            String[] parts = road.split(",");
            int firstCity = Integer.parseInt(parts[0].trim());
            int secondCity = Integer.parseInt(parts[1].trim());
            connect(firstCity, secondCity);
        }

        return findLeader(departureCity) == findLeader(arrivalCity);
    }

    /** Returns the leader of the group this city belongs to. */
    int findLeader(int city) {
        // Step 1: follow the parent links up to the leader
        int leader = city;
        while (parent[leader] != leader) {
            leader = parent[leader];
        }

        // Step 2 (path compression): point every city on the way
        // directly to the leader, so later searches are shorter
        while (city != leader) {
            int nextCity = parent[city];
            parent[city] = leader;
            city = nextCity;
        }
        return leader;
    }

    /** Merges the groups of two cities joined by a road. */
    void connect(int firstCity, int secondCity) {
        int firstLeader = findLeader(firstCity);
        int secondLeader = findLeader(secondCity);
        if (firstLeader == secondLeader) {
            return; // already in the same group
        }

        // Union by size: attach the smaller group under the bigger one
        if (groupSize[firstLeader] < groupSize[secondLeader]) {
            int temp = firstLeader;
            firstLeader = secondLeader;
            secondLeader = temp;
        }
        parent[secondLeader] = firstLeader;
        groupSize[firstLeader] += groupSize[secondLeader];
    }
}
```


### Complexity

- **Time:** almost `O(cityCount + roads.size())`. With both tricks, each `findLeader` call takes nearly constant time.
- **Space:** `O(cityCount)` for the two arrays. There is no neighbor list, so it uses less memory than BFS.


## Which One Should You Use?

Both are fast enough for this problem. BFS is the most natural fit when we ask just one question about a fixed map.


Union-Find is worth knowing too. It shines when new roads keep getting added, and we have to answer "are these two cities connected?" again and again.