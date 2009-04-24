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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.Plan;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.utility.Constants;
import org.mmtk.utility.heap.VMRequest;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.HeapLayoutConstants;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.SizeConstants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible public class Memory extends org.mmtk.vm.Memory
  implements Constants, HeapLayoutConstants, SizeConstants {

  protected final Address getHeapStartConstant() { return BOOT_IMAGE_DATA_START; }
  protected final Address getHeapEndConstant() { return MAXIMUM_MAPPABLE; }
  protected final Address getAvailableStartConstant() { return BOOT_IMAGE_CODE_END; }
  protected final Address getAvailableEndConstant() { return MAXIMUM_MAPPABLE; }
  protected final byte getLogBytesInAddressConstant() { return SizeConstants.LOG_BYTES_IN_ADDRESS; }
  protected final byte getLogBytesInWordConstant() { return SizeConstants.LOG_BYTES_IN_WORD; }
  protected final byte getLogBytesInPageConstant() { return SizeConstants.LOG_BYTES_IN_PAGE; }
  protected final byte getLogMinAlignmentConstant() { return JavaHeader.LOG_MIN_ALIGNMENT;}
  protected final int getMaxBytesPaddingConstant() { return SizeConstants.BYTES_IN_DOUBLE; }
  protected final int getAlignmentValueConstant() { return JavaHeader.ALIGNMENT_VALUE;}

  /* On Intel we align code to 16 bytes as recommended in the optimization manual */
  protected final byte getMaxAlignmentShiftConstant() { return (VM.BuildForIA32 ? 1 : 0) + SizeConstants.LOG_BYTES_IN_LONG - SizeConstants.LOG_BYTES_IN_INT; }

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
    if (bootSpace == null) {
      bootSpace = new ImmortalSpace("boot", Plan.DEFAULT_POLL_FREQUENCY, VMRequest.create(BOOT_SEGMENT_MB));
    }
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
    BootRecord.the_boot_record.setHeapRange(id, start, end);
  }

 /**
   * Demand zero mmaps an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return 0 if successful, otherwise the system errno
   */
  public final int dzmmap(Address start, int size) {
    Address result = org.jikesrvm.runtime.Memory.dzmmap(start, Extent.fromIntZeroExtend(size));
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
    return org.jikesrvm.runtime.Memory.mprotect(start, Extent.fromIntZeroExtend(size),
                                                   org.jikesrvm.runtime.Memory.PROT_NONE);
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
    return org.jikesrvm.runtime.Memory.mprotect(start, Extent.fromIntZeroExtend(size),
                                                   org.jikesrvm.runtime.Memory.PROT_READ |
                                                   org.jikesrvm.runtime.Memory.PROT_WRITE |
                                                   org.jikesrvm.runtime.Memory.PROT_EXEC);
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param len Length in bytes of range to zero
   * Returned: nothing
   */
  public final void zero(Address start, Extent len) {
    org.jikesrvm.runtime.Memory.zero(start,len);
  }

  /**
   * Zero a range of pages of memory.
   * @param start Start of address range (must be a page address)
   * @param len Length in bytes of range (must be multiple of page size)
   */
  public final void zeroPages(Address start, int len) {
      /* AJG: Add assertions to check conditions documented above. */
    org.jikesrvm.runtime.Memory.zeroPages(start,len);
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
    org.jikesrvm.runtime.Memory.dumpMemory(start,beforeBytes,afterBytes);
  }

  /*
   * Utilities from the VM class
   */

  @Inline
  public final void sync() {
    Magic.sync();
  }

  @Inline
  public final void isync() {
    Magic.isync();
  }
}
