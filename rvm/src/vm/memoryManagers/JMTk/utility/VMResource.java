/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * This class implements a virtual memory resource.  The unit of
 * managment for virtual memory resources is the <code>BLOCK</code><p>
 *
 * Instances of this class each manage a contigious region of virtual
 * memory.  The class's static methods and fields coordinate to ensure
 * coherencey among VM resource requests (i.e. that they do not
 * overlap). 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */

public abstract class VMResource implements Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public static variables and methods
  //
  public static final byte NOT_IN_VM = 0;   // 00000000
  public static final byte IN_VM     = 1;   // 00000001
  public static final byte IMMORTAL  = 2;   // 00000010
  public static final byte MOVABLE   = 4;   // 00000100
  public static final byte META_DATA = -128; // 10000000


  public static void showAll () {
    VM.sysFail("VM_Interface.showAll not implemented");
  }

  public static boolean refInVM(VM_Address ref) throws VM_PragmaUninterruptible {
    return addrInVM(VM_Interface.refToAddress(ref));
  }

  public static boolean addrInVM(VM_Address addr) throws VM_PragmaUninterruptible {
    return (getBlockStatus(addr) &  IN_VM) == IN_VM;
  }

  public static boolean refIsImmortal(VM_Address ref) throws VM_PragmaUninterruptible {
    return addrIsImmortal(VM_Interface.refToAddress(ref));
  }

  public static boolean addrIsImmortal(VM_Address addr) throws VM_PragmaUninterruptible {
    return (getBlockStatus(addr) &  IMMORTAL) == IMMORTAL;
  }

  public static int getMaxVMResource() {
    return MAX_VMRESOURCE;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private static methods and variables
  //
  private static VMResource resourceTable[]; // Points to corresponding VM resource.  null if no corresponding VM resource.
  private static byte statusTable[];         // Status of each block, 0 means not used by the VM.
  private static int count;                  // How many VMResources exist now?
  private static VMResource resources[];     // List of all VMResources.
  final private static int MAX_VMRESOURCE = 20;
  final public  static int LOG_PAGES_PER_BLOCK = 3;
  final public  static int LOG_BLOCK_SIZE = LOG_PAGE_SIZE + LOG_PAGES_PER_BLOCK;
  final public  static int BLOCK_SIZE = 1 << LOG_BLOCK_SIZE;
  final public  static int BLOCK_MASK = ~((1 << LOG_BLOCK_SIZE) - 1);
  final private static int NUM_BLOCKS = 1 << (LOG_ADDRESS_SPACE - LOG_BLOCK_SIZE);

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    resources = new VMResource[MAX_VMRESOURCE];
    resourceTable = new VMResource[NUM_BLOCKS];
    statusTable = new byte[NUM_BLOCKS];
    for (int blk = 0; blk < NUM_BLOCKS; blk++) {
      resourceTable[blk] = null;
      statusTable[blk] = 0;
    }
  }

  private static VMResource resourceForBlock(VM_Address addr) {
    return resourceTable[addr.toInt() >>> LOG_BLOCK_SIZE];
  }

  private static byte getBlockStatus(VM_Address addr) {
    return statusTable[addr.toInt() >>> LOG_BLOCK_SIZE];
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  /**
   * Constructor
   */
  VMResource(String vmName, VM_Address vmStart, EXTENT bytes, byte status) {
    start = vmStart;
    int blocks = Conversions.bytesToBlocks(bytes);
    pages = Conversions.blocksToPages(blocks);
    end = start.add(bytes);
    name = vmName;
    index = count++;
    resources[index] = this;
    // now check: block-aligned, non-conflicting
    int startblk = Conversions.addressToBlocks(start);
    if (Conversions.blocksToAddress(startblk).NE(start)) {
      VM.sysWriteln("misaligned VMResource");
      VM._assert(false);
    }
    for (int blk = startblk; blk < (startblk + blocks); blk++) {
      if (resourceTable[blk] != null) {
	VM.sysWriteln("conflicting VMResource");
	VM._assert(false);
      }
      resourceTable[blk] = this;
      statusTable[blk] = status;
    }
    VM_Interface.setHeapRange(index, start, end);
  }

  /**
   * Acquire a number of contigious blocks from the virtual memory resource.
   *
   * @param request The number of blocks requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public abstract VM_Address acquire(int request);
  public abstract VM_Address acquire(int request, MemoryResource mr);
  
  public final int getBlocks() { return Conversions.pagesToBlocks(pages); }

  public final VM_Address getStart() { return start; }
  public final VM_Address getEnd() { return end; }
  public final boolean inRange(VM_Address s) { return (start.LE(s) && s.LT(end)); }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private fields and methods
  //

  private int index;
  protected VM_Address start;
  protected VM_Address end;
  private int pages;
  final protected String name;
}
