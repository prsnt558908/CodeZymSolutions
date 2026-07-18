![Salesforce Low Level Design Round Interview Questions Asked in 2026](https://d3jug84e56mcss.cloudfront.net/blog-images/0/23-1-salesforce-lld-2026-sm-60-80.jpg)


# Salesforce Low Level Design Round Interview Questions Asked in 2026

Low Level Design (LLD) questions for this list have been picked from Salesforce interview experiences shared on forums/blogs etc in 2026. Use this list for final preparation of your Salesforce interview rounds.

Low Level Design of pub-sub queue, kafka and observer pattern are discussed frequently.

Apart from that cache like LRU cache, LFU cache questions are common.

### LRU Cache

[https://leetcode.com/problems/lru-cache/description/](https://leetcode.com/problems/lru-cache/description/)

### LFU Cache

[https://leetcode.com/problems/lfu-cache/description/](https://leetcode.com/problems/lfu-cache/description/)

## Requirements and clarifying questions

In Salesforce LLD rounds, sometimes actual requirements may not be clear from problem statement and interviewer will expect you to figure it out by asking clarifying questions.

For example, A message queue is asked indirectly in the form of something like a connection pool or a job scheduler.

It follows the pattern that resources like connections, machines or cpu (in case of job scheduler) are limited and they may not be assigned immediately.

There is discussion about concurrency control when multiple threads are there.

There may also be discussion on concurrency control if our application is made distributed and we are using a central database (i.e. LLD discussion moving to HLD in the end).

## C++ / OOP questions

- What happens if a class dynamically allocates memory but no destructor is defined
- Can a constructor be private
- Difference between abstraction and encapsulation

---

### Complete list of Salesforce low level design round questions:

[https://codezym.com/lld/salesforce](https://codezym.com/lld/salesforce)

If you are looking for Salesforce DS and Algo round questions, then you can find them here:

[https://codezym.com/lld/salesforce-dsa](https://codezym.com/lld/salesforce-dsa)

---

## Below are recent LLD questions asked in Salesforce:



### 1. Design a Movie ticket booking system like BookMyShow

Design a movie ticket booking system like BookMyShow. System has cinemas located in different cities. Each cinema will have multiple screens, and users can book one or more seats for a given movie show.

System should be able to add new cinemas and movie shows in those cinemas.

Users should be able to list all cinema's in their city which are displaying a particular movie.

For a given cinema, users should also be able to list all shows which are displaying a particular movie.

[https://codezym.com/question/10-design-movie-ticket-booking-system](https://codezym.com/question/10-design-movie-ticket-booking-system)



### 2. Design a restaurant food ordering system like Zomato, Swiggy, DoorDash

Write code for low level design of a restaurant food ordering and rating system, similar to food delivery apps like Zomato, Swiggy, Door Dash, Uber Eats etc.

There will be food items like 'Veg Burger', 'Veg Spring Roll', 'Ice Cream' etc. And there will be restaurants from where you can order these food items.

Same food item can be ordered from multiple restaurants. e.g. you can order 'food-1' 'veg burger' from burger king as well as from McDonald's.

Users can order food, rate orders, fetch restaurants with most rating and fetch restaurants with most rating for a particular food item e.g. restaurants which have the most rating for 'veg burger'.

[https://codezym.com/question/5-design-food-ordering-system](https://codezym.com/question/5-design-food-ordering-system)



### 3. Workflow Step State Transitions History

Design a component that records state changes for steps inside workflows. Each workflow has multiple step ids, and every step starts in the Pending state.

A step can move from Pending to Running, and then from Running to either Completed or Failed. The component must keep the complete transition history for every step after workflow creation.

The initial Pending state at workflow creation is not stored as a transition. Only successful state updates are stored in transition history.

[https://codezym.com/question/257-workflow-step-state-transition-history](https://codezym.com/question/257-workflow-step-state-transition-history)



### 4. Design Service Dependency Impact Analyzer

You are given a collection of microservices and their directed dependencies. Analyze the system to determine which services are affected when one service fails, the maximum depth of that impact, and whether the dependency graph contains a cycle.

[https://codezym.com/question/266-service-dependency-impact-analyzer](https://codezym.com/question/266-service-dependency-impact-analyzer)



### 5. Design a Single-Queue Publish Subscribe System

Design an in-memory publish/subscribe system with exactly one global FIFO (first in first out) queue.

Multiple publishers can publish messages to this queue. Many subscribers can subscribe to the same queue.

When a message is appended, all subscribers are notified, and each subscriber consumes at its own pace.

A subscriber can consume only those messages which were sent while it was subscribed.

PS: Use the Observer pattern (Queue Manager = Subject, Subscribers = Observers).

[https://codezym.com/question/33-design-single-queue-publish-subscribe-system](https://codezym.com/question/33-design-single-queue-publish-subscribe-system)



### 6. Design Spotify Music Streaming

Design the catalogue and playback components of a simplified Spotify-like music streaming service. The system must allow songs to be added, updated, searched, and played while maintaining an independent playback session for every user.

[https://codezym.com/question/268-complete-weekly-work-hours](https://codezym.com/question/268-complete-weekly-work-hours)



### 7. Design Cab Booking Service

Design a simplified cab booking service in which passengers can request and cancel rides, while drivers can accept and complete them. Each ride contains a supplied ride ID, pickup location, destination, ride type, estimated distance, estimated travel time, and calculated fare.

[https://codezym.com/question/269-cab-booking-service](https://codezym.com/question/269-cab-booking-service)



### 8. Design a Kafka like Message Streaming Service with Multiple Topics

Design a working MVP for an in-memory message streaming service supporting multiple topics, producers, and consumers. Ensure that the messages maintain order within partitions.

[https://codezym.com/question/72-design-kafka-like-message-streaming-service](https://codezym.com/question/72-design-kafka-like-message-streaming-service)



### 9. Design Elevator Management System - Single Lift

Write code for low level design of a simple elevator management system consisting of a single lift.

All the lifts are in the same building which has multiple floors.

Lifts are numbered 0 to lifts-1 and floors are numbered 0 to floors -1.

Each lift can carry at max liftsCapacity number of people at a single time.

[https://codezym.com/question/24-design-elevator-management-system-single-lift](https://codezym.com/question/24-design-elevator-management-system-single-lift)



### 10. Design a Library Management System

Design and implement an in-memory Library Management system that caters to its registered members by cataloging and housing books that can be borrowed.

The system must support:

- Adding books to the catalog.
- Registering and unregistering users.
- Reservation management to borrow books using book ids (with FIFO waitlist).
- Fine calculation on late returns (20 rupees per delayed day after 14 days).
- (Bonus) Limiting a user to reserve only one copy of the same book.
- (Bonus) Auditing APIs:
- Given a bookId, list users currently having that book.
- Given a userId, list books currently issued to that user.

[https://codezym.com/question/81-design-library-management-system](https://codezym.com/question/81-design-library-management-system)



### 11. Design a Meeting Room Scheduler - List Bookings

Design an in-memory Meeting Room Scheduler that allows employees to view available rooms, book a room for a time interval, cancel a booking, and list bookings by room or by employee.

[https://codezym.com/question/44-design-meeting-room-scheduler-list-bookings](https://codezym.com/question/44-design-meeting-room-scheduler-list-bookings)



### 12. Design a Meeting Room Scheduler for Recurrent Meetings

Design an in-memory Meeting Room Scheduler for recurrent meetings that allows employees to do the following:

- View available rooms,
- Book a room for meeting which repeats after a given time interval for 20 future instances,
- Cancel a booking and all its instances, and
- List bookings by room or by employee.

[https://codezym.com/question/45-design-meeting-room-scheduler-recurrent-meetings](https://codezym.com/question/45-design-meeting-room-scheduler-recurrent-meetings)



## Thanks for reading.


Wish you the best of luck for your interview prep.