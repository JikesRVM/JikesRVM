/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Lock;
import com.ibm.JikesRVM.VM_Runtime;

/**
 * Jikes RVM implementation of Object.
 *
 * @author Julian Dolby
 */
public class Object {

  public Object() {
  }

  protected Object clone() throws CloneNotSupportedException {
    return VM_Runtime.clone(this);
  }

  public boolean equals (Object o) {
    return this == o;
  }

  protected void finalize () throws Throwable {
  }

  public final Class getClass() {
    return VM_ObjectModel.getObjectType(this).getClassForType();
  }
    
  public int hashCode() {
    return VM_ObjectModel.getObjectHashCode(this);
  }

  public final void notify() {
    VM_Lock.notify(this);
  }

  public final void notifyAll() {
    VM_Lock.notifyAll(this);
  }

  public final void wait () throws InterruptedException {
    VM_Lock.wait(this);
  }

  public final void wait (long time) throws InterruptedException {
    wait(time, 0);
  }

  public final void wait (long time, int frac) throws InterruptedException {
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

  public String toString () {
    return this.getClass().getName() + "@" + Integer.toHexString(this.hashCode());
  }

}
