from collections import deque


class RateLimiter:
    """
    In-memory RateLimiter supporting multiple strategies per resourceId.

    Strategies:
      - "fixed-window-counter"
      - "sliding-window-counter"

    limits format: "maxRequests,timePeriod"
      e.g. "5,2" => at most 5 requests every 2 seconds.
    """

    def __init__(self):
        # resourceId -> strategy instance
        self._strategies = {}

    def addResource(self, resourceId, strategy, limits):
        """
        Configure or update a resource with a given strategy and limits.

        :param resourceId: globally unique, non-blank id
        :param strategy: "fixed-window-counter" or "sliding-window-counter"
        :param limits: "maxRequests,timePeriod" (both >= 1)
        """
        parts = limits.split(",")
        if len(parts) != 2:
            raise ValueError("limits must be 'maxRequests,timePeriod'")

        max_requests = int(parts[0].strip())
        time_period = int(parts[1].strip())

        if strategy == "fixed-window-counter":
            strategy_impl = _FixedWindowCounterStrategy(max_requests, time_period)
        elif strategy == "sliding-window-counter":
            strategy_impl = _SlidingWindowCounterStrategy(max_requests, time_period)
        else:
            raise ValueError(f"Unknown strategy: {strategy}")

        # Replace any existing strategy (state is reset)
        self._strategies[resourceId] = strategy_impl

    def isAllowed(self, resourceId, timestamp):
        """
        Check if a request for this resource at given timestamp is allowed.

        :param resourceId: valid, previously registered resourceId
        :param timestamp: seconds, strictly increasing across all calls
        :return: True if allowed, False if blocked
        """
        strategy = self._strategies.get(resourceId)
        if strategy is None:
            # According to statement: resourceId will always be valid,
            # but we guard anyway.
            raise ValueError(f"Unknown resourceId: {resourceId}")
        return strategy.is_allowed(timestamp)


class _BaseStrategy:
    def __init__(self, max_requests, time_period):
        self.max_requests = max_requests
        self.time_period = time_period

    def is_allowed(self, timestamp):
        raise NotImplementedError


class _FixedWindowCounterStrategy(_BaseStrategy):
    """
    Fixed Window:
      - Time divided into windows of size time_period: [0..time_period-1],
        [time_period..2*time_period-1], ...
      - Per window we count requests: if count >= max_requests => block.
    """

    def __init__(self, max_requests, time_period):
        super().__init__(max_requests, time_period)
        self.current_window = None
        self.current_count = 0

    def is_allowed(self, timestamp):
        # e.g. time_period=5 -> [0..4]=0, [5..9]=1, ...
        window_id = timestamp // self.time_period

        if self.current_window is None or self.current_window != window_id:
            self.current_window = window_id
            self.current_count = 0

        if self.current_count < self.max_requests:
            self.current_count += 1
            return True
        return False


class _SlidingWindowCounterStrategy(_BaseStrategy):
    """
    Sliding Window (log-based):
      - Keep timestamps of recent *allowed* requests in a deque.
      - For a new request at t, remove all entries < (t - time_period + 1).
      - If remaining size < max_requests, allow and record t; else block.
    """

    def __init__(self, max_requests, time_period):
        super().__init__(max_requests, time_period)
        self.timestamps = deque()

    def is_allowed(self, timestamp):
        # Inclusive sliding window: [window_start .. timestamp]
        window_start = timestamp - self.time_period + 1

        # Drop events that are outside the current sliding window
        while self.timestamps and self.timestamps[0] < window_start:
            self.timestamps.popleft()

        # Now len(self.timestamps) = number of requests in [window_start..timestamp]
        if len(self.timestamps) < self.max_requests:
            self.timestamps.append(timestamp)  # record this allowed request
            return True

        # Over the limit in this sliding window
        return False
