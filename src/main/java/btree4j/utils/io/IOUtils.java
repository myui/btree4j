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
 * Copyright 2001-2004 The Apache Software Foundation
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
package btree4j.utils.io;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * IO related utilities.
 */
public final class IOUtils {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private IOUtils() {}

    public static void writeInt(final int v, final OutputStream out) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
    }

    /**
     * @return may be negative value when EOF is detected.
     */
    public static int readInt(final InputStream in) throws IOException {
        final int ch1 = in.read();
        final int ch2 = in.read();
        final int ch3 = in.read();
        final int ch4 = in.read();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public static int readUnsignedIntOrEOF(final InputStream in) throws IOException {
        final int ch1 = in.read();
        if (ch1 == -1) {
            return -1;
        }
        final int ch2 = in.read();
        final int ch3 = in.read();
        final int ch4 = in.read();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public static void writeChar(final char v, final OutputStream out) throws IOException {
        out.write(0xff & (v >> 8));
        out.write(0xff & v);
    }

    public static void writeChar(final char v, final FastByteArrayOutputStream out) {
        out.write(0xff & (v >> 8));
        out.write(0xff & v);
    }

    public static char readChar(final InputStream in) throws IOException {
        final int a = in.read();
        final int b = in.read();
        return (char) ((a << 8) | (b & 0xff));
    }

    public static void readFully(final InputStream in, final byte[] b, int offset, int len)
            throws IOException {
        do {
            final int bytesRead = in.read(b, offset, len);
            if (bytesRead < 0) {
                throw new EOFException();
            }
            len -= bytesRead;
            offset += bytesRead;
        } while (len != 0);
    }

    public static void readFully(final InputStream in, final byte[] b) throws IOException {
        readFully(in, b, 0, b.length);
    }

    public static int readLoop(final InputStream in, final byte[] b, final int off, final int len)
            throws IOException {
        int total = 0;
        for (;;) {
            final int got = in.read(b, off + total, len - total);
            if (got < 0) {
                return (total == 0) ? -1 : total;
            } else {
                total += got;
                if (total == len) {
                    return total;
                }
            }
        }
    }

    public static int copy(InputStream input, OutputStream output) throws IOException {
        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static int copy(Reader input, Writer output) throws IOException {
        final char[] buffer = new char[DEFAULT_BUFFER_SIZE];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static void copy(String input, OutputStream output) throws IOException {
        final StringReader in = new StringReader(input);
        final OutputStreamWriter out = new OutputStreamWriter(output);
        copy(in, out);
        // Unless anyone is planning on rewriting OutputStreamWriter, we have to flush here.
        out.flush();
    }

    /**
     * Serialize given InputStream as String.
     */
    public static String toString(InputStream input) throws IOException {
        final FastMultiByteArrayOutputStream output = new FastMultiByteArrayOutputStream();
        copy(input, output);
        return output.toString();
    }

    public static String toString(InputStream input, String cs) throws IOException {
        final FastMultiByteArrayOutputStream output = new FastMultiByteArrayOutputStream();
        copy(input, output);
        return output.toString(cs);
    }

    public static String toString(Reader input) throws IOException {
        final StringWriter sw = new StringWriter();
        copy(input, sw);
        return sw.toString();
    }

    @Deprecated
    public static void getBytes(final List<byte[]> srcLst, final byte[] dest) {
        int pos = 0;
        for (byte[] bytes : srcLst) {
            final int len = bytes.length;
            System.arraycopy(bytes, 0, dest, pos, len);
            pos += len;
        }
    }

    public static byte[] getBytes(final InputStream in) throws IOException {
        FastByteArrayOutputStream out = new FastByteArrayOutputStream(4096);
        copy(in, out);
        return out.toByteArray();
    }

    public static void closeQuietly(final Closeable channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                ;
            }
        }
    }

    public static void closeQuietly(final Closeable... channels) {
        for (Closeable c : channels) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    ;
                }
            }
        }
    }

    public static void closeAndRethrow(final Exception e, final Closeable... channels)
            throws IllegalStateException {
        closeQuietly(channels);
        throw new IllegalStateException(e);
    }

    /**
     * @param delay delay in milliseconds before task is to be executed.
     */
    public static void schduleCloseQuietly(final Timer timer, final long delay,
            final Closeable... channels) {
        if (delay == 0) {
            closeQuietly(channels);
            return;
        }
        final TimerTask cancel = new TimerTask() {
            @Override
            public void run() {
                closeQuietly(channels);
            }
        };
        timer.schedule(cancel, delay);
    }

    public static void schduleCloseQuietly(final ScheduledExecutorService sched, final int delay,
            final AtomicInteger activeCount, final Closeable... channels) {
        if (delay == 0) {
            closeQuietly(channels);
            return;
        }
        final Runnable cancel = new Runnable() {
            public void run() {
                if (activeCount.get() < 1) {
                    closeQuietly(channels);
                }
            }
        };
        sched.schedule(cancel, delay, TimeUnit.MILLISECONDS);
    }

    public static void writeBytes(@Nullable final byte[] b, final DataOutput out)
            throws IOException {
        if (b == null) {
            out.writeInt(-1);
            return;
        }
        final int len = b.length;
        out.writeInt(len);
        out.write(b, 0, len);
    }

    public static void writeBytes(@Nullable final byte[] b, final FastBufferedOutputStream out)
            throws IOException {
        if (b == null) {
            writeInt(-1, out);
            return;
        }
        final int len = b.length;
        writeInt(len, out);
        out.write(b, 0, len);
    }

    @Nullable
    public static byte[] readBytes(final DataInput in) throws IOException {
        final int len = in.readInt();
        if (len == -1) {
            return null;
        }
        final byte[] b = new byte[len];
        in.readFully(b, 0, len);
        return b;
    }

    @Nullable
    public static byte[] readBytes(final FastBufferedInputStream in) throws IOException {
        final int len = readInt(in);
        if (len == -1) {
            return null;
        }
        final byte[] b = new byte[len];
        in.read(b, 0, len);
        return b;
    }

    public static void writeString(@Nullable final String s, final ObjectOutputStream out)
            throws IOException {
        writeString(s, (DataOutput) out);
    }

    public static void writeString(@Nullable final String s, final DataOutputStream out)
            throws IOException {
        writeString(s, (DataOutput) out);
    }

    public static void writeString(@Nullable final String s, final DataOutput out)
            throws IOException {
        if (s == null) {
            out.writeInt(-1);
            return;
        }
        final int len = s.length();
        out.writeInt(len);
        for (int i = 0; i < len; i++) {
            int v = s.charAt(i);
            out.writeChar(v);
        }
    }

    public static void writeString(@Nullable final String s, final OutputStream out)
            throws IOException {
        if (s == null) {
            writeInt(-1, out);
            return;
        }
        final int len = s.length();
        writeInt(len, out);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            writeChar(c, out);
        }
    }

    @Nullable
    public static String readString(@Nonnull final ObjectInputStream in) throws IOException {
        return readString((DataInput) in);
    }

    @Nullable
    public static String readString(@Nonnull final DataInputStream in) throws IOException {
        return readString((DataInput) in);
    }

    @Nullable
    public static String readString(@Nonnull final DataInput in) throws IOException {
        final int len = in.readInt();
        if (len == -1) {
            return null;
        }
        final char[] ch = new char[len];
        for (int i = 0; i < len; i++) {
            ch[i] = in.readChar();
        }
        return new String(ch);
    }

    @Nullable
    public static String readString(@Nonnull final InputStream in) throws IOException {
        final int len = readInt(in);
        if (len == -1) {
            return null;
        }
        final char[] ch = new char[len];
        for (int i = 0; i < len; i++) {
            ch[i] = readChar(in);
        }
        return new String(ch);
    }

}
