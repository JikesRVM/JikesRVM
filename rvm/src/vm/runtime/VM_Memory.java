/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Low level memory management functions.
 *
 * Note that this class is "uninterruptible" - calling its methods will never 
 * cause the current thread to yield the cpu to another thread (one that
 * might cause a gc, for example).
 *
 * @author Dave Grove
 * @author Derek Lieber
 */
class VM_Memory implements VM_Uninterruptible {

  ////////////////////////
  // (1) Utilities for copying/filling/zeroing memory
  ////////////////////////
  /** 
   * How many bytes is considered large enough to justify the transition to
   * C code to use memcpy?
   */
  private static final int NATIVE_THRESHOLD = 256; 

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   * Assumptions: src != dst || (scrPos >= dstPos + 4) and
   *              src and dst are 8Bit arrays.
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  static void arraycopy8Bit(Object src, int srcPos, Object dst, int dstPos, int len) {
    VM_Magic.pragmaInline();
    if (len > NATIVE_THRESHOLD) {
      memcopy(VM_Magic.objectAsAddress(dst) + dstPos, 
	      VM_Magic.objectAsAddress(src) + srcPos, 
	      len);
    } else {
      if (len >= 4 && (srcPos & 0x3) == (dstPos & 0x3)) {
	// alignment is the same
	int byteStart = srcPos;
	int wordStart = (srcPos + 3) & ~0x3;
	int wordEnd = (srcPos + len) & ~0x3;
	int byteEnd = srcPos + len;
	int startDiff = wordStart - byteStart;
	int endDiff = byteEnd - wordEnd;
	int wordLen = wordEnd - wordStart;
	if (startDiff == 3) {
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	} else if (startDiff == 2) {
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	} else if (startDiff == 1) {
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	}
	internalAligned32Copy(VM_Magic.objectAsAddress(dst) + dstPos,
			      VM_Magic.objectAsAddress(src) + srcPos,
			      wordLen);
	srcPos += wordLen;
	dstPos += wordLen;
	if (endDiff == 3) {
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	} else if (endDiff == 2) {
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	} else if (endDiff == 1) {
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	}	  
      } else {
	for (int i=0; i<len; i++) {
	  VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
	}
      }
    }
  }    

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   * Assumption src != dst || (srcPos >= dstPos + 2).
   * 
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  static void arraycopy(short[] src, int srcPos, short[] dst, int dstPos, int len) {
    VM_Magic.pragmaInline();
    if (len > (NATIVE_THRESHOLD>>1)) {
      memcopy(VM_Magic.objectAsAddress(dst) + (dstPos<<1), 
	      VM_Magic.objectAsAddress(src) + (srcPos<<1), 
	      len<<1);
    } else {
      if (len > 1 && (srcPos & 0x1) == (dstPos & 0x1)) {
	// alignment is the same
	int byteStart = srcPos<<1;
	int wordStart = (byteStart + 3) & ~0x3;
	int wordEnd = (byteStart + (len<<1)) & ~0x3;
	int byteEnd = byteStart + (len<<1);
	int startDiff = wordStart - byteStart;
	int endDiff = byteEnd - wordEnd;
	int wordLen = wordEnd - wordStart;
	if (startDiff != 0) {
	  dst[dstPos++] = src[srcPos++];
	}
	internalAligned32Copy(VM_Magic.objectAsAddress(dst) + (dstPos<<1),
			      VM_Magic.objectAsAddress(src) + (srcPos<<1),
			      wordLen);
	wordLen = wordLen >>> 1;
	srcPos += wordLen;
	dstPos += wordLen;
	if (endDiff != 0) {
	  dst[dstPos++] = src[srcPos++];
	}	  
      } else {
	for (int i=0; i<len; i++) {
	  dst[dstPos+i] = src[srcPos+i];
	}
      }
    }
  }    

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   * Assumption src != dst || (srcPos >= dstPos + 2).
   * 
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  static void arraycopy(char[] src, int srcPos, char[] dst, int dstPos, int len) {
    VM_Magic.pragmaInline();
    if (len > (NATIVE_THRESHOLD>>1)) {
      memcopy(VM_Magic.objectAsAddress(dst) + (dstPos<<1), 
	      VM_Magic.objectAsAddress(src) + (srcPos<<1), 
	      len<<1);
    } else {
      if (len > 1 && (srcPos & 0x1) == (dstPos & 0x1)) {
	// alignment is the same
	int byteStart = srcPos<<1;
	int wordStart = (byteStart + 3) & ~0x3;
	int wordEnd = (byteStart + (len<<1)) & ~0x3;
	int byteEnd = byteStart + (len<<1);
	int startDiff = wordStart - byteStart;
	int endDiff = byteEnd - wordEnd;
	int wordLen = wordEnd - wordStart;
	if (startDiff != 0) {
	  dst[dstPos++] = src[srcPos++];
	}
	internalAligned32Copy(VM_Magic.objectAsAddress(dst) + (dstPos<<1),
			      VM_Magic.objectAsAddress(src) + (srcPos<<1),
			      wordLen);
	wordLen = wordLen >>> 1;
	srcPos += wordLen;
	dstPos += wordLen;
	if (endDiff != 0) {
	  dst[dstPos++] = src[srcPos++];
	}	  
      } else {
	for (int i=0; i<len; i++) {
	  dst[dstPos+i] = src[srcPos+i];
	}
      }
    }
  }    


  /**
   * Copy numbytes from src to dst.
   * Assumption either the ranges are non overlapping, or src >= dst + 4.
   * Also, src and dst are 4 byte aligned and numBytes is a multiple of 4.
   * @param dst the destination addr
   * @param src the source addr
   * @param numBytes the number of bytes top copy
   */
  static void aligned32Copy(int dst, int src, int numBytes) {
    VM_Magic.pragmaInline();
    if (numBytes > NATIVE_THRESHOLD) {
      memcopy(dst, src, numBytes);
    } else {
      internalAligned32Copy(dst, src, numBytes);
    }
  }

  /**
   * Copy numbytes from src to dst.
   * Assumption either the ranges are non overlapping, or src >= dst + 4.
   * @param dst the destination addr
   * @param src the source addr
   * @param numBytes the number of bytes top copy
   */
  private static void internalAligned32Copy(int dst, int src, int numBytes) {
    VM_Magic.pragmaInline();
    for (int i=0; i<numBytes; i+= 4) {
      VM_Magic.setMemoryWord(dst + i, VM_Magic.getMemoryWord(src + i));
    }
  }


  /**
   * Copy a region of memory.
   * Taken:    destination address
   *           source address
   *           number of bytes to copy
   * Returned: nothing
   * Assumption: source and destination regions do not overlap
   */
  static void memcopy(int dst, int src, int cnt) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall3(bootRecord.sysCopyIP, dst, src, cnt);
  }
   
  /**
   * Fill a region of memory.
   * Taken:    destination address
   *           pattern
   *           number of bytes to fill with pattern
   * Returned: nothing
   */
  static void fill(int dst, byte pattern, int cnt) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall3(bootRecord.sysFillIP, dst, pattern, cnt);
  }

  /**
   * Zero a region of memory.
   * Taken:    start of address range (inclusive)
   *           end of address range   (exclusive)
   * Returned: nothing
   */
  static void zero(int start, int end) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall2(bootRecord.sysZeroIP, start, end - start);
  }
   
  /**
   * Zero a range of pages of memory.
   * Taken:    start address       (must be a page address)
   *           number of bytes     (must be multiple of page size)
   * Returned: nothing
   */
  static void zeroPages(int start, int len) {
    if (VM.VerifyAssertions) VM.assert((start & ~4095) == start && (len & ~4095) == len);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall2(bootRecord.sysZeroPagesIP, start, len);
  }

  ////////////////////////
  // (2) Cache management
  ////////////////////////

  /**
   * Synchronize a region of memory: force data in dcache to be written out to main 
   * memory so that it will be seen by icache when instructions are fetched back.
   * Taken:    start of address range
   *           size of address range (bytes)
   * Returned: nothing
   */
  static void sync(int address, int size) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall2(bootRecord.sysSyncCacheIP, address, size);
  }

  
  ////////////////////////
  // (3) MMap
  ////////////////////////

  // constants for protection and mapping calls
  static final int PROT_READ  = 1;
  static final int PROT_WRITE = 2;
  static final int PROT_EXEC  = 4;
  static final int PROT_NONE  = 0;

  static final int MAP_SHARED    =  1;
  static final int MAP_PRIVATE   =  2;
  static final int MAP_FIXED     = 16;
  static final int MAP_ANONYMOUS = 32;

  static final int MS_ASYNC      = 1;
  static final int MS_INVALIDATE = 2;
  static final int MS_SYNC       = 4;

  /**
   * Do mmap general memory mapping call (not implemented)
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   *           protection (int)
   *           flags (int)
   *           fd (int)
   *           offset (long)
   * Returned: ADDRESS (of region)
   */
  static ADDRESS mmap(ADDRESS address, ADDRESS size, 
		      int prot, int flags, int fd, long offset) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address &&
		(size    & ~4095) == size    &&
		(offset  & ~4095) == offset );
    return -1;  // not implemented: requires new magic for 6 args, etc.
    // VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    // return VM.sysCallXXX(bootRecord.sysMMapIP, address, size, prot, flags, fd, offset);
  }

  /**
   * Do mmap file memory mapping call
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   *           file name (char *)
   * Returned: ADDRESS (of region)
   */
  static ADDRESS mmapFile(ADDRESS address, ADDRESS size, int fd, int prot) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address && (size    & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall4(bootRecord.sysMMapGeneralFileIP, address, size, fd, prot);
  }

  /**
   * Do mmap non-file memory mapping call
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   *           protection (int)
   *           flags (int)
   * Returned: ADDRESS (of region)
   */
  static ADDRESS mmap(ADDRESS address, ADDRESS size, int prot, int flags) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address &&	(size    & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall4(bootRecord.sysMMapNonFileIP, address, size, prot, flags);
  }

  /**
   * Do mmap demand zero fixed address memory mapping call
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   * Returned: ADDRESS (of region)
   */
  static ADDRESS mmap(ADDRESS address, ADDRESS size) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address && (size & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall2(bootRecord.sysMMapDemandZeroFixedIP, address, size);
  }

  /**
   * Do mmap demand zero any address memory mapping call
   * Taken:    size of address range (ADDRESS)
   * Returned: ADDRESS (of region)
   */
  static ADDRESS mmap(ADDRESS size) {
    if (VM.VerifyAssertions) VM.assert((size & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall1(bootRecord.sysMMapDemandZeroAnyIP, size);
  }

  /**
   * Do munmap system call
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   * Returned: true if success
   */
  static boolean munmap(ADDRESS address, ADDRESS size) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address && (size & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall2(bootRecord.sysMUnmapIP, address, size) == 0;
  }

  /**
   * Do mprotect system call
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   *           protection (int)
   * Returned: true if success
   */
  static boolean mprotect(ADDRESS address, ADDRESS size, int prot) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address && (size & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysMProtectIP, address, size, prot) == 0;
  }

  /**
   * Do msync system call
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   *           flags (int)
   * Returned: true if success
   */
  static boolean msync(ADDRESS address, ADDRESS size, int flags) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address && (size & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysMSyncIP, address, size, flags) == 0;
  }

  /**
   * Do madvise system call (UNIMPLEMENTED IN LINUX)
   * Taken:    start of address range (ADDRESS)
   *           size of address range (ADDRESS)
   *           advice (int)
   * Returned: true if success
   */
  static boolean madvise(ADDRESS address, ADDRESS size, int advice) {
    if (VM.VerifyAssertions)
      VM.assert((address & ~4095) == address && (size & ~4095) == size);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysMAdviseIP, address, size, advice) == 0;
  }

  /**
   * Do getpagesize call
   * Taken:    none
   * Returned: page size
   */
  static int getpagesize() {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall0(bootRecord.sysGetPageSizeIP);
  }

  
  // test routine
  static void test_mmap() {
    int psize = VM_Memory.getpagesize();
    int size = 1024 * 1024;
    int ro = VM_Memory.PROT_READ;
    int base = 0x38000000;
    int addr = VM_Memory.mmap(base, size);
    VM.sysWrite("page size = ");
    VM.sysWrite(psize);
    VM.sysWrite("\n");
    VM.sysWrite("requested ");
    VM.sysWrite(size);
    VM.sysWrite(" bytes at ");
    VM.sysWriteHex(base);
    VM.sysWrite("\n");
    VM.sysWrite("mmap call returned ");
    VM.sysWriteHex(addr);
    VM.sysWrite("\n");
    if (addr != -1) {
      VM_Magic.setMemoryWord(addr, 17);
      if (VM_Magic.getMemoryWord(addr) == 17) {
	VM.sysWrite("write and read in memory region succeeded\n");
      } else {
	VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.mprotect(addr, size, ro)) {
	VM.sysWrite("mprotect failed\n");
      } else {
	VM.sysWrite("mprotect succeeded!\n");
      }
      if (VM_Magic.getMemoryWord(addr) == 17) {
	VM.sysWrite("read in memory region succeeded\n");
      } else {
	VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.munmap(addr, size)) {
	VM.sysWrite("munmap failed\n");
      } else {
	VM.sysWrite("munmap succeeded!\n");
      }
    }

    addr = VM_Memory.mmap(size);
    VM.sysWrite("requested ");
    VM.sysWrite(size);
    VM.sysWrite(" bytes at any address\n");
    VM.sysWrite("mmap call returned ");
    VM.sysWriteHex(addr);
    VM.sysWrite("\n");

    if (addr != -1) {
      VM_Magic.setMemoryWord(addr, 17);
      if (VM_Magic.getMemoryWord(addr) == 17) {
	VM.sysWrite("write and read in memory region succeeded\n");
      } else {
	VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.mprotect(addr, size, ro)) {
	VM.sysWrite("mprotect failed\n");
      } else {
	VM.sysWrite("mprotect succeeded!\n");
      }

      if (VM_Magic.getMemoryWord(addr) == 17) {
	VM.sysWrite("read in memory region succeeded\n");
      } else {
	VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.munmap(addr, size)) {
	VM.sysWrite("munmap failed\n");
      } else {
	VM.sysWrite("munmap succeeded!\n");
      }
    }

    VM.sysWrite("mmap tests done\n");
  }


  static int align (int address, int alignment) {
      VM_Magic.pragmaInline();
      return (address + alignment - 1) & ~(alignment - 1);
  }

}
