# Key-Value Store with O(1) Operations in Java

#### Problem Statement
[https://codezym.com/question/212-key-value-store-o1-insert-delete-get-first](https://codezym.com/question/212-key-value-store-o1-insert-delete-get-first)

Use a hash map to find entries by key and a doubly linked list to keep them in insertion order. The map points directly to each entry's node, so we can update or delete it without searching through the list. The two ends give us the first and last active entries.


## 1. Start with a simple list

We could store key-value pairs in a list. New entries go at the end, so insertion order is easy to keep.


But `get`, `delete`, and updating an existing key may require searching the whole list. With `n` active entries, that takes O(n) time.


We need a way to find the right entry directly.


## 2. Combine a map with a doubly linked list

The map stores `key -> node`. Each node stores its key, value, previous node, and next node.


The map finds a node in average O(1) time. Once we have that node, its two links let us remove it by connecting its neighbors. An ordinary linked list without a map would still need a search to find the node.


We use two dummy nodes, `head` and `tail`. They mark the ends and never hold user entries. The first real entry is `head.next`, and the last is `tail.prev`.


An empty list has `head.next == tail`. These dummy nodes let the same insertion and deletion code work for the first entry, last entry, and only entry.


`EntryNode` holds one entry and its links. `KeyValueStore` owns the map and list, and provides the required methods.


## 3. Handle each operation

**`put(key, value)`**: If the key exists, update only its value. Keep its node in the same position. Otherwise, create a node, add it just before `tail`, and add it to the map.


**`get(key)`**: Find the node in the map and return its value. Return `""` when the key is missing. Reading an entry does not move it.


**`delete(key)`**: Remove the node from the map, connect its neighbors, and return its value. Return `""` when the key is missing.


**`getFirstEntry()` and `getLastEntry()`**: Read the node beside the corresponding dummy node and return `"key-value"`. Return `""` when the list is empty. These methods do not remove entries.


The helpers `addLast` and `unlink` keep the link changes in one place. Deleting a key removes its node completely, so inserting that key later creates a new node at the end.


## 4. Walk through an example

1. Call `put("a", "apple")` and `put("b", "banana")`. The order is `a, b`.
2. Call `put("a", "apricot")`. The order stays `a, b`, and `getFirstEntry()` returns `"a-apricot"`.
3. Call `delete("a")`. It returns `"apricot"`, leaving only `b`.
4. Call `put("a", "avocado")`. The order becomes `b, a`.
5. Now `getFirstEntry()` returns `"b-banana"`, and `getLastEntry()` returns `"a-avocado"`.


## 5. Why this works

Every active key has exactly one node in the list and one matching entry in the map.


New nodes are always added at the end. Updates keep their nodes in place. Deletion removes only the chosen node and keeps the order of every remaining node.


Therefore, the list always follows the insertion order of active entries. Its first and last real nodes are exactly the entries the two endpoint methods must return.


## 6. Complexity

With `n` active entries, `put`, `get`, and `delete` take average O(1) time. Insertion is amortized O(1), since the hash map may occasionally resize.


The first and last entry methods inspect one node. Building the returned string takes O(k + v) time for key length `k` and value length `v`. Hashing keys also depends on their length. Both lengths are limited to 100, so all methods meet the required average O(1) time with respect to `n`.


The map and list use O(n) space.


## 7. Java Code

```java
import java.util.HashMap;
import java.util.Map;

class EntryNode {
    String key;
    String value;
    EntryNode prev;
    EntryNode next;

    EntryNode(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

public class KeyValueStore {
    Map<String, EntryNode> entries;
    EntryNode head;
    EntryNode tail;

    public KeyValueStore() {
        entries = new HashMap<>();

        // Dummy nodes mark the ends of the list.
        head = new EntryNode("", "");
        tail = new EntryNode("", "");
        head.next = tail;
        tail.prev = head;
    }

    public void put(String key, String value) {
        EntryNode node = entries.get(key);

        if (node != null) {
            // Updating a value must not change insertion order.
            node.value = value;
            return;
        }

        node = new EntryNode(key, value);
        addLast(node);
        entries.put(key, node);
    }

    public String get(String key) {
        EntryNode node = entries.get(key);
        return node == null ? "" : node.value;
    }

    public String delete(String key) {
        EntryNode node = entries.remove(key);

        if (node == null) {
            return "";
        }

        unlink(node);
        return node.value;
    }

    public String getFirstEntry() {
        if (head.next == tail) {
            return "";
        }

        EntryNode node = head.next;
        return node.key + "-" + node.value;
    }

    public String getLastEntry() {
        if (head.next == tail) {
            return "";
        }

        EntryNode node = tail.prev;
        return node.key + "-" + node.value;
    }

    void addLast(EntryNode node) {
        // Insert between the current last node and the tail.
        EntryNode previous = tail.prev;
        previous.next = node;
        node.prev = previous;
        node.next = tail;
        tail.prev = node;
    }

    void unlink(EntryNode node) {
        // Join the neighbors, then clear the removed node's links.
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }
}
```
