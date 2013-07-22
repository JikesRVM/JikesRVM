/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.deque;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;
import org.mmtk.harness.Harness;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.Plan;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Junit unit-tests for ObjectReferenceDeque.
 */
public class ObjectReferenceDequeTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.initOnce();
    Trace.enable(Item.SCHEDULER);
    Scheduler.setThreadModel(Scheduler.Model.DETERMINISTIC);
    Harness.policy.setValue("FIXED");
    Harness.yieldInterval.setValue(1);
    /* Must call this after switching scheduler */
    org.mmtk.harness.scheduler.Scheduler.initCollectors();
  }

  /**
   * Helper method to create object references
   * @param val
   * @return
   */
  private static ObjectReference o(int val) {
    return Address.fromIntSignExtend(val).toObjectReference();
  }

  /**
   * Run a test, ie a list of threads, all run in GC context.
   * @param items
   */
  private void runTest(final CollectorContext... items) {
    for (CollectorContext item : items) {
      Scheduler.scheduleCollectorContext(item);
    }
    Scheduler.scheduleGcThreads();
  }

  @Test
  public void testPushPop() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        Trace.trace(Item.SCHEDULER, "testPushPop: in");
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque = new ObjectReferenceDeque("deque",shared);

        shared.prepareNonBlocking();
        deque.push(o(1));
        Assert.assertEquals(deque.pop(),o(1));
        Assert.assertTrue(deque.isEmpty());
        Trace.trace(Item.SCHEDULER, "testPushPop: out");
      }
    });
  }

  @Test
  public void testInsertPop() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque = new ObjectReferenceDeque("deque",shared);

        shared.prepareNonBlocking();
        deque.insert(o(1));
        Assert.assertEquals(deque.pop(),o(1));
        Assert.assertTrue(deque.isEmpty());
      }
    });
  }

  @Test
  public void testPushPop2() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque = new ObjectReferenceDeque("deque",shared);

        shared.prepareNonBlocking();
        deque.push(o(1));
        deque.push(o(2));
        Assert.assertEquals(deque.pop(),o(2));
        Assert.assertEquals(deque.pop(),o(1));
        Assert.assertTrue(deque.isEmpty());
      }
    });
  }

  @Test
  public void testInsertPop2() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque = new ObjectReferenceDeque("deque",shared);

        shared.prepareNonBlocking();
        deque.insert(o(1));
        deque.insert(o(2));
        Assert.assertEquals(deque.pop(),o(1));
        Assert.assertEquals(deque.pop(),o(2));
        Assert.assertTrue(deque.isEmpty());
      }
    });
  }

  @Test
  public void testPushFlushPop() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque = new ObjectReferenceDeque("deque",shared);

        shared.prepareNonBlocking();
        deque.push(o(1));
        deque.flushLocal();
        Assert.assertEquals(deque.pop(),o(1));
        Assert.assertTrue(deque.isEmpty());
      }
    });
  }

  @Test
  public void testPushFlushPop2() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque = new ObjectReferenceDeque("deque",shared);

        shared.prepareNonBlocking();
        deque.push(o(1));
        deque.push(o(2));
        deque.flushLocal();
        Assert.assertEquals(deque.pop(),o(2));
        Assert.assertEquals(deque.pop(),o(1));
        Assert.assertTrue(deque.isEmpty());
      }
    });
  }

  @Test
  public void test2Heads() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque1 = new ObjectReferenceDeque("deque1",shared);
        ObjectReferenceDeque deque2 = new ObjectReferenceDeque("deque2",shared);

        shared.prepareNonBlocking();
        deque1.push(o(101));
        deque1.push(o(102));
        deque2.push(o(201));
        deque2.push(o(202));
        deque1.flushLocal();
        deque2.flushLocal();
        Assert.assertEquals(deque1.pop(),o(202));
        Assert.assertEquals(deque1.pop(),o(201));
        Assert.assertEquals(deque1.pop(),o(102));
        Assert.assertEquals(deque1.pop(),o(101));
        Assert.assertTrue(deque1.isEmpty());
        Assert.assertTrue(deque2.isEmpty());
      }
    });
  }

  @Test
  public void testnHeads() {
    final int NDEQUES = 4;
    final int ENTRIES = 1500; // Page and a half
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque[] deques = new ObjectReferenceDeque[NDEQUES];

        shared.prepareNonBlocking();
        for (int d=0; d < NDEQUES; d++) {
          deques[d] = new ObjectReferenceDeque("deque+d",shared);
          for (int i=0; i < ENTRIES; i++) {
            deques[d].push(o(d*100000+i+1));
          }
          deques[d].flushLocal();
        }
        for (int i=0; i < ENTRIES * NDEQUES; i++) {
          Assert.assertFalse(deques[0].pop().isNull());
        }
        for (int d=0; d < NDEQUES; d++) {
          Assert.assertTrue(deques[d].isEmpty());
        }
        shared.reset();
      }
    });
  }

  @Test
  public void testPopFlush() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque = new ObjectReferenceDeque("deque",shared);

        shared.prepareNonBlocking();
        for (int i=1; i < 10000; i++) {
          deque.push(o(i));
        }
        for (int i=1; i < 10000; i++) {
          deque.push(o(i));
          Assert.assertFalse(deque.pop().isNull());
          Assert.assertFalse(deque.pop().isNull());
          deque.flushLocal();
        }
        Assert.assertTrue(deque.isEmpty());
        shared.reset();
      }
    });
  }

  @Test
  public void testPopFlush2() {
    runTest(new CollectorContext() {
      @Override
      public void run() {
        SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
        ObjectReferenceDeque deque1 = new ObjectReferenceDeque("deque1",shared);
        ObjectReferenceDeque deque2= new ObjectReferenceDeque("deque2",shared);

        shared.prepareNonBlocking();
        for (int i=1; i < 10000; i++) {
          deque1.push(o(i));
          deque2.push(o(i));
        }
        for (int i=1; i < 10000; i++) {
          deque1.push(o(i));
          Assert.assertFalse(deque1.pop().isNull());
          Assert.assertFalse(deque1.pop().isNull());
          deque1.flushLocal();
          deque2.push(o(i));
          Assert.assertFalse(deque2.pop().isNull());
          Assert.assertFalse(deque2.pop().isNull());
          deque2.flushLocal();
        }
        Assert.assertTrue(deque1.isEmpty());
        Assert.assertTrue(deque2.isEmpty());
        shared.reset();
      }
    });
  }

  /**********************************************************************/

  int counter = 0;

  /**
   * A Collector-context thread that adds 'ins' ObjectReferences
   * into a shared Deque.  A family of 'n' InsertThreads with ordinal
   * 0,1,...,n-1 will insert all the numbers 0..(n*ins)-1 into
   * the deque, then pop entries until the Deque is entry.
   */
  abstract class AddRemoveThread extends CollectorContext {

    private final int n;
    private final int ordinal;
    private final SharedDeque shared;
    private final int ins;
    protected final ObjectReferenceDeque deque;

    public AddRemoveThread(SharedDeque shared, int n, int ordinal, int ins) {
      this.shared = shared;
      this.n = n;
      this.ordinal = ordinal;
      this.ins = ins;
      this.deque = new ObjectReferenceDeque("deque"+ordinal,shared);
    }

    @Override
    public void run() {
      for (int i=0; i < ins; i++) {
        add(o(1+i*n+ordinal));
      }
      deque.flushLocal();
      synchronized(shared) { counter += ins; }
      int d=0;
      while (!deque.isEmpty()) {
        d++;
        Assert.assertFalse(deque.pop().isNull());
        synchronized(shared) { counter--; }
      }
      //System.out.printf("Thread %d pushed %d items, popped %d%n",ordinal,ins,d);
    }

    protected abstract void add(ObjectReference o);

  }


  /**
   * An AddRemoveThread that 'push'es into the deque
   */
  private class PushThread extends AddRemoveThread {

    public PushThread(SharedDeque shared, int n, int ordinal, int ins) {
      super(shared, n, ordinal, ins);
    }

    @Override
    protected void add(ObjectReference o) {
      deque.push(o);
    }
  }

  /**
   * An AddRemoveThread that 'insert's into the deque
   */
  private class InsertThread extends AddRemoveThread {

    public InsertThread(SharedDeque shared, int n, int ordinal, int ins) {
      super(shared, n, ordinal, ins);
    }

    @Override
    protected void add(ObjectReference o) {
      deque.insert(o);
    }
  }


  @Test
  public void testConcurrentPush() {
    final SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
    shared.prepareNonBlocking();
    final int N = 4000;
    counter = 0;
    runTest(
        new PushThread(shared,8,0,N),
        new PushThread(shared,8,1,N),
        new PushThread(shared,8,2,N),
        new PushThread(shared,8,3,N),
        new PushThread(shared,8,4,N),
        new PushThread(shared,8,5,N),
        new PushThread(shared,8,6,N),
        new PushThread(shared,8,7,N));
    Assert.assertTrue(counter == 0);
    shared.reset();
  }

  @Test
  public void testConcurrentInsert() {
    final SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
    shared.prepareNonBlocking();
    final int N = 4000;
    counter = 0;
    runTest(
        new InsertThread(shared,8,0,N),
        new InsertThread(shared,8,1,N),
        new InsertThread(shared,8,2,N),
        new InsertThread(shared,8,3,N),
        new InsertThread(shared,8,4,N),
        new InsertThread(shared,8,5,N),
        new InsertThread(shared,8,6,N),
        new InsertThread(shared,8,7,N));
    Assert.assertTrue(counter == 0);
    shared.reset();
  }

  @Test
  public void testConcurrentPushInsert() {
    final SharedDeque shared = new SharedDeque("shared",Plan.metaDataSpace,1);
    shared.prepareNonBlocking();
    final int N = 4000;
    counter = 0;
    runTest(
        new InsertThread(shared,8,0,N),
        new PushThread(shared,8,1,N),
        new InsertThread(shared,8,2,N),
        new PushThread(shared,8,3,N),
        new InsertThread(shared,8,4,N),
        new PushThread(shared,8,5,N),
        new InsertThread(shared,8,6,N),
        new PushThread(shared,8,7,N));
    Assert.assertTrue(counter == 0);
    shared.reset();
  }

}
