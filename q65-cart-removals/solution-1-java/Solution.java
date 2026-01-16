import java.util.*;

/**
 * O(n log n) expected, WITHOUT Fenwick tree.
 *
 * For each i, we must keep cartItems[i] and remove the minimum number of earlier items
 * so that sum(kept items in [0..i]) <= budget.
 *
 * Since all prices are positive, to minimize removals (maximize kept count),
 * we keep as many smallest items as possible from the previous prefix [0..i-1]
 * under capacity = budget - cartItems[i].
 *
 * We maintain a randomized Treap (BST by value, heap by priority) with:
 * - cnt: frequency of the value
 * - size: total count in subtree
 * - sum: total sum in subtree
 *
 * Query: maximum number of smallest elements whose total sum <= capacity.
 */
public class CartRemovalsWithinBudget {

    public CartRemovalsWithinBudget() {
    }

    public List<Integer> getMinRemovalsWithinBudget(int budget, List<Integer> cartItems) {
        int n = cartItems.size();
        List<Integer> ans = new ArrayList<>(n);

        Treap treap = new Treap();

        for (int i = 0; i < n; i++) {
            int v = cartItems.get(i);
            long cap = (long) budget - (long) v;

            // Per constraints, v <= budget, so cap >= 0. Handle defensively anyway.
            if (cap < 0) {
                ans.add(i); // remove all previous (still wouldn't fit, but shouldn't happen per constraints)
            } else {
                int keptFromPrev = treap.maxCountWithinSum(cap);
                ans.add(i - keptFromPrev); // i previous items exist; remove the rest
            }

            treap.insert(v);
        }

        return ans;
    }

    // ---------------- Treap Implementation ----------------
    static class Treap {
        private static final class Node {
            int key;
            int pr;
            int cnt;      // duplicates of key
            int size;     // total elements in subtree
            long sum;     // sum of values in subtree
            Node left, right;

            Node(int key, int pr) {
                this.key = key;
                this.pr = pr;
                this.cnt = 1;
                recalc();
            }

            void recalc() {
                int leftSize = (left == null) ? 0 : left.size;
                int rightSize = (right == null) ? 0 : right.size;
                long leftSum = (left == null) ? 0L : left.sum;
                long rightSum = (right == null) ? 0L : right.sum;
                this.size = leftSize + cnt + rightSize;
                this.sum = leftSum + (long) key * (long) cnt + rightSum;
            }
        }

        // Deterministic seed helps reproducibility in judge environments.
        private final Random rnd = new Random(1);
        private Node root = null;

        public void insert(int key) {
            // Split root into <= key and > key
            SplitPair p1 = splitLE(root, key);
            // Split left part into <= key-1 and == key
            SplitPair p2 = splitLE(p1.left, key - 1);

            Node equal = p2.right;
            if (equal == null) {
                equal = new Node(key, rnd.nextInt());
            } else {
                equal.cnt++;
                equal.recalc();
            }

            root = merge(merge(p2.left, equal), p1.right);
        }

        /**
         * Returns the maximum number of smallest elements whose total sum <= cap.
         * Greedy over increasing keys is optimal (all values are positive).
         */
        public int maxCountWithinSum(long cap) {
            int kept = 0;
            Node cur = root;

            while (cur != null) {
                long leftSum = (cur.left == null) ? 0L : cur.left.sum;
                int leftSize = (cur.left == null) ? 0 : cur.left.size;

                if (leftSum > cap) {
                    // Can't take all from left; go deeper into left subtree.
                    cur = cur.left;
                    continue;
                }

                // Take entire left subtree
                cap -= leftSum;
                kept += leftSize;

                // Take as many as possible from current node's duplicates
                long canTake = cap / (long) cur.key; // key >= 1
                if (canTake <= 0) {
                    break; // can't take this key => can't take any larger keys either
                }
                long take = Math.min(canTake, (long) cur.cnt);
                kept += (int) take;
                cap -= take * (long) cur.key;

                if (take < cur.cnt) {
                    break; // couldn't take all duplicates of this key; can't take larger keys
                }

                // Move to right subtree (larger keys)
                cur = cur.right;
            }

            return kept;
        }

        // ---------- Treap helpers: split and merge ----------
        private static final class SplitPair {
            Node left;  // <= key
            Node right; // > key
            SplitPair(Node left, Node right) { this.left = left; this.right = right; }
        }

        // Splits by key: left has keys <= key, right has keys > key
        private SplitPair splitLE(Node node, int key) {
            if (node == null) return new SplitPair(null, null);

            if (node.key <= key) {
                SplitPair p = splitLE(node.right, key);
                node.right = p.left;
                node.recalc();
                return new SplitPair(node, p.right);
            } else {
                SplitPair p = splitLE(node.left, key);
                node.left = p.right;
                node.recalc();
                return new SplitPair(p.left, node);
            }
        }

        private Node merge(Node a, Node b) {
            if (a == null) return b;
            if (b == null) return a;

            if (a.pr > b.pr) {
                a.right = merge(a.right, b);
                a.recalc();
                return a;
            } else {
                b.left = merge(a, b.left);
                b.recalc();
                return b;
            }
        }
    }
}
