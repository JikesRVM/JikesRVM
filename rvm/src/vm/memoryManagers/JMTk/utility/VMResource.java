/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package org.mmtk.utility.heap;

import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Plan;
import org.mmtk.vm.Barriers;
import org.mmtk.vm.Memory;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a virtual memory resource.  The unit of
 * managment for virtual memory resources is the <code>PAGE</code><p>
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

public abstract class VMResource implements Constants, Uninterruptible {

  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public static variables and methods
   */
  public static final byte NOT_IN_VM = 0;   // 00000000
  public static final byte IN_VM     = 1;   // 00000001
  public static final byte IMMORTAL  = 2;   // 00000010
  public static final byte MOVABLE   = 4;   // 00000100
  public static final byte META_DATA = -128; // 10000000

  public static final int LOG_BYTES_IN_VM_REGION = EmbeddedMetaData.LOG_BYTES_IN_REGION;

  public static void showAll () {
    for (int vmr = 0; vmr < count; vmr++) {
      Log.write("VMResource ");
      Log.write(vmr); Log.write(" ");
      Log.write(resources[vmr].start); Log.write(" ");
      Log.write(resources[vmr].end); Log.write(" ");
      Log.writeln(resources[vmr].name);
    }
  }

  public static boolean refIsMovable (Address obj) {
    Address addr = ObjectModel.refToAddress(obj);
    return (getPageStatus(addr) & MOVABLE) == MOVABLE;
  }

  public static boolean refInVM(Address ref) throws UninterruptiblePragma {
    return addrInVM(ObjectModel.refToAddress(ref));
  }

  public static boolean addrInVM(Address addr) throws UninterruptiblePragma {
    return (getPageStatus(addr) & IN_VM) == IN_VM;
  }

  public static boolean refIsImmortal(Address ref) throws UninterruptiblePragma {
    return addrIsImmortal(ObjectModel.refToAddress(ref));
  }

  public static boolean addrIsImmortal(Address addr) throws UninterruptiblePragma {
    return (getPageStatus(addr) & IMMORTAL) == IMMORTAL;
  }

  public static int getMaxVMResource() {
    return MAX_VMRESOURCE;
  }

  /****************************************************************************
   *
   * Private static methods and variables
   */
  private static VMResource resourceTable[]; // Points to corresponding VM resource.  null if no corresponding VM resource.
  private static byte spaceTable[];          // Status of each page
  private static int count;                  // How many VMResources exist now?
  private static VMResource resources[];     // List of all VMResources.
  final private static int MAX_VMRESOURCE = 20;
  // final private static int NUM_PAGES = 1 << (LOG_BYTES_IN_ADDRESS_SPACE - LOG_BYTES_IN_PAGE);
  final private static int NUM_PAGES = 1 << 20;

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    resources = new VMResource[MAX_VMRESOURCE];
    spaceTable = new byte[NUM_PAGES];
    for (int blk = 0; blk < NUM_PAGES; blk++) 
      spaceTable[blk] = Plan.UNUSED_SPACE;
  }

  public static void boot() throws InterruptiblePragma {
    // resourceTable = new VMResource[NUM_PAGES];
    resourceTable = (VMResource []) ObjectModel.cloneArray(resources,Plan.IMMORTAL_SPACE,
                                                            NUM_PAGES);
    for (int i=0; i<resources.length; i++) {
      VMResource vm = resources[i];
      if (vm == null) continue;
      int startPage = Conversions.addressToPagesDown(vm.start);
      for (int p = startPage; p < (startPage + vm.pages); p++) {
        if (resourceTable[p] != null) {
          Log.write("Conflicting VMResource: "); Log.write(vm.name);
          Log.write(" and "); Log.writeln(resourceTable[p].name);
          Assert.fail("Conflicting VMResource");
        }
        resourceTable[p] = vm;
      }
    }
    Extent bootSize = Memory.bootImageEnd().diff(Memory.bootImageStart()).toWord().toExtent();
    Plan.bootVM.acquireHelp(Plan.BOOT_START, Conversions.bytesToPagesUp(bootSize));
    LazyMmapper.boot(Plan.BOOT_START, bootSize);
  }

  public static VMResource resourceForPage(Address addr) {
    if (resourceTable == null)
      Assert.fail("resourceForBlock called when resourceTable is null");
    int which = Conversions.addressToPagesDown(addr);
//-#if RVM_FOR_POWERPC && RVM_FOR_LINUX && RVM_FOR_64_ADDR
    if (which >= resourceTable.length)
       return null; 
//-#endif
    return resourceTable[which];
  }

  public static byte getPageStatus(Address addr) {
    VMResource vm = resourceForPage(addr);
    if (vm == null) return NOT_IN_VM;
    return vm.status;
  }

  final public static byte getSpace(Address addr) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
        if (spaceTable == null)
          Assert.fail("getSpace called when spaceTable is null");
	return spaceTable[Conversions.addressToPagesDown(addr)];
    }
    return Barriers.getArrayNoBarrier(spaceTable, 
				    Conversions.addressToPagesDown(addr));
  }

  /****************************************************************************
   *
   * Public instance methods
   */
  /**
   * Constructor
   */
  VMResource(byte space_, String vmName, Address vmStart, Extent bytes, byte status_) {
    Assert._assert(vmStart.EQ(Conversions.roundDownVM(vmStart)));
    space = space_;
    start = vmStart;
    pages = Conversions.bytesToPages(bytes);
    end = start.add(bytes);
    name = vmName;
    index = count++;
    resources[index] = this;
    status = status_;
    Memory.setHeapRange(index, start, end);
    if (end.GT(Memory.MAXIMUM_MAPPABLE)) {
      Log.write("\nError creating VMResrouce "); Log.write(vmName);
      Log.write(" with range "); Log.write(start);
      Log.write(" to "); Log.writeln(end);
      Log.write("Exceeds the maximum mappable address for this OS of "); Log.writeln(Memory.MAXIMUM_MAPPABLE);
      Assert._assert(false);
    }
  }

  /**
   * Acquire a number of contigious blocks from the virtual memory resource.
   *
   * @param request The number of pages requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public abstract Address acquire(int request);
  public abstract Address acquire(int request, MemoryResource mr);
  
  protected void acquireHelp (Address start, int pageRequest) {
    if (!Assert.runningVM()) Assert.fail("VMResource.acquireHelp called before VM is running");
    if (spaceTable == null) 
        Assert.fail("VMResource.acquireHelp called when spaceTable is still empty");
    int pageStart = Conversions.addressToPages(start);
    // Log.write("Acquiring pages "); Log.write(pageStart);
    // Log.write(" to "); Log.write(pageStart + pageRequest - 1);
    // Log.write(" for space "); Log.writeln(space);
    for (int i=0; i<pageRequest; i++) {
      Assert._assert(spaceTable[pageStart+i] == Plan.UNUSED_SPACE 
                               // Suspect - FreeListVM
                               || spaceTable[pageStart+i] == space); 
      spaceTable[pageStart+i] = space;
    }
  }

  protected void releaseHelp (Address start, int pageRequest) {
    if (!Assert.runningVM()) Assert.fail("VMResource.releaseHelp called before VM is running");
    int pageStart = Conversions.addressToPages(start);
    // Log.write("Releasing pages "); Log.write(pageStart);
    // Log.write(" to "); Log.write(pageStart + pageRequest - 1);
    // Log.write(" for space "); Log.writeln(spac!e);
    for (int i=0; i<pageRequest; i++) {
      Assert._assert(spaceTable[pageStart+i] == space ||
                     spaceTable[pageStart+i] == Plan.UNUSED_SPACE); // Suspect - FreeListVM
      spaceTable[pageStart+i] = Plan.UNUSED_SPACE;
    }
  }

  public final int getPages() { return pages; }

  public final Address getStart() { return start; }
  public final Address getEnd() { return end; }
  public final boolean inRange(Address s) { return (start.LE(s) && s.LT(end)); }

  /****************************************************************************
   *
   * Private fields and methods
   */
  final private int index;
  final private byte space;
  final protected String name;
  private byte status;
  protected Address start;
  protected Address end;
  private int pages;
}
