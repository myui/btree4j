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
package btree4j.utils.collections.longs;

import btree4j.utils.lang.HashUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * ChainedHash implementation for int to Object hash.
 */
public class LongHash<V> implements Externalizable, Iterable<LongHash.BucketEntry<V>> {
    private static final long serialVersionUID = 1L;

    private static final float DEFAULT_LOAD_FACTOR = 0.7f;

    private transient float _loadFactor = DEFAULT_LOAD_FACTOR;
    protected BucketEntry<V>[] _buckets;
    private int _mask;
    protected int _threshold;
    protected int _size = 0;

    /**
     * Create a hash table that can comfortably hold the specified number of entries. The actual
     * table created to be is the smallest prime greater than size * 2.
     */
    @SuppressWarnings("unchecked")
    public LongHash(int size, float loadFactor) {
        final int bucketSize = HashUtils.nextPowerOfTwo(size);
        this._buckets = new BucketEntry[bucketSize];
        this._mask = bucketSize - 1;
        this._loadFactor = loadFactor;
        this._threshold = (int) (size * loadFactor);
    }

    public LongHash(int size) {
        this(size, DEFAULT_LOAD_FACTOR);
    }

    public LongHash() {// for Object Serialization
        this(1);
    }

    public int size() {
        return _size;
    }

    /**
     * Put an entry for the given key number. If one already exists, old value is replaced.
     * 
     * @return old value for the given key. if not found, return -1.
     */
    public V put(final long key, final V value) {
        final BucketEntry<V>[] buckets = _buckets;
        final int bucket = indexFor(key, _mask);
        // find an entry
        BucketEntry<V> e;
        for (e = buckets[bucket]; e != null; e = e.next) {
            if (key == e.key) {
                final V replaced = e.value;
                e.value = value;
                e.recordAccess(this);
                return replaced; // found
            }
        }
        // if not found, create a new entry.
        addEntry(bucket, key, value, buckets[bucket]);
        return null;
    }

    public V putIfAbsent(final long key, final V value) {
        final BucketEntry<V>[] buckets = _buckets;
        final int bucket = indexFor(key, _mask);
        // find an entry
        BucketEntry<V> e;
        for (e = buckets[bucket]; e != null; e = e.next) {
            if (key == e.key) {
                return e.value;
            }
        }
        // if not found, create a new entry.
        addEntry(bucket, key, value, buckets[bucket]);
        return null;
    }

    public final V syncPut(final long key, final V value) {
        final BucketEntry<V>[] buckets = _buckets;
        final int bucket = indexFor(key, _mask);
        synchronized (buckets) {
            // find an entry
            BucketEntry<V> e;
            for (e = buckets[bucket]; e != null; e = e.next) {
                if (key == e.key) {
                    final V replaced = e.value;
                    e.value = value;
                    e.recordAccess(this);
                    return replaced; // found
                }
            }
            // if not found, create a new entry.
            addEntry(bucket, key, value, buckets[bucket]);
        }
        return null;
    }

    public final V syncPutIfAbsent(final long key, final V value) {
        final BucketEntry<V>[] buckets = _buckets;
        final int bucket = indexFor(key, _mask);
        synchronized (buckets) {
            // find an entry
            BucketEntry<V> e;
            for (e = buckets[bucket]; e != null; e = e.next) {
                if (key == e.key) {
                    return e.value;
                }
            }
            // if not found, create a new entry.
            addEntry(bucket, key, value, buckets[bucket]);
        }
        return null;
    }

    public final V get(final long key) {
        final BucketEntry<V>[] buckets = _buckets;
        final int bucket = indexFor(key, _mask);
        for (BucketEntry<V> e = buckets[bucket]; e != null; e = e.next) {
            if (key == e.key) {
                e.recordAccess(this);
                return e.value;
            }
        }
        return null;
    }

    public final V syncGet(final long key) {
        final BucketEntry<V>[] buckets = _buckets; // REVIEWME _bucket may be
        // replaced (rehashed)
        final int bucket = indexFor(key, _mask);
        synchronized (buckets) {
            for (BucketEntry<V> e = buckets[bucket]; e != null; e = e.next) {
                if (key == e.key) {
                    e.recordAccess(this);
                    return e.value;
                }
            }
        }
        return null;
    }

    public boolean contains(final long key) {
        final BucketEntry<V>[] buckets = _buckets;
        final int bucket = indexFor(key, _mask);
        for (BucketEntry<V> e = buckets[bucket]; e != null; e = e.next) {
            if (key == e.key) {
                return true;
            }
        }
        return false;
    }

    public synchronized void clear() {
        BucketEntry<V> tab[] = _buckets;
        for (int i = tab.length; --i >= 0;) {
            tab[i] = null;
        }
        this._size = 0;
    }

    public V remove(long key) {
        final BucketEntry<V>[] buckets = _buckets;
        final int bucket = indexFor(key, _mask);
        // find an entry
        BucketEntry<V> e, prev = null;
        for (e = buckets[bucket]; e != null; prev = e, e = e.next) {
            if (key == e.key) {
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    buckets[bucket] = e.next;
                }
                --_size;
                e.recordRemoval(this);
                return e.value;
            }
        }
        return null;
    }

    public static class BucketEntry<V> implements Externalizable {
        private static final long serialVersionUID = 1L;

        long key;
        V value;
        BucketEntry<V> next;

        BucketEntry(long key, V value, BucketEntry<V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }

        private BucketEntry(long key, V value) {
            this(key, value, null);
        }

        public BucketEntry() {}// for serialization

        public long getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        @SuppressWarnings("unchecked")
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            this.key = in.readLong();
            this.value = (V) in.readObject();
            boolean hasNext = in.readBoolean();
            BucketEntry<V> cur = this;
            while (hasNext) {
                final long k = in.readLong();
                final V v = (V) in.readObject();
                BucketEntry<V> n = new BucketEntry<V>(k, v);
                cur.next = n;
                cur = n;
                hasNext = in.readBoolean();
            }
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            assert (value != null);
            BucketEntry<V> cur = this;
            while (true) {
                out.writeLong(cur.key);
                out.writeObject(cur.value);
                if (cur.next != null) {// hasNext
                    out.writeBoolean(true);
                    cur = cur.next;
                } else {
                    out.writeBoolean(false);
                    break;
                }
            }
        }

        public String toString() {
            return new StringBuilder(64).append(key).append('/').append(value).toString();
        }

        protected void recordAccess(LongHash<V> m) {}

        protected void recordRemoval(LongHash<V> m) {}

    }

    protected void addEntry(int bucket, long key, V value, BucketEntry<V> next) {
        final BucketEntry<V> entry = new BucketEntry<V>(key, value, next);
        this._buckets[bucket] = entry;
        if (++_size > _threshold) {
            resize(2 * _buckets.length);
        }
    }

    @SuppressWarnings("unchecked")
    protected void resize(int newCapacity) {
        final int cap = HashUtils.nextPowerOfTwo(newCapacity);
        BucketEntry<V>[] newTable = new BucketEntry[cap];
        rehash(newTable);
        this._buckets = newTable;
        this._mask = cap - 1;
        this._threshold = (int) (newCapacity * _loadFactor);
    }

    private void rehash(BucketEntry<V>[] newTable) {
        final int oldsize = _buckets.length;
        for (int i = 0; i < oldsize; i++) {
            BucketEntry<V> oldEntry = _buckets[i];
            while (oldEntry != null) {
                BucketEntry<V> e = oldEntry;
                oldEntry = oldEntry.next;
                final int bucket = indexFor(e.key, _mask);
                e.next = newTable[bucket];
                newTable[bucket] = e;
            }
        }
    }

    private static int indexFor(final long key, final int mask) {
        return ((int) (key ^ (key >>> 32))) & 0x7fffffff & mask; // REVIEWME
    }

    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this._threshold = in.readInt();
        this._size = in.readInt();
        final int blen = in.readInt();
        this._buckets = new BucketEntry[blen];
        this._mask = blen - 1;
        for (int i = 0; i < blen; i++) {
            _buckets[i] = (BucketEntry<V>) in.readObject();
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(_threshold);
        out.writeInt(_size);
        out.writeInt(_buckets.length);
        for (int i = 0; i < _buckets.length; i++) {
            out.writeObject(_buckets[i]);
        }
    }

    public Iterator<BucketEntry<V>> iterator() {
        return new LongIterator();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(512);
        for (BucketEntry<V> e : this) {
            if (buf.length() > 0) {
                buf.append(", ");
            }
            buf.append('{');
            buf.append(e.key);
            buf.append('/');
            buf.append(e.value);
            buf.append('}');
        }
        return buf.toString();
    }

    private final class LongIterator implements Iterator<BucketEntry<V>> {

        private int cursor = 0;
        private int curBucketIndex = 0;
        private BucketEntry<V> curBucket = null;

        LongIterator() {}

        public boolean hasNext() {
            return _size > cursor;
        }

        public BucketEntry<V> next() {
            ++cursor;
            if (curBucket == null) {
                for (int i = curBucketIndex; i < _buckets.length; i++) {
                    BucketEntry<V> e = _buckets[i];
                    if (e != null) {
                        this.curBucket = e;
                        this.curBucketIndex = i + 1;
                        break;
                    }
                }
            }
            if (curBucket != null) {
                BucketEntry<V> e = curBucket;
                this.curBucket = e.next;
                return e;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static class LongLRUMap<V> extends LongHash<V> {
        private static final long serialVersionUID = 5136805290014155775L;

        private final int maxCapacity;
        protected transient final ChainedEntry<V> entryChainHeader;

        public LongLRUMap(int limit) {
            super(limit, 1.0f);
            this.maxCapacity = limit;
            final ChainedEntry<V> header = new ChainedEntry<V>(-1, null, null);
            this.entryChainHeader = header;
            initEntryChain(header);
        }

        private void initEntryChain(final ChainedEntry<V> header) {
            header.prev = header.next = header;
        }

        @Override
        protected void addEntry(int bucket, long key, V value, BucketEntry<V> next) {
            final ChainedEntry<V> newEntry = new ChainedEntry<V>(key, value, next);
            this._buckets[bucket] = newEntry;
            newEntry.addBefore(entryChainHeader);
            ++_size;
            ChainedEntry<V> eldest = entryChainHeader.next;
            if (removeEldestEntry()) {
                remove(eldest.key);
            } else {
                if (_size > _threshold) {
                    throw new IllegalStateException(
                        "size '" + _size + "' exceeds threshold '" + _threshold + '\'');
                }
            }
        }

        protected boolean removeEldestEntry() {
            return size() > maxCapacity;
        }

        protected static class ChainedEntry<V> extends BucketEntry<V> {
            private static final long serialVersionUID = 8853020653416971039L;

            protected ChainedEntry<V> prev, next;

            ChainedEntry(long key, V value, BucketEntry<V> next) {
                super(key, value, next);
            }

            @Override
            protected void recordAccess(LongHash<V> m) {
                remove();
                LongLRUMap<V> lm = (LongLRUMap<V>) m;
                addBefore(lm.entryChainHeader);
            }

            @Override
            protected void recordRemoval(LongHash<V> m) {
                remove();
            }

            /**
             * Removes this entry from the linked list.
             */
            protected void remove() {
                if (prev != null) {
                    prev.next = next;
                }
                if (next != null) {
                    next.prev = prev;
                }
                prev = null;
                next = null;
            }

            /**
             * Inserts this entry before the specified existing entry in the list.
             */
            protected void addBefore(final ChainedEntry<V> existingEntry) {
                next = existingEntry;
                prev = existingEntry.prev;
                if (prev != null) {
                    prev.next = this;
                }
                next.prev = this;
            }
        }

        @Override
        public final Iterator<BucketEntry<V>> iterator() {
            return new OrderIterator(entryChainHeader);
        }

        private final class OrderIterator implements Iterator<BucketEntry<V>> {

            private ChainedEntry<V> entry;

            public OrderIterator(ChainedEntry<V> e) {
                if (e == null) {
                    throw new IllegalArgumentException();
                }
                this.entry = e;
            }

            public boolean hasNext() {
                return entry.next != entryChainHeader;
            }

            public BucketEntry<V> next() {
                entry = entry.next;
                if (entry == entryChainHeader) {
                    throw new NoSuchElementException();
                }
                return entry;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public synchronized void clear() {
            initEntryChain(entryChainHeader);
            super.clear();
        }

    }

    public interface Cleaner<V> {
        public void cleanup(long key, V value);
    }

}
