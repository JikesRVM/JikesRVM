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
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Offset;

/**
 * This class represents an instance of a type information block.
 *
 * #see {@link VM_TIBLayoutConstants}
 */
@Uninterruptible
public final class VM_TIB implements VM_TIBLayoutConstants, VM_SizeConstants {
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

  /**
   * Private constructor. Can not create instances.
   */
  private VM_TIB(int size) {
    this.data = new Object[size];
  }

  /**
   * Return the backing array (for boot image writing)
   */
  public Object[] getBacking() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return data;
  }

  /**
   * Create a new TIB of the specified size.
   *
   * @param size The size of the TIB
   * @return The created TIB instance.
   */
  @Interruptible
  public static VM_TIB allocate(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_TIB(size);
  }

  /**
   * Get a TIB entry.
   *
   * @param index The index of the entry to get
   * @return The value of that entry
   */
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
  @Intrinsic
  @UninterruptibleNoWarn // hijacked at runtime
  protected void set(int index, Object value) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    data[index] = value;
  }

  /**
   * Return the length of the TIB
   */
  @Intrinsic
  public int length() {
    if (VM.runningVM || VM.writingImage) VM._assert(false);  // should be hijacked
    return data.length;
  }

  /**
   * Get the type for this TIB.
   */
  @Inline
  public VM_Type getType() {
    return VM_Magic.objectAsType(get(TIB_TYPE_INDEX));
  }

  /**
   * Set the type for this TIB.
   */
  public void setType(VM_Type type) {
    set(TIB_TYPE_INDEX, type);
  }

  /**
   * Get the superclass id set for this type.
   */
  @Inline
  public short[] getSuperclassIds() {
    return VM_Magic.objectAsShortArray(get(TIB_SUPERCLASS_IDS_INDEX));
  }

  /**
   * Set the superclass id set for this type.
   */
  public void setSuperclassIds(short[] superclassIds) {
    set(TIB_SUPERCLASS_IDS_INDEX, superclassIds);
  }

  /**
   * Get the ITable array for this type.
   */
  @Interruptible
  public VM_ITableArray getITableArray() {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    return (VM_ITableArray)get(TIB_ITABLES_TIB_INDEX);
  }

  /**
   * Set the ITable array for this type.
   */
  public void setITableArray(VM_ITableArray iTableArray) {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    set(TIB_ITABLES_TIB_INDEX, iTableArray);
  }

  /**
   * Get the does implement entry of the TIB
   */
  @Inline
  public int[] getDoesImplement() {
    return VM_Magic.objectAsIntArray(get(TIB_DOES_IMPLEMENT_INDEX));
  }

  /**
   * Set the does implement entry of the TIB
   */
  public void setDoesImplement(int[] doesImplement) {
    set(TIB_DOES_IMPLEMENT_INDEX, doesImplement);
  }

  /**
   * Get the IMT from the TIB
   */
  @Interruptible
  public VM_IMT getImt() {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    return (VM_IMT)get(TIB_IMT_TIB_INDEX);
  }

  /**
   * Set the IMT of the TIB
   */
  public void setImt(VM_IMT imt) {
    if (VM.VerifyAssertions) VM._assert(imt.length() == IMT_METHOD_SLOTS);
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    set(TIB_IMT_TIB_INDEX, imt);
  }

  /**
   * Set the TIB of the elements of this array (null if not an array).
   */
  public void setArrayElementTib(VM_TIB arrayElementTIB) {
    if (VM.VerifyAssertions) VM._assert(getType().isArrayType());
    set(TIB_ARRAY_ELEMENT_TIB_INDEX, VM_Magic.tibAsObject(arrayElementTIB));
  }

  /**
   * Get a virtual method from this TIB.
   */
  @Interruptible
  public VM_CodeArray getVirtualMethod(int virtualMethodIndex) {
    return (VM_CodeArray) get(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex);
  }

  /**
   * Get a virtual method from this TIB by offset.
   */
  @Interruptible
  public VM_CodeArray getVirtualMethod(Offset virtualMethodOffset) {
    return (VM_CodeArray) get(TIB_FIRST_VIRTUAL_METHOD_INDEX + getVirtualMethodIndex(virtualMethodOffset));
  }

  /**
   * Set a virtual method in this TIB.
   */
  public void setVirtualMethod(int virtualMethodIndex, VM_CodeArray code) {
    if (VM.VerifyAssertions) VM._assert(virtualMethodIndex >= 0);
    set(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex, code);
  }

  /**
   * Set a virtual method in this TIB by offset.
   */
  public void setVirtualMethod(Offset virtualMethodOffset, VM_CodeArray code) {
    int virtualMethodIndex = getVirtualMethodIndex(virtualMethodOffset);
    if (VM.VerifyAssertions) VM._assert(virtualMethodIndex >= 0);
    set(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex, code);
  }

  /**
   * Set a specialized method in this TIB.
   */
  public void setSpecializedMethod(int specializedMethodIndex, VM_CodeArray code) {
    if (VM.VerifyAssertions) VM._assert(specializedMethodIndex >= 0);
    set(TIB_FIRST_SPECIALIZED_METHOD_INDEX + specializedMethodIndex, code);
  }

  /**
   * Set an IMT entry in this TIB.
   */
  public void setImtEntry(int imtEntryIndex, VM_CodeArray code) {
    set(TIB_FIRST_INTERFACE_METHOD_INDEX + imtEntryIndex, code);
  }

  /**
   * The number of virtual methods in this TIB.
   */
  public int numVirtualMethods() {
    return length() - TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }

  /**
   * Does this slot in the TIB hold a TIB entry?
   * @param slot the TIB slot
   * @return true if this the array element TIB
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
   * @return true if slot is one that holds a code array reference
   */
  public boolean slotContainsCode(int slot) {
    if (VM.VerifyAssertions) {
      VM._assert(slot < length());
    }
    return slot >= TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }
}
