# Road Trip Between Cities in Python

#### Problem Statement
[https://codezym.com/question/426-road-trip-between-cities](https://codezym.com/question/426-road-trip-between-cities)


Think of every city as a dot and every road as a line joining two dots. The question then becomes simple: is the arrival city in the same connected piece of the map as the departure city?


To find out, we start at the departure city and keep visiting every city we can reach, one road at a time. If the arrival city shows up, the trip is possible. If we run out of new cities first, it is not.


The key to making this fast is a **neighbor list**. We read all the roads once and note down, for every city, which cities it is directly connected to. After that, we never search through the full road list again.


We will also see a second approach called **Union-Find**. It puts connected cities into the same group, and then just checks if both cities ended up in the same group.


## Brute Force: Scan All Roads for Every City

Let us start with the most direct way to explore the map.


1. Keep a queue of cities we have reached but not explored yet. Put the departure city in it.
2. Take a city out of the queue. If it is the arrival city, return `True`.
3. Otherwise, find every city that has a road to it, and add the new ones to the queue.
4. If the queue becomes empty, every reachable city has been explored. Return `False`.


We also keep a `visited` list. Roads are two-way, so without it we would keep going back and forth between the same two cities forever.


This way of exploring, level by level using a queue, is called **Breadth First Search (BFS)**.


The slow part is step 3. To find the neighbors of one city, we read the whole road list:


```python
visited = [False] * cityCount
queue = deque()
visited[departureCity] = True
queue.append(departureCity)

while queue:
    city = queue.popleft()
    if city == arrivalCity:
        return True

    # Slow: read ALL roads just to find the neighbors of one city
    for road in roads:
        parts = road.split(",")
        firstCity = int(parts[0])
        secondCity = int(parts[1])

        nextCity = -1
        if firstCity == city:
            nextCity = secondCity
        elif secondCity == city:
            nextCity = firstCity

        if nextCity != -1 and not visited[nextCity]:
            visited[nextCity] = True
            queue.append(nextCity)

return False
```


### Why is this slow?

Every city we take out of the queue reads all the roads again. With up to 200,000 reachable cities and 200,000 roads, that is about 200,000 × 200,000 = 40 billion checks.


Most of this work is wasted. A city usually touches only a few roads, but we read all 200,000 of them just to find those few.


## Solution 1: BFS with a Neighbor List

The fix is simple: read the roads only once.


Before exploring, we build a neighbor list where `neighbors[city]` holds every city that has a direct road to `city`. A two-way road `"a,b"` adds `b` to the list of `a`, and `a` to the list of `b`.


Now step 3 is instant. The BFS loop stays the same. Only the scan over all roads is replaced by a loop over `neighbors[city]`.


### Why these data structures?

- **`neighbors` (list of lists)**: gives the direct neighbors of any city in one step. This is where the whole speedup comes from.
- **`visited` (list of booleans)**: cities are numbered `0` to `cityCount - 1`, so a simple list works like a set. `visited[city]` tells us in one step if a city was already seen.
- **`deque` as the queue**: `popleft()` removes the front city instantly. A normal list would be slow here, because `pop(0)` shifts every remaining item by one place.


We use a queue instead of recursion on purpose. Python allows only about 1,000 nested calls by default, so on one long chain of 200,000 cities, recursive code would crash with a `RecursionError`.


### Walkthrough (Example 1)

Roads: `0-4, 4-7, 7-2, 1-5, 5-8, 8-6`. Departure is `0`, arrival is `2`.


- Queue `[0]`: take `0`, add its neighbor `4`.
- Queue `[4]`: take `4`, add `7`. City `0` is already visited.
- Queue `[7]`: take `7`, add `2`.
- Queue `[2]`: take `2`. It is the arrival city, so return `True`.


Cities `1, 5, 8, 6` are never touched, because no road leads to them from city `0`.


### Code

```python
from collections import deque
from typing import List


class RoadTripPlanner:
    """
    Checks if a traveler can reach one city from another using two-way roads.
    Idea: build a neighbor list for every city, then explore outward from
    the departure city with Breadth First Search (BFS).
    """

    def __init__(self):
        pass

    def isTripPossible(self, cityCount: int, roads: List[str],
                       departureCity: int, arrivalCity: int) -> bool:
        # Rule: already at the arrival city, no road needed
        if departureCity == arrivalCity:
            return True

        neighbors = self.buildNeighbors(cityCount, roads)

        # visited[city] becomes True once the city is added to the queue
        visited = [False] * cityCount
        queue = deque()

        visited[departureCity] = True
        queue.append(departureCity)

        while queue:
            city = queue.popleft()
            if city == arrivalCity:
                return True

            # Only the roads that touch this city, no full scan needed
            for nextCity in neighbors[city]:
                if not visited[nextCity]:
                    visited[nextCity] = True
                    queue.append(nextCity)

        # Every reachable city was explored, the arrival city never showed up
        return False

    def buildNeighbors(self, cityCount: int, roads: List[str]) -> List[List[int]]:
        """
        Converts "a,b" road strings into a neighbor list.
        neighbors[city] holds every city that has a direct road to city.
        """
        neighbors = [[] for _ in range(cityCount)]

        for road in roads:
            parts = road.split(",")
            firstCity = int(parts[0])
            secondCity = int(parts[1])

            # Two-way road: each city is a neighbor of the other
            neighbors[firstCity].append(secondCity)
            neighbors[secondCity].append(firstCity)

        return neighbors
```


### Complexity

- **Time:** `O(cityCount + len(roads))`. Every city enters the queue at most once, and every road is checked at most twice, once from each end.
- **Space:** `O(cityCount + len(roads))` for the neighbor list.


## Solution 2: Union-Find (Group the Cities)

Here we stop thinking about paths and think about groups instead.


Two cities are connected only if they belong to the same group of connected cities. So:


1. Start with every city in its own group.
2. For every road, merge the groups of its two cities.
3. Return `True` if the departure and arrival cities end up in the same group.


For Example 2, the roads `0-1, 1-3, 2-4, 4-6` create the groups `{0, 1, 3}`, `{2, 4, 6}` and `{5}`. Cities `3` and `6` are in different groups, so the answer is `False`.


### How are groups stored?

Every group has one **leader** city. `parent[city]` points to another city of the same group, and following these links always ends at the leader. A leader points to itself.


- **`findLeader(city)`**: follows the parent links up to the leader. Two cities are in the same group if they have the same leader.
- **`connect(firstCity, secondCity)`**: finds both leaders. If they are different, one leader starts pointing to the other, and the two groups become one.


Two small tricks keep these links short, so `findLeader` stays fast:


- **Union by size:** `groupSize[leader]` stores how many cities a group has. We always attach the smaller group under the bigger one.
- **Path compression:** after finding a leader, every city we passed on the way is pointed directly to it. Later searches from these cities become much shorter.


Without these tricks, the links could form one long chain, and a single search might walk through all of it.


### Code

```python
from typing import List


class RoadTripPlanner:
    """
    Union-Find solution.
    Every road merges the groups of its two cities. The trip is possible
    only if the departure and arrival cities end up in the same group.
    """

    def __init__(self):
        # parent[city] points towards the leader of the city's group
        self.parent = []
        # groupSize[leader] = number of cities in that leader's group
        self.groupSize = []

    def isTripPossible(self, cityCount: int, roads: List[str],
                       departureCity: int, arrivalCity: int) -> bool:
        # Rule: already at the arrival city, no road needed
        if departureCity == arrivalCity:
            return True

        # At the start, every city is its own group and its own leader
        self.parent = list(range(cityCount))
        self.groupSize = [1] * cityCount

        for road in roads:
            parts = road.split(",")
            firstCity = int(parts[0])
            secondCity = int(parts[1])
            self.connect(firstCity, secondCity)

        return self.findLeader(departureCity) == self.findLeader(arrivalCity)

    def findLeader(self, city: int) -> int:
        """Returns the leader of the group this city belongs to."""
        # Step 1: follow the parent links up to the leader
        leader = city
        while self.parent[leader] != leader:
            leader = self.parent[leader]

        # Step 2 (path compression): point every city on the way
        # directly to the leader, so later searches are shorter
        while city != leader:
            nextCity = self.parent[city]
            self.parent[city] = leader
            city = nextCity

        return leader

    def connect(self, firstCity: int, secondCity: int) -> None:
        """Merges the groups of two cities joined by a road."""
        firstLeader = self.findLeader(firstCity)
        secondLeader = self.findLeader(secondCity)
        if firstLeader == secondLeader:
            return  # already in the same group

        # Union by size: attach the smaller group under the bigger one
        if self.groupSize[firstLeader] < self.groupSize[secondLeader]:
            firstLeader, secondLeader = secondLeader, firstLeader

        self.parent[secondLeader] = firstLeader
        self.groupSize[firstLeader] += self.groupSize[secondLeader]
```


### Complexity

- **Time:** almost `O(cityCount + len(roads))`. With both tricks, each `findLeader` call takes nearly constant time.
- **Space:** `O(cityCount)` for the two lists. There is no neighbor list, so it uses less memory than BFS.


## Which One Should You Use?

Both are fast enough for this problem. BFS is the most natural fit when we ask just one question about a fixed map.


Union-Find is worth knowing too. It shines when new roads keep getting added, and we have to answer "are these two cities connected?" again and again.