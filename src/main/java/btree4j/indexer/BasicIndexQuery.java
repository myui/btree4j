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

import java.util.Arrays;

import btree4j.Value;

public class BasicIndexQuery implements IndexQuery {

    // no operand operators

    public static final int ANY = 0; /* reserved */

    // Unary operand operators (Unary)

    /** Equal */
    public static final int EQ = 1;

    /** Not Equal */
    public static final int NE = -1;

    /** Greater Than */
    public static final int GT = 2;

    /** Less than or Equal */
    public static final int LE = -2;

    /** Less Than */
    public static final int LT = 3;

    /** Greater than or Equal */
    public static final int GE = -3;

    // binary operands operators (Range)

    /** Between (Inclusive) */
    public static final int BW = 4;

    /** Not Between (Inclusive) */
    public static final int NBW = -4;

    /** Between (Exclusive) */
    public static final int BWX = 5;

    /** Not Between (Exclusive) */
    public static final int NBWX = -5;

    // multi operands operators (Set)

    /** IN is treated same as "Between (Inclusive)" internally */
    public static final int IN = 6;

    /** IN is treated same as "Not Between (Inclusive)" internally */
    public static final int NOT_IN = -6;

    // other operators

    /**
     * START_WITH is treated same as "Between (Inclusive) " where second value is same as first
     * value plus max byte. Keys must be sorted.
     */
    public static final int START_WITH = 7;

    /**
     * NOT_START_WITH is treated as "Not Between (Inclusive)" internally where second value is same
     * as first value plus max byte.
     */
    public static final int NOT_START_WITH = -7;

    // ---------------------------------------

    protected final int _operator;
    protected final Value[] _operands;

    // ---------------------------------------

    public BasicIndexQuery(final int op, final Value... operands) {
        this._operator = op;
        this._operands = operands;
    }

    public int getOperator() {
        return _operator;
    }

    public Value[] getOperands() {
        return _operands;
    }

    public Value getOperand(int index) {
        assert (index >= 0) : index;
        assert (index < _operands.length) : "operand[" + index + "] not found: " + _operands;
        return _operands[index];
    }

    public boolean testValue(Value value) {
        switch (_operator) {
            case ANY:
                return true;
            case EQ:
                return value.equals(_operands[0]);
            case NE:
                return !value.equals(_operands[0]);
            case GT:
                return value.compareTo(_operands[0]) > 0;
            case LE:
                return value.compareTo(_operands[0]) <= 0;
            case LT:
                return value.compareTo(_operands[0]) < 0;
            case GE:
                return value.compareTo(_operands[0]) >= 0;
            case BW:
                return value.compareTo(_operands[0]) >= 0 && value.compareTo(_operands[1]) <= 0;
            case NBW:
                return value.compareTo(_operands[0]) <= 0 || value.compareTo(_operands[1]) >= 0;
            case BWX:
                return value.compareTo(_operands[0]) > 0 && value.compareTo(_operands[1]) < 0;
            case NBWX:
                return value.compareTo(_operands[0]) < 0 || value.compareTo(_operands[1]) > 0;
            case IN:
                return Arrays.binarySearch(_operands, value) >= 0;
            case NOT_IN:
                return Arrays.binarySearch(_operands, value) < 0;
            case START_WITH:
                return value.startsWith(_operands[0]);
            case NOT_START_WITH:
                return !value.startsWith(_operands[0]);
            default:
                throw new IllegalStateException("invalid operation : " + _operator);
        }
    }

    public static final class IndexConditionANY extends BasicIndexQuery {

        public IndexConditionANY() {
            super(ANY);
        }

        @Override
        public int getOperator() {
            return ANY;
        }

        @Override
        public boolean testValue(Value value) {
            return true;
        }
    }

    public static final class IndexConditionEQ extends BasicIndexQuery {

        public IndexConditionEQ(Value operand) {
            super(EQ, operand);
        }

        @Override
        public int getOperator() {
            return EQ;
        }

        @Override
        public boolean testValue(Value value) {
            return value.equals(_operands[0]);
        }
    }

    public static final class IndexConditionNE extends BasicIndexQuery {

        public IndexConditionNE(Value operand) {
            super(NE, operand);
        }

        @Override
        public int getOperator() {
            return NE;
        }

        @Override
        public boolean testValue(Value value) {
            return !value.equals(_operands[0]);
        }
    }

    public static final class IndexConditionGT extends BasicIndexQuery {

        public IndexConditionGT(Value operand) {
            super(GT, operand);
        }

        @Override
        public int getOperator() {
            return GT;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) > 0;
        }
    }

    public static final class IndexConditionLE extends BasicIndexQuery {

        public IndexConditionLE(Value operand) {
            super(LE, operand);
        }

        @Override
        public int getOperator() {
            return LE;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) <= 0;
        }
    }

    public static final class IndexConditionLT extends BasicIndexQuery {

        public IndexConditionLT(Value operand) {
            super(LT, operand);
        }

        @Override
        public int getOperator() {
            return LT;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) < 0;
        }
    }

    public static final class IndexConditionGE extends BasicIndexQuery {

        public IndexConditionGE(Value operand) {
            super(GE, operand);
        }

        @Override
        public int getOperator() {
            return GE;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) >= 0;
        }
    }

    public static final class IndexConditionBW extends BasicIndexQuery {

        public IndexConditionBW(Value operand1, Value operand2) {
            super(BW, operand1, operand2);
        }

        @Override
        public int getOperator() {
            return BW;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) >= 0 && value.compareTo(_operands[1]) <= 0;
        }
    }

    public static final class IndexConditionNBW extends BasicIndexQuery {

        public IndexConditionNBW(Value operand1, Value operand2) {
            super(NBW, operand1, operand2);
        }

        @Override
        public int getOperator() {
            return NBW;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) <= 0 || value.compareTo(_operands[1]) >= 0;
        }
    }

    public static final class IndexConditionBWX extends BasicIndexQuery {

        public IndexConditionBWX(Value operand1, Value operand2) {
            super(BWX, operand1, operand2);
        }

        @Override
        public int getOperator() {
            return BWX;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) > 0 && value.compareTo(_operands[1]) < 0;
        }
    }

    public static final class IndexConditionNBWX extends BasicIndexQuery {

        public IndexConditionNBWX(Value operand1, Value operand2) {
            super(NBWX, operand1, operand2);
        }

        @Override
        public int getOperator() {
            return NBWX;
        }

        @Override
        public boolean testValue(Value value) {
            return value.compareTo(_operands[0]) < 0 || value.compareTo(_operands[1]) > 0;
        }
    }

    public static final class IndexConditionIN extends BasicIndexQuery {

        public IndexConditionIN(Value[] operands) {
            super(IN, operands);
        }

        @Override
        public int getOperator() {
            return IN;
        }

        @Override
        public boolean testValue(Value value) {
            return Arrays.binarySearch(_operands, value) >= 0;
        }
    }

    public static final class IndexConditionNIN extends BasicIndexQuery {

        public IndexConditionNIN(Value[] operands) {
            super(NOT_IN, operands);
        }

        @Override
        public int getOperator() {
            return NOT_IN;
        }

        @Override
        public boolean testValue(Value value) {
            return Arrays.binarySearch(_operands, value) < 0;
        }
    }

    public static class IndexConditionSW extends BasicIndexQuery {

        public IndexConditionSW(Value operand1) {
            super(START_WITH, operand1, calculateRightEdgeValue(operand1));
        }

        @Override
        public int getOperator() {
            return START_WITH;
        }

        @Override
        public boolean testValue(Value value) {
            return value.startsWith(_operands[0]);
        }
    }

    public static final class IndexConditionNSW extends BasicIndexQuery {

        public IndexConditionNSW(Value operand1) {
            super(NOT_START_WITH, operand1, calculateRightEdgeValue(operand1));
        }

        @Override
        public int getOperator() {
            return NOT_START_WITH;
        }

        @Override
        public boolean testValue(Value value) {
            return !value.startsWith(_operands[0]);
        }
    }

    private static final Value calculateRightEdgeValue(Value v) {
        byte[] b = new byte[v.getLength() + 1];
        System.arraycopy(v.getData(), v.getPosition(), b, 0, b.length - 1);
        b[b.length - 1] = Byte.MAX_VALUE;
        Value v2 = new Value(b);
        return v2;
    }
}
