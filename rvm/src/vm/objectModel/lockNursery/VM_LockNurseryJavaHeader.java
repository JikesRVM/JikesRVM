/*
 * (C) Copyright IBM Corp. 2002
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_AllocatorHeader;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

/**
 * Defines shared support for one-word headers in the JikesRVM object
 * model. <p> 
 * This object model uses a one-word header for scalar
 * objects.  If a class is synchronized, then a thin lock word is
 * allocated as an "instance field" of the class.
 * The TIB word holds some identifier of the TIB, which varies by
 * object model. <p>
 *
 * Locking either occurs using the thin lock allocated in the object
 * if the class has synchronized methods or through the lock nursery
 * for instances of other classes. <p>
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove 
 */
public class VM_LockNurseryJavaHeader implements VM_Uninterruptible, 
                                                 VM_JavaHeaderConstants,
                                                 VM_Constants {

  private static final int OTHER_HEADER_BYTES = VM_AllocatorHeader.NUM_BYTES_HEADER + VM_MiscHeader.NUM_BYTES_HEADER;
  private static final int SCALAR_HEADER_SIZE = OTHER_HEADER_BYTES + 4; // 1 word for TIB encoding
  private static final int ARRAY_HEADER_SIZE  = SCALAR_HEADER_SIZE + 4; // 1 word for array length

  private static final int SCALAR_PADDING_BYTES = 4;

  protected static final int TIB_OFFSET   = -8;
  private static final int AVAILABLE_BITS_OFFSET = VM.LittleEndian ? (TIB_OFFSET) : (TIB_OFFSET + 3);

  /** How many bits are allocated to a thin lock? */
  public static final int NUM_THIN_LOCK_BITS = 32;
  /** How many bits to shift to get the thin lock? */
  public static final int THIN_LOCK_SHIFT    = 0;

  /**
   * How many bits are used to encode the hash code state?
   */
  protected static final boolean MOVES_OBJECTS = MM_Interface.MOVES_OBJECTS;
  protected static final int HASH_STATE_BITS = MOVES_OBJECTS ? 2 : 0;
  protected static final int HASH_STATE_MASK = MOVES_OBJECTS ? (HASH_STATE_UNHASHED | HASH_STATE_HASHED | HASH_STATE_HASHED_AND_MOVED) : 0;
  protected static final int HASHCODE_BYTES  = MOVES_OBJECTS ? 4 : 0;
  protected static final int HASHCODE_SCALAR_OFFSET = -4; // in "phantom word"
  protected static final int HASHCODE_ARRAY_OFFSET = JAVA_HEADER_END - OTHER_HEADER_BYTES - 4; // to left of header
  
  /**
   * How small is the minimum object header size? 
   * Used to pick chunk sizes for mark-sweep based collectors.
   */
  public static final int MINIMUM_HEADER_SIZE = SCALAR_HEADER_SIZE;

  /**
   * What is the offset of the 'last' byte in the class?
   * For use by VM_ObjectModel.layoutInstanceFields
   */
  public static int objectEndOffset(VM_Class klass) {
    return - klass.getInstanceSizeInternal() - SCALAR_PADDING_BYTES;
  }


  /**
   * Non-atomic read of word containing available bits
   */
  public static int readAvailableBitsWord(Object o) {
    return VM_Magic.getIntAtOffset(o, TIB_OFFSET);
  }

  /**
   * Non-atomic read of byte containing available bits
   */
  public static byte readAvailableBitsByte(Object o) {
    return VM_Magic.getByteAtOffset(o, AVAILABLE_BITS_OFFSET);
  }

  /**
   * Non-atomic write of word containing available bits
   */
  public static void writeAvailableBitsWord(Object o, int val) {
    VM_Magic.setIntAtOffset(o, TIB_OFFSET, val);
  }

  /**
   * Non-atomic write of byte containing available bits
   */
  public static void writeAvailableBitsByte(Object o, byte val) {
    VM_Magic.setByteAtOffset(o, AVAILABLE_BITS_OFFSET, val);
  }

  /**
   * Return true if argument bit is 1, false if it is 0
   */
  public static boolean testAvailableBit(Object o, int idx) {
    return ((1 << idx) & VM_Magic.getIntAtOffset(o, TIB_OFFSET)) != 0;
  }

  /**
   * Set argument bit to 1 if value is true, 0 if value is false
   */
  public static void setAvailableBit(Object o, int idx, boolean flag) {
    int tibWord = VM_Magic.getIntAtOffset(o, TIB_OFFSET);
    if (flag) {
      VM_Magic.setIntAtOffset(o, TIB_OFFSET, tibWord| (1 << idx));
    } else {
      VM_Magic.setIntAtOffset(o, TIB_OFFSET, tibWord & ~(1 << idx));
    }
  }

  /**
   * Freeze the other bits in the byte containing the available bits
   * so that it is safe to update them using setAvailableBits.
   *
   * Should be a no-op, since the TIB is frozen.
   */
  public static void initializeAvailableByte(Object o) {
  }

  /**
   * A prepare on the word containing the available bits
   */
  public static int prepareAvailableBits(Object o) {
    return VM_Magic.prepareInt(o, TIB_OFFSET);
  }
  
  /**
   * An attempt on the word containing the available bits
   */
  public static boolean attemptAvailableBits(Object o, int oldVal, int newVal) {
    return VM_Magic.attemptInt(o, TIB_OFFSET, oldVal, newVal);
  }
  
  /**
   * Given a reference, return an address which is guaranteed to be inside
   * the memory region allocated to the object.
   */
  public static VM_Address getPointerInMemoryRegion(VM_Address ref) {
    return ref.add(TIB_OFFSET);
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeScalarHeader(VM_Address ptr, Object[] tib, int size) {
    // (TIB set by VM_ObjectModel)
    return VM_Magic.addressAsObject(ptr.add(size + SCALAR_PADDING_BYTES));
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static int initializeScalarHeader(BootImageInterface bootImage, int ptr, 
                                           Object[] tib, int size) {
    return ptr + size + SCALAR_PADDING_BYTES;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeArrayHeader(VM_Address ptr, Object[] tib, int size) {
    // (TIB and array length set by VM_ObjectModel)
    return VM_Magic.addressAsObject(ptr.add(ARRAY_HEADER_SIZE));
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static int initializeArrayHeader(BootImageInterface bootImage ,int ptr, 
                                           Object[] tib, int size) {
    // (TIB set by BootImageWriter2; array length set by VM_ObjectModel)
    return ptr + ARRAY_HEADER_SIZE;
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) {
    // TIB dumped in VM_ObjectModel
    int lockOffset = getThinLockOffset(ref);
    if (lockOffset != -1) {
      VM.sysWrite(" THIN LOCK=");
      VM.sysWriteHex(VM_Magic.getIntAtOffset(ref, lockOffset));
    }
  }

  /**
   * Given the smallest base address in a region, return the smallest
   * object reference that could refer to an object in the region.
   */
  public static VM_Address minimumObjectRef (VM_Address regionBaseAddr) {
    return regionBaseAddr.add(ARRAY_HEADER_SIZE);
  }

  /**
   * Given the largest base address in a region, return the largest
   * object reference that could refer to an object in the region.
   */
  public static VM_Address maximumObjectRef (VM_Address regionHighAddr) {
    return regionHighAddr.add(SCALAR_PADDING_BYTES);
  }

  /**
   * Compute the header size of an instance of the given type.
   */
  public static int computeScalarHeaderSize(VM_Class type) {
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Compute the header size of an instance of the given type.
   */
  public static int computeArrayHeaderSize(VM_Array type) {
    return ARRAY_HEADER_SIZE;
  }


  /**
   * Get the hash code of an object.
   */
  public static int getObjectHashCode(Object o) { 
    if (MOVES_OBJECTS) {
      int hashState = VM_Magic.getIntAtOffset(o, TIB_OFFSET) & HASH_STATE_MASK;
      if (hashState == HASH_STATE_HASHED) {
        return VM_Magic.objectAsAddress(o).toInt() >>> 2;
      } else if (hashState == HASH_STATE_HASHED_AND_MOVED) {
        VM_Type t = VM_Magic.getObjectType(o);
        if (t.isArrayType()) {
          return VM_Magic.getIntAtOffset(o, HASHCODE_ARRAY_OFFSET) >>> 2;
        } else {
          return VM_Magic.getIntAtOffset(o, HASHCODE_SCALAR_OFFSET) >>> 2;
        }
      } else {
        int tmp;
        do {
          tmp = VM_Magic.prepareInt(o, TIB_OFFSET);
        } while (!VM_Magic.attemptInt(o, TIB_OFFSET, tmp, tmp | HASH_STATE_HASHED));
        if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition1++;
        return getObjectHashCode(o);
      }
    } else {
      return VM_Magic.objectAsAddress(o).toInt() >>> 2;
    }
  }

  /**
   * how many bytes are needed when the scalar object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Class type) {
    int size = type.getInstanceSize();
    int hashState = VM_Magic.getIntAtOffset(fromObj, TIB_OFFSET) & HASH_STATE_MASK;
    if (hashState != HASH_STATE_UNHASHED) {
      size += HASHCODE_BYTES;
    }
    return size;
  }

  /**
   * how many bytes are needed when the array object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Array type, int numElements) {
    int size = VM_Memory.alignUp(type.getInstanceSize(numElements), 4);
    int hashState = VM_Magic.getIntAtOffset(fromObj, TIB_OFFSET) & HASH_STATE_MASK;
    if (hashState != HASH_STATE_UNHASHED) {
      size += HASHCODE_BYTES;
    }
    return size;
  }
  
  /**
   * Copy an object to the given raw storage address
   */
  public static Object moveObject(VM_Address toAddress, Object fromObj, int numBytes, 
                                  VM_Class type, int tibWord) {
    int hashState = tibWord & HASH_STATE_MASK;
    if (hashState == HASH_STATE_UNHASHED) {
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(numBytes + SCALAR_PADDING_BYTES);
      VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
      Object toObj = VM_Magic.addressAsObject(toAddress.add(numBytes + SCALAR_PADDING_BYTES));
      VM_Magic.setIntAtOffset(toObj, TIB_OFFSET, tibWord);
      return toObj;
    } else if (hashState == HASH_STATE_HASHED) {
      int data = numBytes - HASHCODE_BYTES;
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(data + SCALAR_PADDING_BYTES);
      VM_Memory.aligned32Copy(toAddress, fromAddress, data); 
      Object toObj = VM_Magic.addressAsObject(toAddress.add(data + SCALAR_PADDING_BYTES));
      VM_Magic.setIntAtOffset(toObj, HASHCODE_SCALAR_OFFSET, VM_Magic.objectAsAddress(fromObj).toInt());
      VM_Magic.setIntAtOffset(toObj, TIB_OFFSET, tibWord | HASH_STATE_HASHED_AND_MOVED);
      if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
      return toObj;
    } else { // HASHED_AND_MOVED; 'phanton word' contains hash code.
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(numBytes - HASHCODE_BYTES + SCALAR_PADDING_BYTES);
      VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
      Object toObj = VM_Magic.addressAsObject(toAddress.add(numBytes - HASHCODE_BYTES + SCALAR_PADDING_BYTES));
      VM_Magic.setIntAtOffset(toObj, TIB_OFFSET, tibWord);
      return toObj;
    }
  }

  /**
   * Copy an object to the given raw storage address
   */
  public static Object moveObject(VM_Address toAddress, Object fromObj, int numBytes, 
                                  VM_Array type, int tibWord) throws VM_PragmaInline {
    int hashState = tibWord & HASH_STATE_MASK;
    if (hashState == HASH_STATE_UNHASHED) {
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(ARRAY_HEADER_SIZE);
      VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
      Object toObj = VM_Magic.addressAsObject(toAddress.add(ARRAY_HEADER_SIZE));
      VM_Magic.setIntAtOffset(toObj, TIB_OFFSET, tibWord);
      return toObj;
    } else if (hashState == HASH_STATE_HASHED) {
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(ARRAY_HEADER_SIZE);
      VM_Memory.aligned32Copy(toAddress.add(HASHCODE_BYTES), fromAddress, numBytes - HASHCODE_BYTES); 
      Object toObj = VM_Magic.addressAsObject(toAddress.add(ARRAY_HEADER_SIZE + HASHCODE_BYTES));
      VM_Magic.setIntAtOffset(toObj, HASHCODE_ARRAY_OFFSET, VM_Magic.objectAsAddress(fromObj).toInt());
      VM_Magic.setIntAtOffset(toObj, TIB_OFFSET, tibWord | HASH_STATE_HASHED_AND_MOVED);
      if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
      return toObj;
    } else { // HASHED_AND_MOVED: hash code to 'left' of array header
      VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(ARRAY_HEADER_SIZE + HASHCODE_BYTES);
      VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
      Object toObj = VM_Magic.addressAsObject(toAddress.add(ARRAY_HEADER_SIZE + HASHCODE_BYTES));
      VM_Magic.setIntAtOffset(toObj, TIB_OFFSET, tibWord);
      return toObj;
    }
  }

  /**
   * Get the offset of the thin lock word in this object
   */
  public static int getThinLockOffset(Object o) {
      VM_Type cls = VM_Magic.getObjectType(o);
      return cls.getThinLockOffset();
  }

  /**
   * what is the default offset for a thin lock?
   */
  public static int defaultThinLockOffset() {
    return -1;
  }

  /**
   * Allocate a thin lock word for instances of the type
   * (if they already have one, then has no effect).
   */
  public static void allocateThinLock(VM_Type t) {
    if (t.getThinLockOffset() == -1) {
      if (VM.VerifyAssertions) VM._assert(t.isClassType());
      VM_Class klass = t.asClass();
      int fieldOffset = objectEndOffset(klass) - 4; // layout field backwards!
      klass.setThinLockOffset(fieldOffset);
      klass.increaseInstanceSize(4);
    }
  }

  /**
   * Generic lock
   */
  public static void genericLock(Object o) { 
    int lockOffset = getThinLockOffset(o);
    if (lockOffset != -1) {
      VM_ThinLock.lock(o, lockOffset);
    } else {
      VM_LockNursery.lock(o);
    }
  }

  /**
   * Generic unlock
   */
  public static void genericUnlock(Object o) {
    int lockOffset = getThinLockOffset(o);
    if (lockOffset != -1) {
      VM_ThinLock.unlock(o, lockOffset);
    } else {
      VM_LockNursery.unlock(o);
    }
  }

  /**
   * Obtains the heavy-weight lock, if there is one, associated with the
   * indicated object.  Returns <code>null</code>, if there is no
   * heavy-weight lock associated with the object.
   *
   * @param o the object from which a lock is desired
   * @param create if true, create heavy lock if none found
   * @return the heavy-weight lock on the object (if any)
   */
  public static VM_Lock getHeavyLock(Object o, boolean create) {
    int lockOffset = getThinLockOffset(o);
    if (lockOffset != -1) {
      return VM_ThinLock.getHeavyLock(o, lockOffset, create);
    } else {
      return VM_LockNursery.findOrCreate(o, create);
    }
  }
}
