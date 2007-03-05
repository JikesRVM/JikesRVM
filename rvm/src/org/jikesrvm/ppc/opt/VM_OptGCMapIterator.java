/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.ppc.opt;
import org.jikesrvm.VM;
import org.jikesrvm.opt.VM_OptGenericGCMapIterator;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * An instance of this class provides iteration across the references 
 * represented by a frame built by the OPT compiler.
 *
 * The architecture-specific version of the GC Map iterator.  It inherits
 * its architecture-independent code from VM_OptGenericGCMapIterator.
 * This version is for the PowerPC
 *
 * @author Michael Hind
 */
@Uninterruptible public abstract class VM_OptGCMapIterator extends VM_OptGenericGCMapIterator {

  private static final boolean DEBUG = false;

  public VM_OptGCMapIterator(WordArray registerLocations) {
    super(registerLocations);
  }

  /** 
   * If any non-volatile gprs were saved by the method being processed
   * then update the registerLocations array with the locations where the
   * registers were saved.
   *
   */
  protected void updateLocateRegisters() {

    //  HIGH MEMORY
    //
    //    +------------------+                                           
    //    |  NVolGPR[n]      |
    //    |     ...          |   k == info.getFirstNonVolatileGPR()      
    //    |  NVolGPR[k]      |  <-- info.getUnsignedNonVolatileOffset()      
    //    +------------------+                                           
    //    |  ScratchGPR[LAST]|                                           
    //    |     ...          |     only SaveVolatile Frames              
    //    | ScratchGPR[FIRST]|                                           
    //    +------------------+                                           
    //    |  VolGPR[LAST]    |                                           
    //    |     ...          |     only SaveVolatile Frames              
    //    |  VolGPR[FIRST]   |                                           
    //    +------------------+                                           
    //
    //  LOW MEMORY
    
    int frameOffset = compiledMethod.getUnsignedNonVolatileOffset();
    if (frameOffset >= 0) {

      // get to the nonVol area
      Address nonVolArea = framePtr.plus(frameOffset);
      
      // update non-volatiles that were saved
      int first = compiledMethod.getFirstNonVolatileGPR();
      if (first >= 0) {
        // move to the beginning of the save area for nonvolatiles
        Address location = nonVolArea;
        for (int i = first; i <= LAST_GCMAP_REG; i++) {
          registerLocations.set(i, location.toWord());
          location = location.plus(BYTES_IN_ADDRESS);
        }
      }
      
      // update volatiles if needed
      if (compiledMethod.isSaveVolatile()) {
        // move to the beginning of the save area for volatiles
        Address location = nonVolArea.minus(SAVE_VOL_SIZE);
        
        // Walk the saved volatiles, updating registerLocations array
        for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; i++) {
          registerLocations.set(i, location.toWord());
          location = location.plus(BYTES_IN_ADDRESS);
        }
        
        // Walk the saved scratch, updating registerLocations array
        for (int i = FIRST_SCRATCH_GPR; i <= LAST_SCRATCH_GPR; i++) {
          registerLocations.set(i, location.toWord());
          location = location.plus(BYTES_IN_ADDRESS);
        }
      }
    }
  }

  /** 
   *  Determine the stack location given the frame ptr and spill offset.
   *  (The offset direction varies among architectures.)
   *  @param framePtr the frame pointer
   *  @param offset  the offset 
   *  @return the resulting stack location
   */
  public Address getStackLocation(Address framePtr, int offset) {
    return framePtr.plus(offset);
  }

  /** 
   *  Get address of the first spill location for the frame ptr.
   *  @return the first spill location
   */
  public Address getFirstSpillLoc() {
    return framePtr.plus(SPILL_DISTANCE_FROM_FP);
  }

  /** 
   *  Get address of the last spill location for the frame ptr.
   *
   *  @return the last spill location, if no spills occur, we return the
   *          first spill location
   */
  public Address getLastSpillLoc() {
    if (DEBUG) {
      VM.sysWrite("\n unsigendNVOffset: ");
      VM.sysWrite(compiledMethod.getUnsignedNonVolatileOffset());
      VM.sysWrite("\t isSaveVolatile: ");
      if (compiledMethod.isSaveVolatile()) {
        VM.sysWrite("true");
      } else {
        VM.sysWrite("false");
      }
      VM.sysWrite("\nLAST_VOLATILE_GPR: ");
      VM.sysWrite(LAST_VOLATILE_GPR);
      VM.sysWrite("\tFIRST_VOLATILE_GPR: ");
      VM.sysWrite(LAST_VOLATILE_GPR);
      VM.sysWrite("\nLAST_SCRATCH_GPR: ");
      VM.sysWrite(LAST_SCRATCH_GPR);
      VM.sysWrite("\tFIRST_SCRATCH_GPR: ");
      VM.sysWrite(LAST_SCRATCH_GPR);
      VM.sysWrite("\nSAVE_VOL_SIZE: ");
      VM.sysWrite(SAVE_VOL_SIZE);
      VM.sysWrite("\n");
    }

    // This computation will include some locations that are not technically
    // spill locations.  We do this because we currently do not record 
    // enough info in the VM_OptCompiledMethod object (the one that is available
    // at GC time) to distinguish the lower part of the spill.

    Address firstSpill = getFirstSpillLoc();
    Address lastSpill;
    int nonVolOffset = compiledMethod.getUnsignedNonVolatileOffset();
    if (nonVolOffset != 0) {
      if (compiledMethod.isSaveVolatile()) {
        lastSpill = framePtr.plus(nonVolOffset - BYTES_IN_ADDRESS - SAVE_VOL_SIZE);
      } else {
        lastSpill = framePtr.plus(nonVolOffset - BYTES_IN_ADDRESS);
      }
      // If the above computation is less than firstSpill, there are no spills
      if (lastSpill.LT(firstSpill)) {
        lastSpill = firstSpill;
      }
    } else {
      // If nonVolOffset = 0, there are no spills
      lastSpill = firstSpill;
    }
    return lastSpill;
  }

  static final int SPILL_DISTANCE_FROM_FP = 3 * BYTES_IN_ADDRESS;
  static final int SAVE_VOL_SIZE = BYTES_IN_ADDRESS *
    ((LAST_VOLATILE_GPR - FIRST_VOLATILE_GPR + 1) + 
     (LAST_SCRATCH_GPR - FIRST_SCRATCH_GPR + 1)); 

}
