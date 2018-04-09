/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.bookkeeper.mledger.impl;

import static org.testng.Assert.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.bookkeeper.client.BKException;
import org.apache.bookkeeper.client.BookKeeper.DigestType;
import org.apache.bookkeeper.mledger.AsyncCallbacks.AddEntryCallback;
import org.apache.bookkeeper.mledger.AsyncCallbacks.CloseCallback;
import org.apache.bookkeeper.mledger.Entry;
import org.apache.bookkeeper.mledger.ManagedCursor;
import org.apache.bookkeeper.mledger.ManagedLedger;
import org.apache.bookkeeper.mledger.ManagedLedgerConfig;
import org.apache.bookkeeper.mledger.ManagedLedgerException;
import org.apache.bookkeeper.mledger.ManagedLedgerException.BadVersionException;
import org.apache.bookkeeper.mledger.ManagedLedgerException.ManagedLedgerFencedException;
import org.apache.bookkeeper.mledger.ManagedLedgerFactory;
import org.apache.bookkeeper.mledger.Position;
import org.apache.bookkeeper.test.MockedBookKeeperTestCase;
import org.apache.zookeeper.KeeperException.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class ManagedLedgerErrorsTest extends MockedBookKeeperTestCase {

    @Test
    public void removingCursor() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ManagedCursor c1 = ledger.openCursor("c1");

        assertEquals(zkc.exists("/managed-ledgers/my_test_ledger/c1", false) != null, true);

        zkc.failNow(Code.BADVERSION);

        try {
            c1.close();
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        bkc.failNow(BKException.Code.NoSuchLedgerExistsException);

        // Cursor ledger deletion will fail, but that should not prevent the deleteCursor to fail
        ledger.deleteCursor("c1");

        assertEquals(zkc.exists("/managed-ledgers/my_test_ledger/c1", false) != null, false);
        assertEquals(bkc.getLedgers().size(), 2);
    }

    @Test
    public void removingCursor2() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.openCursor("c1");

        zkc.failNow(Code.CONNECTIONLOSS);

        try {
            ledger.deleteCursor("c1");
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }
    }

    @Test
    public void closingManagedLedger() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.openCursor("c1");
        ledger.addEntry("entry".getBytes());

        bkc.failNow(BKException.Code.NoSuchLedgerExistsException);

        try {
            ledger.close();
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // ML should be closed even if it failed before
        try {
            ledger.addEntry("entry".getBytes());
            fail("managed ledger was closed");
        } catch (ManagedLedgerException e) {
            // ok
        }
    }

    @Test
    public void asyncClosingManagedLedger() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.openCursor("c1");

        bkc.failNow(BKException.Code.NoSuchLedgerExistsException);

        final CountDownLatch latch = new CountDownLatch(1);
        ledger.asyncClose(new CloseCallback() {
            public void closeFailed(ManagedLedgerException exception, Object ctx) {
                latch.countDown();
            }

            public void closeComplete(Object ctx) {
                fail("should have failed");
            }
        }, null);

        latch.await();
    }

    @Test
    public void errorInRecovering() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        factory = new ManagedLedgerFactoryImpl(bkc, zkc);

        bkc.failNow(BKException.Code.LedgerFencedException);

        try {
            ledger = factory.open("my_test_ledger");
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // It should be fine now
        ledger = factory.open("my_test_ledger");
    }

    @Test
    public void errorInRecovering2() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        factory = new ManagedLedgerFactoryImpl(bkc, zkc);

        bkc.failAfter(1, BKException.Code.LedgerFencedException);

        try {
            ledger = factory.open("my_test_ledger");
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // It should be fine now
        ledger = factory.open("my_test_ledger");
    }

    @Test
    public void errorInRecovering3() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        factory = new ManagedLedgerFactoryImpl(bkc, zkc);

        bkc.failAfter(1, BKException.Code.LedgerFencedException);

        try {
            ledger = factory.open("my_test_ledger");
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // It should be fine now
        ledger = factory.open("my_test_ledger");
    }

    @Test
    public void errorInRecovering4() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        factory = new ManagedLedgerFactoryImpl(bkc, zkc);

        zkc.failAfter(1, Code.CONNECTIONLOSS);

        try {
            ledger = factory.open("my_test_ledger");
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // It should be fine now
        ledger = factory.open("my_test_ledger");
    }

    @Test
    public void errorInRecovering5() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        factory = new ManagedLedgerFactoryImpl(bkc, zkc);

        zkc.failAfter(2, Code.CONNECTIONLOSS);

        try {
            ledger = factory.open("my_test_ledger");
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // It should be fine now
        ledger = factory.open("my_test_ledger");
    }

    @Test
    public void errorInRecovering6() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ledger.openCursor("c1");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        factory = new ManagedLedgerFactoryImpl(bkc, zkc);

        zkc.failAfter(3, Code.CONNECTIONLOSS);

        try {
            ledger = factory.open("my_test_ledger");
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // It should be fine now
        ledger = factory.open("my_test_ledger");
    }

    @Test
    public void passwordError() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger", new ManagedLedgerConfig().setPassword("password"));
        ledger.openCursor("c1");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        try {
            ledger = factory.open("my_test_ledger", new ManagedLedgerConfig().setPassword("wrong-password"));
            fail("should fail for password error");
        } catch (ManagedLedgerException e) {
            // ok
        }
    }

    @Test
    public void digestError() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger",
                new ManagedLedgerConfig().setDigestType(DigestType.CRC32));
        ledger.openCursor("c1");
        ledger.addEntry("entry".getBytes());

        ledger.close();

        try {
            ledger = factory.open("my_test_ledger", new ManagedLedgerConfig().setDigestType(DigestType.MAC));
            fail("should fail for digest error");
        } catch (ManagedLedgerException e) {
            // ok
        }
    }

    @Test(timeOut = 20000, invocationCount = 1, skipFailedInvocations = true, enabled = false)
    public void errorInUpdatingLedgersList() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger", new ManagedLedgerConfig().setMaxEntriesPerLedger(1));

        final CountDownLatch latch = new CountDownLatch(1);

        zkc.failAfter(0, Code.CONNECTIONLOSS);

        ledger.asyncAddEntry("entry".getBytes(), new AddEntryCallback() {
            public void addFailed(ManagedLedgerException exception, Object ctx) {
                // not-ok
            }

            public void addComplete(Position position, Object ctx) {
                // ok
            }
        }, null);

        ledger.asyncAddEntry("entry".getBytes(), new AddEntryCallback() {
            public void addFailed(ManagedLedgerException exception, Object ctx) {
                latch.countDown();
            }

            public void addComplete(Position position, Object ctx) {
                fail("should have failed");
            }
        }, null);

        latch.await();
    }

    @Test
    public void recoverAfterWriteError() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ManagedCursor cursor = ledger.openCursor("c1");

        bkc.failNow(BKException.Code.BookieHandleNotAvailableException);

        // With one single error, the write should succeed
        ledger.addEntry("entry".getBytes());

        assertEquals(cursor.getNumberOfEntriesInBacklog(), 1);

        bkc.failNow(BKException.Code.BookieHandleNotAvailableException);
        zkc.failNow(Code.CONNECTIONLOSS);
        try {
            ledger.addEntry("entry".getBytes());
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        assertEquals(cursor.getNumberOfEntriesInBacklog(), 1);

        // Next add will fail as well
        try {
            ledger.addEntry("entry".getBytes());
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        assertEquals(cursor.getNumberOfEntriesInBacklog(), 1);
    }

    @Test
    public void recoverAfterZnodeVersionError() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger", new ManagedLedgerConfig().setMaxEntriesPerLedger(1));

        zkc.failNow(Code.BADVERSION);

        // First write will succeed
        ledger.addEntry("test".getBytes());

        try {
            // This write will try to create a ledger and it will fail at it
            ledger.addEntry("entry".getBytes());
            fail("should fail");
        } catch (BadVersionException e) {
            // ok
        }

        try {
            // At this point the ledger should be fenced for good
            ledger.addEntry("entry".getBytes());
            fail("should fail");
        } catch (ManagedLedgerFencedException e) {
            // ok
        }
    }

    @Test
    public void recoverLongTimeAfterWriteError() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ManagedCursor cursor = ledger.openCursor("c1");

        bkc.failNow(BKException.Code.BookieHandleNotAvailableException);

        // With one single error, the write should succeed
        ledger.addEntry("entry-1".getBytes());

        assertEquals(cursor.getNumberOfEntriesInBacklog(), 1);

        bkc.failNow(BKException.Code.BookieHandleNotAvailableException);
        zkc.failNow(Code.CONNECTIONLOSS);
        try {
            ledger.addEntry("entry-2".getBytes());
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        Thread.sleep(ManagedLedgerImpl.WaitTimeAfterLedgerCreationFailureMs / 2);
        try {
            ledger.addEntry("entry-3".getBytes());
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // After some time, the managed ledger will be available for writes again
        Thread.sleep(ManagedLedgerImpl.WaitTimeAfterLedgerCreationFailureMs / 2 + 10);

        // Next add should succeed, and the previous write should not appear
        ledger.addEntry("entry-4".getBytes());

        assertEquals(cursor.getNumberOfEntriesInBacklog(), 2);

        List<Entry> entries = cursor.readEntries(10);
        assertEquals(entries.size(), 2);
        assertEquals(new String(entries.get(0).getData()), "entry-1");
        assertEquals(new String(entries.get(1).getData()), "entry-4");
        entries.forEach(e -> e.release());
    }

    @Test
    public void recoverLongTimeAfterMultipleWriteErrors() throws Exception {
        ManagedLedgerImpl ledger = (ManagedLedgerImpl) factory.open("recoverLongTimeAfterMultipleWriteErrors");
        ManagedCursor cursor = ledger.openCursor("c1");

        bkc.failNow(BKException.Code.BookieHandleNotAvailableException,
                BKException.Code.BookieHandleNotAvailableException);

        CountDownLatch counter = new CountDownLatch(2);
        AtomicReference<ManagedLedgerException> ex = new AtomicReference<>();

        // Write 2 entries, both should fail the first time and get re-tried internally in a new ledger
        AddEntryCallback cb = new AddEntryCallback() {

            @Override
            public void addComplete(Position position, Object ctx) {
                counter.countDown();
            }

            @Override
            public void addFailed(ManagedLedgerException exception, Object ctx) {
                log.warn("Error in write", exception);
                ex.set(exception);
                counter.countDown();
            }

        };
        ledger.asyncAddEntry("entry-1".getBytes(), cb, null);
        ledger.asyncAddEntry("entry-2".getBytes(), cb, null);

        counter.await();
        assertNull(ex.get());

        assertEquals(cursor.getNumberOfEntriesInBacklog(), 2);

        // Ensure that we are only creating one new ledger
        // even when there are multiple (here, 2) add entry failed ops
        assertEquals(ledger.getLedgersInfoAsList().size(), 1);

        ledger.addEntry("entry-3".getBytes());

        List<Entry> entries = cursor.readEntries(10);
        assertEquals(entries.size(), 3);
        assertEquals(new String(entries.get(0).getData()), "entry-1");
        assertEquals(new String(entries.get(1).getData()), "entry-2");
        assertEquals(new String(entries.get(2).getData()), "entry-3");
        entries.forEach(e -> e.release());
    }

    @Test
    public void recoverAfterMarkDeleteError() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ManagedCursor cursor = ledger.openCursor("my-cursor");
        Position position = ledger.addEntry("entry".getBytes());

        bkc.failNow(BKException.Code.BookieHandleNotAvailableException);
        zkc.failNow(Code.CONNECTIONLOSS);

        try {
            cursor.markDelete(position);
            fail("should fail");
        } catch (ManagedLedgerException e) {
            // ok
        }

        // The metadata ledger is reopened in background, until it's not reopened the mark-delete will fail
        Thread.sleep(100);

        // Next markDelete should succeed
        cursor.markDelete(position);
    }

    @Test
    public void handleCursorRecoveryFailure() throws Exception {
        ManagedLedger ledger = factory.open("my_test_ledger");
        ManagedCursor cursor = ledger.openCursor("my-cursor");

        Position p0 = cursor.getMarkDeletedPosition();

        Position p1 = ledger.addEntry("entry-1".getBytes());
        cursor.markDelete(p1);

        // Re-open from a different factory
        ManagedLedgerFactory factory2 = new ManagedLedgerFactoryImpl(bkc, zkc);

        bkc.failAfter(3, BKException.Code.LedgerRecoveryException);
        ledger = factory2.open("my_test_ledger");
        cursor = ledger.openCursor("my-cursor");

        // Since the cursor was rewind, it will be back to p0
        assertEquals(cursor.getMarkDeletedPosition(), p0);
        factory2.shutdown();
    }

    private static final Logger log = LoggerFactory.getLogger(ManagedLedgerErrorsTest.class);
}