# Design Type System for a Toy Programming Language in Python

#### Problem Statement
[https://codezym.com/question/92-design-toy-programming-language](https://codezym.com/question/92-design-toy-programming-language)


Every type string is really a small tree. `Pair<Box<int>,string>` is a `Pair` node with two children, `Box<int>` and `string`, and `Box<int>` has one child, `int`.


So the solution has three steps. **Parse** each string once into a tree. **Match** the declared parameter trees against the argument trees to find what each type variable like `T` stands for. Then **substitute** those findings into the return type.


The **Composite** design pattern fits this best. Leaf types (`int`, `T`) and container types (tuples and generics) share one parent class, so matching and printing work the same way on every node, whether it holds other types or not.


## A Simple First Try: Work on Raw Strings


The first idea is to keep every type as a plain string, and compare strings on every call.


To compare `Pair<T,U>` with `Pair<Box<int>,string>`, we check that both start with `Pair<`. Then we cut out the insides, `T,U` and `Box<int>,string`, and split them at the commas that are not inside any bracket.

```python
def splitTopLevel(self, inside: str) -> Optional[List[str]]:
    """Splits "Box<int>,string" into ["Box<int>", "string"], None if brackets don't balance."""
    parts = []
    depth = 0
    start = 0
    for i, c in enumerate(inside):
        if c in "(<":
            depth += 1
        if c in ")>":
            depth -= 1
        if depth < 0:
            return None
        if c == "," and depth == 0:
            parts.append(inside[start:i])
            start = i + 1
    if depth != 0:
        return None
    parts.append(inside[start:])
    return parts
```


Then we compare the pieces one by one, going deeper for nested types. A type variable saves the first type it meets, and must see the same type every later time.

```python
def matchText(self, param: str, arg: str, bindings: Dict[str, str]) -> bool:
    """Matches a declared type with a concrete type, both as raw strings."""
    if re.fullmatch(r"[A-Z][a-zA-Z0-9]*", param):  # type variable like "T"
        old = bindings.setdefault(param, arg)
        return old == arg
    if re.fullmatch(r"[a-z][a-zA-Z0-9]*", param):  # primitive like "int"
        return param == arg
    # tuple "(...)" or generic "Name<...>": the text up to the first bracket must be equal
    open_index = 0 if param.startswith("(") else param.index("<")
    if not arg.startswith(param[:open_index + 1]):
        return False
    param_parts = self.splitTopLevel(param[open_index + 1:-1])
    arg_parts = self.splitTopLevel(arg[open_index + 1:-1])
    if len(param_parts) != len(arg_parts):
        return False
    for p, a in zip(param_parts, arg_parts):
        if not self.matchText(p, a, bindings):
            return False
    return True
```


To build the result, we scan the return type string and swap every variable name for its saved type.


This works, but it has real problems:

- **Repeated work.** Every call cuts the same registered parameter strings again.
- **Validation is a separate job.** Unknown names, wrong arity, empty slots, spaces and too deep nesting need one more pass over each string, with its own index tricks.
- **Fragile code.** Every rule hides inside `startswith`, `index` and slicing. One wrong index gives a silently wrong answer.


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


**Why not one class with a `kind` field?** It works, but `match`, `substitute` and `collectVariables` would each turn into a long `if/elif` chain over four kinds. Adding a new kind of type later, like a function type, would mean editing every chain.


**Why not Visitor?** Visitor moves each operation into its own visitor class. That pays off when new operations keep getting added. Here we have three small operations that will not change, so Visitor would only add extra `accept` and `visit` methods everywhere.


### The Important Classes


**`TypeNode`** is the common parent. It is an abstract base class (`ABC`), so every node class must implement all three methods. Its `__str__` calls `substitute` with an empty dict, so nothing gets replaced and we get the plain canonical text. This text is what a type variable saves.


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

If anything breaks a rule, the parser returns `None`.


**`FunctionSignature`** keeps the parsed parameter trees and the parsed return tree of one function.


**`ToyTypeSystem`** keeps three simple collections:

- `primitives`, a `set` of the registered primitives.
- `generic_arity`, a `dict` from a generic name to its arity.
- `functions`, a `dict` from a function name to its signature.

Sets and dicts give fast lookups, both while parsing and when finding a function.


### Checking Names and Limits


The limits in the constraints are rules too. `isValidName` checks a name against its identifier pattern and its length limit. Primitive and generic names can have at most 10 characters, and function names at most 15.


So `addPrimitiveType("abcdefghij")` returns `True`, but `addPrimitiveType("abcdefghijk")` returns `False`, because that name has 11 characters.


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


If the function is unknown, the argument count is wrong, an argument breaks a rule, or any match fails, we return `"ERROR"`.


## Final Code

```python
import re
from abc import ABC, abstractmethod
from typing import Dict, List, Optional, Set


class ToyTypeSystem:
    """Keeps the registered primitives, generic constructors and functions,
    and answers type inference queries."""

    def __init__(self):
        # registered primitive names, like "int"
        self.primitives: Set[str] = set()
        # generic constructor name -> arity, like "Pair" -> 2
        self.generic_arity: Dict[str, int] = {}
        # function name -> its parsed signature
        self.functions: Dict[str, "FunctionSignature"] = {}

    def addPrimitiveType(self, primitiveType: str) -> bool:
        if not self.isValidName(primitiveType, r"[a-z][a-zA-Z0-9]*", 10):
            return False
        if primitiveType in self.primitives:
            return False
        self.primitives.add(primitiveType)
        return True

    def addGenericType(self, genericTypeName: str, arity: int) -> bool:
        if not self.isValidName(genericTypeName, r"[A-Z][a-zA-Z0-9]*", 10):
            return False
        if arity < 1 or arity > 10:
            return False
        if genericTypeName in self.generic_arity:
            return False
        self.generic_arity[genericTypeName] = arity
        return True

    def addFunction(self, functionName: str, parameterTypes: List[str],
                    returnType: str) -> bool:
        if not self.isValidName(functionName, r"[a-z][a-zA-Z0-9]*", 15):
            return False
        if functionName in self.functions or len(parameterTypes) > 10:
            return False

        # parse every parameter, type variables are allowed in a signature
        parameters = []
        parameter_variables = set()
        for type_text in parameterTypes:
            node = self.parse(type_text, True)
            if node is None:
                return False
            node.collectVariables(parameter_variables)
            parameters.append(node)

        return_node = self.parse(returnType, True)
        if return_node is None:
            return False

        # a variable that appears only in the return type can never get a value
        return_variables = set()
        return_node.collectVariables(return_variables)
        if not return_variables <= parameter_variables:
            return False

        self.functions[functionName] = FunctionSignature(parameters, return_node)
        return True

    def inferReturnType(self, functionName: str, argumentTypes: List[str]) -> str:
        function = self.functions.get(functionName)
        if function is None or len(function.parameters) != len(argumentTypes):
            return "ERROR"

        # type variable -> the concrete type it stands for, like "T" -> "Box<int>"
        bindings = {}
        for parameter, type_text in zip(function.parameters, argumentTypes):
            argument = self.parse(type_text, False)  # arguments must be concrete
            if argument is None:
                return "ERROR"
            if not parameter.match(argument, bindings):
                return "ERROR"
        return function.return_type.substitute(bindings)

    def parse(self, text: str, allow_variables: bool) -> Optional["TypeNode"]:
        """Parses one type string. Returns None if it breaks any rule."""
        return TypeParser(text, self.primitives, self.generic_arity, allow_variables).parse()

    def isValidName(self, name: str, pattern: str, max_length: int) -> bool:
        """Checks the identifier rule and the length limit from the constraints."""
        return len(name) <= max_length and re.fullmatch(pattern, name) is not None


class FunctionSignature:
    """One registered function: parsed parameter types and parsed return type."""

    def __init__(self, parameters: List["TypeNode"], return_type: "TypeNode"):
        self.parameters = parameters
        self.return_type = return_type


class TypeNode(ABC):
    """Composite pattern: every part of a type is a TypeNode.
    Leaves: PrimitiveType, TypeVariable. Containers: TupleType, GenericType."""

    @abstractmethod
    def match(self, concrete: "TypeNode", bindings: Dict[str, str]) -> bool:
        """Matches this declared type against a concrete type.
        Records what each type variable stands for in bindings.
        Returns False on any mismatch or inconsistent binding."""

    @abstractmethod
    def substitute(self, bindings: Dict[str, str]) -> str:
        """Builds the canonical text, replacing each type variable with its binding."""

    @abstractmethod
    def collectVariables(self, variables: Set[str]) -> None:
        """Adds the names of all type variables inside this type to the set."""

    def __str__(self) -> str:
        # canonical text with nothing replaced
        return self.substitute({})


class PrimitiveType(TypeNode):
    """A registered primitive like int. Leaf node."""

    def __init__(self, name: str):
        self.name = name

    def match(self, concrete: TypeNode, bindings: Dict[str, str]) -> bool:
        # a primitive matches only the same primitive
        return isinstance(concrete, PrimitiveType) and concrete.name == self.name

    def substitute(self, bindings: Dict[str, str]) -> str:
        return self.name

    def collectVariables(self, variables: Set[str]) -> None:
        pass  # a primitive has no type variables


class TypeVariable(TypeNode):
    """A type variable like T. Appears only in function signatures. Leaf node."""

    def __init__(self, name: str):
        self.name = name

    def match(self, concrete: TypeNode, bindings: Dict[str, str]) -> bool:
        concrete_text = str(concrete)
        if self.name not in bindings:
            bindings[self.name] = concrete_text  # first time: remember the type
            return True
        return bindings[self.name] == concrete_text  # later: it must be the same type

    def substitute(self, bindings: Dict[str, str]) -> str:
        return bindings.get(self.name, self.name)

    def collectVariables(self, variables: Set[str]) -> None:
        variables.add(self.name)


class TupleType(TypeNode):
    """A tuple like (int,T). Holds 2 or more child types."""

    def __init__(self, elements: List[TypeNode]):
        self.elements = elements

    def match(self, concrete: TypeNode, bindings: Dict[str, str]) -> bool:
        if not isinstance(concrete, TupleType):
            return False
        if len(concrete.elements) != len(self.elements):
            return False
        for mine, theirs in zip(self.elements, concrete.elements):
            if not mine.match(theirs, bindings):
                return False
        return True

    def substitute(self, bindings: Dict[str, str]) -> str:
        parts = [element.substitute(bindings) for element in self.elements]
        return "(" + ",".join(parts) + ")"

    def collectVariables(self, variables: Set[str]) -> None:
        for element in self.elements:
            element.collectVariables(variables)


class GenericType(TypeNode):
    """A generic instance like Pair<int,T>. Holds exactly arity child types."""

    def __init__(self, name: str, arguments: List[TypeNode]):
        self.name = name
        self.arguments = arguments

    def match(self, concrete: TypeNode, bindings: Dict[str, str]) -> bool:
        if not isinstance(concrete, GenericType):
            return False
        # same name means same arity, the parser checked both against the registry
        if concrete.name != self.name:
            return False
        for mine, theirs in zip(self.arguments, concrete.arguments):
            if not mine.match(theirs, bindings):
                return False
        return True

    def substitute(self, bindings: Dict[str, str]) -> str:
        parts = [argument.substitute(bindings) for argument in self.arguments]
        return self.name + "<" + ",".join(parts) + ">"

    def collectVariables(self, variables: Set[str]) -> None:
        for argument in self.arguments:
            argument.collectVariables(variables)


class TypeParser:
    """Reads a type string from left to right and builds its TypeNode tree.
    Every rule is checked while reading. Any problem gives None."""

    def __init__(self, text: str, primitives: Set[str], generic_arity: Dict[str, int],
                 allow_variables: bool):
        self.text = text
        self.pos = 0  # index of the next character to read
        self.max_depth = 5  # at most 5 levels of nested brackets
        self.max_length = 200  # at most 200 characters in one type string
        self.primitives = primitives
        self.generic_arity = generic_arity
        self.allow_variables = allow_variables  # True for signatures, False for arguments

    def parse(self) -> Optional[TypeNode]:
        """The whole string must be exactly one type, with nothing left over."""
        if len(self.text) > self.max_length:
            return None
        node = self.parseType(0)
        if self.pos != len(self.text):
            return None
        return node

    def parseType(self, depth: int) -> Optional[TypeNode]:
        """Parses one type that starts at pos. depth is how many brackets we are inside."""
        # tuple: (type,type,...)
        if self.pos < len(self.text) and self.text[self.pos] == "(":
            self.pos += 1
            elements = self.parseList(")", depth + 1)
            if elements is None or len(elements) < 2:
                return None
            return TupleType(elements)

        name = self.readName()
        if name == "" or name[0].isdigit():
            return None

        # lowercase name: must be a registered primitive
        if name[0].islower():
            return PrimitiveType(name) if name in self.primitives else None

        # uppercase name followed by '<': a registered generic with its exact arity
        if self.pos < len(self.text) and self.text[self.pos] == "<":
            arity = self.generic_arity.get(name)
            if arity is None:
                return None
            self.pos += 1
            arguments = self.parseList(">", depth + 1)
            if arguments is None or len(arguments) != arity:
                return None
            return GenericType(name, arguments)

        # bare uppercase name: a type variable, allowed only in signatures
        return TypeVariable(name) if self.allow_variables else None

    def parseList(self, close: str, depth: int) -> Optional[List[TypeNode]]:
        """Parses "type,type,..." up to and including the closing bracket."""
        if depth > self.max_depth:
            return None
        items = []
        while True:
            item = self.parseType(depth)
            if item is None or self.pos >= len(self.text):
                return None
            items.append(item)
            next_char = self.text[self.pos]
            self.pos += 1
            if next_char == close:
                return items
            if next_char != ",":
                return None

    def readName(self) -> str:
        """Reads a run of letters and digits, like "Box" or "int32"."""
        start = self.pos
        while self.pos < len(self.text) and self.isNameChar(self.text[self.pos]):
            self.pos += 1
        return self.text[start:self.pos]

    def isNameChar(self, c: str) -> bool:
        return "a" <= c <= "z" or "A" <= c <= "Z" or "0" <= c <= "9"
```


## Complexity


Let `n` be the length of the strings passed to a method, and `r` the length of the returned type.

- `addPrimitiveType` and `addGenericType`: O(n) for the name check. Set and dict work is O(1) on average.
- `addFunction`: O(n). The parser reads each character only once.
- `inferReturnType`: O(p + n + r), where `p` is the size of the stored parameter trees. Each argument is parsed once, each node is visited once while matching, and the result is built once.