/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import com.ibm.JikesRVM.librarySupport.SystemSupport;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
public class Object {

  public Object() {

  }

  protected Object clone() throws CloneNotSupportedException {
    return SystemSupport.clone(this);
  }

  public boolean equals (Object o) {
    return this == o;
  }

  protected void finalize () throws Throwable {

  }

  public final Class getClass() {
    return SystemSupport.getClass(this);
  }
    
  public int hashCode() {
    return SystemSupport.getDefaultHashCode(this);
  }

  public final void notify() {
    SystemSupport.notify(this);
  }

  public final void notifyAll() {
    SystemSupport.notifyAll(this);
  }

  public final void wait () throws InterruptedException {
    SystemSupport.wait(this); // wait indefinitely
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
      SystemSupport.wait(this, time);
    } else {
      throw new IllegalArgumentException();
    }
  }

  public String toString () {
    return this.getClass().getName() + "@" + Integer.toHexString(this.hashCode());
  }

}
