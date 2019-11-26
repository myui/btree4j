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

public interface IndexQuery {

    public int getOperator();

    public Value[] getOperands();

    public Value getOperand(int index);

    /**
     * testValue tests the specified value for validity against this IndexQuery. The helper classes
     * in org.apache.xindice.core.indexer.helpers should be used for optimized performance.
     *
     * @param value The Value to compare
     * @return Whether or not the value matches
     */
    public boolean testValue(Value value);

}
