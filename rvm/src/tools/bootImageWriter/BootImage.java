/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.FileOutputStream;
import java.io.IOException;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Memory image of virtual machine that will be written to disk file and later
 * "booted".
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public class BootImage extends BootImageWriterMessages
  implements BootImageWriterConstants, BootImageInterface {

  /**
   * Talk while we work?
   */
  private boolean trace = false;

  /**
   * Write words low-byte first?
   */
  private boolean littleEndian;

  /**
   * The actual boot image
   */
  private byte[] bootImage;

  /**
   * Offset of next free word, in bytes
   */
  private int freeOffset;

  /**
   * Offset of last free word + 1, in bytes
   */
  private int endOffset;

  /**
   * Number of objects appearing in bootimage
   */
  private int numObjects;

  /**
   * Number of non-null object addresses appearing in bootimage
   */
  private int numAddresses;

  /**
   * Number of object addresses set to null because they referenced objects
   * that are not part of bootimage
   */
  private int numNulledReferences;

  /**
   * @param ltlEndian write words low-byte first?
   * @param t turn tracing on?
   */
  BootImage(boolean ltlEndian, boolean t) {
    bootImage = new byte[endOffset = IMAGE_SIZE];
    littleEndian = ltlEndian;
    trace = t;
  }

  /**
   * Write boot image to disk.
   *
   * @param imageFileName the name of the image file
   */
  public void write(String imageFileName) throws IOException {
    if (trace) {
      say((numObjects / 1024)   + "k objects");
      say((numAddresses / 1024) + "k non-null object references");
      say(numNulledReferences + " references nulled because they are "+
          "non-jdk fields or point to non-bootimage objects");
      say((VM_Statics.getNumberOfSlots() / 1024) + "k jtoc slots");
      say((getSize() / 1024) + "k image");
      say("writing " + imageFileName);
    }
    FileOutputStream out = new FileOutputStream(imageFileName);
    out.write(bootImage, 0, getSize());
    out.flush();
    out.close();
  }

  /**
   * Get image size, in bytes.
   * @return image size
   */
  public int getSize() {
    return freeOffset;
  }

  /**
   * Allocate a scalar object.
   * Note: size should be divisible by 4
   *
   * @param klass VM_Class object of scalar being allocated
   * @return offset of object within bootimage, in bytes
   */
  public int allocateScalar(VM_Class klass) {
    numObjects++;
    klass.bootCount++;
    klass.bootBytes += klass.getInstanceSize();
    return VM_ObjectModel.allocateScalar(this, klass);
  }

  /**
   * Allocate an array object.
   *
   * @param array VM_Array object of array being allocated.
   * @param numElements number of elements
   * @return offset of object within bootimage, in bytes
   */
  int bootStackCount = 0;
  int bootStackBytes = 0;
  public int allocateArray(VM_Array array, int numElements) {
    numObjects++;
    array.bootCount++;
    int size = array.getInstanceSize(numElements);
    array.bootBytes += ((size + 3) & ~3);
    return VM_ObjectModel.allocateArray(this, array, numElements);
  }

  /**
   * Allocate space in bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   * @param size the number of bytes to allocate
   */
  public int allocateStorage(int size) {
    int lowAddr = freeOffset;
    freeOffset += ((size + 3) & ~3); // maintain word alignment
    if (freeOffset > endOffset)
      fail("bootimage full (need " + size + " more bytes)");
    return lowAddr;
  }


  /**
   * Allocate space in bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   * @param size the number of bytes to allocate
   */
  public int allocateAlignedStorage(int size) {
      // DFB: MAJOR HACK
      freeOffset += 8;
      freeOffset = VM_Memory.align(freeOffset, 16);
      int result = freeOffset - 8;
      freeOffset = VM_Memory.align(freeOffset + size - 8, 4);
      if (freeOffset > endOffset)
	  fail("bootimage full (need " + size + " more bytes)");
      return result;
  }


  /**
   * Fill in 1 byte of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setByte(int offset, int value) {
    bootImage[offset] = (byte) value;
  }

  /**
   * Fill in 2 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setHalfWord(int offset, int value) {
    if (littleEndian) {
      bootImage[offset++] = (byte) (value);
      bootImage[offset  ] = (byte) (value >>  8);
    } else {
      bootImage[offset++] = (byte) (value >>  8);
      bootImage[offset  ] = (byte) (value);
    }
  }

  /**
   * Fill in 4 bytes of bootimage, as numeric.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setFullWord(int offset, int value) {
    if (littleEndian) {
      bootImage[offset++] = (byte) (value);
      bootImage[offset++] = (byte) (value >>  8);
      bootImage[offset++] = (byte) (value >> 16);
      bootImage[offset  ] = (byte) (value >> 24);
    } else {
      bootImage[offset++] = (byte) (value >> 24);
      bootImage[offset++] = (byte) (value >> 16);
      bootImage[offset++] = (byte) (value >>  8);
      bootImage[offset  ] = (byte) (value);
    }
  }

  /**
   * Fill in 4 bytes of bootimage, as object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setAddressWord(int offset, int value) {
    setFullWord(offset, value);
    numAddresses += 1;
  }

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   */
  public void setNullAddressWord(int offset) {
    setAddressWord(offset, 0);
    numNulledReferences += 1;
  }

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setDoubleWord(int offset, long value) {
    if (littleEndian) {
      bootImage[offset++] = (byte) (value);
      bootImage[offset++] = (byte) (value >>  8);
      bootImage[offset++] = (byte) (value >> 16);
      bootImage[offset++] = (byte) (value >> 24);
      bootImage[offset++] = (byte) (value >> 32);
      bootImage[offset++] = (byte) (value >> 40);
      bootImage[offset++] = (byte) (value >> 48);
      bootImage[offset  ] = (byte) (value >> 56);
    } else {
      bootImage[offset++] = (byte) (value >> 56);
      bootImage[offset++] = (byte) (value >> 48);
      bootImage[offset++] = (byte) (value >> 40);
      bootImage[offset++] = (byte) (value >> 32);
      bootImage[offset++] = (byte) (value >> 24);
      bootImage[offset++] = (byte) (value >> 16);
      bootImage[offset++] = (byte) (value >>  8);
      bootImage[offset  ] = (byte) (value);
    }
  }

  /**
   * Keep track of how many references were set null because they pointed to
   * non-bootimage objects.
   */
  public void countNulledReference() {
    numNulledReferences += 1;
  }
}
