from dataclasses import dataclass
from typing import Dict, List, Tuple, Optional


@dataclass(frozen=True)
class MethodKey:
    serviceId: int
    methodName: str


class MiniRpcFramework:
    TYPE_STRING = "String"
    TYPE_LONG = "Long"
    TYPE_DOUBLE = "Double"

    def __init__(self):
        # Registry: (serviceId, methodName) -> parameter types
        self._registry: Dict[MethodKey, List[str]] = {}

    def registerMethod(self, serviceId, methodName, inputParameterTypes):
        # Store a defensive copy since caller can mutate the list later
        key = MethodKey(serviceId=serviceId, methodName=methodName)
        self._registry[key] = list(inputParameterTypes)

    def unregisterMethod(self, serviceId, methodName):
        key = MethodKey(serviceId=serviceId, methodName=methodName)
        return self._registry.pop(key, None) is not None

    def callMethod(self, serviceId, methodName, inputValues):
        key = MethodKey(serviceId=serviceId, methodName=methodName)

        types = self._registry.get(key)
        if types is None:
            return "method does not exist"

        if inputValues is None or len(inputValues) != len(types):
            return "input size mismatch"

        parts: List[str] = []

        for i in range(len(inputValues)):
            t = types[i]
            raw = inputValues[i]

            if t == self.TYPE_STRING:
                # (i+1) + "-" + inputValues[i] + "-"
                parts.append(f"{i + 1}-{raw}")
            elif t == self.TYPE_LONG:
                try:
                    v = int(raw)
                except Exception:
                    return f"invalid input parameter at index {i}"
                computed = (i + 1) * serviceId * v
                parts.append(str(computed))
            elif t == self.TYPE_DOUBLE:
                try:
                    d = float(raw)
                except Exception:
                    return f"invalid input parameter at index {i}"
                x = (i + 1) * serviceId * 2.0 * d
                truncated = int(x)  # truncation towards zero (matches Java cast)
                parts.append(str(truncated))
            else:
                # Spec says this won't happen, but safe fallback
                return f"invalid input parameter at index {i}"

        # Join with hyphens; equivalent to appending "-" per piece and trimming trailing "-"
        return "-".join(parts)
