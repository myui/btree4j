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

import btree4j.utils.io.FileUtils;

import java.io.File;
import java.io.PrintStream;
import java.text.DecimalFormat;

public final class PrintUtils {

    private static final int TRACE_CAUSE_DEPTH = 5;

    private static boolean E_ALL_ON_FATAL = true;

    /** Restricts the instantiation. */
    private PrintUtils() {}

    public static void prettyPrintStackTrace(final Throwable throwable, final PrintStream out) {
        final String s = prettyPrintStackTrace(throwable);
        out.print(s);
    }

    public static String prettyPrintStackTrace(final Throwable throwable) {
        return prettyPrintStackTrace(throwable, TRACE_CAUSE_DEPTH);
    }

    public static String prettyPrintStackTrace(final Throwable throwable, final int traceDepth) {
        final StringBuilder out = new StringBuilder(512);
        out.append(getMessage(throwable));
        out.append("\n\n---- Debugging information ----");
        final int tracedepth;
        if (E_ALL_ON_FATAL
                && (throwable instanceof RuntimeException || throwable instanceof Error)) {
            tracedepth = -1;
        } else {
            tracedepth = traceDepth;
        }
        String captured = captureThrownWithStrackTrace(throwable, "trace-exception", tracedepth);
        out.append(captured);
        final Throwable cause = throwable.getCause();
        if (cause != null) {
            final Throwable rootCause = getRootCause(cause);
            captured = captureThrownWithStrackTrace(rootCause, "trace-cause", TRACE_CAUSE_DEPTH);
            out.append(captured);
        }
        out.append("\n------------------------------- \n");
        return out.toString();
    }

    private static String captureThrownWithStrackTrace(final Throwable throwable,
            final String label, final int traceDepth) {
        assert (traceDepth >= 1 || traceDepth == -1);
        final StringBuilder out = new StringBuilder(255);
        final String clazz = throwable.getClass().getName();
        out.append(String.format("\n%-20s: %s \n", ("* " + label), clazz));
        final StackTraceElement[] st = throwable.getStackTrace();
        int at;
        final int limit = (traceDepth == -1) ? st.length - 1 : traceDepth;
        for (at = 0; at < st.length; at++) {
            if (at < limit) {
                out.append("\tat " + st[at] + '\n');
            } else {
                out.append("\t...\n");
                break;
            }
        }
        if (st.length == 0) {
            out.append("\t no stack traces...");
        } else if (at != (st.length - 1)) {
            out.append("\tat " + st[st.length - 1]);
        }
        String errmsg = throwable.getMessage();
        if (errmsg != null) {
            out.append(String.format("\n%-20s: \n", ("* " + label + "-error-msg")));
            String[] line = errmsg.split("\n");
            final int maxlines = Math.min(line.length, Math.max(1, TRACE_CAUSE_DEPTH - 2));
            for (int i = 0; i < maxlines; i++) {
                out.append('\t');
                out.append(line[i]);
                if (i != (maxlines - 1)) {
                    out.append('\n');
                }
            }
        }
        return out.toString();
    }

    public static String getMessage(Throwable throwable) {
        assert (throwable != null);
        final String errMsg = throwable.getMessage();
        final String clazz = throwable.getClass().getName();
        return (errMsg != null) ? clazz + ": " + errMsg : clazz;
    }

    public static String getOneLineMessage(Throwable throwable) {
        String lines = getMessage(throwable);
        int last = lines.indexOf('\n');
        if (last == -1) {
            last = lines.length();
        }
        return lines.substring(0, last);
    }

    private static Throwable getRootCause(final Throwable throwable) {
        assert (throwable != null);
        Throwable top = throwable;
        while (top != null) {
            Throwable parent = top.getCause();
            if (parent != null) {
                top = parent;
            } else {
                break;
            }
        }
        return top;
    }

    public static void printClassHierarchy(PrintStream out, Class<?> clazz) {
        Class<?> sc = clazz;
        final StringBuilder sbuf = new StringBuilder(64);
        while (sc != null) {
            String space = sbuf.toString();
            out.println(space + sc.getName());
            Class<?>[] ifs = sc.getInterfaces();
            for (int i = 0; i < ifs.length; i++) {
                out.println(space + "  implements: " + ifs[i].getName());
            }
            sc = sc.getSuperclass();
            sbuf.append(' ');
        }
    }

    public static void printClassLoaderHierarchy(PrintStream out, Class<?> clazz) {
        final StringBuilder sbuf = new StringBuilder(32);
        ClassLoader curr_cl = clazz.getClass().getClassLoader();
        while (curr_cl != null) {
            out.println(sbuf.toString() + curr_cl.getClass().getName());
            curr_cl = curr_cl.getParent();
            sbuf.append(' ');
        }
    }

    public static void printMemoryUsage(PrintStream out) {
        out.println("[MemoryUsage] " + new java.util.Date(System.currentTimeMillis()).toString()
                + " - Total: " + Runtime.getRuntime().totalMemory() + "byte , " + "Free: "
                + Runtime.getRuntime().freeMemory() + "byte");
    }

    public static String prettyFileSize(long size) {
        if (size < 0) {
            return "N/A";
        } else {
            if (size < 1024) {
                return size + " bytes";
            } else {
                float kb = size / 1024f;
                if (kb < 1024f) {
                    return String.format("%.1f KB", kb);
                } else {
                    float mb = kb / 1024f;
                    if (mb < 1024f) {
                        return String.format("%.1f MB", mb);
                    } else {
                        float gb = mb / 1024f;
                        return String.format("%.2f GB", gb);
                    }
                }
            }
        }
    }

    public static String prettyFileSize(File file) {
        return prettyFileSize(FileUtils.getFileSize(file));
    }

    public static float toPercent(final double v) {
        return (float) (v * 100.0f);
    }

    public static String toPercentString(final float v) {
        String p = String.valueOf(v * 100.0f);
        int ix = p.indexOf('.') + 1;
        String percent = p.substring(0, ix) + p.substring(ix, ix + 1);
        return percent + '%';
    }

    public static String toPercentString(final double v) {
        String p = String.valueOf(v * 100.0f);
        int ix = p.indexOf('.') + 1;
        String percent = p.substring(0, ix) + p.substring(ix, ix + 1);
        return percent + '%';
    }

    public static String toString(final StackTraceElement[] trace) {
        final int depth = trace.length;
        if (depth == 0) {
            return "\t no stack traces...";
        }
        final StringBuilder buf = new StringBuilder(512);
        for (int i = 0; i < depth; i++) {
            buf.append("\tat ");
            buf.append(trace[i]);
            buf.append('\n');
        }
        return buf.toString();
    }

    public static String toString(final StackTraceElement[] trace, final int maxDepth) {
        if (trace.length == 0) {
            return "\t no stack traces...";
        }
        final StringBuilder buf = new StringBuilder(512);
        final int depth = Math.min(trace.length, maxDepth);
        for (int i = 0; i < depth; i++) {
            buf.append("\tat ");
            buf.append(trace[i]);
            buf.append('\n');
        }
        return buf.toString();
    }

    public static String formatNumber(final long number) {
        DecimalFormat f = new DecimalFormat("#,###");
        return f.format(number);
    }

    public static String formatNumber(final double number) {
        return formatNumber(number, true);
    }

    public static String formatNumber(final double number, boolean commaSep) {
        DecimalFormat f = new DecimalFormat(commaSep ? "#,###.###" : "###.###");
        f.setDecimalSeparatorAlwaysShown(false);
        return f.format(number);
    }
}
