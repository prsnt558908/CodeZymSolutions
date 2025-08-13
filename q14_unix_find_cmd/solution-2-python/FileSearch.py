class FileSearch:
    def __init__(self):
        # Dictionary to store files: { path: sizeMb }
        self.files = {}

    def putFile(self, path, sizeMb):
        """
        Add or update a file entry with given absolute path and size in MB.
        """
        self.files[path] = sizeMb

    def search(self, ruleId, dirPath, args):
        """
        Search based on ruleId:
        Rule 1: args = "minSizeMb" (strictly greater than)
        Rule 2: args = ".ext" (case-sensitive exact match on extension)

        Returns sorted list of matching file paths.
        """
        result = []

        # Ensure dirPath is a directory prefix, not just substring
        def is_under_directory(file_path, directory):
            if directory == "/":  # Root matches all
                return True
            return file_path.startswith(directory.rstrip("/") + "/")

        if ruleId == 1:
            # Rule 1: strictly greater than size
            try:
                min_size = int(args)
            except ValueError:
                return []  # Invalid args
            for path, size in self.files.items():
                if is_under_directory(path, dirPath) and size > min_size:
                    result.append(path)

        elif ruleId == 2:
            # Rule 2: case-sensitive extension match
            ext = args
            for path in self.files:
                if is_under_directory(path, dirPath) and path.endswith(ext):
                    result.append(path)

        # Sort lexicographically
        result.sort()
        return result
