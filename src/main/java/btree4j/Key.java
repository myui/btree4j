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

/**
 * Key extends Value by providing a hash value for the Key.
 */
public class Key extends Value {
    private static final long serialVersionUID = 3445130535839461625L;
    public static final Key[] EMPTY_KEYS = new Key[0];

    public Key(String data) {
        super(data);
    }

    public Key(byte[] data) {
        super(data);
    }

    public Key(byte[] data, int len) {
        super(data, len);
    }

    public Key(byte[] data, int pos, int len) {
        super(data, pos, len);
    }

    public boolean equals(Key key) {
        return hashCode() == key.hashCode() ? compareTo(key) == 0 : false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Key) {
            return equals((Key) obj);
        } else {
            return super.equals(obj);
        }
    }

}
