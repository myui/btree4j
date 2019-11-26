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

import btree4j.utils.collections.longs.LongHash.LongLRUMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PurgeOptObservableLongLRUMap<V extends Comparable<V>> extends LongLRUMap<V> {
    private static final long serialVersionUID = 2481614187542943334L;

    private volatile int purgeUnits;
    private final Cleaner<V> cleaner;

    public PurgeOptObservableLongLRUMap(int limit, Cleaner<V> cleaner) {
        this(limit, 1, cleaner);
    }

    public PurgeOptObservableLongLRUMap(int limit, int purgeUnits, Cleaner<V> cleaner) {
        super(limit);
        if (limit < purgeUnits) {
            throw new IllegalArgumentException(
                "limit '" + limit + "' < pergeUnits '" + purgeUnits + '\'');
        }
        if (purgeUnits < 1) {
            throw new IllegalArgumentException("Illegal purgeUnits: " + purgeUnits);
        }
        this.purgeUnits = purgeUnits;
        this.cleaner = cleaner;
    }

    public void setPurgeUnits(int units) {
        if (units < 1) {
            throw new IllegalArgumentException("Illegal purgeUnits: " + units);
        }
        this.purgeUnits = units;
    }

    @Override
    protected void addEntry(int bucket, long key, V value, LongHash.BucketEntry<V> next) {
        final ComparableChainedEntry<V> newEntry = new ComparableChainedEntry<V>(key, value, next);
        this._buckets[bucket] = newEntry;
        newEntry.addBefore(entryChainHeader);
        ++_size;
        if (removeEldestEntry()) {
            final int purgeSize = purgeUnits;
            final List<ComparableChainedEntry<V>> list =
                    new ArrayList<ComparableChainedEntry<V>>(purgeSize);
            for (int i = 0; i < purgeSize; i++) {
                final ComparableChainedEntry<V> eldest =
                        (ComparableChainedEntry<V>) entryChainHeader.next;
                final V removed = remove(eldest.key);
                if (removed != null) {
                    list.add(eldest);
                }
            }
            Collections.sort(list);
            for (ComparableChainedEntry<V> e : list) {
                cleaner.cleanup(e.key, e.value);
            }
        } else {
            if (_size > _threshold) {
                resize(2 * _buckets.length);
            }
        }
    }

    public void purgeAll() {
        final List<ComparableChainedEntry<V>> list =
                new ArrayList<ComparableChainedEntry<V>>(size());
        for (BucketEntry<V> e : this) {
            list.add((ComparableChainedEntry<V>) e);
        }
        clear();
        Collections.sort(list);
        for (ComparableChainedEntry<V> e : list) {
            cleaner.cleanup(e.key, e.value);
        }
    }

    private static final class ComparableChainedEntry<V extends Comparable<V>>
            extends ChainedEntry<V> implements Comparable<ComparableChainedEntry<V>> {
        private static final long serialVersionUID = 8853020653416971039L;

        ComparableChainedEntry(long key, V value, BucketEntry<V> next) {
            super(key, value, next);
        }

        public int compareTo(ComparableChainedEntry<V> o) {
            return value.compareTo(o.value);
        }
    }

}
