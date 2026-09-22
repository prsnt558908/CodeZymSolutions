# Design Retry Handler for API Calls in Java

#### Problem Statement

[https://codezym.com/question/206-design-retry-handler-for-api-calls](https://codezym.com/question/206-design-retry-handler-for-api-calls)

The core idea is to keep the retry state of every API call separate and move the delay calculation into small interchangeable classes. A `Map` stores one state object for each API call name, while the **Strategy pattern** handles fixed, exponential, and jitter backoff. A small **Factory** maps each strategy name to the correct object. This combination is a good fit because the delay formula is the part that changes. A new strategy can be added without changing the retry handler's state-management logic.

## 1. Start With a Simple Approach

The simplest solution is to keep all logic inside `RetryHandler`.

Whenever a retryable call fails, we could write conditions such as these:

```java
if (backoffStrategy.equals("FIXED")) {
    // Calculate fixed delay.
} else if (backoffStrategy.equals("EXPONENTIAL")) {
    // Calculate exponential delay.
} else if (backoffStrategy.equals("JITTER")) {
    // Calculate jitter delay.
}
```

This works for the three current strategies, but it makes the handler responsible for two different jobs:

- Managing API call states.
- Knowing every backoff formula.

If another strategy is added later, the handler itself must be edited. A long chain of conditions also becomes harder to test and maintain.

## 2. Improve the Design With Strategy and Factory

We create one small interface:

```java
private interface BackoffStrategy {
    long calculateDelay(long baseDelayMillis, int retryAttemptNumber);
}
```

Each backoff rule implements this interface:

- `FixedBackoffStrategy` always returns the base delay.
- `ExponentialBackoffStrategy` doubles the delay for every later retry.
- `JitterBackoffStrategy` adds the retry attempt number to the exponential delay.

`BackoffStrategyFactory` stores these objects in a `Map<String, BackoffStrategy>`. Registration asks the factory to convert a name such as `"FIXED"` into the correct object only once. From then on, the handler simply calls `calculateDelay` without checking which formula is being used.

Strategy separates the formulas, while Factory separates object selection. To add another backoff type, we add its strategy class and register it in the factory. The retry handler logic remains unchanged.

## 3. Store Independent State for Every API Call

Different API calls may have different retry policies and may be at different stages. We therefore store an `ApiCallState` for every API call name.

The state contains the important configuration:

- The selected backoff strategy.
- The maximum number of retry attempts.
- The base delay.

It also contains values that change after executions:

- The number of retry attempts already made.
- Whether the original attempt has produced a result.
- The current status.
- The time of the latest retryable failure.
- The delay calculated for the next retry.

A `HashMap<String, ApiCallState>` gives direct access to the correct state. Updating one API call does not affect any other API call.

We also use a `HashSet<String>` for retryable error names. This lets us quickly check whether an error may be retried. Matching remains case-sensitive because Java strings and `HashSet` lookups are case-sensitive.

## 4. Count Retry Attempts Carefully

The first API execution is attempt `0`. It is not a retry, so it must not increase `retryAttempts`.

For each API call, `hasExecutionResult` starts as `false`.

- On the first `updateExecutionResult` call, we record attempt `0` and keep `retryAttempts` at `0`.
- On every later update, we increase `retryAttempts` before processing the result.

For example, if the original call fails and the first retry succeeds, the success result contains `retryAttempts=1`.

This same counter also tells us which retry is coming next. After a retryable failure, the next retry number is:

```text
nextRetryAttemptNumber = retryAttempts + 1
```

## 5. Process an Execution Result

The result string contains exactly one comma, so we split it into two parts.

For `SUCCESS,response`:

- Mark the call as successful.
- Return the successful response and the actual retry count.

For `ERROR,errorType`:

- If the error is not in `retryableErrors`, mark the call as non-retryable and return `retryAttempts=-1`.
- If the retry count has reached the maximum, mark the retry limit as reached.
- Otherwise, calculate and store the delay for the next retry.

The maximum is checked after counting the current execution. With `maxRetryAttempts = 2`, the allowed executions are attempt `0`, retry `1`, and retry `2`. If retry `2` also fails, the final status is `FAILED_RETRY_LIMIT_REACHED`.

## 6. Calculate the Remaining Delay

After a retryable failure, the handler stores both the failure time and the calculated delay.

For a later call to `getDelayMillis`, we calculate:

```text
elapsed time = current time - latest failure time
remaining delay = calculated delay - elapsed time
```

If the remaining value is zero or negative, the next retry may happen immediately and the method returns `0`.

The three strategies calculate the next delay as follows:

- Fixed delay is `baseDelayMillis`.
- Exponential delay is `baseDelayMillis * 2^(retryAttemptNumber - 1)`.
- Jitter delay is `baseDelayMillis * 2^(retryAttemptNumber - 1) + retryAttemptNumber`.

The method returns special values for final states:

- `-1` means the last error is non-retryable.
- `-2` means the API call has succeeded.
- `-3` means the retry limit has been reached.

## 7. Why a Full State Pattern Is Not Needed

The statuses form a small state machine, so the State pattern may sound suitable. However, each status needs only a few simple checks and return values. Creating a separate class for every status would add several classes without simplifying the solution. An enum is enough for the current rules, while Strategy remains useful because the delay formulas are truly interchangeable behavior.

## 8. Example Walkthrough

Suppose `OrderApi` uses fixed backoff with a base delay of `200` milliseconds.

1. It is registered with `retryAttempts = 0`.
2. Attempt `0` fails with a retryable error at time `1000`.
3. The fixed strategy calculates a delay of `200`.
4. At time `1100`, only `100` milliseconds have passed, so `getDelayMillis` returns `100`.
5. At time `1200`, the complete delay has passed, so it returns `0`.
6. The first retry succeeds and the result reports `retryAttempts=1`.
7. Any later delay check returns `-2` because the call has succeeded.

## 9. Why the Solution Is Correct

We can verify the solution through the main rules of the problem.

### Each API call keeps independent state

Every API call name is mapped to its own `ApiCallState`. All updates, counters, statuses, and delay values are read from and written to that object only. Therefore, actions for one API call cannot change another API call.

### Retry attempts are counted correctly

The first result does not increase the counter because it belongs to attempt `0`. Every later result increases the counter exactly once before it is processed. Therefore, `retryAttempts` always equals the number of retries that have happened.

### A retry is offered only when allowed

For an error result, the handler first checks whether the error is retryable. It then checks whether the maximum number of retries has been reached. A delay is calculated only when the error is retryable and another retry is available. Therefore, no retry is offered after success, after a non-retryable error, or after the retry limit.

### The returned delay is correct

After every retryable failure, the selected strategy calculates the required delay for the next retry number. `getDelayMillis` subtracts exactly the time elapsed since that failure and never returns a negative waiting time. Therefore, it returns the correct remaining delay or `0` when the retry is ready.

Together, these points show that the handler follows the required retry policy for every registered API call.

## 10. Complexity Analysis

Let `A` be the number of registered API calls and `E` be the number of retryable error types.

Each public method takes `O(1)` average time because it uses hash maps and a hash set. Delay calculation also takes `O(1)` time.

The total space usage is `O(A + E)`. We store one state object per API call and one set entry per retryable error.

## 11. Java Solution

```java
import java.util.*;

public class RetryHandler {

    private interface BackoffStrategy {
        long calculateDelay(long baseDelayMillis, int retryAttemptNumber);
    }

    private static class FixedBackoffStrategy implements BackoffStrategy {
        @Override
        public long calculateDelay(long baseDelayMillis, int retryAttemptNumber) {
            return baseDelayMillis;
        }
    }

    private static class ExponentialBackoffStrategy implements BackoffStrategy {
        @Override
        public long calculateDelay(long baseDelayMillis, int retryAttemptNumber) {
            if (baseDelayMillis == 0) {
                return 0;
            }
            return baseDelayMillis * (1L << (retryAttemptNumber - 1));
        }
    }

    private static class JitterBackoffStrategy implements BackoffStrategy {
        private final BackoffStrategy exponentialStrategy = new ExponentialBackoffStrategy();

        @Override
        public long calculateDelay(long baseDelayMillis, int retryAttemptNumber) {
            long exponentialDelay = exponentialStrategy.calculateDelay(
                    baseDelayMillis,
                    retryAttemptNumber
            );
            return exponentialDelay + retryAttemptNumber;
        }
    }

    private static class BackoffStrategyFactory {
        private final Map<String, BackoffStrategy> strategies = new HashMap<>();

        BackoffStrategyFactory() {
            strategies.put("FIXED", new FixedBackoffStrategy());
            strategies.put("EXPONENTIAL", new ExponentialBackoffStrategy());
            strategies.put("JITTER", new JitterBackoffStrategy());
        }

        BackoffStrategy getStrategy(String strategyName) {
            return strategies.get(strategyName);
        }
    }

    private enum CallStatus {
        REGISTERED,
        SUCCESS,
        FAILED_RETRYABLE,
        FAILED_NON_RETRYABLE,
        FAILED_RETRY_LIMIT_REACHED
    }

    private static class ApiCallState {
        private final BackoffStrategy backoffStrategy;
        private final int maxRetryAttempts;
        private final long baseDelayMillis;

        private int retryAttempts;
        private boolean hasExecutionResult;
        private CallStatus status;
        private long lastFailureTimeMillis;
        private long currentDelayMillis;

        ApiCallState(
                BackoffStrategy backoffStrategy,
                int maxRetryAttempts,
                long baseDelayMillis
        ) {
            this.backoffStrategy = backoffStrategy;
            this.maxRetryAttempts = maxRetryAttempts;
            this.baseDelayMillis = baseDelayMillis;
            this.status = CallStatus.REGISTERED;
        }
    }

    private final Set<String> retryableErrors;
    private final BackoffStrategyFactory backoffStrategyFactory;
    private final Map<String, ApiCallState> apiCalls;
    private long lastSeenTimeMillis = Long.MIN_VALUE;

    public RetryHandler(List<String> retryableErrors) {
        this.retryableErrors = new HashSet<>(retryableErrors);
        this.apiCalls = new HashMap<>();
        this.backoffStrategyFactory = new BackoffStrategyFactory();
    }

    public String registerApiCall(
            String apiCallName,
            String backoffStrategy,
            int maxRetryAttempts,
            long baseDelayMillis,
            long currentMillisecondsNow
    ) {
        validateTime(currentMillisecondsNow);

        if (apiCalls.containsKey(apiCallName)) {
            return "already_registered=" + apiCallName;
        }

        ApiCallState state = new ApiCallState(
                backoffStrategyFactory.getStrategy(backoffStrategy),
                maxRetryAttempts,
                baseDelayMillis
        );
        apiCalls.put(apiCallName, state);

        return "registered=" + apiCallName + ", attempt=0, retryAttempts=0";
    }

    public String updateExecutionResult(
            String apiCallName,
            String callResult,
            long currentMillisecondsNow
    ) {
        validateTime(currentMillisecondsNow);
        ApiCallState state = apiCalls.get(apiCallName);

        // Every result after attempt 0 belongs to one retry attempt.
        if (state.hasExecutionResult) {
            state.retryAttempts++;
        }
        state.hasExecutionResult = true;

        String[] resultParts = callResult.split(",", 2);
        String resultType = resultParts[0];
        String resultValue = resultParts[1];

        if (resultType.equals("SUCCESS")) {
            state.status = CallStatus.SUCCESS;
            return formatResult("SUCCESS", resultValue, state.retryAttempts);
        }

        if (!retryableErrors.contains(resultValue)) {
            state.status = CallStatus.FAILED_NON_RETRYABLE;
            return formatResult("FAILED_NON_RETRYABLE", resultValue, -1);
        }

        if (state.retryAttempts >= state.maxRetryAttempts) {
            state.status = CallStatus.FAILED_RETRY_LIMIT_REACHED;
            return formatResult(
                    "FAILED_RETRY_LIMIT_REACHED",
                    resultValue,
                    state.retryAttempts
            );
        }

        state.status = CallStatus.FAILED_RETRYABLE;
        int nextRetryAttemptNumber = state.retryAttempts + 1;
        state.currentDelayMillis = state.backoffStrategy.calculateDelay(
                state.baseDelayMillis,
                nextRetryAttemptNumber
        );
        state.lastFailureTimeMillis = currentMillisecondsNow;

        return formatResult("FAILED_RETRYABLE", resultValue, state.retryAttempts);
    }

    public long getDelayMillis(String apiCallName, long currentMillisecondsNow) {
        validateTime(currentMillisecondsNow);
        ApiCallState state = apiCalls.get(apiCallName);

        if (state.status == CallStatus.FAILED_NON_RETRYABLE) {
            return -1L;
        }
        if (state.status == CallStatus.SUCCESS) {
            return -2L;
        }
        if (state.status == CallStatus.FAILED_RETRY_LIMIT_REACHED) {
            return -3L;
        }
        if (state.status == CallStatus.REGISTERED) {
            return 0L;
        }

        long elapsedMillis = currentMillisecondsNow - state.lastFailureTimeMillis;
        if (elapsedMillis >= state.currentDelayMillis) {
            return 0L;
        }
        return state.currentDelayMillis - elapsedMillis;
    }

    public int attemptsTillNow(String apiCallName, long currentMillisecondsNow) {
        validateTime(currentMillisecondsNow);
        ApiCallState state = apiCalls.get(apiCallName);

        if (state.status == CallStatus.FAILED_NON_RETRYABLE) {
            return -1;
        }
        return state.retryAttempts;
    }

    private String formatResult(String status, String result, int retryAttempts) {
        return "status=" + status
                + ", result=" + result
                + ", retryAttempts=" + retryAttempts;
    }

    private void validateTime(long currentMillisecondsNow) {
        if (currentMillisecondsNow < lastSeenTimeMillis) {
            throw new IllegalArgumentException("Time must be monotonically non-decreasing");
        }
        lastSeenTimeMillis = currentMillisecondsNow;
    }
}
```
