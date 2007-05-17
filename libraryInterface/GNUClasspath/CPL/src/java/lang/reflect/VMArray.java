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
package java.lang.reflect;

import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.runtime.VM_Runtime;

/**
 * VM dependent Array operations
 */
class VMArray {
  /**
   * Dynamically create an array of objects.
   *
   * @param cls guaranteed to be a valid object type
   * @param length the length of the array
   * @return the new array
   * @throws NegativeArraySizeException if dim is negative
   * @throws OutOfMemoryError if memory allocation fails
   */
  static Object createObjectArray(Class<?> cls, int length)
    throws OutOfMemoryError, NegativeArraySizeException {
    if(cls == null)
      throw new NullPointerException();
    if(length < 0)
      throw new NegativeArraySizeException();

    VM_Array arrayType = java.lang.JikesRVMSupport.getTypeForClass(cls).getArrayTypeForElementType();
    if (!arrayType.isInitialized()) {
      arrayType.resolve();
      arrayType.instantiate();
      arrayType.initialize();
    }
    return VM_Runtime.resolvedNewArray(length, arrayType);
  }
}
