import java.util.*;

public class MiniRpcFramework {

    private static final String TYPE_STRING = "String";
    private static final String TYPE_LONG = "Long";
    private static final String TYPE_DOUBLE = "Double";

    private static class MethodKey {
        final int serviceId;
        final String methodName;

        MethodKey(int serviceId, String methodName) {
            this.serviceId = serviceId;
            this.methodName = methodName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodKey)) return false;
            MethodKey other = (MethodKey) o;
            return serviceId == other.serviceId && Objects.equals(methodName, other.methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceId, methodName);
        }
    }

    // Registry: (serviceId, methodName) -> parameter types
    private final Map<MethodKey, List<String>> registry;

    public MiniRpcFramework() {
        this.registry = new HashMap<>();
    }

    public void registerMethod(int serviceId, String methodName, List<String> inputParameterTypes) {
        // Store a defensive copy since caller can mutate the list later
        MethodKey key = new MethodKey(serviceId, methodName);
        registry.put(key, new ArrayList<>(inputParameterTypes));
    }

    public boolean unregisterMethod(int serviceId, String methodName) {
        MethodKey key = new MethodKey(serviceId, methodName);
        return registry.remove(key) != null;
    }

    public String callMethod(int serviceId, String methodName, List<String> inputValues) {
        MethodKey key = new MethodKey(serviceId, methodName);

        List<String> types = registry.get(key);
        if (types == null) {
            return "method does not exist";
        }

        if (inputValues == null || inputValues.size() != types.size()) {
            return "input size mismatch";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < inputValues.size(); i++) {
            String t = types.get(i);
            String raw = inputValues.get(i);

            if (TYPE_STRING.equals(t)) {
                // (i+1) + "-" + inputValues[i] + "-"
                sb.append(i + 1).append('-').append(raw).append('-');
            } else if (TYPE_LONG.equals(t)) {
                long v;
                try {
                    v = Long.parseLong(raw);
                } catch (Exception e) {
                    return "invalid input parameter at index " + i;
                }
                long computed = (long) (i + 1) * (long) serviceId * v;
                sb.append(computed).append('-');
            } else if (TYPE_DOUBLE.equals(t)) {
                double d;
                try {
                    d = Double.parseDouble(raw);
                } catch (Exception e) {
                    return "invalid input parameter at index " + i;
                }
                double x = (double) (i + 1) * (double) serviceId * 2.0 * d;
                int truncated = (int) x; // truncation towards zero, e.g. 75.6 -> 75
                sb.append(truncated).append('-');
            } else {
                // Spec says this won't happen, but safe fallback
                return "invalid input parameter at index " + i;
            }
        }

        // remove last trailing hyphen
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
}
