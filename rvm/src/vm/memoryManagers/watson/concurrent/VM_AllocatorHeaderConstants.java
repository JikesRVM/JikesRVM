/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


/**
 * Define the constants manipulated by VM_CommonAllocatorHeader
 *
 * @see VM_ObjectModel
 * @see VM_AllocatorHeader
 * @see VM_CommonAllocatorHeader
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public interface VM_AllocatorHeaderConstants {
  
  /**
   * How many bytes are used by all GC header fields?
   */
  static final int NUM_BYTES_HEADER = 4;

  /**
   * We have a whole word of our own...don't need any other header bits!
   */
  static final int REQUESTED_BITS =  0;

  /**
   * Use a side mark vector for debugging.
   */
  static final boolean USE_SIDE_MARK_VECTOR = VM_Allocator.GC_MARK_REACHABLE_OBJECTS;

  /*
   * Values that VM_CommonAllocatorHeader requires that we define.
   */
  static final int GC_BARRIER_BIT_IDX  = -1;  // NOT USED;
  static final int GC_FORWARDED        = -1;  // NOT USED;
  static final int GC_BEING_FORWARDED  = -1;  // NOT USED;
}
