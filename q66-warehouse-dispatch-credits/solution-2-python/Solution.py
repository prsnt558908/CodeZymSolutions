class WarehouseDispatchCredits:
    def __init__(self):
        pass

    def maxCredits(self, inventory, dispatch1, dispatch2, skips):
        """
        For a warehouse with inventory x:
        - Normal round removes t = dispatch1 + dispatch2 (you then coworker).
        - Let rem = x mod t, but treat rem=0 as rem=t (end would be on coworker without skips).
        - If rem <= dispatch1, you already finish on your turn (0 skips).
        - Otherwise, you can force extra consecutive "your" turns by making coworker skip.
          Each skip gives one extra dispatch1 before coworker can act.
          Minimum skips needed = ceil(rem / dispatch1) - 1 = floor((rem - 1) / dispatch1).

        Global optimum: each credit is value 1, cost = skips_needed.
        Take warehouses with smallest costs until skip budget is exhausted.
        """
        a = int(dispatch1)
        b = int(dispatch2)
        k = int(skips)
        t = a + b

        costs = []
        for x in inventory:
            x = int(x)
            rem = x % t
            if rem == 0:
                rem = t
            need = (rem - 1) // a  # floor((rem-1)/a)
            costs.append(need)

        costs.sort()

        used = 0
        credits = 0
        for need in costs:
            if used + need <= k:
                used += need
                credits += 1
            else:
                break

        return credits
