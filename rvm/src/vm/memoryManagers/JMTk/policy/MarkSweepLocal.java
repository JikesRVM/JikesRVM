/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.utility.BlockAllocator;
import org.mmtk.utility.Log;
import org.mmtk.utility.Memory;
import org.mmtk.utility.Options;
import org.mmtk.utility.SegregatedFreeList;
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

/**
 * This class implements unsynchronized (local) elements of a
 * mark-sweep collector.  Allocation is via the segregated free list
 * (@see SegregatedFreeList).  Marking is done using both a bit in
 * each header's object word, and a mark bitmap.  Sweeping is
 * performed lazily.<p>
 *
 * A free list block is a contigious region of memory containing cells
 * of a single size class, and is a construct of the
 * SegregatedFreeList.  This class extends the block to include a mark
 * bitmap.  During the mark phase, if an object is encountered with
 * the mark bit in its header unset, it is set and the mark bit in the
 * block header corresponding to that object is set.  The rationale
 * behind this approach is that testing (and setting) the mark bit in
 * the object header is cheap, while using a bitmap makes sweeping
 * more efficient.  This approach maximizes the speed of the common
 * case when marking, while also allowing for fast sweeping, with
 * minimal space overhead (2 bits per object).
 *
 * @see SegregatedFreeList
 * @see MarkSweepSpace
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class MarkSweepLocal extends SegregatedFreeList
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  private static final int MARK_BITMAP_BASE = FREE_LIST_HEADER_BYTES;
  private static final int FRAG_PERCENTILES = 5;  // 20% iles

  private static final int LOG_BYTES_IN_BITMAP = LOG_BYTES_IN_INT;
  private static final int BYTES_IN_BITMAP = 1<<LOG_BYTES_IN_BITMAP;
  private static final int LOG_BITS_IN_BITMAP = LOG_BYTES_IN_BITMAP + LOG_BITS_IN_BYTE;
  private static final int BITS_IN_BITMAP = 1<<LOG_BITS_IN_BITMAP; 

  protected static int[] bitmaps;
  private static int[] finalBitmapMask;

  private static int lastBytesAlloc = 0;
  private static int MS_MUST_COLLECT_THRESHOLD = 1<<30;
  private static long used[];

  /****************************************************************************
   *
   * Instance variables
   */
  private MarkSweepSpace msSpace;

  /* fragmentation measurement */
  private int utilization[];  
  private int allPreUtilization[][];
  private int allPostUtilization[][];
  private int totUtilization[];
  private int allPreBlocks[];
  private int allPostBlocks[];
  private int allPreUsedCells[];
  private int allPostUsedCells[];

  protected final boolean preserveFreeList() { return false; }
  protected final boolean maintainInUse() { return false; }

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  The primary task performed here is to
   * establish the block layout for each size class.
   */
  static {
    cellSize = new int[SIZE_CLASSES];
    blockSizeClass = new byte[SIZE_CLASSES];
    bitmaps = new int[SIZE_CLASSES];
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];
    finalBitmapMask = new int[SIZE_CLASSES];

    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
        int bitsPerCell = (cellSize[sc]<<LOG_BITS_IN_BYTE) + 1;
        int usableBytes = BlockAllocator.blockSize(blk)-FREE_LIST_HEADER_BYTES;
        int usableBits = usableBytes<<LOG_BITS_IN_BYTE;
        int cells = usableBits/bitsPerCell;
        bitmaps[sc] = (cells+BITS_IN_BITMAP-1)>>LOG_BITS_IN_BITMAP;
        blockSizeClass[sc] = blk;
        cellsInBlock[sc] = cells;
        blockHeaderSize[sc] = FREE_LIST_HEADER_BYTES + (bitmaps[sc]<<LOG_BYTES_IN_BITMAP);
        int remainder = cells & (BITS_IN_BITMAP - 1);
        if (remainder == 0)
          finalBitmapMask[sc] =  -1;
        else
          finalBitmapMask[sc] = (1<<remainder)-1;
        
        if (((usableBytes < BYTES_IN_PAGE) && (cells*2 > MAX_CELLS)) ||
            ((usableBytes > (BYTES_IN_PAGE>>1)) && (cells > MIN_CELLS)))
          break;
      }
    }
//     dumpSizeClassData();
  }

  /**
   * Constructor
   *
   * @param space The mark-sweep space to which this allocator
   * instances is bound.  The space's VMResource and MemoryResource
   * are used to initialize the superclass.
   */
  public MarkSweepLocal(MarkSweepSpace space, Plan plan) {
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
  

  /****************************************************************************
   *
   * Allocation
   */

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
  public final void postAlloc(VM_Address cell, VM_Address block,
			      int sizeClass, int bytes, boolean inGC) 
    throws VM_PragmaInline {

    if (inGC) 
      internalMark(cell, block, sizeClass);
  }
  
  /**
   * Initialize any header information for a new block.  In this case,
   * this just means zeroing the header bits.
   *
   * @param block The new block whose header is to be zeroed
   * @param sizeClass The sizeClass of the new block
   */
  public final void postExpandSizeClass(VM_Address block, int sizeClass) {
    Memory.zeroSmall(block.add(MARK_BITMAP_BASE), 
                     VM_Word.fromIntZeroExtend(bitmaps[sizeClass]).lsh(LOG_BYTES_IN_BITMAP).toExtent());
  };

  /**
   * Prepare the next block in the free block list for use by the free
   * list allocator.  In the case of lazy sweeping this involves
   * sweeping the available cells.  <b>The sweeping operation must
   * ensure that cells are pre-zeroed</b>, as this method must return
   * pre-zeroed cells.
   *
   * @param block The block to be prepared for use
   * @param sizeClass The size class of the block
   * @return The address of the first pre-zeroed cell in the free list
   * for this block, or zero if there are no available cells.
   */
  protected final VM_Address advanceToBlock(VM_Address block, int sizeClass) {
    return makeFreeListFromMarkBits(block, sizeClass);
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection. If paranoid, perform a sanity check.
   */
  public final void prepare() {
    if (Options.fragmentationStats)
      fragmentationStatistics(true);
    zeroMarkBits();
    flushFreeLists();
  }

  /**
   * Finish up after a collection.
   *
   */
  public void release() {
    sweepBlocks();                    // sweep the blocks
    restoreFreeLists();
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
        /* first check to see if block is completely free and if possible
         * free the entire block */
        VM_Address next = BlockAllocator.getNextBlock(block);
        if (isEmpty(block, sizeClass))
          freeBlock(block, sizeClass);
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
      while (!block.isZero()) {
        VM_Address base = block.add(MARK_BITMAP_BASE);
        for (int bitmap = 0; bitmap < bitmaps[sizeClass]; bitmap++) {
          VM_Address markAddr = base.add(bitmap<<LOG_BYTES_IN_BITMAP);
          VM_Magic.setMemoryInt(markAddr, 0);
        }
        block = BlockAllocator.getNextBlock(block);
      }
    }
  }
  
  /**
   * Walk through a set of mark bitmaps for a block, and if all cells
   * are unused, return true.
   *
   * @param block The block
   * @param sizeClass The size class for this superpage
   * @return True if all cells for this block are unmarked and
   * therfore should be freed enmasse.
   */
  private final boolean isEmpty(VM_Address block, int sizeClass)
    throws VM_PragmaInline {
    VM_Address markAddr = block.add(MARK_BITMAP_BASE);
    for (int bitmap = 0; bitmap < bitmaps[sizeClass]; bitmap++) {
      int mark = VM_Magic.getMemoryInt(markAddr);
      markAddr = markAddr.add(BYTES_IN_BITMAP);
      if (mark != 0)
        return false;
    }
    return true;
  }

  /**
   * Use the mark bits for a block to infer free cells and thus
   * construct a free list for the block.
   *
   * @param block The block to be processed
   * @param sizeClass The size class for the block
   * @return A free list for the block, corresponding to the cells not
   * in use.
   */
  private final VM_Address makeFreeListFromMarkBits(VM_Address block, 
                                                    int sizeClass)
    throws VM_PragmaInline {
    VM_Address markAddr = block.add(MARK_BITMAP_BASE);
    setFreeList(block, VM_Address.zero());

    for (int bitmap = 0; bitmap < bitmaps[sizeClass]; bitmap++) {
      int free = ~(VM_Magic.getMemoryInt(markAddr));
      if (bitmap == (bitmaps[sizeClass] - 1))
        free &= finalBitmapMask[sizeClass];
      
      if (free != 0)
        freeFromBitmap(block, free, sizeClass, bitmap);
      markAddr = markAddr.add(BYTES_IN_BITMAP);
    }
    return getFreeList(block);
  }

  /**
   * Give a bitmap representing cells to be freed, free all objects on
   * a superpage which are no longer in use.
   *
   * @param block The block with which the bitmap is associated
   * @param free The bitmap of those instances to be freed
   * @param sizeClass The size class for this superpage
   * @param bitmap The mark bitmap from which this free bitmap was
   * produced.
   */
  private final void freeFromBitmap(VM_Address block, int free,
                                    int sizeClass, int bitmap)
    throws VM_PragmaInline {
    int index = (bitmap<<LOG_BITS_IN_BITMAP);
    VM_Address base = block.add(blockHeaderSize[sizeClass]);
    int size = cellSize[sizeClass];
    for(int i=0; i < BITS_IN_BITMAP; i++) {
      if ((free & (1<<i)) != 0) {
        int offset = (index + i) * size;
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
  public static final void internalMarkObject (VM_Address object, byte tag) 
    throws VM_PragmaInline {

    VM_Address ref = VM_Interface.refToAddress(object);
    internalMark (ref, tag);
  }

  public static final void internalMark (VM_Address addrInCell, byte tag) 
    throws VM_PragmaInline {

    VM_Address block = BlockAllocator.getBlockStart(addrInCell, tag);
    int sizeClass = getBlockSizeClass(block);
    internalMark (addrInCell, block, sizeClass);
  }

  public static final void internalMark (VM_Address addrInCell, VM_Address block, int sizeClass)
    throws VM_PragmaInline {

    /* establish bitmask & offset for this cell in the block */
    int index = (addrInCell.diff(block.add(blockHeaderSize[sizeClass])).toInt())/cellSize[sizeClass];
    int bitnumber = index & (BITS_IN_BITMAP - 1);
    int mask = 1<<bitnumber;
    int offset = (index>>LOG_BITS_IN_BITMAP)<<LOG_BYTES_IN_BITMAP;

    /* set the mark bit (this is unsynchroinzed, so need explicit sync) */
    VM_Address tgt = block.add(MARK_BITMAP_BASE + offset);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert((MARK_BITMAP_BASE + offset) < blockHeaderSize[sizeClass]);
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(tgt, 0);
      newValue = oldValue | mask;
    } while(!VM_Magic.attemptInt(tgt, 0, oldValue, newValue));
  }

  /****************************************************************************
   *
   * Fragmentation analysis
   */

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
    if (Options.verbose > 2) Log.writeln();
    if (Options.verboseFragmentationStats)
      Log.write((prepare) ? "> " : "< "); 
    Log.write("(Waste ");
    int waste = blockAllocator.unusedBytes();
    Log.write("B ");
    Log.write(waste/(float)(1<<20)); Log.write(" MB + ");
    Log.write("F ");
    waste = unusedBytes(prepare);
    Log.write(waste/(float)(1<<20)); Log.write(" MB)");
    if (Options.verbose > 2 || Options.verboseFragmentationStats)
      Log.writeln();
  }
 
  /**
   * Return the number of unused bytes on the free lists
   *
   * @param prepare True if this is called in the prepare phase
   * (immediately prior to GC), false if called in the release phase.
   * @return The number of unused bytes on the free lists
   */
  private final int unusedBytes(boolean prepare) {
    int unused = 0;
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = (prepare) ? currentBlock.get(sizeClass) : firstBlock.get(sizeClass);
      int sets =  bitmaps[sizeClass];
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
   * @param bitmaps The number of mark bitmaps for this block (a
   * function of sizeclass).
   * @return the number of cells marked as live on this block.
   */
  private final int markedCells(VM_Address block, int bitmaps)
    throws VM_PragmaInline {
    VM_Address base = block.add(MARK_BITMAP_BASE);
    int usedCells = 0;
    for (int bitmap = 0; bitmap < bitmaps; bitmap++) {
      VM_Address markBitmap = base.add(bitmap<<LOG_BYTES_IN_BITMAP);
      int mark = VM_Magic.getMemoryInt(markBitmap);
      for (int i = 0; i < BITS_IN_BITMAP; i++) {
        if ((mark & (1<<i)) != 0)
          usedCells++;
      }
    }
    return usedCells;
  }

  private final void verboseFragmentationStatistics(boolean prepare) {
    int totUsedCellBytes = 0;      // bytes for cells actually in use
    int totCellBytes = 0;          // bytes consumed by cells
    int totBytes = 0;              // bytes consumed (incl header etc)
    int totBlocks = 0;
    printFragHeader(prepare, false);
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      VM_Address block = firstBlock.get(sizeClass);
      int sets =  bitmaps[sizeClass];
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
      Log.write(prepare ? "\n=> " : "\n=< ");
      Log.write("TOTAL FRAGMENTATION ");
      Log.write(prepare ? "BEFORE " : "AFTER ");
      Log.write("GC INVOCATION");
      Log.write(prepare ? "\n=> " : "\n=< ");
    }
    Log.writeln();
    if (all) Log.write("=");
    Log.write((prepare) ? "> " : "< "); 
    Log.write("szcls size    live free used net  util | ");
    for (int pctl = 0; pctl < FRAG_PERCENTILES; pctl++) {
      Log.write((pctl < (FRAG_PERCENTILES-1)) ? "<" : "<=");
      Log.write((100*(pctl+1))/FRAG_PERCENTILES);
      Log.write((pctl < (FRAG_PERCENTILES-1)) ? "% " : "%\n");
    }
    printFragDivider(prepare, all);
  }

  private final void printFragRow(boolean prepare, boolean all, boolean totals,
                                  int sizeClass, int usedCellBytes,
                                  int freeBytes, int cellBytes, int totBytes,
                                  int blocks) {
    if (all) Log.write("=");
    Log.write((prepare) ? "> " : "< "); 
    if (totals)
      Log.write("totals\t");
    else {
      Log.write(sizeClass); Log.write("\t");
      Log.write(cellSize[sizeClass]);Log.write("\t");
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
    Log.write(bytes/(double)(1<<20));
    Log.write(str);
  }
  private final void printRatio(int numerator, int denominator, String str) {
    Log.write(numerator/(double)denominator); 
    Log.write(str);
  }
  private final void printFragDivider(boolean prepare, boolean all) {
    if (all) Log.write("=");
    Log.write((prepare) ? "> " : "< "); 
    Log.write("----------------------------------------");
    for (int i = 0; i < FRAG_PERCENTILES; i++) 
      Log.write("-----");
    Log.writeln();
  }

  /****************************************************************************
   *
   * Sanity checks and debugging
   */
  private final int getUsedPages() {
    int bytes = 0;
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      bytes += getUsedBlockBytes(firstBlock.get(sc), sc);
    }
    return bytes>>LOG_BYTES_IN_PAGE;
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

  public boolean mustCollect() {
    if ((lastBytesAlloc ^ bytesAlloc) > MS_MUST_COLLECT_THRESHOLD) {
      lastBytesAlloc = bytesAlloc;
      return true;
    } else
      return false;
  }

  private static void dumpSizeClassData() {
    Log.writeln("\nsc\tc size\tsets\tcells\tblk sc\thdr\tspace\twaste\tutilization");
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      Log.write(sc); Log.write("\t");
      Log.write(cellSize[sc]); Log.write("\t");
      Log.write(bitmaps[sc]); Log.write("\t");
      Log.write(cellsInBlock[sc]); Log.write("\t");
      Log.write(blockSizeClass[sc]); Log.write("\t");
      Log.write(blockHeaderSize[sc]); Log.write("\t");
      Log.write(cellSize[sc]*cellsInBlock[sc]); Log.write("\t");
      //      Log.write(cellSize[sc]*cellsInBlock[sc]+blockHeaderSize[sc]+Block.BLOCK_HEADER_SIZE); Log.write("\t");
      Log.write(BlockAllocator.blockSize(blockSizeClass[sc]) - (cellSize[sc]*cellsInBlock[sc]+blockHeaderSize[sc])); Log.write("\t");
      Log.write(((float) (cellSize[sc]*cellsInBlock[sc]))/((float)  BlockAllocator.blockSize(blockSizeClass[sc]))); Log.writeln();
    }
  }

}
