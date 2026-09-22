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

Let `processed_frames[i]` mean:

> The total number of frames from the beginning of the virtual stream that have left stage `i`.

For example, if `processed_frames[1]` is `8`, the second stage has processed global frames `1` through `8`.

We also keep `submitted_frames`, the total number of frames ever accepted by `addBuffer`.

The number of frames currently waiting at a stage can then be calculated without storing its actual queue:

- At the first stage: `submitted_frames - processed_frames[0]`
- At stage `i`: `processed_frames[i - 1] - processed_frames[i]`

The subtraction works because frames that have left the previous stage have entered the current stage. Frames that have also left the current stage are no longer waiting there.

The accumulated total can be much larger than the range of a 32-bit integer. Python integers grow automatically, so they safely store these large frame counts.

## Why Stages Must Be Updated from Right to Left

All stages work simultaneously. Frames leaving one stage during the current second cannot be processed by the next stage until the next call.

For this reason, `processSecond` updates stages from the final stage back toward the first stage.

When stage `i` is updated, `processed_frames[i - 1]` still contains the value from the beginning of the second. Therefore, stage `i` can only process frames that were already waiting when the call started.

If we updated from left to right, the next stage would see the first stage's new value immediately. It could incorrectly process newly transferred frames during the same second.

For every stage, the update is:

```text
frames processed now = min(frames_per_second, frames waiting at this stage)
```

## Detecting Completed Buffers

For every accepted buffer, the `Buffer` dataclass stores:

- Its identifier.
- `ending_frame`, the global position of its final frame.

The dataclass gives us a small, clear record for this information. It is frozen because neither value should change after the buffer is accepted.

After processing one second, `processed_frames[-1]` tells us how many frames have completely left the pipeline.

A buffer is complete when:

```text
buffer.ending_frame <= processed_frames[-1]
```

Buffers are stored in submission order. The index `next_buffer_to_complete` moves forward while buffer end positions have been reached. This automatically returns simultaneous completions in their original submission order.

## Preventing Identifier Reuse

The `used_buffer_ids` set stores every successfully accepted identifier. An identifier is never removed, even after its buffer completes. Therefore, `addBuffer` can reject both active and previously completed identifiers in average `O(1)` time.

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

FIFO processing means a stage always processes the earliest waiting frames. Therefore, after processing any number of frames, the frames that have left that stage form a prefix of the global submission stream. `processed_frames[i]` stores the length of exactly that prefix.

### 2. Each stage processes the correct number of waiting frames

For stage `i`, the previous stage has supplied `processed_frames[i - 1]` frames, while stage `i` has already processed `processed_frames[i]` frames. Their difference is exactly the number waiting. The solution advances by the smaller of this amount and `frames_per_second`, which follows the required capacity rule.

### 3. Newly transferred frames are delayed until the next second

Stages are updated from right to left. Thus, when a stage reads the previous stage's progress, that previous stage has not yet been updated for the current second. Only frames present at the start of the call are available.

### 4. A buffer is reported exactly when its final frame leaves

`ending_frame` is the position of a buffer's final frame in the global stream. The final stage processes that stream in order, so the buffer is complete exactly when the final stage's progress reaches `ending_frame`. The completion index visits buffers in submission order and never reports one twice.

Together, these points show that every call produces the required stage loads and completed buffer identifiers.

## Complexity Analysis

Let `S` be the number of stages, `B` the number of accepted buffers, and `C` the number of buffers completed by the current call.

- Constructor: `O(S)` time and `O(S)` space.
- `addBuffer`: average `O(1)` time.
- `processSecond`: `O(S + C)` time.
- `getStageLoads`: `O(S)` time.
- Total stored state: `O(S + B)` space.

Each completed buffer is visited only once by the completion index.

## Python Solution

```python
from dataclasses import dataclass


@dataclass(frozen=True)
class Buffer:
    buffer_id: str
    ending_frame: int


class AudioBufferPipeline:
    def __init__(self, framesPerSecond, stageNames):
        self.frames_per_second = framesPerSecond
        self.stage_names = list(stageNames)
        self.processed_frames = [0] * len(stageNames)
        self.used_buffer_ids = set()
        self.buffers = []
        self.submitted_frames = 0
        self.next_buffer_to_complete = 0

    def addBuffer(self, bufferId, frameCount):
        if bufferId in self.used_buffer_ids:
            return False

        self.used_buffer_ids.add(bufferId)
        self.submitted_frames += frameCount
        self.buffers.append(Buffer(bufferId, self.submitted_frames))
        return True

    def processSecond(self):
        # Work backward so a stage only sees frames that were waiting
        # when this second started.
        for stage in range(len(self.processed_frames) - 1, 0, -1):
            waiting_frames = (
                self.processed_frames[stage - 1]
                - self.processed_frames[stage]
            )
            self.processed_frames[stage] += min(
                self.frames_per_second, waiting_frames
            )

        waiting_at_first_stage = (
            self.submitted_frames - self.processed_frames[0]
        )
        self.processed_frames[0] += min(
            self.frames_per_second, waiting_at_first_stage
        )

        completed = []
        frames_leaving_pipeline = self.processed_frames[-1]

        while self.next_buffer_to_complete < len(self.buffers):
            buffer = self.buffers[self.next_buffer_to_complete]
            if buffer.ending_frame > frames_leaving_pipeline:
                break

            completed.append(buffer.buffer_id)
            self.next_buffer_to_complete += 1

        return completed

    def getStageLoads(self):
        loads = []

        for stage, stage_name in enumerate(self.stage_names):
            entered_stage = (
                self.submitted_frames
                if stage == 0
                else self.processed_frames[stage - 1]
            )
            queued_frames = entered_stage - self.processed_frames[stage]
            loads.append(f"{stage_name},{queued_frames}")

        return loads
```
