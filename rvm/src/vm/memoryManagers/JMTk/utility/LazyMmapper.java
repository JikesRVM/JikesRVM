/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.JMTk.Conversions;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Lock;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

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

  // There is a monotonicity assmption so that only updates require lock acquisition.
  //
  public static void ensureMapped(VM_Address start, int pages) {
    int startChunk = Conversions.addressToMmapChunksDown(start);
    int endChunk = Conversions.addressToMmapChunksUp(start.add(Conversions.pagesToBytes(pages)));
    for (int chunk=startChunk; chunk < endChunk; chunk++) {
      if (mapped[chunk] == MAPPED) continue;
      VM_Address mmapStart = Conversions.mmapChunksToAddress(chunk);
      lock.acquire();
      // might have become MAPPED here
      lock.check(100);
      if (mapped[chunk] == UNMAPPED) {
	lock.check(101);
	int errno = VM_Interface.mmap(mmapStart, MMAP_CHUNK_SIZE);
	lock.check(102);
	if (errno != 0) {
	  lock.release();
	  VM_Interface.sysWrite("ensureMapped failed with errno ",errno);
	  VM_Interface.sysWriteln(" on address ",mmapStart);
	  VM_Interface._assert(false);
	}
	else {
	  if (verbose) {
	    VM_Interface.sysWrite("mmap succeeded at chunk ",chunk);  VM_Interface.sysWrite("  ",mmapStart);
	    VM_Interface.sysWriteln(" with len = ",MMAP_CHUNK_SIZE);
	  }
	}
	lock.check(103);
      }
      if (mapped[chunk] == PROTECTED) {
	lock.check(201);
	if (!VM_Interface.munprotect(mmapStart, MMAP_CHUNK_SIZE)) {
	  lock.check(202);
	  lock.release();
	  VM_Interface.sysFail("LazyMmapper.ensureMapped (unprotect) failed");
	}
	else {
	  if (verbose) {
	    VM_Interface.sysWrite("munprotect succeeded at chunk ",chunk);  VM_Interface.sysWrite("  ",mmapStart);
	    VM_Interface.sysWriteln(" with len = ",MMAP_CHUNK_SIZE);
	  }
	}
      }
      lock.check(301);
      mapped[chunk] = MAPPED;
      lock.check(302);
      lock.release();
    }

  }

  public static void protect(VM_Address start, int pages) {
    int startChunk = Conversions.addressToMmapChunksDown(start); 
    int chunks = Conversions.pagesToMmapChunksUp(pages);
    int endChunk = startChunk + chunks;
    lock.acquire();
    for (int chunk=startChunk; chunk < endChunk; chunk++) {
      if (mapped[chunk] == MAPPED) {
	VM_Address mmapStart = Conversions.mmapChunksToAddress(chunk);
	if (!VM_Interface.mprotect(mmapStart, MMAP_CHUNK_SIZE)) {
	  lock.release();
	  VM_Interface.sysFail("LazyMmapper.mprotect failed");
	}
	else {
	  if (verbose) {
	    VM_Interface.sysWrite("mprotect succeeded at chunk ",chunk);  VM_Interface.sysWrite("  ",mmapStart);
	    VM_Interface.sysWriteln(" with len = ",MMAP_CHUNK_SIZE);
	  }
	}
	mapped[chunk] = PROTECTED;
      }
      else {
	if (VM_Interface.VerifyAssertions) VM_Interface._assert(mapped[chunk] == PROTECTED);
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

