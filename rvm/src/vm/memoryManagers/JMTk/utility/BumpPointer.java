/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

//-if RVM_WITH_GCSPY
import uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy.AbstractDriver;
//-endif

/**
 * This class implements a simple bump pointer allocator.  The
 * allocator operates in <code>BLOCK</code> sized units.  Intra-block
 * allocation is fast, requiring only a load, addition comparison and
 * store.  If a block boundary is encountered the allocator will
 * request more memory (virtual and actual).
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class BumpPointer extends Allocator 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource from which this bump
   * pointer will acquire virtual memory.
   * @param mr The memory resource from which this bump pointer will
   * acquire memory.
   */
  BumpPointer(MonotoneVMResource vmr) {
    vmResource = vmr;
    reset();
  }

  public void reset () {
    cursor = INITIAL_CURSOR_VALUE;
    limit = INITIAL_LIMIT_VALUE;
  }

  /**
   * Re-associate this bump pointer with a different virtual memory
   * resource.  Reset the bump pointer so that it will use this virtual
   * memory resource on the next call to <code>alloc</code>.
   *
   * @param vmr The virtual memory resouce with which this bump
   * pointer is to be associated.
   */
  public void rebind(MonotoneVMResource vmr) {
    reset();
    vmResource = vmr;
  }

  /**
   * Allocate space for a new object.  This is frequently executed code and 
   * the coding is deliberaetly sensitive to the optimizing compiler.
   * After changing this, always check the IR/MC that is generated.
   *
   * @param isScalar Is the object to be allocated a scalar (or array)?
   * @param bytes The number of bytes allocated
   * @return The address of the first byte of the allocated region
   */
  final public VM_Address alloc(boolean isScalar, int bytes) 
    throws VM_PragmaInline {
    VM_Address oldCursor = cursor;
    VM_Address newCursor = oldCursor.add(bytes);
    if (useLimit) {
      if (newCursor.GT(limit))
        return allocSlow(isScalar, bytes);
    } else {
      VM_Address tmp = oldCursor.toWord().xor(newCursor.toWord()).toAddress();
      if (tmp.GT(TRIGGER))
        return allocSlow(isScalar, bytes);
    }
    cursor = newCursor;
    return oldCursor;
  }

  final protected VM_Address allocSlowOnce(boolean isScalar, int bytes, 
                                           boolean inGC) {
    int chunkSize = ((bytes + CHUNK_SIZE - 1) >>> LOG_CHUNK_SIZE) << LOG_CHUNK_SIZE;
    VM_Address start = ((MonotoneVMResource)vmResource).acquire(Conversions.bytesToPages(chunkSize));
    if (start.isZero())
      return start;
    Memory.zero(start, VM_Extent.fromIntZeroExtend(chunkSize));

    // check for (dis)contiguity with previous chunk
    if (limit.NE(start)) cursor = start;
    limit = start.add(chunkSize);
    return alloc(isScalar, bytes);
  }

  public void show() {
    Log.write("cursor = "); Log.write(cursor);
    Log.write(" limit = "); Log.writeln(limit);
  }

  //-if RVM_WITH_GCSPY
  /**
   * Gather data for GCSpy
   * @param event The GCSpy event
   * @param driver the GCSpy driver for this space
   */
  void gcspyGatherData(int event, AbstractDriver driver) {
    vmResource.gcspyGatherData(event, driver);
  }
  //-endif

  /****************************************************************************
   *
   * Instance variables
   */
  private VM_Address cursor;
  private VM_Address limit;
  private MonotoneVMResource vmResource;

  /****************************************************************************
   *
   * Final class variables (aka constants)
   *
   * Must ensure the bump pointer will go through slow path on (first)
   * alloc of initial value
   */
  private static final int LOG_CHUNK_SIZE = VMResource.LOG_BYTES_IN_PAGE + 3;
  private static final int CHUNK_SIZE = 1 << LOG_CHUNK_SIZE;
  private static final VM_Address TRIGGER = VM_Word.one().lsh(LOG_CHUNK_SIZE).sub(VM_Word.one()).toAddress();
  private static final VM_Address INITIAL_CURSOR_VALUE = TRIGGER;
  private static final VM_Address INITIAL_LIMIT_VALUE = INITIAL_CURSOR_VALUE;
  private static final boolean useLimit = true;
}
