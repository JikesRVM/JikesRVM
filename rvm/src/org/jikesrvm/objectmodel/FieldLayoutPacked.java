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

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.runtime.Memory;

/**
 * Layout fields in an object, packt like sardines in a crushd tin box.
 */
public class FieldLayoutPacked extends FieldLayout implements SizeConstants {

  /**
   * Lay out an object, maintaining offsets of free slots of size 1,2,4 and 8
   * bytes.
   */
  private static class LayoutContext extends FieldLayoutContext {
    private static final int LOG_MAX_SLOT_SIZE = LOG_BYTES_IN_LONG;
    private static final int MAX_SLOT_SIZE = (1 << LOG_MAX_SLOT_SIZE);

    private short slot0;
    private short slot1;
    private short slot2;

    /**
     * Get the value for a given slot.
     */
    private short get(int logSize) {
      switch (logSize) {
        case 0: return slot0;
        case 1: return slot1;
        case 2: return slot2;
        case 3: return (short)Memory.alignUp(getObjectSize(), MAX_SLOT_SIZE);
        default: VM.sysFail("Invalid slot"); return -1;
      }
    }

    /**
     * Set the value for a given slot.
     */
    private void set(int logSize, int value) {
      if (VM.VerifyAssertions) VM._assert(value >= 0 && value < Short.MAX_VALUE);
      short shortValue = (short)value;
      switch (logSize) {
        case 0: slot0 = shortValue; break;
        case 1: slot1 = shortValue; break;
        case 2: slot2 = shortValue; break;
        case 3: if (VM.VerifyAssertions) VM._assert(shortValue == 0);
      }
    }

    /**
     * Create a layout for an object, initializing offsets from its
     * superclass.
     *
     * @param alignment Current alignment of first field.
     * @param superLayout Superclass layout context
     */
    LayoutContext(byte alignment, LayoutContext superLayout) {
      super(alignment, superLayout);
      if (superLayout != null) {
        for (int i = 0; i < LOG_MAX_SLOT_SIZE; i++) {
          set(i, superLayout.get(i));
        }
      }
    }

    /**
     * Return the next available offset for a given size
     *
     * @param size Size of the field to be laid out.  Must be
     * a power of 2.
     */
    @Override
    int nextOffset(int size, boolean isReference) {
      if (VM.VerifyAssertions) VM._assert((size & (size - 1)) == 0);  // Ensure =2^n
      adjustAlignment(size);

      /* Calculate the log of the size of the field */
      int logSize = log2(size);
      int result = 0;

      /* Find a free slot */
      for(int i = logSize; i <= LOG_MAX_SLOT_SIZE; i++) {
        int slot = get(i);
        if (slot != 0 || i == LOG_MAX_SLOT_SIZE) {
          result = slot;
          set(i, 0);
          /* Set any holes we have created */
          for (i = i - 1; i >= logSize; i--) {
            if (VM.VerifyAssertions) VM._assert(get(i) == 0);
            set(i, result + (1 << i));
          }
          break;
        }
      }

      /* Make sure the field fits */
      ensureObjectSize(result + size);

      if (DEBUG) {
        VM.sysWrite("  field: & offset ", result, " New object size = ", getObjectSize());
        VM.sysWrite(" slots: ");
        for(int i=0; i < LOG_MAX_SLOT_SIZE; i++) {
          VM.sysWrite(get(i), i == LOG_MAX_SLOT_SIZE - 1 ? "" : ", ");
        }
        VM.sysWriteln();
      }

      /* Bounds check - scalar objects this size are impossible, surely ?? */
      if (result >= Short.MAX_VALUE) {
        VM.sysFail("Scalar class size exceeds offset width");
      }

      return result;
    }
  }

  public FieldLayoutPacked(boolean largeFieldsFirst, boolean clusterReferenceFields) {
    super(largeFieldsFirst, clusterReferenceFields);
  }

  /**
   * @param klass the class to layout
   * @return The layout context
   * @see FieldLayout#getLayoutContext(RVMClass)
   */
  @Override
  protected FieldLayoutContext getLayoutContext(RVMClass klass) {
    return new LayoutContext((byte) klass.getAlignment(), (LayoutContext) klass.getFieldLayoutContext());
  }
}
