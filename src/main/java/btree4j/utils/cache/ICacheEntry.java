/*
 * Copyright (c) 2006-2018 Makoto Yui
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
package btree4j.utils.cache;

import btree4j.utils.concurrent.lock.ILock;

import java.io.Serializable;

public interface ICacheEntry<K, V> extends Serializable {

    public K getKey();

    public V getValue();

    public V volatileGetValue();

    public void setValue(V newValue);

    public boolean compareAndSetValue(V expect, V update);

    /**
     * Should be overrided. This is equivalent to
     * 
     * <pre>
     * return System.identifyHashCode(getKey())
     * </pre>
     */
    public int hashCode();

    /**
     * This is equivalent to
     * 
     * <pre>
     * if (obj == this)
     *     return true;
     * if (obj instanceof CacheEntry) {
     *     CacheEntry e = (CacheEntry) obj;
     *     return e.getKey() == _key &amp;&amp; e.getValue() == _value;
     * }
     * return false;
     * </pre>
     */
    public boolean equals(Object obj);

    public boolean pin();

    public void unpin();

    public boolean isPinned();

    public int pinCount();

    public void setEvicted();

    public boolean tryEvict();

    public boolean isEvicted();

    public ILock lock();

}
