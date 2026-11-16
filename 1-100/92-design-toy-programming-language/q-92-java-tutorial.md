# Design Type System for a Toy Programming Language in Java

#### Problem Statement
[https://codezym.com/question/92-design-toy-programming-language](https://codezym.com/question/92-design-toy-programming-language)


Every type string is really a small tree. `Pair<Box<int>,string>` is a `Pair` node with two children, `Box<int>` and `string`, and `Box<int>` has one child, `int`.


So the solution has three steps. **Parse** each string once into a tree. **Match** the declared parameter trees against the argument trees to find what each type variable like `T` stands for. Then **substitute** those findings into the return type.


The **Composite** design pattern fits this best. Leaf types (`int`, `T`) and container types (tuples and generics) share one parent class, so matching and printing work the same way on every node, whether it holds other types or not.


## A Simple First Try: Work on Raw Strings


The first idea is to keep every type as a plain string, and compare strings on every call.


To compare `Pair<T,U>` with `Pair<Box<int>,string>`, we check that both start with `Pair<`. Then we cut out the insides, `T,U` and `Box<int>,string`, and split them at the commas that are not inside any bracket.

```java
// splits "Box<int>,string" into ["Box<int>", "string"], null if brackets don't balance
List<String> splitTopLevel(String inside) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < inside.length(); i++) {
        char c = inside.charAt(i);
        if (c == '(' || c == '<') depth++;
        if (c == ')' || c == '>') depth--;
        if (depth < 0) return null;
        if (c == ',' && depth == 0) {
            parts.add(inside.substring(start, i));
            start = i + 1;
        }
    }
    if (depth != 0) return null;
    parts.add(inside.substring(start));
    return parts;
}
```


Then we compare the pieces one by one, going deeper for nested types. A type variable saves the first type it meets, and must see the same type every later time.

```java
// matches a declared type with a concrete type, both as raw strings
boolean matchText(String param, String arg, Map<String, String> bindings) {
    if (param.matches("[A-Z][a-zA-Z0-9]*")) { // type variable like "T"
        String old = bindings.putIfAbsent(param, arg);
        return old == null || old.equals(arg);
    }
    if (param.matches("[a-z][a-zA-Z0-9]*")) { // primitive like "int"
        return param.equals(arg);
    }
    // tuple "(...)" or generic "Name<...>": the text up to the first bracket must be equal
    int open = param.startsWith("(") ? 0 : param.indexOf('<');
    if (!arg.startsWith(param.substring(0, open + 1))) return false;
    List<String> paramParts = splitTopLevel(param.substring(open + 1, param.length() - 1));
    List<String> argParts = splitTopLevel(arg.substring(open + 1, arg.length() - 1));
    if (paramParts.size() != argParts.size()) return false;
    for (int i = 0; i < paramParts.size(); i++) {
        if (!matchText(paramParts.get(i), argParts.get(i), bindings)) return false;
    }
    return true;
}
```


To build the result, we scan the return type string and swap every variable name for its saved type.


This works, but it has real problems:

- **Repeated work.** Every call cuts the same registered parameter strings again.
- **Validation is a separate job.** Unknown names, wrong arity, empty slots, spaces and too deep nesting need one more pass over each string, with its own index tricks.
- **Fragile code.** Every rule hides inside `startsWith`, `indexOf` and `substring`. One wrong index gives a silently wrong answer.


## Better Approach: Parse Once into a Type Tree


The fix is to keep **reading** and **reasoning** apart.

1. A parser reads a type string once, checks every rule, and builds a tree.
2. Matching and substitution work only on trees. No more string cutting.


Parameter trees are built once inside `addFunction`, and every `inferReturnType` call reuses them.


This is the tree for `Pair<Box<int>,string>`:

```text
GenericType Pair
    GenericType Box
        PrimitiveType int
    PrimitiveType string
```


### Why the Composite Pattern


Every type is either a **leaf** (`int`, `T`) or a **container** that holds more types (`(int,T)`, `Box<T>`). That is exactly the shape the Composite pattern is made for.


All four node classes extend one `TypeNode` class with three methods:

- `match(concrete, bindings)` compares a declared type with a concrete type.
- `substitute(bindings)` prints the type, with each variable replaced by its saved type.
- `collectVariables(variables)` lists the type variables inside the type.


A container never needs to know what its children are. A tuple just asks each child to match itself. So each class holds only its own small rule.


**Why not one class with a `kind` field?** It works, but `match`, `substitute` and `collectVariables` would each turn into a long `if/else` chain over four kinds. Adding a new kind of type later, like a function type, would mean editing every chain.


**Why not Visitor?** Visitor moves each operation into its own visitor class. That pays off when new operations keep getting added. Here we have three small operations that will not change, so Visitor would only add extra `accept` and `visit` methods everywhere.


### The Important Classes


**`TypeNode`** is the common parent. Its `toString()` calls `substitute` with an empty map, so nothing gets replaced and we get the plain canonical text. This text is what a type variable saves.


**`PrimitiveType`** is a leaf that matches only the same primitive.


**`TypeVariable`** is the leaf that does the real inference. The first time `T` meets a concrete type, it saves that type's text in `bindings`. Every later time, the concrete type must have the same text, or the match fails. This is why `sameTypeOnly(int,string)` gives `ERROR`.


**`TupleType`** and **`GenericType`** are the containers. They check the outer shape first: a tuple needs the same number of elements, and a generic needs the same name. Same name also means same arity, because the parser checked both against the registry. Then they match their children one by one.


**`TypeParser`** reads a string from left to right and builds the tree. It is the only class that creates nodes, and it checks every rule while reading:

- A lowercase name must be a registered primitive.
- An uppercase name followed by `<` must be a registered generic, used with exactly its arity.
- A bare uppercase name is a type variable. It is allowed in function signatures, but not in call arguments.
- A tuple needs at least 2 elements.
- No empty slots, extra commas, spaces, unbalanced brackets or leftover characters.
- At most 5 levels of nested brackets.
- At most 200 characters in one type string.

If anything breaks a rule, the parser returns `null`.


**`FunctionSignature`** keeps the parsed parameter trees and the parsed return tree of one function.


**`ToyTypeSystem`** keeps three simple collections:

- `Set<String> primitives` holds the registered primitives.
- `Map<String, Integer> genericArity` maps a generic name to its arity.
- `Map<String, FunctionSignature> functions` maps a function name to its signature.

Hash based sets and maps give fast lookups, both while parsing and when finding a function.


### Checking Names and Limits


The limits in the constraints are rules too. `isValidName` checks a name against its identifier pattern and its length limit. Primitive and generic names can have at most 10 characters, and function names at most 15.


So `addPrimitiveType("abcdefghij")` returns `true`, but `addPrimitiveType("abcdefghijk")` returns `false`, because that name has 11 characters.


`addGenericType` also needs an arity from 1 to 10, and `addFunction` accepts at most 10 parameters.


### Registering a Function


`addFunction` checks the function name and the parameter count, then parses every parameter and the return type, with type variables allowed.


It also collects the variables used in the parameters and in the return type. If the return type uses a variable that no parameter has, no call can ever give it a value, so the function is rejected. This same check rejects `badFactory() -> T`, because a function with zero parameters has no variables at all.


### Inferring a Return Type


Let's walk through `swapTuple((T,U)) -> (U,T)`, called with `(int,string)`.

| Declared | Concrete | What happens |
|---|---|---|
| `(T,U)` | `(int,string)` | Both are tuples of size 2, so match the children |
| `T` | `int` | `T` is new, save `T = int` |
| `U` | `string` | `U` is new, save `U = string` |

Now `substitute` rebuilds `(U,T)` with these bindings and returns `(string,int)`.


If the function is unknown, the argument count is wrong, an argument breaks a rule, or any match fails, we return `ERROR`.


## Final Code

```java
import java.util.*;

/**
 * Keeps the registered primitives, generic constructors and functions,
 * and answers type inference queries.
 */
public class ToyTypeSystem {

    // registered primitive names, like "int"
    Set<String> primitives = new HashSet<>();

    // generic constructor name -> arity, like "Pair" -> 2
    Map<String, Integer> genericArity = new HashMap<>();

    // function name -> its parsed signature
    Map<String, FunctionSignature> functions = new HashMap<>();

    public ToyTypeSystem() {
    }

    public boolean addPrimitiveType(String primitiveType) {
        if (!isValidName(primitiveType, "[a-z][a-zA-Z0-9]*", 10)) return false;
        return primitives.add(primitiveType); // false if it already exists
    }

    public boolean addGenericType(String genericTypeName, int arity) {
        if (!isValidName(genericTypeName, "[A-Z][a-zA-Z0-9]*", 10)) return false;
        if (arity < 1 || arity > 10) return false;
        if (genericArity.containsKey(genericTypeName)) return false;
        genericArity.put(genericTypeName, arity);
        return true;
    }

    public boolean addFunction(String functionName, List<String> parameterTypes, String returnType) {
        if (!isValidName(functionName, "[a-z][a-zA-Z0-9]*", 15)) return false;
        if (functions.containsKey(functionName) || parameterTypes.size() > 10) return false;

        // parse every parameter, type variables are allowed in a signature
        List<TypeNode> parameters = new ArrayList<>();
        Set<String> parameterVariables = new HashSet<>();
        for (String type : parameterTypes) {
            TypeNode node = parse(type, true);
            if (node == null) return false;
            node.collectVariables(parameterVariables);
            parameters.add(node);
        }

        TypeNode returnNode = parse(returnType, true);
        if (returnNode == null) return false;

        // a variable that appears only in the return type can never get a value
        Set<String> returnVariables = new HashSet<>();
        returnNode.collectVariables(returnVariables);
        if (!parameterVariables.containsAll(returnVariables)) return false;

        functions.put(functionName, new FunctionSignature(parameters, returnNode));
        return true;
    }

    public String inferReturnType(String functionName, List<String> argumentTypes) {
        FunctionSignature function = functions.get(functionName);
        if (function == null || function.parameters.size() != argumentTypes.size()) return "ERROR";

        // type variable -> the concrete type it stands for, like "T" -> "Box<int>"
        Map<String, String> bindings = new HashMap<>();
        for (int i = 0; i < argumentTypes.size(); i++) {
            TypeNode argument = parse(argumentTypes.get(i), false); // arguments must be concrete
            if (argument == null) return "ERROR";
            if (!function.parameters.get(i).match(argument, bindings)) return "ERROR";
        }
        return function.returnType.substitute(bindings);
    }

    /** Parses one type string. Returns null if it breaks any rule. */
    TypeNode parse(String text, boolean allowVariables) {
        return new TypeParser(text, primitives, genericArity, allowVariables).parse();
    }

    /** Checks the identifier rule and the length limit from the constraints. */
    boolean isValidName(String name, String pattern, int maxLength) {
        return name.length() <= maxLength && name.matches(pattern);
    }
}

/** One registered function: parsed parameter types and parsed return type. */
class FunctionSignature {
    List<TypeNode> parameters;
    TypeNode returnType;

    FunctionSignature(List<TypeNode> parameters, TypeNode returnType) {
        this.parameters = parameters;
        this.returnType = returnType;
    }
}

/**
 * Composite pattern: every part of a type is a TypeNode.
 * Leaves: PrimitiveType, TypeVariable. Containers: TupleType, GenericType.
 */
abstract class TypeNode {

    /**
     * Matches this declared type against a concrete type.
     * Records what each type variable stands for in bindings.
     * Returns false on any mismatch or inconsistent binding.
     */
    abstract boolean match(TypeNode concrete, Map<String, String> bindings);

    /** Builds the canonical text, replacing each type variable with its binding. */
    abstract String substitute(Map<String, String> bindings);

    /** Adds the names of all type variables inside this type to the set. */
    abstract void collectVariables(Set<String> variables);

    /** Canonical text with nothing replaced. */
    public String toString() {
        return substitute(new HashMap<>());
    }
}

/** A registered primitive like int. Leaf node. */
class PrimitiveType extends TypeNode {
    String name;

    PrimitiveType(String name) {
        this.name = name;
    }

    boolean match(TypeNode concrete, Map<String, String> bindings) {
        // a primitive matches only the same primitive
        return concrete instanceof PrimitiveType && ((PrimitiveType) concrete).name.equals(name);
    }

    String substitute(Map<String, String> bindings) {
        return name;
    }

    void collectVariables(Set<String> variables) {
        // a primitive has no type variables
    }
}

/** A type variable like T. Appears only in function signatures. Leaf node. */
class TypeVariable extends TypeNode {
    String name;

    TypeVariable(String name) {
        this.name = name;
    }

    boolean match(TypeNode concrete, Map<String, String> bindings) {
        String concreteText = concrete.toString();
        String bound = bindings.get(name);
        if (bound == null) {
            bindings.put(name, concreteText); // first time: remember the type
            return true;
        }
        return bound.equals(concreteText); // later: it must be the same type
    }

    String substitute(Map<String, String> bindings) {
        return bindings.getOrDefault(name, name);
    }

    void collectVariables(Set<String> variables) {
        variables.add(name);
    }
}

/** A tuple like (int,T). Holds 2 or more child types. */
class TupleType extends TypeNode {
    List<TypeNode> elements;

    TupleType(List<TypeNode> elements) {
        this.elements = elements;
    }

    boolean match(TypeNode concrete, Map<String, String> bindings) {
        if (!(concrete instanceof TupleType)) return false;
        List<TypeNode> others = ((TupleType) concrete).elements;
        if (others.size() != elements.size()) return false;
        for (int i = 0; i < elements.size(); i++) {
            if (!elements.get(i).match(others.get(i), bindings)) return false;
        }
        return true;
    }

    String substitute(Map<String, String> bindings) {
        List<String> parts = new ArrayList<>();
        for (TypeNode element : elements) {
            parts.add(element.substitute(bindings));
        }
        return "(" + String.join(",", parts) + ")";
    }

    void collectVariables(Set<String> variables) {
        for (TypeNode element : elements) {
            element.collectVariables(variables);
        }
    }
}

/** A generic instance like Pair<int,T>. Holds exactly arity child types. */
class GenericType extends TypeNode {
    String name;
    List<TypeNode> arguments;

    GenericType(String name, List<TypeNode> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    boolean match(TypeNode concrete, Map<String, String> bindings) {
        if (!(concrete instanceof GenericType)) return false;
        GenericType other = (GenericType) concrete;
        // same name means same arity, the parser checked both against the registry
        if (!other.name.equals(name)) return false;
        for (int i = 0; i < arguments.size(); i++) {
            if (!arguments.get(i).match(other.arguments.get(i), bindings)) return false;
        }
        return true;
    }

    String substitute(Map<String, String> bindings) {
        List<String> parts = new ArrayList<>();
        for (TypeNode argument : arguments) {
            parts.add(argument.substitute(bindings));
        }
        return name + "<" + String.join(",", parts) + ">";
    }

    void collectVariables(Set<String> variables) {
        for (TypeNode argument : arguments) {
            argument.collectVariables(variables);
        }
    }
}

/**
 * Reads a type string from left to right and builds its TypeNode tree.
 * Every rule is checked while reading. Any problem gives null.
 */
class TypeParser {
    String text;
    int pos = 0;         // index of the next character to read
    int maxDepth = 5;    // at most 5 levels of nested brackets
    int maxLength = 200; // at most 200 characters in one type string
    Set<String> primitives;
    Map<String, Integer> genericArity;
    boolean allowVariables; // true for signatures, false for call arguments

    TypeParser(String text, Set<String> primitives, Map<String, Integer> genericArity,
               boolean allowVariables) {
        this.text = text;
        this.primitives = primitives;
        this.genericArity = genericArity;
        this.allowVariables = allowVariables;
    }

    /** The whole string must be exactly one type, with nothing left over. */
    TypeNode parse() {
        if (text.length() > maxLength) return null;
        TypeNode node = parseType(0);
        if (pos != text.length()) return null;
        return node;
    }

    /** Parses one type that starts at pos. depth is how many brackets we are inside. */
    TypeNode parseType(int depth) {
        // tuple: (type,type,...)
        if (pos < text.length() && text.charAt(pos) == '(') {
            pos++;
            List<TypeNode> elements = parseList(')', depth + 1);
            if (elements == null || elements.size() < 2) return null;
            return new TupleType(elements);
        }

        String name = readName();
        if (name.isEmpty() || Character.isDigit(name.charAt(0))) return null;

        // lowercase name: must be a registered primitive
        if (Character.isLowerCase(name.charAt(0))) {
            return primitives.contains(name) ? new PrimitiveType(name) : null;
        }

        // uppercase name followed by '<': a registered generic with its exact arity
        if (pos < text.length() && text.charAt(pos) == '<') {
            Integer arity = genericArity.get(name);
            if (arity == null) return null;
            pos++;
            List<TypeNode> arguments = parseList('>', depth + 1);
            if (arguments == null || arguments.size() != arity) return null;
            return new GenericType(name, arguments);
        }

        // bare uppercase name: a type variable, allowed only in signatures
        return allowVariables ? new TypeVariable(name) : null;
    }

    /** Parses "type,type,..." up to and including the closing bracket. */
    List<TypeNode> parseList(char close, int depth) {
        if (depth > maxDepth) return null;
        List<TypeNode> items = new ArrayList<>();
        while (true) {
            TypeNode item = parseType(depth);
            if (item == null || pos >= text.length()) return null;
            items.add(item);
            char next = text.charAt(pos++);
            if (next == close) return items;
            if (next != ',') return null;
        }
    }

    /** Reads a run of letters and digits, like "Box" or "int32". */
    String readName() {
        int start = pos;
        while (pos < text.length() && isNameChar(text.charAt(pos))) {
            pos++;
        }
        return text.substring(start, pos);
    }

    boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
```


## Complexity


Let `n` be the length of the strings passed to a method, and `r` the length of the returned type.

- `addPrimitiveType` and `addGenericType`: O(n) for the name check. Set and map work is O(1) on average.
- `addFunction`: O(n). The parser reads each character only once.
- `inferReturnType`: O(p + n + r), where `p` is the size of the stored parameter trees. Each argument is parsed once, each node is visited once while matching, and the result is built once.