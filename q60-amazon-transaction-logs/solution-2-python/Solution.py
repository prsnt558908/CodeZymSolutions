from typing import List, Dict


class TransactionLogProcessor:
    def __init__(self):
        pass

    def processLogs(self, logs: List[str], threshold: int) -> List[str]:
        """
        Count transactions per user id (sender and recipient each count +1 per log),
        but if sender == recipient, count only +1 total for that user for that log.
        Return user ids (as strings) with count >= threshold, sorted by numeric value.
        """
        counts: Dict[str, int] = {}

        for entry in logs:
            # Be robust to extra spaces / trailing spaces
            parts = entry.split()
            if len(parts) < 2:
                continue  # defensive; per constraints this should not happen
            sender = parts[0]
            recipient = parts[1]

            counts[sender] = counts.get(sender, 0) + 1
            if recipient != sender:
                counts[recipient] = counts.get(recipient, 0) + 1

        # Filter and sort by numeric value, then return as strings
        result = [user_id for user_id, c in counts.items() if c >= threshold]
        result.sort(key=lambda x: int(x))
        return result
