import java.util.*;

/**
 * In-memory tracker for files and their collection memberships.
 *
 * Supports:
 *  - addFile(fileName, fileSize, collections)
 *  - getTotalFileSize()
 *  - getTopCollections(strategy)
 */
public class FileCollectionsTracker {

    /** Per-file metadata. */
    private static class FileRecord {
        int size;                 // in KB
        Set<String> collections;  // deduped collection names

        FileRecord(int size, Set<String> collections) {
            this.size = size;
            this.collections = collections;
        }
    }

    /** Per-collection aggregates. */
    private static class CollectionStats {
        long totalSize;  // sum of sizes of member files
        int fileCount;   // number of member files
    }

    // fileName -> record
    private final Map<String, FileRecord> files = new HashMap<>();
    // collectionName -> stats
    private final Map<String, CollectionStats> collectionStats = new HashMap<>();
    // global total size of all files
    private long totalFileSize = 0L;

    public FileCollectionsTracker() {
        // nothing else to init
    }

    public void addFile(String fileName, int fileSize, List<String> collections) {
        if (fileName == null || fileName.isEmpty()) {
            return; // as per constraints, should not happen; ignore defensively
        }
        if (fileSize <= 0) {
            return; // constraints say positive; ignore defensively
        }

        // Normalize collections to a deduped set (treat null as empty)
        Set<String> newCollections = new HashSet<>();
        if (collections != null) {
            for (String c : collections) {
                if (c != null && !c.isEmpty()) {
                    newCollections.add(c);
                }
            }
        }

        FileRecord oldRecord = files.get(fileName);
        if (oldRecord != null) {
            int oldSize = oldRecord.size;
            Set<String> oldCollections = oldRecord.collections;

            // Update global total
            totalFileSize += (long) fileSize - (long) oldSize;

            // Remove old contributions
            for (String col : oldCollections) {
                CollectionStats stats = collectionStats.get(col);
                if (stats != null) {
                    stats.totalSize -= oldSize;
                    stats.fileCount -= 1;
                    if (stats.fileCount == 0) {
                        collectionStats.remove(col);
                    }
                }
            }

            // Add new contributions
            for (String col : newCollections) {
                CollectionStats stats = collectionStats.computeIfAbsent(col, k -> new CollectionStats());
                stats.totalSize += fileSize;
                stats.fileCount += 1;
            }

            // Replace old record
            oldRecord.size = fileSize;
            oldRecord.collections = newCollections;

        } else {
            // New file
            files.put(fileName, new FileRecord(fileSize, newCollections));
            totalFileSize += fileSize;

            for (String col : newCollections) {
                CollectionStats stats = collectionStats.computeIfAbsent(col, k -> new CollectionStats());
                stats.totalSize += fileSize;
                stats.fileCount += 1;
            }
        }
    }

    public int getTotalFileSize() {
        if (totalFileSize <= 0L) return 0;
        // Expected to be within int range in typical interview tests
        if (totalFileSize > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) totalFileSize;
    }

    public List<String> getTopCollections(int strategy) {
        List<String> names = new ArrayList<>(collectionStats.keySet());

        Comparator<String> cmp;
        if (strategy == 0) {
            // sort by total size desc, then name asc
            cmp = (a, b) -> {
                CollectionStats sa = collectionStats.get(a);
                CollectionStats sb = collectionStats.get(b);
                long sizeA = (sa == null) ? 0L : sa.totalSize;
                long sizeB = (sb == null) ? 0L : sb.totalSize;

                if (sizeA != sizeB) {
                    return Long.compare(sizeB, sizeA); // desc
                }
                return a.compareTo(b); // lex asc
            };
        } else {
            // strategy == 1: sort by file count desc, then name asc
            cmp = (a, b) -> {
                CollectionStats sa = collectionStats.get(a);
                CollectionStats sb = collectionStats.get(b);
                int countA = (sa == null) ? 0 : sa.fileCount;
                int countB = (sb == null) ? 0 : sb.fileCount;

                if (countA != countB) {
                    return Integer.compare(countB, countA); // desc
                }
                return a.compareTo(b); // lex asc
            };
        }

        names.sort(cmp);

        int k = Math.min(10, names.size());
        return new ArrayList<>(names.subList(0, k));
    }
}
