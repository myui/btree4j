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

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FileUtils {

    private FileUtils() {}

    public static File getTempDir() {
        String tmpdir = System.getProperty("java.io.tmpdir");
        return new File(tmpdir);
    }

    public static long getFileSize(File file) {
        if (!file.exists()) {
            return -1L;
        }
        long size = 0;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    size += getFileSize(f);
                }
            }
        } else {
            size += file.length();
        }
        return size;
    }

    /**
     * Finds files within a given directory (and optionally its subdirectories). All files found are
     * filtered by an IOFileFilter.
     */
    public static List<File> listFiles(File directory, IOFileFilter fileFilter,
            IOFileFilter dirFilter) {
        //if(!directory.isDirectory()) {
        //    throw new IllegalArgumentException("Parameter 'directory' is not a directory");
        //}
        if (fileFilter == null) {
            throw new NullPointerException("Parameter 'fileFilter' is null");
        }
        //Setup effective file filter
        IOFileFilter effFileFilter =
                new AndFileFilter(fileFilter, new NotFileFilter(DirectoryFileFilter.INSTANCE));
        //Setup effective directory filter
        final IOFileFilter effDirFilter;
        if (dirFilter == null) {
            effDirFilter = FalseFileFilter.INSTANCE;
        } else {
            effDirFilter = new AndFileFilter(dirFilter, DirectoryFileFilter.INSTANCE);
        }
        //Find files
        List<File> files = new ArrayList<File>(12);
        innerListFiles(files, directory, new OrFileFilter(effFileFilter, effDirFilter));
        return files;
    }

    /**
     * Finds files within a given directory (and optionally its subdirectories). All files found are
     * filtered by an IOFileFilter.
     */
    private static void innerListFiles(Collection<File> files, File directory,
            IOFileFilter filter) {
        File[] found = directory.listFiles((FileFilter) filter);
        if (found != null) {
            for (int i = 0; i < found.length; i++) {
                if (found[i].isDirectory()) {
                    innerListFiles(files, found[i], filter);
                } else {
                    files.add(found[i]);
                }
            }
        }
    }

    public static List<File> listFiles(File directory, boolean recursive) {
        return listFiles(directory, TrueFileFilter.INSTANCE,
            (recursive ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE));
    }

    public static List<File> listFiles(File directory, String suffix, boolean recursive) {
        return listFiles(directory, new String[] {suffix}, recursive);
    }

    /**
     * Finds files within a given directory (and optionally its subdirectories) which match an array
     * of suffixes.
     */
    public static List<File> listFiles(File directory, String[] suffixes, boolean recursive) {
        final IOFileFilter filter;
        if (suffixes == null || suffixes.length == 0) {
            filter = TrueFileFilter.INSTANCE;
        } else {
            filter = new SuffixFileFilter(suffixes);
        }
        return listFiles(directory, filter,
            (recursive ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE));
    }

    public static List<File> listFiles(File directory, String[] prefixes, String[] suffixes,
            boolean recursive) {
        IOFileFilter fileFiler = null;
        if (prefixes != null && prefixes.length > 0) {
            fileFiler = new PrefixFileFilter(prefixes);
        }
        if (suffixes != null && suffixes.length > 0) {
            fileFiler = new AndFileFilter(fileFiler, new SuffixFileFilter(suffixes));
        }
        return listFiles(directory, (fileFiler == null ? TrueFileFilter.INSTANCE : fileFiler),
            (recursive ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE));
    }

    public static void cleanDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            throw new IllegalArgumentException(dir + " does not exist");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir + " is not a directory");
        }

        final File[] files = dir.listFiles();
        if (files == null) { // null if security restricted
            throw new IOException("Failed to list contents of " + dir);
        }

        for (int i = 0; i < files.length; i++) {
            if (!files[i].delete()) {
                throw new IOException("Unable to delete file: " + files[i].getAbsolutePath());
            }
        }
    }

    public static String getFileName(File file) {
        assert (file != null);
        if (!file.exists()) {
            return null;
        }
        String filepath = file.getName();
        int i = filepath.lastIndexOf(File.separator);
        return (i >= 0) ? filepath.substring(i + 1) : filepath;
    }

    public static String basename(String filepath) {
        final int index = filepath.lastIndexOf(File.separatorChar);
        if (-1 == index) {
            return filepath;
        } else {
            return filepath.substring(index + 1);
        }
    }

    public static String basename(String filepath, char separator) {
        final int index = filepath.lastIndexOf(separator);
        if (-1 == index) {
            return filepath;
        } else {
            return filepath.substring(index + 1);
        }
    }

    public static String dirName(String filepath, char separatorChar) {
        final int index = filepath.lastIndexOf(separatorChar);
        if (-1 == index) {
            return new String(new char[] {separatorChar});
        } else {
            return filepath.substring(0, index);
        }
    }

    public static void truncateFile(File file) {
        final RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException fnfe) {
            throw new IllegalStateException(fnfe);
        }
        try {
            raf.setLength(0);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        } finally {
            IOUtils.closeQuietly(raf);
        }
    }

    public static String toString(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            return IOUtils.toString(fis);
        } catch (IOException e) {
            throw new IllegalStateException("failed reading a file: " + file.getAbsolutePath(), e);
        }
    }

    public static File resolvePath(File base, String path) {
        File f = new File(path);
        return f.isAbsolute() ? f : new File(base, path);
    }

    public interface IOFileFilter extends FileFilter, FilenameFilter {
    }

    public static final class TrueFileFilter implements IOFileFilter {
        static final TrueFileFilter INSTANCE = new TrueFileFilter();

        private TrueFileFilter() {}

        public boolean accept(File pathname) {
            return true;
        }

        public boolean accept(File dir, String name) {
            return true;
        }
    }

    public static final class FalseFileFilter implements IOFileFilter {
        static final FalseFileFilter INSTANCE = new FalseFileFilter();

        private FalseFileFilter() {}

        public boolean accept(File pathname) {
            return false;
        }

        public boolean accept(File dir, String name) {
            return false;
        }
    }

    public static final class PrefixFileFilter implements IOFileFilter {
        private final String[] prefixes;

        public PrefixFileFilter(String... prefixes) {
            if (prefixes == null) {
                throw new IllegalArgumentException("The array of prefixes must not be null");
            }
            this.prefixes = prefixes;
        }

        public boolean accept(File file) {
            String name = file.getName();
            for (int i = 0; i < this.prefixes.length; i++) {
                if (name.startsWith(this.prefixes[i])) {
                    return true;
                }
            }
            return false;
        }

        public boolean accept(File file, String name) {
            for (int i = 0; i < prefixes.length; i++) {
                if (name.startsWith(prefixes[i])) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class SuffixFileFilter implements IOFileFilter {
        private final String[] suffixes;

        public SuffixFileFilter(String... suffixes) {
            if (suffixes == null) {
                throw new IllegalArgumentException("The array of suffixes must not be null");
            }
            this.suffixes = suffixes;
        }

        public boolean accept(File file) {
            String name = file.getName();
            for (int i = 0; i < this.suffixes.length; i++) {
                if (name.endsWith(this.suffixes[i])) {
                    return true;
                }
            }
            return false;
        }

        public boolean accept(File file, String name) {
            for (int i = 0; i < this.suffixes.length; i++) {
                if (name.endsWith(this.suffixes[i])) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class AndFileFilter implements IOFileFilter {
        private final IOFileFilter[] fileFilters;

        public AndFileFilter(IOFileFilter... filter) {
            assert (filter != null);
            this.fileFilters = filter;
        }

        public boolean accept(final File file) {
            if (this.fileFilters.length == 0) {
                return false;
            }
            for (IOFileFilter fileFilter : fileFilters) {
                if (!fileFilter.accept(file)) {
                    return false;
                }
            }
            return true;
        }

        public boolean accept(final File file, final String name) {
            if (this.fileFilters.length == 0) {
                return false;
            }
            for (IOFileFilter fileFilter : fileFilters) {
                if (!fileFilter.accept(file, name)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class OrFileFilter implements IOFileFilter {
        private final IOFileFilter[] fileFilters;

        public OrFileFilter(IOFileFilter... filter) {
            assert (filter != null);
            this.fileFilters = filter;
        }

        public boolean accept(final File file) {
            for (IOFileFilter fileFilter : fileFilters) {
                if (fileFilter.accept(file)) {
                    return true;
                }
            }
            return false;
        }

        public boolean accept(final File file, final String name) {
            for (IOFileFilter fileFilter : fileFilters) {
                if (fileFilter.accept(file, name)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class NotFileFilter implements IOFileFilter {
        private final IOFileFilter filter;

        public NotFileFilter(IOFileFilter filter) {
            if (filter == null) {
                throw new IllegalArgumentException("The filter must not be null");
            }
            this.filter = filter;
        }

        public boolean accept(File file) {
            return !filter.accept(file);
        }

        public boolean accept(File file, String name) {
            return !filter.accept(file, name);
        }
    }

    public static final class DirectoryFileFilter implements IOFileFilter {
        public static final DirectoryFileFilter INSTANCE = new DirectoryFileFilter();

        private DirectoryFileFilter() {}

        public boolean accept(File file) {
            return file.isDirectory();
        }

        public boolean accept(File dir, String name) {
            return accept(new File(dir, name));
        }
    }

    public static final class NameFileFilter implements IOFileFilter {
        private final String[] names;

        public NameFileFilter(String... names) {
            if (names == null) {
                throw new IllegalArgumentException("The array of names must not be null");
            }
            this.names = names;
        }

        public boolean accept(File file) {
            String name = file.getName();
            for (int i = 0; i < this.names.length; i++) {
                if (name.equals(this.names[i])) {
                    return true;
                }
            }
            return false;
        }

        public boolean accept(File file, String name) {
            for (int i = 0; i < this.names.length; i++) {
                if (name.equals(this.names[i])) {
                    return true;
                }
            }
            return false;
        }
    }
}
