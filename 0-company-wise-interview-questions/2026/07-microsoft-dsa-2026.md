# Microsoft DS & Algo Round Interview Questions Asked in 2026

## Interview Process

Microsoft frequently opens and closes requisitions to manage referral batches.

The AA (As Appropriate) round can include behavioral and High Level Design questions, along with architectural deep dives into your previous projects. In some cases, a DSA question may also be asked.

LRU Cache can be asked in either a DSA or a Low Level Design round.

## Thread and Architecture Discussion

Interviewers may discuss the following topics:

- What is the difference between a process and a thread?
- If multiple threads access the same variable without writing to it, can any problem occur?
- What issues arise when multiple threads access a shared variable?
- What is the difference between a variable created on the heap and a variable created inside a function?
- If every thread has its own function-local variables, how can conflicts arise when multiple threads execute the same function?
- What exactly causes race conditions in multithreaded programs?
- What is Microservices Architecture?
- How is Microservices Architecture different from a monolithic architecture?
- If services run on different virtual machines, how do they communicate?
- How does gRPC help services running on different VMs communicate?
- Why might gRPC be chosen over REST for service-to-service communication?
- What communication mechanisms can be used between services apart from gRPC?
- What is Event-Driven Architecture?
- What does an event mean in Event-Driven Architecture?
- How does Event-Driven Architecture work in a microservices system?
- How is Event-Driven Architecture different from synchronous communication such as REST or gRPC?
- What are the benefits and trade-offs of Event-Driven Architecture?

---

## Complete Microsoft DSA Questions List

[https://codezym.com/lld/microsoft-dsa](https://codezym.com/lld/microsoft-dsa)

## Microsoft Low Level Design Questions

[https://codezym.com/lld/microsoft](https://codezym.com/lld/microsoft)

---

## LeetCode Questions

### LRU Cache

LRU Cache may be asked in either a DSA or an LLD round.

[https://leetcode.com/problems/lru-cache/description/](https://leetcode.com/problems/lru-cache/description/)

### Reverse Nodes in k-Group

[https://leetcode.com/problems/reverse-nodes-in-k-group/description/](https://leetcode.com/problems/reverse-nodes-in-k-group/description/)

### Longest Substring Without Repeating Characters

[https://leetcode.com/problems/longest-substring-without-repeating-characters/](https://leetcode.com/problems/longest-substring-without-repeating-characters/)

### Find Median from Data Stream

[https://leetcode.com/problems/find-median-from-data-stream/description/](https://leetcode.com/problems/find-median-from-data-stream/description/)

### Min Stack

[https://leetcode.com/problems/min-stack/description/](https://leetcode.com/problems/min-stack/description/)

---

## Below is the list of questions

### 1. Load Balancing Requests to Available Servers

Route incoming requests to available servers while respecting when each server is busy or free.

**Practice Link:** [https://codezym.com/question/321-load-balancing-server-requests](https://codezym.com/question/321-load-balancing-server-requests)

### 2. Climbers For Mountain Expedition

Determine how climbers can be selected or organized for an expedition while satisfying the given constraints.

**Practice Link:** [https://codezym.com/question/322-climbers-for-mountain-expedition](https://codezym.com/question/322-climbers-for-mountain-expedition)

### 3. Cheapest Journey Between Cities

Find the minimum-cost route between cities using the available connections and travel costs.

**Practice Link:** [https://codezym.com/question/323-cheapest-journey-between-cities](https://codezym.com/question/323-cheapest-journey-between-cities)

### 4. Design LRU Cache With Time Constraint

Implement an LRU cache whose entries expire after a fixed time while preserving recency-based eviction.

**Practice Link:** [https://codezym.com/question/165-design-lru-cache-time-constraint](https://codezym.com/question/165-design-lru-cache-time-constraint)

### 5. Longest Safe Path in a String

Find the longest valid path or segment in a string while avoiding characters or transitions marked unsafe.

**Practice Link:** [https://codezym.com/question/324-longest-safe-path-in-string](https://codezym.com/question/324-longest-safe-path-in-string)

### 6. Minimum Time at the Office After Skipping Meetings

Minimize time spent at the office by choosing which meetings may be skipped under the given rules.

**Practice Link:** [https://codezym.com/question/325-min-time-at-office](https://codezym.com/question/325-min-time-at-office)

### 7. Search Matching Lines in a Document

Search a document and return the lines that satisfy the requested words or matching criteria.

**Practice Link:** [https://codezym.com/question/327-search-matching-lines-in-document](https://codezym.com/question/327-search-matching-lines-in-document)

### 8. Design N-Stack Using a Fixed-Size Array

Support multiple independent stacks efficiently inside one fixed-size array.

**Practice Link:** [https://codezym.com/question/328-n-stack-using-fixed-array](https://codezym.com/question/328-n-stack-using-fixed-array)

### 9. Next Bigger Number with Same Digits

Rearrange the same digits to produce the smallest number that is strictly greater than the original.

**Practice Link:** [https://codezym.com/question/329-next-bigger-number-same-digits](https://codezym.com/question/329-next-bigger-number-same-digits)

### 10. Floating-Point Number to String

Convert a floating-point value into its required string representation while handling formatting edge cases.

**Practice Link:** [https://codezym.com/question/330-floating-point-number-to-string](https://codezym.com/question/330-floating-point-number-to-string)

### 11. Count Connected Groups in Undirected Graph

Count the connected components formed by the nodes and edges of an undirected graph.

**Practice Link:** [https://codezym.com/question/184-count-connected-groups-undirected-graph](https://codezym.com/question/184-count-connected-groups-undirected-graph)

### 12. Trie-Based Dynamic Multilingual Dictionary with Prefix Queries

Build a multilingual trie that supports dynamic updates and efficient prefix-based word queries.

**Practice Link:** [https://codezym.com/question/331-trie-dictionary-with-prefix-queries](https://codezym.com/question/331-trie-dictionary-with-prefix-queries)

### 13. Minimum Bracket Reversals for a Balanced String

Find the fewest bracket reversals needed to make the entire string balanced.

**Practice Link:** [https://codezym.com/question/332-min-bracket-reversals-for-balanced-string](https://codezym.com/question/332-min-bracket-reversals-for-balanced-string)

### 14. Minimum Heater Radius for All Houses

Compute the smallest common heater radius that provides coverage to every house.

**Practice Link:** [https://codezym.com/question/333-min-heater-radius-for-all-houses](https://codezym.com/question/333-min-heater-radius-for-all-houses)

### 15. Add Two Large Numbers Represented as Strings

Add two arbitrarily large non-negative integers supplied as strings.

**Practice Link:** [https://codezym.com/question/334-add-two-large-numbers](https://codezym.com/question/334-add-two-large-numbers)

### 16. Design Stack With Peek And Pop Maximum Element

Design a stack with normal operations plus efficient access to and removal of its current maximum element.

**Practice Link:** [https://codezym.com/question/186-design-stack-peek-pop-maximum-element](https://codezym.com/question/186-design-stack-peek-pop-maximum-element)

### 17. Compile Packages with Dependencies in a Multi-Threaded Environment

Compile dependency-linked packages in a valid order while running independent work concurrently.

**Practice Link:** [https://codezym.com/question/145-compile-packages-dependencies](https://codezym.com/question/145-compile-packages-dependencies)

### 18. Valid Course Order with Prerequisites

Return an order that satisfies every course prerequisite, or report when no valid order exists.

**Practice Link:** [https://codezym.com/question/336-valid-course-order](https://codezym.com/question/336-valid-course-order)

### 19. Top K Frequent Error Codes

Return the K error codes that occur most often while applying the required tie-breaking order.

**Practice Link:** [https://codezym.com/question/337-top-k-frequent-error-codes](https://codezym.com/question/337-top-k-frequent-error-codes)

### 20. Maximum Added Edges in an Network With Special Employees

Add as many network edges as possible without connecting special employees who must remain separated.

**Practice Link:** [https://codezym.com/question/339-max-added-edges-with-special-employees](https://codezym.com/question/339-max-added-edges-with-special-employees)

### 21. Minimum Meeting Rooms Required

Determine the minimum number of rooms needed to host all overlapping meetings.

**Practice Link:** [https://codezym.com/question/179-minimum-meeting-rooms-required](https://codezym.com/question/179-minimum-meeting-rooms-required)

### 22. Delete Numbers to Earn Maximum Points

Maximize earned points when choosing one value forces deletion of its neighboring values.

**Practice Link:** [https://codezym.com/question/341-delete-numbers-earn-max-points](https://codezym.com/question/341-delete-numbers-earn-max-points)

### 23. Check If Undirected Graph Is Bipartite

Check whether graph nodes can be split into two groups so every edge crosses between the groups.

**Practice Link:** [https://codezym.com/question/342-check-undirected-graph-bipartite](https://codezym.com/question/342-check-undirected-graph-bipartite)

### 24. Find All Pairs With Minimum Absolute Difference

Return every pair of values whose absolute difference is the smallest found in the list.

**Practice Link:** [https://codezym.com/question/343-pairs-with-min-absolute-difference](https://codezym.com/question/343-pairs-with-min-absolute-difference)

### 25. Reconstruct Corrupted Master Page

Recover the correct page sequence from the stored status and next-page metadata.

**Practice Link:** [https://codezym.com/question/344-reconstruct-corrupted-master-page](https://codezym.com/question/344-reconstruct-corrupted-master-page)

### 26. Create Maximum Number of Alloys Within Budget

Maximize the alloys one machine can produce using available stock without exceeding the budget.

**Practice Link:** [https://codezym.com/question/345-max-alloys-within-budget](https://codezym.com/question/345-max-alloys-within-budget)

### 27. Find the Shortest Cycle in an Undirected Graph

Return the length of the shortest cycle in an undirected graph, or indicate that no cycle exists.

**Practice Link:** [https://codezym.com/question/346-shortest-cycle-undirected-graph](https://codezym.com/question/346-shortest-cycle-undirected-graph)

### 28. Count Good Sublists Containing N Different Integers

Count contiguous sublists that contain exactly N distinct integer values.

**Practice Link:** [https://codezym.com/question/347-count-good-sublists](https://codezym.com/question/347-count-good-sublists)

### 29. Total Price Fluctuation

Sum the difference between maximum and minimum prices across every contiguous sublist.

**Practice Link:** [https://codezym.com/question/348-total-price-fluctuation](https://codezym.com/question/348-total-price-fluctuation)

### 30. Count Number of Perfect Pairs

Count pairs satisfying the required relationships between their absolute sums, differences and magnitudes.

**Practice Link:** [https://codezym.com/question/349-count-perfect-pairs](https://codezym.com/question/349-count-perfect-pairs)

### 31. Schedule Jobs with Minimum Difficulty

Split ordered jobs across the required days while minimizing the sum of daily difficulties.

**Practice Link:** [https://codezym.com/question/350-min-difficulty-job-schedule](https://codezym.com/question/350-min-difficulty-job-schedule)

### 32. Minimum Journey Cost To Reach Destination Within Time

Find the least expensive route that reaches the destination within the allowed travel time.

**Practice Link:** [https://codezym.com/question/351-min-journey-cost](https://codezym.com/question/351-min-journey-cost)

### 33. Minimum Operations to Remove All Nodes

Minimize subtree-decrement operations required to remove every valued node from the tree.

**Practice Link:** [https://codezym.com/question/352-min-operations-remove-all-nodes](https://codezym.com/question/352-min-operations-remove-all-nodes)

### 34. Minimum Area of an Axis-Aligned Rectangle

Find the smallest axis-aligned rectangle whose four corners appear among the given points.

**Practice Link:** [https://codezym.com/question/353-min-area-axis-aligned-rectangle](https://codezym.com/question/353-min-area-axis-aligned-rectangle)

### 35. Largest Rectangle Area Using Consecutive Histogram Bars

Compute the largest rectangle that can be formed using consecutive bars of a histogram.

**Practice Link:** [https://codezym.com/question/354-largest-rectangle-area](https://codezym.com/question/354-largest-rectangle-area)

### 36. Remove Duplicates from a Sorted List

Create a new sorted list that keeps only the permitted number of copies of each distinct value.

**Practice Link:** [https://codezym.com/question/355-remove-duplicates-from-sorted-list](https://codezym.com/question/355-remove-duplicates-from-sorted-list)

---

## Thanks for reading.

Wish you the best of luck with your interview preparation.
