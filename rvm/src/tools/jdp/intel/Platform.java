/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * This is the native interface to the Intel platform
 * The native code is based on fork() and ptrace()
 * The process ID is saved in Java: each native call
 * that requires the ID has a back end matching method that
 * inserts the saved ID to the JNI code.
 * @author Ton Ngo  (3/31/01)
 * 
 */

public class Platform implements jdpConstants {
  /**
   * saved process ID for the native code
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

  public static native boolean   mstep1(int pid);
  public static        boolean   mstep() {return mstep1(pid); }

  public static native void      mcontinue1(int pid, int signal);
  public static        void      mcontinue(int signal) { mcontinue1(pid, signal); }

  // public static native void      mcontinueThread1(int pid, int signal);
  // public static        void      mcontinueThread(int signal) { 
  //   mcontinueThread1(pid, signal); 
  // }

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
  public static native boolean   isSegFault(int status);
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
    // Intel TEMP:  Thread not supported yet
    // return getSystemThreadId1(pid);
    return null;
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
  public static native int       readmem1(int pid, int address) throws Exception;   
  public static        int       readmem(int address)  { 
    try {
      return readmem1(pid, address);
    } catch (Exception e) {
      /* e.printStackTrace(); */
      return -1;
    }
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
   * For Intel, we write the INT 3 instruction at the address or use the debug register
   **/

  public static native void      setbp1(int pid, int address);
  public static        void      setbp(int address){ 
    setbp1(pid, address);
  }

  public static native void      clearbp1(int pid, int address);
  public static        void      clearbp(int address){ 
    clearbp1(pid, address);
  }
  
  public static native void printbp1(int pid);  
  public static        void printbp() {
    printbp1(pid);
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
    case 1:
      data = data >> 8;
      break;
    case 2:
      data = data >> 16;
      break;
    case 3:
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
    
    if (lobits==1) {
      data = data >> 16;
    }
    data &= 0x0000FFFF; 
    return new Integer(data).shortValue();
  }



  /********************************************************************
   * for registerExternal.java 
   **/
  public static native void      setRegisterMapping(int sp, String[] gprName, String[] fprName, int tpshift);

  public static native int       readreg1(int pid, int regnum);
  public static        int       readreg(int regnum){
    return readreg1(pid, regnum);
  }

  public static native int       currentFP1(int pid);
  public static        int       currentFP() {
    return currentFP1(pid);
  }

  public static native int       getFPFromPR(int pr);

  public static native int       fregtop1(int pid);
  public static        int       fregtop (){
    int index = fregtop1(pid);
    return index;
  }

  public static native int       fregtag1(int pid);
  public static        int       fregtag (){
    int tag = fregtag1(pid);
    return tag;
  }

  public static native byte[]    readfreg1(int pid, int regnum);
  public static        double    readfreg(int regnum){
    byte[] fp =  readfreg1(pid, regnum);

    // System.out.println("readfreg: convert Intel 10-byte float to Java float");
    // The hardware mantissa has 4 extra bits
    // The hardware significant has 11 extra bits
    // The implicit "1" between the two portion is explicit
    // total of 16 extra bits -> 80 bits 
    // hardware = smmm mmmm mmmm mmmm 1 dddd dddd .... dddd dddd dddd ddd
    // software = s     mmm mmmm mmmm   dddd dddd .... dddd 
    int hi = ((fp[0] & (byte) 0xC0) << 24) | ((fp[0] & (byte) 0x03) << 28);
    hi |= ((fp[1] << 20) & 0x0ff00000);
    hi |= (fp[2] << 13)  & 0x000fe000;
    hi |= ((fp[3] << 5)  & 0x00001fe0);
    hi |= ((fp[4] >> 3)  & 0x0000001f);      

    int lo = ((fp[4] << 29) & 0xE0000000);
    lo |= ((fp[5] << 21) & 0x1FE00000);
    lo |= ((fp[6] << 13) & 0x001FE000);
    lo |= ((fp[7] << 5)  & 0x00001FE0);
    lo |= ((fp[8] >> 3)  & 0x0000001F);
    long lomask = 0xffffffff;
    lomask = (lomask << 32) >>> 32;
    long fpBits = ((long) hi << 32) | ((long) lo & lomask);
    double result = Double.longBitsToDouble(fpBits);

    // System.out.println("float bits = " + Integer.toHexString(hi) + " " + Integer.toHexString(lo) +
    //		       " = " + result);

    return result;
  }

  public static        byte[]     readfregraw(int regnum){
    byte[] fpBuffer =  readfreg1(pid, regnum);
    return fpBuffer;
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

  /**
   * Compute the next valid instruction address
   * 
   *
   */
  public static int nextInstructionAddress(int address)  {
    // enough to capture one instruction and compute the next address
    int byteLength = 16;     
    byte instrByte[]   = new byte[byteLength];
    byte instrLength[] = new byte[byteLength];
    int  instrWord[]   = new int[(byteLength>>2)];
    for (int i = 0; i<(byteLength>>2); i++) {
      instrWord[i] = Platform.readmem(address + i*4);
    }    
    instrByte   = IntelDisassembler.intToByteArray(instrWord);
    instrLength = IntelDisassembler.instructionLength(instrByte);
    
    return (address + instrLength[0]); 
  }


}
