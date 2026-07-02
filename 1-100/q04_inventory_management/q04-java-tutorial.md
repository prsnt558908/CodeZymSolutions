# Understanding Multi-Threading in Java through design of an Order and Inventory Management System for Low Level Design Interviews

## Problem Statement

https://codezym.com/question/4-design-order-and-inventory-system

## Language

java

## Video Explanation

[![Understanding Multi-Threading in Java through design of an Order and Inventory Management System](https://img.youtube.com/vi/VtBL_NNa7hs/hqdefault.jpg)](https://www.youtube.com/watch?v=VtBL_NNa7hs)

YouTube Video : https://www.youtube.com/watch?v=VtBL_NNa7hs

Design of order and inventory management system is a common occurrence in low level design rounds of Amazon, Flipkart, Walmart and other e-commerce companies.

In a typical e-commerce website, different sellers sell different products.

Sellers store multiple types of products in their warehouse.

Inventory is the number of items of a particular product in a seller’s warehouse.

When customers make orders then products from seller’s warehouse are shipped to them thereby reducing inventory.


## Here is what we are going to do

We will discuss the requirements

Then build a simple solution for a multi-threaded environment.

Finally, we will have our completed java code, and you can find its GitHub link and YouTube video explanation at the end.

Our system should have the capability of handling sellers, products and orders. Multiple sellers can sell the same product e.g. product 1, bluetooth speaker boat stone 650 can be sold by multiple sellers.

Website will have productsCount products and they will be numbered 0 to productsCount-1

## Requirements

Add new sellers in the system with a list of pincodes they deliver to and list of payment modes they support.

```java
createSeller(String sellerId, List<String> serviceablePincodes, List<String> paymentModes)
```

Each seller can sell many products and each product can be sold by multiple sellers.

paymentModes will be one of cash, UPI, netbanking, debit card and credit card.

2. A seller should be able to add multiple counts of product in their warehouse.

```java
addInventory(int productId, String sellerId, int delta)
```

delta is number of items of the product that is added. It will always be a positive integer.

3. Sellers should be able to view inventory details for each products in their warehouse.

```java
int getInventory(int productId, String sellerId)
```

returns number of items of productId.

4. Customers should be able to place orders for a particular product from a given seller.

```java
String createOrder(String orderId, String destinationPincode, String sellerId, int productId, int productCount, String paymentMode)
```

Customers should be able to get their product delivered to their address i.e. destination pincode and pay for their order.

paymentMode will be one of cash, UPI, netbanking, debit card and credit card.

Now an order and inventory management system can consist of many other functionalities like managing trucks which supply goods to seller’s warehouse, managing errors that occur while sending orders to customers like sent a blue shirt instead of a red one, managing customer returns etc.

However we will not go into all these details. Our goal is to keep the explanation simple and short enough to fit in a 45 to 60 minutes face to face interview.


Our system should work correctly in a multi-threaded environment. So we will make proper use of synchronization and thread safe data structures.

## Breaking solution in multiple classes

Let’s start with the entity classes and their managers. System will certainly include sellers, so let's start with class seller and corresponding seller’s manager.

## Class Seller

Each seller can deliver products to a set of products and customers can buy them using payment modes supported by them.

```java
class Seller {
    private HashSet<String> serviceablePincodes = new HashSet<>();
    private HashSet<String> sellerPaymentModes = new HashSet<>();

    Seller(List<String> serviceablePincodes, List<String> sellerPaymentModes){
        this.serviceablePincodes.addAll(serviceablePincodes);
        this.sellerPaymentModes.addAll(sellerPaymentModes);
    }

    boolean servesPincode(String pincode){
        return pincode!=null && serviceablePincodes.contains(pincode);
    }

    boolean supportsPaymentType(String paymentType){
        return paymentType!=null && sellerPaymentModes.contains(paymentType);
    }
}
```

## Class SellerManager

They keep collection of sellers. In real world, SellerManager will me managing database table for sellers. In this case we are storing all sellers in a map.

We used ConcurrentHashMap rather than a simple HashMap because when multiple threads try to add sellers to a HashMap, it may give concurrent modification exception.

```java
class SellerManager{
    // sellerId vs serviceable pincodes
    ConcurrentHashMap<String, Seller> sellers = new ConcurrentHashMap<>();

    public void createSeller(String sellerId,
                             List<String> serviceablePincodes, List<String> paymentModes) {
        sellers.put(sellerId, new Seller(serviceablePincodes, paymentModes));
    }

    public Seller getSeller(String sellerId){
        return sellers.get(sellerId);
    }
}
```

## Class InventoryManager

We could have also kept inventory details as a productId vs item count map inside each seller. But inventory has multiple read/write operations attached to it from multiple sources. Hence to simplify the design we will be managing inventory in a separate class InventoryManager.

We will be using a two-level map.

```java
map<productId, map<sellerId, items count>>
```

Outer map is basically storing map of all sellers who sell a particular productId. Inner map is number of items in warehouse of each seller for that particular productId.

To be more exact here is the actual data structure.

```java
// productId vs sellerId vs item count
ConcurrentHashMap<Integer,
        ConcurrentHashMap<String, AtomicInteger>> productInventory
```

## Why ConcurrentHashMap rather than a HashMap

We used ConcurrentHashMap rather than a simple HashMap because when multiple threads try to update product item count in a HashMap then it can give ConcurrentUpdateException or can corrupt item counts data.

e.g. Lets suppose product: 1, seller: seller-4, item count 8,

two threads trying to create an order of 1 item each may read the same count i.e. 8 and update the final count to 7 while it should have been 6.

We need both read and update to occur serially for any given item sold by a given seller. That’s why we need a thread safe data structure such as ConcurrentHashMap rather than a simple HashMap.

## Why use AtomicInteger to store item counts

In above data structure we also store items count in an AtomicInteger rather than an Integer. It is more efficient to update an AtomicInteger rather than a Integer in a multithreaded environment.

Has we used Integer to store items count then for each item count update we would have been doing a write operation on the internal ConcurrentHashMap. Now as we know number of concurrent writes in a ConcurrentHashMap is limited to 8, 16 .. and memory consumption of map increases if we try to increase the number of concurrent writes.

But hundreds of sellers can update their item count for the same product at the same time. So, we need number of concurrent writes to scale without increasing memory overhead.

Using AtomicInteger to store item counts means now for each item count update there is only read operation done on both outer and internal ConcurrentHashMap’s and write is done directlyto AtomicInteger.

Hence any number of threads can update item count now concurrently and memory overhead of AtomicInteger vs Integer is also negligible as compared to using Integer inside a ConcurrentHashMap.

Below is complete code for InventoryManager

It has methods to add, reduce and get inventory in a thread safe way.

```java
class InventoryManager{

    // productId vs sellerId vs productCount
    ConcurrentHashMap<Integer,
            ConcurrentHashMap<String, AtomicInteger>> productInventory
            = new ConcurrentHashMap<>();

    public void addInventory(int productId, String sellerId, int delta) {
        productInventory.putIfAbsent(productId, new ConcurrentHashMap<>());
        ConcurrentHashMap<String, AtomicInteger> sellers=
            productInventory.get(productId);
        sellers.putIfAbsent(sellerId, new AtomicInteger(0));
        AtomicInteger inventory = sellers.get(sellerId);
        inventory.addAndGet(delta);
    }

    public Boolean reduceInventory(int productId, String sellerId, int delta) {
        if(!productInventory.containsKey(productId)) return false;
        ConcurrentHashMap<String, AtomicInteger> sellers=
                productInventory.get(productId);
        AtomicInteger existingInventory = sellers.get(sellerId);
        if(existingInventory==null) return false;
        while (true) {
            int currentValue = existingInventory.get();
            if (currentValue <delta) break;
            if(existingInventory.compareAndSet(
                    currentValue, currentValue - delta))
                return true;
        }
        return false;
    }

    public int getInventory(int productId, String sellerId){
        if(!productInventory.containsKey(productId)) return 0;
        ConcurrentHashMap<String, AtomicInteger> sellers=
          productInventory.get(productId);
        AtomicInteger existingInventory = sellers.get(sellerId);
        return existingInventory==null?0:existingInventory.get();
    }
}
```

## Complete Java Code

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Solution implements Q04EcommerceOrdersInterface {
     private Helper04 helper;
     private SellerManager sellerManager = new SellerManager();
     private InventoryManager inventoryManager = new InventoryManager();
     private int productsCount; 
     public Solution(){}

    public void init(Helper04 helper, int productsCount){
        this.helper=helper;
		this.productsCount=productsCount;
        // helper.println("Ecommerce orders module initialized");
    }

    // paymentModes:  cash, upi, netbanking, debit card and credit card
    public void createSeller(String sellerId,
        List<String> serviceablePincodes, List<String> paymentModes) {
        sellerManager.createSeller(sellerId, serviceablePincodes, paymentModes);
    }

    // returns "inventory added", "product doesn't exist", "seller doesn't exist"
    public void addInventory(int productId, String sellerId, int delta) {
        if(sellerManager.getSeller(sellerId)==null) return ; //"seller doesn't exist";
     //   if(productManager.getProduct(productId)==null) return; // "product doesn't exist";
         inventoryManager.addInventory(productId, sellerId, delta);
    }

    public int getInventory(int productId, String sellerId) {
        return inventoryManager.getInventory(productId, sellerId);
    }

    // creates order and reduces inventory by productCount
    public String createOrder(String orderId, String destinationPincode,
       String sellerId, int productId, int productCount, String paymentMode) {
        Seller seller = sellerManager.getSeller(sellerId);
        if(!seller.servesPincode(destinationPincode))
            return "pincode unserviceable";
        if(!seller.supportsPaymentType(paymentMode))
            return "payment mode not supported";
        boolean reduced = inventoryManager.reduceInventory(
                productId, sellerId, productCount);
        return reduced ? "order placed": "insufficient product inventory";
    }

}

class Seller {
    private HashSet<String> serviceablePincodes = new HashSet<>();
    private HashSet<String> sellerPaymentModes = new HashSet<>();

    Seller(List<String> serviceablePincodes, List<String> sellerPaymentModes){
        this.serviceablePincodes.addAll(serviceablePincodes);
        this.sellerPaymentModes.addAll(sellerPaymentModes);
    }

    boolean servesPincode(String pincode){
        return pincode!=null && serviceablePincodes.contains(pincode);

    }

    boolean supportsPaymentType(String paymentType){
        return paymentType!=null && sellerPaymentModes.contains(paymentType);
    }
}

class SellerManager{
    // sellerId vs serviceable pincodes
    ConcurrentHashMap<String, Seller> sellers = new ConcurrentHashMap<>();

    public void createSeller(String sellerId,
                             List<String> serviceablePincodes, List<String> paymentModes) {
        sellers.put(sellerId, new Seller(serviceablePincodes, paymentModes));
    }

    public Seller getSeller(String sellerId){
        return sellers.get(sellerId);
    }
}

class InventoryManager{

    // productId vs sellerId vs productCount
    ConcurrentHashMap<Integer,
            ConcurrentHashMap<String, AtomicInteger>> productInventory
            = new ConcurrentHashMap<>();

    public void addInventory(int productId, String sellerId, int delta) {
        productInventory.putIfAbsent(productId, new ConcurrentHashMap<>());
        ConcurrentHashMap<String, AtomicInteger> sellers=productInventory.get(productId);
        sellers.putIfAbsent(sellerId, new AtomicInteger(0));
        AtomicInteger inventory = sellers.get(sellerId);
        inventory.addAndGet(delta);
    }

    public Boolean reduceInventory(int productId, String sellerId, int delta) {
        if(!productInventory.containsKey(productId)) return false;
        ConcurrentHashMap<String, AtomicInteger> sellers=productInventory.get(productId);
        AtomicInteger existingInventory = sellers.get(sellerId);
        if(existingInventory==null) return false;
        while (true) {
            int currentValue = existingInventory.get();
            if (currentValue <delta) break;
            if(existingInventory.compareAndSet(
                    currentValue, currentValue - delta))
                return true;
        }
        return false;
    }

    public int getInventory(int productId, String sellerId){
        if(!productInventory.containsKey(productId)) return 0;
        ConcurrentHashMap<String, AtomicInteger> sellers=productInventory.get(productId);
        AtomicInteger existingInventory = sellers.get(sellerId);
        return existingInventory==null?0:existingInventory.get();
    }
}

// uncomment below code in case you are using your local ide like intellij, eclipse etc and
// comment it back again back when you are pasting completed solution in the online CodeZym editor.
// This will help avoid unwanted compilation errors and get method autocomplete in your local code editor.
/**
interface Q04EcommerceOrdersInterface {
    public void init(Helper04 helper, int productsCount);

    // payment modes : cash, upi, netbanking, debit card, credit card
    void createSeller(String sellerId, List<String> serviceablePincodes, List<String> paymentModes);

    void addInventory(int productId, String sellerId, int delta);

    int getInventory(int productId, String sellerId);

    // "order placed", "pincode unserviceable", "insufficient product inventory", "payment mode not supported"
    String createOrder(String orderId, String destinationPincode, String sellerId, int productId,
                    int productCount, String paymentMode);
}

class Helper04 {
    void print(String s){System.out.print(s);}
    void println(String s){System.out.println(s);}
} */
```
