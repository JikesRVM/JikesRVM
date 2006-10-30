/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.FileOutputStream;
import java.io.IOException;
import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.classloader.*;
import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;
import com.ibm.jikesrvm.mm.mmtk.ScanBootImage;

import org.vmmagic.unboxed.*;

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
   * The data portion of the actual boot image
   */
  private byte[] bootImageData;

  /**
   * The code portion of the actual boot image
   */
  private byte[] bootImageCode;

  /**
   * The reference map for the boot image
   */
  private byte[] referenceMap;
  private int referenceMapReferences = 0;
  private int referenceMapLimit = 0;
  private byte[] bootImageRMap;
  private int rMapSize = 0;

  /**
   * Offset of next free data word, in bytes
   */
  private Offset freeDataOffset = Offset.zero();

  /**
   * Offset of next free code word, in bytes
   */
  private Offset freeCodeOffset = Offset.zero();

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

  private int markedReferences;

  /**
   * @param ltlEndian write words low-byte first?
   * @param t turn tracing on?
   */
  BootImage(boolean ltlEndian, boolean t) {
    bootImageData = new byte[BOOT_IMAGE_DATA_SIZE];
    bootImageCode = new byte[BOOT_IMAGE_CODE_SIZE];
    referenceMap = new byte[BOOT_IMAGE_DATA_SIZE>>LOG_BYTES_IN_ADDRESS];
    littleEndian = ltlEndian;
    trace = t;
  }

  /**
   * Write boot image to disk.
   *
   * @param imageFileName the name of the image file
   */
  public void write(String imageCodeFileName, String imageDataFileName, String imageRMapFileName) throws IOException {
    if (trace) {
      say((numObjects / 1024)   + "k objects");
      say((numAddresses / 1024) + "k non-null object references");
      say(numNulledReferences + " references nulled because they are "+
          "non-jdk fields or point to non-bootimage objects");
      say((VM_Statics.getNumberOfSlots() / 1024) + "k jtoc slots");
      say((getDataSize() / 1024) + "k data in image");
      say((getCodeSize() / 1024) + "k code in image");
      say("writing " + imageDataFileName);
    }
    FileOutputStream dataOut = new FileOutputStream(imageDataFileName);
    dataOut.write(bootImageData, 0, getDataSize());
    dataOut.flush();
    dataOut.close();
    if (trace) {
      say("writing " + imageCodeFileName);
    }
    FileOutputStream codeOut = new FileOutputStream(imageCodeFileName);
    codeOut.write(bootImageCode, 0, getCodeSize());
    codeOut.flush();
    codeOut.close();
    if (trace) {
      say("writing " + imageRMapFileName);
    }

    // Kludge around problems with older (pre 5.0) IBM JVMs being unable
    // compact, thus failing to allocate large objects after the heap becomes
    // fragmented.
    bootImageData = null;
    bootImageCode = null;
    System.gc();
    // end IBM JVM kludge
    
    /* Now we generate a compressed reference map.  Typically we get 4 bits/address, but
       we'll create the in-memory array assuming worst case 1:1 compression.  Only the
       used portion of the array actually gets written into the image. */
    bootImageRMap = new byte[referenceMapReferences<<LOG_BYTES_IN_WORD];
    rMapSize = ScanBootImage.encodeRMap(bootImageRMap, referenceMap, referenceMapLimit);
    FileOutputStream rmapOut = new FileOutputStream(imageRMapFileName);
    rmapOut.write(bootImageRMap, 0, rMapSize);
    rmapOut.flush();
    rmapOut.close();
    if (trace) {
      say("total refs: "+ referenceMapReferences);
    }
    ScanBootImage.encodingStats();
  }

  /**
   * Get image data size, in bytes.
   * @return image size
   */
  public int getDataSize() {
    return freeDataOffset.toInt();
  }

  /**
   * Get image code size, in bytes.
   * @return image size
   */
  public int getCodeSize() {
    return freeCodeOffset.toInt();
  }


  /**
   * return the size of the rmap
   */
  public int getRMapSize() {
    return rMapSize;
  }

  /**
   * Allocate a scalar object.
   *
   * @param klass VM_Class object of scalar being allocated
   * @return address of object within bootimage
   */
  public Address allocateScalar(VM_Class klass) {
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
   * @return address of object within bootimage
   */
  public Address allocateArray(VM_Array array, int numElements) {
    numObjects++;
    array.bootCount++;
    array.bootBytes += array.getInstanceSize(numElements);
    return VM_ObjectModel.allocateArray(this, array, numElements);
  }

  /**
   * Allocate an array object.
   *
   * @param array VM_Array object of array being allocated.
   * @param numElements number of elements
   * @return address of object within bootimage
   */
  public Address allocateCode(VM_Array array, int numElements) {
    numObjects++;
    array.bootCount++;
    array.bootBytes += array.getInstanceSize(numElements);
    return VM_ObjectModel.allocateCode(this, array, numElements);
  }

  /**
   * Allocate space in bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   *
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  public Address allocateDataStorage(int size, int align, int offset) {
    size = roundAllocationSize(size);
    Offset unalignedOffset = freeDataOffset;
    freeDataOffset = MM_Interface.alignAllocation(freeDataOffset, align, offset);
    if (VM.ExtremeAssertions) {
      VM._assert(freeDataOffset.plus(offset).toWord().and(Word.fromIntSignExtend(align -1)).isZero()); 
      VM._assert(freeDataOffset.toWord().and(Word.fromIntSignExtend(3)).isZero());
    }
    Offset lowAddr = freeDataOffset;
    freeDataOffset = freeDataOffset.plus(size);
    if (freeDataOffset.sGT(Offset.fromIntZeroExtend(BOOT_IMAGE_DATA_SIZE)))
      fail("bootimage full (need at least " + size + " more bytes for data)");

    VM_ObjectModel.fillAlignmentGap(this, BOOT_IMAGE_DATA_START.plus(unalignedOffset), 
                                    lowAddr.minus(unalignedOffset).toWord().toExtent());
    return BOOT_IMAGE_DATA_START.plus(lowAddr);
  }

  /**
   * Round a size in bytes up to the next value of MIN_ALIGNMENT 
   */
  private int roundAllocationSize(int size) {
    return size + ((-size) & ((1 << VM_JavaHeader.LOG_MIN_ALIGNMENT) - 1));
  } 
  
  /**
   * Allocate space in bootimage. Moral equivalent of 
   * memory managers allocating raw storage at runtime.
   *
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  public Address allocateCodeStorage(int size, int align, int offset) {
    size = roundAllocationSize(size);
    Offset unalignedOffset = freeCodeOffset;
    freeCodeOffset = MM_Interface.alignAllocation(freeCodeOffset, align, offset);
    if (VM.ExtremeAssertions) {
      VM._assert(freeCodeOffset.plus(offset).toWord().and(Word.fromIntSignExtend(align -1)).isZero()); 
      VM._assert(freeCodeOffset.toWord().and(Word.fromIntSignExtend(3)).isZero());
    }
    Offset lowAddr = freeCodeOffset;
    freeCodeOffset = freeCodeOffset.plus(size);
    if (freeCodeOffset.sGT(Offset.fromIntZeroExtend(BOOT_IMAGE_CODE_SIZE)))
      fail("bootimage full (need at least " + size + " more bytes for data)");

    VM_ObjectModel.fillAlignmentGap(this, BOOT_IMAGE_CODE_START.plus(unalignedOffset), 
                                    lowAddr.minus(unalignedOffset).toWord().toExtent());
    
    return BOOT_IMAGE_CODE_START.plus(lowAddr);
  }

  /**
   * Reset the allocator as if no allocation had occured.  This is
   * useful to allow a "trial run", as is done to establish the offset
   * of the JTOC for the entry in the boot image record---so its
   * actual address can be computed early in the build process.
   */
  public void resetAllocator() {
    freeDataOffset = Offset.zero();
    freeCodeOffset = Offset.zero();
  }

  /**
   * Fill in 1 byte of bootimage.
   *
   * @param address address of target
   * @param value value to write
   */
  public void setByte(Address address, int value) {
    int idx;
    byte[] data;
    if (address.GE(BOOT_IMAGE_CODE_START) && address.LE(BOOT_IMAGE_CODE_END)) {
      idx = address.diff(BOOT_IMAGE_CODE_START).toInt();
      data = bootImageCode;
    } else {
      idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
      data = bootImageData;
    }
    data[idx] = (byte) value;
  }


  /**
   * Set a byte in the reference bytemap to indicate that there is an
   * address in the boot image at this offset.  This can be used for
   * relocatability and for fast boot image scanning at GC time.
   *
   * @param address The offset into the boot image which contains an
   * address.
   */
  private void markReferenceMap(Address address) {
    int referenceIndex = address.diff(BOOT_IMAGE_DATA_START).toInt()>>LOG_BYTES_IN_ADDRESS;
    if (referenceMap[referenceIndex] == 0) {
      referenceMap[referenceIndex] = 1;
      referenceMapReferences++;
      if (referenceIndex > referenceMapLimit) referenceMapLimit = referenceIndex;
    }
  }

  /**
   * Fill in 2 bytes of bootimage.
   *
   * @param address address of target
   * @param value value to write
   */
  public void setHalfWord(Address address, int value) {
    int idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
    if (littleEndian) {
      bootImageData[idx++] = (byte) (value);
      bootImageData[idx  ] = (byte) (value >>  8);
    } else {
      bootImageData[idx++] = (byte) (value >>  8);
      bootImageData[idx  ] = (byte) (value);
    }
  }

  /**
   * Fill in 4 bytes of bootimage, as numeric.
   *
   * @param address address of target
   * @param value value to write
   */
  public void setFullWord(Address address, int value) {
    int idx;
    byte[] data;
    if (address.GE(BOOT_IMAGE_CODE_START) && address.LE(BOOT_IMAGE_CODE_END)) {
      idx = address.diff(BOOT_IMAGE_CODE_START).toInt();
      data = bootImageCode;
    } else {
      idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
      data = bootImageData;
    }
    if (littleEndian) {
      data[idx++] = (byte) (value);
      data[idx++] = (byte) (value >>  8);
      data[idx++] = (byte) (value >> 16);
      data[idx  ] = (byte) (value >> 24);
    } else {
      data[idx++] = (byte) (value >> 24);
      data[idx++] = (byte) (value >> 16);
      data[idx++] = (byte) (value >>  8);
      data[idx  ] = (byte) (value);
    }
  }

  /**
   * Fill in 4/8 bytes of bootimage, as object reference.
   *
   * @param address address of target
   * @param value value to write
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   */
  public void setAddressWord(Address address, Word value, boolean objField) {
    if (objField) 
      markReferenceMap(address);
//-#if RVM_FOR_32_ADDR
    setFullWord(address, value.toInt());
    numAddresses++;
//-#endif
//-#if RVM_FOR_64_ADDR
    setDoubleWord(address, value.toLong());
    numAddresses++;
//-#endif
  }

  /**
   * Fill in 4/8 bytes of bootimage, as null object reference.
   *
   * @param address address of target
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   * @param genuineNull true if the value is a genuine null and
   * shouldn't be counted as a blanked field
   */
  public void setNullAddressWord(Address address, boolean objField, boolean genuineNull) {
    setAddressWord(address, Word.zero(), objField);
    if (!genuineNull)
      numNulledReferences += 1;
  }

  /**
   * Fill in 4/8 bytes of bootimage, as null object reference.
   *
   * @param address address of target
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   */
  public void setNullAddressWord(Address address, boolean objField) {
    setNullAddressWord(address, objField, true);
  }

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param address address of target
   * @param value value to write
   */
  public void setDoubleWord(Address address, long value) {
    int idx;
    byte[] data;
    if (address.GE(BOOT_IMAGE_CODE_START) && address.LE(BOOT_IMAGE_CODE_END)) {
      idx = address.diff(BOOT_IMAGE_CODE_START).toInt();
      data = bootImageCode;
    } else {
      idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
      data = bootImageData;
    }
    if (littleEndian) {
      data[idx++] = (byte) (value);
      data[idx++] = (byte) (value >>  8);
      data[idx++] = (byte) (value >> 16);
      data[idx++] = (byte) (value >> 24);
      data[idx++] = (byte) (value >> 32);
      data[idx++] = (byte) (value >> 40);
      data[idx++] = (byte) (value >> 48);
      data[idx  ] = (byte) (value >> 56);
    } else {
      data[idx++] = (byte) (value >> 56);
      data[idx++] = (byte) (value >> 48);
      data[idx++] = (byte) (value >> 40);
      data[idx++] = (byte) (value >> 32);
      data[idx++] = (byte) (value >> 24);
      data[idx++] = (byte) (value >> 16);
      data[idx++] = (byte) (value >>  8);
      data[idx  ] = (byte) (value);
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
