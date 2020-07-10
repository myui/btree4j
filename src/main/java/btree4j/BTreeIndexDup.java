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

import btree4j.utils.collections.longs.LongArrayList;
import btree4j.utils.collections.longs.LongHash.LongLRUMap;
import btree4j.utils.lang.Primitives;
import btree4j.utils.lang.PrintUtils;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An optimized version of duplicate-allowed {@link BTreeIndex}.
 */
public final class BTreeIndexDup extends BTreeIndex {
    private static final Log LOG = LogFactory.getLog(BTreeIndexDup.class);

    private final LongLRUMap<MultiPtrs> ptrsCache = new LongLRUMap<MultiPtrs>(512);

    public BTreeIndexDup(File file) {
        super(file, false);
        BFileHeader fh = getFileHeader();
        fh.setMultiValue(true);
    }

    public BTreeIndexDup(File file, int pageSize, int idxCaches, int dataCaches) {
        super(file, pageSize, idxCaches, dataCaches, false);
        BFileHeader fh = getFileHeader();
        fh.setMultiValue(true);
    }

    @Override
    public synchronized long addValue(final Value key, final Value value) throws BTreeException {
        final long valuePtr = storeValue(value);
        final long ptr = findValue(key);
        if (ptr != KEY_NOT_FOUND) {// key found
            // update the page
            MultiPtrs ptrs;
            synchronized (ptrsCache) {
                ptrs = ptrsCache.get(ptr);
                if (ptrs == null) {//TODO concurrent insertion is too slow..
                    byte[] ptrTuple = retrieveTuple(ptr);
                    ptrs = MultiPtrs.readFrom(ptrTuple);
                    ptrsCache.put(ptr, ptrs);
                }
            }
            ptrs.addPointer(valuePtr);
            updateValue(ptr, ptrs);
            return ptr;
        } else {
            // insert a new key           .
            MultiPtrs ptrs = new MultiPtrs(valuePtr);
            long newPtr = storeValue(ptrs);
            addValue(key, newPtr);
            synchronized (ptrsCache) {
                ptrsCache.put(newPtr, ptrs);
            }
            return newPtr;
        }
    }

    @Override
    protected BTreeCallback getHandler(BTreeCallback handler) {
        return new MultiValuesCallback(handler);
    }

    private final class MultiValuesCallback implements BTreeCallback {

        final BTreeCallback handler;

        public MultiValuesCallback(BTreeCallback handler) {
            this.handler = handler;
        }

        public boolean indexInfo(Value key, long pointer) {
            MultiPtrs ptrs;
            synchronized (ptrsCache) {
                ptrs = ptrsCache.get(pointer);
                if (ptrs == null) {
                    final byte[] ptrTuple;
                    try {
                        ptrTuple = retrieveTuple(pointer);
                    } catch (BTreeException e) {
                        throw new IllegalStateException(e);
                    }
                    ptrs = MultiPtrs.readFrom(ptrTuple);
                }
                ptrsCache.put(pointer, ptrs);
            }
            final LongArrayList lptrs = ptrs.getPointers();
            final int size = lptrs.size();
            for (int i = 0; i < size; i++) {
                final long lptr = lptrs.get(i);
                final byte[] value;
                try {
                    value = retrieveTuple(lptr);
                } catch (BTreeException e) {
                    LOG.error(PrintUtils.prettyPrintStackTrace(e));
                    throw new IllegalStateException(e);
                }
                if (!handler.indexInfo(key, value)) {
                    return false;
                }
            }
            return true;
        }

        public boolean indexInfo(Value key, byte[] value) {
            throw new UnsupportedOperationException();
        }
    }

    static final class MultiPtrs extends Value {
        static final int HEADER_LENGTH = 8;

        private LongArrayList _ptrs;
        private int _used;
        private int _free;

        public MultiPtrs() {
            super();
        }

        public MultiPtrs(long ptr) {
            super(initData(ptr));
            this._ptrs = new LongArrayList(4);
            _ptrs.add(ptr);
            this._used = 1;
            this._free = 3;
        }

        private MultiPtrs(byte[] b, long[] ptrs, int used, int free) {
            super(b);
            this._ptrs = new LongArrayList(ptrs, used);
            this._used = used;
            this._free = free;
        }

        public LongArrayList getPointers() {
            return _ptrs;
        }

        public void addPointer(final long ptr) {
            final byte[] oldData = _data;
            final int offset = HEADER_LENGTH + (_used << 3);
            _ptrs.add(ptr);
            _used++;
            if ((_free--) > 0) {
                Primitives.putInt(oldData, 0, _used);
                Primitives.putInt(oldData, 4, _free);
                Primitives.putLong(oldData, offset, ptr);
            } else {
                this._free = _used; // doubling spaces
                int newLen = HEADER_LENGTH + (_used << 4); //8 + ((_used + _free) * 8);
                final byte[] newData = new byte[newLen];

                System.arraycopy(oldData, 0, newData, 0, oldData.length);
                Primitives.putInt(newData, 0, _used);
                Primitives.putInt(newData, 4, _free);
                Primitives.putLong(newData, offset, ptr);

                this._data = newData;
                this._pos = 0;
                this._len = newLen;
            }
        }

        private static byte[] initData(long ptr) {
            final byte[] b = new byte[40]; // HEADER_LENGTH + 8 + (8 * 3)
            Primitives.putInt(b, 0, 1); // used
            Primitives.putInt(b, 4, 3); // free
            Primitives.putLong(b, 8, ptr);
            return b;
        }

        public static MultiPtrs readFrom(byte[] b) {
            final int used = Primitives.getInt(b, 0);
            final int free = Primitives.getInt(b, 4);
            final long[] ptrs = new long[(used * 3) / 2];
            for (int i = 0, idx = 8; i < used; i++, idx += 8) {
                ptrs[i] = Primitives.getLong(b, idx);
            }
            return new MultiPtrs(b, ptrs, used, free);
        }

    }
}
