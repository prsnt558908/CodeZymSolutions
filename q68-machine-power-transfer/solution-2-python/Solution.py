class MaximizeMachineStrength:
    def __init__(self):
        pass

    def getStrength(self, powers):
        if not powers:
            return 0

        global_min = None
        sum_second = 0
        min_second = None

        for csv in powers:
            min1, min2 = self._two_min_from_csv(csv)  # min1 = smallest, min2 = 2nd smallest (can be equal)

            if global_min is None or min1 < global_min:
                global_min = min1

            sum_second += min2

            if min_second is None or min2 < min_second:
                min_second = min2

        # Maximum achievable = global minimum must remain as a machine strength,
        # and for every other machine we can keep its second-minimum as its strength.
        return global_min + (sum_second - min_second)

    def _two_min_from_csv(self, csv):
        if csv is None:
            raise ValueError("powers[i] cannot be None")

        # Split by comma; ignore whitespace around tokens
        parts = [p.strip() for p in csv.split(",") if p.strip() != ""]
        if len(parts) < 2:
            raise ValueError(f"Each machine must have at least 2 power units: {csv}")

        min1 = None
        min2 = None

        for token in parts:
            x = int(token)  # supports negatives too

            if min1 is None or x < min1:
                min2 = min1
                min1 = x
            elif min2 is None or x < min2:
                min2 = x

        # With at least 2 numbers, min2 will be set.
        return min1, min2
