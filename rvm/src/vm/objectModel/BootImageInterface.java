/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

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
  public int allocateStorage(int size, int align, int offset);

  /**
   * Fill in 1 byte of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setByte(int offset, int value);

  /**
   * Fill in 2 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setHalfWord(int offset, int value);

  /**
   * Fill in 4 bytes of bootimage, as numeric.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setFullWord(int offset, int value);

  /**
   * Fill in 4/8 bytes of bootimage, as object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
//-#if RVM_FOR_32_ADDR
  public void setAddressWord(int offset, int value);
//-#endif
//-#if RVM_FOR_64_ADDR
  public void setAddressWord(int offset, long value);
//-#endif
  public void setAddressWord(int offset, VM_Word value);

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   */
  public void setNullAddressWord(int offset);

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setDoubleWord(int offset, long value);
}
