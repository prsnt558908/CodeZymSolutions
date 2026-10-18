# Virus Spread in Directed Graph in Java

#### Problem Statement
[https://codezym.com/question/118-virus-spread-directed-graph](https://codezym.com/question/118-virus-spread-directed-graph)


## Core Idea

This is a graph reachability problem. Each edge `"a,b"` simply says: if `a` gets the virus, `b` gets it too.


So the answer is: start from the infected person, keep following the edges, and collect everyone you can reach.


We will first look at a simple brute force idea that keeps scanning the edge list again and again. Then we will improve it by storing the graph in a map and walking it with BFS (Breadth First Search) using a queue and a visited set.


For the follow-up (many infected ids), the trick is tiny. Instead of putting one person in the queue at the start, we put all of them. The rest of the code stays the same. The single id method just calls the multi id method with a list of one id.


---


## Understanding the Problem

Let us take Example 5.

```
graph = ["1,4", "2,4", "4,7", "7,8"],  ids = [1, 2]
```

```
1 ──┐
    ├──> 4 ──> 7 ──> 8
2 ──┘
```


- `1` infects `4`.
- `2` also infects `4`, but `4` is already infected, so we count it only once.
- `4` infects `7`, and `7` infects `8`.


Answer: `[1, 2, 4, 7, 8]`


Three small rules to remember:

1. The starting people are always part of the answer, even if they have no edges at all.
2. Each person appears only once in the answer.
3. The answer must be sorted in ascending order.


---


## Solution 1: Brute Force (Scan All Edges Again and Again)


### Idea

Keep a set of infected people. Start it with the given ids.


Now go through every edge. If the `from` person is infected and the `to` person is not, infect the `to` person.


One pass is not enough. A person infected late in the pass may have edges that we already skipped. So we repeat the whole pass until a full pass infects nobody new.


### Why a Set?

We need to answer "is this person infected?" very often. A `HashSet` answers that in constant time. It also keeps every person only once, which handles rule 2 for free.


### Code

```java
import java.util.*;

public class VirusSpread {

    public VirusSpread() {
    }

    public List<Integer> infectedPeople(List<String> graph, int id) {
        // single id is just a list with one id
        return infectedPeopleFromIds(graph, Arrays.asList(id));
    }

    public List<Integer> infectedPeopleFromIds(List<String> graph, List<Integer> ids) {
        Set<Integer> infected = new HashSet<>(ids);

        // keep scanning all edges until no new person gets infected
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String edge : graph) {
                String[] parts = edge.split(",");
                int from = Integer.parseInt(parts[0].trim());
                int to = Integer.parseInt(parts[1].trim());
                if (infected.contains(from) && !infected.contains(to)) {
                    infected.add(to);
                    changed = true;
                }
            }
        }

        List<Integer> result = new ArrayList<>(infected);
        Collections.sort(result);
        return result;
    }
}
```


### Problem With This Approach

Look at this input, where the edges come in reverse order:

```
graph = ["3,4", "2,3", "1,2"],  id = 1
```


- Pass 1: only `"1,2"` works. `2` is infected.
- Pass 2: only `"2,3"` works. `3` is infected.
- Pass 3: only `"3,4"` works. `4` is infected.
- Pass 4: nothing changes, so we stop.


Each pass infects only one new person. For a long chain of `10^5` people we need about `10^5` passes, and each pass reads `10^5` edges.


That is around `10^10` steps, which is far too slow.


The real waste is this: in every pass we read edges of people who are not infected yet, and edges we already used before. We only want to look at the edges of a person once, right after that person gets infected.


---


## Solution 2: Map + BFS (Optimal)


### Idea

Fix the waste in two steps.


**Step 1. Store the graph in a map.**

Read the edge list once and build a map:

```
person id  ->  list of people this person can infect directly
```

For Example 5 the map looks like this:

```
1 -> [4]
2 -> [4]
4 -> [7]
7 -> [8]
```

Now, for any person, we can get all of their direct contacts in one lookup, without scanning the whole edge list.


**Step 2. Spread the virus with BFS.**

- Put the starting people in a queue and mark them infected.
- Take one person out of the queue.
- Look up their contacts in the map.
- Every contact who is not infected yet gets infected and goes into the queue.
- Repeat until the queue is empty.


Every person goes into the queue at most once. Every edge is looked at at most once.


### Why These Data Structures?

**`Map<Integer, List<Integer>> contacts`**

This is the adjacency list. Ids can be as large as `10^9`, so an array indexed by id is not possible. A map only stores the ids that actually appear.


**`Set<Integer> infected`**

This is our visited set. It stops us from processing the same person twice. It is also the final answer, we just sort it at the end.


**`Queue<Integer> queue`**

It holds people who have the virus but have not passed it on yet. It is the "to do" list of the BFS.


### Why Do We Need a Visited Set if There Are No Cycles?

No cycles means we will never loop forever. But one person can still be reached through many different paths.


In Example 5, person `4` is reached from both `1` and `2`. In a bigger graph shaped like a ladder, the number of paths to a person can grow very fast. Without the visited set we would process that person once for every path.


The set makes sure each person is handled only one time.


### Why BFS and Not Recursive DFS?

Recursive DFS would also give the correct answer.


But a chain of `10^5` people means `10^5` nested recursive calls. That can crash with a stack overflow in Java. BFS with a queue has no such risk, and the code is just as short.


### The Follow-Up: Many Infected Ids

This is called multi source BFS. It sounds fancy, but it only means: put all starting people in the queue before the loop begins.


The virus then spreads from all of them at the same time. If two starting people can reach the same person, the visited set makes sure that person is added only once.


So the only real code is inside `infectedPeopleFromIds`. The single id method becomes one line of work:

```java
List<Integer> ids = new ArrayList<>();
ids.add(id);
return infectedPeopleFromIds(graph, ids);
```

This is exactly the "minimal change in code" the follow-up asks for.


### Dry Run (Example 4)

```
graph = ["1,2", "2,4", "3,5", "5,6"],  ids = [1, 3]

map:   1 -> [2],  2 -> [4],  3 -> [5],  5 -> [6]
```

| Step | Take out | New infected | Queue after | Infected set |
|------|----------|--------------|-------------|--------------|
| start | - | 1, 3 | [1, 3] | {1, 3} |
| 1 | 1 | 2 | [3, 2] | {1, 2, 3} |
| 2 | 3 | 5 | [2, 5] | {1, 2, 3, 5} |
| 3 | 2 | 4 | [5, 4] | {1, 2, 3, 4, 5} |
| 4 | 5 | 6 | [4, 6] | {1, 2, 3, 4, 5, 6} |
| 5 | 4 | none | [6] | same |
| 6 | 6 | none | [] | same |


Sorted answer: `[1, 2, 3, 4, 5, 6]`


### Complexity

Let `E` be the number of edges and `V` the number of people.

- Building the map: `O(E)`
- BFS: `O(V + E)`, each person and each edge is handled once
- Sorting the answer: `O(V log V)`


**Total time:** `O(E + V log V)`

**Space:** `O(V + E)` for the map, the set and the queue


### Code

```java
import java.util.*;

public class VirusSpread {

    public VirusSpread() {
    }

    /**
     * One infected person is just a list with a single id.
     * So we reuse the multi id method. This is the "minimal change" for the follow-up.
     */
    public List<Integer> infectedPeople(List<String> graph, int id) {
        List<Integer> ids = new ArrayList<>();
        ids.add(id);
        return infectedPeopleFromIds(graph, ids);
    }

    public List<Integer> infectedPeopleFromIds(List<String> graph, List<Integer> ids) {
        Map<Integer, List<Integer>> contacts = buildContacts(graph);

        // everyone who has the virus, also works as the "visited" set
        Set<Integer> infected = new HashSet<>();
        // people who got the virus but have not passed it on yet
        Queue<Integer> queue = new LinkedList<>();

        // all starting people go into the queue together
        for (int id : ids) {
            if (infected.add(id)) {
                queue.add(id);
            }
        }

        while (!queue.isEmpty()) {
            int person = queue.poll();
            List<Integer> next = contacts.get(person);
            if (next == null) {
                continue; // this person has no outgoing edge
            }

            for (int other : next) {
                // add() returns false if this person was already infected
                if (infected.add(other)) {
                    queue.add(other);
                }
            }
        }

        List<Integer> result = new ArrayList<>(infected);
        Collections.sort(result);
        return result;
    }

    /**
     * Converts "fromId,toId" strings into a map:
     * person id -> list of people this person can infect directly.
     */
    Map<Integer, List<Integer>> buildContacts(List<String> graph) {
        Map<Integer, List<Integer>> contacts = new HashMap<>();
        for (String edge : graph) {
            String[] parts = edge.split(",");
            int from = Integer.parseInt(parts[0].trim());
            int to = Integer.parseInt(parts[1].trim());
            if (!contacts.containsKey(from)) {
                contacts.put(from, new ArrayList<>());
            }
            contacts.get(from).add(to);
        }
        return contacts;
    }
}
```


---


## Key Takeaways

- "Who gets infected" is the same as "who is reachable from the start" in a directed graph.
- Build a map from the edge list once, so each person's contacts are one lookup away.
- BFS with a queue and a visited set handles each person and each edge only once.
- Many starting people? Put all of them in the queue at the start. Nothing else changes.
- The visited set doubles as the answer. Just sort it before returning.