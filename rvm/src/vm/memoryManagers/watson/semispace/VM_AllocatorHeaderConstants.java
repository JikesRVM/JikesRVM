/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Define the constants manipulated by VM_CommonAllocatorHeader. <p>
 * 
 * This collector requires two header bits in the object.
 * For bootimage objects, the mark bit is used to mark objects
 * and the 'marked' value alternates between 1 and 0 at each 
 * collection.
 * For other objects, we use the second header bit to indicate
 * that the object is forwarded or being forwarded. 
 * If this forwarding bit is set, then the 'mark bit' is 
 * used to indicate whether the forwarding is still in progress
 * or completed. 
 *
 * @see VM_ObjectModel
 * @see VM_AllocatorHeader
 * @see VM_CommonAllocatorHeader
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
package MM;

public interface VM_AllocatorHeaderConstants {
  
  /**
   * How many bytes are used by all GC header fields?
   */
  static final int NUM_BYTES_HEADER = 0;

  /**
   * How many bits does this GC system require?
   */
  static final int REQUESTED_BITS = 2;

  /**
   * We don't require a side mark array for bootimage objects.
   */
  static final boolean USE_SIDE_MARK_VECTOR = false;

  /*
   * Values that VM_CommonAllocatorHeader requires that we define.
   */
  static final int GC_BARRIER_BIT_IDX  = -1; // NOT USED;
  static final int GC_FORWARDED        = 0x2;  // ...10
  static final int GC_BEING_FORWARDED  = 0x3;  // ...11
}
