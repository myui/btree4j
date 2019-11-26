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
/*
 * Copyright 1999-2004 The Apache Software Foundation.
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
 */
package btree4j;

import btree4j.utils.io.FastMultiByteArrayOutputStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@NotThreadSafe
public abstract class Paged {
    private static final Log LOG = LogFactory.getLog(Paged.class);

    public static final int DEFAULT_PAGESIZE = 1024 * 4; // 4KB page

    protected static final byte UNUSED = 0;
    protected static final byte OVERFLOW = 126;
    /** Page ID of non-existent page */
    protected static final int NO_PAGE = -1;

    //--------------------------------------------

    @Nonnull
    private final Map<Long, Page> _pages = new WeakHashMap<>(64);

    @Nonnull
    private final FileHeader _fileHeader;
    @Nonnull
    protected final File _file;

    //--------------------------------------------
    // resources

    private boolean _opened = false;
    private RandomAccessFile _raf = null;
    private FileChannel _fc = null;

    //--------------------------------------------

    public Paged(@Nonnull File file) {
        this(file, DEFAULT_PAGESIZE);
    }

    public Paged(@Nonnull File file, @Nonnegative int pageSize) {
        this._fileHeader = createFileHeader(pageSize);
        this._file = file;
    }

    @Nonnull
    public File getFile() {
        return _file;
    }

    /** create index resources and close it. */
    public boolean create() throws BTreeException {
        return create(true);
    }

    public boolean create(boolean close) throws BTreeException {
        ensureResourceOpen();
        try {
            _fileHeader.write();
        } catch (IOException e) {
            throw new BTreeException(e);
        }
        if (close) {
            close();
        } else {
            this._opened = true;
        }
        return true;
    }

    public boolean open() throws BTreeException {
        ensureResourceOpen();
        if (exists()) {
            try {
                _fileHeader.read();
            } catch (IOException e) {
                throw new BTreeException(e);
            }
            this._opened = true;
            return true;
        } else {
            this._opened = false;
            return false;
        }
    }

    @Nonnull
    protected final RandomAccessFile ensureResourceOpen() throws BTreeException {
        if (_raf == null) {
            try {
                this._raf = new RandomAccessFile(_file, "rw");
            } catch (FileNotFoundException e) {
                throw new BTreeException(e);
            }
        }
        if (_fc == null) {
            this._fc = _raf.getChannel();
        }
        return _raf;
    }

    public boolean close() throws BTreeException {
        if (_opened) {
            this._opened = false;
            // close resources
            try {
                _raf.close();
                _fc.close();
            } catch (IOException e) {
                throw new BTreeException(e);
            }
            reset();
            return true;
        } else {
            return false;
        }
    }

    protected final void checkOpened() throws BTreeException {
        if (!_opened) {
            throw new BTreeException("Not opened");
        }
    }

    private final void reset() {
        this._raf = null;
        this._fc = null;
    }

    public boolean drop() throws BTreeException {
        close();
        if (exists()) {
            return getFile().delete();
        } else {
            return true;
        }
    }

    public final boolean exists() {
        return _file.exists();
    }

    public void flush() throws BTreeException {
        try {
            if (_fileHeader._fhDirty) {
                _fileHeader.write();
            }
            _fc.force(true);
        } catch (IOException e) {
            throw new BTreeException(e);
        }
    }

    /**
     * createFileHeader must be implemented by a Paged implementation in order to create an
     * appropriate subclass instance of a FileHeader.
     *
     * @return a new FileHeader
     */
    @Nonnull
    protected abstract FileHeader createFileHeader(@Nonnegative int pageSize);

    /**
     * createPageHeader must be implemented by a Paged implementation in order to create an
     * appropriate subclass instance of a PageHeader.
     */
    @Nonnull
    protected abstract PageHeader createPageHeader();

    /**
     * getPage returns the page specified by pageNum.
     */
    @Nonnull
    protected final Page getPage(long pageNum) throws BTreeException {
        // if not check if it's already loaded in the page cache
        Page p = _pages.get(pageNum); // Check if required page is in the volatile cache
        if (p == null) {
            // if still not found we need to create it and add it to the page cache.
            p = new Page(pageNum);
            try {
                p.read(); // Load the page from disk if necessary
            } catch (IOException e) {
                throw new BTreeException(e);
            }
            _pages.put(pageNum, p);
        }
        return p;
    }

    /**
     * getFreePage returns the first free Page from secondary storage. If no Pages are available,
     * the file is grown as appropriate.
     */
    @Nonnull
    protected final Page getFreePage() throws BTreeException {
        Page p = null;
        // Synchronize read and write to the fileHeader.firstFreePage
        if (_fileHeader._firstFreePage != NO_PAGE) {
            // Steal a deleted page
            p = getPage(_fileHeader._firstFreePage);
            _fileHeader.setFirstFreePage(p._pageHeader._nextPage);
            if (_fileHeader._firstFreePage == NO_PAGE) {
                _fileHeader.setLastFreePage(NO_PAGE);
            }
        }
        if (p == null) { // No deleted pages, grow the file
            p = getPage(_fileHeader.incrTotalPageCount());
        }
        p.initPage(); // Initialize The Page Header (Cleanly)
        return p;
    }

    /**
     * unlinkPages unlinks a set of pages starting at the specified page number.
     */
    protected final void unlinkPages(long pageNum) throws BTreeException {
        unlinkPages(getPage(pageNum));
    }

    /**
     * unlinkPages unlinks a set of pages starting at the specified Page.
     */
    protected final void unlinkPages(@Nonnull Page page) throws BTreeException {
        Page nextPage = page;
        if (nextPage != null) {
            // Walk the chain and add it to the unused list
            long firstPage = nextPage._pageNum;
            long nextPageNum = nextPage.getPageHeader().getNextPage();
            while (nextPageNum != NO_PAGE) {
                nextPage = getPage(nextPageNum);
                nextPageNum = nextPage.getPageHeader().getNextPage();
            }
            long lastPage = nextPage.getPageNum();
            // Free the chain
            if (_fileHeader._lastFreePage != NO_PAGE) {
                Page p = getPage(_fileHeader._lastFreePage);
                p._pageHeader.setNextPage(firstPage);
                p.write();
            }
            if (_fileHeader._firstFreePage == NO_PAGE) {
                _fileHeader.setFirstFreePage(firstPage);
            }
            _fileHeader.setLastFreePage(lastPage);
        }
    }

    /**
     * writeValue writes the multi-paged Value starting at the specified Page.
     *
     * @param page The starting Page
     * @param value The Value to write
     */
    public final void writeValue(@Nonnull Page page, @Nonnull Value value) throws BTreeException {
        InputStream is = value.getInputStream();

        // Write as much as we can onto the primary page.
        PageHeader hdr = page.getPageHeader();
        hdr.setRecordLength(value.getLength());
        try {
            page.readData(is);
        } catch (IOException e) {
            throw new BTreeException(e);
        }

        // Write out the rest of the value onto any needed overflow pages
        Page lastPage = page;
        while (true) {
            final int available;
            try {
                available = is.available();
            } catch (IOException e) {
                throw new BTreeException(e);
            }
            if (available == 0) {
                break;
            }
            LOG.debug("page overflowed");

            Page lpage = lastPage;
            PageHeader lhdr = hdr;

            // Find an overflow page to use
            long np = lhdr.getNextPage();
            if (np != NO_PAGE) {
                // Use an existing page
                lastPage = getPage(np);
            } else {
                // Create a new overflow page
                lastPage = getFreePage();
                lhdr.setNextPage(lastPage.getPageNum());
            }

            // Mark the page as an overflow page
            hdr = lastPage.getPageHeader();
            hdr.setStatus(OVERFLOW);

            // Write some more of the value to the overflow page
            try {
                lastPage.readData(is);
            } catch (IOException e) {
                throw new BTreeException(e);
            }
            lpage.write();
        }

        // Cleanup any unused overflow pages. i.e. the value is smaller then the
        // last time it was written.
        long np = hdr.getNextPage();
        if (np != NO_PAGE) {
            unlinkPages(np);
        }
        hdr.setNextPage(NO_PAGE);
        lastPage.write();
    }

    /**
     * writeValue writes the multi-Paged Value starting at the specified page number.
     *
     * @param page The starting page number
     * @param value The Value to write
     */
    public final void writeValue(long page, @Nonnull Value value) throws BTreeException {
        writeValue(getPage(page), value);
    }

    @Deprecated
    public final long writeValue(@Nonnull Value value) throws BTreeException {
        Page p = getFreePage();
        writeValue(p, value);
        return p.getPageNum();
    }

    /**
     * readValue reads the multi-Paged Value starting at the specified Page.
     *
     * @param page The starting Page
     * @return The Value
     */
    @Nonnull
    public final Value readValue(@Nonnull Page page) throws BTreeException {
        PageHeader sph = page.getPageHeader();
        FastMultiByteArrayOutputStream bos =
                new FastMultiByteArrayOutputStream(sph.getRecordLength());

        // Loop until we've read all the pages into memory
        Page p = page;
        while (true) {
            // Add the contents of the page onto the stream
            try {
                p.writeData(bos);
            } catch (IOException e) {
                throw new BTreeException(e);
            }
            // Continue following the list of pages until we get to the end
            PageHeader ph = p.getPageHeader();
            long nextPage = ph.getNextPage();
            if (nextPage == NO_PAGE) {
                break;
            }
            p = getPage(nextPage);
        }
        // Return a Value with the collected contents of all pages
        return new Value(bos.toByteArray());
    }

    /**
     * readValue reads the multi-Paged Value starting at the specified page number.
     *
     * @param page The starting page number
     * @return The Value
     */
    @Deprecated
    @Nonnull
    public final Value readValue(long page) throws BTreeException {
        return readValue(getPage(page));
    }

    @Nonnull
    protected FileHeader getFileHeader() {
        return _fileHeader;
    }

    public abstract class FileHeader {

        private boolean _fhDirty = true;
        private int _workSize;

        //--------------------------------------------
        // persistant entry

        /** The size of the <code>FileHeader</code>. Usually 1 OS page (4096 byte). */
        private short _fhSize;
        /** The size of a <code>Page</code> */
        private int _pageSize;
        /** The number of total pages in the file */
        private long _totalPageCount;
        /** The first free page in unused secondary space */
        private long _firstFreePage = NO_PAGE;
        /** The last free page in unused secondary space */
        private long _lastFreePage = NO_PAGE;
        /** The size of the <code>PageHeader</code> */
        private byte _pageHeaderSize = PageHeader.DEFAULT_PAGE_HEADER_SIZE;

        //--------------------------------------------

        public FileHeader(int pageSize) {
            this._pageSize = pageSize;
            this._fhSize = (short) 4096;
            this._workSize = calculateWorkSize();
        }

        public final void write() throws IOException {
            if (!_fhDirty) {
                return;
            }
            _raf.seek(0);
            write(_raf);
            if (LOG.isDebugEnabled()) {
                LOG.debug("wrote file header");
            }
            this._fhDirty = false;
        }

        protected void write(RandomAccessFile raf) throws IOException {
            raf.writeShort(_fhSize);
            raf.writeInt(_pageSize);
            raf.writeLong(_totalPageCount);
            raf.writeLong(_firstFreePage);
            raf.writeLong(_lastFreePage);
            raf.writeByte(_pageHeaderSize);
        }

        public final void read() throws IOException {
            _raf.seek(0);
            read(_raf);
            this._workSize = calculateWorkSize();
        }

        protected void read(RandomAccessFile raf) throws IOException {
            this._fhSize = raf.readShort();
            this._pageSize = raf.readInt();
            this._totalPageCount = raf.readLong();
            this._firstFreePage = raf.readLong();
            this._lastFreePage = raf.readLong();
            this._pageHeaderSize = raf.readByte();
        }

        //--------------------------------------------

        public final void setFirstFreePage(long page) {
            this._firstFreePage = page;
            this._fhDirty = true;
        }

        public long getFirstFreePage() {
            return _firstFreePage;
        }

        public final void setLastFreePage(long page) {
            this._lastFreePage = page;
            this._fhDirty = true;
        }

        public long getLastFreePage() {
            return _lastFreePage;
        }

        public final long incrTotalPageCount() {
            this._fhDirty = true;
            return _totalPageCount++;
        }

        public final void setDirty(boolean dirty) {
            this._fhDirty = dirty;
        }

        public void setTotalPageCount(long pageCount) {
            this._fhDirty = true;
            this._totalPageCount = pageCount;
        }

        public final long getTotalPageCount() {
            return _totalPageCount;
        }

        public final int getPageSize() {
            return _pageSize;
        }

        public final int getWorkSize() {
            return _workSize;
        }

        private final int calculateWorkSize() {
            return _pageSize - _pageHeaderSize;
        }
    }

    public static abstract class PageHeader {

        public static final int DEFAULT_PAGE_HEADER_SIZE = 127;

        //--------------------------------------------
        // persistent entry        

        private byte _status = UNUSED;
        private int _dataLen;
        private int _recordLen;
        private long _nextPage = NO_PAGE;

        //--------------------------------------------

        public PageHeader() {}

        public PageHeader(ByteBuffer buf) {
            read(buf);
        }

        public void read(ByteBuffer buf) {
            this._status = buf.get();
            if (_status == UNUSED) {
                return;
            }
            this._dataLen = buf.getInt();
            this._recordLen = buf.getInt();
            this._nextPage = buf.getLong();
        }

        public void write(ByteBuffer buf) {
            buf.put(_status);
            buf.putInt(_dataLen);
            buf.putInt(_recordLen);
            buf.putLong(_nextPage);
        }

        //--------------------------------------------
        // getter, setter

        /** The status of this page (UNUSED, RECORD, DELETED, etc...) */
        public final void setStatus(byte status) {
            this._status = status;
        }

        /** The status of this page (UNUSED, RECORD, DELETED, etc...) */
        public final byte getStatus() {
            return _status;
        }

        /** The next page for this Record (if overflowed) */
        public final void setNextPage(long nextPage) {
            this._nextPage = nextPage;
        }

        /** The next page for this Record (if overflowed) */
        public final long getNextPage() {
            return _nextPage;
        }

        /** The length of the Data */
        public final void setDataLength(int dataLen) {
            this._dataLen = dataLen;
        }

        public final int getDataLength() {
            return _dataLen;
        }

        public final void setRecordLength(int length) {
            this._recordLen = length;
        }

        public final int getRecordLength() {
            return _recordLen;
        }
    }

    public final class Page implements Comparable<Page> {

        private final long _pageNum;
        private final PageHeader _pageHeader;
        /** The offset into the file that this page starts */
        private final long _pageOffset;

        /** The data for this page */
        private ByteBuffer _pageData = null;
        /** The position (relative) of the Data in the data array */
        private int _dataPos;

        public Page(long pageNum) {
            this._pageNum = pageNum;
            this._pageHeader = createPageHeader();
            this._pageOffset = _fileHeader._fhSize + (pageNum * _fileHeader._pageSize);
        }

        public synchronized void read() throws IOException {
            if (_pageData == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("read in page#" + _pageNum + " from page offset " + _pageOffset);
                }
                byte[] buf = new byte[_fileHeader._pageSize];
                _raf.seek(_pageOffset);
                _raf.read(buf);
                this._pageData = ByteBuffer.wrap(buf);
                _pageHeader.read(_pageData);
                this._dataPos = _fileHeader._pageHeaderSize;
            }
        }

        public synchronized void write() throws BTreeException {
            _pageData.rewind();
            _pageHeader.write(_pageData);
            try {
                _raf.seek(_pageOffset);
                _raf.write(_pageData.array());
            } catch (IOException e) {
                throw new BTreeException(e);
            }
        }

        /**
         * Flushes content of the dirty page into the file
         */
        public void flush() throws IOException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("write out page#" + _pageNum + " to page offset " + _pageOffset);
            }
            _raf.seek(_pageOffset);
            _raf.write(_pageData.array());
        }

        public void writeData(OutputStream os) throws IOException {
            if (_pageHeader._dataLen > 0) {
                byte[] b = new byte[_pageHeader._dataLen];
                _pageData.position(_dataPos);
                _pageData.get(b);
                os.write(b);
            }
        }

        public void readData(InputStream is) throws IOException {
            // set data length in the page header
            int avail = is.available();
            int datalen = _fileHeader._workSize;
            if (avail < datalen) {
                datalen = avail;
            }
            _pageHeader.setDataLength(datalen);
            // read data from stream
            if (datalen > 0) {
                byte[] b = new byte[datalen];
                is.read(b);
                // Explicit cast for compatibility with covariant return type on JDK 9's ByteBuffer
                ((Buffer) _pageData).position(getDataPos());
                _pageData.put(b);
            }
        }

        public PageHeader getPageHeader() {
            return _pageHeader;
        }

        public long getPageNum() {
            return _pageNum;
        }

        private int getDataPos() {
            return _dataPos;
        }

        protected void initPage() {
            _pageHeader.setNextPage(NO_PAGE);
            _pageHeader.setStatus(UNUSED);
        }

        public int compareTo(Page other) {
            return (int) (_pageNum - other._pageNum);
        }

        @Override
        public String toString() {
            return "page#" + _pageNum;
        }

    }

}
