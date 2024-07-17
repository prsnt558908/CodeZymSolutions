// ****** It's better to write code in your local code editor and paste it back here *********

import java.util.*;


public class Solution implements Q05RestaurantRatingInterface {
    private Helper05 helper;

    public Solution(){}

    public void init(Helper05 helper){
        this.helper=helper;
        // helper.println("restaurant rating module initialized");
    }

    public void orderFood(String orderId, String restaurantId, String foodItemId) {

    }

    /**
     * when you(customer) are rating an order e.g giving 4 stars to an orders
     * then it means you are assigning 4 stars to both the food item
     * in that restaurant as well as 4 stars to the overall restaurant rating.
     * - rating ranges from 1 to 5, 5 is best, 1 is worst
     */
    public void rateOrder(String orderId, int rating) {

    }

    /**
     * - Fetches a list of top 20 restaurants
     * - unrated restaurants will be at the bottom of list.
     * - restaurants are sorted in descending order on average ratings
     * of the food item and then based on restaurant id lexicographically
     * - ratings are rounded down to 1 decimal point,
     *  i.e. 4.05, 4.08, 4.11, 4.12, 4.14 all become 4.1,
     *    4.15, 4.19, 4.22, 4.24 all become 4.2
     * - e.g. 'food-item-1':  veg burger is rated 4.3 in restaurant-4
     * and 4.6 in restaurant-6 then we will return ['restaurant-6', 'restaurant-4']
     */
    public List<String> getTopRestaurantsByFood(String foodItemId) {
        return new ArrayList<String>();
    }

    /**
     * - Here we are talking about restaurant's overall rating and NOT food item's rating.
     */
    public List<String> getTopRatedRestaurants() {
        return new ArrayList<String>();
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