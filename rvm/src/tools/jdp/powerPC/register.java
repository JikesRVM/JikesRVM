/*
 * (C) Copyright IBM Corp. 2001
 */
/*
 * This is the abstract class for the internal and external 
 * implementation of register
 * This class represents the machine registers
 * We can inspect them, set values
 * @author  Ton Ngo  (2/98)
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
   * Convert the value in the Thread Pointer register to an index 
   * in the VM_Scheduler.threads array
   * @param regValue a value in the Thread Pointer register
   * @return the thread ID, also an index to VM_Scheduler.threads
   */
  public int registerToTPIndex(int regValue) {
    return regValue >>> OBJECT_THREAD_ID_SHIFT;
  }

  /**
   * Check the contextThread field
   * If 0, leave it as 0 to force the context to use the hardware registers 
   * If not 0, set it to the index for the current TP register 
   *
   */
  public void setContextThreadIDFromRun() {
    if (contextThread!=0) {
      // Use the value of the TP register of the thread stopped in
      contextThread = registerToTPIndex(hardwareTP());
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
      System.out.println("contact your nearest Jalapeno customer service center");
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
    int systemThreadID[] = owner.getSystemThreadId();
    gprs = getSystemThreadGPR(threadID, systemThreadID);		
    if (gprs==null) {   // it's not loaded in the system
      gprs = getVMThreadSavedGPR(threadID);
    } 
    return gprs;
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
      address = owner.mem.read(address + field.getOffset());        
      // now read the array of saved registers
      int regs[] = new int[32];
      for (int i=0; i<32; i++) {
	regs[i] = owner.mem.read(address+i*4);
      }
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
    int systemThreadID[] = owner.getSystemThreadId();
    fprs = getSystemThreadFPR(threadID, systemThreadID);		
    if (fprs==null) {   // it's not loaded in the system
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
      double regs[] = new double[32];
      for (int i=0; i<32; i++) {
	int hi = owner.mem.read(address+i*8);
	int lo = owner.mem.read(address+i*8+4);
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
    // Check if this thread is loaded in the system thread or swapped out
    int sprs[];
    int systemThreadID[] = owner.getSystemThreadId();
    sprs = getSystemThreadSPR(threadID, systemThreadID);
    if (sprs!=null) {
      return sprs;
    } else {  
      sprs = new int[2];
      // not in system thread, then the IP is in the stack frame
      sprs[0] = getVMThreadSavedIP(threadID);
      sprs[1] = getVMThreadSavedLR(threadID);
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
  public int getVMThreadSavedIP(int threadID) throws Exception {
    int gprs[] = getVMThreadSavedGPR(threadID);
    int fp = gprs[regGetNum("FP")];
    int ip = owner.mem.read(fp + VM_Constants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    return ip;
  }

  public int getVMThreadSavedLR(int threadID) throws Exception {
    try {
      // the register field (nonstatic)
      int threadPointer = owner.getVMThreadForIndex(threadID);
      VM_Field field = owner.bmap.findVMField("VM_Thread", "contextRegisters");   
      int address = owner.mem.read(threadPointer + field.getOffset());    
      // the GPR's field
      field = owner.bmap.findVMField("VM_Registers", "lr");          
      address = owner.mem.read(address + field.getOffset());        
      // now read the saved link register	
      return owner.mem.read(address);
    } catch  (BmapNotFoundException e) {
      throw new Exception("cannot find the field contextRegisters or lr, has VM_Thread.java or VM_Registers.java been changed?");
    } 
  }

  /**
   * Convert a register name as string to decimal number 
   */
  public static int regGetNum(String regString) throws Exception {
    int reg, i;
  
    reg = -1;
  
    /* first try the special purpose names */
    if (regString.equalsIgnoreCase("ip")) {
      reg = IAR;
    } else if (regString.equalsIgnoreCase("msr")) {
      reg = MSR;
    } else if (regString.equalsIgnoreCase("cr")) {
      reg = CR;
    } else if (regString.equalsIgnoreCase("ctr")) {
      reg = CTR;
    } else if (regString.equalsIgnoreCase("xer")) {
      reg = XER;
    } else if (regString.equalsIgnoreCase("mq")) {
      reg = MQ;
    } else if (regString.equalsIgnoreCase("tid")) {
      reg = TID;
    } else if (regString.equalsIgnoreCase("fpscr")) {
      reg = FPSCR;
    } else if (regString.equalsIgnoreCase("fpinfo")) {
      reg = FPINFO;
    } else if (regString.equalsIgnoreCase("fpscrx")) {
      reg = FPSCRX;
    } else if (regString.equalsIgnoreCase("lr")) {
      reg = LR;
    // } else if (regString.equalsIgnoreCase("fp")) {
    // 	 reg = jvmFP;
    // } else if (regString.equalsIgnoreCase("sp")) {
    // 	 reg = jvmSP;
    // } else if (regString.equalsIgnoreCase("jtoc") ||
    // 		  regString.equalsIgnoreCase("toc")) {
    // 	 reg = jvmJTOC;
    }  else if (regString.equalsIgnoreCase("")) {
      reg = 0;
    } else {
      /* then check the general purpose names */
      for (i=0; i<GPR_NAMES.length; i++) {
  	if (regString.equalsIgnoreCase(GPR_NAMES[i])) {
  	  reg = i;
  	  break;
  	}
      }
      /* then check the floating point names */
      for (i=0; i<FPR_NAMES.length; i++) {
  	if (regString.equalsIgnoreCase(FPR_NAMES[i])) {
  	  reg = i+FPR0;
  	  break;
  	}
      }
    }
  
    /* last resort */
    if (reg==-1) {
      try {
	reg = Integer.parseInt(regString);
	if (reg>FPR31) 
	  throw new Exception("invalid register name");
      } catch (NumberFormatException e) {
	throw new Exception("invalid register name");
      }
    }
  
    return reg;

  }

  public static String regGetName(int reg) throws Exception {
    /* for the general purpose registers, in the range of 0-31 */
    if ((reg>=GPR0) && (reg<=GPR0+GPR_NAMES.length))
      return GPR_NAMES[reg-GPR0];
    
    /* for the floating point registers, in the range of 256-287 */
    if ((reg>=FPR0) && (reg<=FPR0+FPR_NAMES.length))
      return FPR_NAMES[reg-FPR0];
    
    /* for the special purpose RS6000 registers */
    if (reg==IAR)      return "IP";
    if (reg==MSR)      return "MSR";
    if (reg==CR)       return "CR"; 
    if (reg==LR)       return "LR"; 
    if (reg==CTR)      return "CTR";
    if (reg==XER)      return "XER";
    if (reg==MQ)       return "MQ"; 
    if (reg==TID)      return "TID";
    if (reg==FPSCR)    return "FPSCR";
    if (reg==FPINFO)   return "FPINFO";
    if (reg==FPSCRX)   return "FPSCRX";
    
    /* Any override for the special Jalapeno register */
    // if (reg==jvmFP)
    // 	 return "FP";
    // else if (reg==jvmJTOC)
    // 	 return "JTOC";
    // else if (reg==jvmSP)
    // 	 return "SP";
    
    // if no name in the Jalapeno convention, make it a 
    // general purpose or floating point register */
    if ((reg>=FPR0) && (reg<=FPR31))
      return "fr" + (reg-FPR0);
    else if (reg<=FPR0 && reg>=FPR31)
      return "r" + (reg-GPR0);
    else
      throw new Exception("invalid register number");
  }



}
