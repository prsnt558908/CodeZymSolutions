// 5. Design a food delivery system for ordering food and rating restaurants - Multi-Threaded
// Problem statement link : https://codezym.com/question/5
// ****** It's better to write code in your local code editor and paste it back here *********

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

interface RateOrderObserver{
  void update(Order order);
}

interface RateOrderSubject{
    void addObserver(RateOrderObserver observer);
    void notifyAll(Order order);
}

public class Solution implements Q05RestaurantRatingInterface {
    private Helper05 helper;
    private FoodManager foodManager;
    private RestaurantManager restaurantManager;

    private  RestaurantSearchManager restaurantSearchManager;
    private OrdersManager ordersManager;
    
    public Solution(){}

    public void init(Helper05 helper){
        this.helper=helper;
        foodManager = new FoodManager();
        restaurantManager = new RestaurantManager();
        ordersManager = new OrdersManager();
        restaurantSearchManager = new RestaurantSearchManager(
                ordersManager, restaurantManager);
        // helper.println("restaurant rating module initialized");
    }

    public void addFoodItem(String foodItemId, double price, String foodItemName) {
        foodManager.addFoodItem(foodItemId, price, foodItemName);
    }

    public void addRestaurant(String restaurantId,
                 List<String> foodItemIds, String name, String address) {
        restaurantManager.addRestaurant(restaurantId, foodItemIds, name, address);
    }
    
    public void orderFood(String orderId, String restaurantId, String foodItemId) {
        ordersManager.orderFood(orderId,restaurantId, foodItemId);
    }

    /**
     * Customers can rate their order.
     * - when you are giving rating an order e.g giving 4 stars to an orders then it means
     * you are assigning 4 stars to both the food item in that restaurant
     * as well as 4 stars to the overall restaurant rating.
     * @param orderId order which will be rated by customer, orderId will always be valid
     *                i.e. order will always be created for an orderId before rateOrder() is called.
     * @param rating ranges from 1 to 5 stars in increasing order,
     *               1 being the worst and 5 being the best rating.
     */
    public void rateOrder(String orderId, int rating) {
        ordersManager.rateOrder(orderId, rating);
    }

    /**
     * - Fetches a list of top 20 restaurants based on strategy
     * - unrated restaurants will be at the bottom of list.
     */
    public List<String> getTopRestaurantsByFood(String foodItemId) {
        List<String> list = restaurantSearchManager.getRestaurantsByFood(foodItemId,  20);
        return list;
    }

    //  returns top 20 most rated restaurants ids sorted in descending order of their ratings.
    public List<String> getTopRatedRestaurants() {
        List<String> list = restaurantSearchManager.getTopRatedRestaurants(20);
        return list;
    }
}

class FoodItem{
    private String foodItemId, foodItemName;
    private double price;
    FoodItem(String foodItemId, String foodItemName, double price){
        this.foodItemId=foodItemId;
        this.foodItemName=foodItemName;
        this.price=price;
    }

    public String getFoodItemId() {
        return foodItemId;
    }

    public String getFoodItemName() {
        return foodItemName;
    }

    public double getPrice() {
        return price;
    }
}

class FoodManager{
    private ConcurrentHashMap<String, FoodItem> map = new ConcurrentHashMap<>();
    
    void addFoodItem(String foodItemId, double price, String foodItemName){
        FoodItem food = new FoodItem(foodItemId, foodItemName, price);
        map.put(foodItemId, food);
    }
    FoodItem getFoodItem(String foodItemId){
        if(foodItemId==null) return null;
        return map.get(foodItemId);
    }
}

class RestaurantManager{
    // food id vs list of restaurant ids
    private ConcurrentHashMap<String, ArrayList<String>> map =
            new ConcurrentHashMap<>();

    private ConcurrentLinkedDeque<String> allRestaurants=
            new ConcurrentLinkedDeque<>();

    void addRestaurant(String restaurantId,
          List<String> foodItemIds, String name, String address){
      allRestaurants.add(restaurantId);
      for(String foodItemId : foodItemIds)
          if(foodItemId!=null && !foodItemId.isBlank()){
              map.putIfAbsent(foodItemId, new ArrayList<>());
              ArrayList<String> list = map.get(foodItemId);
              synchronized (list){
                  list.add(restaurantId);
              }
          }
    }

    List<String> getAllRestaurants(String foodItemId){
        if(foodItemId==null || foodItemId.isBlank()) return new ArrayList<>();
        return map.getOrDefault(foodItemId, new ArrayList<>());
    }

    Collection<String> getAllRestaurants(){
        return Collections.unmodifiableCollection(allRestaurants);
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

    public String getOrderId() {
        return orderId;
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

    public String toString(){
        return "("+orderId+", "+foodItemId+", "+restaurantId+", "+rating+")";
    }
}

class OrdersManager implements RateOrderSubject{
    private ConcurrentHashMap<String, Order> map = new ConcurrentHashMap<>();

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

class RestaurantSearchManager {
     private MostRatedRestaurants mostRatedRestaurants;
     private MostRatedRestaurantsByFood mostRatedRestaurantsByFood;
     private RestaurantManager restaurantManager;

     RestaurantSearchManager(RateOrderSubject rateOrderSubject,
                             RestaurantManager restaurantManager){
        this.restaurantManager=restaurantManager;
        mostRatedRestaurants = new MostRatedRestaurants();
        mostRatedRestaurantsByFood= new MostRatedRestaurantsByFood();
        rateOrderSubject.addObserver(mostRatedRestaurants);
        rateOrderSubject.addObserver(mostRatedRestaurantsByFood);
     }

    /** n is number of restaurant ids */
    public List<String> getRestaurantsByFood(String foodItemId, int n) {
        List<String> list = mostRatedRestaurantsByFood.getRestaurants(
                foodItemId, n);
        if(list.size()>=n) return list;
        ArrayList<String> restaurants= new ArrayList<>(list);
        buildTopRestaurantsList(restaurants,
                restaurantManager.getAllRestaurants(foodItemId), n);
        return restaurants;
    }

    public List<String> getTopRatedRestaurants(int n) {
        List<String> list = mostRatedRestaurants.getRestaurants(n);
        if(list.size()>=n) return list;
        ArrayList<String> restaurants= new ArrayList<>(list);
        buildTopRestaurantsList(restaurants,
                restaurantManager.getAllRestaurants(), n);
        return restaurants;
    }

    // all.size() may be o(n) and not o(1) so better store it
    void buildTopRestaurantsList(
            ArrayList<String> added, Collection<String> all, int n){
        int allSize=all.size();
        if(added.size()>=n || added.size()>=allSize) return;
        HashSet<String> set = new HashSet<String>(added);
        ArrayList<String> allList=new ArrayList<>(all);
        Collections.sort(allList);
        for(String next:allList){
            if(added.size()>=n || added.size()>=allSize) return;
            if(set.add(next))added.add(next);
        }
    }
}

class MostRatedRestaurants implements RateOrderObserver{
    private SortedSetWithLock set=new SortedSetWithLock();

    List<String> getRestaurants(int n){
        return set.getRestaurants(n);
    }

    public void update(Order order) {
        set.update(order.getRestaurantId(),
                order.getRating());
    }
}

class MostRatedRestaurantsByFood implements RateOrderObserver{
    // food ids vs restaurantId with rating
    private ConcurrentHashMap<String, SortedSetWithLock> map
            = new ConcurrentHashMap<>();

    public void update(Order order) {
        map.putIfAbsent(order.getFoodItemId(), new SortedSetWithLock());
        SortedSetWithLock set = map.get(order.getFoodItemId());
        set.update(order.getRestaurantId(), order.getRating());
    }

    List<String> getRestaurants(String foodItemId, int n){
        SortedSetWithLock set = map.get(foodItemId);
        if(set==null) return new ArrayList<>();
        return set.getRestaurants(n);
    }
}

/**
 * natural order is sort by rating in descending order and then with id lexicographically
 */
class SortedSetWithLock{
    // restaurantId vs rating
    private ConcurrentHashMap<String, Rating> ratingsMap = new ConcurrentHashMap<>();

    // average rating vs set of restaurant ids which have that rating
    private HashMap<Double,HashSet<String>> ratingDivisons = new HashMap<>();

    SortedSetWithLock(){
        for(int rating=10;rating<=50;rating++)
            ratingDivisons.put(rating/10.0,new HashSet<>());
    }
    
    /** n is the number of top restaurants it returns, if we don't  */
    public synchronized List<String> getRestaurants(int n){
        ArrayList<String> restaurants = new ArrayList<>();
        for(int divison=50;divison>=10 && restaurants.size()<n;divison--){
            HashSet<String> set = ratingDivisons.get(divison/10.0);
            if(set.size()==0) continue;
            ArrayList<String> keys = new ArrayList<String>(set);
            Collections.sort(keys);
            for(String restaurantId :keys){
                restaurants.add(restaurantId);
                if(restaurants.size()>=n) break;
            }
        }
        return restaurants;
    }

    // remove from old division and add to new division
    public void update(String restaurantId, int customerRating) {
        ratingsMap.putIfAbsent(restaurantId, new Rating(0,0));
        Rating rating = ratingsMap.get(restaurantId);
        synchronized (rating) {
            if (rating.getAverageRating() >= 1.0) {
                HashSet<String> remove = ratingDivisons.get(
                        rating.getAverageRating());
                synchronized (remove) {
                    remove.remove(restaurantId);
                }
            }
            rating.add(customerRating);
            HashSet<String> toAdd = ratingDivisons.get(
                    rating.getAverageRating());
            synchronized (toAdd) {
                toAdd.add(restaurantId);
            }
        }
    }
}

class Rating{
    private AtomicInteger sum= new AtomicInteger(0),
            count=new AtomicInteger(0);
    Rating(){}
    Rating(int sum, int count){
        this.sum.set(sum);
        this.count.set(count);
    }

    /** rating is rounded down to one decimal point
     i.e. 4.05, 4.08, 4.11, 4.12,4.14 all become 4.1,
       4.15, 4.19,4.22,4.24 all become 4.2
     */
    double getAverageRating(){
        if(count.get()<=0) return 0;
        double rating = sum.doubleValue()/count.doubleValue();
        rating = (double)((int)((rating+0.05)*10))/10.0;
        return rating;
    }
    
    void add(int num){
        this.sum.addAndGet(num);
        this.count.addAndGet(1);
    }

    Rating copy(){
        return new Rating(this.sum.get(), this.count.get());
    }

    public String toString(){
        return "sum "+sum+", count "+count+", avg "+getAverageRating();
    }

}

// uncomment below code in case you are using your local ide like intellij, eclipse etc and
// comment it back again back when you are pasting completed solution in the online CodeZym editor.
// if you don't comment it back, you will get "java.lang.AssertionError: java.lang.LinkageError"
// This will help avoid unwanted compilation errors and get method autocomplete in your local code editor.
/**
interface Q05RestaurantRatingInterface {
    void init(Helper05 helper);
    void addFoodItem(String foodItemId, double price, String foodItemName);
    void addRestaurant(String restaurantId, List<String> foodItemIds, String name, String address);
    void orderFood(String orderId, String restaurantId, String foodItemId);
    void rateOrder(String orderId, int rating);
    List<String> getTopRestaurantsByFood(String foodItemId);
    List<String> getTopRatedRestaurants();
}

class Helper05 {
    void print(String s){System.out.print(s);}
    void println(String s){System.out.println(s);}
}
*/ 