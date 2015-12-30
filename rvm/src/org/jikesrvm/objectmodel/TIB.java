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
package org.jikesrvm.objectmodel;

import static org.jikesrvm.objectmodel.TIBLayoutConstants.*;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BITS_IN_BYTE;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.ArchConstants;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.LazyCompilationTrampoline;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * This class represents an instance of a type information block, at runtime it
 * is an array with Object elements.
 * @see TIBLayoutConstants
 */
@Uninterruptible
@NonMoving
public final class TIB implements RuntimeTable<Object> {
  /**
   * @return the number of words required to hold the lazy method invoker trampoline.
   */
  public static int lazyMethodInvokerTrampolineWords() {
    int codeWords = VM.BuildForIA32 ? (VM.BuildFor32Addr ? 2 : 1) : (VM.BuildFor32Addr ? 3 : 2);
    if (VM.VerifyAssertions && VM.runningVM) {
      int codeBytes = LazyCompilationTrampoline.getInstructions().length() << ArchConstants.getLogInstructionWidth();
      VM._assert(codeWords == ((codeBytes + BYTES_IN_ADDRESS - 1) >>> LOG_BYTES_IN_ADDRESS));
    }
    return codeWords;
  }

  /** Alignment encoded data for this TIB - only used at build time */
  private int alignData;


  /**
   * Calculates the size of a TIB.
   *
   * @param numVirtualMethods the number of virtual methods in the TIB
   * @return the size of a TIB with the given number of virtual methods
   */
  @NoInline
  public static int computeSize(int numVirtualMethods) {
    return TIB_FIRST_VIRTUAL_METHOD_INDEX + numVirtualMethods + lazyMethodInvokerTrampolineWords();
  }

  /**
   * Calculate the virtual method offset for the given index.
   * @param virtualMethodIndex The index to calculate the offset for
   * @return The offset.
   */
  public static Offset getVirtualMethodOffset(int virtualMethodIndex) {
    return Offset.fromIntZeroExtend((TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex) << LOG_BYTES_IN_ADDRESS);
  }

  /**
   * Calculate the virtual method index for the given offset.
   * @param virtualMethodOffset The offset to calculate the index for
   * @return The index.
   */
  public static int getVirtualMethodIndex(Offset virtualMethodOffset) {
    return (virtualMethodOffset.toInt() >>> LOG_BYTES_IN_ADDRESS) - TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }

  /**
   * Calculate the virtual method index for the given raw slot index.
   *
   * @param slot The raw slot to find the virtual method index for.
   * @return The index.
   */
  public static int getVirtualMethodIndex(int slot) {
    if (VM.VerifyAssertions) VM._assert(slot > TIB_FIRST_VIRTUAL_METHOD_INDEX);
    return slot - TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }

  /**
   * The backing data used during boot image writing.
   */
  private final Object[] data;

  private TIB(int size) {
    this.data = new Object[size];
  }

  @Override
  public Object[] getBacking() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return data;
  }

  /**
   * Create a new TIB of the specified size.
   *
   * @param size The size of the TIB
   * @param alignData Alignment-encoded data for this TIB,
   *      AlignmentEncoding.ALIGN_CODE_NONE for no alignment encoding.
   * @return The created TIB instance.
   */
  @NoInline
  @Interruptible
  public static TIB allocate(int size, int alignData) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    TIB tib = new TIB(size);
    tib.setAlignData(alignData);
    return tib;
  }

  /**
   * Get a TIB entry.
   *
   * @param index The index of the entry to get
   * @return The value of that entry
   */
  @Override
  @Intrinsic
  public Object get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return data[index];
  }

  /**
   * Set a TIB entry.
   *
   * @param index The index of the entry to set
   * @param value The value to set the entry to.
   */
  @Override
  @Intrinsic
  @UninterruptibleNoWarn("Interruptible code not reachable at runtime")
  public void set(int index, Object value) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    data[index] = value;
  }

  /**
   * @return the length of the TIB
   */
  @Override
  @Intrinsic
  public int length() {
    return data.length;
  }

  @Inline
  public RVMType getType() {
    if (VM.runningVM) {
      return Magic.objectAsType(get(TIB_TYPE_INDEX));
    } else {
      return (RVMType)get(TIB_TYPE_INDEX);
    }
  }

  public void setType(RVMType type) {
    set(TIB_TYPE_INDEX, type);
  }

  @Inline
  public short[] getSuperclassIds() {
    return Magic.objectAsShortArray(get(TIB_SUPERCLASS_IDS_INDEX));
  }

  public void setSuperclassIds(short[] superclassIds) {
    set(TIB_SUPERCLASS_IDS_INDEX, superclassIds);
  }

  @Interruptible
  public ITableArray getITableArray() {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    return (ITableArray)get(TIB_INTERFACE_DISPATCH_TABLE_INDEX);
  }

  public void setITableArray(ITableArray iTableArray) {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    set(TIB_INTERFACE_DISPATCH_TABLE_INDEX, iTableArray);
  }

  @Inline
  public int[] getDoesImplement() {
    return Magic.objectAsIntArray(get(TIB_DOES_IMPLEMENT_INDEX));
  }

  public void setDoesImplement(int[] doesImplement) {
    set(TIB_DOES_IMPLEMENT_INDEX, doesImplement);
  }

  @Interruptible
  public IMT getImt() {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    return (IMT)get(TIB_INTERFACE_DISPATCH_TABLE_INDEX);
  }

  public void setImt(IMT imt) {
    if (VM.VerifyAssertions) VM._assert(imt.length() == IMT_METHOD_SLOTS);
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    set(TIB_INTERFACE_DISPATCH_TABLE_INDEX, imt);
  }

  public void setArrayElementTib(TIB arrayElementTIB) {
    if (VM.VerifyAssertions) VM._assert(getType().isArrayType());
    set(TIB_ARRAY_ELEMENT_TIB_INDEX, Magic.tibAsObject(arrayElementTIB));
  }

  /**
   * Gets a virtual method from this TIB.
   *
   * When running the VM, we must translate requests to return the internal
   * lazy compilation trampoline marker.
   *
   * @param virtualMethodIndex the index of the virtual method
   * @return the code for the virtual method or a lazy compilation trampoline
   */
  @NoInline
  @Interruptible
  public CodeArray getVirtualMethod(int virtualMethodIndex) {
    int index = TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex;
    if (VM.runningVM && isInternalLazyCompilationTrampoline(virtualMethodIndex)) {
      return LazyCompilationTrampoline.getInstructions();
    }
    return (CodeArray) get(index);
  }

  /**
   * @param virtualMethodIndex the index of the virtual method
   * @return whether a virtual method is the internal lazy compilation trampoline
   */
  @NoInline
  public boolean isInternalLazyCompilationTrampoline(int virtualMethodIndex) {
    int index = TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex;
    Address tibAddress = Magic.objectAsAddress(this);
    Address callAddress = tibAddress.loadAddress(Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS));
    Address maxAddress = tibAddress.plus(Offset.fromIntZeroExtend(length() << LOG_BYTES_IN_ADDRESS));
    return callAddress.GE(tibAddress) && callAddress.LT(maxAddress);
  }

  @Interruptible
  public CodeArray getVirtualMethod(Offset virtualMethodOffset) {
    return getVirtualMethod(getVirtualMethodIndex(virtualMethodOffset));
  }

  /**
   * Set a virtual method in this TIB.
   *
   * When running the VM, we must translate requests to use the internal
   * lazy compilation trampoline.
   *
   * @param virtualMethodIndex the index of the virtual metho
   * @param code the code for the virtual method
   */
  @NoInline
  public void setVirtualMethod(int virtualMethodIndex, CodeArray code) {
    if (VM.VerifyAssertions) VM._assert(virtualMethodIndex >= 0);

    if (VM.runningVM && code == LazyCompilationTrampoline.getInstructions()) {
      Address tibAddress = Magic.objectAsAddress(this);
      Address callAddress = tibAddress.plus(Offset.fromIntZeroExtend(lazyMethodInvokerTrampolineIndex() << LOG_BYTES_IN_ADDRESS));
      set(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex, callAddress);
    } else {
      set(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex, code);
    }
  }

  public void setVirtualMethod(Offset virtualMethodOffset, CodeArray code) {
    setVirtualMethod(getVirtualMethodIndex(virtualMethodOffset), code);
  }

  /**
   * Calculate the address that is the call target for the lazy method invoker trampoline.
   * @return the offset of the instruction that is the call target
   */
  public int lazyMethodInvokerTrampolineIndex() {
    return length() - lazyMethodInvokerTrampolineWords();
  }

  /**
   * Initialize the lazy method invoker trampoline for this tib.
   */
  @NoInline
  public void initializeInternalLazyCompilationTrampoline() {
    CodeArray source = LazyCompilationTrampoline.getInstructions();
    int targetSlot = lazyMethodInvokerTrampolineIndex();
    int logIPW = LOG_BYTES_IN_ADDRESS - ArchConstants.getLogInstructionWidth();
    int logIPI = LOG_BYTES_IN_INT - ArchConstants.getLogInstructionWidth();
    if (VM.VerifyAssertions) VM._assert(ArchConstants.getLogInstructionWidth() <= LOG_BYTES_IN_INT);
    int mask = 0xFFFFFFFF >>> (((1 << logIPI) - 1) << LOG_BITS_IN_BYTE);
    for (int i = 0; i < lazyMethodInvokerTrampolineWords(); i++) {
      Word currentWord = Word.zero();
      int base = i << logIPW;
      for (int j = 0; j < (1 << logIPW) && (base + j) < source.length(); j++) {
        Word currentEntry = Word.fromIntZeroExtend(source.get(base + j) & mask);
        currentEntry = currentEntry.lsh(((VM.LittleEndian ? j : (1 << logIPW) - (j + 1)) << ArchConstants.getLogInstructionWidth()) << LOG_BITS_IN_BYTE);
        currentWord = currentWord.or(currentEntry);
      }
      set(targetSlot + i, currentWord);
    }
  }


  public void setSpecializedMethod(int specializedMethodIndex, CodeArray code) {
    if (VM.VerifyAssertions) VM._assert(specializedMethodIndex >= 0);
    set(TIB_FIRST_SPECIALIZED_METHOD_INDEX + specializedMethodIndex, code);
  }

  /**
   * @return the number of virtual methods in this TIB.
   */
  public int numVirtualMethods() {
    return length() - TIB_FIRST_VIRTUAL_METHOD_INDEX - lazyMethodInvokerTrampolineWords();
  }

  /**
   * Does this slot in the TIB hold a TIB entry?
   * @param slot the TIB slot
   * @return {@code true} if this the array element TIB
   */
  public boolean slotContainsTib(int slot) {
    if (slot == TIB_ARRAY_ELEMENT_TIB_INDEX && getType().isArrayType()) {
      if (VM.VerifyAssertions) VM._assert(get(slot) != null);
      return true;
    }
    return false;
  }

  /**
   * Does this slot in the TIB hold code?
   * @param slot the TIB slot
   * @return {@code true} if slot is one that holds a code array reference
   */
  public boolean slotContainsCode(int slot) {
    if (VM.VerifyAssertions) {
      VM._assert(slot < length());
    }
    return slot >= TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }

  public void setAlignData(int alignData) {
    this.alignData = alignData;
  }

  public int getAlignData() {
    return alignData;
  }
}
