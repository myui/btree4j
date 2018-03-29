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
package btree4j;

import btree4j.indexer.BasicIndexQuery.IndexConditionBW;
import btree4j.utils.io.FileUtils;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class BTreeTest {

    @Test
    public void test() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BTreeTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }

        BTree btree = new BTree(tmpFile);
        btree.init(/* bulkload */ false);

        for (int i = 0; i < 1000; i++) {
            Value k = new Value("k" + i);
            long v = i;
            btree.addValue(k, v);
        }

        for (int i = 0; i < 1000; i++) {
            Value k = new Value("k" + i);
            long expected = i;
            long actual = btree.findValue(k);
            Assert.assertEquals(expected, actual);
        }

        btree.search(new IndexConditionBW(new Value("k" + 900), new Value("k" + 910)),
            new BTreeCallback() {

                @Override
                public boolean indexInfo(Value value, long pointer) {
                    //System.out.println(pointer);
                    return true;
                }

                @Override
                public boolean indexInfo(Value key, byte[] value) {
                    throw new UnsupportedOperationException();
                }
            });
    }

}
