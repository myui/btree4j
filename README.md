btree4j: Disk-based Prefix B+-tree written in Pure Java
======================================================= 
[![Donate](https://img.shields.io/badge/github-donate-yellow.svg)](https://github.com/sponsors/myui)
[![Build Status](https://travis-ci.org/myui/btree4j.svg?branch=master)](https://travis-ci.org/myui/btree4j)
[![License](http://img.shields.io/:license-Apache_v2-blue.svg)](https://github.com/myui/btree4j/blob/master/LICENSE)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.myui/btree4j/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.myui/btree4j)

# What's Btree4j

Btree4j is a disk-based [Prefix B+-tree](https://dl.acm.org/citation.cfm?id=320530) written in Pure Java.

# Using btree4j

```
<dependency>
    <groupId>io.github.myui</groupId>
    <artifactId>btree4j</artifactId>
    <version>0.9.0</version>
</dependency>
```

[Find usage](https://github.com/myui/btree4j/tree/master/src/test/java/btree4j) in unit tests.

# Features and Strength

Applied many improvements over the original Xindice's implementation as follows:

* Implementes [Prefix B+-tree](https://dl.acm.org/citation.cfm?id=320530) in which prefixes are selected carefully to minimize their length. In prefix B+-tree, key prefixes are managed by a [TRIE](https://en.wikipedia.org/wiki/Trie)-like smart algorithm.

> _Rudolf Bayer and Karl Unterauer. "Prefix B-trees", Proc. ACM Trans. Database Syst. 2, 1, pp.11-26), March 1977._ [[DOI](https://doi.org/10.1145/320521.320530 )]

* Pointers are [compressed](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/utils/codec/VariableByteCodec.java) using [Variable Byte Codes](https://en.wikipedia.org/wiki/Variable-length_code) so that more keys/values are fit in memory.

* Support both unique and non-unique indexing. Storing duplicate keys is allowed for non-unique indexing.

* [Index file](https://en.wikipedia.org/wiki/Indexed_file) based on B+-tree is [supported](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/BIndexFile.java). [Multiple values per a key](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/BIndexMultiValueFile.java) is also supported.
 [BIndexFile](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/BIndexFile.java) stores `<bytes[] KEY, byte[] VALUE>` with VALUE stored on distinct data pages and pointers to them are managed by [B+-Tree](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/BTree.java) to avoid consuming many disk pages for large values in B+-tree.

* Support variable-length [key](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/Key.java)/[value](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/Value.java)

* [Prefix and range search](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/indexer/BasicIndexQuery.java) is supported in addition to exact match using a [flexible callback handler](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/BTreeCallback.java). Support [LIKE match](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/indexer/LikeIndexQuery.java) using wildcards.

* Paging (virtual memory) support using [LRU cache replacement policy](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/BTree.java) and [Freespace management](https://github.com/myui/btree4j/blob/master/src/main/java/btree4j/FreeList.java).

* Deletion and updates are, of course, supported.

* Support efficient Bulk-loading.

* Minimum dependencies to external libraries. Runs on Java 8 or later.


# Sponsors

No sponsors yet. Will you be the first?

<span class="badge-githubsponsors"><a href="https://github.com/sponsors/myui" title="Donate to this project using GitHub Sponsors"><img src="https://img.shields.io/badge/github-donate-yellow.svg" alt="GitHub Sponsors donate button" /></a></span>

It will be my motivation to continue working on this project.

# Credits

Copyright 2006 and onwards Makoto Yui<br/>
Copyright 1999-2007 The Apache Software Foundation

This software is originally developed for [XBird](https://github.com/myui/xbird/) based on [Apache Xindice](https://xml.apache.org/xindice/dev/guide-internals.html#3.+Data+storage).
