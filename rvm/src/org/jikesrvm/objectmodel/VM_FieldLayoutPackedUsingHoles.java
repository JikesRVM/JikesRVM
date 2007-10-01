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
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.runtime.VM_Memory;

/**
 * Layout fields in an object, packed like sardines in a crushed tin box.
 */
public class VM_FieldLayoutPackedUsingHoles extends VM_FieldLayout implements VM_SizeConstants {

  /**
   * Lay out an object, maintaining offsets of free slots of size 1,2,4 and 8
   * bytes.
   */
  private static class LayoutContext extends VM_FieldLayoutContext {
    /** A hole available to allocate a byte in */
    private short byteHole;
    /** A hole available to allocate a short in */
    private short shortHole;
    /** A hole available to allocate an int in */
    private short intHole;

    /**
     * Set hole for allocating bytes into
     * @param hole location
     */
    private void setByteHole(int hole) {
      if (hole <= 0xFFFF) {
        byteHole = (short)hole;
      }
      else {
        byteHole = 0;
      }
    }

    /**
     * @return A hole for allocating bytes into or 0 if none
     */
    private int getByteHole() {
      return byteHole;
    }

    /**
     * Set hole for allocating shorts into
     * @param hole location
     */
    private void setShortHole(int hole) {
      hole >>= 1;
      if (hole <= 0xFFFF) {
        shortHole = (short)hole;
      }
      else {
        shortHole = 0;
      }
    }

    /**
     * @return A hole for allocating shorts into or 0 if none
     */
    private int getShortHole() {
      return shortHole << 1;
    }

    /**
     * Set hole for allocating ints into
     * @param hole location
     */
    private void setIntHole(int hole) {
      hole >>= 2;
      if (hole <= 0xFFFF) {
        intHole = (short)hole;
      }
      else {
        intHole = 0;
      }
    }

    /**
     * @return A hole for allocating ints into or 0 if none
     */
    private int getIntHole() {
      return intHole << 2;
    }

    /**
     * Create a layout for an object without a superclass (ie j.l.Object).
     *
     * @param alignment
     */
    LayoutContext(byte alignment) {
      super(alignment);
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
        byteHole = superLayout.byteHole;
        shortHole = superLayout.shortHole;
        intHole = superLayout.intHole;
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
      adjustAlignment(size);
      // result of allocation
      int result;
      // try to fit field into a pre-existing hole
      if (size <= 1 && getByteHole() != 0) {
        result = getByteHole();
        setByteHole(0);
      }
      else if (size <= 2 && getShortHole() != 0) {
        result = getShortHole();
        if (size <= 1) {
          setByteHole(result + 1);
        }
        setShortHole(0);
      }
      else if (size <= 4) {
        if (getIntHole() != 0) {
          result = getIntHole();
          if (size <= 2) {
            setShortHole(result + 2);
          }
          if (size <= 1) {
            setByteHole(result + 1);
          }
          setIntHole(0);
        }
        else {
          // Failed to find a hole so allocate at end of object
          result = getObjectSize();
          setObjectSize (result + 4);
          if (size <= 2) {
            setShortHole(result + 2);
          }
          if (size <= 1) {
            setByteHole(result + 1);
          }
        }
      }
      else {
        // Longs are always allocated at the end of objects
        if (VM.VerifyAssertions) VM._assert(size == 8);
        result = getObjectSize();
        // Ensure long alignment
        int alignedResult = VM_Memory.alignUp(result, BYTES_IN_LONG);
        if (result != alignedResult) {
          setIntHole(result);
          result += 4;
        }
        setObjectSize (result + 8);
      }
      if (DEBUG) {
        VM.sysWrite("  field: & offset ", result, " New object size = ", getObjectSize());
      }

      return result;
    }
  }

  public VM_FieldLayoutPackedUsingHoles(boolean largeFieldsFirst, boolean clusterReferenceFields) {
    super(largeFieldsFirst, clusterReferenceFields);
  }

  /**
   * @see VM_FieldLayout#getLayoutContext(VM_Class)
   */
  @Override
  protected VM_FieldLayoutContext getLayoutContext(VM_Class klass) {
    return new LayoutContext((byte) klass.getAlignment(), (LayoutContext) klass.getFieldLayoutContext());
  }

}
