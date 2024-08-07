// ****** It's better to write code in your local code editor and paste it back here *********

// put all import statements at the top, else it will give compiler error
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
