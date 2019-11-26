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
package btree4j.utils.collections;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class LRUMap<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = 1L;

    protected final int maxCapacity;

    public LRUMap(int maxCapacity) {
        super(maxCapacity, 1.0f, true);
        this.maxCapacity = maxCapacity;
    }

    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest) {
        return size() > maxCapacity;
    }

    public void writeTo(ObjectOutput out) throws IOException {
        out.writeInt(maxCapacity);
        int size = size();
        out.writeInt(size);
        for (Entry<K, V> e : entrySet()) {
            out.writeObject(e.getKey());
            out.writeObject(e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V> LRUMap<K, V> readFrom(ObjectInput in)
            throws IOException, ClassNotFoundException {
        int cap = in.readInt();
        final LRUMap<K, V> map = new LRUMap<K, V>(cap);
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
            K key = (K) in.readObject();
            V value = (V) in.readObject();
            map.put(key, value);
        }
        return map;
    }

}
