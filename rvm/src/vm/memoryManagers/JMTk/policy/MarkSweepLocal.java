/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.policy;

import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.alloc.SegregatedFreeList;
import org.mmtk.utility.Log;
import org.mmtk.utility.Memory;
import org.mmtk.utility.Options;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Plan;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  private static final int FRAG_PERCENTILES = 5;  // 20% iles

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

  protected final boolean preserveFreeList() { return !LAZY_SWEEP; }

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
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];

    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
        int usableBytes = BlockAllocator.blockSize(blk);
        int cells = usableBytes/cellSize[sc];
        blockSizeClass[sc] = blk;
        cellsInBlock[sc] = cells;
        /*cells must start at multiple of BYTES_IN_PARTICLE
           because cellSize is also supposed to be multiple, this should do the trick: */
        blockHeaderSize[sc] = BlockAllocator.blockSize(blk) - cells * cellSize[sc];
        if (((usableBytes < BYTES_IN_PAGE) && (cells*2 > MAX_CELLS)) ||
            ((usableBytes > (BYTES_IN_PAGE>>1)) && (cells > MIN_CELLS)))
          break;
      }
    }
    //    dumpSizeClassData();
  }

  /**
   * Constructor
   *
   * @param space The mark-sweep space to which this allocator
   * instances is bound.  The space's VMResource and MemoryResource
   * are used to initialize the superclass.
   */
  public MarkSweepLocal(MarkSweepSpace space) {
    super(space.getVMResource(), space.getMemoryResource());
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
  protected final Address advanceToBlock(Address block, int sizeClass) {
    if (LAZY_SWEEP)
      return makeFreeListFromLiveBits(block, sizeClass);
    else
      return getFreeList(block);
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
    //    int waste = blockAllocator.unusedBytes();
    //    Log.write("B ");
    //    Log.write(waste/(float)(1<<20)); Log.write(" MB + ");
    Log.write("F ");
    int waste = unusedBytes(prepare);
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
      Address block = (prepare) ? currentBlock.get(sizeClass) : firstBlock.get(sizeClass);
      while (!block.isZero()) {
        unused += cellSize[sizeClass] * (cellsInBlock[sizeClass] - markedCells(block, sizeClass));
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
   * @return the number of cells marked as live on this block.
   */
  private final int markedCells(Address block, int sizeClass)
    throws InlinePragma {
    Extent cellBytes = Extent.fromInt(cellSize[sizeClass]);
    Address cellCursor = block.add(blockHeaderSize[sizeClass]);
    Address nextCellCursor = cellCursor.add(cellBytes);
    Address markCursor = alignToLiveStride(cellCursor);
    Extent blockSize = Extent.fromInt(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
    Address end = block.add(blockSize);
    boolean marked = false;
    int markCount = 0;
    while (markCursor.LT(end)) {
      Word mark = getLiveBits(markCursor);
      for (int i=0; i < BITS_IN_WORD; i++) {
        if (!mark.isZero() && !(mark.and(Word.one().lsh(i)).isZero())) {
	  marked = true;
        }
        markCursor = markCursor.add(BYTES_PER_LIVE_BIT);
        if (markCursor.GE(nextCellCursor)) {
	  if (marked) markCount++;
          cellCursor = nextCellCursor;
          nextCellCursor = nextCellCursor.add(cellBytes);
	  marked = false;
        }
      }
    }
    return markCount;
  }

  private final void verboseFragmentationStatistics(boolean prepare) {
    int totUsedCellBytes = 0;      // bytes for cells actually in use
    int totCellBytes = 0;          // bytes consumed by cells
    int totBytes = 0;              // bytes consumed (incl header etc)
    int totBlocks = 0;
    printFragHeader(prepare, false);
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      Address block = firstBlock.get(sizeClass);
      int blocks = 0;
      int usedCells = 0;
      Address current = currentBlock.get(sizeClass);
      boolean getUsed = block.EQ(current) || current.isZero();
      while (!block.isZero()) {
        blocks++;
        if (getUsed) {
          int marked = markedCells(block, sizeClass);
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

  private final int getUsedBlockBytes(Address block, int sizeClass) {
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
    Log.writeln("\nsc\tc size\tcells\tblk sc\thdr/pad\tspace\twaste\tutilization");
    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      Log.write(sc); Log.write("\t");
      Log.write(cellSize[sc]); Log.write("\t");
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
