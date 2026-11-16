# Design File Downloader Library in Java

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

**`DownloadState`** is an enum with the five states. It always holds exactly one valid state. Strings allow typos, and a pile of flags like `isPaused` and `isCanceled` allows impossible mixes, such as paused and canceled at once.


**`Download`** holds everything about one download:

- `chunks`: a `List<StringBuilder>`, one buffer per chunk. This is the text sitting in memory, and the reason a memory budget exists at all.
- `downloadedChars`: total characters received across all chunks.
- `failureReason`: set only when the download fails, so `getDownloadStatus()` can still show `FILE_TOO_LARGE` later.


It also builds its own output strings: `summary(error)` for `downloadText()` and `status()` for `getDownloadStatus()`.


**`FileDownloaderLibrary`** keeps a `Map<String, Download>` to find any download by id in O(1). Finished downloads stay in the map, because `getDownloadStatus()` must still work for them.


## First Try: Count With Loops

The library keeps asking two questions:

- In `createDownload()`: how many downloads are active right now?
- In `downloadText()`: how many characters are in memory right now?


The simplest answer is to loop over all downloads and count.

```java
int countActive() {
    int count = 0;
    for (Download download : downloads.values()) {
        if (download.state.isActive()) count++;
    }
    return count;
}

int memoryInUse() {
    int used = 0;
    for (Download download : downloads.values()) {
        if (download.state.isActive()) used += download.downloadedChars;
    }
    return used;
}

// in createDownload()
if (countActive() >= maxConcurrentDownloads) return false;

// in downloadText()
if (memoryInUse() + text.length() > totalMemory) return download.summary("NO_MEMORY");
```

This is easy to trust. A finished download simply stops being counted, so freeing its memory happens on its own.


**The problem:** downloads are never removed from the map. So both loops walk over every download ever created, on every call. The more downloads we create, the slower `createDownload()` and `downloadText()` get.


## Better: Running Counters With One Exit

Keep two counters in the library and update them the moment something changes:

- `activeCount` goes up in `createDownload()`.
- `memoryUsed` goes up when text is accepted.


Both go **down** when a download becomes inactive. There are three ways to get there: completed, failed and canceled.


If each of those three places updates the counters on its own, sooner or later one of them will forget. So all three go through **one** method:

```java
void moveToInactive(Download download, DownloadState endState) {
    download.state = endState;
    download.flushToDisk();
    memoryUsed -= download.downloadedChars;
    activeCount--;
}
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
| `createDownload("DL-3", 2, 5)` | `true` | 1 | 0 |
| `createDownload("DL-4", 1, 6)` | `true` | 2 | 0 |
| `createDownload("DL-5", 1, 7)` | `false`: no free slot | 2 | 0 |
| DL-3 gets `"abc"` | 60% | 2 | 3 |
| DL-4 gets `"wxyz"` | 66% | 2 | 7 |
| DL-3 gets `"def"` | `FAILED`: 3 + 3 > 5. DL-3 frees 3 chars | 1 | 4 |
| `createDownload("DL-5", 1, 7)` | `true`: a slot is free again | 2 | 4 |
| DL-5 gets `"1234567"` | `NO_MEMORY`: 4 + 7 > 10 | 2 | 4 |
| `cancelDownload("DL-4")` | `true`. DL-4 frees 4 chars | 1 | 0 |
| DL-5 gets `"1234567"` | `COMPLETED`. DL-5 frees 7 chars | 0 | 0 |


## Java Code

```java
import java.util.*;

/**
 * The life cycle of a download.
 * RUNNING and PAUSED are active. COMPLETED, FAILED and CANCELED are terminal.
 */
enum DownloadState {
    RUNNING, PAUSED, COMPLETED, FAILED, CANCELED;

    boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }
}

/**
 * Everything the library knows about one download.
 */
class Download {
    int chunksCount;
    int fileSize;
    // one text buffer per chunk, kept in memory while the download is active
    List<StringBuilder> chunks = new ArrayList<>();
    int downloadedChars = 0;
    DownloadState state = DownloadState.RUNNING;
    // set only when the download FAILED, so getDownloadStatus() can report it
    String failureReason = "";

    Download(int chunksCount, int fileSize) {
        this.chunksCount = chunksCount;
        this.fileSize = fileSize;
        for (int i = 0; i < chunksCount; i++) {
            chunks.add(new StringBuilder());
        }
    }

    boolean isValidChunk(int chunkId) {
        return chunkId >= 0 && chunkId < chunksCount;
    }

    void addText(int chunkId, String text) {
        chunks.get(chunkId).append(text);
        downloadedChars += text.length();
    }

    /** Percentage of characters received, truncated. Uses long so big files do not overflow. */
    int progress() {
        if (fileSize == 0) return 0;
        return (int) (downloadedChars * 100L / fileSize);
    }

    /** Short summary returned by downloadText(). */
    String summary(String error) {
        return "state=" + state + ",progress=" + progress() + ",error=" + error;
    }

    /** Full status returned by getDownloadStatus(). */
    String status() {
        return summary(failureReason) + ",downloadedChars=" + downloadedChars + ",totalChars=" + fileSize;
    }

    /**
     * A real library would write the chunks, in order, to a file here.
     * For us it is enough to drop them from memory.
     */
    void flushToDisk() {
        chunks.clear();
    }
}

public class FileDownloaderLibrary {
    int totalMemory;
    int maxConcurrentDownloads;
    // every download ever created, so its status is available even after it ends
    Map<String, Download> downloads = new HashMap<>();
    // number of RUNNING + PAUSED downloads
    int activeCount = 0;
    // characters held in memory by all active downloads
    int memoryUsed = 0;

    public FileDownloaderLibrary(int totalMemory, int maxConcurrentDownloads) {
        this.totalMemory = totalMemory;
        this.maxConcurrentDownloads = maxConcurrentDownloads;
    }

    public boolean createDownload(String downloadId, int chunksCount, int fileSize) {
        if (downloads.containsKey(downloadId)) return false;
        if (activeCount >= maxConcurrentDownloads) return false;
        downloads.put(downloadId, new Download(chunksCount, fileSize));
        activeCount++;
        return true;
    }

    public String downloadText(String downloadId, int chunkId, String text) {
        Download download = downloads.get(downloadId);
        if (download == null) return "state=NOT_FOUND,progress=0,error=DOWNLOAD_NOT_FOUND";
        if (!download.isValidChunk(chunkId)) return download.summary("INVALID_CHUNK_ID");
        if (!download.state.isActive()) return download.summary("INACTIVE_DOWNLOAD");
        if (download.state == DownloadState.PAUSED) return download.summary("DOWNLOAD_PAUSED");

        // the file can never hold this text: fail the download for good
        if (download.downloadedChars + text.length() > download.fileSize) {
            download.failureReason = "FILE_TOO_LARGE";
            moveToInactive(download, DownloadState.FAILED);
            return download.summary("FILE_TOO_LARGE");
        }

        // memory is full right now: reject only this text, the download stays RUNNING
        if (memoryUsed + text.length() > totalMemory) return download.summary("NO_MEMORY");

        download.addText(chunkId, text);
        memoryUsed += text.length();
        if (download.downloadedChars == download.fileSize) {
            moveToInactive(download, DownloadState.COMPLETED);
        }
        return download.summary("");
    }

    public boolean pauseDownload(String downloadId) {
        Download download = downloads.get(downloadId);
        if (download == null || download.state != DownloadState.RUNNING) return false;
        download.state = DownloadState.PAUSED;
        return true;
    }

    public boolean resumeDownload(String downloadId) {
        Download download = downloads.get(downloadId);
        if (download == null || download.state != DownloadState.PAUSED) return false;
        download.state = DownloadState.RUNNING;
        return true;
    }

    public boolean cancelDownload(String downloadId) {
        Download download = downloads.get(downloadId);
        if (download == null || !download.state.isActive()) return false;
        moveToInactive(download, DownloadState.CANCELED);
        return true;
    }

    public String getDownloadStatus(String downloadId) {
        Download download = downloads.get(downloadId);
        if (download == null) {
            return "state=NOT_FOUND,progress=0,error=DOWNLOAD_NOT_FOUND,downloadedChars=0,totalChars=0";
        }
        return download.status();
    }

    /**
     * The only way out of the active set. Used for COMPLETED, FAILED and CANCELED.
     * Flushes the text to disk and gives back the memory and the slot.
     */
    void moveToInactive(Download download, DownloadState endState) {
        download.state = endState;
        download.flushToDisk();
        memoryUsed -= download.downloadedChars;
        activeCount--;
    }
}
```


## Complexity

No method loops over other downloads anymore. Each call only touches its own download.

- `pauseDownload()`, `resumeDownload()`, `getDownloadStatus()`: O(1)
- `createDownload()`: O(chunksCount), to create the chunk buffers
- `downloadText()`: O(length of text), to append it
- Flushing a download when it ends: O(chunksCount), once per download