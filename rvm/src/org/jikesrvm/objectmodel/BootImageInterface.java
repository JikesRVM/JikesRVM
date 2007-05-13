/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
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
   */
  void setAddressWord(Address offset, Word value, boolean objField);

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   */
  void setNullAddressWord(Address offset, boolean objField);

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  void setDoubleWord(Address offset, long value);
}
