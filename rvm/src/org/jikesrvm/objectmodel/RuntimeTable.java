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

import org.vmmagic.Intrinsic;

/**
 * This interface is used to indicate a type will behave as a runtime table.
 * Runtime tables are used to implement arrays whose elements can only be
 * manipulated by the the get and set methods of the table. Every runtime table
 * will have a static allocate method and implement the methods below.
 *
 * @see org.jikesrvm.classloader.TypeReference#isRuntimeTable()
 */
public interface RuntimeTable<T> {
  /**
   * Get a value from the table. This method is hijacked by the compiler but the
   * implementation is used during boot image writing.
   *
   * @param index location to read
   * @return value from table
   */
  @Intrinsic
  T get(int index);
  /**
   * Set a value to the table. This method is hijacked by the compiler but the
   * implementation is used during boot image writing.
   *
   * @param index location to write
   * @param value to write
   */
  @Intrinsic
  void set(int index, T value);
  /**
   * Get the table length. This method is hijacked by the compiler but the
   * implementation is used during boot image writing.
   *
   * @return length of table
   */
  @Intrinsic
  int length();
  /**
   * Only called at boot image write time. This returns the backing array to the
   * boot image writer.
   *
   * @return backing array of elements
   */
  @Intrinsic
  T[] getBacking();
}
