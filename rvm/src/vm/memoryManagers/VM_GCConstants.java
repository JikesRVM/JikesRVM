/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Dick Attanasio
 * @author Stephen Smith
 */
interface VM_GCConstants

   {

   static final int WORDSIZE = 4;
   static final int LG_WORDSIZE = 2;

   //
   // Data Fields that control the allocation of memory
   //
   //subpools for the heap; allocate from 
   // fixed size blocks in subpools; never-copying collector
   static final int		GC_SIZES = 12;	// number of subpools:
   static final int[]		GC_SIZEVALUES = {
				 12,
				 16,
				 20,
				 32,
				 64,
				 84,
				 128,
				 256,
         512,
  	     524,
				 1024,
				 2048
				} ;		// size of slots in each subpool
   static final int		GC_MAX_SMALL_SIZE = 2048;

//   static final int		GC_BLOCKSIZE     = 8 * 1024;  // 8k blocksize
//   static final int  		LOG_GC_BLOCKSIZE = 13;	       // keep consistent w/ prev
     static final int		GC_BLOCKSIZE     = 16 * 1024;  // 8k blocksize
     static final int  		LOG_GC_BLOCKSIZE = 14;	       // keep consistent w/ prev

//   static final int		GC_BLOCKSIZE     = 32 * 1024;  // 32K blocksize
//   static final int  		LOG_GC_BLOCKSIZE = 15;	       // keep consistent w/ prev
   static final int 		GC_BLOCKALIGNMENT  = GC_BLOCKSIZE;  // for now


   static final int		GC_STEPS = 2;	// number of gc cycles for new
						// objects to become old
   static final int 		GC_OLD   = GC_STEPS - 1;
// N.B. GC_THRESHHOLD changes when GC_OLD changes.
   static final int		GC_THRESHHOLD = 2 * GC_OLD - 1;
   static final int		GC_REMEMBERED_COHORTS = GC_OLD;

   // initial number of old objects to allocate for
   static final int		GC_INITIALOLDOBJECTS = 1024; 
   static final int		GC_NEW_BLOCK_DEPTH = 10;
// Number of pages to allocate for large objects
// Use 3082 to run cases for optimizing compiler
//
//     static final int		GC_LARGESPACEPAGES = 3072;
     static final int		GC_INITIAL_LARGE_SPACE_PAGES = 100;
 
// use 8meg largeobjectspace as default
// static final int		GC_LARGESPACEPAGES = 1024 + 1024 ;
   static final int		GC_LARGE_SIZES = 20;
   }				

