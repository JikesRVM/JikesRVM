/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.utility.sanitychecker;

import org.mmtk.plan.StopTheWorld;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Scanning;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class performs sanity checks for StopTheWorld collectors. 
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class SanityCheckerLocal implements Uninterruptible, Constants {
  
  /* Trace */
  private SanityTraceLocal sanityTrace;
  
  /**
   * @return The global trace as a SanityChecker instance.
   */
  protected static SanityChecker global() {
    return ActivePlan.global().getSanityChecker();
  }
  
  /****************************************************************************
   * Constants
   */
  public SanityCheckerLocal() {
    sanityTrace = new SanityTraceLocal(global().trace, this);
  }

  /**
   * Perform any sanity checking collection phases.
   * 
   * @param phaseId The id to proces
   * @param primary Perform local single threaded actions on this thread
   * @return True if the phase was handled.
   */
  public final boolean collectionPhase(int phaseId, boolean primary) throws NoInlinePragma {
    if (phaseId == StopTheWorld.SANITY_PREPARE) {
      if (primary) {
        sanityTrace.prepare();
      }
      return true;
    }
    
    if (phaseId == StopTheWorld.SANITY_ROOTS) {
      Scanning.computeAllRoots(sanityTrace);
      sanityTrace.flushRoots();
      return true;
    }
    
    if (phaseId == StopTheWorld.SANITY_CHECK) {
      if (primary) {
        // Trace, checking for dangling pointers
        sanityTrace.startTrace();

        // Iterate over the reachable objects.
        Address curr = global().getSanityTable().getFirst();
        while(!curr.isZero()) {
          ObjectReference ref = SanityDataTable.getObjectReference(curr);
          int normalRC = SanityDataTable.getNormalRC(curr);
          int rootRC = SanityDataTable.getRootRC(curr);
          int expectedRC = sanityExpectedRC(ref, rootRC);
          switch (expectedRC) {
          case SanityChecker.ALIVE:
          case SanityChecker.UNSURE:
            // Always ok.
            break;
          case SanityChecker.DEAD:
            // Never ok.
            Log.write("ERROR: SanityRC = ");
            Log.write(normalRC);
            Log.write(", SpaceRC = 0 ");
            SanityChecker.dumpObjectInformation(ref);
            break;
          default:
            // A mismatch in an RC space
            if (normalRC != expectedRC) {
              Log.write("WARNING: SanityRC = ");
              Log.write(normalRC);
              Log.write(", SpaceRC = ");
              Log.write(expectedRC);
              Log.write(" ");
              SanityChecker.dumpObjectInformation(ref);      
              break;
            }
          }
          curr = global().getSanityTable().getNext(curr);
        }
      }
      return true;
    }
    
    if (phaseId == StopTheWorld.SANITY_RELEASE) {
      if (primary) {
        sanityTrace.release();
      }
      return true;
    }
    
    if (phaseId == StopTheWorld.SANITY_FORWARD) {
      if (primary) {
        TraceLocal trace = ActivePlan.local().getCurrentTrace();
        global().getSanityTable().forwardTable(trace);
      }
      return true;
    }
    
    return false;
  }
  
  /**
   * Process an object during sanity checking, validating data,
   * incrementing counters and enqueuing if this is the first
   * visit to the object.
   * 
   * @param object The object to mark.
   * @param root True If the object is a root. 
   */
  public final void processObject(TraceLocal trace, ObjectReference object, 
                            boolean root) {
    SanityChecker.referenceCount++;
    if (root) SanityChecker.rootReferenceCount++;
        
    if (object.isNull()) {
      SanityChecker.nullReferenceCount++;
      return;
    }
    
    // Get the table entry.
    Address tableEntry = global().getSanityTable().getEntry(object, true);
    
    if (SanityDataTable.incRC(tableEntry, root)) {
      SanityChecker.liveObjectCount++;
      trace.enqueue(object);
    }
  }
  
  /**
   * Return the expected reference count. For non-reference counting 
   * collectors this becomes a true/false relationship.
   *  
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   * @return The expected (root excluded) reference count.
   */
  protected int sanityExpectedRC(ObjectReference object, 
                                           int sanityRootRC) {
    
    Space space = Space.getSpaceForObject(object);
    return space.isReachable(object) 
      ? SanityChecker.ALIVE 
      : SanityChecker.DEAD;
  }
}
