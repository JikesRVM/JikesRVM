/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers;

import VM_JavaHeaderConstants;

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
  static final int NUM_BYTES_HEADER = 0;
  
  /**
   * The only thing this collector might need header bits for is to
   * mark the bootimage objects.  If there are any bits available, 
   * then request one.  Otherwise use the side mark array and request 0 bits.
   */
  static final int REQUESTED_BITS = VM_JavaHeaderConstants.NUM_AVAILABLE_BITS > 0 ? 1 : 0;

  /**
   * We use a side mark vector for bootimage objects iff
   * there are no available bits in the object header that we could use instead.
   */
  static final boolean USE_SIDE_MARK_VECTOR = REQUESTED_BITS == 0;

  /*
   * Values that VM_CommonAllocatorHeader requires that we define.
   */
  static final int GC_BARRIER_BIT_IDX  = -1;  // NOT USED;
  static final int GC_FORWARDED        = -1;  // NOT USED;
  static final int GC_BEING_FORWARDED  = -1;  // NOT USED;
}
