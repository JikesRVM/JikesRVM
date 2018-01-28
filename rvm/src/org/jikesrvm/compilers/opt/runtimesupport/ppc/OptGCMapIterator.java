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
package org.jikesrvm.compilers.opt.runtimesupport.ppc;

import static org.jikesrvm.compilers.opt.runtimesupport.ppc.OptGCMapIteratorConstants.LAST_GCMAP_REG;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_SCRATCH_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_SCRATCH_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.runtimesupport.OptGenericGCMapIterator;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;

/**
 * An instance of this class provides iteration across the references
 * represented by a frame built by the OPT compiler.
 * <p>
 * The architecture-specific version of the GC Map iterator.  It inherits
 * its architecture-independent code from OptGenericGCMapIterator.
 * This version is for the PowerPC.
 */
@Uninterruptible
public final class OptGCMapIterator extends OptGenericGCMapIterator {

  private static final boolean DEBUG = false;

  public OptGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  @Override
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
          registerLocations.set(i, location);
          location = location.plus(BYTES_IN_ADDRESS);
        }
      }

      // update volatiles if needed
      if (compiledMethod.isSaveVolatile()) {
        // move to the beginning of the save area for volatiles
        Address location = nonVolArea.minus(SAVE_VOL_SIZE);

        // Walk the saved volatiles, updating registerLocations array
        for (int i = FIRST_VOLATILE_GPR.value(); i <= LAST_VOLATILE_GPR.value(); i++) {
          registerLocations.set(i, location);
          location = location.plus(BYTES_IN_ADDRESS);
        }

        // Walk the saved scratch, updating registerLocations array
        for (int i = FIRST_SCRATCH_GPR.value(); i <= LAST_SCRATCH_GPR.value(); i++) {
          registerLocations.set(i, location);
          location = location.plus(BYTES_IN_ADDRESS);
        }
      }
    }
  }

  @Override
  public Address getStackLocation(Address framePtr, int offset) {
    return framePtr.plus(offset);
  }

  /**
   *  Get address of the first spill location for the frame ptr.
   *  @return the first spill location
   */
  @Override
  public Address getFirstSpillLoc() {
    return framePtr.plus(SPILL_DISTANCE_FROM_FP);
  }

  /**
   *  Get address of the last spill location for the frame ptr.
   *
   *  @return the last spill location, if no spills occur, we return the
   *          first spill location
   */
  @Override
  public Address getLastSpillLoc() {
    if (DEBUG) {
      VM.sysWriteln();
      VM.sysWrite(" unsigendNVOffset: ");
      VM.sysWrite(compiledMethod.getUnsignedNonVolatileOffset());
      VM.sysWrite("\t isSaveVolatile: ");
      if (compiledMethod.isSaveVolatile()) {
        VM.sysWriteln("true");
      } else {
        VM.sysWriteln("false");
      }
      VM.sysWrite("LAST_VOLATILE_GPR: ");
      VM.sysWrite(LAST_VOLATILE_GPR.toString());
      VM.sysWrite("\tFIRST_VOLATILE_GPR: ");
      VM.sysWriteln(LAST_VOLATILE_GPR.toString());
      VM.sysWrite("LAST_SCRATCH_GPR: ");
      VM.sysWrite(LAST_SCRATCH_GPR.toString());
      VM.sysWrite("\tFIRST_SCRATCH_GPR: ");
      VM.sysWriteln(LAST_SCRATCH_GPR.toString());
      VM.sysWrite("SAVE_VOL_SIZE: ");
      VM.sysWrite(SAVE_VOL_SIZE);
      VM.sysWriteln();
    }

    // This computation will include some locations that are not technically
    // spill locations.  We do this because we currently do not record
    // enough info in the OptCompiledMethod object (the one that is available
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
  static final int SAVE_VOL_SIZE =
    BYTES_IN_ADDRESS * ((LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1) + (LAST_SCRATCH_GPR.value() - FIRST_SCRATCH_GPR.value() + 1));

}
