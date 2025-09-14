from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Optional


# In-memory Container Orchestrator (Python version of the provided Java reference).
# ------------------------------------------------------------------------------
# - Parse machine metadata rows: "machineId,totalCpuUnits,totalMemoryInMB".
# - assignMachine(criteria, name, imageUrl, cpuUnits, memMb):
#     * criteria == 0 → pick machine with MAX spare CPU; tiebreak lexicographically by machineId.
#     * criteria == 1 → pick machine with MAX spare Memory; tiebreak lexicographically by machineId.
#     * Must have free CPU ≥ cpuUnits and free Mem ≥ memMb.
#     * On success, create RUNNING container, reserve resources, return machineId; else return "".
# - stop(name): RUNNING/PAUSED → STOPPED, frees CPU/Mem; returns True iff it existed, assigned, and not already STOPPED.
# - Single-threaded, in-memory, O(M) selection per placement.
# ------------------------------------------------------------------------------


class State(Enum):
    RUNNING = "RUNNING"
    PAUSED = "PAUSED"
    STOPPED = "STOPPED"


@dataclass
class Machine:
    id: str
    total_cpu: int
    total_mem: int
    used_cpu: int = 0
    used_mem: int = 0
    containers: set = field(default_factory=set)  # container names hosted here

    # Compute free resources
    def free_cpu(self) -> int:
        return self.total_cpu - self.used_cpu

    def free_mem(self) -> int:
        return self.total_mem - self.used_mem

    # Capacity check
    def has_capacity_for(self, cpu: int, mem: int) -> bool:
        return self.free_cpu() >= cpu and self.free_mem() >= mem

    # Reserve resources for a container
    def allocate(self, cpu: int, mem: int, container_name: str) -> None:
        self.used_cpu += cpu
        self.used_mem += mem
        self.containers.add(container_name)

    # Release resources from a container
    def release(self, cpu: int, mem: int, container_name: str) -> None:
        self.used_cpu -= cpu
        self.used_mem -= mem
        if self.used_cpu < 0:  # defensive clamp
            self.used_cpu = 0
        if self.used_mem < 0:  # defensive clamp
            self.used_mem = 0
        self.containers.discard(container_name)


@dataclass
class Container:
    name: str
    image_url: str
    cpu: int
    mem: int
    machine_id: Optional[str]
    state: State = State.RUNNING  # starts RUNNING on successful placement


class ContainerManager:
    # Constructor: takes list of "machineId,totalCpuUnits,totalMemoryInMB"
    def __init__(self, machines: List[str]):
        self._machines_by_id: Dict[str, Machine] = {}
        self._containers_by_name: Dict[str, Container] = {}
        self.machines: List[str] = machines[:]  # keep original rows if needed by external tests

        if machines is None:
            raise ValueError("machines list cannot be None")

        for row in machines:
            if row is None or str(row).strip() == "":
                raise ValueError(f"Invalid machine row: {row}")
            parts = str(row).split(",", -1)
            if len(parts) != 3:
                raise ValueError(f"Expected 3 fields: {row}")
            mid = parts[0].strip()
            if mid == "":
                raise ValueError(f"machineId cannot be blank: {row}")
            try:
                cpu = int(parts[1].strip())
                mem = int(parts[2].strip())
            except Exception as e:
                # Use a generic ValueError per spec; test inputs are expected valid.
                raise ValueError(f"Non-integer capacity in row: {row}") from e
            if cpu <= 0 or mem <= 0:
                raise ValueError(f"Capacities must be > 0: {row}")
            if mid in self._machines_by_id:
                raise ValueError(f"Duplicate machineId: {mid}")

            self._machines_by_id[mid] = Machine(id=mid, total_cpu=cpu, total_mem=mem)

    # Assigns a machine for a new container using the given criteria.
    # Returns machineId or "" if placement fails.
    def assignMachine(self, criteria: int, containerName: str, imageUrl: str, cpuUnits: int, memMb: int) -> str:
        # Basic validations per problem statement
        if containerName is None or str(containerName).strip() == "":
            return ""
        if cpuUnits <= 0 or memMb <= 0:
            return ""
        if containerName in self._containers_by_name:  # enforce global uniqueness
            return ""
        if criteria not in (0, 1):
            return ""

        chosen_id = ""
        best_metric: Optional[int] = None

        # Iterate all machines and choose based on metric with tie-breaker
        for m in self._machines_by_id.values():
            if not m.has_capacity_for(cpuUnits, memMb):
                continue

            metric = m.free_cpu() if criteria == 0 else m.free_mem()

            if best_metric is None or metric > best_metric:
                best_metric = metric
                chosen_id = m.id
            elif metric == best_metric:
                # lexicographically smallest machineId
                if m.id < chosen_id:
                    chosen_id = m.id

        if chosen_id == "":
            return ""

        # Allocate on the chosen machine and register container
        host = self._machines_by_id[chosen_id]
        host.allocate(cpuUnits, memMb, containerName)
        cont = Container(
            name=containerName,
            image_url=imageUrl,
            cpu=cpuUnits,
            mem=memMb,
            machine_id=chosen_id,
            state=State.RUNNING,
        )
        self._containers_by_name[containerName] = cont
        return chosen_id

    # Stops a container: RUNNING/PAUSED → STOPPED; frees resources; returns True if state changed.
    def stop(self, name: str) -> bool:
        if name is None or str(name).strip() == "":
            return False
        c = self._containers_by_name.get(name)
        if c is None:
            return False
        if c.state == State.STOPPED:
            return False
        if not c.machine_id:
            return False  # not assigned (defensive)

        host = self._machines_by_id.get(c.machine_id)
        if host is None:
            return False  # defensive; should not happen

        host.release(c.cpu, c.mem, c.name)
        c.state = State.STOPPED
        # Keep machine_id for history; can be set to None if desired:
        # c.machine_id = None
        return True

    # -------- Optional helpers for testing/inspection (not part of the required API) --------

    # Returns sorted dict of machineId -> (freeCpu, freeMem)
    def getMachineFreeResources(self) -> Dict[str, List[int]]:
        out: Dict[str, List[int]] = {}
        for mid in sorted(self._machines_by_id.keys()):
            m = self._machines_by_id[mid]
            out[mid] = [m.free_cpu(), m.free_mem()]
        return out

    # Returns a string summary for a container
    def getContainerInfo(self, name: str) -> str:
        c = self._containers_by_name.get(name)
        if c is None:
            return "NOT_FOUND"
        return f"{c.name}:{c.state.value}@{c.machine_id if c.machine_id else '-'}"
