# Recent Amazon DS & Algo Round Interview Questions in Nov 2026

This list contains recently asked DSA questions in Amazon for SDE, SDET positions. It also includes questions from Bar Raiser rounds. Below are questions with follow-ups asked during interviews.

### 1. Rotting Oranges With Different Connection Times

Rot spreads from rotten oranges to adjacent fresh ones, but each connection between two cells takes its own number of minutes. Return the minimum time for every fresh orange to rot, or -1 if some orange can never rot.

https://codezym.com/question/252-rotting-oranges-weighted

### 2. Can All Factory Machines Start

Some machines can start only after certain other machines are already running. Return true if every machine can be started, or false if machines wait on one another in a circular chain.

https://codezym.com/question/422-can-all-factory-machines-start

### 3. Valid Course Order with Prerequisites

Some courses must be completed before others can be taken. Return an order that completes every course, picking the smaller course number first when several are available, or an empty list if the prerequisites form a cycle.

https://codezym.com/question/336-valid-course-order

### 4. Binary Tree Diagonal Traversal

Return a binary tree's values diagonal by diagonal, where a right child stays on its parent's diagonal and a left child moves to the next one. Inside each diagonal, nodes appear in preorder.

https://codezym.com/question/489-binary-tree-diagonal-traversal

### 5. Maximum Bitwise AND of Chosen Capacities After Increases

Increase any capacities, using a total increase of at most budget, then choose exactly deploymentSize of them. Return the largest possible bitwise AND of the chosen capacities.

https://codezym.com/question/490-max-bitwise-and-after-increases

### 6. Minimum Starting Charge for a Warehouse Robot

A robot moves only right or down from the top-left to the bottom-right workstation, and each workstation adds or uses up battery. Find the smallest starting charge that keeps the battery at 1 or more on at least one route.

https://codezym.com/question/475-robot-min-starting-charge

### 7. Maximum Number of Combo Meals From Menu Sections

Split totalDishes dishes into at least two non-empty menu sections, where a combo meal picks one dish from each section. Return the most combo meals possible, which is the largest product of the section sizes.

https://codezym.com/question/491-max-combo-meals

### 8. Circular Buffer With Three Task Priority Levels

Design a fixed-size circular buffer where tasks of priority 1, 2 and 3 share one array, and any freed slot can be reused. Pop removes the earliest pushed task of the highest priority present, and both operations run in O(1).

https://codezym.com/question/493-priority-circular-buffer

### 9. Shortest Word Tile Group

Find the shortest continuous group of letter tiles whose letters can be rearranged to form the target word, repeated letters included. Return an empty string if none exists, or the leftmost group on a tie.

https://codezym.com/question/417-min-piano-hand-lifts

### 10. Adjacent Characters Are Not the Same

Rearrange a lowercase string so that no two adjacent characters are the same. Return the lexicographically smallest such arrangement, or an empty string if it can't be done.

https://codezym.com/question/361-different-adjacent-characters

### 11. Warehouse Repair Areas

Damaged tiles on a warehouse floor form one repair area when they share a side, but tiles touching only at a corner are not connected. Count the separate repair areas.

https://codezym.com/question/400-warehouse-repair-areas

### 12. Count Number of Distinct Islands in Grid

Count the distinct island shapes in a grid of land and water, where land cells connect up, down, left and right. Two islands match only if one can be shifted onto the other, without rotating or reflecting it.

https://codezym.com/question/190-count-distinct-islands-grid

### 13. Unique Grid Paths with Right and Down Moves

A robot moves only right or down from the top-left cell to the bottom-right cell of an m x n grid. Return the number of different paths it can take.

https://codezym.com/question/316-unique-grid-paths

### 14. Check Whether URLs Are Linked Through Referrer Logs

Every log entry with a referrer creates a one-way link from the referrer page to the visited url. Check whether URL b can be reached from URL a by following one or more of these links.

https://codezym.com/question/495-linked-urls-referrer-logs

### 15. First Non Repeated Character in a Stream

A very large string arrives in chunks, in order, and is too big to keep in memory. At any time, return the first character that appears exactly once in the whole string so far, or an empty string if none does.

https://codezym.com/question/122-first-non-repeated-character-stream

### 16. Check If Target URL Is Reachable by Following Links

Links between web pages can be followed in one direction only, from fromUrl to toUrl. Check whether targetUrl can be reached from startUrl by following any number of links, even when links repeat or form cycles.

https://codezym.com/question/502-target-url-reachable

### 17. Shortest Walk Through a Snowy Park With Shoveling

Find the fewest steps from the top-left gate to the bottom-right cocoa stand in a snowy park grid. Part 1 allows no shoveling, Part 2 allows up to shovelLimit snowy squares, and Part 3 finds the fewest squares to shovel so the walk is as short as with no snow.

https://codezym.com/question/505-snowy-park-shortest-walk

### 18. First One Time Visitor in Stream of Customer Visits

Record a stream of customer visits by customer id. At any time, return the earliest customer who has visited exactly once so far, or -1 if there is none.

https://codezym.com/question/226-one-time-visitor-in-customer-visits-stream

### 19. Select Exam Marks Closest to a Target

From marks sorted lowest to highest, pick exactly markCount marks closest to targetMark and return them in non-decreasing order. When two marks are equally far, the lower mark counts as closer.

https://codezym.com/question/430-exam-marks-closest-to-target

### 20. Train Route Fare Calculator

Add stops to one train route in travel order, where each segment between neighboring stops costs 1 in either direction. A weekday fare is the number of segments, and a weekend fare is capped at weekendFareCap.

https://codezym.com/question/431-train-route-fare-calculator

### 21. Track Recent Temperature Peaks

Record temperature readings one at a time and report the highest among the latest recentReadingLimit readings. Recording takes O(1) amortized time and the query takes O(1).

https://codezym.com/question/432-recent-temperature-peaks

### 22. Improve Fire Station Coverage

A fire truck moves one cell in any direction, including diagonally, each minute. Build at most one new fire station so that the farthest neighborhood is as close as possible to its nearest station, and return that time.

https://codezym.com/question/433-improve-fire-station-coverage

### 23. Stack with Constant-Time Middle Access

Design a stack with push, pop, getTop and getMiddle, where every operation runs in O(1) time. With an even number of elements, the middle is the one closer to the top.

https://codezym.com/question/434-stack-with-constant-time-middle-access

### 24. Basic Calculator for Addition and Subtraction

Build an object-oriented calculator that evaluates a valid expression with non-negative integers, +, -, parentheses and spaces, without using eval. A minus sign can also negate, at the start of the expression or right after an opening parenthesis.

https://codezym.com/question/435-calculator-for-addition-subtraction

### 25. Group Students with Same Marks

Students stand in a fixed row, and one operation changes the marks of every student with one value to another value. Find the fewest records to change so that equal marks always stand together in one continuous group.

https://codezym.com/question/436-group-students-with-same-marks

### 26. Minimum Meeting Rooms Required

Given meetings as "start,end" strings, find the minimum number of rooms needed so that overlapping meetings never share a room. Meetings are half-open, so one ending at 3 doesn't overlap one starting at 3.

https://codezym.com/question/179-minimum-meeting-rooms-required

### 27. Search Minimum And Rotation Count In Rotated Sorted Array

For a rotated sorted list that may contain duplicates, find a target's smallest index and the smallest value. Also count the rotations, which is the index where the minimum value first appears.

https://codezym.com/question/170-rotation-count-rotated-sorted-array

### 28. Find Unoccupied Seat Position With Maximum Distance

In a row of occupied ("O") and unoccupied ("U") seats, find the empty seat farthest from its nearest occupied seat. On a tie, return the smallest position, using O(n) time and O(1) extra space.

https://codezym.com/question/438-unoccupied-seat-with-max-distance

### 29. Validate Chessboard Colours

A square board is painted with colours 0 and 1, given as comma-separated rows. Return true if every pair of cells that share a side have different colours.

https://codezym.com/question/439-validate-chessboard-colours

### 30. Longest Shared Address Sequence

Find the length of the longest sequence of characters shared by two addresses in the same order. Characters may be skipped, so the kept ones don't need to be next to each other.

https://codezym.com/question/440-longest-shared-address-sequence

### 31. Minimum Road Repairs Between Cities

A repair truck goes from the top-left to the bottom-right section of a road map, moving up, down, left or right. Clear sections are free and each damaged section needs one repair, so find the fewest repairs needed.

https://codezym.com/question/441-min-road-repairs

### 32. Maximum Score from Non-Adjacent Tiles

Collect the highest total score from tiles when two neighboring tiles can't both be selected. Solve it for tiles in a straight line and for tiles in a circle, where the first and last tiles are neighbors.

https://codezym.com/question/372-max-score-from-non-adjacent-tiles

### 33. Currency Conversion Queries

Given exact rates like "USD,CAD,2.0", find the conversion rate between two currencies, possibly through other currencies and using rates in both directions. Return -1.0 if they aren't connected, and answer many queries as a follow-up.

https://codezym.com/question/443-currency-conversion-queries

### 34. Restore Astronaut Transmission

Expand a compressed transmission where count[section] means the section repeated count times. Sections can be nested, so "2[m2[np]]" becomes "mnpnpmnpnp".

https://codezym.com/question/420-restore-astronaut-transmission

### 35. Value at Index After Binary String Transformation

In one transformation, every '0' becomes "00" and every '1' becomes "10", all at the same time. Return the bit at a given index after applying it exactly n times.

https://codezym.com/question/444-value-at-index-after-transformation

### 36. Circular Delivery Route

A rider starts in zone 1 of a circular road and visits delivery stops in the given order, going either way around. Leaving each zone costs that zone's travel time, so find the minimum total travel time.

https://codezym.com/question/445-circular-delivery-route

### 37. Valid Binary Tree Subsets

Count the subsets of nodes in a binary tree where no two selected nodes are directly connected by an edge. The empty subset counts, and the answer is returned modulo 1,000,000,007.

https://codezym.com/question/446-valid-binary-tree-subsets

### 38. Maximum Empty Charging Bays Between Vehicles

Vehicles must take charging bays with matching connector types, in arrival order, along a one-way lane. Find the largest possible number of empty bays between two consecutive vehicles in any valid assignment.

https://codezym.com/question/447-empty-charging-bays

### 39. Total Continuous Subarray Sum Equals K

Count the continuous subarrays whose elements add up to exactly k. The list can contain positive numbers, negative numbers and zeroes.

https://codezym.com/question/312-continuous-subarray-sum-equals-k

### 40. Minimum Meals to Eat All Food Items

Eat every food item exactly once, where some items can be eaten only after others were eaten in earlier meals. With at most maxItemsPerMeal items per meal, find the minimum number of meals.

https://codezym.com/question/449-minimum-meals-to-eat-all-food-items

### 41. Video Game Turn Slots

Children take one-slot turns on a game machine, and a child must wait waitingSlots full slots before playing again. Find the fewest slots, unused ones included, to finish every requested turn, with names compared case-insensitively.

https://codezym.com/question/450-video-game-turn-slots

### 42. Sorted Fence Heights

Make fence heights non-decreasing by raising consecutive sections, where adding x to a whole group costs only x. Return the minimum total cost.

https://codezym.com/question/363-sorted-fence-heights

### 43. Task Execution Order

Tasks are given as "taskId,runAt,priority", and a task with an earlier runAt runs first. On the same runAt, higher priority goes first, then the smaller taskId, so return the ids in execution order.

https://codezym.com/question/451-task-execution-order

### 44. Longest Net Transaction Sequence

Transactions are positive for money received and negative for money sent. Return the length of the longest run of consecutive transactions whose net amount is exactly targetNetAmount, or 0 if there is none.

https://codezym.com/question/452-longest-net-transaction-sequence

### 45. Company Ownership Percentage

Companies own percentages of other companies, like "atlas,beacon,40". Find the total share of targetCompany held by ownerCompany by multiplying percentages along each path and adding up all direct and indirect paths.

https://codezym.com/question/453-company-ownership-percentage

### 46. Longest Sequence of Consecutive Integers

Return the length of the longest run of consecutive integer values in an unsorted list, in O(n) time. The values don't need to be next to each other in the list, and duplicates don't add to the length.

https://codezym.com/question/293-longest-sequence-of-consecutive-integers

### 47. Maximum Usable Cable Length

Cut chosen cable rolls, using each chosen roll fully, into exactly segmentCount segments of positive length. The longest half of the segments fail inspection, so maximize the total length of the half that passes.

https://codezym.com/question/366-max-usable-cable-length

### 48. Repeated Student Roll Number

Each repair section covers every road marker from its start through its end. Combine sections that overlap or touch, like "4,9" and "9,14", and return the combined zones sorted by their starting markers.

https://codezym.com/question/398-repeated-student-roll-number

### 49. Max Words Visible on Scrollable Screen

Words are written in order on a screen with fixed rows and columns, never split across rows, and the screen scrolls up when it runs out of rows. Return the most complete words visible on the screen at any time.

https://codezym.com/question/164-max-words-visible-scrollable-screen

### 50. Assign Aggressive Cows To Stalls

Place cowCount cows in distinct stalls on a straight line so the closest pair of cows is as far apart as possible. Return that largest possible minimum distance.

https://codezym.com/question/166-assign-aggressive-cows-stalls