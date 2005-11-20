/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.mmtk.policy.ImmortalSpace;


import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Memory {

  /**
   * @return The lowest address in the heap.
   */
  public static Address HEAP_START() { return Address.zero(); }
  
  /**
   * @return The highest address in the heap.
   */
  public static Address HEAP_END() { return Address.zero(); }
  
  /**
   * Allows for the VM to reserve space between HEAP_START()
   * and AVAILABLE_START() for its own purposes.  MMTk should
   * expect to encounter objects in this range, but may not 
   * allocate in this range.
   * 
   * MMTk expects the virtual address space between AVAILABLE_START()
   * and AVAILABLE_END() to be contiguous and unmapped.
   * 
   * @return The low bound of the memory that MMTk can allocate.
   */
  public static Address AVAILABLE_START() { return Address.zero(); }

  /**
   * Allows for the VM to reserve space between HEAP_END()
   * and AVAILABLE_END() for its own purposes.  MMTk should
   * expect to encounter objects in this range, but may not 
   * allocate in this range.
   * 
   * MMTk expects the virtual address space between AVAILABLE_START()
   * and AVAILABLE_END() to be contiguous and unmapped.
   * 
   * @return The high bound of the memory that MMTk can allocate.
   */
  public static Address AVAILABLE_END() { return Address.zero(); }


  /**
   * Return the space associated with/reserved for the VM.  In the
   * case of Jikes RVM this is the boot image space.<p>
   *
   * @return The space managed by the virtual machine.  
   */
  public static ImmortalSpace getVMSpace() {
    return null;
  }

  /**
   * Global preparation for a collection.
   */
  public static void globalPrepareVMSpace() {}

  /**
   * Thread-local preparation for a collection.
   */
  public static void localPrepareVMSpace() {}

  /**
   * Thread-local post-collection work.
   */
  public static void localReleaseVMSpace() {}

  /**
   * Global post-collection work.
   */
  public static void globalReleaseVMSpace() {}

  /**
   * Sets the range of addresses associated with a heap.
   *
   * @param id the heap identifier
   * @param start the address of the start of the heap
   * @param end the address of the end of the heap
   */
  public static void setHeapRange(int id, Address start, Address end) {}

 /**
   * Maps an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return 0 if successful, otherwise the system errno
   */
  public static int mmap(Address start, int size) {
    return 0;
  }
  
  /**
   * Protects access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public static boolean mprotect(Address start, int size) {
    return false;
  }

  /**
   * Allows access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public static boolean munprotect(Address start, int size) {
    return false;
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param len Length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(Address start, Extent len) {}

  /**
   * Zero a range of pages of memory.
   * @param start Start of address range (must be a page address)
   * @param len Length in bytes of range (must be multiple of page size)
   */
  public static void zeroPages(Address start, int len) {}

  /**
   * Logs the contents of an address and the surrounding memory to the
   * error output.
   *
   * @param start the address of the memory to be dumped
   * @param beforeBytes the number of bytes before the address to be
   * included
   * @param afterBytes the number of bytes after the address to be
   * included
   */
  public static void dumpMemory(Address start, int beforeBytes,
                                int afterBytes) {}

  /**
   * Wait for preceeding cache flush/invalidate instructions to complete 
   * on all processors.  Ensures that all memory writes before this
   * point are visible to all processors.
   */
  public static void sync() throws InlinePragma {}

  /**
   * Wait for all preceeding instructions to complete and discard any 
   * prefetched instructions on this processor.  Also prevents the 
   * compiler from performing code motion across this point.
   */ 
  public static void isync() throws InlinePragma {}
}
