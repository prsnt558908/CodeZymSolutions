# Design Live News Feed System in Java

#### Problem Statement

[https://codezym.com/question/93-design-live-news-feed-system](https://codezym.com/question/93-design-live-news-feed-system)

Use the **Observer pattern** to notify users who follow an article's topics. Store articles in shared topic lists, and remember the list ranges during which each user was subscribed. This supports one notification per article and a feed that checks subscriptions both at publication and when fetched.


## Start with a simple approach

We could store all articles in one list. Publishing would check every user, and fetching a feed would scan every article. We would also need historical subscription information to decide which articles qualify.

Instead, keep subscribers and articles grouped by topic. This avoids checking unrelated users during publication. Subscription ranges let feed requests read the relevant parts of topic lists.


## Why Observer fits

Publishing an article is an event. Subscribed users are the observers, and `onArticlePublished` adds the article to their notification lists.

An article can match several topics followed by the same user. We first combine the subscriber IDs in a set, then notify each user once.

There is only one kind of observer here, so a direct callback method is enough. Strategy could help with several ranking rules, but the statement requires one fixed order. A simple sort is sufficient.


## The important classes

- `Article` holds the ID, title and publish time. Its formatting method produces `articleId | title | currentTime`.
- `Topic` holds articles in publication order and a set of current subscriber IDs. Multiple topics share references to the same article object.
- `Subscription` remembers the article ranges published while one user followed one topic. It also tracks whether that subscription is active.
- `User` holds subscriptions and pending notifications. `LiveNewsFeed` validates requests and connects these objects.

Maps provide direct user and topic lookup. Lists preserve article and notification order, while sets prevent duplicate topics and recipients.


## Remember subscription periods

Each topic has an append-only article list. When a user subscribes, save its current size as `activeStart`. On unsubscribe, save the range from that start up to the current size, then set `activeStart` to `-1`.

A range includes its start and excludes its end. Empty ranges are skipped.

For example, suppose the topic already contains `a0` when the user subscribes. The start is `1`. After publishing `a1`, the user unsubscribes, so we save `[1, 2)`.

Next, `a2` is published while the user is away. They subscribe again at position `3`, then `a3` is published. The eligible articles are `a1` and `a3`. Neither `a0` nor `a2` qualifies.

List positions distinguish operations with equal timestamps. An article published before the subscribe call is excluded, even if both calls use the same minute. Subscription eligibility therefore does not need to compare `currentTime` values.


## Apply the feed rule to the same topic

An article qualifies only if the user followed at least one of its topics when it was published and follows **that same topic** now.

Suppose an article has `sports` and `tech` tags. A user followed only `sports` at publication, then left `sports` and joined `tech`. The article must stay out of their feed.

Under the statement's checks at publication and fetch time, following `sports` again makes that earlier article eligible again. This is why completed ranges are retained. Articles published during an unsubscribed period remain excluded for that topic, and resubscribing never resends old notifications.


## How the methods work

**Subscribe or unsubscribe.** Check that the user and topic exist. Return `false` if the requested state is already set. Otherwise, open or close the subscription range and update the topic's subscriber set. An unchanged subscription must not reset its start.

**Publish an article.** Validate all inputs before changing anything. An empty title is allowed, but an empty topic list is invalid. Deduplicate topic IDs, append the article to each topic, and notify the union of their subscribers. An invalid request neither reserves the article ID nor sends notifications.

**Fetch the feed.** Read ranges only from currently active subscriptions. Collect articles in a map keyed by article ID to remove duplicates. Sort by publish time descending, then article ID ascending. Fetching a feed does not remove articles.

**Fetch notifications.** Return the pending articles in publication order, then replace the inbox with an empty list. Unsubscribing does not remove already queued notifications. Unknown users receive an empty list from either fetch method.


## Complexity

Map and set operations take expected constant time.

- Subscribe or unsubscribe takes amortized `O(1)` time.
- Publishing takes `O(K + S)` time, where `K` is the number of supplied topic entries and `S` is the sum of subscriber counts across the distinct topics.
- Fetching a feed takes `O(H + E + F log F)` time. `H` counts topics the user has ever followed, `E` counts eligible article references visited, and `F` counts distinct returned articles.
- Fetching notifications takes `O(P)` time for `P` pending notifications.

Persistent storage is `O(U + T + A + L + R + Q)`. These count users, topics, articles, article-topic references, subscription records and ranges, and pending notification entries. Each article is stored once, with references shared across topics and inboxes. A feed request uses `O(F)` extra space.


## Complete Java code

Use `LiveNewsFeed.java` as the filename. The helper classes are top-level classes in the same file, and the public methods match the starter code.

```java
import java.util.*;

class Article {
    String articleId;
    String title;
    int publishTime;

    Article(String articleId, String title, int publishTime) {
        this.articleId = articleId;
        this.title = title;
        this.publishTime = publishTime;
    }

    String format() {
        return articleId + " | " + title + " | " + publishTime;
    }
}

class Topic {
    List<Article> articles = new ArrayList<>();
    Set<String> subscribers = new HashSet<>();
}

class Subscription {
    Topic topic;
    List<int[]> completedRanges = new ArrayList<>();
    int activeStart = -1;

    Subscription(Topic topic) {
        this.topic = topic;
    }

    void collectArticles(Map<String, Article> eligibleArticles) {
        if (activeStart < 0) {
            return;
        }

        // Keep earlier subscribed periods when the user subscribes again.
        for (int[] range : completedRanges) {
            addRange(eligibleArticles, range[0], range[1]);
        }
        addRange(eligibleArticles, activeStart, topic.articles.size());
    }

    void addRange(Map<String, Article> eligibleArticles, int start, int end) {
        // The start is included and the end is excluded.
        for (int index = start; index < end; index++) {
            Article article = topic.articles.get(index);
            eligibleArticles.put(article.articleId, article);
        }
    }
}

class User {
    Map<String, Subscription> subscriptions = new HashMap<>();
    List<Article> pendingNotifications = new ArrayList<>();

    void onArticlePublished(Article article) {
        pendingNotifications.add(article);
    }
}

public class LiveNewsFeed {
    Map<String, User> usersById = new HashMap<>();
    Map<String, Topic> topicsById = new HashMap<>();
    Set<String> publishedArticleIds = new HashSet<>();

    public LiveNewsFeed(List<String> existingUserIds, List<String> existingTopicIds) {
        for (String userId : existingUserIds) {
            usersById.put(userId, new User());
        }
        for (String topicId : existingTopicIds) {
            topicsById.put(topicId, new Topic());
        }
    }

    public boolean subscribeUnsubscribeTopic(
            String userId, String topicId, boolean subscribe, int currentTime) {
        User user = usersById.get(userId);
        Topic topic = topicsById.get(topicId);
        if (user == null || topic == null) {
            return false;
        }

        Subscription subscription = user.subscriptions.get(topicId);
        if (subscribe) {
            if (subscription != null && subscription.activeStart >= 0) {
                return false;
            }
            if (subscription == null) {
                subscription = new Subscription(topic);
                user.subscriptions.put(topicId, subscription);
            }

            // List positions preserve call order even when timestamps tie.
            subscription.activeStart = topic.articles.size();
            topic.subscribers.add(userId);
        } else {
            if (subscription == null || subscription.activeStart < 0) {
                return false;
            }
            int end = topic.articles.size();
            if (subscription.activeStart < end) {
                subscription.completedRanges.add(
                    new int[] {subscription.activeStart, end}
                );
            }
            subscription.activeStart = -1;
            topic.subscribers.remove(userId);
        }
        return true;
    }

    public boolean publishArticle(
            String articleId, String title, List<String> topics, int currentTime) {
        if (articleId == null || !articleId.matches("[a-zA-Z0-9]{1,10}")
                || title == null || !title.matches("[a-zA-Z0-9 ]{0,100}")
                || topics == null || topics.isEmpty()
                || currentTime < 0 || currentTime > 100000000
                || publishedArticleIds.contains(articleId)) {
            return false;
        }

        Set<String> uniqueTopics = new HashSet<>();
        for (String topicId : topics) {
            if (!topicsById.containsKey(topicId)) {
                return false;
            }
            uniqueTopics.add(topicId);
        }

        // Validate everything before storing the article or notifying users.
        Article article = new Article(articleId, title, currentTime);
        publishedArticleIds.add(articleId);
        Set<String> recipients = new HashSet<>();
        for (String topicId : uniqueTopics) {
            Topic topic = topicsById.get(topicId);
            topic.articles.add(article);
            recipients.addAll(topic.subscribers);
        }

        // A user observing several matching topics still receives one update.
        for (String userId : recipients) {
            usersById.get(userId).onArticlePublished(article);
        }
        return true;
    }

    public List<String> getPersonalizedFeed(String userId) {
        User user = usersById.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        Map<String, Article> eligibleArticles = new HashMap<>();
        for (Subscription subscription : user.subscriptions.values()) {
            subscription.collectArticles(eligibleArticles);
        }

        List<Article> feed = new ArrayList<>(eligibleArticles.values());
        feed.sort((first, second) -> {
            int timeOrder = Integer.compare(second.publishTime, first.publishTime);
            if (timeOrder != 0) {
                return timeOrder;
            }
            return first.articleId.compareTo(second.articleId);
        });

        List<String> result = new ArrayList<>();
        for (Article article : feed) {
            result.add(article.format());
        }
        return result;
    }

    public List<String> getNotifications(String userId) {
        User user = usersById.get(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (Article article : user.pendingNotifications) {
            result.add(article.format());
        }
        user.pendingNotifications = new ArrayList<>();
        return result;
    }
}
```
