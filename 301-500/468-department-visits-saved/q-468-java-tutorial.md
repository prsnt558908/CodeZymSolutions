# Department Visits Saved for a Shopping List in Java

#### Problem Statement

[https://codezym.com/question/468-department-visits-saved](https://codezym.com/question/468-department-visits-saved)

Count the first item as one department visit. After that, count another visit whenever the department changes. Subtract the number of distinct departments on the shopping list to find the visits saved. A map makes product lookups fast, and a set counts each department once.


## Start with a simple approach

For each item on the shopping list, search through `products` to find its department. Compare it with the previous item's department to count visits. Also add the department to a set.

This works, but finding a department can scan the entire catalog each time. With `P` products and `S` shopping items, it takes up to `O(P * S)` time.

We can avoid these repeated searches by building a map once.


## Use a map and a set

The supplied `DepartmentVisitsSaved` class only needs one method. It does not need to keep any data between calls.

Inside the method, use these values:

- `departmentByProduct` maps a product name to its department. It avoids searching the catalog for every item.
- `departmentsUsed` is a set of departments found on the shopping list. Its size is the number of visits needed when shopping one department at a time.
- `previousDepartment` remembers the previous item's department. `visitsInOrder` counts visits in the original order.

First, split each catalog entry at its comma and fill the map. For example, `"Ice Cream,Frozen Foods"` becomes the key `"Ice Cream"` and the value `"Frozen Foods"`.

Next, scan the shopping list. Look up each item's department. Increase `visitsInOrder` if this is the first item or its department differs from `previousDepartment`.

Add the department to the set, then update `previousDepartment`.

Finally, return `visitsInOrder - departmentsUsed.size()`.

There is no need to sort the list or build a new shopping order. We only need the two visit counts.


## Walk through an example

Consider the shopping list `Hot Dog Buns, Sliced Ham, Turkey, Peanut Butter, Muffins` from the third example.

Its departments are `Bakery, Deli, Deli, Pantry, Bakery`.

- `Hot Dog Buns` starts the first visit, to Bakery.
- `Sliced Ham` starts the second visit, to Deli.
- `Turkey` is also in Deli, so the count stays at two.
- `Peanut Butter` starts the third visit, to Pantry.
- `Muffins` starts the fourth visit, because we return to Bakery.

The set contains Bakery, Deli, and Pantry. Shopping one department at a time needs only three visits.

The answer is `4 - 3 = 1`.


## Why this works

In the original order, each consecutive group of items from the same department is exactly one visit. The code counts the first item and every department change, so it counts these groups correctly.

When shopping one department at a time, each distinct department on the list needs exactly one visit. The set contains precisely those departments.

Subtracting the second count from the first gives the visits saved.


## Details to get right

Use `equals` to compare department names. In Java, `==` does not compare string contents. Calling `department.equals(previousDepartment)` also works when `previousDepartment` is initially `null`, because every shopping item has a valid department.

Keep spaces and letter case unchanged. Names are case-sensitive, so `Dairy` and `dairy` are different departments.

Add departments to the set only while scanning the shopping list. A department that appears only in the catalog should not affect the answer.

One item, one department, or an already grouped shopping list all give `0` visits saved.


## Complexity

Let `P` be the number of products, `S` the number of shopping items, and `D` the number of distinct departments on the shopping list.

Expected time is `O(P + S)`, using average constant-time map and set operations. Product and department names have bounded lengths.

Extra space is `O(P + D)` for the map and set. This is also `O(P)` because `D <= S <= P`.


## Java code

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DepartmentVisitsSaved {

    public DepartmentVisitsSaved() {
    }

    /**
     * Returns the visits saved by shopping one department at a time.
     */
    public int departmentVisitsSaved(List<String> products, List<String> shoppingList) {
        Map<String, String> departmentByProduct = new HashMap<>();

        // Build the lookup once so each shopping item is easy to find.
        for (String product : products) {
            String[] parts = product.split(",", 2);
            departmentByProduct.put(parts[0], parts[1]);
        }

        Set<String> departmentsUsed = new HashSet<>();
        String previousDepartment = null;
        int visitsInOrder = 0;

        for (String product : shoppingList) {
            String department = departmentByProduct.get(product);

            // The first item and every department change start a visit.
            if (!department.equals(previousDepartment)) {
                visitsInOrder++;
            }

            // Count only departments needed by this shopping list.
            departmentsUsed.add(department);
            previousDepartment = department;
        }

        return visitsInOrder - departmentsUsed.size();
    }
}
```
