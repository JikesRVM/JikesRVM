/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
public class VM_SysCall implements Uninterruptible { 

  // lowlevel write to console
  public static void sysWriteChar(char v) {} 
  public static void sysWrite(int value, int hexToo) {}
  public static void sysWriteLong(long value, int hexToo) {}
  public static void sysWriteDouble(double value, int postDecimalDigits) {}

  // startup/shutdown
  public static void sysExit(int value) {}
  public static int sysArg(int argno, byte[] buf, int buflen) { return 0; }

  // misc. info on the process -- used in startup/shutdown
  public static int sysGetenv(byte[] varName, byte[] buf, int limit) {
    return -2;
  }

  // memory
  public static void sysCopy(Address dst, Address src, int cnt) {}
  public static void sysFill(Address dst, int pattern, int cnt) {}
  public static Address sysMalloc(int length) { return null; }
  public static void sysFree(Address location) {} 
  public static void sysZero(Address dst, int cnt) {}
  public static void sysZeroPages(Address dst, int cnt) {}
  public static void sysSyncCache(Address address, int size) {}

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
  public static int sysReadBytes(int fd, Address buf, int cnt) { return 0; }
  public static int sysWriteBytes(int fd, Address buf, int cnt) { return 0; }
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
  public static Address sysShmat(int shmid, Address addr, int flags) { 
    return null; 
  }
  public static int sysShmdt(Address addr) { return 0; }

  // mmap - memory mapping
  public static Address sysMMap(Address start, Extent length, int protection,
                                   int flags, int fd, long offset) { 
    return null; 
  }
  public static Address sysMMapErrno(Address start, Extent length, int protection,
					int flags, int fd, long offset) { 
    return null; 
  }
  public static int sysMUnmap(Address start, Extent length) {
    return 0;
  }
  public static int sysMProtect(Address start, Extent length, int prot) {
    return 0;
  }
  public static int sysMSync(Address start, Extent length, int flags) {
    return 0;
  }
  public static int sysMAdvise(Address start, Extent length, int advice) {
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
  public static int sysVirtualProcessorCreate(Address jtoc, 
                                              Address pr, 
                                              Address ip,
                                              Address fp) { 
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
   *
   * This aborts in case of errors, with an appropriate error message.
   *
   * NOTE: this does not support the full Java spec of parsing a string
   *       into a float.
   * @param buf a null terminated byte[] that can be parsed
   *            by strtof()
   * @return the floating-point value produced by the call to strtof() on buf.
   */
  public static float sysPrimitiveParseFloat(byte[] buf) { return 0; }

  /**
   * Used to parse command line arguments that are
   * bytes and ints early in booting before it 
   * is safe to call Byte.parseByte or Integer.parseInt.
   * 
   * This aborts in case of errors, with an appropriate error message.
   *
   * @param buf a null terminated byte[] that can be parsed
   *            by strtol()
   * @return the int value produced by the call to strtol() on buf.
   * 
   */
  public static int sysPrimitiveParseInt(byte[] buf) { return 0; }

  // time
  public static long sysGetTimeOfDay() { return 0; }

  // shared libraries
  public static Address sysDlopen(byte[] libname) { return null; }
  public static void sysDlclose() {}
  public static Address sysDlsym(Address libHandler, byte[] symbolName) { return null; }
  public static void sysSlibclean() {}

  // network
  public static int sysNetLocalHostName(Address buf, int limit) {
    return 0;
  }
  public static int sysNetRemoteHostName(int internetAddress, 
                                         Address buf,
                                         int limit) {
    return 0;
  }
  public static int sysNetHostAddresses(Address hostname, Address buf, 
                                        int limit) {
    return 0;
  }
  public static int sysNetSocketCreate(int isStream) { return 0; }
  public static int sysNetSocketPort(int fd) { return 0; }
  public static int sysNetSocketSndBuf(int fd) { return 0; }
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
  public static void sysWaitPids(Address pidArray, Address exitStatusArray,
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
  public static int sysHPMgetNumberOfEvents()   { return 0; }
  public static int sysHPMisBigEndian()         { return 0; }
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
  public static Address gcspyDriverAddStream (Address driver, int it) { return null; }
  public static void gcspyDriverEndOutput (Address driver) {}
  public static void gcspyDriverInit (Address driver, int id, Address serverName, Address driverName,
                               Address title, Address blockInfo, int tileNum,
			       Address unused, int mainSpace) {}
  public static void gcspyDriverInitOutput (Address driver) {}
  public static void gcspyDriverResize (Address driver, int size) {}
  public static void gcspyDriverSetTileName (Address driver, int i, Address start, Address end) {}
  public static void gcspyDriverSpaceInfo (Address driver, Address info) {}
  public static void gcspyDriverStartComm (Address driver) {}
  public static void gcspyDriverStream (Address driver, int id, int len) {}
  public static void gcspyDriverStreamByteValue (Address driver, byte value) {}
  public static void gcspyDriverStreamShortValue (Address driver, short value) {}
  public static void gcspyDriverStreamIntValue (Address driver, int value) {}
  public static void gcspyDriverSummary (Address driver, int id, int len) {}
  public static void gcspyDriverSummaryValue (Address driver, int value) {}

  public static void gcspyIntWriteControl (Address driver, int id, int tileNum) {}

  public static Address gcspyMainServerAddDriver(Address addr) { return null; }
  public static void gcspyMainServerAddEvent (Address server, int event, Address name) {}
  public static Address gcspyMainServerInit (int port, int len, Address name, int verbose) { return null; }
  public static int gcspyMainServerIsConnected (Address server, int event) { return 0; }
  public static Address gcspyMainServerOuterLoop () { return null; }; 
  public static void gcspyMainServerSafepoint (Address server, int event) {};
  public static void gcspyMainServerSetGeneralInfo (Address server, Address info) {};
  public static void gcspyMainServerStartCompensationTimer (Address server) {};
  public static void gcspyMainServerStopCompensationTimer (Address server) {};

  public static void gcspyStartserver (Address server, int wait, Address serverOuterLoop) {};
   
  public static void gcspyStreamInit (Address stream, int id, int dataType, Address name,
                               int minValue, int maxValue, int zeroValue, int defaultValue,
			       Address pre, Address post, int presentation, int paintStyle,
			       int maxStreamIndex, int red, int green, int blue) {}

  public static void gcspyFormatSize (Address buffer, int size) {}

  public static int gcspySprintf (Address str, Address format, Address value) { return 0; }
  //-#endif

}
