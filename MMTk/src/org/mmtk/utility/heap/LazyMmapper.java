/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.JMTk.Conversions;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * This class implements lazy mmapping of virtual memory.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class LazyMmapper implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public static methods 
  //
  //

  public static boolean verbose = false;
  public static Lock lock = new Lock("LazyMapper");

  public static void ensureMapped(VM_Address start, int blocks) {
    int startChunk = Conversions.addressToMmapChunks(start);       // round down
    int endChunk = Conversions.addressToMmapChunks(start.add(Conversions.blocksToBytes(blocks)));       // round down
    lock.acquire();
    for (int chunk=startChunk; chunk <= endChunk; chunk++) {
      VM_Address mmapStart = Conversions.mmapChunksToAddress(chunk);
      if (mapped[chunk] == UNMAPPED) {
	int errno = VM_Interface.mmap(mmapStart, MMAP_CHUNK_SIZE);
	if (errno != 0) {
	  lock.release();
	  VM.sysWrite("ensureMapped failed with errno ", errno);
	  VM.sysWriteln(" on address ", mmapStart);
	  VM._assert(false);
	}
	else {
	  if (verbose) {
	    VM.sysWrite("mmap succeeded at chunk ", chunk);  VM.sysWrite("  ", mmapStart);
	    VM.sysWriteln(" with len = ", MMAP_CHUNK_SIZE);
	  }
	}
      }
      if (mapped[chunk] == PROTECTED) {
	if (!VM_Interface.munprotect(mmapStart, MMAP_CHUNK_SIZE)) {
	  lock.release();
 	  VM.sysFail("LazyMmapper.ensureMapped (unprotect) failed");
	}
	else {
	  if (verbose) {
	    VM.sysWrite("munprotect succeeded at chunk ", chunk);  VM.sysWrite("  ", mmapStart);
	    VM.sysWriteln(" with len = ", MMAP_CHUNK_SIZE);
	  }
	}
      }
      mapped[chunk] = MAPPED;
    }
    lock.release();
  }

  public static void protect(VM_Address start, int blocks) {
    int startChunk = Conversions.addressToMmapChunks(start);       // round down
    int chunks = Conversions.blocksToMmapChunks(blocks); // round up
    int endChunk = startChunk + chunks;
    lock.acquire();
    for (int chunk=startChunk; chunk < endChunk; chunk++) {
      if (mapped[chunk] == MAPPED) {
	VM_Address mmapStart = Conversions.mmapChunksToAddress(chunk);
	if (!VM_Interface.mprotect(mmapStart, MMAP_CHUNK_SIZE)) {
	  lock.release();
	  VM.sysFail("LazyMmapper.mprotect failed");
	}
	else {
	  if (verbose) {
	    VM.sysWrite("mprotect succeeded at chunk ", chunk);  VM.sysWrite("  ", mmapStart);
	    VM.sysWriteln(" with len = ", MMAP_CHUNK_SIZE);
	  }
	}
	mapped[chunk] = PROTECTED;
      }
      else {
	if (VM.VerifyAssertions) VM._assert(mapped[chunk] == PROTECTED);
      }
    }
    lock.release();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private static methods and variables
  //
  final public static byte UNMAPPED = 0;
  final public static byte MAPPED = 1;
  final public static byte PROTECTED = 2;   // mapped but not accessible
  private static byte mapped[];
  final public static int LOG_MMAP_CHUNK_SIZE = 20;            
  final public static int MMAP_CHUNK_SIZE = 1 << LOG_MMAP_CHUNK_SIZE;   // the granularity VMResource operates at
  final private static int MMAP_NUM_CHUNKS = 1 << (Constants.LOG_ADDRESS_SPACE - LOG_MMAP_CHUNK_SIZE);
  final public  static int MMAP_CHUNK_MASK = ~((1 << LOG_MMAP_CHUNK_SIZE) - 1);

  private static String chunkStateToString(byte state) {
    switch (state) {
    case UNMAPPED: return "UNMAPPED";
    case MAPPED: return "MAPPED";
    case PROTECTED: return "PROTECTED";
    }
    return "UNKNOWN";
  }

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    mapped = new byte[MMAP_NUM_CHUNKS];
    for (int c = 0; c < MMAP_NUM_CHUNKS; c++) {
      mapped[c] = UNMAPPED;
    }
  }

}

