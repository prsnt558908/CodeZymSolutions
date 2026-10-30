# Design Social Network News Feed in Python

#### Problem Statement
[https://codezym.com/question/85-design-social-network-news-feed](https://codezym.com/question/85-design-social-network-news-feed)


## Core Idea

This problem is mostly about keeping the right numbers ready. We keep users and posts in dicts, so every lookup is fast. Every post also keeps its own live counters: upvotes, downvotes, comments and the running vote total of each user.

With those counters ready, the news feed becomes a pure ranking job. We put the ranking rules in one small class that turns a post into a sort key. This is the **Strategy pattern** in its lightest form, and it is the only pattern this problem really needs. **Observer** looks tempting for a news feed, but here it adds work and saves nothing.

We will start with a brute force solution that keeps everything in plain lists. Then we will fix its slow parts one by one.


## Understanding the Problem

A few rules decide almost every answer:

- **Following is one way.** A user cannot follow themselves. Following the same person again still returns `True`.
- **The vote rule.** After every `votePost` call, the sum of one user's votes on one post must be `-1`, `0` or `1`. Otherwise the call returns `False`.
- **`up` and `down` count every accepted vote**, not only the final state. So `+1, -1, -1` from the same user gives `up=1, down=2, score=-1`.
- **The feed shows every post**, not only posts by followed users. Posts by followed users just come first, even if another post has a higher score.
- **Ranking order:** followed first, then higher score, then more comments, then newer, then smaller `postId` as text.


## Solution 1: Brute Force (Everything in Lists)

The simplest idea is to save every event in a plain list, exactly as it happens.

```python
self.users = []
self.follows = []      # "follower->followee"
self.posts = []
self.comments = []
self.votes = []        # every accepted vote
```

Every check becomes a scan. For the vote rule, we add up all earlier votes of this user on this post:

```python
total = 0
for v in self.votes:
    if v.user_id == userId and v.post_id == postId:
        total += v.vote_type
if abs(total + voteType) > 1:
    return False
self.votes.append(Vote(userId, postId, voteType))
```

The feed is the worst part. On every call we count the votes and comments of every post again, then sort all posts:

```python
# recount everything for every post, on every call
for post in self.posts:
    post.up = 0
    post.down = 0
    post.comment_count = 0
    for v in self.votes:
        if v.post_id != post.id:
            continue
        if v.vote_type == 1:
            post.up += 1
        else:
            post.down += 1
    for c in self.comments:
        if c.post_id == post.id:
            post.comment_count += 1
    post.followed = (viewerUserId + "->" + post.user_id) in self.follows
# then sort all posts by the ranking rules and return the first `limit`
```

### Problems with this approach

- Every lookup, like "does this user exist?", scans a whole list.
- Every vote scans all earlier votes.
- Every feed call recounts everything for every post, then sorts all posts even when we only need the top `limit`.

The counts only change when a vote or a comment happens. So recounting them on every feed call is wasted work.


## Solution 2: Dicts, Live Counters and a Ranking Strategy

We fix the problems above one at a time.

### Improvement 1: Dicts and sets for lookups

We keep `users` (`user id -> User`), `posts` (`post id -> Post`) and a set of `comment_ids`. Every "does it exist?" check becomes `O(1)`.

Each `User` also keeps a `following` set. So "does the viewer follow this author?" is `O(1)`, and following someone twice changes nothing.

### Improvement 2: Each post keeps its own counters

Each post updates its `upvotes`, `downvotes` and `comments` list the moment a vote or a comment arrives. The feed reads these values directly and never counts anything again.

### Improvement 3: The vote rule in one lookup

Each post keeps a dict `vote_sum_by_user` (`user id -> running sum`). A new vote is accepted only if the new sum stays between `-1` and `1`.

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

`heapq.nsmallest(limit, posts, key=...)` does exactly this. It keeps a heap of at most `limit` posts while it scans all of them, then returns those posts sorted from best to worst. This costs `O(P log limit)` instead of `O(P log P)`.

The `key` turns each post into a tuple. Python compares tuples item by item, and a smaller key comes first:

```python
(not_followed, -score, -comment_count, -timestamp, post_id)
```

`not_followed` is `0` for followed authors, so they come first. We negate the numbers because `nsmallest` puts small keys first, while we want bigger scores, more comments and newer posts first. The `post_id` comes last and is compared as text.


## Which Design Pattern Fits?

### Strategy: yes, in its lightest form

The ranking rules are the part of a news feed that changes most often. Today it is "followed first, then score". Tomorrow it may be "newest first" or a trending score.

We could build the sort key directly inside `showNewsFeed`. It works, but then one method does two jobs: picking the top posts and deciding what "top" means.

So the rules live in their own class, `FeedRanking`. Its `sort_key()` builds that tuple, and the feed code only passes `ranking.sort_key` to `heapq.nsmallest`. A new ranking needs only a new class with its own `sort_key()`.

We do not add a bigger setup, like a base strategy class plus a factory. The problem has only one ranking, so that would be extra code with no benefit.

### Observer: tempting, but not a good fit here

Many real news feeds use "fan out on write". When a user posts, every follower is notified (Observer) and the post is pushed into a ready-made feed for that follower.

That does not help here, for two reasons:

- **Every viewer sees every post**, not only posts by people they follow. A pushed feed per user would just be a copy of all posts.
- **The order keeps changing.** Votes and comments change a post's rank after it was pushed, so every vote would mean fixing many stored feeds.

Ranking when the feed is read, from live counters, is simpler and always correct.


## Classes and Why We Need Them

- **`SocialNetworkNewsFeed`** is the entry point. It owns the dicts, checks every rule and builds the feed.
- **`User`** holds the `following` set. We need it for the "followed first" rule.
- **`Post`** holds the content and the live counters. It also owns `vote_sum_by_user`, so the vote rule lives right next to the data it checks.
- **`Comment`** is one reply on a post. The post keeps a list of them, so `len(post.comments)` is the comment count.
- **`FeedRanking`** holds the ranking rules. This is our strategy.


## Final Code

```python
import heapq
from typing import List


class User:
    """A user and the ids of the users they follow."""

    def __init__(self, user_id):
        self.id = user_id
        self.following = set()


class Post:
    """A post that keeps its own counters up to date,
    so ranking never has to count votes or comments again."""

    def __init__(self, post_id, user_id, content, timestamp_millis):
        self.id = post_id
        self.user_id = user_id
        self.content = content
        self.timestamp_millis = timestamp_millis
        self.upvotes = 0
        self.downvotes = 0
        self.comments = []          # direct comments on this post
        self.vote_sum_by_user = {}  # user id -> sum of their votes here

    def score(self):
        return self.upvotes - self.downvotes

    def vote(self, voter_id, vote_type):
        """Adds a +1 or -1 vote.
        Fails if it would push this user's total outside -1..1."""
        new_sum = self.vote_sum_by_user.get(voter_id, 0) + vote_type
        if new_sum < -1 or new_sum > 1:
            return False
        self.vote_sum_by_user[voter_id] = new_sum
        if vote_type == 1:
            self.upvotes += 1
        else:
            self.downvotes += 1
        return True

    def __str__(self):
        return (f"postId={self.id} | user={self.user_id}"
                f" | score={self.score()}"
                f" (up={self.upvotes}, down={self.downvotes})"
                f" | comments={len(self.comments)}"
                f" | time={self.timestamp_millis} | text={self.content}")


class Comment:
    """A direct reply on a post."""

    def __init__(self, comment_id, user_id, text, timestamp_millis):
        self.id = comment_id
        self.user_id = user_id
        self.text = text
        self.timestamp_millis = timestamp_millis


class FeedRanking:
    """The ranking rules of the feed (Strategy pattern).
    The feed code only calls sort_key(),
    so a new ranking needs only a new class."""

    def __init__(self, followed_ids):
        self.followed_ids = followed_ids  # users the viewer follows

    def sort_key(self, post):
        """A smaller key means the post is shown earlier."""
        not_followed = 0 if post.user_id in self.followed_ids else 1
        return (not_followed,             # followed users first
                -post.score(),            # higher score
                -len(post.comments),      # more comments
                -post.timestamp_millis,   # newer
                post.id)                  # smaller postId


class SocialNetworkNewsFeed:
    """In-memory social network with a ranked news feed.
    Dicts give O(1) lookups, every post keeps its own counters,
    and FeedRanking decides the order of the feed."""

    def __init__(self):
        self.users = {}           # user id -> User
        self.posts = {}           # post id -> Post
        self.comment_ids = set()  # every comment id used so far

    def signUp(self, userId: str) -> bool:
        if userId in self.users:
            return False
        self.users[userId] = User(userId)
        return True

    def follow(self, followerUserId: str, followeeUserId: str) -> bool:
        follower = self.users.get(followerUserId)
        if follower is None or followeeUserId not in self.users:
            return False
        if followerUserId == followeeUserId:
            return False
        # a set, so following again changes nothing
        follower.following.add(followeeUserId)
        return True

    def post(self, postId: str, userId: str, content: str,
             timestampMillis: int) -> bool:
        if userId not in self.users or postId in self.posts:
            return False
        self.posts[postId] = Post(postId, userId, content, timestampMillis)
        return True

    def replyToPost(self, commentId: str, userId: str, postId: str,
                    commentText: str, timestampMillis: int) -> bool:
        post = self.posts.get(postId)
        if userId not in self.users or post is None:
            return False
        if commentId in self.comment_ids:
            return False
        self.comment_ids.add(commentId)
        post.comments.append(
            Comment(commentId, userId, commentText, timestampMillis))
        return True

    def votePost(self, userId: str, postId: str, voteType: int) -> bool:
        post = self.posts.get(postId)
        if userId not in self.users or post is None:
            return False
        if voteType != 1 and voteType != -1:
            return False
        return post.vote(userId, voteType)

    def showNewsFeed(self, viewerUserId: str, limit: int) -> List[str]:
        viewer = self.users.get(viewerUserId)
        if viewer is None or limit <= 0:
            return []

        ranking = FeedRanking(viewer.following)

        # nsmallest keeps a heap of at most `limit` posts while it scans
        # all of them, and returns those posts sorted from best to worst
        top = heapq.nsmallest(limit, self.posts.values(),
                              key=ranking.sort_key)
        return [str(post) for post in top]
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
- `voteType` must be exactly `1` or `-1`. Values like `0` or `2` return `False`.
- Ties are finally broken by comparing `postId` as text, so `"p10"` comes before `"p9"`.
- A new follow changes the very next feed, because the ranking reads the `following` set at that moment.