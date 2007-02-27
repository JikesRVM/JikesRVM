/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2003
 */
package java.lang.ref;

import com.ibm.jikesrvm.VM_Magic;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * The JikesRVM implementation of the java.lang.ref.Reference class.
 * @author Chris Hoffmann
 */
public abstract class Reference<T> {

  /**
   * The underlying object.  This field is a Address so it will not
   * be automatically kept alive by the garbage collector.
   */
  private Address referent;

  /**
   * The address of the next Reference object in a linked list of
   * Reference objects. This is an address to allow reference objects to
   * be garbage collected if the mutator no longer maintains strong
   * references to them.
   */
  @SuppressWarnings("unused") // Accessed via VM_EntryPoints
  private Address nextAsAddress;

  /**
   * Link to the next entry on the queue.  If this is null, this
   * reference is not enqueued.  Otherwise it points to the next
   * reference.  The last reference on a queue will point to itself
   * (not to null, that value is used to mark a not enqueued
   * reference). This field and its semantics is defined by the
   * default implementation of java.lang.ref.ReferenceQueue in the GNU
   * classpath release.
   * @see java.lang.ref.ReferenceQueue
   *
   */
  Reference<?> nextOnQueue;

  
  /**
   * The queue this reference is registered on. This is null, if this
   * wasn't registered to any queue or reference was already enqueued.
   */
  ReferenceQueue<T> queue;

  /**
   * Record whether this object has ever been enqueued, to ensure
   * phantom references are never enqueued more than once.
   */
  boolean wasEnqueued = false;

  Reference(T ref) {
    referent = VM_Magic.objectAsAddress(ref);
  }

  Reference(T ref, ReferenceQueue<T> q) {
    if (q == null)
      throw new NullPointerException();
    referent = VM_Magic.objectAsAddress(ref);
    queue = q;
  }

  /**
   * Returns the object, this reference refers to.
   * @return the object, this reference refers to, or null if the 
   * reference was cleared.
   */
  @SuppressWarnings("unchecked") // This method requires an unchecked cast
  public T get() {

    Address tmp = referent;
    
    if (tmp.isZero())
        return null;

    return (T)VM_Magic.addressAsObject(tmp);
  }

  public void clear() {
    referent = Address.zero();
  }

  public boolean isEnqueued() {
    return nextOnQueue != null;
  }

  @Uninterruptible
  public boolean wasEverEnqueued() { 
    return wasEnqueued;
  }

  /* 
   * This method requires external synchronization.
   * The logically uninterruptible pragma is a bold faced lie;
   * injecting it for now to avoid a warning message during the build
   * that users might find confusing. We think the problem is actually
   * not a 'real' problem...
   */
  @LogicallyUninterruptible
  @Uninterruptible
  public boolean enqueue() { 
    if (nextOnQueue == null && queue != null) {
      wasEnqueued = true;
      queue.enqueue(this);
      queue = null;
      return true;
    }
    return false;
  }

}
