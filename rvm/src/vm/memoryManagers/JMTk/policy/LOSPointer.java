/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_AllocatorHeader;

import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_ProcessorLock;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;

/**
 *  A mark-sweep area to hold "large" objects (typically at least 4K).
 *  The large space code is obtained by factoring out the code in various
 *  collectors.
 *
 *  @author Perry Cheng
 */
public class LOSPointer implements Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  private LOSVMResource los;

  LOSPointer(LOSVMResource losvm, MemoryResource mr) {
    los = losvm;
  }


  /**
   * Allocate space for a new object
   *
   * @param isScalar Is the object to be allocated a scalar (or array)?
   * @param bytes The number of bytes allocated
   * @return The address of the first byte of the allocated region
   */
  public VM_Address alloc(boolean isScalar, EXTENT bytes) throws VM_PragmaInline {
    VM_Address result;
    for (int count = 0; ; count++) {
      result = los.alloc(isScalar, bytes); // zeroed by LOSVMResource
      if (!result.isZero()) break;
      VM_Interface.getPlan().poll(true);
      if (count > 2) VM.sysFail("Out of Memory in LOSPointer.alloc");
    }
    if (Plan.verbose > 3) {
      VM.sysWrite("LOSPointer.alloc allocated ", result);
      VM.sysWriteln("   request = ", bytes);
    }
    return result;
  }

  /**
   * Hook to allow heap to perform post-allocation processing of the object.
   * For example, setting the GC state bits in the object header.
   */
  public void postAlloc(Object newObj) throws VM_PragmaUninterruptible { 
    if (VM_Interface.NEEDS_WRITE_BARRIER) {
      VM_ObjectModel.initializeAvailableByte(newObj); 
      Header.setBarrierBit(newObj);
    } 
  }

}
