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
package btree4j;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class FreeList {

    public final static int MAX_FREE_LIST_LENGTH = 128;

    private FreeSpace header = null;
    private FreeSpace last = null;
    private int size = 0;

    private final int maxFrees;

    public FreeList() {
        this(MAX_FREE_LIST_LENGTH);
    }

    public FreeList(int frees) {
        this.maxFrees = frees;
    }

    public void add(FreeSpace space) {
        if (header == null) {
            header = space;
            last = space;
        } else {
            last.next = space;
            space.prev = last;
            last = space;
        }
        ++size;
    }

    public void remove(FreeSpace space) {
        if (space.prev == null) {
            if (space.next != null) {
                space.next.prev = null;
                header = space.next;
            } else {
                header = null;
                last = null;
            }
        } else {
            space.prev.next = space.next;
            if (space.next != null) {
                space.next.prev = space.prev;
            } else {
                last = space.prev;
            }
        }
        --size;
    }

    /** selects a page with the smallest possible page */
    public FreeSpace retrieve(int required) {
        FreeSpace next = header;
        FreeSpace found = null;
        while (next != null) {
            if (next.free >= required) {
                if (found == null) {
                    found = next;
                } else if (next.free < found.free) {
                    found = next;
                }
            }
            next = next.next;
        }
        return found;
    }

    public FreeSpace find(long pageNum) {
        FreeSpace next = header;
        while (next != null) {
            if (next.page == pageNum) {
                return next;
            }
            next = next.next;
        }
        return null;
    }

    public void write(DataOutput out) throws IOException {
        int skip = 0;
        if (size > maxFrees) {
            skip = size - maxFrees;
        }
        int outSize = size - skip;

        out.writeInt(outSize);

        FreeSpace next = header;
        while (next != null) {
            if (skip == 0) {
                out.writeLong(next.page);
                out.writeInt(next.free);
            } else {
                --skip;
            }
            next = next.next;
        }
    }

    public void read(DataInput in) throws IOException {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            long page = in.readLong();
            int free = in.readInt();
            add(new FreeSpace(page, free));
        }
    }

    public static final class FreeSpace {

        public static final int MIN_LEFT_FREE = 64;

        private final long page;

        private int free = 0;
        private FreeSpace prev = null;
        private FreeSpace next = null;

        public FreeSpace(long page, int free) {
            this.page = page;
            this.free = free;
        }

        public long getPage() {
            return page;
        }

        public int getFree() {
            return free;
        }

        public void setFree(int free) {
            this.free = free;
        }
    }

}
