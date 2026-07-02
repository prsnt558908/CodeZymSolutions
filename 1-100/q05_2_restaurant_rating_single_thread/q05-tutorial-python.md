# Design of a Food Ordering System like Zomato, Swiggy, DoorDash in Python for Low Level Design Interviews using Observer design pattern

You must have used restaurant food ordering apps like Zomato/Swiggy/DoorDash/Uber Eats etc, for ordering food.  
Let's see, how will you approach their design in a low level design interview.

Problem Statement: https://codezym.com/question/5-design-food-ordering-system

Please go through above problem statement before reading ahead.

## Video Explanation

[![Design a Food Ordering System Python Solution](https://img.youtube.com/vi/KGN-pSlMZgg/hqdefault.jpg)](https://www.youtube.com/watch?v=KGN-pSlMZgg)

YouTube Video : https://www.youtube.com/watch?v=KGN-pSlMZgg

We will discuss the requirements, then build our solution using multiple classes. We will also see how using observer design pattern leads to a simple solution which is also easy to extend. Finally, we will have complete Python code which you can test using above CodeZym link.

## Requirements

You basically do 3 things in a food ordering system

### 1. Browse list of restaurants sorted by rating, price, popularity etc.

Above problem statement specifies two ways of listing restaurants

```python
get_top_rated_restaurants()
get_top_restaurants_by_food(foodItemId)
```

### 2. Order food item from restaurants

an orderId will already be given and you won’t need to generate one

```python
order_food(orderId, restaurantId, foodItemId)
```

### 3. Rate your order

You can assign a rating of 1, 2, 3, 4 or 5 stars to your order. 5 stars is the best rating and 1 means the worst rating.

when you are giving rating an order e.g giving 4 stars to an order, then it means you are assigning 4 stars to both the food item in that restaurant as well as 4 stars to the overall restaurant rating.

```python
rate_order(orderId, rating)
```

## How to Approach

Now there are going to be other functionalities like Payments, Delivery Tracking etc in a restaurant food ordering system.  
However, you should already know that large systems like these take months, even years to be built by 100’s of software engineers.

Hence, nobody is expecting you to write down all the classes in all the components/functionalities that may exist in these systems. It is simply not possible to describe everything during a 45 min face to face or 75 to 90 minutes machine coding low level design round. So, take it easy.

A better approach is to figure out the feature that your interviewer wants to discuss with you. For a food ordering system almost every time these will be the core features that we discussed above. For a LLD interview, it is important to know what topics to discuss and deep dive into them.  
It's even more important to leave out the other features out of discussion. Let’s start working on the design.

## Breaking the solution in multiple classes

In any LLD interview, easiest thing to do is first list down entities and their managers. We will have class `Order` as our first entity. It will keep track, which food item was ordered from which restaurant and what was the rating given to it.

```python
class Order:
    def __init__(self, orderId, restaurantId, foodItemId, rating):
        self.orderId = orderId
        self.restaurantId = restaurantId
        self.foodItemId = foodItemId
        self.rating = rating
```

Also we need to keep track of overall average rating for restaurants and food items. Lets have a class `Rating` for that. This class keep track of sum of all ratings received and number of people who have assigned rating.

e.g. if 4 people have rated food items from a particular restaurant as 4, 3, 5 and 2 then its sum will be 4+3+5+2 = 14 and count will be 4, so overall average rating of the restaurant will be 14/4 = 3.5

```python
class Rating:
    def __init__(self, sum, count):
        self.sum = sum
        self.count = count

    def getAverageRating(self):
        if self.count <= 0:
            return 0
        rating = self.sum / self.count
        rating = round(rating, 1)
        return rating

    def add(self, num):
        self.sum += num
        self.count += 1
```

Next we will need a class `OrdersManager` to track all orders and rate them. It will keep all orders in a dictionary and have methods for ordering food and rating any order.

```python
class OrdersManager:
    def __init__(self):
        self.map = {}

    def orderFood(self, orderId, restaurantId, foodItemId):
        order = Order(orderId, restaurantId, foodItemId, 0)
        self.map[orderId] = order

    def rateOrder(self, orderId, rating):
        order = self.map[orderId]
        order.setRating(rating)
```

We have two different functionalities to list top restaurants. The first one is `get_top_rated_restaurants()`, we will have class `MostRatedRestaurants` to handle this. It will track rating of all restaurants in a dictionary. It will sort restaurants by average rating and return top n restaurants.

```python
class MostRatedRestaurants:
    def __init__(self):
        self.ratings = defaultdict(lambda: Rating(0, 0))

    def getRestaurants(self, n) -> list[str]:
        sorted_restaurants = sorted(self.ratings.keys(),
           key=lambda x: (-self.ratings[x].getAverageRating(), x))
        return sorted_restaurants[:n]
```

Here we sort by two things:

- higher average rating first
- if ratings are same, smaller restaurant id first

The last functionality which we have is `get_top_restaurants_by_food(foodItemId)`. This functionality will be implemented by a separate class `MostRatedRestaurantsByFood`. It will work similar to above class. However, it will use a two-level dictionary since it needs to keep track of average rating of each food item inside each restaurant. Again, for a given food item, it will sort restaurants and build a list of top n restaurants for that food item.

```python
class MostRatedRestaurantsByFood:
    def __init__(self):
        self.ratings = defaultdict(lambda: defaultdict(lambda: Rating(0, 0)))

    def getRestaurants(self, foodItemId, n) -> list[str]:
        if foodItemId not in self.ratings:
            return []
        restaurants_map = self.ratings[foodItemId]
        sorted_restaurants = sorted(restaurants_map.keys(),
             key=lambda x: (-restaurants_map[x].getAverageRating(), x))
        return sorted_restaurants[:n]
```

Here is what our `Solution` class which is sort of our driver or controller class, look like. It will simply use classes we created above to fulfill all its functionalities.

```python
class Solution:

    def init(self, helper):
        self.helper = helper
        self.ordersManager = OrdersManager()
        self.mostRatedRestaurants = MostRatedRestaurants()
        self.mostRatedRestaurantsByFood = MostRatedRestaurantsByFood()

    def order_food(self, orderId, restaurantId, foodItemId):
        self.ordersManager.orderFood(orderId, restaurantId, foodItemId)

    def rate_order(self, orderId, rating):
        self.ordersManager.rateOrder(orderId, rating)

    def get_top_restaurants_by_food(self, foodItemId) -> list[str]:
        return self.mostRatedRestaurantsByFood.getRestaurants(foodItemId, 20)

    def get_top_rated_restaurants(self) -> list[str]:
        return self.mostRatedRestaurants.getRestaurants(20)
```

## Using Observer design pattern to connect everything

One important question which you might have in mind while seeing the solution till now is, how will the listing classes i.e. class `MostRatedRestaurants` and class `MostRatedRestaurantsByFood` will know when an user rates their order. This is where observer pattern comes in.

We will use observer design pattern to send rate order updates from class `OrdersManager` to our list classes `MostRatedRestaurants` and class `MostRatedRestaurantsByFood`.

Now we can achieve our goal by updating these classes directly from `OrdersManager` class. But that will make restaurants listing classes tightly coupled with the `OrdersManager` class. i.e. in case we need a new class in future which needs rating updated for orders say for sending notifications or analytics then code in class `OrdersManager` will also need to be updated and we want to avoid that.

Observer pattern will give us loose coupling between `OrdersManager` class and restaurants listing classes and it will make it easy to add new observers in future. i.e. classes which need rating updates. Let's discuss how to implement this.

Both restaurant listing classes `MostRatedRestaurants` and `MostRatedRestaurantsByFood` will be our observers. They will inherit the class `RateOrderObserver` and update the ratings of restaurants in their own dictionaries whenever the `update()` method is called.

```python
class RateOrderObserver:
    def update(self, order):
        pass
```

```python
class MostRatedRestaurants(RateOrderObserver):
    def __init__(self):
        self.ratings = defaultdict(lambda: Rating(0, 0))

    def update(self, order):
        if order.getRestaurantId() not in self.ratings:
            self.ratings[order.getRestaurantId()] = Rating(0, 0)
        rating = self.ratings[order.getRestaurantId()]
        rating.add(order.getRating())
```

```python
class MostRatedRestaurantsByFood(RateOrderObserver):
    def __init__(self):
        self.ratings = defaultdict(lambda: defaultdict(lambda: Rating(0, 0)))

    def update(self, order):
        if order.getFoodItemId() not in self.ratings:
            self.ratings[order.getFoodItemId()] = defaultdict(lambda: Rating(0, 0))
        restaurants_map = self.ratings[order.getFoodItemId()]
        if order.getRestaurantId() not in restaurants_map:
            restaurants_map[order.getRestaurantId()] = Rating(0, 0)
        rating = restaurants_map[order.getRestaurantId()]
        rating.add(order.getRating())
```

Class `OrdersManager` will be the subject. It will keep a list of observers and will notify all of them with `notifyAll()` whenever any order is rated by the customer i.e. `rateOrder()` is called.

```python
class OrdersManager:
    def __init__(self):
        self.map = {}
        self.observers = []

    def rateOrder(self, orderId, rating):
        order = self.map[orderId]
        order.setRating(rating)
        self.notifyAll(order)

    def addObserver(self, observer):
        self.observers.append(observer)

    def notifyAll(self, order):
        for observer in self.observers:
            observer.update(order)
```

class `Solution` will add all the observers to `OrdersManager` during initialization.

```python
class Solution:

    def init(self, helper):
        self.helper = helper
        self.ordersManager = OrdersManager()
        self.mostRatedRestaurants = MostRatedRestaurants()
        self.mostRatedRestaurantsByFood = MostRatedRestaurantsByFood()
        self.ordersManager.addObserver(self.mostRatedRestaurants)
        self.ordersManager.addObserver(self.mostRatedRestaurantsByFood)
```

Now if we want to add a new observer, then all we have to do is create the new observer class, initialize it in class `Solution` and add it to `OrdersManager` using `addObserver()`.

## Complete Python Code

```python
from collections import defaultdict

class RateOrderObserver:
    def update(self, order):
        pass

class Solution:

    def init(self, helper):
        self.helper = helper
        self.ordersManager = OrdersManager()
        self.mostRatedRestaurants = MostRatedRestaurants()
        self.mostRatedRestaurantsByFood = MostRatedRestaurantsByFood()
        self.ordersManager.addObserver(self.mostRatedRestaurants)
        self.ordersManager.addObserver(self.mostRatedRestaurantsByFood)
        # self.helper.println("restaurant rating module initialized")

    def order_food(self, orderId, restaurantId, foodItemId):
        self.ordersManager.orderFood(orderId, restaurantId, foodItemId)

    def rate_order(self, orderId, rating):
        self.ordersManager.rateOrder(orderId, rating)

    def get_top_restaurants_by_food(self, foodItemId) -> list[str]:
        return self.mostRatedRestaurantsByFood.getRestaurants(foodItemId, 20)

    def get_top_rated_restaurants(self) -> list[str]:
        return self.mostRatedRestaurants.getRestaurants(20)

class OrdersManager:
    def __init__(self):
        self.map = {}
        self.observers = []

    def orderFood(self, orderId, restaurantId, foodItemId):
        order = Order(orderId, restaurantId, foodItemId, 0)
        self.map[orderId] = order

    def rateOrder(self, orderId, rating):
        order = self.map[orderId]
        order.setRating(rating)
        self.notifyAll(order)

    def addObserver(self, observer):
        self.observers.append(observer)

    def notifyAll(self, order):
        for observer in self.observers:
            observer.update(order)

class MostRatedRestaurants(RateOrderObserver):
    def __init__(self):
        self.ratings = defaultdict(lambda: Rating(0, 0))

    def update(self, order):
        if order.getRestaurantId() not in self.ratings:
            self.ratings[order.getRestaurantId()] = Rating(0, 0)
        rating = self.ratings[order.getRestaurantId()]
        rating.add(order.getRating())

    def getRestaurants(self, n) -> list[str]:
        sorted_restaurants = sorted(self.ratings.keys(),
           key=lambda x: (-self.ratings[x].getAverageRating(), x))
        return sorted_restaurants[:n]

class MostRatedRestaurantsByFood(RateOrderObserver):
    def __init__(self):
        self.ratings = defaultdict(lambda: defaultdict(lambda: Rating(0, 0)))

    def update(self, order):
        if order.getFoodItemId() not in self.ratings:
            self.ratings[order.getFoodItemId()] = defaultdict(lambda: Rating(0, 0))
        restaurants_map = self.ratings[order.getFoodItemId()]
        if order.getRestaurantId() not in restaurants_map:
            restaurants_map[order.getRestaurantId()] = Rating(0, 0)
        rating = restaurants_map[order.getRestaurantId()]
        rating.add(order.getRating())

    def getRestaurants(self, foodItemId, n) -> list[str]:
        if foodItemId not in self.ratings:
            return []
        restaurants_map = self.ratings[foodItemId]
        sorted_restaurants = sorted(restaurants_map.keys(),
             key=lambda x: (-restaurants_map[x].getAverageRating(), x))
        return sorted_restaurants[:n]

class Rating:
    def __init__(self, sum, count):
        self.sum = sum
        self.count = count

    def __str__(self):
        return f"sum {self.sum}, count {self.count}, avg {self.getAverageRating()}"

    def getAverageRating(self):
        if self.count <= 0:
            return 0
        rating = self.sum / self.count
        rating = round(rating, 1)#int((rating + 0.05) * 10) / 10.0
        return rating

    def add(self, num):
        self.sum += num
        self.count += 1

class Order:
    def __init__(self, orderId, restaurantId, foodItemId, rating):
        self.orderId = orderId
        self.restaurantId = restaurantId
        self.foodItemId = foodItemId
        self.rating = rating

    def setRating(self, rating):
        self.rating = rating

    def getRestaurantId(self):
        return self.restaurantId

    def getFoodItemId(self):
        return self.foodItemId

    def getRating(self):
        return self.rating
```
