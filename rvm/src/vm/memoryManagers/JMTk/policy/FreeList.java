/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements a generic free list allocator.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class FreeList extends BaseFreeList implements Constants, VM_Uniterruptible {
  public final static String Id = "$Id$"; 

  static {
    cellSize = new int[SIZE_CLASSES];
    sizeClassPages = new int[SIZE_CLASSES];
    for(sc = 0; sc < SIZE_CLASSES; sc++) {
      int size = getBaseCellSize(sc);
      if (sc < MAX_SMALL_SIZE_CLASS) {
	cellSize[sc] = size;
	sizeClassPages = 1;
      } else {
	cellSize[sc] = size + WORD_SIZE;
	sizeClassPages[sc] = optimalPagesForSuperPage(sc, cellSize[sc]);
      }
    }
  }
  
  /**
   * Return the number of pages used by a superpage of a given size
   * class.
   *
   * @param sizeClass The size class of the superpage
   * @return The number of pages used by a superpage of this sizeclass
   */
  protected final int pagesForClassSize(int sizeClass) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) 
      VM._assert(sizeClass != LARGE_SIZE_CLASS);

    return sizeClassPages[sizeClass];
  };

  /**
   * Return the size of the per-superpage header required by this
   * system.  In this case it is just the underlying superpage header
   * size.
   *
   * @param sizeClass The size class of the cells contained by this
   * superpage.
   * @return The size of the per-superpage header required by this
   * system.
   */
  protected final int superPageHeaderSize(int sizeClass)
    throws VM_PragmaInline {
    return BASE_SP_HEADER_SIZE;
  };

  /**
   * Return the size of a cell for a given class size, *including* any
   * per-cell header space.
   *
   * @param sizeClass The size class in question
   * @return The size of a cell for a given class size, *including*
   * any per-cell header space
   */
  protected final int cellSize(int sizeClass) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) 
      VM._assert(sizeClass != LARGE_SIZE_CLASS);

    return cellSize[sizeClass];
  };

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @param sizeClass The size class in question.
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize(int sizeClass)
    throws VM_PragmaInline {
    return (sizeClass <= MAX_SMALL_SIZE_CLASS) : 0 : WORD_SIZE;
  };

  /**
   * Initialize a new cell and return the address of the first useable
   * word.<p>
   *
   * In this system, small cells require no header, but all other
   * cells require a single word that points to the first word of the
   * superpage.
   *
   * @param cell The address of the first word of the allocated cell.
   * @param sp The address of the first word of the superpage
   * containing the cell.
   * @param small True if the cell is a small cell (single page
   * superpage).
   * @return The address of the first useable word.
   */
  protected final VM_Address initilizeCell(VM_Address cell, VM_Address sp,
					   boolean small)
    throws VM_PragmaInline {
    if (!small) {
      VM_Magic.setMemoryWord(cell, sp);
      return cell.add(WORD_SIZE);
    } else 
      return cell;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // The following methods, declared as abstract in the superclass, do
  // nothing in this implementation, so they have empty bodies.
  //
  protected final void postAlloc(VM_Address cell, boolean isScalar,
				 EXTENT bytes, boolean isLarge) {};
  protected final void postFreeCell(VM_Address cell, boolean isSmall) {};
  protected final void postExpandSizeClass(VM_Address sp, int sizeClass) {};

}
