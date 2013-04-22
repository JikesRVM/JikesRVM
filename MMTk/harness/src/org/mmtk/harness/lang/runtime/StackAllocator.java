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
package org.mmtk.harness.lang.runtime;

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.harness.SimulatedMemory;

import static org.vmmagic.unboxed.harness.MemoryConstants.BYTES_IN_PAGE;

/**
 * This class allocates space in simulated memory for a pcode interpreter's
 * runtime stack.  We don't use MMTk allocators for the obvious
 * reason.
 *
 * Very simple-minded: all stacks are the same size, an integral
 * number of pages, with a no-access page between each stack.  The stack
 * spaces are managed as an array.
 */
public class StackAllocator {

  /** The base address of the stack space. */
  private Address baseAddress;

  /** The top address of the stack space. */
  private Address topAddress;

  /** The size in pages of a stack */
  private int stackSize;

  private boolean[] allocated;

  /**
   * Create a stack allocator that allocates at the base address
   * @param baseAddress
   * @param spaceSize
   * @param stackSize
   */
  public StackAllocator(Address baseAddress, Extent spaceSize, Extent stackSize) {
    this.baseAddress = baseAddress;
    this.topAddress = baseAddress.plus(spaceSize);
    this.stackSize = (int)(stackSize.toLong()/BYTES_IN_PAGE);

    long spacePages = spaceSize.toLong()/BYTES_IN_PAGE;
    int nStacks = (int)((spacePages-2)/(this.stackSize+1));

    this.allocated = new boolean[nStacks];
  }

  public int sizeInBytes() {
    return stackSize*BYTES_IN_PAGE;
  }

  /**
   * The address of the <i>i</i>th stack,
   * @param i
   * @return
   */
  private Address baseAddress(int i) {
    /* Address of the first stack */
    Address base = baseAddress.plus(BYTES_IN_PAGE);
    return base.plus((stackSize+1)*BYTES_IN_PAGE*i);
  }

  /**
   * Allocate the stack index {@code i}
   * @param i
   * @return
   */
  private Address alloc(int i) {
    if (allocated[i]) {
      throw new AssertionError("Stack "+i+" is already allocated");
    }
    Address base = baseAddress(i);
    SimulatedMemory.map(base,sizeInBytes());
    allocated[i] = true;
    return base;
  }

  /**
   * Free the stack at index i
   * @param i
   */
  private void free(int i) {
    if (!allocated[i]) {
      throw new AssertionError("Stack "+i+" is not allocated");
    }
    Address base = baseAddress(i);
    SimulatedMemory.unmap(base,sizeInBytes());
    allocated[i] = false;
  }

  /**
   * Allocate a stack
   * @return
   */
  public synchronized Address alloc() {
    for (int i=0; i < allocated.length; i++) {
      if (!allocated[i]) {
        return alloc(i);
      }
    }
    dump();
    throw new Error("Stack space exhausted");
  }

  /**
   * Free the stack at base address {@code stack}
   * @param stack
   */
  public synchronized void free(Address stack) {
    int index = (int)(stack.diff(baseAddress(0)).toLong()/(sizeInBytes()+BYTES_IN_PAGE));
    assert baseAddress(index).EQ(stack) : "Stack "+index+" has base address "+baseAddress(index)+", freeing stack "+stack;
    free(index);
  }

  private void dump() {
    System.err.println("Stack space base: "+baseAddress);
    System.err.println("Stack space limit: "+topAddress);
    System.err.println("Stack space size: "+topAddress.diff(baseAddress).toLong()/1024+" KB");
    System.err.println("Stack size: "+stackSize+" pages ("+(stackSize*BYTES_IN_PAGE)/1024+" KB)");
    System.err.println("Stacks available: "+allocated.length);
  }
}
