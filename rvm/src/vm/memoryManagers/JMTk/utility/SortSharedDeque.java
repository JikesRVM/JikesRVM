/**
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003.
 */
package org.mmtk.utility;

import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Lock;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_AddressArray;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_MiscHeader;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of buffers
 * for shared use.  The data can be added to and removed from either end
 * of the deque. This class is based upon the SharedQueue class.  The sorting 
 * routines were modified from code written by Narendran Sachindran and 
 * Matthew Hertz for GCTk.
 *
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class SortSharedDeque extends SharedDeque implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  private static final int BYTES_PUSHED = BYTES_IN_ADDRESS * 5;
  private static final int MAX_STACK_SIZE = BYTES_PUSHED * 64;
  private static final VM_Offset INSERTION_SORT_LIMIT = VM_Offset.fromInt(80);
  
  /***********************************************************************
   *
   * Class variables
   */

  /**
   * Constructor
   *
   * @param rpa The allocator from which the instance should obtain buffers.
   */
  public SortSharedDeque(RawPageAllocator rpa, int arity) {
    super(rpa, arity);
    stackBase = VM_AddressArray.create(MAX_STACK_SIZE);
    stackLoc = 0;
  }

  /***********************************************************************
   *
   * Sorting methods, utilities, and instance variables
   */


  /**
   * Return the sorting key for the object passed as a parameter.
   *
   * @param ref The address of the object whose key is wanted
   * @return The value of the sorting key for this object
   */
  private static final VM_Word getKey(VM_Address ref) {
    return VM_MiscHeader.getDeathTime(ref);
  }
  
  private final static VM_Word mask16 = VM_Word.fromIntZeroExtend(0xffff0000);
  private final static VM_Word mask8 = VM_Word.fromIntZeroExtend(0x0000ff00);
  private final static VM_Word mask4 = VM_Word.fromIntZeroExtend(0x000000f0);
  private final static VM_Word mask2 = VM_Word.fromIntZeroExtend(0x0000000c);
  private final static VM_Word mask1 = VM_Word.fromIntZeroExtend(0x00000002);

  /**
   * Find the highest bit that is set in a longword and return a mask
   * with only that bit set.
   * 
   * @param addr Value for which the mask needs to be found
   * @return The highest bit set in the parameter
   */
  private static final VM_Word getBitMask(VM_Word addr) {
    int shift = 0;
    if (!addr.and(mask16).isZero()) {
      addr = addr.rshl(16);
      shift += 16;
    }
    if (!addr.and(mask8).isZero()) {
      addr = addr.rshl(8);
      shift += 8;
    }
    if (!addr.and(mask4).isZero()) {
      addr = addr.rshl(4);
      shift += 4;
    }
    if (!addr.and(mask2).isZero()) {
      addr = addr.rshl(2);
      shift += 2;
    }
    if (!addr.and(mask1).isZero()) {
      shift += 1;
    }
    return(VM_Word.one().lsh(shift));
  }
  
  /** 
   * Perform insertion sort within an intra-block address range.
   *
   *  @param begin Start address of the range to be sorted
   *  @param end End address of the range to be sorted
   */
  private final void insertionSort(VM_Address begin, VM_Address end) {
    VM_Address rPtr = begin.sub(BYTES_IN_ADDRESS);
    VM_Address lPtr;

    while (rPtr.GE(end)) {
      VM_Address rSlot = VM_Magic.getMemoryAddress(rPtr);
      VM_Word rKey = getKey(rSlot);
      lPtr = rPtr.add(BYTES_IN_ADDRESS);
      while (lPtr.LE(begin)) {
        VM_Address lSlot = VM_Magic.getMemoryAddress(lPtr);
        VM_Word lKey = getKey(lSlot);
        if (lKey.GT(rKey)) {
          VM_Magic.setMemoryAddress(lPtr.sub(BYTES_IN_ADDRESS), lSlot);
          lPtr = lPtr.add(BYTES_IN_ADDRESS);
        }
        else
          break;
      }
      VM_Magic.setMemoryAddress(lPtr.sub(BYTES_IN_ADDRESS), rSlot);
      rPtr = rPtr.sub(BYTES_IN_ADDRESS);
    }
  }

  /** 
   * Sort objects using radix exchange sort. An explicit stack is 
   *  maintained to avoid using recursion.
   */
  public final void sort() {
    VM_Address startPtr, startLink, endPtr, endLink;
    VM_Word bitMask;
    if (!head.EQ(HEAD_INITIAL_VALUE)) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(tail.NE(TAIL_INITIAL_VALUE));
      /* Obtain the bitmask for the first iteration and save the start &
	 end pointers and the bitmask on the stack */
      initStack();
      startPtr = popStack();
      while (!startPtr.isZero()) {
	startLink = popStack();
	endPtr = popStack();
	endLink = popStack();
	bitMask = (popStack()).toWord();
	if (startLink.NE(endLink)) {
	  partition(startPtr, startLink, endPtr, endLink, bitMask);
	} else if (startPtr.GT(endPtr)) {
	  /* Use insertionSort for a limited number of objects within
	     a single block */
	  if (startPtr.diff(endPtr).sLT(INSERTION_SORT_LIMIT)) {
	    insertionSort(startPtr, endPtr);
	  } else {
	    partition(startPtr, startLink, endPtr, endLink, bitMask);
	  }
	}
	// Get the next set of data to sort
	startPtr = popStack();
      }
    }
    checkIfSorted();
  }

  /** 
   * Partition the slots in an address range based on the value in 
   * a particular bit. Place the start & end addresses of the two
   * partitions & a bitmask per partition (which indicates the highest 
   * bit position at which the max & min of a partition differ) on the
   * stack. This works just like the partition in quick sort, except
   * that a bit value is being compared here
   *
   * @param startAddr The start address of the range to be sorted
   * @param startLinkAddr The address where the start block has its next field
   * @param endAddr The end address of the range to be sorted
   * @param endLinkAddr The address where the end block has its next field
   * @param bitMask The mask in which the bit to be commpared is set
   */
  private final void partition(VM_Address startAddr, VM_Address startLinkAddr,
			       VM_Address endAddr, VM_Address endLinkAddr,
			       VM_Word bitMask) {
    VM_Address travPtr = endAddr;
    VM_Address travLink = endLinkAddr;
    VM_Address stopPtr = startAddr;
    VM_Address stopLink = startLinkAddr;
    VM_Address travSlot, stopSlot;
    VM_Word travKey, stopKey;
    VM_Word lmax = VM_Word.zero(), rmax = VM_Word.zero();
    VM_Word lmin = VM_Word.max(), rmin = VM_Word.max();

    while (true) {
      /* Compute the address range  within the current block to compute. */
      VM_Address endOfBlock = travLink;

      /* Move the left pointer until the right pointer is reached
	 or an address with a 0 value in the bit position is found. */
      while (true) {
        travSlot = VM_Magic.getMemoryAddress(travPtr);
        travKey = getKey(travSlot);

	/* If we reach the end. */
	if (travPtr.EQ(stopPtr))
	  break;
        /* If we found a 0 in bit position, break. */
        if (travKey.and(bitMask).isZero())
          break;
        if (travKey.GT(rmax))
          rmax = travKey;
        if (travKey.LT(rmin))
          rmin = travKey;
	/* Move to the next entry. */
        travPtr = travPtr.add(BYTES_IN_ADDRESS);
        /* If at end of remset block, move to next block */
        if (travPtr.EQ(endOfBlock)) {
	  travLink = getPrev(travPtr);
	  endOfBlock = travLink;
          travPtr = bufferStart(endOfBlock);
        }
      }

      /* Store the end of the block. */
      endOfBlock = bufferStart(stopPtr);
      /* Move the right pointer until the left pointer is reached
	 or an address with a 1 value in the bit position is found. */
      while (true) {
        stopSlot = VM_Magic.getMemoryAddress(stopPtr);
        stopKey = getKey(stopSlot);
        /* Found a 1 in bit position, break. */
        if (!stopKey.and(bitMask).isZero())
          break;
        if (stopKey.GT(lmax))
          lmax = stopKey;
	if (stopKey.LT(lmin))
          lmin = stopKey;
        if (stopPtr.EQ(travPtr))
	  break;
        /* Move to the next entry, which may be in the next block. */
        if (stopPtr.EQ(endOfBlock)) {
	  stopLink = getNext(stopLink);
	  stopPtr = stopLink;
	  endOfBlock = bufferStart(stopPtr);
        }
	stopPtr = stopPtr.sub(BYTES_IN_ADDRESS);
      }
      if (stopPtr.EQ(travPtr))
	break;
      /* interchange the values pointed to by the left and right pointers */
      VM_Magic.setMemoryAddress(travPtr, stopSlot);
      VM_Magic.setMemoryAddress(stopPtr, travSlot);
    }

    /* If max value is not equal to the min value in the right partition,
       (not all slots are identical) push the right partition on to the stack */
    if (rmax.GT(rmin)) {
      if (travPtr.EQ(bufferStart(travPtr))) {
	 stopLink = getNext(travLink);
	 stopPtr = stopLink;
      } else {
	stopLink = travLink;
	stopPtr = travPtr;
      }
      pushOnStack(getBitMask(rmax.xor(rmin)).toAddress());
      pushOnStack(endLinkAddr);
      pushOnStack(endAddr);
      pushOnStack(stopLink);
      pushOnStack(stopPtr.sub(BYTES_IN_ADDRESS));
    }
    /* if max value is not equal to the min value in the left partition,
       (not all slots are identical) push the left partition on to the stack */
    if (lmax.GT(lmin)) {
      pushOnStack(getBitMask(lmax.xor(lmin)).toAddress());
      pushOnStack(travLink);
      pushOnStack(travPtr);
      pushOnStack(startLinkAddr);
      pushOnStack(startAddr);
    }
  }

  /*************************************************************************
   *
   * Sorting Stack management routines
   */
  private int stackLoc;
  private VM_AddressArray stackBase;

  /* 
   * Allocate memory for the stack and intialize it with the first range
   * to partition
   */
  private final void initStack() {
    stackLoc = 0;

    VM_Address endOfBlock = tail;
    VM_Address trailer = tail;
    VM_Address startPtr = bufferStart(endOfBlock);
    VM_Word min = VM_Word.max();
    VM_Word max = VM_Word.zero();
    // Find the max. and min addresses in the object buffer
    while (endOfBlock.NE(HEAD_INITIAL_VALUE)) {
      startPtr = bufferStart(endOfBlock);
      while (startPtr.LT(endOfBlock)) {
        VM_Address startSlot = VM_Magic.getMemoryAddress(startPtr);
        VM_Word startKey = getKey(startSlot);
        if (startKey.GT(max))
          max = startKey;
        if (startKey.LT(min))
          min = startKey;
        startPtr = startPtr.add(BYTES_IN_ADDRESS);
      }
      endOfBlock = getPrev(startPtr);
    }

    // If max and min are different (not all elements are equal), push the
    // start, end and bitmask values on the stack
    if (max.GT(min)) {
      pushOnStack(getBitMask(max.xor(min)).toAddress());
      pushOnStack(tail);
      pushOnStack(bufferStart(tail));
      pushOnStack(startPtr);
      pushOnStack(startPtr.sub(BYTES_IN_ADDRESS));
    }
  }

 /** 
  * Push an address on to the stack
  * 
  * @param val The address to be pushed
  */
  private final void pushOnStack(VM_Address val) {
    stackBase.set(stackLoc, val);
    stackLoc++;
  }
  
 /**
  * Pop an address from the stack
  * 
  * @return The address at the top of the stack, or 0 if stack is empty
  */
  private final VM_Address popStack() {
    if (stackLoc == 0)
      return VM_Address.zero();
    stackLoc--;
    return stackBase.get(stackLoc);
  }
  
 /** 
  *  Debug routine, used to check if the object buffer is sorted correctly in
  *  decreasing final reference deletion time
  *
  * @param addr The slot containing the address of the buffer to check.
  */
  private final void checkIfSorted() {
    if (VM_Interface.VerifyAssertions) {
      VM_Address next, buf, end;
      VM_Word prevKey = VM_Word.max();
      end = tail;
      buf = bufferStart(end);
      while (buf.NE(HEAD_INITIAL_VALUE)) {
	// iterate through the block
	while (buf.LT(end)) {
	  VM_Address slot = VM_Magic.getMemoryAddress(buf);
	  VM_Word key = getKey(slot);
	  VM_Interface._assert(key.LE(prevKey));
	  prevKey = key;
	  buf = buf.add(BYTES_IN_ADDRESS);
	}
	end = getPrev(end);
	buf = bufferStart(end);
      }
    }
  }
}
