/*
 * (C) Copyright IBM Corp. 2001
 */

package org.mmtk.utility.heap;

import org.mmtk.vm.Constants;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class restricts MonotoneVMResource by preventing release of immortal memory.
 * There is functionality for boot-image support.
 *
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
public class ImmortalVMResource extends MonotoneVMResource implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */
  /**
   * Constructor
   */
  public ImmortalVMResource(byte space_, String vmName, MemoryResource mr, Address vmStart, Extent bytes) {
    super(space_, vmName, mr, vmStart, bytes, (byte) (VMResource.IN_VM | VMResource.IMMORTAL));
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(cursor.GE(vmStart) && cursor.LE(sentinel));
    sentinel = start.add(bytes);
  }

  public final Address acquire(int pageRequest) {
    Address result = super.acquire(pageRequest);
    acquireHelp(start, pageRequest);
    return result;
  }

  public final void release() {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
  }
  
}
