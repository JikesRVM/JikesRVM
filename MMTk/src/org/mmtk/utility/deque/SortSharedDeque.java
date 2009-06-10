/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.deque;

import org.mmtk.policy.RawPageSpace;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of buffers
 * for shared use.  The data can be added to and removed from either end
 * of the deque. This class is based upon the SharedQueue class.  The sorting
 * routines were modified from code written by Narendran Sachindran and
 * Matthew Hertz for GCTk.
 */
@Uninterruptible public abstract class SortSharedDeque extends SharedDeque {


  private static final int BYTES_PUSHED = BYTES_IN_ADDRESS * 5;
  private static final int MAX_STACK_SIZE = BYTES_PUSHED * 64;
  private static final Offset INSERTION_SORT_LIMIT = Offset.fromIntSignExtend(80);

  /***********************************************************************
   *
   * Class variables
   */

  /**
   * Constructor
   *
   * @param rps The space from which the instance should obtain buffers.
   */
  public SortSharedDeque(String name, RawPageSpace rps, int arity) {
    super(name, rps, arity);
    stackBase = AddressArray.create(MAX_STACK_SIZE);
    stackLoc = 0;
  }

  /***********************************************************************
   *
   * Sorting methods, utilities, and instance variables
   */


  /**
   * Return the sorting key for the object passed as a parameter.
   *
   * @param obj The address of the object whose key is wanted
   * @return The value of the sorting key for this object
   */
  protected abstract Word getKey(Address obj);

  private static final Word mask16 = Word.fromIntZeroExtend(0xffff0000);
  private static final Word mask8 = Word.fromIntZeroExtend(0x0000ff00);
  private static final Word mask4 = Word.fromIntZeroExtend(0x000000f0);
  private static final Word mask2 = Word.fromIntZeroExtend(0x0000000c);
  private static final Word mask1 = Word.fromIntZeroExtend(0x00000002);

  /**
   * Find the highest bit that is set in a longword and return a mask
   * with only that bit set.
   *
   * @param addr Value for which the mask needs to be found
   * @return The highest bit set in the parameter
   */
  private static Word getBitMask(Word addr) {
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
    return (Word.one().lsh(shift));
  }

  /**
   * Perform insertion sort within an intra-block address range.
   *
   *  @param begin Start address of the range to be sorted
   *  @param end End address of the range to be sorted
   */
  private void insertionSort(Address begin, Address end) {
    Address rPtr = begin.minus(BYTES_IN_ADDRESS);
    Address lPtr;

    while (rPtr.GE(end)) {
      Address rSlot = rPtr.loadAddress();
      Word rKey = getKey(rSlot);
      lPtr = rPtr.plus(BYTES_IN_ADDRESS);
      while (lPtr.LE(begin)) {
        Address lSlot = lPtr.loadAddress();
        Word lKey = getKey(lSlot);
        if (lKey.GT(rKey)) {
          lPtr.minus(BYTES_IN_ADDRESS).store(lSlot);
          lPtr = lPtr.plus(BYTES_IN_ADDRESS);
        } else {
          break;
        }
      }
      lPtr.minus(BYTES_IN_ADDRESS).store(rSlot);
      rPtr = rPtr.minus(BYTES_IN_ADDRESS);
    }
  }

  /**
   * Sort objects using radix exchange sort. An explicit stack is
   *  maintained to avoid using recursion.
   */
  public void sort() {
    Address startPtr, startLink, endPtr, endLink;
    Word bitMask;
    if (!head.EQ(HEAD_INITIAL_VALUE)) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tail.NE(TAIL_INITIAL_VALUE));
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
  private void partition(Address startAddr, Address startLinkAddr,
                               Address endAddr, Address endLinkAddr,
                               Word bitMask) {
    Address travPtr = endAddr;
    Address travLink = endLinkAddr;
    Address stopPtr = startAddr;
    Address stopLink = startLinkAddr;
    Address travSlot, stopSlot;
    Word travKey, stopKey;
    Word lmax = Word.zero(), rmax = Word.zero();
    Word lmin = Word.max(), rmin = Word.max();

    while (true) {
      /* Compute the address range within the current block to compute. */
      Address endOfBlock = travLink;

      /* Move the left pointer until the right pointer is reached
         or an address with a 0 value in the bit position is found. */
      while (true) {
        travSlot = travPtr.loadAddress();
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
        travPtr = travPtr.plus(BYTES_IN_ADDRESS);
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
        stopSlot = stopPtr.loadAddress();
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
        stopPtr = stopPtr.minus(BYTES_IN_ADDRESS);
      }
      if (stopPtr.EQ(travPtr))
        break;
      /* interchange the values pointed to by the left and right pointers */
      travPtr.store(stopSlot);
      stopPtr.store(travSlot);
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
      pushOnStack(stopPtr.minus(BYTES_IN_ADDRESS));
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
  private AddressArray stackBase;

  /*
   * Allocate memory for the stack and intialize it with the first range
   * to partition
   */
  private void initStack() {
    stackLoc = 0;

    Address endOfBlock = tail;
    Address startPtr = bufferStart(endOfBlock);
    Word min = Word.max();
    Word max = Word.zero();
    // Find the max. and min addresses in the object buffer
    while (endOfBlock.NE(HEAD_INITIAL_VALUE)) {
      startPtr = bufferStart(endOfBlock);
      while (startPtr.LT(endOfBlock)) {
        Address startSlot = startPtr.loadAddress();
        Word startKey = getKey(startSlot);
        if (startKey.GT(max))
          max = startKey;
        if (startKey.LT(min))
          min = startKey;
        startPtr = startPtr.plus(BYTES_IN_ADDRESS);
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
      pushOnStack(startPtr.minus(BYTES_IN_ADDRESS));
    }
  }

  /**
   * Push an address on to the stack
   *
  * @param val The address to be pushed
   */
  private void pushOnStack(Address val) {
    stackBase.set(stackLoc, val);
    stackLoc++;
  }

  /**
   * Pop an address from the stack
   *
   * @return The address at the top of the stack, or 0 if stack is empty
   */
  private Address popStack() {
    if (stackLoc == 0)
      return Address.zero();
    stackLoc--;
    return stackBase.get(stackLoc);
  }

  /**
   * Debug routine, used to check if the object buffer is sorted correctly in
   * decreasing final reference deletion time
 */
  private void checkIfSorted() {
    if (VM.VERIFY_ASSERTIONS) {
      Address buf, end;
      Word prevKey = Word.max();
      end = tail;
      buf = bufferStart(end);
      while (buf.NE(HEAD_INITIAL_VALUE)) {
        // iterate through the block
        while (buf.LT(end)) {
          Address slot = buf.loadAddress();
          Word key = getKey(slot);
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(key.LE(prevKey));
          prevKey = key;
          buf = buf.plus(BYTES_IN_ADDRESS);
        }
        end = getPrev(end);
        buf = bufferStart(end);
      }
    }
  }
}
