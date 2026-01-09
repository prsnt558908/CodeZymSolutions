class MinimumOperationsToSortPermutation:
    def __init__(self):
        pass

    def getMinimumOperations(self, arr):
        """
        Allowed operations:
          1) Reverse the entire array
          2) Left-rotate by 1 (move arr[0] to the end)

        Key fact: These operations generate exactly the dihedral actions on positions,
        so the array can be sorted iff it is a rotation of [1..n] or a rotation of reverse([1..n]).
        We compute the unique candidate rotation amount using the position of '1' and verify it.

        Time:  O(n)
        Space: O(1)
        """
        n = len(arr)
        if n <= 1:
            return 0

        # Find where '1' is (unique in a permutation).
        idx1 = -1
        for i, v in enumerate(arr):
            if v == 1:
                idx1 = i
                break

        INF = 10**18
        ans = INF

        # ----------------------------
        # Case 1: Sorted is a rotation of arr
        # Need k = idx1 so that rotate_left(arr, k) starts with 1.
        # Verify rotate_left(arr, k) == [1..n]
        k = idx1
        ok = True
        for i in range(n):
            if arr[(k + i) % n] != i + 1:
                ok = False
                break
        if ok:
            # We can do k left-shifts directly, or do (n-k) right-shifts using 2 reverses:
            # right shift by 1 = reverse + left shift + reverse
            # so left shift by (n-k) can be done as 2 + (n-k).
            ans = min(ans, min(k, 2 + (n - k)))

        # ----------------------------
        # Case 2: Sorted is a rotation of reverse(arr)
        # reverse(arr) puts original index i at position (n-1-i).
        # Index of '1' in reverse(arr) is k_ref = (n-1-idx1)
        # Verify rotate_left(reverse(arr), k_ref) == [1..n] without building reverse(arr).
        k_ref = (n - 1 - idx1)
        ok = True
        for i in range(n):
            j = (k_ref + i) % n              # index in reverse(arr)
            if arr[n - 1 - j] != i + 1:      # reverse(arr)[j] == arr[n-1-j]
                ok = False
                break
        if ok:
            # We can realize this reflection either as:
            #   reverse then k_ref left shifts  -> 1 + k_ref
            # or
            #   (n-k_ref) left shifts then reverse -> 1 + (n-k_ref)
            ans = min(ans, 1 + min(k_ref, n - k_ref))

        # Problem guarantees solvable, but keep a safe fallback.
        return ans if ans < INF else -1
