/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_AllocatorHeader;

/**
 * Constants for the JavaHeader. 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 * @author Steve Blackburn
 */
public interface VM_JavaHeaderConstants extends VM_SizeConstants {

  static final int TIB_BYTES = BYTES_IN_ADDRESS;
  static final int STATUS_BYTES = BYTES_IN_ADDRESS;
  static final int ARRAY_LENGTH_BYTES = BYTES_IN_INT;

  static final int JAVA_HEADER_BYTES = TIB_BYTES + STATUS_BYTES;
  static final int GC_HEADER_BYTES = VM_AllocatorHeader.NUM_BYTES_HEADER;
  static final int MISC_HEADER_BYTES = VM_MiscHeader.NUM_BYTES_HEADER;
  static final int OTHER_HEADER_BYTES = GC_HEADER_BYTES + MISC_HEADER_BYTES;

  static final int GC_HEADER_OFFSET = -GC_HEADER_BYTES;
  static final int MISC_HEADER_OFFSET = GC_HEADER_OFFSET - MISC_HEADER_BYTES;
  static final int JAVA_HEADER_OFFSET = MISC_HEADER_OFFSET - JAVA_HEADER_BYTES;
  static final int ARRAY_LENGTH_OFFSET = JAVA_HEADER_OFFSET - ARRAY_LENGTH_BYTES;

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


  /*
   * Stuff for address based hashing
   */
  static final VM_Word HASH_STATE_UNHASHED         = VM_Word.zero();
  static final VM_Word HASH_STATE_HASHED           = VM_Word.one().lsh(8); //0x00000100
  static final VM_Word HASH_STATE_HASHED_AND_MOVED = VM_Word.fromIntZeroExtend(3).lsh(8); //0x0000300
  static final VM_Word HASH_STATE_MASK             = HASH_STATE_UNHASHED.or(HASH_STATE_HASHED).or(HASH_STATE_HASHED_AND_MOVED);
  static final int HASHCODE_SCALAR_OFFSET      = 0; // to right of objref
  static final int HASHCODE_BYTES              = BYTES_IN_INT;
  static final int HASHCODE_ARRAY_OFFSET       = ARRAY_LENGTH_OFFSET - HASHCODE_BYTES; // to left of header
  
}
