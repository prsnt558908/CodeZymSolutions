# Design of a Food Ordering System like Zomato, Swiggy, DoorDash in Java for Low Level Design Interviews using Observer design pattern

You must have used restaurant food ordering apps like Zomato/Swiggy/DoorDash/Uber Eats etc, for ordering food.  
Let's see, how will you approach their design in a low level design interview.

Problem Statement:https://codezym.com/question/5-design-food-ordering-system

Please go through above problem statement before reading ahead.

We will discuss the requirements, then build our solution using multiple classes. We will also see how using observer design pattern leads to a simple solution which is also easy to extend. Finally, we will have complete Java code which you can test using above CodeZym link.

## Video Explanation

[![Design a Food Ordering System Java Solution](https://img.youtube.com/vi/v9ehOtY_x7Q/hqdefault.jpg)](https://www.youtube.com/watch?v=v9ehOtY_x7Q)

YouTube Video : https://www.youtube.com/watch?v=v9ehOtY_x7Q


## Requirements

You basically do 3 things in a food ordering system

### 1. Browse list of restaurants sorted by rating, price, popularity etc.

Above problem statement specifies two ways of listing restaurants

```java
getTopRatedRestaurants()
getTopRestaurantsByFood(String foodItemId)
```

### 2. Order food item from restaurants

an orderId will already be given and you won’t need to generate one

```java
orderFood(String orderId, String restaurantId, String foodItemId)
```

### 3. Rate your order

You can assign a rating of 1, 2, 3, 4 or 5 stars to your order. 5 stars is the beast rating and 1 means the worst rating.

when you are giving rating an order e.g giving 4 stars to an order, then it means you are assigning 4 stars to both the food item in that restaurant as well as 4 stars to the overall restaurant rating.

```java
rateOrder(String orderId, int rating)
```

## How to Approach

Now there are going to be other functionalities like Payments, Delivery Tracking etc in a restaurant food ordering system.  
However, you should already know that large systems like these take months, even years to be built by 100’s of software engineers.

Hence, nobody is expecting you to write down all the classes in all the components/functionalities that may exist in these systems. It is simply not possible to describe everything during a 45 min face to face or 75 to 90 minutes machine coding low level design round. So, take it easy.

A better approach is to figure out the feature that your interviewer wants to discuss with you. For a food ordering system almost every time these will be the core features that we discussed above. For a LLD interview, it is important to know what topics to discuss and deep dive into them.  
It's even more important to leave out the other features out of discussion. Let’s start working on the design.

## Breaking the solution in multiple classes

In any LLD interview, easiest thing to do is first list down entities and their managers. We will have class Order as our first entity. It will keep track, which food item was ordered from which restaurant and what was the rating given to it.

```java
class Order{
    private String orderId, restaurantId, foodItemId;
    private int rating;
}
```

Also we need to keep track of overall average rating for restaurants and food items. Lets have a class Rating for that. This class keep track of sum of all ratings received and number of people who have assigned rating.

Write on Medium

e.g. if 4 people have rated food items from a particular restaurant as 4, 3, 5 and 2 then its sum will be 4+3+5+2 = 14 and count will be 4, so overall average rating of the restaurant will be 14/4 = 3.5

```java
class Rating{
    private int sum=0, count=0;

    Rating(int sum, int count){
         this.sum=sum;
         this.count=count;
     }

    // rating is rounded down to one decomal point
    double getAverageRating(){
        if(count<=0) return 0;
        double rating = (double)sum/count;
        rating = (double)((int)((rating+0.05)*10))/10.0;
        return rating;
    }

    void add(int num){
        this.sum+=num;
        this.count+=1;
    }
}
```

Next we will need a class OrdersManager to track all orders and rate them. It will keep all orders in a map and have methods for ordering food and rating any order.

```java
class OrdersManager {
  // orderId vs Order
    private HashMap<String, Order> map = new HashMap<>();

    void orderFood(String orderId, String restaurantId, String foodItemId){
        Order order = new Order(orderId, restaurantId, foodItemId, 0);
        map.put(orderId, order);
    }

    void rateOrder(String orderId, int rating){
        Order order = map.get(orderId);
        order.setRating(rating);
    }
}
```

We have two different functionalities to list top restaurants. The first one is getTopRatedRestaurants(), we will have class MostRatedRestaurants to handle this. It will track rating of all restaurants in a map. It will brute force through the ratings map to build list of top n restaurants.

```java
class MostRatedRestaurants{
    // restaurantId vs rating
    private HashMap<String, Rating> ratings = new HashMap<>();
    
    List<String> getRestaurants(int n){}
}
```

The last functionality which we have is getTopRestaurantsByFood(String foodItemId). This functionality will be implemented by a separate class MostRatedRestaurantsByFood. It will work similar to above class. However, it will use a two-level map since it needs to keep track of average rating of each food item inside each restaurant. Again, for a given food item, it will also brute force through the inner map of all restaurants and build a list of top n restaurants for that food item.

```java
class MostRatedRestaurantsByFood {
    // foodItemId vs restaurantId vs rating
    private HashMap<String, HashMap<String, Rating>> ratings = new HashMap<>();

    List<String> getRestaurants(String foodItemId, int n){}
}
```

Here is what our Solution class which is sort of our driver or controller class, look like. It will simply use classes we created above to fulfill all its functionalities.

```java
public class Solution implements Q05RestaurantRatingInterface {
    private OrdersManager ordersManager;
    private MostRatedRestaurants mostRatedRestaurants;
    private MostRatedRestaurantsByFood mostRatedRestaurantsByFood;

    public void init(Helper05 helper){
        ordersManager = new OrdersManager();
        mostRatedRestaurants = new MostRatedRestaurants();
        mostRatedRestaurantsByFood= new MostRatedRestaurantsByFood();
    }

    public void orderFood(String orderId, String restaurantId, String foodItemId) {
        ordersManager.orderFood(orderId,restaurantId, foodItemId);
    }

    public void rateOrder(String orderId, int rating) {
        ordersManager.rateOrder(orderId, rating);
    }

    public List<String> getTopRestaurantsByFood(String foodItemId) {
        return mostRatedRestaurantsByFood.getRestaurants(foodItemId,  20);
    }

    public List<String> getTopRatedRestaurants() {
        return mostRatedRestaurants.getRestaurants(20);
    }
}
```

## Using Observer design pattern to connect everything

One important question which you might have in mind while seeing the solution till now is, how will the listing classes i.e. class MostRatedRestaurants and class MostRatedRestaurantsByFood will know when an user rates their order. This is where observer pattern comes in.

We will use observer design pattern to send rate order updates from class OrdersManager to or list classes MostRatedRestaurants and class MostRatedRestaurantsByFood .

Now we can achieve our goal by updating these classes directly from OrdersManager class. But that will make restaurants listing classes tightly coupled with the OrdersManager class. i.e. in case we need a new class in future which needs rating updated for orders say for sending notifications or analytics then code in class OrdersManager will also need to be updated and we want to avoid that.

Observer pattern will give us loose coupling between OrdersManager class and restaurants listing classes and it will make it easy to add new observers in future. i.e. classes which need rating updates. Let's discuss how to implement this.

Both restaurant listing classes MostRatedRestaurants and MostRatedRestaurantsByFood will be our observers. They will implement the interface RateOrderObserver and update the ratings of restaurants in their own maps whenever the update() method is called

```java
interface RateOrderObserver{
  void update(Order order);
}
```

```java
class MostRatedRestaurants implements RateOrderObserver{
    // restaurantId vs rating
    private HashMap<String, Rating> ratings = new HashMap<>();

    public  void update(Order order) {
        ratings.putIfAbsent(order.getRestaurantId(), new Rating(0,0));
        Rating rating = ratings.get(order.getRestaurantId());
        rating.add(order.getRating());
        }
}
```

```java
class MostRatedRestaurantsByFood implements RateOrderObserver{
    // foodItemId vs restaurantId vs rating
    private HashMap<String, HashMap<String, Rating>> ratings = new HashMap<>();

    public  void update(Order order) {
        ratings.putIfAbsent(order.getFoodItemId(), new HashMap<>());
        HashMap<String, Rating> restaurantsMap=ratings.get(order.getFoodItemId());
        restaurantsMap.putIfAbsent(order.getRestaurantId(), new Rating(0,0));
        Rating rating = restaurantsMap.get(order.getRestaurantId());
        rating.add(order.getRating());
    }
}
```

Class OrdersManager will be the subject. It will keep a list of observers and will notify all of them with notifyAll() whenever any order is rated by the customer i.e. rateOrder() is called.

```java
class OrdersManager {
    private HashMap<String, Order> map = new HashMap<>();
    private ArrayList<RateOrderObserver> observers = new ArrayList<>();

    void rateOrder(String orderId, int rating){
        Order order = map.get(orderId);
        order.setRating(rating);
        notifyAll(order);
    }

    public void addObserver(RateOrderObserver observer) {
        observers.add(observer);
    }

    public void notifyAll(Order order) {
        for(RateOrderObserver observer : observers) observer.update(order);
    }
}
```

class Solution will add all the observers to OrdersManager during initialization.

```java
public class Solution implements Q05RestaurantRatingInterface {
    private Helper05 helper;
    private OrdersManager ordersManager;
    private MostRatedRestaurants mostRatedRestaurants;
    private MostRatedRestaurantsByFood mostRatedRestaurantsByFood;
    
    public void init(Helper05 helper){
        this.helper=helper;
        ordersManager = new OrdersManager();
        mostRatedRestaurants = new MostRatedRestaurants();
        mostRatedRestaurantsByFood= new MostRatedRestaurantsByFood();

        ordersManager.addObserver(mostRatedRestaurants);
        ordersManager.addObserver(mostRatedRestaurantsByFood);
    }
}
```

Now if we want to add a new observer, then all we have to do is create the new observer class, initialize it in class Solution and add it to OrdersManager using addObserver().

## Complete Java Code

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

interface RateOrderObserver{
  void update(Order order);
}

public class Solution implements Q05RestaurantRatingInterface {
    private Helper05 helper;
    private OrdersManager ordersManager;
    private MostRatedRestaurants mostRatedRestaurants;
    private MostRatedRestaurantsByFood mostRatedRestaurantsByFood;
    
    public Solution(){}

    public void init(Helper05 helper){
        this.helper=helper;
        ordersManager = new OrdersManager();
        mostRatedRestaurants = new MostRatedRestaurants();
        mostRatedRestaurantsByFood= new MostRatedRestaurantsByFood();
        ordersManager.addObserver(mostRatedRestaurants);
        ordersManager.addObserver(mostRatedRestaurantsByFood);
        // helper.println("restaurant rating module initialized");
    }

    public void orderFood(String orderId, String restaurantId, String foodItemId) {
        ordersManager.orderFood(orderId,restaurantId, foodItemId);
    }

    public void rateOrder(String orderId, int rating) {
        ordersManager.rateOrder(orderId, rating);
    }

    public List<String> getTopRestaurantsByFood(String foodItemId) {
        List<String> list = mostRatedRestaurantsByFood.getRestaurants(foodItemId,  20);
        return list;
    }

    public List<String> getTopRatedRestaurants() {
        List<String> list = mostRatedRestaurants.getRestaurants(20);
        return list;
    }
}

class OrdersManager {
    private HashMap<String, Order> map = new HashMap<>();
    private ArrayList<RateOrderObserver> observers = new ArrayList<>();

    void orderFood(String orderId, String restaurantId, String foodItemId){
        Order order = new Order(orderId, restaurantId, foodItemId, 0);
        map.put(orderId, order);
    }

    void rateOrder(String orderId, int rating){
        Order order = map.get(orderId);
        order.setRating(rating);
        notifyAll(order);
    }

    public void addObserver(RateOrderObserver observer) {
        observers.add(observer);
    }

    public void notifyAll(Order order) {
        for(RateOrderObserver observer : observers) observer.update(order);
    }
}

class MostRatedRestaurants implements RateOrderObserver{
    // restaurantId vs rating
    private HashMap<String, Rating> ratings = new HashMap<>();

    public  void update(Order order) {
        ratings.putIfAbsent(order.getRestaurantId(), new Rating(0,0));
        Rating rating = ratings.get(order.getRestaurantId());
        rating.add(order.getRating());
        }

    List<String> getRestaurants(int n){
        TreeSet<String> tree= new TreeSet<String>((a,b)->{
            double ratingA = ratings.getOrDefault(
                    a, new Rating(0,0)).getAverageRating();
            double ratingB = ratings.getOrDefault(
                    b, new Rating(0,0)).getAverageRating();
            if(ratingA!=ratingB) return ratingA>ratingB?-1:1;
            return a.compareTo(b);
        });

        for(String restaurantId: ratings.keySet()){
            tree.add(restaurantId);
            if(tree.size()>n)tree.remove(tree.last());
        }
      return new ArrayList<>(tree);
    }
}

class MostRatedRestaurantsByFood implements RateOrderObserver{
    // foodItemId vs restaurantId vs rating
    private HashMap<String, HashMap<String, Rating>> ratings = new HashMap<>();

    public  void update(Order order) {
        ratings.putIfAbsent(order.getFoodItemId(), new HashMap<>());
        HashMap<String, Rating> restaurantsMap=ratings.get(order.getFoodItemId());
        restaurantsMap.putIfAbsent(order.getRestaurantId(), new Rating(0,0));
        Rating rating = restaurantsMap.get(order.getRestaurantId());
        rating.add(order.getRating());
    }

    List<String> getRestaurants(String foodItemId, int n){
        HashMap<String, Rating> restaurantsMap=ratings.getOrDefault(
                foodItemId, new HashMap<>());
        
        TreeSet<String> tree= new TreeSet<String>((a,b)->{
            double ratingA = restaurantsMap.getOrDefault(
                    a, new Rating(0,0)).getAverageRating();
            double ratingB = restaurantsMap.getOrDefault(
                    b, new Rating(0,0)).getAverageRating();
            if(ratingA!=ratingB) return ratingA>ratingB?-1:1;
            return a.compareTo(b);
        });

        for(String restaurantId: restaurantsMap.keySet()){
            tree.add(restaurantId);
            if(tree.size()>n)tree.remove(tree.last());
        }
        return new ArrayList<>(tree);
    }
}

class Order{
    private String orderId, restaurantId, foodItemId;
    private int rating;
    Order(String orderId, String restaurantId, String foodItemId, int rating){
        this.orderId=orderId;
        this.restaurantId=restaurantId;
        this.foodItemId=foodItemId;
        this.rating=rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public String getFoodItemId() {
        return foodItemId;
    }

    public int getRating() {
        return rating;
    }

}

class Rating{
    private int sum=0, count=0;
    Rating(int sum, int count){
         this.sum=sum;
         this.count=count;
     }
     public String toString(){
         return "sum "+sum+", count "+count+", avg "+getAverageRating();
     }

    // rating is rounded down to one decomal point
    double getAverageRating(){
        if(count<=0) return 0;
        double rating = (double)sum/count;
        rating = (double)((int)((rating+0.05)*10))/10.0;
        return rating;
    }

    void add(int num){
        this.sum+=num;
        this.count+=1;
    }
}



// uncomment below code in case you are using your local ide like intellij, eclipse etc and
// comment it back again back when you are pasting completed solution in the online CodeZym editor.
// if you don't comment it back, you will get "java.lang.AssertionError: java.lang.LinkageError"
// This will help avoid unwanted compilation errors and get method autocomplete in your local code editor.

/*interface Q05RestaurantRatingInterface {
    void init(Helper05 helper);
    void orderFood(String orderId, String restaurantId, String foodItemId);
    void rateOrder(String orderId, int rating);
    List<String> getTopRestaurantsByFood(String foodItemId);
    List<String> getTopRatedRestaurants();
}

class Helper05 {
    void print(String s){System.out.print(s);}
    void println(String s){System.out.println(s);}
}*/
```
