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