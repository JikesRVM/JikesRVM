/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;

/**
 * Iterator for stack frames inserted by hardware trap handler.
 * Such frames are purely used as markers.
 * They contain no object references or JSR return addresses.
 *
 * @author Derek Lieber
 * @date 02 Jun 1999 
 */
public final class VM_HardwareTrapGCMapIterator extends VM_GCMapIterator implements VM_Uninterruptible, VM_SizeConstants {

  public VM_HardwareTrapGCMapIterator(VM_WordArray registerLocations) {
    this.registerLocations = registerLocations;
  }

  public void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, 
                     VM_Address framePtr) {
    this.framePtr = framePtr;
  }
  
  public VM_Address getNextReferenceAddress() {
    // update register locations, noting that the trap handler represented by this stackframe
    // saved all registers into the thread's "hardwareExceptionRegisters" object
    //
    VM_Address registerLocation = VM_Magic.objectAsAddress(thread.hardwareExceptionRegisters.gprs);
    for (int i = 0; i < VM_Constants.NUM_GPRS; ++i) {
      registerLocations.set(i, registerLocation);
      registerLocation = registerLocation.add(BYTES_IN_ADDRESS);
    }
    return VM_Address.zero();
  }

  public VM_Address getNextReturnAddressAddress() { 
    return VM_Address.zero();
  }

  public void reset() {}
  
  public void cleanupPointers() {} 
  
  public int getType() {
    return VM_CompiledMethod.TRAP;
  }
}
