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
package org.jikesrvm.compilers.opt.runtimesupport.ia32;

import static org.jikesrvm.ia32.RegisterConstants.NONVOLATILE_GPRS;
import static org.jikesrvm.ia32.RegisterConstants.NUM_NONVOLATILE_GPRS;
import static org.jikesrvm.ia32.RegisterConstants.NUM_VOLATILE_GPRS;
import static org.jikesrvm.ia32.RegisterConstants.VOLATILE_GPRS;
import static org.jikesrvm.ia32.StackframeLayoutConstants.OPT_SAVE_VOLATILE_TOTAL_SIZE;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_BODY_OFFSET;
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
 * This version is for IA32.
 */
@Uninterruptible
public final class OptGCMapIterator extends OptGenericGCMapIterator {

  private static final boolean DEBUG = false;

  public OptGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  /**
   * If any non-volatile GPRs were saved by the method being processed
   * then update the registerLocations array with the locations where the
   * registers were saved.  Also, check for special methods that also
   * save the volatile GPRs.
   */
  @Override
  protected void updateLocateRegisters() {

    //           HIGH MEMORY
    //
    //       +---------------+                                           |
    //  FP-> |   saved FP    |  <-- this frame's caller's frame          |
    //       +---------------+                                           |
    //       |    cmid       |  <-- this frame's compiledmethod id       |
    //       +---------------+                                           |
    //       |               |                                           |
    //       |  Spill Area   |  <-- spills and other method-specific     |
    //       |     ...       |      compiler-managed storage             |
    //       +---------------+                                           |
    //       |   Saved FP    |     only SaveVolatile Frames              |
    //       |    State      |                                           |
    //       +---------------+                                           |
    //       |  VolGPR[0]    |
    //       |     ...       |     only SaveVolatile Frames
    //       |  VolGPR[n]    |
    //       +---------------+
    //       |  NVolGPR[k]   |  <-- cm.getUnsignedNonVolatileOffset()
    //       |     ...       |   k == cm.getFirstNonVolatileGPR()
    //       |  NVolGPR[n]   |
    //       +---------------+
    //
    //           LOW MEMORY

    int frameOffset = compiledMethod.getUnsignedNonVolatileOffset();
    if (frameOffset >= 0) {
      // get to the non vol area
      Address nonVolArea = framePtr.minus(frameOffset);

      // update non-volatiles
      int first = compiledMethod.getFirstNonVolatileGPR();
      if (first >= 0) {
        // move to the beginning of the nonVol area
        Address location = nonVolArea;

        for (int i = first; i < NUM_NONVOLATILE_GPRS; i++) {
          // determine what register index corresponds to this location
          int registerIndex = NONVOLATILE_GPRS[i].value();
          registerLocations.set(registerIndex, location);
          if (DEBUG) {
            VM.sysWrite("UpdateRegisterLocations: Register ");
            VM.sysWrite(registerIndex);
            VM.sysWrite(" to Location ");
            VM.sysWrite(location);
            VM.sysWriteln();
          }
          location = location.minus(BYTES_IN_ADDRESS);
        }
      }

      // update volatiles if needed
      if (compiledMethod.isSaveVolatile()) {
        // move to the beginning of the nonVol area
        Address location = nonVolArea.plus(BYTES_IN_ADDRESS * NUM_VOLATILE_GPRS);

        for (int i = 0; i < NUM_VOLATILE_GPRS; i++) {
          // determine what register index corresponds to this location
          int registerIndex = VOLATILE_GPRS[i].value();
          registerLocations.set(registerIndex, location);
          if (DEBUG) {
            VM.sysWrite("UpdateRegisterLocations: Register ");
            VM.sysWrite(registerIndex);
            VM.sysWrite(" to Location ");
            VM.sysWrite(location);
            VM.sysWriteln();
          }
          location = location.minus(BYTES_IN_ADDRESS);
        }
      }
    }
  }

  @Override
  public Address getStackLocation(Address framePtr, int offset) {
    return framePtr.minus(offset);
  }

  /**
   *  Get address of the first spill location for the given frame ptr
   *  @return the first spill location
   */
  @Override
  public Address getFirstSpillLoc() {
    return framePtr.plus(STACKFRAME_BODY_OFFSET);
  }

  /**
   *  Get address of the last spill location for the given frame ptr
   *  @return the last spill location
   */
  @Override
  public Address getLastSpillLoc() {
    if (compiledMethod.isSaveVolatile()) {
      return framePtr.minus(compiledMethod.getUnsignedNonVolatileOffset() - BYTES_IN_ADDRESS - OPT_SAVE_VOLATILE_TOTAL_SIZE);
    } else {
      return framePtr.minus(compiledMethod.getUnsignedNonVolatileOffset() - BYTES_IN_ADDRESS);
    }
  }

}
