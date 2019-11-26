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
package btree4j.utils.collections;

import java.io.Serializable;

/**
 * Stack implementation for primitive integer.
 */
public final class IntStack implements Serializable, Cloneable {
    private static final long serialVersionUID = 1717535876231216483L;

    public static final int DEFAULT_CAPACITY = 12;

    private int size;
    private int[] data;

    public IntStack() {
        this(DEFAULT_CAPACITY);
    }

    public IntStack(int initSize) {
        this.data = new int[initSize];
    }

    private IntStack(int[] data) {
        this.data = data;
        this.size = data.length;
    }

    public void push(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    public int pop() {
        return data[--size];
    }

    public int peek() {
        return data[size - 1];
    }

    public int elementAt(int depth) {
        return data[depth];
    }

    public void clear() {
        size = 0;
    }

    public boolean isEmpty() {
        return (size == 0);
    }

    public int size() {
        return size;
    }

    private void ensureCapacity(int size) {
        while (data.length < size) {
            final int len = data.length;
            int[] newArray = new int[len * 2];
            System.arraycopy(data, 0, newArray, 0, len);
            this.data = newArray;
        }
    }

    public int[] toArray() {
        final int[] newArray = new int[size];
        System.arraycopy(data, 0, newArray, 0, size);
        return newArray;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append('[');
        for (int i = 0; i < size; i++) {
            if (i != 0) {
                buf.append(", ");
            }
            buf.append(data[i]);
        }
        buf.append(']');
        return buf.toString();
    }

    @Override
    public IntStack clone() {
        IntStack cloned = new IntStack(data.clone());
        cloned.size = this.size;
        return cloned;
    }

}
