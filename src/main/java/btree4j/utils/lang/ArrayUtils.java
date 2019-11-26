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
/*
 * Copyright 2002-2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package btree4j.utils.lang;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Random;

public final class ArrayUtils {

    /**
     * The index value when an element is not found in a list or array: <code>-1</code>. This value
     * is returned by methods in this class and can also be used in comparisons with values returned
     * by various method from {@link java.util.List}.
     */
    public static final int INDEX_NOT_FOUND = -1;

    private ArrayUtils() {}

    /**
     * <p>
     * Returns the length of the specified array. This method can deal with <code>Object</code>
     * arrays and with primitive arrays.
     * </p>
     *
     * <p>
     * If the input array is <code>null</code>, <code>0</code> is returned.
     * </p>
     *
     * <pre>
     * ArrayUtils.getLength(null)            = 0
     * ArrayUtils.getLength([])              = 0
     * ArrayUtils.getLength([null])          = 1
     * ArrayUtils.getLength([true, false])   = 2
     * ArrayUtils.getLength([1, 2, 3])       = 3
     * ArrayUtils.getLength(["a", "b", "c"]) = 3
     * </pre>
     *
     * @param array the array to retrieve the length from, may be null
     * @return The length of the array, or <code>0</code> if the array is <code>null</code>
     * @throws IllegalArgumentException if the object arguement is not an array.
     */
    public static int getLength(final Object array) {
        if (array == null) {
            return 0;
        } else {
            return Array.getLength(array);
        }
    }

    public static <T> int indexOf(final T[] array, final T value) {
        final int alen = array.length;
        for (int i = 0; i < alen; i++) {
            if (value.equals(array[i])) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    public static <T> int indexOf(final T[] array, final T value, final int startIndex) {
        final int alen = array.length;
        for (int i = startIndex; i < alen; i++) {
            if (value.equals(array[i])) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    public static int indexOf(final int[] array, final int value) {
        final int alen = array.length;
        for (int i = 0; i < alen; i++) {
            if (value == array[i]) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    public static int indexOf(final int[] array, final int valueToFind, int startIndex,
            int endIndex) {
        if (array == null) {
            return INDEX_NOT_FOUND;
        }
        final int til = Math.min(endIndex, array.length);
        if (startIndex < 0 || startIndex > til) {
            throw new IllegalArgumentException("Illegal startIndex: " + startIndex);
        }
        for (int i = startIndex; i < til; i++) {
            if (valueToFind == array[i]) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    public static int indexOf(final byte[] array, final byte valueToFind, int startIndex) {
        if (array == null) {
            return INDEX_NOT_FOUND;
        }
        if (startIndex < 0) {
            startIndex = 0;
        }
        for (int i = startIndex; i < array.length; i++) {
            if (valueToFind == array[i]) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    /**
     * Returns the last index of the given array or -1 if empty or null. This method can deal with
     * <code>Object</code> arrays and with primitive arrays. This value is one less than the size
     * since arrays indices are 0-based.
     * </p>
     *
     * <pre>
     * ArrayUtils.lastIndex(null)            = -1
     * ArrayUtils.lastIndex([])              = -1
     * ArrayUtils.lastIndex([null])          = 0
     * ArrayUtils.lastIndex([true, false])   = 1
     * ArrayUtils.lastIndex([1, 2, 3])       = 2
     * ArrayUtils.lastIndex(["a", "b", "c"]) = 2
     * </pre>
     * 
     * @param array the array to return the last index for, may be null
     * @return the last index, -1 if empty or null
     * @throws IllegalArgumentException if the object arguement is not an array.
     */
    public static int lastIndex(final Object array) {
        return ArrayUtils.getLength(array) - 1;
    }

    /**
     * <p>
     * Inserts the specified element at the specified position in the array. Shifts the element
     * currently at that position (if any) and any subsequent elements to the right (adds one to
     * their indices).
     * </p>
     *
     * <p>
     * This method returns a new array with the same elements of the input array plus the given
     * element on the specified position. The component type of the returned array is always the
     * same as that of the input array.
     * </p>
     *
     * <p>
     * If the input array is <code>null</code>, a new one element array is returned whose component
     * type is the same as the element.
     * </p>
     * 
     * <pre>
     * ArrayUtils.insert(null, 0, null)      = [null]
     * ArrayUtils.insert(null, 0, "a")       = ["a"]
     * ArrayUtils.insert(["a"], 1, null)     = ["a", null]
     * ArrayUtils.insert(["a"], 1, "b")      = ["a", "b"]
     * ArrayUtils.insert(["a", "b"], 3, "c") = ["a", "b", "c"]
     * </pre>
     * 
     * @param array the array to add the element to, may be <code>null</code>
     * @param index the position of the new object
     * @param element the object to add
     * @return A new array containing the existing elements and the new element
     * @throws IndexOutOfBoundsException if the index is out of range (index < 0 || index >
     *         array.length).
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] insert(final Object array, final int index, final Object element) {
        if (array == null) {
            if (index != 0) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Length: 0");
            }
            Object joinedArray =
                    Array.newInstance(element != null ? element.getClass() : Object.class, 1);
            Array.set(joinedArray, 0, element);
            return (T[]) joinedArray;
        }
        int length = getLength(array);
        if (index > length || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
        }
        Object result = Array.newInstance(array.getClass().getComponentType(), length + 1);
        System.arraycopy(array, 0, result, 0, index);
        Array.set(result, index, element);
        if (index < length) {
            System.arraycopy(array, index, result, index + 1, length - index);
        }
        return (T[]) result;
    }

    public static long[] insert(final long[] array, final long element) {
        long[] newArray = (long[]) copyArrayGrow1(array, Long.TYPE);
        newArray[lastIndex(newArray)] = element;
        return newArray;
    }

    public static long[] insert(final long[] vals, final int idx, final long val) {
        long[] newVals = new long[vals.length + 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        newVals[idx] = val;
        if (idx < vals.length) {
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        }
        return newVals;
    }

    public static byte[][] insert(final byte[][] vals, final int idx, final byte[] val) {
        byte[][] newVals = new byte[vals.length + 1][];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        newVals[idx] = val;
        if (idx < vals.length) {
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        }
        return newVals;
    }

    public static byte[] append(final byte[] b1, final byte[] b2) {
        final byte[] nb = new byte[b1.length + b2.length];
        if (b1.length > 0) {
            System.arraycopy(b1, 0, nb, 0, b1.length);
        }
        if (b2.length > 0) {
            System.arraycopy(b2, 0, nb, b1.length, b2.length);
        }
        return nb;
    }

    public static int[] append(final int[] left, final int[] right) {
        final int[] n = new int[left.length + right.length];
        if (left.length > 0) {
            System.arraycopy(left, 0, n, 0, left.length);
        }
        if (right.length > 0) {
            System.arraycopy(right, 0, n, left.length, right.length);
        }
        return n;
    }

    /**
     * <p>
     * Removes the element at the specified position from the specified array. All subsequent
     * elements are shifted to the left (substracts one from their indices).
     * </p>
     *
     * <p>
     * This method returns a new array with the same elements of the input array except the element
     * on the specified position. The component type of the returned array is always the same as
     * that of the input array.
     * </p>
     *
     * <p>
     * If the input array is <code>null</code>, an IndexOutOfBoundsException will be thrown, because
     * in that case no valid index can be specified.
     * </p>
     * 
     * @param array the array to remove the element from, may not be <code>null</code>
     * @param index the position of the element to be removed
     * @return A new array containing the existing elements except the element at the specified
     *         position.
     * @throws IndexOutOfBoundsException if the index is out of range (index < 0 || index >=
     *         array.length), or if the array is <code>null</code>.
     * @since 2.1
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] remove(final T[] array, final int index) {
        int length = getLength(array);
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
        }
        Object result = Array.newInstance(array.getClass().getComponentType(), length - 1);
        System.arraycopy(array, 0, result, 0, index);
        if (index < length - 1) {
            System.arraycopy(array, index + 1, result, index, length - index - 1);
        }
        return (T[]) result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] remove(final T[] array, final int from, final int to) {
        assert (to >= from) : to + " - " + from;
        int length = getLength(array);
        if (from < 0 || to >= length) {
            throw new IndexOutOfBoundsException(
                "from: " + from + ", to: " + to + ", Length: " + length);
        }
        int remsize = to - from + 1;
        Object result = Array.newInstance(array.getClass().getComponentType(), length - remsize);
        System.arraycopy(array, 0, result, 0, from);
        if (to < length - 1) {
            System.arraycopy(array, to + 1, result, from, length - to - 1);
        }
        return (T[]) result;
    }

    public static long[] remove(final long[] vals, final int idx) {
        long[] newVals = new long[vals.length - 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        if (idx < newVals.length) {
            System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
        }
        return newVals;
    }

    public static long[] remove(final long[] vals, final int from, final int to) {
        int remsize = to - from + 1;
        long[] newVals = new long[vals.length - remsize];
        if (from > 0) {
            System.arraycopy(vals, 0, newVals, 0, from);
        }
        if (to < newVals.length) {
            System.arraycopy(vals, to + 1, newVals, from, newVals.length - to);
        }
        return newVals;
    }

    /**
     * Returns a copy of the given array of size 1 greater than the argument. The last value of the
     * array is left to the default value.
     * 
     * @param array The array to copy, must not be <code>null</code>.
     * @param newArrayComponentType If <code>array</code> is <code>null</code>, create a size 1
     *        array of this type.
     * @return A new copy of the array of size 1 greater than the input.
     */
    private static Object copyArrayGrow1(final Object array, final Class<?> newArrayComponentType) {
        if (array != null) {
            int arrayLength = Array.getLength(array);
            Object newArray =
                    Array.newInstance(array.getClass().getComponentType(), arrayLength + 1);
            System.arraycopy(array, 0, newArray, 0, arrayLength);
            return newArray;
        } else {
            return Array.newInstance(newArrayComponentType, 1);
        }
    }

    public static boolean equals(final char[] a, final char[] a2, final int off, final int len) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        int length = a.length;
        if (len != length) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (a[i] != a2[off + i]) {
                return false;
            }
        }
        return true;
    }

    public static <T extends Comparable<T>> int binarySearch(final T[] a, final int fromIndex,
            final int toIndex, final T key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = a[mid];
            int cmp = midVal.compareTo(key);

            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return -(low + 1); // key not found.
    }

    /**
     * Reverses the order of the given array.
     */
    public static void reverse(final int[] ary) {
        if (ary == null) {
            return;
        }
        int i = 0;
        int j = ary.length - 1;
        int tmp;
        while (j > i) {
            tmp = ary[j];
            ary[j] = ary[i];
            ary[i] = tmp;
            j--;
            i++;
        }
    }

    public static int[] reverseTo(final int[] ary) {
        if (ary == null) {
            return null;
        }
        int[] b = new int[ary.length];
        for (int i = 0; i < ary.length; i++) {
            b[ary.length - i - 1] = ary[i];
        }
        return b;
    }

    public static Object resize(final Object[] ary, final int length) {
        final Object newary = Array.newInstance(ary.getClass().getComponentType(), length);
        final int copysize = length > ary.length ? length : ary.length;
        System.arraycopy(ary, 0, newary, 0, copysize);
        return newary;
    }

    public static byte[] resize(final byte[] ary, final int length) {
        final byte[] newary = new byte[length];
        final int copysize = length > ary.length ? ary.length : length;
        System.arraycopy(ary, 0, newary, 0, copysize);
        return newary;
    }

    public static boolean startsWith(final byte[] target, final byte[] prefix) {
        final int testlen = prefix.length;
        if (target.length < testlen) {
            return false;
        }
        for (int i = 0; i < testlen; i++) {
            if (target[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    public static int compareTo(final byte lhs[], final byte rhs[]) {
        if (lhs == rhs) {
            return 0;
        }
        final int len = Math.min(lhs.length, rhs.length);
        for (int i = 0; i < len; i++) {
            if (lhs[i] != rhs[i]) {
                return (lhs[i] & 0xFF) - (rhs[i] & 0xFF);
            }
        }
        return lhs.length - rhs.length;
    }

    public static int compareTo(final byte lhs[], final byte rhs[], final int offset) {
        final int len = Math.min(lhs.length, rhs.length);
        for (int i = offset; i < len; i++) {
            if (lhs[i] != rhs[i]) {
                return (lhs[i] & 0xFF) - (rhs[i] & 0xFF);
            }
        }
        return lhs.length - rhs.length;
    }

    public static int compareTo(final byte lhs[], final byte rhs[], final int offset,
            final int length) {
        int tolen = offset + length;
        final int limit = Math.min(Math.min(lhs.length, rhs.length), tolen);
        for (int i = offset; i < limit; i++) {
            if (lhs[i] != rhs[i]) {
                return (lhs[i] & 0xFF) - (rhs[i] & 0xFF);
            }
        }
        return 0;
    }

    public static int compareTo(final byte[] b1, final int off1, final int len1, final byte[] b2,
            final int off2, final int len2) {
        for (int i = 0; i < len1 && i < len2; i++) {
            final int d = (b1[off1 + i] & 0xFF) - (b2[off2 + i] & 0xFF);
            if (d != 0) {
                return d;
            }
        }
        return len1 - len2;
    }

    public static int compareTo(final int lhs[], final int rhs[]) {
        if (lhs == rhs) {
            return 0;
        }
        final int len = Math.min(lhs.length, rhs.length);
        for (int i = 0; i < len; i++) {
            if (lhs[i] != rhs[i]) {
                return lhs[i] - rhs[i];
            }
        }
        return lhs.length - rhs.length;
    }

    public static <T> boolean contains(final T[] array, final T value) {
        final int len = array.length;
        for (int i = 0; i < len; i++) {
            if (array[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static int longestCommonPrefix(final byte[] ary, final byte[] root) {
        final int limit = Math.min(ary.length, root.length);
        for (int i = 0; i < limit; i++) {
            if (ary[i] != root[i]) {
                return i;
            }
        }
        return limit;
    }

    public static void shuffle(final Object[] array) {
        final Random r = new Random();
        final int limit = array.length;
        for (int i = 0; i < limit; ++i) {
            swap(array, i, r.nextInt(limit));
        }
    }

    public static void shuffle(final int[] array) {
        final Random r = new Random();
        final int limit = array.length;
        for (int i = 0; i < limit; ++i) {
            swap(array, i, r.nextInt(limit));
        }
    }

    public static void shuffle(final Object[] array, final long seed) {
        final Random r = new Random(seed);
        final int limit = array.length;
        for (int i = 0; i < limit; ++i) {
            swap(array, i, r.nextInt(limit));
        }
    }

    public static void swap(final Object[] array, final int i, final int j) {
        Object o = array[i];
        array[i] = array[j];
        array[j] = o;
    }

    public static void swap(final int[] array, final int i, final int j) {
        int o = array[i];
        array[i] = array[j];
        array[j] = o;
    }

    public static double max(final double[] array) {
        double d = Double.MIN_VALUE;
        for (int i = 0; i < array.length; i++) {
            d = Math.max(array[i], d);
        }
        return d;
    }

    public static <T> T max(final T[] array, final double[] scores) {
        if (array.length != scores.length) {
            throw new IllegalArgumentException(
                "array.length(" + array.length + ") != scores.length(" + scores.length + ")");
        }
        T obj = null;
        double d = Double.MIN_VALUE;
        for (int i = 0; i < scores.length; i++) {
            final double score = scores[i];
            if (score > d) {
                d = score;
                obj = array[i];
            }
        }
        return obj;
    }

    public static int minIndex(final float[] scores) {
        int index = -1;
        float min = Float.MAX_VALUE;
        for (int i = 0; i < scores.length; i++) {
            final float f = scores[i];
            if (f < min) {
                min = f;
                index = i;
            }
        }
        return index;
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T[]> getArrayClass(Class<T> clazz) {
        Object array = Array.newInstance(clazz, 0);
        return (Class<T[]>) array.getClass();
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(final Collection<T> c, final Class<? extends T[]> newType) {
        final int length = c.size();
        final T[] array = (T[]) Array.newInstance(newType.getComponentType(), length);
        if (length > 0) {
            int i = 0;
            for (final T elem : c) {
                array[i++] = elem;
            }
        }
        return array;
    }

    public static byte[][] toArray(final Collection<byte[]> c) {
        final int length = c.size();
        final byte[][] array = new byte[length][];
        if (length > 0) {
            int i = 0;
            for (final byte[] b : c) {
                array[i++] = b;
            }
        }
        return array;
    }

}
