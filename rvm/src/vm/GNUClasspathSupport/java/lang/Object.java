/*
 * Copyright IBM Corp 2002
 */
package java.lang;

import com.ibm.JikesRVM.librarySupport.SystemSupport;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
public class Object {

    private static final int j9Config = 0x6D617800;	// 'max\0'

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
        SystemSupport.wait(this); // wait forever
    }

    public final void wait (long time) throws InterruptedException {
	SystemSupport.wait(this, time); // wait at least 'time' milliseconds
    }

    public final void wait (long time, int frac) throws InterruptedException {
	if (time >= 0 && frac >= 0 && frac < 1000000) {
	    if (time == 0)
		wait (frac > 0 ? 1 : 0);
	    else if (frac >= 500000)
		wait (time + 1);
	    else
		wait (time);
	} else  throw new IllegalArgumentException();
    }

    public String toString () {
	return this.getClass().getName() + "@" + Integer.toHexString(this.hashCode());
    }

}
