from typing import List, Dict, Set

class FileFinder:
    def __init__(self):
        # Stores absolute file path -> size in MB
        self.files: Dict[str, int] = {}

    def addFile(self, path: str, sizeMb: int):
        """
        Add or replace a file entry at the given absolute path.
        Assumptions:
        - path is valid and absolute
        - sizeMb >= 0
        """
        self.files[path] = sizeMb

    def runQuery(self, rules: List[str], ops: List[str]) -> List[str]:
        """
        Evaluate a boolean chain of rule results, left-to-right, using:
        AND, OR, AND NOT
        """
        if not rules:
            return []
        if ops is None:
            ops = []

        rule_results: List[Set[str]] = [self._evaluate_rule(r) for r in rules]

        acc: Set[str] = set(rule_results[0])
        for i in range(1, len(rule_results)):
            op = ops[i - 1].strip().upper()
            rhs = rule_results[i]
            if op == "AND":
                acc.intersection_update(rhs)
            elif op == "OR":
                acc.update(rhs)
            elif op == "AND NOT":
                acc.difference_update(rhs)
            else:
                raise ValueError(f"Unsupported operator: {op}")

        return sorted(acc)

    def _evaluate_rule(self, rule_str: str) -> Set[str]:
        parts = self._split_and_trim(rule_str)
        if len(parts) < 3:
            raise ValueError(f"Invalid rule: {rule_str}")

        rule_id = int(parts[0])
        dir_path = parts[1]
        arg = parts[2]

        if rule_id == 1:
            return self._eval_min_size(dir_path, int(arg))
        elif rule_id == 2:
            return self._eval_extension(dir_path, arg)
        else:
            raise ValueError(f"Unknown ruleId: {rule_id}")

    def _eval_min_size(self, dir_path: str, min_size: int) -> Set[str]:
        return {path for path, size in self.files.items()
                if self._is_under_dir(path, dir_path) and size > min_size}

    def _eval_extension(self, dir_path: str, ext: str) -> Set[str]:
        return {path for path in self.files
                if self._is_under_dir(path, dir_path) and path.endswith(ext)}

    def _is_under_dir(self, file_path: str, dir_path: str) -> bool:
        if dir_path == "/":
            return file_path.startswith("/")
        prefix = dir_path if dir_path.endswith("/") else dir_path + "/"
        return file_path.startswith(prefix)

    def _split_and_trim(self, s: str) -> List[str]:
        parts = s.split(",", 2)
        return [p.strip() for p in parts]
