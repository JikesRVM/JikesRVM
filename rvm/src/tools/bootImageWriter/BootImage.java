/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.FileOutputStream;
import java.io.IOException;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

/**
 * Memory image of virtual machine that will be written to disk file and later
 * "booted".
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public class BootImage extends BootImageWriterMessages
  implements BootImageWriterConstants, BootImageInterface, VM_SizeConstants {

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
  private int freeOffset = 0;

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
  public int allocateArray(VM_Array array, int numElements) {
    numObjects++;
    array.bootCount++;
    array.bootBytes += array.getInstanceSize(numElements);
    return VM_ObjectModel.allocateArray(this, array, numElements);
  }

  /**
   * Allocate space in bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   *
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  public int allocateStorage(int size, int align, int offset) {
    freeOffset = MM_Interface.alignAllocation(freeOffset, align, offset);
    if (VM.ExtremeAssertions) {
      VM._assert(((freeOffset + offset) & (align -1)) == 0); 
      VM._assert((freeOffset & ~3) == freeOffset);
    }
    int lowAddr = freeOffset;
    freeOffset += size;
    if (freeOffset > endOffset)
      fail("bootimage full (need at least " + size + " more bytes)");
    
    return lowAddr;
  }

  /**
   * Reset the allocator as if no allocation had occured.  This is
   * useful to allow a "trial run", as is done to establish the offset
   * of the JTOC for the entry in the boot image record---so its
   * actual address can be computed early in the build process.
   */
  public void resetAllocator() {
    freeOffset = 0;
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
   * Fill in 4/8 bytes of bootimage, as object reference.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public void setAddressWord(int offset, VM_Word value) {
//-#if RVM_FOR_32_ADDR
    setFullWord(offset, value.toInt());
    numAddresses++;
//-#endif
//-#if RVM_FOR_64_ADDR
    setDoubleWord(offset, value.toLong());
    numAddresses++;
//-#endif
  }

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   */
  public void setNullAddressWord(int offset) {
    setAddressWord(offset, VM_Word.zero());
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
