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
   * Allocate space in bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  public Offset allocateStorage(int size, int align, int offset);

  /**
   * Fill in 1 byte of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setByte(Offset offset, int value);

  /**
   * Fill in 2 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setHalfWord(Offset offset, int value);

  /**
   * Fill in 4 bytes of bootimage, as numeric.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setFullWord(Offset offset, int value);

  /**
   * Fill in 4/8 bytes of bootimage, as object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setAddressWord(Offset offset, Word value);

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   */
  public void setNullAddressWord(Offset offset);

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setDoubleWord(Offset offset, long value);
}
