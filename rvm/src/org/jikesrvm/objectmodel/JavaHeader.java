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
package org.jikesrvm.objectmodel;

import static org.jikesrvm.mm.mminterface.MemoryManagerConstants.GC_HEADER_BITS;
import static org.jikesrvm.mm.mminterface.MemoryManagerConstants.MOVES_OBJECTS;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.ADDRESS_BASED_HASHING;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.ALIGNMENT_MASK;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.ARRAY_LENGTH_BYTES;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.DYNAMIC_HASH_OFFSET;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.HASHCODE_BYTES;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.HASHCODE_OFFSET;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.HASH_STATE_HASHED;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.HASH_STATE_HASHED_AND_MOVED;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.HASH_STATE_MASK;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.HASH_STATE_UNHASHED;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.JAVA_HEADER_BYTES;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.JAVA_HEADER_OFFSET;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.NUM_AVAILABLE_BITS;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.OTHER_HEADER_BYTES;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.STATUS_BYTES;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.TIB_BYTES;
import static org.jikesrvm.objectmodel.MiscHeader.REQUESTED_BITS;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.scheduler.Lock;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.ThinLock;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Defines the JavaHeader portion of the object header for the
 * default JikesRVM object model.
 * The default object model uses a two word header. <p>
 *
 * One word holds a TIB pointer. <p>
 *
 * The other word ("status word") contains an inline thin lock,
 * either the hash code or hash code state, and a few unallocated
 * bits that can be used for other purposes.
 * If {@link JavaHeaderConstants#ADDRESS_BASED_HASHING} is false,
 * then to implement default hashcodes, Jikes RVM uses a 10 bit hash code
 * that is completely stored in the status word, which is laid out as
 * shown below:
 * <pre>
 *      TTTT TTTT TTTT TTTT TTTT HHHH HHHH HHAA
 * T = thin lock bits
 * H = hash code
 * A = available for use by GCHeader and/or MiscHeader.
 * </pre>
 *
 * If {@link JavaHeaderConstants#ADDRESS_BASED_HASHING ADDRESS_BASED_HASHING} is true,
 * then Jikes RVM uses two bits of the status word to record the hash code state in
 * a typical three state scheme ({@link JavaHeaderConstants#HASH_STATE_UNHASHED},
 * {@link JavaHeaderConstants#HASH_STATE_HASHED}, and
 * {@link JavaHeaderConstants#HASH_STATE_HASHED_AND_MOVED}). In this case, the status
 * word is laid out as shown below:
 * <pre>
 *      TTTT TTTT TTTT TTTT TTTT TTHH AAAA AAAA
 * T = thin lock bits
 * H = hash code state bits
 * A = available for use by GCHeader and/or MiscHeader.
 * </pre>
 */
@Uninterruptible
public class JavaHeader {

  protected static final int SCALAR_HEADER_SIZE = JAVA_HEADER_BYTES + OTHER_HEADER_BYTES;
  protected static final int ARRAY_HEADER_SIZE = SCALAR_HEADER_SIZE + ARRAY_LENGTH_BYTES;

  /** offset of object reference from the lowest memory word */
  public static final int OBJECT_REF_OFFSET = ARRAY_HEADER_SIZE;  // from start to ref
  protected static final Offset TIB_OFFSET = JAVA_HEADER_OFFSET;
  protected static final Offset STATUS_OFFSET = TIB_OFFSET.plus(TIB_BYTES);
  protected static final Offset AVAILABLE_BITS_OFFSET =
      VM.LittleEndian ? (STATUS_OFFSET) : (STATUS_OFFSET.plus(STATUS_BYTES - 1));

  /*
   * Used for 10 bit header hash code in header (!ADDRESS_BASED_HASHING)
   */
  protected static final int HASH_CODE_SHIFT = 2;
  protected static final Word HASH_CODE_MASK = Word.one().lsh(10).minus(Word.one()).lsh(HASH_CODE_SHIFT);
  protected static Word hashCodeGenerator; // seed for generating hash codes with copying collectors.

  /** How many bits are allocated to a thin lock? */
  public static final int NUM_THIN_LOCK_BITS = ADDRESS_BASED_HASHING ? 22 : 20;
  /** How many bits to shift to get the thin lock? */
  public static final int THIN_LOCK_SHIFT = ADDRESS_BASED_HASHING ? 10 : 12;
  /** How many bytes do we have to offset to get to the high locking bits */
  public static final int THIN_LOCK_DEDICATED_U16_OFFSET = VM.LittleEndian ? 2 : (VM.BuildFor64Addr ? 4 : 0);
  /** How many bits do we have to shift to only hold the high locking bits */
  public static final int THIN_LOCK_DEDICATED_U16_SHIFT  = 16;

  /** The alignment value **/
  public static final int ALIGNMENT_VALUE = JavaHeaderConstants.ALIGNMENT_VALUE;
  public static final int LOG_MIN_ALIGNMENT = JavaHeaderConstants.LOG_MIN_ALIGNMENT;

  static {
    if (VM.VerifyAssertions) {
      VM._assert(REQUESTED_BITS + GC_HEADER_BITS <= NUM_AVAILABLE_BITS);
      VM._assert((THIN_LOCK_SHIFT + NUM_THIN_LOCK_BITS - THIN_LOCK_DEDICATED_U16_SHIFT) == 16);
    }
  }

  /**
   * @return the TIB offset.
   */
  public static Offset getTibOffset() {
    return TIB_OFFSET;
  }

  /**
   * What is the first word after the class?
   *
   * @param obj the object in question
   * @param type the object's class
   * @return first word after the scalar object
   */
  public static Address getObjectEndAddress(Object obj, RVMClass type) {
    int size = type.getInstanceSize();
    if (ADDRESS_BASED_HASHING && DYNAMIC_HASH_OFFSET) {
      Word hashState = Magic.objectAsAddress(obj).loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
        size += HASHCODE_BYTES;
      }
    }
    return Magic.objectAsAddress(obj).plus(Memory.alignUp(size, BYTES_IN_INT) -
                                              OBJECT_REF_OFFSET);
  }

  /**
   * What is the first word after the array?
   *
   * @param obj the object in question
   * @param type the object's class
   * @param numElements the number of elements in the array
   * @return the first word after the array
   */
  public static Address getObjectEndAddress(Object obj, RVMArray type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (ADDRESS_BASED_HASHING && DYNAMIC_HASH_OFFSET) {
      Word hashState = Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
        size += HASHCODE_BYTES;
      }
    }
    return Magic.objectAsAddress(obj).plus(Memory.alignUp(size, BYTES_IN_INT) -
                                              OBJECT_REF_OFFSET);
  }

  /**
   * What is the offset of the first word of the class?
   *
   * @param klass unused
   * @return offset of the first word of the class from the object
   *  reference
   */
  public static int objectStartOffset(RVMClass klass) {
    return -OBJECT_REF_OFFSET;
  }

  /**
   * @return offset of the last word of the header from an
   *  out-to-in perspective
   */
  public static int getHeaderEndOffset() {
    return SCALAR_HEADER_SIZE - OBJECT_REF_OFFSET;
  }

  /**
   * How small is the minimum object header size?
   * Can be used to pick chunk sizes for allocators.
   *
   * @return the minimum object size
   */
  public static int minimumObjectSize() {
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Given a reference, return an address which is guaranteed to be inside
   * the memory region allocated to the object.
   *
   * @param ref an object reference
   * @return an address inside the object's memory
   */
  public static Address getPointerInMemoryRegion(ObjectReference ref) {
    return ref.toAddress().plus(TIB_OFFSET);
  }

  /**
   * @param o an object
   * @return the TIB for an object.
   */
  public static TIB getTIB(Object o) {
    return Magic.getTIBAtOffset(o, TIB_OFFSET);
  }

  /**
   * Sets the TIB for an object.
   *
   * @param ref the object
   * @param tib the TIB to set for the object
   */
  public static void setTIB(Object ref, TIB tib) {
    Magic.setObjectAtOffset(ref, TIB_OFFSET, tib);
  }

  /**
   * Sets the TIB for an object during bootimage writing.
   *
   * @param bootImage the bootimage
   * @param refOffset the object's address in the bootimage
   * @param tibAddr the TIB's address in the bootimage
   * @param type the object's type
   */
  @Interruptible
  public static void setTIB(BootImageInterface bootImage, Address refOffset, Address tibAddr, RVMType type) {
    bootImage.setAddressWord(refOffset.plus(TIB_OFFSET), tibAddr.toWord(), false, false);
  }

  /**
   * @param fromObj the object to copy
   * @param type the object's type
   * @return number of needed bytes when the scalar object is copied by GC
   */
  public static int bytesRequiredWhenCopied(Object fromObj, RVMClass type) {
    int size = type.getInstanceSize();
    if (ADDRESS_BASED_HASHING) {
      Word hashState = Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    return size;
  }

  /**
   * @param obj the object
   * @param type the object's type
   * @return number of bytes are used by the scalar object
   */
  public static int bytesUsed(Object obj, RVMClass type) {
    int size = type.getInstanceSize();
    if (MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING) {
        Word hashState = Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          size += HASHCODE_BYTES;
        }
      }
    }
    return size;
  }

  /**
   * how many bytes are needed when the array object is copied by GC?
   *
   * @param fromObj the object to copy
   * @param type the object's type
   * @param numElements the array length
   * @return the number of bytes that are required for the copy
   */
  public static int bytesRequiredWhenCopied(Object fromObj, RVMArray type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (ADDRESS_BASED_HASHING) {
      Word hashState = Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    return Memory.alignUp(size, BYTES_IN_INT);
  }

  /**
   * how many bytes are used by the array object?
   * @param obj the object to copy
   * @param type the object's type
   * @param numElements the array length
   * @return the number of bytes that the array uses
   */
  public static int bytesUsed(Object obj, RVMArray type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING) {
        Word hashState = Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          size += HASHCODE_BYTES;
        }
      }
    }
    return Memory.alignUp(size, BYTES_IN_INT);
  }

  /**
   * Maps from the object ref to the lowest address of the storage
   * associated with the object.
   *
   * @param obj the object reference
   * @return the lowest address in the object's memory region
   */
  @Inline
  public static Address objectStartRef(ObjectReference obj) {
    if (MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
        Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          return obj.toAddress().minus(OBJECT_REF_OFFSET + HASHCODE_BYTES);
        }
      }
    }
    return obj.toAddress().minus(OBJECT_REF_OFFSET);
  }

  /**
   * Get an object reference from the address the lowest word of the
   * object was allocated.  In general this required that we are using
   * a dynamic hash offset or not using address based
   * hashing. However, the GC algorithm could safely do this in the
   * nursery so we can't assert DYNAMIC_HASH_OFFSET.
   *
   * @param start the lowest word in the storage of an allocated object
   * @return the object reference for the object
   */
  public static ObjectReference getObjectFromStartAddress(Address start) {
    /* Skip over any alignment fill */
    while ((start.loadInt()) == ALIGNMENT_VALUE) {
      start = start.plus(BYTES_IN_INT);
    }
    return start.plus(OBJECT_REF_OFFSET).toObjectReference();
  }

  /**
   * Gets an object reference from the address the lowest word of the
   * object was allocated.
   *
   * @param start the lowest word in the storage of an allocated object
   * @return the object reference for the object
   */
  public static ObjectReference getScalarFromStartAddress(Address start) {
    return getObjectFromStartAddress(start);
  }

  /**
   * Gets an object reference from the address the lowest word of the
   * object was allocated.
   *
   * @param start the lowest word in the storage of an allocated object
   * @return the object reference for the object
   */
  public static ObjectReference getArrayFromStartAddress(Address start) {
    return getObjectFromStartAddress(start);
  }

  /**
   * Get the next object in the heap under contiguous
   * allocation. Handles alignment issues only when there are no GC or
   * Misc header words. In the case there are we probably have to ask
   * MemoryManager to distinguish this for us.
   *
   * @param obj the present object
   * @param size the object's size
   * @return the next object, provided that the constraints from above are
   *  met
   */
  protected static ObjectReference getNextObject(ObjectReference obj, int size) {
    if (VM.VerifyAssertions) VM._assert(OTHER_HEADER_BYTES == 0);

    return getObjectFromStartAddress(obj.toAddress().plus(size).minus(OBJECT_REF_OFFSET));
  }

  /**
   * Get the next object in the heap under contiguous
   * allocation. Handles alignment issues.
   *
   * @param obj the current object, which must be a scalar
   * @param type the object's type
   * @return the next scalar object in the heap
   */
  public static ObjectReference getNextObject(ObjectReference obj, RVMClass type) {
    return getObjectFromStartAddress(getObjectEndAddress(obj.toObject(), type));
  }

  /**
   * Get the next array in the heap under contiguous
   * allocation. Handles alignment issues.
   *
   * @param obj the current object, which must be an array
   * @param type the object's type
   * @param numElements the length of the array
   * @return the next scalar object in the heap
   */
  public static ObjectReference getNextObject(ObjectReference obj, RVMArray type, int numElements) {
    return getObjectFromStartAddress(getObjectEndAddress(obj.toObject(), type, numElements));
  }

  /**
   * Gets the reference of an array when copied to the specified region.
   *
   * @param obj the object to copy
   * @param to the target address for the copy
   * @param type the array's type
   * @return the reference of the copy
   */
  @Inline
  public static Object getReferenceWhenCopiedTo(Object obj, Address to, RVMArray type) {
    return getReferenceWhenCopiedTo(obj, to);
  }

  /**
   * Get the reference of a scalar when copied to the specified region.
   *
   * @param obj the object to copy
   * @param to the target address for the copy
   * @param type the scalar's type
   * @return the reference of the copy
   */
  @Inline
  public static Object getReferenceWhenCopiedTo(Object obj, Address to, RVMClass type) {
    return getReferenceWhenCopiedTo(obj, to);
  }

  @Inline
  protected static Object getReferenceWhenCopiedTo(Object obj, Address to) {
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
      // Read the hash state (used below)
      Word statusWord = Magic.getWordAtOffset(obj, STATUS_OFFSET);
      Word hashState = statusWord.and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        to = to.plus(HASHCODE_BYTES);
      }
    }
    return Magic.addressAsObject(to.plus(OBJECT_REF_OFFSET));
  }

  /**
   * Copy a scalar to the given raw storage address.
   *
   * @param toAddress the target address
   * @param fromObj the object to copy
   * @param numBytes how many bytes to copy
   * @param type the scalar's type
   * @return the reference for the object's copy
   */
  @Inline
  public static Object moveObject(Address toAddress, Object fromObj, int numBytes, RVMClass type) {

    // We copy arrays and scalars the same way
    return moveObject(toAddress, fromObj, null, numBytes);
  }

  /**
   * Copies a scalar to the given location.
   *
   * @param fromObj the scalar to copy
   * @param toObj target address for copy
   * @param numBytes how many bytes to copy
   * @param type the scalar's type
   * @return the reference for the object's copy
   */
  @Inline
  public static Object moveObject(Object fromObj, Object toObj, int numBytes, RVMClass type) {

    // We copy arrays and scalars the same way
    return moveObject(Address.zero(), fromObj, toObj, numBytes);
  }

  /**
   * Copies an array to the given raw storage address.
   *
   * @param toAddress the target address
   * @param fromObj the object to copy
   * @param numBytes how many bytes to copy
   * @param type the array's type
   * @return the reference for the object's copy
   */
  @Inline
  public static Object moveObject(Address toAddress, Object fromObj, int numBytes, RVMArray type) {

    // We copy arrays and scalars the same way
    return moveObject(toAddress, fromObj, null, numBytes);
  }

  /**
   * Copies an array to the given location.
   *
   * @param fromObj the object to copy
   * @param toObj the target object
   * @param numBytes the number of bytes to copy
   * @param type the array's type
   * @return the reference for the array's copy
   */
  @Inline
  public static Object moveObject(Object fromObj, Object toObj, int numBytes, RVMArray type) {

    // We copy arrays and scalars the same way
    return moveObject(Address.zero(), fromObj, toObj, numBytes);
  }

  /**
   * Copies an object to the given raw storage address.
   *
   * @param toObj the target object. If this is non-{@code null}, the target
   *  address must be {@code Address.zero()}.
   * @param toAddress the target address. If this is not {@code Address.zero()},
   *  the target object must be {@code null}.
   * @param fromObj the object to copy from
   * @param numBytes the number of bytes to copy
   * @return the reference of the object's copy
   */
  @Inline
  public static Object moveObject(Address toAddress, Object fromObj, Object toObj, int numBytes) {
    if (VM.VerifyAssertions) VM._assert(toAddress.isZero() || toObj == null);

    // Default values
    int copyBytes = numBytes;
    int objRefOffset = OBJECT_REF_OFFSET;
    Word statusWord = Word.zero();
    Word hashState = HASH_STATE_UNHASHED;

    if (ADDRESS_BASED_HASHING) {
      // Read the hash state (used below)
      statusWord = Magic.getWordAtOffset(fromObj, STATUS_OFFSET);
      hashState = statusWord.and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED)) {
        // We do not copy the hashcode, but we do allocate it
        copyBytes -= HASHCODE_BYTES;

        if (!DYNAMIC_HASH_OFFSET) {
          // The hashcode is the first word, so we copy to object one word higher
          if (toObj == null) {
            toAddress = toAddress.plus(HASHCODE_BYTES);
          }
        }
      } else if (!DYNAMIC_HASH_OFFSET && hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
        // Simple operation (no hash state change), but one word larger header
        objRefOffset += HASHCODE_BYTES;
      }
    }

    if (toObj != null) {
      toAddress = Magic.objectAsAddress(toObj).minus(objRefOffset);
    }

    // Low memory word of source object
    Address fromAddress = Magic.objectAsAddress(fromObj).minus(objRefOffset);

    // Do the copy
    Memory.aligned32Copy(toAddress, fromAddress, copyBytes);

    if (toObj == null) {
      toObj = Magic.addressAsObject(toAddress.plus(objRefOffset));
    } else {
      if (VM.VerifyAssertions) VM._assert(toObj == Magic.addressAsObject(toAddress.plus(objRefOffset)));
    }

    // Do we need to copy the hash code?
    if (hashState.EQ(HASH_STATE_HASHED)) {
      int hashCode = Magic.objectAsAddress(fromObj).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();
      if (DYNAMIC_HASH_OFFSET) {
        Magic.setIntAtOffset(toObj, Offset.fromIntSignExtend(numBytes - OBJECT_REF_OFFSET - HASHCODE_BYTES), hashCode);
      } else {
        Magic.setIntAtOffset(toObj, HASHCODE_OFFSET, (hashCode << 1) | ALIGNMENT_MASK);
      }
      Magic.setWordAtOffset(toObj, STATUS_OFFSET, statusWord.or(HASH_STATE_HASHED_AND_MOVED));
      if (ObjectModel.HASH_STATS) ObjectModel.hashTransition2++;
    }

    return toObj;
  }

  @Inline
  @Interruptible
  public static int getObjectHashCode(Object o) {
    if (ADDRESS_BASED_HASHING) {
      if (MOVES_OBJECTS) {
        Word hashState = Magic.getWordAtOffset(o, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED)) {
          // HASHED, NOT MOVED
          return Magic.objectAsAddress(o).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();
        } else if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          // HASHED AND MOVED
          if (DYNAMIC_HASH_OFFSET) {
            // Read the size of this object.
            RVMType t = Magic.getObjectType(o);
            int offset =
                t.isArrayType() ? t.asArray().getInstanceSize(Magic.getArrayLength(o)) -
                                  OBJECT_REF_OFFSET : t.asClass().getInstanceSize() - OBJECT_REF_OFFSET;
            return Magic.getIntAtOffset(o, Offset.fromIntSignExtend(offset));
          } else {
            return (Magic.getIntAtOffset(o, HASHCODE_OFFSET) >>> 1);
          }
        } else {
          // UNHASHED
          Word tmp;
          do {
            tmp = Magic.prepareWord(o, STATUS_OFFSET);
          } while (!Magic.attemptWord(o, STATUS_OFFSET, tmp, tmp.or(HASH_STATE_HASHED)));
          if (ObjectModel.HASH_STATS) ObjectModel.hashTransition1++;
          return getObjectHashCode(o);
        }
      } else {
        return Magic.objectAsAddress(o).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();
      }
    } else { // 10 bit hash code in status word
      int hashCode = Magic.getWordAtOffset(o, STATUS_OFFSET).and(HASH_CODE_MASK).rshl(HASH_CODE_SHIFT).toInt();
      if (hashCode != 0) {
        return hashCode;
      }
      return installHashCode(o);
    }
  }

  @NoInline
  @Interruptible
  protected static int installHashCode(Object o) {
    Word hashCode;
    do {
      hashCodeGenerator = hashCodeGenerator.plus(Word.one().lsh(HASH_CODE_SHIFT));
      hashCode = hashCodeGenerator.and(HASH_CODE_MASK);
    } while (hashCode.isZero());
    while (true) {
      Word statusWord = Magic.prepareWord(o, STATUS_OFFSET);
      if (!(statusWord.and(HASH_CODE_MASK).isZero())) {
        // some other thread installed a hashcode
        return statusWord.and(HASH_CODE_MASK).rshl(HASH_CODE_SHIFT).toInt();
      }
      if (Magic.attemptWord(o, STATUS_OFFSET, statusWord, statusWord.or(hashCode))) {
        // we installed the hash code
        return hashCode.rshl(HASH_CODE_SHIFT).toInt();
      }
    }
  }

  public static Offset getThinLockOffset(Object o) {
    return STATUS_OFFSET;
  }

  public static Offset defaultThinLockOffset() {
    return STATUS_OFFSET;
  }

  /**
   * Allocates a thin lock word for instances of the type
   * (if they already have one, then has no effect).
   *
   * @param t the type that is supposed to receive a thin
   *  lock word
   */
  public static void allocateThinLock(RVMType t) {
    // nothing to do (all objects have thin locks in this object model);
  }

  @Unpreemptible("Become another thread when lock is contended, don't preempt in other cases")
  public static void genericLock(Object o) {
    ThinLock.lock(o, STATUS_OFFSET);
  }

  @Unpreemptible("No interruption unless of exceptions")
  public static void genericUnlock(Object o) {
    ThinLock.unlock(o, STATUS_OFFSET);
  }

  /**
   * @param obj an object
   * @param thread a thread
   * @return <code>true</code> if the lock on obj is currently owned
   *         by thread <code>false</code> if it is not.
   */
  public static boolean holdsLock(Object obj, RVMThread thread) {
    return ThinLock.holdsLock(obj, STATUS_OFFSET, thread);
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
  @Unpreemptible("May be interrupted for allocations of locks")
  public static Lock getHeavyLock(Object o, boolean create) {
    return ThinLock.getHeavyLock(o, STATUS_OFFSET, create);
  }

  /**
   * Non-atomic read of word containing available bits
   *
   * @param o the object to read
   * @return the available bits word
   */
  public static Word readAvailableBitsWord(Object o) {
    return Magic.getWordAtOffset(o, STATUS_OFFSET);
  }

  /**
   * Non-atomic read of byte containing available bits
   * @param o the object to read
   * @return the available bits bytes
   */
  public static byte readAvailableByte(Object o) {
    return Magic.getByteAtOffset(o, AVAILABLE_BITS_OFFSET);
  }

  /**
   * Non-atomic write of word containing available bits.
   *
   * @param o the object whose word will be written
   * @param val the available bits word
   */
  public static void writeAvailableBitsWord(Object o, Word val) {
    Magic.setWordAtOffset(o, STATUS_OFFSET, val);
  }

  /**
   * Non-atomic write of word containing available bits
   *
   * @param bootImage the bootimage
   * @param ref an object reference whose word will be written
   * @param val the available bits word
   */
  @Interruptible
  public static void writeAvailableByte(BootImageInterface bootImage, Address ref, byte val) {
    bootImage.setByte(ref.plus(AVAILABLE_BITS_OFFSET), val);
  }

  /**
   * Non-atomic write of byte containing available bits
   *
   * @param o the object whose available byte will be written
   * @param val the value to write to the available byte
   */
  public static void writeAvailableByte(Object o, byte val) {
    Magic.setByteAtOffset(o, AVAILABLE_BITS_OFFSET, val);
  }

  /**
   * @param o the object whose bit will be tested
   * @param idx the index in the bits
   * @return {@code true} if argument bit is 1, {@code false} if it is 0
   */
  public static boolean testAvailableBit(Object o, int idx) {
    Word mask = Word.fromIntSignExtend(1 << idx);
    Word status = Magic.getWordAtOffset(o, STATUS_OFFSET);
    return mask.and(status).NE(Word.zero());
  }

  /**
   * Sets argument bit to 1 if value is true, 0 if value is false
   *
   * @param o the object whose bit will be set
   * @param idx the index in the bits
   * @param flag {@code true} for 1, {@code false} for 0
   */
  public static void setAvailableBit(Object o, int idx, boolean flag) {
    Word status = Magic.getWordAtOffset(o, STATUS_OFFSET);
    if (flag) {
      Word mask = Word.fromIntSignExtend(1 << idx);
      Magic.setWordAtOffset(o, STATUS_OFFSET, status.or(mask));
    } else {
      Word mask = Word.fromIntSignExtend(1 << idx).not();
      Magic.setWordAtOffset(o, STATUS_OFFSET, status.and(mask));
    }
  }

  /**
   * Freezes the other bits in the byte containing the available bits
   * so that it is safe to update them using setAvailableBits.
   *
   * @param o the object whose available bytes will be initialized
   */
  @Interruptible
  public static void initializeAvailableByte(Object o) {
    if (!ADDRESS_BASED_HASHING) getObjectHashCode(o);
  }

  /**
   * A prepare on the word containing the available bits.
   * <p>
   * Note: this method is intended to be used in conjunction
   * with the attempt method.
   *
   * @param o the object which has the available bits
   * @return the current value of the word
   * @see #attemptAvailableBits(Object, Word, Word)
   */
  public static Word prepareAvailableBits(Object o) {
    return Magic.prepareWord(o, STATUS_OFFSET);
  }

  /**
   * An attempt on the word containing the available bits.
   * <p>
   * Note: this method is intended to be used in conjunction
   * with the prepare method. If the method returns {@code false},
   * callers must update their information about the old value of
   * the available bits word before retrying again.
   *
   * @param o the object which has the available bits
   * @param oldVal the old value that the word is expected to have
   * @param newVal the new value that will be written, if possible
   * @return whether the write occurred
   */
  public static boolean attemptAvailableBits(Object o, Word oldVal, Word newVal) {
    return Magic.attemptWord(o, STATUS_OFFSET, oldVal, newVal);
  }

  /**
   * Given the smallest base address in a region, return the smallest
   * object reference that could refer to an object in the region.
   *
   * @param regionBaseAddr the smallest base address in the region
   * @return the smallest address in the region that could possibly
   *  refer to an object in the region
   */
  public static Address minimumObjectRef(Address regionBaseAddr) {
    return regionBaseAddr.plus(OBJECT_REF_OFFSET);
  }

  /**
   * Given the largest base address in a region, return the largest
   * object reference that could refer to an object in the region.
   *
   * @param regionHighAddr the highest base address in the region
   * @return the largest address in the region that could possibly
   *  refer to an object in the region
   */
  public static Address maximumObjectRef(Address regionHighAddr) {
    return regionHighAddr.plus(OBJECT_REF_OFFSET - SCALAR_HEADER_SIZE);
  }

  /**
   * Computes the header size of an instance of the given type.
   *
   * @param type the instance's type
   * @return size of the head in bytes
   */
  public static int computeScalarHeaderSize(RVMClass type) {
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Computes the header size of an instance of the given type.
   *
   * @param type the instance's type
   * @return size of the head in bytes
   */
  public static int computeArrayHeaderSize(RVMArray type) {
    return ARRAY_HEADER_SIZE;
  }

  /**
   * @param t RVMClass instance being created
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMClass.
   */
  public static int getAlignment(RVMClass t) {
    return t.getAlignment();
  }

  /**
   * @param t RVMClass instance being copied
   * @param obj the object being copied
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMClass.
   */
  public static int getAlignment(RVMClass t, Object obj) {
    return t.getAlignment();
  }

  /**
   * @param t RVMArray instance being created
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMArray.
   */
  public static int getAlignment(RVMArray t) {
    return t.getAlignment();
  }

  /**
   * @param t RVMArray instance being copied
   * @param obj the object being copied
   * @return the desired alignment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument RVMArray.
   */
  public static int getAlignment(RVMArray t, Object obj) {
    return t.getAlignment();
  }

  /**
   * @param t RVMClass instance being created
   * @param needsIdentityHash TODO document this parameter. Is it still needed?
   *  It's never set to true.
   * @return the offset relative to physical beginning of object
   * that must be aligned.
   */
  public static int getOffsetForAlignment(RVMClass t, boolean needsIdentityHash) {
    /* Align the first field - note that this is one word off from
       the reference. */
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET && needsIdentityHash) {
      return SCALAR_HEADER_SIZE + HASHCODE_BYTES;
    }
    return SCALAR_HEADER_SIZE;
  }

  /**
   * @param t RVMClass instance being copied
   * @param obj the object being copied
   * @return the offset relative to physical beginning of object
   * that must be aligned.
   */
  public static int getOffsetForAlignment(RVMClass t, ObjectReference obj) {
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
      Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        return SCALAR_HEADER_SIZE + HASHCODE_BYTES;
      }
    }
    return SCALAR_HEADER_SIZE;
  }

  /**
   * @param t RVMArray instance being created
   * @param needsIdentityHash TODO document this parameter. Is it still needed?
   *  It's never set to true.
   * @return the offset relative to physical beginning of object that must
   * be aligned.
   */
  public static int getOffsetForAlignment(RVMArray t, boolean needsIdentityHash) {
    /* although array_header_size == object_ref_offset we say this
       because the whole point is to align the object ref */
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET && needsIdentityHash) {
        return OBJECT_REF_OFFSET + HASHCODE_BYTES;
    }
    return OBJECT_REF_OFFSET;
  }

  /**
   * @param t RVMArray instance being copied
   * @param obj the object being copied
   * @return the offset relative to physical beginning of object that must
   * be aligned.
   */
  public static int getOffsetForAlignment(RVMArray t, ObjectReference obj) {
    /* although array_header_size == object_ref_offset we say this
       because the whole point is to align the object ref */
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
      Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        return OBJECT_REF_OFFSET + HASHCODE_BYTES;
      }
    }
    return OBJECT_REF_OFFSET;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @return the object whose header was initialized
   */
  public static Object initializeScalarHeader(Address ptr, TIB tib, int size) {
    // (TIB set by ObjectModel)
    Object ref = Magic.addressAsObject(ptr.plus(OBJECT_REF_OFFSET));
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage The bootimage being written
   * @param ptr  The object ref to the storage to be initialized
   * @param tib  The TIB of the instance being created
   * @param size The number of bytes allocated by the GC system for this object.
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @return the address used for a reference to this object
   */
  @Interruptible
  public static Address initializeScalarHeader(BootImageInterface bootImage, Address ptr, TIB tib, int size, boolean needsIdentityHash, int identityHashValue) {
    Address ref = ptr.plus(OBJECT_REF_OFFSET);
    if (needsIdentityHash) {
      bootImage.setFullWord(ref.plus(STATUS_OFFSET), HASH_STATE_HASHED_AND_MOVED.toInt());
      if (DYNAMIC_HASH_OFFSET) {
        // Read the size of this object.
        RVMType t = tib.getType();
        bootImage.setFullWord(ptr.plus(t.asClass().getInstanceSize()), identityHashValue);
      } else {
        ref = ref.plus(HASHCODE_BYTES);
        bootImage.setFullWord(ref.plus(HASHCODE_OFFSET), (identityHashValue << 1) | ALIGNMENT_MASK);
      }
    } else {
      // As boot image objects can't move there is no benefit in lazily setting them to hashed
      bootImage.setFullWord(ref.plus(STATUS_OFFSET), HASH_STATE_HASHED.toInt());
    }
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @return the array whose header was initialized
   */
  public static Object initializeArrayHeader(Address ptr, TIB tib, int size) {
    Object ref = Magic.addressAsObject(ptr.plus(OBJECT_REF_OFFSET));
    // (TIB and array length set by ObjectModel)
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   *
   * @param bootImage the bootimage being written
   * @param ptr  the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param numElements the number of elements in the array
   * @param needsIdentityHash needs an identity hash value
   * @param identityHashValue the value for the identity hash
   * @return the address used for a reference to this object
   */
  @Interruptible
  public static Address initializeArrayHeader(BootImageInterface bootImage, Address ptr, TIB tib, int size, int numElements, boolean needsIdentityHash, int identityHashValue) {
    Address ref = ptr.plus(OBJECT_REF_OFFSET);
    // (TIB set by BootImageWriter; array length set by ObjectModel)
    if (needsIdentityHash) {
      bootImage.setFullWord(ref.plus(STATUS_OFFSET), HASH_STATE_HASHED_AND_MOVED.toInt());
      if (DYNAMIC_HASH_OFFSET) {
        // Read the size of this object.
        RVMType t = tib.getType();
        bootImage.setFullWord(ptr.plus(t.asArray().getInstanceSize(numElements)), identityHashValue);
      } else {
        ref = ref.plus(HASHCODE_BYTES);
        bootImage.setFullWord(ref.plus(HASHCODE_OFFSET), (identityHashValue << 1) | ALIGNMENT_MASK);
      }
    } else {
      // As boot image objects can't move there is no benefit in lazily setting them to hashed
      bootImage.setFullWord(ref.plus(STATUS_OFFSET), HASH_STATE_HASHED.toInt());
    }
    return ref;
  }

  /**
   * For low level debugging of GC subsystem.
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped
   */
  public static void dumpHeader(Object ref) {
    // TIB dumped in ObjectModel
    VM.sysWrite(" STATUS=");
    VM.sysWriteHex(Magic.getWordAtOffset(ref, STATUS_OFFSET).toAddress());
  }
}
