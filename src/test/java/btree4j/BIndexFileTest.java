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

import btree4j.utils.io.FileUtils;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class BIndexFileTest {

    @Test
    public void testSearch() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "BIndexFileTest1.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        BIndexFile btree = new BIndexFile(tmpFile);
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
            //System.out.println(k);
            //System.out.println(v);
            //System.out.println();
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
}
