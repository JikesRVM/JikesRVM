/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang.ref;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.memoryManagers.JMTk.ReferenceProcessor;

/**
 * The JikesRVM implementation of the java.lang.ref.Reference class.
 * @author Chris Hoffmann
 */
public abstract class Reference {

  /**
   * The underlying object.  This field is a VM_Address so it will not
   * be automatically kept alive by the garbage collector.
   */
  private VM_Address referent;

  /**
   * The address of the next Reference object in a linked list of
   * Reference objects. This is an address to allow reference objects to
   * be garbage collected if the mutator no longer maintains strong
   * references to them.
   */
  private VM_Address nextAsAddress;

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
  Reference nextOnQueue;

  
  /**
   * The queue this reference is registered on. This is null, if this
   * wasn't registered to any queue or reference was already enqueued.
   */
  ReferenceQueue queue;

  /**
   * Record whether this object has ever been enqueued, to ensure
   * phantom references are never enqueued more than once.
   */
  boolean wasEnqueued = false;

  Reference(Object ref) {
    referent = VM_Magic.objectAsAddress(ref);
  }

  Reference(Object ref, ReferenceQueue q) {
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
  public Object get() {

    VM_Address tmp = referent;
    
    if (tmp.isZero())
        return null;

    return VM_Magic.addressAsObject(tmp);
  }

  public void clear() {
    referent = VM_Address.zero();
  }

  public boolean isEnqueued() {
    return nextOnQueue != null;
  }

  public boolean wasEverEnqueued() throws VM_PragmaUninterruptible {
    return wasEnqueued;
  }

  /* 
   * This method requires external synchronization.
   * The logically uninterruptible pragma is a bold faced lie;
   * injecting it for now to avoid a warning message during the build
   * that users might find confusing. We think the problem is actually
   * not a 'real' problem...
   */
  public boolean enqueue() throws VM_PragmaUninterruptible, 
                                  VM_PragmaLogicallyUninterruptible {
    if (nextOnQueue == null && queue != null) {
      wasEnqueued = true;
      queue.enqueue(this);
      queue = null;
      return true;
    }
    return false;
  }

}
