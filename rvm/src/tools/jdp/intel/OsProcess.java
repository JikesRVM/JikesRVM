/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/**
 * Abstract class for the internal and external 
 * implementation of OsProcess
 * This is the interface to debug a program in the JVM
 * The external implementation will create, control and inspect
 * the debugee in a different process, while the internal 
 * implementation will run in the same process as the debuggee.
 * Breakpoints: there are 2 set of breakpoints:
 * <ul>
 * <li> Global breakpoints (bpset):  all threads stop, user can set randomly
 *      this is implemented as a breakpointList
 * <li> Thread stepping breakpoints (threadstep):  this is used for stepping
 *      implemented as a breakpointList, one entry for each thread
 * </ul>
 *
 * @author Ton Ngo (1/6/98)
 */

import java.io.*;

abstract class OsProcess implements jdpConstants, VM_BaselineConstants {

  //*********************************************************************************
  // These will be native in the external implementation
  //*********************************************************************************

  /**
   * Abstract method:  check if the system trap is for breakpoint
   * @param status the process status from the system
   * @return true if trapping on breakpoint
   * @exception
   * @see
   */
  public abstract boolean isBreakpointTrap(int status);

  /**
   * Abstract method:  check if the system trap is to be ignored
   * and passed directly to the RVM
   * @param status the process status from the system
   * @return true if trap is to be ignored
   * @exception
   * @see
   */
  public abstract boolean isIgnoredTrap(int status);


  /**
   * Abstract method: check if the instruction is actually a trap instruction
   * if not, the signal means that a user library has been loaded, which is ignored for now
   * later if we want to debug C program, we will need to read the XCOFF info
   * from the library at this point
   * @param status the process status from the system
   * @return true if trap is to be ignored, false otherwise
   */
  // public abstract boolean isIgnoredBreakpointTrap(int status);
  public abstract boolean isIgnoredOtherBreakpointTrap(int status);
  public abstract boolean isLibraryLoadedTrap(int status);


  /**
   * Abstract method:  check if the process has been killed
   * @param status the process status from the system
   * @return true if the process has been killed
   * @exception
   * @see mwait
   */
  public abstract boolean isKilled(int status);

  /**
   * Abstract method:  check if the process has exited normally
   * @param status the process status from the system
   * @return true if the process has exited
   * @exception
   * @see
   */
  public abstract boolean isExit(int status);

  /**
   * Abstract method:  convert to String the process status indicator used by
   * the system
   * @param status the process status from the system
   * @return the status message
   * @exception
   * @see
   */
  public abstract String statusMessage(int status);

  /**
   * Abstract method: destroy the process
   * @param
   * @return
   * @exception
   * @see
   */
  public abstract void mkill();

  /**
   * Abstract method: detach the debugger from the process
   * @param
   * @return
   * @exception
   * @see
   */
  public abstract void mdetach();

  /**
   * Temporary hardware single step for Intel
   */
  public abstract boolean mstep();

  /**
   * Abstract method: wait for the process to return
   * @param
   * @return the process status from the system
   * @exception
   * @see
   */
  public abstract int mwait();

  /**
   * Abstract method: allow all threads in the process to continue execution
   * @param
   * @return
   * @exception
   * @see
   */
  public abstract void mcontinue(int signal);

  /**
   * Allow the current system thread to continue execution
   * @param
   * @return
   * @exception
   * @see
   */
  // continue by thread not supported yet on Lintel
  // public abstract void mcontinueThread(int signal);

  /**
   * Abstract method:  create the external process to execute a program
   * @param ProgramName the name of the main program as would be used in a shell
   * @param args the argument list for the program
   * @return the process id from the system
   * @exception
   * @see
   */
  public abstract int createDebugProcess(String ProgramName, String args[]);

  /**
   * Abstract method:  attach to an external process currently running
   * @param processID the system process ID
   * @param args the argument list for the program
   * @return the same process id from the system
   * @exception
   * @see
   */
  public abstract int attachDebugProcess(int processID);

  /**
   * Abstract method:  get the system thread ID
   * @return an array for the current system thread ID
   * @exception
   * @see register.getSystemThreadGPR
   */
  public abstract int[] getSystemThreadId();

  /**
   * Abstract method:  dump the system thread ID
   * @return a String array for the contents of all system threads
   * @exception
   * @see 
   */
  public abstract String listSystemThreads();  

  /**
   * Abstract method:  get the index of the VM_Thread currently loaded in the system threads
   * @return an array of VM_Thread
   * @exception
   * @see
   */
  public abstract int[] getVMThreadIDInSystemThread();

  /**
   * to access the process memory
   */
  memory mem;       

  /**
   * access for the process registers
   */
  register reg;     

  /**
   * map from mem address to boot image bytecode, methods, ...
   */
  BootMap bmap;     

  /**
   * global breakpoints
   */
  breakpointList bpset;

  /** 
   * breakpoint for stepping each thread
   */
  breakpointList threadstep;
  
  /** 
   * breakpoint for stepping each thread by source line
   */
  breakpointList threadstepLine;

  /**
   * Ignore any breakpoint/trap signal not caused by the special trap instruction
   * used by jdp:  jdpConstants.BKPT_INSTRUCTION
   * This is used in starting up the JVM: stack resizing may occur which is normal
   * which also use trap instruction; jdp needs to set a dummy breakpoint to get
   * the main Method so we need to ignore other trap
   */
  boolean ignoreOtherBreakpointTrap = false;

  /**
   * Flag to print only the current instruction or the full stack when stepping
   */
  boolean verbose;  

  /**
   * one for each thread, hold the signal pending in the debugger
   */
  int lastSignal[]; 
  


  /**
   * Constructor for the external version
   * Create the Java data structure to keep track of the OS process
   * @param ProgramName the name of the main program as would be used in a shell
   * @param args the argument list for the program
   *        use C args convention: arg[0] is the program name, arg[1-n] are the arguments
   * @param mainClass
   * @param classesNeededFilename the list of classes loaded in the boot image
   *        This is to circumvent the problems with class loading and compiling:
   *         (1) the boot image writer uses a different loader than the JDK, and
   *         (2) the compiler generates different code depending on the current
   *        environment.  (Line number will be wrong without this)
   * @param classpath  the java class path to ensure the classes are loaded in the
   *        same order as the boot image.  This causes the TOC to be filled up
   *        in the same order so that the offset to fields are identical between
   *        the TOC in the boot image and the TOC created in the JDK.
   * @return
   * @exception
   * @see
   */
  public OsProcess(String ProgramName, String args[], 
		   String mainClass, String classesNeededFilename, String classpath) {

    createDebugProcess(ProgramName, args);
    lastSignal = new int[1];                     // TODO:  multithreaded
    lastSignal[0] = 0;                           // 0 for no signal
    if (args.length>1) {
      bmap = loadMap(mainClass, classesNeededFilename, classpath);
    }
    verbose = false;
  }

  /**
   * Load the method map for the boot image
   * This is hard-coded, probably not good for the general case
   * we are expecting a C program that would load the JVM boot image
   * so the first argument arg[1] is assumed to be the name of the JVM boot image
   */
  private BootMap loadMap(String mainClass, String classesNeededFilename, String classpath) {

    try { 
      return new BootMapExternal(this, mainClass, 
				 classesNeededFilename, classpath); 
    } catch (BootMapCorruptException bme) {   
      System.out.println(bme); bme.printStackTrace();
      System.out.println("ERROR: corrupted method map");
      return new BootMapExternal(this);                        // create a dummy boot map
    } catch (Exception e) { 
      System.out.println(e); e.printStackTrace();
      System.out.println("ERROR: something wrong with method map :) ");
      return new BootMapExternal(this);                        // create a dummy boot map
    }

  }

  /**
   * Constructor for the external version
   * This version attaches to a currently running process instead of creating one
   * @param processID the system process ID of the running process
   * @param classesNeededFilename the list of classes loaded in the boot image
   *        This is to circumvent the problems with class loading and compiling:
   *         (1) the boot image writer uses a different loader than the JDK, and
   *         (2) the compiler generates different code depending on the current
   *        environment.  (Line number will be wrong without this)
   * @param classpath  the java class path to ensure the classes are loaded in the
   *        same order as the boot image.  This causes the TOC to be filled up
   *        in the same order so that the offset to fields are identical between
   *        the TOC in the boot image and the TOC created in the JDK.
   * @return
   * @exception
   * @see
   */
  public OsProcess (int processID, String mainClass, 
		    String classesNeededFilename, String classpath) throws OsProcessException 
  {
    int pid = attachDebugProcess(processID);
    if (pid==-1)
      throw new OsProcessException("Fail to attach to process");
    lastSignal = new int[1];                     // TODO:  multithreaded
    lastSignal[0] = 0;                           // 0 for no signal
    bmap = loadMap(mainClass, classesNeededFilename, classpath);
    verbose = false;
  }

  /**
   * Constructor for the internal version (no actual process)
   * @param
   * @return
   * @exception
   * @see
   */
  // public OsProcess() {
  //   lastSignal = new int[1];                     // TODO:  multithreaded
  //   lastSignal[0] = 0;                           // 0 for no signal
  //   bmap = new BootMapInternal(this);
  //   verbose = false;
  // 
  // }

  /**
   * Stepping by machine instruction, following branch
   * if there is a user breakpoint at the current address, take care to remove it,
   * step over this address, then put it back
   * @param thread the thread to control
   * @param printMode flag to display the current java source line or
   *                  machine instruction
   * @param skip_prolog
   * @return  true if the process is still running, false if it has exited
   * @exception
   * @see
   */
   public boolean pstep(int thread, int printMode, boolean skip_prolog)
  {
    boolean stillrunning;
    int addr = reg.hardwareIP();
    boolean over_branch = false;
    breakpoint bpSaved = bpset.lookup(addr);

    // set breakpoint for the next step
    // (it's more efficient to set the step breakpoint before clearing 
    // the current breakpoint:  better chance of picking up the method ID
    // without having to scan the full dictionary)
    threadstep.setStepBreakpoint(thread, over_branch, skip_prolog); 

    if (bpSaved!=null) 
      bpset.clearBreakpoint(bpSaved);   // put away any global breakpoint here

    stillrunning = continueCheckingForSignal(thread, printMode, false); 

    // clear step breakpoint 
    threadstep.clearStepBreakpoint(thread);           

    if (stillrunning && bpSaved!=null)
      bpset.setBreakpoint(bpSaved);

    // INTEL version:  doesn't work
    // do Intel single step via ptrace
    // stillrunning = mstep();
    printCurrentStatus(printMode);

    return stillrunning;
  }

  /**
   * Step by source line, over all method calls in current line:
   * To do this correctly, we need to:
   *   -keep track of thread ID in case other threads run the same code
   *   -get the breakpoint for the next valid line
   *   -
   * @param thread the thread to control (currently unused, default current thread)
   * @param printMode flag to display the current java source line or
   *                  machine instruction
   * @return true if the process is still running, false if it has exited
   * @exception
   * @see
   */
  public boolean pstepLine(int thread, int printMode) {
    boolean stillrunning = true;
    boolean lookingForNextLine = true;
    breakpoint bpSaved = bpset.lookup(reg.hardwareIP());
    
    // find the current thread
    int orig_thread = reg.hardwareTP();
    int curr_thread = -1;

    boolean success = threadstepLine.setStepLineBreakpoint(thread);

    if (!success)
    {
      return stillrunning;
    }

    if (bpSaved!=null) 
      bpset.clearBreakpoint(bpSaved);               // put away any global breakpoint here

    // System.out.println("pstepLine: looking for thread " + orig_thread);
    while (lookingForNextLine) {
      stillrunning = continueCheckingForSignal(thread, printMode, false);

      if (stillrunning) {	
	// did we stop at the expected step line breakpoint? 
	int curr_addr = reg.hardwareIP();
	curr_thread = reg.hardwareTP();
	if (threadstep.isAtStepLineBreakpoint(thread,curr_addr) &&
	    (curr_thread!=orig_thread)) {
	  // (1) another thread has stopped at this step line breakpoint
	  // let this thread continue by stepping forward one step to go past the breakpoint
	  threadstep.setStepBreakpoint(thread, false, false);
	  threadstepLine.temporaryClearStepBreakpoint(thread);
	  stillrunning = continueCheckingForSignal(thread, printMode, false);
	  threadstep.clearStepBreakpoint(thread);
	  threadstepLine.restoreStepBreakpoint(thread);
	  
	} else {
	  // (2) the expected thread reaches the step line breakpoint, or
	  // (3) we have hit a different breakpoint, abandon step line and return to console
	  lookingForNextLine = false;
	}
      } else {
	// program has exited
	return stillrunning;
      }
    }

    if (stillrunning) {
      // clear step line breakpoint     
      threadstepLine.clearStepBreakpoint(thread);                  
      
      if (bpSaved!=null)
	bpset.setBreakpoint(bpSaved);      
    }

    return stillrunning;
  }

  /**
   * Stepping by machine instruction, skipping over branch
   * if there is a user breakpoint at the current address, take care to remove it,
   * step over this address, then put it back
   * @param thread the thread to control
   * @return  true if if the process is still running, false if it has exited
   * @exception
   * @see
   */
  public boolean pstepOverBranch(int thread)
  {
    int addr = reg.hardwareIP();
    boolean over_branch = true;
    boolean stillrunning;
    boolean skip_prolog = false;
    breakpoint bpSaved = bpset.lookup(addr);

    // set breakpoint for the next step
    // (it's more efficient to set the step breakpoint before clearing 
    // the current breakpoint:  better chance of picking up the method ID
    // without having to scan the full dictionary)
    threadstep.setStepBreakpoint(thread, over_branch, skip_prolog); 

    if (bpSaved!=null)
      bpset.clearBreakpoint(bpSaved);               // put away any global breakpoint here

    stillrunning = continueCheckingForSignal(thread, PRINTASSEMBLY, false);

    // clear step breakpoint 
    threadstep.clearStepBreakpoint(thread);                  

    if (stillrunning && bpSaved!=null)
      bpset.setBreakpoint(bpSaved);

    return stillrunning;
  }

  /**
   * Common code for pstep, pstepOverBranch, pcontinue
   * @param thread the thread to control
   * @param printMode flag to display the current java source line or
   *                  machine instruction
   * @return  true if if the process is still running, false if it has exited
   * @exception
   * @see
   */
  private boolean continueCheckingForSignal(int thread, int printMode, boolean allThreads) {
    // continue by single thread not supported yet
    // if (allThreads) {
    if (lastSignal[thread]==0)          // do we have a signal pending in the debugger?
      mcontinue(0);    
    else {
      int sig = lastSignal[thread];
      lastSignal[thread] = 0;           // reset last signal when we continue
      mcontinue(sig);    
    }
    // } 
    // else {
    // 	 if (lastSignal[thread]==0)          // do we have a signal pending in the debugger?
    // 	   mcontinueThread(0);               
    // 	 else {
    // 	   int sig = lastSignal[thread];
    // 	   lastSignal[thread] = 0;           // reset last signal when we continue
    // 	   mcontinueThread(sig);    
    // 	 }
    // 
    // }

    try { 
      pwait(allThreads);                          // wait for debuggee to return
      // printCurrentStatus(printMode);
    }
    catch (OsProcessException e) {
      lastSignal[thread] = e.status;
      if (isExit(e.status)) {
	System.out.println("Program has exited.");
	return false;
      }
      else {
	System.out.println("Unexpected signal: " + statusMessage(e.status));
	System.out.println("...type cont/step to continue with this signal");
	// If you want to reinstate this line see powerPC version
	// printCurrentStatus(printMode);	
      }
    }

    return true;
  }

  
  /**
   * Print the current position in the code (source or machine)
   * This is for the hardware state, NOT for the context thread
   * @param printMode flag to display the current java source line or
   *                  machine instruction
   * @return
   * @exception
   * @see
   */
  private void printCurrentStatus(int printMode) {
    switch (printMode) {
    case PRINTNONE: break;
    case PRINTASSEMBLY:
      if (verbose) {
	try {
	  //	  mem.printJVMstack(reg.read("FP"), 4);
	  mem.printJVMstack(reg.currentFP(), 4);
	} catch (Exception e) {
	  System.out.println(e.getMessage());
	}
      } 
      System.out.println(mem.printCurrentInstr());   // print the current instruction
      break;
    case PRINTSOURCE:
      bmap.printCurrentSourceLine();  // print the current source line
      break;
    }
  }

  /**
   *
   * Step by source line, over method call:
   *    go one step forward, then if we are in a different stack frame,
   *    continue till returning to the original stack frame
   * @param thread  the thread to step
   * @return true if the process is still running, false if it has exited
   * @exception
   * @see
   */
  public boolean pstepLineOverMethod(int thread) {
    boolean stillrunning=true;
    boolean skip_prolog;
    int addr, orig_frame, curr_frame;
    String orig_line;
    String curr_line;

    // find the current source line first
    orig_frame = mem.findFrameCount();
    // System.out.println("  start from frame " + orig_frame);

    // take a step forward to see if we step into another frame
    stillrunning = pstepLine(thread, PRINTNONE);
    if (!stillrunning)
      return stillrunning;
    curr_frame = mem.findFrameCount();


    // if we step into a new frame, continue until we return
    if (orig_frame<curr_frame) {
      stillrunning = pcontinueToReturn(thread, PRINTNONE);        
      // stillrunning = pstepLine(thread, PRINTNONE);
      // System.out.println("  one more step to frame " +  mem.findFrameCount());      
    } 
    // didn't step into a method, so just return
    else {                                   
      stillrunning = true;
    }

    if (stillrunning) 
      printCurrentStatus(PRINTSOURCE);

    return stillrunning;

  }


  /**
   * Wait until the debuggee stops, check the cause of stopping:
   * <ul>
   * <li> if it's our own breakpoint, return
   * <li> if it's a breakpoint to be ignored by the debugger and 
   *      serviced by the JVM, quietly pass it on
   * <li> if it's something unexpected, raise an exception
   * </ul>
   * @param allThread flag to run all thread or only the current thread
   * @return
   * @exception OsProcessException if the system signal does not belong to the debugger
   * @see
   */
   public void pwait(boolean allThread) throws OsProcessException
  {
    int addr, inst;
    boolean skipBP=false;
    int status =  mwait();

    while (isIgnoredTrap(status) || (skipBP=isLibraryLoadedTrap(status)) ||
           isIgnoredOtherBreakpointTrap(status)) {

      if (skipBP)
	status = 0;     // reset status so the process continue normally without the signal
      // continue by thread not supported yet
      // if (allThread)
      mcontinue(status);
      // else
      // mcontinueThread(status);
      status =  mwait();
    }
     
    if (isBreakpointTrap(status)) {
      // recompute breakpoint list, some code may have moved
      bpset.relocateBreakpoint();

      // check jdp list of breakpoints to see if it's our own
      addr = reg.hardwareIP();
      if (bpset.doesBreakpointExist(addr) || 
	  threadstep.doesBreakpointExist(addr) ||
	  threadstepLine.doesBreakpointExist(addr) ) {
	return;
      } else {
	throw new OsProcessException(status);
      }
    } else {
      // don't complain during JVM startup, there may be many system traps which are normal
      if (!ignoreOtherBreakpointTrap)
        throw new OsProcessException(status);
    }

  }

  /**
   * Continue all thread execution
   * <p> To continue and still keep the user breakpoint at this address if any:
   * <ul>
   * <li> clear the user breakpoint but remember it
   * <li> step forward one instruction
   * <li> put this breakpoint back before continue on
   * </ul>
   * <p> Clear all the link breakpoints
   * If in source debugging mode, print the current source line
   * otherwise print the current assembly instruction
   * @param thread the thread to control
   * @param printMode flag to display the current java source line or
   *                  machine instruction
   * @param allThreads flag set to true if all threads are to continue,
   *                   false if only current thread is to continue
   * @return true if the process is still running, false if it has exited
   * @exception
   * @see
   */
   public boolean pcontinue(int thread, int printMode, boolean allThreads)
  {
    int addr = reg.hardwareIP();
    boolean over_branch = false;
    boolean stillrunning;
    boolean skip_prolog = false;
    breakpoint bpSaved = bpset.lookup(addr);

    if (bpSaved!=null) {
      System.out.println("pcontinue: saving current breakpoint " + 
      	     	 VM.intAsHexString(addr));
      threadstep.setStepBreakpoint(thread, over_branch, skip_prolog);   // set breakpoint for the next step
      bpset.clearBreakpoint(bpSaved);
      stillrunning = continueCheckingForSignal(thread, PRINTNONE, false);
      threadstep.clearStepBreakpoint(thread);               // clear step breakpoint 
      bpset.setBreakpoint(bpSaved);
    } 

    stillrunning = continueCheckingForSignal(thread, printMode, allThreads);

    return stillrunning;
  }


  /**
   * Continue to the return address from the last branch and link
   * (unless stopped by another breakpoint in between)
   * @param thread the thread to control
   * @param printMode flag to display the current java source line or
   *                  machine instruction
   * @return true if the process is still running, false if it has exited
   * @exception
   * @see
   */
  public boolean pcontinueToReturn(int thread, int printMode) 
  {
    int addr = reg.hardwareIP();
    boolean stillrunning;
    breakpoint bpSaved = bpset.lookup(addr);

    threadstep.setLinkBreakpoint(thread);
    bpset.clearBreakpoint(bpSaved);
    stillrunning = continueCheckingForSignal(thread, printMode, false);
    threadstep.clearStepBreakpoint(thread);                         // clear step breakpoint 

    if (stillrunning && bpSaved!=null)
      bpset.setBreakpoint(bpSaved);

    return stillrunning;
  }

  /**
   * Terminate the current debuggee
   * @param
   * @return
   * @exception
   * @see
   */
  public void pkill() 
  {
    mkill();
    try {
      pwait(true);                            // wait for debuggee to return, all threads
    }
    catch (OsProcessException e) {
      if (isExit(e.status)) 
	System.out.println("Program has exited.");
      else if (isKilled(e.status))
	System.out.println("Program terminated by user.");
      //else
      //System.out.println("Unexpected signal: " + statusMessage(e.status));
    }
  }

  /**
   * Query map
   * @param
   * @return  BootMap of this process
   * @exception
   * @see
   */
  public BootMap bootmap() {
    return bmap;
  }

  /*
   * return the counts in the thread object
   */
  private String getJ2NThreadCounts ( int threadPointer ) throws Exception {
    String result = new String();
    String temp;
    String blanks = "            ";

    if (threadPointer==0)
      return null;

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "J2NYieldCount");
      temp = Integer.toString( mem.read(threadPointer + field.getOffset()));    // it's a nonstatic field
      result += blanks.substring(1, blanks.length() - temp.length() ) + temp; 
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "J2NLockFailureCount");
      temp = Integer.toString( mem.read(threadPointer + field.getOffset()));    // it's a nonstatic field
      result += blanks.substring(1, blanks.length() - temp.length() ) + temp; 
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.RVM_Thread", "J2NTotalYieldDuration");
      temp = Integer.toString( mem.read(threadPointer + field.getOffset()));    // it's a nonstatic field
      result += blanks.substring(1, blanks.length() - temp.length() ) + temp; 
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "J2NTotalLockDuration");
      temp = Integer.toString( mem.read(threadPointer + field.getOffset()));    // it's a nonstatic field
      result += blanks.substring(1, blanks.length() - temp.length() ) + temp; 
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 

    return result;
  }

  private void zeroJ2NThreadCounts ( int threadPointer ) throws Exception {

    if (threadPointer==0)
      return;

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "J2NYieldCount");
      mem.write(threadPointer + field.getOffset(), 0);    // it's a nonstatic field
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "J2NLockFailureCount");
      mem.write(threadPointer + field.getOffset(), 0);    // it's a nonstatic field
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "J2NTotalYieldDuration");
      mem.write(threadPointer + field.getOffset(), 0);    // it's a nonstatic field
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "J2NTotalLockDuration");
      mem.write(threadPointer + field.getOffset(), 0);    // it's a nonstatic field
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 
  }



  /**
   * Search VM_Scheduler.threads and return all nonzero VM_Thread pointers
   * as an array of VM_Threads pointers
   * @return  
   * @exception
   * @see VM_Thread, VM_Scheduler
   */
  public int[] getAllThreads() throws Exception {
    int count = 0;
    int threadPointer;
    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Scheduler", "threads");   // get the thread array
      int address = mem.readTOC(field.getOffset());           // we know it's a static field
      int arraySize = mem.read(address + VM_ObjectModel.getArrayLengthOffset() );

      // first find the size
      VM_Field numThreadsField = bmap.findVMField("com.ibm.JikesRVM.VM_Scheduler", "numActiveThreads");
      int numThreadsAddress = mem.addressTOC(numThreadsField.getOffset());
      //System.out.println("numThreadsAddress = " + numThreadsAddress);
      int numThreads = mem.readsafe(numThreadsAddress) + 1;
      //System.out.println("numThreads = " + numThreads);
      // get the pointer values
      int threadArray[] = new int[numThreads];
      int j=0;
      for (int i=1; i<arraySize; i++) {
	threadPointer = mem.read(address+i*4);
	if (threadPointer!=0) {
	  threadArray[j++] =  threadPointer;
          if (j == numThreads) break;
	}
      }      

      return threadArray;
    } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Scheduler.threads, has VM_Scheduler.java been changed?");
    }
  }

  public int getVMThreadForIndex(int index) throws Exception {
    // to handle the initial condition when the thread structure is not setup
    if (index==0)
      return 0;

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Scheduler", "threads");   // get the thread array
      int address = mem.readTOC(field.getOffset());           // we know it's a static field
      int arraySize = mem.read(address + VM_ObjectModel.getArrayLengthOffset() );
      return mem.read(address+index*4);
    } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Scheduler.threads, has VM_Scheduler.java been changed?");
    } 
  }

  public int getIndexForVMThread(int threadPointer) throws Exception {
    // to handle the initial condition when the thread structure is not setup
    if (threadPointer==0)
      return 0;

    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "threadSlot");   // get the id offset for VM_Thread
      int address = mem.read(threadPointer + field.getOffset());    // it's a nonstatic field
      return address;
      } catch (BmapNotFoundException e) {
      throw new Exception("cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?");
    } 
  }

  public boolean isValidVMThread(int threadPointer) throws Exception {
    int vmthreads[] = getAllThreads();
    
    // Check with the list of thread to make sure it's valid
    for (int i=0; i<vmthreads.length; i++) {
      if (threadPointer==vmthreads[i]) {
	return true;
      }
    }
    return false;

  }

  /**
   * 
   * @return  
   * @exception
   * @see VM_Thread, VM_Scheduler
   */
  public String listAllThreads(boolean byClassName) {
    try {
      String result = "";
      int allThreads[] = getAllThreads();

      // int runThreads[] = getVMThreadIDInSystemThread();
      // for (int j=0; j<runThreads.length; j++) {
      //   runThreads[j] = getVMThreadForIndex(runThreads[j]);      // convert index to thread pointer
      // }

      result = "All threads: " + allThreads.length + "\n";
      if (byClassName) {
	result += "  ID  VM_Thread   Thread Class\n";
	result += "  -- -----------  ------------\n";
      } else {
	result += "  ID  VM_Thread   top stack frame\n";
	result += "  -- -----------  -----------------\n";
      }

      int activeThread = reg.hardwareTP();    

      // for each thread, get its status and marked it as active or running
      for (int i=0; i<allThreads.length; i++) {

	// prefix with the active thread with ->
	if (allThreads[i]==activeThread)
	  result += " >";
	else
	  result += "  ";

	// prefix with the running threads with >
	// boolean running=false;	
	// for (int j=0; j<runThreads.length; j++) {
	//   if (allThreads[i]==runThreads[j])
	//     running=true;
	// }
	// if (running)
	//   result += ">";
	// else
	//   result += " ";

	// get a short thread status
	if (byClassName) {
	  VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "threadSlot");
	  int fieldOffset = field.getOffset();
	  int id = mem.read(allThreads[i] + fieldOffset);
	  result += id + " @" + VM.intAsHexString(allThreads[i]) + "  ";
	  result += bmap.addressToClassString(allThreads[i]).substring(1) + "\n";
	} else {
	  result += threadToString(allThreads[i]) + "\n"; 
	}
      }

      return result;

    } catch (Exception e) {
      return e.getMessage();
    }
  }


  public String listThreadsCounts() {
    try {
      String result = "";
      int allThreads[] = getAllThreads();

      result = "All threads: " + allThreads.length + "\n";
      result += "  ID VM_Thread  yield  failure   total yield   total lck \n";
      result += "  ------------  -----  -------   -----------   --------- \n";

      // for each thread, get its status and marked it as active or running
      for (int i=0; i<allThreads.length; i++) {
	result += "  " + Integer.toHexString( allThreads[i] ) + "  ";
	result += getJ2NThreadCounts ( allThreads[i] ) + "\n";
      }

      return result;

    } catch (Exception e) {
      return e.getMessage();
    }
  }


  public void zeroThreadsCounts() {
    try {
      String result = "";
      int allThreads[] = getAllThreads();

      // for each thread, get its status and marked it as active or running
      for (int i=0; i<allThreads.length; i++) {
	zeroJ2NThreadCounts ( allThreads[i] );
      }

    } catch (Exception e) {
      System.out.println( "zeroThreadsCounts: " + e.getMessage() );
    }
  }


  /**
   * Return a string for all the threads in the VM_Scheduler.processors[n].readyQueue
   * for each virtual processor
   * @return  
   * @exception
   * @see VM_Thread, VM_Scheduler
   */
  public String listReadyThreads(){
    String result = "";

    try {
      // get the list of virtual processors
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Scheduler", "processors");   
      int address = mem.readTOC(field.getOffset());           // we know it's a static field
      int processorCount = mem.read(address + VM_ObjectModel.getArrayLengthOffset() );

      // get the offset into the readyQueue field
      field = bmap.findVMField("com.ibm.JikesRVM.VM_Processor", "readyQueue");   // get the ready queue
      int fieldOffset = field.getOffset();

      // processor 0 is null, the first real processor is at 1
      for (int proc = 1; proc < processorCount; proc++) {

	// get the ready queue for each processor
	int procAddress = mem.read(address + proc*4);
	if (procAddress!=0) {
	  result += "Virtual Processor " + proc + "\n";
	  int queuePointer = mem.read(procAddress + fieldOffset);       
	  result += threadQueueToString(queuePointer);
	}
      }
      
      return result;

    } catch (BmapNotFoundException e) {
      return "ERROR: cannot find VM_Scheduler.processors, has VM_Scheduler been changed?";
    }  
  }

  /**
   * Return a string for all the threads in the ready VM_Scheduler.wakeupQueue
   * @return  
   * @exception
   * @see VM_Thread, VM_Scheduler
   */
  public String listWakeupThreads(){
    String queueName = "wakeupQueue";
    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Scheduler", queueName);   // get the ready queue
      int queuePointer = mem.readTOC(field.getOffset());       // we know it's a static field

      return wakeupQueueToString(queuePointer);

    } catch (BmapNotFoundException e) {
      return "ERROR: cannot find VM_Scheduler." + queueName + ", has VM_Scheduler been changed?";
    }  
  }

  /**
   * Given a pointer to a VM_ThreadQueue, print the name for each thread in the queue
   * @param queueName currently either the readyQueue or the wakeupQueue
   * @return a string for all the threads in this queue 
   * @exception
   * @see VM_Thread, VM_Scheduler, VM_ThreadQueue, VM_ThreadWakeupQueue
   */
  private String threadQueueToString(int queuePointer){
    String result = "";
    int count = 0;
    int fieldOffset;
    int thisThreadPointer, headThreadPointer, tailThreadPointer;
    VM_Field field;
    try {
      field = bmap.findVMField("com.ibm.JikesRVM.VM_ThreadQueue", "head");
      headThreadPointer = mem.read(queuePointer + field.getOffset());
      field = bmap.findVMField("com.ibm.JikesRVM.VM_ThreadQueue", "tail");
      tailThreadPointer = mem.read(queuePointer + field.getOffset());
      // System.out.println("thread queue " + VM.intAsHexString(queuePointer) +
      //		 ", head " + VM.intAsHexString(headThreadPointer) +
      //		 ", tail " + VM.intAsHexString(tailThreadPointer));
      thisThreadPointer = headThreadPointer;
      if (thisThreadPointer!=0) {
	  result += "   " + threadToString(thisThreadPointer) + "\n"; 
	  count++;
      }      
      while (thisThreadPointer != tailThreadPointer) {
	field = bmap.findVMField("com.ibm.JikesRVM.VM_Thread", "next");
	thisThreadPointer = mem.read(thisThreadPointer + field.getOffset());
	if (thisThreadPointer!=0) {
	  result += "   " + threadToString(thisThreadPointer) + "\n"; 
	  count++;
	} else {
	  thisThreadPointer = tailThreadPointer;   // to exit loop
      	}
      }
      
      
    } catch (BmapNotFoundException e) {
      return "ERROR: cannot find VM_ThreadQueue.head or tail, has VM_ThreadQueue been changed?";
    }

    String heading= "";
    heading += "  ID  VM_Thread   top stack frame\n";
    heading += "  -- -----------  -----------------\n";

    return  "Threads in queue:  " + count + "\n" + heading + result;
  }


  /**
   * Given a pointer to a VM_ProxyWakeupQueue, print the name for each thread in the queue
   * @param queueName currently either the readyQueue or the wakeupQueue
   * @return a string for all the threads in this queue 
   * @exception
   * @see VM_Thread, VM_Scheduler, VM_ThreadQueue, VM_ThreadWakeupQueue
   */
  private String wakeupQueueToString(int queuePointer){
    String result = "";
    int count = 0;
    int fieldOffset;
    int thisProxyPointer, thisThreadPointer;
    VM_Field field;
    try {
      field = bmap.findVMField("com.ibm.JikesRVM.VM_ProxyWakeupQueue", "head");
      thisProxyPointer = mem.read(queuePointer + field.getOffset());
      // 	 System.out.println("ready queue " + VM.intAsHexString(queuePointer) +
      // 			    ", head " + VM.intAsHexString(headThreadPointer) +
      // 			    ", tail " + VM.intAsHexString(tailThreadPointer));
      while (thisProxyPointer != 0) {
	field = bmap.findVMField("com.ibm.JikesRVM.VM_Proxy", "thread");
	thisThreadPointer = mem.read(thisProxyPointer + field.getOffset());
	if (thisThreadPointer!=0) {
	  result += "   " + threadToString(thisThreadPointer) + "\n"; 
	  count++;
	}
        field = bmap.findVMField("com.ibm.JikesRVM.VM_Proxy", "wakeupNext");
        thisProxyPointer = mem.read(thisProxyPointer + field.getOffset());
      }
      
      
    } catch (BmapNotFoundException e) {
      return "ERROR: cannot find VM_ThreadQueue.head or tail, has VM_ThreadQueue been changed?";
    }

    String heading= "";
    heading += "  ID  VM_Thread   top stack frame\n";
    heading += "  -- -----------  -----------------\n";

    return  "Threads in queue:  " + count + "\n" + heading + result;
  }


  /**
   * List the threads current loaded and running in the Lintel system threads
   * @return  
   * @exception
   * @see VM_Thread, VM_Scheduler
   */
  public String listGCThreads() {
    String queueName = "collectorQueue";
    try {
      VM_Field field = bmap.findVMField("com.ibm.JikesRVM.VM_Scheduler", queueName);   // get the ready queue
      int queuePointer = mem.readTOC(field.getOffset());       // we know it's a static field

      return "GC threads: \n" + threadQueueToString(queuePointer);

    } catch (BmapNotFoundException e) {
      return "ERROR: cannot find VM_Scheduler." + queueName + ", has VM_Scheduler been changed?";
    }  

  }

  /**
   * List the threads current loaded and running in the Lintel system threads
   * @return  
   * @exception
   * @see VM_Thread, VM_Scheduler
   */
  public String listRunThreads() {
    String result = "";
    int runThreads[] = getVMThreadIDInSystemThread();
    try {
      for (int j=0; j<runThreads.length; j++) {
      	runThreads[j] = getVMThreadForIndex(runThreads[j]);      // convert index to thread pointer
      }
      
      result += "Running in system threads: " + runThreads.length + "\n";
      result += "  ID  VM_Thread   top stack frame\n";
      result += "  -- -----------  -----------------\n";
      for (int i=0; i< runThreads.length; i++) {
      	if (runThreads[i]!=0) {
      	  result += "   " + threadToString(runThreads[i]) + "\n"; 
      	} else {
      	  System.out.println("CAUTION:  unexpected null thread in system thread\n");
      	}
      }
      
      return result;

    } catch (Exception e) {
      return e.getMessage();
    }

  }

  private String threadToString(int threadPointer) {
    String result = "";

    try {
      // get the thread ID from the thread object
      int id = mem.read(threadPointer + VM_Entrypoints.threadSlotField.getOffset());
      result += id + " @" + VM.intAsHexString(threadPointer);
      
      // Find the name of the top stack frame
      int ip = reg.getVMThreadIP(id);
      int[] savedRegs = reg.getVMThreadGPR(id);
      int pr = savedRegs[reg.regGetNum("PR")];
      int fp = reg.getFPFromPR(pr);
      int compiledMethodID = bmap.getCompiledMethodID(fp, ip);
      if (compiledMethodID==NATIVE_METHOD_ID) {
	result += "  (native procedure)";
      } else {
        VM_Method mth = bmap.findVMMethod(compiledMethodID, true);
        if (mth!=null) {
          String method_name = mth.getName().toString();
          String method_sig = mth.getDescriptor().toString();
          result += "  " + method_name+ "  " + method_sig;
        } else {
          result += "  (method ID not set up yet)";
        }
      }

      return result;
    } catch (BmapNotFoundException e) {
      return "cannot find VM_Thread.threadSlot, has VM_Thread.java been changed?";
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  public void enableIgnoreOtherBreakpointTrap() {
    ignoreOtherBreakpointTrap = true;
  }
  public void disableIgnoreOtherBreakpointTrap() {
    ignoreOtherBreakpointTrap = false;
  }

}
