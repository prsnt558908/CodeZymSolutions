

# Design a Shopping Cart: Java Editorial Solution

#### Problem Statement

[https://codezym.com/question/41-design-shopping-cart](https://codezym.com/question/41-design-shopping-cart)


A shopping cart is really just two lookup tables talking to each other. One table is the catalog, holding the price and remaining stock of every item. The other is the cart itself, holding what the current user has picked up so far.

Once you see it that way, the whole problem gets small. Adding an item is a couple of lookups and a comparison. Viewing the cart is reading the second table back in sorted order. Checking out is one pass over the cart to total the bill and update the stock.

This is a bookkeeping problem, not a behavioral one, so it does not need a design pattern sitting on top of it. The real design decision here is picking the right data structure so that every lookup is instant, instead of scanning through a list each time.

## Why not a design pattern

Two patterns might sound tempting for something named "design a shopping cart".

A Repository pattern to hide how the catalog is stored, and a Strategy pattern to make the validation rules swappable. Both solve a problem this version of the cart does not actually have. There is exactly one place the data lives, an in-memory map, and exactly one validation rule, check the item exists, then check the stock. Wrapping either of those in a pattern adds extra classes and indirection with nothing to swap in return.

Plain classes built around hash maps are the more optimal choice here. They are shorter, easier to read, and just as easy to test as a pattern-based version would be.

## What the cart needs to do

The constructor is handed a catalog, a list of rows shaped like `itemId,pricePerUnit,unitsAvailable`.

From there, three operations are supported.

Adding an item checks two things, in order. Does the item exist in the catalog, and is there enough stock left for the requested count. An unknown item returns `UNAVAILABLE`. Not enough stock returns `OUT OF STOCK`. Otherwise the item is added to the cart, or its count is increased if it was already there, and the call returns `SUCCESS`.

Viewing the cart returns every item currently in it as `itemId,count` rows, sorted by item id.

Checking out adds up the cost of everything in the cart, empties the cart, and reduces the stock of whatever was bought. That reduced stock carries forward, it is not reset later. If the cart is empty, checkout returns `-1` instead of a total.

## Approach 1: Brute Force with Lists

The most direct way to model this is with plain lists. Keep the catalog as a list of items, and the cart as a list of id and count pairs, in whatever order they were added.

To add an item, scan the catalog list until the id matches, then scan the cart list to see if it is already there. To view the cart, copy the cart list, sort the copy by item id, and format each row. To check out, walk the cart list, and for every entry scan the catalog again to find its price and reduce its stock.

This is easy to follow because it does exactly what the problem describes, one step at a time. It is also correct, it passes every sample test.

The problem is speed. `addItem` scans the whole catalog, and possibly the whole cart, on every call, so it costs O(n + m), where n is the catalog size and m is the cart size. `checkout` is worse, it scans the catalog once for every item in the cart, so it costs O(m times n). None of this matters for a handful of items, but it stops scaling once the catalog or the cart grows.

```java
import java.util.*;

/**
 * Brute force version.
 * Catalog and cart are both plain lists, and every lookup is a linear scan.
 * Simple to read, but slower as the catalog or cart grows.
 */
public class ShoppingCart {

    // One row from the catalog: an item's id, its price, and units left in stock.
    private static class CatalogItem {
        String itemId;
        int price;
        int stock;
        CatalogItem(String itemId, int price, int stock) {
            this.itemId = itemId;
            this.price = price;
            this.stock = stock;
        }
    }

    // One row in the cart: an item id and how many units of it were added.
    private static class CartItem {
        String itemId;
        int count;
        CartItem(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    private final List<CatalogItem> catalog;
    private final List<CartItem> cart;

    public ShoppingCart(List<String> items) {
        catalog = new ArrayList<>();
        for (String row : items) {
            String[] parts = row.split(",");
            catalog.add(new CatalogItem(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        cart = new ArrayList<>();
    }

    public String addItem(String itemId, int count) {
        // Scan the catalog for this item.
        CatalogItem catalogItem = null;
        for (CatalogItem c : catalog) {
            if (c.itemId.equals(itemId)) {
                catalogItem = c;
                break;
            }
        }
        if (catalogItem == null) {
            return "UNAVAILABLE";
        }

        // Scan the cart to see if this item is already in it.
        CartItem cartItem = null;
        for (CartItem c : cart) {
            if (c.itemId.equals(itemId)) {
                cartItem = c;
                break;
            }
        }
        int alreadyInCart = (cartItem == null) ? 0 : cartItem.count;

        if (alreadyInCart + count > catalogItem.stock) {
            return "OUT OF STOCK";
        }

        if (cartItem == null) {
            cart.add(new CartItem(itemId, count));
        } else {
            cartItem.count += count;
        }
        return "SUCCESS";
    }

    public List<String> viewCart() {
        List<CartItem> sorted = new ArrayList<>(cart);
        sorted.sort((a, b) -> a.itemId.compareTo(b.itemId));

        List<String> result = new ArrayList<>();
        for (CartItem c : sorted) {
            result.add(c.itemId + "," + c.count);
        }
        return result;
    }

    public int checkout() {
        if (cart.isEmpty()) {
            return -1;
        }

        int total = 0;
        for (CartItem c : cart) {
            for (CatalogItem catalogItem : catalog) {
                if (catalogItem.itemId.equals(c.itemId)) {
                    total += catalogItem.price * c.count;
                    catalogItem.stock -= c.count;
                    break;
                }
            }
        }
        cart.clear();
        return total;
    }
}
```

`CatalogItem` and `CartItem` are small classes that just group related fields together, an id with its price and stock, or an id with its count, so the code is not juggling separate parallel lists for id, price, and stock.

## Approach 2: Optimized with Hash Maps

The slow part of the brute force version is always the same, scanning a list to find one entry by its id. A hash map does exactly that lookup in constant time, with no scanning at all.

So the catalog becomes a map from itemId to an `Item` object holding its price and remaining stock. The cart becomes a map from itemId to how many units of it are in the cart. Checking if an item exists, checking how many are already in the cart, and updating that count all become single map operations instead of scans.

The one place that still needs sorted order is `viewCart`, since the rows must come back sorted by item id. A hash map keeps no order at all, so the fix is simple, copy its keys into a plain list and sort that list. This only costs time proportional to the number of items actually in the cart, not the whole catalog, and it gives sorted output without reaching for a `TreeMap`. A regular list plus one sort call does the same job and is easier to reason about.

```java
import java.util.*;

/**
 * Optimized version.
 * The catalog and the cart are both hash maps keyed by itemId, so every
 * lookup or update is O(1). Sorting only happens when viewCart() is
 * actually called, and only over the (usually small) set of items in the
 * cart, not the whole catalog.
 */
public class ShoppingCart {

    // Bundles what we need to know about one catalog item: its price
    // (fixed) and how many units are still left to sell (changes on checkout).
    private static class Item {
        final int price;
        int stock;
        Item(int price, int stock) {
            this.price = price;
            this.stock = stock;
        }
    }

    private final Map<String, Item> catalog;   // itemId -> price & remaining stock
    private final Map<String, Integer> cart;   // itemId -> units added to the cart

    public ShoppingCart(List<String> items) {
        catalog = new HashMap<>();
        for (String row : items) {
            String[] parts = row.split(",");
            String itemId = parts[0];
            int price = Integer.parseInt(parts[1]);
            int stock = Integer.parseInt(parts[2]);
            catalog.put(itemId, new Item(price, stock));
        }
        cart = new HashMap<>();
    }

    public String addItem(String itemId, int count) {
        Item item = catalog.get(itemId);
        if (item == null) {
            return "UNAVAILABLE";
        }

        int alreadyInCart = cart.getOrDefault(itemId, 0);
        if (alreadyInCart + count > item.stock) {
            return "OUT OF STOCK";
        }

        cart.put(itemId, alreadyInCart + count);
        return "SUCCESS";
    }

    public List<String> viewCart() {
        // Cart keys come out of the map in no particular order, so we
        // copy them into a list and sort just that small list.
        List<String> itemIds = new ArrayList<>(cart.keySet());
        Collections.sort(itemIds);

        List<String> result = new ArrayList<>();
        for (String itemId : itemIds) {
            result.add(itemId + "," + cart.get(itemId));
        }
        return result;
    }

    public int checkout() {
        if (cart.isEmpty()) {
            return -1;
        }

        int total = 0;
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            Item item = catalog.get(entry.getKey());
            int count = entry.getValue();
            total += item.price * count;
            item.stock -= count;   // stock reduction carries over to future orders
        }

        cart.clear();
        return total;
    }
}
```

`Item` bundles price and stock together so one map lookup gives back both, instead of keeping two separate maps that would need to stay in sync with each other.

`catalog` is a `HashMap` because items are only ever looked up by id, never by price or by stock. `cart` is a `HashMap` for the same reason, and clearing it on checkout is a single call.

### Time and space complexity

`addItem` is O(1) on average, a couple of map lookups and one update.

`viewCart` is O(k log k), where k is the number of distinct items in the cart, because of the sort. This is the only place sorting happens, and it is bounded by the cart, not the catalog.

`checkout` is O(k), one pass over the cart's entries.

Space use is O(n + k), where n is the number of items in the catalog and k is the number of items currently in the cart.


## Summary

A shopping cart like this does not need a design pattern, it needs two good lookups. Moving from lists to hash maps turns the same logic from a series of scans into constant-time operations, with sorting kept only where it is actually required, inside `viewCart`.