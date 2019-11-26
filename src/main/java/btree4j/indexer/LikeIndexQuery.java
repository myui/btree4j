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
import btree4j.utils.lang.Primitives;
import btree4j.utils.lang.StringUtils;

import java.util.ArrayList;

public final class LikeIndexQuery extends IndexConditionSW {

    private static final char DEFAULT_ESCAPE = '\\';
    private static final int MATCH = 0, ONE = 1, ANY = 2;

    private final int escapeChar;

    private char[][] _patterns;
    private int[] _types;

    public LikeIndexQuery(Value prefix, String suffix) {
        this(prefix, suffix, DEFAULT_ESCAPE);
    }

    public LikeIndexQuery(Value prefix, String suffix, char escape) {
        super(prefix);
        if (suffix == null) {
            throw new IllegalArgumentException("Illegal null suffix");
        }
        this.escapeChar = escape;
        initPattern(suffix);
    }

    private void initPattern(String p) {
        final int ptnlen = p.length();
        final IntStack typeStack = new IntStack(12);
        final ArrayList<char[]> patternStack = new ArrayList<char[]>();
        final StringBuilder pending = new StringBuilder(64);
        for (int i = 0; i < ptnlen; i++) {
            char c = p.charAt(i);
            if (c == escapeChar) {
                if (i >= (ptnlen - 1)) {
                    throw new IllegalArgumentException("Illegal like expression: " + p);
                }
                c = p.charAt(++i);
                if (c != '%' && c != '_' && c != escapeChar) {
                    throw new IllegalArgumentException("Illegal like expression: " + p);
                }
                pending.append(c);
            } else if (c == '%') {
                if (!typeStack.isEmpty()) {
                    int lastType = typeStack.peek();
                    if (lastType == '%') {
                        continue;
                    }
                }
                if (pending.length() > 0) {
                    String s = pending.toString();
                    typeStack.push(MATCH);
                    patternStack.add(StringUtils.getChars(s));
                    pending.setLength(0);
                }
                typeStack.push(ANY);
                patternStack.add(null);
            } else if (c == '_') {
                if (pending.length() > 0) {
                    String s = pending.toString();
                    typeStack.push(MATCH);
                    patternStack.add(StringUtils.getChars(s));
                    pending.setLength(0);
                }
                typeStack.push(ONE);
                patternStack.add(null);
            } else {
                pending.append(c);
            }
        }
        if (pending.length() > 0) {
            String s = pending.toString();
            typeStack.push(MATCH);
            patternStack.add(StringUtils.getChars(s));
        }
        this._patterns = patternStack.toArray(new char[patternStack.size()][]);
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
        char[] target = Primitives.toChars(data, offset, length);
        return match(target, _patterns, _types, 0, 0);
    }

    private static boolean match(char[] target, char[][] verifyPatterns, int[] verifyType, int ti,
            int pi) {
        final int round = verifyType.length;
        final int tlimit = target.length;
        for (; pi < round; pi++) {
            final int type = verifyType[pi];
            final char[] ptn = verifyPatterns[pi];
            switch (type) {
                case MATCH:
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
                case ONE:
                    if (ti++ >= tlimit) {
                        return false;
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
                        // backtrack to ANY
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
        StringBuilder buf = new StringBuilder(64);
        buf.append("prefix: ");
        buf.append(_operands[0].toString());
        buf.append(", suffix: ");
        for (int i = 0; i < _types.length; i++) {
            int type = _types[i];
            if (type == ANY) {
                buf.append('%');
            } else if (type == ONE) {
                buf.append('_');
            } else if (type == MATCH) {
                char[] pattern = _patterns[i];
                buf.append(pattern);
            } else {
                throw new IllegalStateException("Unexpected type: " + type);
            }
        }
        return buf.toString();
    }

}
