/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Support for lowlevel (ie non-JNI) invocation of C functions.
 * 
 * All methods of this class have the following signature:
 * <pre>
 * public static <TYPE> NAME(<args to pass to sysNAME via native calling convention>)
 * </pre>
 * When one of Jikes RVM's compilers encounters an invokestatic that invokes
 * a method of this class, instead of generating code that calls the method
 * it looks up offset of the matching function pointer in VM_BootRecord.java 
 * and generates code to invoke it passing the rest of the arguments 
 * using the native OS calling convention.  The result of the call is assumed
 * to be returned using the native OS calling convention.
 * <p>
 * NOTE: From the standpoint of the rest of the VM, an invocation 
 * to a method of VM_SysCall is uninterruptible.
 * <p>
 * NOTE: There must be a matching field NAMEIP in VM_BootRecord.java
 *       for each method declared here.
 * 
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_SysCall implements VM_Uninterruptible { 

  // startup/shutdown
  public static void sysWriteChar(char v) {} 
  public static void sysWrite(int value, int hexToo) {}
  public static void sysWriteLong(long value, int hexToo) {}
  public static void sysExit(int value) {}
  public static int sysArg(int argno, byte[] buf, int buflen) { return 0; }

  // memory
  public static void sysCopy(VM_Address dst, VM_Address src, int cnt) {}
  public static void sysFill(VM_Address dst, int pattern, int cnt) {}
  public static VM_Address sysMalloc(int length) { return null; }
  public static void sysFree(VM_Address location) {} 
  public static void sysZero(VM_Address dst, int cnt) {}
  public static void sysZeroPages(VM_Address dst, int cnt) {}
  public static void sysSyncCache(VM_Address address, int size) {}

  // files
  public static int sysStat(byte[] name, int kind) { return 0; }
  public static int sysList(byte[] name, byte[] buf, int limit) { 
    return 0; 
  }
  public static int sysOpen(byte[] name, int how) { return 0; }
  public static int sysUtime(byte[] fileName, int modTimeSec) {
    return 0;
  }
  public static int sysReadByte(int fd) { return 0; }
  public static int sysWriteByte(int fd, int data) { return 0; }
  public static int sysReadBytes(int fd, VM_Address buf, int cnt) { return 0; }
  public static int sysWriteBytes(int fd, VM_Address buf, int cnt) { return 0; }
  public static int sysSeek(int fd, int offset, int whence) { return 0; }
  public static int sysClose(int fd) { return 0; }
  public static int sysDelete(byte[] name) { return 0; }
  public static int sysRename(byte[] fromName, byte[] toName) { return 0; }
  public static int sysMkDir(byte[] name) { return 0; }
  public static int sysBytesAvailable(int fd) { return 0; }
  public static int sysIsValidFD(int fd) { return 0; }
  public static int sysLength(int fd) { return 0; }
  public static int sysSetLength(int fd, int len) { return 0; }
  public static int sysSyncFile(int fd) { return 0; }
  public static int sysIsTTY(int fd) { return 0; }
  public static int sysSetFdCloseOnExec(int fd) { return 0; }
  public static int sysAccess(byte[] name, int kind) { return 0; }

  // shm* - memory mapping
  public static int sysShmget(int key, int size, int flags) { return 0; }
  public static int sysShmctl(int shmid, int command) { return 0; }
  public static VM_Address sysShmat(int shmid, VM_Address addr, int flags) { 
    return null; 
  }
  public static int sysShmdt(VM_Address addr) { return 0; }

  // mmap - memory mapping
  public static VM_Address sysMMap(VM_Address start, VM_Extent length, int protection,
                                   int flags, int fd, long offset) { 
    return null; 
  }
  public static VM_Address sysMMapNonFile(VM_Address start, VM_Extent length,
                                          int protection, int flags) {
    return null;
  }
  public static VM_Address sysMMapGeneralFile(VM_Address start, VM_Extent length,
                                              int fd, int prot) {
    return null;
  }
  public static VM_Address sysMMapDemandZeroFixed(VM_Address start, VM_Extent length) {
    return null;
  }
  public static VM_Address sysMMapDemandZeroAny(VM_Extent length) { return null; }
  public static int sysMUnmap(VM_Address start, VM_Extent length) {
    return 0;
  }
  public static int sysMProtect(VM_Address start, VM_Extent length, int prot) {
    return 0;
  }
  public static int sysMSync(VM_Address start, VM_Extent length, int flags) {
    return 0;
  }
  public static int sysMAdvise(VM_Address start, VM_Extent length, int advice) {
    return 0;
  }
  public static int sysGetPageSize() { return 0; }

  // threads
  public static int sysNumProcessors() { return 0; }
  /**
   * Create a virtual processor (aka "unix kernel thread", "pthread").
   * @param jtoc  register values to use for thread startup
   * @param pr
   * @param ip
   * @param fp
   * @return virtual processor's o/s handle
   */
  public static int sysVirtualProcessorCreate(VM_Address jtoc, 
                                              VM_Address pr, 
                                              VM_Address ip,
                                              VM_Address fp) { 
    return 0;
  }
  /**
   * Bind execution of current virtual processor to specified physical cpu.
   * @param cpuId  physical cpu id (0, 1, 2, ...)
   */
  public static void sysVirtualProcessorBind(int cpuid) {}
  public static void sysVirtualProcessorYield() {}
  /**
   * Start interrupt generator for thread timeslicing.
   * The interrupt will be delivered to whatever virtual processor happens 
   * to be running when the timer expires.
   */
  public static void sysVirtualProcessorEnableTimeSlicing(int timeSlice) {}
  public static int sysPthreadSelf() { return 0; }
  public static int sysPthreadSignal(int pthread) { return 0; }
  public static void sysPthreadExit() {}
  public static int sysPthreadJoin(int pthread) { return 0; }
  //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  public static int sysStashVmProcessorInPthread(VM_Processor vmProcessor) { return 0; }
  //-#endif

  // arithmetic 
  public static long sysLongDivide(long x, long y) { return 0; }
  public static long sysLongRemainder(long x, long y) { return 0; }
  public static float sysLongToFloat(long x) { return 0f; }
  public static double sysLongToDouble(long x) { return 0; }
  public static int sysFloatToInt(float x) { return 0; }
  public static int sysDoubleToInt(double x) { return 0; }
  public static long sysFloatToLong(float x) { return 0; }
  public static long sysDoubleToLong(double x) { return 0; }
  //-#if RVM_FOR_POWERPC
  public static double sysDoubleRemainder(double x, double y) { return 0; }
  //-#endif

  /**
   * Used to parse command line arguments that are
   * doubles and floats early in booting before it 
   * is safe to call Float.valueOf or Double.valueOf.
   * NOTE: this does not support the full Java spec of parsing a string
   *       into a float.
   * @param buf a null terminated byte[] that can be parsed
   *            by sscanf("%f")
   * @return the double value produced by the call to sscanf on buf.
   */
  public static float sysPrimitiveParseFloat(byte[] buf) { return 0; }

  /**
   * Used to parse command line arguments that are
   * bytes and ints early in booting before it 
   * is safe to call Byte.parseByte or Integer.parseInt.
   * 
   * @param buf a null terminated byte[] that can be parsed
   *            by sscanf("%d")
   * @return the int value produced by the call to sscanf on buf.
   */
  public static int sysPrimitiveParseInt(byte[] buf) { return 0; }

  // time
  public static long sysGetTimeOfDay() { return 0; }

  // shared libraries
  public static VM_Address sysDlopen(byte[] libname) { return null; }
  public static void sysDlclose() {}
  public static VM_Address sysDlsym(VM_Address libHandler, byte[] symbolName) { return null; }
  public static void sysSlibclean() {}

  // network
  public static int sysNetLocalHostName(VM_Address buf, int limit) {
    return 0;
  }
  public static int sysNetRemoteHostName(int internetAddress, 
                                         VM_Address buf,
                                         int limit) {
    return 0;
  }
  public static int sysNetHostAddresses(VM_Address hostname, VM_Address buf, 
                                        int limit) {
    return 0;
  }
  public static int sysNetSocketCreate(int isStream) { return 0; }
  public static int sysNetSocketPort(int fd) { return 0; }
  public static int sysNetSocketFamily(int fd) { return 0; }
  public static int sysNetSocketLocalAddress(int fd) { return 0; }
  public static int sysNetSocketBind(int fd, int family, int localAddress,
                                     int localPort) { 
    return 0;
  }
  public static int sysNetSocketConnect(int fd, int family, int remoteAddress,
                                        int remotePort) {
    return 0;
  }
  public static int sysNetSocketListen(int fd, int backlog) { return 0; }
  public static int sysNetSocketAccept(int fd, java.net.SocketImpl connectionObject) {
    return 0;
  }
  public static int sysNetSocketLinger(int fd, int enable, int timeout) {
    return 0;
  }
  public static int sysNetSocketNoDelay(int fd, int enable) {
    return 0;
  }
  public static int sysNetSocketNoBlock(int fd, int enable) {
    return 0;
  }
  public static int sysNetSocketClose(int fd) {
    return 0;
  }
  public static int sysNetSocketShutdown(int fd, int how) {
    return 0;
  }
  public static int sysNetSelect(int[] allFds, int rc, int wc, int ec) {
    return 0;
  }

  // process management
  public static void sysWaitPids(VM_Address pidArray, VM_Address exitStatusArray,
                                 int numPids) {}

  //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  // system startup pthread sync. primitives
  //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  public static void sysCreateThreadSpecificDataKeys() {}
  //-#endif
  public static void sysInitializeStartupLocks(int howMany) {}
  public static void sysWaitForVirtualProcessorInitialization() {} 
  public static void sysWaitForMultithreadingStart() {} 
  //-#endif

  //-#if RVM_WITH_HPM
  // sysCall entry points to HPM
  public static int sysHPMinit() { return 0; }
  public static int sysHPMsetEvent(int e1, int e2, int e3, int e4) { return 0; }
  public static int sysHPMsetEventX(int e5, int e6, int e7, int e8) {return 0; } 
  public static int sysHPMsetMode(int mode) { return 0; }
  public static int sysHPMgetNumberOfCounters() { return 0; }
  public static int sysHPMtest() { return 0; }
  public static int sysHPMsetProgramMyThread() { return 0; }
  public static int sysHPMstartMyThread() { return 0; }
  public static int sysHPMstopMyThread() { return 0; }
  public static int sysHPMresetMyThread() { return 0; }
  public static long sysHPMgetCounterMyThread(int counter) { return 0; }
  public static int sysHPMsetProgramMyGroup() { return 0; }
  public static int sysHPMstartMyGroup() { return 0; }
  public static int sysHPMstopMyGroup() { return 0; }
  public static int sysHPMresetMyGroup() { return 0; }
  public static long sysHPMgetCounterMyGroup(int counter) { return 0; }
  public static int sysHPMprintMyGroup() { return 0; }
  //-#endif

  //-#if RVM_WITH_GCSPY
  // sysCall entry points to GCSpy
  public static VM_Address gcspyDriverAddStream (VM_Address driver, int it) { return null; }
  public static void gcspyDriverEndOutput (VM_Address driver) {}
  public static void gcspyDriverInit (VM_Address driver, int id, VM_Address serverName, VM_Address driverName,
                               VM_Address title, VM_Address blockInfo, int tileNum,
			       VM_Address unused, int mainSpace) {}
  public static void gcspyDriverInitOutput (VM_Address driver) {}
  public static void gcspyDriverResize (VM_Address driver, int size) {}
  public static void gcspyDriverSetTileName (VM_Address driver, int i, int start, int end) {}
  public static void gcspyDriverSpaceInfo (VM_Address driver, VM_Address info) {}
  public static void gcspyDriverStartComm (VM_Address driver) {}
  public static void gcspyDriverStream (VM_Address driver, int id, int len) {}
  public static void gcspyDriverStreamByteValue (VM_Address driver, byte value) {}
  public static void gcspyDriverStreamShortValue (VM_Address driver, short value) {}
  public static void gcspyDriverStreamIntValue (VM_Address driver, int value) {}
  public static void gcspyDriverSummary (VM_Address driver, int id, int len) {}
  public static void gcspyDriverSummaryValue (VM_Address driver, int value) {}

  public static void gcspyIntWriteControl (VM_Address driver, int id, int tileNum) {}

  public static VM_Address gcspyMainServerAddDriver(VM_Address addr) { return null; }
  public static void gcspyMainServerAddEvent (VM_Address server, int event, VM_Address name) {}
  public static VM_Address gcspyMainServerInit (int port, int len, VM_Address name, int verbose) { return null; }
  public static int gcspyMainServerIsConnected (VM_Address server, int event) { return 0; }
  public static VM_Address gcspyMainServerOuterLoop () { return null; }; 
  public static void gcspyMainServerSafepoint (VM_Address server, int event) {};
  public static void gcspyMainServerSetGeneralInfo (VM_Address server, VM_Address info) {};
  public static void gcspyMainServerStartCompensationTimer (VM_Address server) {};
  public static void gcspyMainServerStopCompensationTimer (VM_Address server) {};

  public static void gcspyStartserver (VM_Address server, int wait, VM_Address serverOuterLoop) {};
   
  public static void gcspyStreamInit (VM_Address stream, int id, int dataType, VM_Address name,
                               int minValue, int maxValue, int zeroValue, int defaultValue,
			       VM_Address pre, VM_Address post, int presentation, int paintStyle,
			       int maxStreamIndex, int red, int green, int blue) {}

  public static void gcspyFormatSize (VM_Address buffer, int size) {}

  public static int gcspySprintf (VM_Address str, VM_Address format, VM_Address value) { return 0; }
  //-#endif

}
