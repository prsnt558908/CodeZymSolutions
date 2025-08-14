import java.util.*;

public class FileFinder {

    // Stores absolute file path -> size in MB
    private final Map<String, Integer> files = new HashMap<>();

    public FileFinder() { }

    /**
     * Add or replace a file entry at the given absolute path.
     * Assumptions per spec:
     * - path is valid and absolute
     * - sizeMb >= 0
     */
    public void addFile(String path, int sizeMb) {
        files.put(path, sizeMb);
    }

    /**
     * Evaluate a boolean chain of rule results, left-to-right, using operators:
     * "AND", "OR", "AND NOT".
     *
     * rules: each string is "ruleId,dirPath,arg"
     *   - Rule 1: "1,DIR,minSizeMb"  -> files strictly larger than minSizeMb under DIR (recursive)
     *   - Rule 2: "2,DIR,.ext"       -> files whose names end with .ext under DIR (recursive)
     *
     * ops: size must be rules.size() - 1
     *   Combine as: (((r1 op0 r2) op1 r3) op2 r4) ...
     *
     * Return lexicographically ascending unique list.
     */
    public List<String> runQuery(List<String> rules, List<String> ops) {
        if (rules == null || rules.isEmpty()) return new ArrayList<>();
        if (ops == null) ops = Collections.emptyList();

        // Precompute each rule's result set
        List<Set<String>> ruleResults = new ArrayList<>(rules.size());
        for (String rule : rules) {
            ruleResults.add(evaluateRule(rule));
        }

        // Left-to-right fold over operators
        Set<String> acc = new HashSet<>(ruleResults.get(0));
        for (int i = 1; i < ruleResults.size(); i++) {
            String op = ops.get(i - 1).trim().toUpperCase(Locale.ROOT);
            Set<String> rhs = ruleResults.get(i);

            switch (op) {
                case "AND":
                    acc.retainAll(rhs);
                    break;
                case "OR":
                    acc.addAll(rhs);
                    break;
                case "AND NOT":
                    acc.removeAll(rhs);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + op);
            }
        }

        // Sort ascending, de-dup guaranteed by Set
        List<String> out = new ArrayList<>(acc);
        Collections.sort(out);
        return out;
    }

    // --------------------- helpers ---------------------

    private Set<String> evaluateRule(String ruleStr) {
        // Expect: "ruleId,dirPath,arg"
        String[] parts = splitAndTrim(ruleStr);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid rule: " + ruleStr);
        }

        int ruleId = Integer.parseInt(parts[0]);
        String dirPath = parts[1];
        String arg = parts[2];

        switch (ruleId) {
            case 1: // size strictly greater than min
                int minSize = Integer.parseInt(arg);
                return evalMinSize(dirPath, minSize);
            case 2: // extension match by final suffix (case-sensitive)
                return evalExtension(dirPath, arg);
            default:
                throw new IllegalArgumentException("Unknown ruleId: " + ruleId);
        }
    }

    private Set<String> evalMinSize(String dirPath, int minSize) {
        Set<String> res = new HashSet<>();
        for (Map.Entry<String, Integer> e : files.entrySet()) {
            String path = e.getKey();
            int size = e.getValue();
            if (isUnderDir(path, dirPath) && size > minSize) {
                res.add(path);
            }
        }
        return res;
    }

    private Set<String> evalExtension(String dirPath, String ext) {
        Set<String> res = new HashSet<>();
        for (String path : files.keySet()) {
            if (isUnderDir(path, dirPath) && path.endsWith(ext)) {
                res.add(path);
            }
        }
        return res;
    }

    /**
     * Recursive directory check:
     * - For dirPath = "/", match all absolute paths (start with "/").
     * - Else, match paths that start with dirPath + "/".
     *   (Prevents "/app/logs2" from matching "/app/logs".)
     */
    private boolean isUnderDir(String filePath, String dirPath) {
        if ("/".equals(dirPath)) {
            return filePath.startsWith("/");
        }
        String prefix = dirPath.endsWith("/") ? dirPath : (dirPath + "/");
        return filePath.startsWith(prefix);
    }

    private String[] splitAndTrim(String s) {
        String[] raw = s.split(",", -1);
        String[] out = new String[raw.length];
        for (int i = 0; i < raw.length; i++) out[i] = raw[i].trim();
        return out;
    }
}
