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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
abstract class SegregatedFreeList extends Allocator implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  protected static final VM_Address DEBUG_BLOCK = VM_Address.fromInt(0xffffffff);  // 0x5b098008
  protected static final int SIZE_CLASSES = 40;
  protected static final int FREE_LIST_HEADER_BYTES = WORD_SIZE;
  private static final int FREE_LIST_OFFSET = 0;
  private static final int FREE_LIST_BITS = BlockAllocator.MAX_BLOCK_LOG;
  private static final int SIZE_CLASS_BITS = 6;
  private static final int INUSE_BITS = 8;
  private static final int SIZE_CLASS_SHIFT = FREE_LIST_BITS;
  private static final int INUSE_SHIFT = FREE_LIST_BITS + SIZE_CLASS_BITS;
  protected static final int MIN_CELLS = 6;
  protected static final int MAX_CELLS = 99; //(1<<(INUSE_BITS-1))-1;
  private static final int FREE_LIST_MASK = (1<<FREE_LIST_BITS)-1;
  private static final int SIZE_CLASS_MASK = ((1<<SIZE_CLASS_BITS)-1)<<SIZE_CLASS_SHIFT;
  private static final int INUSE_MASK = ((1<<INUSE_BITS)-1)<<INUSE_SHIFT;

  protected static int[] cellSize;
  protected static byte[] blockSizeClass;
  protected static int[] blockHeaderSize;
  protected static int[] cellsInBlock;

  public static final boolean FRAGMENTATION_CHECK = true;
  protected static final boolean FRAG_VERBOSE = false;
  protected static int bytesAlloc;
  private long[] fragInuseCellBytes;
  private int[] fragUsedPages;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  protected BlockAllocator blockAllocator;
  protected VM_AddressArray freeList; 
  protected VM_AddressArray firstBlock;
  protected VM_AddressArray lastBlock;
  protected VM_AddressArray currentBlock;
  protected int []          cellsInUse;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource from which this free list
   * allocator will acquire virtual memory.
   * @param mr The memory resource against which memory consumption
   * for this free list allocator will be accounted.
   */
  SegregatedFreeList(FreeListVMResource vmr, MemoryResource mr, Plan plan) {
    blockAllocator = new BlockAllocator(vmr, mr, plan);
    freeList = new VM_AddressArray(SIZE_CLASSES);
    firstBlock = new VM_AddressArray(SIZE_CLASSES);
    lastBlock = new VM_AddressArray(SIZE_CLASSES);
    currentBlock = new VM_AddressArray(SIZE_CLASSES);
    cellsInUse = new int[SIZE_CLASSES];
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Allocate <code>bytes</code> contigious bytes of zeroed memory.<p>
   *
   * This code must be efficient and must compile easily.  Here we
   * minimize the number of calls to inlined functions, and force the
   * "slow path" (uncommon case) out of line to reduce pressure on the
   * compiler.  We have a call to an abstract method that allows
   * subclasses to customize post-allocation.
   *
   * @param isScalar True if the object to occupy this space will be a
   * scalar.
   * @param bytes The size of the object to occupy this space, in bytes.
   * @return The address of the first word of <code>bytes</code>
   * contigious bytes of zeroed memory.
   */
  public final VM_Address alloc(boolean isScalar, EXTENT bytes) 
    throws VM_PragmaInline {
    if (FRAGMENTATION_CHECK)
      bytesAlloc += bytes;

    int sizeClass = getSizeClass(bytes);
//     VM.sysWrite(bytes); VM.sysWrite(" "); VM.sysWrite(sizeClass); VM.sysWrite(" "); VM.sysWrite(VM_Magic.objectAsAddress(freeList)); VM.sysWrite(" "); VM.sysWrite(freeList[sizeClass]); VM.sysWrite("\n");
    VM_Address cell = freeList.get(sizeClass);
//     VM.sysWrite(cell); VM.sysWrite("-");
    if (!cell.isZero()) {
//     VM.sysWrite(cell); VM.sysWrite("-");
//     VM.sysWrite(getNextCell(cell)); VM.sysWrite("-");
      cellsInUse[sizeClass]++;
      freeList.set(sizeClass, getNextCell(cell));
      postAlloc(cell, currentBlock.get(sizeClass), sizeClass, bytes);
      Memory.zeroSmall(cell, bytes);
      return cell;
    } else
      return allocFromNextFreeList(isScalar, bytes, sizeClass);
  }

  abstract void postAlloc(VM_Address cell, VM_Address block, int sizeClass,
			  EXTENT bytes);

  /**
   * Allocate <code>bytes</code> contigious bytes of non-zeroed memory
   * in a context where the free list is empty. This will mean either
   * finding another block with a non-empty free list, or allocating a
   * new block.<p>
   *
   * This code should be relatively infrequently executed, so it is
   * forced out of line to reduce pressure on the compilation of the
   * core alloc routine.<p>
   *
   * Precondtion: The free list for <code>sizeClass</code> is exhausted.<p>
   *
   * Postconditions: A new cell has been allocated (not zeroed), and
   * the block containing the cell has been placed on the appropriate
   * free list data structures.  The free list itself is not updated
   * (the caller must do so).<p>
   *
   * @param sizeClass  The size class of the cell to be allocated.
   * @return The address of the first word of the <code>bytes</code>
   * contigious bytes of non-zerod memory.
   */
  private final VM_Address allocFromNextFreeList(boolean isScalar, int bytes,
						 int sizeClass)
    throws VM_PragmaNoInline {
    VM_Address current = currentBlock.get(sizeClass);
    if (!current.isZero()) {
      flushFreeList(current, sizeClass, VM_Address.zero());
      current = BlockAllocator.getNextBlock(current);
      while (!current.isZero()) {
	advanceToBlock(current, sizeClass);
	VM_Address cell = getFreeList(current);
	if (!cell.isZero()) {
	  currentBlock.set(sizeClass, current);
	  cellsInUse[sizeClass] = getInUse(current) + 1;
	  freeList.set(sizeClass, getNextCell(cell));
	  postAlloc(cell, currentBlock.get(sizeClass),
		    sizeClass, bytes);
	  Memory.zeroSmall(cell, bytes);
	  return cell;
	}
	current = BlockAllocator.getNextBlock(current);
      }
    }
    return allocSlow(isScalar, bytes);
  }

  protected final VM_Address allocSlowOnce (boolean isScalar, EXTENT bytes)
    throws VM_PragmaInline {
    int sizeClass = getSizeClass(bytes);
    VM_Address cell = expandSizeClass(sizeClass);
    if (cell.isZero())
      return VM_Address.zero();

    cellsInUse[sizeClass]++;
    freeList.set(sizeClass, getNextCell(cell));
    postAlloc(cell, currentBlock.get(sizeClass), sizeClass,
	      bytes);
    Memory.zeroSmall(cell, bytes);
    return cell;
  }

  abstract protected void advanceToBlock(VM_Address block, int sizeClass);

  private final VM_Address expandSizeClass(int sizeClass) 
    throws VM_PragmaInline {
    VM_Address block = blockAllocator.alloc(blockSizeClass.get(sizeClass));
    if (block.isZero())
      return VM_Address.zero();

    installNewBlock(block, sizeClass);

    int cellExtent = cellSize[sizeClass];
    VM_Address cursor = block.add(blockHeaderSize[sizeClass]);
    VM_Address sentinal = block.add(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
    VM_Address lastCell = VM_Address.zero();
    int cellCount = 0;
    //    VM.sysWrite("xs["); VM.sysWrite(block); VM.sysWrite(" "); VM.sysWrite(sizeClass); VM.sysWrite(" "); VM.sysWrite(blockHeaderSize[sizeClass]); VM.sysWrite(" "); VM.sysWrite(block.add(blockHeaderSize[sizeClass])); VM.sysWrite(" "); VM.sysWrite(sentinal); VM.sysWrite(" "); 
    while (cursor.add(cellExtent).LE(sentinal)) {
      //      VM.sysWrite(cursor); VM.sysWrite(" "); 
      setNextCell(cursor, lastCell); // fixme why did I want to pass classsize as third arg???
      lastCell = cursor;
      cursor = cursor.add(cellExtent);
      cellCount++;
    }
    //    VM.sysWrite("]\n");
    cellsInUse[sizeClass] = 0;
    postExpandSizeClass(block, sizeClass);
    
    if (VM.VerifyAssertions)
      VM._assert(!lastCell.isZero());
    return lastCell;
  }

  abstract void postExpandSizeClass(VM_Address block, int sizeClass);

  protected final int getNextCell(VM_Address cell)
    throws VM_PragmaInline {
    return VM_Magic.getMemoryWord(cell);
  }

  private final void setNextCell(VM_Address cell, VM_Address next)
    throws VM_PragmaInline {
    VM_Magic.setMemoryAddress(cell, next);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Freeing
  //

  protected final void free(VM_Address cell, VM_Address block, int sizeClass)
    throws VM_PragmaInline {
    //    VM.sysWrite("f<"); VM.sysWrite(cell); VM.sysWrite(">\n");
    addToFreeList(cell, block);
    if (decInUse(block) == 0)
      freeBlock(block, sizeClass);
  }

  private final void addToFreeList(VM_Address cell, VM_Address block)
    throws VM_PragmaInline {
    VM_Address next = getFreeList(block);
    setNextCell(cell, next); // fixme why did I want to pass classsize as third arg???
    setFreeList(block, cell);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Block management
  //

  private final void installNewBlock(VM_Address block, int sizeClass) 
    throws VM_PragmaInline {
    BlockAllocator.linkedListInsert(block, lastBlock.get(sizeClass));
    currentBlock.set(sizeClass, block);
    lastBlock.set(sizeClass, block);
    if (firstBlock.get(sizeClass).isZero())
      firstBlock.set(sizeClass, block);
  }

  protected final void freeBlock(VM_Address block, int sizeClass) 
    throws VM_PragmaInline {
    //   VM.sysWrite("fb<");VM.sysWrite(block); VM.sysWrite(">\n");
    VM_Address next = BlockAllocator.getNextBlock(block);
    VM_Address prev = BlockAllocator.getPrevBlock(block);
    BlockAllocator.unlinkBlock(block);
    if (firstBlock.get(sizeClass).EQ(block))
      firstBlock.set(sizeClass, next);
    if (currentBlock.get(sizeClass).EQ(block))
      currentBlock.set(sizeClass, next);
    if (lastBlock.get(sizeClass).EQ(block))
      lastBlock.set(sizeClass, prev);
    blockAllocator.free(block, blockSizeClass[sizeClass]);
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Size classes
  //

  /**
   * Get the size class for a given number of bytes.<p>
   *
   * We use size classes based on a worst case internal fragmentation
   * loss target of 1/8.  In fact, across sizes from 8 bytes to 512
   * the average worst case loss is 13.3%, giving an expected loss
   * (assuming uniform distribution) of about 7%.  We avoid using the
   * Lea class sizes because they were so numerous and therefore
   * liable to lead to excessive inter-class-size fragmentation.<p>
   * 
   * This method may segregate arrays and scalars (currently it does
   * not).
   *
   * @param isScalar True if the object to occupy the allocated space
   * will be a scalar (i.e. not a array).
   * @param bytes The number of bytes required to accommodate the
   * object to be allocated.
   * @return The size class capable of accomodating the allocation
   * request.  If the request is sufficiently large then
   * <code>LARGE_SIZE_CLASS</code> will be returned, indicating that
   * the request will not be satisfied by the freelist, but must be
   * dealt with explicitly as a large object.
   */
  protected static final int getSizeClass(EXTENT bytes)
    throws VM_PragmaInline {
    if (!((bytes > 0) && (bytes <= 8192))) {
      VM.sysWrite(bytes); VM.sysWrite("!\n");
    }

    if (VM.VerifyAssertions) VM._assert((bytes > 0) && (bytes <= 8192));

    int sz1 = bytes - 1;
    int offset = 0;
    return ((sz1 <=   63) ?      (sz1 >>  2): //    4 bytes apart
	    (sz1 <=  127) ? 12 + (sz1 >>  4): //   16 bytes apart
	    (sz1 <=  255) ? 16 + (sz1 >>  5): //   32 bytes apart
	    (sz1 <=  511) ? 20 + (sz1 >>  6): //   64 bytes apart
	    (sz1 <= 2047) ? 26 + (sz1 >>  8): //  256 bytes apart
	                    32 + (sz1 >> 10));// 1024 bytes apart
  }

  /**
   * Return the size of a basic cell (i.e. not including any cell
   * header) for a given size class.
   *
   * @param sc The size class in question
   * @return The size of a basic cell (i.e. not including any cell
   * header).
   */
  protected static final int getBaseCellSize(int sc) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert((sc >= 0) && (sc < SIZE_CLASSES));

    return ((sc < 16) ? (sc +  1) <<  2:
	    (sc < 19) ? (sc - 11) <<  4:
	    (sc < 23) ? (sc - 15) <<  5:
	    (sc < 27) ? (sc - 19) <<  6:
	    (sc < 33) ? (sc - 25) <<  8:
	                (sc - 31) << 10);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Misc
  //

  public final void flushFreeLists() {
    //    VM.sysWrite("Flushing free lists!\n");
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++)
      if (currentBlock[sizeClass] != 0) {
	VM_Address block = currentBlock.get(sizeClass);
	VM_Address cell = freeList.get(sizeClass);
	flushFreeList(block, sizeClass, cell);
	currentBlock.set(sizeClass, VM_Address.zero());
	freeList.set(sizeClass, VM_Address.zero());
      }
  }

  public final void restoreFreeLists() {
    //    VM.sysWrite("Flushing free lists!\n");
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      currentBlock.set(sizeClass, firstBlock.get(sizeClass));
      VM_Address block = firstBlock.get(sizeClass);
      if (block.isZero()) {
	freeList.set(sizeClass, VM_Address.zero());
	cellsInUse[sizeClass] = 0;
      } else {
	freeList.set(sizeClass, getFreeList(block));
	cellsInUse[sizeClass] = getInUse(block);
      }
    }
  }

  private final void flushFreeList(VM_Address block, int sizeClass, 
				   VM_Address cell) 
    throws VM_PragmaInline {
    setFreeListSizeClassAndInUse(block, cell, sizeClass, cellsInUse[sizeClass]);
  }

  private final void refreshFreeLists() {
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++)
      currentBlock.set(sizeClass, firstBlock.get(sizeClass));
  }

  private final void setFreeListSizeClassAndInUse(VM_Address block,
						  VM_Address cell,
						  int sizeClass, int inuse) 
    throws VM_PragmaInline {
    int value = (cell.toInt() & FREE_LIST_MASK) | (sizeClass << SIZE_CLASS_SHIFT) | (inuse << INUSE_SHIFT);
    VM_Magic.setMemoryWord(block.add(FREE_LIST_OFFSET), value);
    
    if (block.EQ(DEBUG_BLOCK)) {
      VM.sysWrite(inuse); VM.sysWrite(" "); VM.sysWrite(value); VM.sysWrite(" "); VM.sysWrite(inuse << INUSE_SHIFT); VM.sysWrite(" "); VM.sysWrite(value>>INUSE_SHIFT); VM.sysWrite(" sfliu\n");
    }
    if (VM.VerifyAssertions) {
      VM._assert(inuse == getInUse(block));
      if (cell != getFreeList(block)) {
	VM.sysWrite(cell); VM.sysWrite(" != "); VM.sysWrite(getFreeList(block)); VM.sysWrite("\n");
      }
      VM._assert(cell == getFreeList(block));
      VM._assert(sizeClass == getSizeClass(block));
    }
  }

  protected static final VM_Address getFreeList(VM_Address block) 
    throws VM_PragmaInline {
    int value = VM_Magic.getMemoryWord(block.add(FREE_LIST_OFFSET));
    value = (value & FREE_LIST_MASK);
    if (value == 0)
      return VM_Address.zero();
    else
      return VM_Address.fromInt(value | (block.toInt() & ~FREE_LIST_MASK));
  }

  private static final void setFreeList(VM_Address block, VM_Address cell)
    throws VM_PragmaInline {
    int value = VM_Magic.getMemoryWord(block.add(FREE_LIST_OFFSET));
    value = (cell.toInt() & FREE_LIST_MASK) | (value & ~FREE_LIST_MASK);
    VM_Magic.setMemoryWord(block.add(FREE_LIST_OFFSET), value);
  }

  protected static final int getSizeClass(VM_Address block) 
    throws VM_PragmaInline {
    int value = VM_Magic.getMemoryWord(block.add(FREE_LIST_OFFSET));
    value = (value & SIZE_CLASS_MASK)>>SIZE_CLASS_SHIFT;
    return value;
  }

  private static final void setSizeClass(VM_Address block, int sizeClass)
    throws VM_PragmaInline {
    int value = VM_Magic.getMemoryWord(block.add(FREE_LIST_OFFSET));
    value = (value & ~SIZE_CLASS_MASK) | (sizeClass<<SIZE_CLASS_SHIFT);
    VM_Magic.setMemoryWord(block.add(FREE_LIST_OFFSET), value);
  }

  protected static final int getInUse(VM_Address block) 
    throws VM_PragmaInline {
    int value = VM_Magic.getMemoryWord(block.add(FREE_LIST_OFFSET));
    if (block.EQ(DEBUG_BLOCK)) {
      VM.sysWrite(value); VM.sysWrite(" "); VM.sysWrite(value>>INUSE_SHIFT); VM.sysWrite(" giu\n");
    }
    return value >> INUSE_SHIFT;
  }

  private static final void setInUse(VM_Address block, int inuse) 
    throws VM_PragmaInline {
    int value = VM_Magic.getMemoryWord(block.add(FREE_LIST_OFFSET));
    value = (value & ~INUSE_MASK) | (inuse << INUSE_SHIFT);
    if (block.EQ(DEBUG_BLOCK)) {
      VM.sysWrite(block); VM.sysWrite(" "); VM.sysWrite(value); VM.sysWrite(" "); VM.sysWrite(value>>INUSE_SHIFT); VM.sysWrite(" siu\n");
    }
    VM_Magic.setMemoryWord(block.add(FREE_LIST_OFFSET), value);
  }

  private static final int decInUse(VM_Address block) 
    throws VM_PragmaInline {
    
    int value = VM_Magic.getMemoryWord(block.add(FREE_LIST_OFFSET));
    value -= (1<<INUSE_SHIFT);
    VM_Magic.setMemoryWord(block.add(FREE_LIST_OFFSET), value);
    if (VM.VerifyAssertions) {
      VM._assert((value>>INUSE_SHIFT) < (1<<INUSE_BITS));
      VM._assert((value>>INUSE_SHIFT) > 0);
    }
    return value>>INUSE_SHIFT;
  }
}
