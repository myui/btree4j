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

import btree4j.utils.io.FileUtils;
import btree4j.utils.lang.PrintUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import static btree4j.BTree.KEY_NOT_FOUND;

public class BTreeTest {
    private static final boolean DEBUG = true;
    final private int MAXN = 5000 * 100;

    @Test
    public void shouldAdd() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BTreeTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }

        BTree btree = new BTree(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            btree.addValue(k, i);
        }

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            long actual = btree.findValue(k);
            Assert.assertEquals(i, actual);
        }
    }

    @Test
    public void shouldAddThenRemove() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BTreeTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }

        BTree btree = new BTree(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            btree.addValue(k, i);
        }

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            btree.removeValue(k);
        }

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            long actual = btree.findValue(k);
            Assert.assertEquals(KEY_NOT_FOUND, actual);
        }
    }

    @Test
    public void shouldAddThenRemoveFrom() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BTreeTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }

        BTree btree = new BTree(tmpFile);
        btree.init(/* bulkload */ false);
        btree.findValue(new Value("k"));

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            btree.addValue(k, i);
        }

        btree.removeValueFrom(new Value("k" + 0));

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            long actual = btree.findValue(k);
            Assert.assertEquals(KEY_NOT_FOUND, actual);
        }
    }

    private int getFirstDigit(int a) {
        return Integer.parseInt(Integer.toString(a).substring(0, 1));
    }
    @Test
    public void shouldAddThenRemoveFromHalf() throws BTreeException {
        shouldAddThenRemoveFrom(MAXN / 2);
    }
    @Test
    public void shouldAddThenRemoveFromOneTenth() throws BTreeException {
        shouldAddThenRemoveFrom(MAXN / 10);
    }
    @Test
    public void shouldAddThenRemoveFromAndClearChild() throws BTreeException {
        shouldAddThenRemoveFrom(249905 < MAXN ? 249905 : 1);
    }
    @Test
    public void shouldAddThenRemoveFromAndChangeRoot() throws BTreeException {
        shouldAddThenRemoveFrom(433 < MAXN ? 433 : 1);
    }

    private void shouldAddThenRemoveFrom(int threshold) throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BTreeTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }

        BTree btree = new BTree(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            btree.addValue(k, i);
        }

        btree.removeValueFrom(new Value("k" + threshold));

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            long actual = btree.findValue(k);
            if (String.valueOf(i).compareTo(String.valueOf(threshold)) < 0)
                Assert.assertEquals(i, actual);
            else
                Assert.assertEquals(KEY_NOT_FOUND, actual);
        }
    }

    @Test
    public void shouldAddThenRemoveHalf500k() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BTreeTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }

        BTree btree = new BTree(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            btree.addValue(k, i);
        }

        for (int i = 0; i < MAXN/2; i++) {
            Value k = new Value("k" + i);
            btree.removeValue(k);
        }

        for (int i = 0; i < MAXN; i++) {
            Value k = new Value("k" + i);
            long actual = btree.findValue(k);
            if (i < MAXN / 2)
                Assert.assertEquals(KEY_NOT_FOUND, actual);
            else
                Assert.assertEquals(i, actual);
        }
    }

    @Test
    public void shouldAddRemoveRandom1m() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File indexFile = new File(tmpDir, "test10m.idx");
        indexFile.deleteOnExit();
        if (indexFile.exists()) {
            Assert.assertTrue(indexFile.delete());
        }

        BTree btree = new BTree(indexFile, false);
        btree.init(false);

        final Map<Value, Long> kv = new HashMap<>();
        final Random rand = new Random();
        for (int i = 0; i < 2 * MAXN; i++) {
            long nt = System.nanoTime(), val = rand.nextInt(Integer.MAX_VALUE); // FIXME val = rand.nextLong();
            Value key = new Value(String.valueOf(nt) + val);
            btree.addValue(key, val);
            if (i % 10000 == 0) {
                kv.put(key, val);
                //println("put k: " + key + ", v: " + val);
            }
            Assert.assertEquals(val, btree.findValue(key));

            //if (i % 1000000 == 0) {
            //    btree.flush();
            //}
        }
        btree.flush(true, true);
        btree.close();

        Assert.assertTrue(indexFile.exists());
        println("File size of '" + FileUtils.getFileName(indexFile) + "': "
                + PrintUtils.prettyFileSize(indexFile));

        btree = new BTree(indexFile, false);
        btree.init(false);
        for (Entry<Value, Long> e : kv.entrySet()) {
            Value k = e.getKey();
            Long v = e.getValue();
            long result = btree.findValue(k);
            Assert.assertNotEquals("key is not registered: " + k, KEY_NOT_FOUND, result);
            Assert.assertEquals("Exexpected value '" + result + "' found for key: " + k,
                    v.longValue(), result);
        }
    }

    private static void println(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }

}
