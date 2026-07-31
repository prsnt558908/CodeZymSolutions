![Goldman Sachs Interview Questions for CoderPad, SuperDay Coding and LLD Rounds](https://d3jug84e56mcss.cloudfront.net/blog-images/0/16-1-goldman-sachs-2026-sm-60-80.jpg)

# Goldman Sachs Interview Questions for CoderPad, SuperDay Coding and LLD Rounds

## Interview Process

Goldman Sachs have an OA round followed by CoderPad round. CoderPad is sort of like phone screen / virtual face-to-face round with interviewer. After that you get selected for onsite rounds.

They call their onsite hiring events Superday. They hire for software engineer associate analyst role. Associate role is similar to SDE 2 in other companies and requires around 3 years of experience.

I have built this questions list from recent interview experiences of candidates.

## CoderPad, DSA and LLD Focus

CoderPad has mostly questions around DSA. If you have 1+ YOE then they can go for LLD. But everything is communicated by HR very clearly. Working code is expected for low level design rounds.

If you are appearing for Java developer role then there is high chance of getting a low level design round. Spring Boot will also be asked.

Questions like Design an e-commerce site will cover both LLD + HLD. For this question you can use Strategy Design Pattern to handle different types of payment solutions like NetBanking, Credit Card, Debit Card, UPI, PayPal etc.

---


## All Questions List

[https://codezym.com/lld/goldmansachs](https://codezym.com/lld/goldmansachs)


---

## LeetCode Questions (Free Ones)

GS frequently asks repetitive questions. Hence practicing previously asked questions is helpful. Below are some questions that have been asked directly from LeetCode.

### Unique Paths

[https://leetcode.com/problems/unique-paths/description/](https://leetcode.com/problems/unique-paths/description/)

### LRU Cache

[https://leetcode.com/problems/lru-cache/](https://leetcode.com/problems/lru-cache/)

### Number of Islands

[https://leetcode.com/problems/number-of-islands/description/](https://leetcode.com/problems/number-of-islands/description/)

### Longest Substring Without Repeating Characters

[https://leetcode.com/problems/longest-substring-without-repeating-characters/description/](https://leetcode.com/problems/longest-substring-without-repeating-characters/description/)

### Single Element in a Sorted Array

[https://leetcode.com/problems/single-element-in-a-sorted-array/description/](https://leetcode.com/problems/single-element-in-a-sorted-array/description/)

### Longest Substring with At Most K Distinct Characters

[https://leetcode.com/problems/longest-substring-with-at-most-k-distinct-characters/description/](https://leetcode.com/problems/longest-substring-with-at-most-k-distinct-characters/description/)

### Minimum Path Sum

[https://leetcode.com/problems/minimum-path-sum/description/](https://leetcode.com/problems/minimum-path-sum/description/)

### Max Area of Island

[https://leetcode.com/problems/max-area-of-island/description/](https://leetcode.com/problems/max-area-of-island/description/)

### First Unique Character in a String

[https://leetcode.com/problems/first-unique-character-in-a-string/description/](https://leetcode.com/problems/first-unique-character-in-a-string/description/)

### Array Nesting

[https://leetcode.com/problems/array-nesting/description/](https://leetcode.com/problems/array-nesting/description/)

### Koko Eating Bananas

[https://leetcode.com/problems/koko-eating-bananas/](https://leetcode.com/problems/koko-eating-bananas/)

### Coin Change

[https://leetcode.com/problems/coin-change/description/](https://leetcode.com/problems/coin-change/description/)

### Container With Most Water

[https://leetcode.com/problems/container-with-most-water/description/](https://leetcode.com/problems/container-with-most-water/description/)

### Word Break

[https://leetcode.com/problems/word-break](https://leetcode.com/problems/word-break)

### Trapping Rain Water

[https://leetcode.com/problems/trapping-rain-water/](https://leetcode.com/problems/trapping-rain-water/)

### Task Scheduler

[https://leetcode.com/problems/task-scheduler/description/](https://leetcode.com/problems/task-scheduler/description/)

### Validate Binary Search Tree

[https://leetcode.com/problems/validate-binary-search-tree/](https://leetcode.com/problems/validate-binary-search-tree/)

### Snakes and Ladders

[https://leetcode.com/problems/snakes-and-ladders/description/](https://leetcode.com/problems/snakes-and-ladders/description/)

### Frequency of the Most Frequent Element

[https://leetcode.com/problems/frequency-of-the-most-frequent-element/description/](https://leetcode.com/problems/frequency-of-the-most-frequent-element/description/)

---

## Low Level Design and DSA Questions with Follow Ups Asked During Interview Rounds

### 1. Design a rate limiter

Design an in-memory rate limiter . Implement a RateLimiter Class with an isAllowed method.

Requests will be made to different resourceIds. Each resourceId will have a strategy associated with it.

**Practice Link:** [https://codezym.com/question/34](https://codezym.com/question/34)

### 2. Design Stock Market Investing Platform

You are asked to design a financial services platform (BuyStocks) where:

Users can sign up, buy/sell stocks, and view their portfolios.

Admins can add stocks with an initial price for the day and define the list of available stocks.

Users start trading with the amount present in their wallet.

**Practice Link:** [https://codezym.com/question/86](https://codezym.com/question/86)

### 3. Design a Parking Lot

Write code for low level design of a parking lot with multiple floors.

The parking lot has two kinds of parking spaces: type = 2, for 2 wheeler vehicles and type = 4, for 4 wheeler vehicles.

There are multiple floors in the parking lot. On each floor, vehicles are parked in parking spots arranged in rows and columns.

**Practice Link:** [https://codezym.com/question/7](https://codezym.com/question/7)

### 4. Design a Traffic Signal Light System

Design a Traffic Light System that dynamically adjusts which road gets the green light based on real-time vehicle arrivals.

The system manages a single intersection with multiple incoming roads. At any moment, at most one road can be open. An open road has a green light, and every other road is closed with a red light.

**Practice Link:** [https://codezym.com/question/125](https://codezym.com/question/125)

### 5. Design Parking Lot Pricing System

Design a Parking Lot Pricing System that calculates parking price efficiently using different pricing strategies.

**Practice Link:** [https://codezym.com/question/126](https://codezym.com/question/126)

### 6. Design Snake and Ladder Game

Design a Snake and Ladder game simulator.

The game is played on a standard Snake and Ladder board with 100 numbered cells. Cell 1 is the starting position and cell 100 is the winning position.

The game supports multiple players and follows round-robin turn order. Multiple players may stay on the same cell at the same time without any interaction.

Some cells contain ladders. Landing on the start of a ladder immediately moves the player to a higher-numbered cell.

Some cells contain snakes. Landing on the head of a snake immediately moves the player to a lower-numbered cell.

**Practice Link:** [https://codezym.com/question/130](https://codezym.com/question/130)

### 7. Design a TTL (Time to Live) Cache

Design a TTL based Cache.

The cache stores key-value pairs, where each key is associated with a time-to-live (TTL). A key is available only for its valid TTL window and becomes expired after that.

**Practice Link:** [https://codezym.com/question/132](https://codezym.com/question/132)

### 8. Design Notification System

Implement a Notification System.

The system should support multiple notification channels such as EMAIL, SMS, PUSH etc. Users can subscribe to a specific eventType on one or more channels. When a notification is sent for an event type, the system must deliver that notification to all users currently subscribed to that event type through their subscribed channels.

**Practice Link:** [https://codezym.com/question/133](https://codezym.com/question/133)

### 9. Design Order Checkout and Payment for E-commerce Website

Design an E-commerce payment checkout system.

Focus on order cancellation and payment flows.

The checkout system should support creating an order, starting a payment, completing a payment, cancelling an order, and reading the current order details.

**Practice Link:** [https://codezym.com/question/134](https://codezym.com/question/134)

### 10. Design URL Shortener

Design a URL shortener.

The system should support creating a short URL for a given long URL, resolving a short code back to the original long URL, deactivating an existing short URL, and reading the details of a short URL.

**Practice Link:** [https://codezym.com/question/135](https://codezym.com/question/135)

### 11. Design expense sharing app like Splitwise

Design a lightweight expense-sharing system that tracks how much each person owes or is owed after group expenses are split evenly among participants. The system maintains net balances and exposes operations to add users, record expenses, and list simplified debtor-to-creditor balances.

**Practice Link:** [https://codezym.com/question/12](https://codezym.com/question/12)

### 12. Matrix Special Elements

Given a matrix, return the number of distinct values that appear as a unique minimum or unique maximum in at least one row or at least one column.

**Practice Link:** [https://codezym.com/question/108](https://codezym.com/question/108)

### 13. String Burst

Given a sequence of lowercase English letters and a burstLength, repeatedly remove every contiguous group whose size is greater than or equal to burstLength.

**Practice Link:** [https://codezym.com/question/109](https://codezym.com/question/109)

### 14. Highest Average Student Score

Given a list of student score entries, find the student with the highest average score.

**Practice Link:** [https://codezym.com/question/110](https://codezym.com/question/110)

### 15. Maximum Stones Path

Given a grid of stone values, find the maximum total number of stones that can be collected while moving from the bottom-left cell to the top-right cell.

**Practice Link:** [https://codezym.com/question/111](https://codezym.com/question/111)

### 16. Largest Tree in a Forest

Given a forest represented by a list of child-parent relationships, find the root of the tree that contains the maximum number of nodes.

**Practice Link:** [https://codezym.com/question/112](https://codezym.com/question/112)

### 17. Minimum Cost Array

Given a list of costs, find the minimum total cost needed to cross the array or reach the end of the array.

**Practice Link:** [https://codezym.com/question/113](https://codezym.com/question/113)

### 18. Hospital Triage Priority Scheduling

Hospital Triage Priority Scheduling determines the treatment order of a patient in a hospital emergency room.

**Practice Link:** [https://codezym.com/question/114](https://codezym.com/question/114)

### 19. Eliminate Substring

You are given two strings s and sub, where both contain only uppercase English letters.

Repeatedly perform the following operation: Remove one occurrence of the substring sub from s.

After each removal, join the remaining left and right parts of the string. Continue until the string no longer contains sub.

**Practice Link:** [https://codezym.com/question/115](https://codezym.com/question/115)

### 20. Bitwise XOR Subsequences

You are given a list of integers arr and an integer k.

A subsequence of a list is formed by removing zero or more elements without changing the order of the remaining elements.

A subsequence is considered valid if every pair of adjacent elements in that subsequence has a bitwise XOR equal to k.

Determine the length of the longest valid subsequence.

**Practice Link:** [https://codezym.com/question/116](https://codezym.com/question/116)

### 21. Single Element in Sorted Array - Pairs, Triplets

You are given a sorted list of integers where every element appears exactly twice, except for one element which appears exactly once.

Return the single element that appears only once.

**Practice Link:** [https://codezym.com/question/117](https://codezym.com/question/117)

### 22. Virus Spread in Directed Graph

There is a set of people represented as a uni-directional graph with no cycles.

Each person has a unique id.

If a person gets the virus, then every person reachable from that person through directed graph edges will also get the virus.

Given the graph and the id of one infected person, return all people who will get the virus.

**Practice Link:** [https://codezym.com/question/118](https://codezym.com/question/118)

### 23. Top K Tallest Buildings

You are given a list of building heights.

Return the indexes of the top k tallest buildings.

Use 0-based indexing.

If multiple buildings have the same height, the building with the lower index must come first.

Return the selected indexes ordered from tallest building to shortest building.

**Practice Link:** [https://codezym.com/question/119](https://codezym.com/question/119)

### 24. Count Prefix Using Trie

Implement a Trie (Prefix Tree) that supports inserting words and counting how many inserted words start with a given prefix.

**Practice Link:** [https://codezym.com/question/120](https://codezym.com/question/120)

### 25. Top K Frequent Numbers in a Stream

Implement a class that supports the following operations on numbers:

insert(num) - insert a number into the data structure.

isTopK(num) - return whether the number is currently among the top k most frequent numbers in the data structure.

**Practice Link:** [https://codezym.com/question/121](https://codezym.com/question/121)

### 26. First Non Repeated Character in a Stream

Design a class that finds the first non-repeated character in a string.

The full string is not given at once. Instead, small substrings are streamed in the order they appear in the larger string.

After processing streamed substrings, return the first character whose total frequency in the full streamed string is exactly 1.

**Practice Link:** [https://codezym.com/question/122](https://codezym.com/question/122)

### 27. Minimum Characters to Remove from Ends to Make a Palindrome

Given a string, return the minimum number of characters to remove from the left end, the right end, or both ends so that the remaining string is a palindrome.

You may remove only characters from the two ends of the string. Characters in the middle cannot be removed unless they become part of an end after earlier removals.

**Practice Link:** [https://codezym.com/question/123](https://codezym.com/question/123)

### 28. Container With Most Water Upright and Tilted

You are given a list of integers height of length n.

There are n vertical lines drawn such that the two endpoints of the ith line are (i, 0) and (i, height[i]).

You need to support two related computations:

Find two lines that together with the x-axis form a container such that the container contains the most water.

Find the maximum amount of water if the container formed by the chosen two lines is allowed to be tilted.

Return the maximum amount of water for each version using its corresponding method.

**Practice Link:** [https://codezym.com/question/124](https://codezym.com/question/124)

### 29. Combine Integer Arrays / Lists

Combine two integer lists into one without repeating elements.

You are given two integer lists nums1 and nums2. Both lists may contain duplicate values.

Return a new list that contains each distinct integer exactly once, while preserving the order of first appearance across the two input lists.

**Practice Link:** [https://codezym.com/question/127](https://codezym.com/question/127)

### 30. Max Path Sum in Matrix

You are traveling from Washington to Florida through a grid of cities. Each string in the input list represents one row of the matrix, with integer values separated by commas. Each cell in the matrix represents the number of National Parks you can visit in that city.

**Practice Link:** [https://codezym.com/question/128](https://codezym.com/question/128)

---

## Thanks for reading.


Wish you the best of luck for your interview prep.
