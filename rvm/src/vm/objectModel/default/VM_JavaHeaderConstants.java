/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
//-#if RVM_WITH_JMTK
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_AllocatorHeader;
//-#endif
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
import com.ibm.JikesRVM.memoryManagers.watson.VM_AllocatorHeader;
//-#endif

/**
 * Constants for the JavaHeader. 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public interface VM_JavaHeaderConstants {

  static final int JAVA_HEADER_END = -12;

  static final int ARRAY_LENGTH_OFFSET = -4;

  /**
   * This object model supports two schemes for hashcodes:
   * (1) a 10 bit hash code in the object header
   * (2) use the address of the object as its hashcode.
   *     In a copying collector, this forces us to add a word
   *     to copied objects that have had their hashcode taken.
   */
  static final boolean ADDRESS_BASED_HASHING = false; // true;

  /** How many bits in the header are available for the GC and MISC headers? */
  static final int NUM_AVAILABLE_BITS = ADDRESS_BASED_HASHING ? 8 : 2;

  /**
   * Does this object model use the same header word to contain
   * the TIB and a forwarding pointer?
   */
  static final boolean FORWARDING_PTR_OVERLAYS_TIB = false;

  static final int OTHER_HEADER_BYTES = VM_AllocatorHeader.NUM_BYTES_HEADER + VM_MiscHeader.NUM_BYTES_HEADER;

  /*
   * Stuff for address based hashing
   */
  static final int HASH_STATE_UNHASHED         = 0x00000000;
  static final int HASH_STATE_HASHED           = 0x00000100;
  static final int HASH_STATE_HASHED_AND_MOVED = 0x00000300;
  static final int HASH_STATE_MASK             = HASH_STATE_UNHASHED | HASH_STATE_HASHED | HASH_STATE_HASHED_AND_MOVED;
  static final int HASHCODE_SCALAR_OFFSET      = -4; // in "phantom word"
  static final int HASHCODE_ARRAY_OFFSET       = JAVA_HEADER_END - OTHER_HEADER_BYTES - 4; // to left of header
  static final int HASHCODE_BYTES              = 4;
  
}
