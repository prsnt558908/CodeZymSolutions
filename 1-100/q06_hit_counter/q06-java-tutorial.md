# Low Level Design of a Webpage Visits Counter

**Problem Statement:** https://codezym.com/question/6-design-hit-counter-multithreaded

Below YouTube Video describes machine coding solution for object-oriented design of a Webpage Visits Counter.

## Video Explanation

[![Low Level Design of a Webpage Visits Counter](https://img.youtube.com/vi/jU7I2-jWJ8k/hqdefault.jpg)](https://www.youtube.com/watch?v=jU7I2-jWJ8k)

YouTube Video : https://www.youtube.com/watch?v=jU7I2-jWJ8k

This question can be asked in multiple forms in a LLD interview, e.g.

- design a hit counter or

- design a twitter/Instagram/Facebook posts like counter or

- Become a Medium member

- design a YouTube video views counter and so on…

Below are 3 types of solutions using different data structures.

## 1. Most efficient : Using a ArrayList of AtomicInteger

```java
ArrayList<AtomicInteger> pageVisitCounts
```

```java
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class Solution implements Q06WebpageVisitCounterInterface {
    private Helper06 helper;
    private ArrayList<AtomicInteger> pageVisitCounts;

    public Solution(){}

    public void init(int totalPages, Helper06 helper){
        this.helper=helper;
        pageVisitCounts = new ArrayList<>();
        for(int i=0;i<totalPages;i++)pageVisitCounts.add(new AtomicInteger(0));
    }

    public void incrementVisitCount(int pageIndex) {
        pageVisitCounts.get(pageIndex).incrementAndGet();
    }

    public int getVisitCount(int pageIndex) {
        return pageVisitCounts.get(pageIndex).get();
    }
}
```

## 2. Using a ConcurrentHashmap

```java
ConcurrentHashMap<Integer, Integer> pageVisitCounts
```

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Solution implements Q06WebpageVisitCounterInterface {
    private Helper06 helper;
    // pageIndex vs visit count
    private ConcurrentHashMap<Integer, Integer> countsMap;

    public Solution(){}

    public void init(int totalPages, Helper06 helper){
        this.helper=helper;
        countsMap = new ConcurrentHashMap<>();
        for(int i=0;i<totalPages;i++)countsMap.put(i, 0);
    }

    public void incrementVisitCount(int pageIndex) {
        countsMap.compute(pageIndex, (key, oldValue) ->  oldValue+1);
    }

    public int getVisitCount(int pageIndex) {
        return countsMap.get(pageIndex);
    }
}
```

## 3. Least efficient: Using an ArrayList of Integer

```java
ArrayList<Integer> pageVisitCounts
```

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Solution implements Q06WebpageVisitCounterInterface {
    private Helper06 helper;
    private ArrayList<Integer> visitCounts;

    public Solution(){}

    public void init(int totalPages, Helper06 helper){
        this.helper=helper;
        visitCounts = new ArrayList<>();
        for(int i=0;i<totalPages;i++) visitCounts.add(0);
    }

    public void incrementVisitCount(int pageIndex) {
        if(pageIndex<0||pageIndex>=visitCounts.size())return;
        synchronized(visitCounts) {
            visitCounts.set(pageIndex, 1 + visitCounts.get(pageIndex));
        }
    }

     public int getVisitCount(int pageIndex) {
        if(pageIndex<0||pageIndex>=visitCounts.size())return 0;
        return visitCounts.get(pageIndex);
    }
}
```
