/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This is the abstract class for the internal and external 
 * implementation of register
 * This class represents the machine registers
 * We can inspect them, set values
 * @author Ton Ngo  (2/98)
 */

abstract class register implements VM_Constants, VM_BaselineConstants, registerConstants
{
  /**
   * Pointer back to the process that owns this set of registers
   */
  OsProcess owner;     

  /**
   *  ID of the context thread (index into VM_Scheduler.threads[])
   *  @see setContextThreadID, getContextThread, getContextThreadID
   */
  int contextThread;   

  /**
   *  Cached JTOC value for use when in native stack frame
   */
  int cachedJTOC;


  public register(OsProcess process) {
    owner = process;
    contextThread = 0;  // initial value, real value is never 0
  }

  // these will be native for the external implementation
  public abstract int   read(int regnum);
  public abstract float fread(int regnum);
  public abstract void write(int regnum, int value);
  public abstract void fwrite(int regnum, float value);

  public abstract int   read(String regname) throws Exception;
  public abstract float fread(String regname) throws Exception;
  public abstract void write(String regname, int value) throws Exception;
  public abstract void fwrite(String regname, float value) throws Exception;

  public abstract int hardwareTP();
  public abstract int hardwareIP();
  public abstract int currentIP();
  public abstract int currentFP();
  public abstract int currentSP();
  public abstract void cacheJTOC();
  public abstract int getJTOC();
  public abstract int getFPFromPR(int pr);

  public abstract String getValue(String regname, int count) throws Exception;



  /**
   * Get the general purpose registers for a VM_Thread currently running in a system thread
   * @param VMthreadID  the pointer to the VM_Thread, expected to be kept in R15
   * @param systemThreadId   an array of all system thread ID for this process
   * @return an array for the 32 registers, or null if the thread is not loaded in any
   *         system thread
   * @see OsProcess.getSystemThreadId
   */
  public abstract int [] getSystemThreadGPR(int VMthreadID, int systemThreadId[]);
  public abstract int [] getSystemThreadSPR(int VMthreadID, int systemThreadId[]);

  /**
   * Get the floating point registers for a VM_Thread currently running in a system thread
   * @param VMthreadID  the pointer to the VM_Thread, expected to be kept in R15
   * @param systemThreadId   an array of all system thread ID for this process
   * @return an array for the 32 FPR, or null if the thread is not loaded in any
   *         system thread
   * @see OsProcess.getSystemThreadId
   */
  public abstract double [] getSystemThreadFPR(int VMthreadID, int systemThreadId[]);


  /**
   * Look up a VM_Thread object address to get its thread index 
   * in the VM_Scheduler.threads array
   * @param regValue a value in the Thread Pointer register
   * @return the thread ID, also an index to VM_Scheduler.threads
   */
  public int threadPointerToIndex(int tp) {
    return owner.mem.read(tp + VM_Entrypoints.threadSlotOffset);
  }

  /**
   * Check the contextThread field
   * If 0, leave it as 0 to force the context to use the hardware registers 
   * If not 0, set it to the index for the current TP register 
   *
   */
  public void setContextThreadIDFromRun() {
    if (contextThread!=0) {
      // the thread where execution is stopped in:  get the VM_Thread 
      // from the processor register.
      contextThread = threadPointerToIndex(hardwareTP());
    }
  }

  /**
   * Set the contextThread field to the specified thread ID
   * Setting to 0 will forces the context to use the hardware registers 
   * it will also stay at 0 until it is set manually to a valid thread ID
   * @param threadIndex a thead ID
   * @exception if the thread ID is invalid (does not match any thread
   *            in VM_Scheduler.threads)
   */
  public void setContextThreadID(int threadIndex) throws Exception {
    // ID of 0 forces the context to use the hardware registers
    if (threadIndex==0) {
      contextThread = threadIndex;
    } else {
      // for nonzero ID, verify that it's a valid ID
      int threadPointer = owner.getVMThreadForIndex(threadIndex);   
      if (threadPointer!=0)
	contextThread = threadIndex;
      else
	throw new Exception("invalid thread ID");
    }
  }

  public int getContextThread() {
    try {
      return owner.getVMThreadForIndex(contextThread);    
    } catch (Exception e) {
      System.out.println(e.getMessage());
      System.out.println("jdp error: Context thread ID should have been checked before saved");
      System.out.println("contact your nearest Jikes RVM customer service center");
      return 0;
    }
  }

  public int getContextThreadID() {
    return contextThread;    
  }

  /**
   * Get the general purpose register for a VM_Thread
   * @param threadID  the id of the VM_thread object
   * @return an array for the GPR's
   * @exception
   * @see
   */
  public int [] getVMThreadGPR(int threadID) throws Exception {
    // Check if this thread is loaded in the system thread or swapped out
    int gprs[];
    // int systemThreadID[] = owner.getSystemThreadId();
    // gprs = getSystemThreadGPR(threadID, systemThreadID);		
    // if (gprs==null) {   // it's not loaded in the system
    // 	 gprs = getVMThreadSavedGPR(threadID);
    // } 
    
    // Lintel TEMP:  replace the code above    
    // currently we don't know how to get pthread info via ptrace
    // so we can only get the register value of the currently thread we stopped in
    // this code does not check the thread currently loaded in pthreads other than
    // the one we stopped in.
    if (threadIsLoaded(threadID)) {
      int systemThreadID[] = owner.getSystemThreadId();
      gprs = getSystemThreadGPR(threadID, systemThreadID);
    } else {
      gprs = getVMThreadSavedGPR(threadID);
    }

    return gprs;
  }

  public boolean threadIsLoaded(int threadID) throws Exception {
    int thisThread = owner.getVMThreadForIndex(threadID);
    int proc = read("PR");
    int loadedThread = owner.mem.read(proc + VM_Entrypoints.activeThreadOffset);
    return (thisThread==loadedThread || thisThread==0);
  }

  /**
   * Get the general purpose register for a VM_Thread saved in memory
   * @param threadID  the id of the VM_thread object
   * @return an array for the GPR's
   * @exception
   * @see
   */
  public int [] getVMThreadSavedGPR (int threadID) throws Exception {
    try {
      // the register field (nonstatic)
      int threadPointer = owner.getVMThreadForIndex(threadID);
      VM_Field field = owner.bmap.findVMField("VM_Thread", "contextRegisters");   
      int address = owner.mem.read(threadPointer + field.getOffset());    
      // the GPR's field
      field = owner.bmap.findVMField("VM_Registers", "gprs");          
      int gprsAddress = owner.mem.read(address + field.getOffset());        
      // now read the array of saved registers
      int regs[] = new int[10];
      for (int i=0; i<8; i++) {
	regs[i] = owner.mem.read(gprsAddress+i*4);
      }
      field = owner.bmap.findVMField("VM_Registers", "ip");          
      regs[8] = owner.mem.read(address + field.getOffset());        
      field = owner.bmap.findVMField("VM_Registers", "fp");          
      regs[9] = owner.mem.read(address + field.getOffset());        
      return regs;
    } catch  (BmapNotFoundException e) {
      throw new Exception("cannot find the field contextRegisters or gprs, has VM_Thread.java or VM_Registers.java been changed?");
    } 
  }

  /**
   * Get the floating point register for a VM_Thread
   * @param threadID  the id of the VM_thread object
   * @return an array for the GPR's
   * @exception
   * @see
   */
  public double [] getVMThreadFPR(int threadID) throws Exception {
    // Check if this thread is loaded in the system thread or swapped out
    double fprs[];
    // int systemThreadID[] = owner.getSystemThreadId();
    // fprs = getSystemThreadFPR(threadID, systemThreadID);		
    // if (fprs==null) {   // it's not loaded in the system
    // 	 fprs = getVMThreadSavedFPR(threadID);
    // } 

    // Lintel TEMP:  replace the code above    
    // currently we don't know how to get pthread info via ptrace
    // so we can only get the register value of the currently thread we stopped in
    // this code does not check the thread currently loaded in pthreads other than
    // the one we stopped in.
    if (threadIsLoaded(threadID)) {
      int systemThreadID[] = owner.getSystemThreadId();
      fprs = getSystemThreadFPR(threadID, systemThreadID);      
    } else {
      fprs = getVMThreadSavedFPR(threadID);
    }
    return fprs;

  }

  /**
   * Get the general purpose register for a VM_Thread saved in memory
   * @param threadID  the id of the VM_thread object
   * @return an array for the GPR's
   * @exception
   * @see
   */
  public double [] getVMThreadSavedFPR (int threadID) throws Exception {
    try {
      // the register field (nonstatic)
      int threadPointer = owner.getVMThreadForIndex(threadID);
      VM_Field field = owner.bmap.findVMField("VM_Thread", "contextRegisters");   
      int address = owner.mem.read(threadPointer + field.getOffset());    
      // the GPR's field
      field = owner.bmap.findVMField("VM_Registers", "fprs");          
      address = owner.mem.read(address + field.getOffset());        
      // now read the array of saved registers
      double regs[] = new double[NUM_FPRS];
      for (int i=0; i<NUM_FPRS; i++) {
	int lo = owner.mem.read(address+i*8);
	int hi = owner.mem.read(address+i*8+4);
	long regbits = owner.bmap.twoIntsToLong(hi, lo);
	regs[i] = Double.longBitsToDouble(regbits);
      }
      return regs;
    } catch  (BmapNotFoundException e) {
      throw new Exception("cannot find the field contextRegisters or fprs, has VM_Thread.java or VM_Registers.java been changed?");
    } 
  }

  public int getVMThreadIP(int threadID) throws Exception  {
    int sprs[] = getVMThreadSPR(threadID);
    return sprs[0];

  }

  /**
   * Get the special purpose register for a VM_Thread
   * @param threadID  the id of the VM_thread object
   * @return an array for the SPR's
   * @exception
   * @see
   */
  public int[] getVMThreadSPR(int threadID) throws Exception  {
    // Need to check if this thread is loaded in the system thread or swapped out,
    int sprs[]= new int[1];

    // int systemThreadID[] = owner.getSystemThreadId();
    // sprs = getSystemThreadSPR(threadID, systemThreadID);
    // if (sprs!=null) {
    // 	    return sprs;
    // } else {  
    // 	    sprs = new int[2];
    // 	    // not in system thread, then the IP is in the stack frame
    // 	    sprs[0] = getVMThreadSavedIP(threadID);
    // 	    sprs[1] = getVMThreadSavedFP(threadID);
    // 	    return sprs;
    // }

    // Lintel TEMP:  replace the code above    
    // currently we don't know how to get pthread info via ptrace
    // so we can only get the register value of the currently thread we stopped in
    // this code does not check the thread currently loaded in pthreads other than
    // the one we stopped in.
    if (threadIsLoaded(threadID)) {
      sprs[0] = read("IP");
      return sprs;
    } else {
      // not in system thread, then the IP is in the stack frame
      sprs[0] = getVMThreadSavedIP(threadID);
      return sprs;
    }

  }


  /**
   * For a VM_Thread that is swapped out, the IP is saved in the
   * topmost stack frame, so get it through the saved FP
   * @param threadID  the id of the VM_thread object
   * @return the IP saved at FP + 
   * @exception
   *
   */
  // public int getVMThreadSavedIP(int threadID) throws Exception {
  //   int gprs[] = getVMThreadSavedGPR(threadID);
  //   int fp = gprs[regGetNum("FP")];
  //   int ip = owner.mem.read(fp + VM_Constants.STACKFRAME_RETURN_ADDRESS_OFFSET);
  //   return ip;
  // }

  public int getVMThreadSavedIP(int threadID) throws Exception {
    try {
      // the register field (nonstatic)
      int threadPointer = owner.getVMThreadForIndex(threadID);
      VM_Field field = owner.bmap.findVMField("VM_Thread", "contextRegisters");   
      int address = owner.mem.read(threadPointer + field.getOffset());    
      // the GPR's field
      field = owner.bmap.findVMField("VM_Registers", "ip");          
      address = owner.mem.read(address + field.getOffset());        
      return address;
    } catch  (BmapNotFoundException e) {
      throw new Exception("cannot find the field contextRegisters or ip, has VM_Thread.java or VM_Registers.java been changed?");
    } 
  }

  public int getVMThreadSavedFP(int threadID) throws Exception {
    try {
      // the register field (nonstatic)
      int threadPointer = owner.getVMThreadForIndex(threadID);
      VM_Field field = owner.bmap.findVMField("VM_Thread", "contextRegisters");   
      int address = owner.mem.read(threadPointer + field.getOffset());    
      // the GPR's field
      field = owner.bmap.findVMField("VM_Registers", "fp");          
      address = owner.mem.read(address + field.getOffset());        
      return address;
    } catch  (BmapNotFoundException e) {
      throw new Exception("cannot find the field contextRegisters or fp, has VM_Thread.java or VM_Registers.java been changed?");
    } 
  }

  /**
   * Convert a register name as string to decimal number 
   */
  public static int regGetNum(String regString) throws Exception {
    int reg, i;
  
    if (regString.equalsIgnoreCase("jt")) {
    	    return registerConstants.EDI;
    // } else if (regString.equalsIgnoreCase("ip")) {
    // 	    return IAR;
    } else if (regString.equalsIgnoreCase("pr")) {
    	    return PR;
    // } else if (regString.equalsIgnoreCase("fp")) {
    // 	    return FP;
    } else if (regString.equalsIgnoreCase("sp")) {
    	    return SP;
    } else if (regString.equalsIgnoreCase("jtoc") ||
    		     regString.equalsIgnoreCase("toc")) {
    	    return JTOC;
    // }  else if (regString.equalsIgnoreCase("")) {
    // 	    return 0;
    }

    /* first try the special purpose names */
    for (i = 0; i<RVM_GPR_NAMES.length; i++) {
      if (regString.equalsIgnoreCase(RVM_GPR_NAMES[i]))
	return i;
    }

    if (regString.equalsIgnoreCase("ip"))
      return IAR;

    /* then check the general purpose names */
    for (i=0; i<GPR_NAMES.length; i++) {
      if (regString.equalsIgnoreCase(GPR_NAMES[i]))
	return i;
    }

    /* then check the floating point names */
    for (i=0; i<FPR_NAMES.length; i++) {
      if (regString.equalsIgnoreCase(FPR_NAMES[i]))
	return (i+FPR0);
    }
    
  
    /* last resort */
    try {
      reg = Integer.parseInt(regString);
      if (reg>GPR7) 
	throw new Exception("invalid register name");
      return reg;
    } catch (NumberFormatException e) {
      System.out.println("regGetNum: invalid register name, " + regString);
      throw new Exception("invalid register name");
    }
  
  }

  public static String regGetName(int reg) throws Exception {
    /* for the general purpose registers, in the range of 0-31 */
    if ((reg>=GPR0) && (reg<GPR0+GPR_NAMES.length)) {
      return GPR_NAMES[reg-GPR0];
    }
    
    /* for the floating point registers, in the range of 256-287 */
    if ((reg>=FPR0) && (reg<FPR0+FPR_NAMES.length)) {
      return FPR_NAMES[reg-FPR0];
    }

    /* for the special purpose RS6000 registers */
    if (reg==IAR)      return "IP";
    if (reg==LR)       return "LR"; 
    
    /* Any override for the special RVM register */
    if (reg==JTOC)
      return "JTOC";
    else if (reg==SP)
      return "SP";
    
    // if no name in the RVM convention, make it a 
    // general purpose or floating point register */
    if ((reg>=GPR0) && (reg<=GPR7))
      return "r" + reg;
    else
      throw new Exception("invalid register number");

  }
    


}
