# Next Larger Palindrome Using Same Digits in Java

#### Problem Statement
[https://codezym.com/question/201-next-larger-palindrome-same-digits](https://codezym.com/question/201-next-larger-palindrome-same-digits)


We are given a palindrome, and we need the smallest palindrome that is bigger than it and uses exactly the same digits.

The key insight is that a palindrome is fully decided by its left half. The right half is just a mirror image, and the middle digit (for odd lengths) can never move. So we only need to rearrange the left half.

A bigger left half always gives a bigger palindrome. So the answer is simply the next bigger arrangement of the left half, known as the **next permutation**. We find it in one pass, mirror it, and we are done in O(n) time.

We will start with a brute force solution and improve it step by step.

---

## Brute Force: Try Every Arrangement

The most direct idea is to try every arrangement of all the digits. We keep the ones that are palindromes and bigger than `num`, and return the smallest.

```java
String best = null;

// allArrangements() returns every ordering of the digits in num
for (String candidate : allArrangements(num)) {
    boolean isBigger = candidate.compareTo(num) > 0;
    if (isPalindrome(candidate) && isBigger) {
        if (best == null || candidate.compareTo(best) < 0) {
            best = candidate;
        }
    }
}
return best == null ? "" : best;
```

Every arrangement has the same length as `num`. So comparing them as strings is the same as comparing them as numbers. This is why we never need to convert the huge input into a real number.

The problem is speed. `n` digits can be ordered in `n!` ways. Just 15 digits already give over a trillion orderings, and `n` can be 100,000.

Also, most of those orderings are not even palindromes.

---

## Improvement 1: Only Rearrange the Left Half

Let's split `"23532"` into its parts.

```
"23532"  =  "23"  +  "5"  +  "32"
            left    middle   right (left reversed)
```

The right half is always the left half reversed. So once we pick the left half, the whole palindrome is fixed.

The middle digit can never change. Every digit in the left half has a twin in the right half, so the middle has to be the one digit without a twin.

For the same reason, the left half always holds the same digits. Only their order can change.

The left half also holds the leading digits. So a bigger left half means a bigger palindrome. For example, `"23"` gives `23532`, while `"32"` gives `32523`.

So the problem becomes:

> Find the smallest arrangement of the left half that is bigger than the current one.

This cuts the work a lot, but it is still too slow. The left half can have 50,000 digits, and the number of orderings still grows like a factorial.

Also, we still build every arrangement, while we only need the one that comes right after the current one.

---

## Improvement 2: Next Permutation (Optimal)

Next permutation finds the very next bigger arrangement directly, in one pass. No other arrangement is generated.

The idea: to grow a number by the smallest amount, change digits as far to the right as possible.

Let's walk through `num = "1354224531"`. Its left half is `"13542"`.

### Step 1: Find the Pivot

Scan from the right end. Stop at the first digit that is smaller than the digit right after it. This digit is the pivot.

```
1 3 5 4 2
  ^
pivot = 3, because 3 < 5
```

Everything after the pivot, `"542"`, is in descending order. That part is already as big as it can be, so to grow we must make the pivot bigger.

If there is no pivot, the whole left half is in descending order, like `"987"` in `"987789"`. It is already the biggest arrangement, so we return `""`.

This also covers a single digit like `"7"`, whose left half is empty.

### Step 2: Find the Digit to Swap With

Scan from the right end again. Stop at the first digit that is bigger than the pivot.

Since the part after the pivot is descending, this is the smallest digit that is still bigger than the pivot.

```
1 3 5 4 2
  ^   ^
  3   4
```

### Step 3: Swap Them

```
1 4 5 3 2
```

The pivot went up by the smallest possible amount.

### Step 4: Reverse Everything After the Pivot

The part after the pivot, `"532"`, is still descending. Reversing it makes it ascending, which is its smallest possible order.

```
1 4 2 3 5
```

We reverse instead of sort because this part is already descending. Reversing takes just one pass.

### Build the Answer

The new left half is `"14235"`. We add the middle digit (only for odd lengths) and then the left half reversed.

```
"14235" + "53241" = "1423553241"
```

---

## Complexity

- **Time:** O(n). Every step makes at most one pass over the left half, and building the answer is one more pass.
- **Space:** O(n) for the left half array and the answer.

---

## Java Code

Two choices matter here:

- **`char[]` for the left half:** Java strings cannot be changed. A char array lets us swap and reverse digits in place.
- **`StringBuilder` for the answer:** it builds the 100,000 character answer efficiently, instead of creating a new string on every append.

```java
public class NextPalindromeUsingSameDigits {

    public NextPalindromeUsingSameDigits() {
    }

    /**
     * Returns the smallest palindrome bigger than num that uses the same digits.
     * Returns "" if no such palindrome exists.
     */
    public String nextPalindrome(String num) {
        int n = num.length();
        int half = n / 2;

        // The left half decides the whole palindrome
        char[] left = num.substring(0, half).toCharArray();

        // Left half is already in its biggest order, so no bigger palindrome exists
        if (!nextPermutation(left)) {
            return "";
        }

        StringBuilder result = new StringBuilder(n);
        result.append(left);

        // The middle digit (odd length only) never moves
        if (n % 2 == 1) {
            result.append(num.charAt(half));
        }

        // The right half is the left half in reverse
        for (int i = half - 1; i >= 0; i--) {
            result.append(left[i]);
        }

        return result.toString();
    }

    /**
     * Rearranges digits into the next bigger order, in place.
     * Returns false if digits are already in their biggest order.
     */
    boolean nextPermutation(char[] digits) {
        // Step 1: from the right, find the pivot (first digit smaller than the digit after it)
        int i = digits.length - 2;
        while (i >= 0 && digits[i] >= digits[i + 1]) {
            i--;
        }

        // No pivot: digits are in descending order
        if (i < 0) {
            return false;
        }

        // Step 2: from the right, find the first digit bigger than the pivot
        int j = digits.length - 1;
        while (digits[j] <= digits[i]) {
            j--;
        }

        // Step 3: swap, so the pivot grows by the smallest possible amount
        swap(digits, i, j);

        // Step 4: reverse the descending part after the pivot to make it as small as possible
        reverse(digits, i + 1, digits.length - 1);
        return true;
    }

    /** Swaps the digits at positions a and b. */
    void swap(char[] digits, int a, int b) {
        char temp = digits[a];
        digits[a] = digits[b];
        digits[b] = temp;
    }

    /** Reverses digits between start and end, both inclusive. */
    void reverse(char[] digits, int start, int end) {
        while (start < end) {
            swap(digits, start, end);
            start++;
            end--;
        }
    }
}
```