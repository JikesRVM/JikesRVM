/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class provides a generic interface to collector calls.  All
 * calls by the VM runtime and compilers to the collector should be
 * through this interface or VM_Allocator.
 *
 * @author Steve Blackburn
 * @modified Steve Smith
 * 
 * @see VM_Allocator
 */
public class VM_Collector implements VM_Constants, VM_Uninterruptible {

  /**
   * Initialization that occurs at <i>build</i> time.  The value of
   * statics as at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is the entry point for all build-time activity in the collector.
   */
  public static final void init () {
    VM_Allocator.init();
  }

  /**
   * Initialization that occurs at <i>boot</i> time (runtime
   * initialization).  This is only executed by one processor (the
   * primordial thread).
   */
  public static final void boot (VM_BootRecord theBootRecord) {
    VM_Allocator.boot(theBootRecord);
  }

  /**
   * Perform postBoot operations such as dealing with command line
   * options (this is called as soon as options have been parsed,
   * which is necessarily after the basic allocator boot).
   */
  public static void postBoot() {
  }

  /** 
   *  Process GC parameters.
   */
  public static void processCommandLineArg(String arg) {
      VM.sysWriteln("Unrecognized collection option: ", arg);
      VM.sysExit(1);
  }

  /**
   * Returns true if GC is in progress.
   *
   * @return True if GC is in progress.
   */
  public static final boolean gcInProgress() {
    return VM_Allocator.gcInProgress;
  }

  /**
   * Returns the number of collections that have occured.
   *
   * @return The number of collections that have occured.
   */
  public static final int collectionCount() {
    return VM_CollectorThread.collectionCount;
  }
  
  /**
   * Returns the amount of free memory.
   *
   * @return The amount of free memory.
   */
  public static final long freeMemory() {
    return VM_Allocator.freeMemory();
  }

  /**
   * Returns the amount of total memory.
   *
   * @return The amount of total memory.
   */
  public static final long totalMemory() {
    return VM_Allocator.totalMemory();
  }

  /**
   * Forces a garbage collection.
   */
  public static final void gc() {
    VM_Allocator.gc();
  }

  /**
   * Sets up the fields of a <code>VM_Processor</code> object to
   * accommodate allocation and garbage collection running on that processor.
   * This may involve creating a remset array or a buffer for GC tracing.
   * 
   * This method is called from the constructor of VM_Processor. For the
   * PRIMORDIAL processor, which is allocated while the bootimage is being
   * built, this method is called a second time, from VM.boot, when the 
   * VM is starting.
   *
   * @param p The <code>VM_Processor</code> object.
   */
  public static final void setupProcessor(VM_Processor p) {
    VM_Allocator.setupProcessor(p);
  }

  static final int MARK_VALUE = VM_Allocator.MARK_VALUE;
  static final boolean NEEDS_WRITE_BARRIER = VM_Allocator.writeBarrier;
  static final boolean MOVES_OBJECTS = VM_Allocator.movesObjects;
  static boolean useMemoryController = false;

}
