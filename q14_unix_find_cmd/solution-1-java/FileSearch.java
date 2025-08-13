import java.util.*;

/**
 * In-memory "find" style file search with extensible rules.
 *
 * Rules:
 *  1) Files strictly larger than a given size in MB (args = "minSizeMb", e.g., "5")
 *  2) Files with a given extension (args = ".ext", e.g., ".xml")
 *
 * Design allows adding more rules via registerRule(int id, Rule r).
 */
public class FileSearch {

    /** File path -> size (MB). A map guarantees uniqueness of paths (no duplicates). */
    private final Map<String, Integer> files = new HashMap<>();

    /** Rule interface (Strategy) for matching a file against a parameter string. */
    @FunctionalInterface
    interface Rule {
        boolean matches(String path, int sizeMb, String args);
    }

    /** Registered rules by id. */
    private final Map<Integer, Rule> rules = new HashMap<>();

    public FileSearch() {
        // Rule 1: strictly larger than given size (MB)
        rules.put(1, (path, sizeMb, args) -> {
            int min;
            try {
                min = Integer.parseInt(args.trim());
            } catch (Exception e) {
                // If bad args, treat as non-match
                return false;
            }
            return sizeMb > min;
        });

        // Rule 2: file has given extension (case-sensitive), args includes the dot, e.g. ".xml"
        rules.put(2, (path, sizeMb, args) -> path.endsWith(args));
    }

    /**
     * Optionally allow adding/extending rules later.
     */
    public void registerRule(int ruleId, Rule rule) {
        rules.put(ruleId, rule);
    }

    /**
     * Adds or replaces a file entry at the given absolute path.
     * @param path absolute path like "/dir/sub/file.ext"
     * @param sizeMb integer size in MB
     */
    public void putFile(String path, int sizeMb) {
        // Minimal guard; per constraints inputs are absolute and valid.
        if (path == null || path.isEmpty() || path.charAt(0) != '/') return;
        // Overwrite on duplicate path per spec.
        files.put(path, sizeMb);
    }

    /**
     * Finds files by rule under dirPath (recursive), returning sorted lexicographically ascending.
     * @param ruleId which rule to apply (1: min-size, 2: extension)
     * @param dirPath absolute directory path (e.g., "/data")
     * @param args argument string consumed by the rule (e.g., "8" or ".xml")
     * @return ascending lexicographically sorted list of matching file paths
     */
    public List<String> search(int ruleId, String dirPath, String args) {
        Rule rule = rules.get(ruleId);
        if (rule == null) {
            // Unknown rule -> empty result (or could throw)
            return new ArrayList<>();
        }

        String normalizedDir = normalizeDir(dirPath);
        List<String> out = new ArrayList<>();

        for (Map.Entry<String, Integer> e : files.entrySet()) {
            String path = e.getKey();
            int size = e.getValue();

            if (isUnderDir(path, normalizedDir) && rule.matches(path, size, args)) {
                out.add(path);
            }
        }

        Collections.sort(out); // strict ascending lexicographical order
        return out;
    }

    /**
     * Normalize directory path:
     *  - Keep "/" as-is
     *  - Remove trailing slashes for other directories ("/data///" -> "/data")
     */
    private static String normalizeDir(String dir) {
        if (dir == null || dir.isEmpty()) return "/";
        if ("/".equals(dir)) return "/";
        int end = dir.length();
        while (end > 1 && dir.charAt(end - 1) == '/') end--;
        return dir.substring(0, end);
    }

    /**
     * Returns true if file path is under the given directory (recursive).
     * Root "/" contains every absolute path.
     * For non-root dir D, a file path is "under" D iff it starts with "D/".
     */
    private static boolean isUnderDir(String filePath, String dir) {
        if ("/".equals(dir)) {
            return filePath.startsWith("/");
        }
        String prefix = dir + "/";
        return filePath.startsWith(prefix);
    }
}
