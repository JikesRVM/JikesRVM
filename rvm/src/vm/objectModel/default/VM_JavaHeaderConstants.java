/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 

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
  static final boolean ADDRESS_BASED_HASHING = true;

  /** How many bits in the header are available for the GC and MISC headers? */
  static final int NUM_AVAILABLE_BITS = ADDRESS_BASED_HASHING ? 6 : 2;

  /**
   * Does this object model use the same header word to contain
   * the TIB and a forwarding pointer?
   */
  static final boolean FORWARDING_PTR_OVERLAYS_TIB = false;

}
