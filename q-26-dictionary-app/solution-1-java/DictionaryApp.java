import java.util.*;

/**
 * Design Dictionary App to store words and their meanings.
 *
 * Features:
 * - storeWord(word, meaning): insert or overwrite meaning for a word.
 *   * Inputs are lowercase, non-blank.
 *   * word consists of [a-z], space ' ', and hyphen '-'.
 *   * word never contains '.'.
 * - getMeaning(word): fetch meaning or "" if absent.
 * - searchWords(prefix, n): up to n words starting with prefix, lexicographically ascending.
 *   * 1 <= n <= 20 (clamped defensively).
 * - exists(pattern): checks if at least one stored word matches pattern with '.' wildcards.
 *   * '.' matches exactly one character (letter, space, hyphen, etc.).
 *   * Non-dot characters must match exactly; pattern length must equal word length.
 *   * Stored words never contain '.', only the query pattern may include dots.
 *
 * Implementation notes:
 * - HashMap<String,String> for word → meaning.
 * - TreeSet<String> to maintain words in sorted order for prefix search.
 * - Map<Integer, Set<String>> buckets by length to speed up wildcard existence checks.
 */
public class DictionaryApp {

    // word -> meaning
    private final Map<String, String> dict;

    // All words kept sorted for efficient prefix scans
    private final NavigableSet<String> wordsSorted;

    // Length -> set of words with that exact length (for exists(pattern))
    private final Map<Integer, Set<String>> wordsByLen;

    public DictionaryApp() {
        this.dict = new HashMap<>();
        this.wordsSorted = new TreeSet<>();
        this.wordsByLen = new HashMap<>();
    }

    /**
     * Insert or update the mapping (word → meaning).
     * Assumes inputs are lowercase and non-blank; word has [a-z -], no '.'.
     */
    public void storeWord(String word, String meaning) {
        Objects.requireNonNull(word, "word");
        Objects.requireNonNull(meaning, "meaning");

        boolean isNew = !dict.containsKey(word);
        dict.put(word, meaning);

        if (isNew) {
            wordsSorted.add(word);
            int len = word.length();
            wordsByLen.computeIfAbsent(len, k -> new HashSet<>()).add(word);
        }
        // If overwrite, no structural change needed (same word & length).
    }

    /**
     * Return the meaning for word if present, else "".
     */
    public String getMeaning(String word) {
        Objects.requireNonNull(word, "word");
        return dict.getOrDefault(word, "");
    }

    /**
     * Return up to n words starting with prefix, sorted lexicographically ascending.
     * If fewer than n matches exist, return all matches.
     * 
     */
    public List<String> searchWords(String prefix, int n) {
        Objects.requireNonNull(prefix, "prefix");
        int limit = Math.max(1, n);
        List<String> result = new ArrayList<>(limit);

        for (String w : wordsSorted.tailSet(prefix, true)) {
            if (!w.startsWith(prefix)) break;
            result.add(w);
            if (result.size() == limit) break;
        }
        return result;
    }

    /**
     * Return true if at least one stored word matches the pattern.
     * '.' in pattern matches exactly one character (letter/space/hyphen/etc.).
     * Non-dot characters must match exactly (including spaces and hyphens).
     * Pattern length must equal candidate word length.
     */
    public boolean exists(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        int len = pattern.length();
        Set<String> candidates = wordsByLen.get(len);
        if (candidates == null || candidates.isEmpty()) return false;

        for (String w : candidates) {
            if (matchesPattern(w, pattern)) {
                return true;
            }
        }
        return false;
    }

    // Helper: check if 'word' matches 'pattern' under the '.' rule.
    private boolean matchesPattern(String word, String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char pc = pattern.charAt(i);
            char wc = word.charAt(i);
            if (pc == '.') {
                // '.' matches any single character (letter, space, hyphen, etc.)
                // No additional check needed.
            } else {
                if (pc != wc) return false;
            }
        }
        return true;
    }
}
