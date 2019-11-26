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

import btree4j.Value;

import java.util.Arrays;

public final class IndexMatch {

    private long[] entries;
    private int last;

    private transient long[] matchedCache = null;

    public IndexMatch(int expected) {
        this.entries = new long[expected];
        this.last = 0;
    }

    public void add(Value key, long entry) {
        ensureCapacity(last + 1);
        entries[last] = entry;
        ++last;
    }

    public int countMatched() {
        return last;
    }

    public long[] getMatchedSorted() {
        if (matchedCache != null) {
            return matchedCache;
        }
        Arrays.sort(entries, 0, last);
        long[] newArray = new long[last];
        System.arraycopy(entries, 0, newArray, 0, last);
        this.matchedCache = newArray;
        return matchedCache;
    }

    public long[] getMatchedUnsorted() {
        if (matchedCache != null) {
            return matchedCache;
        }
        if (entries.length == last) {
            this.matchedCache = entries;
            return entries;
        }
        long[] newArray = new long[last];
        System.arraycopy(entries, 0, newArray, 0, last);
        this.matchedCache = newArray;
        return matchedCache;
    }

    private void ensureCapacity(int minCapacity) {
        int oldCapacity = entries.length;
        if (minCapacity > oldCapacity) {
            int newCapacity = oldCapacity * 2;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity + (oldCapacity / 2);
            }
            long[] newArray = new long[newCapacity];
            System.arraycopy(entries, 0, newArray, 0, last);
            this.entries = newArray;
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(last * 2 + 2);
        buf.append('{');
        for (int i = 0; i < last; i++) {
            if (i != 0) {
                buf.append(',');
            }
            buf.append(entries[i]);
        }
        buf.append('}');
        return buf.toString();
    }

}
