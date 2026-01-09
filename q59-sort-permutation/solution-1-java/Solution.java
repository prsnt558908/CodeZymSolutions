import java.util.ArrayDeque;
import java.util.Arrays;

public class MinimumOperationsToSortPermutation {

    public MinimumOperationsToSortPermutation() {

    }

    public int getMinimumOperations(int[] arr) {
        int n = arr.length;
        if (n <= 1) return 0;

        // Find index of value 1
        int pos1 = -1;
        for (int i = 0; i < n; i++) {
            if (arr[i] == 1) {
                pos1 = i;
                break;
            }
        }
        if (pos1 == -1) return -1; // should not happen for a valid permutation

        // Check if sorted can be obtained by:
        // 1) only rotations (no reverse): out[i] = arr[(i + pos1) % n]
        boolean okRotate = true;
        for (int i = 0; i < n; i++) {
            int idx = pos1 + i;
            if (idx >= n) idx -= n;
            if (arr[idx] != i + 1) {
                okRotate = false;
                break;
            }
        }

        // 2) reverse + rotation: out[i] = arr[(pos1 - i) mod n]
        boolean okReverse = true;
        for (int i = 0; i < n; i++) {
            int idx = pos1 - i;
            if (idx < 0) idx += n;
            if (arr[idx] != i + 1) {
                okReverse = false;
                break;
            }
        }

        // BFS on dihedral group elements (2n states):
        // Represent transform by (flip, b) where:
        // flip=0 => out[i] = arr[( i + b) mod n]
        // flip=1 => out[i] = arr[(-i + b) mod n]
        //
        // Operations:
        // r: left-rotate by 1 => (flip, b) -> (flip, b+1) if flip=0 else (flip, b-1)
        // f: reverse          => (flip, b) -> (1-flip, b+(n-1)) if flip=0 else (1-flip, b+1)
        int[][] dist = new int[2][n];
        Arrays.fill(dist[0], -1);
        Arrays.fill(dist[1], -1);

        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[0][0] = 0;
        q.addLast(new int[]{0, 0});

        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int flip = cur[0];
            int b = cur[1];
            int d = dist[flip][b];

            // Apply r (left rotate by 1)
            int nbR;
            if (flip == 0) {
                nbR = b + 1;
                if (nbR == n) nbR = 0;
            } else {
                nbR = b - 1;
                if (nbR < 0) nbR += n;
            }
            if (dist[flip][nbR] == -1) {
                dist[flip][nbR] = d + 1;
                q.addLast(new int[]{flip, nbR});
            }

            // Apply f (reverse)
            int nflip = 1 - flip;
            int nbF;
            if (flip == 0) {
                nbF = b + (n - 1);
                if (nbF >= n) nbF -= n;
            } else {
                nbF = b + 1;
                if (nbF == n) nbF = 0;
            }
            if (dist[nflip][nbF] == -1) {
                dist[nflip][nbF] = d + 1;
                q.addLast(new int[]{nflip, nbF});
            }
        }

        int ans = Integer.MAX_VALUE;
        if (okRotate) ans = Math.min(ans, dist[0][pos1]);
        if (okReverse) ans = Math.min(ans, dist[1][pos1]);

        // Guaranteed solvable per statement
        return ans == Integer.MAX_VALUE ? -1 : ans;
    }
}
