/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_AddressArray;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;

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
  private static final int INUSE_BITMAP_BASE = BITMAP_BASE;
  private static final int MARK_BITMAP_BASE = BITMAP_BASE + WORD_SIZE;
  private static final boolean LAZY_SWEEP = true;
  protected static int[] bitmapPairs;
  
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
  }
  
  /**
   */
  static {
    cellSize = new int[SIZE_CLASSES];
    blockSizeClass = new byte[SIZE_CLASSES];
    bitmapPairs = new int[SIZE_CLASSES];
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];
    if (FRAGMENTATION_CHECK) {
      inuse = new long[SIZE_CLASSES];
      used = new long[SIZE_CLASSES];
    }
    
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
	int virtualCells = BlockAllocator.blockSize(blk)/cellSize[sc];
	int pairs = (virtualCells+WORD_BITS-1)>>LOG_WORD_BITS;
	int avail = BlockAllocator.blockSize(blk) - FREE_LIST_HEADER_BYTES
	  - ((pairs<<LOG_WORD_SIZE)*2);
	int cells = avail/cellSize[sc];
	blockSizeClass[sc] = blk;
	bitmapPairs[sc] = pairs;
	cellsInBlock[sc] = cells;
	blockHeaderSize[sc] = FREE_LIST_HEADER_BYTES + (pairs<<(LOG_WORD_SIZE+1));
	if (((avail < PAGE_SIZE) && (cells*2 > MAX_CELLS)) ||
	    ((avail > (PAGE_SIZE>>1)) && (cells > MIN_CELLS)))
	  break;
      }
    }
    //    dumpSizeClassData();
  }

  private static void dumpSizeClassData() {
    VM.sysWrite("\nsc\tc size\tpairs\tcells\tblk sc\thdr\tspace\twaste\tutilization\n");
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      VM.sysWrite(sc); VM.sysWrite("\t");
      VM.sysWrite(cellSize[sc]); VM.sysWrite("\t");
      VM.sysWrite(bitmapPairs[sc]); VM.sysWrite("\t");
      VM.sysWrite(cellsInBlock[sc]); VM.sysWrite("\t");
      VM.sysWrite(blockSizeClass[sc]); VM.sysWrite("\t");
      VM.sysWrite(blockHeaderSize[sc]); VM.sysWrite("\t");
      VM.sysWrite(cellSize[sc]*cellsInBlock[sc]); VM.sysWrite("\t");
      //      VM.sysWrite(cellSize[sc]*cellsInBlock[sc]+blockHeaderSize[sc]+Block.BLOCK_HEADER_SIZE); VM.sysWrite("\t");
      VM.sysWrite(BlockAllocator.blockSize(blockSizeClass[sc]) - (cellSize[sc]*cellsInBlock[sc]+blockHeaderSize[sc])); VM.sysWrite("\t");
      VM.sysWrite(((float) (cellSize[sc]*cellsInBlock[sc]))/((float)  BlockAllocator.blockSize(blockSizeClass[sc]))); VM.sysWrite("\n");
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
   */
  protected final void postAlloc(VM_Address cell, VM_Address block,
				 int sizeClass, EXTENT bytes) 
    throws VM_PragmaInline {

    // establish bitmask & offset for this cell in the block
    //    int index = (cell.diff(block).toInt())/cellSize[sizeClass];
    int index = (cell.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
    int bitnumber = index & (WORD_BITS - 1);
    VM_Word mask = VM_Word.fromInt(1<<bitnumber);
    int offset = (index>>LOG_WORD_BITS)<<(LOG_WORD_SIZE + 1);

    VM_Address bitmapWord;
    VM_Word word;
    if (VM.VerifyAssertions)
      VM._assert((INUSE_BITMAP_BASE + offset) < blockHeaderSize[sizeClass]);

    // set the inuse bit
    bitmapWord = block.add(INUSE_BITMAP_BASE + offset);
    word = VM_Magic.getMemoryWord(bitmapWord);
    word = word.or(mask);
    VM_Magic.setMemoryWord(bitmapWord, word);

    if (msSpace.inMSCollection) {
      // set the mark bit
      bitmapWord = block.add(MARK_BITMAP_BASE + offset);
      word = VM_Magic.getMemoryWord(bitmapWord);
      word = word.or(mask);
      VM_Magic.setMemoryWord(bitmapWord, word);
    }
  };  
  
  protected final void postExpandSizeClass(VM_Address block, int sizeClass){
    Memory.zeroSmall(block.add(BITMAP_BASE), bitmapPairs[sizeClass]<<(LOG_WORD_SIZE+1));
  };
  
  protected final void advanceToBlock(VM_Address block, int sizeClass) {
    if (LAZY_SWEEP)
      freePairs(block, sizeClass, true);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a collection. If paranoid, perform a sanity check.
   */
  public final void prepare() {
    if (LAZY_SWEEP)
      zeroMarkBits();
    flushFreeLists();
    if (PARANOID)
      sanity();
    if (FRAGMENTATION_CHECK) {
      fragmentationSpotCheck();
      VM.sysWrite("-> live: "); VM.sysWriteInt(bytesAlloc + bytesLive); VM.sysWrite(", "); VM.sysWriteInt((bytesAlloc + bytesLive)>>LOG_PAGE_SIZE); VM.sysWrite(", "); VM.sysWriteInt(getUsedPages()); VM.sysWrite("\n");
      bytesLive = 0; bytesAlloc = 0;
    }
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
    if (FRAGMENTATION_CHECK)
      fragmentationSpotCheck();
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
	if (!freePairs(block, sizeClass, false))
	  freeBlock(block, sizeClass);
	else if (!LAZY_SWEEP)
	  freePairs(block, sizeClass, true);
	block = next;
      }
    }
  }


  /**
   * Sweep all blocks for free objects. 
   */
  private final void zeroMarkBits() {
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = currentBlock.get(sizeClass);
//       VM.sysWrite("zeroing... "); VM.sysWrite(block);
      if (!block.isZero())
	block = BlockAllocator.getNextBlock(block);
//       VM.sysWrite(" "); VM.sysWrite(block);
      
      while (!block.isZero()) {
// 	VM.sysWrite(" "); VM.sysWrite(block);
	zeroMarkBits(block, sizeClass);
	block = BlockAllocator.getNextBlock(block);
      }
//       VM.sysWrite("....done\n");
    }
  }

  private final void zeroMarkBits(VM_Address block, int sizeClass)
    throws VM_PragmaInline {
//     VM.sysWrite("z("); VM.sysWrite(block); VM.sysWrite(")\n");
    VM_Address base = block.add(MARK_BITMAP_BASE);
    for (int pair = 0; pair < bitmapPairs[sizeClass]; pair++) {
      if (VM.VerifyAssertions)
	VM._assert((INUSE_BITMAP_BASE == BITMAP_BASE) 
		   && (MARK_BITMAP_BASE == (BITMAP_BASE + WORD_SIZE)));
      VM_Address markBitmap = base.add(pair<<(LOG_WORD_SIZE+1));
      VM_Magic.setMemoryWord(markBitmap, VM_Word.zero());
    }
  }

  /**
   * Walk through a set of mark/inuse pairs for a block.
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
  private final boolean freePairs(VM_Address block, int sizeClass,
				  boolean release)
    throws VM_PragmaInline {
    VM_Address base = block.add(BITMAP_BASE);
    boolean inUse = false;
    for (int pair = 0; pair < bitmapPairs[sizeClass]; pair++) {
      if (VM.VerifyAssertions)
	VM._assert((INUSE_BITMAP_BASE == BITMAP_BASE) 
		   && (MARK_BITMAP_BASE == (BITMAP_BASE + WORD_SIZE)));
      VM_Address inUseBitmap = base;
      base = base.add(WORD_SIZE);
      VM_Address markBitmap = base;
      base = base.add(WORD_SIZE);
      VM_Word mark = VM_Magic.getMemoryWord(markBitmap);
      if (release) {
	VM_Word inuse = VM_Magic.getMemoryWord(inUseBitmap);
	VM_Word free = mark.xor(inuse);
	if (!free.isZero()) {
	  freeFromBitmap(block, free, sizeClass, pair);
	  VM_Magic.setMemoryWord(inUseBitmap, mark); 
	}
	if (!mark.isZero())
	  VM_Magic.setMemoryWord(markBitmap, VM_Word.zero());
      } else if (!mark.isZero())
	return true;
    }
    return false;
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
   * @param pair The mark/inuse pair from which this free bitmap was
   * produced (inidicating the locations of the objects in the free
   * bitmap).
   * @param small True if these are small obejcts.
   */
  private final void freeFromBitmap(VM_Address block, VM_Word free,
				    int sizeClass, int pair)
    throws VM_PragmaInline {
    int index = (pair<<LOG_WORD_BITS);
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
    if (FRAGMENTATION_CHECK)
      bytesLive += VM_Interface.getSizeWhenCopied(object);
    VM_Address ref = VM_JavaHeader.getPointerInMemoryRegion(object);
    VM_Address block = BlockAllocator.getBlockStart(ref, tag);
    int sizeClass = getSizeClass(block);

    // establish bitmask & offset for this cell in the block
    int index = (ref.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
    int bitnumber = index & (WORD_BITS - 1);
    VM_Word mask = VM_Word.fromInt(1<<bitnumber);
    int offset = (index>>LOG_WORD_BITS)<<(LOG_WORD_SIZE + 1);

    // set the mark bit (this method is unsynchroinzed, so need explicit sync)
    VM_Address tgt = block.add(MARK_BITMAP_BASE + offset);
    VM_Word oldValue, newValue;
//     VM.sysWrite("mi["); VM.sysWrite(sizeClass); VM.sysWrite(" "); VM.sysWrite(object); VM.sysWrite(" "); VM.sysWrite(VM_Magic.getMemoryWord(VM_Address.fromInt(0x5bca0048)));  VM.sysWrite(" "); VM.sysWrite(tgt); VM.sysWrite(" "); VM.sysWrite(index); VM.sysWrite(" "); VM.sysWrite(offset); VM.sysWrite(" "); VM.sysWrite(block); 
    if (VM.VerifyAssertions)
      VM._assert((MARK_BITMAP_BASE + offset) < blockHeaderSize[sizeClass]);
    do {
      oldValue = VM_Word.fromInt(VM_Magic.prepare(tgt, 0));
      newValue = oldValue.or(mask);
    } while(!VM_Magic.attempt(tgt, 0, oldValue.toInt(), newValue.toInt()));
//     VM.sysWrite(" "); VM.sysWrite(VM_Magic.getMemoryWord(VM_Address.fromInt(0x5bca0048))); VM.sysWrite("]\n");
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Sanity checks and debugging
  //
  /**
   * Sweep all blocks for free objects. 
   */
  private final void sanity() {
    VM.sysWrite("<");
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = firstBlock.get(sizeClass);
      while (!block.isZero()) {
	if (block.EQ(DEBUG_BLOCK)) {
	  VM.sysWrite(firstBlock.get(sizeClass)); VM.sysWrite("[ ");
	}
	int free = checkFreeList(block, sizeClass);
	checkUsed(block, sizeClass, free);
	if (block.EQ(DEBUG_BLOCK)) {
	  VM.sysWrite(firstBlock.get(sizeClass)); VM.sysWrite("] ");
	  VM.sysWrite("done\n");
	}
	block = BlockAllocator.getNextBlock(block);
      }
    }
    VM.sysWrite("sane>");
  }

  private final int checkFreeList(VM_Address block, int sizeClass) {
    VM_Address cell;
    if (currentBlock.get(sizeClass).EQ(block))
      cell = freeList.get(sizeClass);
    else
      cell = getFreeList(block);

    int freeCells = 0;
    if (block.EQ(DEBUG_BLOCK)) {
      VM.sysWrite(sizeClass); VM.sysWrite(" (");
    }
    while (!cell.isZero()) {
      if (block.EQ(DEBUG_BLOCK)) {
	VM.sysWrite(cell); VM.sysWrite(" ");
      }
      freeCells++;
      if (!isFree(block, cell, sizeClass)) {
	VM.sysWrite("Extraneous free list entry: ");
	VM.sysWrite(cell); VM.sysWrite(" ");
	VM.sysWrite(block); VM.sysWrite("\n");
	if (VM.VerifyAssertions) VM._assert(false);
      }
      if (freeCells > MAX_CELLS) {
	VM.sysWrite("Runaway freelist: ");
	VM.sysWrite(cell); VM.sysWrite(" ");
	VM.sysWrite(block); VM.sysWrite("\n");
	if (VM.VerifyAssertions) VM._assert(false);
      }
      cell = getNextCell(cell);
    }
    if (block.EQ(DEBUG_BLOCK)) {
      VM.sysWrite(") ");
    }
    return freeCells;
  }
  
  private final boolean isFree(VM_Address block, VM_Address cell,
			       int sizeClass) {
    int index = (cell.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
    int bitnumber = index & (WORD_BITS - 1);
    VM_Word mask = VM_Word.fromInt(1<<bitnumber);
    int offset = (index>>LOG_WORD_BITS)<<(LOG_WORD_SIZE + 1);
    VM_Address word = block.add(INUSE_BITMAP_BASE + offset);
    boolean inuse = !(VM_Magic.getMemoryWord(word).and(mask).isZero());
    if (inuse && block.EQ(DEBUG_BLOCK)) {
      VM.sysWrite(index); VM.sysWrite(" "); VM.sysWrite(block); VM.sysWrite(" "); VM.sysWrite(word); VM.sysWrite(" "); VM.sysWrite(VM_Magic.getMemoryWord(word)); VM.sysWrite("\n");
    }
    return !inuse;
  }

  private final void checkUsed(VM_Address block, int sizeClass, int free) {
    int used = 0;
    if (block.EQ(DEBUG_BLOCK)) {
      VM.sysWrite("\n"); VM.sysWrite(sizeClass); VM.sysWrite(" "); VM.sysWrite(bitmapPairs[sizeClass]); VM.sysWrite("\n"); 
    }
    VM_Address base = block;
    for (int pair = 0; pair < bitmapPairs[sizeClass]; pair++) {
      VM_Address bitmap = base.add(INUSE_BITMAP_BASE + (pair<<(LOG_WORD_SIZE+1)));
      VM_Word word = VM_Magic.getMemoryWord(bitmap);
      if (block.EQ(DEBUG_BLOCK)) {
	VM.sysWrite(pair); VM.sysWrite(" "); VM.sysWrite(bitmap); VM.sysWrite(" "); VM.sysWrite(word); VM.sysWrite("\n");
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
      VM.sysWrite("Incoherent inuse count ");
      VM.sysWrite(block); VM.sysWrite(": ");
      VM.sysWrite(inuse); VM.sysWrite(" != ");
      VM.sysWrite(used); VM.sysWrite("\n");
      if (VM.VerifyAssertions) VM._assert(false);
    }
    
    if ((cellsInBlock[sizeClass] - used) != free) {
      VM.sysWrite("Incoherent free and inuse counts ");
      VM.sysWrite(block); VM.sysWrite(": ");
      VM.sysWrite(cellsInBlock[sizeClass]); VM.sysWrite(" != ");
      VM.sysWrite(used); VM.sysWrite(" + ");
      VM.sysWrite(free); VM.sysWrite("\n");
      if (VM.VerifyAssertions) VM._assert(false);
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
    if (FRAGMENTATION_CHECK)
      fragmentationTotals();
  }
  private final void fragmentationTotals() {
    fragmentationCheck(true, true);
  }
  private final void fragmentationSpotCheck() {
    fragmentationCheck(false, FRAG_VERBOSE);
  }

  private final void fragmentationCheck(boolean totals, boolean print) {
    int totInuse = 0; 
    int totUsed = 0;
    if (print)
      printFragHeader(totals);
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      long i, u;
      if (totals) {
	i = inuse[sizeClass];
	u = used[sizeClass];
      } else {
	VM_Address block = firstBlock.get(sizeClass);
	i = getInuseCellBytes(block, sizeClass);
	u = getUsedBlockBytes(block, sizeClass);
	inuse[sizeClass] += i;
	used[sizeClass] += u;
      }
      totInuse += i;
      totUsed += u;
      if (print)
	printFragRow(sizeClass, i, u);
    }
    if (print)
      printFragTotal(totInuse, totUsed);
  }

  private final void printFragHeader(boolean totals) {
    if (totals)
      VM.sysWrite("--------------- total fragmentation ----------------\n");
    else
      VM.sysWrite("---------------- spot fragmentation ----------------\n");
    VM.sysWrite("szcls\tbytes\tinuse\tfree\tused\tfrag\n");
  }
  private final void printFragRow(int sizeClass, long inuse, long used) {
    printFragRow(sizeClass, inuse, used, false);
  }
  private final void printFragTotal(long inuse, long used) {
    VM.sysWrite("----------------------------------------------------\n");
    printFragRow(-1, inuse, used, true);
    VM.sysWrite("----------------------------------------------------\n");
  }
  private final void printFragRow(int sizeClass, long inuse, long used,
				  boolean total) {
    if (total) {
      VM.sysWrite("total\t\t");
    } else {
      VM.sysWrite(sizeClass); VM.sysWrite("\t");
      VM.sysWrite(cellSize[sizeClass]); VM.sysWrite("\t");
    }
    VM.sysWrite(inuse); VM.sysWrite("\t");
    VM.sysWrite(used - inuse); VM.sysWrite("\t");
    VM.sysWrite(used); VM.sysWrite("\t");
    VM.sysWrite((float) (1.0 - ((float) inuse/ (float) used)));
    VM.sysWrite("\n");
  }

  private final int getInuseCellBytes(VM_Address block, int sizeClass) {
    int inUseBytes = 0;
    while (!block.isZero()) {
      int inuse = 0;
      if (currentBlock.get(sizeClass).EQ(block))
	inuse = cellsInUse[sizeClass];
      else
	inuse = getInUse(block);
      inUseBytes += inuse * cellSize[sizeClass];
      block = BlockAllocator.getNextBlock(block);
    }
    return inUseBytes;
  }

  public boolean mustCollect() {
    if ((lastBytesAlloc ^ bytesAlloc) > MS_MUST_COLLECT_THRESHOLD) {
      lastBytesAlloc = bytesAlloc;
      return true;
    } else
      return false;
  }

}
