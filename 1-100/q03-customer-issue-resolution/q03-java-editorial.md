# Design Customer Issue Resolution System in Java

Problem Statement: https://codezym.com/question/3-design-customer-issue-resolution-system

## Video Explanation

[![Design Customer Issue Resolution System Java Solution](https://img.youtube.com/vi/03TaNq9_dXs/hqdefault.jpg)](https://www.youtube.com/watch?v=03TaNq9_dXs)

YouTube Video : https://www.youtube.com/watch?v=03TaNq9_dXs

## Explanation

We need to design a customer issue resolution system where customers can create issues, agents can be added with specific expertise, issues can be assigned to suitable agents, and agent resolution history can be fetched.

The solution is divided into small classes so that each class has a clear responsibility.

## Important Classes and Data Structures

### `Solution`

`Solution` is the main entry point required by the problem statement. It connects all other managers together and exposes methods like `createIssue`, `addAgent`, `assignIssue`, `resolveIssue`, and `getAgentHistory`.

### `IssueManager`

`IssueManager` stores all issues using a `ConcurrentHashMap`.

We need this class because issue creation, assignment, and resolution should be handled in one place. It also works as a subject in the Observer pattern. Whenever an issue is assigned or resolved, it notifies all registered observers.

### `AgentsManager`

`AgentsManager` stores all agents and also keeps a mapping from issue type to agents who can handle that issue type.

It uses:

- `ConcurrentHashMap<String, Agent>` to store agents by agent id.
- `HashMap<Integer, ConcurrentLinkedDeque<String>>` to store issue type vs agent ids.

This makes it easy to quickly find agents who have expertise in a given issue type.

### `AgentAssigner`

`AgentAssigner` chooses the correct assignment strategy based on `assignStrategy`.

The supported strategies are:

- `0` → assign agent with least open issues overall
- `1` → assign agent with most resolved issues for that issue type
- `2` → assign agent with least open issues for that issue type

This keeps the assignment logic flexible and easy to extend.

### Selector Classes

There are three selector classes:

- `LowestIssuesOpenSelecter`
- `MostExperiencedAgentSelecter`
- `TraineeAgentSelecter`

These classes implement the Strategy pattern. Each selector has its own way of choosing the best agent.

They also implement `IssueObserver`, so they can update their internal counters whenever an issue is assigned or resolved.

### `Issue`

`Issue` stores issue details like issue id, order id, description, issue type, assigned agent, resolution, and status.

The status is stored as:

- `0` → unassigned
- `1` → assigned
- `2` → resolved

### `Agent`

`Agent` stores agent details and resolved issue history.

The resolved history is required because the problem asks us to return the list of issues resolved by an agent.

## Code

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Strategy interface used by different agent assignment strategies.
 */
interface AgentSelecter {
    String assignAgent(int issueType);
}

/**
 * Observer interface used to update assignment statistics when an issue changes.
 */
interface IssueObserver {
    void updated(Issue issue);
}

/**
 * Subject interface used by IssueManager.
 * Selector classes observe issue assignment and resolution events.
 */
interface IssueSubject {
    void attach(IssueObserver observer);

    void notifyAll(Issue issue);
}

public class Solution implements Q03CustomerIssueAssignerInterface {
    private Helper03 helper;
    private IssueManager issueManager;
    private AgentAssigner agentAssigner;
    private AgentsManager agentManager;

    private List<String> issueTypes;

    public Solution() {
    }

    public void init(List<String> issueTypes, Helper03 helper) {
        this.helper = helper;
        this.issueTypes = issueTypes;

        issueManager = new IssueManager();
        agentManager = new AgentsManager(issueTypes);

        // AgentAssigner registers all assignment strategies as observers of IssueManager.
        agentAssigner = new AgentAssigner(issueManager, agentManager, issueTypes);
    }

    // Returns "issue created", "issue already exists", or "invalid issue type".
    public String createIssue(
            String issueId,
            String orderId,
            int issueType,
            String description
    ) {
        if (issueType < 0 || issueType >= issueTypes.size()) {
            return "invalid issue type";
        }

        return issueManager.createIssue(issueId, orderId, issueType, description);
    }

    public void resolveIssue(String issueId, String resolution) {
        Issue issue = issueManager.getIssue(issueId);

        // Ignore invalid, already resolved, or unassigned issues.
        if (issue == null || issue.isResolved() || !issue.isAssigned()) {
            return;
        }

        Agent agent = agentManager.getAgent(issue.getAgentId());
        agent.addResolveIssueId(issueId);

        issueManager.resolveIssue(issueId, resolution);
    }

    // Returns "success" or "agent already exists".
    public String addAgent(String agentId, List<Integer> expertise) {
        return agentManager.addAgent(agentId, expertise);
    }

    // Returns a list of issue ids resolved by this agent.
    public List<String> getAgentHistory(String agentId) {
        Agent agent = agentManager.getAgent(agentId);

        if (agent == null) {
            return new ArrayList<>();
        }

        return agent.getResolvedIssues();
    }

    /*
     * assignStrategy:
     * 0 -> agent with least open issues overall
     * 1 -> agent with most resolved issues of given issue type
     * 2 -> agent with least open issues of given issue type
     */
    public String assignIssue(String issueId, int assignStrategy) {
        Issue issue = issueManager.getIssue(issueId);

        if (issue == null) {
            return "issue doesn't exist";
        }

        if (issue.isAssigned()) {
            return "issue already assigned";
        }

        String agentId = agentAssigner.getAgentForAssigningIssue(issue, assignStrategy);

        if (agentId.length() == 0) {
            return "agent with expertise doesn't exist";
        }

        issueManager.assignIssue(issueId, agentId);
        return agentId;
    }
}

class AgentsManager {
    // issueType -> list of agentIds who have expertise in that issue type
    private HashMap<Integer, ConcurrentLinkedDeque<String>> agentSkillsMap;

    // agentId -> Agent
    private ConcurrentHashMap<String, Agent> agents;

    AgentsManager(List<String> issueTypes) {
        agentSkillsMap = new HashMap<>();
        agents = new ConcurrentHashMap<>();

        // Initialize one queue for every issue type.
        for (int i = 0; i < issueTypes.size(); i++) {
            agentSkillsMap.put(i, new ConcurrentLinkedDeque<>());
        }
    }

    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }

    // Returns "success" or "agent already exists".
    public String addAgent(String agentId, List<Integer> expertise) {
        if (agents.containsKey(agentId)) {
            return "agent already exists";
        }

        agents.put(agentId, new Agent(agentId, expertise));

        // Add this agent under every issue type they can handle.
        for (int skill : expertise) {
            Collection<String> agentIds = agentSkillsMap.get(skill);
            agentIds.add(agentId);
        }

        return "success";
    }

    Collection<String> getAgentIdsForIssue(int issueType) {
        Collection<String> agents = agentSkillsMap.getOrDefault(
                issueType,
                new ConcurrentLinkedDeque<>()
        );

        // Return read-only view so callers cannot directly modify internal mapping.
        return Collections.unmodifiableCollection(agents);
    }
}

class IssueManager implements IssueSubject {
    private ArrayList<IssueObserver> observeAllIssues = new ArrayList<>();

    // issueId -> Issue
    private ConcurrentHashMap<String, Issue> allIssues = new ConcurrentHashMap<>();

    /**
     * @return "issue created" or "issue already exists"
     */
    public String createIssue(
            String issueId,
            String orderId,
            int issueType,
            String description
    ) {
        if (allIssues.containsKey(issueId)) {
            return "issue already exists";
        }

        Issue issue = new Issue(issueId, orderId, description, issueType);
        allIssues.put(issueId, issue);

        return "issue created";
    }

    public void resolveIssue(String issueId, String resolution) {
        Issue issue = allIssues.get(issueId);

        if (issue == null) {
            return;
        }

        issue.resolveIssue(resolution);

        // Notify strategies so they can update open/resolved issue counters.
        notifyAll(issue);
    }

    public Issue getIssue(String issueId) {
        return allIssues.getOrDefault(issueId, null);
    }

    void assignIssue(String issueId, String agentId) {
        Issue issue = allIssues.get(issueId);

        if (issue == null) {
            return;
        }

        issue.assignIssue(agentId);

        // Notify strategies so they can update open issue counters.
        notifyAll(issue);
    }

    public void attach(IssueObserver observer) {
        observeAllIssues.add(observer);
    }

    public void notifyAll(Issue issue) {
        for (IssueObserver observer : observeAllIssues) {
            observer.updated(issue);
        }
    }
}

class Agent {
    private String agentId;
    private List<Integer> skills;

    // Stores issue ids resolved by this agent.
    private ArrayList<String> resolvedIssues;

    Agent(String agentId, List<Integer> skills) {
        this.agentId = agentId;
        this.skills = skills;
        resolvedIssues = new ArrayList<>();
    }

    public List<String> getResolvedIssues() {
        return resolvedIssues;
    }

    /**
     * This method is synchronized because multiple issues may be resolved
     * by the same agent in a concurrent environment.
     */
    public synchronized void addResolveIssueId(String issueId) {
        resolvedIssues.add(issueId);
    }
}

class Issue {
    private String issueId;
    private String orderId;
    private String description;

    private int issueType;

    private String agentId;
    private String resolution;

    /*
     * status:
     * 0 -> unassigned
     * 1 -> assigned
     * 2 -> resolved
     */
    private int status;

    public Issue(String issueId, String orderId, String description, int issueType) {
        this.issueId = issueId;
        this.orderId = orderId;
        this.description = description;
        this.issueType = issueType;

        agentId = "";
        resolution = "";
        status = 0;
    }

    public String getAgentId() {
        return agentId;
    }

    public int getIssueType() {
        return issueType;
    }

    public void assignIssue(String agentId) {
        this.agentId = agentId;
        this.status = 1;
    }

    public boolean isAssigned() {
        return agentId != null && !agentId.isBlank();
    }

    public boolean isResolved() {
        return status == 2;
    }

    public void resolveIssue(String resolution) {
        this.resolution = this.resolution + " " + resolution;
        status = 2;
    }
}

class AgentAssigner {
    // assignStrategy -> assignment strategy
    private HashMap<Integer, AgentSelecter> map = new HashMap<>();

    AgentAssigner(
            IssueSubject issueSubject,
            AgentsManager agentManager,
            List<String> issueTypes
    ) {
        AgentSelecter lowestIssuesOpenAssigner =
                new LowestIssuesOpenSelecter(agentManager);

        AgentSelecter experiencedAgentAssigner =
                new MostExperiencedAgentSelecter(agentManager, issueTypes);

        AgentSelecter traineeAgentAssigner =
                new TraineeAgentSelecter(agentManager, issueTypes);

        map.put(0, lowestIssuesOpenAssigner);
        map.put(1, experiencedAgentAssigner);
        map.put(2, traineeAgentAssigner);

        // Register selectors as observers so their counters stay updated.
        issueSubject.attach((IssueObserver) lowestIssuesOpenAssigner);
        issueSubject.attach((IssueObserver) experiencedAgentAssigner);
        issueSubject.attach((IssueObserver) traineeAgentAssigner);
    }

    // Returns agent id, or empty string if no strategy/agent is found.
    String getAgentForAssigningIssue(Issue issue, int assignStrategy) {
        int issueType = issue.getIssueType();
        AgentSelecter strategy = map.get(assignStrategy);

        if (strategy != null) {
            return strategy.assignAgent(issueType);
        }

        return "";
    }
}

/**
 * Strategy 0:
 * Assigns the issue to the agent with the least number of open issues overall.
 */
class LowestIssuesOpenSelecter implements AgentSelecter, IssueObserver {
    // agentId -> open issue count
    private final ConcurrentHashMap<String, AtomicInteger> agentOpenIssuesMap =
            new ConcurrentHashMap<>();

    private final AgentsManager agentsManager;

    LowestIssuesOpenSelecter(AgentsManager agentsManager) {
        this.agentsManager = agentsManager;
    }

    // Returns agent id, or empty string if no suitable agent exists.
    public String assignAgent(int issueType) {
        String chosenAgentId = "";
        int minOpenIssues = 1000 * 1000 * 1000;

        Collection<String> agentIds = agentsManager.getAgentIdsForIssue(issueType);

        for (String agentId : agentIds) {
            AtomicInteger open = agentOpenIssuesMap.getOrDefault(
                    agentId,
                    new AtomicInteger(0)
            );

            // If an agent has no open issues, choose immediately.
            if (open.get() == 0) {
                return agentId;
            }

            if (open.get() <= minOpenIssues) {
                minOpenIssues = open.get();
                chosenAgentId = agentId;
            }
        }

        return chosenAgentId;
    }

    public void updated(Issue issue) {
        if (!issue.isAssigned()) {
            return;
        }

        agentOpenIssuesMap.putIfAbsent(
                issue.getAgentId(),
                new AtomicInteger(0)
        );

        AtomicInteger existing = agentOpenIssuesMap.get(issue.getAgentId());

        if (!issue.isResolved()) {
            existing.addAndGet(1);
        } else {
            existing.addAndGet(-1);
        }
    }
}

/**
 * Strategy 1:
 * Assigns the issue to the agent who has resolved the most issues
 * of the same issue type.
 */
class MostExperiencedAgentSelecter implements AgentSelecter, IssueObserver {
    // issueType -> (agentId -> resolved issue count for this issue type)
    private final HashMap<Integer, ConcurrentHashMap<String, AtomicInteger>>
            issueTypeAgentResolvedCountMap = new HashMap<>();

    private final AgentsManager agentsManager;

    MostExperiencedAgentSelecter(
            AgentsManager agentsManager,
            List<String> issueTypes
    ) {
        this.agentsManager = agentsManager;

        for (int issueType = 0; issueType < issueTypes.size(); issueType++) {
            issueTypeAgentResolvedCountMap.put(issueType, new ConcurrentHashMap<>());
        }
    }

    // Returns agent id, or empty string if no suitable agent exists.
    public String assignAgent(int issueType) {
        ConcurrentHashMap<String, AtomicInteger> agentResolvedIssuesCountMap =
                issueTypeAgentResolvedCountMap.get(issueType);

        Collection<String> agentIds = agentsManager.getAgentIdsForIssue(issueType);

        String chosenAgentId = "";
        int maxResolvedIssues = -1;

        for (String agentId : agentIds) {
            AtomicInteger issuesResolved = agentResolvedIssuesCountMap.getOrDefault(
                    agentId,
                    new AtomicInteger(0)
            );

            if (issuesResolved.get() >= maxResolvedIssues) {
                maxResolvedIssues = issuesResolved.get();
                chosenAgentId = agentId;
            }
        }

        return chosenAgentId;
    }

    public void updated(Issue issue) {
        if (!issue.isResolved()) {
            return;
        }

        ConcurrentHashMap<String, AtomicInteger> agentResolvedIssuesCountMap =
                issueTypeAgentResolvedCountMap.get(issue.getIssueType());

        agentResolvedIssuesCountMap.putIfAbsent(
                issue.getAgentId(),
                new AtomicInteger(0)
        );

        AtomicInteger existing = agentResolvedIssuesCountMap.get(issue.getAgentId());
        existing.addAndGet(1);
    }
}

/**
 * Strategy 2:
 * Assigns the issue to the agent with the least number of open issues
 * for the same issue type.
 */
class TraineeAgentSelecter implements AgentSelecter, IssueObserver {
    // issueType -> (agentId -> open issue count for this issue type)
    private final HashMap<Integer, ConcurrentHashMap<String, Integer>>
            issueTypeAgentOpenCountMap = new HashMap<>();

    private final AgentsManager agentsManager;

    TraineeAgentSelecter(AgentsManager agentsManager, List<String> issueTypes) {
        this.agentsManager = agentsManager;

        for (int issueType = 0; issueType < issueTypes.size(); issueType++) {
            issueTypeAgentOpenCountMap.put(issueType, new ConcurrentHashMap<>());
        }
    }

    // Returns agent id, or empty string if no suitable agent exists.
    public String assignAgent(int issueType) {
        Collection<String> agentIds = agentsManager.getAgentIdsForIssue(issueType);

        ConcurrentHashMap<String, Integer> agentOpenIssues =
                issueTypeAgentOpenCountMap.get(issueType);

        String chosenAgentId = "";
        int minOpenIssues = 1000 * 1000 * 1000;

        for (String agentId : agentIds) {
            Integer open = agentOpenIssues.getOrDefault(agentId, 0);

            // If an agent has no open issues of this type, choose immediately.
            if (open == 0) {
                return agentId;
            }

            if (open <= minOpenIssues) {
                minOpenIssues = open;
                chosenAgentId = agentId;
            }
        }

        return chosenAgentId;
    }

    public void updated(Issue issue) {
        if (!issue.isAssigned()) {
            return;
        }

        ConcurrentHashMap<String, Integer> agentOpenIssues =
                issueTypeAgentOpenCountMap.get(issue.getIssueType());

        agentOpenIssues.compute(
                issue.getAgentId(),
                (key, value) -> {
                    int next = value == null ? 0 : value;
                    return issue.isResolved() ? next - 1 : next + 1;
                }
        );
    }
}

/*
 * Uncomment below code if you are using your local IDE like IntelliJ or Eclipse.
 * Comment it again before pasting the completed solution in the online CodeZym editor.
 * This helps avoid unwanted compilation errors and gives method autocomplete locally.
 */

/*
interface Q03CustomerIssueAssignerInterface {
    void init(List<String> issueTypes, Helper03 helper);

    String createIssue(String issueId, String orderId, int issueType, String description);

    String assignIssue(String issueId, int assignDecisionType);

    void resolveIssue(String issueId, String resolution);

    String addAgent(String agentId, List<Integer> expertise);

    List<String> getAgentHistory(String agentId);
}

class Helper03 {
    void print(String s) {
        System.out.print(s);
    }

    void println(String s) {
        System.out.println(s);
    }
}
*/
```
