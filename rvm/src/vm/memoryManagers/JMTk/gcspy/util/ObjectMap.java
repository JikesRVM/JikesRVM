/*
 * (C) Copyright Richard Jones, University of Kent at Canterbury 2001-3.
 * All rights reserved.
 */

package org.mmtk.utility.gcspy;

import com.ibm.JikesRVM.VM_SizeConstants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

//-#if RVM_WITH_GCSPY
import org.mmtk.plan.Plan;
import org.mmtk.utility.Log;
import org.mmtk.utility.Options;
import org.mmtk.utility.heap.VMResource;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.gcspy.Util;

import com.ibm.JikesRVM.VM_Memory;

//-#endif

/**
 * THIS CLASS IS NOT A GCSPY COMPONENT
 *
 * However, a collector must provide GCspy with a data about 
 * objects in the heap. One way to do this might be directly,
 * at allocation time. In principle, this is a bad solution
 * because it imposes a tax on the allocator.
 * Another way is to trawl through the heap at collection time,
 * but this requires a method of identifying all objects in 
 * (the region of) the heap under examination.  
 * The current layout of Jikes scalars and arrays makes this difficult
 * - although there are several ways in which this might be fixed.
 *
 * This class provides a map of objects that have been allocated.
 * By iterating through the map, all objects can be discovered.
 * Again, this adds a run-time cost to allocation which is a Bad
 * Thing (TM).
 *
 * We create an object map large enough to accommodate the largest
 * possible heap. 
 * As each processor allocates to a different block, we don't need
 * separate object maps for each processor.
 *
 * Although ObjectMap.alloc needs to be fast, space usage needs to 
 * be kept reasonable.
 * A single object map, sufficiently large to cover the largest possible
 * heap, would be the fastest solution but requires too much space
 * Instead, we use a 3-level object map. The second two levels are allocated lazily.
 *
 *              +-------+
 *              | chk 0 |--->bitmap 0
 *   +------+   +-------+
 *   | pg 0 |-->| chk 1 |--->bitmap 1
 *   +------+   +-------+      ^
 *   |      |   |  ...  |      |
 *   +------+   +-------+      |
 *   | pg n |   | chk n |      |
 *   +----^-+   +---^---+      |
 *        |         |          |
 *        +-|-----+---|---+--------+
 *        | page  | chunk | offset | object address
 *        +-------+-------+--------+
 *
 * Bitmap layout:
 *     msb                                                lsb
 *    +------------------------------------------------------+
 *    | | | |              ...                             | |
 *    +------------------------------------------------------+
 *     ^                                                    ^
 *     |                                                    |
 *  last word in this chunk                     first word in this chunk
 *
 * Address to bitmap translation:
 * +-----------------+--------------------+-------------+------------+--+
 * | page number     | chunk number       | word number | bit number |00|
 * +-----------------+--------------------+-------------+------------+--+
 *                    <------------------> LOG_BITMAPS_IN_PAGEMAP     <> LOG_OBJECT_ALIGNMENT
 *  <----------------> LOG_PAGEMAPS_IN_OBJECTMAP         <----------> LOG_BITS_IN_INT            
 *                                         <-----------> LOG_INTS_IN_BITMAP
 *
 * Note: throughout assume BITMAP_SIZE is a power of 2
 *
 * @author <a href="http://www.cs.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */


public class ObjectMap 
  implements VM_SizeConstants, Uninterruptible {
  public final static String Id = "$Id$";
 
 
//-#if RVM_WITH_GCSPY
  ////////////////////////////////////////////////////////////////////////////
  // Constants
  //
  // Nb. malloc doesn't give page-aligned space

  private static final int LOG_PAGE_SIZE = 12;
  static final int PAGE_SIZE = 1<<LOG_PAGE_SIZE;

  // 
  // Bits
  // private static final int LOG_BITS_IN_INT =                                                  //    5
  private static final int LOG_OBJECT_ALIGNMENT = LOG_BYTES_IN_ADDRESS;                          //    2 - 3
  private static final int WORD_OFFSET = LOG_BITS_IN_INT + LOG_OBJECT_ALIGNMENT;                 //    7 - 8
  // BITMAP_SIZE *must* map precisely 1 VMResource.PAGE because we release whole bitmaps for efficiency
  private static final int LOG_INTS_IN_BITMAP = LOG_PAGE_SIZE - WORD_OFFSET;                     //    5 - 4
  private static final int INTS_IN_BITMAP = 1 << LOG_INTS_IN_BITMAP;                             //   32 - 16
  private static final int BITMAP_SIZE = INTS_IN_BITMAP << LOG_BYTES_IN_INT;                     //  128 - 64
  private static final int SLOT_OFFSET = WORD_OFFSET + LOG_INTS_IN_BITMAP;                       //   12 - 12
  
  // Page map
  // LOG_PAGES_PER_PAGEMAP is the only parameter that can be varied
  private static final int LOG_PAGES_PER_PAGEMAP = 0 + LOG_BYTES_IN_ADDRESS - LOG_BYTES_IN_INT;  //    0 - 1
  private static final int LOG_PAGEMAP_SIZE = LOG_PAGE_SIZE + LOG_PAGES_PER_PAGEMAP;             //   12 - 13
  private static final int PAGEMAP_SIZE =  1 << LOG_PAGEMAP_SIZE;                                // 4096 - 8192
  private static final int LOG_BITMAPS_IN_PAGEMAP = LOG_PAGEMAP_SIZE -  LOG_BYTES_IN_ADDRESS;    //   10
  private static final int BITMAPS_IN_PAGEMAP = 1 << LOG_BITMAPS_IN_PAGEMAP;                     // 1024
  private static final int PAGE_OFFSET = LOG_BITMAPS_IN_PAGEMAP + SLOT_OFFSET;                   //   22
  
  // Object map
  private static final int LOG_HEAPEND = 32; 
  private static final int LOG_PAGEMAPS_IN_OBJECTMAP =  LOG_HEAPEND - PAGE_OFFSET;               //   10
  private static final int PAGEMAPS_IN_OBJECTMAP = 1 << LOG_PAGEMAPS_IN_OBJECTMAP;               // 1024
  private static final int LOG_OBJECTMAP_SIZE = LOG_PAGEMAPS_IN_OBJECTMAP + LOG_BYTES_IN_ADDRESS;//   12 - 13
  private static final int OBJECTMAP_SIZE = 1 << LOG_OBJECTMAP_SIZE;                             // 4096 - 8192


  ////////////////////////////////////////////////////////////////////////////
  // Fields
  //
  
  private Address objectMap_;        // the object map

  // Iterator data
  private int iterPage_;                // index of the slot in objectMap_
  private int iterSlot_;                // index of the slot in the pagemap
  private int iterWord_;                // index of the word in the bitmap
  private int iterBit_;                 // number of the bit in this word
  private Address iterEnd_;          // address of end of iterator space
  private int iterPageEnd_;             // index of page of end of iterator space
  private boolean hasNext_; 

  // Debugging
  // TODO debug levels need sorting out
  public boolean allocationTrap = false;        // allow allocation
  public int allocCounter = 0;
  public Address lastAddressAllocated = Address.zero();
  private static int memoryConsumed = 0;
  private static int memoryHighWater = 0;
 
  
  ////////////////////////////////////////////////////////////////////////////
  // Initialisation
  //

  /**
   * Constructor
   */
  public ObjectMap() { }
  
  /**
   * Initialisation that occurs at *boot* time (runtime initialisation).
   * This is only executed by one processor (the primordial thread).
   * Our boot happens before the collector has booted
   */
  public final void boot() {
    objectMap_ = Util.malloc(OBJECTMAP_SIZE);
    VM_Memory.zero(objectMap_, OBJECTMAP_SIZE);
    memoryConsumed = OBJECTMAP_SIZE;
    memoryHighWater = OBJECTMAP_SIZE;
    
    debug(1, "ObjectMap booted, OBJECTMAP_SIZE=", OBJECTMAP_SIZE);
    debug(1, ", PAGEMAPS_IN_OBJECTMAP=", PAGEMAPS_IN_OBJECTMAP);
    debug(1, ", heap memory: ", Plan.totalMemory());    
    debugln(1, ", PAGE_SIZE=", PAGE_SIZE);
    debug(1, "PAGEMAP_SIZE=", PAGEMAP_SIZE);
    debugln(1, ", BITMAPS_IN_PAGEMAP=", BITMAPS_IN_PAGEMAP);
    debug(1, "BITMAP_SIZE=", BITMAP_SIZE);
    debugln(1, ", INTS_IN_BITMAP=", INTS_IN_BITMAP);
  }
  
  ////////////////////////////////////////////////////////////////////////////
  // Allocation/deallocation
  //

  /**
   * Grow the object map by n bytes by adding other bitmaps
   *
   * @param chunkAddr The address of the first chunk to be added 
   * @param chunks The number of chunks to be added
   */
  public final void grow(Address chunkAddr, int bytes) {
    addOrRelease(chunkAddr, bytes, true);
  }

  /**
   * Grow the object map one chunk by adding another bitmap
   *
   * @param chunkAddr The address of the chunk to be added 
   */
  public final void grow(Address chunkAddr) { 
    addOrRelease(chunkAddr, 1, true);
  }

  /**
   * Release n bytes from the object table
   *
   * @param chunkAddr The address of the first chunk to be released 
   * @param chunks The number of bytes to be released
   */
  public final void release(Address chunkAddr, int bytes) {
    addOrRelease(chunkAddr, bytes, false);
  }
  
  /**
   * Release a chunk from the object table
   *
   * @param chunkAddr The address of the chunk to be added 
   */
  public final void release(Address chunkAddr) {
    addOrRelease(chunkAddr, 1, false);
  }
  
  /**
   * Release a chunk(s) from the object table
   *
   * @param chunkAddr The address of the first chunk to be added/released
   * @param chunks The number of chunks to add or release
   * @param add Whether to add or release chunks
   */
  private final void addOrRelease(Address start, int bytes, boolean add) {   
    int page = addressToPage(start);
    Address end = start.add(bytes);
    int lastPage = addressToPage(end);

    debug(2, "ObjectMap.release(", start);
    debug(2, "-", end);
    debugln(2, ")");

    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(page <= lastPage, "ObjectMap.addOrRelease: start page > last page");
    }
                  
    if (add) { // adding chunks
      int slot = addressToSlot(start);
      while (page <= lastPage && page < PAGEMAPS_IN_OBJECTMAP) {
        int lastSlot = (page == lastPage) ? addressToSlot(end) : BITMAPS_IN_PAGEMAP;
        for ( ; slot < lastSlot; slot++) {
          checkSlot(page, slot);
        } 
        page++;
        slot = 0;
      }
    }
    else { 
      // Releasing chunks
      // Here we return bitmaps but not the (higher-level) pagemaps
      debug(2, "end page ", addressToPage(end));
      debug(2, ", end slot ", addressToSlot(end));
      debug(2, ", end word ", addressToWord(end));
      debugln(2, ", end bit ", addressToBit(end));
      if (VM_Interface.VerifyAssertions) {
        VM_Interface._assert(addressToWord(end) == 0, "addressToWord not zero"); // Must release complete bitmaps
        VM_Interface._assert(addressToBit(end)  == 0, "addressToBit not zero");
      }

      int slot = addressToSlot(start);
      while (page <= lastPage  && page < PAGEMAPS_IN_OBJECTMAP) {
        int lastSlot = (page == lastPage) ? addressToSlot(end) : BITMAPS_IN_PAGEMAP;
        for ( ; slot < lastSlot; slot++) {
          // Can anyone else have released this?
	  debug(2, "addOrRelease: is bitmap empty on page ", page);
          if (!emptyBitmap(page, slot)) {
	    debug(2, "?..freeing bitmap");
            freeBitmap(page, slot);
	  }
	  debugln(2, "..done slot ", slot);
        }
        page++;
        slot = 0;
      }
      // We set bits corresponding to object refs. Because a scalar ref points 1 word beyond
      // the extent of the object, we may set the first or second bit in the next bitmap.
      // However, we release bitmaps corresponding to semi-spaces, which do not include this
      // next bitmap. Here we clear these two bits, corresponding to end and end.add(BYTES_IN_ADDRESS), if set.
      page = addressToPage(end);
      slot = addressToSlot(end);
      if (!emptyBitmap(page, slot)) {
	if (getBit(page, slot, 0, 0))
          setBit(page, slot, 0, 0, false);
	if (getBit(page, slot, 0, 1))
	  setBit(page, slot, 0, 1, false);
      }
    }
  }

  /**
   * Zero a slot in the object map
   * @param s the slot to zero
   *
  private final void zeroSlot(int slot) { 
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(!objectMap_.isZero(), "objectMap_ is null!");
    Address addr = getSlotAddress(slot);
    if (VM_Interface.VerifyAssertions) {
      Address bitmap = VM _Magic.getMemoryAddress(addr);
      if (!bitmap.isZero()) 
        VM_Interface.sysWriteln("Zeroing object map slot with non-empty bitmap!", slot);
    }
    VM _Magic.setMemoryAddress(addr, Address.zero());
  }
  */
  
 
  /**
   * Zero a bitmap
   *
   * @param page the page to check
   * @param slot the slot to check
   */
  private final void freeBitmap(int page, int slot) {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(!objectMap_.isZero(), "objectMap_ is null!");
    
    Address addr = getSlotAddress(page, slot);
    Address bitmap = addr.loadAddress();
    if (!bitmap.isZero()) {
      Util.free(bitmap);
      addr.storeAddress(Address.zero());
      memoryConsumed -= BITMAP_SIZE;
    }
  }
  
  /**
   * Allocate a fresh pagemap (i.e. a set of pages of Address)
   *
   * @return the address of the start of this set of pages
   */
  private final Address allocPagemap() {
    Address pagemap = Util.malloc(PAGEMAP_SIZE);
    VM_Memory.zero(pagemap, PAGEMAP_SIZE);
    memoryConsumed += PAGEMAP_SIZE;
    if (memoryConsumed > memoryHighWater)
      memoryHighWater = memoryConsumed;
    debugln(2, "allocPagemap: memoryHighWater=", memoryHighWater);
    return pagemap;
  }
  
  /**
   * Allocate a fresh bitmap (i.e. a set of pages of int)
   *
   * @return the address of the start of this set of pages
   */
  private final Address allocBitmap() {
    //Address bitmap = rpa.alloc(PAGES_PER_BITMAP);
    Address bitmap = Util.malloc(BITMAP_SIZE);
    VM_Memory.zero(bitmap, BITMAP_SIZE);
    memoryConsumed += BITMAP_SIZE;
    if (memoryConsumed > memoryHighWater)
      memoryHighWater = memoryConsumed;
    debugln(2, "allocBitmap: memoryHighWater=", memoryHighWater);
    return bitmap;
  }

  /**
   * Check for empty slot in object map and acquire new bitmap
   * if necessary.
   *
   * @param page the page to check
   * @param slot the slot to check
   * @return true if a new bitmap is allocated
   */
  private final boolean checkSlot(int page, int slot) //throws InlinePragma 
  {
    boolean newpage = checkPage(page);
    
    Address slotAddr = getSlotAddress(page, slot);
    Address slotValue = slotAddr.loadAddress();
    if (VM_Interface.VerifyAssertions) {
      if (newpage) {
        if (!slotValue.isZero())
        VM_Interface._assert(slotValue.isZero(), "checkSlot: got a new pagemap but didn't need a new bitmap!");
      }
    }
    if (slotValue.isZero()) {
      Address bm = allocBitmap();
      debug(2, "allocating new bitmap for page ", page);
      debug(2, ", slot ", slot);
      debug(2, " (address ", slotAddr);
      debugln(2, ") at  ", bm);
      slotAddr.storeAddress(bm);
      return true;
    }
    return false;
  }

  /**
   * Check for empty pagemap in object map and acquire new pagemap
   * if necessary.
   *
   * @param page the index of pagemap  to check in the object table
   * @return true if new page allocated
   */
  private final boolean checkPage(int page) //throws InlinePragma 
  {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(!objectMap_.isZero(), "objectMap_ is null!");
    
    Address pageAddr = getPageAddress(page);
    Address pagemap = pageAddr.loadAddress();
    if (pagemap.isZero()) {
      pagemap = allocPagemap();
      debug(2, "allocating new pagemap for page ", page);
      debug(2, " (address ", pageAddr);
      debugln(2, ") at ", pagemap);
      pageAddr.storeAddress(pagemap);
      return true;
    }
    return false;
  }


  /**
   * Set bit in object map to show that an object has been allocated
   * at this address. 
   * TODO This version is probably unacceptably slow: instead we should track
   * bitmap position in the bump allocator, rather than recalculating from
   * the address each time.
   *
   * @param addr The address of the object
   */
  public final void alloc(Address addr) throws InlinePragma {
    // Check that allocation is allowed
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(!allocationTrap, "Unexpected allocation");
    
    // Get the slot, word and bit
    int page = addressToPage(addr);
    int slot = addressToSlot(addr);
    int word = addressToWord(addr);
    int bit  = addressToBit(addr);
    
    checkSlot(page, slot);
    setBit(page, slot, word, bit, true);

    // Debugging
    allocCounter++;
    lastAddressAllocated = addr;
  }

  /**
   * Unset bit in object map to show that the object at this address has 
   * been deallocated. 
   *
   * @param addr The address of the object
   */
  public final void dealloc(Address addr) {
    // Get the slot, word and bit
    int page = addressToPage(addr);
    int slot = addressToSlot(addr);
    int word = addressToWord(addr);
    int bit = addressToBit(addr);

    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(addr == bitmapToAddress(page, slot, word, bit));
    
    /*
    // Check that this slot is in use
    if (VM_Interface.VerifyAssertions) 
      if (emptyBitmap(page, slot)) {
        Log.write("ObjectMap slot not in use: ", page);
        Log.writeln(": ", slot);
        VM_Interface._assert(false);
      }
    */
    // FIXME It seems that some objects are created without being processed by postAlloc
    // (e.g. in semiSpace/Plan). Presumably these are primordial, hand-constructed objects ---
    // they appear to be right at the start of the first semispace. 
    // Thus, we'll not unset bits corresponding to these.
    // Fortunately there are few of these objects and we do track them correctly as
    // soon as they've been copied, for example.
    if (!emptyBitmap(page, slot)) 
      setBit(page, slot, word, bit, false);
  }


  ////////////////////////////////////////////////////////////////////////////
  // Page and bitmap lookup
  //

  /**
   * Get page address 
   *
   * @param index The index of a pagemap in the object map 
   * @return The address of this pagemap's slot in the object map
   */
  private Address getPageAddress(int index) //throws InlinePragma 
  {
    int offset = index << LOG_BYTES_IN_ADDRESS;
    return objectMap_.add(offset);
  }
  /**
   * Get page value 
   *
   * @param page The index of a pagemap in the object map
   * @return The address of the correspondng pagemap 
   */
  private Address getPagemap(int page) {
    Address addr = getPageAddress(page);
    return addr.loadAddress();
  }

  /**
   * Get slot address 
   *
   * @param page The index of the pagemap in the object map
   * @param slot The index of the bitmap in that pagemap
   * @return The address of this bitmap's slot in the pagemap
   */
  private Address getSlotAddress(int page, int slot) //throws InlinePragma 
  {
    int offset = slot << LOG_BYTES_IN_ADDRESS;
    Address pagemapAddr = getPagemap(page);
    return pagemapAddr.add(offset);
  }
  
  /**
   * Get slot value 
   *
   * @param page The index of the pagemap
   * @param slot The index of the bitmap in that pagemap
   * @return The address of the corresponding bitmap 
   */
  private Address getBitmap(int page, int slot) {
    Address slotAddr = getSlotAddress(page, slot);
    return slotAddr.loadAddress();
  }
  
  /**
   * Is page empty?
   *
   * @param page The index of  the pagemap in the objectmap
   * @return true if a pagemap has not been allocated for this index 
   */
  private boolean emptyPage(int page) {
    if (page >= PAGEMAPS_IN_OBJECTMAP)
      return true;
    return getPagemap(page).isZero();
  }

  /**
   * Is slot empty?
   *
   * @param page The index of  the pagemap in the objectmap
   * @param slot The index of the bitmap in that pagemap
   * @return true if a corresponding bitmap has not been allocated  
   */
  private boolean emptyBitmap(int page, int slot) {
    if (emptyPage(page))
      return true;
    return getBitmap(page, slot).isZero();
  }
  
  ////////////////////////////////////////////////////////////////////////////
  // Get and set bits
  //

  /**
   * Get value of an int in a bitmap
   *
   * @param page the page number
   * @param slot the slot number
   * @param word the int number
   * @return the int value
   */
  private final int getBitmapInt(int page, int slot, int word) //throws InlinePragma 
  {
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(page < PAGEMAPS_IN_OBJECTMAP);
      VM_Interface._assert(!emptyPage(page), "ObjectMap page not in use");
      VM_Interface._assert(slot < BITMAPS_IN_PAGEMAP);
      VM_Interface._assert(!emptyBitmap(page, slot), "ObjectMap slot not in use");
    }
    Address bitmap = getBitmap(page, slot);

    if (VM_Interface.VerifyAssertions) { 
      VM_Interface._assert(!bitmap.isZero(), "failed to get bitmap from getBitmap");
      VM_Interface._assert(word < INTS_IN_BITMAP);
    }
    int offset = word << LOG_BYTES_IN_INT;
    Address addr = bitmap.add(offset);
    return addr.loadInt();
  }
 
 /**
   * Set value of an int in a bitmap
   *
   * @param page the page number
   * @param slot the slot number
   * @param word the int number
   * @param value the new value
   */
  private final void setBitmapInt(int page, int slot, int word, int value) //throws InlinePragma 
  {
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(page < PAGEMAPS_IN_OBJECTMAP);
      VM_Interface._assert(!emptyPage(page), "ObjectMap page not in use");
      VM_Interface._assert(slot < BITMAPS_IN_PAGEMAP);
      VM_Interface._assert(!emptyBitmap(page, slot), "ObjectMap slot not in use");
    }
    Address bitmap = getBitmap(page, slot);
         
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(word < INTS_IN_BITMAP);        
    int offset = word << LOG_BYTES_IN_INT;
    Address addr = bitmap.add(offset);
    addr.storeInt(value);
  }
  
  /**
   * Get a bit in a bitmap
   * @param page the page in the object map
   * @param slot the slot in that page
   * @param word the word number
   * @param bit the bit number
   * @return true if set
   */
  private final boolean getBit(int page, int slot, int word, int bit) //throws InlinePragma 
  {
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(page < PAGEMAPS_IN_OBJECTMAP);
      VM_Interface._assert(slot < BITMAPS_IN_PAGEMAP);
      VM_Interface._assert(word < INTS_IN_BITMAP);
      VM_Interface._assert(bit < BITS_IN_INT);
      VM_Interface._assert(!getPagemap(page).isZero(), "setBit: bad page");
      VM_Interface._assert(!getBitmap(page, slot).isZero(), "setBit: bad slot");
    }

    // Get the value of the word
    int wordValue = getBitmapInt(page, slot, word);
    int mask = 1 << bit;
    return ((wordValue & mask) != 0);
  }

  /**
   * Set a bit in a bitmap
   *
   * @param page the page in the object map
   * @param slot the slot in that page
   * @param word the word number
   * @param bit the bit number
   * @param set Set bit to 1 if true, else set to 0
   */
  private final void setBit(int page, int slot, int word, int bit, boolean set) //throws InlinePragma 
  {
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(page < PAGEMAPS_IN_OBJECTMAP);
      VM_Interface._assert(slot < BITMAPS_IN_PAGEMAP);
      VM_Interface._assert(word < INTS_IN_BITMAP);
      VM_Interface._assert(bit < BITS_IN_INT);
      VM_Interface._assert(!getPagemap(page).isZero(), "setBit: bad page");
      VM_Interface._assert(!getBitmap(page, slot).isZero(), "setBit: bad slot");
    }

    // Get the old value of the word
    int wordValue = getBitmapInt(page, slot, word);
    int mask = 1 << bit;

    // Check that it isn't / is already set
    if (VM_Interface.VerifyAssertions) 
      if (set) {
        if((wordValue & mask) != 0) {
          debug(0, "Bit already set for ", bitmapToAddress(page, slot, word, bit));
          debug(0, ", slot=", slot);
          debug(0, ", word=", word);
          debug(0, ", bit=", bit);
          debug(0, ", wordValue=", wordValue);
          debugln(0, ", mask=", mask);
          debugln(0, "  Heap: ", Plan.totalMemory());
          debugln(0, "  Total allocated = ", allocCounter);
          debug(0, "  ");
          dbgObjectMap();
        }
        //VM_Interface._assert((wordValue & mask) == 0, "  ObjectMap bit already set");
      } else {
        if((wordValue & mask) == 0) {
	  return; // Accept that the bit is not set - see comments in dealloc
	  /*
          debug(0, "Bit not set for ", bitmapToAddress(page, slot, word, bit));
          debug(0, ", slot=", slot);
          debug(0, " word=", word);
          debugln(0, " bit=", bit);
          debug(0, ", wordValue=", wordValue);
          debug(0, ", mask=", mask);
          debugln(0, "  Heap: ", Plan.totalMemory());
          debugln(0, "  Total allocated = ", allocCounter);
          debug(0, "  ");
          dbgObjectMap();
	  */
        }
        //VM_Interface._assert((wordValue & mask) != 0, "  ObjectMap bit not set");
      }

    // Set the bit
    int value = set ? (wordValue | mask) :
                      (wordValue & (~mask));
    setBitmapInt(page, slot, word, value);
  }




  ////////////////////////////////////////////////////////////////////////////
  // Object map iterators 
  //


   /**
    * Initialise a simple iterator to walk over allocated objects 
    * in a contiguous region of the heap.
    * Note that the iterator methods are not rentrant, i.e. a call to
    * iterator() will reinitialise the iterator, disrupting hasNext() and
    * next().
    *
    * @param start The start of the region to iterate over (inclusive)
    * @param end The end of the region to iterate over (exclusive)
    *
    * @see hasNext
    * @see next
    */
  public void iterator(Address start, Address end) {
      debug(2, "Iterating from ", start);
      debug(2, "to ", end);

      // set boundaries of search space
      iterPage_ = addressToPage(start);
      iterSlot_ = addressToSlot(start);
      iterWord_ = addressToWord(start);
      iterBit_ = addressToBit(start);
      
      if (VM_Interface.VerifyAssertions)
        VM_Interface._assert(start == bitmapToAddress(iterPage_, iterSlot_, iterWord_, iterBit_));
      
      iterEnd_ = end;
      iterPageEnd_ = addressToPage(end);

      advanceIterator(false);
  }

  /** 
   * Are there any further objects in the region covered by this iterator?
   * Note that this advances the iterator at each call.
   * 
   * @return True if there are further objects
   *
   * @see next
   * @see iterator
   */
  public boolean hasNext()  {  
    return hasNext_;
  }

  /**
   * The address of the next object in the region covered by this iterator
   * 
   * @return The address of the next object or 0 if none
   */
  public Address next() {
    Address rv = hasNext_ ? bitmapToAddress(iterPage_, iterSlot_, iterWord_, iterBit_) 
                             : Address.zero();
    advanceIterator(true);
    return rv;
  }

  /**
   * Advance the iterator to the next item
   * 
   * @param strict True if the iterator is to be strictly advanced
   *               (i.e. never return current location)
   */
  private void advanceIterator(boolean strict) {
    
    int iterPage = iterPage_;
    int iterSlot = iterSlot_;
    int iterWord = iterWord_;
    int iterBit = iterBit_;
    
    debug(2, "hasNext = ", bitmapToAddress(iterPage, iterSlot, iterWord, iterBit));
    debug(2, "<", iterEnd_);

    // Search starts at this location 
    if (strict) {
      if (++iterBit == BITS_IN_INT) {
        iterBit = 0;
        if (++iterWord == INTS_IN_BITMAP) {
          iterWord = 0;
          if (++iterSlot == BITMAPS_IN_PAGEMAP) {
            iterSlot = 0;
            iterPage++;
          }
        }
      }
    }

    // find next set bit
    for ( ; iterPage <= iterPageEnd_ && iterPage < PAGEMAPS_IN_OBJECTMAP; iterPage++) {
      if (emptyPage(iterPage)) {
        iterSlot = 0;
        iterWord = 0;
        iterBit = 0;
        continue;
      }
      for ( ; iterSlot < BITMAPS_IN_PAGEMAP; iterSlot++) {
        if (emptyBitmap(iterPage, iterSlot)) {
          iterWord = 0;
          iterBit = 0;
          continue;
        }
        for ( ; iterWord < INTS_IN_BITMAP; iterWord++) {
          int val = getBitmapInt(iterPage, iterSlot, iterWord);
          if (val == 0) {
            iterBit = 0;
            continue;
          }
          for ( ; iterBit < BITS_IN_INT; iterBit++) {
            int mask = 1 << iterBit;
            if ((val & mask) != 0) {
              // found next set word
              hasNext_ = bitmapToAddress(iterPage, iterSlot, iterWord, iterBit).LT(iterEnd_);
	      debug(2, "hasNext = ", bitmapToAddress(iterPage, iterSlot, iterWord, iterBit));
	      debug(2, "<", iterEnd_);
              iterPage_ = iterPage;
              iterSlot_ = iterSlot;
              iterWord_ = iterWord;
              iterBit_ = iterBit;
              return;
            }
          }
          iterBit = 0;
        }
        iterWord = 0;
      }
      iterSlot = 0;
    }
    iterPage_ = iterPage;
    iterSlot_ = iterSlot;
    iterWord_ = iterWord;
    iterBit_ = iterBit;
    hasNext_ = false;
    debug(2, "hasNext = false");
  }



  ////////////////////////////////////////////////////////////////////////////
  // Address conversion
  //
  
  /**
   * Convert address of object or chunk to index of page in the
   * object map.
   *
   * @param addr The address of the object
   * @return The address of the slot
   */
  private static final int addressToPage(Address addr) //throws InlinePragma 
  {
    if (VM_Interface.VerifyAssertions) {    
      boolean inHeap = VMResource.refInVM(addr);
      if (!inHeap) 
        debugln(0, "Bad address: ", addr);
      VM_Interface._assert(inHeap, "Address is outside the heap");
      VM_Interface._assert(((addr.toWord().rshl(PAGE_OFFSET).toInt()) < PAGEMAPS_IN_OBJECTMAP));
    }
      
    return addr.toWord().rshl(PAGE_OFFSET).toInt();
  }

  /**
   * Convert address of object or chunk to address of slot in the
   * object map.
   *
   * @param addr The address of the object
   * @return The address of the slot
   */
  private static final int addressToSlot(Address addr) //throws InlinePragma 
  {
    if (VM_Interface.VerifyAssertions) {    
      boolean inHeap = VMResource.refInVM(addr);
      if (!inHeap) 
        debugln(0, "Bad address: ", addr);
      VM_Interface._assert(inHeap, "Address is outside the heap");
    }   
    return (addr.toWord().rshl(SLOT_OFFSET).toInt() & (BITMAPS_IN_PAGEMAP - 1));
  }
  
  /**
   * Get the number of the word in a bitmap corresponding to this address
   *
   * @param addr The address of the object
   * @return The number of the bitmap word containing this address
   */
  private static final int addressToWord(Address addr) //throws InlinePragma 
  {
    return (addr.toWord().rshl(WORD_OFFSET).toInt() & (INTS_IN_BITMAP - 1));
  }
  
  /**
   * Get the number of the bit corresponding to this address
   *
   * @param addr The address of the object
   * @return The bit
   */
  private static final int addressToBit(Address addr)  //throws InlinePragma 
  {
    return (addr.toWord().rshl(LOG_OBJECT_ALIGNMENT).toInt() & (BITS_IN_INT - 1));
  }

  /**
   * Recover an address from a bit set in the object map structure
   *
   * @param page The page number in the objectMap_
   * @param slot The slot number in that page
   * @param word The word number in the bitmap
   * @param bit The number of the bit in the bit mask
   */
  private static Address bitmapToAddress(int page, int slot, int word, int bit) {
    Word pageAddr  = Word.fromIntZeroExtend(page).lsh(PAGE_OFFSET);
    Word chunkAddr  = Word.fromIntZeroExtend(slot).lsh(SLOT_OFFSET);
    Word wordAddr  = Word.fromIntZeroExtend(word).lsh(WORD_OFFSET);
    Word bitAddr  = Word.fromIntZeroExtend(bit).lsh(LOG_OBJECT_ALIGNMENT);
    
    Address addr = pageAddr.or(chunkAddr).or(wordAddr).or(wordAddr).or(bitAddr).toAddress();

    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(page == addressToPage(addr));
      VM_Interface._assert(slot == addressToSlot(addr));
      VM_Interface._assert(word == addressToWord(addr));
      VM_Interface._assert(bit == addressToBit(addr));
    }

    return addr;
  }

  ////////////////////////////////////////////////////////////////////////////
  // Debugging
  //

  /**
   * Debugging output
   */
  private static final void debug(int level, String mesg) { 
    if(Options.verbose >= level) 
      Log.write(mesg); 
  }
  private static final void debugln(int level, String mesg) { 
    if(Options.verbose >= level) 
      Log.writeln(mesg); 
  }
  private static final void debug(int level, String mesg, int value) { 
    if(Options.verbose >= level) 
      Log.write(mesg, value); 
  }
  private static final void debugln(int level, String mesg, int value) { 
    if(Options.verbose >= level) 
      Log.writeln(mesg, value); 
  }
  private static final void debug(int level, String mesg, long value) { 
    if(Options.verbose >= level) 
      Log.write(mesg, value); 
  }
  private static final void debugln(int level, String mesg, long value) { 
    if(Options.verbose >= level) 
      Log.writeln(mesg, value); 
  }
  private static final void debug(int level, String mesg, Address value) { 
    if(Options.verbose >= level) 
      Log.write(mesg, value); 
  }
  private static final void debugln(int level, String mesg, Address value) { 
    if(Options.verbose >= level) 
      Log.writeln(mesg, value); 
  }

  public void testTranslation() {
    // trivial example
    Address HIGH_SS_START = Address.fromIntZeroExtend(0x92500000);
    testTranslation(HIGH_SS_START);
    testTranslation(HIGH_SS_START.add(252));
    testTranslation(HIGH_SS_START.sub(744));
    VM_Interface.sysFail("bye from testTranslation"); 
  }
  
  public  void testTranslation(Address testAddr) {
    // test our translations
    Log.writeln("Test address ", testAddr);
    int testPage = addressToPage(testAddr);
    Log.writeln("  page ", testPage);
    int testSlot = addressToSlot(testAddr);
    Log.writeln("  slot ", testSlot);
    int testWord = addressToWord(testAddr);
    Log.writeln("  word ", testWord);
    int testBit = addressToBit(testAddr);
    Log.writeln("  bit  ", testBit);
    Log.writeln("  address ", bitmapToAddress(testPage, testSlot, testWord, testBit));
    Log.writeln("");

    Log.writeln("  allocating at ", testAddr);
    alloc(testAddr);
    Log.writeln("  value written ", getBitmapInt(testPage, testSlot, testWord));
    Log.write("  objectmap_ start ", objectMap_);
    Log.writeln(", end ", objectMap_.add(OBJECTMAP_SIZE));
    Address pmaddr = getPageAddress(testPage);
    Log.write("  page address ", pmaddr);
    Log.writeln(", end ", pmaddr.add(PAGEMAP_SIZE));
    Log.writeln("  slot address ", getSlotAddress(testPage, testSlot));
    Log.writeln("  bitmap address ", getBitmap(testPage, testSlot));
    Log.writeln("  searching around test address ", testAddr);
    iterator(testAddr.sub(1024), testAddr.add(1024));
    while (hasNext()) {
      Address addr = next();
      Log.writeln("  found object at ", addr);
    }
    VMResource.showAll(); 
  }

  /**
   * Dump the object map
   */
  public void dbgObjectMap() {
    int usedPages = 0;
    Log.write("used slots: ");
    while (usedPages < PAGEMAPS_IN_OBJECTMAP) {
      // find first used pagemap
      while (usedPages < PAGEMAPS_IN_OBJECTMAP  && emptyPage(usedPages)) 
        usedPages++;
      
      int usedStart = 0;
      int usedEnd = 0;
      while (usedStart < BITMAPS_IN_PAGEMAP) {
        // find first used bitmap slot
        while (usedStart < BITMAPS_IN_PAGEMAP && emptyBitmap(usedPages, usedStart)) 
          usedStart++;
	if (usedStart >= BITMAPS_IN_PAGEMAP)
	  break;
        usedEnd = usedStart + 1;
        // find first unused bitmap slot
        while (usedEnd <BITMAPS_IN_PAGEMAP  && !emptyBitmap(usedPages, usedEnd)) 
          usedEnd++;
        usedEnd--;
	Log.write(usedPages);
	Log.write(":",usedStart);
	Log.write("-", usedEnd);
	Log.write(" ");
	usedStart = usedEnd + 1;
      }
      usedPages++;
    }
    Log.write("\n");
  }
  

  /**
   * Allow/disallow allocation
   * This just a test to check that no allocation is done in the Java heap
   *
   * @param trap true=trap allocation
   * @return The old value
   */
  public boolean trapAllocation(boolean trap) {
    boolean oldTrap = allocationTrap;
    allocationTrap = trap;
    return oldTrap;
  }


  /**
   * Write the location of a (slot, word, bit) triple
   * 
   * @param page The page
   * @param slot The slot
   * @param word The word
   * @param bit The bit
   */           
  private static final void writeLocation(int page, int slot, int word, int bit) {
    Log.write('<', page); 
    Log.write(',', slot); 
    Log.write(',', word); 
    Log.write(',', bit);
    Log.write('>');
  }
      
//-#else
  public ObjectMap() {}
  public final void boot() {}
  public final void release(Address chunkAddr, int bytes) {}
  public final void release(Address chunkAddr) {}
  public final void alloc(Address addr) {}
  public final void dealloc(Address addr) {}
  public void iterator(Address start, Address end) {}
  public boolean hasNext() { return false; }
  public Address next() { return null; }
  public boolean trapAllocation(boolean trap) { return false; }
//-#endif
}
