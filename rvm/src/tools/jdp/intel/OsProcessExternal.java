/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This is the implemetation for the abstract portion of OsProcess
 * This process can execute any compiled program
 * @author Ton Ngo  (1/6/98)
 */
import java.io.*;

class OsProcessExternal extends OsProcess implements VM_Constants {

  /**
   * Constructor for external implementation of OsProcess
   * A new system process is created to run the JVM
   * @param ProgramName the name of the main program as would be used in a shell
   * @param args the argument list for the program
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
   public OsProcessExternal(String ProgramName, String args[], 
		   String mainClass, String classesNeededFilename, String classpath)
  {
    super(ProgramName, args, mainClass, classesNeededFilename, classpath);

    // pass the register mapping to the native code
    init(FRAME_POINTER, 
	 SP, 
	 GPR_NAMES,
	 FPR_NAMES,
	 OBJECT_THREAD_ID_SHIFT); 

    mem = new memoryExternal(this);
    reg = new registerExternal(this);
    bpset = new breakpointList(this);
    threadstep = new breakpointList(this, 1);  // only one thread for now
    threadstepLine = new breakpointList(this, 1);  // only one thread for now

  }

  /**
   * Constructor for external implementation of OsProcess
   * This version attaches to a currently running process instead of creating one
   * @param processID the system process ID of the running process
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
  public OsProcessExternal(int processID, String mainClass,
			   String classesNeededFilename, String classpath) throws OsProcessException {
    super(processID, mainClass, classesNeededFilename, classpath);
    // pass the register mapping to the native code
    init(FRAME_POINTER, 
	 SP, 
	 GPR_NAMES,
	 FPR_NAMES,
	 OBJECT_THREAD_ID_SHIFT); 
    mem = new memoryExternal(this);
    reg = new registerExternal(this);    
    bpset = new breakpointList(this);
    threadstep = new breakpointList(this, 1);  // only one thread for now
    threadstepLine = new breakpointList(this, 1);  // only one thread for now

  }
  
  /**
   * Save the register name mapping in the C stack for the native code to refer to
   * @param fp the register number for Frame Pointer
   * @param sp the register number for stack pointer
   * @param tp the register number for thread pointer
   * @param jtoc the register number for table of content
   * @param gprName an array of String (0-31), GPR register number as index to its name
   * @param fprName an array of String (0-31), FPR register number as index to its name
   * @param tpshift the amount of shift right to convert the thread ID register
   * @return
   * @exception
   * @see
   */
  private void init(int fp, int sp, String[] gprName, String[] fprName, int tpshift) {
    Platform.setRegisterMapping(fp, sp, gprName, fprName, tpshift);
  }

  /**
   * Implement abstract method: destroy the process
   * @param
   * @return
   * @exception
   * @see
   */
  public void mkill(){
    Platform.mkill();
  }

  /**
   * Abstract method: detach the debugger from the process
   * @param
   * @return
   * @exception
   * @see
   */
  public void mdetach() {
    Platform.mdetach();
  }

  /**
   * Wait for the process to return
   * @param
   * @return the process status from the system
   * @exception
   * @see
   */
  public int mwait() {
    return Platform.mwait();
  }

  /**
   * Temporary hardware single step for Intel
   */
  public boolean mstep() {
    return Platform.mstep();
  }

  /**
   * Allow all threads in the process to continue execution
   * @param
   * @return
   * @exception
   * @see
   */
  public void mcontinue(int signal) {
    Platform.mcontinue(signal);
  }

  /**
   * Allow the current system thread to continue execution
   * @param
   * @return
   * @exception
   * @see
   */
  // continue by thread not supported yet on Lintel
  // public void mcontinueThread(int signal) {
  //   Platform.mcontinueThread(signal);
  // }

  /**
   * Create the external process to execute a program
   * @param ProgramName the name of the main program as would be used in a shell
   * @param args the argument list for the program
   * @return the process id from the system
   * @exception
   * @see
   */
  public int createDebugProcess(String ProgramName, String args[]) {
    return Platform.createDebugProcess(ProgramName, args);
  }

  /**
   * Abstract method:  attach to an external process currently running
   * @param processID the system process ID
   * @return the same process id from the system
   * @exception
   * @see
   */
  public int attachDebugProcess(int processID) {
    return Platform.attachDebugProcess(processID);
  }

  /**
   * Check if the system trap is for breakpoint
   * @param status the process status from the system
   * @return true if trapping on breakpoint
   * @exception
   * @see
   */
  public boolean isBreakpointTrap(int status) {
    return Platform.isBreakpointTrap(status);
  }

  /**
   * Check if the system trap is for a library loaded event
   * @param status the process status from the system
   * @return true if trapping on breakpoint
   * @exception
   * @see
   */
  public boolean isLibraryLoadedTrap(int status) {
    return Platform.isLibraryLoadedTrap(status);
  }  

  /**
   * check if the trap is a segmentation violation signal
   */
  public boolean isSegFault(int status) {
    return Platform.isSegFault(status);
  }

  /**
   * Abstract method:  check if the system trap is to be ignored
   * and passed directly to the RVM
   * @param status the process status from the system
   * @return true if trap is to be ignored
   * @exception
   * @see
   */
  public boolean isIgnoredTrap(int status)  {
    return Platform.isIgnoredTrap(status);
  }

  /**
   * Check if this is a trap instruction used by the JVM
   * and not by the debugger
   * Elect to ignore it or not depending on the flag OsProcess.ignoreOtherBreakpointTrap
   * @param status the process status from the system
   * @return true if trap is to be ignored, false otherwise
   */
  public boolean isIgnoredOtherBreakpointTrap(int status) {
    if (ignoreOtherBreakpointTrap && isSegFault(status)) {
      return true;
    } else {
      return false;   // real trap or illegal instruction, dont' ignore
    }
  }

  /**
   * Check if the process has been killed
   * @param status the process status from the system
   * @return true if the process has been killed
   * @exception
   * @see mwait
   */
  public boolean isKilled(int status) {
    return Platform.isKilled(status);
  }

  /**
   * Check if the process has exited normally
   * @param status the process status from the system
   * @return true if the process has exited
   * @exception
   * @see
   */
  public boolean isExit(int status){
    return Platform.isExit(status);
  }

  /**
   * Convert to String the process status indicator used by
   * the system
   * @param status the process status from the system
   * @return the status message
   * @exception
   * @see
   */
  public String statusMessage(int status)  {
    return Platform.statusMessage(status);
  }

  /**
   * Abstract method:  get the system thread ID
   * @return an array for the current system thread ID
   * @exception
   * @see
   */
  public int[] getSystemThreadId() {
    return Platform.getSystemThreadId();
  }


  /**
   * Abstract method:  dump the system thread ID
   * @return a String array for the contents of all system threads
   * @exception
   * @see listSystemThread
   */
  public String listSystemThreads()  {
    return Platform.listSystemThreads();
  }


  /**
   * Abstract method:  get the VM_Thread currently loaded in the system threads
   * @return an array of VM_Thread
   * @exception
   * @see
   */
  public int[] getVMThreadIDInSystemThread() {
    return Platform.getVMThreadIDInSystemThread();
  }


}
