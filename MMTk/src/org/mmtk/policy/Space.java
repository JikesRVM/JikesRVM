/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.heap.Map;
import org.mmtk.utility.heap.Mmapper;
import org.mmtk.utility.heap.PageResource;
import org.mmtk.utility.heap.SpaceDescriptor;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.Log;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class defines and manages spaces.  Each policy is an instance
 * of a space.  A space is a region of virtual memory (contigious or
 * discontigous) which is subject to the same memory management
 * regime.  Multiple spaces (instances of this class or its
 * descendants) may have the same policy (eg there could be numerous
 * instances of CopySpace, each with different roles). Spaces are
 * defined in terms of a unique region of virtual memory, so no two
 * space instances ever share any virtual memory.<p>
 *
 * In addition to tracking virtual memory use and the mapping to
 * policy, spaces also manage memory consumption (<i>used</i> virtual
 * memory).<p>
 *
 * Discontigious spaces are currently unsupported.
 */
@Uninterruptible public abstract class Space implements Constants {

  // the following is somewhat arbitrary for the 64 bit system at this stage
  private static final int LOG_ADDRESS_SPACE = (BYTES_IN_ADDRESS == 4) ? 32 : 40;
  protected static Address HEAP_START = chunkAlign(VM.HEAP_START, true);
  private static Address AVAILABLE_START = chunkAlign(VM.AVAILABLE_START, false);
  private static Address AVAILABLE_END = chunkAlign(VM.AVAILABLE_END, true);
  protected static Extent AVAILABLE_BYTES = AVAILABLE_END.toWord().minus(AVAILABLE_START.toWord()).toExtent();
  public static final Address HEAP_END = chunkAlign(VM.HEAP_END, false);

  public static final int LOG_BYTES_IN_CHUNK = 22;
  public static final int BYTES_IN_CHUNK = 1 << LOG_BYTES_IN_CHUNK;
  private static final int LOG_MAX_CHUNKS = LOG_ADDRESS_SPACE - LOG_BYTES_IN_CHUNK;
  public static final int MAX_CHUNKS = 1 << LOG_MAX_CHUNKS;
  public static final int MAX_SPACES = 20; // quite arbitrary

  private static final int PAGES = 0;
  private static final int MB = 1;
  private static final int PAGES_MB = 2;
  private static final int MB_PAGES = 3;

  private static int spaceCount = 0;
  private static Space[] spaces = new Space[MAX_SPACES];
  protected static Address heapCursor = HEAP_START;
  protected static Address heapLimit = HEAP_END;

  /****************************************************************************
   *
   * Instance variables
   */
  private final int descriptor;
  private final int index;
  private final String name;
  private final boolean immortal;
  private final boolean movable;

  private boolean allocationFailed;
  protected PageResource pr;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * This is the base constructor for <i>contigious</i> spaces
   * (i.e. those that occupy a single contigious range of virtual
   * memory which is identified at construction time).<p>
   *
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   */
  Space(String name, boolean movable, boolean immortal, int descriptor) {
    this.name = name;
    this.movable = movable;
    this.immortal = immortal;
    this.descriptor = descriptor;
    index = spaceCount++;
    spaces[index] = this;
  }

  /****************************************************************************
   *
   * Accessor methods
   */

  /** Name getter @return The name of this space */
  public final String getName() { return name; }

  /** Descriptor method @return The integer descriptor for this space */
  public final int getDescriptor() { return descriptor; }

  /** Index getter @return The index (ordinal number) of this space */
  public final int getIndex() { return index; }

  /** Immortal getter @return True if this space is never collected */
  public final boolean isImmortal() { return immortal; }

  /** Movable getter @return True if objects in this space may move */
  public final boolean isMovable() { return movable; }

  /** Allocationfailed getter @return true if an allocation has failed since GC */
  public final boolean allocationFailed() { return allocationFailed; }

  /** Clear Allocationfailed flag */
  public final void clearAllocationFailed() { allocationFailed = false; }

  /** ReservedPages getter @return The number of reserved pages */
  public final int reservedPages() { return pr.reservedPages(); }

  /** CommittedPages getter @return The number of committed pages */
  public final int committedPages() { return pr.committedPages(); }

  /** RequiredPages getter @return The number of required pages */
  public final int requiredPages() { return pr.requiredPages(); }

  /** Cumulative committed pages getter @return Cumulative committed pages. */
  public static long cumulativeCommittedPages() {
    return PageResource.cumulativeCommittedPages();
  }

  /****************************************************************************
   *
   * Object and address tests / accessors
   */

  /**
   * Return true if the given object is in an immortal (uncollected) space.
   *
   * @param object The object in question
   * @return True if the given object is in an immortal (uncollected) space.
   */
  public static boolean isImmortal(ObjectReference object) {
    Space space = getSpaceForObject(object);
    if (space == null)
      return true;
    else
      return space.isImmortal();
  }

  /**
   * Return true if the given object is in space that moves objects.
   *
   * @param object The object in question
   * @return True if the given object is in space that moves objects.
   */
  @Inline
  public static boolean isMovable(ObjectReference object) {
    Space space = getSpaceForObject(object);
    if (space == null)
      return true;
    else
      return space.isMovable();
  }

  /**
   * Return true if the given object is in a space managed by MMTk.
   *
   * @param object The object in question
   * @return True if the given object is in a space managed by MMTk.
   */
  @Inline
  public static boolean isMappedObject(ObjectReference object) {
    return !object.isNull() && (getSpaceForObject(object) != null) && Mmapper.objectIsMapped(object);
  }

  /**
   * Return true if the given address is in a space managed by MMTk.
   *
   * @param address The address in question
   * @return True if the given address is in a space managed by MMTk.
   */
  @Inline
  public static boolean isMappedAddress(Address address) {
    return Map.getSpaceForAddress(address) != null && Mmapper.addressIsMapped(address);
  }

  /**
   * Return true if the given object is the space associated with the
   * given descriptor.
   *
   * @param descriptor The descriptor for a space
   * @param object The object in question
   * @return True if the given object is in the space associated with
   * the descriptor.
   */
  @Inline
  public static boolean isInSpace(int descriptor, ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    return isInSpace(descriptor, VM.objectModel.refToAddress(object));
  }

  /**
   * Return true if the given address is the space associated with the
   * given descriptor.
   *
   * @param descriptor The descriptor for a space
   * @param address The address in question.
   * @return True if the given address is in the space associated with
   * the descriptor.
   */
  @Inline
  public static boolean isInSpace(int descriptor, Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!address.isZero());
    if (!SpaceDescriptor.isContiguous(descriptor)) {
      return getDescriptorForAddress(address) == descriptor;
    } else {
      Address start = SpaceDescriptor.getStart(descriptor);
      if (!VM.VERIFY_ASSERTIONS &&
          SpaceDescriptor.isContiguousHi(descriptor))
        return address.GE(start);
      else {
        Extent size = Word.fromIntSignExtend(SpaceDescriptor.getChunks(descriptor)).lsh(LOG_BYTES_IN_CHUNK).toExtent();
        Address end = start.plus(size);
        return address.GE(start) && address.LT(end);
      }
    }
  }

  /**
   * Return the space for a given object
   *
   * @param object The object in question
   * @return The space containing the object
   */
  @Inline
  public static Space getSpaceForObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    return Map.getSpaceForAddress(VM.objectModel.refToAddress(object));
  }

  /**
   * Return the descriptor for a given address.
   *
   * @param address The address in question.
   * @return The descriptor for the space containing the address.
   */
  @Inline
  public static int getDescriptorForAddress(Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!address.isZero());
    return Map.getDescriptorForAddress(address);
  }


  /****************************************************************************
   *
   * Page management
   */

  /**
   * Acquire a number of pages from the page resource, returning
   * either the address of the first page, or zero on failure.<p>
   *
   * This may trigger a GC if necessary.<p>
   *
   * First the page budget is checked to see whether polling the GC is
   * necessary.  If so, the GC is polled.  If a GC is required then the
   * request fails and zero is returned.<p>
   *
   * If the check of the page budget does not lead to GC being
   * triggered, then a request is made for specific pages in virtual
   * memory.  If the page manager cannot satisify this request, then
   * the request fails, a GC is forced, and zero is returned.
   * Otherwise the address of the first page is returned.<p>
   *
   * @param pages The number of pages requested
   * @return The start of the first page if successful, zero on
   * failure.
   */
  public final Address acquire(int pages) {
    boolean allowPoll = !Plan.gcInProgress() && Plan.isInitialized() && !Plan.isEmergencyAllocation();

    /* First check page budget and poll if necessary */
    if (!pr.reservePages(pages)) {
      /* Need to poll, either fixing budget or requiring GC */
      if (allowPoll && VM.activePlan.global().poll(false, this)) {
        pr.clearRequest(pages);
        return Address.zero(); // GC required, return failure
      }
    }

    /* Page budget is ok, try to acquire virtual memory */
    Address rtn = pr.getNewPages(pages);
    if (rtn.isZero()) {
      /* Failed, so force a GC */
      if (Plan.isEmergencyAllocation()) {
        pr.clearRequest(pages);
        VM.assertions.fail("Failed emergency allocation");
      }
      if (!allowPoll) VM.assertions.fail("Physical allocation failed during special (collection/emergency) allocation!");
      allocationFailed = true;
      VM.collection.reportPhysicalAllocationFailed();
      VM.activePlan.global().poll(true, this);
      pr.clearRequest(pages);
      return Address.zero();
    }

    if (allowPoll) VM.collection.reportAllocationSuccess();
    return rtn;
  }

  /**
   * Release a unit of allocation (a page or pages)
   *
   * @param start The address of the start of the region to be released
   */
  public abstract void release(Address start);

  /**
   * Clear the allocation failed flag for all spaces.
   *
   */
  public static void clearAllAllocationFailed() {
    for (int i = 0; i < spaceCount; i++) {
      spaces[i].clearAllocationFailed();
    }
  }

  /**
   * Get the total number of pages reserved by all of the spaces
   *
   * @return the total number of pages reserved by all of the spaces
   */
  private static int getPagesReserved() {
    int pages = 0;
    for (int i = 0; i < spaceCount; i++) {
      pages += spaces[i].reservedPages();
    }
    return pages;
  }

  /****************************************************************************
   *
   * Debugging / printing
   */

  /**
   * Print out the memory used by all spaces, in megabytes
   */
  public static void printUsageMB() { printUsage(MB); }

  /**
   * Print out the memory used by all spaces, in megabytes
   */
  public static void printUsagePages() { printUsage(PAGES); }

  /**
   * Print out a map of virtual memory useage by all spaces
   */
  public static void printVMMap() {
    for (int i = 0; i < spaceCount; i++) {
      Space space = spaces[i];
      Log.write(space.name); Log.write(" ");
      space.printVMRange();
    }
  }

  protected abstract void printVMRange();

  /**
   * Ensure that all MMTk spaces (all spaces aside from the VM space)
   * are mapped. Demand zero map all of them if they are not already
   * mapped.
   */
  @Interruptible
  public static void eagerlyMmapMMTkSpaces() {
    for (int i = 0; i < spaceCount; i++) {
      Space space = spaces[i];
      if (space != VM.memory.getVMSpace()) {
        if (Options.verbose.getValue() > 2) {
          Log.write("Mapping ");
          Log.write(space.getName());
          Log.write(" ");
          space.printVMRange();
        }
        space.ensureMapped();
      }
    }
  }

  /**
   * Ensure that this space is mapped.
   * Demand zero map all of them if they are not already mapped.
   */
  protected abstract void ensureMapped();

  /**
   * Print out the memory used by all spaces in either megabytes or
   * pages.
   *
   * @param mode An enumeration type that specifies the format for the
   * prining (PAGES, MB, PAGES_MB, or MB_PAGES).
   */
  private static void printUsage(int mode) {
    Log.write("used = ");
    printPages(getPagesReserved(), mode);
    boolean first = true;
    for (int i = 0; i < spaceCount; i++) {
      Space space = spaces[i];
      Log.write(first ? " = " : " + ");
      first = false;
      Log.write(space.name); Log.write(" ");
      printPages(space.reservedPages(), mode);
    }
    Log.writeln();
  }

  /**
   * Print out the number of pages and or megabytes, depending on the mode.
   *
   * @param pages The number of pages
   * @param mode An enumeration type that specifies the format for the
   * prining (PAGES, MB, PAGES_MB, or MB_PAGES).
   */
  private static void printPages(int pages, int mode) {
    double mb = (double) (pages << LOG_BYTES_IN_PAGE) / (double) (1 << 20);
    switch (mode) {
    case PAGES: Log.write(pages); Log.write(" pgs"); break;
    case MB:    Log.write(mb); Log.write(" Mb"); break;
    case PAGES_MB: Log.write(pages); Log.write(" pgs ("); Log.write(mb); Log.write(" Mb)"); break;
    case MB_PAGES: Log.write(mb); Log.write(" Mb ("); Log.write(pages); Log.write(" pgs)"); break;
      default: VM.assertions.fail("writePages passed illegal printing mode");
    }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Trace an object as part of a collection and return the object,
   * which may have been forwarded (if a copying collector).
   *
   * @param trace The trace being conducted.
   * @param object The object to trace
   * @return The object, forwarded, if appropriate
   */
  public abstract ObjectReference traceObject(TraceLocal trace,
      ObjectReference object);


  /**
   * Has the object in this space been reached during the current collection.
   * This is used for GC Tracing.
   *
   * @param object The object reference.
   * @return True if the object is reachable.
   */
  public boolean isReachable(ObjectReference object) {
    return isLive(object);
  }


  /**
   * Is the object in this space alive?
   *
   * @param object The object reference.
   * @return True if the object is live.
   */
  public abstract boolean isLive(ObjectReference object);

  /**
   * Align an address to a space chunk
   *
   * @param addr The address to be aligned
   * @param down If true the address will be rounded down, otherwise
   * it will rounded up.
   */
  private static Address chunkAlign(Address addr, boolean down) {
    if (!down) addr = addr.plus(BYTES_IN_CHUNK - 1);
    return addr.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toAddress();
  }
}
