# Design Unix find Command With Boolean Predicates in Java

#### Problem Statement
[https://codezym.com/question/15-design-unix-find-command-boolean-predicates](https://codezym.com/question/15-design-unix-find-command-boolean-predicates)

The problem can be divided into two parts.

The first part is deciding whether one file matches one search rule.

A rule like `1,/app/logs,5` means the file must be bigger than 5 MB and sit somewhere under `/app/logs`.

A rule like `2,/app/logs,.log` means the name must end with `.log`.

Every rule checks something different, but they all take the same input, one path and one size, and they all return yes or no.

That is what makes the **Strategy** pattern fit here.

Each rule becomes its own small class behind a common interface.

The problem says new rules will be added later, for example a name-substring match. With this setup, adding a new rule means one new class plus one line to register it, and nothing inside existing code changes.

The second part is combining the results.

Running one rule over all the stored files gives a list of file paths which are selected using that rule. Running the next rule gives another list.

- `AND` keeps the paths present in both lists.
- `OR` keeps everything from either list.
- `AND NOT` keeps the paths in the first list that are missing from the second.

These three operators fit Strategy for the same reason the rules did. They all take the same input, two lists of paths, and they all return one merged list.

So each operator also becomes a small class behind a common interface.

The finder keeps two maps, one from rule id to its rule class and one from operator name to its merge class. Picking the right class is a lookup instead of a growing if-else ladder.

The query then reduces to a plain loop: run the next rule, merge it into the running result, move on to the next operator.

There is another well-known way to handle the boolean part, called the **Specification** pattern.

Instead of merging result lists, it merges the rules themselves into one combined rule. That is the standard choice when business rules get combined with AND, OR and NOT, so it is worth knowing, and it is solution 3 below.

For this problem it does not buy much. The expression here is a plain left to right chain with no brackets, so the extra flexibility never gets used.

---

## Understanding the input

A rule is a plain comma separated string.

| Rule | Format | Meaning |
|------|--------|---------|
| 1 | `1,dirPath,minSizeMb` | files **strictly larger** than `minSizeMb`, anywhere under `dirPath` |
| 2 | `2,dirPath,.ext` | files with extension `.ext`, anywhere under `dirPath` |

Operators are applied strictly left to right, with no brackets and no precedence.

```
rules = [r1, r2, r3, r4]
ops   = ["AND", "OR", "AND NOT"]

((r1 AND r2) OR r3) AND NOT r4
```

So `ops.size()` is always `rules.size() - 1`, and the expression is a chain that leans to the left.

### Four details that are easy to get wrong

**1. Directory match is a prefix match, but not a plain one.** `/app/logs` must match `/app/logs/archive/x.log`, and must **not** match `/app/logsx/x.log`. Comparing with `path.startsWith(dir)` is wrong, we need `path.startsWith(dir + "/")`.

**2. The root directory breaks the fix from point 1.** A rule is allowed to search from `/`, as in `1,/,15`, meaning every file over 15 MB. But `dir + "/"` turns into `//`, which matches nothing at all, so that rule quietly returns an empty list.

That failure is sneaky, because an empty list is a perfectly legal answer. Used with `AND NOT` it removes nothing, and the final result silently keeps files that should have been excluded. Root needs its own check.

**3. Size is strictly greater than.** A rule `1,/tmp,0` skips a file of exactly 0 MB.

**4. Extension is checked on the file name, not on the full path.** In example 1 the file `/app/logs/archive/old.log.bak` does **not** match the rule `.log`, because its name ends with `.bak`. It appears in the final answer only through the separate `.bak` rule.

---

## Solution 1 : Brute force with lists

The first version everybody writes. For each rule, loop over all files and collect the matches into a list. Then merge the lists one operator at a time.

```java
import java.util.*;

public class FileFinder {

    private final Map<String, Integer> files = new HashMap<>();

    public void addFile(String path, int sizeMb) {
        files.put(path, sizeMb);
    }

    public List<String> runQuery(List<String> rules, List<String> ops) {
        List<String> result = matchRule(rules.get(0));

        for (int i = 0; i + 1 < rules.size(); i++) {
            List<String> next = matchRule(rules.get(i + 1));
            String op = ops.get(i);
            List<String> merged = new ArrayList<>();

            if (op.equals("AND")) {
                for (String p : result) if (next.contains(p)) merged.add(p);
            } else if (op.equals("OR")) {
                merged.addAll(result);
                for (String p : next) if (!merged.contains(p)) merged.add(p);
            } else if (op.equals("AND NOT")) {
                for (String p : result) if (!next.contains(p)) merged.add(p);
            }
            result = merged;
        }
        Collections.sort(result);
        return result;
    }

    // Every new rule means one more branch in this ladder
    private List<String> matchRule(String rule) {
        String[] parts = rule.split(",");
        String dir = parts[1];
        List<String> out = new ArrayList<>();

        for (Map.Entry<String, Integer> file : files.entrySet()) {
            String path = file.getKey();
            int size = file.getValue();
            if (!isUnder(path, dir)) continue;

            if (parts[0].equals("1")) {
                if (size > Integer.parseInt(parts[2])) out.add(path);
            } else if (parts[0].equals("2")) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (name.endsWith(parts[2])) out.add(path);
            }
        }
        return out;
    }

    /** Root means the whole tree, otherwise "/app/logs" must not pick up "/app/logsx". */
    private boolean isUnder(String path, String dir) {
        String d = dir.trim();
        while (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        return d.equals("/") || path.startsWith(d + "/");
    }
}
```

This works, so as a first draft it is fine. The trouble starts when you look at it as a design.

### What is wrong with it

**It is closed to extension.** The problem says clearly that new criteria will be added later, for example a name-substring match. Here, every new criteria forces a change inside `matchRule`, a method already doing three jobs at once: parsing, matching, and collecting.

**`list.contains` is a linear scan.** An AND between two lists of `n` paths costs `n * n` string comparisons. With 2500 files this adds up for no good reason.

**OR needs a manual duplicate check.** Lists do not de-duplicate, so we hand-roll it with another `contains` call, which is the same slow scan again.

**The operators are hard-coded too.** Adding a fourth operator means another `else if` in the middle of `runQuery`.

---

## Fixing it in parts

### Fix 1 : swap the list for a set

The merge step only ever asks "is this path in the other group?". A `HashSet` answers that in constant time and removes duplicates for free.

```java
Set<String> merged = new HashSet<>(result);

if (op.equals("AND"))          merged.retainAll(next);  // intersection
else if (op.equals("OR"))      merged.addAll(next);     // union
else if (op.equals("AND NOT")) merged.removeAll(next);  // difference
```

Faster and shorter, but both if-else ladders are still there.

### Fix 2 : Strategy for the search rules

Let every criteria be its own class behind a common interface.

```java
interface SearchCriteria {
    boolean matches(String path, int sizeMb);
}
```

`MinSizeCriteria` knows about sizes. `ExtensionCriteria` knows about extensions. Neither knows the other exists, and neither knows anything about AND or OR.

### Fix 3 : Strategy for the boolean operators

The three lines from Fix 1 are three interchangeable algorithms over the same input, which is exactly what Strategy is for.

```java
interface CombineStrategy {
    Set<String> combine(Set<String> left, Set<String> right);
}
```

`AndStrategy` intersects. `OrStrategy` unions. `AndNotStrategy` subtracts. Now both ladders are gone, replaced by two lookup maps, and `runQuery` shrinks to a loop that reads like the problem statement.

---

## Solution 2 : Two families of Strategy

### The pieces and why each one exists

**`SearchCriteria` interface.** The first Strategy family. Each implementation holds its own configuration, a directory plus a size or an extension, and answers one question about one file.

**`CombineStrategy` interface.** The second Strategy family. Each implementation takes two sets of paths and returns a merged set. It never looks at a size or an extension, it only does set algebra.

**`Map<String, CriteriaBuilder> criteriaBuilders`.** Rule id `"1"` maps to a lambda that builds a `MinSizeCriteria`. This is the single place that knows which id means what.

**`Map<String, CombineStrategy> combineStrategies`.** Operator name maps to the merge algorithm. The symmetry with the map above is the nice part: a new rule is one entry in the first map, a new operator is one entry in the second.

**`Map<String, Integer> files`.** Paths are unique by nature and a map key is unique by nature, so we get de-duplication for free, and a repeated `addFile` on the same path behaves like an update.

**`Set<String>` for intermediate results.** Each rule produces a set, each operator merges two sets. Uniqueness is handled by the data structure rather than by hand.

### Final code

This is the complete class and the one to submit.

```java
import java.util.*;

/**
 * In-memory version of the Unix "find" command.
 *
 * Storage  : path -> size in MB
 * Strategy : SearchCriteria, one class per search rule
 * Strategy : CombineStrategy, one class per boolean predicate
 */
public class FileFinder {

    /* =====================================================================
     * STRATEGY 1 : search criteria
     * "Does this one file match this one rule?"
     * ===================================================================== */

    private interface SearchCriteria {
        boolean matches(String path, int sizeMb);
    }

    /** Rule 1 -> files strictly larger than minSizeMb, anywhere under dir. */
    private static class MinSizeCriteria implements SearchCriteria {
        private final String dir;
        private final int minSizeMb;

        MinSizeCriteria(String dir, int minSizeMb) {
            this.dir = normalizeDir(dir);
            this.minSizeMb = minSizeMb;
        }

        @Override
        public boolean matches(String path, int sizeMb) {
            return sizeMb > minSizeMb && isUnder(path, dir);
        }
    }

    /** Rule 2 -> files whose name ends with the given extension, anywhere under dir. */
    private static class ExtensionCriteria implements SearchCriteria {
        private final String dir;
        private final String ext;

        ExtensionCriteria(String dir, String ext) {
            this.dir = normalizeDir(dir);
            this.ext = ext;
        }

        @Override
        public boolean matches(String path, int sizeMb) {
            return isUnder(path, dir) && fileName(path).endsWith(ext);
        }
    }

    /* =====================================================================
     * STRATEGY 2 : boolean predicates
     * "Given two result sets, how do I merge them into one?"
     * ===================================================================== */

    private interface CombineStrategy {
        Set<String> combine(Set<String> left, Set<String> right);
    }

    /** AND -> keep only what is in both sets. */
    private static class AndStrategy implements CombineStrategy {
        @Override
        public Set<String> combine(Set<String> left, Set<String> right) {
            Set<String> merged = new HashSet<>(left);
            merged.retainAll(right);
            return merged;
        }
    }

    /** OR -> keep everything from both sets, duplicates collapse on their own. */
    private static class OrStrategy implements CombineStrategy {
        @Override
        public Set<String> combine(Set<String> left, Set<String> right) {
            Set<String> merged = new HashSet<>(left);
            merged.addAll(right);
            return merged;
        }
    }

    /** AND NOT -> keep what is in the left set but not in the right one. */
    private static class AndNotStrategy implements CombineStrategy {
        @Override
        public Set<String> combine(Set<String> left, Set<String> right) {
            Set<String> merged = new HashSet<>(left);
            merged.removeAll(right);
            return merged;
        }
    }

    /* =====================================================================
     * FileFinder itself
     * ===================================================================== */

    /** Builds a criteria object out of the parts of a rule string. */
    private interface CriteriaBuilder {
        SearchCriteria build(String[] parts);
    }

    /** ruleId -> builder. A new search rule is one extra entry here. */
    private final Map<String, CriteriaBuilder> criteriaBuilders = new HashMap<>();

    /** operator name -> strategy. A new boolean predicate is one extra entry here. */
    private final Map<String, CombineStrategy> combineStrategies = new HashMap<>();

    /** path -> size in MB. A repeated path simply overwrites the old size. */
    private final Map<String, Integer> files = new HashMap<>();

    public FileFinder() {
        criteriaBuilders.put("1", parts -> new MinSizeCriteria(parts[1], Integer.parseInt(parts[2].trim())));
        criteriaBuilders.put("2", parts -> new ExtensionCriteria(parts[1], parts[2].trim()));

        combineStrategies.put("AND", new AndStrategy());
        combineStrategies.put("OR", new OrStrategy());
        combineStrategies.put("AND NOT", new AndNotStrategy());
    }

    public void addFile(String path, int sizeMb) {
        files.put(path.trim(), sizeMb);
    }

    public List<String> runQuery(List<String> rules, List<String> ops) {
        if (rules == null || rules.isEmpty()) return new ArrayList<>();

        // Start with the files matching the first rule.
        Set<String> result = runRule(rules.get(0));

        // Then apply one operator at a time, strictly left to right.
        for (int i = 0; i + 1 < rules.size() && i < ops.size(); i++) {
            Set<String> next = runRule(rules.get(i + 1));
            CombineStrategy strategy = combineStrategies.get(ops.get(i).trim());
            if (strategy == null) throw new IllegalArgumentException("Unknown operator: " + ops.get(i));
            result = strategy.combine(result, next);
        }

        // A set is already unique, we only need the ordering.
        List<String> sorted = new ArrayList<>(result);
        Collections.sort(sorted);
        return sorted;
    }

    /** Runs one rule against every stored file and collects the matching paths. */
    private Set<String> runRule(String rule) {
        String[] parts = rule.split(",");
        CriteriaBuilder builder = criteriaBuilders.get(parts[0].trim());
        if (builder == null) throw new IllegalArgumentException("Unknown rule id: " + parts[0]);

        SearchCriteria criteria = builder.build(parts);
        Set<String> matched = new HashSet<>();
        for (Map.Entry<String, Integer> file : files.entrySet()) {
            if (criteria.matches(file.getKey(), file.getValue())) matched.add(file.getKey());
        }
        return matched;
    }

    /* ---------------- small shared helpers ---------------- */

    /** Drops trailing slashes so "/docs" and "/docs/" behave the same. Root becomes "". */
    private static String normalizeDir(String dir) {
        String d = dir.trim();
        while (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        return d.equals("/") ? "" : d;
    }

    /** Recursive containment. Root matches everything, "/docs" never matches "/docsx/a.xml". */
    private static boolean isUnder(String path, String normalizedDir) {
        return normalizedDir.isEmpty() || path.startsWith(normalizedDir + "/");
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
```

Each combiner copies into a fresh `HashSet` rather than mutating `left`. Calling `retainAll` on the running result directly would also work here, but copying means a strategy can never corrupt a set its caller still holds.

### Adding a new criteria, or a new operator

The two extension points are symmetric, and neither touches `runQuery`.

A new search rule:

```java
private static class NameContainsCriteria implements SearchCriteria {
    private final String dir, needle;

    NameContainsCriteria(String dir, String needle) {
        this.dir = normalizeDir(dir);
        this.needle = needle;
    }

    @Override
    public boolean matches(String path, int sizeMb) {
        return isUnder(path, dir) && fileName(path).contains(needle);
    }
}

// in the constructor
criteriaBuilders.put("3", parts -> new NameContainsCriteria(parts[1], parts[2].trim()));
```

A new boolean operator, say `XOR`, meaning the file matched exactly one of the two rules:

```java
private static class XorStrategy implements CombineStrategy {
    @Override
    public Set<String> combine(Set<String> left, Set<String> right) {
        Set<String> both = new HashSet<>(left);
        both.retainAll(right);

        Set<String> merged = new HashSet<>(left);
        merged.addAll(right);
        merged.removeAll(both);
        return merged;
    }
}

// in the constructor
combineStrategies.put("XOR", new XorStrategy());
```

That is the payoff of Strategy, and it is what the problem statement is asking for when it says the design must allow adding new criteria later.

---

## Solution 3 : Specification pattern

Solution 2 combines **results**. The Specification pattern combines **rules** instead, and it is the usual way business rules get composed with boolean logic in real systems, so it is worth seeing.

The idea: a boolean combination of two rules is itself a rule. `AndSpecification(a, b)` answers the same "does this file qualify?" question that `a` and `b` answer, so it can be passed anywhere a rule is expected. Folding left to right builds one object for the whole query.

```
r1  ->  And(r1, r2)  ->  Or(And(r1, r2), r3)  ->  AndNot(Or(And(r1, r2), r3), r4)
```

Then a single pass over the file map keeps whatever satisfies that one object. No intermediate sets are built at all.

The search criteria classes stay exactly as they were. Only the boolean layer changes, plus one small adapter that lets a criteria sit as a leaf of the tree.

```java
import java.util.*;

/**
 * In-memory version of the Unix "find" command.
 *
 * Storage       : path -> size in MB
 * Strategy      : SearchCriteria, one class per search rule
 * Specification : boolean expression built from those criteria
 */
public class FileFinder {

    /* =====================================================================
     * STRATEGY : search criteria, unchanged from solution 2
     * ===================================================================== */

    private interface SearchCriteria {
        boolean matches(String path, int sizeMb);
    }

    /** Rule 1 -> files strictly larger than minSizeMb, anywhere under dir. */
    private static class MinSizeCriteria implements SearchCriteria {
        private final String dir;
        private final int minSizeMb;

        MinSizeCriteria(String dir, int minSizeMb) {
            this.dir = normalizeDir(dir);
            this.minSizeMb = minSizeMb;
        }

        @Override
        public boolean matches(String path, int sizeMb) {
            return sizeMb > minSizeMb && isUnder(path, dir);
        }
    }

    /** Rule 2 -> files whose name ends with the given extension, anywhere under dir. */
    private static class ExtensionCriteria implements SearchCriteria {
        private final String dir;
        private final String ext;

        ExtensionCriteria(String dir, String ext) {
            this.dir = normalizeDir(dir);
            this.ext = ext;
        }

        @Override
        public boolean matches(String path, int sizeMb) {
            return isUnder(path, dir) && fileName(path).endsWith(ext);
        }
    }

    /* =====================================================================
     * SPECIFICATION : a boolean expression over one file
     * ===================================================================== */

    private interface Specification {
        boolean isSatisfiedBy(String path, int sizeMb);
    }

    /** Leaf. Lets one criteria sit inside a specification tree. */
    private static class CriteriaSpecification implements Specification {
        private final SearchCriteria criteria;

        CriteriaSpecification(SearchCriteria criteria) {
            this.criteria = criteria;
        }

        @Override
        public boolean isSatisfiedBy(String path, int sizeMb) {
            return criteria.matches(path, sizeMb);
        }
    }

    /** AND -> both sides must hold. */
    private static class AndSpecification implements Specification {
        private final Specification left, right;

        AndSpecification(Specification left, Specification right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isSatisfiedBy(String path, int sizeMb) {
            return left.isSatisfiedBy(path, sizeMb) && right.isSatisfiedBy(path, sizeMb);
        }
    }

    /** OR -> at least one side must hold. */
    private static class OrSpecification implements Specification {
        private final Specification left, right;

        OrSpecification(Specification left, Specification right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isSatisfiedBy(String path, int sizeMb) {
            return left.isSatisfiedBy(path, sizeMb) || right.isSatisfiedBy(path, sizeMb);
        }
    }

    /** AND NOT -> left must hold and right must not. */
    private static class AndNotSpecification implements Specification {
        private final Specification left, right;

        AndNotSpecification(Specification left, Specification right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isSatisfiedBy(String path, int sizeMb) {
            return left.isSatisfiedBy(path, sizeMb) && !right.isSatisfiedBy(path, sizeMb);
        }
    }

    /* =====================================================================
     * FileFinder itself
     * ===================================================================== */

    private interface CriteriaBuilder {
        SearchCriteria build(String[] parts);
    }

    private final Map<String, CriteriaBuilder> criteriaBuilders = new HashMap<>();
    private final Map<String, Integer> files = new HashMap<>();

    public FileFinder() {
        criteriaBuilders.put("1", parts -> new MinSizeCriteria(parts[1], Integer.parseInt(parts[2].trim())));
        criteriaBuilders.put("2", parts -> new ExtensionCriteria(parts[1], parts[2].trim()));
    }

    public void addFile(String path, int sizeMb) {
        files.put(path.trim(), sizeMb);
    }

    public List<String> runQuery(List<String> rules, List<String> ops) {
        List<String> result = new ArrayList<>();
        if (rules == null || rules.isEmpty()) return result;

        // Fold the rules left to right into one specification tree.
        Specification query = toSpecification(rules.get(0));
        for (int i = 0; i + 1 < rules.size() && i < ops.size(); i++) {
            Specification next = toSpecification(rules.get(i + 1));
            query = combine(query, next, ops.get(i).trim());
        }

        // One pass over the stored files, keys of a map are already unique.
        for (Map.Entry<String, Integer> file : files.entrySet()) {
            if (query.isSatisfiedBy(file.getKey(), file.getValue())) result.add(file.getKey());
        }
        Collections.sort(result);
        return result;
    }

    /** "2,/docs,.xml" -> ExtensionCriteria, wrapped as a leaf specification. */
    private Specification toSpecification(String rule) {
        String[] parts = rule.split(",");
        CriteriaBuilder builder = criteriaBuilders.get(parts[0].trim());
        if (builder == null) throw new IllegalArgumentException("Unknown rule id: " + parts[0]);
        return new CriteriaSpecification(builder.build(parts));
    }

    private Specification combine(Specification left, Specification right, String op) {
        if (op.equals("AND")) return new AndSpecification(left, right);
        if (op.equals("OR")) return new OrSpecification(left, right);
        if (op.equals("AND NOT")) return new AndNotSpecification(left, right);
        throw new IllegalArgumentException("Unknown operator: " + op);
    }

    /* ---------------- small shared helpers ---------------- */

    /** Drops trailing slashes so "/docs" and "/docs/" behave the same. Root becomes "". */
    private static String normalizeDir(String dir) {
        String d = dir.trim();
        while (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        return d.equals("/") ? "" : d;
    }

    /** Recursive containment. Root matches everything, "/docs" never matches "/docsx/a.xml". */
    private static boolean isUnder(String path, String normalizedDir) {
        return normalizedDir.isEmpty() || path.startsWith(normalizedDir + "/");
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
```

### When Specification earns its keep, and why not here

Specification shines when the boolean expression is **nested or reused**. Think of `(premium OR longTenure) AND NOT fraudFlagged` in a pricing engine. A composite is itself a rule, so it can be stored in a field, passed to another service, reused across requests, or nested to any depth. Set combining cannot do that, because a set of results is tied to one moment and one data set.

This problem has none of those needs. The expression is a flat chain with no brackets, it is thrown away at the end of the call, and nothing is reused. So the tree is paying for flexibility that never gets used, and it charges an extra adapter class for it.

That is why solution 2 is the one to reach for here. Solution 3 is the design you want the moment brackets or reusable rules enter the picture.

---

## Why not Interpreter or Chain of Responsibility

**Interpreter** is the closest rival to solution 3, because we are evaluating an expression. It earns its keep when there is a real grammar to handle: nested brackets, operator precedence, a tokenizer, a parse step. Our expression has none of that. Adopting Interpreter would mean writing grammar and parsing machinery that never gets used.

**Chain of Responsibility** sounds right at first, since "pass each file through a chain of filters" is a tempting description. The problem is that in Chain of Responsibility each handler either handles the request or forwards it, and the chain stops at the first handler that takes ownership. That can model a run of ANDs, but it cannot express OR or AND NOT, where both sides must be consulted before deciding. Patch it so handlers return booleans and the caller combines them, and you have rebuilt Specification with extra ceremony.

---

## Complexity

Let `F` be the number of stored files and `R` the number of rules in a query.

**Solution 1, brute force.** Each merge uses `list.contains`, a linear scan, so a single merge can cost `F * F` comparisons and the query lands at `O(R * F * F)`.

**Solution 2, combine strategies.** Each rule scans all files once, `O(F * R)`, and each merge is a set operation costing `O(F)`. Total `O(F * R)` plus `O(K log K)` to sort `K` results.

**Solution 3, specification.** Same `O(F * R)`, but it short-circuits per file and allocates no intermediate sets.

Memory is `O(F)` for the file map in every version, plus `O(F)` for the intermediate sets in solution 2 or `O(R)` for the tree in solution 3.