import java.util.*;

public class FileSystemShell {

    /** Directory node */
    private static final class Dir {
        final String name;
        Dir parent;
        // TreeMap to enable lexicographic smallest child retrieval in O(log K)
        final TreeMap<String, Dir> children = new TreeMap<>();

        Dir(String name, Dir parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    private final Dir root;
    private Dir cwd;

    public FileSystemShell() {
        this.root = new Dir("", null);
        // Root's parent is itself to simplify ".." at "/"
        this.root.parent = this.root;
        this.cwd = this.root;
    }

    /** Returns absolute path of current working directory; root -> "/" */
    public String pwd() {
        if (cwd == root) return "/";
        Deque<String> stack = new ArrayDeque<>();
        Dir cur = cwd;
        while (cur != root) {
            stack.push(cur.name);
            cur = cur.parent;
        }
        StringBuilder sb = new StringBuilder();
        while (!stack.isEmpty()) {
            sb.append('/').append(stack.pop());
        }
        return sb.toString();
    }

    /**
     * mkdir -p semantics:
     * - Absolute path starts at root, relative starts at cwd.
     * - Creates intermediate parents as needed.
     * - Interprets "." and ".." normally; rejects "*" (out of scope for mkdir).
     * - Idempotent if directory already exists.
     */
    public void mkdir(String path) {
        Objects.requireNonNull(path, "path");
        Dir cur = isAbsolute(path) ? root : cwd;

        for (String seg : tokenize(path)) {
            if (seg.equals(".")) {
                // no-op
                continue;
            } else if (seg.equals("..")) {
                // go to parent; at root remains root
                cur = (cur == root) ? root : cur.parent;
            } else if (seg.equals("*")) {
                // Per spec: mkdir path never contains '*'
                // Silently ignore OR throw — choose one. We'll ignore to be lenient.
                // throw new IllegalArgumentException("mkdir: '*' wildcard not allowed in path");
                continue;
            } else {
                // create or descend
                Dir next = cur.children.get(seg);
                if (next == null) {
                    next = new Dir(seg, cur);
                    cur.children.put(seg, next);
                }
                cur = next;
            }
        }
    }

    /**
     * cd with wildcard '*':
     * - Absolute or relative.
     * - "." and ".." normalized.
     * - "*" as a full segment only; resolves deterministically:
     *     1) If children exist, pick lexicographically smallest child.
     *     2) Else, prefer "." (stay).
     * - On any unresolved normal segment, command fails and CWD remains unchanged.
     */
    public void cd(String path) {
        Objects.requireNonNull(path, "path");
        Dir trial = isAbsolute(path) ? root : cwd;

        for (String seg : tokenize(path)) {
            if (seg.equals(".")) {
                // stay
            } else if (seg.equals("..")) {
                trial = (trial == root) ? root : trial.parent;
            } else if (seg.equals("*")) {
                if (!trial.children.isEmpty()) {
                    String pick = trial.children.firstKey(); // lexicographically smallest child
                    trial = trial.children.get(pick);
                } else {
                    // prefer "." (no-op)
                }
            } else {
                Dir next = trial.children.get(seg);
                if (next == null) {
                    // Failure: leave CWD unchanged
                    return;
                }
                trial = next;
            }
        }

        // Success: commit
        this.cwd = trial;
    }

    // -------------------- helpers --------------------

    private static boolean isAbsolute(String path) {
        return path.startsWith("/");
    }

    /**
     * Manual tokenizer for path segments:
     * - Treat multiple consecutive slashes as one (skip empties).
         - Preserves every ".." occurrence (critical for deep pops).
     */
    private static List<String> tokenize(String path) {
        List<String> out = new ArrayList<>();
        int n = path.length();
        int i = 0;

        // Optional trim to ignore surrounding whitespace
        // but DO NOT modify internal characters
        while (i < n && Character.isWhitespace(path.charAt(i))) i++;
        int j = n - 1;
        while (j >= 0 && Character.isWhitespace(path.charAt(j))) j--;
        if (i > j) return out;

        int start = i;
        for (int k = i; k <= j; k++) {
            char c = path.charAt(k);
            if (c == '/') {
                if (k > start) {
                    String seg = path.substring(start, k);
                    if (!seg.isEmpty()) out.add(seg);
                }
                // skip consecutive slashes
                while (k + 1 <= j && path.charAt(k + 1) == '/') k++;
                start = k + 1;
            }
        }
        if (start <= j) {
            String seg = path.substring(start, j + 1);
            if (!seg.isEmpty()) out.add(seg);
        }
        return out;
    }
}