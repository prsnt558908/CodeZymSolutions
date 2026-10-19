# Design Social Networking Site Friend Recommendations in Java

#### Problem Statement
[https://codezym.com/question/318-social-networking-site-friend-recommendations](https://codezym.com/question/318-social-networking-site-friend-recommendations)

This problem has two parts.

The first part is simple bookkeeping: storing posts, likes, comments and friend requests, and rejecting invalid actions. `HashMap` and `HashSet` turn every one of these checks into an O(1) lookup.

The second part is the interesting one: **People You May Know**. Friendships form a graph. The people to recommend are the friends of your friends, ranked by how many mutual friends you share. We find them by walking just two steps away from the user and counting how many times each person shows up.

For the design, we use the **Strategy pattern** for the recommendation algorithm, because it is the part most likely to change (mutual friends today, shared interests or location tomorrow). The site class only talks to a `RecommendationStrategy` interface. Everything else stays as plain classes (`User`, `Post`, `Comment`), because extra patterns there would add code without adding value.

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

## Improvement 1: HashMap and HashSet for O(1) Checks

Most operations ask one simple question: does this already exist? `HashMap` and `HashSet` answer it in O(1).

| Data structure | Why we need it |
|---|---|
| `Map<String, User> users` | Find a user by id, and check that the user exists |
| `Map<String, Post> posts` | Find a post by id, and check if a post id is already taken |
| `Set<String> commentIds` | Comment ids are unique across all posts, so one set for the whole site checks them |
| `Set<String> likedBy` inside `Post` | A set never holds the same user twice, so `likedBy.add(userId)` returns `false` on a second like |

The `User` class is where the friend logic lives. Each user keeps three sets:

```java
Set<String> friends = new HashSet<>();           // accepted friends
Set<String> sentRequests = new HashSet<>();      // pending requests this user sent
Set<String> receivedRequests = new HashSet<>();  // pending requests this user received
```

Why two request sets and not one? Because the rules care about direction. Answering a request looks at one direction only, while sending a new request and recommending people must look at both. With both sets, a user can answer "is anything pending between me and this person?" with two quick lookups:

```java
boolean hasPendingRequestWith(String otherUserId) {
    return sentRequests.contains(otherUserId) || receivedRequests.contains(otherUserId);
}
```

The friend request flow now becomes short and clear:

- **Send:** if both users exist, are different people, are not friends yet and have nothing pending, add `toUserId` to the sender's `sentRequests` and `fromUserId` to the receiver's `receivedRequests`.
- **Respond:** if `fromUserId` has no pending request to `toUserId`, return `REQUEST_NOT_FOUND`. Otherwise remove the request from both sets. If it is accepted, add each user to the other user's `friends`, because a friendship is always mutual.

## Improvement 2: Walk Friends of Friends, Not Every User

Even with sets, the first attempt still tests every user on the site:

```java
// First attempt: test every user on the site
Map<String, Integer> mutualCount = new HashMap<>();
for (User other : users.values()) {
    if (other.userId.equals(user.userId) || user.friends.contains(other.userId)
            || user.hasPendingRequestWith(other.userId)) {
        continue;
    }
    int mutual = 0;
    for (String friendId : user.friends) {
        if (other.friends.contains(friendId)) {
            mutual++;
        }
    }
    if (mutual > 0) {
        mutualCount.put(other.userId, mutual);
    }
}
```

This costs O(U × F) per call, where U is the number of users on the site and F is the number of friends of our user.

Let us look at it from the user's side instead. A friend of a friend is someone we can reach in **two steps**: me → my friend → their friend.

Here is the key observation. If we reach Chetan through 3 of our friends, then those 3 people are friends with both of us. So **the number of times we reach a person is exactly the number of mutual friends**. No set comparison is needed.

```java
// candidate id -> number of mutual friends
Map<String, Integer> mutualCount = new HashMap<>();
for (String friendId : user.friends) {
    for (String candidateId : users.get(friendId).friends) {
        if (isEligible(user, candidateId)) {
            mutualCount.put(candidateId, mutualCount.getOrDefault(candidateId, 0) + 1);
        }
    }
}
```

`isEligible()` skips the user, existing friends and anyone with a pending request in either direction.

Now we only visit people who are actually connected to the user. Strangers are never touched. And because every candidate is a key in the map, each person can appear only once.

Finally, sort the candidates: more mutual friends first, and for equal counts, user id in ascending order. Then return the first `limit` of them.

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

If this logic lives inside `SocialNetworkingSite`, every new idea means editing the class that also handles posts and friend requests. So we put it behind an interface:

```java
interface RecommendationStrategy {
    List<String> recommend(User user, Map<String, User> users, int limit);
}
```

`MutualFriendsStrategy` is our current implementation. The site only holds a `RecommendationStrategy` field and calls `recommend()`. To try a new algorithm, we write a new class and change that one field. Even the brute force code above could be wrapped as a strategy, and the site would not notice any difference.

Why Strategy instead of plain code? With plain code, the ranking rule and the site are glued together. Strategy keeps them apart for the price of one small interface, and ranking is exactly the part that keeps changing in real systems.

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
| `RecommendationStrategy` | Interface for any recommendation algorithm |
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

- Keep incoming requests grouped by receiver, just like our `receivedRequests` set. In a database, that is a table indexed by receiver id.
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

## Final Java Code

```java
import java.util.*;

/**
 * A comment written by a user on a post.
 */
class Comment {
    String commentId;
    String userId;
    String text;

    Comment(String commentId, String userId, String text) {
        this.commentId = commentId;
        this.userId = userId;
        this.text = text;
    }
}

/**
 * A post along with its likes and comments.
 */
class Post {
    String postId;
    String authorId;
    String content;
    // a set, so the same user can never like this post twice
    Set<String> likedBy = new HashSet<>();
    List<Comment> comments = new ArrayList<>();

    Post(String postId, String authorId, String content) {
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
    }
}

/**
 * A user with their friends and pending friend requests.
 */
class User {
    String userId;
    Set<String> friends = new HashSet<>();
    // people this user sent a request to, still waiting for an answer
    Set<String> sentRequests = new HashSet<>();
    // people who sent a request to this user, still waiting for an answer
    Set<String> receivedRequests = new HashSet<>();

    User(String userId) {
        this.userId = userId;
    }

    /** True if a request is pending between this user and the other user, in either direction. */
    boolean hasPendingRequestWith(String otherUserId) {
        return sentRequests.contains(otherUserId) || receivedRequests.contains(otherUserId);
    }
}

/**
 * Strategy: decides which users to recommend to a user.
 * A new algorithm only needs a new class. The site class does not change.
 */
interface RecommendationStrategy {
    List<String> recommend(User user, Map<String, User> users, int limit);
}

/**
 * Recommends friends of friends.
 * More mutual friends come first. Equal counts are sorted by user id (A to Z).
 */
class MutualFriendsStrategy implements RecommendationStrategy {

    public List<String> recommend(User user, Map<String, User> users, int limit) {
        // candidate id -> number of mutual friends
        Map<String, Integer> mutualCount = new HashMap<>();

        // each time we reach a candidate through one of our friends,
        // that friend is one more mutual friend
        for (String friendId : user.friends) {
            for (String candidateId : users.get(friendId).friends) {
                if (isEligible(user, candidateId)) {
                    mutualCount.put(candidateId, mutualCount.getOrDefault(candidateId, 0) + 1);
                }
            }
        }

        List<String> candidates = new ArrayList<>(mutualCount.keySet());
        candidates.sort((a, b) -> {
            int countA = mutualCount.get(a);
            int countB = mutualCount.get(b);
            if (countA != countB) {
                return countB - countA;      // more mutual friends first
            }
            return a.compareTo(b);           // then user id in ascending order
        });

        List<String> result = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < limit; i++) {
            result.add(candidates.get(i));
        }
        return result;
    }

    /** Skips the user, existing friends and anyone with a pending request in either direction. */
    boolean isEligible(User user, String candidateId) {
        return !candidateId.equals(user.userId)
                && !user.friends.contains(candidateId)
                && !user.hasPendingRequestWith(candidateId);
    }
}

/**
 * Entry point of the site. Stores users, posts and comment ids,
 * and hands "people you may know" to a recommendation strategy.
 */
public class SocialNetworkingSite {
    Map<String, User> users = new HashMap<>();
    Map<String, Post> posts = new HashMap<>();
    // comment ids must be unique across all posts
    Set<String> commentIds = new HashSet<>();
    // change this one object to change how recommendations are made
    RecommendationStrategy recommendationStrategy = new MutualFriendsStrategy();

    public SocialNetworkingSite(List<String> userIds) {
        for (String userId : userIds) {
            users.put(userId, new User(userId));
        }
    }

    public boolean addPost(String userId, String postId, String content) {
        if (!users.containsKey(userId) || posts.containsKey(postId) || isEmpty(content)) {
            return false;
        }
        posts.put(postId, new Post(postId, userId, content));
        return true;
    }

    public boolean likePost(String userId, String postId) {
        Post post = posts.get(postId);
        if (!users.containsKey(userId) || post == null) {
            return false;
        }
        // add() returns false if this user already liked the post
        return post.likedBy.add(userId);
    }

    public boolean addComment(String userId, String postId, String commentId, String comment) {
        Post post = posts.get(postId);
        if (!users.containsKey(userId) || post == null
                || commentIds.contains(commentId) || isEmpty(comment)) {
            return false;
        }
        commentIds.add(commentId);
        post.comments.add(new Comment(commentId, userId, comment));
        return true;
    }

    public String sendFriendRequest(String fromUserId, String toUserId) {
        User sender = users.get(fromUserId);
        User receiver = users.get(toUserId);
        if (sender == null || receiver == null || fromUserId.equals(toUserId)
                || sender.friends.contains(toUserId)
                || sender.hasPendingRequestWith(toUserId)) {
            return "INVALID_REQUEST";
        }
        sender.sentRequests.add(toUserId);
        receiver.receivedRequests.add(fromUserId);
        return "REQUEST_SENT";
    }

    public String respondToFriendRequest(String fromUserId, String toUserId, boolean accept) {
        User sender = users.get(fromUserId);
        User receiver = users.get(toUserId);
        // only a request sent by fromUserId to toUserId counts, not the other way round
        if (sender == null || receiver == null || !sender.sentRequests.contains(toUserId)) {
            return "REQUEST_NOT_FOUND";
        }
        // accepted or rejected, the request is not pending anymore
        sender.sentRequests.remove(toUserId);
        receiver.receivedRequests.remove(fromUserId);
        if (!accept) {
            return "REQUEST_REJECTED";
        }
        // friendship is always mutual, so save it on both sides
        sender.friends.add(toUserId);
        receiver.friends.add(fromUserId);
        return "REQUEST_ACCEPTED";
    }

    public List<String> getPeopleYouMayKnow(String userId, int limit) {
        User user = users.get(userId);
        if (user == null || limit <= 0) {
            return new ArrayList<>();
        }
        return recommendationStrategy.recommend(user, users, limit);
    }

    /** Text is empty if it is missing or has no visible character. */
    boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }
}
```