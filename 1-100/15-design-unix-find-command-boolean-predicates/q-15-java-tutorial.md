# Design Unix find Command With Boolean Predicates in Java

#### Problem Statement
[https://codezym.com/question/15-design-unix-find-command-boolean-predicates](https://codezym.com/question/15-design-unix-find-command-boolean-predicates)

The whole problem fits into one idea: keep the files in memory, turn every search rule into a small object that can answer one question, "does this file qualify?", and then glue those objects together with AND, OR and AND NOT.

Two design patterns do the work here. **Strategy** gives each search criteria its own tiny class, so adding a new rule later means writing one small class instead of editing a growing if-else block inside the finder.

**Specification** lets two rule objects be wrapped into a bigger object that still answers the same yes or no question, so `r1 AND r2` is itself just another rule object.

The neat part is that both patterns share a single interface. That means a query of any length collapses into one object, and we can simply run it over every stored file once and sort whatever survives.

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

So `ops.size()` is always `rules.size() - 1`, and the shape of the expression is a chain that leans to the left.

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

**It is closed to extension.** The problem says clearly that new criteria will be added later, for example a name-substring match. With this code, every new criteria forces a change inside `matchRule`, a method that is already doing three jobs at once: parsing, matching, and collecting.

**`list.contains` is a linear scan.** An AND between two lists of `n` paths costs `n * n` string comparisons. With 2500 files and 1200 calls this adds up quickly for no good reason.

**OR needs a manual duplicate check.** Lists do not de-duplicate, so we hand-roll it with another `contains` call, which is the same slow scan again.

**Intermediate lists pile up.** Every operator builds one more list, so a five rule query allocates five result lists plus four merged lists, all to produce one final answer.

---

## Fixing it in parts

### Fix 1 : use a Set instead of a List

The merge step only ever asks "is this path in the other group?". That is exactly what a `HashSet` answers in constant time, and it also removes duplicates for free.

```java
Set<String> merged = new HashSet<>(result);

if (op.equals("AND"))          merged.retainAll(next);  // intersection
else if (op.equals("OR"))      merged.addAll(next);     // union
else if (op.equals("AND NOT")) merged.removeAll(next);  // difference
```

Already much better, but the extension problem is untouched.

### Fix 2 : Strategy for the criteria

Instead of an if-else ladder, let every criteria be its own class behind a common interface.

```java
interface Spec {
    boolean isSatisfiedBy(String path, int sizeMb);
}
```

`MinSizeSpec` knows about sizes. `ExtensionSpec` knows about extensions. Neither one knows the other exists. Adding a name-substring rule tomorrow is a new class plus one line of registration, and `runQuery` never changes.

To build the right object from a rule id we keep a small map from rule id to a builder, rather than a switch. A `switch` would have to be edited for every new rule, a map just gets one more entry.

### Fix 3 : Specification for the booleans

Here is the step that makes everything click. A boolean combination of two criteria also answers "does this file qualify?", so it can implement the **same** `Spec` interface.

```java
class AndSpec implements Spec {
    private final Spec left, right;
    public boolean isSatisfiedBy(String path, int sizeMb) {
        return left.isSatisfiedBy(path, sizeMb) && right.isSatisfiedBy(path, sizeMb);
    }
}
```

Because `AndSpec` is itself a `Spec`, it can be the left child of the next operator. Folding the rules left to right gives us one object for the entire query.

```
r1  ->  And(r1, r2)  ->  Or(And(r1, r2), r3)  ->  AndNot(Or(And(r1, r2), r3), r4)
```

And this quietly deletes Fix 1 as well. There are no intermediate sets to merge any more, because there is nothing to merge. We hold one predicate, walk the file map once, and keep the paths that satisfy it.

Set algebra and boolean logic agree perfectly here, which is why the swap is safe:

| Operator | Set view | Predicate view |
|----------|----------|----------------|
| AND | intersection | `left && right` |
| OR | union | `left \|\| right` |
| AND NOT | difference | `left && !right` |

---

## Solution 2 : Strategy plus Specification

### The pieces and why each one exists

**`Spec` interface.** The single most important decision in this design. A leaf criteria and a boolean combination both answer the same yes or no question, so they share one type. That is what allows a query of any length to be represented as a single object.

**`MinSizeSpec` and `ExtensionSpec`.** The Strategy classes. Each holds its own configuration, the directory plus a size or an extension, and knows nothing about queries, operators or the file store.

**`AndSpec`, `OrSpec`, `AndNotSpec`.** The Specification combiners. Each holds two children of type `Spec`, which is exactly what lets the tree grow to any depth.

**`Map<String, CriteriaBuilder> builders`.** A tiny factory. Rule id `"1"` maps to a lambda that builds a `MinSizeSpec`. This is the one place that knows which rule id means what, so it is the one place to touch when a new criteria arrives.

**`Map<String, Integer> files`.** Paths are unique by nature and a map key is unique by nature, so a map gives us de-duplication for free and makes a repeated `addFile` on the same path behave like an update. A list of file objects would need a scan for both.

### Final code


```java
import java.util.*;

/**
 * In-memory version of the Unix "find" command.
 *
 * Storage  : path -> size in MB
 * Criteria : Strategy pattern, one small class per search rule
 * Booleans : Specification pattern, criteria are combined into one predicate tree
 */
public class FileFinder {

    /**
     * One interface serves both roles.
     * A leaf answers a single criteria, a combiner answers a boolean mix of two others.
     */
    private interface Spec {
        boolean isSatisfiedBy(String path, int sizeMb);
    }

    /** Criteria 1 -> files strictly larger than minSizeMb, anywhere under dir. */
    private static class MinSizeSpec implements Spec {
        private final String dir;
        private final int minSizeMb;

        MinSizeSpec(String dir, int minSizeMb) {
            this.dir = normalizeDir(dir);
            this.minSizeMb = minSizeMb;
        }

        public boolean isSatisfiedBy(String path, int sizeMb) {
            return sizeMb > minSizeMb && isUnder(path, dir);
        }
    }

    /** Criteria 2 -> files whose name ends with the given extension, anywhere under dir. */
    private static class ExtensionSpec implements Spec {
        private final String dir;
        private final String ext;

        ExtensionSpec(String dir, String ext) {
            this.dir = normalizeDir(dir);
            this.ext = ext;
        }

        public boolean isSatisfiedBy(String path, int sizeMb) {
            return isUnder(path, dir) && fileName(path).endsWith(ext);
        }
    }

    /** AND -> the file must satisfy both sides. Same as set intersection. */
    private static class AndSpec implements Spec {
        private final Spec left, right;

        AndSpec(Spec left, Spec right) { this.left = left; this.right = right; }

        public boolean isSatisfiedBy(String path, int sizeMb) {
            return left.isSatisfiedBy(path, sizeMb) && right.isSatisfiedBy(path, sizeMb);
        }
    }

    /** OR -> the file must satisfy at least one side. Same as set union. */
    private static class OrSpec implements Spec {
        private final Spec left, right;

        OrSpec(Spec left, Spec right) { this.left = left; this.right = right; }

        public boolean isSatisfiedBy(String path, int sizeMb) {
            return left.isSatisfiedBy(path, sizeMb) || right.isSatisfiedBy(path, sizeMb);
        }
    }

    /** AND NOT -> in the left side but not in the right side. Same as set difference. */
    private static class AndNotSpec implements Spec {
        private final Spec left, right;

        AndNotSpec(Spec left, Spec right) { this.left = left; this.right = right; }

        public boolean isSatisfiedBy(String path, int sizeMb) {
            return left.isSatisfiedBy(path, sizeMb) && !right.isSatisfiedBy(path, sizeMb);
        }
    }

    /** Turns the comma separated parts of a rule string into a criteria object. */
    private interface CriteriaBuilder {
        Spec build(String[] parts);
    }

    /** ruleId -> builder. Adding a new criteria later is one extra entry here. */
    private final Map<String, CriteriaBuilder> builders = new HashMap<>();

    /** path -> size in MB. A repeated path simply overwrites the old size. */
    private final Map<String, Integer> files = new HashMap<>();

    public FileFinder() {
        builders.put("1", parts -> new MinSizeSpec(parts[1], Integer.parseInt(parts[2].trim())));
        builders.put("2", parts -> new ExtensionSpec(parts[1], parts[2].trim()));
    }

    public void addFile(String path, int sizeMb) {
        files.put(path.trim(), sizeMb);
    }

    public List<String> runQuery(List<String> rules, List<String> ops) {
        List<String> result = new ArrayList<>();
        if (rules == null || rules.isEmpty()) return result;

        // Fold the rules left to right into a single specification tree.
        Spec query = buildCriteria(rules.get(0));
        for (int i = 0; i + 1 < rules.size() && i < ops.size(); i++) {
            Spec next = buildCriteria(rules.get(i + 1));
            query = combine(query, next, ops.get(i).trim());
        }

        // One pass over the stored files, keys of a map are already unique.
        for (Map.Entry<String, Integer> file : files.entrySet()) {
            if (query.isSatisfiedBy(file.getKey(), file.getValue())) result.add(file.getKey());
        }
        Collections.sort(result);
        return result;
    }

    /** "2,/docs,.xml" -> ExtensionSpec("/docs", ".xml") */
    private Spec buildCriteria(String rule) {
        String[] parts = rule.split(",");
        CriteriaBuilder builder = builders.get(parts[0].trim());
        if (builder == null) throw new IllegalArgumentException("Unknown rule id: " + parts[0]);
        return builder.build(parts);
    }

    private Spec combine(Spec left, Spec right, String op) {
        if (op.equals("AND")) return new AndSpec(left, right);
        if (op.equals("OR")) return new OrSpec(left, right);
        if (op.equals("AND NOT")) return new AndNotSpec(left, right);
        throw new IllegalArgumentException("Unknown operator: " + op);
    }

    /** Drops trailing slashes so that "/docs" and "/docs/" behave the same. Root becomes "". */
    private static String normalizeDir(String dir) {
        String d = dir.trim();
        while (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        return d.equals("/") ? "" : d;
    }

    /** Recursive containment. "/docs" matches "/docs/a.xml" but never "/docsx/a.xml". */
    private static boolean isUnder(String path, String normalizedDir) {
        return normalizedDir.isEmpty() || path.startsWith(normalizedDir + "/");
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
```

### Adding a new criteria later

Say we now want rule 3, a name-substring match. The entire change is this.

```java
private static class NameContainsSpec implements Spec {
    private final String dir, needle;

    NameContainsSpec(String dir, String needle) {
        this.dir = normalizeDir(dir);
        this.needle = needle;
    }

    public boolean isSatisfiedBy(String path, int sizeMb) {
        return isUnder(path, dir) && fileName(path).contains(needle);
    }
}

// inside the constructor
builders.put("3", parts -> new NameContainsSpec(parts[1], parts[2].trim()));
```

`runQuery` is untouched. So is every existing criteria. That is the payoff of Strategy, and it is exactly what the problem statement asks for when it says the design must allow adding new criteria later.

---

## Why Strategy and Specification, and not something else

**Interpreter is the closest rival.** We are, after all, evaluating an expression. Interpreter earns its keep when there is a real grammar to handle: nested brackets, operator precedence, a tokenizer, a parse step. Our expression has none of that, it is a flat chain evaluated left to right. Adopting Interpreter would mean writing grammar and parsing machinery that never gets used, to reach the same tree that three small Specification classes give us directly.

**Chain of Responsibility sounds right but does not fit.** The phrase "pass each file through a chain of filters" makes it tempting. The problem is that in Chain of Responsibility each handler either handles the request or forwards it, and the chain stops at the first handler that takes ownership. That can model a run of ANDs, but it has no way to express OR or AND NOT, where both sides must be consulted before you can decide. If you patch it so that handlers return booleans and the caller combines them, you have rebuilt Specification with extra ceremony.

One pattern we do get for free is **Composite**. `AndSpec`, `OrSpec` and `AndNotSpec` each hold children of their own interface type, which is the Composite shape. Specification is Composite applied to booleans, so there is nothing extra to add.

---

## Complexity

Let `F` be the number of stored files and `R` the number of rules in a query.

- `addFile` is `O(1)`, a single map write.
- `runQuery` builds the tree in `O(R)`, then does one pass over `F` files. Each file walks at most `2R - 1` nodes, and `&&` and `||` short circuit, so it is usually far less. That gives `O(F * R)` plus `O(K log K)` for sorting `K` results.
- Memory is `O(F)` for the file map plus `O(R)` for the query tree, which is discarded after the call.

The brute force version is genuinely worse, not just uglier. Each operator merges two lists using `list.contains`, which is a linear scan, so a single merge can cost `F * F` comparisons and the whole query lands at `O(R * F * F)`. Switching to a set drops it back to `O(F * R)`, and the specification tree reaches the same bound while allocating no intermediate collection at all.