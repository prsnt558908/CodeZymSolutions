class WorkroomRemoteness:
    def __init__(self):
        pass

    def getMaximumRemoteness(self, skill, room):
        """
        Returns the maximum "remoteness" among all valid assignments.

        We assign developers (skill[0..n-1]) to increasing indices in `room`
        such that room[pos[i]] == skill[i]. Remoteness is:
            max_i (pos[i+1] - pos[i] - 1)

        Approach (O(n + m)):
        - left[i]  = earliest index in room where skill[0..i] can be matched (greedy from left)
        - right[i] = latest index in room where skill[i..n-1] can be matched (greedy from right)
        - For adjacent pair (i, i+1), the best possible gap is:
              right[i+1] - left[i] - 1
          Take max over all pairs.
        """
        n = len(skill)
        if n <= 1:
            return 0

        m = len(room)
        left = [0] * n
        right = [0] * n

        # Earliest positions (prefix match)
        j = 0
        for i in range(n):
            need = skill[i]
            while j < m and room[j] != need:
                j += 1
            # Under valid constraints, j will be < m here.
            left[i] = j
            j += 1

        # Latest positions (suffix match)
        j = m - 1
        for i in range(n - 1, -1, -1):
            need = skill[i]
            while j >= 0 and room[j] != need:
                j -= 1
            right[i] = j
            j -= 1

        ans = 0
        for i in range(n - 1):
            gap = right[i + 1] - left[i] - 1
            if gap > ans:
                ans = gap
        return ans
