# Design an Extensible Excel-Like Autofill Engine in Java

#### Problem Statement
[https://codezym.com/question/320-excel-like-autofill-engine](https://codezym.com/question/320-excel-like-autofill-engine)

When you drag a cell in Excel, it looks at the starting values and decides how to continue them. `12` continues as `13, 14, 15`, while `p, Q, r` simply repeats. Every fill type does the same two jobs: check whether it understands the seeds, then generate the values.

That is exactly what the **Strategy pattern** is made for. Each fill type becomes its own small class with two methods, `supports()` and `generate()`. The engine keeps a list of these strategies, asks them one by one and lets the first one that says yes do the work.

Strategy alone is the best fit here. A simple list handles the selection, so we do not need a Factory or a Chain of Responsibility on top of it. Adding dates, weekdays or Fibonacci later means writing one new class and registering it, while the selection logic and the existing strategies stay untouched.

We will start with a plain if-else version, see where it breaks down, and then refactor it into the Strategy pattern.

## The Rules in Short

| Fill type | Accepted seeds | Output |
|---|---|---|
| `NUMBER` | exactly one valid integer | `seed, seed+1, seed+2, ...` |
| `CHARACTER_SEQUENCE` | one or more seeds, each a single letter from `a-z` or `A-Z` | the seeds repeated in the same order |

In both cases the output has exactly `cellCount` values and starts with the seeds themselves.

If no fill type accepts the **whole** list, the answer is an empty list.

The only tricky part is deciding what counts as a valid integer:

| Seed | Valid number? | Why |
|---|---|---|
| `"12"`, `"-3"`, `"0"` | Yes | plain integers |
| `"+5"` | No | a plus sign is not allowed |
| `"01"` | No | leading zero |
| `"-0"` | No | negative zero |
| `"1.5"`, `" 5"` | No | not a plain integer |
| `"1000000001"` | No | outside `-1,000,000,000` to `1,000,000,000` |

## Approach 1: One Class With If-Else

The most direct solution puts everything inside `autofill()`. First check if the seeds look like a number. If not, check if they are all letters. Otherwise return an empty list.

```java
import java.util.*;

public class AutofillEngine {

    public AutofillEngine() {
    }

    public List<String> autofill(List<String> seedValues, int cellCount) {
        List<String> result = new ArrayList<>();
        if (seedValues == null || seedValues.isEmpty()) return result;

        // Fill type 1: a single number that counts up
        if (seedValues.size() == 1 && isValidInteger(seedValues.get(0))) {
            long start = Long.parseLong(seedValues.get(0));
            for (int i = 0; i < cellCount; i++) {
                result.add(String.valueOf(start + i));
            }
            return result;
        }

        // Fill type 2: a repeating pattern of letters
        if (allEnglishLetters(seedValues)) {
            for (int i = 0; i < cellCount; i++) {
                result.add(seedValues.get(i % seedValues.size()));
            }
            return result;
        }

        // Every new fill type needs one more if-block above this line
        return result;
    }

    boolean isValidInteger(String seed) {
        if (seed == null || seed.isEmpty()) return false;
        String digits = seed.startsWith("-") ? seed.substring(1) : seed;
        if (digits.isEmpty() || digits.length() > 10) return false;
        for (char c : digits.toCharArray()) {
            if (c < '0' || c > '9') return false;
        }
        if (digits.charAt(0) == '0' && !seed.equals("0")) return false;
        long value = Long.parseLong(seed);
        return value >= -1_000_000_000L && value <= 1_000_000_000L;
    }

    boolean allEnglishLetters(List<String> seedValues) {
        for (String seed : seedValues) {
            if (seed == null || seed.length() != 1) return false;
            char c = seed.charAt(0);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        }
        return true;
    }
}
```

### Problems With This Approach

It works, but think about what happens when we add dates, weekdays and Fibonacci.

- **The method keeps growing.** Every new fill type is one more if-block inside `autofill()`.
- **One class does every job.** It picks the fill type, validates every format and generates the values. A small change to the number rules can break letter filling by mistake.
- **Hard to test.** You cannot test one fill type on its own.
- **It breaks the main requirement.** New fill types must be added without changing the selection logic or any existing strategy. Here, every new type edits `autofill()`.

## Approach 2: Strategy Pattern

Look at the if-else code again. Each block does the same two things: **check** if the seeds fit, and if they do, **generate** the values. Only the details change.

So we move each block into its own class behind a common interface. That is the Strategy pattern: one job (filling cells), many interchangeable ways of doing it.

### The Classes

**1. `AutofillStrategy` (interface)**

The contract every fill type follows:

- `supports(seedValues)` returns `true` only if **every** seed is valid for this fill type.
- `generate(seedValues, cellCount)` returns exactly `cellCount` values.

The engine calls `generate()` only after `supports()` returns `true`. So a strategy always validates all seeds before generating anything, and a list like `["a", "b", "1"]` is rejected as a whole.

We need this interface because the engine only talks to it. The engine never needs to know which fill types exist.

**2. `NumberStrategy`**

Accepts exactly one valid integer and counts up from it. Its `isValidInteger()` check works in four steps:

1. Remove an optional leading `-`.
2. What is left must be 1 to 10 characters long, and every character must be a digit `0` to `9`. A `+` fails here.
3. If it starts with `0`, the whole seed must be exactly `"0"`. This one rule rejects both `"01"` and `"-0"`.
4. Read it as a `long` and check the range.

Why not just call `Long.parseLong()` and catch the error? It is too forgiving. It happily accepts `"+5"`, `"007"` and `"-0"`, which the rules reject. So we check the format ourselves and use `Long.parseLong()` only to read the value.

We compare against `'0'` to `'9'` directly because `Character.isDigit()` also accepts digits from other languages. And we use a `long` because a 10 digit seed like `"9999999999"` does not fit in an `int`.

A valid seed is always written in its normal form, so `String.valueOf(start)` gives back the exact seed. That is why the first generated value always equals the seed.

**3. `CharacterSequenceStrategy`**

Accepts a list where every seed is exactly one English letter. It repeats the seeds using `i % seedValues.size()`. For 3 seeds this gives `0, 1, 2, 0, 1, 2, ...`, so it jumps back to the first seed after the last one.

Seeds go into the output exactly as given, so `"Q"` stays `"Q"`. We compare with `a-z` and `A-Z` directly because `Character.isLetter()` would also accept letters like `é`.

**4. `AutofillEngine`**

Holds a `List<AutofillStrategy>`. It asks each strategy in order, and the first one that supports the seeds generates the result. If none does, it returns an empty list.

Why a `List`? We only need to keep the strategies in a fixed order and walk through them. The order also acts as the priority if two future strategies ever accept the same seeds. For today's two strategies the order does not matter, because no seed can be both a valid integer and a letter.

### Class Diagram

```mermaid
classDiagram
    class AutofillStrategy {
        <<interface>>
        supports(seedValues) boolean
        generate(seedValues, cellCount) List~String~
    }
    class NumberStrategy {
        isValidInteger(seed) boolean
    }
    class CharacterSequenceStrategy {
        isEnglishLetter(seed) boolean
    }
    class AutofillEngine {
        List~AutofillStrategy~ strategies
        addStrategy(strategy) void
        autofill(seedValues, cellCount) List~String~
    }
    AutofillStrategy <|.. NumberStrategy
    AutofillStrategy <|.. CharacterSequenceStrategy
    AutofillEngine o-- AutofillStrategy : asks in order
```

### How a Call Flows

For `autofill(["p", "Q", "r"], 8)`:

1. `NumberStrategy.supports()` returns `false` because there is more than one seed.
2. `CharacterSequenceStrategy.supports()` returns `true` because every seed is a single letter.
3. It generates `["p", "Q", "r", "p", "Q", "r", "p", "Q"]`.

For `autofill(["A", "2"], 6)`, both strategies return `false`, so the engine returns `[]`.

### Adding a New Fill Type

Say we want weekdays (`Mon, Tue, Wed, ...`):

1. Create `WeekdayStrategy implements AutofillStrategy`.
2. Write its own `supports()` and `generate()`.
3. Add it to the list in the constructor, or plug it in from outside with `addStrategy()`.

`autofill()`, `NumberStrategy` and `CharacterSequenceStrategy` stay exactly as they are.

### Why Not Simple Factory or Chain of Responsibility?

**Simple Factory:** a factory could look at the seeds and return the right strategy. But to choose, it needs an if-else that knows the rules of every fill type. Each new type means editing the factory, which is the same problem as Approach 1. In our design each strategy checks its own seeds, so nothing central has to change.

**Chain of Responsibility:** each strategy could keep a link to the next one and pass the seeds along when it cannot handle them. That gives the same "first one that can handle it wins" result. But now every strategy has to manage a `next` link, and someone has to wire the chain together. A plain list with a loop does the same job with less code.

### How It Follows SOLID

| Principle | In this design |
|---|---|
| Single Responsibility | Each strategy handles one fill type. The engine only picks a strategy. |
| Open/Closed | A new fill type is a new class plus one registration line. The selection logic and existing strategies are never edited. |
| Liskov Substitution | Any strategy works wherever an `AutofillStrategy` is expected. |
| Interface Segregation | The interface has only the two methods every strategy needs. |
| Dependency Inversion | `autofill()` works only with the `AutofillStrategy` interface, never with concrete classes. |

### Complexity

Let `n` be `cellCount`.

- **Validation:** every seed is read at most once per strategy, so it is linear in the total length of the seeds. With at most 100 short seeds, this is tiny.
- **Generation:** `O(n)`, one step per cell.
- **Space:** `O(n)` for the returned list.

## Java Code

```java
import java.util.*;

/**
 * One fill type of the autofill engine.
 * Each strategy decides on its own which seeds it accepts and how to continue them.
 */
interface AutofillStrategy {

    /** Returns true only if every seed value is valid for this strategy. */
    boolean supports(List<String> seedValues);

    /** Returns exactly cellCount values. Called only after supports() returned true. */
    List<String> generate(List<String> seedValues, int cellCount);
}

/**
 * NUMBER strategy: exactly one integer seed, then +1 for every next cell.
 * Example: ["12"] gives 12, 13, 14, ...
 */
class NumberStrategy implements AutofillStrategy {

    long minSeed = -1_000_000_000L;
    long maxSeed = 1_000_000_000L;

    public boolean supports(List<String> seedValues) {
        return seedValues != null && seedValues.size() == 1 && isValidInteger(seedValues.get(0));
    }

    public List<String> generate(List<String> seedValues, int cellCount) {
        long start = Long.parseLong(seedValues.get(0));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < cellCount; i++) {
            result.add(String.valueOf(start + i));
        }
        return result;
    }

    /**
     * Valid: "0", "12", "-3".
     * Invalid: "+5", "01", "-0", "1.5", or anything outside the allowed range.
     */
    boolean isValidInteger(String seed) {
        if (seed == null || seed.isEmpty()) return false;

        // An optional minus sign is allowed. A plus sign is not, so it fails the digit check below.
        String digits = seed.startsWith("-") ? seed.substring(1) : seed;

        // At least one digit and at most 10, because 1,000,000,000 has 10 digits
        if (digits.isEmpty() || digits.length() > 10) return false;

        // Only plain digits 0 to 9 (Character.isDigit() would also accept digits of other languages)
        for (char c : digits.toCharArray()) {
            if (c < '0' || c > '9') return false;
        }

        // No leading zero, unless the whole seed is "0". This also rejects "-0".
        if (digits.charAt(0) == '0' && !seed.equals("0")) return false;

        // long, because a 10 digit seed like "9999999999" does not fit in an int
        long value = Long.parseLong(seed);
        return value >= minSeed && value <= maxSeed;
    }
}

/**
 * CHARACTER_SEQUENCE strategy: every seed is one English letter.
 * The seeds form a pattern that repeats. Example: ["p", "Q", "r"] gives p, Q, r, p, Q, r, ...
 */
class CharacterSequenceStrategy implements AutofillStrategy {

    public boolean supports(List<String> seedValues) {
        if (seedValues == null || seedValues.isEmpty()) return false;

        // Check every seed before generating anything
        for (String seed : seedValues) {
            if (!isEnglishLetter(seed)) return false;
        }
        return true;
    }

    public List<String> generate(List<String> seedValues, int cellCount) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < cellCount; i++) {
            // i % size goes 0, 1, 2, 0, 1, 2, ... so the pattern restarts after the last seed
            result.add(seedValues.get(i % seedValues.size()));
        }
        return result;
    }

    /** Exactly one character from a-z or A-Z. Accented or non-English letters are rejected. */
    boolean isEnglishLetter(String seed) {
        if (seed == null || seed.length() != 1) return false;
        char c = seed.charAt(0);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}

public class AutofillEngine {

    // Strategies are asked in this order. The first one that supports the seeds does the work.
    List<AutofillStrategy> strategies = new ArrayList<>();

    public AutofillEngine() {
        // Built-in strategies: NUMBER and CHARACTER_SEQUENCE
        strategies.add(new NumberStrategy());
        strategies.add(new CharacterSequenceStrategy());
    }

    /** Plugs in a new fill type (dates, weekdays, Fibonacci) without changing any existing code. */
    public void addStrategy(AutofillStrategy strategy) {
        strategies.add(strategy);
    }

    public List<String> autofill(List<String> seedValues, int cellCount) {
        for (AutofillStrategy strategy : strategies) {
            if (strategy.supports(seedValues)) {
                return strategy.generate(seedValues, cellCount);
            }
        }
        // No strategy understands these seeds
        return new ArrayList<>();
    }
}
```