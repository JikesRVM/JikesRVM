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
package java.lang.ref;

import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_OBJECT_GETFIELD_BARRIER;

import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.Barriers;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.ReferenceFieldsVary;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * The JikesRVM implementation of the java.lang.ref.Reference class.
 */
@ReferenceFieldsVary
public abstract class Reference<T> {

  private static final Offset REFERENCE_FIELD_OFFSET = RVMType.JavaLangRefReferenceReferenceField.getOffset();
  private static final int REFERENCE_FIELD_ID = RVMType.JavaLangRefReferenceReferenceField.getId();

  /**
   * The underlying object.  This field is a Address so it will not
   * be automatically kept alive by the garbage collector.
   *
   * Set and maintained by the ReferenceProcessor class.
   */
  private Address _referent;

  /**
   * Link to the next entry on the queue.  If this is null, this
   * reference is not enqueued.  Otherwise it points to the next
   * reference.  The last reference on a queue will point to itself
   * (not to null, that value is used to mark a not enqueued
   * reference). This field and its semantics is defined by the
   * default implementation of java.lang.ref.ReferenceQueue in the GNU
   * classpath release.
   * @see java.lang.ref.ReferenceQueue
 */
  Reference nextOnQueue;


  /**
   * The queue this reference is registered on. This is null, if this
   * wasn't registered to any queue or reference was already enqueued.
   */
  ReferenceQueue<T> queue;

  Reference(T ref) {
  }

  Reference(T ref, ReferenceQueue<T> q) {
    if (q == null)
      throw new NullPointerException();
    queue = q;
  }

  /**
   * Returns the object, this reference refers to.
   * @return the object, this reference refers to, or null if the
   * reference was cleared.
   */
  @SuppressWarnings("unchecked") // This method requires an unchecked cast
  public T get() {
    return (T)getInternal();
  }

  /**
   * Takes the passed address and (atomically) performs any read barrier actions
   * before returning it as an object.
   *
   * @param tmp The non-zero referent address
   * @return The referent object.
   */
  @Uninterruptible
  @Inline
  Object getInternal() {
    if (RVMType.JavaLangRefReferenceReferenceField.madeTraced()) {
      if (NEEDS_OBJECT_GETFIELD_BARRIER) {
        return Barriers.objectFieldRead(this, REFERENCE_FIELD_OFFSET, REFERENCE_FIELD_ID);
      } else {
        return Magic.getObjectAtOffset(this, REFERENCE_FIELD_OFFSET);
      }
    } else {
      Address tmp = _referent;
      if (tmp.isZero()) {
        return null;
      } else {
        Object ref = Magic.addressAsObject(tmp);

        if (Barriers.NEEDS_JAVA_LANG_REFERENCE_READ_BARRIER) {
          ref = Barriers.javaLangReferenceReadBarrier(ref);
        }
        return ref;
      }
    }
  }

  public void clear() {
    if (RVMType.JavaLangRefReferenceReferenceField.madeTraced()) {
      if (NEEDS_OBJECT_GETFIELD_BARRIER) {
        Barriers.objectFieldWrite(this, null, REFERENCE_FIELD_OFFSET, REFERENCE_FIELD_ID);
      } else {
        Magic.setObjectAtOffset(this, REFERENCE_FIELD_OFFSET, null);
      }
    }
  }

  public boolean isEnqueued() {
    return nextOnQueue != null;
  }

  /*
   * This method requires external synchronization.
   * The logically uninterruptible pragma is a bold faced lie;
   * injecting it for now to avoid a warning message during the build
   * that users might find confusing. We think the problem is actually
   * not a 'real' problem...
   */
  public boolean enqueue() {
    if (nextOnQueue == null && queue != null) {
      queue.enqueue(this);
      queue = null;
      return true;
    }
    return false;
  }

  @Uninterruptible
  public boolean enqueueInternal() {
    if (nextOnQueue == null && queue != null) {
      queue.enqueueInternal(this);
      queue = null;
      return true;
    }
    return false;
  }

  // TODO: Harmony
  void dequeue() {
    return;
  }
}
