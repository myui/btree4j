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
package btree4j.indexer;

import btree4j.BTree;
import btree4j.BTreeCallback;
import btree4j.BTreeException;
import btree4j.Value;

import java.io.File;

public class BTreeIndexer implements Indexer {

    /** 4k page */
    private static final int PAGE_SIZE = 1024 * 4;
    /** 4k * 1024 = 4m page cache */
    private static final int IN_MEMORY_PAGES = 1024;
    /** cache size 32m */
    private static final int INDEX_BUILD_PAGES = 1024 * 8;

    private final String name;
    private final BTree btree;

    public BTreeIndexer(File file) {
        this(null, file);
    }

    public BTreeIndexer(String name, File file) {
        this(name, file, false);
    }

    public BTreeIndexer(String name, File file, boolean bulkBuild) {
        this.name = name;
        final BTree tree =
                new BTree(file, PAGE_SIZE, bulkBuild ? INDEX_BUILD_PAGES : IN_MEMORY_PAGES, true);
        try {
            tree.init(bulkBuild);
        } catch (BTreeException e) {
            throw new IllegalStateException(
                "failed on initializing b+-tree: " + file.getAbsolutePath(), e);
        }
        this.btree = tree;
    }

    public String getName() {
        return name;
    }

    public long add(byte[] key, long value) throws BTreeException {
        return btree.addValue(new Value(key), value);
    }

    public long remove(byte[] key) throws BTreeException {
        return btree.removeValue(new Value(key));
    }

    public long remove(byte[] key, long value) throws BTreeException {
        return btree.removeValue(new Value(key));
    }

    public IndexMatch find(IndexQuery cond) throws BTreeException {
        IndexMatch match = new IndexMatch(12);
        Callback callback = new Callback(match);
        btree.search(cond, callback);
        return match;
    }

    private static final class Callback implements BTreeCallback {

        private final IndexMatch match;

        public Callback(IndexMatch match) {
            this.match = match;
        }

        public boolean indexInfo(Value value, long pointer) {
            match.add(value, pointer);
            return true;
        }

        public boolean indexInfo(Value key, byte[] value) {
            throw new IllegalStateException();
        }

    }

    public void flush(boolean close) throws BTreeException {
        btree.flush(true, true);
        if (close) {
            btree.close();
        }
    }

    public void close() throws BTreeException {
        btree.close();
    }
}
