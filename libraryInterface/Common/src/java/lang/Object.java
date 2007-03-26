/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
package java.lang;

import org.jikesrvm.VM_ObjectModel;
import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.runtime.VM_Runtime;

/**
 * Jikes RVM implementation of java.lang.Object.
 * 
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 * 
 * @author Julian Dolby
 */
public class Object {

  protected Object clone() throws CloneNotSupportedException {
    return VM_Runtime.clone(this);
  }

  public boolean equals (Object o) {
    return this == o;
  }

  protected void finalize () throws Throwable {
  }

  public final Class<?> getClass() {
    return VM_ObjectModel.getObjectType(this).getClassForType();
  }
    
  public int hashCode() {
    return VM_ObjectModel.getObjectHashCode(this);
  }

  public final void notify() throws IllegalMonitorStateException {
    VM_Lock.notify(this);
  }

  public final void notifyAll() throws IllegalMonitorStateException {
    VM_Lock.notifyAll(this);
  }

  public String toString () {
    return getClass().getName() + "@" + Integer.toHexString(hashCode());
  }
  
  public final void wait () throws InterruptedException,
                                   IllegalMonitorStateException {
    VM_Lock.wait(this);
  }

  public final void wait (long time) throws InterruptedException,
                                            IllegalMonitorStateException,
                                            IllegalArgumentException {
    wait(time, 0);
  }

  public final void wait (long time, int frac)  throws InterruptedException,
                                                       IllegalMonitorStateException,
                                                       IllegalArgumentException {
    if (time >= 0 && frac >= 0 && frac < 1000000) {
      if (time == 0 && frac > 0) {
        time = 1;
      } else if (frac >= 500000) {
        time += 1;
      } 
      if (time == 0) {
        VM_Lock.wait(this);
      } else {
        VM_Lock.wait(this, time);
      }
    } else {
      throw new IllegalArgumentException();
    }
  }
}
