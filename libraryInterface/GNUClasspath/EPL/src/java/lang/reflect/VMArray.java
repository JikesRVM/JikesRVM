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
package java.lang.reflect;

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
    return VMCommonLibrarySupport.createArray(cls, length);
  }
}
