/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.*;
import java.io.*;

/**
 * A VM_TraceWriter offloads interruptible work during a thread switch.
 * Because a trace record is procuced when Jikes RVM is in the midst of a 
 * thread switch, the operations that can be perform are limited.
 * More complicated operations, such as IO, must be off loaded to the VM_TraceWriter.
 * In particular, VM_TraceWriter opens and closes the trace file, and notifies the 
 * producer of events that occur in the applications execution.
 * <p>
 * There is one VM_TraceWriter (consumer) associated with every 
 * VM_HardwarePerformanceMonitor (producer) object.
 * We use processorAffinity to bind a VM_TraceWriter thread to the VM_Processor associated 
 * with the VM_HardwarePerformanceMonitor that VM_TraceWriter is associated with.
 * <p>
 * VM_TraceWriter provides entry points for VM_Callbacks events.
 * Care must be taken to prevent race conditions as the thread that handles
 * the call back will not be VM_TraceWriter and might be partially through
 * handling the event when a thread switch occurs.
 * We maintain the constraint that only the VM_TraceWriter writes the buffers
 * to disk and closes the trace file.
 * <p>
 * The Startup callback, called from MainThread.run(), opens the trace file.
 * The Exit callback, called from VM.sysExit, wakes up VM_TraceWriter to 
 * flush buffers and close the trace file.
 * For any other call back (AppStart, AppComplete, AppRunStart, and AppRunComplete)
 * if a trace file is open, VM_TraceWriter notifies its producer of the call back
 * by calling a method defined by the producer.
 * The coordination between the producer and this consumer is fragile, because
 * we don't use synchronization to minimize overhead.  
 * <p>
 * This class has behavior that is similar as an organizer in the adaptive optimization system.
 * 
 * @author Peter Sweeney
 * @date 2/6/2003
 */
class VM_TraceWriter extends VM_ThreadSwitchConsumer 
implements   VM_Callbacks.StartupMonitor,           VM_Callbacks.ExitMonitor,
            VM_Callbacks.AppStartMonitor,    VM_Callbacks.AppCompleteMonitor,
         VM_Callbacks.AppRunStartMonitor, VM_Callbacks.AppRunCompleteMonitor
{
  /*
   * output trace file
   */
  private FileOutputStream trace_file = null;
  // virtual processor id
  private int pid                = 0;
  public int getPid() throws VM_PragmaUninterruptible { return pid; }

  // Flag for when to close the trace file.
  // At notifyExit time, producer sets flag to true.
  public boolean notifyExit = false;
  /**
   * Consumer Constructor
   *
   * @param producer         the associated producer
   */
  VM_TraceWriter(VM_ThreadSwitchProducer producer, int pid) 
  { 
    if(VM_HardwarePerformanceMonitors.verbose>=2) {
      VM.sysWriteln("VM_TraceWriter(",pid,") constructor");
    }
    this.producer = producer;
    this.pid      = pid;
    // virtual processor that this thread wants to run on!
    processorAffinity = VM_Scheduler.processors[pid];
    producer.setConsumer(this);
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      setupCallbacks();
    }
    makeDaemon(true);
  }
  /**
   * Consumer specific setup.  
   * A good place to install and activate any producers.
   */
  protected void initialize() {
  }

  /**
   * An abstract VM_ThreadSwitchConsumer method.
   *
   * Called when:
   * 1) the trace buffer is full.
   *    Write the full buffer to disk and reset the buffer.
   * 2) notify exit has been called and producer has written notify exit trace record.
   *    Write current buffer to disk and close file.
   */
  void thresholdReached() 
  {
    VM_HardwarePerformanceMonitor hpm = (VM_HardwarePerformanceMonitor)producer;
    if (notifyExit == true) {
      // flush current buffer
      if(VM_HardwarePerformanceMonitors.verbose>=4)
	VM.sysWriteln("VM_TraceWriter.thresholdReached() notifyExit flush current buffer ",
		      hpm.getNameOfCurrentBuffer());
      byte[] buffer = hpm.getCurrentBuffer();
      int    index  = hpm.getCurrentIndex();
      writeFileOutputStream(buffer, index);
      closeFileOutputStream();
    } else {
      // flush full buffer
      if(VM_HardwarePerformanceMonitors.verbose>=4)
	VM.sysWriteln("VM_TraceWriter.thresholdReached() write full buffer ",hpm.getNameOfFullBuffer());
      byte[] buffer = hpm.getFullBuffer();
      int    index  = hpm.getFullIndex();
      writeFileOutputStream(buffer, index);
      hpm.resetFull();    
    }
  }

  /*
   * Open FileOutputStream file to write HPM trace records!
   * CONSTRAINT: trace_file is null
   * Actions:
   *  Open file
   *  Write header information 
   *  Initialize producers buffers
   *  Activate producer.
   *
   * @param trace_file_name name of file to open
   */
  private void openFileOutputStream(String trace_file_name)
  {
    if(VM_HardwarePerformanceMonitors.verbose>=2)VM.sysWriteln("VM_TraceWriter.openFileOutputStream(",trace_file_name,")");

    if (trace_file != null) {	// constraint
      VM.sysWriteln("***VM_TraceWriter.openFileOutputStream(",trace_file_name,") trace_file != null!***");      
      new Exception().printStackTrace(); VM.shutdown(-1);
    }

    try {
      trace_file = new FileOutputStream(trace_file_name);
    } catch (FileNotFoundException e) {
      VM.sysWriteln("***VM_TraceWriter.openFileOutputStream() FileNotFound exception with new FileOutputStream("+trace_file_name+")");
      e.printStackTrace(); VM.shutdown(-1);
    } catch (SecurityException e) {
      VM.sysWriteln("***VM_TraceWriter.openFileOutputStream() Security exception with new FileOutputStream("+trace_file_name+")");
      e.printStackTrace(); VM.shutdown(-1);
    } 
    writeHeader();
    ((VM_HardwarePerformanceMonitor)producer).resetCurrent();

    // tell producer it is okay to produce
    ((VM_HardwarePerformanceMonitor)producer).activate();
  }
  /*
   * Write header information whenever a HPM OutputFileStream is opened!
   * Header consists of:
   *   int version_number
   *   String name of header file
   */
  private void writeHeader()
  {
    if(VM_HardwarePerformanceMonitors.verbose>=2){ VM.sysWriteln("VM_TraceWriter.writeHeader() PID ",pid); }

    byte[] buffer   = new byte[32+(10*100)];	// temporary buffer
    int    index    = 0;
    
    // write version number 
    int version_number = VM_HardwarePerformanceMonitors.hpm_info.version_number;
    VM_Magic.setIntAtOffset(buffer, index, version_number);
    index += VM_HardwarePerformanceMonitors.SIZE_OF_INT;
    // write name of header file
    if(VM_HardwarePerformanceMonitors.verbose>=4) {
      VM.sysWriteln("VM_TraceWriter.writeHeader() write headerFilename \"",VM_HardwarePerformanceMonitors.hpm_info.headerFilename(),"\"");
    }
    index = VM_HardwarePerformanceMonitors.writeStringToBuffer(buffer, index, VM_HardwarePerformanceMonitors.hpm_info.headerFilename().getBytes());

    // write header to file.
    writeFileOutputStream(buffer, index);
  }

  /*
   * Write a buffer of length length to FileOutputStream!
   * Writes from buffer for length bytes.
   * CONSTRAINT: trace file has been opened.
   *
   * @param buffer bytes to write to file
   * @param length number of bytes to write 
   */
  public void writeFileOutputStream(byte[] buffer, int length)
  {
    if(VM_HardwarePerformanceMonitors.verbose>=4)VM.sysWriteln("VM_TraceWriter.writeFileOutputStream(buffer, 0, ",length,")");
    if (length <= 0) return;
    if (trace_file == null) { 	// constraint
      VM.sysWriteln("\n***VM_TraceWriter.writeFileOutputStream() trace_file == null!  Call VM.shutdown(-9)***");
      VM.shutdown(-9);
    }
    try {
      // allow only one writer at a time to trace file.
      synchronized(trace_file) {
	trace_file.write(buffer, 0, length);
      }
    } catch (IOException e) {
      VM.sysWriteln("***VM_TraceWriter.writeFileOutputStream(",length,") throws IOException!***");
      e.printStackTrace(); VM.shutdown(-1);
    }
  }
  /*
   * Close HPM FileOutputStream and set trace_file to null!
   * Actions:
   *  close file
   *
   * Relaxed constraint: trace_file is not null!
   */
  private void closeFileOutputStream()
  {
    if(VM_HardwarePerformanceMonitors.verbose>=2)VM.sysWriteln("VM_TraceWriter.closeFileOutputStream()");
    if (trace_file == null) {	// constraint
      if(VM_HardwarePerformanceMonitors.verbose>=3)
	VM.sysWriteln("\n***VM_TraceWriter.closeFileOutputStream() trace_file == null!***\n");
      return;
    }
    try {
       trace_file.close();
    } catch (IOException e) {
      VM.sysWriteln("***VM_TraceWriter.closeFileOutputStream() throws IOException!***");
      e.printStackTrace(); VM.shutdown(-1);
    }

    trace_file = null;

    if(VM_HardwarePerformanceMonitors.verbose>=3){
      ((VM_HardwarePerformanceMonitor)producer).dumpStatistics();
    }
  }

  /*********************************
   * VM callbacks
   *********************************/
  /**
   * If tracing, set up call backs to manipulate files
   * Manages tracing functionality.
   * Because anyone can place a call back anywhere, these
   * methods must be robust.
   */
  private void setupCallbacks()
  {
    //-#if RVM_WITH_HPM
    VM_Callbacks.addStartupMonitor(this);
    VM_Callbacks.addExitMonitor(this);
    VM_Callbacks.addAppStartMonitor(this);
    VM_Callbacks.addAppCompleteMonitor(this);
    VM_Callbacks.addAppRunStartMonitor(this);
    VM_Callbacks.addAppRunCompleteMonitor(this);
    //-#endif
  }
  /**
   * Called when the VM is starting up.
   * Assumed called once.
   * Actions:
   *  Open trace file. 
   */
  public void notifyStartup()
  {
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      if(VM_HardwarePerformanceMonitors.verbose>=2){ VM.sysWriteln("VM_TraceWriter.notifyStartup() PID ",pid); }
      if (trace_file != null) {
	VM.sysWriteln("***VM_TraceWriter.notifyStartup() pid ",pid," trace_file != null!***");
	VM.sysExit(-1);
      }
      int n_processors = VM_Scheduler.numProcessors;
      String file_name = VM_HardwarePerformanceMonitors.hpm_info.filenamePrefix+"."+pid+".startup";
      if(VM_HardwarePerformanceMonitors.verbose>=4) VM.sysWriteln(" file name \"",file_name,"\"");
      openFileOutputStream(file_name);

    }
  }
  /**
   * Called when the VM is about to exit to tear down HPM tracing.
   * Assumed called once.
   * Notify producer that notifyExit was called.
   *
   * The thread that executes this method is not necessarily the thread that
   * produces the trace records.
   *
   * @param value the exit value
   */
  public void notifyExit(int value)
  {
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      if(VM_HardwarePerformanceMonitors.verbose>=2) { 
	VM.sysWriteln("VM_TraceWriter.notifyExit(",value,") PID ",pid); 
      }
      if (trace_file == null) {
	VM.sysWriteln("\n***VM_TraceWriter.notifyExit() PID ",pid," trace_file == null! notifyStartup never called!***\n");
	VM.sysExit(-1);
      }

      ((VM_HardwarePerformanceMonitor)producer).notifyExit(value);
    }
  }

  /**
   * Called when the application starts.
   * Actions:
   *  notify producer
   *
   * @param app   name of application
   */
  public void notifyAppStart(String app)
  {
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      if(VM_HardwarePerformanceMonitors.verbose>=2){ VM.sysWriteln("VM_TraceWriter.notifyAppStart(",app,") PID ",pid); }
      if (trace_file == null) {
	VM.sysWriteln("\n***VM_TraceWriter.notifyAppStart() pid ",pid," trace_file == null!***\n");
	return;
	//	VM.sysExit(-1);
      }
      ((VM_HardwarePerformanceMonitor)producer).notifyAppStart(app);
    }
  }
  /**
   * Called when the application completes
   * Actions:
   *  notify producer
   *
   * param app   name of application
   */
  public void notifyAppComplete(String app)
  {
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      if(VM_HardwarePerformanceMonitors.verbose>=2){ VM.sysWriteln("VM_TraceWriter.notifyAppComplete(",app,") PID ",pid); }
      if (trace_file == null) {
	VM.sysWrite(  "\n***VM_TraceWriter.notifyAppComplete(",app,") PID ",pid);
	VM.sysWriteln(" trace_file == null! notifyAppStart() never called!***\n");
	return;
	//	VM.sysExit(-1);
      }
      ((VM_HardwarePerformanceMonitor)producer).notifyAppComplete(app);
    }
  }
  /**
   * Called when the application starts one of its run
   * Actions:
   *  notify producer
   *
   * param app   name of application
   * param run   run number
   */
  public void notifyAppRunStart(String app, int run)
  {
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      if(VM_HardwarePerformanceMonitors.verbose>=2){ 
	VM.sysWrite("VM_TraceWriter.notifyAppRunStart(",app,", ",run);
	VM.sysWriteln(") PID ",pid);
      }
      if(trace_file == null) {
	VM.sysWrite  ("***VM_TraceWriter.notifyAppRunStart(",app,", ",run);
	VM.sysWrite  (") PID ",pid);
	VM.sysWriteln(" trace_file == null!***");
	return;
	// VM.sysExit(-1);
      }
      ((VM_HardwarePerformanceMonitor)producer).notifyAppRunStart(app,run);
    } 
  }
  /**
   * Called when the application completes one of its run
   * Actions:
   *  notify producer
   *
   * param app   name of application
   * param run   run number
   */
  public void notifyAppRunComplete(String app, int run)
  {
    if (VM_HardwarePerformanceMonitors.hpm_trace) {
      if(VM_HardwarePerformanceMonitors.verbose>=2){ VM.sysWrite("VM_TraceWriter.notifyAppRunComplete(",app,",",run);VM.sysWriteln(") PID ",pid); }
      if (trace_file == null) {
	VM.sysWrite(  "\n***VM_TraceWriter.notifyAppRunComplete(",app,",",run);
	VM.sysWriteln(") PID ",pid," trace_file == null!***\n");
	return;
	//	VM.sysExit(-1);
      }
      ((VM_HardwarePerformanceMonitor)producer).notifyAppRunComplete(app,run);
    }
  }
  /**
   * name of thread.
   */
  public String toString() throws VM_PragmaUninterruptible {
    return "VM_TraceWriter";
  }
}
