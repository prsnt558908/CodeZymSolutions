from collections import defaultdict

class Solution:
    def __init__(self):
        self.helper = None
        self.page_visit_counts = defaultdict(int)  
       

    def init(self, total_pages, helper):
        self.helper = helper
        for _ in range(total_pages):
            self.page_visit_counts[_] = 0  # Initialize all counts to 0

    def increment_visit_count(self, page_index):
        self.page_visit_counts[page_index] += 1

    def get_visit_count(self, page_index):
        return self.page_visit_counts[page_index]