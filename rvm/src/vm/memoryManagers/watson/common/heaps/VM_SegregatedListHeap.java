/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * The heap is divided into chunks of size GC_BLOCKSIZE bytes 
 * for objects <= 2048 bytes.  Each chunk is divided into slots 
 * of one of a set of fixed sizes (see GC_SIZEVALUES in VM_GCConstants), 
 * and an allocation is satisfied from a chunk whose size is the smallest
 * that accommodates the request.  Each virtual processor allocates from
 * a private set of chunks to avoid locking on allocations, which
 * occur only when a new chunk is required. <p> 
 * 
 * @author Dick Attanasio
 * @author David F. Bacon
 * @author Perry Cheng
 * @author Dave Grove
 * @author Stephen Smith
 * 
 * @see VM_BlockControl
 * @see VM_SizeControl
 * @see VM_Processor
 */
final class VM_SegregatedListHeap extends VM_Heap
  implements VM_Uninterruptible, VM_GCConstants {

  private static final boolean DEBUG = false;
  private static final boolean COUNT_FAST_ALLOC = false;
  private static final boolean DEBUG_LINK = false;
  private static final boolean GSC_TRACE = false;
  private static final boolean REPORT_BLOCKS = false;
  private static final boolean GCDEBUG_PARTIAL = false; 
  private static final boolean COUNT_ZEROED_BLOCKS = false;

  static final boolean DEBUG_FREEBLOCKS = false; 
  // Debug free space calculation
  private static final boolean GCDEBUG_FREESPACE = false; 

  // Debug free slot management
  private static final boolean DEBUG_FREE = false;

  private final static int OUT_OF_BLOCKS =  -1;

  private int total_blocks_in_use;

  private static final VM_Array byteArrayType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[B"), VM_SystemClassLoader.getVMClassLoader()).asArray();

  // value below is a tuning parameter: for single threaded appl, on multiple processors
  private static final int         numBlocksToKeep = 10;     // GSC 

  private VM_ProcessorLock sysLockFree  = new VM_ProcessorLock();
  private VM_ProcessorLock sysLockBlock = new VM_ProcessorLock();

  // 1 VM_BlockControl per GC-SIZES for initial use, before heap setup
  private VM_BlockControl[]  init_blocks;  // VM_BlockControl[] 1 per BLKSIZE block of the heap

  private VM_BlockControl[]  blocks;       // 1 per BLKSIZE block of the heap

  private int[]       partialBlockList;         // GSC

  // Used when COUNT_FAST_ALLOC is true
  private static int    allocCount;
  private static int    fastAllocCount;

  private int    num_blocks;    // number of blocks in the heap
  private int    first_freeblock;  // number of first available block
  private int    highest_block;    // number of highest available block
  private int    blocks_available;  // number of free blocks for small obj's

  private int[] accum;
  private int[] total;


  /**
   * backing store for heap metadata
   */
  private VM_MallocHeap mallocHeap;

  VM_SegregatedListHeap(String s, VM_MallocHeap mh) {
    super(s);
    mallocHeap = mh;
  }

  /**
   * Setup done during bootimage writing
   */
  public void init (VM_Processor st) throws VM_PragmaInterruptible {
    partialBlockList = new int[GC_SIZES];          // GSC
    for (int i = 0; i<GC_SIZES; i++) {
      partialBlockList[i] = OUT_OF_BLOCKS;
    }

    st.sizes = new VM_SizeControl[GC_SIZES];
    init_blocks = new VM_BlockControl[GC_SIZES];

    // On the jdk side, we allocate an array of VM_SizeControl Blocks, 
    // one for each size.
    // We also allocate init_blocks array within the boot image.  
    // At runtime we allocate the rest of the BLOCK_CONTROLS, whose number 
    // depends on the heapsize, and copy the contents of init_blocks 
    // into the first GC_SIZES of them.

    for (int i = 0; i < GC_SIZES; i++) {
      st.sizes[i] = new VM_SizeControl();
      init_blocks[i] = new VM_BlockControl();
      st.sizes[i].first_block = i;  // 1 block/size initially
      st.sizes[i].current_block = i;
      st.sizes[i].ndx = i;
      init_blocks[i].mark = new byte[GC_BLOCKSIZE/GC_SIZEVALUES[i] ];
      for (int ii = 0; ii < GC_BLOCKSIZE/GC_SIZEVALUES[i]; ii++) {
	init_blocks[i].mark[ii]  = 0;
      }
      init_blocks[i].nextblock= OUT_OF_BLOCKS;
      init_blocks[i].slotsize = GC_SIZEVALUES[i];
    }

    // set up GC_INDEX_ARRAY for this Processor
    st.GC_INDEX_ARRAY = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
    st.GC_INDEX_ARRAY[0] = st.sizes[0];  // for size = 0
    for (int i = 0, j = 1; i < GC_SIZES; i++) {
      for (; j <= GC_SIZEVALUES[i]; j++) {
	st.GC_INDEX_ARRAY[j] = st.sizes[i];
      }
    }

    st.backingSLHeap = this;
  }


  public void boot (VM_Processor st, VM_ImmortalHeap immortalHeap) throws VM_PragmaInterruptible {
    blocks = init_blocks;

    // Now set the beginning address of each block into each VM_BlockControl
    // Note that init_blocks is in the boot image, but heap pages are controlled by it
    for (int i=0; i < GC_SIZES; i++)  {
      init_blocks[i].baseAddr = start.add(i * GC_BLOCKSIZE);
      build_list_for_new_block(init_blocks[i], st.sizes[i]);
    }

    // Now allocate the blocks array - which will be used to allocate blocks to sizes
    
    num_blocks = size / GC_BLOCKSIZE;
    blocks = (VM_BlockControl[]) immortalHeap.allocateArray(VM_BlockControl.ARRAY_TYPE, num_blocks);

    // index for highest page in heap
    highest_block = num_blocks -1;
    blocks_available = highest_block - GC_SIZES;   // available to allocate
  
    // Now fill in blocks with values from blocks_init
    for (int i = 0; i < GC_SIZES; i++) {
      // NOTE: if blocks are identified by index, st.sizes[] need not be changed; if
      // blocks are identified by address, then updates st.sizes[0-GC_SIZES] here
      blocks[i] = init_blocks[i];
    }

    // At this point we have assigned the first GC_SIZES blocks, 
    // 1 per, to each GC_SIZES bin
    // and are prepared to allocate from such, or from large object space:
    // VM_BlockControl blocks are not used to manage large objects - 
    // they are unavailable by special logic for allocation of small objs
    //
    first_freeblock = GC_SIZES;   // next to be allocated
    init_blocks    = null;        // these are currently live through blocks

    // Now allocate the rest of the VM_BlockControls
    for (int i = GC_SIZES; i < num_blocks; i++) {
      VM_BlockControl bc = (VM_BlockControl) immortalHeap.allocateScalar(VM_BlockControl.TYPE);
      blocks[i] = bc;
      bc.baseAddr = start.add(i * GC_BLOCKSIZE); 
      bc.nextblock = (i == num_blocks - 1) ? OUT_OF_BLOCKS : i + 1;
    }
  
    // statistics arrays for blocks usage
    if (REPORT_BLOCKS) {
      total = new int[GC_SIZES];
      accum = new int[GC_SIZES];
    }
  }


  /**
   * Allocate size bytes of raw memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory(int size) {
    if (VM.VerifyAssertions) {
      VM.assert(size <= GC_MAX_SMALL_SIZE);
      VM.assert(VM_Processor.getCurrentProcessor().backingSLHeap == this);
    }
    return allocateFastPath(size);
  }

  /**
   * Hook to allow heap to perform post-allocation processing of the object.
   * For example, setting the GC state bits in the object header.
   */
  protected void postAllocationProcessing(Object newObj) { 
    // nothing to do in this heap
  }


  /**
   * Fast path (inlined) allocation path for SegregatedListHeap.
   * Processor local allocation via fields on the VM_Processor object.
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  public static VM_Address allocateFastPath (int size) throws OutOfMemoryError, VM_PragmaInline {

    if (COUNT_FAST_ALLOC) allocCount++;
    
    // Use magic to avoid spurious array bounds check on common case allocation path.
    // NOTE: This code sequence is carefully written to generate
    //       optimal code when inlined by the optimzing compiler.  
    //       If you change it you must verify that the efficient 
    //       inlined allocation sequence isn't hurt! --dave
    VM_Address loc = VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor().GC_INDEX_ARRAY).add(size << 2);
    VM_Address rs = VM_Magic.getMemoryAddress(loc);
    VM_SizeControl the_size = VM_Magic.addressAsSizeControl(rs);
    VM_Address next_slot = the_size.next_slot;
    if (next_slot.isZero()) {
      // slow path: find a new block to allocate from
      return VM_Processor.getCurrentProcessor().backingSLHeap.allocateSlot(the_size, size); 
    } else {
      // inlined fast path: get slot from block
      return allocateSlotFast(the_size, next_slot); 
    }
  }


  /** 
   * given an address in the heap 
   *  set the corresponding mark byte on
   */
  public boolean  mark(VM_Address ref) {
    VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);
    int blkndx  = (tref.diff(start)) >> LOG_GC_BLOCKSIZE ;
    VM_BlockControl  this_block = blocks[blkndx];
    int  offset   = tref.diff(this_block.baseAddr); 
    int  slotndx  = offset/this_block.slotsize;
    
    if (this_block.mark[slotndx] != 0) return false;
    
    if (false) {
      // store byte into byte array
      this_block.mark[slotndx] = 1;
    } else {
      byte tbyte = (byte)1;
      int  temp, temp1;
      do  {
	// get word with proper byte from map
	temp1 = VM_Magic.prepare(this_block.mark, ((slotndx>>2)<<2));
	if (this_block.mark[slotndx] != 0) return false;
	//-#if RVM_FOR_IA32    
	int index = slotndx%4; // get byte in word - little Endian
	//-#else 
	int index = 3 - (slotndx%4); // get byte in word - big Endian
	//-#endif
	int mask = tbyte << (index * 8); // convert to bit in word
	temp  = temp1 | mask;        // merge into existing word
      }  while (!VM_Magic.attempt(this_block.mark, ((slotndx>>2)<<2), temp1, temp));
    }

    this_block.live  = true;
    return  true;
  }


  public boolean isLive(VM_Address ref) {
    VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);
    //  check for live small object
    int  blkndx, slotno, size, ij;
    blkndx  = (tref.diff(start)) >> LOG_GC_BLOCKSIZE ;
    VM_BlockControl  this_block = blocks[blkndx];
    int  offset   = tref.diff(this_block.baseAddr);
    int  slotndx  = offset/this_block.slotsize;
    return (this_block.mark[slotndx] != 0);
  }


  /**
   * Allocate a fixed-size small chunk when we know one is available.  Always inlined.
   * 
   * @param the_size Header record for the given slot size
   * @param next_slot contents of the_size.next_slot
   * @return Address of free, zero-filled storage
   */
  protected static VM_Address allocateSlotFast(VM_SizeControl the_size, 
					       VM_Address next_slot) throws OutOfMemoryError, VM_PragmaInline {

    if (COUNT_FAST_ALLOC) fastAllocCount++;

    // Get the next object from the head of the list
    VM_Address objaddr = next_slot;
    if (DEBUG_LINK) {
      if (objaddr.isZero()) VM.sysFail("the_size.next_slot is zero");
      VM_SegregatedListHeap bh = VM_Processor.getCurrentProcessor().backingSLHeap;
      if (!bh.addrInHeap(objaddr)) VM.sysFail("Bad ptr");
      if (!bh.isPtrInBlock(objaddr, the_size)) VM.sysFail("Pointer out of block");
    }

    // Update the next pointer
    the_size.next_slot = VM_Magic.getMemoryAddress(objaddr);
    if (DEBUG_LINK && (!the_size.next_slot.isZero())) {
      VM_SegregatedListHeap bh = VM_Processor.getCurrentProcessor().backingSLHeap;
      if (!bh.addrInHeap(the_size.next_slot)) VM.sysFail("Bad ptr");
      if (!bh.isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
    }

    // Zero out the old next pointer 
    // NOTE: Possible MP bug on machines with relaxed memory models....
    //       technically we need a store barrier after we zero this word!
    VM_Magic.setMemoryWord(objaddr, 0);

    // Return zero-filled storage
    return objaddr;
  }


  /**
   * Allocate a fixed-size small chunk when we have run off the end of the 
   * current block.  This will either use a partially filled block of the 
   * given size, or a completely empty block, or it may trigger GC.
   *   @param the_size Header record for the given slot size
   *   @param size Size in bytes to allocate
   *   @return Address of free, zero-filled storage
   */
  protected VM_Address allocateSlot(VM_SizeControl the_size, int size) throws OutOfMemoryError, VM_PragmaNoInline {

    int count = 0;
    while(true) {
      VM_Address objaddr = allocateSlotFromBlocks(the_size, size);
      if (!objaddr.isZero()) return objaddr;

      // Didn't get any memory; ask allocator to deal with this
      // This may not return (VM exits with OutOfMemoryError)
      VM_Allocator.heapExhausted(this, size, count++);

      // An arbitrary amount of time may pass until this thread runs again,
      // and in fact it could be running on a different virtual processor.
      // Therefore, reacquire the processor local size control.
      // reset the_size in case we are on a different processor after GC
      the_size  = VM_Processor.getCurrentProcessor().GC_INDEX_ARRAY[size];

      // At this point allocation might or might not succeed, since the
      // thread which originally requested the collection is usually not
      // the one to run after gc is finished; therefore failing here is
      // not a permanent failure necessarily
      VM_Address next_slot = the_size.next_slot;
      if (!next_slot.isZero())  // try fast path again
	return allocateSlotFast(the_size, next_slot);
    }
  }


  /**
   * Find a new block to use for the given slot size, format the 
   * free list, and allocate an object from that list.  First tries to
   * allocate from the processor-local list for the given size, then from 
   * the global list of partially filled blocks of the given size, and
   * finally tries to get an empty block and format it to the given size.
   *   @param the_size Header record for the given slot size
   *   @param size Size in bytes to allocate
   *   @return Address of free storage or 0 if none is available
   */
  protected VM_Address allocateSlotFromBlocks (VM_SizeControl the_size, int size) {
    VM_BlockControl the_block = blocks[the_size.current_block];

    // First, look for a slot in the blocks on the existing list
    while (the_block.nextblock != OUT_OF_BLOCKS) {
      the_size.current_block = the_block.nextblock;
      the_block = blocks[the_block.nextblock];
      if (build_list(the_block, the_size))
	return allocateSlotFast(the_size, the_size.next_slot);
    }
    
    // Next, try to get a partially filled block of the given size from the global pool
    while (getPartialBlock(the_size.ndx) == 0) {
      if (GSC_TRACE) {
	VM_Processor.getCurrentProcessor().disableThreadSwitching();
	VM.sysWrite("allocatex: adding partial block: ndx "); VM.sysWrite(the_size.ndx,false);
	VM.sysWrite(" current was "); VM.sysWrite(the_size.current_block,false);
	VM.sysWrite(" new current is "); VM.sysWrite(the_block.nextblock,false);
	VM.sysWrite("\n");
	VM_Processor.getCurrentProcessor().enableThreadSwitching();
      }

      the_size.current_block = the_block.nextblock;
      the_block = blocks[the_block.nextblock];

      if (build_list(the_block, the_size))
	return allocateSlotFast(the_size, the_size.next_slot);

      if (GSC_TRACE) {
	VM_Processor.getCurrentProcessor().disableThreadSwitching();
	VM.sysWrite("allocatey: partial block was full\n");
	VM_Processor.getCurrentProcessor().enableThreadSwitching();
      }
    }

    // Finally, try to allocate a free block and format it to the given size
    if (getnewblock(the_size.ndx) == 0) {
      the_size.current_block = the_block.nextblock;
      int idx = the_size.current_block;
      if (idx < 0 || idx >= blocks.length) {
	VM.sysWriteln("idx out of range ",idx);
	VM.sysWriteln("; len is ",blocks.length);
	VM.sysWriteln("Processor ", VM_Processor.getCurrentProcessor().id);
	VM.sysFail("killing VM");
      }
      build_list_for_new_block(blocks[idx], the_size);
      return allocateSlotFast(the_size, the_size.next_slot);
    }
  
    // All attempts failed; heap is currently exhausted.
    return VM_Address.zero();
  }


  // build, in the block, the list of free slot pointers, and update the
  // associated VM_SizeControl.next_slot; return true if a free slot was found,
  // or false if not
  //
  protected boolean build_list (VM_BlockControl the_block, VM_SizeControl the_size) {
    byte[] the_mark = the_block.mark;
    int first_free = 0, i = 0, j;
    VM_Address current, next;
    
    if (VM.VerifyAssertions && the_mark == null) {
      VM.sysWriteln("mark = ", VM_Magic.objectAsAddress(the_mark));
      VM.sysWriteln("size = ", GC_SIZEVALUES[the_size.ndx]);
    }

    if (VM.VerifyAssertions) VM.assert(the_mark != null);

    for (; i < the_mark.length ; i++) {
      if (the_mark[i] == 0) break;
    }

    if (i == the_mark.length) { 
      // no free slot was found
      if (DEBUG_LINK) 
	VM_Scheduler.trace("build_list: ", "found a full block", the_block.slotsize);

      // Reset control info for this block, for next collection 
      VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		     VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
      the_block.live = false;
      return false;  // no free slots in this block
    } else {  
      // here is the first
      current = the_block.baseAddr.add(i * the_block.slotsize);
    }
    
    VM_Memory.zero(current.add(4), current.add(the_block.slotsize));
    the_size.next_slot = current;
    if (DEBUG_LINK && (!the_size.next_slot.isZero())) {
      if (!addrInHeap(the_size.next_slot)) VM.sysFail("Bad ptr");
      if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
    }

    // now find next free slot
    i++;   
    for (; i < the_mark.length ; i++) {
      if (the_mark[i] == 0) break;
    }

    if (i == the_mark.length) {    
      // this block has only 1 free slot, so..
      VM_Magic.setMemoryWord(current, 0);  // null pointer to next

      if (DEBUG_LINK) {
	VM_Scheduler.trace("build_list: ", "found blk w 1 free slot", the_block.slotsize);
	do_check(the_block, the_size);
      }

      // Reset control info for this block, for next collection 
      VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		     VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
      the_block.live = false;
      return true;
    }

    next = the_block.baseAddr.add(i * the_block.slotsize);
    VM_Magic.setMemoryAddress(current, next);
    current = next; 
    VM_Memory.zero(current.add(4), current.add(the_block.slotsize));

    // build the rest of the list; there is at least 1 more free slot
    for (i = i + 1; i < the_mark.length ; i++) {
      if (the_mark[i] == 0) {  // This slot is free
	next = the_block.baseAddr.add(i * the_block.slotsize);
	VM_Magic.setMemoryAddress(current, next);  // enter list pointer
	current = next;
	VM_Memory.zero(current.add(4), current.add(the_block.slotsize));
      }
    }

    VM_Magic.setMemoryWord(current,0);    // set the end of the list
    if (DEBUG_LINK) do_check(the_block, the_size);
    // Reset control info for this block, for next collection 
    VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		   VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
    the_block.live = false;
    return true;
  }


  // A debugging routine: called to validate the result of build_list 
  // and build_list_for_new_block
  // 
  protected void do_check (VM_BlockControl the_block, VM_SizeControl the_size) {
    int count = 0;
    if (blocks[the_size.current_block] != the_block) {
      VM_Scheduler.trace("do_check", "BlockControls don't match");
      VM.sysFail("BlockControl Inconsistency");
    }
    if (the_size.next_slot.isZero()) VM_Scheduler.trace("do_check", "no free slots in block");
    VM_Address temp = the_size.next_slot;
    while (!temp.isZero()) {
      if ((temp.LT(the_block.baseAddr)) || (temp.GT(the_block.baseAddr.add(GC_BLOCKSIZE))))  {
	VM_Scheduler.trace("do_check: TILT:", "invalid slot ptr", temp);
	VM.sysFail("Bad freelist");
      }
      count++;
      temp = VM_Magic.getMemoryAddress(temp);
    }
	
    if (count > the_block.mark.length)  {
      VM_Scheduler.trace("do_check: TILT:", "too many slots in block");
      VM.sysFail("too many slots");
    }
  }
      
  // Input: a VM_BlockControl that was just assigned to a size; the VM_SizeControl
  // associated with the block
  //
  protected void build_list_for_new_block (VM_BlockControl the_block, VM_SizeControl the_size) {
    byte[] the_mark = the_block.mark;
    int i, delta;
    VM_Address current = the_block.baseAddr;
    VM_Memory.zero(current, current.add(GC_BLOCKSIZE));
    delta  = the_block.slotsize;
    the_size.next_slot = current ;  // next one to allocate
    if (DEBUG_LINK && (!the_size.next_slot.isZero())) {
      if (!addrInHeap(the_size.next_slot)) VM.sysFail("Bad ptr");
      if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
    }
    for (i = 0; i < the_mark.length -1; i++) {
      VM_Magic.setMemoryAddress(current, current.add(delta));
      current = current.add(delta);
    }
    // last slot does not point forward - already zeroed
    //  
    if (DEBUG_LINK) do_check(the_block, the_size);

    // Reset control info for this block, for next collection 
    VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		   VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
    the_block.live = false;
  }
  

  // A routine to obtain a free VM_BlockControl and return it
  // to the caller.  First use is for the VM_Processor constructor: 
  protected int getnewblockx (int ndx) {
    sysLockBlock.lock();

    if (first_freeblock == OUT_OF_BLOCKS) {
      VM_Allocator.heapExhausted(this, 0, 0);
    }  
  
    VM_BlockControl alloc_block = blocks[first_freeblock];
    int theblock = first_freeblock;
    first_freeblock = alloc_block.nextblock;
    sysLockBlock.unlock();
    alloc_block.nextblock = OUT_OF_BLOCKS;  // this is last block in list for thissize
    alloc_block.slotsize  = GC_SIZEVALUES[ndx];
    int size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;    

    if (alloc_block.mark != null)  {
      if (size <= alloc_block.alloc_size) {
	VM_ObjectModel.setArrayLength(alloc_block.mark, size);
	return theblock;
      } else {    // free the existing array space
	mallocHeap.atomicFreeArray(alloc_block.mark);
      }
    }
    // allocate a mark array from the malloc heap.
    alloc_block.mark = VM_Magic.objectAsByteArray(mallocHeap.atomicAllocateArray(byteArrayType, size));
    return theblock;
  }


  protected int getPartialBlock (int ndx) {
    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_SizeControl this_size = st.sizes[ndx];
    VM_BlockControl currentBlock = blocks[this_size.current_block];

    sysLockBlock.lock();

    if (partialBlockList[ndx] == OUT_OF_BLOCKS) {
      //      if (GSC_TRACE) {
      //  VM_Processor.getCurrentProcessor().disableThreadSwitching();
      //  VM.sysWrite("getPartialBlock: ndx = "); VM.sysWrite(ndx,false);
      //  VM.sysWrite(" returning -1\n");
      //  VM_Processor.getCurrentProcessor().enableThreadSwitching();
      //      }
      sysLockBlock.release();
      return -1;
    }

    // get first partial block of same slot size
    //
    currentBlock.nextblock = partialBlockList[ndx];
    VM_BlockControl allocBlock = blocks[partialBlockList[ndx]];

    partialBlockList[ndx] = allocBlock.nextblock;
    allocBlock.nextblock = OUT_OF_BLOCKS;

    if (GSC_TRACE) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("getPartialBlock: ndx = "); VM.sysWrite(ndx,false);
      VM.sysWrite(" allocating "); VM.sysWrite(currentBlock.nextblock);
      VM.sysWrite(" baseAddr "); VM.sysWrite(allocBlock.baseAddr);
      VM.sysWrite("\n");
      if (VM.VerifyAssertions) VM.assert(allocBlock.slotsize==GC_SIZEVALUES[ndx]);
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }

    sysLockBlock.unlock();
    return 0;
  } 

 
  protected int getnewblock (int ndx) {
    int i, save, size;
    VM_Processor st = VM_Processor.getCurrentProcessor();

    VM_SizeControl this_size = st.sizes[ndx];
    VM_BlockControl alloc_block = blocks[this_size.current_block];

    sysLockBlock.lock();
    if (first_freeblock == OUT_OF_BLOCKS) {
      if (verbose >= 1)
	VM_Scheduler.trace(" gc collection triggered by getnewblock call", "");
      sysLockBlock.release();
      return -1;
    }

    alloc_block.nextblock = first_freeblock;
    alloc_block = blocks[first_freeblock];
    first_freeblock = alloc_block.nextblock;  // new first_freeblock
    sysLockBlock.unlock();

    alloc_block.nextblock = OUT_OF_BLOCKS;  // this is last block in list for thissize
    alloc_block.slotsize  = GC_SIZEVALUES[ndx];
    size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;  

    // on first assignment of this block, get space from mallocHeap
    // for alloc array, for the size requested.  
    // If not first assignment, if the existing array is large enough for 
    // the new size, use them; else free the existing one, and get space 
    // for new one.  Keep the size for the currently allocated array in
    // alloc_block.alloc_size.  This value only goes up during the running
    // of the VM.

    if (alloc_block.mark != null) {
      if (size <= alloc_block.alloc_size) {
	VM_ObjectModel.setArrayLength(alloc_block.mark, size);
	return 0;
      } else {    // free the existing make array
	mallocHeap.atomicFreeArray(alloc_block.mark);
      }
    }

    alloc_block.mark = VM_Magic.objectAsByteArray(mallocHeap.atomicAllocateArray(byteArrayType, size));
    return 0;
  }

  protected int getndx(int size) {
    if (size <= GC_SIZEVALUES[0]) return 0;  // special case most common
    if (size <= GC_SIZEVALUES[1]) return 1;  // special case most common
    if (size <= GC_SIZEVALUES[2]) return 2;  // special case most common
    if (size <= GC_SIZEVALUES[3]) return 3;  // special case most common
    if (size <= GC_SIZEVALUES[4]) return 4;  // special case most common
    if (size <= GC_SIZEVALUES[5]) return 5;  // special case most common
    if (size <= GC_SIZEVALUES[6]) return 6;  // special case most common
    if (size <= GC_SIZEVALUES[7]) return 7;  // special case most common
    for (int i =8; i < GC_SIZES; i++) 
      if (size <= GC_SIZEVALUES[i]) return i;
    return -1;
  }


  //  a debugging routine: to make sure a pointer is into the give block
  protected  boolean isPtrInBlock (VM_Address ptr, VM_SizeControl the_size) {
    VM_BlockControl  the_block = blocks[the_size.current_block]; 
    VM_Address base = the_block.baseAddr;
    int  offset = ptr.diff(base);
    VM_Address  endofslot = ptr.add(the_block.slotsize);
    if (offset%the_block.slotsize != 0) VM.sysFail("Ptr not to beginning of slot");
    VM_Address  bound = base.add(GC_BLOCKSIZE);
    return ptr.GE(base) && endofslot.LE(bound);
  }


  void dumpblocks (VM_Processor st)  {
    VM.sysWriteln("\n-- Processor ", st.id, " --");
    for (int i = 0; i < GC_SIZES; i++) {
      VM.sysWrite(" Size ", GC_SIZEVALUES[i], "  ");
      VM_BlockControl the_block = blocks[st.sizes[i].first_block];
      VM.sysWrite(st.sizes[i].first_block, false);
      while (true) {
	VM.sysWrite("  ", the_block.nextblock);
	if (the_block.nextblock == OUT_OF_BLOCKS) break;
	the_block = blocks[the_block.nextblock];
      }		
      VM.sysWriteln();
    }
  }


  void dumpblocks () {
    VM_Processor  st = VM_Processor.getCurrentProcessor();
    VM.sysWrite(first_freeblock, "  is the first freeblock index \n");
    for (int i = 0; i < GC_SIZES; i++) {
      VM.sysWrite(i, "th VM_SizeControl first_block = ", st.sizes[i].first_block);
      VM.sysWrite(" current_block = ", st.sizes[i].current_block, "\n\n");
    }
    
    for (int i = 0; i < num_blocks; i++) {
      VM.sysWriteln(i, "th VM_BlockControl   ", (blocks[i].live) ? "   live  " : "not live  ");
      VM.sysWriteln("baseaddr = ", blocks[i].baseAddr);
      VM.sysWriteln("nextblock = ", blocks[i].nextblock);
    }
  } 

  void clobber (VM_Address addr, int length) {
    int value = 0xdeaddead;
    int i;
    for (i = 0; i + 3 < length; i = i+4) 
      VM_Magic.setMemoryWord(addr.add(i), value);
  }


  void clobberfree () {
    VM_Processor  st = VM_Processor.getCurrentProcessor();
    for (int i = 0; i < GC_SIZES; i ++) {
      VM_BlockControl this_block = blocks[st.sizes[i].first_block];  
      byte[] this_alloc      = this_block.mark;
      for (int ii = 0; ii < this_alloc.length; ii ++) {
	  if (this_alloc[ii] == 0) clobber(this_block.baseAddr.add(ii * GC_SIZEVALUES[i]), GC_SIZEVALUES[i]);
      }
      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
        this_block = blocks[next];
        this_alloc      = this_block.mark;
        for (int ii = 0; ii < this_alloc.length; ii ++) {
          if (this_alloc[ii] == 0)
            clobber(this_block.baseAddr.add(ii * GC_SIZEVALUES[i]), GC_SIZEVALUES[i]);
        }
        next = this_block.nextblock;
      }
    }

    // Now clobber the free list
    sysLockBlock.lock();
    VM_BlockControl block;
    for (int freeIndex = first_freeblock; freeIndex != OUT_OF_BLOCKS; freeIndex = block.nextblock) {
      block = blocks[freeIndex];
      clobber(block.baseAddr, GC_BLOCKSIZE);
    }
    sysLockBlock.unlock();
  }



  public long freeMemory () {
    return freeBlocks() * GC_BLOCKSIZE;
  }

  public long partialBlockFreeMemory() {
    if (VM_Allocator.verbose >= 2) VM.sysWrite("WARNING: partialBlockFreeMemory not implemented; returning 0\n");
    return 0;
  }

  protected int emptyOfCurrentBlock(VM_BlockControl the_block, VM_Address current_pointer) {
    int sum = 0;
    while (!current_pointer.isZero()) {
      sum += the_block.slotsize;
      current_pointer = VM_Magic.getMemoryAddress(current_pointer);
    }
    return sum;
  }


  //  calculate the number of free bytes in a block of slotsize size
  protected  int emptyof (int size, byte[] alloc) {
    int  total = 0;
    for (int i = 0; i < alloc.length; i++) {
      if (alloc[i] == 0) total += GC_SIZEVALUES[size];
    }
    return  total;
  }

  // Count all VM_blocks in the chain from the input to the end
  //
  protected int blocksInChain(VM_BlockControl the_block) {
    int next = the_block.nextblock;
    int count = 1;
    while (next != OUT_OF_BLOCKS) {
      if (GCDEBUG_FREESPACE) VM_Scheduler.trace(" In blocksinChain", "next = ", next); 
      count++; 
      the_block = blocks[next]; 
      next = the_block.nextblock;
    }
    return count;
  }

  // Count the blocks in the size_control input from first to current
  // 
  protected int blocksToCurrent (VM_SizeControl the_size) {
    int count = 1;
    if (the_size.first_block == the_size.current_block) return 1;
    VM_BlockControl the_block = blocks[the_size.first_block];	
    int next = the_block.nextblock;
    while (next != the_size.current_block) {
      if (GCDEBUG_FREESPACE) {
	VM_Scheduler.trace(" In blocksToCurrent", "next = ", next);
	VM_Scheduler.trace("                   ", "firs = ", the_size.first_block);
        VM_Scheduler.trace("                   ", "curr = ", the_size.current_block);
      }
      count++;
      the_block = blocks[next];
      next = the_block.nextblock;
    }
    return count;
  }
	

  void setupProcessor (VM_Processor st) throws VM_PragmaInterruptible {
    VM_Array scArrayType = VM_SizeControl.TYPE.getArrayTypeForElementType();
    int scArraySize = scArrayType.getInstanceSize(GC_SIZES);
    int scSize = VM_SizeControl.TYPE.getInstanceSize();
    int regionSize = scArraySize + scSize * GC_SIZES;

    // Allocate objects for processor-local meta data from backing malloc heap.
    VM.disableGC();
    VM_Address region = mallocHeap.allocateZeroedMemory(regionSize);
    st.sizes = 
      (VM_SizeControl[])VM_ObjectModel.initializeArray(region, 
						       scArrayType.getTypeInformationBlock(), 
						       GC_SIZES, scArraySize);
    region = region.add(scArraySize);
    for (int i = 0; i < GC_SIZES; i++) {
      st.sizes[i] = 
	(VM_SizeControl)VM_ObjectModel.initializeScalar(region, 
							VM_SizeControl.TYPE.getTypeInformationBlock(), 
							scSize);
      region = region.add(scSize);
    }

    regionSize = scArrayType.getInstanceSize(GC_MAX_SMALL_SIZE + 1);
    region = mallocHeap.allocateZeroedMemory(regionSize);
    st.GC_INDEX_ARRAY = 
      (VM_SizeControl[])VM_ObjectModel.initializeArray(region, 
						       scArrayType.getTypeInformationBlock(), 
						       GC_MAX_SMALL_SIZE + 1, 
						       regionSize);

    VM.enableGC();

    // Finish setting up the size controls and index arrays
    for (int i = 0; i<GC_SIZES; i++) {
      int ii = getnewblockx(i);
      st.sizes[i].first_block = ii;   // 1 block/size initially
      st.sizes[i].current_block = ii;
      st.sizes[i].ndx = i;    // to fit into old code
      build_list_for_new_block(blocks[ii], st.sizes[i]);
    }
	
    st.GC_INDEX_ARRAY[0]  = st.sizes[0];  // for size = 0
    int  j = 1;
    for (int i = 0; i < GC_SIZES; i++) {
      for (; j <= GC_SIZEVALUES[i]; j++) { 
	st.GC_INDEX_ARRAY[j] = st.sizes[i];
      }
    }
    
    st.backingSLHeap = this;
  }
      

  // This routine scans all blocks for all sizes for all processors,
  // beginning with current block; at the end of gc, this gives a
  // correct count of allocated blocks; at the beginning of gc, it
  // does not, since the scan starts from current-block always.(at
  // the end, current_block == first_block
  //
  void freeSmallSpaceDetails (boolean block_count) {
    if (DEBUG_FREEBLOCKS || VM_Allocator.verbose >= 1) {
      int next;
      int blocks_in_use = 0, blocks_in_partial = 0;
      for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	VM_Processor st = VM_Scheduler.processors[i];
	VM.sysWriteln(" \nDetails of small space usage: Processor", i);
	blocks_in_use = blocks_in_use + freeSmallSpaceDetail(st);
      }
      if (block_count) {
	VM.sysWrite("\n Blocks allocated to procs = ", blocks_in_use);
	for (int i = 0; i < GC_SIZES; i++) {
	  if (partialBlockList[i] == OUT_OF_BLOCKS) continue;
	  VM_BlockControl this_block = blocks[partialBlockList[i]];
	  if (this_block == null) continue;
	  blocks_in_partial++;
	  next = this_block.nextblock;
	  while (next != OUT_OF_BLOCKS) {
	    blocks_in_partial++;
	    this_block = blocks[next];
	    next = this_block.nextblock;
	  }
	}
	VM.sysWriteln("\nBlocks partial lists = ", blocks_in_partial);
	VM.sysWrite("Total blocks not free = ", blocks_in_use + blocks_in_partial);
	VM.sysWriteln("Total blocks in sys = ", num_blocks);
      }
      VM.sysWriteln("Number of Free Blocks is ", freeBlocks());
      VM.sysWriteln(blocks_available, "  blocks are available");
    }
    
    if (COUNT_FAST_ALLOC) {
      VM.sysWrite(allocCount, "  total allocations \n");
      VM.sysWrite(fastAllocCount, "  total fastAllocations \n");
    }
  }
			
  protected int freeSmallSpaceDetail (VM_Processor st) {
    int blocks_in_use = 0;
    for (int i = 0; i < GC_SIZES; i++) {
      VM_BlockControl this_block = blocks[st.sizes[i].first_block];
      blocks_in_use++;
      int temp = emptyOfCurrentBlock(this_block, st.sizes[i].next_slot);
      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
	this_block = blocks[next];
	blocks_in_use++;
	temp += emptyof(i, this_block.mark);
	next = this_block.nextblock;
      }
      VM.sysWrite(GC_SIZEVALUES[i]," sized slots have ");
      VM.sysWrite(temp/GC_SIZEVALUES[i]," slots free in ");
      VM.sysWriteln(blocksInChain(blocks[st.sizes[i].first_block]), " allocated blocks");
    }
    return blocks_in_use;
  }

  int freeBlocks () {
    sysLockBlock.lock();
    if (first_freeblock == OUT_OF_BLOCKS) {
      sysLockBlock.unlock();
      return 0;
    }
    VM_BlockControl the_block = blocks[first_freeblock];
    int i = 1;
    int next = the_block.nextblock;
    while (next != OUT_OF_BLOCKS) {
      the_block = blocks[next];
      i++;
      next = the_block.nextblock;
    }
    sysLockBlock.unlock();
    
    return i;
  }

  void postCollectionReport() {
    if (REPORT_BLOCKS) reportBlocks();
    if (DEBUG_FREEBLOCKS) freeSmallSpaceDetails(true);
  }

      
  void reportBlocks() {
    if (!REPORT_BLOCKS) return;
    for (int j = 0; j < GC_SIZES; j++) 
      total[j] = 0;  
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      VM.sysWriteln(" Processor ", i);
      VM_Processor st = VM_Scheduler.processors[i];
      for (int j = 0; j < GC_SIZES; j++) {
	VM_SizeControl the_size = st.sizes[j];
	accum[j] = 1;		// count blocks allocated to this size
	VM_BlockControl the_block = blocks[the_size.first_block];
	int next = the_block.nextblock;
	while (next != OUT_OF_BLOCKS) {
	  accum[j]++;
	  the_block = blocks[next];
	  next = the_block.nextblock;
	}
	total[j] += accum[j];
	VM.sysWrite(" blocksize = ", GC_SIZEVALUES[j]);
	VM.sysWriteln(" allocated blocks = ", accum[j]);
	accum[j] = 0;
      }
    }	// all processors
    VM.sysWriteln();
    int sum = 0;
    for (int j = 0; j < GC_SIZES; j++) {
      VM.sysWrite(" blocksize = ", GC_SIZEVALUES[j]);
      VM.sysWriteln(" total allocated blocks = ", total[j]);
      sum += total[j];
    }
    
    VM.sysWrite(" Total blocks allocated = ", sum);
    VM.sysWrite(" total blocks in system = ", num_blocks);
    VM.sysWriteln(" available blocks = ", blocks_available);
  }
     

  /**
   * Done by 1 collector thread at start of collection
   */
  void startCollect() {

    blocks_available = 0;    // not decremented during allocation

    if (GSC_TRACE) VM.sysWrite("\nZeroing partial block mark arrays\n");

    for (int i = 0; i < GC_SIZES; i++) {
      int counter = 0;
      int index = partialBlockList[i];
      while ( index != OUT_OF_BLOCKS ) {
	if (GSC_TRACE) counter++;
	VM_BlockControl this_block = blocks[index];
	VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark).add(this_block.mark.length));
	this_block.live = false;
	index = this_block.nextblock;
      }
      if (GSC_TRACE) {
	VM.sysWrite(" size = "); VM.sysWrite(i,false);
	VM.sysWrite(" first = "); VM.sysWrite(partialBlockList[i],false);
	VM.sysWrite(" count = "); VM.sysWrite(counter,false); VM.sysWrite("\n");
      }
    }
  }


  void zeromarks(VM_Processor st) {
    int block_count = 0;
    for (int i = 0; i < GC_SIZES; i++) {
      
      //  NEED TO INITIALIZE THE BLOCK AFTER CURRENT_BLOCK, FOR
      //  EACH SIZE, SINCE THIS WAS NOT DONE DURING MUTATOR EXECUTION
      VM_BlockControl this_block = blocks[st.sizes[i].current_block];

      int next = this_block.nextblock;
      if (COUNT_ZEROED_BLOCKS) block_count++;
      while (next != OUT_OF_BLOCKS) {
        if (COUNT_ZEROED_BLOCKS) block_count++;
        this_block = blocks[next];
        if (DEBUG && (this_block.mark == null))
          VM.sysWrite(" In collect, found block with no mark \n");
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark).add(this_block.mark.length));
        this_block.live = false;
        next = this_block.nextblock;
      }
    }

    if (COUNT_ZEROED_BLOCKS) VM_Scheduler.trace(" Blocks zeroed", " = ", block_count);
  }


  void setupallocation(VM_Processor st) {
    for (int i = 0; i < GC_SIZES; i++) {
      VM_BlockControl this_block = blocks[st.sizes[i].first_block];
      VM_SizeControl this_size  = st.sizes[i];
      // begin scan in 1st block again
      this_size.current_block = this_size.first_block;
      if (!build_list(this_block, this_size)) this_size.next_slot = VM_Address.zero();
    }
  }


  /**
   * Parallel sweep
   */
  public void sweep(VM_CollectorThread mylocal) {
    // Following local variables for free_block logic
    int local_first_free_ndx = OUT_OF_BLOCKS; 
    int local_blocks_available = 0; 
    int temp;
    VM_BlockControl local_first_free_block = null;

    VM_Processor st = VM_Processor.getCurrentProcessor();
    for (int i = 0; i < GC_SIZES; i++) {
      VM_BlockControl this_block = blocks[st.sizes[i].first_block];
      VM_SizeControl this_size  = st.sizes[i];
      // begin scan in 1st block again
      this_size.current_block = this_size.first_block; 
      if (!build_list(this_block, this_size)) this_size.next_slot = VM_Address.zero();
      int next = this_block.nextblock;
      this_size.lastBlockToKeep = -1;      // GSC
      int blockCounter = 0;               // GSC
      int counter = 0;
      while (next != OUT_OF_BLOCKS) {

	/*DEBUG CODE - catch loop of blocks */
	if (DEBUG) if (counter++ == 500000) {
	  dumpblocks();  // dump size controls and blocks
	  VM.sysExit(2000);
	}
	    
	VM_BlockControl next_block = blocks[next];
	if (!next_block.live) {
	  if (local_first_free_block == null) {
	    local_first_free_block  = next_block;
	  }
	  //  In this stanza, we make the next's next the next of this_block, and put
	  //  original next on the freelist
	  this_block.nextblock = next_block.nextblock;  // out of live list
	  next_block.nextblock  = local_first_free_ndx;
	  local_first_free_ndx  = next;
	  local_blocks_available++;
	} else {  
	  // found that next block is live
	  if (++blockCounter == numBlocksToKeep) {
	    // GSC
	    this_size.lastBlockToKeep = this_block.baseAddr.diff(start) / GC_BLOCKSIZE;
	  }
	  this_block = next_block;
	}
	next  = this_block.nextblock; 
      }
      // this_block -> last block in list, with next==0. remember its
      // index for possible moving of partial blocks to global lists below
      //
      this_size.last_allocated = this_block.baseAddr.diff(start) / GC_BLOCKSIZE;
    }

    if (DEBUG_FREEBLOCKS) 
      VM_Scheduler.trace(" Found assigned", " freeblocks", local_blocks_available);

    // Now scan through all blocks on the partial blocks list
    // putting empty ones onto the local list of free blocks
    //
    for (int i = mylocal.gcOrdinal - 1; 
	 i < GC_SIZES; 
	 i+= VM_CollectorThread.numCollectors()) {
      if (GCDEBUG_PARTIAL) {
	VM_Scheduler.trace("Allocator: doing partials"," index", i);
	VM_Scheduler.trace("   value in ", "list = ", partialBlockList[i]);
      }
      if (partialBlockList[i] == OUT_OF_BLOCKS) continue;
      VM_BlockControl this_block = blocks[partialBlockList[i]];
      temp = this_block.nextblock;
      while (!this_block.live) {
	local_blocks_available++;
	if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block at",
						" head of partial list", partialBlockList[i]);
	if (local_first_free_block == null) {
	  if (VM.VerifyAssertions) VM.assert(local_first_free_ndx == OUT_OF_BLOCKS);
	  local_first_free_block = this_block;
	}
	temp = this_block.nextblock;
	this_block.nextblock = local_first_free_ndx;
	local_first_free_ndx = (this_block.baseAddr.diff(start))/GC_BLOCKSIZE;
	partialBlockList[i] = temp;
	if (temp == OUT_OF_BLOCKS) break;
	this_block = blocks[temp];
      }
	
      if (temp == OUT_OF_BLOCKS) continue;
      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
	VM_BlockControl next_block = blocks[next];
	if (!next_block.live) {
	  if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block ",
						  " in partial list", next);
	  // In this stanza, we make the next's next the next of this_block, and put
	  // original next on the freelist
	  if (local_first_free_block == null) {
	    if (VM.VerifyAssertions) VM.assert(local_first_free_ndx == OUT_OF_BLOCKS);
	    local_first_free_block = next_block;
	  }
	  this_block.nextblock = next_block.nextblock; // out of live list
	  next_block.nextblock = local_first_free_ndx;
	  local_first_free_ndx = next;
	  local_blocks_available++;
	} else {
	  this_block = next_block;  // live block done
	}
	next = this_block.nextblock;
      }
    }

    if (DEBUG_FREEBLOCKS) 
      VM_Scheduler.trace(" Found partial ", " freeblocks", local_blocks_available);
    
    // Rendezvous here because below and above, partialBlocklist can be modified
    VM_CollectorThread.gcBarrier.rendezvous(VM_CollectorThread.MEASURE_WAIT_TIMES);

    sysLockFree.lock();   // serialize access to global block data

    // If this processor found empty blocks, add them to global free list
    //
    if (local_first_free_block != null) {
      if (DEBUG_FREEBLOCKS) {
	if (local_first_free_ndx == OUT_OF_BLOCKS) 
	  VM_Scheduler.trace(" LFFB not NULL", "LFFI = out_of_Blocks");
      }
      local_first_free_block.nextblock = first_freeblock;
      first_freeblock = local_first_free_ndx;
      blocks_available += local_blocks_available;
    }
    
    // Add excess partially full blocks (maybe full ???) blocks
    // of each size to the global list for that size
    //
    if (GSC_TRACE) VM.sysWrite("\nAdding to global partial block lists\n");
    
    for (int i = 0; i < GC_SIZES; i++) {
      VM_SizeControl this_size = st.sizes[i];
      if (this_size.lastBlockToKeep != -1 ) {
	VM_BlockControl lastToKeep = blocks[this_size.lastBlockToKeep];
	int firstToGiveUp = lastToKeep.nextblock;
	if (firstToGiveUp != OUT_OF_BLOCKS) {
	  blocks[this_size.last_allocated].nextblock = partialBlockList[i];
	  partialBlockList[i] = firstToGiveUp;
	  lastToKeep.nextblock = OUT_OF_BLOCKS;
	}
      }
      if (GSC_TRACE) {
	VM.sysWrite(" size = "); VM.sysWrite(i,false);
	VM.sysWrite(" new first = "); VM.sysWrite(partialBlockList[i],false);
	VM.sysWrite("\n");
      }
    }
    sysLockFree.unlock();  // release lock on global block data
  }
}
