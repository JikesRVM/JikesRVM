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
 * This class is the basis for all policies.  Each policy maps to a
 * space (a region of virtual memory which may be contigious or
 * discontigious).  This class manages spaces.<p>
 *
 * Currently discontigious spaces are unsuppored.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class Space implements Constants, Uninterruptible {
  /* somewhat arbitrary for the 64 bit system at this stage */
  private static final int LOG_ADDRESS_SPACE = (BYTES_IN_ADDRESS == 32) ? 32 : 40;

  public static final int LOG_BYTES_IN_CHUNK = 22;
  public static final int BYTES_IN_CHUNK = 1<<LOG_BYTES_IN_CHUNK;
  private static final int LOG_MAX_CHUNKS = LOG_ADDRESS_SPACE - LOG_BYTES_IN_CHUNK;
  public static final int MAX_CHUNKS = 1<<LOG_MAX_CHUNKS;
  private static int spaceCount = 0;
  private static Address HEAP_START = chunkAlign(Memory.HEAP_START(), true);
  private static Address AVAILABLE_START = chunkAlign(Memory.AVAILABLE_START(), false);
  private static Address AVAILABLE_END = chunkAlign(Memory.AVAILABLE_END(), true);
  private static Extent AVAILABLE_BYTES = AVAILABLE_END.toWord().sub(AVAILABLE_START.toWord()).toExtent();
  public static Address HEAP_END = chunkAlign(Memory.HEAP_END(), false);

  private static int[] map;

  private static boolean DEBUG = false;

  public static final int MAX_SPACES = 20; // quite arbitrary
  private static Space[] spaces = new Space[MAX_SPACES];
  
  private static Address heapCursor = HEAP_START;
  private static Address heapLimit = HEAP_END;
  private int id;
  protected Extent extent;
  private String name;
  protected Address start;
  private int index;

  protected PageResource pr;
  protected boolean immortal;
  protected boolean movable;

  public Space(String name, boolean movable, boolean immortal, Address start, 
                Extent bytes) {
    this.name = name;
    this.movable = movable;
    this.immortal = immortal;
    this.start = start;
    index = spaceCount++;
    spaces[index] = this;

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
    Address end = start.add(bytes);
    Memory.setHeapRange(index, start, end);
    if (DEBUG) {
      Log.write(name); Log.write(" ");
      Log.write(start); Log.write(" ");
      Log.write(start.add(extent)); Log.write(" ");
      Log.writeln(bytes.toWord());
    }
    createID(false);
    Map.insert(start, extent, id, this);
  }

  private Space(String name, boolean movable, boolean immortal, Extent bytes,
		boolean top) {
    this(name, movable, immortal, (top) ? HEAP_END.sub(bytes) : HEAP_START, 
         bytes);
    if (top) {
      if (heapLimit.NE(HEAP_END)) {
	Log.write("Unable to satisfy virtual address space request \"");
	Log.write(name); Log.write("\" at ");
	Log.writeln(heapLimit);
	Assert.fail("exiting");
      }
      heapLimit = heapLimit.sub(extent);
    } else {
      if (heapCursor.GT(HEAP_START)) {
	Log.write("Unable to satisfy virtual address space request \"");
	Log.write(name); Log.write("\" at ");
	Log.writeln(heapCursor);
	Assert.fail("exiting");
      }
      heapCursor = heapCursor.add(extent);
    }
  }

  public Space(String name, boolean movable, boolean immortal, int mb) {
    this(name, movable, immortal, heapCursor, Word.fromInt(mb).lsh(LOG_BYTES_IN_MBYTE).toExtent());
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

  public Space(String name, boolean movable, boolean immortal, float frac) {
    this(name, movable, immortal, heapCursor, getFracAvailable(frac));
    heapCursor = heapCursor.add(extent);
  }

  public Space(String name, boolean movable, boolean immortal, float frac,
	       boolean top) {
    this(name, movable, immortal, getFracAvailable(frac), top);
  }


  public Space(String name, boolean movable, boolean immortal, int mb,
	       boolean top) {
    this(name, movable, immortal, Word.fromInt(mb).lsh(LOG_BYTES_IN_MBYTE).toExtent(), top);
  }

  private static Extent getFracAvailable(float frac) {
    long bytes = (long) (frac * AVAILABLE_BYTES.toLong());
    Word mb = Word.fromInt((int) (bytes >> LOG_BYTES_IN_MBYTE));
    Extent rtn = mb.lsh(LOG_BYTES_IN_MBYTE).toExtent();
    return chunkAlign(rtn, false);
  }

  public Extent getExtent() { return extent; }
  
  public static void showUsageMB() { showUsage(MB); }
  public static void showUsagePages() { showUsage(PAGES); }

  private static void showUsage(int mode) {
    Log.write("used = ");
    writePages(getPagesUsed(), mode);
    boolean first = true;
    for (int i = 0; i < spaceCount; i++) {
      Space space = spaces[i];
      Log.write(first ? " = " : " + ");
      first = false;
      Log.write(space.name); Log.write(" ");
      writePages(space.reservedPages(), mode);
    }
    Log.writeln();
  }

  public static void showVMMap() {
    for (int i = 0; i < spaceCount; i++) {
      Space space = spaces[i];
      Log.write(space.name); Log.write(" ");
      Log.write(space.start); Log.write("->");
      Log.writeln(space.start.add(space.extent.sub(1))); 
    }
  }

  public Address getStart() {
//     if (Assert.VERIFY_ASSERTIONS) Assert._assert(!shared);
    return start;
  }

  private static final int getPagesUsed() {
    int pages = 0;
    for (int i = 0; i < spaceCount; i++) {
      pages += spaces[i].reservedPages();
    }
    return pages;
  }

  private int hashAddress(Address a) throws InlinePragma {
    return a.toWord().rshl(LOG_BYTES_IN_CHUNK).toInt();
  }

  private void createID(boolean shared) {
    if (shared) 
      id = SpaceDescriptor.getDescriptor();
    else
      id = SpaceDescriptor.getDescriptor(start, start.add(extent));
  }
  
  public int getID() {
    return id;
  }
  
  public int getIndex() { return index; }

  public static int getIDForObject(Address object) throws InlinePragma {
    return Map.getIDForObject(object);
  }
  
  public static Space getSpaceForObject(Address object) 
    throws InlinePragma {
    return Map.getSpaceForObject(object);
  }

  public static boolean isInSpace(int descriptor, Address object)
    throws InlinePragma {
    if (!SpaceDescriptor.isContiguous(descriptor)) {
      return getIDForObject(object) == descriptor;
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

  public String name() { return name; }

  public static boolean isImmortal(Address object) {
    Space space = getSpaceForObject(object);
    if (space == null) 
      return true;
    else
      return space.isImmortal();
  }

  public static boolean isMovable(Address object) {
    Space space = getSpaceForObject(object);
    if (space == null) 
      return true;
    else
      return space.isMovable();
  }

  public static boolean isInVM(Address object) {
    return getSpaceForObject(object) != null;
  }

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

  public void release(Address start) { Assert._assert(false); }

  public final int reservedPages() { return pr.reservedPages(); }
  public final int committedPages() { return pr.committedPages(); }


  public static final boolean mappedObject(Address object)
    throws InlinePragma {
    return Map.getSpaceForObject(object) != null;
  }

  public static final boolean mappedAddress(Address address)
    throws InlinePragma {
    return Map.getSpaceForAddress(address) != null;
  }

  private static final Address chunkAlign(Address addr, boolean down) {
    if (!down) addr = addr.add(BYTES_IN_CHUNK - 1);
    return addr.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toAddress();
  }

  private static final Extent chunkAlign(Extent bytes, boolean down) {
    if (!down) bytes = bytes.add(BYTES_IN_CHUNK - 1);
    return bytes.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toExtent();
  }

  /**
   * Return the cumulative number of committed pages
   *
   * @return The cumulative number of committed pages.
   */
  public static long cumulativeCommittedPages() { return PageResource.cumulativeCommittedPages(); }

  final static int PAGES = 0;
  public final static int MB = 1;
  final static int PAGES_MB = 2;
  final static int MB_PAGES = 3;

  /**
   * Print out the number of pages and or megabytes, depending on the mode.
   * A prefix string is outputted first.
   *
   * @param prefix A prefix string
   * @param pages The number of pages
   */
  public static void writePages(int pages, int mode) {
    double mb = Conversions.pagesToMBytes(pages);
    switch (mode) {
      case PAGES: Log.write(pages); Log.write(" pgs"); break; 
      case MB:    Log.write(mb); Log.write(" Mb"); break;
      case PAGES_MB: Log.write(pages); Log.write(" pgs ("); Log.write(mb); Log.write(" Mb)"); break;
    case MB_PAGES: Log.write(mb); Log.write(" Mb ("); Log.write(pages); Log.write(" pgs)"); break;
      default: Assert.fail("writePages passed illegal printing mode");
    }
  }

  public  boolean isImmortal() {
    return immortal; 
  }
  public  boolean isMovable() {
    return movable;
  }
  abstract public Address traceObject(Address object); 

}
