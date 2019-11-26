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
package btree4j.benchmark;

import btree4j.BTreeIndex;
import btree4j.BTreeException;
import btree4j.Value;
import btree4j.utils.io.FileUtils;

import java.io.File;

import org.junit.Assert;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
public class JMHBenchmark {

    private BTreeIndex btree;

    @Setup
    public void setup() throws BTreeException {
        File tmpDir = FileUtils.getTempDir();
        Assert.assertTrue(tmpDir.exists());
        File tmpFile = new File(tmpDir, "JMHBenchmark.idx");
        tmpFile.deleteOnExit();
        if (tmpFile.exists()) {
            Assert.assertTrue(tmpFile.delete());
        }
        this.btree = new BTreeIndex(tmpFile);
        btree.init(/* bulkload */ false);
    }

    @TearDown
    public void tearDown() throws BTreeException {
        btree.close();
    }

    //
    // JMHBenchmark.benchAdd1k       thrpt   10   98079.705 ± 9358.808  ops/s    
    //
    @Benchmark
    @OperationsPerInvocation(1000)
    public void benchAdd1k() throws BTreeException {
        runAdd(1000);
    }

    //
    // JMHBenchmark.benchAdd1kGet1k  thrpt   10  146025.716 ± 7516.560  ops/s
    //
    @Benchmark
    @OperationsPerInvocation(2000)
    public void benchAdd1kGet1k() throws BTreeException {
        runAdd(1000);
        runGet(1000);
    }

    public void runAdd(final int count) throws BTreeException {
        for (int i = 0; i < count; i++) {
            Value k = new Value("k" + i);
            Value v = new Value("v" + i);

            btree.addValue(k, v);
            if (i % 100 == 0) {
                btree.putValue(k, new Value("v" + i + "_u"));
            }
        }
    }

    public void runGet(final int count) throws BTreeException {
        for (int i = 0; i < count; i++) {
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

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(JMHBenchmark.class.getSimpleName())
                                          .forks(1)
                                          .warmupIterations(5)
                                          .measurementIterations(10)
                                          .mode(Mode.Throughput)
                                          .build();

        new Runner(opt).run();
    }
}
