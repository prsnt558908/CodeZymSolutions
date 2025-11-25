from __future__ import annotations

from dataclasses import dataclass
from enum import Enum, auto
from typing import Dict, List, Optional, Tuple


class SegType(Enum):
    STATIC = auto()
    PARAM = auto()
    WILDCARD = auto()


@dataclass
class Parsed:
    segs: List[str]
    types: List[SegType]
    staticCount: int
    paramCount: int


@dataclass
class Route:
    pattern: str
    result: str
    segs: List[str]
    types: List[SegType]
    staticCount: int
    paramCount: int
    length: int
    order: int  # insertion order


class Router:
    """
    In-memory middleware router.

    Supports:
      - Static segments (literal)
      - Param segments starting with ":" (match exactly one segment)
      - Wildcard segment "*" (match exactly one segment)

    Precedence for callRoute:
      1) More static segments wins
      2) If tied, more param segments wins
      3) If tied, longer pattern (more segments) wins
      4) If still tied, earlier inserted wins

    searchRoutes:
      - Given a wildcardPattern (may contain static / ":" / "*"),
        returns results for all stored patterns that are "compatible" with it.
      - Compatibility means: same length and no position where both are static
        but different literals.
      - Returned in insertion order of routes.
    """

    def __init__(self):
        # Python dict preserves insertion order since 3.7.
        # Updating an existing key doesn't change its position, matching LinkedHashMap behavior.
        self.routes: Dict[str, Route] = {}
        self.orderCounter: int = 0

    def addRoute(self, pathPattern: str, result: str) -> None:
        if pathPattern in self.routes:
            # update result, keep order
            self.routes[pathPattern].result = result
            return

        parsed = self._parse(pathPattern)
        r = Route(
            pattern=pathPattern,
            result=result,
            segs=parsed.segs,
            types=parsed.types,
            staticCount=parsed.staticCount,
            paramCount=parsed.paramCount,
            length=len(parsed.segs),
            order=self.orderCounter,
        )
        self.orderCounter += 1
        self.routes[pathPattern] = r

    def callRoute(self, path: str) -> str:
        request = self._parse(path)
        best: Optional[Route] = None

        for r in self.routes.values():
            if not self._matchesConcrete(request, r):
                continue
            if best is None or self._isBetter(r, best):
                best = r

        return "NOT_FOUND" if best is None else best.result

    def searchRoutes(self, wildcardPattern: str) -> List[str]:
        query = self._parse(wildcardPattern)
        out: List[str] = []

        for r in self.routes.values():
            if self._isCompatible(query, r):
                out.append(r.result)

        return out

    # ---------------- helpers ----------------

    @staticmethod
    def _parse(pathOrPattern: str) -> Parsed:
        segs: List[str] = []
        types: List[SegType] = []
        staticCount = 0
        paramCount = 0

        if not pathOrPattern:
            return Parsed(segs, types, staticCount, paramCount)

        # split and skip empties from leading/trailing slashes
        for s in pathOrPattern.split("/"):
            if not s:
                continue
            segs.append(s)

            if s == "*":
                types.append(SegType.WILDCARD)
            elif s.startswith(":"):
                types.append(SegType.PARAM)
                paramCount += 1
            else:
                types.append(SegType.STATIC)
                staticCount += 1

        return Parsed(segs, types, staticCount, paramCount)

    @staticmethod
    def _matchesConcrete(request: Parsed, route: Route) -> bool:
        if len(request.segs) != route.length:
            return False

        for i in range(route.length):
            t = route.types[i]
            patSeg = route.segs[i]
            reqSeg = request.segs[i]

            if t == SegType.STATIC:
                if patSeg != reqSeg:
                    return False
            else:
                # PARAM or WILDCARD matches any single non-empty segment.
                if reqSeg == "":
                    return False

        return True

    @staticmethod
    def _isCompatible(query: Parsed, stored: Route) -> bool:
        # Pattern-pattern compatibility for searchRoutes.
        # Compatible iff:
        #  - same length
        #  - at every index, not (both STATIC and different literal)
        if len(query.segs) != stored.length:
            return False

        for i in range(stored.length):
            qt = query.types[i]
            st = stored.types[i]
            if qt == SegType.STATIC and st == SegType.STATIC:
                if query.segs[i] != stored.segs[i]:
                    return False
            # Otherwise at least one is PARAM or WILDCARD => compatible.

        return True

    @staticmethod
    def _isBetter(a: Route, b: Route) -> bool:
        # Precedence comparator for callRoute.
        # Returns True if a is better (more specific) than b.
        if a.staticCount != b.staticCount:
            return a.staticCount > b.staticCount
        if a.paramCount != b.paramCount:
            return a.paramCount > b.paramCount  # param beats wildcard
        if a.length != b.length:
            return a.length > b.length  # longer wins
        return a.order < b.order  # earlier inserted wins
