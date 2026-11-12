# Value at Index After Binary String Transformation in Java

#### Problem Statement

[https://codezym.com/question/444-value-at-index-after-transformation](https://codezym.com/question/444-value-at-index-after-transformation)

Each transformation keeps a bit and adds a zero after it. After `n` transformations, every original bit occupies a block of `2^n` positions. The first position keeps the original bit, and every other position is zero. We can use this observation to find the answer directly, without building the transformed string.


## 1. Start with a simple simulation

Build a new string for each transformation. Append the current bit followed by `'0'`, which handles both replacement rules.

This snippet shows the straightforward approach inside `getBit`.

```java
String current = binaryString;

for (int step = 0; step < n; step++) {
    StringBuilder next = new StringBuilder();

    for (int i = 0; i < current.length(); i++) {
        next.append(current.charAt(i));
        next.append('0');
    }

    current = next.toString();
}

return current.charAt(index) - '0';
```

`StringBuilder` lets us append characters without repeatedly copying the growing string.


However, the length doubles each time. If the original length is `m`, the final length is `m * 2^n`. For `n >= 1`, this simulation uses `O(m * 2^n)` time and extra space. With `n = 0`, it simply reads the original string in `O(1)` time and extra space.


Even a single original bit produces over a billion characters when `n = 30`. Building all those characters is unnecessary when we only need one.


## 2. Use the blocks instead

Watch what happens to one original `'1'`.

- Before any transformation: `1`
- After one transformation: `10`
- After two transformations: `1000`
- After three transformations: `10000000`

An original `'0'` follows the same growth, but its entire block contains zeros.


Let `blockSize = 2^n`. Two calculations locate the requested position.

- `index / blockSize` gives the original bit that owns the block, using integer division.
- `index % blockSize` gives the position inside that block.

If the remainder is not zero, return `0`. Otherwise, return the original bit at `index / blockSize`.


For example, with `binaryString = "101"` and `n = 2`, the three blocks are `1000`, `0000`, and `1000`.

At index `8`, the remainder is `0` and the original index is `2`, so the answer is `1`. At index `5`, the remainder is `1`, so the answer is `0`.


When `n = 0`, the block size is `1`. Every index is a block start, so the same logic reads the original string directly.


## 3. Why this works

Initially, each original bit forms a one-character block. Suppose a block starts with its original bit and contains only zeros afterward.


The next transformation keeps that first bit and inserts zeros after every character. The block doubles in size, and every position after the first remains zero. Repeating this argument gives blocks of size `2^n` with exactly the structure our calculation uses.


## 4. Complexity and implementation

The solution takes `O(1)` time and `O(1)` extra space under the given constraints. It performs a few integer operations and reads at most one character.


`1 << n` computes the integer value `2^n`. Since `n <= 30`, the block size fits in a Java `int`. We never calculate or store the full transformed length.


`BinaryStringTransformer` only needs the required `getBit` method. Each call uses its own inputs, so no fields or extra collections are needed. The index is guaranteed to be valid.


## Final code

```java
public class BinaryStringTransformer {

    /**
     * Returns the requested bit without building the transformed string.
     */
    public int getBit(String binaryString, int n, int index) {
        // Each original bit occupies 2^n positions.
        int blockSize = 1 << n;

        // Every position after the start of a block is zero.
        if (index % blockSize != 0) {
            return 0;
        }

        int originalIndex = index / blockSize;
        return binaryString.charAt(originalIndex) - '0';
    }
}
```
