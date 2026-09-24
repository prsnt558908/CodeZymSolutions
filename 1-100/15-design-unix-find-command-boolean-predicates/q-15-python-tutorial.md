# Design Unix find Command With Boolean Predicates in Python

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

The finder keeps two dictionaries, one from rule id to its rule class and one from operator name to its merge class. Picking the right class is a lookup instead of a growing if-else ladder.

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

So `len(ops)` is always `len(rules) - 1`, and the expression is a chain that leans to the left.

### Four details that are easy to get wrong

**1. Directory match is a prefix match, but not a plain one.** `/app/logs` must match `/app/logs/archive/x.log`, and must **not** match `/app/logsx/x.log`. Comparing with `path.startswith(directory)` is wrong, we need `path.startswith(directory + "/")`.

**2. The root directory breaks the fix from point 1.** A rule is allowed to search from `/`, as in `1,/,15`, meaning every file over 15 MB. But `directory + "/"` turns into `//`, which matches nothing at all, so that rule quietly returns an empty list.

That failure is sneaky, because an empty list is a perfectly legal answer. Used with `AND NOT` it removes nothing, and the final result silently keeps files that should have been excluded. Root needs its own check.

**3. Size is strictly greater than.** A rule `1,/tmp,0` skips a file of exactly 0 MB.

**4. Extension is checked on the file name, not on the full path.** In example 1 the file `/app/logs/archive/old.log.bak` does **not** match the rule `.log`, because its name ends with `.bak`. It appears in the final answer only through the separate `.bak` rule.

---

## Solution 1 : Brute force with lists

The first version everybody writes. For each rule, loop over all files and collect the matches into a list. Then merge the lists one operator at a time.

```python
from typing import List


class FileFinder:

    def __init__(self):
        self.files = {}

    def addFile(self, path: str, sizeMb: int):
        self.files[path] = sizeMb

    def runQuery(self, rules: List[str], ops: List[str]) -> List[str]:
        result = self._match_rule(rules[0])

        for i in range(len(rules) - 1):
            nxt = self._match_rule(rules[i + 1])
            op = ops[i]

            if op == "AND":
                merged = [p for p in result if p in nxt]
            elif op == "OR":
                merged = list(result)
                for p in nxt:
                    if p not in merged:
                        merged.append(p)
            else:                                   # AND NOT
                merged = [p for p in result if p not in nxt]
            result = merged

        return sorted(result)

    # Every new rule means one more branch in this ladder
    def _match_rule(self, rule: str) -> List[str]:
        parts = rule.split(",")
        directory = parts[1]
        out = []

        for path, size in self.files.items():
            if not self._is_under(path, directory):
                continue

            if parts[0] == "1":
                if size > int(parts[2]):
                    out.append(path)
            elif parts[0] == "2":
                if path.rsplit("/", 1)[-1].endswith(parts[2]):
                    out.append(path)
        return out

    def _is_under(self, path: str, directory: str) -> bool:
        """Root means the whole tree, otherwise "/app/logs" must not pick up "/app/logsx"."""
        d = directory.strip()
        while len(d) > 1 and d.endswith("/"):
            d = d[:-1]
        return d == "/" or path.startswith(d + "/")
```

This works, so as a first draft it is fine. The trouble starts when you look at it as a design.

### What is wrong with it

**It is closed to extension.** The problem says clearly that new criteria will be added later, for example a name-substring match. Here, every new criteria forces a change inside `_match_rule`, a method already doing three jobs at once: parsing, matching, and collecting.

**`p in list` is a linear scan.** An AND between two lists of `n` paths costs `n * n` string comparisons. With 2500 files this adds up for no good reason.

**OR needs a manual duplicate check.** Lists do not de-duplicate, so we hand-roll it with another `in` check, which is the same slow scan again.

**The operators are hard-coded too.** Adding a fourth operator means another `elif` in the middle of `runQuery`.

---

## Fixing it in parts

### Fix 1 : swap the list for a set

The merge step only ever asks "is this path in the other group?". A `set` answers that in constant time and removes duplicates for free.

```python
if op == "AND":
    merged = left & right      # intersection
elif op == "OR":
    merged = left | right      # union
elif op == "AND NOT":
    merged = left - right      # difference
```

Faster and shorter, but both if-else ladders are still there.

### Fix 2 : Strategy for the search rules

Let every criteria be its own class behind a common interface.

```python
class SearchCriteria:
    def matches(self, path: str, size_mb: int) -> bool:
        raise NotImplementedError
```

`MinSizeCriteria` knows about sizes. `ExtensionCriteria` knows about extensions. Neither knows the other exists, and neither knows anything about AND or OR.

### Fix 3 : Strategy for the boolean operators

The three lines from Fix 1 are three interchangeable algorithms over the same input, which is exactly what Strategy is for.

```python
class CombineStrategy:
    def combine(self, left: Set[str], right: Set[str]) -> Set[str]:
        raise NotImplementedError
```

`AndStrategy` intersects. `OrStrategy` unions. `AndNotStrategy` subtracts. Now both ladders are gone, replaced by two lookup dictionaries, and `runQuery` shrinks to a loop that reads like the problem statement.

---

## Solution 2 : Two families of Strategy

### The pieces and why each one exists

**`SearchCriteria` base class.** The first Strategy family. Each implementation holds its own configuration, a directory plus a size or an extension, and answers one question about one file.

**`CombineStrategy` base class.** The second Strategy family. Each implementation takes two sets of paths and returns a merged set. It never looks at a size or an extension, it only does set algebra.

**`self.criteria_builders`.** Rule id `"1"` maps to a lambda that builds a `MinSizeCriteria`. This is the single place that knows which id means what.

**`self.combine_strategies`.** Operator name maps to the merge algorithm. The symmetry with the dictionary above is the nice part: a new rule is one entry in the first, a new operator is one entry in the second.

**`self.files` dictionary.** Paths are unique by nature and a dictionary key is unique by nature, so we get de-duplication for free, and a repeated `addFile` on the same path behaves like an update.

**`set` for intermediate results.** Each rule produces a set, each operator merges two sets. Uniqueness is handled by the data structure rather than by hand.

### Final code

This is the complete file and the one to submit.

```python
from typing import List, Dict, Set, Callable


def normalize_dir(directory: str) -> str:
    """Drops trailing slashes so "/docs" and "/docs/" behave the same. Root becomes ""."""
    d = directory.strip()
    while len(d) > 1 and d.endswith("/"):
        d = d[:-1]
    return "" if d == "/" else d


def is_under(path: str, normalized_dir: str) -> bool:
    """Recursive containment. Root matches everything, "/docs" never matches "/docsx/a.xml"."""
    return normalized_dir == "" or path.startswith(normalized_dir + "/")


def file_name(path: str) -> str:
    return path.rsplit("/", 1)[-1]


# =====================================================================
# STRATEGY 1 : search criteria
# "Does this one file match this one rule?"
# =====================================================================

class SearchCriteria:
    def matches(self, path: str, size_mb: int) -> bool:
        raise NotImplementedError


class MinSizeCriteria(SearchCriteria):
    """Rule 1 -> files strictly larger than min_size_mb, anywhere under directory."""

    def __init__(self, directory: str, min_size_mb: int):
        self.dir = normalize_dir(directory)
        self.min_size_mb = min_size_mb

    def matches(self, path: str, size_mb: int) -> bool:
        return size_mb > self.min_size_mb and is_under(path, self.dir)


class ExtensionCriteria(SearchCriteria):
    """Rule 2 -> files whose name ends with the given extension, anywhere under directory."""

    def __init__(self, directory: str, ext: str):
        self.dir = normalize_dir(directory)
        self.ext = ext

    def matches(self, path: str, size_mb: int) -> bool:
        return is_under(path, self.dir) and file_name(path).endswith(self.ext)


# =====================================================================
# STRATEGY 2 : boolean predicates
# "Given two result sets, how do I merge them into one?"
# =====================================================================

class CombineStrategy:
    def combine(self, left: Set[str], right: Set[str]) -> Set[str]:
        raise NotImplementedError


class AndStrategy(CombineStrategy):
    """AND -> keep only what is in both sets."""

    def combine(self, left: Set[str], right: Set[str]) -> Set[str]:
        return left & right


class OrStrategy(CombineStrategy):
    """OR -> keep everything from both sets, duplicates collapse on their own."""

    def combine(self, left: Set[str], right: Set[str]) -> Set[str]:
        return left | right


class AndNotStrategy(CombineStrategy):
    """AND NOT -> keep what is in the left set but not in the right one."""

    def combine(self, left: Set[str], right: Set[str]) -> Set[str]:
        return left - right


class FileFinder:
    """In-memory version of the Unix find command."""

    def __init__(self):
        # path -> size in MB. A repeated path simply overwrites the old size.
        self.files: Dict[str, int] = {}

        # rule id -> builder. A new search rule is one extra entry here.
        self.criteria_builders: Dict[str, Callable[[List[str]], SearchCriteria]] = {
            "1": lambda parts: MinSizeCriteria(parts[1], int(parts[2].strip())),
            "2": lambda parts: ExtensionCriteria(parts[1], parts[2].strip()),
        }

        # operator name -> strategy. A new boolean predicate is one extra entry here.
        self.combine_strategies: Dict[str, CombineStrategy] = {
            "AND": AndStrategy(),
            "OR": OrStrategy(),
            "AND NOT": AndNotStrategy(),
        }

    def addFile(self, path: str, sizeMb: int):
        self.files[path.strip()] = sizeMb

    def runQuery(self, rules: List[str], ops: List[str]) -> List[str]:
        if not rules:
            return []

        # Start with the files matching the first rule.
        result = self._run_rule(rules[0])

        # Then apply one operator at a time, strictly left to right.
        for i in range(min(len(rules) - 1, len(ops))):
            nxt = self._run_rule(rules[i + 1])
            result = self.combine_strategies[ops[i].strip()].combine(result, nxt)

        # A set is already unique, we only need the ordering.
        return sorted(result)

    def _run_rule(self, rule: str) -> Set[str]:
        """Runs one rule against every stored file and collects the matching paths."""
        parts = rule.split(",")
        criteria = self.criteria_builders[parts[0].strip()](parts)
        return {path for path, size in self.files.items() if criteria.matches(path, size)}
```

Python's `&`, `|` and `-` already return brand new sets, so no combiner can corrupt a set its caller still holds.

### Adding a new criteria, or a new operator

The two extension points are symmetric, and neither touches `runQuery`.

A new search rule:

```python
class NameContainsCriteria(SearchCriteria):
    """Rule 3 -> files whose name contains the given text, anywhere under directory."""

    def __init__(self, directory: str, needle: str):
        self.dir = normalize_dir(directory)
        self.needle = needle

    def matches(self, path: str, size_mb: int) -> bool:
        return is_under(path, self.dir) and self.needle in file_name(path)


# one more entry in criteria_builders
"3": lambda parts: NameContainsCriteria(parts[1], parts[2].strip()),
```

A new boolean operator, say `XOR`, meaning the file matched exactly one of the two rules:

```python
class XorStrategy(CombineStrategy):
    def combine(self, left: Set[str], right: Set[str]) -> Set[str]:
        return left ^ right


# one more entry in combine_strategies
"XOR": XorStrategy(),
```

That is the payoff of Strategy, and it is what the problem statement is asking for when it says the design must allow adding new criteria later.

---

## Solution 3 : Specification pattern

Solution 2 combines **results**. The Specification pattern combines **rules** instead, and it is the usual way business rules get composed with boolean logic in real systems, so it is worth seeing.

The idea: a boolean combination of two rules is itself a rule. `AndSpecification(a, b)` answers the same "does this file qualify?" question that `a` and `b` answer, so it can be passed anywhere a rule is expected. Folding left to right builds one object for the whole query.

```
r1  ->  And(r1, r2)  ->  Or(And(r1, r2), r3)  ->  AndNot(Or(And(r1, r2), r3), r4)
```

Then a single pass over the file dictionary keeps whatever satisfies that one object. No intermediate sets are built at all.

The search criteria classes stay exactly as they were. Only the boolean layer changes, plus one small adapter that lets a criteria sit as a leaf of the tree.

```python
from typing import List, Dict, Callable


def normalize_dir(directory: str) -> str:
    """Drops trailing slashes so "/docs" and "/docs/" behave the same. Root becomes ""."""
    d = directory.strip()
    while len(d) > 1 and d.endswith("/"):
        d = d[:-1]
    return "" if d == "/" else d


def is_under(path: str, normalized_dir: str) -> bool:
    """Recursive containment. Root matches everything, "/docs" never matches "/docsx/a.xml"."""
    return normalized_dir == "" or path.startswith(normalized_dir + "/")


def file_name(path: str) -> str:
    return path.rsplit("/", 1)[-1]


# =====================================================================
# STRATEGY : search criteria, unchanged from solution 2
# =====================================================================

class SearchCriteria:
    def matches(self, path: str, size_mb: int) -> bool:
        raise NotImplementedError


class MinSizeCriteria(SearchCriteria):
    """Rule 1 -> files strictly larger than min_size_mb, anywhere under directory."""

    def __init__(self, directory: str, min_size_mb: int):
        self.dir = normalize_dir(directory)
        self.min_size_mb = min_size_mb

    def matches(self, path: str, size_mb: int) -> bool:
        return size_mb > self.min_size_mb and is_under(path, self.dir)


class ExtensionCriteria(SearchCriteria):
    """Rule 2 -> files whose name ends with the given extension, anywhere under directory."""

    def __init__(self, directory: str, ext: str):
        self.dir = normalize_dir(directory)
        self.ext = ext

    def matches(self, path: str, size_mb: int) -> bool:
        return is_under(path, self.dir) and file_name(path).endswith(self.ext)


# =====================================================================
# SPECIFICATION : a boolean expression over one file
# =====================================================================

class Specification:
    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        raise NotImplementedError


class CriteriaSpecification(Specification):
    """Leaf. Lets one criteria sit inside a specification tree."""

    def __init__(self, criteria: SearchCriteria):
        self.criteria = criteria

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.criteria.matches(path, size_mb)


class AndSpecification(Specification):
    """AND -> both sides must hold."""

    def __init__(self, left: Specification, right: Specification):
        self.left, self.right = left, right

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.left.is_satisfied_by(path, size_mb) and self.right.is_satisfied_by(path, size_mb)


class OrSpecification(Specification):
    """OR -> at least one side must hold."""

    def __init__(self, left: Specification, right: Specification):
        self.left, self.right = left, right

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.left.is_satisfied_by(path, size_mb) or self.right.is_satisfied_by(path, size_mb)


class AndNotSpecification(Specification):
    """AND NOT -> left must hold and right must not."""

    def __init__(self, left: Specification, right: Specification):
        self.left, self.right = left, right

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.left.is_satisfied_by(path, size_mb) and not self.right.is_satisfied_by(path, size_mb)


class FileFinder:
    """In-memory version of the Unix find command."""

    def __init__(self):
        self.files: Dict[str, int] = {}

        self.criteria_builders: Dict[str, Callable[[List[str]], SearchCriteria]] = {
            "1": lambda parts: MinSizeCriteria(parts[1], int(parts[2].strip())),
            "2": lambda parts: ExtensionCriteria(parts[1], parts[2].strip()),
        }

        self.combiners: Dict[str, Callable[[Specification, Specification], Specification]] = {
            "AND": AndSpecification,
            "OR": OrSpecification,
            "AND NOT": AndNotSpecification,
        }

    def addFile(self, path: str, sizeMb: int):
        self.files[path.strip()] = sizeMb

    def runQuery(self, rules: List[str], ops: List[str]) -> List[str]:
        if not rules:
            return []

        # Fold the rules left to right into one specification tree.
        query = self._to_specification(rules[0])
        for i in range(min(len(rules) - 1, len(ops))):
            nxt = self._to_specification(rules[i + 1])
            query = self.combiners[ops[i].strip()](query, nxt)

        # One pass over the stored files, dictionary keys are already unique.
        return sorted(p for p, size in self.files.items() if query.is_satisfied_by(p, size))

    def _to_specification(self, rule: str) -> Specification:
        """"2,/docs,.xml" -> ExtensionCriteria, wrapped as a leaf specification."""
        parts = rule.split(",")
        criteria = self.criteria_builders[parts[0].strip()](parts)
        return CriteriaSpecification(criteria)
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

**Solution 1, brute force.** Each merge uses `p in list`, a linear scan, so a single merge can cost `F * F` comparisons and the query lands at `O(R * F * F)`.

**Solution 2, combine strategies.** Each rule scans all files once, `O(F * R)`, and each merge is a set operation costing `O(F)`. Total `O(F * R)` plus `O(K log K)` to sort `K` results.

**Solution 3, specification.** Same `O(F * R)`, but it short-circuits per file and allocates no intermediate sets.


Memory is `O(F)` for the file dictionary in every version, plus `O(F)` for the intermediate sets in solution 2 or `O(R)` for the tree in solution 3.