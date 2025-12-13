import java.util.*;

public class ShoppingCart {

    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final String OUT_OF_STOCK = "OUT OF STOCK";
    private static final String SUCCESS = "SUCCESS";

    // Internal representation of a catalog item
    private static class Item {
        int pricePerUnit;
        int unitsAvailable; // remaining stock

        Item(int pricePerUnit, int unitsAvailable) {
            this.pricePerUnit = pricePerUnit;
            this.unitsAvailable = unitsAvailable;
        }
    }

    // Catalog: itemId -> Item (price + remaining stock)
    private final Map<String, Item> catalog;
    // Cart: itemId -> units in cart
    private final Map<String, Integer> cart;

    public ShoppingCart(List<String> items) {
        this.catalog = new HashMap<>();
        this.cart = new HashMap<>();

        if (items != null) {
            for (String row : items) {
                if (row == null) {
                    continue;
                }
                row = row.trim();
                if (row.isEmpty()) {
                    continue;
                }

                // Each row: itemId,pricePerUnit,unitsAvailable
                String[] parts = row.split(",");
                if (parts.length != 3) {
                    // Problem statement guarantees valid input format,
                    // so we can safely skip invalid rows if any.
                    continue;
                }

                String itemId = parts[0];
                int price = Integer.parseInt(parts[1]);
                int units = Integer.parseInt(parts[2]);

                catalog.put(itemId, new Item(price, units));
            }
        }
    }

    public String addItem(String itemId, int count) {
        Item item = catalog.get(itemId);
        if (item == null) {
            // Unknown item ID
            return UNAVAILABLE;
        }

        // Check remaining stock
        if (item.unitsAvailable < count) {
            return OUT_OF_STOCK;
        }

        // Reserve stock by decreasing available units
        item.unitsAvailable -= count;

        // Add to cart (accumulate if already present)
        int existing = cart.getOrDefault(itemId, 0);
        cart.put(itemId, existing + count);

        return SUCCESS;
    }

    public List<String> viewCart() {
        List<String> result = new ArrayList<>();
        if (cart.isEmpty()) {
            return result; // empty list
        }

        // Sort itemIds lexicographically
        List<String> itemIds = new ArrayList<>(cart.keySet());
        Collections.sort(itemIds);

        for (String itemId : itemIds) {
            int count = cart.get(itemId);
            result.add(itemId + "," + count);
        }

        return result;
    }

    public int checkout() {
        if (cart.isEmpty()) {
            return -1; // empty-cart checkout
        }

        int total = 0;
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue();

            Item item = catalog.get(itemId);
            if (item != null) {
                total += item.pricePerUnit * count;
            }
        }

        // Clear cart after checkout
        cart.clear();

        return total;
    }
}
