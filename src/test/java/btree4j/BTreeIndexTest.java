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
package btree4j;

import btree4j.indexer.BasicIndexQuery.IndexConditionANY;
import btree4j.utils.datetime.StopWatch;
import btree4j.utils.io.FileUtils;
import btree4j.utils.lang.ArrayUtils;
import btree4j.utils.lang.Primitives;
import btree4j.utils.lang.PrintUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Test;

public class BTreeIndexTest {
    private static final boolean DEBUG = true;

    @Test
    public void testAddPutGet() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BIndexFileTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        BTreeIndex btree = new BTreeIndex(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < 1000; i++) {
            Value k = new Value("k" + i);
            Value v = new Value("v" + i);

            btree.addValue(k, v);
            if (i % 100 == 0) {
                btree.putValue(k, new Value("v" + i + "_u"));
            }
        }

        for (int i = 0; i < 1000; i++) {
            Value k = new Value("k" + i);
            final Value expected;
            if (i % 100 == 0) {
                expected = new Value("v" + i + "_u");
            } else {
                expected = new Value("v" + i);
            }
            Value actual = btree.getValue(k);
            Assert.assertEquals(expected, actual);
        }
    }

    @Test
    public void testAddPutGetAfterReopen() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BIndexFileTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        BTreeIndex btree = new BTreeIndex(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < 1000; i++) {
            Value k = new Value("k" + i);
            Value v = new Value("v" + i);

            btree.addValue(k, v);
            if (i % 100 == 0) {
                btree.putValue(k, new Value("v" + i + "_u"));
            }
        }
        btree.flush();
        btree.close();

        btree = new BTreeIndex(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < 1000; i++) {
            Value k = new Value("k" + i);
            final Value expected;
            if (i % 100 == 0) {
                expected = new Value("v" + i + "_u");
            } else {
                expected = new Value("v" + i);
            }
            Value actual = btree.getValue(k);
            Assert.assertEquals(expected, actual);
        }
    }

    @Test
    public void testBTreeIndexDup() throws IOException, BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "test1.bmidx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        println("Use index file: " + tmpFile.getAbsolutePath());
        BTreeIndexDup btree = new BTreeIndexDup(tmpFile);
        btree.init(false);

        invokeTest(btree);
    }

    @Test
    public void testBTreeIndex() throws IOException, BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "test1.bfidx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        println("Use index file: " + tmpFile.getAbsolutePath());
        BTreeIndex btree = new BTreeIndex(tmpFile, true);
        btree.init(false);

        invokeTest(btree);
    }

    private static void invokeTest(BTreeIndex btree) throws BTreeException {
        final int repeat = 1000000;
        final int max = 1000;
        final Random rand = new Random(3232328098123L);
        final int[] keys = new int[repeat];
        for (int i = 0; i < repeat; i++) {
            keys[i] = rand.nextInt(max);
        }
        Arrays.sort(keys);
        final int[] values = keys.clone();
        ArrayUtils.shuffle(values);

        final StopWatch watchdog1 = new StopWatch("Construction of " + repeat + " objects");
        btree.setBulkloading(true, 0.1f, 0.1f);
        final SortedMap<Integer, Set<Integer>> expected = new TreeMap<Integer, Set<Integer>>();
        for (int i = 0; i < repeat; i++) {
            int k = keys[i];
            int v = values[i];
            Set<Integer> vset = expected.get(k);
            if (vset == null) {
                vset = new HashSet<Integer>();
                expected.put(k, vset);
            }
            vset.add(v);
            btree.addValue(new Value(k), new Value(v));
        }
        System.err.println(watchdog1);

        final StopWatch watchdog2 = new StopWatch("Searching " + repeat + " objects");
        btree.setBulkloading(false, 0.1f, 0.1f);
        final SortedMap<Integer, Set<Integer>> actual = new TreeMap<Integer, Set<Integer>>();
        btree.search(new IndexConditionANY(), new BTreeCallback() {
            public boolean indexInfo(Value value, long pointer) {
                throw new UnsupportedOperationException();
            }

            public boolean indexInfo(Value key, byte[] value) {
                byte[] kb = key.getData();
                int kv = (int) Primitives.getLong(kb);
                int vv = (int) Primitives.getLong(value);

                Set<Integer> vset = actual.get(kv);
                if (vset == null) {
                    vset = new HashSet<Integer>();
                    actual.put(kv, vset);
                }
                vset.add(vv);
                return true;
            }
        });
        System.err.println(watchdog2);

        Assert.assertEquals(actual.size(), max);
        Assert.assertEquals(actual.size(), expected.size());
        for (Map.Entry<Integer, Set<Integer>> e : expected.entrySet()) {
            Integer key = e.getKey();
            Set<Integer> vsetExpected = e.getValue();
            Assert.assertNotNull(vsetExpected);
            Set<Integer> vsetActual = actual.get(key);
            Assert.assertEquals(vsetActual, vsetExpected);
        }

        btree.flush();

        File file = btree.getFile();
        println("File size of '" + FileUtils.getFileName(file) + "': "
                + PrintUtils.prettyFileSize(file));

        System.gc();
    }

    @Test
    public void test10mKeys() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File indexFile = new File(tmpDir, "test10m.idx");
        indexFile.deleteOnExit();
        if (indexFile.exists()) {
            Assert.assertTrue(indexFile.delete());
        }

        BTreeIndex btree = new BTreeIndex(indexFile, false);
        btree.init(false);

        final Map<Value, Long> kv = new HashMap<>();
        final Random rand = new Random();
        for (int i = 0; i < 10000000; i++) {
            long nt = System.nanoTime(), val = rand.nextLong();
            Value key = new Value(String.valueOf(nt) + val);
            Value value = new Value(val);
            btree.addValue(key, value);
            if (i % 10000 == 0) {
                kv.put(key, val);
                //println("put k: " + key + ", v: " + val);
            }
            Assert.assertEquals(value, btree.getValue(key));

            //if (i % 1000000 == 0) {
            //    btree.flush();
            //}
        }
        btree.flush();
        btree.close();

        Assert.assertTrue(indexFile.exists());
        println("File size of '" + FileUtils.getFileName(indexFile) + "': "
                + PrintUtils.prettyFileSize(indexFile));

        btree = new BTreeIndex(indexFile, false);
        btree.init(false);
        for (Entry<Value, Long> e : kv.entrySet()) {
            Value k = e.getKey();
            Long v = e.getValue();
            Value result = btree.getValue(k);
            Assert.assertNotNull("key is not registered: " + k, result);
            Assert.assertEquals("Exexpected value '" + result + "' found for key: " + k,
                v.longValue(), Primitives.getLong(result.getData()));
        }
    }

    private static void println(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }

}
