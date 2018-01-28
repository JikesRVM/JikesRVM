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

import static org.vmmagic.unboxed.harness.MemoryConstants.BYTES_IN_WORD;
import static org.vmmagic.unboxed.harness.MemoryConstants.LOG_BYTES_IN_PAGE;
import static org.vmmagic.unboxed.harness.MemoryConstants.LOG_BYTES_IN_WORD;
import static org.vmmagic.unboxed.harness.MemoryConstants.LOG_BYTES_IN_LONG;
import static org.vmmagic.unboxed.harness.MemoryConstants.LOG_BYTES_IN_INT;

import org.mmtk.harness.Harness;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.heap.layout.HeapParameters;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.SimulatedMemory;

@Uninterruptible
public class Memory extends org.mmtk.vm.Memory {

  /**
   * Select the memory model.  In 32-bit mode we only have one choice, but
   * in 64-bit mode, we look at the value of the heapLayout option.
   */
  private static boolean heapLayout32BitInternal() {
    return ArchitecturalWord.getModel().bitsInWord() == 32 ||
      Harness.heapLayout.getValue() == 32;
  }

  /**
   * In a real virtual machine, this space would occupy all the addresses of
   * the virtual machine's code and data.  In the harness, this is where the
   * stacks live.
   */
  private static ImmortalSpace vmSpace = null;

  /**
   * Size of the VM space.
   */
  private static final Extent vmSpaceSize = Extent.fromIntZeroExtend(0x10000000);

  /**
   * Lowest address in the MMTk heap.
   * <p>
   * Heap start address is essentially arbitrary in 32-bit mode, but with the 64-bit memory
   * layout, MMTk requires a specific layout.
   */
  public static final Address heapStartAddress      = heapLayout32BitInternal() ?
      Address.fromIntZeroExtend(0x10000000) :
      Address.fromLong(1L << HeapParameters.LOG_SPACE_SIZE_64)  ;

  /**
   * Highest address in the MMTk heap.
   * <p>
   * Heap end address is essentially arbitrary in 32-bit mode, but with the 64-bit memory
   * layout, MMTk requires a specific layout.
   */
  public static final Address heapEndAddress        = heapLayout32BitInternal() ?
      Address.fromIntZeroExtend(0xA0000000) :
      Address.fromLong((1L << (HeapParameters.LOG_SPACE_SIZE_64 + HeapParameters.LOG_MAX_SPACES)) - 1);

  /**
   * This method is how we pass the VM space to MMTk.
   *
   * @return The ImmortalSpace occupied by the VM.
   */
  @Override
  @Interruptible
  public ImmortalSpace getVMSpace() {
    if (vmSpace == null) {
      vmSpace = new ImmortalSpace("vm", VMRequest.fixedSize((int)(getVmspacesize().toLong() / (1 << 20))));
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

  /** {@inheritDoc} */
  @Override
  public boolean mprotect(Address start, int size) {
    return SimulatedMemory.protect(start, size);
  }

  /** {@inheritDoc} */
  @Override
  public boolean munprotect(Address start, int size) {
    return SimulatedMemory.unprotect(start, size);
  }

  /** {@inheritDoc} */
  @Override
  public void zero(boolean useNT, Address start, Extent len) {
    SimulatedMemory.zero(start, len);
  }

  /** {@inheritDoc} */
  @Override
  public void dumpMemory(Address start, int beforeBytes, int afterBytes) {
    SimulatedMemory.dumpMemory(start, beforeBytes, afterBytes);
  }

  /** {@inheritDoc} */
  @Override
  @Inline
  public void fence() {
    Scheduler.yield();
    // Nothing required
  }

  /** {@inheritDoc} */
  @Override
  @Inline
  public void combinedLoadBarriers() {
    Scheduler.yield();
    // Nothing required
  }

  /*
   * NOTE: The following methods must be implemented by subclasses of this
   * class, but are internal to the VM<->MM interface glue, so are never
   * called by MMTk users.
   */
  @Override
  protected boolean getHeapLayout32Bit() {
    return heapLayout32BitInternal();
  }

  @Override
  protected Address getHeapStartConstant() {
    return getHeapstartaddress();
  }
  @Override
  protected Address getHeapEndConstant() {
    return getHeapendaddress();
  }
  @Override
  protected Address getAvailableStartConstant() {
    return getHeapstartaddress().plus(getVmspacesize());
  }
  @Override
  protected Address getAvailableEndConstant()  {
    return getHeapendaddress();
  }
  @Override
  protected byte getLogBytesInAddressConstant() {
    return (byte) LOG_BYTES_IN_WORD;
  }
  @Override
  protected byte getLogBytesInWordConstant() {
    return (byte) LOG_BYTES_IN_WORD;
  }
  @Override
  protected byte getLogBytesInPageConstant() {
    return LOG_BYTES_IN_PAGE;
  }
  @Override
  protected byte getLogMinAlignmentConstant()  {
    return (byte) LOG_BYTES_IN_INT;
  }
  @Override
  protected byte getMaxAlignmentShiftConstant() {
    return 1 + LOG_BYTES_IN_LONG - LOG_BYTES_IN_INT;
  }
  @Override
  protected int getMaxBytesPaddingConstant() {
    return BYTES_IN_WORD;
  }
  @Override
  protected int getAlignmentValueConstant() {
    return ObjectModel.ALIGNMENT_VALUE;
  }

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
