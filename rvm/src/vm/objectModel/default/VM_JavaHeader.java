/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Constants;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_AllocatorHeader;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.opt.ir.*;
//-#endif

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
 * If {@link VM_JavaHeaderConstants#ADDRESS_BASED_HASHING} is false, 
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
 * If {@link VM_JavaHeaderConstants#ADDRESS_BASED_HASHING ADDRESS_BASED_HASHING} is true, 
 * then Jikes RVM uses two bits of the status word to record the hash code state in
 * a typical three state scheme ({@link #HASH_STATE_UNHASHED}, {@link #HASH_STATE_HASHED},
 * and {@link #HASH_STATE_HASHED_AND_MOVED}). In this case, the status word is laid
 * out as shown below:
 * <pre>
 *      TTTT TTTT TTTT TTTT TTTT TTHH AAAA AAAA
 * T = thin lock bits
 * H = hash code state bits
 * A = available for use by GCHeader and/or MiscHeader.
 * </pre>
 * 
 * @author Bowen Alpern
 * @author David Bacon
 * @author Steve Fink
 * @author Daniel Frampton
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_JavaHeader implements VM_JavaHeaderConstants,
                                            Uninterruptible 
                                            //-#if RVM_WITH_OPT_COMPILER
                                            ,OPT_Operators
                                            //-#endif
{

  private static final int SCALAR_HEADER_SIZE = JAVA_HEADER_BYTES + OTHER_HEADER_BYTES;
  private static final int ARRAY_HEADER_SIZE  = SCALAR_HEADER_SIZE + ARRAY_LENGTH_BYTES;

  /** offset of object reference from the lowest memory word */
  private static final int OBJECT_REF_OFFSET     = ARRAY_HEADER_SIZE;  // from start to ref
  private static final Offset TIB_OFFSET         = JAVA_HEADER_OFFSET;
  private static final Offset STATUS_OFFSET      = TIB_OFFSET.add(STATUS_BYTES);
  private static final Offset AVAILABLE_BITS_OFFSET = VM.LittleEndian 
                                                   ? (STATUS_OFFSET) 
                                                   : (STATUS_OFFSET.add(STATUS_BYTES-1));

  /*
   * Used for 10 bit header hash code in header (!ADDRESS_BASED_HASHING)
   */
  private static final int HASH_CODE_SHIFT = 2;
  private static final Word HASH_CODE_MASK  = Word.one().lsh(10).sub(Word.one()).lsh(HASH_CODE_SHIFT);
  private static Word hashCodeGenerator; // seed for generating hash codes with copying collectors.

  /** How many bits are allocated to a thin lock? */
  public static final int NUM_THIN_LOCK_BITS = ADDRESS_BASED_HASHING ? 22 : 20;
  /** How many bits to shift to get the thin lock? */
  public static final int THIN_LOCK_SHIFT    = ADDRESS_BASED_HASHING ? 10 : 12;

  static {
    if (VM.VerifyAssertions) {
      VM._assert(VM_MiscHeader.REQUESTED_BITS + VM_AllocatorHeader.REQUESTED_BITS <= NUM_AVAILABLE_BITS);
    }
  }

  /**
   * What is the offset of the first word after the class?
   * For use by VM_ObjectModel.layoutInstanceFields
   */
  public static int objectEndOffset(VM_Class klass) {
    return klass.getInstanceSizeInternal() - OBJECT_REF_OFFSET;
  }

  /**
   * What is the offset of the first word of the class?
   */
  public static int objectStartOffset(VM_Class klass) {
    return - OBJECT_REF_OFFSET;
  }

  /**
   * What is the last word of the header from an out-to-in perspective?
   */
  public static int getHeaderEndOffset() {
    return SCALAR_HEADER_SIZE - OBJECT_REF_OFFSET; 
  }

  /**
   * How small is the minimum object header size? 
   * Can be used to pick chunk sizes for allocators.
   */
  public static int minimumObjectSize() {
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Given a reference, return an address which is guaranteed to be inside
   * the memory region allocated to the object.
   */
  public static Address getPointerInMemoryRegion(ObjectReference ref) {
    return ref.toAddress().add(TIB_OFFSET);
  }

  /**
   * Get the TIB for an object.
   */
  public static Object[] getTIB(Object o) { 
    return VM_Magic.getObjectArrayAtOffset(o, TIB_OFFSET);
  }
  
  /**
   * Set the TIB for an object.
   */
  public static void setTIB(Object ref, Object[] tib) {
    VM_Magic.setObjectAtOffset(ref, TIB_OFFSET, tib);
  }

  /**
   * Set the TIB for an object.
   */
  public static void setTIB(BootImageInterface bootImage, Offset refOffset, 
                            Address tibAddr, VM_Type type) throws InterruptiblePragma {
    bootImage.setAddressWord(refOffset.add(TIB_OFFSET), tibAddr.toWord());
  }

  /**
   * Process the TIB field during copyingGC
   */
  public static void gcProcessTIB(ObjectReference ref) {
    MM_Interface.processPtrLocation(ref.toAddress().add(TIB_OFFSET));
  }

  /**
   * how many bytes are needed when the scalar object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Class type) {
    int size = type.getInstanceSize();
    if (ADDRESS_BASED_HASHING) {
      Word hashState = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    return size;
  }

  /**
   * how many bytes are used by the scalar object?
   */
  public static int bytesUsed(Object obj, VM_Class type) {
    int size = type.getInstanceSize();
    if (MM_Interface.MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING) {
        Word hashState = VM_Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          size += HASHCODE_BYTES;
        }
      }
    }
    return size;
  }

  /**
   * how many bytes are needed when the array object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Array type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (ADDRESS_BASED_HASHING) {
      Word hashState = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    return VM_Memory.alignUp(size, BYTES_IN_INT);
  }

  /**
   * how many bytes are used by the array object?
   */
  public static int bytesUsed(Object obj, VM_Array type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (MM_Interface.MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING) {
        Word hashState = VM_Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          size += HASHCODE_BYTES;
        } 
      }
    }
    return VM_Memory.alignUp(size, BYTES_IN_INT);
  }

  /**
   * Map from the object ref to the lowest address of the storage
   * associated with the object
   */
  public static Address objectStartRef(ObjectReference obj)
    throws InlinePragma {
    if (MM_Interface.MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
        Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          return obj.toAddress().sub(OBJECT_REF_OFFSET + HASHCODE_BYTES);
        }
      }
    }
    return obj.toAddress().sub(OBJECT_REF_OFFSET);
  }

  /**
   * Get an object reference from the address the lowest word of the
   * object was allocated.  In general this required that we are using
   * a dynamic hash offset or not using address based
   * hashing. However, the GC algorithm could safely do this in the
   * nursery so we can't assert DYNAMIC_HASH_OFFSET.
   */
  public static ObjectReference getObjectFromStartAddress(Address start) {
    if (VM.VerifyAssertions) VM._assert(OTHER_HEADER_BYTES == 0);

    Address obj = start.add(OBJECT_REF_OFFSET); 

    if (!VM.BuildFor64Addr) {
      Object tib = VM_Magic.getObjectAtOffset(obj, TIB_OFFSET);
  
      // only two possible misses, and if there isnt an object this is ok?
      if (tib == null) {
        obj = obj.add(BYTES_IN_ADDRESS);
        tib = VM_Magic.getObjectAtOffset(obj, TIB_OFFSET);
        if (tib == null) {
          obj = obj.add(BYTES_IN_ADDRESS);
        }
      }
    }

    return obj.toObjectReference(); 
  }

  /**
   * Get an object reference from the address the lowest word of the
   * object was allocated.
   */
  public static ObjectReference getScalarFromStartAddress(Address start) {
    return getObjectFromStartAddress(start);
  }
  
  /**
   * Get an object reference from the address the lowest word of the
   * object was allocated.
   */
  public static ObjectReference getArrayFromStartAddress(Address start) {
    return getObjectFromStartAddress(start);
  }

  /**
   * Get the next object in the heap under contiguous
   * allocation. Handles alignment issues only when there are no GC or
   * Misc header words. In the case there are we probably have to ask
   * MM_Interface to distinguish this for us.
   */
  private static ObjectReference getNextObject(ObjectReference obj, int size) {
    if (VM.VerifyAssertions) VM._assert(OTHER_HEADER_BYTES == 0);
    
    Address next = obj.toAddress().add(size);
    
    if (!VM.BuildFor64Addr) {
      Object tib = VM_Magic.getObjectAtOffset(next, TIB_OFFSET); 
   
     // only one possible miss, and if there isnt an object this is ok? 
      if (tib == null) {
        next = next.add(BYTES_IN_ADDRESS);
        tib = VM_Magic.getObjectAtOffset(next, TIB_OFFSET); 
        if (tib == null) {
          next = next.add(BYTES_IN_ADDRESS);
        } 
      } 
    }
   
    return next.toObjectReference();
  }
 
  /**
   * Get the next scalar in the heap under contiguous
   * allocation. Handles alignment issues
   */
  public static ObjectReference getNextObject(ObjectReference obj,
                                              VM_Class type) {
    return getNextObject(obj, bytesUsed(obj, type));
  }

  /**
   * Get the next array in the heap under contiguous
   * allocation. Handles alignment issues
   */
  public static ObjectReference getNextObject(ObjectReference obj,
                                              VM_Array type, int numElements) {
    return getNextObject(obj, bytesUsed(obj, type, numElements));
  }

  /**
   * Copy a scalar to the given raw storage address
   */
  public static Object moveObject(Address toAddress, Object fromObj, 
                                  int numBytes, boolean noGCHeader,
                                  VM_Class type)
    throws InlinePragma {

    // We copy arrays and scalars the same way
    return moveObject(toAddress, fromObj, numBytes, noGCHeader);
 }

  /**
   * Copy an array to the given raw storage address
   */
  public static Object moveObject(Address toAddress, Object fromObj,
                                  int numBytes, boolean noGCHeader, 
                                  VM_Array type)
    throws InlinePragma {

    // We copy arrays and scalars the same way
    return moveObject(toAddress, fromObj, numBytes, noGCHeader);
  }

  /**
   * Copy an object to the given raw storage address
   */
  public static Object moveObject(Address toAddress, Object fromObj, 
                                  int numBytes, boolean noGCHeader)
    throws InlinePragma {

    // Default values
    int copyBytes = numBytes;
    int objRefOffset = OBJECT_REF_OFFSET;
    Word statusWord = Word.zero(); 
    Word hashState = HASH_STATE_UNHASHED;
    
    if (ADDRESS_BASED_HASHING) {
      // Read the hash state (used below)
      statusWord = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET);
      hashState = statusWord.and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED)) {
        // We do not copy the hashcode, but we do allocate it
        copyBytes -= HASHCODE_BYTES;

        if (!DYNAMIC_HASH_OFFSET) {
          // The hashcode is the first word, so we copy to object one word higher
          toAddress = toAddress.add(HASHCODE_BYTES);
        }
      } else if (!DYNAMIC_HASH_OFFSET && hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
        // Simple operation (no hash state change), but one word larger header
        objRefOffset += HASHCODE_BYTES;
      }
    }

    // Low memory word of source object 
    Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(objRefOffset);
   
    // Was the GC header stolen at allocation time? ok for arrays and scalars.
    if (noGCHeader) {
      if (VM.VerifyAssertions) {
        // No object can be hashed and moved unless using dynamic hash offset
        VM._assert(hashState.NE(HASH_STATE_HASHED_AND_MOVED) || DYNAMIC_HASH_OFFSET);
      }
 
      // We copy less but start higher in memory.
      copyBytes -= GC_HEADER_BYTES;
      objRefOffset -= GC_HEADER_BYTES;
      toAddress = toAddress.add(GC_HEADER_BYTES);
    }

    // Do the copy
    VM_Memory.aligned32Copy(toAddress, fromAddress, copyBytes); 
    Object toObj = VM_Magic.addressAsObject(toAddress.add(objRefOffset));

    // Do we need to copy the hash code?
    if (hashState.EQ(HASH_STATE_HASHED)) {
      int hashCode = VM_Magic.objectAsAddress(fromObj).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();  
      if (DYNAMIC_HASH_OFFSET) {
        VM_Magic.setIntAtOffset(toObj, Offset.fromIntSignExtend(numBytes - objRefOffset - HASHCODE_BYTES), hashCode);
      } else {
        VM_Magic.setIntAtOffset(toObj, HASHCODE_OFFSET, hashCode);
      } 
      VM_Magic.setWordAtOffset(toObj, STATUS_OFFSET, statusWord.or(HASH_STATE_HASHED_AND_MOVED));
      if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
    }

    return toObj;
  }
  

  /**
   * Get the hash code of an object.
   */
  public static int getObjectHashCode(Object o) { 
    if (ADDRESS_BASED_HASHING) {
      if (MM_Interface.MOVES_OBJECTS) {
        Word hashState = VM_Magic.getWordAtOffset(o, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED)) {
          // HASHED, NOT MOVED
          return VM_Magic.objectAsAddress(o).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();  
        } else if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          // HASHED AND MOVED
          if (DYNAMIC_HASH_OFFSET) {
            // Read the size of this object.
          VM_Type t = VM_Magic.getObjectType(o);
            int offset = t.isArrayType()
              ? t.asArray().getInstanceSize(VM_Magic.getArrayLength(o)) - OBJECT_REF_OFFSET
              : t.asClass().getInstanceSize() - OBJECT_REF_OFFSET;
            return VM_Magic.getIntAtOffset(o, Offset.fromIntSignExtend(offset));
          } else {
            return VM_Magic.getIntAtOffset(o, HASHCODE_OFFSET);
          }
        } else {
          // UNHASHED
          Word tmp;
          do {
            tmp = VM_Magic.prepareWord(o, STATUS_OFFSET);
          } while (!VM_Magic.attemptWord(o, STATUS_OFFSET, tmp, tmp.or(HASH_STATE_HASHED)));
          if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition1++;
          return getObjectHashCode(o);
        }
      } else {
        return VM_Magic.objectAsAddress(o).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();  
      }
    } else { // 10 bit hash code in status word
      int hashCode = VM_Magic.getWordAtOffset(o, STATUS_OFFSET).and(HASH_CODE_MASK).rshl(HASH_CODE_SHIFT).toInt();
      if (hashCode != 0) 
        return hashCode; 
      return installHashCode(o);
    }
  }
  
  /** Install a new hashcode (only used if !ADDRESS_BASED_HASHING) */
  private static int installHashCode(Object o) throws NoInlinePragma {
    Word hashCode;
    do {
      hashCodeGenerator = hashCodeGenerator.add(Word.one().lsh(HASH_CODE_SHIFT));
      hashCode = hashCodeGenerator.and(HASH_CODE_MASK);
    } while (hashCode.isZero());
    while (true) {
      Word statusWord = VM_Magic.prepareWord(o, STATUS_OFFSET);
      if (!(statusWord.and(HASH_CODE_MASK).isZero())) // some other thread installed a hashcode
        return statusWord.and(HASH_CODE_MASK).rshl(HASH_CODE_SHIFT).toInt();
      if (VM_Magic.attemptWord(o, STATUS_OFFSET, statusWord, statusWord.or(hashCode)))
        return hashCode.rshl(HASH_CODE_SHIFT).toInt();  // we installed the hash code
    }
  }

  /**
   * Get the offset of the thin lock word in this object
   */
  public static Offset getThinLockOffset(Object o) {
    return STATUS_OFFSET;
  }

  /**
   * what is the default offset for a thin lock?
   */
  public static Offset defaultThinLockOffset() {
    return STATUS_OFFSET;
  }

  /**
   * Allocate a thin lock word for instances of the type
   * (if they already have one, then has no effect).
   */
  public static void allocateThinLock(VM_Type t) {
    // nothing to do (all objects have thin locks in this object model);
  }

  /**
   * Generic lock
   */
  public static void genericLock(Object o) { 
    VM_ThinLock.lock(o, STATUS_OFFSET);
  }

  /**
   * Generic unlock
   */
  public static void genericUnlock(Object o) {
    VM_ThinLock.unlock(o, STATUS_OFFSET);
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
    return VM_ThinLock.getHeavyLock(o, STATUS_OFFSET, create);
  }

  /**
   * Non-atomic read of word containing available bits
   */
  public static Word readAvailableBitsWord(Object o) {
    return VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
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
  public static void writeAvailableBitsWord(Object o, Word val) {
    VM_Magic.setWordAtOffset(o, STATUS_OFFSET, val);
  }

  /**
   * Non-atomic write of word containing available bits
   */
  public static void writeAvailableBitsWord(BootImageInterface bootImage,
                                            Offset ref, Word val) throws InterruptiblePragma {
    bootImage.setAddressWord(STATUS_OFFSET.add(ref), val);
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
    Word mask = Word.fromIntSignExtend(1<<idx);
    Word status = VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
    return mask.and(status).NE(Word.zero());
  }

  /**
   * Set argument bit to 1 if value is true, 0 if value is false
   */
  public static void setAvailableBit(Object o, int idx, boolean flag) {
    Word status = VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
    if (flag) {
      Word mask = Word.fromIntSignExtend(1<<idx);
      VM_Magic.setWordAtOffset(o, STATUS_OFFSET, status.or(mask));
    } else {
      Word mask = Word.fromIntSignExtend(1<<idx).not();
      VM_Magic.setWordAtOffset(o, STATUS_OFFSET, status.and(mask));
    }
  }

  /**
   * Freeze the other bits in the byte containing the available bits
   * so that it is safe to update them using setAvailableBits.
   */
  public static void initializeAvailableByte(Object o) {
    if (!ADDRESS_BASED_HASHING) getObjectHashCode(o);
  }

  /**
   * A prepare on the word containing the available bits
   */
  public static Word prepareAvailableBits(Object o) {
    return VM_Magic.prepareWord(o, STATUS_OFFSET);
  }
  
  /**
   * An attempt on the word containing the available bits
   */
  public static boolean attemptAvailableBits(Object o, Word oldVal,
                                             Word newVal) {
    return VM_Magic.attemptWord(o, STATUS_OFFSET, oldVal, newVal);
  }
  
  /**
   * Given the smallest base address in a region, return the smallest
   * object reference that could refer to an object in the region.
   */
  public static Address minimumObjectRef (Address regionBaseAddr) {
    return regionBaseAddr.add(OBJECT_REF_OFFSET);
  }

  /**
   * Given the largest base address in a region, return the largest
   * object reference that could refer to an object in the region.
   */
  public static Address maximumObjectRef (Address regionHighAddr) {
    return regionHighAddr.add(OBJECT_REF_OFFSET - SCALAR_HEADER_SIZE); 
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
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Class.
   * @param t VM_Class instance being created
   */
  public static int getAlignment(VM_Class t) {
    return t.getAlignment();
  }

  /**
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Class.
   * @param t VM_Class instance being copied
   * @param obj the object being copied
   */
  public static int getAlignment(VM_Class t, Object obj) {
    return t.getAlignment();
  }

  /**
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Array.
   * @param t VM_Array instance being created
   */
  public static int getAlignment(VM_Array t) {
    return t.getAlignment();
  }

  /**
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Array.
   * @param t VM_Array instance being copied
   * @param obj the object being copied
   */
  public static int getAlignment(VM_Array t, Object obj) {
    return t.getAlignment();
  }

  /**
   * Return the offset relative to physical beginning of object
   * that must be aligned.
   * @param t VM_Class instance being created
   */
  public static int getOffsetForAlignment(VM_Class t) {
    /* Align the first field - note that this is one word off from
       the reference. */ 
    return SCALAR_HEADER_SIZE; 
  }

  /**
   * Return the offset relative to physical beginning of object
   * that must be aligned.
   * @param t VM_Class instance being copied
   * @param obj the object being copied
   */
  public static int getOffsetForAlignment(VM_Class t, ObjectReference obj) {
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
      Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        return SCALAR_HEADER_SIZE + HASHCODE_BYTES;
      }
    } 
    return SCALAR_HEADER_SIZE; 
  }

  /**
   * Return the offset relative to physical beginning of object that must
   * be aligned.
   * @param t VM_Array instance being created
   */
  public static int getOffsetForAlignment(VM_Array t) {
    /* although array_header_size == object_ref_offset we say this
       because the whole point is to align the object ref */
    return OBJECT_REF_OFFSET;
  }

  /**
   * Return the offset relative to physical beginning of object that must
   * be aligned.
   * @param t VM_Array instance being copied
   * @param obj the object being copied
   */
  public static int getOffsetForAlignment(VM_Array t, ObjectReference obj) {
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
   */
  public static Object initializeScalarHeader(Address ptr, Object[] tib, 
                                              int size) {
    // (TIB set by VM_ObjectModel)
    Object ref = VM_Magic.addressAsObject(ptr.add(OBJECT_REF_OFFSET));
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage The bootimage being written
   * @param ptr  The object ref to the storage to be initialized
   * @param tib  The TIB of the instance being created
   * @param size The number of bytes allocated by the GC system for this object.
   */
  public static Offset initializeScalarHeader(BootImageInterface bootImage,
                                           Offset ptr, Object[] tib, int size)
    throws InterruptiblePragma {
    Offset ref = ptr.add(OBJECT_REF_OFFSET);
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeArrayHeader(Address ptr, Object[] tib, int size) {
    Object ref = VM_Magic.addressAsObject(ptr.add(OBJECT_REF_OFFSET));
    // (TIB and array length set by VM_ObjectModel)
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * XXX This documentation probably needs fixing TODO
   * 
   * @param bootImage the bootimage being written
   * @param ptr  the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @return Document ME TODO XXX
   */
  public static Offset initializeArrayHeader(BootImageInterface bootImage, Offset ptr, 
                                          Object[] tib, int size) throws InterruptiblePragma {
    Offset ref = ptr.add(OBJECT_REF_OFFSET);
    // (TIB set by BootImageWriter2; array length set by VM_ObjectModel)
    return ref;
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) {
    // TIB dumped in VM_ObjectModel
    VM.sysWrite(" STATUS=");
    VM.sysWriteHex(VM_Magic.getWordAtOffset(ref, STATUS_OFFSET).toAddress());
  }


  /**
   * The following method will emit code that moves a reference to an
   * object's TIB into a destination register.
   *
   * @param asm the assembler object to emit code with
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   */
  //-#if RVM_FOR_POWERPC
  public static void baselineEmitLoadTIB(VM_Assembler asm, int dest, 
                                         int object) throws InterruptiblePragma {
    asm.emitLAddrOffset(dest, object, TIB_OFFSET);
  }
  //-#elif RVM_FOR_IA32
  public static void baselineEmitLoadTIB(VM_Assembler asm, byte dest, 
                                         byte object) throws InterruptiblePragma {
    asm.emitMOV_Reg_RegDisp(dest, object, TIB_OFFSET);
  }
  //-#endif

  //-#if RVM_WITH_OPT_COMPILER
  /**
   * Mutate a GET_OBJ_TIB instruction to the LIR
   * instructions required to implement it.
   * 
   * @param s the GET_OBJ_TIB instruction to lower
   * @param ir the enclosing OPT_IR
   */
  public static void lowerGET_OBJ_TIB(OPT_Instruction s, OPT_IR ir) throws InterruptiblePragma {
    // TODO: valid location operand.
    OPT_Operand address = GuardedUnary.getClearVal(s);
    Load.mutate(s, REF_LOAD, GuardedUnary.getClearResult(s), 
                address, new OPT_AddressConstantOperand(TIB_OFFSET), 
                null, GuardedUnary.getClearGuard(s));
  }
  //-#endif

  /*
    static public void showConstants() {
    VM.sysWriteln("NUM_AVAILABLE_BITS = ", NUM_AVAILABLE_BITS);
    VM.sysWriteln("AVAILABLE_BITS_OFFSET = ", AVAILABLE_BITS_OFFSET);
    VM.sysWriteln("NUM_THIN_LOCK_BITS = ", NUM_THIN_LOCK_BITS);
    VM.sysWriteln("THIN_LOCK_SHIFT = ", THIN_LOCK_SHIFT);
    VM.sysWriteln("VM_MiscHeader.REQUESTED_BITS = ", VM_MiscHeader.REQUESTED_BITS);
    VM.sysWriteln("VM_AllocatorHeader.REQUESTED_BITS  = ", VM_AllocatorHeader.REQUESTED_BITS);
  }
  */

}
