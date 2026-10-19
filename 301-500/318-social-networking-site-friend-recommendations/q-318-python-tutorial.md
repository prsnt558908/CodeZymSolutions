# Design Social Networking Site Friend Recommendations in Python

#### Problem Statement
[https://codezym.com/question/318-social-networking-site-friend-recommendations](https://codezym.com/question/318-social-networking-site-friend-recommendations)

This problem has two parts.

The first part is simple bookkeeping: storing posts, likes, comments and friend requests, and rejecting invalid actions. Dictionaries and sets turn every one of these checks into an O(1) lookup.

The second part is the interesting one: **People You May Know**. Friendships form a graph. The people to recommend are the friends of your friends, ranked by how many mutual friends you share. We find them by walking just two steps away from the user and counting how many times each person shows up.

For the design, we use the **Strategy pattern** for the recommendation algorithm, because it is the part most likely to change (mutual friends today, shared interests or location tomorrow). The site class only talks to a `RecommendationStrategy` abstract class. Everything else stays as plain classes (`User`, `Post`, `Comment`), because extra patterns there would add code without adding value.

## Key Rules to Notice

A few rules decide most of the edge cases.

- Post ids are unique across the whole site, and so are comment ids. A comment id cannot be reused, even on a different post.
- A user can like a post only once.
- A new friend request is invalid if a request is already pending **in either direction**. If Dev has a pending request to Isha, Isha cannot send one to Dev either.
- `respondToFriendRequest(fromUserId, toUserId, accept)` only matches a request sent **by** `fromUserId` **to** `toUserId`. A request in the other direction does not count.
- Accepting or rejecting ends the request. After a rejection, either user can send a new one.
- People You May Know skips the user, current friends, and anyone with a pending request in either direction.

## First Attempt: Lists and Brute Force

The most direct idea is to keep everything in lists: a list of posts, a list of likes, a list of comments, a list of friend pairs and a list of pending requests.

Every check then becomes a loop. Is this post id already taken? Loop over all posts. Did this user already like the post? Loop over all likes.

For People You May Know, we loop over **every user on the site**, skip the ones we must exclude, and count how many friends each of them shares with our user. Anyone with at least one shared friend is a friend of a friend. Then we sort them.

This gives correct answers, but it has three problems:

1. Every duplicate check scans a whole list, which is O(n).
2. People You May Know looks at all users (up to 10,000) on every call, even though most of them have no link to our user.
3. The ranking logic is stuck inside the main class. Changing how we rank people means editing the same class that handles posts and friend requests.

Let us fix them one by one.

## Improvement 1: Dictionaries and Sets for O(1) Checks

Most operations ask one simple question: does this already exist? A dictionary or a set answers it in O(1).

| Data structure | Why we need it |
|---|---|
| `users` dictionary (user id to `User`) | Find a user by id, and check that the user exists |
| `posts` dictionary (post id to `Post`) | Find a post by id, and check if a post id is already taken |
| `comment_ids` set | Comment ids are unique across all posts, so one set for the whole site checks them |
| `liked_by` set inside `Post` | A set never holds the same user twice, so `userId in post.liked_by` instantly tells us about a second like |

The `User` class is where the friend logic lives. Each user keeps three sets:

```python
self.friends = set()            # accepted friends
self.sent_requests = set()      # pending requests this user sent
self.received_requests = set()  # pending requests this user received
```

Why two request sets and not one? Because the rules care about direction. Answering a request looks at one direction only, while sending a new request and recommending people must look at both. With both sets, a user can answer "is anything pending between me and this person?" with two quick lookups:

```python
def has_pending_request_with(self, other_user_id):
    return other_user_id in self.sent_requests or other_user_id in self.received_requests
```

The friend request flow now becomes short and clear:

- **Send:** if both users exist, are different people, are not friends yet and have nothing pending, add `toUserId` to the sender's `sent_requests` and `fromUserId` to the receiver's `received_requests`.
- **Respond:** if `fromUserId` has no pending request to `toUserId`, return `REQUEST_NOT_FOUND`. Otherwise remove the request from both sets. If it is accepted, add each user to the other user's `friends`, because a friendship is always mutual.

## Improvement 2: Walk Friends of Friends, Not Every User

Even with sets, the first attempt still tests every user on the site:

```python
# First attempt: test every user on the site
mutual_count = {}
for other in users.values():
    if (other.user_id == user.user_id or other.user_id in user.friends
            or user.has_pending_request_with(other.user_id)):
        continue
    mutual = 0
    for friend_id in user.friends:
        if friend_id in other.friends:
            mutual += 1
    if mutual > 0:
        mutual_count[other.user_id] = mutual
```

This costs O(U × F) per call, where U is the number of users on the site and F is the number of friends of our user.

Let us look at it from the user's side instead. A friend of a friend is someone we can reach in **two steps**: me → my friend → their friend.

Here is the key observation. If we reach Chetan through 3 of our friends, then those 3 people are friends with both of us. So **the number of times we reach a person is exactly the number of mutual friends**. No set comparison is needed.

```python
# candidate id -> number of mutual friends
mutual_count = {}
for friend_id in user.friends:
    for candidate_id in users[friend_id].friends:
        if self.is_eligible(user, candidate_id):
            mutual_count[candidate_id] = mutual_count.get(candidate_id, 0) + 1
```

`is_eligible()` skips the user, existing friends and anyone with a pending request in either direction.

Now we only visit people who are actually connected to the user. Strangers are never touched. And because every candidate is a key in the dictionary, each person can appear only once.

Finally, sort the candidates: more mutual friends first, and for equal counts, user id in ascending order. The sort key `(-count, user_id)` does both in one go. Then return the first `limit` of them.

### Quick Dry Run (Example 3)

```
arun --- bina --- chetan
          |
          +------ diya
```

- arun has one friend: bina.
- bina's friends are arun, chetan and diya. We skip arun, because arun is the user we are finding suggestions for.
- chetan is reached once and diya is reached once, so both have 1 mutual friend.
- The counts are equal, so we sort by id and get `["chetan", "diya"]`.

## Improvement 3: Strategy Pattern for Recommendations

Ranking by mutual friends is only one way to suggest people. Real social networks also use shared schools, workplaces, groups or location.

If this logic lives inside `SocialNetworkingSite`, every new idea means editing the class that also handles posts and friend requests. So we put it behind an abstract class:

```python
class RecommendationStrategy(ABC):
    @abstractmethod
    def recommend(self, user, users, limit):
        pass
```

`MutualFriendsStrategy` is our current implementation. The site only holds a `recommendation_strategy` object and calls `recommend()`. To try a new algorithm, we write a new class and change that one line. Even the brute force code above could be wrapped as a strategy, and the site would not notice any difference.

Why Strategy instead of plain code? With plain code, the ranking rule and the site are glued together. Strategy keeps them apart for the price of one small abstract class, and ranking is exactly the part that keeps changing in real systems.

## Patterns That Sound Good but Are Not Needed

**State pattern for friend requests.** A request moves from pending to accepted or rejected, so State looks like a natural fit. But once a request is answered, we simply remove it. No behavior changes from one state to another. Two sets and one `if` do the whole job, while State would add 3 or 4 extra classes.

**Observer pattern for likes, comments and new friendships.** Observer is great when other parts of the system must react to an event, for example sending a notification or refreshing a recommendation cache. Nothing in this problem listens to such events. Every answer is computed when it is asked for, so Observer would only add empty plumbing.

## Class Overview

| Class | Responsibility |
|---|---|
| `SocialNetworkingSite` | Entry point. Validates every call and updates users, posts and comments |
| `User` | Friends, sent requests and received requests of one user |
| `Post` | Content, likes and comments of one post |
| `Comment` | One comment on a post |
| `RecommendationStrategy` | Abstract class for any recommendation algorithm |
| `MutualFriendsStrategy` | Friends of friends, ranked by number of mutual friends |

## Complexity

| Operation | Time |
|---|---|
| `addPost`, `likePost`, `addComment` | O(1) |
| `sendFriendRequest`, `respondToFriendRequest` | O(1) |
| `getPeopleYouMayKnow` | O(V + C log C) |

Here V is the sum of the friend counts of all the user's friends (everyone we walk through), and C is the number of candidates we find.

## Follow-up Interview Questions

**1. What if one user receives a huge number of friend requests?**

- Keep incoming requests grouped by receiver, just like our `received_requests` set. In a database, that is a table indexed by receiver id.
- Show the requests page by page (for example 20 at a time) instead of loading all of them, and limit how many requests one user can send per day.

**2. How do we handle concurrency, dirty reads and race conditions?**

- The classic race: two users send requests to each other at the same moment. Both checks see "nothing pending", so both requests get created.
- Make "check and write" one atomic step. In a database, keep one row per pair of users, keyed by (smaller id, larger id), with a unique constraint. In memory, lock both users, always in sorted id order, so two threads can never deadlock.
- Save both sides of a new friendship in one transaction. Readers then see either no friendship or the full friendship, never a half saved one (reading half saved data is exactly a dirty read).

**3. How would People You May Know work at scale?**

- Our algorithm, friends of friends ranked by mutual friends, is the classic starting point.
- At scale, compute suggestions in a background job, cache them, and refresh them when friendships change.
- For users with huge friend lists, walk only a sample of their friends, and keep just the top `limit` results in a small min-heap instead of sorting everything.
- Add more signals to the score, like shared groups, workplace, school or location.

**4. Where should the data be stored?**

- Users, posts and comments: a relational or document database, split into shards by id.
- Likes: a key value or wide column store that can handle a very high write rate.
- Friendships: a graph database fits friends of friends queries naturally. A sharded table of (userId, friendId) pairs also works well.
- Ready-made recommendations: an in-memory cache such as Redis.

## Final Python Code

```python
from abc import ABC, abstractmethod


class Comment:
    """A comment written by a user on a post."""

    def __init__(self, comment_id, user_id, text):
        self.comment_id = comment_id
        self.user_id = user_id
        self.text = text


class Post:
    """A post along with its likes and comments."""

    def __init__(self, post_id, author_id, content):
        self.post_id = post_id
        self.author_id = author_id
        self.content = content
        # a set, so each user is stored at most once
        self.liked_by = set()
        self.comments = []


class User:
    """A user with their friends and pending friend requests."""

    def __init__(self, user_id):
        self.user_id = user_id
        self.friends = set()
        # people this user sent a request to, still waiting for an answer
        self.sent_requests = set()
        # people who sent a request to this user, still waiting for an answer
        self.received_requests = set()

    def has_pending_request_with(self, other_user_id):
        """True if a request is pending between this user and the other user, in either direction."""
        return other_user_id in self.sent_requests or other_user_id in self.received_requests


class RecommendationStrategy(ABC):
    """
    Strategy: decides which users to recommend to a user.
    A new algorithm only needs a new class. The site class does not change.
    """

    @abstractmethod
    def recommend(self, user, users, limit):
        pass


class MutualFriendsStrategy(RecommendationStrategy):
    """
    Recommends friends of friends.
    More mutual friends come first. Equal counts are sorted by user id (A to Z).
    """

    def recommend(self, user, users, limit):
        # candidate id -> number of mutual friends
        mutual_count = {}

        # each time we reach a candidate through one of our friends,
        # that friend is one more mutual friend
        for friend_id in user.friends:
            for candidate_id in users[friend_id].friends:
                if self.is_eligible(user, candidate_id):
                    mutual_count[candidate_id] = mutual_count.get(candidate_id, 0) + 1

        # more mutual friends first, then user id in ascending order
        candidates = sorted(mutual_count, key=lambda cid: (-mutual_count[cid], cid))
        return candidates[:limit]

    def is_eligible(self, user, candidate_id):
        """Skips the user, existing friends and anyone with a pending request in either direction."""
        return (candidate_id != user.user_id
                and candidate_id not in user.friends
                and not user.has_pending_request_with(candidate_id))


class SocialNetworkingSite:
    """
    Entry point of the site. Stores users, posts and comment ids,
    and hands "people you may know" to a recommendation strategy.
    """

    def __init__(self, userIds):
        self.users = {}           # user id -> User
        self.posts = {}           # post id -> Post
        # comment ids must be unique across all posts
        self.comment_ids = set()
        # change this one object to change how recommendations are made
        self.recommendation_strategy = MutualFriendsStrategy()
        for user_id in userIds:
            self.users[user_id] = User(user_id)

    def addPost(self, userId, postId, content):
        if userId not in self.users or postId in self.posts or self.is_empty(content):
            return False
        self.posts[postId] = Post(postId, userId, content)
        return True

    def likePost(self, userId, postId):
        post = self.posts.get(postId)
        if userId not in self.users or post is None or userId in post.liked_by:
            return False
        post.liked_by.add(userId)
        return True

    def addComment(self, userId, postId, commentId, comment):
        post = self.posts.get(postId)
        if (userId not in self.users or post is None
                or commentId in self.comment_ids or self.is_empty(comment)):
            return False
        self.comment_ids.add(commentId)
        post.comments.append(Comment(commentId, userId, comment))
        return True

    def sendFriendRequest(self, fromUserId, toUserId):
        sender = self.users.get(fromUserId)
        receiver = self.users.get(toUserId)
        if (sender is None or receiver is None or fromUserId == toUserId
                or toUserId in sender.friends
                or sender.has_pending_request_with(toUserId)):
            return "INVALID_REQUEST"
        sender.sent_requests.add(toUserId)
        receiver.received_requests.add(fromUserId)
        return "REQUEST_SENT"

    def respondToFriendRequest(self, fromUserId, toUserId, accept):
        sender = self.users.get(fromUserId)
        receiver = self.users.get(toUserId)
        # only a request sent by fromUserId to toUserId counts, not the other way round
        if sender is None or receiver is None or toUserId not in sender.sent_requests:
            return "REQUEST_NOT_FOUND"
        # accepted or rejected, the request is not pending anymore
        sender.sent_requests.remove(toUserId)
        receiver.received_requests.remove(fromUserId)
        if not accept:
            return "REQUEST_REJECTED"
        # friendship is always mutual, so save it on both sides
        sender.friends.add(toUserId)
        receiver.friends.add(fromUserId)
        return "REQUEST_ACCEPTED"

    def getPeopleYouMayKnow(self, userId, limit):
        user = self.users.get(userId)
        if user is None or limit <= 0:
            return []
        return self.recommendation_strategy.recommend(user, self.users, limit)

    def is_empty(self, text):
        """Text is empty if it is missing or has no visible character."""
        return text is None or text.strip() == ""
```