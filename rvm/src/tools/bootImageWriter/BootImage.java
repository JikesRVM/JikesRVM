/*
 * (C) Copyright IBM Corp. 2001
 */
//BootImage.java
//$Id$

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Memory image of virtual machine that will be written to disk file and later
 * "booted".
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public class BootImage extends BootImageWriterMessages
  implements BootImageWriterConstants
{
  /**
   * Talk while we work?
   */
  private static boolean trace = false;

  /**
   * Write words low-byte first?
   */
  private static boolean littleEndian;

  /**
   * The actual boot image
   */
  private static byte[] bootImage;

  /**
   * Offset of next free word, in bytes
   */
  private static int freeOffset;

  /**
   * Offset of last free word + 1, in bytes
   */
  private static int endOffset;

  /**
   * Number of objects appearing in bootimage
   */
  private static int numObjects;

  /**
   * Number of non-null object addresses appearing in bootimage
   */
  private static int numAddresses;

  /**
   * Number of object addresses set to null because they referenced objects
   * that are not part of bootimage
   */
  private static int numNulledReferences;

  /**
   * Prepare boot image for use.
   *
   * @param ltlEndian write words low-byte first?
   * @param t turn tracing on?
   */
  public static void init(boolean ltlEndian, boolean t) {
    bootImage = new byte[endOffset = IMAGE_SIZE];
    littleEndian = ltlEndian;
    trace = t;
  }

  /**
   * Write boot image to disk.
   *
   * @param imageFileName the name of the image file
   */
  public static void write(String imageFileName) throws IOException {
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
  public static int getSize() {
    return freeOffset;
  }

  /**
   * Allocate a scalar object.
   * Note: size should be divisible by 4
   *
   * @param size object's size, in bytes (including object header)
   * @return offset of object within bootimage, in bytes
   */
  public static int allocateScalar(int size) {
    numObjects += 1;

    //
    // allocate space, noting that scalars are layed out "backwards"
    //
    freeOffset += size;
    if (freeOffset > endOffset)
      fail("bootimage full (need " + size + " more bytes)");
    int imageOffset = freeOffset - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;

    //
    // set .status field
    // - for reference counting gc, install large reference count
    // - for generational gc, turn on barrier bit for boot (== "old") object
    //
    if (VM.BuildForConcurrentGC) // RCGC
      setFullWord(imageOffset + OBJECT_REFCOUNT_OFFSET, BOOTIMAGE_REFCOUNT);
    else
      setFullWord(imageOffset + OBJECT_STATUS_OFFSET, OBJECT_BARRIER_MASK);

    return imageOffset;
  }

  /**
   * Allocate an array object.
   *
   * @param size object's size, in bytes (including object header)
   * @param numElements number of elements
   * @return offset of object within bootimage, in bytes
   */
  public static int allocateArray(int size, int numElements) {
    numObjects += 1;

    //
    // allocate space, noting that arrays are layed out "forwards"
    //
    int imageOffset = freeOffset - OBJECT_HEADER_OFFSET;

    // preserve future alignments on word boundaries
    freeOffset += ((size + 3) & ~3);
    if (freeOffset > endOffset)
      fail("bootimage full (need " + size + " more bytes)");

    //
    // set .status field
    // - for reference counting gc, install large reference count
    // - for generational gc, turn on barrier bit for boot (== "old") object
    //
    if (VM.BuildForConcurrentGC) // RCGC
      setFullWord(imageOffset + OBJECT_REFCOUNT_OFFSET, BOOTIMAGE_REFCOUNT);
    else
      setFullWord(imageOffset + OBJECT_STATUS_OFFSET, OBJECT_BARRIER_MASK);

    //
    // set .length field
    //
    setFullWord(imageOffset + ARRAY_LENGTH_OFFSET, numElements);

    return imageOffset;
  }

  /**
   * Fill in 1 byte of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public static void setByte(int offset, int value) {
    bootImage[offset] = (byte) value;
  }

  /**
   * Fill in 2 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public static void setHalfWord(int offset, int value) {
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
  public static void setFullWord(int offset, int value) {
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
  public static void setAddressWord(int offset, int value) {
    setFullWord(offset, value);
    numAddresses += 1;
  }

  /**
   * Fill in 4 bytes of bootimage, as null object reference.
   *
   * @param offset offset of target from start of image, in bytes
   */
  public static void setNullAddressWord(int offset) {
    setAddressWord(offset, 0);
    numNulledReferences += 1;
  }

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param offset offset of target from start of image, in bytes
   * @param value value to write
   */
  public static void setDoubleWord(int offset, long value) {
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
  public static void countNulledReference() {
    numNulledReferences += 1;
  }
}

