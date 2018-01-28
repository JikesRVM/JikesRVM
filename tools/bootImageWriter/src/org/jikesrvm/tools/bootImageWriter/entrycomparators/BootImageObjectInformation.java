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
package org.jikesrvm.tools.bootImageWriter.entrycomparators;

import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.fail;

import java.lang.reflect.Array;
import java.util.HashMap;

import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.objectmodel.RuntimeTable;

/**
 * Provides information on objects in the boot image to the comparators in this package.
 */
class BootImageObjectInformation {

  private static final HashMap<RVMType, Integer> typeSizes = new HashMap<RVMType, Integer>();

  /**
   * Get the number of non-final references of the object in the boot image
   * @param type of object
   * @param obj we want the size of
   * @return number of non-final references
   */
  static int getNumberOfNonFinalReferences(RVMType type, Object obj) {
    if (type.isArrayType()) {
      if (type.asArray().getElementType().isReferenceType()) {
        if (obj instanceof RuntimeTable) {
          obj = ((RuntimeTable<?>)obj).getBacking();
        } else if (obj instanceof CodeArray) {
          obj = ((CodeArray)obj).getBacking();
        }
        if (!obj.getClass().isArray()) {
          fail("This should be an array " + obj.getClass() + " " + type);
        }
        return Array.getLength(obj);
      } else {
        return 0;
      }
    } else {
      Integer size = typeSizes.get(type);
      if (size == null) {
        // discount final references that aren't part of the boot image
        size = type.asClass().getNumberOfNonFinalReferences();
        typeSizes.put(type, size);
      }
      return size;
    }
  }

  /**
   * Get the number of non-final references of the object in the boot image
   * @param type of object
   * @param obj we want the size of
   * @return number of non-final references
   */
  static int getNumberOfReferences(RVMType type, Object obj) {
    if (type.isArrayType()) {
      if (type.asArray().getElementType().isReferenceType()) {
        if (obj instanceof RuntimeTable) {
          obj = ((RuntimeTable<?>)obj).getBacking();
        } else if (obj instanceof CodeArray) {
          obj = ((CodeArray)obj).getBacking();
        }
        if (!obj.getClass().isArray()) {
          fail("This should be an array " + obj.getClass() + " " + type);
        }
        return Array.getLength(obj);
      } else {
        return 0;
      }
    } else {
      Integer size = typeSizes.get(type);
      if (size == null) {
        // discount final references that aren't part of the boot image
        size = type.getReferenceOffsets().length;
        typeSizes.put(type, size);
      }
      return size;
    }
  }

  /**
   * Get the size of the object in the boot image
   * @param type of object
   * @param obj we want the size of
   * @return size of object
   */
  static int getSize(RVMType type, Object obj) {
    if (type.isArrayType()) {
      if (obj instanceof RuntimeTable) {
        obj = ((RuntimeTable<?>)obj).getBacking();
      } else if (obj instanceof CodeArray) {
        obj = ((CodeArray)obj).getBacking();
      }
      if (!obj.getClass().isArray()) {
        fail("This should be an array " + obj.getClass() + " " + type);
      }
      return type.asArray().getInstanceSize(Array.getLength(obj));
    } else {
      return type.asClass().getInstanceSize();
    }
  }

}
