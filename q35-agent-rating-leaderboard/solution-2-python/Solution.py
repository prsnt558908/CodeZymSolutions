from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from typing import Dict, List


@dataclass
class Stats:
    # Stores total sum of ratings and count of ratings
    sum: int = 0
    count: int = 0

    def add(self, rating: int) -> None:
        self.sum += rating
        self.count += 1

    def get_average(self) -> Decimal:
        if self.count == 0:
            # average = 0.0 when there are no ratings
            return Decimal("0.0")
        # average = sum / count, rounded to 1 decimal using HALF_UP
        avg = Decimal(self.sum) / Decimal(self.count)
        return avg.quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)


class AgentRatingLeaderboard:
    def __init__(self):
        # Overall stats per agent
        self.overallStats: Dict[str, Stats] = {}
        # Monthly stats: month ("YYYY-MM") -> (agentName -> Stats)
        self.monthlyStats: Dict[str, Dict[str, Stats]] = {}

    def rateAgent(self, agentName: str, rating: int, date: str) -> None:
        # Update overall stats
        overall = self.overallStats.get(agentName)
        if overall is None:
            overall = Stats()
            self.overallStats[agentName] = overall
        overall.add(rating)

        # Extract month "YYYY-MM" from "YYYY-MM-DD"
        month = date[:7]

        # Update monthly stats
        monthMap = self.monthlyStats.get(month)
        if monthMap is None:
            monthMap = {}
            self.monthlyStats[month] = monthMap

        monthStats = monthMap.get(agentName)
        if monthStats is None:
            monthStats = Stats()
            monthMap[agentName] = monthStats
        monthStats.add(rating)

    def getAverageRatings(self) -> List[str]:
        # Local helper class for sorting
        @dataclass
        class AgentAverage:
            name: str
            average: Decimal

        list_: List[AgentAverage] = []

        for agentName, stats in self.overallStats.items():
            avg = stats.get_average()
            list_.append(AgentAverage(agentName, avg))

        # Sort by average rating descending, then agent name ascending
        list_.sort(key=lambda a: (-a.average, a.name))

        result: List[str] = []
        for aa in list_:
            result.append(f"{aa.name},{aa.average}")
        return result

    def getBestAgentsByMonth(self, month: str) -> List[str]:
        monthMap = self.monthlyStats.get(month)
        if monthMap is None:
            # No ratings for this month
            return []

        # Local helper class for sorting
        @dataclass
        class AgentAverage:
            name: str
            average: Decimal

        list_: List[AgentAverage] = []

        for agentName, stats in monthMap.items():
            avg = stats.get_average()
            list_.append(AgentAverage(agentName, avg))

        # Sort by average rating descending, then agent name ascending
        list_.sort(key=lambda a: (-a.average, a.name))

        result: List[str] = []
        for aa in list_:
            result.append(f"{aa.name},{aa.average}")
        return result
