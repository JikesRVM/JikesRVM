/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.VM_Uninterruptible;

//BEGIN HRM
import com.ibm.JikesRVM.classloader.VM_Method;
//END HRM

/**
 * This class provides support for HPM related operations at thread switch time.  
 * In particular, this class reads the HPM counter values at each thread switch.  
 * <p>
 * There is one instance of this class created for each VM_Processor instance.
 * <p>
 * If tracing has been specified, the HPM counter values are recorded into 
 * one or two buffers.  We use two buffers to ensure that tracing is not interrupted.
 * When the buffer is full, a consumer (VM_TraceWriter) is activated.
 * There is one VM_TraceWriter instance for each instance of this class.
 * <p>
 * We maintain the constraint that only this code writes to the trace buffer's
 * during thread switch time!
 * Methods are provided to handle callbacks.  The method writes information to a 
 * black board that is read when a thread switch occurs.
 * <p>
 * There are multiple trace record formats that can be written to a trace file.  
 * <p>
 * This class has the same constraints as a listener in the adaptive optimization system.
 * 
 * @author Peter F. Sweeney
 * @date 2/6/2003
 * @modified Matthias Hauswirth (8/8/2003) added support to collect method IDs
 */
public class VM_HardwarePerformanceMonitor implements VM_Uninterruptible
{
  /*
   * My consumer.
   */
  protected VM_TraceWriter consumer;

  /**
   * Consumer associated with this producer.
   */
  final public void setConsumer(VM_TraceWriter consumer) {
    this.consumer = consumer;
  }

  /**
   * Wake up the consumer thread (if any) associated with the producer
   */
  final public void activateConsumer() {
    if (consumer != null) {
      consumer.activate();
    }
  }

  /*
   * The field active (manipulated by consumer) determines when we produce
   */
  private boolean active = false;

  /*
   * Start producing.  Determined by consumer.
   */
  public  void   activate() throws VM_PragmaLogicallyUninterruptible
  { 
    if(VM_HardwarePerformanceMonitors.verbose>=2)VM.sysWriteln("VM_HPM.activate() PID ",vpid);
    active = true;  
  }
  /*
   * Stop producing.  Determined by consumer.
   */
  public  void passivate()  throws VM_PragmaLogicallyUninterruptible
  { 
    if(VM_HardwarePerformanceMonitors.verbose>=2)VM.sysWriteln("VM_HPM.passivate() PID ",vpid);
    active = false; 
  }
  /*
   *  Determine if HPM sampling is available.
  public boolean isActive() { return active; }
   */

  /*
   * record formats
   */
  static final public int            TRACE_FORMAT = 1;
  static final public int        START_APP_FORMAT = 2;
  static final public int     COMPLETE_APP_FORMAT = 3;
  static final public int    START_APP_RUN_FORMAT = 4;
  static final public int COMPLETE_APP_RUN_FORMAT = 5;
  static final public int             EXIT_FORMAT = 6;
  static final public int          PADDING_FORMAT = 10;	// add spaces
  /*
   * static fields required for tracing HPM counter values
   */
  static private int     OUTPUT_BUFFER_SIZE = 4096;	// initial output buffer size

  // Keep HPM counter values for each Virtual Processor.
  private HPM_counters vp_counters;
  // can't allocate during thread switch (preallocate local)
  private HPM_counters tmp_counters;
  // number of HPM counters on underlying PowerPC machine (value cached from HPM_info)
  private int n_counters = 0;
  // virtual processor id
  private int vpid                = 0;

  // keep count of number of thread switches
  private int  n_threadSwitches = 0;
  
  //BEGIN HRM
  // error codes used in MID field of trace counter records, if MID can't be obtained
  private final static int UNAVAILABLE_MID                    = -1;
  private final static int CMID_WITHOUT_COMPILED_METHOD_MID   = -2;
  private final static int COMPILED_METHOD_WITHOUT_METHOD_MID = -3;
  private final static int CMID_IS_STACKFRAME_SENTINEL_MID    = -4;
  private final static int CMID_IS_INVISIBLE_METHOD_ID_MID    = -5;
  private final static int CMID_OUT_OF_BOUNDS_MID             = -6;

  // variables for communicating cmids from the various VM_Thread methods, 
  // where we capture those cmids, to this object
  public boolean cmidAvailable = false;
  // callee Compiled Method ID
  public int callee_CMID;
  // caller Compiled Method ID
  public int caller_CMID;
  // the following two lines exist for debugging purposes
  private long cmidAvailableCount   = 0;
  private long cmidUnavailableCount = 0;
  //END HRM

  /*
   * Constructor
   * There is one VM_HardwarePerformanceMonitor object per VM_Processor.
   * Called from VM_Processor constructor.
   */
  public VM_HardwarePerformanceMonitor(int vpid)
  {
    this.vpid     = vpid;
  }
  /*
   * Work we don't want in the boot image.
   * CONSTRAINT: called after VM_HardwarePerformanceMonitors.boot() is called.
   * Called from VM_Scheduler.boot() when VM_Processor instances are created, when
   * the RVM is running on a single kernel thread.
   */
  public void boot() throws VM_PragmaLogicallyUninterruptible
  {
    if(VM_HardwarePerformanceMonitors.verbose>=1)VM.sysWriteln("VM_HPM.boot() PID ",vpid);
    vp_counters  = new HPM_counters();
    tmp_counters = new HPM_counters();
    n_counters = VM_HardwarePerformanceMonitors.hpm_info.numberOfCounters;
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      if(VM_HardwarePerformanceMonitors.verbose>=2)VM.sysWriteln("VM_HPM.boot() pid ",vpid," create VM_TraceWriter as a consumer");
      consumer     = new VM_TraceWriter(this, vpid);
      consumer.start();	// start the thread !
      if(VM_HardwarePerformanceMonitors.verbose>=2)VM.sysWriteln("VM_HPM.boot() pid ",vpid," allocate local buffers");
      buffer_1 = new byte[OUTPUT_BUFFER_SIZE];
      buffer_2 = new byte[OUTPUT_BUFFER_SIZE];
      // start with buffer ONE.
      buffer_code = ONE; index_1 = 0;
    }
  }
  
  /*
   * Update HPM counters.
   * Accumulate HPM counter values with virtual processor, and 
   * the thread that is being swapped out (previous_trhead)
   * If tracing on, record a trace record.
   *
   * CONSTRAINT: JNI calls cause stack to be grown and cause an assertion failure. Use sysCalls.
   * CONSTRAINT: this method is uninterruptible!
   * ASSUMPTION: only called if active == true.
   *
   * @param previous_thread     thread that is being switched out
   * @param timerInterrupted   	timer interrupted if true
   * @param threadSwitch        did a thread switch occur or not?
   */
  public void updateHPMcounters(VM_Thread previous_thread, boolean timerInterrupted, boolean threadSwitch)
  {
    //-#if RVM_WITH_HPM
    if (VM_HardwarePerformanceMonitors.hpm_trace && ! active) 
      // only collect what can be traced when tracing
      return;

    VM_SysCall.sysHPMstopMyThread();
    long endOfWallTime   = VM_Time.cycles();
    long startOfWallTime = 0;
    long wallTime        = 0;
    n_threadSwitches++;
    if (previous_thread.hpm_counters == null) {
      VM.sysWriteln("***VM_HPM.pdateHPMcounters() Previous thread id ",
		    previous_thread.getIndex(),"'s hpm_counters was null!***"); VM.shutdown(VM.exitStatusHPMTrouble);
    }
    if (previous_thread.startOfWallTime != -1) {	// not the first time!
      startOfWallTime = previous_thread.startOfWallTime;
      wallTime   = endOfWallTime - startOfWallTime;
      if (wallTime < 0) {  // don't expect this to happen
	VM.sysWrite("***VM_HPM.updateHPMcounters(",previous_thread.getIndex());
	VM.sysWrite(") wall time overflowed: current ",endOfWallTime);
	VM.sysWrite(" - start ",startOfWallTime);VM.sysWrite(" = delta ",wallTime);
	VM.sysWriteln(" < 0!***"); wallTime = 0;
      } 
    }
    tmp_counters.counters[0] = wallTime;	// need relative time for aggregate values
    // read counters
    for (int i=1; i<=n_counters; i++) {
      long value = VM_SysCall.sysHPMgetCounterMyThread(i);
      tmp_counters.counters[i] = value;
    }
    if (VM_HardwarePerformanceMonitors.hpm_trace) {     // tracing on ?
      if (active) { 			// only acccumulate what is recorded!
	tmp_counters.accumulate(                 vp_counters, n_counters);
	tmp_counters.accumulate(previous_thread.hpm_counters, n_counters);
	int tid        = previous_thread.getIndex();
	int global_tid = (timerInterrupted?previous_thread.getGlobalIndex():-previous_thread.getGlobalIndex());
	tracing(tid, global_tid, startOfWallTime, endOfWallTime, tmp_counters, threadSwitch);
      }
    } else {			 	// always accumulate
      tmp_counters.accumulate(                 vp_counters, n_counters);
      tmp_counters.accumulate(previous_thread.hpm_counters, n_counters);
    }

    VM_SysCall.sysHPMresetMyThread();
    VM_SysCall.sysHPMstartMyThread();
    //-#endif
  }

  // number of trace records missed due to both buffers being full
  private int  missed_records = 0;
  public  int missedRecords() { return missed_records; }

  // number of trace records written
  private int n_records = 0;
  public  int numberOfRecords() { return n_records; }

  /*
   * OUTPUT buffers 
   * Buffering scheme.  When a buffer gets full, activate consumer to write full buffer to
   * disk and have this produce switch to the other buffer.
   *
   * Only at thread switch time are the buffers written to.
   */
  static private byte    ONE   = 1;			// first buffer
  static private byte    TWO   = 0;			// second buffer
  private byte    buffer_code = ONE;	// name of buffer to use.
  private byte[]  buffer      = null;	// buffer to use
  private int     index       = 0;	// index into buffer

  // double buffer output
  private byte[]  buffer_1            = null;	// output buffer for HPM counter values
  private int     index_1             = 0;	// output buffer index
  private byte[]  buffer_2            = null;	// output buffer for HPM counter values
  private int     index_2             = 0;	// output buffer index

  /**
   * Record HPM counter values.
   * Trace record contains:
   *   (tid(16) & buffer_code(1) & thread_switch(1) & vpid(10 & trace_format(4)) (int), 
   *   global_tid(int), startOfWallTime(long), endOfWallTime(long), mid1(int), mid2(int) counters(long)*
   *
   * CONSTRAINT: only called if VM_HardwarePerformanceMonitors.hpm_trace is true.
   * CONSTRAINT: only write to buffer when a valid buffer is found.
   * CONSTRAINT: only called if active is true
   *
   * To save space, the following 32-bit encoding is used:
   *    tid(16), buffer_code(1), thread_switch(1), vpid(10), format(4)
   * this allows easy access to format as format & 0x000F.
   *
   * @param tid              thread id (positive if timer interrupted)
   * @param global_tid       globally unique thread id (positive if timer interrupted)
   * @param startOfWallTime  global clock time when thread was scheduled
   * @param endOfWallTime    global clock time when thread was swapped out
   * @param counters         HPM counter values
   * @param threadSwitch     true if thread switch occuring.
   */
  private void tracing(int tid, int global_tid, long startOfWallTime, 
		       long endOfWallTime, HPM_counters counters, boolean threadSwitch) 
  {
    //-#if RVM_WITH_HPM
    // which buffer to use
    if (! processCallbacksFromConsumer()) return;
    if (! pickBuffer(VM_HardwarePerformanceMonitors.getRecordSize())) return;

    // buffer != null only if active==true

    //BEGIN HRM
    if (VM_HardwarePerformanceMonitors.verbose>=6) VM.sysWriteln("begin method stuff");
    int callee_MID;
    int caller_MID;
    if (cmidAvailable) {
      if (callee_CMID<1 || callee_CMID>=VM_CompiledMethods.numCompiledMethods()) {
	callee_MID = CMID_OUT_OF_BOUNDS_MID;
	VM.sysWriteln("***VM_HPM.tracing() CALLEE_CMID_OUT_OF_BOUNDS_MID*** ",callee_CMID);
	VM.sysExit(-1);
      } else {
	final VM_CompiledMethod cm1 = VM_CompiledMethods.getCompiledMethod(callee_CMID);
	if (cm1==null) {
	  callee_MID = CMID_WITHOUT_COMPILED_METHOD_MID;
	  VM.sysWriteln("***VM_HPM.tracing() CALLEE_CMID_WITHOUT_COMPILED_METHOD_MID*** ",callee_CMID);
	  VM.sysExit(-1);
	} else {
	  final VM_Method m1 = cm1.getMethod();
	  if (m1==null) {
	    callee_MID = COMPILED_METHOD_WITHOUT_METHOD_MID;
	    VM.sysWriteln("***VM_HPM.tracing() CALLEE_CMID_WITHOUT_METHOD_MID*** ",callee_CMID);
	    VM.sysExit(-1);
	  } else {
	    callee_MID = m1.getId();
	    /**
	    VM.sysWrite("m1: ["); VM.sysWrite(m1.getId()); VM.sysWrite("] ");
	    VM.sysWrite(m1.getDeclaringClass().getDescriptor());
	    VM.sysWrite(m1.getName()); VM.sysWrite(m1.getDescriptor()); VM.sysWriteln();
	    **/
	  }
	}
      }
      if (caller_CMID<1 || caller_CMID>=VM_CompiledMethods.numCompiledMethods()) {
	caller_MID = CMID_OUT_OF_BOUNDS_MID;
	VM.sysWriteln("***VM_HPM.tracing() CALLER_CMID_OUT_OF_BOUNDS_MID*** ",caller_CMID);
	VM.sysExit(-1);
      } else {
	final VM_CompiledMethod cm2 = VM_CompiledMethods.getCompiledMethod(caller_CMID);
	if (cm2==null) {
	  caller_MID = CMID_WITHOUT_COMPILED_METHOD_MID;
	  VM.sysWriteln("***VM_HPM.tracing() CALLER_CMID_WITHOUT_COMPILED_METHOD_MID*** ",caller_CMID);
	  VM.sysExit(-1);
	} else {
	  final VM_Method m2 = cm2.getMethod();
	  if (m2==null) {
	    caller_MID = COMPILED_METHOD_WITHOUT_METHOD_MID;
	    VM.sysWriteln("***VM_HPM.tracing() CALLER_CMID_WITHOUT_METHOD_MID*** ",caller_CMID);
	    VM.sysExit(-1);
	  } else {
	    caller_MID = m2.getId();
	    /**
	    VM.sysWrite("m2: ["); VM.sysWrite(m2.getId()); VM.sysWrite("] ");
	    VM.sysWrite(m2.getDeclaringClass().getDescriptor());
	    VM.sysWrite(m2.getName()); VM.sysWrite(m2.getDescriptor()); VM.sysWriteln();
	    **/
	  }
	}
      }
      //VM_Thread.dumpCallStack();
      cmidAvailableCount++;
      cmidAvailable = false;
    } else {
      cmidUnavailableCount++;
      VM.sysWrite("***VM_HPM.tracing() cmidAvailable == false***");
      VM.sysWrite("cmidAvailableCount:   ");
      VM.sysWriteln(cmidAvailableCount);
      VM.sysWrite("cmidUnavailableCount:   ");
      VM.sysWriteln(cmidUnavailableCount);
      //VM.sysWriteln("cmid NOT available");
      VM_Thread.dumpCallStack();
      callee_MID = UNAVAILABLE_MID;
      caller_MID = UNAVAILABLE_MID;
    }
    if (VM_HardwarePerformanceMonitors.verbose>=6) VM.sysWriteln("end method stuff");
    //END HRM

    int thread_switch = (threadSwitch==true?1:0);
    int encoding = (tid  << 16) + (buffer_code << 15) + (thread_switch << 14) + 
                   (vpid <<  4) + TRACE_FORMAT;
    if(VM_HardwarePerformanceMonitors.verbose>=5 || VM_HardwarePerformanceMonitors.hpm_trace_verbose == vpid) {
      if (threadSwitch) VM.sysWrite(" ");
      else              VM.sysWrite("*");
      VM.sysWrite(index,": ");
      VM.sysWrite(TRACE_FORMAT);
      VM.sysWrite(" BC ",buffer_code);
      VM.sysWrite(" PID ", vpid);  
      //      VM.sysWrite(" ("); VM.sysWriteHex(encoding); VM.sysWrite(")");
      VM.sysWrite(" GTID "); if (global_tid < 10) VM.sysWrite(" ");VM.sysWrite(global_tid);
      VM.sysWrite( " TID "); if (       tid < 10) VM.sysWrite(" ");VM.sysWrite(       tid);
      VM.sysWrite(" SWT " ); VM.sysWriteLong(startOfWallTime);
      VM.sysWrite(" EWT " ); VM.sysWriteLong(endOfWallTime);
      //BEGIN HRM
      VM.sysWrite(" CALLEE ", callee_MID);
      VM.sysWrite(" CALLER ", caller_MID);
      //END HRM
      if(n_counters > 4) VM.sysWrite("\n  ");
    }
    if (buffer != null) { // write record header
      n_records++;
      VM_Magic.setIntAtOffset( buffer, index, encoding);	// encoding
      index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;
      VM_Magic.setIntAtOffset( buffer, index, global_tid);	// globally unique tid  
      index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;
      VM_Magic.setLongAtOffset(buffer, index, startOfWallTime);	// start of global time
      index += VM_HardwarePerformanceMonitors.SIZE_OF_LONG;
      VM_Magic.setLongAtOffset(buffer, index,   endOfWallTime);	// end   of global time
      index += VM_HardwarePerformanceMonitors.SIZE_OF_LONG;
      //BEGIN HRM
      VM_Magic.setIntAtOffset( buffer, index, callee_MID);	// callee MID
      index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;
      VM_Magic.setIntAtOffset( buffer, index, caller_MID);	// caller MID
      index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;
      //END HRM
    }
    for(int i=1; i<=n_counters; i++) {
      long value = counters.counters[i];
      if(VM_HardwarePerformanceMonitors.verbose>=5 || VM_HardwarePerformanceMonitors.hpm_trace_verbose == vpid) {
	VM.sysWrite(" ",i,": ");VM.sysWriteLong(value); 
      }
      if (buffer != null) { // write HPM counter values
	VM_Magic.setLongAtOffset(buffer, index, value);		  
	index += VM_HardwarePerformanceMonitors.SIZE_OF_LONG;
      }
    }
    if (VM_HardwarePerformanceMonitors.verbose>=5 || 
	VM_HardwarePerformanceMonitors.hpm_trace_verbose == vpid) {
      VM.sysWriteln();
    }
    updateBufferIndex();
    //-#endif
  }
  /**
   * Because we are double buffering the output to the trace file,
   * pick the appropriate buffer to use.
   * This routine sets buffer and index to the active variables: either 
   * buffer_1 and index_1, or buffer_2 and index_2.
   * If both buffers are full, complain and count the number of times this
   * condition arises.
   * CONSTRAINT: always called when active is true.
   *
   * @param record_size size (in bytes) of record to be written to buffer
   * @return false      a buffer can't be found.
   */
  private boolean pickBuffer(int record_size) 
  {
    if (buffer_code == ONE) {
      if (index_1 + record_size > OUTPUT_BUFFER_SIZE) {
	if (! consumer.isActive()) {
	  // swap buffers and activate consumer to write full buffer to disk
	  buffer = buffer_2; index = index_2; buffer_code = TWO; 
	  activateConsumer();
	  return true;
	} else {
	  if(VM_HardwarePerformanceMonitors.verbose>=3)
	    VM.sysWriteln("***VM_HPM.pickBuffer() missed trace record when buffer_code == ONE!***");
	  missed_records++;
	  buffer = null;
	  return false;
	}
      } else {
	buffer = buffer_1; index = index_1; buffer_code = ONE;
	return true;
      }
    } else if (buffer_code == TWO) { 
      if (index_2 + record_size > OUTPUT_BUFFER_SIZE) {
	if (! consumer.isActive()) {
	  // swap buffers and activate consumer to write full buffer to disk
	  buffer = buffer_1; index = index_1; buffer_code = ONE; 
	  activateConsumer();
	  return true;
	} else {
	  if(VM_HardwarePerformanceMonitors.verbose>=3)
	    VM.sysWriteln("***VM_HPM.pickBuffer() missed trace record when buffer_code == TWO!***");
	  missed_records++;
	  buffer = null;
	  return false;
	}
      } else {
	buffer = buffer_2; index = index_2;  buffer_code = TWO;
	return true;
      }
    } else {
      VM.sysWriteln("***VM_HPM.pickBuffer() buffer_code ",buffer_code," not 1 or 2!***");
      return false;
    }
  }
  /*
   * Update buffer index (index is not a reference!)
   * This method could be eliminated by defining a class that represents
   * a buffer as a byte array and an index.
   */
  private void updateBufferIndex()
  {
    // reset buffer's index value
    if (buffer != null) {
      if        (buffer_code == ONE) {
	index_1 = index;
      } else if (buffer_code == TWO) {
	index_2 = index;
      }
    }
  }
  /**
   * Blackboard for consumer to notify that a callback has occurred.
   * Provide entry points for each call back supported.
   * Because locking is prohibited at thread switch time, coordination
   * between the consumer and the producer are handled through a blackboard.
   * Each callback has its own blackboard to record the callback.
   * The consumer updates the callbacks blackboard.
   *
   * This interface is fagile if multiple callbacks of the same type occur
   * before the first one is processed, initial callbacks will be lost.  
   * Other race conditions are possible.  
   * ASSUMPTIONS: multiple callbacks of the same type occur less frequently than thread switching.
   * <p>
   * NOTE: Need to store application name as an array instead of a String because 
   * String.length() is interruptible!
   */
  // notify application start black board
  private boolean notifyAppStart          = false;
  private byte[]  start_app_name          = null;
  
  // notify application complete black board
  private boolean notifyAppComplete       = false;
  private byte[]  complete_app_name       = null;
  
  // notify application run start black board
  private boolean notifyAppRunStart       = false;
  private byte[]  start_app_run_name      = null;
  private int     start_app_run           = -1;
  
  // notify application run complete black board
  private boolean notifyAppRunComplete    = false;
  private byte[]  complete_app_run_name   = null;
  private int     complete_app_run        = -1;

  /*
   * Before writing a trace record containing the HPM counter values
   * to the buffer, check if any callbacks have occurred.
   */
  private boolean processCallbacksFromConsumer() 
  {
    int padding = 0;
    int buffer_length = 0;
    if (notifyAppRunComplete) {
      buffer_length = 4 + 4 + 4 + 4 + complete_app_run_name.length;
      padding = complete_app_run_name.length % 4;
      if (padding != 0) {
	buffer_length += 4 + 4 + padding;
      }
      if (! pickBuffer(buffer_length)) return false;
      writeAppRun(COMPLETE_APP_RUN_FORMAT, complete_app_run, complete_app_run_name, padding);
      notifyAppRunComplete = false; complete_app_run = -1; complete_app_run_name = null;
      updateBufferIndex();
      n_records++;
    }
    if (notifyAppRunStart) {
      buffer_length = 4 + 4 + 4 + 4 + start_app_run_name.length;
      padding = start_app_run_name.length % 4;
      if (padding != 0) {
	buffer_length += 4 + 4 + padding;
      }
      if (! pickBuffer(buffer_length)) return false;
      writeAppRun(START_APP_RUN_FORMAT, start_app_run, start_app_run_name, padding);
      notifyAppRunStart = false;      start_app_run = -1;     start_app_run_name = null;
      updateBufferIndex();
      n_records++;
    }
    if (notifyAppComplete) {
      buffer_length = 4 + 4 + complete_app_name.length;
      padding = complete_app_name.length % 4;
      if (padding != 0) {
	buffer_length += 4 + 4 + padding;
      }
      if (! pickBuffer(buffer_length)) return false;
      writeApp(COMPLETE_APP_FORMAT, complete_app_name, padding);
      notifyAppComplete = false;                               complete_app_name = null;
      updateBufferIndex();
      n_records++;
    }
    if (notifyAppStart) {
      buffer_length = 4 + 4 + start_app_name.length;
      padding = start_app_name.length % 4;
      if (padding != 0) {
	buffer_length += 4 + 4 + padding;
      }
      if (! pickBuffer(buffer_length)) return false;
      writeApp(START_APP_FORMAT, start_app_name, padding);
      notifyAppStart = false;                                     start_app_name = null;
      updateBufferIndex();
      n_records++;
    }

    return true;
  }
  /*
   * Assume buffer and index are set appropriately.
   */
  private void writeApp(int FORMAT, byte[] app, int padding)
  {
    VM_Magic.setIntAtOffset( buffer, index, FORMAT);					// format
    index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;

    index = VM_HardwarePerformanceMonitors.writeStringToBuffer(buffer, index, app);	// app name

    if (VM_HardwarePerformanceMonitors.verbose>=3) {
      VM.sysWrite  ("writeApp(",FORMAT,", ");
      VM.sysWrite  (") n_records ",n_records);
      VM.sysWriteln(", missed ",missed_records);
    }
    if (padding != 0) {
      addPadding(4-padding);
    }
  }

  private void writeAppRun(int FORMAT, int run, byte[] app, int padding)
  {
    VM_Magic.setIntAtOffset( buffer, index, FORMAT);					// format
    index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;
    
    VM_Magic.setIntAtOffset( buffer, index, run);					// run
    index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;
    
    index = VM_HardwarePerformanceMonitors.writeStringToBuffer(buffer, index, app);	// app name

    if (VM_HardwarePerformanceMonitors.verbose>=3) {
      VM.sysWrite  ("writeAppRun(",FORMAT,", ",run);
      VM.sysWrite  (") n_records ",n_records);
      VM.sysWriteln(", missed ",missed_records);
    }
    if (padding != 0) {
      addPadding(4-padding);
    }
  }

  /*
   * This method forces additional trace records to force 4 byte alignments.
   *
   * Needed to be able to detect end of file when reading trace file.
   *
   * @param padding  amount of padding to add
   */
  private void addPadding(int padding)
  {
    if (padding==0) {
      if (VM_HardwarePerformanceMonitors.verbose>=3) {
	VM.sysWrite  ("***addPadding(",padding,") called with pad length of 0!***");
      }
      return;
    }
    byte pad = 0;
    VM_Magic.setIntAtOffset( buffer, index, PADDING_FORMAT);				// format
    index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;

    VM_Magic.setIntAtOffset( buffer, index, padding);					// length
    index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;

    for (int i=0; i<padding; i++) {							// add padding
      VM_Magic.setByteAtOffset( buffer, index, pad);	
      index += VM_HardwarePerformanceMonitors.SIZE_OF_BYTE;
    }
    if (VM_HardwarePerformanceMonitors.verbose>=3) {
      VM.sysWriteln("addPadding(",padding,") index ", index);
    }
    n_records++;
  }

  /*
   * General entry points.
   */
  /**
   * Get this virtual processors hardware counters. 
   * @return HPM counters
   */
  public  HPM_counters vp_counters() throws VM_PragmaLogicallyUninterruptible {
    return vp_counters; 
  }
  /*
   * Entry points for consumer (VM_TraceWriter)
   */

  /**
   * Called when notifyAppStart callback occurs.
   * Assume callback made to each virtual processor separately.
   *
   * @param app    application name
   */
  public void notifyAppStart(String app)  throws VM_PragmaLogicallyUninterruptible
  {
    start_app_name = app.getBytes();
    notifyAppStart = true;
  }
  /**
   * Called when notifyAppComplete callback occurs.
   *
   * @param app    application name
   */
  public void notifyAppComplete(String app)  throws VM_PragmaLogicallyUninterruptible
  {
    complete_app_name = app.getBytes();
    notifyAppComplete = true;
  }
  /**
   * Called when notifyAppRunStart callback occurs.
   *
   * @param app    application name
   * @param run    number of run
   */
  public void notifyAppRunStart(String app, int run)  throws VM_PragmaLogicallyUninterruptible
  {
    start_app_run_name = app.getBytes();
    start_app_run      = run;
    notifyAppRunStart  = true;
  }
  /**
   * Called when notifyAppRunComplete callback occurs.
   *
   * @param app    application name
   * @param run    number of run
   */
  public void notifyAppRunComplete(String app, int run)  throws VM_PragmaLogicallyUninterruptible
  {
    complete_app_run_name = app.getBytes();
    complete_app_run      = run;
    notifyAppRunComplete  = true;
  }
  /**
   * dump statistics.
   * Side effects are to reset n_records.
   */
  public void dumpStatistics() throws VM_PragmaLogicallyUninterruptible {
      VM.sysWrite("VM_HPM.dumpStatistics() wrote ",
		  n_records," records");
      n_records = 0;
  }


  /**
   * Which buffer is current?
   */
  public String getNameOfCurrentBuffer() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code == ONE) { return "ONE"; } 
    else if (buffer_code == TWO) { return "TWO"; }
    else {
      VM.sysWrite("***VM_HPM.getNameOfCurrentBuffer() buffer_code = ",buffer_code,", but must be 1 or 2!***");
      return null; 
    }
  }
  /**
   * Called from consumer to flush buffer at notifyExit time.
   */
  public byte[] getCurrentBuffer() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code == ONE) { return buffer_1; } 
    else if (buffer_code == TWO) { return buffer_2; }
    else {
      VM.sysWrite("***VM_HPM.getCurrentBuffer() buffer_code = ",buffer_code,", but must be 1 or 2!***");
      return null; 
    }
  }
  /**
   * Called from consumer to flush buffer at notifyExit time.
   */
  public int getCurrentIndex() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code == ONE) { return index_1; } 
    else if (buffer_code == TWO) { return index_2; } 
    else { 
      VM.sysWrite("***VM_HPM.getCurrentIndex() buffer_code = ",buffer_code,", but must be 1 or 2!***");
      VM.shutdown(VM.exitStatusHPMTrouble);
    }
    return -1; 
  }
  /**
   * Called from consumer after trace file is opened.
   */
  public void resetCurrent() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code==ONE) { index_1 = 0; }
    else if (buffer_code==TWO) { index_2 = 0; }
    else {
      VM.sysWrite("***VM_HPM.resetCurrent() buffer_code = ",buffer_code,", but must be 1 or 2!***");
    }
  }
  /**
   * Which buffer is full?
   */
  public String getNameOfFullBuffer() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code == ONE) { return "TWO"; } 
    else if (buffer_code == TWO) { return "ONE"; }
    else {
      VM.sysWrite("***VM_HPM.getNameOfFullBuffer() buffer_code = ",buffer_code,", but must be 1 or 2!***");
      return null; 
    }
  }
  /**
   * Called from consumer to get buffer to consume.
   */
  public byte[] getFullBuffer() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code == ONE) { return buffer_2; } 
    else if (buffer_code == TWO) { return buffer_1; }
    else {
      VM.sysWrite("***VM_HPM.getFullBuffer() buffer_code = ",buffer_code,", but must be 1 or 2!***");
      return null; 
    }
  }
  /**
   * Called from consumer to get end of full buffer.
   */
  public int getFullIndex() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code == ONE) { return index_2; } 
    else if (buffer_code == TWO) { return index_1; } 
    else { 
      VM.sysWrite("***VM_HPM.getFullIndex() buffer_code = ",buffer_code,", but must be 1 or 2!***");
      VM.shutdown(VM.exitStatusHPMTrouble);
    }
    return -1; 
  }
  /*
   * Called from consumer to reset full buffer index.
   */
  public void resetFull() throws VM_PragmaLogicallyUninterruptible
  {
    if      (buffer_code==ONE) { index_2  = 0; }
    else if (buffer_code==TWO) { index_1  = 0; }
    else {
      VM.sysWrite("***VM_HPM.resetFull() buffer_code = ",buffer_code,", but must be 1 or 2!***");
    }
  }
}

