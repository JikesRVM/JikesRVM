/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount;

import org.mmtk.plan.StopTheWorldLocal;
import org.mmtk.policy.LargeRCObjectLocal;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.scan.*;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a base functionality of a simple
 * non-concurrent reference counting collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class RCBaseLocal extends StopTheWorldLocal
  implements Uninterruptible {

  /**
   * @return The active global plan as an <code>RCBase</code> instance.
   */
  private static final RCBase global() throws InlinePragma {
    return (RCBase)ActivePlan.global();
  }

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  public RefCountLocal rc;
  public LargeRCObjectLocal los;

  // queues (buffers)
  protected ObjectReferenceDeque decBuffer;
  protected ObjectReferenceDeque modBuffer;
  protected ObjectReferenceDeque newRootSet;

  // Enumerators
  public RCDecEnumerator decEnum;
  public RCModifiedEnumerator modEnum;
  public RCSanityEnumerator sanityEnum;


  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public RCBaseLocal() {
    if (RCBase.WITH_COALESCING_RC) {
      modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
      modEnum = new RCModifiedEnumerator();
    }
    decBuffer = new ObjectReferenceDeque("dec buf", global().decPool);
    newRootSet = new ObjectReferenceDeque("root set", global().rootPool);
    los = new LargeRCObjectLocal(RCBase.loSpace);
    rc = new RefCountLocal(RCBase.rcSpace, los, decBuffer, newRootSet);
    decEnum = new RCDecEnumerator();
    if (RefCountSpace.RC_SANITY_CHECK) sanityEnum = new RCSanityEnumerator(rc);
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.  This exists
   * to support {@link PlanLocal#getOwnAllocator(Allocator)}.<p>
   *
   * Note that we override <code>los</code>, so we must account for it
   * here (rather than in our superclass).
   *
   * @see PlanLocal#getOwnAllocator(Allocator)
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  public Space getSpaceFromAllocator(Allocator a) {
    if (a == rc)  return RCBase.rcSpace;
    if (a == los) return RCBase.loSpace;
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link PlanLocal#getOwnAllocator(Allocator)}.<p>
   *
   * Note that we override <code>los</code>, so we must account for it
   * here (rather than in our superclass).
   *
   * @see PlanLocal#getOwnAllocator(Allocator)
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == RCBase.rcSpace) return rc;
    if (space == RCBase.loSpace) return los;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Pointer enumeration
   */

  /**
   * A field of an object is being enumerated by ScanObject as part of
   * a recursive decrement (when an object dies, its referent objects
   * must have their counts decremented).  If the field points to the
   * RC space, decrement the count for the referent.
   *
   * @param location The address of a reference field with an object
   * being enumerated.
   */
  public final void enumerateDecrementPointerLocation(Address location)
  throws InlinePragma {
    ObjectReference object = location.loadObjectReference();
    if (RCBase.isRCObject(object)) {
      decBuffer.push(object);
    }
  }


  /**
   * A field of an object in the modified buffer is being enumerated
   * by ScanObject. If the field points to the RC space, increment the
   * count of the referent object.
   *
   * @param objLoc The address of a reference field with an object
   * being enumerated.
   */
  public void enumerateModifiedPointerLocation(Address objLoc)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(RCBase.WITH_COALESCING_RC);
    ObjectReference object = objLoc.loadObjectReference();
    if (RCBase.isRCObject(object)) RefCountSpace.incRC(object);
  }

  /****************************************************************************
   *
   * RC methods
   */

  /**
   * Add an object to the decrement buffer
   *
   * @param object The object to be added to the decrement buffer
   */
  public final void addToDecBuf(ObjectReference object)
  throws InlinePragma {
    decBuffer.push(object);
  }

  /**
   * Add an object to the root set
   *
   * @param root The object to be added to root set
   */
  public final void addToRootSet(ObjectReference root)
  throws InlinePragma {
    newRootSet.push(root);
  }

  /**
   * Process the modified object buffers, enumerating each object's
   * fields
   */
  public final void processModBufs() {
    modBuffer.flushLocal();
    ObjectReference object;
    while (!(object = modBuffer.pop()).isNull()) {
      RefCountSpace.makeUnlogged(object);
      Scan.enumeratePointers(object, modEnum);
    }
  }

  /**
   * Trace a reference during an increment sanity traversal.  This is
   * only used as part of the ref count sanity check, and it forms the
   * basis for a transitive closure that assigns a reference count to
   * each object.
   *
   * @param object The object being traced
   * @param location The location from which this object was
   * reachable, null if not applicable.
   * @param root <code>true</code> if the object is being traced
   * directly from a root.
   */
  public void incSanityTrace(ObjectReference object, Address location,
                            boolean root) {
    if (RCBase.isRCObject(object)) {
      if (RefCountSpace.incSanityRC(object, root))
        Scan.enumeratePointers(object, sanityEnum);
    } else if (RefCountSpace.markSanityRC(object))
      Scan.enumeratePointers(object, sanityEnum);
  }

  /**
   * Trace a reference during an check sanity traversal.  This is only
   * used as part of the ref count sanity check, and it forms the
   * basis for a transitive closure that checks reference counts
   * against sanity reference counts.  If the counts are not matched,
   * an error is raised.
   *
   * @param object The object being traced
   * @param location The location from which this object was
   * reachable, null if not applicable.
   */
  public void checkSanityTrace(ObjectReference object,
                                     Address location) {
    if (RCBase.isRCObject(object)) {
      if (RefCountSpace.checkAndClearSanityRC(object)) {
        Scan.enumeratePointers(object, sanityEnum);
        rc.addLiveSanityObject(object);
      }
    } else if (RefCountSpace.unmarkSanityRC(object))
      Scan.enumeratePointers(object, sanityEnum);
  }
}
