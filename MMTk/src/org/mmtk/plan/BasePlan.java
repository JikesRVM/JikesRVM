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
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This abstract class implments the core functionality for all memory
 * management schemes.  All JMTk plans should inherit from this
 * class.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 * 
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class BasePlan 
  implements Constants, VM_Uninterruptible {
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

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    metaDataMR = new MemoryResource(META_DATA_POLL_FREQUENCY);
    metaDataVM = new MonotoneVMResource("Meta data", metaDataMR, META_DATA_START, META_DATA_SIZE, VMResource.META_DATA);
    metaDataRPA = new RawPageAllocator(metaDataVM, metaDataMR);
  }

  /**
   * Constructor
   */
  BasePlan() {
    id = count++;
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
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
    if (verbose > 2) VMResource.showAll();
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Add a gray object
   *
   * @param obj The object to be enqueued
   */
  public static final void enqueue(VM_Address obj)
    throws VM_PragmaInline {
    VM_Interface.getPlan().values.push(obj);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   * @param root True if <code>objLoc</code> is within a root.
   */
  public static final void traceObjectLocation(VM_Address objLoc, boolean root)
    throws VM_PragmaInline {
    VM_Address obj = VM_Magic.getMemoryAddress(objLoc);
    VM_Address newObj = Plan.traceObject(obj, root);
    VM_Magic.setMemoryAddress(objLoc, newObj);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.  This reference is presumed <i>not</i>
   * to be from a root.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   */
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
   * @param root True if the reference to <code>obj</code> was held in a root.
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
   * A new reference is about to be created by a putfield bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The address of the object containing the source of a
   * new reference.
   * @param offset The offset into the source object where the new
   * reference resides (the offset is in bytes and with respect to the
   * object address).
   * @param tgt The target of the new reference
   */
  public void putFieldWriteBarrier(VM_Address src, int offset,
				   VM_Address tgt){}

  /**
   * A new reference is about to be created by a aastore bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The address of the array containing the source of a
   * new reference.
   * @param index The index into the array where the new reference
   * resides (the index is the "natural" index into the array,
   * i.e. a[index]).
   * @param tgt The target of the new reference
   */
  public void arrayStoreWriteBarrier(VM_Address ref, int index, 
				     VM_Address value) {}

  /**
   * A new reference is about to be created by a putStatic bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param slot The location into which the new reference will be
   * stored (the address of the static field being stored into).
   * @param tgt The target of the new reference
   */
  public void putStaticWriteBarrier(VM_Address slot, VM_Address tgt){}

  /**
   * An array of reference type has <i>just been copied</i> into.  For
   * each new reference, take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * FIXME  The call in VM_Array should be changed to invoke this
   * <i>prior</i> to the copy, not after the copy.  Although this
   * makes no difference in the case of the standard generational
   * barrier, in general, write barriers should be invoked immediately
   * <i>prior</i> to the copy, not immediately after the copy.<p>
   *
   * <i>This way of dealing with array copy write barriers is
   * suboptimal...</i>
   *
   * @param src The array containing the source of the new references
   * (i.e. the destination of the copy).
   * @param startIndex The index into the array where the first new
   * reference resides (the index is the "natural" index into the
   * array, i.e. a[index]).
   * @param endIndex
   */
  public void arrayCopyWriteBarrier(VM_Address ref, int startIndex,
				    int endIndex) {}

  /**
   * An array copy is underway, and <code>VM_Array</code> is iterating
   * through the references as it copies them.  This method is called
   * once for each method immediately prior to the copy of that
   * reference occuring.
   *
   * @param src The source of the new reference (i.e. the slot
   * containing the pointer about to be stored into).
   * @param tgt The value about to be stored into <code>src</code>
   */
  public void arrayCopyRefCountWriteBarrier(VM_Address src, VM_Address tgt) {}

  /**
   * A reference is about to be read by a getField bytecode.  Take
   * appropriate read barrier action.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param tgt The address of the object containing the pointer
   * about to be read
   * @param offset The offset from tgt of the field to be read from
   */
  public void getFieldReadBarrier(VM_Address tgt, int offset) {}

  /**
   * A reference is about to be read by a getStatic bytecode. Take
   * appropriate read barrier action.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param slot The location from which the reference will be read
   * (the address of the static field being read).
   */
  public void getStaticReadBarrier(VM_Address slot) {}

  ////////////////////////////////////////////////////////////////////////////
  //
  // Space management
  //

  /**
   * Return the amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).  Note that this may overstate the amount
   * of <i>available memory</i>, which must account for unused memory
   * that is held in reserve for copying, and therefore unavailable
   * for allocation.
   *
   * @return The amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).
   */
  public static long freeMemory() throws VM_PragmaUninterruptible {
    return totalMemory() - usedMemory();
  }

  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this includes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static long usedMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(Plan.getPagesUsed());
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in bytes.
   *
   * @return The total amount of memory managed to the memory
   * management system, in bytes.
   */
  public static long totalMemory() throws VM_PragmaUninterruptible {
    return Options.initialHeapSize;
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in pages.
   *
   * @return The total amount of memory managed to the memory
   * management system, in pages.
   */
  public static int getTotalPages() throws VM_PragmaUninterruptible { 
    int heapPages = Conversions.bytesToPages(Options.initialHeapSize);
    return heapPages; 
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  /**
   * This method should be called whenever an error is encountered.
   *
   * @param str A string describing the error condition.
   */
  public void error(String str) {
    Plan.showUsage(PAGES);
    Plan.showUsage(MB);
    VM.sysFail(str);
  }

  /**
   * Return true if a collection is in progress.
   *
   * @return True if a collection is in progress.
   */
  static public boolean gcInProgress() {
    return gcInProgress;
  }

  /**
   * Return the GC count (the count is incremented at the start of
   * each GC).
   *
   * @return The GC count (the count is incremented at the start of
   * each GC).
   */
  public static int gcCount() { 
    return gcCount;
  }

  /**
   * Return the <code>RawPageAllocator</code> being used.
   *
   * @return The <code>RawPageAllocator</code> being used.
   */
  public static RawPageAllocator getMetaDataRPA() {
    return metaDataRPA;
  }

  /**
   * The VM is about to exit.  Perform any clean up operations.
   *
   * @param value The exit value
   */
  public void notifyExit(int value) {
    if (verbose == 1) {
      VM.sysWrite("[End ", ((VM_Interface.now() - bootTime)*1000));
      VM.sysWrite("ms]\n");
    }
  }



  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  final static int PAGES = 0;
  final static int MB = 1;
  final static int PAGES_MB = 2;

  /**
   * Print out the number of pages and or megabytes, depending on the mode.
   * A prefix string is outputted first.
   *
   * @param prefix A prefix string
   * @param pages The number of pages
   */
  public static void writePages(String prefix, int pages, int mode) {
    VM.sysWrite(prefix);
    if (mode == PAGES) {
       VM.sysWrite(pages); 
       return;
    }
    else {
      double mb = Conversions.pagesToBytes(pages) / (1024.0 * 1024.0);
      if (mode == MB)
	VM.sysWrite(mb, " Mb");
      else if (mode == PAGES_MB) {
	VM.sysWrite(" (", mb);
	VM.sysWrite(" Mb)");
      }
      else
	VM.sysFail("writePages passed illegal printing mode");
    }
  }


}
