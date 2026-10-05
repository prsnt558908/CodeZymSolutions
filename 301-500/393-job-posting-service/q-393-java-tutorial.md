# Design Job Posting Service With Candidate Matching in Java

#### Problem Statement
[https://codezym.com/question/393-job-posting-service](https://codezym.com/question/393-job-posting-service)

Every feature in this problem depends on one small number: **how many skills a candidate and a job have in common**. Eligibility needs it, the recruiter's ranking needs it and the candidate's job list needs it. So the whole design is about getting this number quickly and keeping results in the right order.

We start with a simple brute force that recalculates everything on every query. Then we improve it in two steps: a **skill index** (skill → jobs that need it) so a candidate only looks at jobs that share a skill, and a **sorted set of eligible applicants inside each job** so a recruiter gets the ranking without sorting again.

This is a low level design problem, but using a design pattern is not optimal here. Job types do not change any rule, and both rankings turn out to be the same rule. A few plain classes and one compare method give shorter and clearer code than a Factory or a Strategy would.

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

So one small class that holds `(id, matches, years)` and knows how to compare itself can sort both lists. We call it `MatchResult`.

## Do We Need a Design Pattern?

Two patterns look tempting at first, but neither is a good fit.

**Factory for job types.** The statement talks about full-time, part-time, internship and contract jobs. So it is tempting to create `FullTimeJob`, `InternshipJob` and so on through a factory. But the job type changes nothing. Every type uses the same eligibility check and the same ranking, so these subclasses would be identical copies. A plain `type` field is enough.

**Strategy for ranking.** Two ranking rules sound like two strategies. But as the table above shows, it is really one rule: more matches, more years, smaller id. Only the meaning of "years" changes. One `compareTo` method covers both, so a strategy interface with two classes would add code and give nothing back.

If a future rule depends on the job type (for example, internships ignoring experience), that is the right time to add a Strategy for eligibility and pick it with a Factory.

## Solution 1: Brute Force

### Idea

Keep jobs and candidates in two maps (id → object), so any lookup by id is O(1). Each job keeps a set of the candidates who applied.

When someone asks for a ranking, count the matching skills from scratch, keep the eligible ones, sort them and return the first `limit` ids.

### Classes and why we need them

- **`JobOpening`** holds the job details and its applicant ids. The applicants are kept in a `Set`, so a repeat application is caught for free. The job also counts matches and checks eligibility, because the thresholds belong to the job.
- **`CandidateProfile`** holds the candidate's skills and years of experience. Skills are kept in a `Set`, so while counting matches, "does the candidate have this skill?" is an O(1) check.
- **`MatchResult`** is one row of a ranking: `(id, matches, years)`. Its `compareTo` holds the ranking rule, so `Collections.sort` does all the ordering.

### How each method works

- `postJobOpening`: create a `JobOpening` and store it in the `jobs` map.
- `createOrUpdateCandidateProfile`: create the profile, or replace its skills and years. Applications live inside the jobs, so they are not touched.
- `applyForJob`: add the candidate id to the job's applicant set. `Set.add()` returns `false` if the id was already there, which is exactly the answer we need.
- `getTopCandidates`: for each applicant, count matches, keep the eligible ones, sort and return the first `limit`.
- `getTopEligibleJobs`: for **every job** in the system, count matches, keep the eligible ones, sort and return the first `limit`.

### Problems with this approach

- `getTopEligibleJobs` checks every job, even jobs that share no skill with the candidate. With thousands of jobs, most of that work is wasted.
- `getTopCandidates` recounts and re-sorts all applicants on every call, even when nothing changed since the last call. A popular job with thousands of applicants pays this cost again and again.

### Code

```java
import java.util.*;

/**
 * A job opening posted by a recruiter.
 */
class JobOpening {
    String jobId;
    String recruiterId;
    String title;
    String type;              // full-time, internship ... only a label, it changes no rule
    Set<String> skills;       // distinct skills the job asks for
    int minSkillMatches;
    int minYears;
    Set<String> applicantIds = new HashSet<>();   // everyone who applied, eligible or not

    JobOpening(String jobId, String recruiterId, String title, String type,
               List<String> skills, int minSkillMatches, int minYears) {
        this.jobId = jobId;
        this.recruiterId = recruiterId;
        this.title = title;
        this.type = type;
        this.skills = new HashSet<>(skills);
        this.minSkillMatches = minSkillMatches;
        this.minYears = minYears;
    }

    /** Number of this job's skills that the candidate also has. */
    int countMatches(Set<String> candidateSkills) {
        int matches = 0;
        for (String skill : skills) {
            if (candidateSkills.contains(skill)) matches++;
        }
        return matches;
    }

    boolean isEligible(int matches, int years) {
        return matches >= minSkillMatches && years >= minYears;
    }
}

/**
 * A candidate's skills and years of experience.
 */
class CandidateProfile {
    String candidateId;
    Set<String> skills;       // a set makes "does the candidate have skill X?" an O(1) check
    int years;

    CandidateProfile(String candidateId, List<String> skills, int years) {
        this.candidateId = candidateId;
        this.skills = new HashSet<>(skills);
        this.years = years;
    }
}

/**
 * One row of a ranking: a candidate for a job, or a job for a candidate.
 * Both rankings use the same rule: more matches, then more years, then smaller id.
 */
class MatchResult implements Comparable<MatchResult> {
    String id;
    int matches;
    int years;    // candidate's experience, or the job's minimum experience

    MatchResult(String id, int matches, int years) {
        this.id = id;
        this.matches = matches;
        this.years = years;
    }

    @Override
    public int compareTo(MatchResult other) {
        if (matches != other.matches) return Integer.compare(other.matches, matches); // more matches first
        if (years != other.years) return Integer.compare(other.years, years);         // more years first
        return id.compareTo(other.id);                                                // smaller id first
    }
}

public class JobPostingService {
    Map<String, JobOpening> jobs = new HashMap<>();
    Map<String, CandidateProfile> candidates = new HashMap<>();

    public JobPostingService() {
    }

    public void postJobOpening(String recruiterId, String jobId, String jobTitle, String jobType,
                               List<String> requiredSkills, int minimumSkillMatches, int minimumYearsOfExperience) {
        jobs.put(jobId, new JobOpening(jobId, recruiterId, jobTitle, jobType,
                requiredSkills, minimumSkillMatches, minimumYearsOfExperience));
    }

    public void createOrUpdateCandidateProfile(String candidateId, List<String> skills, int yearsOfExperience) {
        CandidateProfile candidate = candidates.get(candidateId);
        if (candidate == null) {
            candidates.put(candidateId, new CandidateProfile(candidateId, skills, yearsOfExperience));
            return;
        }
        // Applications are stored inside the jobs, so they stay untouched
        candidate.skills = new HashSet<>(skills);
        candidate.years = yearsOfExperience;
    }

    public boolean applyForJob(String candidateId, String jobId) {
        JobOpening job = jobs.get(jobId);
        if (job == null || !candidates.containsKey(candidateId)) return false;
        // Set.add() returns false when the candidate has already applied
        return job.applicantIds.add(candidateId);
    }

    public List<String> getTopCandidates(String recruiterId, String jobId, int limit) {
        JobOpening job = jobs.get(jobId);
        if (job == null || !job.recruiterId.equals(recruiterId)) return new ArrayList<>();

        // Recount every applicant on every call
        List<MatchResult> eligible = new ArrayList<>();
        for (String candidateId : job.applicantIds) {
            CandidateProfile candidate = candidates.get(candidateId);
            int matches = job.countMatches(candidate.skills);
            if (job.isEligible(matches, candidate.years)) {
                eligible.add(new MatchResult(candidateId, matches, candidate.years));
            }
        }
        return topIds(eligible, limit);
    }

    public List<String> getTopEligibleJobs(String candidateId, int limit) {
        CandidateProfile candidate = candidates.get(candidateId);
        if (candidate == null) return new ArrayList<>();

        // Check every job in the system, even jobs that share no skill
        List<MatchResult> eligible = new ArrayList<>();
        for (JobOpening job : jobs.values()) {
            int matches = job.countMatches(candidate.skills);
            if (job.isEligible(matches, candidate.years)) {
                eligible.add(new MatchResult(job.jobId, matches, job.minYears));
            }
        }
        return topIds(eligible, limit);
    }

    /** Sorts the rows by the ranking rule and returns the first 'limit' ids. */
    List<String> topIds(List<MatchResult> rows, int limit) {
        Collections.sort(rows);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < limit; i++) {
            ids.add(rows.get(i).id);
        }
        return ids;
    }
}
```

## Solution 2: Skill Index and Sorted Applicants

We fix the two problems one at a time.

### Improvement 1: Skill index for `getTopEligibleJobs`

`minimumSkillMatches` is at least 1, so a job can only be eligible if it shares **at least one** skill with the candidate. That is why we keep a map from each skill to the ids of the jobs that need it.

To find jobs for a candidate, we walk only the lists of the candidate's own skills. Each time a job id shows up, we add 1 to its counter. At the end, every counter is exactly that job's matching skill count. Jobs that share no skill are never touched.

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

So instead of sorting on every read, each job keeps a `TreeSet<MatchResult>` (a sorted set) of its eligible applicants. `getTopCandidates` just reads the first `limit` rows.

- **On apply:** count matches. If the candidate is eligible, add a row to the job's sorted set.
- **On profile update:** remove the candidate's old row from every job they applied to, update the profile, then add a fresh row to each of those jobs where the candidate is eligible now.

This is exactly what Example 3 needs. `lee3` applies with only `android`, so no row is added. After the update adds `kotlin`, the refresh step adds a row and `lee3` shows up in the ranking.

Each candidate now keeps `appliedJobIds`, and the job no longer needs its own applicant set. This one set does two jobs: it catches repeat applications, and it tells us which rankings to refresh after a profile update.

**Why remove before updating?** To remove a row from a sorted set, we pass in a row with the same values. We rebuild those values from the candidate's current profile, so this must happen **before** the profile changes. That is why the order is always: remove old row, update profile, add new row.

**The trade-off:** a profile update now touches every job the candidate applied to. This is a good deal, because a candidate usually applies to a few jobs, while a popular job can have thousands of applicants and is viewed again and again.

### Data structures at a glance

```
jobs           : jobId       -> JobOpening
candidates     : candidateId -> CandidateProfile
jobIdsBySkill  : skill       -> ids of jobs that need this skill

JobOpening.rankedApplicants    : sorted set of eligible applicants, best first
CandidateProfile.appliedJobIds : ids of jobs this candidate applied to
```

### Code

```java
import java.util.*;

/**
 * A job opening posted by a recruiter.
 */
class JobOpening {
    String jobId;
    String recruiterId;
    String title;
    String type;              // full-time, internship ... only a label, it changes no rule
    Set<String> skills;       // distinct skills the job asks for
    int minSkillMatches;
    int minYears;

    // Only the applicants who are eligible right now, always kept in ranking order
    TreeSet<MatchResult> rankedApplicants = new TreeSet<>();

    JobOpening(String jobId, String recruiterId, String title, String type,
               List<String> skills, int minSkillMatches, int minYears) {
        this.jobId = jobId;
        this.recruiterId = recruiterId;
        this.title = title;
        this.type = type;
        this.skills = new HashSet<>(skills);
        this.minSkillMatches = minSkillMatches;
        this.minYears = minYears;
    }

    /** Number of this job's skills that the candidate also has. */
    int countMatches(Set<String> candidateSkills) {
        int matches = 0;
        for (String skill : skills) {
            if (candidateSkills.contains(skill)) matches++;
        }
        return matches;
    }

    boolean isEligible(int matches, int years) {
        return matches >= minSkillMatches && years >= minYears;
    }
}

/**
 * A candidate's skills, years of experience and the jobs they applied to.
 */
class CandidateProfile {
    String candidateId;
    Set<String> skills;       // a set makes "does the candidate have skill X?" an O(1) check
    int years;
    // Spots repeat applications and tells us which rankings to refresh after a profile update
    Set<String> appliedJobIds = new HashSet<>();

    CandidateProfile(String candidateId, List<String> skills, int years) {
        this.candidateId = candidateId;
        this.skills = new HashSet<>(skills);
        this.years = years;
    }
}

/**
 * One row of a ranking: a candidate for a job, or a job for a candidate.
 * Both rankings use the same rule: more matches, then more years, then smaller id.
 */
class MatchResult implements Comparable<MatchResult> {
    String id;
    int matches;
    int years;    // candidate's experience, or the job's minimum experience

    MatchResult(String id, int matches, int years) {
        this.id = id;
        this.matches = matches;
        this.years = years;
    }

    @Override
    public int compareTo(MatchResult other) {
        if (matches != other.matches) return Integer.compare(other.matches, matches); // more matches first
        if (years != other.years) return Integer.compare(other.years, years);         // more years first
        return id.compareTo(other.id);                                                // smaller id first
    }
}

public class JobPostingService {
    Map<String, JobOpening> jobs = new HashMap<>();
    Map<String, CandidateProfile> candidates = new HashMap<>();
    // skill -> ids of the jobs that ask for this skill
    Map<String, List<String>> jobIdsBySkill = new HashMap<>();

    public JobPostingService() {
    }

    public void postJobOpening(String recruiterId, String jobId, String jobTitle, String jobType,
                               List<String> requiredSkills, int minimumSkillMatches, int minimumYearsOfExperience) {
        JobOpening job = new JobOpening(jobId, recruiterId, jobTitle, jobType,
                requiredSkills, minimumSkillMatches, minimumYearsOfExperience);
        jobs.put(jobId, job);
        for (String skill : job.skills) {
            jobIdsBySkill.computeIfAbsent(skill, k -> new ArrayList<>()).add(jobId);
        }
    }

    public void createOrUpdateCandidateProfile(String candidateId, List<String> skills, int yearsOfExperience) {
        CandidateProfile candidate = candidates.get(candidateId);
        if (candidate == null) {
            candidates.put(candidateId, new CandidateProfile(candidateId, skills, yearsOfExperience));
            return;
        }
        // 1. Remove the old rows while the old skills and years are still known
        for (String jobId : candidate.appliedJobIds) {
            removeFromRanking(jobs.get(jobId), candidate);
        }
        // 2. Update the profile, applications stay as they are
        candidate.skills = new HashSet<>(skills);
        candidate.years = yearsOfExperience;
        // 3. Add fresh rows to the jobs where the candidate is eligible now
        for (String jobId : candidate.appliedJobIds) {
            addToRanking(jobs.get(jobId), candidate);
        }
    }

    public boolean applyForJob(String candidateId, String jobId) {
        CandidateProfile candidate = candidates.get(candidateId);
        JobOpening job = jobs.get(jobId);
        if (candidate == null || job == null) return false;
        // Set.add() returns false when the candidate has already applied
        if (!candidate.appliedJobIds.add(jobId)) return false;
        addToRanking(job, candidate);
        return true;
    }

    public List<String> getTopCandidates(String recruiterId, String jobId, int limit) {
        List<String> result = new ArrayList<>();
        JobOpening job = jobs.get(jobId);
        if (job == null || !job.recruiterId.equals(recruiterId)) return result;

        // Already sorted, so just read the first 'limit' rows
        for (MatchResult applicant : job.rankedApplicants) {
            if (result.size() == limit) break;
            result.add(applicant.id);
        }
        return result;
    }

    public List<String> getTopEligibleJobs(String candidateId, int limit) {
        List<String> result = new ArrayList<>();
        CandidateProfile candidate = candidates.get(candidateId);
        if (candidate == null) return result;

        // Count shared skills using the index, jobs with no shared skill are never touched
        Map<String, Integer> matchCount = new HashMap<>();
        for (String skill : candidate.skills) {
            for (String jobId : jobIdsBySkill.getOrDefault(skill, new ArrayList<>())) {
                matchCount.put(jobId, matchCount.getOrDefault(jobId, 0) + 1);
            }
        }

        List<MatchResult> eligible = new ArrayList<>();
        for (String jobId : matchCount.keySet()) {
            JobOpening job = jobs.get(jobId);
            int matches = matchCount.get(jobId);
            if (job.isEligible(matches, candidate.years)) {
                eligible.add(new MatchResult(jobId, matches, job.minYears));
            }
        }
        Collections.sort(eligible);

        for (int i = 0; i < eligible.size() && i < limit; i++) {
            result.add(eligible.get(i).id);
        }
        return result;
    }

    /** Adds the candidate to the job's ranking, only if they are eligible. */
    void addToRanking(JobOpening job, CandidateProfile candidate) {
        int matches = job.countMatches(candidate.skills);
        if (job.isEligible(matches, candidate.years)) {
            job.rankedApplicants.add(new MatchResult(candidate.candidateId, matches, candidate.years));
        }
    }

    /** Removes the candidate's row from the job's ranking. Does nothing if they were not eligible. */
    void removeFromRanking(JobOpening job, CandidateProfile candidate) {
        int matches = job.countMatches(candidate.skills);
        // A sorted set finds a row by comparing values, so a row with the same values removes the old one
        job.rankedApplicants.remove(new MatchResult(candidate.candidateId, matches, candidate.years));
    }
}
```

## Complexity

`S` = skills in a job, `C` = skills of a candidate, `N` = applicants of a job, `K` = jobs a candidate applied to, `J` = all jobs, `E` = eligible jobs found, `P` = job ids visited through the skill index.

| Method | Solution 1 | Solution 2 |
|---|---|---|
| `postJobOpening` | O(S) | O(S) |
| `createOrUpdateCandidateProfile` | O(C) | O(C + K × (S + log N)) |
| `applyForJob` | O(1) | O(S + log N) |
| `getTopCandidates` | O(N × S + N log N) | O(log N + limit) |
| `getTopEligibleJobs` | O(J × S + E log E) | O(P + E log E) |

Solution 2 makes both queries much cheaper and pays a little more when a candidate applies or updates their profile.