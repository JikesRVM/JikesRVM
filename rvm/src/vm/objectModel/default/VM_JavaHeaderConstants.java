/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_AllocatorHeader;
import org.vmmagic.unboxed.*;

/**
 * Constants for the JavaHeader. 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Blackburn
 * @author Steve Fink
 * @author Daniel Frampton
 * @author Dave Grove
 */
public interface VM_JavaHeaderConstants extends VM_SizeConstants {

  static final int TIB_BYTES = BYTES_IN_ADDRESS;
  static final int STATUS_BYTES = BYTES_IN_ADDRESS;

  /* we use 64 bits for the length on a 64 bit architecture as this makes 
     the other words 8-byte aligned, and the header has to be 8-byte aligned. */
  static final int ARRAY_LENGTH_BYTES = VM.BuildFor64Addr 
                                        ? BYTES_IN_ADDRESS
                                        : BYTES_IN_INT;

  static final int JAVA_HEADER_BYTES = TIB_BYTES + STATUS_BYTES;
  static final int GC_HEADER_BYTES = VM_AllocatorHeader.NUM_BYTES_HEADER;
  static final int MISC_HEADER_BYTES = VM_MiscHeader.NUM_BYTES_HEADER;
  static final int OTHER_HEADER_BYTES = GC_HEADER_BYTES + MISC_HEADER_BYTES;

  static final int ARRAY_LENGTH_OFFSET =                     - ARRAY_LENGTH_BYTES;
  static final int JAVA_HEADER_OFFSET  = ARRAY_LENGTH_OFFSET - JAVA_HEADER_BYTES;
  static final int MISC_HEADER_OFFSET  = JAVA_HEADER_OFFSET  - MISC_HEADER_BYTES;
  static final int GC_HEADER_OFFSET    = MISC_HEADER_OFFSET  - GC_HEADER_BYTES;
  

  /**
   * This object model supports two schemes for hashcodes:
   * (1) a 10 bit hash code in the object header
   * (2) use the address of the object as its hashcode.
   *     In a copying collector, this forces us to add a word
   *     to copied objects that have had their hashcode taken.
   */
  static final boolean ADDRESS_BASED_HASHING = 
    //-#if RVM_WITH_GCTRACE
    false;
    //-#else
    true;
    //-#endif

  /** How many bits in the header are available for the GC and MISC headers? */
  static final int NUM_AVAILABLE_BITS = ADDRESS_BASED_HASHING ? 8 : 2;

  /**
   * Does this object model use the same header word to contain
   * the TIB and a forwarding pointer?
   */
  static final boolean FORWARDING_PTR_OVERLAYS_TIB = false;

  /**
   * Does this object model place the hash for a hashed and moved object 
   * after the data (at a dynamic offset)
   */
  static final boolean DYNAMIC_HASH_OFFSET = ADDRESS_BASED_HASHING && false;

  /**
   * Can we perform a linear scan?
   */
  static final boolean ALLOWS_LINEAR_SCAN = true;

  /**
   * Do we need to segregate arrays and scalars to do a linear scan?
   */
  static final boolean SEGREGATE_ARRAYS_FOR_LINEAR_SCAN = false;

  /*
   * Stuff for address based hashing
   */
  static final Word HASH_STATE_UNHASHED         = Word.zero();
  static final Word HASH_STATE_HASHED           = Word.one().lsh(8); //0x00000100
  static final Word HASH_STATE_HASHED_AND_MOVED = Word.fromIntZeroExtend(3).lsh(8); //0x0000300
  static final Word HASH_STATE_MASK             = HASH_STATE_UNHASHED.or(HASH_STATE_HASHED).or(HASH_STATE_HASHED_AND_MOVED);
  
  static final int HASHCODE_BYTES              = BYTES_IN_INT;
  static final int HASHCODE_OFFSET = GC_HEADER_OFFSET - HASHCODE_BYTES; 
  
}
