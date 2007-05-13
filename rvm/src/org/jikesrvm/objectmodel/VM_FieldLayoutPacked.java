/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Australian National University 2007
 */
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Class;

/**
 * Layout fields in an object, packt like sardines in a crushd tin box.
 */
public class VM_FieldLayoutPacked extends VM_FieldLayout implements VM_SizeConstants {
  
  /**
   * Lay out an object, maintaining offsets of free slots of size 1,2,4 and 8
   * bytes.
 */
  private static class LayoutContext extends VM_FieldLayoutContext {
    private static final int LOG_MAX_SLOT_SIZE = 3;
    private static final int MAX_SLOT_SIZE = (1<<LOG_MAX_SLOT_SIZE);
    final short[] slots = new short[LOG_MAX_SLOT_SIZE+1];
    
    /**
     * Create a layout for an object without a superclass (ie j.l.Object).
     * 
     * @param alignment
     */
    LayoutContext(byte alignment) {
      super(alignment);
      for (int i=0; i < slots.length; i++) {
        slots[i] = 0;
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
      super(alignment,superLayout);
      for (int i=0; i < slots.length; i++)
        if (superLayout != null) {
          slots[i] = superLayout.slots[i];
        } else {
          slots[i] = 0;
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
      if (VM.VerifyAssertions) VM._assert((size & (size-1)) == 0);  // Ensure =2^n
      adjustAlignment(size);
      
      /* Calculate the log of the size of the field */
      int logSize = 0;
      while ((1<<logSize) != size)
        logSize += 1;
      int result = slots[logSize];
      slots[logSize] += size;
      
      /* 
       * Other size classes may have been pointing to this slot.
       * Bump them up to the next multiple of their size class past
       * the space allocated for this field.
       */
      for (int i=0; i < slots.length; i++)
        if (slots[i] == result)
          slots[i] += VM_FieldLayoutPacked.max(size,1<<i);
      
      /*
       * At this point, each free slot pointer should be either identical to
       * the free slot of the next largest size class, or not aligned
       * to the next size class.  Make it so.
       */
      for (int i = slots.length-2, nextSlotSize = MAX_SLOT_SIZE; i >= 0; i--, nextSlotSize >>= 1) {
        if ((slots[i] & (nextSlotSize-1)) == 0) {
          // slot is aligned to the next size class
          slots[i] = slots[i+1];
        }
      }
      ensureObjectSize(result + size);
      if (DEBUG) {
        VM.sysWrite("  field: & offset ",result," New object size = ",getObjectSize());
        VM.sysWrite(" slots: ",slots[0],",",slots[1]);
        VM.sysWriteln(",",slots[2],",",slots[3]);
      }
      
      /*
       * Bounds check - scalar objects this size are impossible, surely ?? 
       */
      if (result >= Short.MAX_VALUE) {
        VM.sysFail("Scalar class size exceeds offset width");
      }
      
      return result;
    }
  }
    
  public VM_FieldLayoutPacked(boolean largeFieldsFirst,
      boolean clusterReferenceFields) {
    super(largeFieldsFirst, clusterReferenceFields);
  }

  /**
   * @see VM_FieldLayout#getLayoutContext(VM_Class)
   */
  @Override
  protected VM_FieldLayoutContext getLayoutContext(VM_Class klass) {
    return new LayoutContext((byte)klass.getAlignment(), 
        (LayoutContext)klass.getFieldLayoutContext());
  }

}
