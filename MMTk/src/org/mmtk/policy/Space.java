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
package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.Map;
import org.mmtk.utility.heap.Mmapper;
import org.mmtk.utility.heap.PageResource;
import org.mmtk.utility.heap.SpaceDescriptor;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.Log;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class defines and manages spaces.  Each policy is an instance
 * of a space.  A space is a region of virtual memory (contiguous or
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
 */
@Uninterruptible
public abstract class Space implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  private static boolean DEBUG = false;

  // the following is somewhat arbitrary for the 64 bit system at this stage
  public static final int LOG_ADDRESS_SPACE = (BYTES_IN_ADDRESS == 4) ? 32 : 40;
  public static final int LOG_BYTES_IN_CHUNK = 22;
  public static final int BYTES_IN_CHUNK = 1 << LOG_BYTES_IN_CHUNK;
  public static final int PAGES_IN_CHUNK = 1 << (LOG_BYTES_IN_CHUNK - LOG_BYTES_IN_PAGE);
  private static final int LOG_MAX_CHUNKS = LOG_ADDRESS_SPACE - LOG_BYTES_IN_CHUNK;
  public static final int MAX_CHUNKS = 1 << LOG_MAX_CHUNKS;
  public static final int MAX_SPACES = 20; // quite arbitrary

  public static final Address HEAP_START = chunkAlign(VM.HEAP_START, true);
  public static final Address AVAILABLE_START = chunkAlign(VM.AVAILABLE_START, false);
  public static final Address AVAILABLE_END = chunkAlign(VM.AVAILABLE_END, true);
  public static final Extent AVAILABLE_BYTES = AVAILABLE_END.toWord().minus(AVAILABLE_START.toWord()).toExtent();
  public static final int AVAILABLE_PAGES = AVAILABLE_BYTES.toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
  public static final Address HEAP_END = chunkAlign(VM.HEAP_END, false);

  private static final boolean FORCE_SLOW_MAP_LOOKUP = false;

  private static final int PAGES = 0;
  private static final int MB = 1;
  private static final int PAGES_MB = 2;
  private static final int MB_PAGES = 3;

  private static int spaceCount = 0;
  private static Space[] spaces = new Space[MAX_SPACES];
  private static Address heapCursor = HEAP_START;
  private static Address heapLimit = HEAP_END;

  /****************************************************************************
   *
   * Instance variables
   */
  private final String name;
  private final int nameLength;
  protected final int descriptor;
  private final int index;
  private final VMRequest vmRequest;

  protected final boolean immortal;
  protected final boolean movable;
  protected final boolean contiguous;

  protected PageResource pr;
  protected final Address start;
  protected final Extent extent;
  protected Address headDiscontiguousRegion;

  private boolean allocationFailed;

  /****************************************************************************
   *
   * Initialization
   */

  {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(PAGES_IN_CHUNK > 1);
  }

  /**
   * This is the base constructor for <i>all</i> spaces.<p>
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param vmRequest An object describing the virtual memory requested.
   */
  protected Space(String name, boolean movable, boolean immortal, VMRequest vmRequest) {
    this.name = name;
    this.nameLength = name.length();  // necessary to avoid calling length() in uninterruptible code
    this.movable = movable;
    this.immortal = immortal;
    this.vmRequest = vmRequest;
    this.index = spaceCount++;
    spaces[index] = this;

    if (vmRequest.type == VMRequest.REQUEST_DISCONTIGUOUS) {
      this.contiguous = false;
      this.descriptor = SpaceDescriptor.createDescriptor();
      this.start = Address.zero();
      this.extent = Extent.zero();
      this.headDiscontiguousRegion = Address.zero();
      VM.memory.setHeapRange(index, HEAP_START, HEAP_END); // this should really be refined!  Once we have a code space, we can be a lot more specific about what is a valid code heap area
      return;
    }

    Address start;
    Extent extent;

    if (vmRequest.type == VMRequest.REQUEST_FRACTION) {
      extent = getFracAvailable(vmRequest.frac);
    } else {
      extent = vmRequest.extent;
    }

    if (extent.NE(chunkAlign(extent, false))) {
      VM.assertions.fail(name + " requested non-aligned extent: " + extent.toLong() + " bytes");
    }

    if (vmRequest.type == VMRequest.REQUEST_FIXED) {
      start = vmRequest.start;
      if (start.NE(chunkAlign(start, false))) {
        VM.assertions.fail(name + " starting on non-aligned boundary: " + start.toLong() + " bytes");
      }
    } else if (vmRequest.top) {
      heapLimit = heapLimit.minus(extent);
      start = heapLimit;
    } else {
      start = heapCursor;
      heapCursor = heapCursor.plus(extent);
    }

    if (heapCursor.GT(heapLimit)) {
      Log.write("Out of virtual address space allocating \"");
      Log.write(name); Log.write("\" at ");
      Log.write(heapCursor.minus(extent)); Log.write(" (");
      Log.write(heapCursor); Log.write(" > ");
      Log.write(heapLimit); Log.writeln(")");
      VM.assertions.fail("exiting");
    }

    this.contiguous = true;
    this.start = start;
    this.extent = extent;
    this.descriptor = SpaceDescriptor.createDescriptor(start, start.plus(extent));

    VM.memory.setHeapRange(index, start, start.plus(extent));
    Map.insert(start, extent, descriptor, this);

    if (DEBUG) {
      Log.write(name); Log.write(" ");
      Log.write(start); Log.write(" ");
      Log.write(start.plus(extent)); Log.write(" ");
      Log.writeln(extent.toWord());
    }
  }

  /****************************************************************************
   *
   * Accessor methods
   */

  /** Start of discontig getter @return The start of the discontiguous space */
  public static Address getDiscontigStart() { return heapCursor; }

  /** End of discontig getter @return The end of the discontiguous space */
  public static Address getDiscontigEnd() { return heapLimit.minus(1); }

  /** Name getter @return The name of this space */
  public final String getName() { return name; }

  /** Start getter @return The start address of this space */
  public final Address getStart() { return start; }

  /** Extent getter @return The size (extent) of this space */
  public final Extent getExtent() { return extent; }

  /** Descriptor method @return The integer descriptor for this space */
  public final int getDescriptor() { return descriptor; }

  /** Index getter @return The index (ordinal number) of this space */
  public final int getIndex() { return index; }

  /** Immortal getter @return True if this space is never collected */
  public final boolean isImmortal() { return immortal; }

  /** Movable getter @return True if objects in this space may move */
  public boolean isMovable() { return movable; }

  /** Allocationfailed getter @return true if an allocation has failed since GC */
  public final boolean allocationFailed() { return allocationFailed; }

  /** Clear Allocationfailed flag */
  public final void clearAllocationFailed() { allocationFailed = false; }

  /** ReservedPages getter @return The number of reserved pages */
  public final int reservedPages() { return pr.reservedPages(); }

  /** CommittedPages getter @return The number of committed pages */
  public final int committedPages() { return pr.committedPages(); }

  /** AvailablePages getter @return The number of pages available for allocation */
  public final int availablePhysicalPages() { return pr.getAvailablePhysicalPages(); }

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
    if (FORCE_SLOW_MAP_LOOKUP || !SpaceDescriptor.isContiguous(descriptor)) {
      return Map.getDescriptorForAddress(address) == descriptor;
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
   * Return the space for a given address, not necessarily the
   * start address of an object.
   *
   * @param addr The address in question
   * @return The space containing the address
   */
  public static Space getSpaceForAddress(Address addr) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr.isZero());
    return Map.getSpaceForAddress(addr);
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
  @LogicallyUninterruptible
  public final Address acquire(int pages) {
    boolean allowPoll = VM.activePlan.isMutator() && Plan.isInitialized();

    /* Check page budget */
    int pagesReserved = pr.reservePages(pages);

    /* Poll, either fixing budget or requiring GC */
    if (allowPoll && VM.activePlan.global().poll(false, this)) {
      pr.clearRequest(pagesReserved);
      VM.collection.blockForGC();
      return Address.zero(); // GC required, return failure
    }

    /* Page budget is ok, try to acquire virtual memory */
    Address rtn = pr.getNewPages(pagesReserved, pages);
    if (rtn.isZero()) {
      /* Failed, so force a GC */
      if (!allowPoll) VM.assertions.fail("Physical allocation failed when polling not allowed!");
      boolean gcPerformed = VM.activePlan.global().poll(true, this);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(gcPerformed, "GC not performed when forced.");
      pr.clearRequest(pagesReserved);
      VM.collection.blockForGC();
      return Address.zero();
    }

    return rtn;
  }

  /**
   * Extend the virtual memory associated with a particular discontiguous
   * space.  This simply involves requesting a suitable number of chunks
   * from the pool of chunks available to discontiguous spaces.
   *
   * @param chunks The number of chunks by which the space needs to be extended
   * @return The address of the new discontiguous space.
   */
  public Address growDiscontiguousSpace(int chunks) {
    return headDiscontiguousRegion = Map.allocateContiguousChunks(descriptor, this, chunks, headDiscontiguousRegion);
  }

  /**
   * Return the number of chunks required to satisfy a request for a certain number of pages
   *
   * @param pages The number of pages desired
   * @return The number of chunks needed to satisfy the request
   */
  public static int requiredChunks(int pages) {
    Extent extent = chunkAlign(Extent.fromIntZeroExtend(pages<<LOG_BYTES_IN_PAGE), false);
    return extent.toWord().rshl(LOG_BYTES_IN_CHUNK).toInt();
  }

  /**
   * This hook is called by page resources each time a space grows.  The space may
   * tap into the hook to monitor heap growth.  The call is made from within the
   * page resources' critical region, immediately before yielding the lock.
   *
   * @param start The start of the newly allocated space
   * @param bytes The size of the newly allocated space
   * @param newChunk True if the new space encroached upon or started a new chunk or chunks.
   */
  public void growSpace(Address start, Extent bytes, boolean newChunk) {}

  /**
   * Release one or more contiguous chunks associated with a discontiguous
   * space.
   *
   * @param chunk THe address of the start of the contiguous chunk or chunks
   * @return The number of chunks freed
   */
  public int releaseDiscontiguousChunks(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(chunk.EQ(chunkAlign(chunk, true)));
    if (chunk.EQ(headDiscontiguousRegion)) {
      headDiscontiguousRegion = Map.getNextContiguousRegion(chunk);
    }
    return Map.freeContiguousChunks(chunk);
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
    Log.writeln("Key: (I)mmortal (N)onmoving (D)iscontiguous (E)xtent (F)raction");
    Log.write("     HEAP_START "); Log.writeln(HEAP_START);
    Log.write("AVAILABLE_START "); Log.writeln(AVAILABLE_START);
    for (int i = 0; i < spaceCount; i++) {
      Space space = spaces[i];

      for (int s = 0; s < 11 - space.nameLength; s++)
        Log.write(" ");
      Log.write(space.name); Log.write(" ");
      Log.write(space.immortal ? "I" : " ");
      Log.write(space.movable ? " " : "N");

      if (space.contiguous) {
        Log.write("  ");
        Log.write(space.start); Log.write("->");
        Log.write(space.start.plus(space.extent.minus(1)));
        if (space.vmRequest.type == VMRequest.REQUEST_EXTENT) {
          Log.write(" E "); Log.write(space.vmRequest.extent);
        } else if (space.vmRequest.type == VMRequest.REQUEST_FRACTION) {
          Log.write(" F "); Log.write(space.vmRequest.frac);
        }
        Log.writeln();
      } else {
        Log.write("D [");
        for(Address a = space.headDiscontiguousRegion; !a.isZero(); a = Map.getNextContiguousRegion(a)) {
          Log.write(a); Log.write("->");
          Log.write(a.plus(Map.getContiguousRegionSize(a).minus(1)));
          if (Map.getNextContiguousRegion(a) != Address.zero())
            Log.write(", ");
        }
        Log.writeln("]");
      }
    }
    Log.write("  AVAILABLE_END "); Log.writeln(AVAILABLE_END);
    Log.write("       HEAP_END "); Log.writeln(HEAP_END);
  }

  /**
   * Interface to use to implement the Visitor Pattern for Spaces.
   */
  public static interface SpaceVisitor {
    void visit(Space s);
  }

  /**
   * Implement the Visitor Pattern for Spaces.
   * @param v The visitor to perform on each Space instance
   */
  @Interruptible
  public static void visitSpaces(SpaceVisitor v) {
    for (int i = 0; i < spaceCount; i++) {
      v.visit(spaces[i]);
    }
  }


  /**
   * Ensure that all MMTk spaces (all spaces aside from the VM space)
   * are mapped. Demand zero map all of them if they are not already
   * mapped.
   */
  @Interruptible
  public static void eagerlyMmapMMTkSpaces() {
    eagerlyMmapMMTkContiguousSpaces();
    eagerlyMmapMMTkDiscontiguousSpaces();
  }


  /**
   * Ensure that all contiguous MMTk spaces are mapped. Demand zero map
   * all of them if they are not already mapped.
   */
  @Interruptible
  public static void eagerlyMmapMMTkContiguousSpaces() {
    for (int i = 0; i < spaceCount; i++) {
      Space space = spaces[i];
      if (space != VM.memory.getVMSpace()) {
        if (Options.verbose.getValue() > 2) {
          Log.write("Mapping ");
          Log.write(space.name);
          Log.write(" ");
          Log.write(space.start);
          Log.write("->");
          Log.writeln(space.start.plus(space.extent.minus(1)));
        }
        Mmapper.ensureMapped(space.start, space.extent.toInt()>>LOG_BYTES_IN_PAGE);
      }
    }
  }

  /**
   * Ensure that all discontiguous MMTk spaces are mapped. Demand zero map
   * all of them if they are not already mapped.
   */
  @Interruptible
  public static void eagerlyMmapMMTkDiscontiguousSpaces() {
    Address regionStart = Space.getDiscontigStart();
    Address regionEnd = Space.getDiscontigEnd();
    int pages = regionEnd.diff(regionStart).toInt()>>LOG_BYTES_IN_PAGE;
    Log.write("Mapping discontiguous spaces ");
    Log.write(regionStart);
    Log.write("->");
    Log.writeln(regionEnd.minus(1));
    Mmapper.ensureMapped(getDiscontigStart(), pages);
  }

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
  public abstract ObjectReference traceObject(TransitiveClosure trace, ObjectReference object);


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
   * @return The chunk-aligned address
   */
  public static Address chunkAlign(Address addr, boolean down) {
    if (!down) addr = addr.plus(BYTES_IN_CHUNK - 1);
    return addr.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toAddress();
  }

  /**
   * Align an extent to a space chunk
   *
   * @param bytes The extent to be aligned
   * @param down If true the extent will be rounded down, otherwise
   * it will rounded up.
   * @return The chunk-aligned extent
   */
  public static Extent chunkAlign(Extent bytes, boolean down) {
    if (!down) bytes = bytes.plus(BYTES_IN_CHUNK - 1);
    return bytes.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toExtent();
  }

  /**
   * Convert a fraction into a number of bytes according to the
   * fraction of available bytes.
   *
   * @param frac The fraction of available virtual memory desired
   * @return The corresponding number of bytes, chunk-aligned.
   */
  public static Extent getFracAvailable(float frac) {
    long bytes = (long) (frac * AVAILABLE_BYTES.toLong());
    Word mb = Word.fromIntSignExtend((int) (bytes >> LOG_BYTES_IN_MBYTE));
    Extent rtn = mb.lsh(LOG_BYTES_IN_MBYTE).toExtent();
    return chunkAlign(rtn, false);
  }
}
