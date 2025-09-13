from dataclasses import dataclass, field
from typing import Dict, List, Set
import bisect


@dataclass
class DictionaryApp:
    """
    Design Dictionary App to store words and their meanings.

    Features:
    - storeWord(word, meaning): insert or overwrite meaning for a word.
      * Inputs are lowercase, non-blank.
      * word consists of [a-z], space ' ', and hyphen '-'.
      * word never contains '.'.
    - getMeaning(word): fetch meaning or "" if absent.
    - searchWords(prefix, n): up to n words starting with prefix, lexicographically ascending.
      * If fewer than n matches exist, return all matches.
      * n >= 1 (defensively clamped).
    - exists(pattern): checks if at least one stored word matches pattern with '.' wildcards.
      * '.' matches exactly one LOWERCASE LETTER [a-z].
      * Non-dot characters must match exactly; pattern length must equal word length.
      * Stored words never contain '.', only the query pattern may include dots.

    Implementation notes:
    - dict: Dict[str, str] for word → meaning.
    - words_sorted: List[str] kept sorted for prefix search (bisect).
    - words_by_len: Dict[int, Set[str]] buckets by length to speed up wildcard checks.
    """

    _dict: Dict[str, str] = field(default_factory=dict)              # word -> meaning
    _words_sorted: List[str] = field(default_factory=list)           # all words, sorted
    _words_by_len: Dict[int, Set[str]] = field(default_factory=dict) # length -> set of words

    # Insert or update the mapping (word → meaning).
    # Assumes inputs are lowercase and non-blank; word has [a-z -], no '.'
    def storeWord(self, word: str, meaning: str) -> None:
        is_new = word not in self._dict
        self._dict[word] = meaning

        if is_new:
            # maintain sorted list
            bisect.insort(self._words_sorted, word)
            # add to length bucket
            L = len(word)
            if L not in self._words_by_len:
                self._words_by_len[L] = set()
            self._words_by_len[L].add(word)
        # If overwrite, no structural change (same word & length)

    # Return the meaning for word if present, else ""
    def getMeaning(self, word: str) -> str:
        return self._dict.get(word, "")

    # Return up to n words starting with prefix, sorted lexicographically ascending.
    # If fewer than n matches exist, return all matches. n is clamped to at least 1.
    def searchWords(self, prefix: str, n: int) -> List[str]:
        limit = 1 if n <= 0 else n
        res: List[str] = []
        # find first index where prefix could be inserted
        i = bisect.bisect_left(self._words_sorted, prefix)
        while i < len(self._words_sorted):
            w = self._words_sorted[i]
            if not w.startswith(prefix):
                break
            res.append(w)
            if len(res) == limit:
                break
            i += 1
        return res

    # Return true if at least one stored word matches the pattern.
    # '.' in pattern matches exactly one lowercase letter [a-z].
    # Non-dot characters must match exactly (including spaces and hyphens).
    # Pattern length must equal candidate word length.
    def exists(self, pattern: str) -> bool:
        L = len(pattern)
        candidates = self._words_by_len.get(L)
        if not candidates:
            return False
        for w in candidates:
            if self._matches_pattern(w, pattern):
                return True
        return False

    # Helper: check if 'word' matches 'pattern' under the '.' == [a-z] rule.
    def _matches_pattern(self, word: str, pattern: str) -> bool:
        # '.' matches any single character
        return all(pc == '.' or wc == pc for wc, pc in zip(word, pattern))
