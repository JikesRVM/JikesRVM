/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 */
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.semispace.*;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.SortTODSharedDeque;
import org.mmtk.utility.TraceGenerator;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;

/**
 * This plan has been modified slightly to perform the processing necessary
 * for GC trace generation.  To maximize performance, it attempts to remain
 * as faithful as possible to semiSpace/Plan.java.
 *
 * The generated trace format is as follows:
 *    B 345678 12
 *      (Object 345678 was created in the boot image with a size of 12 bytes)
 *    U 59843 234 47298
 *      (Update object 59843 at the slot at offset 234 to refer to 47298)
 *    S 1233 12345
 *      (Update static slot 1233 to refer to 12345)
 *    T 4567 78924
 *      (The TIB of 4567 is set to refer to 78924)
 *    D 342789
 *      (Object 342789 became unreachable)
 *    A 6860 24 346648 3
 *      (Object 6860 was allocated, requiring 24 bytes, with fp 346648 on
 *        thread 3; this allocation has perfect knowledge)
 *    a 6884 24 346640 5
 *      (Object 6864 was allocated, requiring 24 bytes, with fp 346640 on
 *        thread 5; this allocation DOES NOT have perfect knowledge)
 *    I 6860 24 346648 3
 *      (Object 6860 was allocated into immortal space, requiring 24 bytes,
 *        with fp 346648 on thread 3; this allocation has perfect knowledge)
 *    i 6884 24 346640 5
 *      (Object 6864 was allocated into immortal space, requiring 24 bytes,
 *        with fp 346640 on thread 5; this allocation DOES NOT have perfect
 *        knowledge)
 *    48954->[345]LObject;:blah()V:23   Ljava/lang/Foo;
 *      (Citation for: a) where the was allocated, fp of 48954,
 *         at the method with ID 345 -- or void Object.blah() -- and bytecode
 *         with offset 23; b) the object allocated is of type java.lang.Foo)
 *    D 342789 361460
 *      (Object 342789 became unreachable after 361460 was allocated)
 *
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class GCTrace extends SS implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  /* Spaces */
  public static RawPageSpace traceSpace = new RawPageSpace("trace", DEFAULT_POLL_FREQUENCY, META_DATA_MB);
  
  public static int TRACE = traceSpace.getDescriptor();

  /* GC state */
  public static boolean traceInducedGC = false; // True if trace triggered GC
  public static boolean deathScan = false;
  public static boolean finalDead = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    SortTODSharedDeque workList = new SortTODSharedDeque(traceSpace, 1);
    SortTODSharedDeque traceBuf = new SortTODSharedDeque(traceSpace, 1); 
    TraceGenerator.init(workList, traceBuf);
  }

  /**
   * Constructor
   */
  public GCTrace() {}

  /**
   * The planExit method is called at RVM termination to allow the
   * trace process to finish.
   */
  public final void notifyExit(int value) {
    super.notifyExit(value);
    finalDead = true;
    traceInducedGC = false;
    deathScan = true;
  }

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of pages in use (committed for use)
   * beyond the number of pages available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.<p>
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @see org.mmtk.policy.Space#acquire(int)
   * @param mustCollect if <code>true</code> then a collection is
   * required and must be triggered.  Otherwise a collection is only
   * triggered if we deem it necessary.
   * @param space the space that triggered the polling (i.e. the space
   * into which an allocation is about to occur).
   * @return True if a collection has been triggered
   */
  public boolean poll(boolean mustCollect, Space space) 
    throws LogicallyUninterruptiblePragma {
    if (getCollectionsInitiated() > 0 || !isInitialized() || space == metaDataSpace)
      return false;
    mustCollect |= stressTestGCRequired();
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = space.reservedPages() - space.committedPages();
      if (space == copySpace0 || space == copySpace1)
        required = required<<1; // must account for copy reserve
      traceInducedGC = false;
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   */
  
  public void collectionPhase(int phaseId) {
    if (traceInducedGC && phaseId == PREPARE) {
      // Do Nothing
      return;
    }
    
    if (phaseId == RELEASE) {
      if (!traceInducedGC) {
        progress = true;
        deathScan = false;
      } else {
        /* Perform the death time calculations */
        deathScan = true;
        TraceGenerator.postCollection();
        deathScan = false;
        /* release each of the collected regions */
        super.collectionPhase(phaseId);
      }
      return;
    }
    
    // Delegate up.
    super.collectionPhase(phaseId);
  }
  
  
  /****************************************************************************
   *
   * Space management
   */

  /**
   * @return Since trace induced collections are not called to free up memory,
   * their failure to return memory isn't cause for concern.
   */
  public boolean isLastGCFull() {
    return !traceInducedGC;
  }
  
  /**
   * @return the active PlanLocal as a GCTraceLocal
   */
  public static final GCTraceLocal local() {
    return ((GCTraceLocal)ActivePlan.local());
  }
  
  /**
   * @return the active Plan as a GCTrace
   */
  public static final GCTrace global() {
    return ((GCTrace)ActivePlan.global());
  }
}
