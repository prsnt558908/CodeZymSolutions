// ****** It's better to write code in your local code editor and paste it back here *********

// put all import statements at the top, else it will give compiler error
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

interface RateOrderObserver{
  void update(Order order);
}

interface RateOrderSubject{
    void addObserver(RateOrderObserver observer);
    void notifyAll(Order order);
}

/**
 * - All the methods of this class will be tested in a MULTI-THREADED environment.
 * - If you are creating new interfaces, then they have to be declared on the top, even before class Solution, else it will give class not found error for classes implementing them.
 * - use helper.print("") or helper.println("") for printing logs else logs will not be visible.
 */
public class Solution implements Q05RestaurantRatingInterface {
    private Helper05 helper;
    private  RestaurantSearchManager restaurantSearchManager;
    private OrdersManager ordersManager;

    // constructor must always be public, don't remove the public keyword
    public Solution(){}

    /**
     * Use this method to initialize your instance variables
     * @param helper
     */
    public void init(Helper05 helper){
        this.helper=helper;
        ordersManager = new OrdersManager();
        restaurantSearchManager = new RestaurantSearchManager(ordersManager);
        // helper.println("restaurant rating module initialized");
    }

    /**
     * Orders food item from a restaurant.
     * - for now lets assume for that only a single food item is purchased in one order.
     * - orderId, restaurantId, foodItemId will all be valid and available.
     * @param restaurantId restaurant from where food is being ordered.
     * @param foodItemId food item which is being ordered
     */
    public void orderFood(String orderId, String restaurantId, String foodItemId) {
        ordersManager.orderFood(orderId,restaurantId, foodItemId);
    }

    /**
     * Customers can rate their order.
     * - when you are giving rating an order e.g giving 4 stars to an orders then it means you are assigning 4 stars to both the food item in that restaurant as well as 4 stars to the overall restaurant ranting.
     * @param orderId order which will be rated by customer, orderId will always be valid i.e. order will always be created for an orderId before rateOrder() is called.
     * @param rating ranges from 1 to 5 stars in increasing order, 1 being the worst and 5 being the best rating.
     */
    public void rateOrder(String orderId, int rating) {
        ordersManager.rateOrder(orderId, rating);
    }

    /**
     * - Fetches a list of top 20 restaurants based on strategy
     * - unrated restaurants will be at the bottom of list.
     * - restaurants will be sorted on the basis of strategy
     * - restaurants are sorted in descending order on average ratings of the food item and then based on restaurant id lexicographically
     * - e.g. veg burger is rated 4.3 in restaurant-4 and 4.6 in restaurant-6 then we will return ['restaurant-6', 'restaurant-4']
     * @param foodItemId food item for which restaurants need to be fetched.
     * - lets assume that in all below examples in strategy we are talking about 'food-item-1': Veg Burger
     */
    public List<String> getTopRestaurantsByFood(String foodItemId) {
        List<String> list = restaurantSearchManager.getRestaurantsByFood(foodItemId,  20);
        return list;//.subList(1, list.size());
    }

    /**
     * - returns top 20 most rated restaurants ids sorted in descending order of their ratings.
     * - ratings are rounded down to 1 decimal point, i.e. 4.05, 4.08, 4.11, 4.12,4.14 all become 4.1,
     *  4.15, 4.19,4.22,4.24 all become 4.2
     * - if two restaurants have the same rating then they will be ordered lexicographically by their restaurantId.
     * - Here we are talking about restaurant's overall rating and NOT food item's rating.
     * - e.g. restaurant-2 is rated 4.6 while restaurant-3 is rated 4.2 and restaurant-5 is rated 4.4  and restaurant-6 is rated 4.6, we will return ['restaurant-2','restaurant-6', 'restaurant-5', 'restaurant-3']
     * - even though restaurant-2 and restaurant-6 have same rating , restaurant-6 came later because it is lexicographically greater than restaurant-2
     */
    public List<String> getTopRatedRestaurants() {
        List<String> list = restaurantSearchManager.getTopRatedRestaurants(20);
        return list;//.subList(1, list.size());
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

     RestaurantSearchManager(RateOrderSubject rateOrderSubject){
        mostRatedRestaurants = new MostRatedRestaurants();
        mostRatedRestaurantsByFood= new MostRatedRestaurantsByFood();
        rateOrderSubject.addObserver(mostRatedRestaurants);
        rateOrderSubject.addObserver(mostRatedRestaurantsByFood);
     }

    /** n is number of restaurant ids */
    public List<String> getRestaurantsByFood(String foodItemId, int n) {
        List<String> list = mostRatedRestaurantsByFood.getRestaurants(foodItemId, n);
        StringBuffer sb = new StringBuffer();
       // for(String s:list)sb.append("("+s+" : "+mostRatedRestaurantsByFood.getRating(foodItemId, s)+"), ");
       // System.out.println("from solution.java getRestaurantsByFood for "+foodItemId+" \n "+sb);
        return list;
    }

    public List<String> getTopRatedRestaurants(int n) {
        List<String> list = mostRatedRestaurants.getRestaurants(n);
        return list;
    }
}

class MostRatedRestaurants implements RateOrderObserver{
    // restaurantId vs rating
    private HashMap<String, Rating> ratingsMap = new HashMap<>();
    // to allow multiple threads to read and update simultaneously
    private ArrayList<HashSet<String>> ratingDivisons = new ArrayList<>();

    MostRatedRestaurants(){
        for(int i=0;i<42;i++)ratingDivisons.add(new HashSet<>());
    }

    // uses synchronize to write
    public  void update(Order order) {
        ratingsMap.putIfAbsent(order.getRestaurantId(), new Rating(0,0));
        Rating rating = ratingsMap.get(order.getRestaurantId());
        if(rating.getAverageRating()>=1.0) {
            HashSet<String> remove = ratingDivisons.get(getDivisonKey(rating.getAverageRating()));
           // synchronized (remove) {
                remove.remove(order.getRestaurantId());
           // }
        }
        rating.add(order.getRating());
        HashSet<String> toAdd = ratingDivisons.get(getDivisonKey(rating.getAverageRating()));
       // synchronized (toAdd){
            toAdd.add(order.getRestaurantId());
       // }
        ratingsMap.put(order.getRestaurantId(), rating);
        //System.out.println("Solution.java : order : "+order+" rating "+rating+", divison key "+getDivisonKey(rating.getAverageRating()));
       // System.out.println("from solution.java "+order.getRestaurantId()+" "+rating.getAverageRating());
    }

   private int getDivisonKey(double rating){
        return (int)(rating*10-10);
    }

    /** n is the number of top restaurants it returns, if we don't  */
     List<String> getRestaurants(int n){
        ArrayList<String> restaurants = new ArrayList<>();
        for(int i=ratingDivisons.size()-1;i>=0 && restaurants.size()<n;i--){
            HashSet<String> set = ratingDivisons.get(i);
            if(set.size()==0) continue;
            ArrayList<String> keys = new ArrayList<String>(set);
            Collections.sort(keys);
            if(restaurants.size()+set.size()<=n){
                restaurants.addAll(keys);
                continue;
            }
            for(String restaurantId :keys){
                restaurants.add(restaurantId);
                if(restaurants.size()>=n) break;
            }
        }
        return restaurants;
    }
}

class MostRatedRestaurantsByFood implements RateOrderObserver{
    // food ids vs restaurantId and rating
    private HashMap<String, SortedSetWithLock> map = new HashMap<>();

    double getRating(String foodId, String restaurantId){
       return map.get(foodId).get(restaurantId).getAverageRating();
    }
    
    public void update(Order order) {
        map.putIfAbsent(order.getFoodItemId(), new SortedSetWithLock());
        SortedSetWithLock set = map.get(order.getFoodItemId());
            Rating newRating = set.get(order.getRestaurantId());
            newRating.add(order.getRating());
            set.update(order.getRestaurantId(), newRating);
     //   System.out.println("solution.java update rating : foodId "+order.getFoodItemId()+", restaurantId "+order.getRestaurantId()+", rating to add "+order.getRating()+", new rating :  "+set.get(order.getRestaurantId()));
    }

    List<String> getRestaurants(String foodItemId, int n){
        SortedSetWithLock set = map.get(foodItemId);
        if(set==null) return new ArrayList<>();
        return set.getIds(n);
    }
}

/**
 * natural order is sort by rating in descending order and then with id lexicographically
 */
class SortedSetWithLock{
    private ConcurrentHashMap<String, Rating> ratings = new ConcurrentHashMap<>();
 /*   private TreeSet<String> sorted = new TreeSet<String>((a,b)->{
        double ratingA = ratings.getOrDefault(a, new Rating(0,0)).getAverageRating();
        double ratingB = ratings.getOrDefault(b, new Rating(0,0)).getAverageRating();
        if(ratingA!=ratingB) return ratingA>ratingB?-1:1;
        return a.compareTo(b);
    }); */

   synchronized void update(String id, Rating newValue){
        ratings.put(id, newValue);
      /*  if(ratings.containsKey(id)) {
            synchronized (sorted) {
                sorted.remove(id);
            }
        }
        ratings.put(id, newValue);
        synchronized (sorted){
            sorted.add(id);
        }*/
    }

    /** returns copy of the value not the actual rating or new Rating(0,0) */
    Rating get(String id){
        return ratings.getOrDefault(id, new Rating(0,0)).copy();
    }

   synchronized List<String> getIds(int n){
        ArrayList<String> ids = new ArrayList<>(ratings.keySet());
        ids.sort((a,b)->{
            double ratingA = ratings.get(a).getAverageRating();
            double ratingB = ratings.get(b).getAverageRating();
            if(ratingA!=ratingB) return ratingA>ratingB?-1:1;
            return a.compareTo(b);
        });
       /* synchronized (sorted) {
            for (String id : sorted) {
                ids.add(id);
                if (ids.size() >= n) break;
            }
        } */
        if(ids.size()>n)return ids.subList(0,n);
        return ids;
    }
 }

class Rating{
    private AtomicInteger sum=new AtomicInteger(0), count=new AtomicInteger(0);
     Rating(){}
     Rating(int sum, int count){
         this.sum.set(sum);
         this.count.set(count);
     }
     public String toString(){
         return "sum "+sum+", count "+count+", avg "+getAverageRating();
     }

    // rating is rounded down to one decomal point
    double getAverageRating(){
        if(count.get()<=0) return 0;
        double rating = sum.doubleValue()/count.doubleValue();
      //  System.out.println("from Solution.java sum = "+sum+", count ="+count+", rating = "+rating);
        rating = (double)((int)((rating+0.05)*10))/10.0;
      //  System.out.println("from Solution.java sum = "+sum+", count ="+count+", rating = "+rating);
        return rating;
    }

    void add(int num){
        this.sum.addAndGet(num);
        this.count.addAndGet(1);
    }
    Rating copy(){
        return new Rating(this.sum.get(), this.count.get());
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

    public String toString(){
        return "("+orderId+", "+foodItemId+", "+restaurantId+", "+rating+")";
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

}

// uncomment below code in case you are using your local ide like intellij, eclipse etc and
// comment it back again back when you are pasting completed solution in the online CodeZym editor.
// if you don't comment it back, you will get "java.lang.AssertionError: java.lang.LinkageError"
// This will help avoid unwanted compilation errors and get method autocomplete in your local code editor.
/**
interface Q05RestaurantRatingInterface {
    void init(Helper05 helper);
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