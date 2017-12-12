/*
 * @(#)$Id$
 *
 * Copyright 2006-2008 Makoto YUI
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
 * 
 * Contributors:
 *     Makoto YUI - initial implementation
 */
package btree4j;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Assert;

import btree4j.indexer.BasicIndexQuery.IndexConditionANY;
import junit.framework.TestCase;
import xbird.storage.DbException;
import xbird.util.datetime.StopWatch;
import xbird.util.io.FileUtils;
import xbird.util.lang.ArrayUtils;
import xbird.util.lang.PrintUtils;
import xbird.util.primitive.Primitives;

/**
 * 
 * <DIV lang="en"></DIV> <DIV lang="ja"></DIV>
 * 
 * @author Makoto YUI (yuin405+xbird@gmail.com)
 */
public class BIndexMultiValueFileTest extends TestCase {

    public void testBIndexMultiValueFile() throws IOException, DbException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "test1.bmidx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        System.out.println("Use index file: " + tmpFile.getAbsolutePath());
        BIndexMultiValueFile btree = new BIndexMultiValueFile(tmpFile);
        btree.init(false);

        invokeTest(btree);
    }

    public void testBIndexFile() throws IOException, DbException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "test1.bfidx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        System.out.println("Use index file: " + tmpFile.getAbsolutePath());
        BIndexFile btree = new BIndexFile(tmpFile, true);
        btree.init(false);

        invokeTest(btree);
    }

    private static void invokeTest(BIndexFile btree) throws DbException {
        final int repeat = 10000000;
        final int max = 1000;
        final Random rand = new Random(3232328098123L);
        final int[] keys = new int[repeat];
        for (int i = 0; i < repeat; i++) {
            keys[i] = rand.nextInt(max);
        }
        Arrays.sort(keys);
        final int[] values = ArrayUtils.copy(keys);
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
        btree.search(new IndexConditionANY(), new CallbackHandler() {
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
        System.err.println("File size of '" + FileUtils.getFileName(file) + "': "
                + PrintUtils.prettyFileSize(file));

        System.gc();
    }

}
