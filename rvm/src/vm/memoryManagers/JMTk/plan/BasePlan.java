/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class BasePlan implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static int verbose = 0;

  // GC state and control variables
  private static int count = 0;        // Number of plan instances in existence
  protected static boolean gcInProgress = false;  // Controlled by subclasses
  protected static int gcCount = 0;    // Number of GCs initiated


  // Timing variables
  protected static double bootTime;

  // Meta data resources
  private static MonotoneVMResource metaDataVM;
  protected static MemoryResource metaDataMR;
  protected static RawPageAllocator metaDataRPA;

  // Miscellaneous constants
  private static final int META_DATA_POLL_FREQUENCY = (1<<31) - 1; // never
  protected static final int DEFAULT_POLL_FREQUENCY = (128<<10)>>LOG_PAGE_SIZE;
  protected static final int DEFAULT_LOS_SIZE_THRESHOLD = 16 * 1024;
  protected static final int NON_PARTICIPANT = 0;
  protected static final boolean GATHER_WRITE_BARRIER_STATS = false;

  // Memory layout constants
  protected static final EXTENT        SEGMENT_SIZE = 0x10000000;
  protected static final int           SEGMENT_MASK = SEGMENT_SIZE - 1;
  protected static final VM_Address      BOOT_START = VM_Address.fromInt(VM_Interface.bootImageAddress);
  protected static final EXTENT           BOOT_SIZE = SEGMENT_SIZE - (VM_Interface.bootImageAddress & SEGMENT_MASK);   // use the remainder of the segment
  protected static final VM_Address        BOOT_END = BOOT_START.add(BOOT_SIZE);
  protected static final VM_Address  IMMORTAL_START = BOOT_START;
  protected static final EXTENT       IMMORTAL_SIZE = BOOT_SIZE + 16 * 1024 * 1024;
  protected static final VM_Address    IMMORTAL_END = IMMORTAL_START.add(IMMORTAL_SIZE);
  protected static final VM_Address META_DATA_START = IMMORTAL_END;
  protected static final EXTENT     META_DATA_SIZE  = 32 * 1024 * 1024;
  protected static final VM_Address   META_DATA_END = META_DATA_START.add(META_DATA_SIZE);  
  protected static final VM_Address      PLAN_START = META_DATA_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private int id = 0;                     // Zero-based id of plan instance

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //
  static {
    metaDataMR = new MemoryResource(META_DATA_POLL_FREQUENCY);
    metaDataVM = new MonotoneVMResource("Meta data", metaDataMR, META_DATA_START, META_DATA_SIZE, VMResource.META_DATA);
    metaDataRPA = new RawPageAllocator(metaDataVM, metaDataMR);
  }

  BasePlan() {
    id = count++;
  }

  /**
   * The boot method is called early in the boot process before any allocation.
   */
  public static void boot() throws VM_PragmaInterruptible {
    bootTime = VM_Interface.now();
  }

  /**
   * The boot method is called by the runtime immediately after
   * command-line arguments are available.  Note that allocation must
   * be supported prior to this point because the runtime
   * infrastructure may require allocation in order to parse the
   * command line arguments.  For this reason all plans should operate
   * gracefully on the default minimum heap size until the point that
   * boot is called.
   */
  public static void postBoot() {
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  // Add a gray object
  //
  public static void enqueue(VM_Address obj) throws VM_PragmaInline {
    VM_Interface.getPlan().values.push(obj);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   * @return void
   */
  public static final void traceObjectLocation(VM_Address objLoc, boolean root)
    throws VM_PragmaInline {
    VM_Address obj = VM_Magic.getMemoryAddress(objLoc);
    VM_Address newObj = Plan.traceObject(obj, root);
    VM_Magic.setMemoryAddress(objLoc, newObj);
  }
  public static final void traceObjectLocation(VM_Address objLoc)
    throws VM_PragmaInline {
    traceObjectLocation(objLoc, false);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @param interiorRef The interior reference inside obj that must be traced.
   * @return The possibly moved interior reference.
   */
  public static final VM_Address traceInteriorReference(VM_Address obj,
							VM_Address interiorRef,
							boolean root) {
    VM_Offset offset = interiorRef.diff(obj);
    VM_Address newObj = Plan.traceObject(obj, root);
    if (VM.VerifyAssertions) {
      if (offset.toInt() > (1<<24)) {  // There is probably no object this large
	VM.sysWriteln("ERROR: Suspiciously large delta of interior pointer from object base");
	VM.sysWriteln("       object base = ", obj);
	VM.sysWriteln("       interior reference = ", interiorRef);
	VM.sysWriteln("       delta = ", offset.toInt());
	VM._assert(false);
      }
    }
    return newObj.add(offset);
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Read and write barriers.  By default do nothing, override if
  // appropriate.
  //

  /**
   * Perform a write barrier operation for the putField bytecode.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param srcObj The address of the object containing the pointer to
   * be written to.
   * @param offset The offset from srcObj of the slot the pointer is
   * to be written into
   * @param tgt The address to which the source will point
   */
  public void putFieldWriteBarrier(VM_Address srcObj, int offset,
				   VM_Address tgt){}

  /**
   * Perform a write barrier operation for the putStatic bytecode.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param srcObj The address of the object containing the pointer to
   * be written to
   * @param offset The offset from static table (JTOC) of the slot the
   * pointer is to be written into
   * @param tgt The address to which the source will point
   */
  public void putStaticWriteBarrier(VM_Address slot, VM_Address tgt){}
  public void arrayStoreWriteBarrier(VM_Address ref, int index, 
				     VM_Address value) {}
  public void arrayCopyWriteBarrier(VM_Address ref, int startIndex,
				    int endIndex) {}
  public void arrayCopyRefCountWriteBarrier(VM_Address src, VM_Address tgt) {}

  /**
   * Perform a read barrier operation of the getField bytecode.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param tgtObj The address of the object containing the pointer
   * about to be read
   * @param offset The offset from tgtObj of the field to be read from
   */
  public void getFieldReadBarrier(VM_Address tgtObj, int offset) {}

  /**
   * Perform a read barrier operation for the getStatic bytecode.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param tgtObj The address of the object containing the pointer
   * about to be read
   * @param offset The offset from tgtObj of the field to be read from
   */
  public void getStaticReadBarrier(int staticOffset) {}

  ////////////////////////////////////////////////////////////////////////////
  //
  // Space management
  //
  public static long freeMemory() throws VM_PragmaUninterruptible {
    return totalMemory() - usedMemory();
  }

  public static long usedMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(Plan.getPagesUsed());
  }

  public static long totalMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(Plan.getPagesAvail());
  }

  public static int getTotalPages() throws VM_PragmaUninterruptible { 
    int heapPages = Conversions.bytesToPages(Options.initialHeapSize);
    return heapPages; 
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //
  /*
   * This method should be called whenever an error is encountered.
   *
   */
  public void error(String str) {
    Plan.showUsage();
    VM.sysFail(str);
  }

  static public boolean gcInProgress() {
    return gcInProgress;
  }

  public static int gcCount() {
    return gcCount;
  }

  public static RawPageAllocator getMetaDataRPA() {
    return metaDataRPA;
  }

  public static void notifyExit(int value) {
    if (verbose == 1) {
      VM.sysWrite("[End ", ((VM_Interface.now() - bootTime)*1000));
      VM.sysWrite("ms]\n");
    }
  }

}
