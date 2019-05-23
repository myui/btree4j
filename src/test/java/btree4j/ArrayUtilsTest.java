package btree4j;

import btree4j.utils.lang.ArrayUtils;
import org.junit.Assert;
import org.junit.Test;

public class ArrayUtilsTest {
    // --------------- remove
    @Test
    public void shouldRemoveFirstValue() {
        Integer[] a = {1, 2, 3};
        a = ArrayUtils.remove(a, 0);
        Assert.assertArrayEquals(new Integer[]{2, 3}, a);
    }

    @Test
    public void shouldRemoveLastValue() {
        Integer[] a = {1, 2, 3};
        a = ArrayUtils.remove(a, 2);
        Assert.assertArrayEquals(new Integer[]{1, 2}, a);
    }

    @Test
    public void shouldRemoveMiddleValue() {
        Integer[] a = {1, 2, 3};
        a = ArrayUtils.remove(a, 1);
        Assert.assertArrayEquals(new Integer[]{1, 3}, a);
    }

    @Test
    public void shouldRemoveFirstValuePrimitive() {
        long[] a = {1, 2, 3};
        a = ArrayUtils.remove(a, 0);
        Assert.assertArrayEquals(new long[]{2, 3}, a);
    }

    @Test
    public void shouldRemoveLastValuePrimitive() {
        long[] a = {1, 2, 3};
        a = ArrayUtils.remove(a, 2);
        Assert.assertArrayEquals(new long[]{1, 2}, a);
    }

    @Test
    public void shouldRemoveMiddleValuePrimitive() {
        long[] a = {1, 2, 3};
        a = ArrayUtils.remove(a, 1);
        Assert.assertArrayEquals(new long[]{1, 3}, a);
    }

    // --------------- copyOf
    @Test
    public void shouldRemoveFromFirstValue() {
        Integer[] a = {1, 2, 3};
        a = ArrayUtils.copyOf(a, 0);
        Assert.assertArrayEquals(new Integer[]{}, a);
    }

    @Test
    public void shouldRemoveFromLastValue() {
        Integer[] a = {1, 2, 3};
        a = ArrayUtils.copyOf(a, 2);
        Assert.assertArrayEquals(new Integer[]{1, 2}, a);
    }

    @Test
    public void shouldRemoveFromMiddleValue() {
        Integer[] a = {1, 2, 3};
        a = ArrayUtils.copyOf(a, 1);
        Assert.assertArrayEquals(new Integer[]{1}, a);
    }

    @Test
    public void shouldRemoveFromFirstValuePrimitive() {
        long[] a = {1, 2, 3};
        a = ArrayUtils.copyOf(a, 0);
        Assert.assertArrayEquals(new long[]{}, a);
    }

    @Test
    public void shouldRemoveFromLastValuePrimitive() {
        long[] a = {1, 2, 3};
        a = ArrayUtils.copyOf(a, 2);
        Assert.assertArrayEquals(new long[]{1, 2}, a);
    }

    @Test
    public void shouldRemoveFromMiddleValuePrimitive() {
        long[] a = {1, 2, 3};
        a = ArrayUtils.copyOf(a, 1);
        Assert.assertArrayEquals(new long[]{1}, a);
    }
}
