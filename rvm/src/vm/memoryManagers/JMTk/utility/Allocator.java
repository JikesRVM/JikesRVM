/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;


import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This abstract base class provides the basis for processor-local
 * allocation.  The key functionality provided is the retry mechanism
 * that is necessary to correctly handle the fact that a "slow-path"
 * allocation can cause a GC which violate the uninterruptability assumption.
 * This results in the thread being moved to a different processor so that
 * the allocator object it is using is not actually the one for the processor
 * it is running on.
 *
 * Failing to handle this properly will lead to very hard to trace bugs
 * where the allocation that caused a GC or allocations immediately following
 * GC are run incorrectly.
 *
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */

abstract class Allocator implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /**
   * Maximum number of retries on consecutive allocation failure.
   *
   */
  private static final int MAX_RETRY = 5;

  /**
   * Constructor
   *
   */
  Allocator () {
  }

  abstract protected VM_Address allocSlowOnce (boolean isScalar, int bytes);

  public VM_Address allocSlow (boolean isScalar, int bytes) throws VM_PragmaNoInline { 
    Allocator current = this;
    for (int i=0; i<MAX_RETRY; i++) {
      VM_Address result = current.allocSlowOnce(isScalar, bytes);
      if (!result.isZero())
	return result;
      current = BasePlan.getOwnAllocator(current);
    }
    VM_Interface.sysFail("Out of Memory after multiple retries");
    return VM_Address.zero();
  }

}
