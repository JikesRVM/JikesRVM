/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.unboxed.*;
 
/**
 * Interface of BootImage that is used to define object model classes.
 *
 * @author Dave Grove
 * @author Derek Lieber
 */
public interface BootImageInterface {

  /**
   * Allocate space in data portion of bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  public Address allocateDataStorage(int size, int align, int offset);

  /**
   * Allocate space in code portion of bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  public Address allocateCodeStorage(int size, int align, int offset);

  /**
   * Fill in 1 byte of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setByte(Address offset, int value);

  /**
   * Fill in 2 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setHalfWord(Address offset, int value);

  /**
   * Fill in 4 bytes of bootimage, as numeric.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setFullWord(Address offset, int value);

  /**
   * Fill in 4/8 bytes of bootimage, as object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setAddressWord(Address offset, Word value);

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   */
  public void setNullAddressWord(Address offset);

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setDoubleWord(Address offset, long value);
}
