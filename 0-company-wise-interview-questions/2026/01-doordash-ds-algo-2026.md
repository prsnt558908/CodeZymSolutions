![Doordash Codecraft and DS & Algo Round Interview Questions Asked in 2026](https://d3jug84e56mcss.cloudfront.net/blog-images/0/24-1-doordash-2026-sm-60-80.jpg)


# Doordash Codecraft and DS & Algo Round Interview Questions Asked in 2026

These questions have been taken from Doordash interview experiences shared by candidates in blogs/forums etc. Use this list for final preparation of your DoorDash interviews.

Codecraft round is more of real world api style question. It is similar to a low level design question.

Even for frontend and MLE roles, there will be DSA rounds.

Good thing is that questions are repeated frequently. Their question bank is not that large. This is true for all rounds including codecraft, ds & algo or debugging round.

---
### Complete list of Doordash interview questions:

[https://codezym.com/lld/doordash](https://codezym.com/lld/doordash)

---

Doordash hires software engineer for E3, E4, E5 roles and so on. SDE 2 role is called E4.

The rounds are Phone Screen (DSA), Codecraft, Debugging, System Design, Hiring Manager

- Phone Screen (DSA)
- Codecraft
- Debugging
- System Design
- Hiring Manager

hiring manager round is mosly behavioral questions like:

- Tell me about a time you received feedback;
- Tell me about a bug in production and how you handled it;
- Tell me about a project you're proud of;

Questions like walls and nearest gate for empty rooms, restaurant search suggestions have been asked frequently during phone screen round.

[https://codezym.com/question/270-walls-nearest-gate-empty-rooms](https://codezym.com/question/270-walls-nearest-gate-empty-rooms)

[https://codezym.com/question/107-doordash-restaurant-search-engine](https://codezym.com/question/107-doordash-restaurant-search-engine)



### Debugging Round

There is also a separate debugging round which goes on something like below (No AI allowed for this round):

You have a single main.py file (or equivalent in your chosen programming language) that contains some business logic for a mock Dasher Assignment Service. This service would be used to assign dashers to deliveries.

You need to identify the logic bugs, bad design/coding practices and improve the codebase. Prioritize fixing logic bugs

### Tips for Debugging Rounds:

- AI tools are not allowed.
- Keep fixes simple and reuse existing code.
- Use debug prints in HackerRank, as console output may be truncated.
- Read the business requirements carefully to solve the right problem.
- Explain your thought process clearly so the interviewer can help.

## Below are questions which you can practice on leetcode (free ones):

### Partition Array Such That Maximum Difference Is K

[https://leetcode.com/problems/partition-array-such-that-maximum-difference-is-k/description/](https://leetcode.com/problems/partition-array-such-that-maximum-difference-is-k/description/)

### Check if One String Swap Can Make Strings Equal

[https://leetcode.com/problems/check-if-one-string-swap-can-make-strings-equal/](https://leetcode.com/problems/check-if-one-string-swap-can-make-strings-equal/)

### Binary Tree Maximum Path Sum

[https://leetcode.com/problems/binary-tree-maximum-path-sum/description/](https://leetcode.com/problems/binary-tree-maximum-path-sum/description/)

### Basic Calculator II

[https://leetcode.com/problems/basic-calculator-ii/description/](https://leetcode.com/problems/basic-calculator-ii/description/)

### Next Greater Element III

[https://leetcode.com/problems/next-greater-element-iii/description/](https://leetcode.com/problems/next-greater-element-iii/description/)

### 01 Matrix

[https://leetcode.com/problems/01-matrix/description/](https://leetcode.com/problems/01-matrix/description/)

### Insert Delete GetRandom O(1)

[https://leetcode.com/problems/insert-delete-getrandom-o1/](https://leetcode.com/problems/insert-delete-getrandom-o1/)

### Palindromic Substrings

[https://leetcode.com/problems/palindromic-substrings/description/](https://leetcode.com/problems/palindromic-substrings/description/)

---

## Below are other frequently asked questions including follow ups:



### 1. Codecraft: Design Dasher Payout Service

Design and implement a Dasher payout service as part of a new Payments Service in a micro-service architecture. The service must expose a payout API that calculates how much a Dasher should be paid for a given day.

[https://codezym.com/question/97-design-dasher-payout-service](https://codezym.com/question/97-design-dasher-payout-service)



### 2. Codecraft: Design Workflow Automation Engine for Self Help Menu

Design and implement a workflow automation engine for DoorDash-style order support automation. The system stores users, restaurants, and orders in memory, then reacts to order status updates with deterministic actions and logs.

In Part 1, implement the core workflow behavior: an order starts as OPEN, informational updates keep it open, ORDER_DELIVERED completes it, DELIVERY_CANCELLED cancels it, and closed orders should ignore later non-refund updates.

In Part 2, extend the same system so that different issues produce different compensation amounts. For example, late delivery may cause no refund, a 50% refund, or a 100% refund depending on how late it was, and a customer cancellation before preparation starts should return 95%.

The goal is to model clean order state transitions and return easy-to-test execution logs.

[https://codezym.com/question/98-design-workflow-automation-engine](https://codezym.com/question/98-design-workflow-automation-engine)



### 3. Debugging: Round Robin Traffic Router

Implement a traffic router that distributes incoming requests across backend servers using round-robin selection.

[https://codezym.com/question/272-debug-round-robin-traffic-router](https://codezym.com/question/272-debug-round-robin-traffic-router)



### 4. Debugging: Dasher Assignment Service for Order Delivery

Implement a Dasher Assignment Service for order delivery that maintains a pool of available dashers, assigns dashers to orders, and makes dashers available again after their assigned orders are delivered.

[https://codezym.com/question/274-dasher-assignment-service-order-delivery](https://codezym.com/question/274-dasher-assignment-service-order-delivery)



### 5. Dasher Payout Logic - Simple

As part of migrating away from a monolith to a micro-service architecture your team has been tasked with building out a new Payments Service. Your project is to build out the Dasher payout logic. As part of your work you will get the relevant data in your constructor.

The service should support calculating payout for a Dasher for a given day from the sequence of delivery activities returned by the upstream dependency.

[https://codezym.com/question/103-dasher-payout-logic](https://codezym.com/question/103-dasher-payout-logic)



### 6. Order ID Removal Priority Based on Neighbors

You are given a list of unique integers representing order IDs. Determine their priority by repeatedly removing one order ID according to the values of its current neighbors.

[https://codezym.com/question/273-orderid-removal-priority-neighbors](https://codezym.com/question/273-orderid-removal-priority-neighbors)



### 7. Find Longest Sequence of Pickup Jobs for Dashers

You are given the pickup job lists for two dashers who want to dash together in one car. Each list is an ordered list of merchant names. The dashers must respect the original left-to-right order of pickups in their own lists, but they are allowed to skip some pickup jobs.

Your task is to find the longest sequence of pickups that both dashers can complete together while preserving order in both lists.

[https://codezym.com/question/99-find-longest-sequence-pickup-jobs-dashers](https://codezym.com/question/99-find-longest-sequence-pickup-jobs-dashers)



### 8. Find Closest DashMart Distance and DashMart Serving Maximum Customers

A DashMart is a warehouse run by DoorDash that houses items found in convenience stores, grocery stores, and restaurants. You are given a city plan with open roads, blocked roads, DashMarts, and optionally customers. City planners want you to identify how far a location is from its closest DashMart. You can only travel in four directions: up, down, left, and right. Locations are given in [row, col] format.

In the follow-up version, the city plan may also contain customers. Each customer is assigned to the closest reachable DashMart. You need to find which DashMart serves the maximum number of customers.

[https://codezym.com/question/100-find-closest-dashmart-distance](https://codezym.com/question/100-find-closest-dashmart-distance)



### 9. Maximum Calories within Budget

You are ordering food from DoorDash and selecting items from a menu. You have a fixed budget, and your goal is to get the maximum total calories that can be bought without going over that budget.

You are given:<br>
A list of item prices in dollars.<br>
A list of calorie values for the same items in the same order.<br>
A budget in dollars.<br>
Each menu item may be purchased more than once. You may choose any combination of items as long as the total price does not exceed the budget.

Return the greatest total calories that can be purchased within the budget. If no item can be purchased, return 0.

[https://codezym.com/question/101-maximum-calories-within-budget](https://codezym.com/question/101-maximum-calories-within-budget)



### 10. Aggregate Gift Card Data

Design a system to manage payments using gift cards.<br>
The system should support adding users with gift cards, making payments using one or more gift cards, and returning aggregated gift card data for a user.<br>
A user can use multiple gift cards in a single payment.

[https://codezym.com/question/102-aggregate-gift-card-data](https://codezym.com/question/102-aggregate-gift-card-data)



### 11. Walls and Nearest Gate for Empty Rooms

You are given a rectangular grid containing walls, gates, and empty rooms. Fill every reachable empty room with the minimum number of steps required to reach a gate.

[https://codezym.com/question/270-walls-nearest-gate-empty-rooms](https://codezym.com/question/270-walls-nearest-gate-empty-rooms)



### 12. Design Best Reviews and Monthly Rewards System

Design a review system where viewers can write reviews, rate reviews written by other viewers, and reward the best reviews for each calendar month.

[https://codezym.com/question/271-best-reviews-and-monthly-rewards](https://codezym.com/question/271-best-reviews-and-monthly-rewards)



### 13. Chef Order Preparation List

A chef receives all his orders for the day as a list of unique order ids. He creates a new list by repeatedly removing the smallest eligible order from the current list and appending it to the new list. Return the order in which the chef creates the new list.

[https://codezym.com/question/104-chef-order-preparation-list](https://codezym.com/question/104-chef-order-preparation-list)



### 14. Count and Print Changed Nodes in DoorDash Menu Tree

At DoorDash, menus are updated daily and even hourly to keep them up-to-date. Each menu can be regarded as a tree. When the merchant sends the latest menu, calculate how many nodes have changed, been added, or been deleted.

[https://codezym.com/question/105-count-changed-nodes-doordash-menu-tree](https://codezym.com/question/105-count-changed-nodes-doordash-menu-tree)



### 15. Longest Path and Increasing Paths in a Grid

You are given a row x column integer grid. Find the length of the longest strictly increasing path in the grid.

From any cell, you may move only to its immediate left, right, up, or down neighbor. Diagonal movement is not allowed, and you cannot go outside the grid.

In addition to the maximum length, also return one longest strictly increasing path, and return all strictly increasing paths.

[https://codezym.com/question/106-longest-increasing-path-grid](https://codezym.com/question/106-longest-increasing-path-grid)



### 16. DoorDash Restaurant Search Engine

You are building a restaurant search engine for DoorDash using a trie (prefix tree).

You are given an initial list of restaurant names such as ["panda express", "panera bread"]. Each restaurant name must be stored so that you can:<br>
insert a new restaurant name,<br>
check whether an exact restaurant name exists,<br>
check whether any stored restaurant name starts with a given prefix,<br>
return up to k restaurant names that start with a given prefix.<br>
The search engine stores only unique restaurant names. If the same restaurant name appears multiple times in the constructor input or is inserted multiple times later, it is still stored only once.

If fewer than k restaurant names match the prefix, return all matching names. If no restaurant name matches the prefix, return an empty list.

Returned restaurant names must be sorted in lexicographically ascending order.

[https://codezym.com/question/107-doordash-restaurant-search-engine](https://codezym.com/question/107-doordash-restaurant-search-engine)



### 17. Dasher Coverage of Connected User Areas

You are given two binary grids representing Dasher availability and user locations. Count how many user clusters are fully covered by available Dashers.

[https://codezym.com/question/275-dasher-coverage-connected-user-area](https://codezym.com/question/275-dasher-coverage-connected-user-area)



### 18. Modified LRU Cache for Dasher Delivery Assignments

Implement a fixed-capacity cache that stores the most recent delivery assigned to each dasher. The cache keeps dashers based only on how recently their entries were added or updated.

[https://codezym.com/question/276-modified-lru-cache-dasher-delivery](https://codezym.com/question/276-modified-lru-cache-dasher-delivery)


### 19. Strings That Have Two Letters Swapped

You are given a string name and a list of strings nameList. Return every string from nameList that can be made equal to name by swapping exactly two characters in that string.

[https://codezym.com/question/277-strings-two-letters-swapped](https://codezym.com/question/277-strings-two-letters-swapped)



### 20. Names Requiring Minimum Changes to Become an Anagram

You are given a string name and a list of strings nameList. Return every string from nameList that requires the minimum number of character changes to become an anagram of name.

[https://codezym.com/question/278-names-minimum-changes-anagram](https://codezym.com/question/278-names-minimum-changes-anagram)



## Thanks for reading.


Wish you the best of luck for your interview prep.
