/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/*
 * This class represents the machine registers
 * We can inspect them, set new values
 * This is the external implementation:  the registers are
 * accessed from outside the debuggee process;  most methods
 * are native and thus platform-dependent
 * The names for the registers are picked up from VM_BaselineConstants.java
 * @author Ton Ngo 2/98
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
    return Platform.readfreg(regnum);
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
   * Implement abstract method: get the hardware thread pointer register (R15)
   * @return the TP value
   * @exception
   * @see VM_Statics 
   */
  public int hardwareTP(){
    return read(GPR15);
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
      int currentJTOC = read("JT");
      // if this is the first time, keep whatever we find
      // this means that the first call to cacheJTOC must be at a point when the JTOC register is valid
      if (cachedJTOC==0) {
	if (Debugger.interpretMode)
	  // in interpreted mode, get it from mapVM, which is read from the file RVM.map
	  cachedJTOC = mapVM.cachedJTOC;   
	else
	  // in boot only mode, get the value directly from the register when system comes up
	  cachedJTOC = currentJTOC;
	return;
      }

      // for other time, check if the value within the JVM space
      if (owner.bmap.isInRVMspace(currentJTOC)) {
	// System.out.println("cacheJTOC: cached " + VM.intAsHexString(currentJTOC));
	cachedJTOC = currentJTOC;
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
    // At the beginning, the RVM thread structure is not yet setup
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
      } else if (regnum==LR) {
	int sprs[] = getVMThreadSPR(contextThread);
	return sprs[1];	  
      } else if (regnum==CR) {
	int sprs[] = getVMThreadSPR(contextThread);
	if (sprs.length==3)
	  return sprs[2];
	else
	  throw new Exception("not available");
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
    int systemThreadID[];
    int regdata;    
    double fregs[];
    String result="";
        
    regnum = regGetNum(regname);
    if (regnum != -1) {
      
      if (count == 0) {   /* print all registers */
	regs = getVMThreadGPR(contextThread);
  	for (i=0; i<8; i++) {   /* the general purpose reg */
  	  for (j=0; j<4; j++) {
  	    k = j*8+i;
  	    if (k<10)    // just to line things up
	      result += "   R" + k + ":" + VM.intAsHexString(regs[k]);
	    // + " " + regGetName(k);
  	    else
	      result += "  R" + k + ":" + VM.intAsHexString(regs[k]);
  	  }
  	  result += "\n";
  	}
	result += "\n";

	// the floating point regs: 32 regs starting with FPR0 
	if (Debugger.showFPRsPreference) {
	  fregs = getVMThreadFPR(contextThread);
	  for (i=0; i<8; i++) {   
	    for (j=0; j<4; j++ ) {
	      k = j*8 + i;
	      if (Debugger.fprPreference=='f') {
		result += "  FR" + k + " = " + fregs[k];
	      } else {
		result += "  FR" + k + " = ";
		result += Long.toHexString(Double.doubleToLongBits(fregs[k]));
	      }
	    }
	    result += "\n";
	  }
	}

	// the special purpose reg: just IP, LR and CR for now
	regs = getVMThreadSPR(contextThread);
	result += "  IP = " + VM.intAsHexString(regs[0]) + 
	  "   LR = " + VM.intAsHexString(regs[1]);
	if (regs.length==3)
	  result += "   CR = " + VM.intAsHexString(regs[2]) + "\n";
	else
	  result += "   CR = (not available) \n";

	if (!Debugger.showFPRsPreference)
	  result += "To see FPRs specify 'pref showFPRs true'\n";
	else
	  result += "To hide FPRs specify 'pref showFPRs false'\n";
	result += "To see register names eg FP, PR and JT use command 'regnames'\n";

      } 
  
      else if (count == 1) {   /* print specific registers */
  	if ((regnum>=GPR0 && regnum<=GPR31) || (regnum==IAR)|| (regnum==LR)
	     || (regnum==CR) ) {
	  try {
	    regdata = getContextRegister(regname);
	    result += " R" + regnum + ":" + VM.intAsHexString(regdata) + "  " + regGetName(regnum);
	  } catch (Exception e) {
	    result += " R" + regnum + ": (not available) " + regGetName(regnum);
	  }
   	} else if ((regnum>=FPR0) && (regnum<=FPR31)) {
	  fregs = getVMThreadFPR(contextThread);
	  result += " FR" + (regnum-FPR0) + ":" + fregs[regnum] + "  " + regGetName(regnum);
  	} 	
      }  
  
      else if (count < 32) {   /* print range of registers */
	regs = getVMThreadGPR(contextThread);
	fregs = getVMThreadFPR(contextThread);	
  	for (i=regnum; i<=regnum+count; i++) {
  	  if (i<FPR0) {
	    result += " R" + i + ":" + VM.intAsHexString(regs[i]);
  	  } else if ((i>=FPR0) && (i<=FPR31)) {
	    result += " FR" + (i-FPR0) + ":" + fregs[i];
  	  }	
  	}
	result += "\n To see register names eg PR, FP etc use command regnames \n";
      }
  
    }
  
    return result;

  }


  /** 
   * Show the register symbolic names
   *
   */
  public String getNames() throws Exception {
    int regnum, regs[], i, j, k;    
    int systemThreadID[];
    int regdata;    
    double fregs[];
    String result="";
        
    regnum = 0;  // start with first register
    for (i=0; i<8; i++) {   /* the general purpose reg */
      for (j=0; j<4; j++) {
	k = j*8+i;
	if (k<10)    // just to line things up
	  result += "   R" + k + " = " + regGetName(k);
	else
	  result += "  R" + k + " = " + regGetName(k);
      }
      result += "\n";
    }
    result += "\n";

    // the floating point regs: 32 regs starting with FPR0 
    for (i=0; i<8; i++) {   
      for (j=0; j<4; j++ ) {
	k = j*8 + i;
	result += "  FR" + k + " = " + regGetName(k+FPR0);
      }
      result += "\n";
    }

    return result;
  }


}


