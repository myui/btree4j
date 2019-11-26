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

import btree4j.FreeList.FreeSpace;
import btree4j.indexer.IndexQuery;
import btree4j.utils.collections.LRUMap;
import btree4j.utils.collections.longs.LongHash.BucketEntry;
import btree4j.utils.collections.longs.LongHash.Cleaner;
import btree4j.utils.collections.longs.PurgeOptObservableLongLRUMap;
import btree4j.utils.lang.Primitives;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BTreeIndex extends BTree {

    private static final byte DATA_RECORD = 10;

    public static final int DATA_CACHE_SIZE;
    public static final int DATA_CACHE_PURGE_UNIT;
    static {
        DATA_CACHE_SIZE = Primitives.parseInt(Settings.get("btree4j.bfile.datacache_size"), 2048); // 4k * 2048 = 8m
        DATA_CACHE_PURGE_UNIT =
                Primitives.parseInt(Settings.get("btree4j.bfile.datacache_purgeunit"), 16); // 4k * 16 = 64k
    }

    private final PurgeOptObservableLongLRUMap<DataPage> dataCache;
    private final int numDataCaches;
    private final Map<Value, Long> storeCache = new LRUMap<Value, Long>(64);

    public BTreeIndex(File file) {
        this(file, true);
    }

    public BTreeIndex(File file, boolean duplicateAllowed) {
        this(file, DEFAULT_IN_MEMORY_NODES, duplicateAllowed);
    }

    public BTreeIndex(File file, int idxPageCaches, boolean duplicateAllowed) {
        this(file, DEFAULT_PAGESIZE, idxPageCaches, DATA_CACHE_SIZE, duplicateAllowed);
    }

    public BTreeIndex(File file, int pageSize, int idxPageCaches, int dataPageCaches,
            boolean duplicateAllowed) {
        super(file, pageSize, idxPageCaches, duplicateAllowed);
        final Synchronizer sync = new Synchronizer();
        this.dataCache = new PurgeOptObservableLongLRUMap<DataPage>(dataPageCaches,
            DATA_CACHE_PURGE_UNIT, sync);
        this.numDataCaches = dataPageCaches;
    }

    public void setBulkloading(boolean enable, float nodeCachePurgePerc, float dataCachePurgePerc) {
        setBulkloading(enable, nodeCachePurgePerc);
        if (enable) {
            if (dataCachePurgePerc <= 0 || dataCachePurgePerc > 1) {
                throw new IllegalArgumentException(
                    "dataCachePurgePerc is illegal as percentage: " + nodeCachePurgePerc);
            }
            int units = Math.max((int) (numDataCaches * dataCachePurgePerc), numDataCaches);
            dataCache.setPurgeUnits(units);
        } else {
            dataCache.setPurgeUnits(numDataCaches);
        }
    }

    @Override
    protected BFileHeader createFileHeader(int pageSize) {
        return new BFileHeader(pageSize);
    }

    @Override
    protected BFileHeader getFileHeader() {
        return (BFileHeader) super.getFileHeader();
    }

    @Override
    protected BFilePageHeader createPageHeader() {
        return new BFilePageHeader();
    }

    @Nullable
    public final byte[] getValueBytes(long key) throws BTreeException {
        return getValueBytes(new Value(key));
    }

    @Nullable
    public synchronized byte[] getValueBytes(@Nonnull Value key) throws BTreeException {
        final long ptr = findValue(key);
        if (ptr == KEY_NOT_FOUND) {
            return null;
        }
        return retrieveTuple(ptr);
    }

    protected synchronized final byte[] retrieveTuple(long ptr) throws BTreeException {
        long pageNum = getPageNumFromPointer(ptr);
        DataPage dataPage = getDataPage(pageNum);
        int tidx = getTidFromPointer(ptr);
        return dataPage.get(tidx);
    }

    @Nullable
    public Value getValue(@Nonnull Value key) throws BTreeException {
        final byte[] tuple = getValueBytes(key);
        if (tuple == null) {
            return null;
        }
        return new Value(tuple);
    }

    @Override
    public final void search(IndexQuery query, BTreeCallback callback) throws BTreeException {
        super.search(query, getHandler(callback));
    }

    protected BTreeCallback getHandler(BTreeCallback handler) {
        return new BFileCallback(handler);
    }

    public final long addValue(long key, @Nonnull byte[] value) throws BTreeException {
        return addValue(new Value(key), new Value(value));
    }

    public final long addValue(@Nonnull Value key, @Nonnull byte[] value) throws BTreeException {
        return addValue(key, new Value(value));
    }

    /**
     * @return pointer to the inserted record
     */
    public synchronized long addValue(@Nonnull Value key, @Nonnull Value value)
            throws BTreeException {
        long ptr = findValue(key);
        if (ptr != KEY_NOT_FOUND) {// key found
            // update the page
            if (!isDuplicateAllowed()) {
                updateValue(ptr, value);
                return ptr;
            }
        }
        // insert a new key
        ptr = storeValue(value);
        addValue(key, ptr);
        return ptr;
    }

    public final long putValue(@Nonnull Value key, @Nonnull byte[] value) throws BTreeException {
        return putValue(key, new Value(value));
    }

    public synchronized long putValue(@Nonnull Value key, @Nonnull Value value)
            throws BTreeException {
        long ptr = findValue(key);
        if (ptr != KEY_NOT_FOUND) {
            // update the page
            updateValue(ptr, value);
            return ptr;
        } else {
            // insert a new key
            ptr = storeValue(value);
            addValue(key, ptr);
            return ptr;
        }
    }

    protected final void updateValue(long ptr, @Nonnull Value value) throws BTreeException {
        long pageNum = getPageNumFromPointer(ptr);
        DataPage dataPage = getDataPage(pageNum);
        int tidx = getTidFromPointer(ptr);
        dataPage.set(tidx, value);
    }

    protected final long storeValue(@Nonnull Value value) throws BTreeException {
        final Long cachedPtr = storeCache.get(value);
        if (cachedPtr != null) {
            return cachedPtr.longValue();
        }

        final BFileHeader fh = getFileHeader();
        final FreeList freeList = fh.getFreeList();

        final int requiredSize = value.getLength() + 4;

        final DataPage dataPage;
        FreeSpace free = freeList.retrieve(requiredSize);
        if (free == null) {
            DataPage newPage = createDataPage();
            free = new FreeSpace(newPage.getPageNum(), fh.getWorkSize());
            freeList.add(free);
            dataPage = newPage;
        } else {
            dataPage = getDataPage(free.getPage());
        }

        final long pageNum = dataPage.getPageNum();
        final int tid = dataPage.add(value);

        saveFreeList(freeList, free, dataPage);

        long ptr = createPointer(pageNum, tid);
        storeCache.put(value, ptr);
        return ptr;
    }

    private void saveFreeList(@Nonnull FreeList freeList, @Nullable FreeSpace free,
            @Nonnull DataPage dataPage) {
        final BFileHeader fh = getFileHeader();
        final int leftFree = fh.getWorkSize() - dataPage.getTotalDataLen();
        if (free != null) {
            free.setFree(leftFree);
            if (leftFree < FreeSpace.MIN_LEFT_FREE) {
                freeList.remove(free);
            }
        } else {
            if (leftFree >= FreeSpace.MIN_LEFT_FREE) {
                FreeSpace newFree = new FreeSpace(dataPage.getPageNum(), leftFree);
                freeList.add(newFree);
            }
        }
    }

    public synchronized byte[][] remove(Value key) throws BTreeException {
        final List<byte[]> list = new ArrayList<byte[]>(4);
        while (true) {
            final long ptr = findValue(key);
            if (ptr == KEY_NOT_FOUND) {// key found
                break;
            }
            final byte[] v = removeValue(ptr);
            if (v != null) {
                storeCache.remove(new Value(v));
                list.add(v);
            }
            super.removeValue(key, ptr);
        }
        if (list.isEmpty()) {
            return null;
        }
        final byte[][] ary = new byte[list.size()][];
        return list.toArray(ary);
    }

    protected final byte[] removeValue(long ptr) throws BTreeException {
        long pageNum = getPageNumFromPointer(ptr);
        DataPage dataPage = getDataPage(pageNum);
        int tidx = getTidFromPointer(ptr);
        final byte[] b = dataPage.remove(tidx);

        /*
        // adjust the free-list
        BFileHeader fh = getFileHeader();
        FreeList freeList = fh.getFreeList();
        FreeSpace free = freeList.find(pageNum);
        saveFreeList(freeList, free, dataPage);
         */

        return b;
    }

    private DataPage createDataPage() throws BTreeException {
        Page p = getFreePage();
        DataPage dataPage = new DataPage(p);
        dataCache.put(p.getPageNum(), dataPage);
        return dataPage;
    }

    private DataPage getDataPage(long pageNum) throws BTreeException {
        DataPage dataPage = dataCache.get(pageNum);
        if (dataPage == null) {
            Page p = getPage(pageNum);
            dataPage = new DataPage(p);
            try {
                dataPage.read();
            } catch (IOException e) {
                throw new BTreeException("failed to read page#" + pageNum, e);
            }
            dataCache.put(pageNum, dataPage);
        }
        return dataPage;
    }

    private final class DataPage implements Comparable<DataPage> {
        private final Page page;
        private final BFilePageHeader ph;

        private final List<byte[]> tuples = new ArrayList<byte[]>(12);
        private int totalDataLen = 0;

        private boolean loaded = false;
        private boolean dirty = false;

        public DataPage(Page page) {
            this.page = page;
            ph = (BFilePageHeader) page.getPageHeader();
            ph.setStatus(DATA_RECORD);
        }

        public long getPageNum() {
            return page.getPageNum();
        }

        public int getTotalDataLen() {
            return totalDataLen;
        }

        public int add(Value value) {
            final int idx = tuples.size();
            if (idx > Short.MAX_VALUE) {
                throw new IllegalStateException("blocks length exceeds limit: " + idx);
            }

            byte[] tuple = value.getData();
            tuples.add(tuple);

            // update controls
            ph.incrTupleCount();
            totalDataLen += (tuple.length + 4);
            //ph.setDataLength(totalDataLen);
            setDirty();

            return idx;
        }

        public void set(int tidx, Value value) {
            if (tidx >= tuples.size()) {
                throw new IllegalStateException(
                    "Illegal tid for DataPage#" + page.getPageNum() + ": " + tidx);
            }
            final byte[] tuple = value.getData();
            final byte[] oldTuple = tuples.set(tidx, tuple);
            if (oldTuple != null) {
                int diff = tuple.length - oldTuple.length;
                totalDataLen += diff;
                //ph.setDataLength(totalDataLen);
            }
            setDirty();
        }

        public byte[] remove(int tidx) throws BTreeException {
            final int size = tuples.size();
            if (tidx >= size) {
                throw new IllegalStateException("Index out of range: " + tidx);
            }
            final byte[] tuple = tuples.get(tidx); // TODO REVIEWME storeCache. remove effects other tids.
            this.dirty = true;
            if (ph.decrTupleCount() == 0) {
                dataCache.remove(page.getPageNum());
                unlinkPages(page);
            }
            return tuple;
        }

        private void setDirty() {
            this.dirty = true;
            dataCache.put(page.getPageNum(), this);
        }

        @Nullable
        public byte[] get(int tidx) {
            if (tidx >= tuples.size()) {
                return null; // REVIEWME
            }
            return tuples.get(tidx);
        }

        public void read() throws BTreeException, IOException {
            if (loaded) {
                return; // should never happens (just for debugging)
            }
            final int tupleCount = ph.getTupleCount();
            if (tupleCount == 0) {
                return;
            }
            Value v = readValue(page);
            DataInputStream in = new DataInputStream(v.getInputStream());
            for (int i = 0; i < tupleCount; i++) {
                int len = in.readInt();
                byte[] tuple = new byte[len];
                in.read(tuple);
                tuples.add(tuple);
            }
            if (in.available() > 0) {
                throw new IllegalStateException(in.available() + " bytes left");
            }
            this.totalDataLen = v.getLength();
            this.loaded = true;
        }

        public void write() throws BTreeException {
            if (!dirty) {
                return;
            }
            if (totalDataLen == 0) {
                return;
            }
            final byte[] dest = new byte[totalDataLen];
            int pos = 0;
            for (byte[] tuple : tuples) {
                assert (tuple != null);
                final int len = tuple.length;
                Primitives.putInt(dest, pos, len);
                pos += 4;
                System.arraycopy(tuple, 0, dest, pos, len);
                pos += len;
            }
            if (pos != totalDataLen) {
                throw new IllegalStateException(
                    "writes = " + pos + ", but totalDataLen = " + totalDataLen);
            }
            writeValue(page, new Value(dest));
            this.dirty = false;
        }

        @Override
        public int compareTo(DataPage other) {
            return page.compareTo(other.page);
        }
    }

    protected final class BFileHeader extends BTreeFileHeader {

        private final FreeList freeList = new FreeList(128);
        private boolean multiValue = false;

        public BFileHeader(int pageSize) {
            super(pageSize);
        }

        public FreeList getFreeList() {
            return freeList;
        }

        public void setMultiValue(boolean multi) {
            this.multiValue = multi;
        }

        @Override
        public void read(RandomAccessFile raf) throws IOException {
            super.read(raf);
            this.multiValue = raf.readBoolean();
            freeList.read(raf);
        }

        @Override
        public void write(RandomAccessFile raf) throws IOException {
            super.write(raf);
            raf.writeBoolean(multiValue);
            freeList.write(raf);
        }
    }

    private final class BFilePageHeader extends BTreePageHeader {

        private int tupleCount = 0;

        public BFilePageHeader() {
            super();
        }

        public int getTupleCount() {
            return tupleCount;
        }

        public int incrTupleCount() {
            return tupleCount++;
        }

        public int decrTupleCount() {
            return tupleCount--;
        }

        @Override
        public void read(ByteBuffer buf) {
            super.read(buf);
            this.tupleCount = buf.getInt();
        }

        @Override
        public void write(ByteBuffer buf) {
            super.write(buf);
            buf.putInt(tupleCount);
        }

    }

    private final class BFileCallback implements BTreeCallback {

        final BTreeCallback handler;

        public BFileCallback(BTreeCallback handler) {
            this.handler = handler;
        }

        public boolean indexInfo(Value key, long pointer) {
            final byte[] tuple;
            try {
                tuple = retrieveTuple(pointer);
            } catch (BTreeException e) {
                throw new IllegalStateException(e);
            }
            return handler.indexInfo(key, tuple);
        }

        public boolean indexInfo(Value key, byte[] value) {
            throw new UnsupportedOperationException();
        }
    }

    private static long createPointer(long pageNum, int tid) {
        if (pageNum > 0x7fffffffffffL) {// over 6 bytes
            throw new IllegalArgumentException(
                "Unexpected pageNumber that exceeds system limit: " + pageNum);
        }
        if (tid > 0x7fff) {// over 4 bytes
            throw new IllegalArgumentException("Illegal idx that exceeds system limit: " + tid);
        }
        return tid | ((pageNum & 0xffffffffffffL) << 16);
    }

    private static long getPageNumFromPointer(long ptr) {
        return ptr >>> 16;
    }

    private static int getTidFromPointer(long ptr) {
        return (int) (ptr & 0xffffL);
    }

    private static final class Synchronizer implements Cleaner<DataPage> {

        public Synchronizer() {}

        @Override
        public void cleanup(long key, DataPage dataPage) {
            try {
                dataPage.write();
            } catch (BTreeException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void flush() throws BTreeException {
        flush(true, false);
    }

    @Override
    public synchronized void flush(boolean purge, boolean clear) throws BTreeException {
        if (purge) {
            for (BucketEntry<DataPage> e : dataCache) {
                DataPage dataPage = e.getValue();
                dataPage.write();
            }
        }
        if (clear) {
            dataCache.clear();
        }
        super.flush(purge, clear);
    }
}
