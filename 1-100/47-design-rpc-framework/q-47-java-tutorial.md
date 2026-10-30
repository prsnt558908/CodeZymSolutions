# Design an RPC Framework in Java

#### Problem Statement

[https://codezym.com/question/47-design-rpc-framework](https://codezym.com/question/47-design-rpc-framework)

The core idea is to store each method's parameter types under its service ID and method name. A call finds this signature, checks its inputs, and builds the result one piece at a time. Maps and short type checks are enough here. No design pattern is needed for the three fixed rules.

The entire framework runs in memory and simulates calls using the formulas in the statement.

## 1. Start with a list

Imagine a list of method entries. Each entry stores a service ID, a method name, and its parameter types. Finding a method means scanning that list.

```java
// Each entry contains one registered method's details.
for (MethodEntry method : registeredMethods) {
    if (method.serviceId == serviceId
            && method.methodName.equals(methodName)) {
        return method.parameterTypes;
    }
}
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

`StringBuilder` collects the output without repeatedly copying the growing result. The final code keeps the starter class and method signatures.

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniRpcFramework {
    Map<Integer, Map<String, List<String>>> services;

    public MiniRpcFramework() {
        services = new HashMap<>();
    }

    public void registerMethod(
            int serviceId,
            String methodName,
            List<String> inputParameterTypes) {
        Map<String, List<String>> methods = services.get(serviceId);
        if (methods == null) {
            methods = new HashMap<>();
            services.put(serviceId, methods);
        }

        // Copy the list so later caller changes do not change the signature.
        methods.put(methodName, new ArrayList<>(inputParameterTypes));
    }

    public boolean unregisterMethod(int serviceId, String methodName) {
        Map<String, List<String>> methods = services.get(serviceId);
        if (methods == null || methods.remove(methodName) == null) {
            return false;
        }

        // Do not keep a service entry after its last method is removed.
        if (methods.isEmpty()) {
            services.remove(serviceId);
        }
        return true;
    }

    public String callMethod(
            int serviceId,
            String methodName,
            List<String> inputValues) {
        Map<String, List<String>> methods = services.get(serviceId);
        List<String> types = methods == null ? null : methods.get(methodName);

        // Check existence before count, and count before parsing.
        if (types == null) {
            return "method does not exist";
        }
        if (inputValues.size() != types.size()) {
            return "input size mismatch";
        }

        StringBuilder output = new StringBuilder();
        int i = 0;

        for (String value : inputValues) {
            String type = types.get(i);
            int position = i + 1;
            String piece;

            try {
                if (type.equals("String")) {
                    piece = position + "-" + value;
                } else if (type.equals("Long")) {
                    long number = Long.parseLong(value);
                    piece = Long.toString(position * serviceId * number);
                } else {
                    double number = Double.parseDouble(value);
                    double result = position * serviceId * 2 * number;
                    // Cast after multiplication to truncate the final result.
                    piece = Integer.toString((int) result);
                }
            } catch (NumberFormatException e) {
                return "invalid input parameter at index " + i;
            }

            // Add only separators. Preserve hyphens that belong to the input.
            if (i > 0) {
                output.append("-");
            }
            output.append(piece);
            i++;
        }

        return output.toString();
    }
}
```
