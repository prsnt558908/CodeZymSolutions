# Design Document Comment Service in Java

#### Problem Statement
[https://codezym.com/question/326-document-comment-service](https://codezym.com/question/326-document-comment-service)

Every document needs its own list of comments, and those comments must come back in the same order they were added. So the heart of this solution is one `HashMap`: the key is a document id and the value is the list of comments on that document. One lookup in this map tells us if the document exists, lets us add a comment and gives back only that document's comments.

No design pattern is needed here. The whole problem is about picking the right data structure, so a small `Comment` class plus one map is the cleanest and fastest design. Adding a pattern would only add extra classes without solving any extra requirement.

We will first build a simple version with two plain lists, see why it gets slow, and then improve it with the map.

## Understanding the Problem

- The service starts with a fixed list of unique document ids.
- `addComment` stores a comment only when the document exists and returns `true`. For an unknown document it returns `false` and stores nothing.
- `getComments` returns one document's comments as `"reviewerId,commentText"`, in the order they were added.
- An unknown document, or a document with no comments, gives an empty list.
- The same reviewer can post the same text many times. Every copy is kept.

## Why Not Use a Design Pattern?

Two patterns sound good for a comment system, but neither fits this problem.

**Observer:** real apps often notify the document owner when someone comments. Observer would let listeners subscribe to a "comment added" event. But this problem has no notifications at all, so the listener interface and the subscriber list would never be used.

**Composite:** comments with replies form a tree, and Composite lets us treat a comment and its replies in the same way. Here there are no replies. Every comment sits directly under a document, so a flat list is enough.

If notifications or replies are needed later, these patterns can be added then. For the current requirements, plain classes do the job fully.

## Solution 1: Simple Approach with Two Lists

The most direct idea is to keep everything in two plain lists:

- `documentIds`: ids of all existing documents.
- `allComments`: every comment of every document, in the order they were added. Each comment remembers its document id.

**addComment:** if `documentIds` does not contain the id, return `false`. Otherwise append the comment to `allComments` and return `true`.

**getComments:** go through `allComments` and pick only the comments of the requested document.

New comments are always appended at the end, so `allComments` is already in insertion order. That means the picked comments also come out in the correct order.

### Why we need the `Comment` class

A comment has three pieces of data: document id, reviewer id and text. Keeping them together in one object is cleaner than storing one long string and splitting it later.

The output format `"reviewerId,commentText"` also lives in one place, the `format()` method. If the format ever changes, only one line changes.

### Code

```java
import java.util.*;

/**
 * One comment: the document it belongs to, who wrote it and what it says.
 */
class Comment {
    String documentId;
    String reviewerId;
    String commentText;

    Comment(String documentId, String reviewerId, String commentText) {
        this.documentId = documentId;
        this.reviewerId = reviewerId;
        this.commentText = commentText;
    }

    /** Returns the comment in the required "reviewerId,commentText" format. */
    String format() {
        return reviewerId + "," + commentText;
    }
}

public class CommentService {

    // ids of all existing documents
    List<String> documentIds;

    // comments of ALL documents, in the order they were added
    List<Comment> allComments = new ArrayList<>();

    public CommentService(List<String> documentIds) {
        this.documentIds = new ArrayList<>(documentIds); // our own copy of the ids
    }

    public boolean addComment(String documentId, String reviewerId, String commentText) {
        // contains() compares the ids one by one
        if (!documentIds.contains(documentId)) {
            return false; // unknown document, nothing is stored
        }
        allComments.add(new Comment(documentId, reviewerId, commentText));
        return true;
    }

    public List<String> getComments(String documentId) {
        List<String> result = new ArrayList<>();
        // scan the comments of every document and keep only this document's ones
        for (Comment comment : allComments) {
            if (comment.documentId.equals(documentId)) {
                result.add(comment.format());
            }
        }
        return result;
    }
}
```

### Problems with this approach

The code is correct, but it slows down as the data grows.

- `documentIds.contains()` compares ids one by one. With 10,000 documents, a single `addComment` can do 10,000 string comparisons.
- `getComments` scans the comments of **all** documents just to find the few that belong to one document. With 50,000 stored comments, every read scans all 50,000 of them.

With up to 100,000 method calls, this can reach billions of comparisons. We need a way to jump straight to one document and its comments.

## Solution 2: HashMap from Document to Its Comments (Optimal)

Both problems have the same fix: give every document its own list of comments, and keep these lists in a `HashMap` with the document id as the key.

After the two `addComment` calls of Example 1, the map looks like this:

```
"design-12"  ->  [ ("reviewer-4", "Please clarify this section"),
                   ("reviewer-9", "The diagram looks correct") ]
"report-27"  ->  [ ]
```

**Constructor:** put every document id in the map with an empty list. Now "the id is a key in the map" simply means "the document exists".

**addComment:** get the document's list from the map. No list means the document does not exist, so return `false` right away. Since nothing is stored, a rejected comment can never show up later. Otherwise append a new `Comment` and return `true`.

**getComments:** get the document's list from the map. No list means an unknown document, so return an empty list. Otherwise format each comment and return the result.

### Why these data structures

- **`HashMap<String, List<Comment>>`:** finds a document in constant time on average. It does two jobs at once: it tells us if a document exists and it holds that document's comments. So we do not need a separate set of ids.
- **`ArrayList<Comment>`:** adding at the end keeps the insertion order for free, and it allows duplicate comments, exactly as the problem wants.
- **`Comment`:** no longer needs a document id field, because the map key already tells us which document a comment belongs to.

`getComments` always builds a new list. So a caller can never change our stored comments by editing the list it receives.

### Complexity

| Operation | Solution 1 | Solution 2 |
|---|---|---|
| Constructor | O(D) | O(D) |
| `addComment` | O(D) | O(1) on average |
| `getComments` | O(N) | O(K) |

D is the number of documents, N is the total number of stored comments and K is the number of comments on the requested document.

`getComments` in Solution 2 only touches the K comments it has to return, so it cannot get any faster. Both solutions use O(D + N) space.

### Code

```java
import java.util.*;

/**
 * One comment: who wrote it and what it says.
 * The document it belongs to is the map key in CommentService.
 */
class Comment {
    String reviewerId;
    String commentText;

    Comment(String reviewerId, String commentText) {
        this.reviewerId = reviewerId;
        this.commentText = commentText;
    }

    /** Returns the comment in the required "reviewerId,commentText" format. */
    String format() {
        return reviewerId + "," + commentText;
    }
}

/**
 * Stores comments for existing documents.
 * Every document id points to its own list of comments, kept in insertion order.
 */
public class CommentService {

    // document id -> its comments, in the order they were added.
    // Only documents passed to the constructor have a key.
    Map<String, List<Comment>> commentsByDocument = new HashMap<>();

    public CommentService(List<String> documentIds) {
        for (String documentId : documentIds) {
            commentsByDocument.put(documentId, new ArrayList<>());
        }
    }

    public boolean addComment(String documentId, String reviewerId, String commentText) {
        List<Comment> comments = commentsByDocument.get(documentId);
        if (comments == null) {
            return false; // unknown document, nothing is stored
        }
        comments.add(new Comment(reviewerId, commentText));
        return true;
    }

    public List<String> getComments(String documentId) {
        List<String> result = new ArrayList<>();
        List<Comment> comments = commentsByDocument.get(documentId);
        if (comments == null) {
            return result; // unknown document gives an empty list
        }
        for (Comment comment : comments) {
            result.add(comment.format());
        }
        return result;
    }
}
```