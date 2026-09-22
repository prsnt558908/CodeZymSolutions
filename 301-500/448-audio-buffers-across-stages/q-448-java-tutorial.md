# Design Audio Buffers Across Different Stages

#### Problem Statement

[https://codezym.com/question/448-audio-buffers-across-stages](https://codezym.com/question/448-audio-buffers-across-stages)

The system follows a simple pipeline design: every audio frame visits the stages in order, and every stage works at the same fixed rate. The most important observation is that FIFO processing never changes the global order of the frames. Because of this, we do not need to create an object for every frame or physically move frame objects between stage queues. We can represent each stage with one cumulative progress counter and represent each buffer by the global position of its final frame.

## Understanding the Pipeline

Suppose buffers are submitted in this order:

- Buffer `A` contains 3 frames.
- Buffer `B` contains 2 frames.

We can imagine all submitted frames as one virtual stream:

```text
A A A B B
1 2 3 4 5
```

Buffer `A` ends at global frame position `3`, and buffer `B` ends at position `5`.

Since every stage uses FIFO order, each stage always processes a prefix of this same stream. A stage may be behind another stage, but it can never change the order.

## A Direct Simulation

A direct solution could create every frame separately and move those frame objects through queues.

That approach is not practical because one buffer may contain up to `1,000,000,000` frames. Creating an object for every frame would require far too much memory and time.

A better simulation could store chunks of frames in a queue for every stage. This avoids one object per frame, but portions of a buffer may be split across stages and seconds. Managing and moving all those chunks makes the solution longer than necessary.

We can simplify it further by tracking only cumulative progress.

## Cumulative Progress for Every Stage

Let `processedFrames[i]` mean:

> The total number of frames from the beginning of the virtual stream that have left stage `i`.

For example, if `processedFrames[1]` is `8`, the second stage has processed global frames `1` through `8`.

We also keep `submittedFrames`, the total number of frames ever accepted by `addBuffer`.

The number of frames currently waiting at a stage can then be calculated without storing its actual queue:

- At the first stage: `submittedFrames - processedFrames[0]`
- At stage `i`: `processedFrames[i - 1] - processedFrames[i]`

The subtraction works because frames that have left the previous stage have entered the current stage. Frames that have also left the current stage are no longer waiting there.

All counters use `long`. With up to `100,000` buffers and up to `1,000,000,000` frames in each buffer, the total may be much larger than the range of an `int`.

## Why Stages Must Be Updated from Right to Left

All stages work simultaneously. Frames leaving one stage during the current second cannot be processed by the next stage until the next call.

For this reason, `processSecond` updates stages from the final stage back toward the first stage.

When stage `i` is updated, `processedFrames[i - 1]` still contains the value from the beginning of the second. Therefore, stage `i` can only process frames that were already waiting when the call started.

If we updated from left to right, the next stage would see the first stage's new value immediately. It could incorrectly process newly transferred frames during the same second.

For every stage, the update is:

```text
frames processed now = min(framesPerSecond, frames waiting at this stage)
```

## Detecting Completed Buffers

For every accepted buffer, the `Buffer` class stores:

- Its identifier.
- `endingFrame`, the global position of its final frame.

After processing one second, `processedFrames[lastStage]` tells us how many frames have completely left the pipeline.

A buffer is complete when:

```text
buffer.endingFrame <= processedFrames[lastStage]
```

Buffers are stored in submission order. The pointer `nextBufferToComplete` moves forward while buffer end positions have been reached. This automatically returns simultaneous completions in their original submission order.

## Preventing Identifier Reuse

The `usedBufferIds` set stores every successfully accepted identifier. An identifier is never removed, even after its buffer completes. Therefore, `addBuffer` can reject both active and previously completed identifiers in average `O(1)` time.

## Small Walkthrough

Consider a pipeline with a rate of `4` frames per second, three stages, and one buffer named `voice` containing `6` frames.

- Initially, the buffer ends at global position `6`.
- After the first second, stage 1 has processed 4 frames. The loads are `2, 4, 0`.
- After the second second, the first two stages have processed 6 and 4 frames. The loads are `0, 2, 4`.
- After the third second, the last stage has processed only 4 frames. The buffer is not complete because `4 < 6`.
- After the fourth second, the last stage reaches frame 6, so `voice` is returned as completed.

No individual frame object needs to be created during this process.

## Why the Solution Is Correct

### 1. Every progress counter represents the correct prefix

FIFO processing means a stage always processes the earliest waiting frames. Therefore, after processing any number of frames, the frames that have left that stage form a prefix of the global submission stream. `processedFrames[i]` stores the length of exactly that prefix.

### 2. Each stage processes the correct number of waiting frames

For stage `i`, the previous stage has supplied `processedFrames[i - 1]` frames, while stage `i` has already processed `processedFrames[i]` frames. Their difference is exactly the number waiting. The solution advances by the smaller of this amount and `framesPerSecond`, which follows the required capacity rule.

### 3. Newly transferred frames are delayed until the next second

Stages are updated from right to left. Thus, when a stage reads the previous stage's progress, that previous stage has not yet been updated for the current second. Only frames present at the start of the call are available.

### 4. A buffer is reported exactly when its final frame leaves

`endingFrame` is the position of a buffer's final frame in the global stream. The final stage processes that stream in order, so the buffer is complete exactly when the final stage's progress reaches `endingFrame`. The completion pointer visits buffers in submission order and never reports one twice.

Together, these points show that every call produces the required stage loads and completed buffer identifiers.

## Complexity Analysis

Let `S` be the number of stages, `B` the number of accepted buffers, and `C` the number of buffers completed by the current call.

- Constructor: `O(S)` time and `O(S)` space.
- `addBuffer`: average `O(1)` time.
- `processSecond`: `O(S + C)` time.
- `getStageLoads`: `O(S)` time.
- Total stored state: `O(S + B)` space.

Each completed buffer is visited only once by the completion pointer.

## Java Solution

```java
import java.util.*;

public class AudioBufferPipeline {

    private static class Buffer {
        private final String id;
        private final long endingFrame;

        private Buffer(String id, long endingFrame) {
            this.id = id;
            this.endingFrame = endingFrame;
        }
    }

    private final long framesPerSecond;
    private final List<String> stageNames;
    private final long[] processedFrames;
    private final Set<String> usedBufferIds;
    private final List<Buffer> buffers;

    private long submittedFrames;
    private int nextBufferToComplete;

    public AudioBufferPipeline(int framesPerSecond, List<String> stageNames) {
        this.framesPerSecond = framesPerSecond;
        this.stageNames = new ArrayList<>(stageNames);
        this.processedFrames = new long[stageNames.size()];
        this.usedBufferIds = new HashSet<>();
        this.buffers = new ArrayList<>();
    }

    public boolean addBuffer(String bufferId, int frameCount) {
        if (!usedBufferIds.add(bufferId)) {
            return false;
        }

        submittedFrames += frameCount;
        buffers.add(new Buffer(bufferId, submittedFrames));
        return true;
    }

    public List<String> processSecond() {
        // Work backward so a stage only sees frames that were waiting
        // when this second started.
        for (int stage = processedFrames.length - 1; stage >= 1; stage--) {
            long waitingFrames = processedFrames[stage - 1] - processedFrames[stage];
            processedFrames[stage] += Math.min(framesPerSecond, waitingFrames);
        }

        long waitingAtFirstStage = submittedFrames - processedFrames[0];
        processedFrames[0] += Math.min(framesPerSecond, waitingAtFirstStage);

        List<String> completed = new ArrayList<>();
        long framesLeavingPipeline = processedFrames[processedFrames.length - 1];

        while (nextBufferToComplete < buffers.size()
                && buffers.get(nextBufferToComplete).endingFrame <= framesLeavingPipeline) {
            completed.add(buffers.get(nextBufferToComplete).id);
            nextBufferToComplete++;
        }

        return completed;
    }

    public List<String> getStageLoads() {
        List<String> loads = new ArrayList<>(stageNames.size());

        for (int stage = 0; stage < stageNames.size(); stage++) {
            long enteredStage = stage == 0
                    ? submittedFrames
                    : processedFrames[stage - 1];
            long queuedFrames = enteredStage - processedFrames[stage];
            loads.add(stageNames.get(stage) + "," + queuedFrames);
        }

        return loads;
    }
}
```
