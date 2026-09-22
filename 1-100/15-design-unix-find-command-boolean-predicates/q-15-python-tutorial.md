# Design Unix find Command With Boolean Predicates in Python

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

So `len(ops)` is always `len(rules) - 1`, and the shape of the expression is a chain that leans to the left.

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

**It is closed to extension.** The problem says clearly that new criteria will be added later, for example a name-substring match. With this code, every new criteria forces a change inside `_match_rule`, a method that is already doing three jobs at once: parsing, matching, and collecting.

**`p in list` is a linear scan.** An AND between two lists of `n` paths costs `n * n` string comparisons. With 2500 files and 1200 calls this adds up quickly for no good reason.

**OR needs a manual duplicate check.** Lists do not de-duplicate, so we hand-roll it with another `in` check, which is the same slow scan again.

**Intermediate lists pile up.** Every operator builds one more list, so a five rule query allocates five result lists plus four merged lists, all to produce one final answer.

---

## Fixing it in parts

### Fix 1 : use a set instead of a list

The merge step only ever asks "is this path in the other group?". That is exactly what a `set` answers in constant time, and it also removes duplicates for free.

```python
merged = set(result)

if op == "AND":
    merged &= set(nxt)      # intersection
elif op == "OR":
    merged |= set(nxt)      # union
elif op == "AND NOT":
    merged -= set(nxt)      # difference
```

Already much better, but the extension problem is untouched.

### Fix 2 : Strategy for the criteria

Instead of an if-else ladder, let every criteria be its own class behind a common interface.

```python
class Spec:
    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        raise NotImplementedError
```

`MinSizeSpec` knows about sizes. `ExtensionSpec` knows about extensions. Neither one knows the other exists. Adding a name-substring rule tomorrow is a new class plus one line of registration, and `runQuery` never changes.

To build the right object from a rule id we keep a small dictionary from rule id to a builder, rather than an if-else chain. A chain would have to be edited for every new rule, a dictionary just gets one more entry.

### Fix 3 : Specification for the booleans

Here is the step that makes everything click. A boolean combination of two criteria also answers "does this file qualify?", so it can implement the **same** `Spec` interface.

```python
class AndSpec(Spec):
    def __init__(self, left: Spec, right: Spec):
        self.left, self.right = left, right

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.left.is_satisfied_by(path, size_mb) and self.right.is_satisfied_by(path, size_mb)
```

Because `AndSpec` is itself a `Spec`, it can be the left child of the next operator. Folding the rules left to right gives us one object for the entire query.

```
r1  ->  And(r1, r2)  ->  Or(And(r1, r2), r3)  ->  AndNot(Or(And(r1, r2), r3), r4)
```

And this quietly deletes Fix 1 as well. There are no intermediate sets to merge any more, because there is nothing to merge. We hold one predicate, walk the file dictionary once, and keep the paths that satisfy it.

Set algebra and boolean logic agree perfectly here, which is why the swap is safe:

| Operator | Set view | Predicate view |
|----------|----------|----------------|
| AND | intersection | `left and right` |
| OR | union | `left or right` |
| AND NOT | difference | `left and not right` |

---

## Solution 2 : Strategy plus Specification

### The pieces and why each one exists

**`Spec` base class.** The single most important decision in this design. A leaf criteria and a boolean combination both answer the same yes or no question, so they share one type. That is what allows a query of any length to be represented as a single object.

**`MinSizeSpec` and `ExtensionSpec`.** The Strategy classes. Each holds its own configuration, the directory plus a size or an extension, and knows nothing about queries, operators or the file store.

**`AndSpec`, `OrSpec`, `AndNotSpec`.** The Specification combiners. Each holds two children of type `Spec`, which is exactly what lets the tree grow to any depth.

**`self.builders` dictionary.** A tiny factory. Rule id `"1"` maps to a lambda that builds a `MinSizeSpec`. This is the one place that knows which rule id means what, so it is the one place to touch when a new criteria arrives.

**`self.files` dictionary.** Paths are unique by nature and a dictionary key is unique by nature, so it gives us de-duplication for free and makes a repeated `addFile` on the same path behave like an update. A list of file objects would need a scan for both.

### Final code


```python
from typing import List, Dict, Callable


def normalize_dir(directory: str) -> str:
    """Drops trailing slashes so that "/docs" and "/docs/" behave the same. Root becomes ""."""
    d = directory.strip()
    while len(d) > 1 and d.endswith("/"):
        d = d[:-1]
    return "" if d == "/" else d


def is_under(path: str, normalized_dir: str) -> bool:
    """Recursive containment. "/docs" matches "/docs/a.xml" but never "/docsx/a.xml"."""
    return normalized_dir == "" or path.startswith(normalized_dir + "/")


def file_name(path: str) -> str:
    return path.rsplit("/", 1)[-1]


class Spec:
    """One interface for both roles.

    A leaf answers a single criteria, a combiner answers a boolean mix of two others.
    """

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        raise NotImplementedError


class MinSizeSpec(Spec):
    """Criteria 1 -> files strictly larger than min_size_mb, anywhere under directory."""

    def __init__(self, directory: str, min_size_mb: int):
        self.dir = normalize_dir(directory)
        self.min_size_mb = min_size_mb

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return size_mb > self.min_size_mb and is_under(path, self.dir)


class ExtensionSpec(Spec):
    """Criteria 2 -> files whose name ends with the given extension, anywhere under directory."""

    def __init__(self, directory: str, ext: str):
        self.dir = normalize_dir(directory)
        self.ext = ext

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return is_under(path, self.dir) and file_name(path).endswith(self.ext)


class AndSpec(Spec):
    """AND -> the file must satisfy both sides. Same as set intersection."""

    def __init__(self, left: Spec, right: Spec):
        self.left, self.right = left, right

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.left.is_satisfied_by(path, size_mb) and self.right.is_satisfied_by(path, size_mb)


class OrSpec(Spec):
    """OR -> the file must satisfy at least one side. Same as set union."""

    def __init__(self, left: Spec, right: Spec):
        self.left, self.right = left, right

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.left.is_satisfied_by(path, size_mb) or self.right.is_satisfied_by(path, size_mb)


class AndNotSpec(Spec):
    """AND NOT -> in the left side but not in the right side. Same as set difference."""

    def __init__(self, left: Spec, right: Spec):
        self.left, self.right = left, right

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return self.left.is_satisfied_by(path, size_mb) and not self.right.is_satisfied_by(path, size_mb)


class FileFinder:
    """In-memory version of the Unix find command."""

    def __init__(self):
        # path -> size in MB. A repeated path simply overwrites the old size.
        self.files: Dict[str, int] = {}

        # rule id -> builder. Adding a new criteria later is one extra entry here.
        self.builders: Dict[str, Callable[[List[str]], Spec]] = {
            "1": lambda parts: MinSizeSpec(parts[1], int(parts[2].strip())),
            "2": lambda parts: ExtensionSpec(parts[1], parts[2].strip()),
        }

        self.combiners: Dict[str, Callable[[Spec, Spec], Spec]] = {
            "AND": AndSpec,
            "OR": OrSpec,
            "AND NOT": AndNotSpec,
        }

    def addFile(self, path: str, sizeMb: int):
        self.files[path.strip()] = sizeMb

    def runQuery(self, rules: List[str], ops: List[str]) -> List[str]:
        if not rules:
            return []

        # Fold the rules left to right into a single specification tree.
        query = self._build_criteria(rules[0])
        for i in range(min(len(rules) - 1, len(ops))):
            nxt = self._build_criteria(rules[i + 1])
            query = self.combiners[ops[i].strip()](query, nxt)

        # One pass over the stored files, dictionary keys are already unique.
        return sorted(p for p, size in self.files.items() if query.is_satisfied_by(p, size))

    def _build_criteria(self, rule: str) -> Spec:
        """"2,/docs,.xml" -> ExtensionSpec("/docs", ".xml")"""
        parts = rule.split(",")
        return self.builders[parts[0].strip()](parts)
```

### Adding a new criteria later

Say we now want rule 3, a name-substring match. The entire change is this.

```python
class NameContainsSpec(Spec):
    """Criteria 3 -> files whose name contains the given text, anywhere under directory."""

    def __init__(self, directory: str, needle: str):
        self.dir = normalize_dir(directory)
        self.needle = needle

    def is_satisfied_by(self, path: str, size_mb: int) -> bool:
        return is_under(path, self.dir) and self.needle in file_name(path)


# inside FileFinder.__init__, one more entry in the builders dictionary
"3": lambda parts: NameContainsSpec(parts[1], parts[2].strip()),
```

`runQuery` is untouched. So is every existing criteria. That is the payoff of Strategy, and it is exactly what the problem statement asks for when it says the design must allow adding new criteria later.

---

## Why Strategy and Specification, and not something else

**Interpreter is the closest rival.** We are, after all, evaluating an expression. Interpreter earns its keep when there is a real grammar to handle: nested brackets, operator precedence, a tokenizer, a parse step. Our expression has none of that, it is a flat chain evaluated left to right. Adopting Interpreter would mean writing grammar and parsing machinery that never gets used, to reach the same tree that three small Specification classes give us directly.

**Chain of Responsibility sounds right but does not fit.** The phrase "pass each file through a chain of filters" makes it tempting. The problem is that in Chain of Responsibility each handler either handles the request or forwards it, and the chain stops at the first handler that takes ownership. That can model a run of ANDs, but it has no way to express OR or AND NOT, where both sides must be consulted before you can decide. If you patch it so that handlers return booleans and the caller combines them, you have rebuilt Specification with extra ceremony.

One pattern we do get for free is **Composite**. `AndSpec`, `OrSpec` and `AndNotSpec` each hold children of their own base type, which is the Composite shape. Specification is Composite applied to booleans, so there is nothing extra to add.

---

## Complexity

Let `F` be the number of stored files and `R` the number of rules in a query.

- `addFile` is `O(1)`, a single dictionary write.
- `runQuery` builds the tree in `O(R)`, then does one pass over `F` files. Each file walks at most `2R - 1` nodes, and `and` and `or` short circuit, so it is usually far less. That gives `O(F * R)` plus `O(K log K)` for sorting `K` results.
- Memory is `O(F)` for the file dictionary plus `O(R)` for the query tree, which is discarded after the call.

The brute force version is genuinely worse, not just uglier. Each operator merges two lists using `p in list`, which is a linear scan, so a single merge can cost `F * F` comparisons and the whole query lands at `O(R * F * F)`. Switching to a set drops it back to `O(F * R)`, and the specification tree reaches the same bound while allocating no intermediate collection at all.