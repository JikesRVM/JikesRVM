/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Dick Attanasio
 * @author Stephen Smith
 */
interface VM_GCConstants {

  static final int WORDSIZE = 4;
  static final int LG_WORDSIZE = 2;

  /*
   * Data Fields that control the allocation of memory
   * subpools for the heap; allocate from 
   * fixed size blocks in subpools; never-copying collector
   */
  static final int[]	GC_SIZEVALUES = {
                                 VM_JavaHeader.MINIMUM_HEADER_SIZE + 4,
				 VM_JavaHeader.MINIMUM_HEADER_SIZE + 8,
				 VM_JavaHeader.MINIMUM_HEADER_SIZE + 12,
				 32,
				 64,
				 84,
				 128,
				 256,
                                 512,
  	                         524,
				 1024,
				 2048
                                 };
  static final int	        GC_SIZES          = GC_SIZEVALUES.length;
  static final int		GC_MAX_SMALL_SIZE = GC_SIZEVALUES[GC_SIZEVALUES.length-1];
  static final int  		LOG_GC_BLOCKSIZE  = 14;
  static final int		GC_BLOCKSIZE      = 1 << LOG_GC_BLOCKSIZE;
  static final int 		GC_BLOCKALIGNMENT = GC_BLOCKSIZE;

  /** number of gc cycles for new objects to become old */
  static final int		GC_STEPS = 2;	
  static final int 		GC_OLD   = GC_STEPS - 1;

  // N.B. GC_THRESHHOLD changes when GC_OLD changes.
  static final int		GC_THRESHHOLD = 2 * GC_OLD - 1;
  static final int		GC_REMEMBERED_COHORTS = GC_OLD;

   // initial number of old objects to allocate for
  static final int		GC_INITIALOLDOBJECTS = 1024; 
  static final int		GC_NEW_BLOCK_DEPTH = 10;

  static final int		GC_INITIAL_LARGE_SPACE_PAGES = 100;
  static final int		GC_LARGE_SIZES = 20;

  /**
   * When true (the default), 
   * mutator allocations are done via VM_Chunk.allocateChunk1
   */
  static final boolean PROCESSOR_LOCAL_ALLOCATE = true;

  /**
   * When true (the default), Collector Threads 
   * acquire space to copy objects via VM_Chunk.allocateChunk2
   */
  static final boolean PROCESSOR_LOCAL_MATURE_ALLOCATE = true;

  // set at most one of the following 2 zeroing options on, if neither is
  // on then one processor zeros at end of GC (a bad idea, keep for comparison)

  /**
   * When true, all collector threads zero the space for new allocations
   * in parallel, at the end of a collection, before mutators execute.
   */
  static final boolean ZERO_NURSERY_IN_PARALLEL = false;

  /**
   * When true (the default), no zeroing is done at the end of a collection.
   * Instead, each VM_Processor zeros the chunks of heap it acquires in order
   * to satisfy allocation requests.
   */
  static final boolean ZERO_CHUNKS_ON_ALLOCATION = true;
}				

