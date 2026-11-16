# Design File Downloader Library in Python

#### Problem Statement
[https://codezym.com/question/91-design-file-downloader-library](https://codezym.com/question/91-design-file-downloader-library)


## Core Idea

Every download is a small **state machine**. It starts as `RUNNING`, can switch between `RUNNING` and `PAUSED`, and can end as `COMPLETED`, `FAILED` or `CANCELED`.


Above all the downloads sits the library. It guards two shared limits: how many downloads can be **active** at the same time, and how many **characters** all active downloads can keep in memory.


So the whole solution comes down to one thing: keep these two numbers correct every time a download changes state.


For the design, a plain **enum** for the states is enough. The **State pattern** looks like a natural fit at first, but here it would spread a few one-line rules across many classes. We will see why below.


## Download States

| From | What happens | To |
|---|---|---|
| `RUNNING` | `pauseDownload()` | `PAUSED` |
| `PAUSED` | `resumeDownload()` | `RUNNING` |
| `RUNNING` or `PAUSED` | `cancelDownload()` | `CANCELED` |
| `RUNNING` | text makes the total equal to `fileSize` | `COMPLETED` |
| `RUNNING` | text would make the total bigger than `fileSize` | `FAILED` |


`RUNNING` and `PAUSED` are **active**. A paused download still holds its slot and its memory.


The other three are **terminal**. A download that gets there never changes again. Its text is flushed to disk and its memory is freed.


## Why Not the State Pattern?

The State pattern gives every state its own class, like `RunningState` and `PausedState`. It shines when each state has a lot of its own behavior.


Here it would add more code than it saves:

- `COMPLETED`, `FAILED` and `CANCELED` behave exactly the same. They reject everything.
- Every transition is one line, like "if `RUNNING`, set `PAUSED`".
- The hard part is the memory budget and the active slots. They are shared by all downloads, so they belong to the library, not to a state. State classes would need a link back to the library just to free memory.


An enum with an `isActive()` helper keeps every rule on one screen. If the downloader later gets states with real behavior, like retries or a waiting queue, the State pattern would start to pay off.


## The Building Blocks

**`DownloadState`** is an `Enum` with the five states. It always holds exactly one valid state. Strings allow typos, and a pile of flags like `isPaused` and `isCanceled` allows impossible mixes, such as paused and canceled at once.


**`Download`** holds everything about one download:

- `chunks`: a list with one list of text pieces per chunk. Python strings are immutable, so collecting pieces in a list is the cheap way to keep appending. This is the text sitting in memory, and the reason a memory budget exists at all.
- `downloadedChars`: total characters received across all chunks.
- `failureReason`: set only when the download fails, so `getDownloadStatus()` can still show `FILE_TOO_LARGE` later.


It also builds its own output strings: `summary(error)` for `downloadText()` and `status()` for `getDownloadStatus()`.


**`FileDownloaderLibrary`** keeps a dictionary `downloads` (downloadId to `Download`) to find any download by id in O(1). Finished downloads stay in it, because `getDownloadStatus()` must still work for them.


## First Try: Count With Loops

The library keeps asking two questions:

- In `createDownload()`: how many downloads are active right now?
- In `downloadText()`: how many characters are in memory right now?


The simplest answer is to loop over all downloads and count.

```python
def countActive(self) -> int:
    count = 0
    for download in self.downloads.values():
        if download.state.isActive():
            count += 1
    return count

def memoryInUse(self) -> int:
    used = 0
    for download in self.downloads.values():
        if download.state.isActive():
            used += download.downloadedChars
    return used

# in createDownload()
if self.countActive() >= self.maxConcurrentDownloads:
    return False

# in downloadText()
if self.memoryInUse() + len(text) > self.totalMemory:
    return download.summary("NO_MEMORY")
```

This is easy to trust. A finished download simply stops being counted, so freeing its memory happens on its own.


**The problem:** downloads are never removed from the dictionary. So both loops walk over every download ever created, on every call. The more downloads we create, the slower `createDownload()` and `downloadText()` get.


## Better: Running Counters With One Exit

Keep two counters in the library and update them the moment something changes:

- `activeCount` goes up in `createDownload()`.
- `memoryUsed` goes up when text is accepted.


Both go **down** when a download becomes inactive. There are three ways to get there: completed, failed and canceled.


If each of those three places updates the counters on its own, sooner or later one of them will forget. So all three go through **one** method:

```python
def moveToInactive(self, download: Download, endState: DownloadState) -> None:
    download.state = endState
    download.flushToDisk()
    self.memoryUsed -= download.downloadedChars
    self.activeCount -= 1
```

While a download is active, every character it received is still in memory. So when it leaves, it gives back exactly `downloadedChars`.


Now no method loops over the other downloads, and the counters always stay correct.


## Order of Checks in `downloadText()`

This is where most bugs hide. Check in this order and stop at the first match:

1. Unknown `downloadId` → `DOWNLOAD_NOT_FOUND`
2. `chunkId` outside `0` to `chunksCount - 1` → `INVALID_CHUNK_ID`
3. Download already ended → `INACTIVE_DOWNLOAD`
4. Download is paused → `DOWNLOAD_PAUSED`
5. New total would go past `fileSize` → download becomes `FAILED` with `FILE_TOO_LARGE`
6. `memoryUsed` + text length would go past `totalMemory` → `NO_MEMORY`
7. Otherwise append the text. If the total now equals `fileSize`, the download becomes `COMPLETED`.


Three things to notice:

- `FILE_TOO_LARGE` comes before `NO_MEMORY`. Text that can never fit the file is a permanent problem, so we fail right away. `NO_MEMORY` is temporary: the same text may fit later.
- Every error except `FILE_TOO_LARGE` only rejects this one call. The download stays as it was, and `getDownloadStatus()` never shows that error.
- The memory check runs even when the text would finish the file. In the example below, DL-5 gets `NO_MEMORY` for the exact 7 characters it needs.


## Example 3, Step by Step

`totalMemory = 10` and `maxConcurrentDownloads = 2`. Watch the two counters.

| Call | Result | activeCount | memoryUsed |
|---|---|---|---|
| `createDownload("DL-3", 2, 5)` | `True` | 1 | 0 |
| `createDownload("DL-4", 1, 6)` | `True` | 2 | 0 |
| `createDownload("DL-5", 1, 7)` | `False`: no free slot | 2 | 0 |
| DL-3 gets `"abc"` | 60% | 2 | 3 |
| DL-4 gets `"wxyz"` | 66% | 2 | 7 |
| DL-3 gets `"def"` | `FAILED`: 3 + 3 > 5. DL-3 frees 3 chars | 1 | 4 |
| `createDownload("DL-5", 1, 7)` | `True`: a slot is free again | 2 | 4 |
| DL-5 gets `"1234567"` | `NO_MEMORY`: 4 + 7 > 10 | 2 | 4 |
| `cancelDownload("DL-4")` | `True`. DL-4 frees 4 chars | 1 | 0 |
| DL-5 gets `"1234567"` | `COMPLETED`. DL-5 frees 7 chars | 0 | 0 |


## Python Code

```python
from enum import Enum


class DownloadState(Enum):
    """The life cycle of a download.
    RUNNING and PAUSED are active. COMPLETED, FAILED and CANCELED are terminal."""

    RUNNING = 1
    PAUSED = 2
    COMPLETED = 3
    FAILED = 4
    CANCELED = 5

    def isActive(self) -> bool:
        return self == DownloadState.RUNNING or self == DownloadState.PAUSED


class Download:
    """Everything the library knows about one download."""

    def __init__(self, chunksCount: int, fileSize: int):
        self.chunksCount = chunksCount
        self.fileSize = fileSize
        # one list of text pieces per chunk, kept in memory while the download is active
        self.chunks = [[] for _ in range(chunksCount)]
        self.downloadedChars = 0
        self.state = DownloadState.RUNNING
        # set only when the download FAILED, so getDownloadStatus() can report it
        self.failureReason = ""

    def isValidChunk(self, chunkId: int) -> bool:
        return 0 <= chunkId < self.chunksCount

    def addText(self, chunkId: int, text: str) -> None:
        self.chunks[chunkId].append(text)
        self.downloadedChars += len(text)

    def progress(self) -> int:
        """Percentage of characters received, truncated."""
        if self.fileSize == 0:
            return 0
        return self.downloadedChars * 100 // self.fileSize

    def summary(self, error: str) -> str:
        """Short summary returned by downloadText()."""
        return f"state={self.state.name},progress={self.progress()},error={error}"

    def status(self) -> str:
        """Full status returned by getDownloadStatus()."""
        return (self.summary(self.failureReason)
                + f",downloadedChars={self.downloadedChars},totalChars={self.fileSize}")

    def flushToDisk(self) -> None:
        """A real library would write the chunks, in order, to a file here.
        For us it is enough to drop them from memory."""
        self.chunks = []


class FileDownloaderLibrary:
    def __init__(self, totalMemory: int, maxConcurrentDownloads: int):
        self.totalMemory = totalMemory
        self.maxConcurrentDownloads = maxConcurrentDownloads
        # downloadId -> Download. Finished downloads stay here, so their status still works.
        self.downloads = {}
        # number of RUNNING + PAUSED downloads
        self.activeCount = 0
        # characters held in memory by all active downloads
        self.memoryUsed = 0

    def createDownload(self, downloadId: str, chunksCount: int, fileSize: int) -> bool:
        if downloadId in self.downloads:
            return False
        if self.activeCount >= self.maxConcurrentDownloads:
            return False
        self.downloads[downloadId] = Download(chunksCount, fileSize)
        self.activeCount += 1
        return True

    def downloadText(self, downloadId: str, chunkId: int, text: str) -> str:
        download = self.downloads.get(downloadId)
        if download is None:
            return "state=NOT_FOUND,progress=0,error=DOWNLOAD_NOT_FOUND"
        if not download.isValidChunk(chunkId):
            return download.summary("INVALID_CHUNK_ID")
        if not download.state.isActive():
            return download.summary("INACTIVE_DOWNLOAD")
        if download.state == DownloadState.PAUSED:
            return download.summary("DOWNLOAD_PAUSED")

        # the file can never hold this text: fail the download for good
        if download.downloadedChars + len(text) > download.fileSize:
            download.failureReason = "FILE_TOO_LARGE"
            self.moveToInactive(download, DownloadState.FAILED)
            return download.summary("FILE_TOO_LARGE")

        # memory is full right now: reject only this text, the download stays RUNNING
        if self.memoryUsed + len(text) > self.totalMemory:
            return download.summary("NO_MEMORY")

        download.addText(chunkId, text)
        self.memoryUsed += len(text)
        if download.downloadedChars == download.fileSize:
            self.moveToInactive(download, DownloadState.COMPLETED)
        return download.summary("")

    def pauseDownload(self, downloadId: str) -> bool:
        download = self.downloads.get(downloadId)
        if download is None or download.state != DownloadState.RUNNING:
            return False
        download.state = DownloadState.PAUSED
        return True

    def resumeDownload(self, downloadId: str) -> bool:
        download = self.downloads.get(downloadId)
        if download is None or download.state != DownloadState.PAUSED:
            return False
        download.state = DownloadState.RUNNING
        return True

    def cancelDownload(self, downloadId: str) -> bool:
        download = self.downloads.get(downloadId)
        if download is None or not download.state.isActive():
            return False
        self.moveToInactive(download, DownloadState.CANCELED)
        return True

    def getDownloadStatus(self, downloadId: str) -> str:
        download = self.downloads.get(downloadId)
        if download is None:
            return "state=NOT_FOUND,progress=0,error=DOWNLOAD_NOT_FOUND,downloadedChars=0,totalChars=0"
        return download.status()

    def moveToInactive(self, download: Download, endState: DownloadState) -> None:
        """The only way out of the active set. Used for COMPLETED, FAILED and CANCELED.
        Flushes the text to disk and gives back the memory and the slot."""
        download.state = endState
        download.flushToDisk()
        self.memoryUsed -= download.downloadedChars
        self.activeCount -= 1
```


## Complexity

No method loops over other downloads anymore. Each call only touches its own download.

- `pauseDownload()`, `resumeDownload()`, `getDownloadStatus()`: O(1)
- `createDownload()`: O(chunksCount), to create the chunk lists
- `downloadText()`: O(length of text), to append it
- Flushing a download when it ends: O(chunksCount), once per download