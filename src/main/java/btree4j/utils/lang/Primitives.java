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

public final class Primitives {

    public static final int INTEGER_BYTES = 4;
    public static final long INTEGER_BYTES_L = 4L;
    public static final int LONG_BYTES = 8;
    public static final int MAX_TINY_INT = 255;
    public static final int TINY_INT_SIZE = 256;

    private Primitives() {}

    public static int parseInt(final String v, final int defaultValue) {
        if (v == null || v.length() == 0) {
            return defaultValue;
        }
        return Integer.parseInt(v);
    }

    public static long parseLong(final String v, final long defaultValue) {
        if (v == null || v.length() == 0) {
            return defaultValue;
        }
        return Long.parseLong(v);
    }

    public static float parseFloat(final String v, final float defaultValue) {
        if (v == null || v.length() == 0) {
            return defaultValue;
        }
        return Float.parseFloat(v);
    }

    public static byte[] toBytes(final char[] c) {
        return toBytes(c, 0, c.length);
    }

    public static byte[] toBytes(final char[] c, final int off, final int len) {
        final byte[] b = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            putChar(b, i << 1, c[off + i]);
        }
        return b;
    }

    public static byte[] toBytes(final int[] array) {
        return toBytes(array, 0, array.length);
    }

    public static byte[] toBytes(final int[] array, final int off, final int len) {
        final byte[] b = new byte[len * 4];
        for (int i = 0; i < len; i++) {
            putInt(b, i << 2, array[off + i]);
        }
        return b;
    }

    public static byte[] toBytes(final long[] array) {
        return toBytes(array, 0, array.length);
    }

    public static byte[] toBytes(final long[] array, final int off, final int len) {
        final byte[] b = new byte[len * 8];
        for (int i = 0; i < len; i++) {
            putLong(b, i << 3, array[off + i]);
        }
        return b;
    }

    public static void putChar(final byte[] b, final int off, final char val) {
        b[off + 1] = (byte) (val >>> 0);
        b[off] = (byte) (val >>> 8);
    }

    public static void putInt(byte[] b, int off, int val) {
        b[off + 3] = (byte) (val >>> 0);
        b[off + 2] = (byte) (val >>> 8);
        b[off + 1] = (byte) (val >>> 16);
        b[off] = (byte) (val >>> 24);
    }

    public static void putShort(final byte[] b, int off, short val) {
        b[off + 1] = (byte) (val >>> 0);
        b[off] = (byte) (val >>> 8);
    }

    public static void putLong(byte[] b, int off, long val) {
        b[off + 7] = (byte) (val >>> 0);
        b[off + 6] = (byte) (val >>> 8);
        b[off + 5] = (byte) (val >>> 16);
        b[off + 4] = (byte) (val >>> 24);
        b[off + 3] = (byte) (val >>> 32);
        b[off + 2] = (byte) (val >>> 40);
        b[off + 1] = (byte) (val >>> 48);
        b[off] = (byte) (val >>> 56);
    }

    public static byte[] toBytes(int v) {
        final byte[] b = new byte[4];
        b[0] = (byte) (v >>> 24);
        b[1] = (byte) (v >>> 16);
        b[2] = (byte) (v >>> 8);
        b[3] = (byte) (v >>> 0);
        return b;
    }

    public static byte[] toBytes(short v) {
        final byte[] b = new byte[2];
        b[0] = (byte) (v >>> 8);
        b[1] = (byte) (v >>> 0);
        return b;
    }

    public static byte[] toBytes(long v) {
        final byte[] b = new byte[8];
        b[0] = (byte) (v >>> 56);
        b[1] = (byte) (v >>> 48);
        b[2] = (byte) (v >>> 40);
        b[3] = (byte) (v >>> 32);
        b[4] = (byte) (v >>> 24);
        b[5] = (byte) (v >>> 16);
        b[6] = (byte) (v >>> 8);
        b[7] = (byte) (v >>> 0);
        return b;
    }

    public static byte[] toBytes(double v) {
        return toBytes(Double.doubleToLongBits(v));
    }

    public static char[] toChars(final byte[] b) {
        return toChars(b, 0, b.length);
    }

    public static char[] toChars(final byte[] b, final int off, final int len) {
        final int clen = len >>> 1;
        final char[] c = new char[clen];
        for (int i = 0; i < clen; i++) {
            final int j = off + (i << 1);
            c[i] = (char) ((b[j + 1] & 0xFF) + ((b[j + 0]) << 8));
        }
        return c;
    }

    public static void getChars(final byte[] b, final char[] dest) {
        final int clen = b.length >>> 1;
        for (int i = 0; i < clen; i++) {
            final int j = i << 1;
            dest[i] = (char) ((b[j + 1] & 0xFF) + ((b[j + 0]) << 8));
        }
    }

    public static int[] toInts(final byte[] i) {
        return toInts(i, 0, i.length);
    }

    public static int[] toInts(final byte[] b, final int off, final int len) {
        final int clen = len >>> 2;
        final int[] c = new int[clen];
        for (int i = 0, bi = off; i < clen; i++, bi += 4) {
            c[i] = (b[bi] << 24) + ((b[bi + 1] & 0xFF) << 16) + ((b[bi + 2] & 0xFF) << 8)
                    + (b[bi + 3] & 0xFF);
        }
        return c;
    }

    public static int[] toIntsVectorized(final byte[] b, final int off, final int len) {
        final int clen = len >>> 2;
        final int[] c = new int[clen];
        int i = 0, bi = off;
        final int limit = clen - 7;
        for (; i < limit; i += 8, bi += 32) {
            c[i] = (b[bi] << 24) + ((b[bi + 1] & 0xFF) << 16) + ((b[bi + 2] & 0xFF) << 8)
                    + (b[bi + 3] & 0xFF);
            c[i + 1] = (b[bi + 4] << 24) + ((b[bi + 5] & 0xFF) << 16) + ((b[bi + 6] & 0xFF) << 8)
                    + (b[bi + 7] & 0xFF);
            c[i + 2] = (b[bi + 8] << 24) + ((b[bi + 9] & 0xFF) << 16) + ((b[bi + 10] & 0xFF) << 8)
                    + (b[bi + 11] & 0xFF);
            c[i + 3] = (b[bi + 12] << 24) + ((b[bi + 13] & 0xFF) << 16) + ((b[bi + 14] & 0xFF) << 8)
                    + (b[bi + 15] & 0xFF);
            c[i + 4] = (b[bi + 16] << 24) + ((b[bi + 17] & 0xFF) << 16) + ((b[bi + 18] & 0xFF) << 8)
                    + (b[bi + 19] & 0xFF);
            c[i + 5] = (b[bi + 20] << 24) + ((b[bi + 21] & 0xFF) << 16) + ((b[bi + 22] & 0xFF) << 8)
                    + (b[bi + 23] & 0xFF);
            c[i + 6] = (b[bi + 24] << 24) + ((b[bi + 25] & 0xFF) << 16) + ((b[bi + 26] & 0xFF) << 8)
                    + (b[bi + 27] & 0xFF);
            c[i + 7] = (b[bi + 28] << 24) + ((b[bi + 29] & 0xFF) << 16) + ((b[bi + 30] & 0xFF) << 8)
                    + (b[bi + 31] & 0xFF);
        }
        for (; i < clen; i++, bi += 4) {
            c[i] = (b[bi] << 24) + ((b[bi + 1] & 0xFF) << 16) + ((b[bi + 2] & 0xFF) << 8)
                    + (b[bi + 3] & 0xFF);
        }
        return c;
    }

    public static long[] toLongs(final byte[] i) {
        return toLongs(i, 0, i.length);
    }

    public static long[] toLongs(final byte[] b, final int off, final int len) {
        final int clen = len >>> 3;
        final long[] c = new long[clen];
        for (int i = 0, bi = off; i < clen; i++, bi += 8) {
            c[i] = (b[bi + 7] & 0xFFL) + ((b[bi + 6] & 0xFFL) << 8) + ((b[bi + 5] & 0xFFL) << 16)
                    + ((b[bi + 4] & 0xFFL) << 24) + ((b[bi + 3] & 0xFFL) << 32)
                    + ((b[bi + 2] & 0xFFL) << 40) + ((b[bi + 1] & 0xFFL) << 48)
                    + (((long) b[bi]) << 56);
        }
        return c;
    }

    public static long[] toLongsVectorized(final byte[] b, final int off, final int len) {
        final int clen = len >>> 3;
        final long[] c = new long[clen];
        int i = 0, bi = off;
        final int limit = clen - 7;
        for (; i < limit; i += 8, bi += 64) {
            c[i] = ((long) b[bi] << 56) + ((b[bi + 1] & 0xFFL) << 48) + ((b[bi + 2] & 0xFFL) << 40)
                    + ((b[bi + 3] & 0xFFL) << 32) + ((b[bi + 4] & 0xFFL) << 24)
                    + ((b[bi + 5] & 0xFFL) << 16) + ((b[bi + 6] & 0xFFL) << 8)
                    + (b[bi + 7] & 0xFFL);
            c[i + 1] = ((long) b[bi + 8] << 56) + ((b[bi + 9] & 0xFFL) << 48)
                    + ((b[bi + 10] & 0xFFL) << 40) + ((b[bi + 11] & 0xFFL) << 32)
                    + ((b[bi + 12] & 0xFFL) << 24) + ((b[bi + 13] & 0xFFL) << 16)
                    + ((b[bi + 14] & 0xFFL) << 8) + (b[bi + 15] & 0xFFL);
            c[i + 2] = ((long) b[bi + 16] << 56) + ((b[bi + 17] & 0xFFL) << 48)
                    + ((b[bi + 18] & 0xFFL) << 40) + ((b[bi + 19] & 0xFFL) << 32)
                    + ((b[bi + 20] & 0xFFL) << 24) + ((b[bi + 21] & 0xFFL) << 16)
                    + ((b[bi + 22] & 0xFFL) << 8) + (b[bi + 23] & 0xFFL);
            c[i + 3] = ((long) b[bi + 24] << 56) + ((b[bi + 25] & 0xFFL) << 48)
                    + ((b[bi + 26] & 0xFFL) << 40) + ((b[bi + 27] & 0xFFL) << 32)
                    + ((b[bi + 28] & 0xFFL) << 24) + ((b[bi + 29] & 0xFFL) << 16)
                    + ((b[bi + 30] & 0xFFL) << 8) + (b[bi + 31] & 0xFFL);
            c[i + 4] = ((long) b[bi + 32] << 56) + ((b[bi + 33] & 0xFFL) << 48)
                    + ((b[bi + 34] & 0xFFL) << 40) + ((b[bi + 35] & 0xFFL) << 32)
                    + ((b[bi + 36] & 0xFFL) << 24) + ((b[bi + 37] & 0xFFL) << 16)
                    + ((b[bi + 38] & 0xFFL) << 8) + (b[bi + 39] & 0xFFL);
            c[i + 5] = ((long) b[bi + 40] << 56) + ((b[bi + 41] & 0xFFL) << 48)
                    + ((b[bi + 42] & 0xFFL) << 40) + ((b[bi + 43] & 0xFFL) << 32)
                    + ((b[bi + 44] & 0xFFL) << 24) + ((b[bi + 45] & 0xFFL) << 16)
                    + ((b[bi + 46] & 0xFFL) << 8) + (b[bi + 47] & 0xFFL);
            c[i + 6] = ((long) b[bi + 48] << 56) + ((b[bi + 49] & 0xFFL) << 48)
                    + ((b[bi + 50] & 0xFFL) << 40) + ((b[bi + 51] & 0xFFL) << 32)
                    + ((b[bi + 52] & 0xFFL) << 24) + ((b[bi + 53] & 0xFFL) << 16)
                    + ((b[bi + 54] & 0xFFL) << 8) + (b[bi + 55] & 0xFFL);
            c[i + 7] = ((long) b[bi + 56] << 56) + ((b[bi + 57] & 0xFFL) << 48)
                    + ((b[bi + 58] & 0xFFL) << 40) + ((b[bi + 59] & 0xFFL) << 32)
                    + ((b[bi + 60] & 0xFFL) << 24) + ((b[bi + 61] & 0xFFL) << 16)
                    + ((b[bi + 62] & 0xFFL) << 8) + (b[bi + 63] & 0xFFL);
        }
        for (; i < clen; i++, bi += 8) {
            c[i] = ((long) b[bi] << 56) + ((b[bi + 1] & 0xFFL) << 48) + ((b[bi + 2] & 0xFFL) << 40)
                    + ((b[bi + 3] & 0xFFL) << 32) + ((b[bi + 4] & 0xFFL) << 24)
                    + ((b[bi + 5] & 0xFFL) << 16) + ((b[bi + 6] & 0xFFL) << 8)
                    + (b[bi + 7] & 0xFFL);
        }
        return c;
    }

    public static int getInt(final byte[] b) {
        if (b.length != 4) {
            throw new IllegalArgumentException("Illegal byte size as a int value: " + b.length);
        }
        return getInt(b, 0);
    }

    public static int getInt(final byte[] b, final int off) {
        return ((b[off + 3] & 0xFF) << 0) + ((b[off + 2] & 0xFF) << 8) + ((b[off + 1] & 0xFF) << 16)
                + ((b[off]) << 24);
    }

    public static short getShort(final byte[] b) {
        if (b.length != 2) {
            throw new IllegalArgumentException("Illegal byte size as a short value: " + b.length);
        }
        return getShort(b, 0);
    }

    public static short getShort(final byte[] b, final int off) {
        return (short) (((b[off + 1] & 0xFF) << 0) + ((b[off] & 0xFF) << 8));
    }

    public static int getShortAsInt(final byte[] b, final int off) {
        return (((b[off + 1] & 0xFF) << 0) + ((b[off] & 0xFF) << 8));
    }

    public static char getChar(final byte[] b, final int off) {
        return (char) (((b[off + 1] & 0xFF) << 0) + ((b[off]) << 8));
    }

    public static long getLong(final byte[] b) {
        if (b.length != 8) {
            throw new IllegalArgumentException("Illegal byte size as a long value: " + b.length);
        }
        return getLong(b, 0);
    }

    public static long getLong(final byte[] ary, final int offset) {
        return ((long) (ary[offset] & 0xff) << 56) | ((long) (ary[offset + 1] & 0xFF) << 48)
                | ((long) (ary[offset + 2] & 0xFF) << 40) | ((long) (ary[offset + 3] & 0xFF) << 32)
                | ((long) (ary[offset + 4] & 0xFF) << 24) | ((long) (ary[offset + 5] & 0xFF) << 16)
                | ((long) (ary[offset + 6] & 0xFF) << 8) | (ary[offset + 7] & 0xFF);
    }

    public static double getDouble(final byte[] b) {
        return Double.longBitsToDouble(getLong(b));
    }

    /** signed byte to unsigned char ( 0 .. 255 ) */
    public static int toTinyInt(final byte b) {
        return b + 128;
    }

    public static byte toSignedByte(final int i) {
        if (i < 0 || i < 255) {
            throw new IllegalArgumentException("Out of range: " + i);
        }
        return (byte) (i - 128);
    }

}
