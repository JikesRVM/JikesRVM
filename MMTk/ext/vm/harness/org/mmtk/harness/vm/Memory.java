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
package org.mmtk.harness.vm;

import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.utility.heap.VMRequest;

import org.vmmagic.unboxed.*;
import org.vmmagic.unboxed.harness.MemoryConstants;
import org.vmmagic.unboxed.harness.SimulatedMemory;
import org.vmmagic.pragma.*;

@Uninterruptible
public class Memory extends org.mmtk.vm.Memory {

  /**
   * Allows for the VM to reserve space between HEAP_START()
   * and AVAILABLE_START() for its own purposes.  MMTk should
   * expect to encounter objects in this range, but may not
   * allocate in this range.
   *
   * MMTk expects the virtual address space between AVAILABLE_START()
   * and AVAILABLE_END() to be contiguous and unmapped.
   * Allows for the VM to reserve space between HEAP_END()
   * and AVAILABLE_END() for its own purposes.  MMTk should
   * expect to encounter objects in this range, but may not
   * allocate in this range.
   *
   * MMTk expects the virtual address space between AVAILABLE_START()
   * and AVAILABLE_END() to be contiguous and unmapped.
   *
   * @return The high bound of the memory that MMTk can allocate.
   */
   private static ImmortalSpace vmSpace = null;
   private static Extent VMSPACE_SIZE = Extent.fromIntZeroExtend(0x10000000);

   // Uncomment the below to exercise MMTk's 64-bit address space handling

//   public static final Address HEAP_START      = ArchitecturalWord.getModel().bitsInWord() == 32 ?
//       Address.fromIntZeroExtend(0x10000000) :
//       Address.fromLong(0x210000000L)  ;
//   public static final Address HEAP_END        = ArchitecturalWord.getModel().bitsInWord() == 32 ?
//       Address.fromIntZeroExtend(0xA0000000) :
//       Address.fromLong(0x2A0000000L);

   public static final Address HEAP_START = Address.fromIntZeroExtend(0x10000000);
   public static final Address HEAP_END = Address.fromIntZeroExtend(0xA0000000);

  /**
   * Return the space associated with/reserved for the VM.  In the
   * case of Jikes RVM this is the boot image space.<p>
   *
   * @return The space managed by the virtual machine.
   */
  @Interruptible
  public ImmortalSpace getVMSpace() {
    if (vmSpace == null) {
      vmSpace = new ImmortalSpace("vm", 0, VMRequest.create(VMSPACE_SIZE, false));
    }
    return vmSpace;
  }

  /** Global preparation for a collection. */
  public void globalPrepareVMSpace() {
    // Nothing in vmSpace
  }

  /** Per-collector preparation for a collection. */
  public void collectorPrepareVMSpace() {
    // Nothing in vmSpace
  }

  /** Per-collector post-collection work. */
  public void collectorReleaseVMSpace() {
    // Nothing in vmSpace
  }

  /** Global post-collection work. */
  public void globalReleaseVMSpace() {
    // Nothing in vmSpace
  }

  /**
   * Sets the range of addresses associated with a heap.
   *
   * @param id the heap identifier
   * @param start the address of the start of the heap
   * @param end the address of the end of the heap
   */
  public void setHeapRange(int id, Address start, Address end) {
    // TODO: More checking possible
  }

  /**
   * Demand zero mmaps an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return 0 if successful, otherwise the system errno
   */
  public int dzmmap(Address start, int size) {
    if (SimulatedMemory.map(start, size)) {
      return 0;
    }
    return -1;
  }

  /**
   * Protects access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public boolean mprotect(Address start, int size) {
    return SimulatedMemory.protect(start, size);
  }

  /**
   * Allows access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public boolean munprotect(Address start, int size) {
    return SimulatedMemory.unprotect(start, size);
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param len Length in bytes of range to zero
   * Returned: nothing
   */
  public void zero(Address start, Extent len) {
    SimulatedMemory.zero(start, len);
  }

  /**
   * Zero a range of pages of memory.
   * @param start Start of address range (must be a page address)
   * @param len Length in bytes of range (must be multiple of page size)
   */
  public void zeroPages(Address start, int len) {
    SimulatedMemory.zeroPages(start, len);
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
  public void dumpMemory(Address start, int beforeBytes, int afterBytes) {
    SimulatedMemory.dumpMemory(start, beforeBytes, afterBytes);
  }

  /**
   * Wait for preceeding cache flush/invalidate instructions to complete
   * on all processors.  Ensures that all memory writes before this
   * point are visible to all processors.
   */
  @Inline
  public void sync() {
    Scheduler.yield();
    // Nothing required
  }

  /**
   * Wait for all preceeding instructions to complete and discard any
   * prefetched instructions on this processor.  Also prevents the
   * compiler from performing code motion across this point.
   */
  @Inline
  public void isync() {
    Scheduler.yield();
    // Nothing required
  }

  /*
   * NOTE: The following methods must be implemented by subclasses of this
   * class, but are internal to the VM<->MM interface glue, so are never
   * called by MMTk users.
   */
  /** @return The lowest address in the virtual address space known to MMTk */
  protected Address getHeapStartConstant() { return HEAP_START; }
  /** @return The highest address in the virtual address space known to MMTk */
  protected Address getHeapEndConstant() { return HEAP_END; }
  /** @return The lowest address in the contiguous address space available to MMTk  */
  protected Address getAvailableStartConstant() { return HEAP_START.plus(VMSPACE_SIZE); }
  /** @return The highest address in the contiguous address space available to MMTk */
  protected Address getAvailableEndConstant()  { return HEAP_END; }
  /** @return The log base two of the size of an address */
  protected byte getLogBytesInAddressConstant() { return (byte) MemoryConstants.LOG_BYTES_IN_WORD; }
  /** @return The log base two of the size of a word */
  protected byte getLogBytesInWordConstant() { return (byte) MemoryConstants.LOG_BYTES_IN_WORD; }
  /** @return The log base two of the size of an OS page */
  protected byte getLogBytesInPageConstant() { return MemoryConstants.LOG_BYTES_IN_PAGE; }
  /** @return The log base two of the minimum allocation alignment */
  protected byte getLogMinAlignmentConstant()  { return (byte) MemoryConstants.LOG_BYTES_IN_WORD; }
  /** @return The log base two of (MAX_ALIGNMENT/MIN_ALIGNMENT) */
  protected byte getMaxAlignmentShiftConstant() { return 1; }
  /** @return The maximum number of bytes of padding to prepend to an object */
  protected int getMaxBytesPaddingConstant() { return MemoryConstants.BYTES_IN_WORD; }
  /** @return The value to store in alignment holes */
  protected int getAlignmentValueConstant() { return ObjectModel.ALIGNMENT_VALUE; }
}
