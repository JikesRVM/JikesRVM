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
package org.jikesrvm;

import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.scheduler.SystemThread;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.vmmagic.pragma.NonMoving;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class ThreadJMXTest {

  private static ThreadMXBean threadJmxBean;

  @BeforeClass
  public static void setupMXBeans() {
    threadJmxBean = ManagementFactory.getThreadMXBean();
  }

  @Test
  public void queryingThreadMxBeanDoesntCauseErros() {
    long tid = Thread.currentThread().getId();
    ThreadInfo tInfo = threadJmxBean.getThreadInfo(tid);
    assertThat(tInfo, notNullValue());
  }

  @Test
  public void queryingThreadMxBeanForSystemThreadYieldsNull() {
    SystemThread st = new TestSystemThread();
    st.start();
    long tid = st.getRVMThread().getJavaLangThread().getId();
    ThreadInfo tInfo = threadJmxBean.getThreadInfo(tid);
    assertThat(tInfo, nullValue());
  }


  @Test
  public void queryingInvalidThreadIdsYieldsNull() {
    long tid = Long.MAX_VALUE / 2;
    ThreadInfo tInfo = threadJmxBean.getThreadInfo(tid);
    assertThat(tInfo, nullValue());
  }


  @Test
  public void arrayOfThreadIdsIsNonEmpty() throws Exception {
    assertThat(threadJmxBean.getAllThreadIds().length, greaterThanOrEqualTo(0));
  }

  @NonMoving
  private static class TestSystemThread extends SystemThread {

    TestSystemThread() {
      super("test-system-thread");
    }

    @Override
    public void run() {
      while (true) {
        try {
          synchronized (this) {
            this.wait();
          }
        } catch (IllegalMonitorStateException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

}
