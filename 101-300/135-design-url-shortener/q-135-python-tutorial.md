# Design URL Shortener in Python


#### Problem Statement
[https://codezym.com/question/135-design-url-shortener](https://codezym.com/question/135-design-url-shortener)


A URL shortener is mostly about fast lookups. Every method receives a `shortCode`. So we store each short URL in a `dict` with the `shortCode` as the key. The supported domains go in a `set`. This makes every operation O(1).


The rules are simple, so we do not need a heavy design pattern. The State pattern looks tempting for ACTIVE and INACTIVE. But there are only two states and the status changes just once. A small enum does the same job with much less code.


Our final design is plain object oriented code. A `ShortUrl` class holds the data of one short URL. A `UrlStatus` enum stores its status. The `URLShortener` class is the main entry point. It checks the rules and finds the right `ShortUrl`.


## Rules We Must Follow


- A short URL can be created only for a supported domain.
- A `shortCode` must be unique across all domains. If `abc123` exists under `sho.rt`, it cannot be created under `go.io`.
- A deactivated `shortCode` can never be created again.
- `resolveUrl` returns the long URL only when the short URL is active. Otherwise it returns `""`.
- `deactivateShortUrl` returns `True` only for an active short URL. It returns `False` for an unknown or already inactive code.
- `getShortUrlDetails` returns 5 strings in a fixed order. It returns an empty list for an unknown code.


Example of the details list:


```
["SHORT_CODE:abc123", "LONG_URL:https://example.com/products/1", "DOMAIN:sho.rt", "SHORT_URL:sho.rt/abc123", "STATUS:ACTIVE"]
```


## Solution 1: Simple Approach Using Dicts


The first idea that comes to mind is to keep each piece of data in its own dict or set.


- `supportedDomains` (set): all allowed domains.
- `codeToLongUrl` (dict): short code to long URL.
- `codeToDomain` (dict): short code to domain.
- `inactiveCodes` (set): short codes that were deactivated.


Why a `set` for domains? A `list` would need a full scan for every check. A `set` answers "is this domain allowed?" in O(1).


### The Key Trick: Never Delete a Code


When a short URL is deactivated, we do not remove it. We only add its code to `inactiveCodes`.


This gives us two things for free:


- The code stays in `codeToLongUrl`. So `createShortUrl` sees it as taken and never reuses it.
- `getShortUrlDetails` can still show the old data with `STATUS:INACTIVE`.


### Python Code


```python
class URLShortener:
    def __init__(self, supportedDomains):
        self.supportedDomains = set(supportedDomains)
        self.codeToLongUrl = {}     # shortCode -> long URL
        self.codeToDomain = {}      # shortCode -> domain
        self.inactiveCodes = set()  # short codes that were deactivated

    def createShortUrl(self, shortCode, longUrl, domain):
        # A deactivated code is still in codeToLongUrl, so it can never be reused
        if domain not in self.supportedDomains or shortCode in self.codeToLongUrl:
            return ""
        self.codeToLongUrl[shortCode] = longUrl
        self.codeToDomain[shortCode] = domain
        return f"{domain}/{shortCode}"

    def resolveUrl(self, shortCode):
        if shortCode not in self.codeToLongUrl or shortCode in self.inactiveCodes:
            return ""
        return self.codeToLongUrl[shortCode]

    def deactivateShortUrl(self, shortCode):
        if shortCode not in self.codeToLongUrl or shortCode in self.inactiveCodes:
            return False
        self.inactiveCodes.add(shortCode)
        return True

    def getShortUrlDetails(self, shortCode):
        if shortCode not in self.codeToLongUrl:
            return []
        domain = self.codeToDomain[shortCode]
        status = "INACTIVE" if shortCode in self.inactiveCodes else "ACTIVE"
        return [
            f"SHORT_CODE:{shortCode}",
            f"LONG_URL:{self.codeToLongUrl[shortCode]}",
            f"DOMAIN:{domain}",
            f"SHORT_URL:{domain}/{shortCode}",
            f"STATUS:{status}",
        ]
```


### Problems With This Approach


This code works and every call is O(1). But the design does not grow well.


- The data of one short URL is spread across three structures.
- To add a new field, like a click count or an expiry date, we need one more dict. We also need to change almost every method.
- The check `shortCode in self.inactiveCodes` is repeated in three methods.
- It is easy to update one dict and forget another. That leads to bugs.


The speed is fine. The problem is that the code is hard to read and hard to extend.


## Solution 2: Better Approach With a ShortUrl Class


The fix is simple. Put all the data of one short URL inside one object.


Now we need only one dict: `shortCode -> ShortUrl`. Each `ShortUrl` takes care of its own data and status.


### Do We Need a Design Pattern?


Two patterns look useful at first. But neither is a good fit for this problem.


**State Pattern**


The State pattern is used when an object behaves differently in different states. Here we would create an `ActiveState` class and an `InactiveState` class. Each one would decide what `resolve` and `deactivate` should do.


But we have only two states. The status changes only once, from ACTIVE to INACTIVE, and never goes back. The whole difference in behavior is a single `if` check.


So the State pattern would add a base class and two subclasses for something one enum can do. It is worth it only when there are many states with different rules, like ACTIVE, PAUSED, EXPIRED and DELETED.


**Strategy Pattern**


A real URL shortener must generate short codes. It could use random strings, a counter in base62, or a hash. The Strategy pattern is a common way to switch between such methods.


But in this problem the caller always gives us the `shortCode`. We never generate one. So there is nothing to switch, and a strategy would be unused code.


So the cleanest choice is plain classes with an enum.


### Class Design


**`UrlStatus` (Enum)**


Stores `ACTIVE` or `INACTIVE`.


Why not plain strings? A typo like `"ACTVE"` in a string goes unnoticed and causes a silent bug. With an `Enum`, a typo like `UrlStatus.ACTVE` raises an error right away. Also, `status.value` gives the exact text we need in the details list.


A `bool` would also work. But an enum carries its own text and is easy to extend with new states later.


**`ShortUrl` (class)**


Holds `shortCode`, `longUrl`, `domain` and `status`. It also has a few small helper methods:


- `isActive()`: checks if the status is ACTIVE.
- `getShortUrl()`: builds `domain/shortCode`.
- `deactivate()`: changes ACTIVE to INACTIVE. Returns `False` if it was already inactive.
- `getDetails()`: builds the 5 detail strings in the right order.


Why do we need it? Each short URL now manages itself. The main class does not need to know how the details are built or how the status changes.


**`URLShortener` (main class)**


Holds two simple structures:


- `supportedDomains` (set): O(1) check for a valid domain.
- `urls` (dict): O(1) lookup of a short URL by its code.


This class checks the rules and finds the right `ShortUrl`. Then it lets that object do the actual work.


### How Each Method Works


**createShortUrl**


1. If the domain is not supported, return `""`.
2. If the code is already in the dict, return `""`. This blocks both active and deactivated codes, because we never delete from the dict.
3. Otherwise create a new `ShortUrl`, save it in the dict and return `domain/shortCode`.


**resolveUrl**


Find the `ShortUrl` in the dict. Return its long URL only if it exists and is active. Otherwise return `""`.


**deactivateShortUrl**


If the code is not in the dict, return `False`. Otherwise call `deactivate()` on the `ShortUrl`. It returns `False` if the URL was already inactive.


**getShortUrlDetails**


If the code is not in the dict, return `[]`. Otherwise return `getDetails()`.


### Python Code


```python
from enum import Enum


class UrlStatus(Enum):
    """Status of a short URL. Safer than plain strings, a typo fails right away."""
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"


class ShortUrl:
    """Keeps all the data of one short URL in one place."""

    def __init__(self, shortCode, longUrl, domain):
        self.shortCode = shortCode
        self.longUrl = longUrl
        self.domain = domain
        self.status = UrlStatus.ACTIVE  # every new short URL starts as active

    def isActive(self):
        return self.status == UrlStatus.ACTIVE

    def getShortUrl(self):
        """Full short URL, e.g. "sho.rt/abc123"."""
        return f"{self.domain}/{self.shortCode}"

    def deactivate(self):
        """Changes status from ACTIVE to INACTIVE.
        Returns False if the URL was already inactive.
        """
        if not self.isActive():
            return False
        self.status = UrlStatus.INACTIVE
        return True

    def getDetails(self):
        """Details in the exact order the problem asks for."""
        return [
            f"SHORT_CODE:{self.shortCode}",
            f"LONG_URL:{self.longUrl}",
            f"DOMAIN:{self.domain}",
            f"SHORT_URL:{self.getShortUrl()}",
            f"STATUS:{self.status.value}",
        ]


class URLShortener:
    def __init__(self, supportedDomains):
        # set gives a fast O(1) check for "is this domain supported?"
        self.supportedDomains = set(supportedDomains)

        # shortCode -> ShortUrl
        # We never remove a deactivated URL from this dict.
        # So a used code stays blocked forever and can never be created again.
        self.urls = {}

    def createShortUrl(self, shortCode, longUrl, domain):
        # Domain must be supported and the code must never have been used before
        if domain not in self.supportedDomains or shortCode in self.urls:
            return ""
        url = ShortUrl(shortCode, longUrl, domain)
        self.urls[shortCode] = url
        return url.getShortUrl()

    def resolveUrl(self, shortCode):
        url = self.urls.get(shortCode)
        if url is None or not url.isActive():
            return ""
        return url.longUrl

    def deactivateShortUrl(self, shortCode):
        url = self.urls.get(shortCode)
        if url is None:
            return False
        return url.deactivate()

    def getShortUrlDetails(self, shortCode):
        url = self.urls.get(shortCode)
        if url is None:
            return []
        return url.getDetails()
```


### Complexity


- **Time:** O(1) on average for every method. Each call does only a few dict and set lookups.
- **Space:** O(n), where n is the number of short URLs created.