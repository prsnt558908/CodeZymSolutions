# Design Job Posting Service With Candidate Matching in Python

#### Problem Statement
[https://codezym.com/question/393-job-posting-service](https://codezym.com/question/393-job-posting-service)

Every feature in this problem depends on one small number: **how many skills a candidate and a job have in common**. Eligibility needs it, the recruiter's ranking needs it and the candidate's job list needs it. So the whole design is about getting this number quickly and keeping results in the right order.

We start with a simple brute force that recalculates everything on every query. Then we improve it in two steps: a **skill index** (skill → jobs that need it) so a candidate only looks at jobs that share a skill, and a **sorted list of eligible applicants inside each job** so a recruiter gets the ranking without sorting again.

This is a low level design problem, but using a design pattern is not optimal here. Job types do not change any rule, and both rankings turn out to be the same rule. A few plain classes and one sort key give shorter and clearer code than a Factory or a Strategy would.

## Understanding the Problem

A few rules decide everything:

- **Matching skills:** the number of the job's skills that the candidate also has.
- **Eligible:** `matches >= minimumSkillMatches` and `yearsOfExperience >= minimumYearsOfExperience`.
- A candidate can apply even when not eligible. The recruiter does not see them for now, but a later profile update can make them eligible.
- A profile update keeps all old applications.
- `getTopCandidates` looks only at the job's applicants. `getTopEligibleJobs` looks at **all** jobs, applied or not.

The two rankings look different, but they follow the same three steps:

| Step | Top candidates of a job | Top jobs of a candidate |
|---|---|---|
| 1 | More matching skills | More matching skills |
| 2 | More years of experience | Higher minimum years of experience |
| 3 | Smaller candidate id | Smaller job id |

So one small helper that turns `(id, matches, years)` into a sort key can sort both lists. We call it `rank_key`.

## Do We Need a Design Pattern?

Two patterns look tempting at first, but neither is a good fit.

**Factory for job types.** The statement talks about full-time, part-time, internship and contract jobs. So it is tempting to create `FullTimeJob`, `InternshipJob` and so on through a factory. But the job type changes nothing. Every type uses the same eligibility check and the same ranking, so these subclasses would be identical copies. A plain `job_type` field is enough.

**Strategy for ranking.** Two ranking rules sound like two strategies. But as the table above shows, it is really one rule: more matches, more years, smaller id. Only the meaning of "years" changes. One sort key covers both, so a strategy class for each ranking would add code and give nothing back.

If a future rule depends on the job type (for example, internships ignoring experience), that is the right time to add a Strategy for eligibility and pick it with a Factory.

## Solution 1: Brute Force

### Idea

Keep jobs and candidates in two dictionaries (id → object), so any lookup by id is O(1). Each job keeps a set of the candidates who applied.

When someone asks for a ranking, count the matching skills from scratch, keep the eligible ones, sort them and return the first `limit` ids.

### Classes and why we need them

- **`JobOpening`** holds the job details and its applicant ids. The applicants are kept in a `set`, so spotting a repeat application is O(1). The job also counts matches and checks eligibility, because the thresholds belong to the job.
- **`CandidateProfile`** holds the candidate's skills and years of experience. Skills are kept in a `set` for both jobs and candidates, so the shared skills are just `job.skills & candidate.skills`.
- **`rank_key`** turns one ranking row into the tuple `(-matches, -years, id)`. Python sorts tuples item by item, and the minus signs make bigger numbers come first. The id is left as it is, so ties go to the smaller id. A plain `sort()` now gives the exact ranking for both lists.

### How each method works

- `postJobOpening`: create a `JobOpening` and store it in the `jobs` dictionary.
- `createOrUpdateCandidateProfile`: create the profile, or replace its skills and years. Applications live inside the jobs, so they are not touched.
- `applyForJob`: return `False` if the candidate id is already in the job's applicant set. Otherwise add it and return `True`.
- `getTopCandidates`: for each applicant, count matches, keep the eligible ones, sort and return the first `limit`.
- `getTopEligibleJobs`: for **every job** in the system, count matches, keep the eligible ones, sort and return the first `limit`.

### Problems with this approach

- `getTopEligibleJobs` checks every job, even jobs that share no skill with the candidate. With thousands of jobs, most of that work is wasted.
- `getTopCandidates` recounts and re-sorts all applicants on every call, even when nothing changed since the last call. A popular job with thousands of applicants pays this cost again and again.

### Code

```python
class JobOpening:
    """A job opening posted by a recruiter."""

    def __init__(self, job_id, recruiter_id, title, job_type, skills, min_skill_matches, min_years):
        self.job_id = job_id
        self.recruiter_id = recruiter_id
        self.title = title
        self.job_type = job_type            # full-time, internship ... only a label, it changes no rule
        self.skills = set(skills)           # distinct skills the job asks for
        self.min_skill_matches = min_skill_matches
        self.min_years = min_years
        self.applicant_ids = set()          # everyone who applied, eligible or not

    def count_matches(self, candidate_skills):
        """Number of this job's skills that the candidate also has."""
        return len(self.skills & candidate_skills)

    def is_eligible(self, matches, years):
        return matches >= self.min_skill_matches and years >= self.min_years


class CandidateProfile:
    """A candidate's skills and years of experience."""

    def __init__(self, candidate_id, skills, years):
        self.candidate_id = candidate_id
        self.skills = set(skills)           # a set makes shared skills one quick intersection
        self.years = years


def rank_key(item_id, matches, years):
    """One row of a ranking: a candidate for a job, or a job for a candidate.

    Both rankings use the same rule: more matches, then more years, then smaller id.
    Python sorts tuples item by item, and the minus signs put bigger numbers first.
    """
    return (-matches, -years, item_id)


class JobPostingService:
    def __init__(self):
        self.jobs = {}          # job id -> JobOpening
        self.candidates = {}    # candidate id -> CandidateProfile

    def postJobOpening(self, recruiterId, jobId, jobTitle, jobType, requiredSkills, minimumSkillMatches, minimumYearsOfExperience):
        self.jobs[jobId] = JobOpening(jobId, recruiterId, jobTitle, jobType,
                                      requiredSkills, minimumSkillMatches, minimumYearsOfExperience)

    def createOrUpdateCandidateProfile(self, candidateId, skills, yearsOfExperience):
        candidate = self.candidates.get(candidateId)
        if candidate is None:
            self.candidates[candidateId] = CandidateProfile(candidateId, skills, yearsOfExperience)
            return
        # Applications are stored inside the jobs, so they stay untouched
        candidate.skills = set(skills)
        candidate.years = yearsOfExperience

    def applyForJob(self, candidateId, jobId):
        job = self.jobs.get(jobId)
        if job is None or candidateId not in self.candidates:
            return False
        if candidateId in job.applicant_ids:
            return False                    # already applied
        job.applicant_ids.add(candidateId)
        return True

    def getTopCandidates(self, recruiterId, jobId, limit):
        job = self.jobs.get(jobId)
        if job is None or job.recruiter_id != recruiterId:
            return []

        # Recount every applicant on every call
        rows = []
        for candidate_id in job.applicant_ids:
            candidate = self.candidates[candidate_id]
            matches = job.count_matches(candidate.skills)
            if job.is_eligible(matches, candidate.years):
                rows.append(rank_key(candidate_id, matches, candidate.years))
        return self.top_ids(rows, limit)

    def getTopEligibleJobs(self, candidateId, limit):
        candidate = self.candidates.get(candidateId)
        if candidate is None:
            return []

        # Check every job in the system, even jobs that share no skill
        rows = []
        for job in self.jobs.values():
            matches = job.count_matches(candidate.skills)
            if job.is_eligible(matches, candidate.years):
                rows.append(rank_key(job.job_id, matches, job.min_years))
        return self.top_ids(rows, limit)

    def top_ids(self, rows, limit):
        """Sorts the rows by the ranking rule and returns the first 'limit' ids."""
        rows.sort()
        return [item_id for _, _, item_id in rows[:limit]]
```

## Solution 2: Skill Index and Sorted Applicants

We fix the two problems one at a time.

### Improvement 1: Skill index for `getTopEligibleJobs`

`minimumSkillMatches` is at least 1, so a job can only be eligible if it shares **at least one** skill with the candidate. That is why we keep a dictionary from each skill to the ids of the jobs that need it.

To find jobs for a candidate, we walk only the lists of the candidate's own skills. Each time a job id shows up, we add 1 to its counter. At the end, every counter is exactly that job's matching skill count. Jobs that share no skill are never touched.

For the counting we use `Counter`, a dictionary made for counting. `counter.update(list_of_ids)` adds 1 for every id in the list.

Let's run it on Example 2. Candidate `sam9` has `python`, `sql`, `aws` and `docker`:

```
python -> [data18, machine6]
sql    -> [data18]
aws    -> [cloud11]
docker -> [cloud11]

counters: data18 = 2, cloud11 = 2, machine6 = 1
```

`machine6` needs 2 matches, so it is dropped. `data18` and `cloud11` both match 2 skills and `sam9` has enough experience (5 years) for both. `cloud11` has the higher minimum experience (4 vs 3), so the answer is `[cloud11, data18]`.

### Improvement 2: Keep eligible applicants sorted inside each job

A job's ranking only changes at two moments:

1. A candidate applies.
2. A candidate who already applied updates their profile.

So instead of sorting on every read, each job keeps its eligible applicants already in ranking order. `getTopCandidates` just returns the first `limit` rows.

Python has no built-in sorted set, so each job keeps a plain list of `rank_key` tuples and the `bisect` module keeps it sorted. `bisect.insort` puts a new row in its right place, and `bisect.bisect_left` finds a row we want to delete.

- **On apply:** count matches. If the candidate is eligible, insert a row into the job's sorted list.
- **On profile update:** remove the candidate's old row from every job they applied to, update the profile, then insert a fresh row into each of those jobs where the candidate is eligible now.

This is exactly what Example 3 needs. `lee3` applies with only `android`, so no row is added. After the update adds `kotlin`, the refresh step adds a row and `lee3` shows up in the ranking.

Each candidate now keeps `applied_job_ids`, and the job no longer needs its own applicant set. This one set does two jobs: it catches repeat applications, and it tells us which rankings to refresh after a profile update.

**Why remove before updating?** To find the old row, we rebuild its tuple from the candidate's current profile. So this must happen **before** the profile changes. That is why the order is always: remove old row, update profile, add new row.

**The trade-off:** a profile update now touches every job the candidate applied to. This is a good deal, because a candidate usually applies to a few jobs, while a popular job can have thousands of applicants and is viewed again and again.

### Data structures at a glance

```
jobs             : job id       -> JobOpening
candidates       : candidate id -> CandidateProfile
job_ids_by_skill : skill        -> ids of jobs that need this skill

JobOpening.ranked_applicants     : sorted list of eligible applicants, best first
CandidateProfile.applied_job_ids : ids of jobs this candidate applied to
```

### Code

```python
import bisect
from collections import Counter


class JobOpening:
    """A job opening posted by a recruiter."""

    def __init__(self, job_id, recruiter_id, title, job_type, skills, min_skill_matches, min_years):
        self.job_id = job_id
        self.recruiter_id = recruiter_id
        self.title = title
        self.job_type = job_type            # full-time, internship ... only a label, it changes no rule
        self.skills = set(skills)           # distinct skills the job asks for
        self.min_skill_matches = min_skill_matches
        self.min_years = min_years
        # Only the applicants who are eligible right now, as rank_key tuples kept in sorted order
        self.ranked_applicants = []

    def count_matches(self, candidate_skills):
        """Number of this job's skills that the candidate also has."""
        return len(self.skills & candidate_skills)

    def is_eligible(self, matches, years):
        return matches >= self.min_skill_matches and years >= self.min_years


class CandidateProfile:
    """A candidate's skills, years of experience and the jobs they applied to."""

    def __init__(self, candidate_id, skills, years):
        self.candidate_id = candidate_id
        self.skills = set(skills)           # a set makes shared skills one quick intersection
        self.years = years
        # Spots repeat applications and tells us which rankings to refresh after a profile update
        self.applied_job_ids = set()


def rank_key(item_id, matches, years):
    """One row of a ranking: a candidate for a job, or a job for a candidate.

    Both rankings use the same rule: more matches, then more years, then smaller id.
    Python sorts tuples item by item, and the minus signs put bigger numbers first.
    """
    return (-matches, -years, item_id)


class JobPostingService:
    def __init__(self):
        self.jobs = {}                  # job id -> JobOpening
        self.candidates = {}            # candidate id -> CandidateProfile
        self.job_ids_by_skill = {}      # skill -> ids of the jobs that ask for this skill

    def postJobOpening(self, recruiterId, jobId, jobTitle, jobType, requiredSkills, minimumSkillMatches, minimumYearsOfExperience):
        job = JobOpening(jobId, recruiterId, jobTitle, jobType,
                         requiredSkills, minimumSkillMatches, minimumYearsOfExperience)
        self.jobs[jobId] = job
        for skill in job.skills:
            self.job_ids_by_skill.setdefault(skill, []).append(jobId)

    def createOrUpdateCandidateProfile(self, candidateId, skills, yearsOfExperience):
        candidate = self.candidates.get(candidateId)
        if candidate is None:
            self.candidates[candidateId] = CandidateProfile(candidateId, skills, yearsOfExperience)
            return
        # 1. Remove the old rows while the old skills and years are still known
        for job_id in candidate.applied_job_ids:
            self.remove_from_ranking(self.jobs[job_id], candidate)
        # 2. Update the profile, applications stay as they are
        candidate.skills = set(skills)
        candidate.years = yearsOfExperience
        # 3. Add fresh rows to the jobs where the candidate is eligible now
        for job_id in candidate.applied_job_ids:
            self.add_to_ranking(self.jobs[job_id], candidate)

    def applyForJob(self, candidateId, jobId):
        candidate = self.candidates.get(candidateId)
        job = self.jobs.get(jobId)
        if candidate is None or job is None:
            return False
        if jobId in candidate.applied_job_ids:
            return False                    # already applied
        candidate.applied_job_ids.add(jobId)
        self.add_to_ranking(job, candidate)
        return True

    def getTopCandidates(self, recruiterId, jobId, limit):
        job = self.jobs.get(jobId)
        if job is None or job.recruiter_id != recruiterId:
            return []
        # Already sorted, so just read the first 'limit' rows
        return [candidate_id for _, _, candidate_id in job.ranked_applicants[:limit]]

    def getTopEligibleJobs(self, candidateId, limit):
        candidate = self.candidates.get(candidateId)
        if candidate is None:
            return []

        # Count shared skills using the index, jobs with no shared skill are never touched
        match_count = Counter()
        for skill in candidate.skills:
            match_count.update(self.job_ids_by_skill.get(skill, []))

        rows = []
        for job_id, matches in match_count.items():
            job = self.jobs[job_id]
            if job.is_eligible(matches, candidate.years):
                rows.append(rank_key(job_id, matches, job.min_years))
        rows.sort()
        return [job_id for _, _, job_id in rows[:limit]]

    def add_to_ranking(self, job, candidate):
        """Adds the candidate to the job's ranking, only if they are eligible."""
        matches = job.count_matches(candidate.skills)
        if job.is_eligible(matches, candidate.years):
            bisect.insort(job.ranked_applicants, rank_key(candidate.candidate_id, matches, candidate.years))

    def remove_from_ranking(self, job, candidate):
        """Removes the candidate's row from the job's ranking. Does nothing if they were not eligible."""
        matches = job.count_matches(candidate.skills)
        row = rank_key(candidate.candidate_id, matches, candidate.years)
        # Binary search finds where the row would be, then we delete it if it is there
        index = bisect.bisect_left(job.ranked_applicants, row)
        if index < len(job.ranked_applicants) and job.ranked_applicants[index] == row:
            job.ranked_applicants.pop(index)
```

## Complexity

`S` = skills in a job, `C` = skills of a candidate, `N` = applicants of a job, `K` = jobs a candidate applied to, `J` = all jobs, `E` = eligible jobs found, `P` = job ids visited through the skill index.

| Method | Solution 1 | Solution 2 |
|---|---|---|
| `postJobOpening` | O(S) | O(S) |
| `createOrUpdateCandidateProfile` | O(C) | O(C + K × (S + N)) |
| `applyForJob` | O(1) | O(S + N) |
| `getTopCandidates` | O(N × S + N log N) | O(limit) |
| `getTopEligibleJobs` | O(J × S + E log E) | O(P + E log E) |

The `N` in Solution 2 comes from inserting into or deleting from the middle of a Python list, which shifts the items after it. That shift is one fast memory move, so it stays cheap even for jobs with many applicants.

Solution 2 makes both queries much cheaper and pays a little more when a candidate applies or updates their profile.