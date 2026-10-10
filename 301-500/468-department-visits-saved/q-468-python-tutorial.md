# Department Visits Saved for a Shopping List in Python

#### Problem Statement

[https://codezym.com/question/468-department-visits-saved](https://codezym.com/question/468-department-visits-saved)

Count the first item as one department visit. After that, count another visit whenever the department changes. Subtract the number of distinct departments on the shopping list to find the visits saved. A dictionary makes product lookups fast, and a set counts each department once.


## Start with a simple approach

For each item on the shopping list, search through `products` to find its department. Compare it with the previous item's department to count visits. Also add the department to a set.

This works, but finding a department can scan the entire catalog each time. With `P` products and `S` shopping items, it takes up to `O(P * S)` time.

We can avoid these repeated searches by building a dictionary once.


## Use a dictionary and a set

The supplied `DepartmentVisitsSaved` class only needs one method. It does not need to keep any data between calls.

Inside the method, use these values:

- `department_by_product` maps a product name to its department. It avoids searching the catalog for every item.
- `departments_used` is a set of departments found on the shopping list. Its size is the number of visits needed when shopping one department at a time.
- `previous_department` remembers the previous item's department. `visits_in_order` counts visits in the original order.

First, split each catalog entry at its comma and fill the dictionary. For example, `"Ice Cream,Frozen Foods"` becomes the key `"Ice Cream"` and the value `"Frozen Foods"`.

Next, scan the shopping list. Look up each item's department. Increase `visits_in_order` if this is the first item or its department differs from `previous_department`.

Add the department to the set, then update `previous_department`.

Finally, return `visits_in_order - len(departments_used)`.

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

The `!=` operator compares department names by their contents. The first department also differs from the initial `None`, so the first visit is counted without a separate condition.

Keep spaces and letter case unchanged. Names are case-sensitive, so `Dairy` and `dairy` are different departments.

Add departments to the set only while scanning the shopping list. A department that appears only in the catalog should not affect the answer.

One item, one department, or an already grouped shopping list all give `0` visits saved.


## Complexity

Let `P` be the number of products, `S` the number of shopping items, and `D` the number of distinct departments on the shopping list.

Expected time is `O(P + S)`, using average constant-time dictionary and set operations. Product and department names have bounded lengths.

Extra space is `O(P + D)` for the dictionary and set. This is also `O(P)` because `D <= S <= P`.


## Python code

```python
class DepartmentVisitsSaved:
    def __init__(self):
        pass

    def departmentVisitsSaved(self, products, shoppingList):
        """Return the visits saved by shopping one department at a time."""
        department_by_product = {}

        # Build the lookup once so each shopping item is easy to find.
        for product in products:
            product_name, department = product.split(",", 1)
            department_by_product[product_name] = department

        departments_used = set()
        previous_department = None
        visits_in_order = 0

        for product in shoppingList:
            department = department_by_product[product]

            # The first item and every department change start a visit.
            if department != previous_department:
                visits_in_order += 1

            # Count only departments needed by this shopping list.
            departments_used.add(department)
            previous_department = department

        return visits_in_order - len(departments_used)
```
