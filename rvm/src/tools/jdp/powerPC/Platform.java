/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/*
 * This is the native interface to the AIX platform
 * The native code is based on fork() and ptrace()
 * The AIX process ID is saved in Java: each native call
 * that requires the ID has a back end matching method that
 * inserts the saved ID to the JNI code.
 * @author Ton Ngo  (1/11/99)
 * 
 */

public class Platform implements jdpConstants {
  /**
   * saved AIX process ID for the native code
   */
  static int pid=0; 

  static boolean initDone = false;

  public static void init(){
    if (!initDone) {
      initDone = true;
      System.loadLibrary("osprocess");
    }
  }

  /* for OsProcessExternal.java */
  public static native void      mkill1(int pid);
  public static        void      mkill() { 
    mkill1(pid); 
    pid = 0;
  }

  public static native void      mdetach1(int pid);
  public static        void      mdetach() { mdetach1(pid); }

  public static native int       mwait();

  public static native void      mcontinue1(int pid, int signal);
  public static        void      mcontinue(int signal) { mcontinue1(pid, signal); }

  public static native void      mcontinueThread1(int pid, int signal);
  public static        void      mcontinueThread(int signal) { 
    mcontinueThread1(pid, signal); 
  }

  public static native int       createDebugProcess1(String ProgramName, String args[]);
  public static        int       createDebugProcess(String ProgramName, String args[]){
    pid = createDebugProcess1(ProgramName, args);
    return pid;
  }

  public static native int       attachDebugProcess1(int processID);
  public static        int       attachDebugProcess(int processID) {
    pid = attachDebugProcess1(processID);
    return pid;
  }

  public static native boolean   isBreakpointTrap(int status);
  public static native boolean   isLibraryLoadedTrap(int status);
  public static native boolean   isIgnoredTrap(int status);
  public static native boolean   isKilled(int status);
  public static native boolean   isExit(int status);
  public static native String    statusMessage(int status);

  public static native String    getNativeProcedureName1(int pid, int instructionAddress);
  public static        String    getNativeProcedureName(int instructionAddress) {
    return getNativeProcedureName1(pid, instructionAddress);
  }

							
  public static native int[]     getSystemThreadId1(int pid);
  public static        int[]     getSystemThreadId() { 
    return getSystemThreadId1(pid);
  }

  public static native String    listSystemThreads1(int pid);  
  public static        String    listSystemThreads() { 
    return listSystemThreads1(pid);
  }

  public static native int[]     getVMThreadIDInSystemThread1(int pid);
  public static        int[]     getVMThreadIDInSystemThread() { 
    return getVMThreadIDInSystemThread1(pid);
  }


  /********************************************************************
   * for memoryExternal.java 
   **/
  public static native int       readmem1(int pid, int address);   
  public static        int       readmem(int address)  { 
    return readmem1(pid, address);
  }

  public static native int[]     readmemblock1(int pid, int address, int count);
  public static        int[]     readmemblock(int address, int count){ 
    return readmemblock1(pid, address, count);
  }

  public static native void      writemem1(int pid, int address, int value);
  public static        void      writemem(int address, int value){ 
    writemem1(pid, address, value);
  }

  public static native int       branchTarget1(int pid, int instruction, int address);
  public static        int       branchTarget(int instruction, int address){ 
    return branchTarget1(pid, instruction, address);
  }


  /******************************************************************************
   * Methods for breakpoint
   * setting actual breakpoint is machine dependent so it should be native.
   * For RS6000, we write an invalid instruction at the address
   * For Intel, we write the INT 3 instruction at the address
   **/

  public static native void      setbp1(int pid, int address, int brkpt_instruction);
  public static        void      setbp(int address){ 
    setbp1(pid, address, BKPT_INSTRUCTION);
  }

  public static native void      clearbp1(int pid, int address, int instr);
  public static        void      clearbp(int address, int instr){ 
    clearbp1(pid, address, instr);
  }
  

  /**
   * Read a byte of memory
   * @param address a random byte address
   * @return a byte
   * @exception
   * @see
   */
  public static byte readByte(int address) {
    int waddr = address & 0xFFFFFFFC;
    int data = readmem(waddr);     
    int lobits = address & 0x00000003;

    switch (lobits) {    // pick out the byte requested
    case 2:
      data = data >> 8;
      break;
    case 1:
      data = data >> 16;
      break;
    case 0:
      data = data >> 24;
      break;
    }
    data &= 0x000000FF; 
    return new Integer(data).byteValue();
  }

  /**
   * Read a short word from memory
   * @param address a random address (aligned to 2-bytes)
   * @return
   * @exception
   * @see
   */
  public static short readShort(int address) {
    int waddr = address & 0xFFFFFFFC;
    // System.out.println("2 byte aligned at " + Integer.toHexString(waddr));
    int data = readmem(waddr);     
    int lobits = address & 0x00000002;
    
    if (lobits==0) {
      data = data >> 16;
    }
    data &= 0x0000FFFF; 
    return new Integer(data).shortValue();
  }

  /**
   * Read a long (two words) from memory
   * @param address a random address 
   * @return
   * @exception
   * @see
   */
  public static long readLong(int address) {
    int mappedFieldValue = readmem(address);
    int mappedFieldValue1 = readmem(address+4);
    long newlong;
    newlong = mappedFieldValue;
    newlong = (newlong << 32);
    newlong |= ((long)mappedFieldValue1) & 0xFFFFFFFFL;
    return newlong;
  }

  /**
   * Read a double (two words) from memory
   * @param address a random address 
   * @return
   * @exception
   * @see
   */
  public static double readDouble(int address) {
    int mappedFieldValue = readmem(address);
    int mappedFieldValue1 = readmem(address+4);
    double newdouble;
    long templong;
    
    templong = mappedFieldValue;
    templong = (templong << 32);
    templong |= ((long)mappedFieldValue1) & 0xFFFFFFFFL;;
    newdouble = Double.longBitsToDouble(templong);
    return newdouble;
  }

  /********************************************************************
   * for registerExternal.java 
   **/
  public static native void      setRegisterMapping(int fp, int sp, int tp, int jtoc, String[] gprName, String[] fprName, int tpshift);

  public static native int       readreg1(int pid, int regnum);
  public static        int       readreg(int regnum){
    return readreg1(pid, regnum);
  }

  public static native float     readfreg1(int pid, int regnum);
  public static        float     readfreg(int regnum){
    return readfreg1(pid, regnum);
  }

  public static native void      writereg1(int pid, int regnum, int value);     
  public static        void      writereg(int regnum, int value)   {
    writereg1(pid, regnum, value); 
  }

  public static native void      writefreg1(int pid, int regnum, float value);  
  public static        void      writefreg(int regnum, float value){
    writefreg1(pid, regnum, value); 
  }

  public static native int []    getSystemThreadGPR1(int pid, int VMThreadID, int systemThreadId[]);
  public static        int []    getSystemThreadGPR(int VMThreadID, int systemThreadId[]){
    return getSystemThreadGPR1(pid, VMThreadID, systemThreadId);
  }

  public static native double [] getSystemThreadFPR1(int pid, int VMThreadID, int systemThreadId[]);
  public static        double [] getSystemThreadFPR(int VMThreadID, int systemThreadId[]){
    return getSystemThreadFPR1(pid, VMThreadID, systemThreadId);
  }

  public static native int []    getSystemThreadSPR1(int pid, int VMThreadID, int systemThreadId[]);
  public static        int []    getSystemThreadSPR(int VMThreadID, int systemThreadId[]){
    return getSystemThreadSPR1(pid, VMThreadID, systemThreadId);
  }

  /********************************************************************
   * for Debugger.java
   **/
  public static final int initialbp_offset = 16;
  public static final int stepbrImplemented = 1; // Yes - implemented
  public static final int cthreadImplemented = 1; // Yes - implemented
  public static final int listtRunImplemented = 1; // Yes - implemented
  public static final int listtSystemImplemented = 1; // Yes - implemented
  public static final int listiNegCountImplemented = 1; // Yes - implemented
  public static final String extraRegNames = "IP LR CR\n";

  public static void printbp() {
    // No significance on PowerPC - do nothing
  }

}
