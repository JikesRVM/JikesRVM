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

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Word;

/**
 * Interface of BootImage that is used to define object model classes.
 */
public interface BootImageInterface {

  /**
   * Allocate space in data portion of bootimage. Moral equivalent of
   * memory managers allocating raw storage at runtime.
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  Address allocateDataStorage(int size, int align, int offset);

  /**
   * Allocate space in code portion of bootimage. Moral equivalent of
   * memory managers allocating raw storage at runtime.
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  Address allocateCodeStorage(int size, int align, int offset);

  /**
   * Fill in 1 byte of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  void setByte(Address offset, int value);

  /**
   * Fill in 2 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  void setHalfWord(Address offset, int value);

  /**
   * Fill in 4 bytes of bootimage, as numeric.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  void setFullWord(Address offset, int value);

  /**
   * Fill in 4/8 bytes of bootimage, as object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   * @param root Does this slot contain a possible reference into the heap? (objField must also be true)
   */
  void setAddressWord(Address offset, Word value, boolean objField, boolean root);

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   * @param root Does this slot contain a possible reference into the heap? (objField must also be true)
   */
  void setNullAddressWord(Address offset, boolean objField, boolean root);

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  void setDoubleWord(Address offset, long value);
}
