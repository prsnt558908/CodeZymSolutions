import java.util.*;

/**
 * In-memory Container Orchestrator.
 *
 * Features implemented per spec:
 * - Parse machine metadata: "machineId,totalCpuUnits,totalMemoryInMB".
 * - assignMachine(criteria, name, imageUrl, cpuUnits, memMb):
 *     * criteria==0 → pick machine with MAX spare CPU; tiebreak lexicographically by machineId.
 *     * criteria==1 → pick machine with MAX spare Memory; tiebreak lexicographically by machineId.
 *     * Must have free CPU ≥ cpuUnits and free Mem ≥ memMb.
 *     * Creates a RUNNING container on the chosen machine and reserves resources.
 *     * Returns machineId or "" if placement not possible.
 * - stop(name): RUNNING → STOPPED, frees CPU/Mem; true iff it existed, was assigned, and not already STOPPED.
 *
 * Notes:
 * - Containers start in RUNNING state on successful assignment.
 * - PAUSED state is defined for completeness (reserves resources like RUNNING),
 *   but no pause/resume APIs are required by this spec.
 * - All logic is single-threaded, in-memory, O(M) selection per placement.
 */
public class ContainerManager {

    private enum State { RUNNING, PAUSED, STOPPED }

    private static final class Machine {
        final String id;
        final int totalCpu;
        final int totalMem;
        int usedCpu;
        int usedMem;
        // Optional: track container names assigned here (not required by spec)
        final Set<String> containers = new HashSet<>();

        Machine(String id, int totalCpu, int totalMem) {
            this.id = id;
            this.totalCpu = totalCpu;
            this.totalMem = totalMem;
            this.usedCpu = 0;
            this.usedMem = 0;
        }

        int freeCpu() { return totalCpu - usedCpu; }
        int freeMem() { return totalMem - usedMem; }

        boolean hasCapacityFor(int cpu, int mem) {
            return freeCpu() >= cpu && freeMem() >= mem;
        }

        void allocate(int cpu, int mem, String containerName) {
            usedCpu += cpu;
            usedMem += mem;
            containers.add(containerName);
        }

        void release(int cpu, int mem, String containerName) {
            usedCpu -= cpu;
            usedMem -= mem;
            if (usedCpu < 0) usedCpu = 0;
            if (usedMem < 0) usedMem = 0;
            containers.remove(containerName);
        }
    }

    private static final class Container {
        final String name;
        final String imageUrl;
        final int cpu;
        final int mem;
        String machineId; // non-null only while RUNNING/PAUSED in this implementation
        State state;

        Container(String name, String imageUrl, int cpu, int mem, String machineId) {
            this.name = name;
            this.imageUrl = imageUrl;
            this.cpu = cpu;
            this.mem = mem;
            this.machineId = machineId;
            this.state = State.RUNNING;
        }
    }

    private final Map<String, Machine> machinesById = new HashMap<>();
    private final Map<String, Container> containersByName = new HashMap<>();

    /**
     * Constructor: parses machine rows of the form "machineId,totalCpuUnits,totalMemoryInMB".
     */
    public ContainerManager(List<String> machines) {
        if (machines == null) throw new IllegalArgumentException("machines list cannot be null");
        for (String row : machines) {
            if (row == null || row.isBlank()) {
                throw new IllegalArgumentException("Invalid machine row: " + row);
            }
            String[] parts = row.split(",", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Expected 3 fields: " + row);
            }
            String id = parts[0].trim();
            String cpuStr = parts[1].trim();
            String memStr = parts[2].trim();
            if (id.isEmpty()) throw new IllegalArgumentException("machineId cannot be blank: " + row);
            int cpu, mem;
            try {
                cpu = Integer.parseInt(cpuStr);
                mem = Integer.parseInt(memStr);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Non-integer capacity in row: " + row);
            }
            if (cpu <= 0 || mem <= 0) {
                throw new IllegalArgumentException("Capacities must be > 0: " + row);
            }
            if (machinesById.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate machineId: " + id);
            }
            machinesById.put(id, new Machine(id, cpu, mem));
        }
    }

    /**
     * Assigns a machine to host a new container, using the selection criteria.
     * @param criteria 0 = max spare CPU; 1 = max spare Memory
     * @return machineId or "" if placement fails
     */
    public String assignMachine(int criteria, String containerName, String imageUrl, int cpuUnits, int memMb) {
        // Basic validation (per problem statement)
        if (containerName == null || containerName.isBlank()) return "";
        if (cpuUnits <= 0 || memMb <= 0) return "";
        if (containersByName.containsKey(containerName)) return ""; // enforce uniqueness defensively
        if (criteria != 0 && criteria != 1) return "";

        // Choose the best machine among those with sufficient capacity
        String chosenId = "";
        int bestMetric = Integer.MIN_VALUE;

        for (Machine m : machinesById.values()) {
            if (!m.hasCapacityFor(cpuUnits, memMb)) continue;

            int metric = (criteria == 0) ? m.freeCpu() : m.freeMem();
            if (metric > bestMetric) {
                bestMetric = metric;
                chosenId = m.id;
            } else if (metric == bestMetric && metric != Integer.MIN_VALUE) {
                // Tie-break: lexicographically smallest machineId
                if (!chosenId.isEmpty() && m.id.compareTo(chosenId) < 0) {
                    chosenId = m.id;
                } else if (chosenId.isEmpty()) {
                    chosenId = m.id;
                }
            }
        }

        if (chosenId.isEmpty()) {
            return "";
        }

        // Place the container
        Machine target = machinesById.get(chosenId);
        target.allocate(cpuUnits, memMb, containerName);
        Container c = new Container(containerName, imageUrl, cpuUnits, memMb, chosenId);
        containersByName.put(containerName, c);
        return chosenId;
    }

    /**
     * Stops a container: RUNNING/PAUSED → STOPPED, frees resources; returns true if state changed.
     */
    public boolean stop(String name) {
        if (name == null || name.isBlank()) return false;
        Container c = containersByName.get(name);
        if (c == null) return false;
        if (c.state == State.STOPPED) return false;
        if (c.machineId == null || c.machineId.isBlank()) return false; // not assigned (defensive)

        Machine host = machinesById.get(c.machineId);
        if (host == null) return false; // defensive, should not happen

        // Free resources and mark STOPPED
        host.release(c.cpu, c.mem, c.name);
        c.state = State.STOPPED;
        // Keep machineId for history; could be nulled if preferred:
        // c.machineId = null;
        return true;
    }

    // ---- Optional helpers for testing/inspection (not required by spec) ----

    /** Returns current free CPU and Mem for all machines, e.g. for debugging. */
    public Map<String, int[]> getMachineFreeResources() {
        Map<String, int[]> out = new TreeMap<>();
        for (Machine m : machinesById.values()) {
            out.put(m.id, new int[]{ m.freeCpu(), m.freeMem() });
        }
        return out;
    }

    /** Returns a container's state and host, if present. */
    public String getContainerInfo(String name) {
        Container c = containersByName.get(name);
        if (c == null) return "NOT_FOUND";
        return c.name + ":" + c.state + "@" + (c.machineId == null ? "-" : c.machineId);
    }
}
