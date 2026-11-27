# Recent Amazon Low Level Design Interview Round Questions in Nov 2026


 Below is the list of questions. Use them to prepare for your Amazon low level design rounds.

### 1. Design Spotify Music Streaming and Search

Keep a global song catalogue where songs can be added, updated and searched by title, artist or album. Every user also gets their own playback session to play, pause, resume, seek and stop songs.

https://codezym.com/question/268-spotify-music-streaming-and-search

### 2. Design Extensible Notification System for Sending Alerts

Send alerts of different categories through channels like EMAIL, SMS and PUSH, creating one log per unique recipient and channel. New categories and channels can be registered later without changing any method signature.

https://codezym.com/question/454-notification-system-for-sending-alerts

### 3. Design Unix "find" command for file search

Store files in memory and recursively search a directory for files larger than a given size or with a given extension. Return sorted paths, and keep it easy to add new search criteria using the Strategy pattern.

https://codezym.com/question/14-design-unix-find-command-file-search

### 4. Design Unix "find" command - with boolean predicates

Extend the file search so that size and extension rules can be combined with AND, OR and AND NOT, evaluated from left to right. Results must be unique and sorted, using the Strategy and Specification patterns.

https://codezym.com/question/15-design-unix-find-command-boolean-predicates

### 5. Design Software Timer Queue on a Single Hardware Timer

Run many software timers on the single hardware timer of an embedded device. Set the hardware timer only when it is idle or a new timer expires sooner, and when it fires, return every due timer and re-arm it for the next one.

https://codezym.com/question/494-single-hardware-timer-queue

### 6. Design Most Recently Watched Tab for Video Service

Keep a recently watched tab for each user with the latest video first, where watching a video again moves it to the front without duplicates. Fetch the latest few videos or remove a video from the tab.

https://codezym.com/question/496-recently-watched-tab

### 7. Design E-commerce System with Cart, Stock and Orders

Manage products with price and stock, user carts that can never hold more than the available stock, and cart totals. Placing an order for the whole cart reduces stock, and cancelling it puts the stock back.

https://codezym.com/question/497-ecommerce-cart-stock-orders

### 8. Design Notification System

Users subscribe to event types on channels like EMAIL, SMS or PUSH. A notification is delivered once for every unique user and channel subscription, returned in sorted order and saved in each user's inbox.

https://codezym.com/question/133-design-notification-system

### 9. Design Audio Buffers Across Different Stages

Move audio buffers through ordered processing stages, where every stage handles at most a fixed number of frames per second in FIFO order. Report which buffers finish each second and how many frames wait at each stage.

https://codezym.com/question/448-audio-buffers-across-stages

### 10. Mix Songs from DJservice and Recommendation Service

Build a playlist from song portions sent by a DJ service and a recommendation service, mixed equally or by custom weights. Only portions having all preferred tags are used, and one service fills in when the other runs out.

https://codezym.com/question/437-dj-service-and-song-recommendation

### 11. Design Device Manager With Message Capabilities

Track whether each device is plugged in, its charging percentage and its optional display and speaker capabilities. Sending a message returns one output per supported capability, with the display output always first.

https://codezym.com/question/442-device-manager-with-messaging-capabilities

### 12. Design Multi-Module Logging Framework

Three modules submit logs with unique sequence numbers to a FILE or TOOL destination and get an ACK once the log is written, or REJECTED for bad input. Fetch logs by destination, module, level and sequence-number range.

https://codezym.com/question/340-multi-module-logging-framework

### 13. Design Vending Machine With Exact Payment

Select a product and insert accepted cash only up to its exact price, since the machine never gives change. Dispense on exact payment, return the inserted cash on cancel, and allow restocking with a new price.

https://codezym.com/question/335-vending-machine-with-exact-payment

### 14. Design a Meeting Room Scheduler - List Bookings

Book meeting rooms for inclusive time intervals without overlaps, so [10, 20] and [20, 30] clash. Find rooms that are free for an interval, cancel bookings, and list bookings by room or by employee.

https://codezym.com/question/44-design-meeting-room-scheduler-list-bookings

### 15. Design a Meeting Room Scheduler for Recurrent Meetings

Book a meeting that repeats at a fixed interval for 20 occurrences, only if every occurrence is free in that room. Cancel a booking with all its occurrences, find free rooms, and list the first n bookings by room or employee.

https://codezym.com/question/45-design-meeting-room-scheduler-recurrent-meetings

### 16. Design Recent User Query LRU Cache

Cache the results of recent user queries with a fixed capacity, where storing and fetching both take O(1) average time. When a new query overflows the cache, the least recently used query is removed.

https://codezym.com/question/455-recent-user-query-lru-cache

### 17. Design Pizza Pricing System

Calculate a pizza's final price from its base price, tax and topping servings using the Decorator pattern. Cheeseburst raises the tax rate and costs less after the first serving, and it can't be combined with mushroom.

https://codezym.com/question/18-design-pizza-pricing-system

### 18. Design Pizza Pricing System - Followup

Extend pizza pricing with size-based rules. Cheeseburst servings are capped by size, pineapple is allowed only on medium and large pizzas, mushroom lowers the tax rate, and corn pricing depends on the size.

https://codezym.com/question/19-design-pizza-pricing-system-followup

### 19. Design a Parking Lot

Park 2-wheelers and 4-wheelers in a multi-floor grid of spots using one of two strategies: the lowest-index free spot, or the floor with the most free spots. Also unpark vehicles, search them and count free spots per floor.

https://codezym.com/question/7-design-a-parking-lot

### 20. Design Cron Job Scheduler

Schedule one-time and recurring jobs, then dispatch due runs to available workers in a fixed order without duplicates. Failed attempts are retried up to a limit, and the status of every run can be checked.

https://codezym.com/question/456-cron-job-scheduler