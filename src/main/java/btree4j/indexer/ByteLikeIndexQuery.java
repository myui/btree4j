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
import btree4j.indexer.BasicIndexQuery.IndexConditionSW;
import btree4j.utils.collections.IntStack;
import btree4j.utils.io.FastMultiByteArrayOutputStream;
import btree4j.utils.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public final class ByteLikeIndexQuery extends IndexConditionSW {

    private static final int MATCH = 0, ANY = 1;

    private final byte anyPattern;

    private byte[][] _patterns;
    private int[] _types;

    public ByteLikeIndexQuery(Value prefix, byte[] suffix, byte any) {
        super(prefix);
        if (suffix == null) {
            throw new IllegalArgumentException("Illegal null suffix");
        }
        this.anyPattern = any;
        initPattern(suffix);
    }

    private void initPattern(byte[] p) {
        final IntStack typeStack = new IntStack(12);
        final ArrayList<byte[]> patternStack = new ArrayList<byte[]>();
        try (FastMultiByteArrayOutputStream pending = new FastMultiByteArrayOutputStream(32)) {
            final int ptnlen = p.length;
            for (int i = 0; i < ptnlen; i++) {
                byte c = p[i];
                if (c == anyPattern) {
                    if (!typeStack.isEmpty()) {
                        int lastType = typeStack.peek();
                        if (lastType == anyPattern) {
                            continue;
                        }
                    }
                    if (pending.size() > 0) {
                        typeStack.push(MATCH);
                        patternStack.add(pending.toByteArray());
                        pending.reset();
                    }
                    typeStack.push(ANY);
                    patternStack.add(null);
                } else {
                    pending.write(c);
                }
            }
            if (pending.size() > 0) {
                typeStack.push(MATCH);
                patternStack.add(pending.toByteArray());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        this._patterns = patternStack.toArray(new byte[patternStack.size()][]);
        this._types = typeStack.toArray();
    }

    @Override
    public boolean testValue(Value value) {
        boolean sw = value.startsWith(_operands[0]);
        if (!sw) {
            return false;
        }
        if (_types.length == 0) {
            return true;
        }
        byte[] data = value.getData();
        int offset = _operands[0].getLength();
        int length = data.length - offset;
        final byte[] target;
        if (offset == 0 && length == data.length) {
            target = data;
        } else {
            target = Arrays.copyOfRange(data, offset, offset + length);
        }
        return match(target, _patterns, _types, 0, 0);
    }

    private static boolean match(byte[] target, byte[][] verifyPatterns, int[] verifyType, int ti,
            int pi) {
        final int round = verifyType.length;
        final int tlimit = target.length;
        for (; pi < round; pi++) {
            final int type = verifyType[pi];
            switch (type) {
                case MATCH:
                    final byte[] ptn = verifyPatterns[pi];
                    final int ptnlen = ptn.length;
                    if ((ti + ptnlen) > tlimit) {
                        return false;
                    }
                    for (int j = 0; j < ptnlen; j++) {
                        if (target[ti++] != ptn[j]) {
                            return false;
                        }
                    }
                    break;
                case ANY:
                    if (++pi >= round) {
                        return true;
                    }
                    // there are more rounds
                    for (; ti < tlimit; ti++) {// recursive trick
                        if (match(target, verifyPatterns, verifyType, ti, pi)) {
                            return true;
                        }
                        // TRICK: backtrack to ANY
                    }
                    return false;
                default:
                    throw new IllegalStateException("Illegal type: " + type);
            }
        }
        return ti == tlimit;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64);
        buf.append("prefix: ");
        buf.append(_operands[0].toString());
        buf.append(", suffix: ");
        for (int i = 0; i < _types.length; i++) {
            int type = _types[i];
            if (type == ANY) {
                buf.append('%');
            } else if (type == MATCH) {
                final byte[] pattern = _patterns[i];
                buf.append(StringUtils.encodeHex(pattern));
            } else {
                throw new IllegalStateException("Unexpected type: " + type);
            }
        }
        return buf.toString();
    }

}
