/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility;

import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

import org.mmtk.vm.gcspy.AbstractDriver;

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
public final class BumpPointer extends Allocator 
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
  public BumpPointer(MonotoneVMResource vmr) {
    vmResource = vmr;
    reset();
  }

  public void reset () {
    cursor = VM_Address.zero();
    limit = VM_Address.zero();
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
   * @param bytes The number of bytes allocated
   * @param align The requested alignment
   * @param offset The offset from the alignment 
   * @return The address of the first byte of the allocated region
   */
  final public VM_Address alloc(int bytes, int align, int offset) 
    throws VM_PragmaInline {
    VM_Address oldCursor = alignAllocation(cursor, align, offset);
    VM_Address newCursor = oldCursor.add(bytes);
      if (newCursor.GT(limit))
      return allocSlow(bytes, align, offset);
    cursor = newCursor;
    return oldCursor;
  }

  final protected VM_Address allocSlowOnce(int bytes, int align, int offset, 
                                           boolean inGC) {
    VM_Extent chunkSize = VM_Word.fromIntZeroExtend(bytes).add(CHUNK_MASK).and(CHUNK_MASK.not()).toExtent();
    VM_Address start = ((MonotoneVMResource)vmResource).acquire(Conversions.bytesToPages(chunkSize));
    if (start.isZero())
      return start;

    // check for (dis)contiguity with previous chunk
    if (limit.NE(start)) cursor = start;
    limit = start.add(chunkSize);
    return alloc(bytes, align, offset);
  }

  public void show() {
    Log.write("cursor = "); Log.write(cursor);
    Log.write(" limit = "); Log.writeln(limit);
  }

  /**
   * Gather data for GCSpy
   * @param event The GCSpy event
   * @param driver the GCSpy driver for this space
   */
  public void gcspyGatherData(int event, AbstractDriver driver) {
    vmResource.gcspyGatherData(event, driver);
  }

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
  private static final VM_Word CHUNK_MASK = VM_Word.one().lsh(LOG_CHUNK_SIZE).sub(VM_Word.one());
}
