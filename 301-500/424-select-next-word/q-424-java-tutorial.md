# Select Next Word in Java

#### Problem Statement

[https://codezym.com/question/424-select-next-word](https://codezym.com/question/424-select-next-word)

First keep only the words that agree with every earlier score. For each remaining word, group the possible targets by the score they would produce. Choose the word whose largest group is smallest, breaking ties alphabetically. This is called **minimax**, making the worst possible outcome as small as possible.


## 1. Keep Only Possible Targets

The helper `matchScore(first, second)` counts equal characters at the same positions. Every word has six characters, so this takes constant time.

For each playable word, compare its score against every previous attempt. Reject it as soon as one score disagrees with the recorded result.

Store the words that pass in `candidates`. This list contains both the possible targets and the allowed next attempts. With no history, every playable word passes.

The problem guarantees that at least one candidate remains, so we can use the first candidate as the initial answer.

A previously attempted word is rejected automatically. Its score against itself is 6, while every recorded score is below 6.


## 2. Simple Solution: Simulate Every Possible Target

For each candidate `guess`, pretend each candidate is the hidden target. Calculate its feedback, then scan the candidates again to count how many words would survive that feedback.

The largest survivor count is the risk of `guess`. Here is the calculation for one guess:

```java
int risk = 0;
for (String target : candidates) {
    int feedback = matchScore(guess, target);
    int remaining = 0;
    for (String word : candidates) {
        if (matchScore(guess, word) == feedback) {
            remaining++;
        }
    }
    risk = Math.max(risk, remaining);
}
```

If `k` candidates remain, doing this for all `k` guesses takes **O(k³)** time. Different targets can produce the same feedback, so we repeatedly count the same group.


## 3. Improve It: Count Each Score Group Once

There are only seven possible scores, from 0 through 6. For each guess, create a fresh integer array called `scoreCounts` with seven entries.

`scoreCounts[s]` stores how many candidate targets produce score `s`. Compare the guess with each target once, increase the matching counter, and track the largest counter as `risk`.

Include the guess itself. It contributes one target to score 6, which is part of the grouping required by the problem.

Replace the best answer when the risk is smaller. If the risks are equal, choose the alphabetically smaller word using `compareTo`. This makes the result independent of the input order.


### Walk Through an Example

Suppose the words are `["stream", "bright", "flight", "planet", "friend"]`, with no previous attempts.

For the guess `"bright"`, the scores against these words are `[0, 6, 4, 1, 2]`. Every occupied group has one target, so its risk is **1**.

For `"stream"`, the scores are `[6, 0, 0, 0, 1]`. Three targets fall into score 0, so its risk is **3**.

No guess can have a risk below 1. Since `"bright"` reaches that value and is also the alphabetically smallest word in this list, the answer is `"bright"`.


### Why This Works

Filtering keeps exactly the words that satisfy every previous result. For a fixed guess, each score group contains exactly the targets that would survive that feedback, so the largest group gives its true worst-case risk.

We evaluate every allowed guess and select the smallest risk. The explicit word comparison also gives the required answer when risks tie.


### Complexity

Let `n` be the number of playable words, `h` the number of previous attempts, and `k` the number of remaining candidates.

Filtering takes **O(n + n × h)** time, including the scan through the words when the history is empty. Evaluating all guesses takes **O(k²)** time because each score comparison checks just six positions. Total time is **O(n + n × h + k²)**.

Extra space is **O(k)** for the candidate list. Only seven counters are needed at a time. This fits the limit of 100 playable words comfortably.


## Java Solution

```java
import java.util.ArrayList;
import java.util.List;

public class WordMatchStrategy {

    public WordMatchStrategy() {
    }

    public String selectNextWord(
            List<String> playableWords,
            List<String> previousAttempts,
            List<Integer> receivedScores) {

        List<String> candidates = new ArrayList<>();

        // A possible target must agree with every earlier result.
        for (String word : playableWords) {
            boolean possible = true;
            for (int i = 0; i < previousAttempts.size(); i++) {
                if (matchScore(word, previousAttempts.get(i)) != receivedScores.get(i)) {
                    possible = false;
                    break;
                }
            }
            if (possible) {
                candidates.add(word);
            }
        }

        String bestWord = candidates.get(0);
        int bestRisk = candidates.size() + 1;

        for (String guess : candidates) {
            // Include all seven scores, including an exact match with the guess.
            int[] scoreCounts = new int[7];
            int risk = 0;

            for (String target : candidates) {
                int score = matchScore(guess, target);
                scoreCounts[score]++;
                risk = Math.max(risk, scoreCounts[score]);
            }

            // Compare words explicitly, so input order does not affect a tie.
            if (risk < bestRisk
                    || (risk == bestRisk && guess.compareTo(bestWord) < 0)) {
                bestWord = guess;
                bestRisk = risk;
            }
        }

        return bestWord;
    }

    public int matchScore(String first, String second) {
        int score = 0;
        for (int i = 0; i < 6; i++) {
            if (first.charAt(i) == second.charAt(i)) {
                score++;
            }
        }
        return score;
    }
}
```
