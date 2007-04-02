/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.common;

import org.jikesrvm.memorymanagers.mminterface.VM_GCMapIterator;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.ArchitectureSpecific;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Iterator for stack frames inserted by hardware trap handler.
 * Such frames are purely used as markers.
 * They contain no object references or JSR return addresses.
 *
 * @author Derek Lieber
 * @date 02 Jun 1999 
 */
@Uninterruptible public final class VM_HardwareTrapGCMapIterator extends VM_GCMapIterator implements VM_SizeConstants {

  public VM_HardwareTrapGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations;
  }

  public void setupIterator(VM_CompiledMethod compiledMethod, Offset instructionOffset, 
                     Address framePtr) {
    this.framePtr = framePtr;
  }
  
  public Address getNextReferenceAddress() {
    // update register locations, noting that the trap handler represented by this stackframe
    // saved all registers into the thread's "hardwareExceptionRegisters" object
    //
    Address registerLocation = VM_Magic.objectAsAddress(thread.hardwareExceptionRegisters.gprs);
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
