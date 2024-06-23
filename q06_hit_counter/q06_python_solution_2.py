from collections import defaultdict
from threading import Lock

class Solution:
    def __init__(self):
        self.helper = None
        self.page_visit_counts = defaultdict(int)  # Use defaultdict(int) for atomic-like behavior
        self.lock = Lock()  # Create a lock for thread safety

    def init(self, total_pages, helper):
        self.helper = helper
        for _ in range(total_pages):
            self.page_visit_counts[_] = 0  # Initialize all counts to 0

    def increment_visit_count(self, page_index):
        self.page_visit_counts[page_index] += 1
        #with self.lock:  # Acquire lock before updating count
           #self.page_visit_counts[page_index] += 1

    def get_visit_count(self, page_index):
        #with self.lock:  # Acquire lock before reading count
            return self.page_visit_counts[page_index]