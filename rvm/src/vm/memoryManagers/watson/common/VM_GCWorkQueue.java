/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_ClassLoader;
import com.ibm.JikesRVM.VM_SystemClassLoader;
import com.ibm.JikesRVM.VM_Atom;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.VM_Class;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_Thread;

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
 *
 * @see VM_Allocator
 * @see VM_CollectorThread
 *
 * @author Tony Cocchi
 * @author Stephen Smith
 */
public class VM_GCWorkQueue {
   
  //-----------------------
  //static variables

  // validate refs when put into buffers - to catch bads refs early
  private static final boolean VALIDATE_BUFFER_PUTS = false;

  private static final boolean trace = false;
  private static final boolean debug = false;

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
  public static int WORK_BUFFER_SIZE = 4 * 1024;

  /** single instance of GCWorkQueue, allocated in the bootImage */
  static VM_GCWorkQueue workQueue = new VM_GCWorkQueue();
  
  //-----------------------
  //instance variables

  private int     numRealProcessors;
  private int     numThreads;
  private int     numThreadsWaiting;   
  private VM_Address bufferHead;
  private boolean completionFlag;
  
  //-----------------------

  /** constructor */
  VM_GCWorkQueue() throws VM_PragmaUninterruptible {
    numRealProcessors = 1;     // default to 1 real physical processor
  }

  /**
   * Reset the shared work queue, setting the number of
   * participating gc threads.
   */
  synchronized void initialSetup (int n) throws VM_PragmaLogicallyUninterruptible {
    
    if(trace) VM.sysWrite(" GCWorkQueue.initialSetup entered\n");
    
    numThreads = n;
    numThreadsWaiting = 0;
    completionFlag = false;
    bufferHead = VM_Address.zero();

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
  void reset () throws VM_PragmaUninterruptible {
    
    int debug_counter = 0;
    int debug_counter_counter = 0;

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
    resetWorkQBuffers () throws VM_PragmaUninterruptible {

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if(trace) VM.sysWrite(" GCWorkQueue.resetWorkQBuffers entered\n");

    if (myThread.putBufferStart.isZero()) 
      VM_GCWorkQueue.allocatePutBuffer(myThread);
    else {
      // reset TOP pointer for existing buffer
      myThread.putBufferTop = myThread.putBufferStart.add(WORK_BUFFER_SIZE - 4);    
    }

    //check get buffer 
    if (!myThread.getBufferStart.isZero()) {
      VM_GCWorkQueue.freeGetBuffer(myThread);  // release old work q buffer
      myThread.getBufferStart = VM_Address.zero();
    }
    //clear remaining pointers
    myThread.getBufferTop = VM_Address.zero();
    myThread.getBufferEnd = VM_Address.zero();
  }
  
  /**
   * Add a full thread local "put" buffer to the shared work queue
   *
   * @param bufferAddress address of buffer to add to shared queue
   */
  void addBuffer (VM_Address bufferAddress) throws VM_PragmaLogicallyUninterruptible {

    synchronized (this) {

      if(trace) VM.sysWrite(" GCWorkQueue.addBuffer entered\n");

      // add to buffer list
      VM_Address temp = bufferHead;
      bufferHead = bufferAddress;
      
      // set forward ptr in first word of buffer
      VM_Magic.setMemoryAddress( bufferAddress, temp );
      
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
  synchronized VM_Address getBuffer() throws VM_PragmaLogicallyUninterruptible {
    
    if(trace) VM.sysWrite(" GCWorkQueue.getBuffer entered\n");

    if (bufferHead.EQ(VM_Address.zero())) return bufferHead;
    VM_Address temp = bufferHead;
    bufferHead = VM_Address.fromInt(VM_Magic.getMemoryWord(temp));
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
  VM_Address getBufferAndWait () throws VM_PragmaLogicallyUninterruptible, VM_PragmaUninterruptible {
    VM_Address  temp;
    int debug_counter = 0;
    int debug_counter_counter = 0;

    if(trace) VM.sysWrite(" GCWorkQueue.getBufferAndWait entered\n");

    synchronized(this) {

      // see if any work to do, if so, return next work buffer
      if (!bufferHead.isZero()) {
	temp = bufferHead;
	bufferHead = VM_Address.fromInt(VM_Magic.getMemoryWord(temp));
	return temp; 
      }

      if (numThreads == 1) return VM_Address.zero();  // only 1 thread, no work, just return

      numThreadsWaiting++;   // add self to number of gc threads waiting
      
      // if this is last gc thread, ie all threads waiting, then we are done
      //
      if (numThreadsWaiting == numThreads) {
	numThreadsWaiting--;         // take ourself out of count
	completionFlag = true;       // to lets waiting threads return
	return VM_Address.zero();
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
	if (!bufferHead.isZero()) {
	  temp = bufferHead;
	  bufferHead = VM_Magic.getMemoryAddress(temp);
	  numThreadsWaiting--;
	  return temp; 
	}
	//currently no work - are we finished
	if ( completionFlag == true) {
	  numThreadsWaiting--;         // take ourself out of count
	  if (numThreadsWaiting == 0)
	    completionFlag = false;    // last thread out resets completion flag
	  return VM_Address.zero(); // are we complete
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
  static void putToWorkBuffer (VM_Address ref ) throws VM_PragmaUninterruptible {

    if (VALIDATE_BUFFER_PUTS) {
      if (!VM_GCUtil.validRef(ref)) {
	VM_Scheduler.traceHex("GCWorkQueue:putToWorkBuffer:","bad ref =",ref.toInt());
	VM_GCUtil.dumpRef(ref);
	VM_Memory.dumpMemory(ref, 64, 64);  // dump 16 words on either side of bad ref
	VM_Scheduler.trace("GCWorkQueue:putToWorkBuffer:","dumping executing stack");
	VM_Scheduler.dumpStack();
	/*** following may generate too much 
	VM_Scheduler.trace("GCWorkQueue:putToWorkBuffer:","dumping all thread stacks");
	VM_GCUtil.dumpAllThreadStacks();
	***/
	VM.assert(false);
      }
    }

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (COUNT_GETS_AND_PUTS) myThread.putWorkCount++;

    VM_Magic.setMemoryAddress(myThread.putBufferTop, ref);
    myThread.putBufferTop = myThread.putBufferTop.sub(4);
    if (myThread.putBufferTop.EQ(myThread.putBufferStart)) {

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
  public static VM_Address getFromWorkBuffer () throws VM_PragmaUninterruptible {

    VM_Address newbufaddress;
    VM_Address temp;
    double temptime;

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (COUNT_GETS_AND_PUTS) myThread.getWorkCount++;

    myThread.getBufferTop = myThread.getBufferTop.add(4);

    if (myThread.getBufferTop.LT(myThread.getBufferEnd))
      //easy case- return next work q item
      return VM_Magic.getMemoryAddress( myThread.getBufferTop);

    // get buffer is empty, getBufferTop == getBufferEnd

    // get buffer from shared queue of work buffers
    newbufaddress = workQueue.getBuffer();

    if (!newbufaddress.isZero()) {
      if (WORKQUEUE_COUNTS) myThread.getBufferCount++;
      if (!myThread.getBufferStart.isZero())
	  VM_GCWorkQueue.freeGetBuffer(myThread);  // release old work q buffer
      myThread.getBufferStart = newbufaddress;
      //set up pointers for new get buffer
      myThread.getBufferTop = myThread.getBufferStart.add(4);
      myThread.getBufferEnd = myThread.getBufferStart.add(WORK_BUFFER_SIZE); 
      return VM_Magic.getMemoryAddress(myThread.getBufferTop);
    }
    
    // no buffers in work queue at this time.  if our putBuffer is not empty, swap
    // it with our empty get buffer, and start processing the items in it
    if (myThread.putBufferTop.LT(myThread.putBufferStart.add(WORK_BUFFER_SIZE - 4))) {
      
      if (WORKQUEUE_COUNTS) myThread.swapBufferCount++;
      
      if (!myThread.getBufferStart.isZero()) {
	// have get buffer, swap of get buffer and put buffer
	if(trace) VM.sysWrite(" GCWorkQueue.getFromWorkBuffer swapping\n");
	// swap start addresses
	temp = myThread.putBufferStart;
	myThread.putBufferStart = myThread.getBufferStart;
	myThread.getBufferStart = temp;
	//set end pointer of get buffer
	myThread.getBufferEnd = myThread.getBufferStart.add(WORK_BUFFER_SIZE);
	// swap current top pointer
	temp = myThread.putBufferTop;
	myThread.putBufferTop = myThread.getBufferTop.sub(4);  // -4 to compensate for +4 above
	myThread.getBufferTop = temp;           // points to empty slot preceding first occupied slot
      }
      else {
	// no get buffer, take put buffer and allocate new put buffer
	if(trace) VM.sysWrite(" GCWorkQueue.getFromWorkBuffer swapping-no get buffer\n");
	myThread.getBufferStart =  myThread.putBufferStart;
	myThread.getBufferTop =  myThread.putBufferTop;    
	myThread.getBufferEnd = myThread.getBufferStart.add(WORK_BUFFER_SIZE); 

	VM_GCWorkQueue.allocatePutBuffer(myThread);  //get a new Put Buffer
      }
      //return first entry in new get buffer
      myThread.getBufferTop = myThread.getBufferTop.add(4);
      return VM_Magic.getMemoryAddress(myThread.getBufferTop);
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
    if (!newbufaddress.isZero()) {
      if (MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
	myThread.bufferWaitTime += (VM_Time.now() - temptime); 

      if (WORKQUEUE_COUNTS) myThread.getBufferCount++;

      VM_GCWorkQueue.freeGetBuffer(myThread);  // release the old work q buffer
      myThread.getBufferStart = newbufaddress;

      //set up pointers for new get buffer
      myThread.getBufferTop = myThread.getBufferStart.add(4);
      myThread.getBufferEnd = myThread.getBufferStart.add(WORK_BUFFER_SIZE); 
      return VM_Magic.getMemoryAddress(myThread.getBufferTop);
    }
    
    // no more work and no more buffers ie end of work queue phase of gc

    if (MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      myThread.finishWaitTime = (VM_Time.now() - temptime);

    // reset top ptr for get buffer to its proper "empty" state
    myThread.getBufferTop = myThread.getBufferStart.add(WORK_BUFFER_SIZE - 4);
    
    if(trace) VM.sysWrite(" GCWorkQueue.getFromWorkBuffer no more work\n");
    return VM_Address.zero();
    
  }  // getFromWorkBuffer

  /**
   * allocate a work queue "put" buffer for a VM_CollectorThread,
   * first look to see if the thread has a saved "extra buffer",
   * if none available, then allocate from the system heap.
   *
   * @param   VM_CollectorThread needing a new put buffer
   */
  private static void
    allocatePutBuffer (VM_CollectorThread myThread) throws VM_PragmaUninterruptible { 
    VM_Address bufferAddress;

    if (!myThread.extraBuffer.isZero()) {
      bufferAddress = myThread.extraBuffer;
      myThread.extraBuffer = VM_Address.zero();
    }
    else if (!myThread.extraBuffer2.isZero()) {
      bufferAddress = myThread.extraBuffer2;
      myThread.extraBuffer2 = VM_Address.zero();
    }
    else {
      bufferAddress = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP, WORK_BUFFER_SIZE));
      if (bufferAddress.isZero()) {
	VM.sysWrite(" In VM_GCWorkQueue: call to sysMalloc for work buffer returned 0\n");
	VM.shutdown(1901);
      }
    }
    myThread.putBufferStart = bufferAddress;
    myThread.putBufferTop = bufferAddress.add(WORK_BUFFER_SIZE - 4);
  }  // allocatePutBuffer

  /**
   * free current "get" buffer for a VM_CollectorThread, first try
   * to save as one of the threads two "extra" buffers, then return
   * buffer to the system heap.
   *
   * @param   VM_CollectorThread with a get buffer to free
   */
  private static void freeGetBuffer (VM_CollectorThread myThread) throws VM_PragmaUninterruptible { 

    if (myThread.extraBuffer.isZero()) 
      myThread.extraBuffer = myThread.getBufferStart;
    else if (myThread.extraBuffer2.isZero())
      myThread.extraBuffer2 = myThread.getBufferStart;
    else 
      VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, myThread.getBufferStart.toInt());

  }  // freeGetBuffer


  /**
   * method to give a waiting thread/processor something do for a while
   * without interferring with others trying to access the synchronized block.
   * Spins if running with fewer RVM "processors" than physical processors.
   * Yields (to Operating System) if running with more "processors" than
   * real processors.
   *
   * @param x amount to spin in some unknown units
   */
  private int spinABit ( int x) throws VM_PragmaUninterruptible {
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
  static void emptyWorkQueue() throws VM_PragmaUninterruptible {

      VM_Address ref = VM_GCWorkQueue.getFromWorkBuffer();
    
      if (WORKQUEUE_COUNTS) {
	  VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
	  myThread.rootWorkCount = myThread.putWorkCount;
      }
      
      while (!ref.isZero()) {
	  VM_ScanObject.scanObjectOrArray( ref );	   
	  ref = VM_GCWorkQueue.getFromWorkBuffer();
      }
  }  // emptyWorkQueue

  // methods for measurement statistics

  static void resetCounters ( VM_CollectorThread ct ) throws VM_PragmaUninterruptible {
    ct.copyCount = 0;
    ct.rootWorkCount = 0;
    ct.putWorkCount = 0;
    ct.getWorkCount = 0;
    ct.swapBufferCount = 0;
    ct.putBufferCount = 0;
    ct.getBufferCount = 0;
  }

  static void
  resetWaitTimes ( VM_CollectorThread ct ) throws VM_PragmaUninterruptible {
    ct.bufferWaitCount = 0;
    ct.bufferWaitTime = 0.0;
    ct.finishWaitTime = 0.0;
  }

  static void 
  saveAllCounters() throws VM_PragmaUninterruptible {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      saveCounters(ct);
      resetCounters(ct);
    }
  }

  static void 
  resetAllCounters() throws VM_PragmaUninterruptible {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      resetCounters(ct);
    }
  }

  static void
  saveCounters ( VM_CollectorThread ct ) throws VM_PragmaUninterruptible {
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
  saveAllWaitTimes() throws VM_PragmaUninterruptible {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      saveWaitTimes(ct);
      resetWaitTimes(ct);
    }
  }

  static void
  resetAllWaitTimes() throws VM_PragmaUninterruptible {
    int i;
    VM_CollectorThread ct;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      resetWaitTimes(ct);
    }
  }
  
  static void
  saveWaitTimes ( VM_CollectorThread ct ) throws VM_PragmaUninterruptible {
    ct.bufferWaitCount1 = ct.bufferWaitCount ;
    ct.bufferWaitTime1  = ct.bufferWaitTime  ;
    ct.finishWaitTime1  = ct.finishWaitTime  ;
    resetWaitTimes(ct);
  }

  static void
  printAllWaitTimes () throws VM_PragmaUninterruptible {
    int i;
    VM_CollectorThread ct;
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
  printAllCounters() throws VM_PragmaUninterruptible {
    // print load balancing work queue counts
    int i;
    VM_CollectorThread ct;

    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      VM.sysWrite(i, false);
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
      VM.sysWrite("\n");
    }
  }

}
