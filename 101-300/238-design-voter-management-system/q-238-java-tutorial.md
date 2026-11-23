# Design Voter Management System for Conducting Elections in Java

#### Problem Statement
[https://codezym.com/question/238-design-voter-management-system](https://codezym.com/question/238-design-voter-management-system)


An election here is a tree of places. A **State** has many **Constituencies**, a constituency has many **Districts**, and a district has many **EVMs**.

We store states, constituencies and districts as small classes linked in a tree, where each parent finds its children by name using a map. Every EVM and every voter simply points to its district.

Two more maps find any EVM or voter directly by id, because `castVote` only gives us ids.

The key decision is where to count votes. Results are always asked for a whole constituency, so each constituency keeps a running vote total for its candidates. Casting a vote becomes one counter update, and reading results becomes one sort.

No design pattern is needed here. The rules are fixed, there is one way to vote and one way to rank results, so plain classes with maps give the shortest and clearest code. Chain of Responsibility and Composite look tempting, and we will see why they do not fit.


## How the Election Is Organized

```
State: Bihar
 └── Constituency: Patna Sahib
      └── District: Patna Urban
           └── EVM: EVM-101 (candidates: Asha, Biren, Chitra)
```

A few rules shape the whole design:

- Places are created only by `addEvm`. A district exists only after an EVM is added to it.
- A district is found by its full path: state, then constituency, then district. So two districts with the same name in different constituencies are different districts.
- A voter belongs to one district and can vote only on an EVM in that district.
- Votes for the same candidate name on different EVMs of a constituency are added together.


## Solution 1: Count Votes When Results Are Asked

The first idea is to let each EVM count its own votes, just like a real machine. Each district keeps a list of its EVMs.

To get a constituency result, we walk through all its districts and EVMs and add up the numbers.

```java
class District {
    List<Evm> evms = new ArrayList<>(); // every EVM in this district
}

class Evm {
    District district;
    List<String> candidates;
    Map<String, Integer> voteCounts = new HashMap<>(); // votes cast on this EVM only
}

// castVote: the EVM counts its own vote
evm.voteCounts.put(candidateName, evm.voteCounts.getOrDefault(candidateName, 0) + 1);

// getConstituencyResult: add up every EVM, on every call
Map<String, Integer> totals = new HashMap<>();
for (District district : constituency.districts.values()) {
    for (Evm evm : district.evms) {
        for (String candidate : evm.candidates) {
            int votes = evm.voteCounts.getOrDefault(candidate, 0);
            totals.put(candidate, totals.getOrDefault(candidate, 0) + votes);
        }
    }
}
// then sort the names in totals and format them
```

This gives correct answers, but it has two problems:

- **Slow results.** Every call walks every EVM in the constituency again, even if nothing changed. One constituency could hold all 100,000 EVMs with 100 candidates each. That is up to 10 million steps for a single result call.
- **Slow candidate check.** `candidates` is a `List`, so checking if a candidate is on the EVM means comparing names one by one.


## Solution 2: Keep a Running Total in Each Constituency

Results are only read per constituency. So we count votes right there, at the moment they are cast.

Each successful vote adds 1 to a single counter. A result call then only needs to sort the candidates of that one constituency.


### The Classes

**State, Constituency, District**

A `State` keeps its constituencies, and a `Constituency` keeps its districts, each in a `Map` by name. So finding a district from its path takes just three map lookups.

A `District` keeps a link to its parent `Constituency`. This way a vote cast on an EVM reaches the right total through `evm.district.constituency`.

**Constituency.voteCounts**

This `Map<String, Integer>` is the heart of the solution. It maps each candidate name to the total votes in the constituency.

When an EVM is added, each of its candidates is put in this map with 0 votes, unless the name is already there. That is how zero vote candidates show up in results.

Since the key is the candidate name, the same candidate on many EVMs shares one count automatically.

**Evm**

Stores its district and its candidates in a `Set`. The set answers "is this candidate on this EVM?" in constant time, without scanning a list.

**Voter**

Stores the voter's district and a `hasVoted` flag. The flag turns "vote only once" into a simple check.

**VoterManagementSystem**

The main class. It keeps `states` by name, plus `evms` and `voters` by id. Ids are unique across the whole system, so these flat maps find an EVM or a voter directly, without searching the tree.


### How Each Method Works

**addEvm**

First reject the call if the EVM id already exists or the candidate list is empty. Only then create any missing level using `computeIfAbsent`. Checking first means a failed call never leaves empty places behind.

Then save the EVM and add its candidates to the constituency.

**registerVoter**

Follow the path state, constituency, district. If any level is missing or the voter id is taken, return `false`. Otherwise save the voter with a link to its district.

**castVote**

Run the five checks in the exact order the problem asks for:

1. Voter exists, else `VOTER_NOT_FOUND`
2. Voter has not voted yet, else `VOTER_ALREADY_VOTED`
3. EVM exists, else `EVM_NOT_FOUND`
4. EVM is in the voter's district, else `INVALID_EVM_FOR_VOTER`
5. Candidate is on the EVM, else `CANDIDATE_NOT_FOUND`

For check 4 we simply compare the two `District` objects. Each district is created once and shared by its EVMs and voters, so the same district is always the same object.

The voter is marked and the vote is counted only after all checks pass. So a rejected attempt never marks the voter as voted.

**getConstituencyResult**

Find the constituency, or return an empty list if it does not exist. Sort its candidate names by votes from high to low. On a tie, the smaller name (by `compareTo`) comes first. Then format each one as `"name,votes"`.


### Design Patterns That Look Tempting

**Chain of Responsibility** for the five vote checks: each check would become a handler class that passes the request to the next one. This helps when checks change often.

Here the checks are fixed and each one is a single `if`. A chain would add five classes and hide the check order, which is the one thing the problem is strict about.

**Composite** for the location tree: every level would share one interface, and any node could report its votes by asking its children.

But that is just Solution 1 in another form, since every result call walks the tree again. Our levels also do different jobs, so treating them the same adds nothing.


### Complexity

| Method | Time |
| --- | --- |
| `addEvm` | O(c), where c is the number of candidates on the EVM |
| `registerVoter` | O(1) |
| `castVote` | O(1) |
| `getConstituencyResult` | O(k log k), where k is the number of candidates in the constituency |

A result call no longer depends on how many EVMs the constituency has.


### Java Code

```java
import java.util.*;

// A state keeps its constituencies by name.
class State {
    String name;
    Map<String, Constituency> constituencies = new HashMap<>();

    State(String name) {
        this.name = name;
    }
}

// A constituency keeps its districts and the running vote total
// of every candidate found on any EVM inside it.
class Constituency {
    String name;
    Map<String, District> districts = new HashMap<>();
    Map<String, Integer> voteCounts = new HashMap<>(); // candidate name -> total votes

    Constituency(String name) {
        this.name = name;
    }

    // Adds the candidate with 0 votes, only if not already present
    void addCandidate(String candidateName) {
        voteCounts.putIfAbsent(candidateName, 0);
    }

    // The candidate is already in the map, it was added along with its EVM
    void addVote(String candidateName) {
        voteCounts.put(candidateName, voteCounts.get(candidateName) + 1);
    }

    // Returns "candidateName,voteCount" for every candidate, in result order
    List<String> getResult() {
        List<String> names = new ArrayList<>(voteCounts.keySet());
        names.sort((a, b) -> {
            int votesA = voteCounts.get(a);
            int votesB = voteCounts.get(b);
            if (votesA != votesB) {
                return Integer.compare(votesB, votesA); // more votes first
            }
            return a.compareTo(b); // tie: smaller name first
        });

        List<String> result = new ArrayList<>();
        for (String name : names) {
            result.add(name + "," + voteCounts.get(name));
        }
        return result;
    }
}

// A district knows its parent constituency,
// so a vote cast here can update the constituency total directly.
class District {
    String name;
    Constituency constituency;

    District(String name, Constituency constituency) {
        this.name = name;
        this.constituency = constituency;
    }
}

// A voting machine placed in exactly one district.
class Evm {
    String id;
    District district;
    Set<String> candidates; // set gives a quick "is this candidate here?" check

    Evm(String evmId, District district, List<String> candidateNames) {
        this.id = evmId;
        this.district = district;
        this.candidates = new HashSet<>(candidateNames);
    }

    boolean hasCandidate(String candidateName) {
        return candidates.contains(candidateName);
    }
}

// A registered voter. Each voter can vote only once.
class Voter {
    String id;
    District district;
    boolean hasVoted = false;

    Voter(String voterId, District district) {
        this.id = voterId;
        this.district = district;
    }
}

public class VoterManagementSystem {
    Map<String, State> states = new HashMap<>(); // state name -> state
    Map<String, Evm> evms = new HashMap<>();     // EVM id -> EVM
    Map<String, Voter> voters = new HashMap<>(); // voter id -> voter

    public VoterManagementSystem() {
    }

    public boolean addEvm(String stateName, String constituencyName, String districtName,
                          String evmId, List<String> candidateNames) {
        // Validate first, so a failed call never creates empty places
        if (evms.containsKey(evmId) || candidateNames == null || candidateNames.isEmpty()) {
            return false;
        }

        // Create each level only if it does not exist yet
        State state = states.computeIfAbsent(stateName, name -> new State(name));
        Constituency constituency = state.constituencies.computeIfAbsent(
                constituencyName, name -> new Constituency(name));
        District district = constituency.districts.computeIfAbsent(
                districtName, name -> new District(name, constituency));

        evms.put(evmId, new Evm(evmId, district, candidateNames));

        // Candidates must show up in results even with zero votes
        for (String candidateName : candidateNames) {
            constituency.addCandidate(candidateName);
        }
        return true;
    }

    public boolean registerVoter(String voterId, String stateName,
                                 String constituencyName, String districtName) {
        District district = findDistrict(stateName, constituencyName, districtName);
        if (district == null || voters.containsKey(voterId)) {
            return false;
        }
        voters.put(voterId, new Voter(voterId, district));
        return true;
    }

    public String castVote(String voterId, String evmId, String candidateName) {
        // Checks run in the exact order the problem asks for
        Voter voter = voters.get(voterId);
        if (voter == null) {
            return "VOTER_NOT_FOUND";
        }
        if (voter.hasVoted) {
            return "VOTER_ALREADY_VOTED";
        }
        Evm evm = evms.get(evmId);
        if (evm == null) {
            return "EVM_NOT_FOUND";
        }
        if (evm.district != voter.district) { // each district is one shared object
            return "INVALID_EVM_FOR_VOTER";
        }
        if (!evm.hasCandidate(candidateName)) {
            return "CANDIDATE_NOT_FOUND";
        }

        // All checks passed, so record the vote
        voter.hasVoted = true;
        evm.district.constituency.addVote(candidateName);
        return "SUCCESS";
    }

    public List<String> getConstituencyResult(String stateName, String constituencyName) {
        Constituency constituency = findConstituency(stateName, constituencyName);
        if (constituency == null) {
            return new ArrayList<>();
        }
        return constituency.getResult();
    }

    // Returns null if the state or the constituency does not exist
    Constituency findConstituency(String stateName, String constituencyName) {
        State state = states.get(stateName);
        if (state == null) {
            return null;
        }
        return state.constituencies.get(constituencyName);
    }

    // Returns null if any level of the path does not exist
    District findDistrict(String stateName, String constituencyName, String districtName) {
        Constituency constituency = findConstituency(stateName, constituencyName);
        if (constituency == null) {
            return null;
        }
        return constituency.districts.get(districtName);
    }
}
```