# Design URL Shortener in Java


#### Problem Statement
[https://codezym.com/question/135-design-url-shortener](https://codezym.com/question/135-design-url-shortener)


A URL shortener is mostly about fast lookups. Every method receives a `shortCode`. So we store each short URL in a `HashMap` with the `shortCode` as the key. The supported domains go in a `HashSet`. This makes every operation O(1).


The rules are simple, so we do not need a heavy design pattern. The State pattern looks tempting for ACTIVE and INACTIVE. But there are only two states and the status changes just once. A small enum does the same job with much less code.


Our final design is plain object oriented code. A `ShortUrl` class holds the data of one short URL. A `UrlStatus` enum stores its status. The `URLShortener` class is the main entry point. It checks the rules and finds the right `ShortUrl`.


## Rules We Must Follow


- A short URL can be created only for a supported domain.
- A `shortCode` must be unique across all domains. If `abc123` exists under `sho.rt`, it cannot be created under `go.io`.
- A deactivated `shortCode` can never be created again.
- `resolveUrl` returns the long URL only when the short URL is active. Otherwise it returns `""`.
- `deactivateShortUrl` returns `true` only for an active short URL. It returns `false` for an unknown or already inactive code.
- `getShortUrlDetails` returns 5 strings in a fixed order. It returns an empty list for an unknown code.


Example of the details list:


```
["SHORT_CODE:abc123", "LONG_URL:https://example.com/products/1", "DOMAIN:sho.rt", "SHORT_URL:sho.rt/abc123", "STATUS:ACTIVE"]
```


## Solution 1: Simple Approach Using Maps


The first idea that comes to mind is to keep each piece of data in its own map or set.


- `supportedDomains` (Set): all allowed domains.
- `codeToLongUrl` (Map): short code to long URL.
- `codeToDomain` (Map): short code to domain.
- `inactiveCodes` (Set): short codes that were deactivated.


Why a `Set` for domains? A `List` would need a full scan for every check. A `Set` answers "is this domain allowed?" in O(1).


### The Key Trick: Never Delete a Code


When a short URL is deactivated, we do not remove it. We only add its code to `inactiveCodes`.


This gives us two things for free:


- The code stays in `codeToLongUrl`. So `createShortUrl` sees it as taken and never reuses it.
- `getShortUrlDetails` can still show the old data with `STATUS:INACTIVE`.


### Java Code


```java
import java.util.*;

public class URLShortener {
    Set<String> supportedDomains;
    Map<String, String> codeToLongUrl;  // shortCode -> long URL
    Map<String, String> codeToDomain;   // shortCode -> domain
    Set<String> inactiveCodes;          // short codes that were deactivated

    public URLShortener(List<String> supportedDomains) {
        this.supportedDomains = new HashSet<>(supportedDomains);
        this.codeToLongUrl = new HashMap<>();
        this.codeToDomain = new HashMap<>();
        this.inactiveCodes = new HashSet<>();
    }

    public String createShortUrl(String shortCode, String longUrl, String domain) {
        // A deactivated code is still in codeToLongUrl, so it can never be reused
        if (!supportedDomains.contains(domain) || codeToLongUrl.containsKey(shortCode)) {
            return "";
        }
        codeToLongUrl.put(shortCode, longUrl);
        codeToDomain.put(shortCode, domain);
        return domain + "/" + shortCode;
    }

    public String resolveUrl(String shortCode) {
        if (!codeToLongUrl.containsKey(shortCode) || inactiveCodes.contains(shortCode)) {
            return "";
        }
        return codeToLongUrl.get(shortCode);
    }

    public boolean deactivateShortUrl(String shortCode) {
        if (!codeToLongUrl.containsKey(shortCode) || inactiveCodes.contains(shortCode)) {
            return false;
        }
        inactiveCodes.add(shortCode);
        return true;
    }

    public List<String> getShortUrlDetails(String shortCode) {
        List<String> details = new ArrayList<>();
        if (!codeToLongUrl.containsKey(shortCode)) {
            return details;
        }
        String domain = codeToDomain.get(shortCode);
        String status = inactiveCodes.contains(shortCode) ? "INACTIVE" : "ACTIVE";

        details.add("SHORT_CODE:" + shortCode);
        details.add("LONG_URL:" + codeToLongUrl.get(shortCode));
        details.add("DOMAIN:" + domain);
        details.add("SHORT_URL:" + domain + "/" + shortCode);
        details.add("STATUS:" + status);
        return details;
    }
}
```


### Problems With This Approach


This code works and every call is O(1). But the design does not grow well.


- The data of one short URL is spread across three structures.
- To add a new field, like a click count or an expiry date, we need one more map. We also need to change almost every method.
- The check `inactiveCodes.contains(shortCode)` is repeated in three methods.
- It is easy to update one map and forget another. That leads to bugs.


The speed is fine. The problem is that the code is hard to read and hard to extend.


## Solution 2: Better Approach With a ShortUrl Class


The fix is simple. Put all the data of one short URL inside one object.


Now we need only one map: `shortCode -> ShortUrl`. Each `ShortUrl` takes care of its own data and status.


### Do We Need a Design Pattern?


Two patterns look useful at first. But neither is a good fit for this problem.


**State Pattern**


The State pattern is used when an object behaves differently in different states. Here we would create an `ActiveState` class and an `InactiveState` class. Each one would decide what `resolve` and `deactivate` should do.


But we have only two states. The status changes only once, from ACTIVE to INACTIVE, and never goes back. The whole difference in behavior is a single `if` check.


So the State pattern would add an interface and two classes for something one enum can do. It is worth it only when there are many states with different rules, like ACTIVE, PAUSED, EXPIRED and DELETED.


**Strategy Pattern**


A real URL shortener must generate short codes. It could use random strings, a counter in base62, or a hash. The Strategy pattern is a common way to switch between such methods.


But in this problem the caller always gives us the `shortCode`. We never generate one. So there is nothing to switch, and a strategy would be unused code.


So the cleanest choice is plain classes with an enum.


### Class Design


**`UrlStatus` (enum)**


Stores `ACTIVE` or `INACTIVE`.


Why not a `String`? A typo like `"ACTVE"` compiles fine and causes a silent bug. With an enum, the compiler catches it. Also, `status.name()` gives the exact text we need in the details list.


A `boolean` would also work. But an enum prints its own name and is easy to extend with new states later.


**`ShortUrl` (class)**


Holds `shortCode`, `longUrl`, `domain` and `status`. It also has a few small helper methods:


- `isActive()`: checks if the status is ACTIVE.
- `getShortUrl()`: builds `domain/shortCode`.
- `deactivate()`: changes ACTIVE to INACTIVE. Returns `false` if it was already inactive.
- `getDetails()`: builds the 5 detail strings in the right order.


Why do we need it? Each short URL now manages itself. The main class does not need to know how the details are built or how the status changes.


**`URLShortener` (main class)**


Holds two simple structures:


- `Set<String> supportedDomains`: O(1) check for a valid domain.
- `Map<String, ShortUrl> urls`: O(1) lookup of a short URL by its code.


This class checks the rules and finds the right `ShortUrl`. Then it lets that object do the actual work.


### How Each Method Works


**createShortUrl**


1. If the domain is not supported, return `""`.
2. If the code is already in the map, return `""`. This blocks both active and deactivated codes, because we never delete from the map.
3. Otherwise create a new `ShortUrl`, save it in the map and return `domain/shortCode`.


**resolveUrl**


Find the `ShortUrl` in the map. Return its long URL only if it exists and is active. Otherwise return `""`.


**deactivateShortUrl**


If the code is not in the map, return `false`. Otherwise call `deactivate()` on the `ShortUrl`. It returns `false` if the URL was already inactive.


**getShortUrlDetails**


If the code is not in the map, return an empty list. Otherwise return `getDetails()`.


### Java Code


```java
import java.util.*;

/**
 * Status of a short URL.
 * An enum is safer than plain strings. A typo like "ACTVE" will not compile.
 */
enum UrlStatus {
    ACTIVE,
    INACTIVE
}

/**
 * Keeps all the data of one short URL in one place.
 */
class ShortUrl {
    String shortCode;
    String longUrl;
    String domain;
    UrlStatus status;

    ShortUrl(String shortCode, String longUrl, String domain) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.domain = domain;
        this.status = UrlStatus.ACTIVE; // every new short URL starts as active
    }

    boolean isActive() {
        return status == UrlStatus.ACTIVE;
    }

    /** Full short URL, e.g. "sho.rt/abc123" */
    String getShortUrl() {
        return domain + "/" + shortCode;
    }

    /**
     * Changes status from ACTIVE to INACTIVE.
     * Returns false if the URL was already inactive.
     */
    boolean deactivate() {
        if (!isActive()) {
            return false;
        }
        status = UrlStatus.INACTIVE;
        return true;
    }

    /** Details in the exact order the problem asks for. */
    List<String> getDetails() {
        List<String> details = new ArrayList<>();
        details.add("SHORT_CODE:" + shortCode);
        details.add("LONG_URL:" + longUrl);
        details.add("DOMAIN:" + domain);
        details.add("SHORT_URL:" + getShortUrl());
        details.add("STATUS:" + status.name());
        return details;
    }
}

public class URLShortener {
    // Set gives a fast O(1) check for "is this domain supported?"
    Set<String> supportedDomains;

    // shortCode -> ShortUrl
    // We never remove a deactivated URL from this map.
    // So a used code stays blocked forever and can never be created again.
    Map<String, ShortUrl> urls;

    public URLShortener(List<String> supportedDomains) {
        this.supportedDomains = new HashSet<>(supportedDomains);
        this.urls = new HashMap<>();
    }

    public String createShortUrl(String shortCode, String longUrl, String domain) {
        // Domain must be supported and the code must never have been used before
        if (!supportedDomains.contains(domain) || urls.containsKey(shortCode)) {
            return "";
        }
        ShortUrl url = new ShortUrl(shortCode, longUrl, domain);
        urls.put(shortCode, url);
        return url.getShortUrl();
    }

    public String resolveUrl(String shortCode) {
        ShortUrl url = urls.get(shortCode);
        if (url == null || !url.isActive()) {
            return "";
        }
        return url.longUrl;
    }

    public boolean deactivateShortUrl(String shortCode) {
        ShortUrl url = urls.get(shortCode);
        if (url == null) {
            return false;
        }
        return url.deactivate();
    }

    public List<String> getShortUrlDetails(String shortCode) {
        ShortUrl url = urls.get(shortCode);
        if (url == null) {
            return new ArrayList<>();
        }
        return url.getDetails();
    }
}
```


### Complexity


- **Time:** O(1) on average for every method. Each call does only a few map and set lookups.
- **Space:** O(n), where n is the number of short URLs created.