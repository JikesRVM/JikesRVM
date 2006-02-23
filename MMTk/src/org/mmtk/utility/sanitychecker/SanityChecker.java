/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.utility.sanitychecker;

import org.mmtk.plan.Trace;
import org.mmtk.plan.StopTheWorld;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.ObjectModel;
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
public final class SanityChecker implements Uninterruptible, Constants {
  
  /* Counters */
  public static long referenceCount;
  public static long rootReferenceCount;
  public static long danglingReferenceCount;
  public static long nullReferenceCount;
  public static long liveObjectCount;

  public static final int DEAD              = -2;
  public static final int ALIVE             = -1;
  public static final int UNSURE            =  0;
  
  public static final int SANITY_DATA_MB       = 32;
  public static final int LOG_SANITY_DATA_SIZE = 21; 
  public static final RawPageSpace sanitySpace = new RawPageSpace("sanity", Integer.MAX_VALUE, SANITY_DATA_MB);
  public static final int SANITY               = sanitySpace.getDescriptor(); 

  /* Trace */
  public Trace trace;
  private SanityDataTable sanityTable;
  private boolean preGCSanity;
  
  /****************************************************************************
   * Constants
   */
  public SanityChecker() {
    sanityTable = new SanityDataTable(sanitySpace, LOG_SANITY_DATA_SIZE);
    trace = new Trace(sanitySpace);
    preGCSanity = true;
  }
  
  /**
   * @return The current sanity data table.
   */
  public SanityDataTable getSanityTable() {
    return sanityTable;
  }
  
  /** 
   * @return True if this is pre-gc sanity, false if post-gc 
   */
  public boolean preGCSanity() {
    return preGCSanity;
  }
  
  /**
   * Perform any sanity checking collection phases.
   * 
   * @param phaseId The id to proces
   * @return True if the phase was handled.
   */
  public boolean collectionPhase(int phaseId) throws NoInlinePragma {
    if (phaseId == StopTheWorld.SANITY_PREPARE) {
      Log.writeln("");
      Log.write("============================== GC Sanity Checking ");
      Log.writeln("==============================");
      Log.writeln("Performing Sanity Checks...");
      
      // Reset counters
      referenceCount = 0;
      nullReferenceCount = 0;
      liveObjectCount = 0;
      danglingReferenceCount = 0;
      rootReferenceCount = 0;
      
      // Clear data space
      sanityTable.acquireTable();
      
      trace.prepare();
      return true;
    }
    
    if (phaseId == StopTheWorld.SANITY_ROOTS) {
      Scanning.resetThreadCounter();
      return true;
    }
    
    if (phaseId == StopTheWorld.SANITY_RELEASE) {
      trace.release();
      sanityTable.releaseTable();
   
      Log.writeln("roots\tobjects\tdangling");
      Log.write(rootReferenceCount);Log.write("\t");
      Log.write(liveObjectCount);Log.write("\t");
      Log.writeln(danglingReferenceCount);
      
      Log.write("========================================");
      Log.writeln("========================================");
      
      preGCSanity = !preGCSanity;
      return true;
    }
    
    return false;
  }
  
  /**
   * Print out object information (used for warning and error messages)
   * 
   * @param object The object to dump info for.
   */
  public static void dumpObjectInformation(ObjectReference object) {
    Log.write(object);
    Log.write(" [");
    Log.write(Space.getSpaceForObject(object).getName());
    Log.write("] ");
    Log.writeln(ObjectModel.getTypeDescriptor(object));
  }
}
