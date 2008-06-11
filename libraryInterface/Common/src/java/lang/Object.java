/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang;

import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.Pure;

/**
 * Jikes RVM implementation of {@link java.lang.Object}
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
public class Object {

  @SuppressWarnings({"PMD.ProperCloneImplementation","PMD.CloneMethodMustImplementCloneable","CloneDoesntCallSuperClone"})
  protected Object clone() throws CloneNotSupportedException {
    return RuntimeEntrypoints.clone(this);
  }

  public boolean equals(Object o) {
    return this == o;
  }

  @SuppressWarnings({"PMD.EmptyFinalizer","FinalizeDoesntCallSuperFinalize"})
  protected void finalize() throws Throwable {
  }

  @Pure
  public final Class<?> getClass() {
    return VM_ObjectModel.getObjectType(this).getClassForType();
  }

  @Pure
  public int hashCode() {
    return VM_ObjectModel.getObjectHashCode(this);
  }

  public final void notify() throws IllegalMonitorStateException {
    VM_Thread.notify(this);
  }

  public final void notifyAll() throws IllegalMonitorStateException {
    VM_Thread.notifyAll(this);
  }

  @Pure
  public String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode());
  }

  public final void wait() throws InterruptedException,
                                   IllegalMonitorStateException {
    VM_Thread.wait(this);
  }

  public final void wait(long time) throws InterruptedException,
                                            IllegalMonitorStateException,
                                            IllegalArgumentException {
    wait(time, 0);
  }

  public final void wait(long time, int frac)  throws InterruptedException,
                                                      IllegalMonitorStateException,
                                                      IllegalArgumentException {
    if (time >= 0 && frac >= 0 && frac < 1000000) {
      if (time == 0 && frac > 0) {
        time = 1;
      } else if (frac >= 500000) {
        time += 1;
      }
      if (time == 0) {
        VM_Thread.wait(this);
      } else {
        VM_Thread.wait(this, time);
      }
    } else {
      throw new IllegalArgumentException();
    }
  }
}
