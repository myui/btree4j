/*
 * Copyright (c) 2006 and onwards Makoto Yui
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package btree4j.utils.lang;

import javax.annotation.Nonnegative;

public final class HashUtils {

    private HashUtils() {}

    public static boolean isPowerOfTwo(final int x) {
        if (x < 1) {
            return false;
        }
        return (x & (x - 1)) == 0;
    }

    public static boolean isPowerOfTwo(final long x) {
        if (x < 1) {
            return false;
        }
        return (x & (x - 1)) == 0;
    }

    public static int nextPowerOfTwo(final int targetSize) {
        return nextPowerOfTwo(1, targetSize);
    }

    public static int nextPowerOfTwo(final int minSizeLog, final int targetSize) {
        int i;
        for (i = minSizeLog; (1 << i) < targetSize; i++);
        return 1 << i;
    }

    public static int shiftsForNextPowerOfTwo(final int targetSize) {
        int i;
        for (i = 0; (1 << i) < targetSize; i++);
        return i;
    }

    public static int hashCode(final long l) {
        return (int) (l ^ (l >>> 32));
    }

    public static int hashCode(final char[] ch, final int offset, final int length) {
        if (ch == null) {
            return 0;
        }
        int result = 1;
        final int limit = offset + length;
        for (int i = offset; i < limit; i++) {
            result = 31 * result + ch[i];
        }
        return result;
    }

    public static int hashCode(final byte[] b) {
        return hashCode(b, 0, b.length);
    }

    public static int hashCode(final byte[] b, final int offset, final int length) {
        if (b == null) {
            return 0;
        }
        int result = 1;
        final int limit = offset + length;
        for (int i = offset; i < limit; i++) {
            result = 31 * result + b[i];
        }
        return result;
    }

    /**
     * Returns a hash index for the given thread. Uses a one-step FNV-1a hash code
     * (http://www.isthe.com/chongo/tech/comp/fnv/) based on the given thread's Thread.getId().
     * These hash codes have more uniform distribution properties with respect to small moduli (here
     * 1-31) than do other simple hashing functions.
     *
     * <p>
     * To return an index between 0 and max, we use a cheap approximation to a mod operation, that
     * also corrects for bias due to non-power-of-2 remaindering (see
     * {@link java.util.Random#nextInt}). Bits of the hashcode are masked with "nbits", the ceiling
     * power of two of table size (looked up in a table packed into three ints). If too large, this
     * is retried after rotating the hash by nbits bits, while forcing new top bit to 0, which
     * guarantees eventual termination (although with a non-random-bias). This requires an average
     * of less than 2 tries for all table sizes, and has a maximum 2% difference from perfectly
     * uniform slot probabilities when applied to all possible hash codes for sizes less than 32.
     *
     * @return a per-thread-random index, <code>0 &lt;= index &lt;= max</code>
     */
    public static final int hash(final Thread thrd, final int max) {
        final long id = thrd.getId();
        int hash = (((int) (id ^ (id >>> 32))) ^ 0x811c9dc5) * 0x01000193;

        final int nbits = (((0xfffffc00 >> max) & 4) | // Compute ceil(log2(m+1))
                ((0x000001f8 >>> max) & 2) | // The constants hold
                ((0xffff00f2 >>> max) & 1)); // a lookup table
        int index;
        while ((index = hash & ((1 << nbits) - 1)) > max) {// May retry on
            hash = (hash >>> nbits) | (hash << (33 - nbits)); // non-power-2 m
        }
        return index;
    }

    // Hash spreader
    public static final int hash(final Thread thrd) {
        int h = System.identityHashCode(thrd);
        // You would think that System.identityHashCode on the current thread
        // would be a good hash fcn, but actually on SunOS 5.8 it is pretty lousy
        // in the low bits.
        h ^= (h >>> 20) ^ (h >>> 12); // Bit spreader, borrowed from Doug Lea
        h ^= (h >>> 7) ^ (h >>> 4);
        return h << 2; // Pad out cache lines.  The goal is to avoid cache-line contention
    }

    public static final int xorFolding32(final long hash) {
        return ((int) (hash >>> 32)) ^ ((int) hash);
    }

    public static long xorFolding32(final long hash, final int shift) {
        final long mask = (1L << shift) - 1L;
        return (hash >> shift) ^ (hash & mask);
    }

    public static long xorFolding(final long hash, final int shift) {
        final long mask = (1L << shift) - 1L;
        if (shift < 16) {
            return ((hash >> shift) ^ hash) & mask;
        } else {
            return (hash >> shift) ^ (hash & mask);
        }
    }

    @Nonnegative
    public static int positiveXorFolding(final int hash, final int shift) {
        if (shift > 31) {
            throw new IllegalArgumentException("Illegal shift for 32-bits value: " + shift);
        }
        final int mask = (1 << shift) - 1;
        if (shift < 16) {
            return ((hash >> shift) ^ hash) & mask;
        } else {
            return (hash >>> shift) ^ (hash & mask);
        }
    }

}
