/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.policy;

import org.mmtk.plan.BasePlan;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.Map;
import org.mmtk.utility.heap.PageResource;
import org.mmtk.utility.heap.SpaceDescriptor;
import org.mmtk.utility.Log;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Memory;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Plan;

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
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class Space implements Constants, Uninterruptible {
  
  /****************************************************************************
   *
   * Class variables
   */

  private static boolean DEBUG = false;

  // the following is somewhat arbitrary for the 64 bit system at this stage
  private static final int LOG_ADDRESS_SPACE = (BYTES_IN_ADDRESS == 4) ? 32 : 40;
  private static Address HEAP_START = chunkAlign(Memory.HEAP_START(), true);
  private static Address AVAILABLE_START = chunkAlign(Memory.AVAILABLE_START(), false);
  private static Address AVAILABLE_END = chunkAlign(Memory.AVAILABLE_END(), true);
  private static Extent AVAILABLE_BYTES = AVAILABLE_END.toWord().sub(AVAILABLE_START.toWord()).toExtent();
  public static Address HEAP_END = chunkAlign(Memory.HEAP_END(), false);

  public static final int LOG_BYTES_IN_CHUNK = 22;
  public static final int BYTES_IN_CHUNK = 1<<LOG_BYTES_IN_CHUNK;
  private static final int LOG_MAX_CHUNKS = LOG_ADDRESS_SPACE - LOG_BYTES_IN_CHUNK;
  public static final int MAX_CHUNKS = 1<<LOG_MAX_CHUNKS;
  public static final int MAX_SPACES = 20; // quite arbitrary

  private static final int PAGES = 0;
  private static final int MB = 1;
  private static final int PAGES_MB = 2;
  private static final int MB_PAGES = 3;

  private static int spaceCount = 0;
  private static int[] map;
  private static Space[] spaces = new Space[MAX_SPACES];
  private static Address heapCursor = HEAP_START;
  private static Address heapLimit = HEAP_END;

  /****************************************************************************
   *
   * Instance variables
   */
  private int descriptor;
  private int index;
  private String name;
  protected Address start;
  protected Extent extent;
  protected boolean immortal;
  protected boolean movable;

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
   * @param start The start address of the space in virtual memory
   * @param bytes The size of the space in virtual memory, in bytes
   */
  Space(String name, boolean movable, boolean immortal, Address start, 
	Extent bytes) {
    this.name = name;
    this.movable = movable;
    this.immortal = immortal;
    this.start = start;
    index = spaceCount++;
    spaces[index] = this;

    /* ensure requests are chunk aligned */
    if (bytes.NE(chunkAlign(bytes, false))) {
      Log.write("Warning: ");
      Log.write(name);
      Log.write(" space request for ");
      Log.write(bytes.toLong()); Log.write(" bytes rounded up to ");
      bytes = chunkAlign(bytes, false);
      Log.write(bytes.toLong()); Log.writeln(" bytes");
      Log.writeln("(requests should be Space.BYTES_IN_CHUNK aligned)");
    }
    this.extent = bytes;

    Memory.setHeapRange(index, start, start.add(bytes));
    createDescriptor(false);
    Map.insert(start, extent, descriptor, this);

    if (DEBUG) {
      Log.write(name); Log.write(" ");
      Log.write(start); Log.write(" ");
      Log.write(start.add(extent)); Log.write(" ");
      Log.writeln(bytes.toWord());
    }
  }

  /**
   * Construct a space of a given number of megabytes in size.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>.  If there is insufficient address
   * space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   */
  Space(String name, boolean movable, boolean immortal, int mb) {
    this(name, movable, immortal, heapCursor, 
	 Word.fromInt(mb).lsh(LOG_BYTES_IN_MBYTE).toExtent());
    heapCursor = heapCursor.add(extent);
    if (heapCursor.GT(heapLimit)) {
      Log.write("Out of virtual address space allocating \"");
      Log.write(name); Log.write("\" at ");
      Log.write(heapCursor.sub(extent)); Log.write(" (");
      Log.write(heapCursor); Log.write(" > ");
      Log.write(heapLimit); Log.writeln(")");
      Assert.fail("exiting");
    }
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>.  If there
   * is insufficient address space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   */
  Space(String name, boolean movable, boolean immortal, float frac) {
    this(name, movable, immortal, heapCursor, getFracAvailable(frac));
    heapCursor = heapCursor.add(extent);
  }

  /**
   * Construct a space that consumes a given number of megabytes of
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>, and whether it should be at the
   * top or bottom of the available virtual memory.  If the request
   * clashes with existing virtual memory allocations, then the
   * constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  Space(String name, boolean movable, boolean immortal, int mb, boolean top) {
    this(name, movable, immortal, 
	 Word.fromInt(mb).lsh(LOG_BYTES_IN_MBYTE).toExtent(), top);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>, and
   * whether it should be at the top or bottom of the available
   * virtual memory.  If the request clashes with existing virtual
   * memory allocations, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  Space(String name, boolean movable, boolean immortal, float frac, 
	boolean top) {
    this(name, movable, immortal, getFracAvailable(frac), top);
  }

  /**
   * This is a private constructor that creates a contigious space at
   * the top or bottom of the available virtual memory, if
   * possible.<p>
   *
   * The caller specifies the size of the region of virtual memory and
   * whether it should be at the top or bottom of the available
   * address space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param bytes The size of the space in virtual memory, in bytes
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  private Space(String name, boolean movable, boolean immortal, Extent bytes,
		boolean top) {
    this(name, movable, immortal, (top) ? HEAP_END.sub(bytes) : HEAP_START, 
         bytes);
    if (top) {  // request for the top of available memory
      if (heapLimit.NE(HEAP_END)) {
	Log.write("Unable to satisfy virtual address space request \"");
	Log.write(name); Log.write("\" at ");
	Log.writeln(heapLimit);
	Assert.fail("exiting");
      }
      heapLimit = heapLimit.sub(extent);
    } else {   // request for the bottom of available memory
      if (heapCursor.GT(HEAP_START)) {
	Log.write("Unable to satisfy virtual address space request \"");
	Log.write(name); Log.write("\" at ");
	Log.writeln(heapCursor);
	Assert.fail("exiting");
      }
      heapCursor = heapCursor.add(extent);
    }
  }


  /****************************************************************************
   *
   * Accessor methods
   */

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
  public final boolean isMovable() { return movable; }

  /** ReservedPages getter @return The number of reserved pages */
  public final int reservedPages() { return pr.reservedPages(); }

  /** CommittedPages getter @return The number of committed pages */
  public final int committedPages() { return pr.committedPages(); }

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
  public static final boolean isImmortal(Address object) {
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
  public static final boolean isMovable(Address object)
    throws InlinePragma {
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
  public static final boolean isMappedObject(Address object)
    throws InlinePragma {
    return !object.isZero() && (getSpaceForObject(object) != null);
  }

  /**
   * Return true if the given address is in a space managed by MMTk.
   *
   * @param address The address in question
   * @return True if the given address is in a space managed by MMTk.
   */
  public static final boolean isMappedAddress(Address address)
    throws InlinePragma {
    return Map.getSpaceForAddress(address) != null;
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
  public static boolean isInSpace(int descriptor, Address object)
    throws InlinePragma {
    if (!SpaceDescriptor.isContiguous(descriptor)) {
      return getDescriptorForObject(object) == descriptor;
    } else {
      Address addr = ObjectModel.refToAddress(object);
      Address start = SpaceDescriptor.getStart(descriptor);
      if (!Assert.VERIFY_ASSERTIONS &&
	  SpaceDescriptor.isContiguousHi(descriptor))
	return addr.GE(start);
      else {
	Extent size = Word.fromInt(SpaceDescriptor.getChunks(descriptor)).lsh(LOG_BYTES_IN_CHUNK).toExtent();
	Address end = start.add(size);
	return addr.GE(start) && addr.LT(end);
      }
    }
  }

  /**
   * Return the space for a given object
   *
   * @param object The object in question
   * @return The space containing the object
   */
  public static Space getSpaceForObject(Address object) 
    throws InlinePragma {
    return Map.getSpaceForObject(object);
  }

  /**
   * Return the descriptor for a given object
   *
   * @param object The object in question
   * @return The descriptor for the space containing the object
   */
  public static int getDescriptorForObject(Address object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isZero());
    return Map.getDescriptorForObject(object);
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
    /* First check page budget and poll if necessary */
    if (!pr.reservePages(pages)) {
      /* Need to poll, either fixing budget or requiring GC */
      if (Plan.getInstance().poll(false, this))
        return Address.zero(); // GC required, return failure
    }
    
    /* Page budget is OK, so try to acquire specific pages in virtual memory */
    Address rtn = pr.getNewPages(pages);
    if (rtn.isZero())
      Plan.getInstance().poll(true, this); // Failed, so force a GC

    return rtn;
  }

  /**
   * Release a unit of allocation (a page or pages)
   *
   * @param start The address of the start of the region to be released
   */
  abstract public void release(Address start);

  /**
   * Get the total number of pages reserved by all of the spaces
   *
   * @return the total number of papers reserved by all of the spaces
   */
  private static final int getPagesReserved() {
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
      Log.write(space.start); Log.write("->");
      Log.writeln(space.start.add(space.extent.sub(1))); 
    }
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
    double mb = Conversions.pagesToMBytes(pages);
    switch (mode) {
    case PAGES: Log.write(pages); Log.write(" pgs"); break; 
    case MB:    Log.write(mb); Log.write(" Mb"); break;
    case PAGES_MB: Log.write(pages); Log.write(" pgs ("); Log.write(mb); Log.write(" Mb)"); break;
    case MB_PAGES: Log.write(mb); Log.write(" Mb ("); Log.write(pages); Log.write(" pgs)"); break;
      default: Assert.fail("writePages passed illegal printing mode");
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
   * @param object
   * @return The object, forwarded, if appropriate
   */
  abstract public Address traceObject(Address object); 

  /**
   * Align an address to a space chunk
   *
   * @param addr The address to be aligned
   * @param down If true the address will be rounded down, otherwise
   * it will rounded up.
   */
  private static final Address chunkAlign(Address addr, boolean down) {
    if (!down) addr = addr.add(BYTES_IN_CHUNK - 1);
    return addr.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toAddress();
  }

  /**
   * Align an extent to a space chunk
   *
   * @param bytes The extent to be aligned
   * @param down If true the address will be rounded down, otherwise
   * it will rounded up.
   */
  private static final Extent chunkAlign(Extent bytes, boolean down) {
    if (!down) bytes = bytes.add(BYTES_IN_CHUNK - 1);
    return bytes.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toExtent();
  }

  /**
   * Initialize/create the descriptor for this space
   *
   * @param shared True if this is a shared (discontigious) space
   */
  private void createDescriptor(boolean shared) {
    if (shared) 
      descriptor = SpaceDescriptor.createDescriptor();
    else
      descriptor = SpaceDescriptor.createDescriptor(start, start.add(extent));
  }
  
  /**
   * Convert a fraction into a number of bytes according to the
   * fraction of available bytes.
   *
   * @param frac The fraction of avialable virtual memory desired
   * @return The corresponding number of bytes, chunk-aligned.
   */
  private static Extent getFracAvailable(float frac) {
    long bytes = (long) (frac * AVAILABLE_BYTES.toLong());
    Word mb = Word.fromInt((int) (bytes >> LOG_BYTES_IN_MBYTE));
    Extent rtn = mb.lsh(LOG_BYTES_IN_MBYTE).toExtent();
    return chunkAlign(rtn, false);
  }
}
