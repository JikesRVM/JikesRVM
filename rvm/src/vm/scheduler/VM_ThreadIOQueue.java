/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * A list of threads waiting for i/o data to become available.
 *
 * To avoid blocking a virtual processor on an i/o operation, and to
 * avoid polling, we maintain a list of file/socket descriptors (fd's) that 
 * need to be checked for data availability. When data becomes available
 * on an fd, as indicated by the unix "select()" system call, we allow the
 * corresponding thread to resume execution.
 *
 * At the moment we only use this technique for network i/o. The same could be
 * done for disk i/o, but we currently don't bother: we use blocking disk i/o 
 * and assume that it will complete immediately.
 *
 * @author Derek Lieber
 * @date 25 June 1999 
 */
public final class VM_ThreadIOQueue extends VM_AbstractThreadQueue 
  implements VM_Uninterruptible {
  //----------------//
  // Implementation //
  //----------------//
   
  private int       id;     // id of this queue, for event tracing
  private VM_Thread head;   // first thread on list
  private VM_Thread tail;   // last thread on list
  private int       length; // number of threads on list
  private int       ready;  // number of threads that have been checked and found to be ready for reading/writing without blocking

  private int[] readFds  = new int[2048]; // parameter area for passing fds to sysNetSocketSelect (2048 == FD_SETSIZE in "/usr/include/sys/select.h")
  private int[] writeFds  = new int[2048]; // parameter area for passing fds to sysNetSocketSelect (2048 == FD_SETSIZE in "/usr/include/sys/select.h")
  public static final int FD_READY = -99;        // flag used to represent fd on which operation can proceed without blocking
   
  private double                   selectTime;       // time at which next "select" call can be made, in seconds

  private static  boolean          selectInProgress; // is a "select" system call currently in progress?
  public static  VM_ProcessorLock selectInProgressMutex = new VM_ProcessorLock(); // guard for updating "selectInProgress" flag
   
   //-----------//
   // Interface //
   //-----------//

  VM_ThreadIOQueue(int id) {
    this.id = id;
  }

  // Do any threads have i/o data available?
  //
  boolean isReady() {
    if (length == 0)
      return false; // no threads

    if (ready != 0)
      return true;  // some threads ready

    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logNetSelectBegin();

    // gather array of fds to be interrogated
    //
    VM_Thread t = head;
    int rc = 0; 
    int wc = 0;
    for (int i = 0; i < length; ++i) {
      if (t.waitFdRead != -1) 
	readFds[rc++]  = t.waitFdRead;
      else
	writeFds[wc++]  = t.waitFdWrite;

      // does the thread have a pending interrupt ?
      if ( null != t.externalInterrupt )
	ready++;

      t = t.next;
    }

    if (ready != 0)
      return true;  // some threads interrupted

    // interrogate them
    //
    VM_Processor.getCurrentProcessor().isInSelect = true;
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    selectInProgressMutex.lock();
    int ret = VM.sysCall4(bootRecord.sysNetSelectIP, VM_Magic.objectAsAddress(readFds).toInt(), 
			  VM_Magic.objectAsAddress(writeFds).toInt(), rc, wc);
    selectInProgressMutex.unlock();
    VM_Processor.getCurrentProcessor().isInSelect = false;
      
    if (ret == -1) {
      // VM_Scheduler.trace("VM_ThreadIOQueue", "isReady: select() error");
      return false; // can happen if foreign host disconnects one of the sockets unexpectedly
    }

    if (ret != 0) {
      // update everybody's status
      //
      t = head;
      for (int i = 0, tr  = 0, tw = 0; i < length; ++i) {
	if (t.waitFdRead != -1) {
	  if (readFds[tr++] == FD_READY) {
	    t.waitFdReady = true;
	    ready += 1;
	  } 
	} else {
	  if (writeFds[tw++] == FD_READY) {
	    t.waitFdReady = true;
	    ready += 1;
	  }
	}
	t = t.next;
      }
    }
    // trace("VM_ThreadIOQueue.isReady: ");
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logNetSelectEnd(ready);
 
    return ready != 0;
  }

  // Is queue empty?
  //
  boolean isEmpty() {
    return length == 0;
  }
   
  // Add thread to list of those waiting for i/o data.
  //
  void enqueue(VM_Thread t) {
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logEnqueue(t, id);
    if (VM.VerifyAssertions) VM_Scheduler._assert(t.next == null); // not currently on any other queue
    if (VM.VerifyAssertions) VM_Scheduler._assert((t.waitFdRead >= 0||t.waitFdWrite >= 0) && t.waitFdReady == false);
      
    // trace("VM_ThreadIOQueue.enqueue: before ");
      
    if (head == null) head = t;
    else tail.next = t;
    tail = t;
    ++length;
      
    // trace("VM_ThreadIOQueue.enqueue: ");
  }

  // Fetch a thread that has i/o data available on its fd.
  // Returned: thread (null --> nobody ready)
  //
  VM_Thread dequeue() {
    VM_Thread prev = null;
    VM_Thread t = head;
    for (;;) {
      if (t.waitFdReady || ( null != t.externalInterrupt ) )
	break;
      prev = t;
      t = t.next;
      if (t == null)
	return null;
    }

    // trace("VM_ThreadIOQueue.dequeue: before ");

    if (prev == null) head = t.next;
    else prev.next = t.next;
    if (tail == t) tail = prev;
    t.next = null;
         
    --length;
    --ready;
      
    // trace("VM_ThreadIOQueue.dequeue: ");
      
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logDequeue(t, id);
    return t;
  }
   
  // Number of threads on queue.
  //
  int length() {
    return length;
  }
   
  // Debugging.
  //
  boolean contains(VM_Thread x) {
    for (VM_Thread t = head; t != null; t = t.next)
      if (t == x) return true;
    return false;
  }

  void dump() {
    dump(" ");
  }

  private void trace(String prefix) {
    VM_Scheduler.outputMutex.lock();
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    dump(prefix);
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    VM_Scheduler.outputMutex.unlock();
  }
      
  private void dump(String prefix) {
    VM.sysWrite(prefix);
    for (VM_Thread t = head; t != null; t = t.next) {
      VM.sysWrite(t.getIndex(), false);
      VM.sysWrite("(");
      VM.sysWrite(t.waitFdRead, false);
      VM.sysWrite((t.waitFdReady ? "+) " : "-) "));
    }
    VM.sysWrite("\n");
  }
}
