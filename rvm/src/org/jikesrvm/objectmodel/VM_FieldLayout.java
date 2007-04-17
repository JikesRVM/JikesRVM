/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.vmmagic.unboxed.Offset;

/**
 * This abstract class defines the interface for schemes that layout fields
 * in an object.  Not header fields, (scalar) object fields.
 *
 * The field layout object encapsulates layout state.
 *
 * @author Robin Garner
 *
 */
public abstract class VM_FieldLayout implements VM_SizeConstants {
  
  /**
   * Enable debugging
   */
  protected static final boolean DEBUG = false;
  
  /** Whether to lay out 8byte values first in order to avoid some holes */
  private final boolean largeFieldsFirst;
  
  /** Lay out reference fields in a block */
  private final boolean clusterReferenceFields;
  

  public VM_FieldLayout(boolean largeFieldsFirst,
      boolean clusterReferenceFields) {
    this.largeFieldsFirst = largeFieldsFirst;
    this.clusterReferenceFields = clusterReferenceFields;
  }
  
  /**
   * Maximum of two integers
   */
  protected static int max (int x, int y) {
    return (x > y) ? x : y;
  }
  
  /*
   * Abstract methods that determine the behaviour of a particular layout sceme
   */
  
  /**
   * Return the appropriate layout context object for the given class.
   * 
   * @param klass The class
   * @return The layout context
   */
  protected abstract VM_FieldLayoutContext getLayoutContext(VM_Class klass);

  /**
   * This is where a class gets laid out.  Differences in layout strategy
   * are largely encapsulated in the layoutContext object.
   * 
   * @param klass The class to lay out.
   */
  public void layoutInstanceFields(VM_Class klass) {
    /*
     * Determine available field slots from parent classes, and allocate
     * a new context object for this class and its children.
     */ 
    VM_FieldLayoutContext fieldLayout = getLayoutContext(klass);
    
    // Prefered alignment of object - modified to reflect added fields
    // New fields to be allocated for this object
    VM_Field[] fields = klass.getDeclaredFields();
    
    if (DEBUG) {
      VM.sysWriteln("Laying out: ",klass.toString());
    }
    
    /* 
     * Layout reference fields first pre-pass - This can help some
     * GC schemes.
     */
    if (clusterReferenceFields) {
      // For every field
      for (VM_Field field : fields) {
          if (!field.isStatic() && !field.hasOffset() ) {
            if (field.getType().isReferenceType()) {
              layoutField(fieldLayout, klass, field, BYTES_IN_ADDRESS);
            }
          }
        }
    }
    
    /* 
     * Layout 8byte values first pre-pass - do this to avoid unecessary
     * holes for object layouts such as an int followed by a long
     */
    if (largeFieldsFirst) {
      // For every field
      for (VM_Field field : fields) {
          // Should we allocate space in the object now?
          if (!field.isStatic() && !field.hasOffset()) {
            if (field.getType().getMemoryBytes() == BYTES_IN_LONG) {
              layoutField(fieldLayout, klass, field, BYTES_IN_LONG);
            }
          }
        }
    }

    
    for (VM_Field field : fields) {                               // For every field
      int fieldSize = field.getType().getMemoryBytes();           // size of field
      if (!field.isStatic() && !field.hasOffset()) {              // Allocate space in the object?
        layoutField(fieldLayout, klass, field, fieldSize);
      }
    }
    // VM_JavaHeader requires objects to be int sized/aligned
    if(VM.VerifyAssertions) VM._assert((fieldLayout.getObjectSize() & 0x3) == 0);

    /* Update class to reflect changes */
    
    updateClass(klass, fieldLayout);
  }

  /**
   * Update the VM_Class with context info.
   * 
   * @param klass
   * @param fieldLayout
   */
  protected void updateClass(VM_Class klass, VM_FieldLayoutContext fieldLayout) {
    /*
     * Save the new field layout.
     */
    klass.setFieldLayoutContext(fieldLayout);
    
    klass.setInstanceSizeInternal(VM_ObjectModel.computeScalarHeaderSize(klass) + fieldLayout.getObjectSize());
    klass.setAlignment(fieldLayout.getAlignment());
  }

  /**
   * Update a field to set its offset within the object.
   * 
   * @param klass
   * @param field
   * @param offset
   */
  protected void setOffset(VM_Class klass, VM_Field field, int offset) {
    
    Offset fieldOffset;
    if (offset >= 0) {
      fieldOffset = Offset.fromIntSignExtend(
        VM_JavaHeader.objectStartOffset(klass) + 
        VM_ObjectModel.computeScalarHeaderSize(klass) + offset);
    } else {
      /* Negative offsets go before the header */
      fieldOffset = Offset.fromIntSignExtend( 
          VM_JavaHeader.objectStartOffset(klass) + offset);
    }
    field.setOffset(fieldOffset);
    if (DEBUG) {
      VM.sysWrite("  field: ",field.toString());
      VM.sysWriteln(" offset ",fieldOffset.toInt());
    }
  }

  /**
   * Lay out a given field.
   * 
   * @param layout State for the layout process
   * @param klass The class whose fields we're laying out.
   * @param field The field we are laying out.
   * @param fieldSize The size of the field.
   */
  protected void layoutField(VM_FieldLayoutContext layout, VM_Class klass, VM_Field field,
      int fieldSize) {
    boolean isRef = field.getType().isReferenceType();
    setOffset(klass, field, layout.nextOffset(fieldSize,isRef));
  }
}
