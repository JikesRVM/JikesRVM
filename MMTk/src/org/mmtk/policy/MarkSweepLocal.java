/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
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

/**
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class MarkSweepLocal extends SegregatedFreeList
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static final int BITMAP_BASE = FREE_LIST_HEADER_BYTES;
  private static final int MARK_BITMAP_BASE = BITMAP_BASE;
  private static final int INUSE_BITMAP_BASE = BITMAP_BASE + WORD_SIZE;
  private static final boolean LAZY_SWEEP = true;
  private static final boolean MARK_BITS_ONLY = true;
  private static final int LOG_SET_SIZE = ((MARK_BITS_ONLY) ? 0 : 1);
  private static final int FRAG_PERCENTILES = 5;  // 20% iles

  protected static int[] bitmapSets;
  private static int[] finalWordBitmapMask; // FIXME needs to be VM_WordArray

  protected static int bytesLive;
  private static int lastBytesAlloc = 0;
  //  private static int MS_MUST_COLLECT_THRESHOLD = 1<<22;
  private static int MS_MUST_COLLECT_THRESHOLD = 1<<30;
  private static long inuse[];
  private static long used[];

  private static final boolean PARANOID = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private MarkSweepSpace msSpace;

  // fragmentation measurement
  private int utilization[];  
  private int allPreUtilization[][];
  private int allPostUtilization[][];
  private int totUtilization[];
  private int allPreBlocks[];
  private int allPostBlocks[];
  private int allPreUsedCells[];
  private int allPostUsedCells[];

  protected final boolean preserveFreeList() { return !MARK_BITS_ONLY; }
  protected final boolean maintainInUse() { return !MARK_BITS_ONLY; }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param space The mark-sweep space to which this allocator
   * instances is bound.  The space's VMResource and MemoryResource
   * are used to initialize the superclass.
   */
  MarkSweepLocal(MarkSweepSpace space, Plan plan) {
    super(space.getVMResource(), space.getMemoryResource(), plan);
    msSpace = space;
    utilization = new int[FRAG_PERCENTILES];
    totUtilization = new int[FRAG_PERCENTILES];
    allPreUtilization = new int[SIZE_CLASSES][FRAG_PERCENTILES]; 
    allPostUtilization = new int[SIZE_CLASSES][FRAG_PERCENTILES]; 
    allPreBlocks = new int[SIZE_CLASSES]; 
    allPostBlocks = new int[SIZE_CLASSES]; 
    allPreUsedCells = new int[SIZE_CLASSES]; 
    allPostUsedCells = new int[SIZE_CLASSES]; 
  }
  
  /**
   */
  static {
    cellSize = new int[SIZE_CLASSES];
    blockSizeClass = new byte[SIZE_CLASSES];
    bitmapSets = new int[SIZE_CLASSES];
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];
    finalWordBitmapMask = new int[SIZE_CLASSES]; // FIXME needs to be VM_WordArray
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
	int virtualCells = BlockAllocator.blockSize(blk)/cellSize[sc];
	int sets = (virtualCells+WORD_BITS-1)>>LOG_WORD_BITS;
	int avail = BlockAllocator.blockSize(blk) - FREE_LIST_HEADER_BYTES
	  - (sets<<(LOG_WORD_SIZE + LOG_SET_SIZE));
	int cells = avail/cellSize[sc];
	blockSizeClass[sc] = blk;
	bitmapSets[sc] = sets;
	cellsInBlock[sc] = cells;
	blockHeaderSize[sc] = FREE_LIST_HEADER_BYTES + (sets<<(LOG_WORD_SIZE+LOG_SET_SIZE));
	int remainder = cells & (WORD_BITS - 1);
	if (remainder == 0)
	  finalWordBitmapMask[sc] = (WORD_BITS - 1);
	else
	  finalWordBitmapMask[sc] = (1<<remainder)-1;

	if (((avail < PAGE_SIZE) && (cells*2 > MAX_CELLS)) ||
	    ((avail > (PAGE_SIZE>>1)) && (cells > MIN_CELLS)))
	  break;
      }
    }
    //    dumpSizeClassData();
  }

  private static void dumpSizeClassData() {
    VM_Interface.sysWrite("\nsc\tc size\tsets\tcells\tblk sc\thdr\tspace\twaste\tutilization\n");
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      VM_Interface.sysWrite(sc); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(cellSize[sc]); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(bitmapSets[sc]); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(cellsInBlock[sc]); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(blockSizeClass[sc]); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(blockHeaderSize[sc]); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(cellSize[sc]*cellsInBlock[sc]); VM_Interface.sysWrite("\t");
      //      VM.sysWrite(cellSize[sc]*cellsInBlock[sc]+blockHeaderSize[sc]+Block.BLOCK_HEADER_SIZE); VM.sysWrite("\t");
      VM_Interface.sysWrite(BlockAllocator.blockSize(blockSizeClass[sc]) - (cellSize[sc]*cellsInBlock[sc]+blockHeaderSize[sc])); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(((float) (cellSize[sc]*cellsInBlock[sc]))/((float)  BlockAllocator.blockSize(blockSizeClass[sc]))); VM_Interface.sysWrite("\n");
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   * @param block The block in which the new cell resides
   * @param sizeClass The size class of the new cell
   * @param isScalar True if the cell will be occupied by a scalar
   * @param bytes The size of the cell in bytes
   * @param inGC If true, this allocation is occuring with respect to
   * a space that is currently being collected.
   */
  protected final void postAlloc(VM_Address cell, VM_Address block,
				 int sizeClass, int bytes, boolean inGC) 
    throws VM_PragmaInline {

    if (inGC || maintainInUse()) {
      // establish bitmask & offset for this cell in the block
      int index = (cell.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
      int bitnumber = index & (WORD_BITS - 1);
      VM_Word mask = VM_Word.fromInt(1<<bitnumber);
      int offset = (index>>LOG_WORD_BITS)<<(LOG_WORD_SIZE + LOG_SET_SIZE);
      
      
      VM_Word word;
      VM_Address bitmapWord;
      if (maintainInUse()) {
	if (VM_Interface.VerifyAssertions) 
	  debugOffset(index, offset, sizeClass, cell, block);
	// set the inuse bit
	bitmapWord = block.add(INUSE_BITMAP_BASE + offset);
	word = VM_Magic.getMemoryWord(bitmapWord);
	word = word.or(mask);
	VM_Magic.setMemoryWord(bitmapWord, word);
      }
      if (inGC) {
	// set the mark bit
	bitmapWord = block.add(MARK_BITMAP_BASE + offset);
	word = VM_Magic.getMemoryWord(bitmapWord);
	word = word.or(mask);
	VM_Magic.setMemoryWord(bitmapWord, word);
      }
    }
  }

  private final void debugOffset(int index, int offset, int sizeClass,
				 VM_Address cell, VM_Address block) {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(maintainInUse());

    if (!((INUSE_BITMAP_BASE + offset) < blockHeaderSize[sizeClass])) {
      VM_Interface.sysWriteln("cell                       = ",cell);
      VM_Interface.sysWriteln("block.add(...)             = ",block.add(blockHeaderSize[sizeClass]));
      VM_Interface.sysWriteln("cellSize[sizeClass]        = ",cellSize[sizeClass]);
      VM_Interface.sysWriteln("index                      = ",index);
      VM_Interface.sysWriteln("offset                     = ",offset);
      VM_Interface.sysWriteln("sizeClass                  = ",sizeClass);
      VM_Interface.sysWriteln("blockHeaderSize[sizeClass] = ",blockHeaderSize[sizeClass]);
    }
    VM_Interface._assert((INUSE_BITMAP_BASE + offset) < blockHeaderSize[sizeClass]);
  }
  
  protected final void postExpandSizeClass(VM_Address block, int sizeClass){
    Memory.zeroSmall(block.add(BITMAP_BASE), 
		     VM_Extent.fromInt(bitmapSets[sizeClass]<<(LOG_WORD_SIZE+LOG_SET_SIZE)));
  };
  
  protected final VM_Address advanceToBlock(VM_Address block, int sizeClass) {
    if (LAZY_SWEEP) {
      if (maintainInUse())  {
	freeSets(block, sizeClass, true);
	return getFreeList(block);
      } else
	return makeFreeListFromMarkBits(block, sizeClass);
    } else
      return getFreeList(block);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a collection. If paranoid, perform a sanity check.
   */
  public final void prepare() {
    if (Options.fragmentationStats)
      fragmentationStatistics(true);
    if (LAZY_SWEEP)
      zeroMarkBits();
    flushFreeLists();
    if (PARANOID)
      sanity();
  }

  /**
   * Finish up after a collection.
   *
   */
  public void release() {
    // sweep the blocks
    sweepBlocks();
    restoreFreeLists();
    if (PARANOID)
      sanity();
    if (Options.fragmentationStats)
      fragmentationStatistics(false);
  }

  /**
   * Sweep all blocks for free objects. 
   */
  private final void sweepBlocks() {
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = firstBlock.get(sizeClass);
      while (!block.isZero()) {
	// first check to see if block is completely free and if possible
	// free the entire block
	VM_Address next = BlockAllocator.getNextBlock(block);
	if (!freeSets(block, sizeClass, false))
	  freeBlock(block, sizeClass);
	else if (!LAZY_SWEEP)
	  freeSets(block, sizeClass, true);
	block = next;
      }
    }
  }


  /**
   * Zero the mark bits for all blocks prior to the mark phase
   */
  private final void zeroMarkBits() {
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = firstBlock.get(sizeClass);
      int sets =  bitmapSets[sizeClass];
      while (!block.isZero()) {
	zeroMarkBits(block, sets);
	block = BlockAllocator.getNextBlock(block);
      }
    }
  }

  private final void zeroMarkBits(VM_Address block, int sets)
    throws VM_PragmaInline {
    VM_Address base = block.add(MARK_BITMAP_BASE);
    for (int set = 0; set < sets; set++) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert((INUSE_BITMAP_BASE == (BITMAP_BASE + WORD_SIZE)) 
			     && (MARK_BITMAP_BASE == BITMAP_BASE));
      VM_Address markBitmap = base.add(set<<(LOG_WORD_SIZE+LOG_SET_SIZE));
      VM_Magic.setMemoryWord(markBitmap, VM_Word.zero());
    }
  }

  /**
   * Walk through a set of mark/inuse sets for a block.
   *
   * @param block The block
   * @param sizeClass The size class for this superpage
   * @param release If true, then free up instances as they are
   * discovered.  If false do not free any instances, but return true
   * as soon as any in-use cell is discovered.
   * @return True if this block should be scavanged for free
   * instances, false if all instances are free, and therfore should
   * be freed enmasse.
   */
  private final boolean freeSets(VM_Address block, int sizeClass, 
				 boolean release)
    throws VM_PragmaInline {
    VM_Address base = block.add(MARK_BITMAP_BASE);
    boolean inUse = false;
    for (int set = 0; set < bitmapSets[sizeClass]; set++) {
      if (VM_Interface.VerifyAssertions) {
	VM_Interface._assert((INUSE_BITMAP_BASE == (BITMAP_BASE + WORD_SIZE)) 
			     && (MARK_BITMAP_BASE == BITMAP_BASE));
      }
      VM_Address markBitmap = base;
      base = base.add(WORD_SIZE);
      VM_Address inUseBitmap = base;
      if (maintainInUse()) base = base.add(WORD_SIZE);
      VM_Word mark = VM_Magic.getMemoryWord(markBitmap);
      if (release) {
	VM_Word inuse = VM_Magic.getMemoryWord(inUseBitmap);
	VM_Word free = mark.xor(inuse);
	if (!free.isZero()) {
	  freeFromBitmap(block, free, sizeClass, set);
	  VM_Magic.setMemoryWord(inUseBitmap, mark); 
	}
	if (!mark.isZero())
	  VM_Magic.setMemoryWord(markBitmap, VM_Word.zero());
      } else if (!mark.isZero())
	return true;
    }
    return false;
  }


  private final VM_Address makeFreeListFromMarkBits(VM_Address block, 
						    int sizeClass)
    throws VM_PragmaInline {
    VM_Address base = block.add(MARK_BITMAP_BASE);
    boolean inUse = false;
    setFreeList(block, VM_Address.zero());

    for (int set = 0; set < bitmapSets[sizeClass]; set++) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert((INUSE_BITMAP_BASE == (BITMAP_BASE + WORD_SIZE))
		   && (MARK_BITMAP_BASE == BITMAP_BASE));
      VM_Address markBitmap = base;
      base = base.add(WORD_SIZE<<LOG_SET_SIZE);
      VM_Word free = VM_Magic.getMemoryWord(markBitmap).not();

      if (set == (bitmapSets[sizeClass] - 1))
	free = free.and(VM_Word.fromInt(finalWordBitmapMask[sizeClass]));

      if (!free.isZero())
	freeFromBitmap(block, free, sizeClass, set);
    }
    return getFreeList(block);
  }

  /**
   * Give a bitmap representing cells to be freed, free all objects on
   * a superpage which are no longer in use.
   *
   * @param allocator The allocator through which the cells are freed
   * @param sp The superpage containing these cells and bitmaps
   * @param free The bitmap of those instances to be freed
   * @param sizeClass The size class for this superpage
   * @param cellSize The size of cells on this superpage
   * @param set The mark/inuse set from which this free bitmap was
   * produced (inidicating the locations of the objects in the free
   * bitmap).
   * @param small True if these are small obejcts.
   */
  private final void freeFromBitmap(VM_Address block, VM_Word free,
				    int sizeClass, int set)
    throws VM_PragmaInline {
    int index = (set<<LOG_WORD_BITS);
    VM_Address base = block.add(blockHeaderSize[sizeClass]);
    int size = cellSize[sizeClass];
    for(int i=0; i < WORD_BITS; i++) {
      if (!(free.and(VM_Word.fromInt((1<<i))).isZero())) {
	int offset = (index + i)* size;
	VM_Address cell = base.add(offset);
	free(cell, block, sizeClass);
      }
    }
  }
  
  /**
   * An object has been marked (identified as live), so the
   * corresponding mark bit is set in the block header.
   *
   * @param object The object which has been marked.
   */
  public static final void internalMarkObject(VM_Address object, byte tag) 
    throws VM_PragmaInline {
    VM_Address ref = VM_Interface.refToAddress(object);
    VM_Address block = BlockAllocator.getBlockStart(ref, tag);
    int sizeClass = getBlockSizeClass(block);

    // establish bitmask & offset for this cell in the block
    int index = (ref.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
    int bitnumber = index & (WORD_BITS - 1);
    VM_Word mask = VM_Word.fromInt(1<<bitnumber);
    int offset = (index>>LOG_WORD_BITS)<<(LOG_WORD_SIZE + LOG_SET_SIZE);

    // set the mark bit (this method is unsynchroinzed, so need explicit sync)
    VM_Address tgt = block.add(MARK_BITMAP_BASE + offset);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert((MARK_BITMAP_BASE + offset) < blockHeaderSize[sizeClass]);
    VM_Word oldValue, newValue;
    do {
      oldValue = VM_Word.fromInt(VM_Magic.prepareInt(tgt, 0));
      newValue = oldValue.or(mask);
    } while(!VM_Magic.attemptInt(tgt, 0, oldValue.toInt(), newValue.toInt()));
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Fragmentation analysis
  //

  /**
   * Print out fragmentation and wastage statistics.  This is not intended
   * to be efficient, just accurate and informative.
   */
  private final void fragmentationStatistics(boolean prepare) {
    if (Options.verboseFragmentationStats)
      verboseFragmentationStatistics(prepare);
    shortFragmentationStatistics(prepare);
  }

  private final void shortFragmentationStatistics(boolean prepare) {
    if (Plan.verbose > 2) VM_Interface.sysWrite("\n");
    if (Options.verboseFragmentationStats)
      VM_Interface.sysWrite((prepare) ? "> " : "< "); 
    VM_Interface.sysWrite("(Waste ");
    int waste = blockAllocator.unusedBytes();
    VM_Interface.sysWrite("B ");
    VM_Interface.sysWrite(waste/(float)(1<<20)); VM_Interface.sysWrite(" MB + ");
    VM_Interface.sysWrite("F ");
    waste = unusedBytes(prepare);
    VM_Interface.sysWrite(waste/(float)(1<<20)); VM_Interface.sysWrite(" MB)");
    if (Plan.verbose > 2 || Options.verboseFragmentationStats)
      VM_Interface.sysWrite("\n");
  }

 
  /**
   * Return the number of unused bytes on the free lists
   *
   * @param prepare True if this is called in the prepare phase
   * (immediately prior to GC), false if called in the release phase.
   * @return The number of unused bytes on the free lists
   */
  private final int unusedBytes(boolean prepare) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(MARK_BITS_ONLY);
    int unused = 0;
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = (prepare) ? currentBlock.get(sizeClass) : firstBlock.get(sizeClass);
      int sets =  bitmapSets[sizeClass];
      while (!block.isZero()) {
	unused += cellSize[sizeClass] * (cellsInBlock[sizeClass] - markedCells(block, sets));
	block = BlockAllocator.getNextBlock(block);
      }
    }
    return unused;
  }
  
  /**
   * Return the number of cells marked as live.  The utility of this
   * method is a function of when it is called (i.e. mark bits are
   * zeroed, set, and become stale, and depending on where in this
   * cycle this method is called, the results will differ
   * dramatically).
   *
   * @param block The block whose marked cells are to be counted
   * @param sets The number of mark/inuse "sets" for this block (a
   * function of sizeclass).
   * @return the number of cells marked as live on this block.
   */
  private final int markedCells(VM_Address block, int sets)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(MARK_BITS_ONLY);
    VM_Address base = block.add(MARK_BITMAP_BASE);
    int usedCells = 0;
    for (int set = 0; set < sets; set++) {
      if (VM_Interface.VerifyAssertions)
 	VM_Interface._assert(MARK_BITMAP_BASE == BITMAP_BASE);
      VM_Address markBitmap = base.add(set<<(LOG_WORD_SIZE+LOG_SET_SIZE));
      VM_Word mark = VM_Magic.getMemoryWord(markBitmap);
      for (int i = 0; i < WORD_BITS; i++) {
 	if (!(mark.and(VM_Word.fromInt(1<<i)).isZero()))
 	  usedCells++;
      }
    }
    return usedCells;
  }

  private final void verboseFragmentationStatistics(boolean prepare) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(MARK_BITS_ONLY);

    int totUsedCellBytes = 0;      // bytes for cells actually in use
    int totCellBytes = 0;          // bytes consumed by cells
    int totBytes = 0;              // bytes consumed (incl header etc)
    int totBlocks = 0;
    printFragHeader(prepare, false);
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = firstBlock.get(sizeClass);
      int sets =  bitmapSets[sizeClass];
      int blocks = 0;
      int usedCells = 0;
      VM_Address current = currentBlock.get(sizeClass);
      boolean getUsed = block.EQ(current) || current.isZero();
      while (!block.isZero()) {
	blocks++;
	if (getUsed) {
	  int marked = markedCells(block, sets);
	  int pctl = (FRAG_PERCENTILES * marked)/(cellsInBlock[sizeClass]+1);
	  utilization[pctl]++;
	  usedCells += marked;
	  if (prepare) {
	    allPreUtilization[sizeClass][pctl]++;
	    allPreUsedCells[sizeClass] += marked;
	  } else {
	    allPostUtilization[sizeClass][pctl]++;
	    allPostUsedCells[sizeClass] += marked;
	  }
	} else {
	  usedCells += cellsInBlock[sizeClass];
	  utilization[FRAG_PERCENTILES - 1]++;
	  if (prepare) {
	    allPreUtilization[sizeClass][FRAG_PERCENTILES - 1]++;
	    allPreUsedCells[sizeClass] += cellsInBlock[sizeClass];
	  } else {
	    allPostUtilization[sizeClass][FRAG_PERCENTILES - 1]++;
	    allPostUsedCells[sizeClass] += cellsInBlock[sizeClass];
	  }
	  getUsed = block.EQ(current);
	}
	block = BlockAllocator.getNextBlock(block);
      }
      totBlocks += blocks;
      if (prepare)
	allPreBlocks[sizeClass] += blocks;
      else
	allPostBlocks[sizeClass] += blocks;
      int usedCellBytes = usedCells * cellSize[sizeClass];
      totUsedCellBytes += usedCellBytes;
      int cellBytes = (blocks * cellsInBlock[sizeClass]) * cellSize[sizeClass];
      totCellBytes += cellBytes;
      int bytes = blocks * BlockAllocator.blockSize(blockSizeClass[sizeClass]);
      totBytes += bytes;
      printFragRow(prepare, false, false, sizeClass, usedCellBytes, cellBytes - usedCellBytes, cellBytes, bytes, blocks);
    }
    printFragRow(prepare, false, true, 0, totUsedCellBytes, totCellBytes - totUsedCellBytes, totCellBytes, totBytes, totBlocks);
  }

  private final void finalVerboseFragmentationStatistics(boolean prepare) {
    int totUsedCellBytes = 0;      // bytes for cells actually in use
    int totCellBytes = 0;          // bytes consumed by cells
    int totBytes = 0;              // bytes consumed (incl header etc)
    int totBlocks = 0;
    printFragHeader(prepare, true);
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      int blocks = (prepare) ? allPreBlocks[sizeClass] : allPostBlocks[sizeClass];
      int usedCells = (prepare) ? allPreUsedCells[sizeClass] : allPostUsedCells[sizeClass];
      totBlocks += blocks;
      int usedCellBytes = usedCells * cellSize[sizeClass];
      totUsedCellBytes += usedCellBytes;
      int cellBytes = (blocks * cellsInBlock[sizeClass]) * cellSize[sizeClass];
      totCellBytes += cellBytes;
      int bytes = blocks * BlockAllocator.blockSize(blockSizeClass[sizeClass]);
      totBytes += bytes;
      printFragRow(prepare, true, false, sizeClass, usedCellBytes, cellBytes - usedCellBytes, cellBytes, bytes, blocks);
    }
    printFragRow(prepare, true, true, 0, totUsedCellBytes, totCellBytes - totUsedCellBytes, totCellBytes, totBytes, totBlocks);
  }

  private final void printFragHeader(boolean prepare, boolean all) {
    if (all) {
      VM_Interface.sysWrite(prepare ? "\n=> " : "\n=< ");
      VM_Interface.sysWrite("TOTAL FRAGMENTATION ");
      VM_Interface.sysWrite(prepare ? "BEFORE " : "AFTER ");
      VM_Interface.sysWrite("GC INVOCATION");
      VM_Interface.sysWrite(prepare ? "\n=> " : "\n=< ");
    }
    VM_Interface.sysWrite("\n");
    if (all) VM_Interface.sysWrite("=");
    VM_Interface.sysWrite((prepare) ? "> " : "< "); 
    VM_Interface.sysWrite("szcls size    live free used net  util | ");
    for (int pctl = 0; pctl < FRAG_PERCENTILES; pctl++) {
      VM_Interface.sysWrite((pctl < (FRAG_PERCENTILES-1)) ? "<" : "<=");
      VM_Interface.sysWrite((100*(pctl+1))/FRAG_PERCENTILES);
      VM_Interface.sysWrite((pctl < (FRAG_PERCENTILES-1)) ? "% " : "%\n");
    }
    printFragDivider(prepare, all);
  }

  private final void printFragRow(boolean prepare, boolean all, boolean totals,
				  int sizeClass, int usedCellBytes,
				  int freeBytes, int cellBytes, int totBytes,
				  int blocks) {
    if (all) VM_Interface.sysWrite("=");
    VM_Interface.sysWrite((prepare) ? "> " : "< "); 
    if (totals)
      VM_Interface.sysWrite("totals\t");
    else {
      VM_Interface.sysWrite(sizeClass); VM_Interface.sysWrite("\t");
      VM_Interface.sysWrite(cellSize[sizeClass]);VM_Interface.sysWrite("\t");
    }
    printMB(usedCellBytes, " ");
    printMB(freeBytes, " ");
    printMB(cellBytes, " ");
    printMB(totBytes, " ");
    printRatio(usedCellBytes, totBytes, " | ");
    for (int pctl = 0; pctl < FRAG_PERCENTILES; pctl++) {
      String str = (pctl < FRAG_PERCENTILES - 1) ? " " : "\n";
      if (totals) {
	printRatio(totUtilization[pctl], blocks, str);
	totUtilization[pctl] = 0;
      } else {
	int util;
	if (all) {
	  if (prepare)
	    util = allPreUtilization[sizeClass][pctl];
	  else
	    util = allPostUtilization[sizeClass][pctl];
	} else {
	  util = utilization[pctl];
	  utilization[pctl] = 0;
	}
	printRatio(util, blocks, str);
	totUtilization[pctl] += util;
      }
    }
  }

  private final void printMB(int bytes, String str) {
    VM_Interface.sysWrite(bytes/(double)(1<<20));
    VM_Interface.sysWrite(str);
  }
  private final void printRatio(int numerator, int denominator, String str) {
    VM_Interface.sysWrite(numerator/(double)denominator); 
    VM_Interface.sysWrite(str);
  }
  private final void printFragDivider(boolean prepare, boolean all) {
    if (all) VM_Interface.sysWrite("=");
    VM_Interface.sysWrite((prepare) ? "> " : "< "); 
    VM_Interface.sysWrite("----------------------------------------");
    for (int i = 0; i < FRAG_PERCENTILES; i++) 
      VM_Interface.sysWrite("-----");
    VM_Interface.sysWrite("\n");
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Sanity checks and debugging
  //
  /**
   * Sweep all blocks for free objects. 
   */
  private final void sanity() {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(maintainInUse() && preserveFreeList());

    VM_Interface.sysWrite("<");
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = firstBlock.get(sizeClass);
      while (!block.isZero()) {
	if (block.EQ(DEBUG_BLOCK)) {
	  VM_Interface.sysWrite(firstBlock.get(sizeClass)); VM_Interface.sysWrite("[ ");
	}
	int free = checkFreeList(block, sizeClass);
	checkUsed(block, sizeClass, free);
	if (block.EQ(DEBUG_BLOCK)) {
	  VM_Interface.sysWrite(firstBlock.get(sizeClass)); VM_Interface.sysWrite("] ");
	  VM_Interface.sysWrite("done\n");
	}
	block = BlockAllocator.getNextBlock(block);
      }
    }
    VM_Interface.sysWrite("sane>");
  }

  private final int checkFreeList(VM_Address block, int sizeClass) {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(maintainInUse() && preserveFreeList());

    boolean debug = block.EQ(DEBUG_BLOCK);
    boolean isCurrent = currentBlock.get(sizeClass).EQ(block);
    VM_Address cell = isCurrent ? freeList.get(sizeClass) : getFreeList(block);
    int freeCells = 0;

    if (debug)
      VM_Interface.sysWrite(sizeClass," (");
    while (!cell.isZero()) {
      if (debug)
	VM_Interface.sysWrite(" ",cell); 
      freeCells++;
      if (!isFree(block, cell, sizeClass)) {
	VM_Interface.sysWrite("  Extraneous free list entry: ",cell);
	VM_Interface.sysWriteln(" ",block); 
	if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
      }
      if (freeCells > MAX_CELLS) {
	VM_Interface.sysWrite("  Runaway freelist: ",cell);
	VM_Interface.sysWriteln(" ",block); 
	if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
      }
      cell = getNextCell(cell);
    }
    if (debug)
      VM_Interface.sysWrite(") ");

    return freeCells;
  }
  
  private final boolean isFree(VM_Address block, VM_Address cell,
			       int sizeClass) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(maintainInUse());
    int index = (cell.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
    int bitnumber = index & (WORD_BITS - 1);
    VM_Word mask = VM_Word.fromInt(1<<bitnumber);
    int offset = (index>>LOG_WORD_BITS)<<(LOG_WORD_SIZE + LOG_SET_SIZE);
    VM_Address word = block.add(INUSE_BITMAP_BASE + offset);
    boolean inuse = !(VM_Magic.getMemoryWord(word).and(mask).isZero());
    if (inuse && block.EQ(DEBUG_BLOCK)) {
      VM_Interface.sysWrite(index); VM_Interface.sysWrite(" "); VM_Interface.sysWrite(block); VM_Interface.sysWrite(" "); VM_Interface.sysWrite(word); VM_Interface.sysWrite(" "); VM_Interface.sysWrite(VM_Magic.getMemoryWord(word)); VM_Interface.sysWrite("\n");
    }
    return !inuse;
  }

  private final void checkUsed(VM_Address block, int sizeClass, int free) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(maintainInUse());

    int used = 0;
    if (block.EQ(DEBUG_BLOCK)) {
      VM_Interface.sysWrite("\n"); VM_Interface.sysWrite(sizeClass); VM_Interface.sysWrite(" "); VM_Interface.sysWrite(bitmapSets[sizeClass]); VM_Interface.sysWrite("\n"); 
    }
    VM_Address base = block;
    for (int set = 0; set < bitmapSets[sizeClass]; set++) {
      VM_Address bitmap = base.add(INUSE_BITMAP_BASE + (set<<(LOG_WORD_SIZE+LOG_SET_SIZE)));
      VM_Word word = VM_Magic.getMemoryWord(bitmap);
      if (block.EQ(DEBUG_BLOCK)) {
	VM_Interface.sysWrite(set); VM_Interface.sysWrite(" "); VM_Interface.sysWrite(bitmap); VM_Interface.sysWrite(" "); VM_Interface.sysWrite(word); VM_Interface.sysWrite("\n");
      }
      for (int bit = 0; bit < WORD_BITS; bit++) {
	if (!(word.and(VM_Word.fromInt(1<<bit)).isZero()))
	  used++;
      }
    }

    int inuse;
    if (currentBlock.get(sizeClass) == block)
      inuse = cellsInUse[sizeClass];
    else
      inuse = getInUse(block);

    if (inuse != used) {
      VM_Interface.sysWrite("Incoherent inuse count ");
      VM_Interface.sysWrite(block); VM_Interface.sysWrite(": ");
      VM_Interface.sysWrite(inuse); VM_Interface.sysWrite(" != ");
      VM_Interface.sysWrite(used); VM_Interface.sysWrite("\n");
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    }
    
    if ((cellsInBlock[sizeClass] - used) != free) {
      VM_Interface.sysWrite("Incoherent free and inuse counts ");
      VM_Interface.sysWrite(block); VM_Interface.sysWrite(": ");
      VM_Interface.sysWrite(cellsInBlock[sizeClass]); VM_Interface.sysWrite(" != ");
      VM_Interface.sysWrite(used); VM_Interface.sysWrite(" + ");
      VM_Interface.sysWrite(free); VM_Interface.sysWrite("\n");
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    }
  }

  private final int getUsedPages() {
    int bytes = 0;
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      bytes += getUsedBlockBytes(firstBlock.get(sc), sc);
    }
    return bytes>>LOG_PAGE_SIZE;
  }

  private final int getUsedBlockBytes(VM_Address block, int sizeClass) {
    int bytes = 0;
    while (!block.isZero()) {
      bytes += BlockAllocator.blockSize(blockSizeClass[sizeClass]);
      block = BlockAllocator.getNextBlock(block);
    }
    return bytes;
  }

  public final void exit() {
    if (Options.verboseFragmentationStats) {
      finalVerboseFragmentationStatistics(true);
      finalVerboseFragmentationStatistics(false);
    }
  }
//   private final void fragmentationTotals() {
//     fragmentationCheck(true, true);
//   }
//   private final void fragmentationSpotCheck() {
//     fragmentationCheck(false, FRAG_VERBOSE);
//   }

  public boolean mustCollect() {
    if ((lastBytesAlloc ^ bytesAlloc) > MS_MUST_COLLECT_THRESHOLD) {
      lastBytesAlloc = bytesAlloc;
      return true;
    } else
      return false;
  }

}
