# Design an RPC Framework in Python

#### Problem Statement

[https://codezym.com/question/47-design-rpc-framework](https://codezym.com/question/47-design-rpc-framework)

The core idea is to store each method's parameter types under its service ID and method name. A call finds this signature, checks its inputs, and builds the result one piece at a time. Maps and short type checks are enough here. No design pattern is needed for the three fixed rules.

The entire framework runs in memory and simulates calls using the formulas in the statement.

## 1. Start with a list

Imagine a list of method entries. Each entry stores a service ID, a method name, and its parameter types. Finding a method means scanning that list.

```python
# Each entry contains one registered method's details.
for method in registered_methods:
    if method.serviceId == serviceId and method.methodName == methodName:
        return method.parameterTypes
```

With `M` registered methods, a lookup can examine all `M` entries. Registration also needs a lookup to replace an existing signature. Removal has the same problem.

## 2. Replace the scan with two maps

Use an outer map keyed by `serviceId`. Each value is another map, keyed by `methodName`, whose value is a list of parameter types.

For example, service `3` can contain a method named `sum` with the signature `["String", "Long", "Double"]`. Another service can register its own `sum` independently.

- **Register:** create the service map if needed, then store the signature. Assigning the same method name again replaces the old signature.
- **Copy the signature:** store a new list so changes to the caller's list cannot alter the registration later.
- **Unregister:** remove the method and return whether it existed. Remove the service map too when it becomes empty.

`MiniRpcFramework` owns this registry and exposes the three required operations. A separate method class is unnecessary because a registered method stores only its parameter types.

## 3. Validate before returning a result

The order of checks matters.

1. If the method is absent, return `"method does not exist"`.
2. If the argument count differs, return `"input size mismatch"`.
3. Read inputs from left to right. If parsing fails, return `"invalid input parameter at index i"`, replacing `i` with the zero-based index.

For example, a call with both the wrong count and an invalid number must return the count error first.

For each valid input, let `position = i + 1`.

- `String`: the piece is `position + "-" + value`. Keep the text unchanged.
- `Long`: the piece is `position * serviceId * number`.
- `Double`: the piece is `position * serviceId * 2 * number`, converted to an integer **after** multiplication. Truncate the result. Do not round it.

Build one piece at a time and place `"-"` between pieces. Do not strip all trailing hyphens, because a string value may itself end in a hyphen or be empty.

Only a complete result is returned. A parsing failure discards the local output built so far and leaves the registry unchanged.

## 4. Walk through one call

Register `sum` for service `3` with `["String", "Long", "Double"]`, then call it with `["ab", "10", "4.2"]`.

The first piece is `"1-ab"`. The second is `2 * 3 * 10 = 60`. The third is `3 * 3 * 2 * 4.2 = 75.6`, which truncates to `75`.

Joining the pieces gives `"1-ab-60-75"`.

## 5. Why not Strategy here?

Strategy could put each parameter type's rule in its own class. This statement has only three fixed rules and no operation for adding new type behavior, so those extra classes provide little benefit.

A short `if/else` keeps the rules easy to read. Strategy would become useful if adding new parameter types were a requirement.

## Complexity

Let `p` be a method's parameter count and `C` be the total characters read and produced during a call. Using average constant-time map operations:

- Registration takes `O(p)` time to copy the signature.
- Unregistration takes `O(1)` time.
- A call takes `O(p + C)` time and `O(p + C)` temporary space, including its output.

The registry uses `O(M + P)` entries, where `M` is the number of registered methods and `P` is their total parameter count, plus the stored method-name text.

## Complete implementation

`pieces` stores the output parts, and `join` creates the result once. The dataclass uses `default_factory=dict` to give each framework instance its own registry. The parsing helpers handle the numeric text, including the signed 64-bit `Long` range.

```python
from dataclasses import dataclass, field


@dataclass
class MiniRpcFramework:
    services: dict[int, dict[str, list[str]]] = field(
        default_factory=dict, init=False
    )

    def registerMethod(self, serviceId, methodName, inputParameterTypes):
        methods = self.services.get(serviceId)
        if methods is None:
            methods = {}
            self.services[serviceId] = methods

        # Copy the list so later caller changes do not change the signature.
        methods[methodName] = list(inputParameterTypes)

    def unregisterMethod(self, serviceId, methodName):
        methods = self.services.get(serviceId)
        if methods is None or methodName not in methods:
            return False

        del methods[methodName]

        # Do not keep a service entry after its last method is removed.
        if not methods:
            del self.services[serviceId]
        return True

    def callMethod(self, serviceId, methodName, inputValues):
        methods = self.services.get(serviceId)
        types = None if methods is None else methods.get(methodName)

        # Check existence before count, and count before parsing.
        if types is None:
            return "method does not exist"
        if len(inputValues) != len(types):
            return "input size mismatch"

        pieces = []

        for i, value in enumerate(inputValues):
            parameter_type = types[i]
            position = i + 1

            try:
                if parameter_type == "String":
                    piece = f"{position}-{value}"
                elif parameter_type == "Long":
                    number = self.parseLong(value)
                    piece = str(position * serviceId * number)
                else:
                    number = self.parseDouble(value)
                    result = position * serviceId * 2 * number
                    # Convert after multiplication to truncate the final result.
                    piece = str(int(result))
            except (ValueError, OverflowError):
                return f"invalid input parameter at index {i}"

            # Add only separators. Preserve hyphens that belong to the input.
            pieces.append(piece)

        return "-".join(pieces)

    def parseLong(self, value):
        """Parse signed integer text without spaces or digit separators."""
        if not value or value != value.strip() or "_" in value:
            raise ValueError("Invalid integer text")

        number = int(value)
        if number < -(2**63) or number > 2**63 - 1:
            raise ValueError("Integer is outside the Long range")
        return number

    def parseDouble(self, value):
        """Parse floating-point text, including exponents and type suffixes."""
        value = value.strip()
        if not value or "_" in value:
            raise ValueError("Invalid floating-point text")

        if value[-1] in "dDfF":
            value = value[:-1]
        if value.lower().startswith(("0x", "+0x", "-0x")):
            if "p" not in value.lower():
                raise ValueError("Missing hexadecimal exponent")
            return float.fromhex(value)
        return float(value)
```
