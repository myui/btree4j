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
package btree4j.utils.collections.longs;

import java.io.Serializable;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Dynamic Integer array.
 */
public final class LongArrayList implements Serializable {
    private static final long serialVersionUID = -3522471296182738673L;

    public static final int DEFAULT_CAPACITY = 12;

    /** array entity */
    @Nonnull
    private long[] data;
    private int used;

    public LongArrayList() {
        this(DEFAULT_CAPACITY);
    }

    public LongArrayList(int size) {
        this.data = new long[size];
        this.used = 0;
    }

    public LongArrayList(@CheckForNull long[] initValues, int used) {
        this.data = Objects.requireNonNull(initValues);
        this.used = used;
    }

    public void add(long value) {
        if (used >= data.length) {
            expand(used + 1);
        }
        data[used++] = value;
    }

    public void add(long[] values) {
        final int needs = used + values.length;
        if (needs >= data.length) {
            expand(needs);
        }
        System.arraycopy(values, 0, data, used, values.length);
        this.used = needs;
    }

    /**
     * dynamic expansion.
     */
    private void expand(final int minimumCapacity) {
        while (data.length < minimumCapacity) {
            int oldLen = data.length;
            int newLen = (int) Math.max(minimumCapacity, Math.min(oldLen * 2L, Integer.MAX_VALUE));
            long[] newArray = new long[newLen];
            System.arraycopy(data, 0, newArray, 0, oldLen);
            this.data = newArray;
        }
    }

    public long remove() {
        if (used == 0) {
            throw new NoSuchElementException("No elements to remove");
        }
        return data[--used];
    }

    public long remove(int index) {
        if (index >= used) {
            throw new IndexOutOfBoundsException();
        }

        final long ret;
        if (index == used) {
            ret = data[index];
            --used;
        } else { // index < used
            ret = data[index];
            System.arraycopy(data, index + 1, data, index, used - index - 1);
            --used;
        }
        return ret;
    }

    public void set(int index, long value) {
        if (index > used) {
            throw new IllegalArgumentException("Index MUST be less than \"size()\".");
        } else if (index == used) {
            ++used;
        }
        data[index] = value;
    }

    public long get(int index) {
        if (index >= used) {
            throw new IndexOutOfBoundsException();
        }
        return data[index];
    }

    public long indexOf(int key) {
        return Arrays.binarySearch(data, key);
    }

    public int size() {
        return used;
    }

    public void clear() {
        used = 0;
    }

    public long[] toArray() {
        final long[] newArray = new long[used];
        System.arraycopy(data, 0, newArray, 0, used);
        return newArray;
    }

    public long[] array() {
        return data;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof LongArrayList) {
            LongArrayList other = (LongArrayList) obj;
            if (other.size() != used) {
                return false;
            }
            return Arrays.equals(toArray(), other.toArray());
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append('[');
        for (int i = 0; i < used; i++) {
            if (i != 0) {
                buf.append(", ");
            }
            buf.append(data[i]);
        }
        buf.append(']');
        return buf.toString();
    }
}
