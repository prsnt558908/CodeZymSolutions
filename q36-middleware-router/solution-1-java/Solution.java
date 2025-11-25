import java.util.*;

/**
 * In-memory middleware router.
 *
 * Supports:
 *  - Static segments (literal)
 *  - Param segments starting with ":" (match exactly one segment)
 *  - Wildcard segment "*" (match exactly one segment)
 *
 * Precedence for callRoute:
 *  1) More static segments wins
 *  2) If tied, more param segments wins
 *  3) If tied, longer pattern (more segments) wins
 *  4) If still tied, earlier inserted wins
 *
 * searchRoutes:
 *  - Given a wildcardPattern (may contain static / ":" / "*"),
 *    returns results for all stored patterns that are "compatible" with it.
 *  - Compatibility means: same length and no position where both are static
 *    but different literals.
 *  - Returned in insertion order of routes.
 */
public class Router {

    private enum SegType { STATIC, PARAM, WILDCARD }

    private static class Route {
        final String pattern;
        String result;
        final List<String> segs;
        final List<SegType> types;
        final int staticCount;
        final int paramCount;
        final int length;
        final int order; // insertion order

        Route(String pattern, String result, int order) {
            this.pattern = pattern;
            this.result = result;
            this.order = order;

            Parsed p = parse(pattern);
            this.segs = p.segs;
            this.types = p.types;
            this.staticCount = p.staticCount;
            this.paramCount = p.paramCount;
            this.length = p.segs.size();
        }
    }

    private static class Parsed {
        List<String> segs = new ArrayList<>();
        List<SegType> types = new ArrayList<>();
        int staticCount = 0;
        int paramCount = 0;
    }

    // LinkedHashMap preserves insertion order; updating an existing key doesn't change order.
    private final LinkedHashMap<String, Route> routes = new LinkedHashMap<>();
    private int orderCounter = 0;

    public Router() { }

    public void addRoute(String pathPattern, String result) {
        if (routes.containsKey(pathPattern)) {
            routes.get(pathPattern).result = result; // update result, keep order
            return;
        }
        Route r = new Route(pathPattern, result, orderCounter++);
        routes.put(pathPattern, r);
    }

    public String callRoute(String path) {
        Parsed request = parse(path);
        Route best = null;

        for (Route r : routes.values()) {
            if (!matchesConcrete(request, r)) continue;
            if (best == null || isBetter(r, best)) {
                best = r;
            }
        }
        return best == null ? "NOT_FOUND" : best.result;
    }

    public List<String> searchRoutes(String wildcardPattern) {
        Parsed query = parse(wildcardPattern);
        List<String> out = new ArrayList<>();

        for (Route r : routes.values()) {
            if (isCompatible(query, r)) {
                out.add(r.result);
            }
        }
        return out;
    }

    // ---------------- helpers ----------------

    private static Parsed parse(String pathOrPattern) {
        Parsed p = new Parsed();
        if (pathOrPattern == null || pathOrPattern.isEmpty()) return p;

        String[] raw = pathOrPattern.split("/");
        for (String s : raw) {
            if (s == null || s.isEmpty()) continue; // skip empties from leading/trailing slashes
            p.segs.add(s);

            if (s.equals("*")) {
                p.types.add(SegType.WILDCARD);
            } else if (s.startsWith(":")) {
                p.types.add(SegType.PARAM);
                p.paramCount++;
            } else {
                p.types.add(SegType.STATIC);
                p.staticCount++;
            }
        }
        return p;
    }

    private static boolean matchesConcrete(Parsed request, Route route) {
        if (request.segs.size() != route.length) return false;

        for (int i = 0; i < route.length; i++) {
            SegType t = route.types.get(i);
            String patSeg = route.segs.get(i);
            String reqSeg = request.segs.get(i);

            if (t == SegType.STATIC) {
                if (!patSeg.equals(reqSeg)) return false;
            } else {
                // PARAM or WILDCARD matches any single non-empty segment.
                if (reqSeg.isEmpty()) return false;
            }
        }
        return true;
    }

    /**
     * Pattern-pattern compatibility for searchRoutes.
     * Compatible iff:
     *  - same length
     *  - at every index, not (both STATIC and different literal)
     */
    private static boolean isCompatible(Parsed query, Route stored) {
        if (query.segs.size() != stored.length) return false;

        for (int i = 0; i < stored.length; i++) {
            SegType qt = query.types.get(i);
            SegType st = stored.types.get(i);

            if (qt == SegType.STATIC && st == SegType.STATIC) {
                if (!query.segs.get(i).equals(stored.segs.get(i))) {
                    return false;
                }
            }
            // Otherwise at least one is PARAM or WILDCARD => compatible.
        }
        return true;
    }

    /**
     * Precedence comparator for callRoute.
     * Returns true if a is better (more specific) than b.
     */
    private static boolean isBetter(Route a, Route b) {
        if (a.staticCount != b.staticCount) {
            return a.staticCount > b.staticCount;
        }
        if (a.paramCount != b.paramCount) {
            return a.paramCount > b.paramCount; // param beats wildcard
        }
        if (a.length != b.length) {
            return a.length > b.length; // longer wins
        }
        return a.order < b.order; // earlier inserted wins
    }
}
