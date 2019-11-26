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
 * Copyright 1999-2004 The Apache Software Foundation.
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
package btree4j;

import btree4j.indexer.BasicIndexQuery;
import btree4j.indexer.IndexQuery;
import btree4j.utils.codec.VariableByteCodec;
import btree4j.utils.collections.longs.LongHash.BucketEntry;
import btree4j.utils.collections.longs.LongHash.Cleaner;
import btree4j.utils.collections.longs.PurgeOptObservableLongLRUMap;
import btree4j.utils.io.FastMultiByteArrayOutputStream;
import btree4j.utils.lang.ArrayUtils;
import btree4j.utils.lang.Primitives;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * BTree represents a Variable Magnitude Simple-Prefix B+Tree File.
 */
public class BTree extends Paged {
    private static final Log LOG = LogFactory.getLog(BTree.class);

    /** If page size is 4k, 16m (4k * 4096) cache */
    public static final int DEFAULT_IN_MEMORY_NODES;
    private static final int BTREE_NODECACHE_PURGE_UNIT;
    static {
        DEFAULT_IN_MEMORY_NODES =
                Primitives.parseInt(Settings.get("btree4j.btree.nodecache_size"), 4096); // 16m
        BTREE_NODECACHE_PURGE_UNIT =
                Primitives.parseInt(Settings.get("btree4j.bfile.nodecache_purgeunit"), 8); // 32k
    }

    public static final int KEY_NOT_FOUND = -1;
    private static final int LEAST_KEYS = 5;

    private static final byte[] EmptyBytes = new byte[0];
    private static final Value EmptyValue = new Value(EmptyBytes);

    protected static final byte LEAF = 1;
    protected static final byte BRANCH = 2;

    /**
     * Cache of the recently used tree nodes.
     *
     * Cache contains weak references to the BTreeNode objects, keys are page numbers (Long
     * objects). Access synchronized by this map itself.
     */
    @Nonnull
    private final PurgeOptObservableLongLRUMap<BTreeNode> _cache;
    private final int numNodeCaches;

    @Nonnull
    private final BTreeFileHeader _fileHeader;

    private BTreeRootInfo _rootInfo;
    private BTreeNode _rootNode;

    public BTree(@Nonnull File file) {
        this(file, true);
    }

    public BTree(@Nonnull File file, boolean duplicateAllowed) {
        this(file, DEFAULT_PAGESIZE, DEFAULT_IN_MEMORY_NODES, duplicateAllowed);
    }

    public BTree(@Nonnull File file, @Nonnegative int pageSize, int caches,
            boolean duplicateAllowed) {
        super(file, pageSize);
        BTreeFileHeader fh = getFileHeader();
        fh.incrTotalPageCount(); // for root page
        fh._duplicateAllowed = duplicateAllowed;
        this._fileHeader = fh;
        final Synchronizer sync = new Synchronizer();
        this._cache = new PurgeOptObservableLongLRUMap<BTreeNode>(caches,
            BTREE_NODECACHE_PURGE_UNIT, sync);
        this.numNodeCaches = caches;
    }

    public void init(boolean bulkload) throws BTreeException {
        if (!exists()) {
            boolean created = create(false);
            if (!created) {
                throw new IllegalStateException(
                    "create B+Tree file failed: " + _file.getAbsolutePath());
            }
        } else {
            open();
        }
    }

    public void setBulkloading(boolean enable, float nodeCachePurgePerc) {
        if (enable) {
            if (nodeCachePurgePerc <= 0 || nodeCachePurgePerc > 1) {
                throw new IllegalArgumentException(
                    "nodeCachePurgePerc is illegal as percentage: " + nodeCachePurgePerc);
            }
            int units = Math.max((int) (numNodeCaches * nodeCachePurgePerc), numNodeCaches);
            _cache.setPurgeUnits(units);
        } else {
            _cache.setPurgeUnits(numNodeCaches);
        }
    }

    private static final class Synchronizer implements Cleaner<BTreeNode> {

        Synchronizer() {}

        @Override
        public void cleanup(long key, @Nonnull BTreeNode node) {
            if (!node.dirty) {
                return;
            }
            try {
                node.write();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (BTreeException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    @Override
    public boolean open() throws BTreeException {
        if (super.open()) {
            long p = _fileHeader.getRootPage();
            this._rootInfo = new BTreeRootInfo(p);
            this._rootNode = getBTreeNode(_rootInfo, p, null);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean create(boolean close) throws BTreeException {
        if (super.create(false)) {
            // Don't call this.open() as it will try to read rootNode from the disk
            super.open();
            // Initialize root node
            long p = _fileHeader.getRootPage();
            this._rootInfo = new BTreeRootInfo(p);
            this._rootNode = new BTreeNode(_rootInfo, getPage(p), null);
            _rootNode.ph.setStatus(LEAF);
            _rootNode.set(new Value[0], new long[0]);
            try {
                _rootNode.write();
            } catch (IOException e) {
                throw new BTreeException(e);
            }
            synchronized (_cache) {
                _cache.put(_rootNode.page.getPageNum(), _rootNode);
            }
            if (close) {
                close();
            }
            return true;
        }
        return false;
    }

    protected final boolean isDuplicateAllowed() {
        return _fileHeader._duplicateAllowed;
    }

    /**
     * addValue adds a Value to the BTree and associates a pointer with it. The pointer can be used
     * for referencing any type of data.
     *
     * @param key The Value to add
     * @param pointer The pointer to associate with it
     * @return The previous value for the pointer (or -1)
     */
    public synchronized long addValue(@Nonnull Value key, long pointer) throws BTreeException {
        try {
            return _rootNode.addValue(key, pointer);
        } catch (IOException e) {
            throw new BTreeException(e);
        }
    }

    /**
     * removeValue removes a Value from the BTree and returns the associated pointer for it.
     *
     * @param key The Value to remove
     * @return The pointer that was associated with it
     */
    public synchronized long removeValue(@Nonnull Value key) throws BTreeException {
        try {
            return _rootNode.removeValue(key);
        } catch (IOException e) {
            throw new BTreeException(e);
        }
    }

    /**
     * Removed specified key/pointer pair(s) in the index.
     *
     * @return The number of matched items.
     */
    public synchronized int removeValue(@Nonnull Value key, long pointer) throws BTreeException {
        try {
            return _rootNode.removeValue(key, pointer);
        } catch (IOException e) {
            throw new BTreeException(e);
        }
    }

    /**
     * findValue finds a Value in the BTree and returns the associated pointer for it.
     *
     * @param key The key to find
     * @return The pointer associated with the given key
     */
    public synchronized long findValue(@Nonnull Value key) throws BTreeException {
        return _rootNode.findValue(key);
    }

    public enum SearchType {
        LEFT_MOST, LEFT /* normal */, RIGHT, RIGHT_MOST
    }

    /**
     * query performs a query against the BTree and performs callback operations to report the
     * search results.
     *
     * @param query The IndexQuery to use
     * @param callback The callback instance
     */
    public synchronized void search(@Nonnull IndexQuery query, @Nonnull BTreeCallback callback)
            throws BTreeException {
        final BTreeNode root = _rootNode;
        final Value[] keys = query.getOperands();
        final int op = query.getOperator();
        try {
            switch (op) {
                case BasicIndexQuery.EQ: {
                    if (isDuplicateAllowed()) {
                        BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                        BTreeNode right = root.getLeafNode(SearchType.RIGHT, keys[0]);
                        scanRange(left, right, query, callback);
                    } else {
                        BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                        left.scanLeaf(query, callback, true);
                    }
                    break;
                }
                case BasicIndexQuery.GT:
                case BasicIndexQuery.GE: {
                    BTreeNode right = root.getLeafNode(SearchType.LEFT, keys[keys.length - 1]);
                    BTreeNode rightmost = root.getLeafNode(SearchType.RIGHT_MOST, null);
                    scanRange(right, rightmost, query, callback);
                    break;
                }
                case BasicIndexQuery.LE:
                case BasicIndexQuery.LT: {
                    BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                    BTreeNode leftmost = root.getLeafNode(SearchType.LEFT_MOST, null);
                    scanRange(leftmost, left, query, callback);
                    break;
                }
                case BasicIndexQuery.NE:
                case BasicIndexQuery.NBW:
                case BasicIndexQuery.NOT_IN:
                case BasicIndexQuery.NOT_START_WITH:
                case BasicIndexQuery.NBWX: {
                    BTreeNode leftmost = root.getLeafNode(SearchType.LEFT_MOST, null);
                    BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                    BTreeNode rightmost = root.getLeafNode(SearchType.RIGHT_MOST, null);
                    BTreeNode right = root.getLeafNode(SearchType.RIGHT, keys[keys.length - 1]);
                    scanRange(leftmost, left, query, callback);
                    long lp = left.page.getPageNum(), rp = right.page.getPageNum();
                    if (lp != rp) {
                        scanRange(right, rightmost, query, callback);
                    }
                    break;
                }
                case BasicIndexQuery.BW:
                case BasicIndexQuery.START_WITH:
                case BasicIndexQuery.IN:
                case BasicIndexQuery.BWX: {
                    BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                    BTreeNode right = root.getLeafNode(SearchType.RIGHT, keys[keys.length - 1]);
                    scanRange(left, right, query, callback);
                    break;
                }
                default: {
                    BTreeNode leftmost = root.getLeafNode(SearchType.LEFT_MOST, null);
                    BTreeNode rightmost = root.getLeafNode(SearchType.RIGHT_MOST, null);
                    scanRange(leftmost, rightmost, query, callback);
                    break;
                }
            }
        } catch (IOException e) {
            throw new BTreeException(e);
        }
    }

    private final void scanRange(@Nonnull BTreeNode left, @Nonnull BTreeNode right,
            @Nonnull IndexQuery query, @Nonnull BTreeCallback callback) throws BTreeException {
        final long rightmostPageNum = right.page.getPageNum();
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                "scan range [" + left.page.getPageNum() + ", " + rightmostPageNum + "] start");
        }
        BTreeNode cur = left;
        int scaned = 0;
        while (true) {
            long curPageNum = cur.page.getPageNum();
            if (curPageNum == rightmostPageNum) {
                cur.scanLeaf(query, callback, true);
                ++scaned;
                break;
            } else {
                cur.scanLeaf(query, callback, scaned == 0);
                ++scaned;
            }
            long next = cur._next;
            if (next == curPageNum) {
                throw new IllegalStateException("detected a cyclic link at page#" + curPageNum);
            } else if (next == -1L) {
                throw new IllegalStateException("range scan failed... bug?");
            }
            cur = getBTreeNode(_rootInfo, next, null);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("scan range end. total scaned pages: " + scaned);
        }
    }

    @Override
    protected FileHeader createFileHeader(int pageSize) {
        return new BTreeFileHeader(pageSize);
    }

    @Override
    protected PageHeader createPageHeader() {
        return new BTreePageHeader();
    }

    @Override
    protected BTreeFileHeader getFileHeader() {
        return (BTreeFileHeader) super.getFileHeader();
    }

    /**
     * getRootNode retrieves the BTree node for the specified root object.
     *
     * @param root The root object to retrieve with
     * @return The root node
     */
    protected final BTreeNode getRootNode(BTreeRootInfo root) throws BTreeException {
        if (root.page == _rootInfo.page) {
            return _rootNode;
        } else {
            return getBTreeNode(root, root.getPage(), null);
        }
    }

    private final BTreeNode getBTreeNode(BTreeRootInfo root, long page, BTreeNode parent)
            throws BTreeException {
        BTreeNode node;
        synchronized (_cache) {
            node = _cache.get(page);
            if (node == null) {
                node = new BTreeNode(root, getPage(page), parent);
                try {
                    node.read();
                } catch (IOException e) {
                    throw new BTreeException("failed to read page#" + page, e);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("read node page#" + page + ", keys: " + node.keys.length);
                }
                _cache.put(page, node);
            } else {
                if (parent != null) {
                    node.setParent(parent);
                }
            }
        }
        return node;
    }

    private final BTreeNode getBTreeNode(BTreeRootInfo root, long page) throws BTreeException {
        BTreeNode node;
        synchronized (_cache) {
            node = _cache.get(page);
            if (node == null) {
                node = new BTreeNode(root, getPage(page));
                try {
                    node.read();
                } catch (IOException e) {
                    throw new BTreeException("failed to read page#" + page, e);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("read node page#" + page + ", keys: " + node.keys.length);
                }
                _cache.put(page, node);
            }
        }
        return node;
    }

    private final BTreeNode createBTreeNode(BTreeRootInfo root, byte status,
            @CheckForNull BTreeNode parent) throws BTreeException {
        if (parent == null) {
            throw new IllegalArgumentException();
        }
        Page p = getFreePage();
        BTreeNode node = new BTreeNode(root, p, parent);
        //node.set(new Value[0], new long[0]);
        node.ph.setStatus(status);
        synchronized (_cache) {
            _cache.put(p.getPageNum(), node);
        }
        return node;
    }

    @Override
    public void flush() throws BTreeException {
        flush(true, false);
    }

    public synchronized void flush(boolean purge, boolean clear) throws BTreeException {
        if (purge) {
            try {
                for (BucketEntry<BTreeNode> e : _cache) {
                    BTreeNode node = e.getValue();
                    if (node != null) {
                        node.write();
                    }
                }
            } catch (IOException ioe) {
                throw new BTreeException(ioe);
            }
        }
        if (clear) {
            _cache.clear();
        }
        super.flush();
    }

    private static final class BTreeRootInfo {

        private final long page;

        private BTreeRootInfo(long page) {
            this.page = page;
        }

        public long getPage() {
            return page;
        }
    }

    private final class BTreeNode implements Comparable<BTreeNode> {

        private final BTreeRootInfo root;
        private final Page page;
        private final BTreePageHeader ph;

        // cached entry
        private BTreeNode parentCache;

        private Value[] keys;
        private long[] ptrs;
        private long _next = -1;
        private long _prev = -1; // internal entry for debugging
        private Value prefix = null;

        private boolean loaded = false;
        private int currentDataLen = -1;
        private boolean dirty = false;

        //--------------------------------------------

        protected BTreeNode(final BTreeRootInfo root, final Page page, final BTreeNode parentNode) {
            this.root = root;
            this.page = page;
            this.ph = (BTreePageHeader) page.getPageHeader();
            ph.setParent(parentNode);
            this.parentCache = parentNode;
        }

        protected BTreeNode(final BTreeRootInfo root, final Page page) {
            this.root = root;
            this.page = page;
            this.ph = (BTreePageHeader) page.getPageHeader();
        }

        private BTreeNode getParent() {
            if (parentCache != null) {
                return parentCache;
            }
            long page = ph.parentPage;
            if (page != Paged.NO_PAGE) {
                try {
                    parentCache = getBTreeNode(_rootInfo, page);
                } catch (BTreeException e) {
                    throw new IllegalStateException("failed to get parent page #" + page, e);
                }
                return parentCache;
            }
            return null;
        }

        private void setParent(BTreeNode node) {
            long parentPage = node.page.getPageNum();
            if (parentPage != ph.parentPage) {
                ph.parentPage = parentPage;
                this.parentCache = node;
                this.dirty = true;
            }
        }

        long addValue(@Nonnull Value key, final long pointer) throws IOException, BTreeException {
            int idx = searchRightmostKey(keys, key, keys.length);
            switch (ph.getStatus()) {
                case BRANCH: {
                    idx = idx < 0 ? -(idx + 1) : idx + 1;
                    return getChildNode(idx).addValue(key, pointer);
                }
                case LEAF: {
                    final boolean found = idx >= 0;
                    final long oldPtr;
                    if (found) {
                        if (!isDuplicateAllowed()) {
                            throw new BTreeCorruptException(
                                "Attempt to add duplicate key to the unique index: " + key);
                        }
                        oldPtr = ptrs[idx];
                        key = keys[idx]; // use the existing key object
                        idx = idx + 1;
                    } else {
                        oldPtr = -1;
                        idx = -(idx + 1);
                    }
                    set(ArrayUtils.<Value>insert(keys, idx, key),
                        ArrayUtils.insert(ptrs, idx, pointer));
                    incrDataLength(key, pointer);

                    // Check to see if we've exhausted the block
                    if (needSplit()) {
                        split();
                    }
                    return oldPtr;
                }
                default:
                    throw new BTreeCorruptException("Invalid Page Type '" + ph.getStatus()
                            + "' was detected for page#" + page.getPageNum());
            }
        }

        /** search the leftmost key for duplicate allowed index */
        private int searchLeftmostKey(final Value[] ary, final Value key, final int to) {
            if (!_fileHeader._duplicateAllowed) {
                return ArrayUtils.binarySearch(keys, 0, to, key);
            }
            int low = 0;
            int high = to - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                Value midVal = ary[mid];
                int cmp = midVal.compareTo(key);
                if (cmp < 0) {
                    low = mid + 1;
                } else if (cmp > 0) {
                    high = mid - 1;
                } else {
                    for (int i = mid - 1; i >= 0; i--) {
                        Value nxtVal = ary[i];
                        cmp = midVal.compareTo(nxtVal);
                        if (cmp != 0) {
                            break;
                        }
                        mid = i;
                    }
                    return mid; // key found
                }
            }
            return -(low + 1); // key not found.
        }

        /** search the rightmost key for duplicate allowed index */
        private int searchRightmostKey(final Value[] ary, final Value key, final int to) {
            if (!_fileHeader._duplicateAllowed) {
                return ArrayUtils.binarySearch(keys, 0, to, key);
            }
            int low = 0;
            int high = to - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                Value midVal = ary[mid];
                int cmp = midVal.compareTo(key);
                if (cmp < 0) {
                    low = mid + 1;
                } else if (cmp > 0) {
                    high = mid - 1;
                } else {
                    for (int i = mid + 1; i <= high; i++) {
                        Value nxtVal = ary[i];
                        cmp = midVal.compareTo(nxtVal);
                        if (cmp != 0) {
                            break;
                        }
                        mid = i;
                    }
                    return mid; // key found
                }
            }
            return -(low + 1); // key not found.
        }

        /** @return pointer of left-most matched item */
        long removeValue(Value searchKey) throws IOException, BTreeException {
            int leftIdx = searchLeftmostKey(keys, searchKey, keys.length);
            switch (ph.getStatus()) {
                case BRANCH:
                    leftIdx = (leftIdx < 0) ? -(leftIdx + 1) : leftIdx + 1;
                    return getChildNode(leftIdx).removeValue(searchKey);
                case LEAF:
                    if (leftIdx < 0) {
                        return KEY_NOT_FOUND;
                    } else {
                        long oldPtr = ptrs[leftIdx];
                        set(ArrayUtils.remove(keys, leftIdx), ArrayUtils.remove(ptrs, leftIdx));
                        decrDataLength(searchKey);
                        return oldPtr;
                    }
                default:
                    throw new BTreeCorruptException(
                        "Invalid page type '" + ph.getStatus() + "' in removeValue");
            }
        }

        /** @return the number of matched items */
        @Deprecated
        int removeValue(Value searchKey, long pointer) throws IOException, BTreeException {
            int leftIdx = searchLeftmostKey(keys, searchKey, keys.length);
            int rightIdx = isDuplicateAllowed() ? searchRightmostKey(keys, searchKey, keys.length)
                    : leftIdx;
            switch (ph.getStatus()) {
                case BRANCH: {
                    leftIdx = (leftIdx < 0) ? -(leftIdx + 1) : leftIdx + 1;
                    //FIXME keys may be separated nodes
                    return getChildNode(leftIdx).removeValue(searchKey, pointer);
                }
                case LEAF: {
                    if (leftIdx < 0) {
                        return 0;
                    } else {
                        int founds = 0;
                        for (int i = leftIdx; i <= rightIdx; i++) {
                            long p = ptrs[i];
                            if (p == pointer) {
                                set(ArrayUtils.remove(keys, i), ArrayUtils.remove(ptrs, i));
                                decrDataLength(searchKey);
                                i--;
                                rightIdx--;
                            }
                        }
                        return founds;
                    }
                }
                default:
                    throw new BTreeCorruptException(
                        "Invalid page type '" + ph.getStatus() + "' in removeValue");
            }
        }

        /**
         * Internal (to the BTreeNode) method. Because this method is called only by BTreeNode
         * itself, no synchronization done inside of this method.
         */
        @Nullable
        private BTreeNode getChildNode(final int idx) throws BTreeException {
            if (ph.getStatus() == BRANCH && idx >= 0 && idx < ptrs.length) {
                return getBTreeNode(root, ptrs[idx], this);
            }
            return null;
        }

        /**
         * Need to split this node after adding one more value?
         * 
         * @see #write()
         */
        private boolean needSplit() {
            int afterKeysLength = keys.length + 1;
            if (afterKeysLength < LEAST_KEYS) {// at least 5 elements in a node
                return false;
            }
            if (afterKeysLength > Short.MAX_VALUE) {
                return true;
            }
            assert (prefix != null);
            // CurrLength + one Long pointer + value length + one int (for value length)
            // actual datalen is smaller than this datalen, because prefix is used.
            int datalen = calculateDataLength();
            int worksize = _fileHeader.getWorkSize();
            return datalen > worksize;
        }

        /**
         * Internal to the BTreeNode method
         */
        private void split() throws IOException, BTreeException {
            final Value[] leftVals;
            final Value[] rightVals;
            final long[] leftPtrs;
            final long[] rightPtrs;
            final Value separator;

            final short vc = ph.getValueCount();
            int pivot = vc / 2;

            // Split the node into two nodes
            final byte pageType = ph.getStatus();
            int leftLookup = 0;
            switch (pageType) {
                case BRANCH: {
                    leftVals = new Value[pivot];
                    leftPtrs = new long[leftVals.length + 1];
                    rightVals = new Value[vc - (pivot + 1)];
                    rightPtrs = new long[rightVals.length + 1];

                    System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
                    System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
                    System.arraycopy(keys, leftVals.length + 1, rightVals, 0, rightVals.length);
                    System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);

                    separator = keys[leftVals.length];
                    break;
                }
                case LEAF: {
                    Value pivotLeft = keys[pivot - 1];
                    Value pivotRight = keys[pivot];
                    if (pivotLeft.equals(pivotRight)) {
                        int leftmost = searchLeftmostKey(keys, pivotLeft, pivot - 1);
                        int diff = pivot - leftmost;
                        if (diff < 0 || diff > Short.MAX_VALUE) {
                            throw new IllegalStateException("pivot: " + pivot + ", leftmost: "
                                    + leftmost + "\nkeys: " + Arrays.toString(keys));
                        }
                        leftLookup = diff;
                    }

                    leftVals = new Value[pivot];
                    leftPtrs = new long[leftVals.length];
                    rightVals = new Value[vc - pivot];
                    rightPtrs = new long[rightVals.length];

                    System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
                    System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
                    System.arraycopy(keys, leftVals.length, rightVals, 0, rightVals.length);
                    System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);

                    separator = getSeparator(leftVals[leftVals.length - 1], rightVals[0]);
                    break;
                }
                default:
                    throw new BTreeCorruptException("Invalid page type in split: " + pageType);
            }

            // Promote the pivot to the parent branch
            final BTreeNode parent = getParent(); // this node may be GC'd
            if (parent == null) {
                // This can only happen if this is the root     
                BTreeNode lNode = createBTreeNode(root, pageType, this);
                lNode.set(leftVals, leftPtrs);
                lNode.calculateDataLength();
                lNode.setAsParent();

                BTreeNode rNode = createBTreeNode(root, pageType, this);
                rNode.set(rightVals, rightPtrs);
                rNode.calculateDataLength();
                rNode.setAsParent();

                if (pageType == LEAF) {
                    setLeavesLinked(lNode, rNode);
                }

                ph.setStatus(BRANCH);
                set(new Value[] {separator},
                    new long[] {lNode.page.getPageNum(), rNode.page.getPageNum()});
                calculateDataLength();
            } else {
                set(leftVals, leftPtrs);
                calculateDataLength();

                BTreeNode rNode = createBTreeNode(root, pageType, parent);
                rNode.set(rightVals, rightPtrs);
                rNode.calculateDataLength();
                rNode.setAsParent();

                if (pageType == LEAF) {
                    setLeavesLinked(this, rNode);
                    if (leftLookup > 0) {
                        rNode.ph.setLeftLookup(leftLookup);
                    }
                }

                long leftPtr = page.getPageNum();
                long rightPtr = rNode.page.getPageNum();
                parent.promoteValue(separator, leftPtr, rightPtr);
            }
        }

        /** Set the parent-link in all child nodes to point to this node */
        private void setAsParent() throws BTreeException {
            if (ph.getStatus() == BRANCH) {
                for (final long ptr : ptrs) {
                    BTreeNode child = getBTreeNode(_rootInfo, ptr, this);
                    child.setParent(this);
                }
            }
        }

        /** Set leaves linked */
        private void setLeavesLinked(@Nonnull final BTreeNode left, @Nonnull final BTreeNode right)
                throws BTreeException {
            final long leftPageNum = left.page.getPageNum();
            final long rightPageNum = right.page.getPageNum();
            final long origNext = left._next;
            if (origNext != -1L) {
                right._next = origNext;
                BTreeNode origNextNode = getBTreeNode(root, origNext);
                origNextNode._prev = rightPageNum;
                origNextNode.setDirty(true);
            }
            left._next = rightPageNum;
            right._prev = leftPageNum;
        }

        private void promoteValue(@Nonnull final Value key, final long leftPtr, final long rightPtr)
                throws IOException, BTreeException {
            final int leftIdx = searchRightmostKey(keys, key, keys.length);
            int insertPoint = (leftIdx < 0) ? -(leftIdx + 1) : leftIdx + 1;
            boolean found = false;
            for (int i = insertPoint; i >= 0; i--) {
                final long ptr = ptrs[i];
                if (ptr == leftPtr) {
                    insertPoint = i;
                    found = true;
                    break;
                } else {
                    continue; // just for debugging
                }
            }
            if (!found) {
                throw new IllegalStateException(
                    "page#" + page.getPageNum() + ", insertPoint: " + insertPoint + ", leftPtr: "
                            + leftPtr + ", ptrs: " + Arrays.toString(ptrs));
            }
            set(ArrayUtils.<Value>insert(keys, insertPoint, key),
                ArrayUtils.insert(ptrs, insertPoint + 1, rightPtr));
            incrDataLength(key, rightPtr);

            // Check to see if we've exhausted the block
            if (needSplit()) {
                split();
            }
        }

        /** Gets shortest-possible separator for the pivot */
        private Value getSeparator(@Nonnull final Value value1, @Nonnull final Value value2) {
            int idx = value1.compareTo(value2);
            if (idx == 0) {
                return value1.clone();
            }
            byte[] b = new byte[Math.abs(idx)];
            value2.copyTo(b, 0, b.length);
            return new Value(b);
        }

        /**
         * Sets values and pointers. Internal (to the BTreeNode) method, not synchronized.
         */
        private void set(@Nonnull final Value[] values, @Nonnull final long[] ptrs) {
            final int vlen = values.length;
            if (vlen > Short.MAX_VALUE) {
                throw new IllegalArgumentException("entries exceeds limit: " + vlen);
            }
            this.keys = values;
            this.ptrs = ptrs;
            this.ph.setValueCount((short) vlen);
            if (vlen > 1) {
                final int prevPrefixLen = ph.getPrefixLength();
                this.prefix = getPrefix(values[0], values[vlen - 1]);
                final int prefixLen = prefix.getLength();
                assert (prefixLen <= Short.MAX_VALUE) : prefixLen;
                if (prefixLen != prevPrefixLen) {
                    int diff = prefixLen - prevPrefixLen;
                    currentDataLen += diff;
                    ph.setPrefixLength((short) prefixLen);
                }
            } else {
                this.prefix = EmptyValue;
                ph.setPrefixLength((short) 0);
            }
            setDirty(true);
            _cache.put(page.getPageNum(), this); // required for paging out
        }

        private void setDirty(final boolean dirt) {
            this.dirty = dirt;
        }

        @Nonnull
        private Value getPrefix(@Nonnull final Value v1, @Nonnull final Value v2) {
            final int idx = Math.abs(v1.compareTo(v2)) - 1;
            if (idx > 0) {
                final byte[] d2 = v2.getData();
                return new Value(d2, v2.getPosition(), idx);
            } else {
                return EmptyValue;
            }
        }

        /**
         * Reads node only if it is not loaded yet
         */
        private void read() throws IOException, BTreeException {
            if (!this.loaded) {
                Value v = readValue(page);
                DataInputStream in = new DataInputStream(v.getInputStream());
                // Read in the common prefix (if any)
                final short pfxLen = ph.getPrefixLength();
                final byte[] pfxBytes;
                if (pfxLen > 0) {
                    pfxBytes = new byte[pfxLen];
                    in.read(pfxBytes);
                    this.prefix = new Value(pfxBytes);
                } else {
                    pfxBytes = EmptyBytes;
                    this.prefix = EmptyValue;
                }
                // Read in the Values
                Value prevKey = null;
                final int keyslen = ph.getValueCount();
                keys = new Value[keyslen];
                for (int i = 0; i < keyslen; i++) {
                    final int valSize = in.readInt();
                    if (valSize == -1) {
                        prevKey.incrRefCount();
                        keys[i] = prevKey;
                    } else {
                        byte[] b = new byte[pfxLen + valSize];
                        if (pfxLen > 0) {
                            System.arraycopy(pfxBytes, 0, b, 0, pfxLen);
                        }
                        if (valSize > 0) {
                            in.read(b, pfxLen, valSize);
                        }
                        prevKey = new Value(b);
                        keys[i] = prevKey;
                    }
                }
                // Read in the pointers
                final int ptrslen = ph.getPointerCount();
                ptrs = new long[ptrslen];
                for (int i = 0; i < ptrslen; i++) {
                    ptrs[i] = VariableByteCodec.decodeUnsignedLong(in);
                }
                // Read in the links if current node is a leaf
                if (ph.getStatus() == LEAF) {
                    this._prev = in.readLong();
                    this._next = in.readLong();
                }
                this.currentDataLen = v.getLength();
                this.loaded = true;
            }
        }

        private void write() throws IOException, BTreeException {
            if (!dirty) {
                return;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace((ph.getStatus() == LEAF ? "Leaf " : "Branch ") + "Node#"
                        + page.getPageNum() + " - " + Arrays.toString(keys));
            }
            final FastMultiByteArrayOutputStream bos =
                    new FastMultiByteArrayOutputStream(_fileHeader.getWorkSize());
            final DataOutputStream os = new DataOutputStream(bos);

            // write out the prefix
            final short prefixlen = ph.getPrefixLength();
            if (prefixlen > 0) {
                prefix.writeTo(os);
            }
            // Write out the Values
            Value prevKey = null;
            for (int i = 0; i < keys.length; i++) {
                final Value v = keys[i];
                if (v == prevKey) {
                    os.writeInt(-1);
                } else {
                    final int len = v.getLength();
                    final int size = len - prefixlen;
                    os.writeInt(size);
                    if (size > 0) {
                        v.writeTo(os, prefixlen, size);
                    }
                }
                prevKey = v;
            }
            // Write out the pointers
            for (int i = 0; i < ptrs.length; i++) {
                VariableByteCodec.encodeUnsignedLong(ptrs[i], os);
            }
            // Write out link if current node is a leaf
            if (ph.getStatus() == LEAF) {
                os.writeLong(_prev);
                os.writeLong(_next);
            }

            writeValue(page, new Value(bos.toByteArray()));
            this.parentCache = null;
            setDirty(false);
        }

        private int calculateDataLength() {
            if (currentDataLen > 0) {
                return currentDataLen;
            }
            final int vlen = keys.length;
            final short prefixlen = ph.getPrefixLength();
            int datalen = prefixlen + (vlen >>> 2) /* key size */;
            Value prevValue = null;
            for (int i = 0; i < vlen; i++) {
                final long ptr = ptrs[i];
                datalen += VariableByteCodec.requiredBytes(ptr);
                final Value v = keys[i];
                if (v == prevValue) {
                    continue;
                }
                final int keylen = v.getLength();
                final int actkeylen = keylen - prefixlen; /* actual keys length */
                datalen += actkeylen;
                prevValue = v;
            }
            if (ph.getStatus() == LEAF) {
                datalen += 16;
            }
            this.currentDataLen = datalen;
            return datalen;
        }

        private void incrDataLength(@Nonnull final Value key, final long ptr) {
            int datalen = currentDataLen;
            if (datalen == -1) {
                datalen = calculateDataLength();
            }
            final int refcnt = key.incrRefCount();
            if (refcnt == 1) {
                datalen += key.getLength();
            }
            datalen += VariableByteCodec.requiredBytes(ptr);
            datalen += 4 /* key size */;
            this.currentDataLen = datalen;
        }

        private void decrDataLength(final Value value) {
            int datalen = currentDataLen;
            final int refcnt = value.decrRefCount();
            if (refcnt == 0) {
                datalen -= value.getLength();
            }
            datalen -= (4 + 8);
            this.currentDataLen = datalen;
        }

        /** find lest-most value which matches to the key */
        long findValue(@Nonnull Value searchKey) throws BTreeException {
            int idx = searchLeftmostKey(keys, searchKey, keys.length);
            switch (ph.getStatus()) {
                case BRANCH:
                    idx = idx < 0 ? -(idx + 1) : idx + 1;
                    return getChildNode(idx).findValue(searchKey);
                case LEAF:
                    if (idx < 0) {
                        return KEY_NOT_FOUND;
                    } else {
                        if (idx == 0 && (ph.getLeftLookup() > 0)) {
                            BTreeNode leftmostNode = this;
                            while (true) {
                                leftmostNode = getBTreeNode(root, leftmostNode._prev);
                                final Value[] lmKeys = leftmostNode.keys;
                                assert (lmKeys.length > 0);
                                if (!lmKeys[0].equals(searchKey)) {
                                    break;
                                }
                                final int prevLookup = leftmostNode.ph.getLeftLookup();
                                if (prevLookup == 0) {
                                    break;
                                }
                            }
                            final Value[] lmKeys = leftmostNode.keys;
                            final int lmIdx = leftmostNode.searchLeftmostKey(lmKeys, searchKey,
                                lmKeys.length);
                            if (lmIdx < 0) {
                                throw new BTreeCorruptException(
                                    "Duplicated key was not found: " + searchKey);
                            }
                            final long[] leftmostPtrs = leftmostNode.ptrs;
                            return leftmostPtrs[lmIdx];
                        } else {
                            return ptrs[idx];
                        }
                    }
                default:
                    throw new BTreeCorruptException(
                        "Invalid page type '" + ph.getStatus() + "' in findValue");
            }
        }

        /**
         * Scan the leaf node. Note that keys might be shortest-possible value.
         */
        void scanLeaf(@Nonnull final IndexQuery query, @Nonnull final BTreeCallback callback,
                final boolean edge) {
            assert (ph.getStatus() == LEAF) : ph.getStatus();
            Value[] conds = query.getOperands();
            switch (query.getOperator()) {
                case BasicIndexQuery.EQ: {
                    if (!edge) {
                        for (int i = 0; i < keys.length; i++) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                        return;
                    }
                    final int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if (leftIdx >= 0) {
                        assert (isDuplicateAllowed());
                        final int rightIdx =
                                searchRightmostKey(keys, conds[conds.length - 1], keys.length);
                        for (int i = leftIdx; i <= rightIdx; i++) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.NE: {
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    int rightIdx = isDuplicateAllowed()
                            ? searchRightmostKey(keys, conds[conds.length - 1], keys.length)
                            : leftIdx;
                    for (int i = 0; i < ptrs.length; i++) {
                        if (i < leftIdx || i > rightIdx) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.BWX:
                case BasicIndexQuery.BW:
                case BasicIndexQuery.START_WITH:
                case BasicIndexQuery.IN: {
                    if (!edge) {
                        for (int i = 0; i < keys.length; i++) {
                            if (query.testValue(keys[i])) {
                                callback.indexInfo(keys[i], ptrs[i]);
                            }
                        }
                        return;
                    }
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if (leftIdx < 0) {
                        leftIdx = -(leftIdx + 1);
                    }
                    int rightIdx = searchRightmostKey(keys, conds[conds.length - 1], keys.length);
                    if (rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for (int i = leftIdx; i < ptrs.length; i++) {
                        if (i <= rightIdx && query.testValue(keys[i])) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.NBWX:
                case BasicIndexQuery.NBW:
                case BasicIndexQuery.NOT_START_WITH: {
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if (leftIdx < 0) {
                        leftIdx = -(leftIdx + 1);
                    }
                    int rightIdx = searchRightmostKey(keys, conds[conds.length - 1], keys.length);
                    if (rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for (int i = 0; i < ptrs.length; i++) {
                        if ((i <= leftIdx || i >= rightIdx) && query.testValue(keys[i])) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.LT: {
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if (leftIdx < 0) {
                        leftIdx = -(leftIdx + 1); // insertion point
                    }
                    for (int i = 0; i < leftIdx; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.LE: {
                    int leftIdx = searchRightmostKey(keys, conds[0], keys.length);
                    if (leftIdx < 0) {
                        leftIdx = -(leftIdx + 1); // insertion point
                    }
                    if (leftIdx >= ptrs.length) {
                        leftIdx = ptrs.length - 1;
                    }
                    for (int i = 0; i <= leftIdx; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.GT: {
                    int rightIdx = searchRightmostKey(keys, conds[0], keys.length);
                    if (rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for (int i = rightIdx + 1; i < ptrs.length; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.GE: {
                    int rightIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if (rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for (int i = rightIdx; i < ptrs.length; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.ANY:
                    for (int i = 0; i < ptrs.length; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                case BasicIndexQuery.NOT_IN:
                default:
                    for (int i = 0; i < ptrs.length; i++) {
                        if (query.testValue(keys[i])) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
            }
        }

        BTreeNode getLeafNode(@Nonnull final SearchType searchType, @Nonnull final Value key)
                throws IOException, BTreeException {
            final byte nodeType = ph.getStatus();
            switch (nodeType) {
                case BRANCH:
                    switch (searchType) {
                        case LEFT: {
                            int leftIdx = searchLeftmostKey(keys, key, keys.length);
                            leftIdx = leftIdx < 0 ? -(leftIdx + 1) : leftIdx + 1;
                            return getChildNode(leftIdx).getLeafNode(searchType, key);
                        }
                        case RIGHT: {
                            int rightIdx = searchRightmostKey(keys, key, keys.length);
                            rightIdx = rightIdx < 0 ? -(rightIdx + 1) : rightIdx + 1;
                            return getChildNode(rightIdx).getLeafNode(searchType, key);
                        }
                        case LEFT_MOST:
                            return getChildNode(0).getLeafNode(searchType, key);
                        case RIGHT_MOST:
                            int rightIdx = ptrs.length - 1;
                            assert (rightIdx >= 0);
                            return getChildNode(rightIdx).getLeafNode(searchType, key);
                        default:
                            throw new IllegalStateException();
                    }
                case LEAF:
                    switch (searchType) {
                        case LEFT: {
                            if (keys.length == 0) {
                                break;
                            }
                            BTreeNode leftmostNode = this;
                            if (keys[0].equals(key)) {
                                int lookup = ph.getLeftLookup();
                                while (lookup > 0) {
                                    leftmostNode = getBTreeNode(root, leftmostNode._prev);
                                    int keylen = leftmostNode.keys.length;
                                    if (lookup < keylen) {
                                        break;
                                    }
                                    lookup = leftmostNode.ph.getLeftLookup();
                                    if (lookup == 0) {
                                        break;
                                    }
                                    Value firstKey = leftmostNode.keys[0];
                                    if (!firstKey.equals(key)) {
                                        break;
                                    }
                                }
                            }
                            return leftmostNode;
                        }
                        case RIGHT_MOST:
                            if (_next != -1L) {
                                BTreeNode nextNode = getBTreeNode(root, _next);
                                BTreeNode parent = getParent();
                                throw new IllegalStateException("next=" + _next + ".. more leaf ["
                                        + nextNode + "] exists on the right side of leaf ["
                                        + this.toString() + "]\n parent-ptrs: "
                                        + Arrays.toString(parent.ptrs));
                            }
                            break;
                        case LEFT_MOST:
                            if (_prev != -1L) {
                                BTreeNode prevNode = getBTreeNode(root, _prev);
                                BTreeNode parent = getParent();
                                throw new IllegalStateException("prev=" + _prev + ".. more leaf ["
                                        + prevNode + "] exists on the left side of leaf ["
                                        + this.toString() + "]\n parent-ptrs: "
                                        + Arrays.toString(parent.ptrs));
                            }
                            break;
                        default:
                            break;
                    }
                    return this;
                default:
                    throw new BTreeCorruptException("Invalid page type in query: " + nodeType);
            }
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            final long rootPage = root.getPage();
            BTreeNode pn = this;
            while (true) {
                final long curPageNum = pn.page.getPageNum();
                buf.append(curPageNum);
                pn = pn.getParent();
                if (pn == null) {
                    if (curPageNum != rootPage) {
                        buf.append("<-?");
                    }
                    break;
                } else {
                    buf.append("<-");
                }
            }
            return buf.toString();
        }

        public int compareTo(BTreeNode other) {
            return page.compareTo(other.page);
        }
    }

    protected class BTreeFileHeader extends FileHeader {

        private long _rootPage = 0;
        private boolean _duplicateAllowed = true;

        public BTreeFileHeader(int pageSize) {
            super(pageSize);
        }

        @Override
        public synchronized void read(RandomAccessFile raf) throws IOException {
            super.read(raf);
            this._duplicateAllowed = raf.readBoolean();
            this._rootPage = raf.readLong();
        }

        @Override
        public synchronized void write(RandomAccessFile raf) throws IOException {
            super.write(raf);
            raf.writeBoolean(_duplicateAllowed);
            raf.writeLong(_rootPage);
        }

        /** The root page of the storage tree */
        @Deprecated
        public final void setRootPage(long rootPage) {
            this._rootPage = rootPage;
            setDirty(true);
        }

        /** The root page of the storage tree */
        public final long getRootPage() {
            return _rootPage;
        }
    }

    protected class BTreePageHeader extends PageHeader {

        private long parentPage;
        private short valueCount = 0;
        private short prefixLength = 0;
        private int leftLookup = 0;

        public BTreePageHeader() {
            super();
        }

        @Deprecated
        public BTreePageHeader(ByteBuffer buf) {
            super(buf);
        }

        @Override
        public void read(ByteBuffer buf) {
            super.read(buf);
            if (getStatus() == UNUSED) {
                return;
            }
            parentPage = buf.getLong();
            valueCount = buf.getShort();
            prefixLength = buf.getShort();
            leftLookup = buf.getInt();
        }

        @Override
        public void write(ByteBuffer buf) {
            super.write(buf);
            buf.putLong(parentPage);
            buf.putShort(valueCount);
            buf.putShort(prefixLength);
            buf.putInt(leftLookup);
        }

        public void setParent(BTreeNode parentNode) {
            if (parentNode == null) {
                this.parentPage = Paged.NO_PAGE;
            } else {
                this.parentPage = parentNode.page.getPageNum();
            }
        }

        /** The number of values stored by this page */
        public final void setValueCount(short valueCount) {
            this.valueCount = valueCount;
        }

        /** The number of values stored by this page */
        public final short getValueCount() {
            return valueCount;
        }

        /** The number of pointers stored by this page */
        public final int getPointerCount() {
            if (getStatus() == BRANCH) {
                return valueCount + 1;
            } else {
                return valueCount;
            }
        }

        public final short getPrefixLength() {
            return prefixLength;
        }

        public final void setPrefixLength(short prefixLength) {
            this.prefixLength = prefixLength;
        }

        public final int getLeftLookup() {
            return leftLookup;
        }

        public final void setLeftLookup(int leftLookup) {
            this.leftLookup = leftLookup;
        }
    }

    public static final class BTreeCorruptException extends RuntimeException {
        private static final long serialVersionUID = 5609947858701765326L;

        public BTreeCorruptException(String message) {
            super(message);
        }

        public BTreeCorruptException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
