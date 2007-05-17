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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.Plan;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.utility.Constants;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_BootRecord;
import org.jikesrvm.VM_HeapLayoutConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.jikesrvm.objectmodel.VM_JavaHeader;
import org.jikesrvm.VM_SizeConstants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible public class Memory extends org.mmtk.vm.Memory
  implements Constants, VM_HeapLayoutConstants, VM_SizeConstants {

  protected final Address getHeapStartConstant() { return BOOT_IMAGE_DATA_START; }
  protected final Address getHeapEndConstant() { return MAXIMUM_MAPPABLE; }
  protected final Address getAvailableStartConstant() { return BOOT_IMAGE_CODE_END; }
  protected final Address getAvailableEndConstant() { return MAXIMUM_MAPPABLE; }
  protected final byte getLogBytesInAddressConstant() { return VM_SizeConstants.LOG_BYTES_IN_ADDRESS; }
  protected final byte getLogBytesInWordConstant() { return VM_SizeConstants.LOG_BYTES_IN_WORD; }
  protected final byte getLogBytesInPageConstant() { return 12; }
  protected final byte getLogMinAlignmentConstant() { return VM_JavaHeader.LOG_MIN_ALIGNMENT;}
  protected final byte getMaxAlignmentShiftConstant() { return VM_SizeConstants.LOG_BYTES_IN_LONG - VM_SizeConstants.LOG_BYTES_IN_INT; }
  protected final int getMaxBytesPaddingConstant() { return VM_SizeConstants.BYTES_IN_DOUBLE; } 
  protected final int getAlignmentValueConstant() { return VM_JavaHeader.ALIGNMENT_VALUE;}
  
  private static ImmortalSpace bootSpace;
  
  /* FIXME the following was established via trial and error :-( */
  //  private static int BOOT_SEGMENT_MB = 4+(BOOT_IMAGE_SIZE.toInt()>>LOG_BYTES_IN_MBYTE);
  private static int BOOT_SEGMENT_MB = (0x10000000>>LOG_BYTES_IN_MBYTE);

  /**
   * Return the space associated with/reserved for the VM.  In the
   * case of Jikes RVM this is the boot image space.<p>
   *
   * The boot image space must be mapped at the start of available
   * virtual memory, hence we use the constructor that requests the
   * lowest address in the address space.  The address space awarded
   * to this space depends on the order in which the request is made.
   * If this request is not the first request for virtual memory then
   * the Space allocator will die with an error stating that the
   * request could not be satisfied.  The remedy is to ensure it is
   * initialized first.
   *
   * @return The space managed by the virtual machine.  In this case,
   * the boot image space is returned.
   */
  @Interruptible
  public final ImmortalSpace getVMSpace() { 
    if (bootSpace == null)
      bootSpace = new ImmortalSpace("boot", Plan.DEFAULT_POLL_FREQUENCY, 
                                    BOOT_SEGMENT_MB, false);
    return bootSpace;
  }

  /** Global preparation for a collection. */
  public final void globalPrepareVMSpace() { bootSpace.prepare(); }

  /** Per-collector preparation for a collection. */
  public final void collectorPrepareVMSpace() {}

  /** Per-collector post-collection work. */
  public final void collectorReleaseVMSpace() {}

  /** Global post-collection work. */
  public final void globalReleaseVMSpace() { bootSpace.release(); }

  /**
   * Sets the range of addresses associated with a heap.
   *
   * @param id the heap identifier
   * @param start the address of the start of the heap
   * @param end the address of the end of the heap
   */
  public final void setHeapRange(int id, Address start, Address end) {
    VM_BootRecord.the_boot_record.setHeapRange(id, start, end);
  }

 /**
   * Demand zero mmaps an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return 0 if successful, otherwise the system errno
   */
  public final int dzmmap(Address start, int size) {
    Address result = VM_Memory.dzmmap(start, Extent.fromIntZeroExtend(size));
    if (result.EQ(start)) return 0;
    if (result.GT(Address.fromIntZeroExtend(127))) {
      VM.sysWrite("demand zero mmap with MAP_FIXED on ", start);
      VM.sysWriteln(" returned some other address", result);
      VM.sysFail("mmap with MAP_FIXED has unexpected behavior");
    }
    return result.toInt();
  }
  
  /**
   * Protects access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public final boolean mprotect(Address start, int size) {
    return VM_Memory.mprotect(start, Extent.fromIntZeroExtend(size),
                              VM_Memory.PROT_NONE);
  }

  /**
   * Allows access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public final boolean munprotect(Address start, int size) {
    return VM_Memory.mprotect(start, Extent.fromIntZeroExtend(size),
                              VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC);
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param len Length in bytes of range to zero
   * Returned: nothing
   */
  public final void zero(Address start, Extent len) {
    VM_Memory.zero(start,len);
  }

  /**
   * Zero a range of pages of memory.
   * @param start Start of address range (must be a page address)
   * @param len Length in bytes of range (must be multiple of page size)
   */
  public final void zeroPages(Address start, int len) {
      /* AJG: Add assertions to check conditions documented above. */
    VM_Memory.zeroPages(start,len);
  }

  /**
   * Logs the contents of an address and the surrounding memory to the
   * error output.
   *
   * @param start the address of the memory to be dumped
   * @param beforeBytes the number of bytes before the address to be
   * included
   * @param afterBytes the number of bytes after the address to be
   * included
   */
  public final void dumpMemory(Address start, int beforeBytes,
                                int afterBytes) {
    VM_Memory.dumpMemory(start,beforeBytes,afterBytes);
  }

  /*
   * Utilities from the VM class
   */

  @Inline
  public final void sync() { 
    VM_Magic.sync();
  }

  @Inline
  public final void isync() { 
    VM_Magic.isync();
  }
}
