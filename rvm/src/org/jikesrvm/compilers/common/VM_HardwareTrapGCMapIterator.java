/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.common;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.memorymanagers.mminterface.VM_GCMapIterator;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.WordArray;

/**
 * Iterator for stack frames inserted by hardware trap handler.
 * Such frames are purely used as markers.
 * They contain no object references or JSR return addresses.
 */
@Uninterruptible
public final class VM_HardwareTrapGCMapIterator extends VM_GCMapIterator implements VM_SizeConstants {

  public VM_HardwareTrapGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations;
  }

  public void setupIterator(VM_CompiledMethod compiledMethod, Offset instructionOffset, Address framePtr) {
    this.framePtr = framePtr;
  }

  public Address getNextReferenceAddress() {
    // update register locations, noting that the trap handler represented by this stackframe
    // saved all registers into the thread's "hardwareExceptionRegisters" object
    //
    Address registerLocation = VM_Magic.objectAsAddress(thread.getHardwareExceptionRegisters().gprs);
    for (int i = 0; i < ArchitectureSpecific.VM_ArchConstants.NUM_GPRS; ++i) {
      registerLocations.set(i, registerLocation.toWord());
      registerLocation = registerLocation.plus(BYTES_IN_ADDRESS);
    }
    return Address.zero();
  }

  public Address getNextReturnAddressAddress() {
    return Address.zero();
  }

  public void reset() {}

  public void cleanupPointers() {}

  public int getType() {
    return VM_CompiledMethod.TRAP;
  }
}
