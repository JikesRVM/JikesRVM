/* -*-coding: iso-8859-1 -*-
 * 
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 */
package org.mmtk.utility;

import org.mmtk.vm.Plan;
import org.mmtk.vm.ReferenceGlue;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class manages soft, weak and phantom references.
 * The VM maintains a list of pending reference objects of the various
 * types.  This list is either outside the heap or uses addresses,
 * so that a reference will not stay alive during GC if it is not
 * used elsewhere in the mutator.  During GC, the various lists are
 * processed in the proper order to determine if any reference objects
 * are no longer active or whether referents that have died should be
 * kept alive.
 *
 * Loosely based on Finalizer.java
 * 
 * @author Chris Hoffmann
 * @modified Andrew Gray
 */
public class ReferenceProcessor implements Uninterruptible {

  public static final int SOFT_SEMANTICS = 0;
  public static final int WEAK_SEMANTICS = 1;
  public static final int PHANTOM_SEMANTICS = 2;

  public static final String semanticStrings [] = {
    "SOFT", "WEAK", "PHANTOM" };

  private static boolean clearSoftReferences = false;

  /* Debug flags */
  private static final boolean  TRACE        = false;
  private static final boolean  TRACE_DETAIL = false;

  /* Suppress default constructor for noninstantiability */
  private ReferenceProcessor() {
    /* constructor will never be invoked */
  }

  /**
   * Scan references with the specified semantics.
   * @param semantics the number representing the semantics
   */
  private static void traverse(int semantics)
    throws LogicallyUninterruptiblePragma {

    if (TRACE) {
      Log.write("Starting ReferenceProcessor.traverse(");
      Log.write(semanticStrings[semantics]);
      Log.writeln(")");
    }
    
    ReferenceGlue.scanReferences(semantics);

    if (TRACE) {
      Log.writeln("Ending ReferenceProcessor.traverse()");
    }
  }

  /**
   * Process a reference with the specified semantics.
   * @param reference the address of the reference. This may or may not
   * be the address of a heap object, depending on the VM.
   * @param semantics the code number of the semantics
   */
  public static Address processReference(Address reference,
                                            int semantics)
  {
    Assert._assert(!reference.isZero());
    
    if (TRACE) {
      Log.write("+++ old reference: "); Log.writeln(reference);
    }

    Address newReference;
    /*
     * If the reference is dead, we're done with it. Let it (and
     * possibly its referent) be garbage-collected.
     */
    if (ReferenceGlue.REFERENCES_ON_HEAP && !Plan.isLive(reference)) {
      newReference = Address.zero();
    } else {
      /* Otherwise... */
      if (ReferenceGlue.REFERENCES_ON_HEAP)
        newReference = Plan.getForwardedReference(reference);
      else
        newReference = reference;
      Address oldReferent = ReferenceGlue.getReferent(reference);

      if (TRACE_DETAIL) {
        Log.write("    new reference: "); Log.writeln(newReference);
        Log.write(" old referENT: "); Log.writeln(oldReferent);
      }
      /*
       * If the application has cleared the referent the Java spec says
       * this does not cause the Reference object to be enqueued. We
       * simply allow the Reference object to fall out of our
       * waiting list.
       */
      if (oldReferent.isZero()) {
        newReference = Address.zero();
      } else {
        boolean enqueue = false;

        if (semantics == PHANTOM_SEMANTICS && !Plan.isLive(oldReferent))
          {
            /*
             * Keep phantomly reachable objects from being collected
             * until they are completely unreachable.
             */
            if (TRACE_DETAIL) {
              Log.write("    resurrecting: "); Log.writeln(oldReferent);
            }
            Plan.makeAlive(oldReferent);
            enqueue = true;
          }
        else if (semantics == SOFT_SEMANTICS && !clearSoftReferences) {
          /*
           * Unless we've completely run out of memory, we keep
           * softly reachable objects alive.
           */
          if (TRACE_DETAIL) {
            Log.write("    resurrecting: "); Log.writeln(oldReferent);
          }
          Plan.makeAlive(oldReferent);
        }
          
        if (Plan.isLive(oldReferent)) {
          /*
           * Referent is still reachable in a way that is as strong as
           * or stronger than the current reference level.
           */
          Address newReferent = Plan.getForwardedReference(oldReferent);

          if (TRACE) {
            Log.write(" new referENT: "); Log.writeln(newReferent);
          }
            
          /*
           * The reference object stays on the waiting list, and the
           * referent is untouched. The only thing we must do is
           * ensure that the former addresses are updated with the
           * new forwarding addresses in case the collector is a
           * copying collector.
           */
          
          /* Update the referent */
          ReferenceGlue.setReferent(newReference, newReferent);
        }
        else {
          /* Referent is unreachable. */
            
          if (TRACE) {
            Log.write(" UNREACHABLE:  "); Log.writeln(oldReferent);
          }

          /*
           * Weak and soft references always clear the referent
           * before enqueueing. We don't actually call
           * Reference.clear() as the user could have overridden the
           * implementation and we don't want any side-effects to
           * occur.
           */
          if (semantics != PHANTOM_SEMANTICS) {
            if (TRACE_DETAIL) {
              Log.write(" clearing: "); Log.writeln(oldReferent);
            }
            ReferenceGlue.setReferent(newReference, Address.zero());
          }
          enqueue = true;
        }

        if (enqueue) {
          /*
           * Ensure phantomly reachable objects are enqueued only
           * the first time they become phantomly reachable.
           */
          ReferenceGlue.enqueueReference(newReference,
                                         semantics == PHANTOM_SEMANTICS);
            
        }
      }
    }
    return newReference;
  }

  /**
   * Set flag indicating if soft references referring to non-strongly
   * reachable objects should be cleared during GC. Usually this is 
   * false so the referent will stay alive. But when memory becomes
   * scarce the collector should reclaim all such objects before it is
   * forced to throw an OutOfMemory exception. Note that this flag
   * applies only to the next collection. After each collection the
   * setting is restored to false.
   * @param set <code>true</code> if soft references should be cleared
   */
  public static void setClearSoftReferences(boolean set) {
    clearSoftReferences = set;
  }

  /**
   * Process soft references.
   */
  public static void processSoftReferences() {
    traverse(SOFT_SEMANTICS);
    clearSoftReferences = false;
  }
  
  /**
   * Process weak references.
   */
  public static void processWeakReferences() {
    traverse(WEAK_SEMANTICS);
  }
  
  /**
   * Process phantom references.
   */
  public static void processPhantomReferences() {
    traverse(PHANTOM_SEMANTICS);
  }
}
