/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Class that manages work buffers of references to objects that need to
 * be scanned during a collection.  Each GC thread (a VM_CollectorThread)
 * keeps a thread local "getBuffer" of references that it will process.
 * References it finds for objects that need to be scanned, such as
 * objects it marks during the scan phase, it inserts into its thread
 * local "putBuffer".  When a putBuffer becomes full, it is added to the
 * shared queue of buffers to be processed.  When a threads getBuffer is
 * empty, it first attempts to swap it with its own putBuffer, if it has
 * entries.  If it is also empty, it attempts to get a buffer from the
 * shared queue.
 * <p>
 * All GC threads waiting for getBuffers from the shared queue identifies
 * the end of a scan phase during collection.
 * <p>
 * The default size of each buffer is specified by <b>WORK_BUFFER_SIZE</b>,
 * which can be overridden by the command line argument -wbnnn
 * <p>
 * For measurement purposes only, it is possible to compile the system
 * to use the old thread private workqueues, which do not attempt
 * to balance the workload. To do this, set the flag USE_OLD_PRIVATE_WORKQUEUE
 * to <b>true</b>.
 *
 * @see VM_Allocator
 * @see VM_CollectorThread
 *
 * @author Tony Cocchi
 * @author Stephen Smith
 */
class VM_GCWorkQueue  implements VM_Uninterruptible {
   
  //-----------------------
  //static variables

  // validate refs when put into buffers - to catch bads refs early
  private static final boolean VALIDATE_BUFFER_PUTS = false;

  private static final boolean trace = false;
  private static final boolean debug = false;

  /**
   * Flag to cause the OLD thread private work queues to be used
   * instead of the current load balancing work queues.  This option
   * is retained only for measurement purposes.
   * <p>
   * Set to <b>true</b> to use the old private work queues.
   * This will also cause the <b>WORKQUEUE_COUNTS</b> flags to print
   * per threads counts of private work queue activity
   */
  static final boolean USE_OLD_PRIVATE_WORKQUEUE = false;

  /**
   * Flag to cause per thread counts of WorkQueue buffer activity
   * to be recorded and reported when -verbose:gc is specified.
   */
  static final boolean WORKQUEUE_COUNTS = false;

  /**
   * Flag to include counts of Gets and Puts counts in the counts
   * measured and reported when WORKQUEUE_COUNTS = true.
   * To a very close approximation, the Put count is the number of
   * objects marked (and thus scanned). The sum of the Gets & the
   * sum of the Puts should be approx. equal. (some system objects
   * do not get put into the workqueue buffers)
   */
  static final boolean COUNT_GETS_AND_PUTS = WORKQUEUE_COUNTS && false;

  /**
   * Flag to cause measurement of time waiting for get buffers,
   * and time waiting for all threads to finish processing their
   * buffers, measured on a per thread basis.
   * See VM_CollectorThread.MEASURE_WAIT_TIMES.
   */
  static final boolean MEASURE_WAIT_TIMES = VM_CollectorThread.MEASURE_WAIT_TIMES;

  /**
   * Default size of each work queue buffer. It can be overridden by the
   * command line argument -X:wbsize=nnn where nnn is in entries (words).
   * Changing the buffer size can significantly affect the performance
   * of the load-balancing Work Queue.
   */
  static int WORK_BUFFER_SIZE = 4 * 1024;

  /** single instance of GCWorkQueue, allocated in the bootImage */
  static VM_GCWorkQueue workQueue = null;
  
  //-----------------------
  //instance variables

  private int     numRealProcessors;
  private int     numThreads;
  private int     numThreadsWaiting;   
  private int     bufferHead;
  private boolean completionFlag;
  
  //-----------------------

  /** constructor */
  VM_GCWorkQueue() {
    numRealProcessors = 1;     // default to 1 real physical processor
  }

  /**
   * Initialization for the BootImage. Called from VM_Allocator.init().
   */
  static void
    init () {
    // we want the GCWorkQueue object in the bootimage so it doesn't get moved.
    // we invoke synchronized methods/blocks on these, and if moved, the lock state
    // may not get maintained correctly, ie lock in one copy & unlock in the other copy
    //
    workQueue = new VM_GCWorkQueue();
  }

  /**
   * Reset the shared work queue, setting the number of
   * participating gc threads.
   */
  synchronized void
    initialSetup (int n) {
    
    if(trace) VM.sysWrite(" GCWorkQueue.initialSetup entered\n");
    
    numThreads = n;
    numThreadsWaiting = 0;
    completionFlag = false;
    bufferHead = 0;

    if ( ! VM.BuildForSingleVirtualProcessor) {
      // Set number of Real processors on the running computer. This will allow
      // spinABit() to spin when running with fewer proecssors than real processors
      numRealProcessors = VM.sysCall0(VM_BootRecord.the_boot_record.sysNumProcessorsIP);
      if(trace)
	VM_Scheduler.trace("VM_GCWorkQueue.initialSetup:","numRealProcessors =",numRealProcessors);
    }
  }

  /**
   * Reset the shared work queue. There should be nothing to do,
   * since all flags & counts should have been reset to their
   * proper initial value by the last thread leaving the previous
   * use of the Work Queue.  Reset will wait until all threads
   * have left a previous use of the queue. It should always
   * be called (by 1 thread) before any reuse of the Work Queue.
   */  
  void reset () {
    
    int debug_counter = 0;
    int debug_counter_counter = 0;

    if (USE_OLD_PRIVATE_WORKQUEUE) return;  // no shared data to reset

    if (trace) VM_Scheduler.trace("VM_GCWorkQueue.reset:","numThreadsWaiting =",
				  numThreadsWaiting);

    // Last thread to leave a previous use of the Work Queue will reset
    // the completionFlag to false, wait here for that to happen.
    //
    while (completionFlag == true) {
      
      // spin here a while waiting for others to leave
      int x = spinABit(5);
      
      if (debug) {
	if ( (++debug_counter % 100000) == 0) {
	  VM_Scheduler.trace("VM_GCWorkQueue","waiting - numThreadsWaiting =",
			     numThreadsWaiting);
	  if ( ++debug_counter_counter > 10)
	    VM.sysFail("Stuck In WorkQueue.reset");
	}
      }
    } // end of while loop

  }  // reset

  /**
   * Reset the thread local work queue buffers for the calling
   * GC thread (a VM_CollectorThread).
   */
  static void
    resetWorkQBuffers () {

    if (USE_OLD_PRIVATE_WORKQUEUE) { resetPrivateWorkQueue(); return; }

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if(trace) VM.sysWrite(" GCWorkQueue.resetWorkQBuffers entered\n");

    if (myThread.putBufferStart == 0) {
      VM_GCWorkQueue.allocatePutBuffer(myThread);
    }
    else {
      // reset TOP pointer for existing buffer
      myThread.putBufferTop = myThread.putBufferStart + WORK_BUFFER_SIZE - 4;    
    }

    //check get buffer 
    if (myThread.getBufferStart != 0 ) {
      VM_GCWorkQueue.freeGetBuffer(myThread);  // release old work q buffer
      myThread.getBufferStart = 0;
    }
    //clear remaining pointers
    myThread.getBufferTop = 0;
    myThread.getBufferEnd = 0;
  }
  
  /**
   * Add a full thread local "put" buffer to the shared work queue
   *
   * @param bufferAddress address of buffer to add to shared queue
   */
  void
    addBuffer (int bufferAddress) {

    synchronized (this) {

      if(trace) VM.sysWrite(" GCWorkQueue.addBuffer entered\n");

      // add to buffer list
      int temp = bufferHead;
      bufferHead = bufferAddress;
      
      // set forward ptr in first word of buffer
      VM_Magic.setMemoryWord( bufferAddress, temp );   
      
      // wake up any waiting threads (if any)
      if (numThreadsWaiting == 0) return;
      else this.notify();            // wake up 1 thread thats waiting
    }
  }

  /**
   * Get a buffer from the shared work queue. Returns zero if no buffers
   * are currently available.
   *
   * @return address of buffer or 0 if none available
   */
  synchronized int
    getBuffer() {
    
    if(trace) VM.sysWrite(" GCWorkQueue.getBuffer entered\n");

    if (bufferHead == 0) return 0;
    int temp = bufferHead;
    bufferHead =   VM_Magic.getMemoryWord( temp );
    return temp; 
  }


  /**
   * Get a buffer from the shared work queue.  Waits for a buffer
   * if none are currently available. Returns the address of a
   * buffer if one becomes available. Returns zero if it reaches
   * a state where all participating threads are waiting for a buffer.
   * This is used during collection to indicate the end of a scanning
   * phase.
   *
   * CURRENTLY WAIT BY SPINNING - SOMEDAY DO WAIT NOTIFY
   *
   * @return address of a buffer
   *         zero if no buffers available & all participants waiting
   */
  int getBufferAndWait () {
    int  temp;
    int debug_counter = 0;
    int debug_counter_counter = 0;

    if(trace) VM.sysWrite(" GCWorkQueue.getBufferAndWait entered\n");

    synchronized(this) {

      // see if any work to do, if so, return next work buffer
      if (bufferHead != 0) {
	temp = bufferHead;
	bufferHead =   VM_Magic.getMemoryWord( temp );
	return temp; 
      }

      if (numThreads == 1) return 0;  // only 1 thread, no work, just return

      numThreadsWaiting++;   // add self to number of gc threads waiting
      
      // if this is last gc thread, ie all threads waiting, then we are done
      //
      if (numThreadsWaiting == numThreads) {
	numThreadsWaiting--;         // take ourself out of count
	completionFlag = true;       // to lets waiting threads return
	return 0;
      }
    } // end of synchronized block
    
    // wait for work to arrive (or for end) currently spin a while & then 
    // recheck the work queue (lacking a "system" level wait-notify)
    //
    while (true) {
      
      // if we had a system wait-notify...we could do following...
      // try { wait(); } 
      // catch (InterruptedException e) {
      //	    VM.sysWrite("Interrupted Exception in getBufferAndWait")
      //		}

      if (debug) {
	if ( (++debug_counter % 100000) == 0) {
	  VM_Scheduler.trace("VM_GCWorkQueue","waiting - numThreadsWaiting =",numThreadsWaiting);
	  if ( ++debug_counter_counter > 10)
	    VM.shutdown(-2);
	}
      }
      
      //spin here a while
      int x = spinABit(5);
      
      synchronized(this) {
	// see if any work to do
	if (bufferHead != 0) {
	  temp = bufferHead;
	  bufferHead =   VM_Magic.getMemoryWord( temp );
	  numThreadsWaiting--;
	  return temp; 
	}
	//currently no work - are we finished
	if ( completionFlag == true) {
	  numThreadsWaiting--;         // take ourself out of count
	  if (numThreadsWaiting == 0)
	    completionFlag = false;    // last thread out resets completion flag
	  return 0; // are we complete
	}
      } // end of synchronized block
      
    } // end of while loop
    
  } // end of method

  //----------------------------------------------------
  // methods used by gc threads to put entries into and get entries out of
  // thread local work buffers they are processing
  //----------------------------------------------------

  /**
   * Add a reference to the thread local put buffer.  If it is full 
   * add it to the shared queue of buffers and acquire a new empty buffer.
   * Put buffers are filled from end to start, with "top" pointing to an
   * empty slot to be filled.
   *
   * @param ref  object reference to add to the put buffer
   */
  static void
    putToWorkBuffer ( int ref ) {
    int newbufaddress;

    if (VALIDATE_BUFFER_PUTS) {
      if (!VM_GCUtil.validRef(ref)) {
	VM_Scheduler.traceHex("GCWorkQueue:putToWorkBuffer:","bad ref =",ref);
	VM_GCUtil.dumpRef(ref);
	VM_GCUtil.dumpMemoryWords(ref-64, 32);  // dump 16 words on either side of bad ref
	VM_Scheduler.trace("GCWorkQueue:putToWorkBuffer:","dumping executing stack");
	VM_Scheduler.dumpStack();
	/*** following may generate too much 
	VM_Scheduler.trace("GCWorkQueue:putToWorkBuffer:","dumping all thread stacks");
	VM_GCUtil.dumpAllThreadStacks();
	***/
	VM.assert(false);
      }
    }

    if (USE_OLD_PRIVATE_WORKQUEUE) { putToPrivateWorkQueue( ref ); return; }

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (COUNT_GETS_AND_PUTS) myThread.putWorkCount++;

    VM_Magic.setMemoryWord( myThread.putBufferTop, ref );
    myThread.putBufferTop -= 4;
    if (myThread.putBufferTop == myThread.putBufferStart) {

      // current buffer is full, give to common pool, get new buffer	    
      if (WORKQUEUE_COUNTS) myThread.putBufferCount++;
      VM_GCWorkQueue.workQueue.addBuffer(myThread.putBufferStart);

      VM_GCWorkQueue.allocatePutBuffer(myThread);    // allocate new Put Buffer
    }
  }

  /**
   * Get a reference from the thread local get buffer.  If the get buffer
   * is empty, and the put buffer has entries, then swap the get and put
   * buffers.  If the put buffer is also empty, get a buffer to process
   * from the shared queue of buffers.
   *
   * Get buffer entries are extracted from start to end with "top" pointing
   * to the last entry returned (and now empty).
   *
   * Returns zero when there are no buffers in the shared queue and
   * all participating GC threads are waiting.
   *
   * @return object reference from the get buffer
   *         zero when there are no more references to process
   */
  static int getFromWorkBuffer () {
    int newbufaddress;
    int temp;
    double temptime;

    if (USE_OLD_PRIVATE_WORKQUEUE)  return getFromPrivateWorkQueue();

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (COUNT_GETS_AND_PUTS) myThread.getWorkCount++;

    myThread.getBufferTop += 4;

    if (myThread.getBufferTop < myThread.getBufferEnd)
      //easy case- return next work q item
      return  VM_Magic.getMemoryWord( myThread.getBufferTop);

    // get buffer is empty, getBufferTop == getBufferEnd

    // get buffer from shared queue of work buffers
    newbufaddress = workQueue.getBuffer();

    if ( newbufaddress != 0 ) {
      if (WORKQUEUE_COUNTS) myThread.getBufferCount++;
      if (myThread.getBufferStart != 0 ) {
	VM_GCWorkQueue.freeGetBuffer(myThread);  // release old work q buffer
      }
      myThread.getBufferStart = newbufaddress;
      //set up pointers for new get buffer
      myThread.getBufferTop = myThread.getBufferStart + 4;
      myThread.getBufferEnd = myThread.getBufferStart + WORK_BUFFER_SIZE; 
      return VM_Magic.getMemoryWord( myThread.getBufferTop);
    }
    
    // no buffers in work queue at this time.  if our putBuffer is not empty, swap
    // it with our empty get buffer, and start processing the items in it
    if (myThread.putBufferTop < myThread.putBufferStart + WORK_BUFFER_SIZE - 4) {
      
      if (WORKQUEUE_COUNTS) myThread.swapBufferCount++;
      
      if(myThread.getBufferStart != 0){
	// have get buffer, swap of get buffer and put buffer
	if(trace) VM.sysWrite(" GCWorkQueue.getFromWorkBuffer swapping\n");
	// swap start addresses
	temp = myThread.putBufferStart;
	myThread.putBufferStart = myThread.getBufferStart;
	myThread.getBufferStart = temp;
	//set end pointer of get buffer
	myThread.getBufferEnd = myThread.getBufferStart + WORK_BUFFER_SIZE;
	// swap current top pointer
	temp = myThread.putBufferTop;
	myThread.putBufferTop = myThread.getBufferTop - 4;  // -4 to compensate for +4 above
	myThread.getBufferTop = temp;           // points to empty slot preceding first occupied slot
      }
      else {
	// no get buffer, take put buffer and allocate new put buffer
	if(trace) VM.sysWrite(" GCWorkQueue.getFromWorkBuffer swapping-no get buffer\n");
	myThread.getBufferStart =  myThread.putBufferStart;
	myThread.getBufferTop =  myThread.putBufferTop;    
	myThread.getBufferEnd = myThread.getBufferStart + WORK_BUFFER_SIZE; 

	VM_GCWorkQueue.allocatePutBuffer(myThread);  //get a new Put Buffer
      }
      //return first entry in new get buffer
      myThread.getBufferTop += 4;
      return  VM_Magic.getMemoryWord( myThread.getBufferTop);
    }
    // put buffer and get buffer are both empty
    // go wait for work or notification that gc is finished
    
    // get buffer from queue or wait for more work
    if (MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES) {
      temptime = VM_Time.now();
      myThread.bufferWaitCount++;
    }
    newbufaddress = workQueue.getBufferAndWait() ;
    
    if(trace) VM.sysWrite(" GCWorkQueue.getFromWorkBuffer return from getBuffernAndWait\n");
    
    // get a new buffer of work
    if ( newbufaddress != 0) {
      if (MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
	myThread.bufferWaitTime += (VM_Time.now() - temptime); 

      if (WORKQUEUE_COUNTS) myThread.getBufferCount++;

      VM_GCWorkQueue.freeGetBuffer(myThread);  // release the old work q buffer
      myThread.getBufferStart = newbufaddress;

      //set up pointers for new get buffer
      myThread.getBufferTop = myThread.getBufferStart + 4;
      myThread.getBufferEnd = myThread.getBufferStart + WORK_BUFFER_SIZE; 
      return VM_Magic.getMemoryWord( myThread.getBufferTop);
    }
    
    // no more work and no more buffers ie end of work queue phase of gc

    if (MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      myThread.finishWaitTime = (VM_Time.now() - temptime);

    // reset top ptr for get buffer to its proper "empty" state
    myThread.getBufferTop = myThread.getBufferStart + WORK_BUFFER_SIZE - 4;
    
    if(trace) VM.sysWrite(" GCWorkQueue.getFromWorkBuffer no more work\n");
    return 0;
    
  }  // getFromWorkBuffer

  /**
   * allocate a work queue "put" buffer for a VM_CollectorThread,
   * first look to see if the thread has a saved "extra buffer",
   * if none available, then allocate from the system heap.
   *
   * @param   VM_CollectorThread needing a new put buffer
   */
  private static void
    allocatePutBuffer (VM_CollectorThread myThread) { 
    int bufferAddress;

    if (myThread.extraBuffer != 0) {
      bufferAddress = myThread.extraBuffer;
      myThread.extraBuffer = 0;
    }
    else if (myThread.extraBuffer2 != 0) {
      bufferAddress = myThread.extraBuffer2;
      myThread.extraBuffer2 = 0;
    }
    else {
      if ((bufferAddress = VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
						   WORK_BUFFER_SIZE)) == 0){
	VM.sysWrite(" In VM_GCWorkQueue: call to sysMalloc for work buffer returned 0\n");
	VM.shutdown(1901);
      }
    }
    myThread.putBufferStart = bufferAddress;
    myThread.putBufferTop = bufferAddress + WORK_BUFFER_SIZE - 4;
  }  // allocatePutBuffer

  /**
   * free current "get" buffer for a VM_CollectorThread, first try
   * to save as one of the threads two "extra" buffers, then return
   * buffer to the system heap.
   *
   * @param   VM_CollectorThread with a get buffer to free
   */
  private static void
    freeGetBuffer (VM_CollectorThread myThread) { 

    if (myThread.extraBuffer == 0) {
      myThread.extraBuffer = myThread.getBufferStart;
    }
    else if (myThread.extraBuffer2 == 0) {
      myThread.extraBuffer2 = myThread.getBufferStart;
    }
    else {
      VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, myThread.getBufferStart);
    }
  }  // freeGetBuffer


  /**
   * method to give a waiting thread/processor something do for a while
   * without interferring with others trying to access the synchronized block.
   * Spins if running with fewer Jalapeno "processors" than physical processors.
   * Yields (to Operating System) if running with more "processors" than
   * real processors.
   *
   * @param x amount to spin in some unknown units
   */
  private int spinABit ( int x) {
    int sum = 0;

    if (numThreads < numRealProcessors) {
      // spin for a while, keeping the operating system thread
      for ( int i = 0; i < (x*100); i++) {
	sum = sum + i;
      }
      return sum;
    }
    else {
      // yield executing operating system thread back to the operating system
      VM.sysCall0(VM_BootRecord.the_boot_record.sysVirtualProcessorYieldIP);
      return 0;
    }
  }


  /**
   * Process references in work queue buffers until empty.
   */
  static void  
  emptyWorkQueue() {
    int ref = VM_GCWorkQueue.getFromWorkBuffer();
    
    if (WORKQUEUE_COUNTS) {
      VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
      myThread.rootWorkCount = myThread.putWorkCount;
    }
    
    while ( ref != 0 ) {
      VM_ScanObject.scanObjectOrArray( ref );	   
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }  // emptyWorkQueue

  // FOLLOWING IS IMPLEMENTATION OF OLD THREAD PRIVATE WORK QUEUE

  // Following methods implement the old thread private work queue, which
  // was found to not provide sufficient load balancing among the executing
  // collector threads.  It is retained here only for measurement purposes.
  // Setting the Flag USE_OLD_PRIVATE_WORKQUEUE==true, will cause the thread
  // private work queue to be used instead of the load balancing work
  // queue.  In this case the flag WORKQUEUE_COUNTS will cause -verbose:gc
  // to pring out per thread counts for the thread private work queue.

  /** Work Queue size for the OLD thread private workqueues */
  final static int     WORK_QUEUE_SIZE = 128 * 1024;

  // work queue for use by each GC thread during GC consists of a linked
  // list of buffers, each WORK_QUEUE_SIZE bytes big. each buffer is
  // allocated from system memory (not from VM heap) and not a java object.
  // formated as follows:
  //
  //    | next ptr | back ptr | entry 0 | entry 1 | ....           |
  //                            ^                  ^                ^
  //                            WQStart            WQTop            WQEnd
  //

  /**
   * Reset OLD thread private work queue, allocate queue buffer if necessary.
   */
  static void
  resetPrivateWorkQueue () {
    VM_CollectorThread mythread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (mythread.workQStartAddress == 0) {
      // allocate first workqueue buffer
      int newbufaddress = VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP, WORK_QUEUE_SIZE);
      if ( newbufaddress == 0 ) {
	VM.sysWrite(" In VM_GCPrivateWorkQueue: sysMalloc for workqueue returned 0 \n");
	VM.sysExit(1800);
      }
      VM_Magic.setMemoryWord( newbufaddress, 0 );              // next ptr = null
      VM_Magic.setMemoryWord( newbufaddress+4, 0 );            // prev ptr = null
      mythread.workQStartAddress = newbufaddress + 8;
      mythread.workQEndAddress = newbufaddress + WORK_QUEUE_SIZE;
    }
    // reset queue to empty state, workQueueTop -> first free slot
    mythread.workQueueTop = mythread.workQStartAddress;
  }

  /**
   * Add a ref to the OLD thread private work queue of objects to be scanned.
   * Note -  <b>workQueueTop</b> points to word beyond end of queue
   *
   * @param ref  object reference to add to work queue
   */
  static void
  putToPrivateWorkQueue ( int ref ) {
    int newbufaddress;

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    // note:workQueueTop points to word beyond end of queue
    if ( myThread.workQueueTop >= myThread.workQEndAddress ) {
      newbufaddress = VM_Magic.getMemoryWord(myThread.workQStartAddress-8);  // next buffer ptr    
      if ( newbufaddress == 0 ) {
	// get new buffer, set to empty, and link to current buffer
	if ((newbufaddress = VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
					 WORK_QUEUE_SIZE)) == 0) {
	  VM.sysWrite(" In putToPrivateWorkQueue, call to sysMalloc returned 0 \n");
	  VM.sysExit(1800);
	}
	VM_Magic.setMemoryWord( myThread.workQStartAddress-8, newbufaddress );   // next prt
	VM_Magic.setMemoryWord( newbufaddress, 0 );                             // next prt = null
	VM_Magic.setMemoryWord( newbufaddress+4, myThread.workQStartAddress-8 ); // prev prt
      }
      // make next buffer the current buffer
      myThread.workQStartAddress = myThread.workQueueTop = newbufaddress + 8;
      myThread.workQEndAddress = newbufaddress + WORK_QUEUE_SIZE;
    }
    VM_Magic.setMemoryWord( myThread.workQueueTop, ref );
    myThread.workQueueTop = myThread.workQueueTop + 4;

    if (WORKQUEUE_COUNTS) {
      myThread.putWorkCount++;
      myThread.currentWorkCount++;
      if ( myThread.currentWorkCount > myThread.maxWorkCount )
	myThread.maxWorkCount = myThread.currentWorkCount;
    }
  }  // putToPrivateWorkQueue

  /**
   * Gets top reference from the OLD thread private work queue.
   * Note -  <b>workQueueTop</b> points to word beyond end of queue
   *
   * @return top reference in queue, 0 if queue is empty
   */
  static int  
  getFromPrivateWorkQueue () {

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if ( myThread.workQueueTop == myThread.workQStartAddress ) {
      // current buffer is empty, make previous buffer the current buffer
      int newbufaddress = VM_Magic.getMemoryWord(myThread.workQStartAddress-4); // prev ptr 
      if ( newbufaddress == 0 ) {
	// no more buffers to process, must be done
	// could free EXTRA buffers, leaving one, for now just leave all buffers
	return 0;
      }
      // make preceeding buffer the current buffer
      myThread.workQStartAddress = newbufaddress + 8;
      myThread.workQEndAddress = myThread.workQueueTop =
	newbufaddress + WORK_QUEUE_SIZE;
    }
    if (WORKQUEUE_COUNTS)  myThread.currentWorkCount--;
    myThread.workQueueTop = myThread.workQueueTop - 4;
    return VM_Magic.getMemoryWord( myThread.workQueueTop );
  }   // getFromPrivateWorkQueue


  /**
   * Process objects in the work queue until the queue is empty.
   * Note -  <b>workQueueTop</b> points to word beyond end of queue
   */
  static void  
  emptyPrivateWorkQueue () {
    // take top object from work queue and process (possibly adding
    // more objects to the work queue).  repeat until queue is empty
    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int ref;

    if (WORKQUEUE_COUNTS)
      myThread.initialWorkCount = myThread.putWorkCount;
    
    while ( true ) {
      if ( myThread.workQueueTop == myThread.workQStartAddress ) {
	// current buffer is empty, make previous buffer the current buffer
	int newbufaddress = VM_Magic.getMemoryWord(myThread.workQStartAddress-4); // prev ptr 
	if ( newbufaddress == 0 ) {
	  // no more buffers to process, must be done
	  // could free EXTRA buffers, leaving one, for now just leave all buffers
	  break;
	}
	// make preceeding buffer the current buffer
	myThread.workQStartAddress = newbufaddress + 8;
	myThread.workQEndAddress = myThread.workQueueTop =
	  newbufaddress + WORK_QUEUE_SIZE;
      }
      if (WORKQUEUE_COUNTS)  myThread.currentWorkCount--;
      myThread.workQueueTop = myThread.workQueueTop - 4;
      VM_ScanObject.scanObjectOrArray(VM_Magic.getMemoryWord( myThread.workQueueTop ));
    }  // end while true loop - all entries processed
  }   // emptyPrivateWorkQueue

// End of Private WorkQueue Routines
// ------------------------------------------------------------
//

  static void
  resetCounters ( VM_CollectorThread ct ) {
    // counters used for thread private work queue
    ct.initialWorkCount = 0;
    ct.maxWorkCount = 0;
    ct.currentWorkCount = 0;
    ct.totalWorkCount = 0;

    // following for measuring new common work queue with local work buffers
    ct.copyCount = 0;
    ct.rootWorkCount = 0;
    ct.putWorkCount = 0;
    ct.getWorkCount = 0;
    ct.swapBufferCount = 0;
    ct.putBufferCount = 0;
    ct.getBufferCount = 0;
  }

  static void
  resetWaitTimes ( VM_CollectorThread ct ) {
    ct.bufferWaitCount = 0;
    ct.bufferWaitTime = 0.0;
    ct.finishWaitTime = 0.0;
  }

  static void 
  saveAllCounters() {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      saveCounters(ct);
      resetCounters(ct);
    }
  }

  static void 
  resetAllCounters() {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      resetCounters(ct);
    }
  }

  static void
  saveCounters ( VM_CollectorThread ct ) {
    ct.copyCount1       = ct.copyCount       ;
    ct.rootWorkCount1   = ct.rootWorkCount   ;
    ct.putWorkCount1    = ct.putWorkCount    ;
    ct.getWorkCount1    = ct.getWorkCount    ;
    ct.swapBufferCount1 = ct.swapBufferCount ;
    ct.putBufferCount1  = ct.putBufferCount  ;
    ct.getBufferCount1  = ct.getBufferCount  ;
    resetCounters(ct);
  }

  static void 
  saveAllWaitTimes() {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      saveWaitTimes(ct);
      resetWaitTimes(ct);
    }
  }

  static void
  resetAllWaitTimes() {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      resetWaitTimes(ct);
    }
  }
  
  static void
  saveWaitTimes ( VM_CollectorThread ct ) {
    ct.bufferWaitCount1 = ct.bufferWaitCount ;
    ct.bufferWaitTime1  = ct.bufferWaitTime  ;
    ct.finishWaitTime1  = ct.finishWaitTime  ;
    resetWaitTimes(ct);
  }


  static void
  printAllWaitTimes () {
    int i;
    VM_CollectorThread ct;

    if ( VM_GCWorkQueue.USE_OLD_PRIVATE_WORKQUEUE )
      return;    // times not measured for old work queue strategy

    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );

      VM.sysWrite(i,false);
      VM.sysWrite(" number of waits ");
      VM.sysWrite(ct.bufferWaitCount1,false);
      VM.sysWrite("  buffer wait time ");
      VM.sysWrite( (int)((ct.bufferWaitTime1)*1000000.0), false);
      VM.sysWrite("(us)  finish wait time ");
      VM.sysWrite( (int)((ct.finishWaitTime1)*1000000.0), false);
      VM.sysWrite("(us)\n");
    }
  }  // printAllWaitTimes

  static void
  printAllCounters() {
    // print load balancing work queue counts
    int i;
    VM_CollectorThread ct;

    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      VM.sysWrite(i, false);
      if ( !VM_GCWorkQueue.USE_OLD_PRIVATE_WORKQUEUE ) {

	VM.sysWrite(" copied ");
	VM.sysWrite(ct.copyCount1,false);
	VM.sysWrite(" roots ");
	VM.sysWrite(ct.rootWorkCount1,false);
	VM.sysWrite(" puts ");
	VM.sysWrite(ct.putWorkCount1,false);
	VM.sysWrite(" gets ");
	VM.sysWrite(ct.getWorkCount1,false);
	VM.sysWrite(" put bufs ");
	VM.sysWrite(ct.putBufferCount1,false);
	VM.sysWrite(" get bufs ");
	VM.sysWrite(ct.getBufferCount1,false);
	VM.sysWrite(" swaps ");
	VM.sysWrite(ct.swapBufferCount1,false);
      }
      else {
	// print thread private work queue counts
	VM.sysWrite(" copied ");
	VM.sysWrite(ct.copyCount1, false);
	VM.sysWrite(" roots ");
	VM.sysWrite(ct.initialWorkCount,false);
	VM.sysWrite(" maxWorkCount ");
	VM.sysWrite(ct.maxWorkCount,false);
	VM.sysWrite(" totalWorkCount ");
	VM.sysWrite(ct.putWorkCount,false);
      }
      VM.sysWrite("\n");
    }
  }

}
