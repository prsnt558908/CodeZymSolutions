# Design Social Network News Feed in Java

#### Problem Statement
[https://codezym.com/question/85-design-social-network-news-feed](https://codezym.com/question/85-design-social-network-news-feed)


## Core Idea

This problem is mostly about keeping the right numbers ready. We keep users and posts in hash maps, so every lookup is fast. Every post also keeps its own live counters: upvotes, downvotes, comments and the running vote total of each user.

With those counters ready, the news feed becomes a pure ranking job. We put the ranking rules in one small comparator class. This is the **Strategy pattern** in its lightest form, and it is the only pattern this problem really needs. **Observer** looks tempting for a news feed, but here it adds work and saves nothing.

We will start with a brute force solution that keeps everything in plain lists. Then we will fix its slow parts one by one.


## Understanding the Problem

A few rules decide almost every answer:

- **Following is one way.** A user cannot follow themselves. Following the same person again still returns `true`.
- **The vote rule.** After every `votePost` call, the sum of one user's votes on one post must be `-1`, `0` or `1`. Otherwise the call returns `false`.
- **`up` and `down` count every accepted vote**, not only the final state. So `+1, -1, -1` from the same user gives `up=1, down=2, score=-1`.
- **The feed shows every post**, not only posts by followed users. Posts by followed users just come first, even if another post has a higher score.
- **Ranking order:** followed first, then higher score, then more comments, then newer, then smaller `postId` as text.


## Solution 1: Brute Force (Everything in Lists)

The simplest idea is to save every event in a plain list, exactly as it happens.

```java
List<String> users = new ArrayList<>();
List<String> follows = new ArrayList<>();       // "follower->followee"
List<RawPost> posts = new ArrayList<>();
List<RawComment> comments = new ArrayList<>();
List<Vote> votes = new ArrayList<>();           // every accepted vote
```

Every check becomes a scan. For the vote rule, we add up all earlier votes of this user on this post:

```java
int sum = 0;
for (Vote v : votes) {
    if (v.userId.equals(userId) && v.postId.equals(postId)) sum += v.voteType;
}
if (Math.abs(sum + voteType) > 1) return false;
votes.add(new Vote(userId, postId, voteType));
```

The feed is the worst part. On every call we count the votes and comments of every post again, then sort all posts:

```java
// recount everything for every post, on every call
for (RawPost post : posts) {
    post.up = 0;
    post.down = 0;
    post.commentCount = 0;
    for (Vote v : votes) {
        if (!v.postId.equals(post.id)) continue;
        if (v.voteType == 1) post.up++;
        else post.down++;
    }
    for (RawComment c : comments) {
        if (c.postId.equals(post.id)) post.commentCount++;
    }
    post.followed = follows.contains(viewerUserId + "->" + post.userId);
}
// then sort all posts by the ranking rules and return the first `limit`
```

### Problems with this approach

- Every lookup, like "does this user exist?", scans a whole list.
- Every vote scans all earlier votes.
- Every feed call recounts everything for every post, then sorts all posts even when we only need the top `limit`.

The counts only change when a vote or a comment happens. So recounting them on every feed call is wasted work.


## Solution 2: Maps, Live Counters and a Ranking Strategy

We fix the problems above one at a time.

### Improvement 1: Hash maps for lookups

We keep `users` (`userId -> User`), `posts` (`postId -> Post`) and a set of `commentIds`. Every "does it exist?" check becomes `O(1)`.

Each `User` also keeps a `following` set. So "does the viewer follow this author?" is `O(1)`, and following someone twice changes nothing.

### Improvement 2: Each post keeps its own counters

Each post updates its `upvotes`, `downvotes` and `comments` list the moment a vote or a comment arrives. The feed reads these values directly and never counts anything again.

### Improvement 3: The vote rule in one lookup

Each post keeps a map `voteSumByUser` (`userId -> running sum`). A new vote is accepted only if the new sum stays between `-1` and `1`.

Here is Example 3, where user `x` votes on post `p20` five times:

| Vote | New sum | Result | up | down |
|---|---|---|---|---|
| `+1` | `1` | accepted | 1 | 0 |
| `+1` | `2` | rejected | 1 | 0 |
| `-1` | `0` | accepted | 1 | 1 |
| `-1` | `-1` | accepted | 1 | 2 |
| `-1` | `-2` | rejected | 1 | 2 |

A rejected vote changes nothing. The final score is `1 - 2 = -1`.

### Improvement 4: Keep only the top `limit` posts

We do not need all posts in sorted order, only the best `limit` of them.

So we keep a heap (`PriorityQueue`) that never holds more than `limit` posts. The heap is ordered from worst to best, so `peek()` always gives the worst post we kept. Once the heap is full, a new post goes in only if it beats that one, and the worst one is thrown out.

At the end we sort the few posts left in the heap. This costs `O(P log limit)` instead of `O(P log P)`, and usually most posts are skipped after just one comparison.


## Which Design Pattern Fits?

### Strategy: yes, in its lightest form

The ranking rules are the part of a news feed that changes most often. Today it is "followed first, then score". Tomorrow it may be "newest first" or a trending score.

We could write the five comparisons directly inside `showNewsFeed`. It works, but then one method does two jobs: picking the top posts and deciding what "top" means.

So the rules live in their own class, `FeedRanking`, which implements Java's `Comparator<Post>`. The heap and the final sort only call `compare()`. A comparator handed to a sort is the classic Java example of the Strategy pattern, and a new ranking needs only a new comparator.

We do not add a bigger setup, like our own strategy interface plus a factory. The problem has only one ranking, so that would be extra code with no benefit.

### Observer: tempting, but not a good fit here

Many real news feeds use "fan out on write". When a user posts, every follower is notified (Observer) and the post is pushed into a ready-made feed for that follower.

That does not help here, for two reasons:

- **Every viewer sees every post**, not only posts by people they follow. A pushed feed per user would just be a copy of all posts.
- **The order keeps changing.** Votes and comments change a post's rank after it was pushed, so every vote would mean fixing many stored feeds.

Ranking when the feed is read, from live counters, is simpler and always correct.


## Classes and Why We Need Them

- **`SocialNetworkNewsFeed`** is the entry point. It owns the maps, checks every rule and builds the feed.
- **`User`** holds the `following` set. We need it for the "followed first" rule.
- **`Post`** holds the content and the live counters. It also owns `voteSumByUser`, so the vote rule lives right next to the data it checks.
- **`Comment`** is one reply on a post. The post keeps a list of them, so `comments.size()` is the comment count.
- **`FeedRanking`** holds the ranking rules. This is our strategy.


## Final Code

```java
import java.util.*;

/**
 * In-memory social network with a ranked news feed.
 * Maps give O(1) lookups, every post keeps its own counters,
 * and FeedRanking decides the order of the feed.
 */
public class SocialNetworkNewsFeed {

    Map<String, User> users;  // userId -> user
    Map<String, Post> posts;  // postId -> post
    Set<String> commentIds;   // every commentId used so far

    public SocialNetworkNewsFeed() {
        users = new HashMap<>();
        posts = new HashMap<>();
        commentIds = new HashSet<>();
    }

    public boolean signUp(String userId) {
        if (users.containsKey(userId)) return false;
        users.put(userId, new User(userId));
        return true;
    }

    public boolean follow(String followerUserId, String followeeUserId) {
        User follower = users.get(followerUserId);
        if (follower == null || !users.containsKey(followeeUserId)) return false;
        if (followerUserId.equals(followeeUserId)) return false;
        // following is a set, so following again changes nothing
        follower.following.add(followeeUserId);
        return true;
    }

    public boolean post(String postId, String userId, String content, long timestampMillis) {
        if (!users.containsKey(userId) || posts.containsKey(postId)) return false;
        posts.put(postId, new Post(postId, userId, content, timestampMillis));
        return true;
    }

    public boolean replyToPost(String commentId, String userId, String postId,
                               String commentText, long timestampMillis) {
        Post post = posts.get(postId);
        if (!users.containsKey(userId) || post == null) return false;
        if (commentIds.contains(commentId)) return false;
        commentIds.add(commentId);
        post.comments.add(new Comment(commentId, userId, commentText, timestampMillis));
        return true;
    }

    public boolean votePost(String userId, String postId, int voteType) {
        Post post = posts.get(postId);
        if (!users.containsKey(userId) || post == null) return false;
        if (voteType != 1 && voteType != -1) return false;
        return post.vote(userId, voteType);
    }

    public List<String> showNewsFeed(String viewerUserId, int limit) {
        List<String> feed = new ArrayList<>();
        User viewer = users.get(viewerUserId);
        if (viewer == null || limit <= 0) return feed;

        FeedRanking ranking = new FeedRanking(viewer.following);

        // Keep only the best `limit` posts. The heap is ordered from worst to best,
        // so its top (peek) is always the worst post we kept so far.
        PriorityQueue<Post> best = new PriorityQueue<>(ranking.reversed());
        for (Post post : posts.values()) {
            if (best.size() < limit) {
                best.add(post);
            } else if (ranking.compare(post, best.peek()) < 0) {
                best.poll();  // this post beats the worst one we kept, so swap them
                best.add(post);
            }
        }

        List<Post> top = new ArrayList<>(best);
        top.sort(ranking);
        for (Post post : top) feed.add(post.toString());
        return feed;
    }
}

/** A user and the ids of the users they follow. */
class User {
    String id;
    Set<String> following = new HashSet<>();

    User(String id) {
        this.id = id;
    }
}

/**
 * A post that keeps its own counters up to date,
 * so ranking never has to count votes or comments again.
 */
class Post {
    String id;
    String userId;
    String content;
    long timestampMillis;
    int upvotes = 0;
    int downvotes = 0;
    List<Comment> comments = new ArrayList<>();            // direct comments on this post
    Map<String, Integer> voteSumByUser = new HashMap<>();  // userId -> sum of their votes here

    Post(String id, String userId, String content, long timestampMillis) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.timestampMillis = timestampMillis;
    }

    int score() {
        return upvotes - downvotes;
    }

    /** Adds a +1 or -1 vote, unless it would push this user's total outside -1..1. */
    boolean vote(String voterId, int voteType) {
        int newSum = voteSumByUser.getOrDefault(voterId, 0) + voteType;
        if (newSum < -1 || newSum > 1) return false;
        voteSumByUser.put(voterId, newSum);
        if (voteType == 1) upvotes++;
        else downvotes++;
        return true;
    }

    @Override
    public String toString() {
        return "postId=" + id + " | user=" + userId
                + " | score=" + score() + " (up=" + upvotes + ", down=" + downvotes + ")"
                + " | comments=" + comments.size()
                + " | time=" + timestampMillis + " | text=" + content;
    }
}

/** A direct reply on a post. */
class Comment {
    String id;
    String userId;
    String text;
    long timestampMillis;

    Comment(String id, String userId, String text, long timestampMillis) {
        this.id = id;
        this.userId = userId;
        this.text = text;
        this.timestampMillis = timestampMillis;
    }
}

/**
 * The ranking rules of the feed (Strategy pattern).
 * The feed code only calls compare(), so a new ranking needs only a new Comparator.
 */
class FeedRanking implements Comparator<Post> {
    Set<String> followedIds;  // users the viewer follows

    FeedRanking(Set<String> followedIds) {
        this.followedIds = followedIds;
    }

    /** Returns a negative number when post a should be shown before post b. */
    @Override
    public int compare(Post a, Post b) {
        // 1. posts by followed users first
        boolean aFollowed = followedIds.contains(a.userId);
        boolean bFollowed = followedIds.contains(b.userId);
        if (aFollowed != bFollowed) return aFollowed ? -1 : 1;

        // 2. higher score first
        if (a.score() != b.score()) return Integer.compare(b.score(), a.score());

        // 3. more comments first
        if (a.comments.size() != b.comments.size()) {
            return Integer.compare(b.comments.size(), a.comments.size());
        }

        // 4. newer first
        if (a.timestampMillis != b.timestampMillis) {
            return Long.compare(b.timestampMillis, a.timestampMillis);
        }

        // 5. smaller postId first
        return a.id.compareTo(b.id);
    }
}
```


## Complexity

`U` users, `P` posts, `C` comments, `V` accepted votes, `F` follow records, `k = limit`.

| Operation | Brute Force | Final Solution |
|---|---|---|
| `signUp` | `O(U)` | `O(1)` |
| `follow` | `O(U + F)` | `O(1)` |
| `post` | `O(U + P)` | `O(1)` |
| `replyToPost` | `O(U + P + C)` | `O(1)` |
| `votePost` | `O(U + P + V)` | `O(1)` |
| `showNewsFeed` | `O(U + P * (V + C + F) + P log P)` | `O(P log k)` |


## Edge Cases Worth Remembering

- The viewer's own posts appear in their feed as "not followed", because nobody can follow themselves.
- Post ids and comment ids are checked separately. A comment may use the same id as a post.
- `voteType` must be exactly `1` or `-1`. Values like `0` or `2` return `false`.
- Ties are finally broken by comparing `postId` as text, so `"p10"` comes before `"p9"`.
- A new follow changes the very next feed, because the ranking reads the `following` set at that moment.