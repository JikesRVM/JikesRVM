/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class represents the machine registers
 * We can inspect them, set new values
 * This is the external implementation:  the registers are
 * accessed from outside the debuggee process;  most methods
 * are native and thus platform-dependent
 * The names for the registers are picked up from VM_BaselineConstants.java
 * @author Ton Ngo (2/98)
 */


class registerExternal extends register implements VM_BaselineConstants, registerConstants
{

  public registerExternal (OsProcess process) {
    super(process);
  }

  //******************************************************************************
  // Native methods to access the registers
  //******************************************************************************
  public int   read(int regnum) {
    return Platform.readreg(regnum);
  }

  public float fread(int regnum) {
    return (float) Platform.readfreg(regnum);
  }

  public void  write(int regnum, int value)  {
    Platform.writereg(regnum, value);
  }

  public void  fwrite(int regnum, float value) {
    Platform.writefreg(regnum, value);
  }


  /**
   * Get the general purpose registers for a VM_Thread currently running in a system thread
   * @param VMThreadID  the ID of the VM_Thread, expected to be kept in R15
   * @param systemThreadId   an array of all system thread ID for this process
   * @return an array for the 32 registers, or null if the thread is not loaded in any
   *         system thread
   * @see OsProcess.getSystemThreadId
   */
  public int [] getSystemThreadGPR(int VMThreadID, int systemThreadId[]) {
    return Platform.getSystemThreadGPR(VMThreadID, systemThreadId);
  }

  /**
   * Get the floating point registers for a VM_Thread currently running in a system thread
   * @param VMThreadID  the ID of the VM_Thread, expected to be kept in R15
   * @param systemThreadId   an array of all system thread ID for this process
   * @return an array for the 32 FPR, or null if the thread is not loaded in any
   *         system thread
   * @see OsProcess.getSystemThreadId
   */
  public double [] getSystemThreadFPR(int VMThreadID, int systemThreadId[]) {
    return Platform.getSystemThreadFPR(VMThreadID, systemThreadId);
  }
  
  /**
   * Get the special purpose registers for a VM_Thread currently running in a system thread
   * @param VMThreadID  the ID of the VM_Thread, expected to be kept in R15
   * @param systemThreadId   an array of all system thread ID for this process
   * @return an array for the special purpose register, or null if the thread is not 
   *         loaded in any system thread
   * @see OsProcess.getSystemThreadId
   */
  public int [] getSystemThreadSPR(int VMThreadID, int systemThreadId[]) {
    return Platform.getSystemThreadSPR(VMThreadID, systemThreadId);
  }

  // Same as above but the registers are specified as string to allow symbolic name
  public int   read(String regname) throws Exception {
    return read(regGetNum(regname));
  }

  public float fread(String regname) throws Exception {
    return fread(regGetNum(regname));
  }

  public void write(String regname, int value) throws Exception {
    write(regGetNum(regname), value);
  }

  public void fwrite(String regname, float value) throws Exception {
    fwrite(regGetNum(regname), value);
  }

  /**
   * Implement abstract method: get the hardware thread pointer register
   * Intel does not have a thread pointer register
   * @return the TP value
   * @exception
   * @see VM_Statics 
   */
  public int hardwareTP(){
    int proc = read(PR);
    int thread = owner.mem.read(proc + VM_Entrypoints.activeThreadOffset);    
    return thread;
  }

  /**
   * Implement abstract method: get the hardware IP register (R128=IAR)
   * @return the IP value
   * @exception
   * @see VM_Statics 
   */
  public int hardwareIP(){
    return read(IAR);
  }


  /**
   * Cache JTOC value for use when we are in a native stack frame and the TOC register
   * holds the TOC value for native codes instead of the JVM
   * NOTE: this only works if VM_BootRecord does not move 
   *
   */
  public void cacheJTOC() {
    try {    

      // if this is the first time, keep whatever we find
      // this means that the first call to cacheJTOC must be at a point when the JTOC register is valid
      if (cachedJTOC==0) {
	if (Debugger.interpretMode)
	  // in interpreted mode, get it from mapVM, which is read from the file Jalapeno.map
	  cachedJTOC = mapVM.cachedJTOC;   
	// System.out.println("CAUTION: cacheJTOC todo in mapVM.cachedJTOC");
	else
	  // in boot only mode, get the value directly from the register when system comes up
	  cachedJTOC = read("JT");
	return;
      }

      // for other time, check if the code is within the JVM space
      int currentIP = read("IP");
      if (!owner.bmap.isInJVMspace(currentIP)) {
	// System.out.println("cacheJTOC: cached " + VM.intAsHexString(currentJTOC));
	int currentPROC = read("PR");
	cachedJTOC = owner.mem.read(currentPROC + VM_Entrypoints.jtocOffset);
      } 
      // else {
      //   System.out.println("cacheJTOC: not cached " + VM.intAsHexString(currentJTOC) +
      // 		      ", keeping old value " + VM.intAsHexString(cachedJTOC));
      // }

    } catch (Exception e) {
      System.out.println("cacheJTOC: could not read JTOC register, check if the name JT have changed in VM_BaselineConstants.java, GPR_NAMES");          
    }  
  }

  public int getJTOC() {
    return cachedJTOC;
  }

  /** 
   * Get the Instruction Pointer for the context thread
   * 
   */
  public int currentIP() {
    try {
      return getContextRegister("IP");
    } catch (Exception e) {
      System.out.println("ERROR getting current IP from context");
      return 0;
    }
  }

  /** 
   * Get the Frame Pointer for the context thread
   * 
   */
  public int currentFP(){
    try {
      return getContextRegister("FP");
    } catch (Exception e) {
      return 0;
    }
  }

  /** 
   * Get the Stack Pointer for the context thread
   *
   */
  public int currentSP(){
    try {
      return getContextRegister("SP");
    } catch (Exception e) {
      return 0;
    }
  }

  /** 
   * Get a integer register by name for the context thread
   * (GPR or IP)
   */
  public int getContextRegister(String regName) throws Exception {
    // At the beginning, the Jalapeno thread structure is not yet setup
    // the context thread ID is kept as 0 to indicate this
    // In this case, go straight to the hardware registers
    if (contextThread==0) {
      return read(regName);
    } else {
      int regnum, regs[];
      regnum = regGetNum(regName);
      if (regnum==IAR) {
	int sprs[] = getVMThreadSPR(contextThread);
	return sprs[0];
      } else if (regnum==FP) {
	int sprs[] = getVMThreadSPR(contextThread);
	return sprs[1];	  
      } else {
	regs = getVMThreadGPR(contextThread);
	return regs[regnum];
      }
    }
  }

  /** 
   * Access the registers for the context thread
   *
   */
  public String getValue(String regname, int count) throws Exception {
    int regnum, regs[], i, j, k;    
    double fregs[];
    int systemThreadID[];
    int regdata;    
    String result="";
        
    regnum = regGetNum(regname);
    if (regnum != -1) {
      
      if (count == 0) {   /* print all registers */
	regs = getVMThreadGPR(contextThread);
	if (regs==null) {
	  result += "null";
	} else {
	  for (i=0; i<9; i++) {   /* the general purpose reg */
	    if ( i < JALAPENO_GPR_NAMES.length )
	      result += JALAPENO_GPR_NAMES[i];
	    else
	      result += "IP";

	    result += "  = " + 
	      VM.intAsHexString(regs[i]) + "  " + regGetName(i);
	    result += "\n";
	  }
	}

	if (threadIsLoaded(contextThread)) {
          /* floating point registers */
	  int tag = Platform.fregtag();
	  int top = Platform.fregtop();
	  // result += "tag ";
	  // result += VM.intAsHexString(tag);
	  // result += "\n";
	  result += "FPR stack:\n";
	  // count the number of stack slots with valid value
	  int numValid = 0;
	  for (i=0; i<8; i++) {   
	    int status = (tag>>(i*2)) & 0x3;
	    if (status==0)
	      numValid++;
	  }

	  // get the values
	  int stackTop = 0;
	  for (i=0; i<8; i++) {   
	    double fpr= Platform.readfreg(i);
	    result += FPR_NAMES[i];
	    if (stackTop++<numValid)
	      result += " (valid) = " + fpr + "\n";
	    else 
	      result += " (empty) = " + fpr + "\n";
	  }
	} else {
	  fregs = getVMThreadSavedFPR(contextThread);
	  result += "FPR saved context :\n";
	  for (i=fregs.length-1; i>=0; i--) { 
	    result += "FP" + i + " = " + fregs[i];
	    result += "\n";	    
	  }
	}

      } 
  
      else if (count == 1) {   /* print specific registers */
  	if ((regnum>=GPR0 && regnum<=GPR7) || (regnum==IAR)|| (regnum==LR)) {
	  try {
	    regdata = getContextRegister(regname);
	    result += regname + " = " + VM.intAsHexString(regdata) + "  " + regGetName(regnum);
	  } catch (Exception e) {
	    result += regname + " = (not available) " + regGetName(regnum);
	  }
   	} 
      }  
  
      else if (count < 8) {   /* print range of registers */
	regs = getVMThreadGPR(contextThread);
  	for (i=regnum; i<=regnum+count; i++) {
	  result += " R" + i + " = " + VM.intAsHexString(regs[i]) + "  " + regGetName(i);
  	}
      }
  
    }
  
    return result;

  }




}


