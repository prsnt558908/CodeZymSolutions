# Design Live News Feed System in Python

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

Dictionaries provide direct user and topic lookup. Lists preserve order, while sets prevent duplicates. Dataclasses keep the small data objects clear, and `default_factory` gives every object its own collections.


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


## Complete Python code

The method names and parameter order match the starter code.

```python
import re
from dataclasses import dataclass, field
from typing import Dict, List, Set, Tuple


@dataclass
class Article:
    articleId: str
    title: str
    publishTime: int

    def format(self) -> str:
        return f"{self.articleId} | {self.title} | {self.publishTime}"


@dataclass
class Topic:
    articles: List[Article] = field(default_factory=list)
    subscribers: Set[str] = field(default_factory=set)


@dataclass
class Subscription:
    topic: Topic
    completedRanges: List[Tuple[int, int]] = field(default_factory=list)
    activeStart: int = -1

    def collectArticles(self, eligibleArticles: Dict[str, Article]) -> None:
        if self.activeStart < 0:
            return

        # Keep earlier subscribed periods when the user subscribes again.
        for start, end in self.completedRanges:
            self.addRange(eligibleArticles, start, end)
        self.addRange(eligibleArticles, self.activeStart, len(self.topic.articles))

    def addRange(
        self, eligibleArticles: Dict[str, Article], start: int, end: int
    ) -> None:
        # The start is included and the end is excluded.
        for index in range(start, end):
            article = self.topic.articles[index]
            eligibleArticles[article.articleId] = article


@dataclass
class User:
    subscriptions: Dict[str, Subscription] = field(default_factory=dict)
    pendingNotifications: List[Article] = field(default_factory=list)

    def onArticlePublished(self, article: Article) -> None:
        self.pendingNotifications.append(article)


class LiveNewsFeed:
    def __init__(self, existingUserIds: List[str], existingTopicIds: List[str]):
        self.usersById: Dict[str, User] = {
            userId: User() for userId in existingUserIds
        }
        self.topicsById: Dict[str, Topic] = {
            topicId: Topic() for topicId in existingTopicIds
        }
        self.publishedArticleIds: Set[str] = set()

    def subscribeUnsubscribeTopic(
        self, userId: str, topicId: str, subscribe: bool, currentTime: int
    ) -> bool:
        user = self.usersById.get(userId)
        topic = self.topicsById.get(topicId)
        if user is None or topic is None:
            return False

        subscription = user.subscriptions.get(topicId)
        if subscribe:
            if subscription is not None and subscription.activeStart >= 0:
                return False
            if subscription is None:
                subscription = Subscription(topic)
                user.subscriptions[topicId] = subscription

            # List positions preserve call order even when timestamps tie.
            subscription.activeStart = len(topic.articles)
            topic.subscribers.add(userId)
        else:
            if subscription is None or subscription.activeStart < 0:
                return False
            end = len(topic.articles)
            if subscription.activeStart < end:
                subscription.completedRanges.append((subscription.activeStart, end))
            subscription.activeStart = -1
            topic.subscribers.remove(userId)
        return True

    def publishArticle(
        self, articleId: str, title: str, topics: List[str], currentTime: int
    ) -> bool:
        if (
            articleId is None
            or re.fullmatch(r"[a-zA-Z0-9]{1,10}", articleId) is None
            or title is None
            or re.fullmatch(r"[a-zA-Z0-9 ]{0,100}", title) is None
            or topics is None
            or not topics
            or currentTime < 0
            or currentTime > 100000000
            or articleId in self.publishedArticleIds
        ):
            return False

        uniqueTopics: Set[str] = set()
        for topicId in topics:
            if topicId not in self.topicsById:
                return False
            uniqueTopics.add(topicId)

        # Validate everything before storing the article or notifying users.
        article = Article(articleId, title, currentTime)
        self.publishedArticleIds.add(articleId)
        recipients: Set[str] = set()
        for topicId in uniqueTopics:
            topic = self.topicsById[topicId]
            topic.articles.append(article)
            recipients.update(topic.subscribers)

        # A user observing several matching topics still receives one update.
        for userId in recipients:
            self.usersById[userId].onArticlePublished(article)
        return True

    def getPersonalizedFeed(self, userId: str) -> List[str]:
        user = self.usersById.get(userId)
        if user is None:
            return []

        eligibleArticles: Dict[str, Article] = {}
        for subscription in user.subscriptions.values():
            subscription.collectArticles(eligibleArticles)

        feed = sorted(
            eligibleArticles.values(),
            key=lambda article: (-article.publishTime, article.articleId),
        )
        return [article.format() for article in feed]

    def getNotifications(self, userId: str) -> List[str]:
        user = self.usersById.get(userId)
        if user is None:
            return []

        result = [article.format() for article in user.pendingNotifications]
        user.pendingNotifications = []
        return result
```
