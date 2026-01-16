class MinimumErrorsInBinaryString:
    def __init__(self):
        pass

    def getMinErrors(self, errorString, x, y):
        """
        Minimize:
          errors = x * (# of subsequences "01") + y * (# of subsequences "10")
        after replacing each '!' by '0' or '1', where subsequences are i<j pairs.

        Observations:
        - If x <= y, an optimal assignment exists where all '!' are 0...0 then 1...1 (single split).
          Use baseline all '!' = '1', then flip prefix '!' from 1 -> 0 and track best.
        - If x > y, an optimal assignment exists where all '!' are 1...1 then 0...0 (single split).
          Use baseline all '!' = '0', then flip prefix '!' from 0 -> 1 and track best.

        Time: O(n), Space: O(n) for prefix ones.
        """
        MOD = 1_000_000_007
        n = len(errorString)
        if n == 0:
            return 0

        prefer01 = (x <= y)
        bang_base = '1' if prefer01 else '0'  # baseline replacement for '!'

        prefix_ones = [0] * (n + 1)
        bang_positions = []

        ones = 0
        zeros = 0
        cost = 0

        # Build baseline string implicitly, compute baseline cost in one pass.
        for i, ch in enumerate(errorString):
            if ch == '!':
                ch = bang_base
                bang_positions.append(i)

            prefix_ones[i + 1] = prefix_ones[i] + (1 if ch == '1' else 0)

            if ch == '0':
                # This '0' forms "10" with all previous ones.
                cost += y * ones
                zeros += 1
            else:  # ch == '1'
                # This '1' forms "01" with all previous zeros.
                cost += x * zeros
                ones += 1

        min_cost = cost
        total_ones_base = ones

        # Flip '!' positions one by one (left to right).
        # If prefer01: flip 1->0; else flip 0->1.
        for step, pos in enumerate(bang_positions, start=1):
            already_flipped = step - 1

            ones_before_base = prefix_ones[pos]

            # Total ones before flipping at this step (after already_flipped flips):
            ones_total_before = total_ones_base + (-already_flipped if prefer01 else already_flipped)

            # Ones before pos in current assignment:
            ones_before = ones_before_base + (-already_flipped if prefer01 else already_flipped)
            zeros_before = pos - ones_before

            bit_old = 1 if bang_base == '1' else 0  # current bit at pos before flip
            zeros_total_before = n - ones_total_before

            ones_after = ones_total_before - ones_before - bit_old
            zeros_after = zeros_total_before - zeros_before - (1 - bit_old)

            if prefer01:
                # Flip 1 -> 0 at pos
                delta = (y * ones_before - x * zeros_before) + (x * ones_after - y * zeros_after)
            else:
                # Flip 0 -> 1 at pos
                delta = (x * zeros_before - y * ones_before) + (y * zeros_after - x * ones_after)

            cost += delta
            if cost < min_cost:
                min_cost = cost

        return int(min_cost % MOD)
