/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Constants;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_AllocatorHeader;
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
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_JavaHeader implements VM_JavaHeaderConstants,
                                            VM_Uninterruptible 
                                            //-#if RVM_WITH_OPT_COMPILER
                                            ,OPT_Operators
                                            //-#endif
{

  // TIB + STATUS + OTHER_HEADER_BYTES
  private static final int SCALAR_HEADER_SIZE = JAVA_HEADER_BYTES + OTHER_HEADER_BYTES;
  // SCALAR_HEADER + ARRAY LENGTH;
  private static final int ARRAY_HEADER_SIZE  = SCALAR_HEADER_SIZE + ARRAY_LENGTH_BYTES;

  private static final int STATUS_OFFSET  = JAVA_HEADER_OFFSET;
  private static final int TIB_OFFSET     = STATUS_OFFSET + STATUS_BYTES;

  private static final int AVAILABLE_BITS_OFFSET = VM.LittleEndian ? (STATUS_OFFSET) : (STATUS_OFFSET + STATUS_BYTES-1);

  /*
   * Stuff for 10 bit header hash code in header
   */
  private static final int HASH_CODE_SHIFT = 2;
  private static final VM_Word HASH_CODE_MASK  = VM_Word.one().lsh(10).sub(VM_Word.one()).lsh(HASH_CODE_SHIFT);
  private static VM_Word hashCodeGenerator; // seed for generating hash codes with copying collectors.

  /** How many bits are allocated to a thin lock? */
  public static final int NUM_THIN_LOCK_BITS = ADDRESS_BASED_HASHING ? 22 : 20;
  /** How many bits to shift to get the thin lock? */
  public static final int THIN_LOCK_SHIFT    = ADDRESS_BASED_HASHING ? 10 : 12;

  /**
   * How small is the minimum object header size? 
   * Used to pick chunk sizes for mark-sweep based collectors.
   */
  public static final int MINIMUM_HEADER_SIZE = SCALAR_HEADER_SIZE;

  static {
    if (VM.VerifyAssertions) {
      VM._assert(VM_MiscHeader.REQUESTED_BITS + VM_AllocatorHeader.REQUESTED_BITS <= NUM_AVAILABLE_BITS);
    }
  }

  /**
   * What is the offset of the 'last' byte in the class?
   * For use by VM_ObjectModel.layoutInstanceFields
   */
  public static int objectEndOffset(VM_Class klass) {
    return - klass.getInstanceSizeInternal();
  }

  /**
   * Given a reference, return an address which is guaranteed to be inside
   * the memory region allocated to the object.
   */
  public static VM_Address getPointerInMemoryRegion(VM_Address ref) {
    return ref.add(TIB_OFFSET);
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
  public static void setTIB(BootImageInterface bootImage, int refOffset, 
                            VM_Address tibAddr, VM_Type type) throws VM_PragmaInterruptible {
    bootImage.setAddressWord(refOffset + TIB_OFFSET, tibAddr.toWord());
  }

  /**
   * Process the TIB field during copyingGC
   */
  public static void gcProcessTIB(VM_Address ref) {
    MM_Interface.processPtrLocation(ref.add(TIB_OFFSET));
  }

  /**
   * how many bytes are needed when the scalar object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Class type) {
    int size = type.getInstanceSize();
    if (ADDRESS_BASED_HASHING) {
      VM_Word hashState = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    // JMTK requires sizes to be multiples of BYTES_IN_PARTICLE
    // Jikes RVM currently forces scalars to be multiples of
    // BYTES_IN_INT. Round up if BYTES_IN_PARTICLES is bigger.
    if (MM_Constants.BYTES_IN_PARTICLE > BYTES_IN_INT) {
      size = VM_Memory.alignUp(size, MM_Constants.BYTES_IN_PARTICLE);
    }
    return size;
  }

  /**
   * how many bytes are needed when the array object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Array type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (ADDRESS_BASED_HASHING) {
      VM_Word hashState = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    // JMTk requires all allocation requests to be multiples of BYTES_IN_PARTICLE
    return VM_Memory.alignUp(size, MM_Constants.BYTES_IN_PARTICLE);
  }

  /**
   * Map from the object ref to the lowest address of the storage
   * associated with the object
   */
  public static VM_Address objectStartRef(VM_Address obj) throws VM_PragmaInline {
    Object[] tib = VM_ObjectModel.getTIB(obj);
    VM_Type type = VM_Magic.objectAsType(tib[VM_TIBLayoutConstants.TIB_TYPE_INDEX]);
    if (type.isClassType()) {
      VM_Class klass = type.asClass();
      int instanceSize = klass.getInstanceSize();
      return obj.sub(instanceSize);
    } else {
      return obj.sub(ARRAY_HEADER_SIZE);
    }
  }

  /**
   * Copy a scalar to the given raw storage address
   */
  public static Object moveObject(VM_Address toAddress, Object fromObj, 
                                  int numBytes, VM_Class type, 
				  VM_Word availBitsWord) throws VM_PragmaInline {
    VM_Word hashState = HASH_STATE_UNHASHED;
    if (ADDRESS_BASED_HASHING) hashState = availBitsWord.and(HASH_STATE_MASK);
    int objectEndOffset = objectEndOffset(type);
    VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).add(objectEndOffset);
    int copyBytes = numBytes;
    if (VM_AllocatorHeader.STEAL_NURSERY_SCALAR_GC_HEADER)
      copyBytes -= GC_HEADER_BYTES;
    VM_Memory.aligned32Copy(toAddress, fromAddress, copyBytes); 
    Object toObj = VM_Magic.addressAsObject(toAddress.sub(objectEndOffset));
    if (hashState.EQ(HASH_STATE_HASHED)) {
      int hashCode = VM_Magic.objectAsAddress(fromObj).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();  
      VM_Magic.setIntAtOffset(toObj, HASHCODE_SCALAR_OFFSET, hashCode);
      VM_Magic.setWordAtOffset(toObj, STATUS_OFFSET, availBitsWord.or(HASH_STATE_HASHED_AND_MOVED));
      if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
    } else {
      VM_Magic.setWordAtOffset(toObj, STATUS_OFFSET, availBitsWord);
    }
    return toObj;
 }

  /**
   * Copy an array to the given raw storage address
   */
  public static Object moveObject(VM_Address toAddress, Object fromObj,
                                  int numBytes, VM_Array type,
				  VM_Word availBitsWord) throws VM_PragmaInline {
    int headersize; 
    VM_Word hashState = HASH_STATE_UNHASHED;
    if (ADDRESS_BASED_HASHING) hashState = availBitsWord.and(HASH_STATE_MASK);
    if (hashState.EQ(HASH_STATE_UNHASHED)) {
      headersize = ARRAY_HEADER_SIZE; 
    } else {
      headersize = ARRAY_HEADER_SIZE + HASHCODE_BYTES;
    }
    VM_Address fromAddress = VM_Magic.objectAsAddress(fromObj).sub(headersize);
    VM_Memory.aligned32Copy(toAddress, fromAddress, numBytes); 
    Object toObj = VM_Magic.addressAsObject(toAddress.add(headersize));
    if (hashState.EQ(HASH_STATE_HASHED)) {
      int hashCode = VM_Magic.objectAsAddress(fromObj).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();  
      VM_Magic.setIntAtOffset(toObj, HASHCODE_ARRAY_OFFSET, hashCode);
      VM_Magic.setWordAtOffset(toObj, STATUS_OFFSET, availBitsWord.or(HASH_STATE_HASHED_AND_MOVED));
      if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
    } else {
      VM_Magic.setWordAtOffset(toObj, STATUS_OFFSET, availBitsWord);
    }
    return toObj;
  }
  
  /**
   * Get the hash code of an object.
   */
  public static int getObjectHashCode(Object o) { 
    if (ADDRESS_BASED_HASHING) {
      if (MM_Interface.MOVES_OBJECTS) {
	VM_Word hashState = VM_Magic.getWordAtOffset(o, STATUS_OFFSET).and(HASH_STATE_MASK);
	if (hashState.EQ(HASH_STATE_HASHED)) {
	  return VM_Magic.objectAsAddress(o).toWord().rshl(LOG_BYTES_IN_ADDRESS).toInt();  
	} else if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          VM_Type t = VM_Magic.getObjectType(o);
          if (t.isArrayType()) {
            return VM_Magic.getIntAtOffset(o, HASHCODE_ARRAY_OFFSET);
          } else {
            return VM_Magic.getIntAtOffset(o, HASHCODE_SCALAR_OFFSET);
          }
        } else {
	  VM_Word tmp;
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
  private static int installHashCode(Object o) throws VM_PragmaNoInline {
    VM_Word hashCode;
    do {
      hashCodeGenerator = hashCodeGenerator.add(VM_Word.one().lsh(HASH_CODE_SHIFT));
      hashCode = hashCodeGenerator.and(HASH_CODE_MASK);
    } while (hashCode.isZero());
    while (true) {
      VM_Word statusWord = VM_Magic.prepareWord(o, STATUS_OFFSET);
      if (!(statusWord.and(HASH_CODE_MASK).isZero())) // some other thread installed a hashcode
	return statusWord.and(HASH_CODE_MASK).rshl(HASH_CODE_SHIFT).toInt();
      if (VM_Magic.attemptWord(o, STATUS_OFFSET, statusWord, statusWord.or(hashCode)))
	return hashCode.rshl(HASH_CODE_SHIFT).toInt();  // we installed the hash code
    }
  }

  /**
   * Get the offset of the thin lock word in this object
   */
  public static int getThinLockOffset(Object o) {
    return STATUS_OFFSET;
  }

  /**
   * what is the default offset for a thin lock?
   */
  public static int defaultThinLockOffset() {
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
  public static VM_Word readAvailableBitsWord(Object o) {
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
  public static void writeAvailableBitsWord(Object o, VM_Word val) {
    VM_Magic.setWordAtOffset(o, STATUS_OFFSET, val);
  }

  /**
   * Non-atomic write of word containing available bits
   */
  public static void writeAvailableBitsWord(BootImageInterface bootImage,
                                            int ref, VM_Word val) throws VM_PragmaInterruptible {
    bootImage.setAddressWord(ref + STATUS_OFFSET, val);
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
    VM_Word mask = VM_Word.fromIntSignExtend(1<<idx);
    VM_Word status = VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
    return mask.and(status).NE(VM_Word.zero());
  }

  /**
   * Set argument bit to 1 if value is true, 0 if value is false
   */
  public static void setAvailableBit(Object o, int idx, boolean flag) {
    VM_Word status = VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
    if (flag) {
      VM_Word mask = VM_Word.fromIntSignExtend(1<<idx);
      VM_Magic.setWordAtOffset(o, STATUS_OFFSET, status.or(mask));
    } else {
      VM_Word mask = VM_Word.fromIntSignExtend(1<<idx).not();
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
  public static VM_Word prepareAvailableBits(Object o) {
    return VM_Magic.prepareWord(o, STATUS_OFFSET);
  }
  
  /**
   * An attempt on the word containing the available bits
   */
  public static boolean attemptAvailableBits(Object o, VM_Word oldVal,
                                             VM_Word newVal) {
    return VM_Magic.attemptWord(o, STATUS_OFFSET, oldVal, newVal);
  }
  
  /**
   * Given the smallest base address in a region, return the smallest
   * object reference that could refer to an object in the region.
   */
  public static VM_Address minimumObjectRef (VM_Address regionBaseAddr) {
    return regionBaseAddr.add(SCALAR_HEADER_SIZE);
  }

  /**
   * Given the largest base address in a region, return the largest
   * object reference that could refer to an object in the region.
   */
  public static VM_Address maximumObjectRef (VM_Address regionHighAddr) {
    return regionHighAddr;
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
    return t.getInstanceSize(); // TIB is at the end
  }

  /**
   * Return the offset relative to physical beginning of object
   * that must be aligned.
   * @param t VM_Class instance being copied
   * @param obj the object being copied
   */
  public static int getOffsetForAlignment(VM_Class t, VM_Address obj) {
    // TIB is at end and HASHCODE (if present) is to right of TIB
    return t.getInstanceSize();
  }

  /**
   * Return the offset relative to physical beginning of object that must
   * be aligned.
   * @param t VM_Array instance being created
   */
  public static int getOffsetForAlignment(VM_Array t) {
    return ARRAY_HEADER_SIZE; // ie, align the pointer itself (and thus the array elements)
  }

  /**
   * Return the offset relative to physical beginning of object that must
   * be aligned.
   * @param t VM_Array instance being copied
   * @param obj the object being copied
   */
  public static int getOffsetForAlignment(VM_Array t, VM_Address obj) {
    if (ADDRESS_BASED_HASHING) {
      VM_Word hashState = VM_Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_UNHASHED)) {
        return ARRAY_HEADER_SIZE;
      } else {
        return ARRAY_HEADER_SIZE + HASHCODE_BYTES;
      }
    } else {
      return ARRAY_HEADER_SIZE;
    }
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeScalarHeader(VM_Address ptr, Object[] tib, int size) {
    // (TIB set by VM_ObjectModel)
    Object ref = VM_Magic.addressAsObject(ptr.add(size));
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static int initializeScalarHeader(BootImageInterface bootImage,
                                           int ptr, Object[] tib, int size)
    throws VM_PragmaInterruptible {
    int ref = ptr + size;

    // (TIB set by BootImageWriter2)

    //    if (MM_Interface.NEEDS_WRITE_BARRIER) {
    if (false) {
      // must set barrier bit for bootimage objects
      if (ADDRESS_BASED_HASHING) {
	bootImage.setAddressWord(ref + STATUS_OFFSET, VM_AllocatorHeader.GC_BARRIER_BIT_MASK);
      } else {
        // Since the write barrier accesses the available bits bytes 
        // non-atomically we also need to initialize the hash code
        // to freeze the rest of the bits in the byte.
	VM_Word hashCode;
        do {
	  hashCodeGenerator = hashCodeGenerator.add(VM_Word.one().lsh(HASH_CODE_SHIFT));
	  hashCode = hashCodeGenerator.and(HASH_CODE_MASK);
	} while (hashCode.isZero());
	bootImage.setAddressWord(ref + STATUS_OFFSET, hashCode.or(VM_AllocatorHeader.GC_BARRIER_BIT_MASK));
      }
    }

    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeArrayHeader(VM_Address ptr, Object[] tib, int size) {
    // (TIB and array length set by VM_ObjectModel)
    Object ref = VM_Magic.addressAsObject(ptr.add(ARRAY_HEADER_SIZE));
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static int initializeArrayHeader(BootImageInterface bootImage, int ptr, 
                                          Object[] tib, int size) throws VM_PragmaInterruptible {
    int ref = ptr + ARRAY_HEADER_SIZE;
    // (TIB set by BootImageWriter2; array length set by VM_ObjectModel)

    //    if (MM_Interface.NEEDS_WRITE_BARRIER) {
    if (false) {
      // must set barrier bit for bootimage objects
      if (ADDRESS_BASED_HASHING) {
	bootImage.setAddressWord(ref + STATUS_OFFSET, VM_AllocatorHeader.GC_BARRIER_BIT_MASK);
      } else {
        // Since the write barrier accesses the available bits bytes 
        // non-atomically we also need to initialize the hash code
        // to freeze the rest of the bits in the byte.
	VM_Word hashCode;
        do {
	  hashCodeGenerator = hashCodeGenerator.add(VM_Word.one().lsh(HASH_CODE_SHIFT));
	  hashCode = hashCodeGenerator.and(HASH_CODE_MASK);
	} while (hashCode.isZero());
	bootImage.setAddressWord(ref + STATUS_OFFSET, hashCode.or(VM_AllocatorHeader.GC_BARRIER_BIT_MASK));
      }
    }

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
                                         int object) throws VM_PragmaInterruptible {
    asm.emitLAddr(dest, TIB_OFFSET, object);
  }
  //-#elif RVM_FOR_IA32
  public static void baselineEmitLoadTIB(VM_Assembler asm, byte dest, 
                                         byte object) throws VM_PragmaInterruptible {
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
  public static void lowerGET_OBJ_TIB(OPT_Instruction s, OPT_IR ir) throws VM_PragmaInterruptible {
    // TODO: valid location operand.
    OPT_Operand address = GuardedUnary.getClearVal(s);
    Load.mutate(s, INT_LOAD, GuardedUnary.getClearResult(s), 
                address, new OPT_IntConstantOperand(TIB_OFFSET), 
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
