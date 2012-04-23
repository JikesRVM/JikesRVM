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
package org.jikesrvm.tools.bootImageWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel.MapMode;

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.mm.mmtk.ScanBootImage;
import org.jikesrvm.objectmodel.BootImageInterface;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Memory image of virtual machine that will be written to disk file and later
 * "booted".
 */
public class BootImage extends BootImageWriterMessages
  implements BootImageWriterConstants, BootImageInterface, SizeConstants {

  /**
   * Talk while we work?
   */
  private final boolean trace;

  /**
   * The data portion of the actual boot image
   */
  private final ByteBuffer bootImageData;

  /**
   * The code portion of the actual boot image
   */
  private final ByteBuffer bootImageCode;

  /**
   * The reference map for the boot image
   */
  private final byte[] referenceMap;
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

  /**
   * Data output file
   */
  private final RandomAccessFile dataOut;

  /**
   * Code output file
   */
  private final RandomAccessFile codeOut;


  /**
   * Code map file name
   */
  private final String imageCodeFileName;

  /**
   * Data map file name
   */
  private final String imageDataFileName;

  /**
   * Root map file name
   */
  private final String imageRMapFileName;

  /**
   * Use mapped byte buffers? We need to truncate the byte buffer
   * before writing it to disk. This operation is support on UNIX but
   * not Windows.
   */
  private static final boolean mapByteBuffers = false;

  /**
   * @param ltlEndian write words low-byte first?
   * @param t turn tracing on?
   */
  BootImage(boolean ltlEndian, boolean t, String imageCodeFileName, String imageDataFileName, String imageRMapFileName) throws IOException {
    this.imageCodeFileName = imageCodeFileName;
    this.imageDataFileName = imageDataFileName;
    this.imageRMapFileName = imageRMapFileName;
    dataOut = new RandomAccessFile(imageDataFileName,"rw");
    codeOut = new RandomAccessFile(imageCodeFileName,"rw");
    if (mapByteBuffers) {
      bootImageData = dataOut.getChannel().map(MapMode.READ_WRITE, 0, BOOT_IMAGE_DATA_SIZE);
      bootImageCode = codeOut.getChannel().map(MapMode.READ_WRITE, 0, BOOT_IMAGE_CODE_SIZE);
    } else {
      bootImageData = ByteBuffer.allocate(BOOT_IMAGE_DATA_SIZE);
      bootImageCode = ByteBuffer.allocate(BOOT_IMAGE_CODE_SIZE);
    }
    ByteOrder endian = ltlEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    bootImageData.order(endian);
    bootImageCode.order(endian);
    referenceMap = new byte[BOOT_IMAGE_DATA_SIZE >> LOG_BYTES_IN_ADDRESS];
    trace = t;
  }

  /**
   * Write boot image to disk.
   */
  public void write() throws IOException {
    if (trace) {
      say((numObjects / 1024)   + "k objects");
      say((numAddresses / 1024) + "k non-null object references");
      say(numNulledReferences + " references nulled because they are "+
          "non-jdk fields or point to non-bootimage objects");
      say(((Statics.getNumberOfReferenceSlots()+ Statics.getNumberOfNumericSlots()) / 1024) + "k jtoc slots");
      say((getDataSize() / 1024) + "k data in image");
      say((getCodeSize() / 1024) + "k code in image");
      say("writing " + imageDataFileName);
    }
    if (!mapByteBuffers) {
      dataOut.write(bootImageData.array(), 0, getDataSize());
    } else {
      dataOut.getChannel().truncate(getDataSize());
    }
    dataOut.close();

    if (trace) {
      say("writing " + imageCodeFileName);
    }
    if (!mapByteBuffers) {
      codeOut.write(bootImageCode.array(), 0, getCodeSize());
    } else {
      codeOut.getChannel().truncate(getCodeSize());
    }
    codeOut.close();

    if (trace) {
      say("writing " + imageRMapFileName);
    }

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
   * @param klass RVMClass object of scalar being allocated
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @return address of object within bootimage
   */
  public Address allocateScalar(RVMClass klass, boolean needsIdentityHash, int identityHashValue) {
    numObjects++;
    BootImageWriter.logAllocation(klass, klass.getInstanceSize());
    return ObjectModel.allocateScalar(this, klass, needsIdentityHash, identityHashValue);
  }

  /**
   * Allocate an array object.
   *
   * @param array RVMArray object of array being allocated.
   * @param numElements number of elements
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @param alignment special alignment value
   * @param alignCode Alignment-encoded value (AlignmentEncoding.ALIGN_CODE_NONE for none)
   * @return address of object within bootimage
   */
  public Address allocateArray(RVMArray array, int numElements, boolean needsIdentityHash, int identityHashValue, int alignCode) {
    numObjects++;
    BootImageWriter.logAllocation(array, array.getInstanceSize(numElements));
    return ObjectModel.allocateArray(this, array, numElements, needsIdentityHash, identityHashValue, alignCode);
  }

  /**
   * Allocate an array object.
   *
   * @param array RVMArray object of array being allocated.
   * @param numElements number of elements
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @param alignment special alignment value
   * @param alignCode Alignment-encoded value (AlignmentEncoding.ALIGN_CODE_NONE for none)
   * @return address of object within bootimage
   */
  public Address allocateArray(RVMArray array, int numElements, boolean needsIdentityHash, int identityHashValue, int align, int alignCode) {
    numObjects++;
    BootImageWriter.logAllocation(array, array.getInstanceSize(numElements));
    return ObjectModel.allocateArray(this, array, numElements, needsIdentityHash, identityHashValue, align, alignCode);
  }

  /**
   * Allocate an array object.
   *
   * @param array RVMArray object of array being allocated.
   * @param numElements number of elements
   * @return address of object within bootimage
   */
  public Address allocateCode(RVMArray array, int numElements) {
    numObjects++;
    BootImageWriter.logAllocation(array, array.getInstanceSize(numElements));
    return ObjectModel.allocateCode(this, array, numElements);
  }

  /**
   * Allocate space in bootimage. Moral equivalent of
   * memory managers allocating raw storage at runtime.
   *
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  @Override
  public Address allocateDataStorage(int size, int align, int offset) {
    size = roundAllocationSize(size);
    Offset unalignedOffset = freeDataOffset;
    freeDataOffset = MemoryManager.alignAllocation(freeDataOffset, align, offset);
    if (VM.ExtremeAssertions) {
      VM._assert(freeDataOffset.plus(offset).toWord().and(Word.fromIntSignExtend(align -1)).isZero());
      VM._assert(freeDataOffset.toWord().and(Word.fromIntSignExtend(3)).isZero());
    }
    Offset lowAddr = freeDataOffset;
    freeDataOffset = freeDataOffset.plus(size);
    if (freeDataOffset.sGT(Offset.fromIntZeroExtend(BOOT_IMAGE_DATA_SIZE)))
      fail("bootimage full (need at least " + size + " more bytes for data)");

    ObjectModel.fillAlignmentGap(this, BOOT_IMAGE_DATA_START.plus(unalignedOffset),
                                    lowAddr.minus(unalignedOffset).toWord().toExtent());
    return BOOT_IMAGE_DATA_START.plus(lowAddr);
  }

  /**
   * Round a size in bytes up to the next value of MIN_ALIGNMENT
   */
  private int roundAllocationSize(int size) {
    return size + ((-size) & ((1 << JavaHeader.LOG_MIN_ALIGNMENT) - 1));
  }

  /**
   * Allocate space in bootimage. Moral equivalent of
   * memory managers allocating raw storage at runtime.
   *
   * @param size the number of bytes to allocate
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   */
  @Override
  public Address allocateCodeStorage(int size, int align, int offset) {
    size = roundAllocationSize(size);
    Offset unalignedOffset = freeCodeOffset;
    freeCodeOffset = MemoryManager.alignAllocation(freeCodeOffset, align, offset);
    if (VM.ExtremeAssertions) {
      VM._assert(freeCodeOffset.plus(offset).toWord().and(Word.fromIntSignExtend(align -1)).isZero());
      VM._assert(freeCodeOffset.toWord().and(Word.fromIntSignExtend(3)).isZero());
    }
    Offset lowAddr = freeCodeOffset;
    freeCodeOffset = freeCodeOffset.plus(size);
    if (freeCodeOffset.sGT(Offset.fromIntZeroExtend(BOOT_IMAGE_CODE_SIZE)))
      fail("bootimage full (need at least " + size + " more bytes for code)");

    ObjectModel.fillAlignmentGap(this, BOOT_IMAGE_CODE_START.plus(unalignedOffset),
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
  @Override
  public void setByte(Address address, int value) {
    int idx;
    ByteBuffer data;
    if (address.GE(BOOT_IMAGE_CODE_START) && address.LE(BOOT_IMAGE_CODE_END)) {
      idx = address.diff(BOOT_IMAGE_CODE_START).toInt();
      data = bootImageCode;
    } else {
      idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
      data = bootImageData;
    }
    data.put(idx, (byte)value);
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
  @Override
  public void setHalfWord(Address address, int value) {
    int idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
    bootImageData.putChar(idx, (char)value);
  }

  /**
   * Fill in 4 bytes of bootimage, as numeric.
   *
   * @param address address of target
   * @param value value to write
   */
  @Override
  public void setFullWord(Address address, int value) {
    int idx;
    ByteBuffer data;
    if (address.GE(BOOT_IMAGE_CODE_START) && address.LE(BOOT_IMAGE_CODE_END)) {
      idx = address.diff(BOOT_IMAGE_CODE_START).toInt();
      data = bootImageCode;
    } else {
      idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
      data = bootImageData;
    }
    data.putInt(idx, value);
  }

  /**
   * Fill in 4/8 bytes of bootimage, as object reference.
   * @param address address of target
   * @param value value to write
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   * @param root Does this slot contain a possible reference into the heap?
   * (objField must also be true)
   */
  @Override
  public void setAddressWord(Address address, Word value, boolean objField, boolean root) {
    if (VM.VerifyAssertions) VM._assert(!root || objField);
    if (objField) value = MemoryManager.bootTimeWriteBarrier(value);
    if (root) markReferenceMap(address);
    if (VM.BuildFor32Addr)
      setFullWord(address, value.toInt());
    else
      setDoubleWord(address, value.toLong());
    numAddresses++;
  }

  /**
   * Fill in 4/8 bytes of bootimage, as null object reference.
   *
   * @param address address of target
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   * @param root Does this slot contain a possible reference into the heap? (objField must also be true)
   * @param genuineNull true if the value is a genuine null and
   * shouldn't be counted as a blanked field
   */
  public void setNullAddressWord(Address address, boolean objField, boolean root, boolean genuineNull) {
    setAddressWord(address, Word.zero(), objField, root);
    if (!genuineNull)
      numNulledReferences += 1;
  }

  /**
   * Fill in 4/8 bytes of bootimage, as null object reference.
   * @param address address of target
   * @param objField true if this word is an object field (as opposed
   * to a static, or tib, or some other metadata)
   * @param root Does this slot contain a possible reference into the heap? (objField must also be true)
   */
  @Override
  public void setNullAddressWord(Address address, boolean objField, boolean root) {
    setNullAddressWord(address, objField, root, true);
  }

  /**
   * Fill in 8 bytes of bootimage.
   *
   * @param address address of target
   * @param value value to write
   */
  @Override
  public void setDoubleWord(Address address, long value) {
    int idx;
    ByteBuffer data;
    if (address.GE(BOOT_IMAGE_CODE_START) && address.LE(BOOT_IMAGE_CODE_END)) {
      idx = address.diff(BOOT_IMAGE_CODE_START).toInt();
      data = bootImageCode;
    } else {
      idx = address.diff(BOOT_IMAGE_DATA_START).toInt();
      data = bootImageData;
    }
    data.putLong(idx, value);
  }

  /**
   * Keep track of how many references were set null because they pointed to
   * non-bootimage objects.
   */
  public void countNulledReference() {
    numNulledReferences += 1;
  }
}
