/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Define the constants manipulated by VM_CommonAllocatorHeader. <p>
 * Since we never do a GC, we don't need any header bits/bytes.
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
   * How many bits does this GC system require?
   */
  static final int REQUESTED_BITS = 0;

  /**
   * We don't require a side mark array for bootimage objects.
   */
  static final boolean USE_SIDE_MARK_VECTOR = false;

  /*
   * Values that VM_CommonAllocatorHeader requires that we define.
   */
  static final int GC_BARRIER_BIT_IDX  = -1; // NOT USED;
  static final int GC_FORWARDED        = -1; // NOT USED;
  static final int GC_BEING_FORWARDED  = -1; // NOT USED;
}
