

# Category Hierarchy System in Java

#### Problem Statement

[https://codezym.com/question/457-category-hierarchy-system](https://codezym.com/question/457-category-hierarchy-system)

The main idea is to store each category with two lists, one for its products and another for its subcategories. A search walks only the selected category and its descendants. The **Strategy pattern** keeps each filter independent, while a small factory map creates the right strategy from a filter string. This combination gives us flexible filtering without adding unnecessary class hierarchies.

## 1. Start With a Simple Approach

One approach is to examine every product in the system.

For each product, we could walk upward through its category’s parents to check whether it belongs under the requested category. If it does, we apply the filters.

This works, but it wastes time examining products from unrelated categories.

If there are `P` products and the hierarchy has depth `H`, checking category membership alone could take `O(P × H)` time per search.

### How can we improve this?

Instead of starting from every product, start from the requested category.

Visit its products, then visit its children, their children, and so on.

Now we only examine the part of the hierarchy that matters.

## 2. Store the Hierarchy With Simple Collections

Each category stores only its direct children and direct products.

```text
store
└── electronics
    ├── p40: Travel Charger
    ├── computers
    │   ├── p10: Orbit Laptop
    │   └── p30: Mini Desktop
    └── accessories
        └── p20: Laptop Sleeve
```

Notice that `electronics` contains both a direct product and subcategories. Our model must support both at the same time.

### Important classes and collections

| Class or collection | Purpose |
| --- | --- |
| `Category` | Stores category details, child categories, and direct products |
| `Product` | Stores product details and a name prepared for case-insensitive matching |
| `Map<String, Category>` | Finds a category by ID and detects duplicate category IDs |
| `Set<String>` | Detects duplicate product IDs across the entire system |
| `List<Category>` | Stores a category’s direct children |
| `List<Product>` | Stores a category’s direct products |
| `ArrayDeque<Category>` | Works as a stack while searching the hierarchy |

We do not need a global product map because the API never looks up a product by its ID. A set is enough to enforce uniqueness.

We also do not need a separate list of root categories. Every category is available through the category map, and a search always starts from a supplied category ID.

Each product is stored in exactly one category. We do not copy it into its ancestors.

## 3. Make Each Filter an Independent Strategy

A filter has one responsibility.

> Decide whether a product matches its rule.

We represent that responsibility with a small interface.

```java
private interface ProductFilter {
    boolean matches(Product product);
}
```

Each supported filter has its own implementation.

| Filter strategy | Matching rule |
| --- | --- |
| `NameEqualsFilter` | The complete name matches |
| `NameContainsFilter` | The name contains the supplied text |
| `MinPriceFilter` | The price is greater than or equal to the minimum |
| `MaxPriceFilter` | The price is less than or equal to the maximum |

The search method does not need to understand these individual rules. It simply asks every strategy whether the product matches.

A product is included only when every strategy returns `true`.

### Why use Strategy here?

Putting all filter checks directly inside `getProducts` would mix two different jobs.

- Walking through categories.
- Understanding every filter type.

Every new filter would then require another condition inside the search method.

Strategy separates these jobs. The traversal stays unchanged when new filters are added.

### Why use a factory map?

The public API receives strings rather than filter objects.

A map connects each filter type to a function that creates its strategy.

```text
"nameEquals"       → creates NameEqualsFilter
"nameContains"     → creates NameContainsFilter
"minPriceInCents"  → creates MinPriceFilter
"maxPriceInCents"  → creates MaxPriceFilter
```

For example, adding a future `nameStartsWith` filter would require only two changes.

1. Add its strategy class.
2. Register its creation function in the map.

The `getProducts` signature, traversal, and existing filter classes remain unchanged.

This is a small factory mechanism, not a large factory class hierarchy.

### Do we need the Composite pattern?

A full Composite design could give categories and products a shared interface.

That is unnecessary for this API. Categories store children, while products are checked by filters. They do not need to expose the same operations.

Two simple classes are enough to represent the hierarchy clearly.

## 4. Validate Before Changing State

### Adding a category

Before inserting anything, check that:

- The category ID is not blank.
- The category name is not blank.
- The category ID is not already used.
- A nonempty parent ID identifies an existing category.

Only an empty parent ID creates a root.

A whitespace-only parent ID is not an empty string. It does not identify an existing category, so the call is rejected.

All checks happen before changing the map or the parent’s child list.

### Adding a product

Before inserting anything, check that:

- The product ID, category ID, and product name are not blank.
- The product ID is not already used.
- The category exists.
- The price is not negative.

A rejected call never reserves an ID or changes an existing product.

### Parsing filters

Parse and validate the complete filter list before searching.

For each filter:

1. Split at the first `=`.
2. Check that the type and value are present.
3. Reject repeated types.
4. Find the type in the factory map.
5. Create and validate its strategy.

After parsing, reject the list if the minimum price is greater than the maximum price.

An invalid filter list is not partially applied. The entire search returns an empty list.

### Preserve spaces in name filters

Do not trim names or filter values.

For example, `nameContains= ` searches for a space. Its value is not empty.

We convert only English uppercase letters from `A-Z` into lowercase letters for matching. The original product name is preserved for the returned row.

The normalized product name is calculated once when the product is added.

### Avoid overflowing numeric filter values

The statement bounds stored product prices, but it does not give an upper bound for numeric filter text.

The implementation uses `BigInteger` for price comparisons so a large decimal boundary does not overflow a `long`.

Price filter values must be nonempty decimal digit strings. Leading zeros are allowed.

## 5. Search With an Explicit Stack

The search follows these steps.

1. Find the requested category.
2. Parse all filters.
3. Put the category on a stack.
4. Repeatedly remove a category from the stack.
5. Check its direct products against every filter.
6. Put its children on the stack.
7. Sort the matching products by product ID.
8. Convert them into output rows.

This is an iterative depth-first search.

Using an explicit stack avoids depending on Java’s recursion limit for a deep hierarchy.

### Why is a visited set unnecessary?

A category has only one parent, categories cannot move, and a new category must use an existing parent.

These rules prevent cycles. Each category in the selected subtree is reached exactly once.

### Why sort after filtering?

The order in which categories are visited does not determine the required product order.

We collect only matching products and sort those products using:

```java
first.id.compareTo(second.id)
```

This gives the required case-sensitive ordering without sorting unrelated products.

## 6. Example Walkthrough

Search the `electronics` category with these filters.

```text
minPriceInCents=2000
maxPriceInCents=60000
```

The search includes products directly inside `electronics` and products inside both of its subcategories.

| Product | Price | Matches both filters |
| --- | ---: | --- |
| `p40` | 2499 | Yes |
| `p10` | 85000 | No |
| `p30` | 55000 | Yes |
| `p20` | 1999 | No |

After sorting by product ID, the result is:

```text
[
  "p30,Mini Desktop,computers,55000",
  "p40,Travel Charger,electronics,2499"
]
```

The category ID in each row is the product’s own category, not the category where the search started.

## 7. Why the Solution Is Correct

### It searches exactly the requested subtree

The stack starts with the selected category. From each visited category, the algorithm adds only its direct children.

Therefore, it reaches every descendant and never enters an unrelated category.

### It applies every filter

A product is added only if every strategy returns `true`.

An empty strategy list accepts every product. An invalid filter list is rejected before traversal.

### It returns each product once

Each category is visited once, and each product belongs to exactly one category.

Therefore, no product is collected twice.

### It returns the correct order and format

Matching products are sorted by their IDs using `String.compareTo`.

Each product is then formatted using its original ID, original name, owning category ID, and price.

### Rejected additions leave the system unchanged

Both insertion methods complete their validation before changing stored collections.

A failed insertion cannot overwrite data or partially change the hierarchy.

## 8. Complexity

Let:

- `C_s` be the number of categories in the selected subtree.
- `P_s` be the number of products in that subtree.
- `F` be the number of filters.
- `R` be the number of matching products.
- `T` be the time needed to parse and prepare the filters.
- `L` be the length of a product name.

Under the given fixed ID and product-name length limits:

| Operation | Time |
| --- | --- |
| `addCategory` | `O(1)` expected |
| `addProduct` | `O(L)` expected, including name normalization |
| `getProducts` | `O(T + C_s + P_s × F + R log R)` |

Here, `F` is at most `4`.

The system stores `O(C + P)` data, where `C` and `P` are the total category and product counts.

A query uses `O(C_s + R + F)` additional space, plus the storage for parsed filter values. The stack can hold many sibling categories, so its worst-case size is not limited to the hierarchy depth.

A large subtree can still require a large scan, even when few products match. This approach avoids unrelated branches, but it is not a search index.

## 9. Java Implementation

```java
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class CategoryHierarchySystem {

    private final Map<String, Category> categories = new HashMap<>();
    private final Set<String> productIds = new HashSet<>();

    private final Map<String, Function<String, ProductFilter>> filterFactories =
            new HashMap<>();

    public CategoryHierarchySystem() {
        filterFactories.put("nameEquals", NameEqualsFilter::new);
        filterFactories.put("nameContains", NameContainsFilter::new);

        filterFactories.put("minPriceInCents", value -> {
            BigInteger minimum = parseNonnegativeInteger(value);
            return minimum == null ? null : new MinPriceFilter(minimum);
        });

        filterFactories.put("maxPriceInCents", value -> {
            BigInteger maximum = parseNonnegativeInteger(value);
            return maximum == null ? null : new MaxPriceFilter(maximum);
        });
    }

    public boolean addCategory(
            String categoryId,
            String categoryName,
            String parentCategoryId) {

        if (categoryId.isBlank()
                || categoryName.isBlank()
                || categories.containsKey(categoryId)) {
            return false;
        }

        Category parent = null;

        // Only the empty string creates a root category.
        if (!parentCategoryId.isEmpty()) {
            parent = categories.get(parentCategoryId);

            if (parent == null) {
                return false;
            }
        }

        // All validation is complete before stored data changes.
        Category category = new Category(categoryId, categoryName);
        categories.put(categoryId, category);

        if (parent != null) {
            parent.children.add(category);
        }

        return true;
    }

    public boolean addProduct(
            String productId,
            String categoryId,
            String productName,
            long priceInCents) {

        if (productId.isBlank()
                || categoryId.isBlank()
                || productName.isBlank()
                || priceInCents < 0
                || productIds.contains(productId)) {
            return false;
        }

        Category category = categories.get(categoryId);

        if (category == null) {
            return false;
        }

        Product product = new Product(
                productId, categoryId, productName, priceInCents);

        category.products.add(product);
        productIds.add(productId);

        return true;
    }

    public List<String> getProducts(String categoryId, List<String> filters) {
        Category start = categories.get(categoryId);

        if (start == null) {
            return new ArrayList<>();
        }

        List<ProductFilter> rules = parseFilters(filters);

        if (rules == null) {
            return new ArrayList<>();
        }

        List<Product> matches = new ArrayList<>();
        Deque<Category> stack = new ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            Category current = stack.pop();

            for (Product product : current.products) {
                if (matchesAll(product, rules)) {
                    matches.add(product);
                }
            }

            for (Category child : current.children) {
                stack.push(child);
            }
        }

        matches.sort((first, second) -> first.id.compareTo(second.id));

        List<String> result = new ArrayList<>(matches.size());

        for (Product product : matches) {
            result.add(product.toRow());
        }

        return result;
    }

    // Returns null when any filter or the complete combination is invalid.
    private List<ProductFilter> parseFilters(List<String> filters) {
        List<ProductFilter> rules = new ArrayList<>();
        Set<String> seenTypes = new HashSet<>();

        BigInteger minimum = null;
        BigInteger maximum = null;

        for (String text : filters) {
            int separator = text.indexOf('=');

            if (separator <= 0 || separator == text.length() - 1) {
                return null;
            }

            String type = text.substring(0, separator);
            String value = text.substring(separator + 1);

            // Do not trim the value. Spaces can matter in name filters.
            if (!seenTypes.add(type)) {
                return null;
            }

            Function<String, ProductFilter> factory =
                    filterFactories.get(type);

            if (factory == null) {
                return null;
            }

            ProductFilter rule = factory.apply(value);

            if (rule == null) {
                return null;
            }

            rules.add(rule);

            if (rule instanceof MinPriceFilter) {
                minimum = ((MinPriceFilter) rule).minimum;
            } else if (rule instanceof MaxPriceFilter) {
                maximum = ((MaxPriceFilter) rule).maximum;
            }
        }

        // Validate the relationship between the two price boundaries.
        if (minimum != null
                && maximum != null
                && minimum.compareTo(maximum) > 0) {
            return null;
        }

        return rules;
    }

    private static boolean matchesAll(
            Product product,
            List<ProductFilter> rules) {

        for (ProductFilter rule : rules) {
            if (!rule.matches(product)) {
                return false;
            }
        }

        return true;
    }

    private static BigInteger parseNonnegativeInteger(String value) {
        if (value.isEmpty()) {
            return null;
        }

        // Accept decimal digits only, without signs or surrounding spaces.
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            if (character < '0' || character > '9') {
                return null;
            }
        }

        return new BigInteger(value);
    }

    private static String foldEnglishCase(String value) {
        char[] characters = value.toCharArray();

        for (int i = 0; i < characters.length; i++) {
            if (characters[i] >= 'A' && characters[i] <= 'Z') {
                characters[i] =
                        (char) (characters[i] + ('a' - 'A'));
            }
        }

        return new String(characters);
    }

    private static final class Category {
        private final String id;
        private final String name;
        private final List<Category> children = new ArrayList<>();
        private final List<Product> products = new ArrayList<>();

        private Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class Product {
        private final String id;
        private final String categoryId;
        private final String name;
        private final String normalizedName;
        private final BigInteger priceInCents;

        private Product(
                String id,
                String categoryId,
                String name,
                long priceInCents) {

            this.id = id;
            this.categoryId = categoryId;
            this.name = name;
            this.normalizedName = foldEnglishCase(name);

            // Convert once rather than during every price comparison.
            this.priceInCents = BigInteger.valueOf(priceInCents);
        }

        private String toRow() {
            return id + "," + name + "," + categoryId + "," + priceInCents;
        }
    }

    @FunctionalInterface
    private interface ProductFilter {
        boolean matches(Product product);
    }

    private static final class NameEqualsFilter implements ProductFilter {
        private final String expectedName;

        private NameEqualsFilter(String value) {
            this.expectedName = foldEnglishCase(value);
        }

        @Override
        public boolean matches(Product product) {
            return product.normalizedName.equals(expectedName);
        }
    }

    private static final class NameContainsFilter implements ProductFilter {
        private final String text;

        private NameContainsFilter(String value) {
            this.text = foldEnglishCase(value);
        }

        @Override
        public boolean matches(Product product) {
            return product.normalizedName.contains(text);
        }
    }

    private static final class MinPriceFilter implements ProductFilter {
        private final BigInteger minimum;

        private MinPriceFilter(BigInteger minimum) {
            this.minimum = minimum;
        }

        @Override
        public boolean matches(Product product) {
            return product.priceInCents.compareTo(minimum) >= 0;
        }
    }

    private static final class MaxPriceFilter implements ProductFilter {
        private final BigInteger maximum;

        private MaxPriceFilter(BigInteger maximum) {
            this.maximum = maximum;
        }

        @Override
        public boolean matches(Product product) {
            return product.priceInCents.compareTo(maximum) <= 0;
        }
    }
}
```