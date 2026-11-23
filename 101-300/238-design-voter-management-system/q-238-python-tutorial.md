# Design Voter Management System for Conducting Elections in Python

#### Problem Statement
[https://codezym.com/question/238-design-voter-management-system](https://codezym.com/question/238-design-voter-management-system)


An election here is a tree of places. A **State** has many **Constituencies**, a constituency has many **Districts**, and a district has many **EVMs**.

We store states, constituencies and districts as small classes linked in a tree, where each parent finds its children by name using a dictionary. Every EVM and every voter simply points to its district.

Two more dictionaries find any EVM or voter directly by id, because `castVote` only gives us ids.

The key decision is where to count votes. Results are always asked for a whole constituency, so each constituency keeps a running vote total for its candidates. Casting a vote becomes one counter update, and reading results becomes one sort.

No design pattern is needed here. The rules are fixed, there is one way to vote and one way to rank results, so plain classes with dictionaries give the shortest and clearest code. Chain of Responsibility and Composite look tempting, and we will see why they do not fit.


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

```python
class District:
    def __init__(self):
        self.evms = []  # every EVM in this district


class Evm:
    def __init__(self, district, candidateNames):
        self.district = district
        self.candidates = list(candidateNames)
        self.voteCounts = {}  # votes cast on this EVM only


# castVote: the EVM counts its own vote
evm.voteCounts[candidateName] = evm.voteCounts.get(candidateName, 0) + 1

# getConstituencyResult: add up every EVM, on every call
totals = {}
for district in constituency.districts.values():
    for evm in district.evms:
        for candidate in evm.candidates:
            totals[candidate] = totals.get(candidate, 0) + evm.voteCounts.get(candidate, 0)
# then sort the names in totals and format them
```

This gives correct answers, but it has two problems:

- **Slow results.** Every call walks every EVM in the constituency again, even if nothing changed. One constituency could hold all 100,000 EVMs with 100 candidates each. That is up to 10 million steps for a single result call.
- **Slow candidate check.** `candidates` is a list, so checking if a candidate is on the EVM means comparing names one by one.


## Solution 2: Keep a Running Total in Each Constituency

Results are only read per constituency. So we count votes right there, at the moment they are cast.

Each successful vote adds 1 to a single counter. A result call then only needs to sort the candidates of that one constituency.


### The Classes

**State, Constituency, District**

A `State` keeps its constituencies, and a `Constituency` keeps its districts, each in a dictionary by name. So finding a district from its path takes just three dictionary lookups.

A `District` keeps a link to its parent `Constituency`. This way a vote cast on an EVM reaches the right total through `evm.district.constituency`.

**Constituency.voteCounts**

This dictionary is the heart of the solution. It maps each candidate name to the total votes in the constituency.

When an EVM is added, each of its candidates is put in this dictionary with 0 votes, unless the name is already there. That is how zero vote candidates show up in results.

Since the key is the candidate name, the same candidate on many EVMs shares one count automatically.

**Evm**

Stores its district and its candidates in a `set`. The set answers "is this candidate on this EVM?" in constant time, without scanning a list.

**Voter**

Stores the voter's district and a `hasVoted` flag. The flag turns "vote only once" into a simple check.

**VoterManagementSystem**

The main class. It keeps `states` by name, plus `evms` and `voters` by id. Ids are unique across the whole system, so these flat dictionaries find an EVM or a voter directly, without searching the tree.


### How Each Method Works

**addEvm**

First reject the call if the EVM id already exists or the candidate list is empty. Only then create any missing level using `setdefault`, which returns the existing level or saves the new one. Checking first means a failed call never leaves empty places behind.

Then save the EVM and add its candidates to the constituency.

**registerVoter**

Follow the path state, constituency, district. If any level is missing or the voter id is taken, return `False`. Otherwise save the voter with a link to its district.

**castVote**

Run the five checks in the exact order the problem asks for:

1. Voter exists, else `VOTER_NOT_FOUND`
2. Voter has not voted yet, else `VOTER_ALREADY_VOTED`
3. EVM exists, else `EVM_NOT_FOUND`
4. EVM is in the voter's district, else `INVALID_EVM_FOR_VOTER`
5. Candidate is on the EVM, else `CANDIDATE_NOT_FOUND`

For check 4 we simply test that both point to the same `District` object, using `is`. Each district is created once and shared by its EVMs and voters, so the same district is always the same object.

The voter is marked and the vote is counted only after all checks pass. So a rejected attempt never marks the voter as voted.

**getConstituencyResult**

Find the constituency, or return an empty list if it does not exist. Sort its candidate names by votes from high to low. On a tie, the smaller name (by normal string comparison) comes first. Then format each one as `"name,votes"`.


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


### Python Code

```python
from typing import List, Optional


class State:
    """A state keeps its constituencies by name."""

    def __init__(self, name: str):
        self.name = name
        self.constituencies = {}  # constituency name -> Constituency


class Constituency:
    """Keeps its districts and the running vote total of every
    candidate found on any EVM inside it."""

    def __init__(self, name: str):
        self.name = name
        self.districts = {}   # district name -> District
        self.voteCounts = {}  # candidate name -> total votes

    def addCandidate(self, candidateName: str) -> None:
        # Adds the candidate with 0 votes, only if not already present
        self.voteCounts.setdefault(candidateName, 0)

    def addVote(self, candidateName: str) -> None:
        # The candidate is already in the dictionary, it was added along with its EVM
        self.voteCounts[candidateName] += 1

    def getResult(self) -> List[str]:
        # More votes first, smaller name first on a tie
        names = sorted(self.voteCounts, key=lambda name: (-self.voteCounts[name], name))
        return [name + "," + str(self.voteCounts[name]) for name in names]


class District:
    """Knows its parent constituency, so a vote cast here
    can update the constituency total directly."""

    def __init__(self, name: str, constituency: Constituency):
        self.name = name
        self.constituency = constituency


class Evm:
    """A voting machine placed in exactly one district."""

    def __init__(self, evmId: str, district: District, candidateNames: List[str]):
        self.id = evmId
        self.district = district
        self.candidates = set(candidateNames)  # quick "is this candidate here?" check

    def hasCandidate(self, candidateName: str) -> bool:
        return candidateName in self.candidates


class Voter:
    """A registered voter. Each voter can vote only once."""

    def __init__(self, voterId: str, district: District):
        self.id = voterId
        self.district = district
        self.hasVoted = False


class VoterManagementSystem:

    def __init__(self):
        self.states = {}  # state name -> State
        self.evms = {}    # EVM id -> Evm
        self.voters = {}  # voter id -> Voter

    def addEvm(self, stateName: str, constituencyName: str, districtName: str,
               evmId: str, candidateNames: List[str]) -> bool:
        # Validate first, so a failed call never creates empty places
        if evmId in self.evms or not candidateNames:
            return False

        # setdefault returns the existing level, or saves and returns the new one
        state = self.states.setdefault(stateName, State(stateName))
        constituency = state.constituencies.setdefault(
            constituencyName, Constituency(constituencyName))
        district = constituency.districts.setdefault(
            districtName, District(districtName, constituency))

        self.evms[evmId] = Evm(evmId, district, candidateNames)

        # Candidates must show up in results even with zero votes
        for candidateName in candidateNames:
            constituency.addCandidate(candidateName)
        return True

    def registerVoter(self, voterId: str, stateName: str, constituencyName: str,
                      districtName: str) -> bool:
        district = self.findDistrict(stateName, constituencyName, districtName)
        if district is None or voterId in self.voters:
            return False
        self.voters[voterId] = Voter(voterId, district)
        return True

    def castVote(self, voterId: str, evmId: str, candidateName: str) -> str:
        # Checks run in the exact order the problem asks for
        voter = self.voters.get(voterId)
        if voter is None:
            return "VOTER_NOT_FOUND"
        if voter.hasVoted:
            return "VOTER_ALREADY_VOTED"
        evm = self.evms.get(evmId)
        if evm is None:
            return "EVM_NOT_FOUND"
        if evm.district is not voter.district:  # each district is one shared object
            return "INVALID_EVM_FOR_VOTER"
        if not evm.hasCandidate(candidateName):
            return "CANDIDATE_NOT_FOUND"

        # All checks passed, so record the vote
        voter.hasVoted = True
        evm.district.constituency.addVote(candidateName)
        return "SUCCESS"

    def getConstituencyResult(self, stateName: str, constituencyName: str) -> List[str]:
        constituency = self.findConstituency(stateName, constituencyName)
        if constituency is None:
            return []
        return constituency.getResult()

    def findConstituency(self, stateName: str, constituencyName: str) -> Optional[Constituency]:
        # Returns None if the state or the constituency does not exist
        state = self.states.get(stateName)
        if state is None:
            return None
        return state.constituencies.get(constituencyName)

    def findDistrict(self, stateName: str, constituencyName: str,
                     districtName: str) -> Optional[District]:
        # Returns None if any level of the path does not exist
        constituency = self.findConstituency(stateName, constituencyName)
        if constituency is None:
            return None
        return constituency.districts.get(districtName)
```