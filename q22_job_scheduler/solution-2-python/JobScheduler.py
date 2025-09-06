from dataclasses import dataclass, field
from typing import Dict, List, Set, Callable, Tuple


@dataclass
class Machine:
    machine_id: str
    caps: Set[str] = field(default_factory=set)
    unfinished: int = 0
    finished: int = 0


@dataclass
class JobRecord:
    job_id: str
    machine_id: str
    completed: bool = False


class JobScheduler:
    """
    In-memory scheduler that:
      - Stores machines with capabilities (case-insensitive).
      - Assigns jobs to machines that are supersets of required capabilities.
      - Supports multiple selection criteria with deterministic, lexicographic tie-breaks.
    """

    def __init__(self):
        # machine_id -> Machine
        self._machines: Dict[str, Machine] = {}
        # job_id -> JobRecord
        self._jobs: Dict[str, JobRecord] = {}

        # Criteria registry: maps 'criteria' int -> a key function for min()
        # Each key function returns a tuple; lower tuple wins.
        # Tie-breaker is always machine_id lexicographically (added at the end of each key).
        self._criteria: Dict[int, Callable[[Machine], Tuple]] = {
            0: lambda m: (m.unfinished, m.machine_id),           # Least unfinished
            1: lambda m: (-m.finished, m.machine_id),            # Most finished (negate for min())
        }

    # --- Public API ---

    def addMachine(self, machineId: str, capabilities: List[str]) -> None:
        """
        Add a machine with a unique ID and a list of capabilities (case-insensitive).
        """
        normalized_caps = {self._norm(c) for c in capabilities}
        self._machines[machineId] = Machine(machine_id=machineId, caps=normalized_caps)

    def assignMachineToJob(self, jobId: str, capabilitiesRequired: List[str], criteria: int) -> str:
        """
        Assign a job to a compatible machine per the given criteria.
        Returns machineId or "" if no compatible machine exists.
        Side effects: increments the selected machine's unfinished count and records the job assignment.
        """
        required = {self._norm(c) for c in capabilitiesRequired}

        # Find compatible machines (superset of required caps)
        candidates = [m for m in self._machines.values() if required.issubset(m.caps)]
        if not candidates:
            return ""

        # Choose key function; default to criterion 0 if unknown
        key_fn = self._criteria.get(criteria, self._criteria[0])

        # Deterministic selection with lexicographic tie-break built into key
        chosen = min(candidates, key=key_fn)

        # Update state and record assignment
        chosen.unfinished += 1
        self._jobs[jobId] = JobRecord(job_id=jobId, machine_id=chosen.machine_id, completed=False)
        return chosen.machine_id

    def jobCompleted(self, jobId: str) -> None:
        """
        Mark a previously assigned job as finished and update counters.
        Assumes jobId is always valid (per problem statement).
        """
        rec = self._jobs.get(jobId)
        if not rec or rec.completed:
            return  # According to spec jobId is valid; this guard keeps idempotency.

        rec.completed = True
        m = self._machines[rec.machine_id]
        # Decrement unfinished, increment finished
        if m.unfinished > 0:
            m.unfinished -= 1
        m.finished += 1

    # --- Extensibility helpers ---

    def register_criterion(self, code: int, key_fn: Callable[[Machine], Tuple]) -> None:
        """
        Register a new assignment criterion. The key_fn should return a tuple suitable for min().
        Always include machine_id as the last element (or rely on this wrapper) to ensure deterministic tie-breaks.
        Example:
            scheduler.register_criterion(2, lambda m: (-m.custom_score, m.machine_id))
        """
        self._criteria[code] = key_fn

    # --- Internal utils ---

    @staticmethod
    def _norm(capability: str) -> str:
        """Normalize capability token for case-insensitive comparisons."""
        return capability.strip().lower()
