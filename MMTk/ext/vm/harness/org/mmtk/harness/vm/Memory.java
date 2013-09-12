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
   *
   * TODO is this comment in the right place?
   */
   private static ImmortalSpace vmSpace = null;

   private static final Extent vmSpaceSize = Extent.fromIntZeroExtend(0x10000000);

   // Uncomment the below to exercise MMTk's 64-bit address space handling

//   public static final Address HEAP_START      = ArchitecturalWord.getModel().bitsInWord() == 32 ?
//       Address.fromIntZeroExtend(0x10000000) :
//       Address.fromLong(0x210000000L)  ;
//   public static final Address HEAP_END        = ArchitecturalWord.getModel().bitsInWord() == 32 ?
//       Address.fromIntZeroExtend(0xA0000000) :
//       Address.fromLong(0x2A0000000L);

   private static final Address heapStartAddress = Address.fromIntZeroExtend(0x10000000);
   private static final Address heapEndAddress = Address.fromIntZeroExtend(0x40000000);

  @Override
  @Interruptible
  public ImmortalSpace getVMSpace() {
    if (vmSpace == null) {
      vmSpace = new ImmortalSpace("vm", VMRequest.fixedSize((int)(getVmspacesize().toLong()/(1<<20))));
    }
    return vmSpace;
  }

  @Override
  public void globalPrepareVMSpace() {
    // Nothing in vmSpace
  }

  @Override
  public void collectorPrepareVMSpace() {
    // Nothing in vmSpace
  }

  @Override
  public void collectorReleaseVMSpace() {
    // Nothing in vmSpace
  }

  @Override
  public void globalReleaseVMSpace() {
    // Nothing in vmSpace
  }

  @Override
  public void setHeapRange(int id, Address start, Address end) {
    // TODO: More checking possible
  }

  @Override
  public int dzmmap(Address start, int size) {
    if (SimulatedMemory.map(start, size)) {
      return 0;
    }
    return -1;
  }

  @Override
  public boolean mprotect(Address start, int size) {
    return SimulatedMemory.protect(start, size);
  }

  @Override
  public boolean munprotect(Address start, int size) {
    return SimulatedMemory.unprotect(start, size);
  }

  @Override
  public void zero(boolean useNT, Address start, Extent len) {
    SimulatedMemory.zero(start, len);
  }

  @Override
  public void dumpMemory(Address start, int beforeBytes, int afterBytes) {
    SimulatedMemory.dumpMemory(start, beforeBytes, afterBytes);
  }

  @Override
  @Inline
  public void sync() {
    Scheduler.yield();
    // Nothing required
  }

  @Override
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
  @Override
  protected Address getHeapStartConstant() { return getHeapstartaddress(); }
  @Override
  protected Address getHeapEndConstant() { return getHeapendaddress(); }
  @Override
  protected Address getAvailableStartConstant() { return getHeapstartaddress().plus(getVmspacesize()); }
  @Override
  protected Address getAvailableEndConstant()  { return getHeapendaddress(); }
  @Override
  protected byte getLogBytesInAddressConstant() { return (byte) MemoryConstants.LOG_BYTES_IN_WORD; }
  @Override
  protected byte getLogBytesInWordConstant() { return (byte) MemoryConstants.LOG_BYTES_IN_WORD; }
  @Override
  protected byte getLogBytesInPageConstant() { return MemoryConstants.LOG_BYTES_IN_PAGE; }
  @Override
  protected byte getLogMinAlignmentConstant()  { return (byte) MemoryConstants.LOG_BYTES_IN_WORD; }
  @Override
  protected byte getMaxAlignmentShiftConstant() { return 1; }
  @Override
  protected int getMaxBytesPaddingConstant() { return MemoryConstants.BYTES_IN_WORD; }
  @Override
  protected int getAlignmentValueConstant() { return ObjectModel.ALIGNMENT_VALUE; }

  /**
   * @return the vmspacesize
   */
  public static Extent getVmspacesize() {
    return vmSpaceSize;
  }

  /**
   * @return the heapstartaddress
   */
  public static Address getHeapstartaddress() {
    return heapStartAddress;
  }

  /**
   * @return the heapendaddress
   */
  public static Address getHeapendaddress() {
    return heapEndAddress;
  }
}
