/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.utility;

import org.mmtk.plan.Plan;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_AddressArray;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_WordArray;

/**
 * This class implements "block" data structures of various sizes.<p>
 * 
 * Blocks are a non-shared (thread-local) coarse-grained unit of
 * storage. Blocks are available in approximately power-of-two sizes.
 * Virtual memory space is taken from a VM resource, and pages
 * consumed by blocks are accounted for by a memory resource.
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class BlockAllocator implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 


  /****************************************************************************
   *
   * Class variables
   */

  // block freelist
  private static final byte MIN_BLOCK_LOG = 9;  // 512 bytes
  public static final byte MAX_BLOCK_LOG = 15; // 32K bytes
  public static final int MAX_BLOCK_SIZE = 1<<MAX_BLOCK_LOG;
  private static final byte MAX_BLOCK_PAGES = 1<<(MAX_BLOCK_LOG - LOG_BYTES_IN_PAGE);
  private static final byte MAX_BLOCK_SIZE_CLASS = MAX_BLOCK_LOG - MIN_BLOCK_LOG;
  private static final byte PAGE_BLOCK_SIZE_CLASS = LOG_BYTES_IN_PAGE - MIN_BLOCK_LOG;
  public static final int BLOCK_SIZE_CLASSES = MAX_BLOCK_SIZE_CLASS + 1;
  private static final int FREE_LIST_BITS = 4;
  private static final int FREE_LIST_ENTRIES = 1<<(FREE_LIST_BITS*2);

  // Block header and field offsets
  public static final int BLOCK_HEADER_SIZE = 2 * BYTES_IN_ADDRESS;
  private static final int NEXT_FIELD_OFFSET = -(BYTES_IN_ADDRESS);
  private static final int PREV_FIELD_OFFSET = -(2 * BYTES_IN_ADDRESS);
  private static final int FL_MARKER_OFFSET = -(2 * BYTES_IN_ADDRESS);
  private static final int FL_NEXT_FIELD_OFFSET = -(BYTES_IN_ADDRESS);
  private static final int FL_PREV_FIELD_OFFSET = 0;
  private static final VM_Address BASE_FL_MARKER = VM_Address.max(); // -1
  private static final VM_Address MIN_FL_MARKER = BASE_FL_MARKER.sub(FREE_LIST_ENTRIES);
  private static final VM_Address USED_MARKER = VM_Address.zero();
  private static VM_WordArray blockMask;
  private static VM_WordArray buddyMask;
  
  // granularity of memory resource requests
  private static final int MR_POLL_PAGES = 1<<4; // 16 pages

  private static final boolean PARANOID = false;

  /****************************************************************************
   *
   * Instance variables
   */
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  private VM_AddressArray freeList;
  private Plan plan;
  private int pagesInUse;

  /****************************************************************************
   *
   * Initialization
   */
  BlockAllocator(FreeListVMResource vmr, MemoryResource mr, Plan thePlan) {
    vmResource = vmr;
    memoryResource = mr;
    plan = thePlan;
    freeList = VM_AddressArray.create(FREE_LIST_ENTRIES);
  }

  static {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(BLOCK_SIZE_CLASSES <= (1<<FREE_LIST_BITS));
    blockMask = VM_WordArray.create(BLOCK_SIZE_CLASSES);
    buddyMask = VM_WordArray.create(BLOCK_SIZE_CLASSES);
    for (byte sc = 0; sc <= MAX_BLOCK_SIZE_CLASS; sc++) {
      blockMask.set(sc, VM_Word.one().lsh(MIN_BLOCK_LOG + sc).sub(VM_Word.one()).not());
      buddyMask.set(sc, VM_Word.one().lsh(MIN_BLOCK_LOG + sc));
    }
  }

  /****************************************************************************
   *
   * Allocation & freeing
   */
  public final VM_Address alloc(byte blockSizeClass) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert((blockSizeClass >= 0) && (blockSizeClass <= MAX_BLOCK_SIZE_CLASS));
    VM_Address rtn = VM_Address.zero();
    if (PARANOID)
      sanity();

    // increment page charges
    if (blockSizeClass >= PAGE_BLOCK_SIZE_CLASS) {
      if (!incPageCharge(1<<(blockSizeClass-PAGE_BLOCK_SIZE_CLASS)))
        return VM_Address.zero();  // need to GC & retry...
    }

    // try to use satisfy from free list
    byte freeListID = getFreeListID(blockSizeClass);
    if (!freeList.get(freeListID).isZero()) {
      rtn = freeList.get(freeListID);
      freeList.set(freeListID, getNextFLBlock(rtn));
      if (!getNextFLBlock(rtn).isZero())
        setPrevFLBlock(getNextFLBlock(rtn), VM_Address.zero());
    } else {
      rtn = slowAlloc(blockSizeClass);
      if (rtn.isZero())
        return VM_Address.zero(); // need to GC & retry...
    }
    
    if (VM_Interface.VerifyAssertions) {
      VM_Word mask = blockMask.get(blockSizeClass);
      VM_Interface._assert(rtn.EQ(rtn.toWord().and(mask).add(VM_Word.fromIntZeroExtend(BLOCK_HEADER_SIZE)).toAddress()));
    }

    if (PARANOID)
      sanity();
    return rtn;
  }

  private final VM_Address slowAlloc(byte originalSC) {
    return slowAlloc(originalSC, originalSC);
  }
  private final VM_Address slowAlloc(byte requestedSC, byte originalSC) {
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert((originalSC >= 0) && (originalSC <= MAX_BLOCK_SIZE_CLASS));
      VM_Interface._assert((requestedSC >= 0) && (requestedSC <= MAX_BLOCK_SIZE_CLASS));
    }
    VM_Address rtn = VM_Address.zero();

    if (requestedSC == MAX_BLOCK_SIZE_CLASS) {
      // coarsest grain request satisfied by VM resource request
      rtn = vmResource.acquire(1<<(MAX_BLOCK_LOG-LOG_BYTES_IN_PAGE), 
                               memoryResource, originalSC, false);
      if (rtn.isZero())
        return VM_Address.zero(); // need to GC & retry...
      rtn = rtn.add(BLOCK_HEADER_SIZE);
    } else {
      byte srcSC = getFreeListID(requestedSC, originalSC);
      if (!freeList.get(srcSC).isZero()) {  // available through free list
        rtn = freeList.get(srcSC);
        if (VM_Interface.VerifyAssertions)
          VM_Interface._assert(VMResource.getTag(rtn) == originalSC);
        freeList.set(srcSC, getNextFLBlock(rtn));
        if (!getNextFLBlock(rtn).isZero())
          setPrevFLBlock(getNextFLBlock(rtn), VM_Address.zero());
      } else {                     // must split larger sizes
        rtn = slowAlloc((byte) (requestedSC + 1), originalSC);
        if (rtn.isZero())
          return VM_Address.zero(); // need to GC & retry...
        if (VM_Interface.VerifyAssertions)
          VM_Interface._assert(VMResource.getTag(rtn) == originalSC);
        rtn = split(rtn, (byte) (requestedSC + 1), originalSC, srcSC);
        if (rtn.isZero())
          return VM_Address.zero(); // need to GC & retry...
        if (VM_Interface.VerifyAssertions)
          VM_Interface._assert(VMResource.getTag(rtn) == originalSC);
      }
    }
    if (!rtn.isZero())
      markAsUsed(rtn);
    return rtn;
  }
  
  private final VM_Address split(VM_Address parent, byte parentSC,
                                 byte originalSC, byte targetFL) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(VMResource.getTag(parent) == originalSC);
    if (parentSC == PAGE_BLOCK_SIZE_CLASS) {
      if (!incPageCharge(1))
        return VM_Address.zero();
    }
    VM_Address next = parent.add(rawBlockSize(parentSC - 1));
    
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(VMResource.getTag(next) == getOriginalSC(targetFL));
    addToFreeList(next, targetFL);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(VMResource.getTag(parent) == originalSC);
    return parent;
  }
  
  public final void free(VM_Address block, byte blockSizeClass) {
    if (PARANOID)
      sanity();
    //    Log.write("f["); Log.write(block); Log.write(" "); Log.write(blockSizeClass);     Log.writeln("]");
    if (blockSizeClass >= PAGE_BLOCK_SIZE_CLASS) {
      decPageCharge(1<<(blockSizeClass-PAGE_BLOCK_SIZE_CLASS));
    }
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(blockSizeClass == VMResource.getTag(block));
    merge(block, blockSizeClass, blockSizeClass);
    if (PARANOID)
      sanity();
  }

  private final void markAsUsed(VM_Address block) {
    VM_Magic.setMemoryAddress(block.add(FL_MARKER_OFFSET), USED_MARKER);
  }

  private final boolean isFree(VM_Address block) {
    return VM_Magic.getMemoryAddress(block.add(FL_MARKER_OFFSET)).GE(MIN_FL_MARKER);
  }

  private final byte getFreeListID(VM_Address block) {
    return (byte) BASE_FL_MARKER.diff(VM_Magic.getMemoryAddress(block.add(FL_MARKER_OFFSET))).toInt();
  }
  private final byte getFreeListID(byte sizeClass) {
    return (byte) (sizeClass<<FREE_LIST_BITS);
  }

  private final byte getFreeListID(byte sizeClass, byte originalSizeClass) {
    return (byte) (sizeClass | (originalSizeClass<<FREE_LIST_BITS));
  }
  private final byte getOriginalSC(byte freeListId) {
    return (byte) (freeListId >> FREE_LIST_BITS);
  }

  private final void release(VM_Address block, byte blockSizeClass) {
    block = block.sub(BLOCK_HEADER_SIZE);
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(block.toWord().and(VM_Word.one().lsh(MAX_BLOCK_LOG).sub(VM_Word.one())).isZero());
    vmResource.release(block, memoryResource, blockSizeClass, false);
  }

  private final void sanity() {
    for (int fl = 0; fl < FREE_LIST_ENTRIES; fl++) {
       VM_Address block = freeList.get(fl);
       VM_Address prev = VM_Address.zero();
       while (!block.isZero()) {
         if (VM_Interface.VerifyAssertions) {
           VM_Interface._assert(isFree(block));
           VM_Interface._assert(fl == getFreeListID(block));
           VM_Interface._assert(prev.EQ(getPrevFLBlock(block)));
           VM_Interface._assert(freeListEntries(block) == 1);
         }
         prev = block;
         block = getNextFLBlock(block);
       }
     }
  }

  private final int freeListEntries(VM_Address block) {
    int entries = 0;
    for (int fl = 0; fl < FREE_LIST_ENTRIES; fl++) {
      VM_Address blk = freeList.get(fl);
      while (!blk.isZero()) {
        if (blk.EQ(block))
          entries++;
        blk = getNextFLBlock(blk);
      }
    }
    return entries;
  }

  /**
   * Return the number of bytes consumed by unused blocks.
   *
   * @return The number of bytes consumed by unused blocks.
   */
  public final int unusedBytes() {
    int minipgs = 0;
    for (int fl = 0; fl < FREE_LIST_ENTRIES; fl++) {
      VM_Address block = freeList.get(fl);
      while (!block.isZero()) {
        int sc;
        if ((fl & ((1<<FREE_LIST_BITS)-1)) != 0)
          sc = fl & ((1<<FREE_LIST_BITS)-1);
        else 
          sc = fl>>>FREE_LIST_BITS;
        minipgs += 1<<sc;
        block = getNextFLBlock(block);
      }
    }
    return (minipgs<<MIN_BLOCK_LOG);
  }

  private final void merge(VM_Address child, byte childSC, byte originalSC) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(originalSC == VMResource.getTag(child));
    if (childSC == MAX_BLOCK_SIZE_CLASS)
      release(child, originalSC);
    else {
      VM_Address buddy = child.toWord().xor(buddyMask.get(childSC)).toAddress();
      byte flid = getFreeListID(childSC, originalSC);
      if (isFree(buddy) && (getFreeListID(buddy) == flid)) {
        removeFromFreeList(buddy, flid);
        if (child.GT(buddy))
          child = buddy;
        childSC++;
        if (childSC == PAGE_BLOCK_SIZE_CLASS)
          decPageCharge(1);
        merge(child, childSC, originalSC);
      } else
        addToFreeList(child, flid);
    }
  }


  private final void addToFreeList(VM_Address block, byte freeListID) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(VMResource.getTag(block) == getOriginalSC(freeListID));
    VM_Address next = freeList.get(freeListID);
    VM_Magic.setMemoryAddress(block.add(FL_MARKER_OFFSET), BASE_FL_MARKER.sub(freeListID));
    VM_Magic.setMemoryAddress(block.add(FL_NEXT_FIELD_OFFSET), next);
    VM_Magic.setMemoryAddress(block.add(FL_PREV_FIELD_OFFSET), VM_Address.zero());
    if (!next.isZero())
      VM_Magic.setMemoryAddress(next.add(FL_PREV_FIELD_OFFSET), block);
    freeList.set(freeListID, block);
    //    Log.write("a["); Log.write(block); Log.write(" "); Log.write(freeListID); Log.write(" "); Log.write(getNextFLBlock(block)); Log.write(" "); Log.write(getPrevFLBlock(block)); Log.writeln("]");
  }

  private final void removeFromFreeList(VM_Address block, byte freeListID) {
    //    Log.write("r["); Log.write(block); Log.write(" "); Log.write(freeListID); Log.write(" "); Log.write(getNextFLBlock(block)); Log.write(" "); Log.write(getPrevFLBlock(block)); Log.write(" "); Log.write(freeList.get(freeListID));
    if (freeList.get(freeListID).EQ(block)) {
      freeList.set(freeListID, getNextFLBlock(block));
      if (!getNextFLBlock(block).isZero())
        setPrevFLBlock(getNextFLBlock(block), VM_Address.zero());
    }
    else {
      VM_Address prev = getPrevFLBlock(block);
      VM_Address next = getNextFLBlock(block);
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(!prev.isZero());
      setNextFLBlock(prev, next);
      if (!next.isZero())
        setPrevFLBlock(next, prev);
    }
    //    Log.writeln("]");
  }

  
  /****************************************************************************
   *
   * Linked list operations
   */


  public static final VM_Address getNextBlock(VM_Address block) {
    return VM_Magic.getMemoryAddress(block.add(NEXT_FIELD_OFFSET));
  }

  private static final VM_Address getNextFLBlock(VM_Address block) {
    return VM_Magic.getMemoryAddress(block.add(FL_NEXT_FIELD_OFFSET));
  }

  private static final void setNextFLBlock(VM_Address block, VM_Address next) {
    VM_Magic.setMemoryAddress(block.add(FL_NEXT_FIELD_OFFSET), next);
  }

  private static final VM_Address getPrevFLBlock(VM_Address block) {
    return VM_Magic.getMemoryAddress(block.add(FL_PREV_FIELD_OFFSET));
  }

  private static final void setPrevFLBlock(VM_Address block, VM_Address prev) {
    VM_Magic.setMemoryAddress(block.add(FL_PREV_FIELD_OFFSET), prev);
  }

  public static final VM_Address getPrevBlock(VM_Address block) {
    return VM_Magic.getMemoryAddress(block.add(PREV_FIELD_OFFSET));
  }

  public static final void linkedListInsert(VM_Address block, VM_Address prev) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!block.isZero());
    VM_Address next;
    if (!prev.isZero()) {
      next = VM_Magic.getMemoryAddress(prev.add(NEXT_FIELD_OFFSET));
      VM_Magic.setMemoryAddress(prev.add(NEXT_FIELD_OFFSET), block);
    } else
      next = VM_Address.zero();
    VM_Magic.setMemoryAddress(block.add(PREV_FIELD_OFFSET), prev);
    VM_Magic.setMemoryAddress(block.add(NEXT_FIELD_OFFSET), next);
    if (!next.isZero())
      VM_Magic.setMemoryAddress(next.add(PREV_FIELD_OFFSET), block);
  }

  public static final void unlinkBlock(VM_Address block) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!block.isZero());
    VM_Address next = getNextBlock(block);
    VM_Address prev = getPrevBlock(block);
    VM_Magic.setMemoryAddress(block.add(PREV_FIELD_OFFSET), VM_Address.zero());
    VM_Magic.setMemoryAddress(block.add(NEXT_FIELD_OFFSET), VM_Address.zero());
    if (!prev.isZero())
      VM_Magic.setMemoryAddress(prev.add(NEXT_FIELD_OFFSET), next);
    if (!next.isZero())
      VM_Magic.setMemoryAddress(next.add(PREV_FIELD_OFFSET), prev);
  }


  /****************************************************************************
   *
   * Misc
   */

  private final boolean incPageCharge(int pages) {
    boolean rtn = true;
    // consuming whole pages, so must inc page count
    int newPagesInUse = pagesInUse + pages;
    if ((pagesInUse == 0) || ((newPagesInUse ^ pagesInUse) > MR_POLL_PAGES))
      rtn = memoryResource.acquire(MR_POLL_PAGES);
    if (rtn)
      pagesInUse = newPagesInUse;
    return rtn;
  }
  
  private final void decPageCharge(int pages) {
    // releasing whole pages, so must dec page count
    int newPagesInUse = pagesInUse - pages;
    if ((newPagesInUse == 0) || ((newPagesInUse ^ pagesInUse) > MR_POLL_PAGES))
      memoryResource.release(MR_POLL_PAGES);
    pagesInUse = newPagesInUse;
  }

  /**
   * Return the block for a given cell address.
   */
  public static final VM_Address getBlockStart(VM_Address cell,
                                               byte blockSizeClass) {
    VM_Word mask = blockMask.get(blockSizeClass);
    return cell.toWord().and(mask).add(VM_Word.fromIntZeroExtend(BLOCK_HEADER_SIZE)).toAddress();
  }

  public static final int blockSize(int blockSizeClass) {
    return rawBlockSize(blockSizeClass) - BLOCK_HEADER_SIZE;
  }

  private static final int rawBlockSize(int blockSizeClass) {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert((blockSizeClass >= 0) && (blockSizeClass <= MAX_BLOCK_SIZE_CLASS));
    return 1<<(MIN_BLOCK_LOG + blockSizeClass);
  }

  private void setPageTags(VM_Address block, byte sizeClass) {
    int pages = (sizeClass <= PAGE_BLOCK_SIZE_CLASS) ? 1 : 1<<(sizeClass-PAGE_BLOCK_SIZE_CLASS);
    vmResource.setTag(block.sub(BLOCK_HEADER_SIZE), pages, sizeClass);
  }

}
