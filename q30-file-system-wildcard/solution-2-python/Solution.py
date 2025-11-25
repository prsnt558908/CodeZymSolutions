from dataclasses import dataclass, field
from typing import Dict, List, Optional


# ---------------------------------------------
# Directory node
# ---------------------------------------------
@dataclass
class _Dir:
    # final String name;
    name: str
    # Dir parent;
    parent: Optional["_Dir"] = None
    # TreeMap<String, Dir> children -> dict in Python; we pick min(child) when needed
    children: Dict[str, "_Dir"] = field(default_factory=dict)


class FileSystemShell:
    """
    In-memory unix filesystem shell supporting:
      - mkdir <path>
      - pwd
      - cd <path>   (with special wildcard segment '*')

    Semantics:
      - Start at root "/".
      - Absolute paths start from "/", relative from current working directory.
      - Multiple consecutive slashes are treated as a single '/'.
      - '.' = current directory; '..' = parent directory (root's parent is itself).
      - mkdir creates the final directory; parents are auto-created (mkdir -p).
      - mkdir is idempotent; creating an existing directory is a no-op.
      - mkdir path never contains '*' (ignored if present).
      - cd fails (CWD unchanged) if any normal segment doesn't resolve.
      - '*' as a path segment matches exactly one segment at that position,
        resolved deterministically:
          1) Prefer a child directory; if multiple, pick lexicographically smallest.
          2) If no child exists, prefer '.' (no-op).
          3) (By spec) fallback to '..' — practically redundant since '.' is always valid.
    """

    def __init__(self):
        # private final Dir root;
        self._root = _Dir(name="")
        # Root's parent is itself to simplify ".." at "/"
        self._root.parent = self._root
        # private Dir cwd;
        self._cwd = self._root

    # /** Returns absolute path of current working directory; root -> "/" */
    def pwd(self) -> str:
        if self._cwd is self._root:
            return "/"
        # Deque<String> stack = new ArrayDeque<>();
        stack: List[str] = []
        cur = self._cwd
        while cur is not self._root:
            stack.append(cur.name)
            cur = cur.parent  # type: ignore[assignment]
        stack.reverse()
        # Join with no trailing slash, e.g. /a/b/c
        return "/" + "/".join(stack)

    # /**
    #  * mkdir -p semantics:
    #  * - Absolute path starts at root, relative starts at cwd.
    #  * - Creates intermediate parents as needed.
    #  * - Interprets "." and ".." normally; rejects "*" (out of scope for mkdir).
    #  * - Idempotent if directory already exists.
    #  */
    def mkdir(self, path: str):
        if path is None:
            raise ValueError("path")
        cur = self._root if self._is_absolute(path) else self._cwd

        for seg in self._tokenize(path):
            if seg == ".":
                # no-op
                continue
            elif seg == "..":
                # go to parent; at root remains root
                cur = self._root if cur is self._root else cur.parent  # type: ignore[assignment]
            elif seg == "*":
                # Per spec: mkdir path never contains '*'
                # Silently ignore to be lenient (alternatively, raise).
                continue
            else:
                # create or descend
                nxt = cur.children.get(seg)
                if nxt is None:
                    nxt = _Dir(name=seg, parent=cur)
                    cur.children[seg] = nxt
                cur = nxt

    # /**
    #  * cd with wildcard '*':
    #  * - Absolute or relative.
    #  * - "." and ".." normalized.
    #  * - "*" as a full segment only; resolves deterministically:
    #  *     1) If children exist, pick lexicographically smallest child.
    #  *     2) Else, prefer "." (stay).
    #  * - On any unresolved normal segment, command fails and CWD remains unchanged.
    #  */
    def cd(self, path: str):
        if path is None:
            raise ValueError("path")
        trial = self._root if self._is_absolute(path) else self._cwd

        for seg in self._tokenize(path):
            if seg == ".":
                # stay
                continue
            elif seg == "..":
                trial = self._root if trial is self._root else trial.parent  # type: ignore[assignment]
            elif seg == "*":
                if trial.children:
                    # lexicographically smallest child
                    pick = min(trial.children.keys())
                    trial = trial.children[pick]
                else:
                    # prefer "." (no-op). (Spec mentions fallback to "..", but "." already applies.)
                    pass
            else:
                nxt = trial.children.get(seg)
                if nxt is None:
                    # Failure: leave CWD unchanged
                    return
                trial = nxt

        # Success: commit
        self._cwd = trial

    # -------------------- helpers --------------------

    @staticmethod
    def _is_absolute(path: str) -> bool:
        return path.startswith("/")

    # /**
    #  * Manual tokenizer for path segments:
    #  * - Treat multiple consecutive slashes as one (skip empties).
    #  * - Preserves every ".." occurrence (critical for deep pops).
    #  * - Trims surrounding whitespace without touching internals.
    #  */
    @staticmethod
    def _tokenize(path: str) -> List[str]:
        out: List[str] = []
        n = len(path)
        if n == 0:
            return out

        # Trim surrounding whitespace (like the Java version's optional trim)
        i = 0
        while i < n and path[i].isspace():
            i += 1
        j = n - 1
        while j >= 0 and path[j].isspace():
            j -= 1
        if i > j:
            return out

        start = i
        k = i
        while k <= j:
            c = path[k]
            if c == "/":
                if k > start:
                    seg = path[start:k]
                    if seg:
                        out.append(seg)
                # skip consecutive slashes
                while k + 1 <= j and path[k + 1] == "/":
                    k += 1
                start = k + 1
            k += 1

        if start <= j:
            seg = path[start : j + 1]
            if seg:
                out.append(seg)

        return out