/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_GCMapIterator;

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
public final class VM_HardwareTrapGCMapIterator extends VM_GCMapIterator implements Uninterruptible, VM_SizeConstants {

  public VM_HardwareTrapGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations;
  }

  public void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, 
                     Address framePtr) {
    this.framePtr = framePtr;
  }
  
  public Address getNextReferenceAddress() {
    // update register locations, noting that the trap handler represented by this stackframe
    // saved all registers into the thread's "hardwareExceptionRegisters" object
    //
    Address registerLocation = VM_Magic.objectAsAddress(thread.hardwareExceptionRegisters.gprs);
    for (int i = 0; i < VM_Constants.NUM_GPRS; ++i) {
      registerLocations.set(i, registerLocation);
      registerLocation = registerLocation.add(BYTES_IN_ADDRESS);
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
