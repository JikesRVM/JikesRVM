/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002,2003,2004
 */
//$Id: VM_SysCallMagic.java 10307 2006-02-28 16:52:13Z dgrove-oss $
package com.ibm.jikesrvm;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Support for lowlevel (ie non-JNI) invocation of C functions.
 * 
 * All methods of this class have the following signature:
 * <pre>
 * public static <TYPE> NAME(Address functionAddress, <args to pass to sysNAME via native calling convention>)
 * </pre>
 * When one of Jikes RVM's compilers encounters an invokestatic that invokes
 * a method of this class, instead of generating code that calls the method
 * it generates code to invoke it through functionAddress passing the rest of the arguments 
 * using the native OS calling convention.  The result of the call is assumed
 * to be returned using the native OS calling convention.
 * 
 * NOTE: When POWEROPEN_ABI is defined, the functionAddress is really a pointer to the
 *       function descriptor.
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_SysCallMagic { 

  // lowlevel write to console
  public static void sysWriteChar(Address functionAddress, char v) {} 
  public static void sysWrite(Address functionAddress, int value, int hexToo) {}
  public static void sysWriteLong(Address functionAddress, long value, int hexToo) {}
  public static void sysWriteDouble(Address functionAddress, double value, int postDecimalDigits) {}

  // startup/shutdown
  public static void sysExit(Address functionAddress, int value) {}
  public static int sysArg(Address functionAddress, int argno, byte[] buf, int buflen) { return 0; }

  // misc. info on the process -- used in startup/shutdown
  public static int sysGetenv(Address functionAddress, byte[] varName, byte[] buf, int limit) {
    return -2;
  }

  // memory
  public static void sysCopy(Address functionAddress, Address dst, Address src, Extent cnt) {}
  public static void sysFill(Address functionAddress, Address dst, int pattern, Extent cnt) {}
  public static Address sysMalloc(Address functionAddress, int length) { return null; }
  public static void sysFree(Address functionAddress, Address location) {} 
  public static void sysZero(Address functionAddress, Address dst, Extent cnt) {}
  public static void sysZeroPages(Address functionAddress, Address dst, int cnt) {}
  public static void sysSyncCache(Address functionAddress, Address address, int size) {}

  // files
  public static int sysStat(Address functionAddress, byte[] name, int kind) { return 0; }
  public static int sysList(Address functionAddress, byte[] name, byte[] buf, int limit) { 
    return 0; 
  }
  public static int sysOpen(Address functionAddress, byte[] name, int how) { return 0; }
  public static int sysUtime(Address functionAddress, byte[] fileName, int modTimeSec) {
    return 0;
  }
  public static int sysReadByte(Address functionAddress, int fd) { return 0; }
  public static int sysWriteByte(Address functionAddress, int fd, int data) { return 0; }
  public static int sysReadBytes(Address functionAddress, int fd, Address buf, int cnt) { return 0; }
  public static int sysWriteBytes(Address functionAddress, int fd, Address buf, int cnt) { return 0; }
  public static int sysSeek(Address functionAddress, int fd, int offset, int whence) { return 0; }
  public static int sysClose(Address functionAddress, int fd) { return 0; }
  public static int sysDelete(Address functionAddress, byte[] name) { return 0; }
  public static int sysRename(Address functionAddress, byte[] fromName, byte[] toName) { return 0; }
  public static int sysMkDir(Address functionAddress, byte[] name) { return 0; }
  public static int sysBytesAvailable(Address functionAddress, int fd) { return 0; }
  public static int sysIsValidFD(Address functionAddress, int fd) { return 0; }
  public static int sysLength(Address functionAddress, int fd) { return 0; }
  public static int sysSetLength(Address functionAddress, int fd, int len) { return 0; }
  public static int sysSyncFile(Address functionAddress, int fd) { return 0; }
  public static int sysIsTTY(Address functionAddress, int fd) { return 0; }
  public static int sysSetFdCloseOnExec(Address functionAddress, int fd) { return 0; }
  public static int sysAccess(Address functionAddress, byte[] name, int kind) { return 0; }

  // shm* - memory mapping
  public static int sysShmget(Address functionAddress, int key, int size, int flags) { return 0; }
  public static int sysShmctl(Address functionAddress, int shmid, int command) { return 0; }
  public static Address sysShmat(Address functionAddress, int shmid, Address addr, int flags) { 
    return null; 
  }
  public static int sysShmdt(Address functionAddress, Address addr) { return 0; }

  // mmap - memory mapping
  public static Address sysMMap(Address functionAddress, Address start, Extent length, int protection,
                                   int flags, int fd, Offset offset) { 
    return null; 
  }
  public static Address sysMMapErrno(Address functionAddress, Address start, Extent length, int protection,
                                        int flags, int fd, Offset offset) { 
    return null; 
  }
  public static int sysMUnmap(Address functionAddress, Address start, Extent length) {
    return 0;
  }
  public static int sysMProtect(Address functionAddress, Address start, Extent length, int prot) {
    return 0;
  }
  public static int sysMSync(Address functionAddress, Address start, Extent length, int flags) {
    return 0;
  }
  public static int sysMAdvise(Address functionAddress, Address start, Extent length, int advice) {
    return 0;
  }
  public static int sysGetPageSize(Address functionAddress) { return 0; }

  // threads
  public static int sysNumProcessors(Address functionAddress) { return 0; }
  /**
   * Create a virtual processor (aka "unix kernel thread", "pthread").
   * @param jtoc  register values to use for thread startup
   * @param pr
   * @param ip
   * @param fp
   * @return virtual processor's o/s handle
   */
  public static int sysVirtualProcessorCreate(Address functionAddress, Address jtoc, 
                                              Address pr, 
                                              Address ip,
                                              Address fp) { 
    return 0;
  }
  /**
   * Bind execution of current virtual processor to specified physical cpu.
   * @param cpuid  physical cpu id (0, 1, 2, ...)
   */
  public static void sysVirtualProcessorBind(Address functionAddress, int cpuid) {}
  public static void sysVirtualProcessorYield(Address functionAddress) {}
  /**
   * Start interrupt generator for thread timeslicing.
   * The interrupt will be delivered to whatever virtual processor happens 
   * to be running when the timer expires.
   */
  public static void sysVirtualProcessorEnableTimeSlicing(Address functionAddress, int timeSlice) {}
  public static int sysPthreadSelf(Address functionAddress) { return 0; }
  public static void sysPthreadSetupSignalHandling(Address functionAddress) {}
  public static int sysPthreadSignal(Address functionAddress, int pthread) { return 0; }
  public static void sysPthreadExit(Address functionAddress) {}
  public static int sysPthreadJoin(Address functionAddress, int pthread) { return 0; }
  public static int sysStashVmProcessorInPthread(Address functionAddress, VM_Processor vmProcessor) { return 0; }

  // arithmetic 
  public static long sysLongDivide(Address functionAddress, long x, long y) { return 0; }
  public static long sysLongRemainder(Address functionAddress, long x, long y) { return 0; }
  public static float sysLongToFloat(Address functionAddress, long x) { return 0f; }
  public static double sysLongToDouble(Address functionAddress, long x) { return 0; }
  public static int sysFloatToInt(Address functionAddress, float x) { return 0; }
  public static int sysDoubleToInt(Address functionAddress, double x) { return 0; }
  public static long sysFloatToLong(Address functionAddress, float x) { return 0; }
  public static long sysDoubleToLong(Address functionAddress, double x) { return 0; }
  //-#if RVM_FOR_POWERPC
  public static double sysDoubleRemainder(Address functionAddress, double x, double y) { return 0; }
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
  public static float sysPrimitiveParseFloat(Address functionAddress, byte[] buf) { return 0; }

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
  public static int sysPrimitiveParseInt(Address functionAddress, byte[] buf) { return 0; }

  /** Parse memory sizes passed as command-line arguments.
   */
  public static long sysParseMemorySize(Address functionAddress, byte[] sizeName, byte[] sizeFlag,
                                        byte[] defaultFactor, int roundTo,
                                        byte[] argToken, byte[] subArg) {
    return -1;
  }

  // time
  public static long sysGetTimeOfDay(Address functionAddress) { return 0; }
  public static void sysNanosleep(Address functionAddress, long howLongNanos) {}

  // shared libraries
  public static Address sysDlopen(Address functionAddress, byte[] libname) { return null; }
  public static void sysDlclose(Address functionAddress) {}
  public static Address sysDlsym(Address functionAddress, Address libHandler, byte[] symbolName) { return null; }
  public static void sysSlibclean(Address functionAddress) {}

  //-#if RVM_WITH_UNUSED_SYSCALLS
  // network
  public static int sysNetLocalHostName(Address functionAddress, Address buf, int limit) {
    return 0;
  }
  public static int sysNetRemoteHostName(Address functionAddress, int internetAddress, 
                                         Address buf,
                                         int limit) {
    return 0;
  }
  public static int sysNetHostAddresses(Address functionAddress, Address hostname, Address buf, 
                                        int limit) {
    return 0;
  }
  //-#endif

  public static int sysNetSocketCreate(Address functionAddress, int isStream) { return 0; }
  public static int sysNetSocketPort(Address functionAddress, int fd) { return 0; }
  public static int sysNetSocketSndBuf(Address functionAddress, int fd) { return 0; }
  public static int sysNetSocketFamily(Address functionAddress, int fd) { return 0; }
  public static int sysNetSocketLocalAddress(Address functionAddress, int fd) { return 0; }
  public static int sysNetSocketBind(Address functionAddress, int fd, int family, int localAddress,
                                     int localPort) { 
    return 0;
  }
  public static int sysNetSocketConnect(Address functionAddress, int fd, int family, int remoteAddress,
                                        int remotePort) {
    return 0;
  }
  public static int sysNetSocketListen(Address functionAddress, int fd, int backlog) { return 0; }
  public static int sysNetSocketAccept(Address functionAddress, int fd, java.net.SocketImpl connectionObject) {
    return 0;
  }
  public static int sysNetSocketLinger(Address functionAddress, int fd, int enable, int timeout) {
    return 0;
  }
  public static int sysNetSocketNoDelay(Address functionAddress, int fd, int enable) {
    return 0;
  }
  public static int sysNetSocketNoBlock(Address functionAddress, int fd, int enable) {
    return 0;
  }
  public static int sysNetSocketClose(Address functionAddress, int fd) {
    return 0;
  }
  public static int sysNetSocketShutdown(Address functionAddress, int fd, int how) {
    return 0;
  }
  public static int sysNetSelect(Address functionAddress, int[] allFds, int rc, int wc, int ec) {
    return 0;
  }

  // process management
  public static void sysWaitPids(Address functionAddress, Address pidArray, Address exitStatusArray,
                                 int numPids) {}

  // system startup pthread sync. primitives
  public static void sysCreateThreadSpecificDataKeys(Address functionAddress) {}
  public static void sysInitializeStartupLocks(Address functionAddress, int howMany) {}
  public static void sysWaitForVirtualProcessorInitialization(Address functionAddress) {} 
  public static void sysWaitForMultithreadingStart(Address functionAddress) {} 

  //-#if RVM_WITH_HPM
  // sysCall entry points to HPM
  public static int sysHPMinit(Address functionAddress) { return 0; }
  public static int sysHPMsetEvent(Address functionAddress, int e1, int e2, int e3, int e4) { return 0; }
  public static int sysHPMsetEventX(Address functionAddress, int e5, int e6, int e7, int e8) {return 0; } 
  public static int sysHPMsetMode(Address functionAddress, int mode) { return 0; }
  public static int sysHPMgetNumberOfCounters(Address functionAddress) { return 0; }
  public static int sysHPMgetNumberOfEvents(Address functionAddress)   { return 0; }
  public static int sysHPMisBigEndian(Address functionAddress)         { return 0; }
  public static int sysHPMtest(Address functionAddress) { return 0; }
  public static int sysHPMsetProgramMyThread(Address functionAddress) { return 0; }
  public static int sysHPMstartMyThread(Address functionAddress) { return 0; }
  public static int sysHPMstopMyThread(Address functionAddress) { return 0; }
  public static int sysHPMresetMyThread(Address functionAddress) { return 0; }
  public static long sysHPMgetCounterMyThread(Address functionAddress, int counter) { return 0; }
  public static int sysHPMsetProgramMyGroup(Address functionAddress) { return 0; }
  public static int sysHPMstartMyGroup(Address functionAddress) { return 0; }
  public static int sysHPMstopMyGroup(Address functionAddress) { return 0; }
  public static int sysHPMresetMyGroup(Address functionAddress) { return 0; }
  public static long sysHPMgetCounterMyGroup(Address functionAddress, int counter) { return 0; }
  public static int sysHPMprintMyGroup(Address functionAddress) { return 0; }
  //-#endif

  //-#if RVM_WITH_GCSPY
  // sysCall entry points to GCSpy
  public static Address gcspyDriverAddStream (Address functionAddress, Address driver, int it) { return null; }
  public static void gcspyDriverEndOutput (Address functionAddress, Address driver) {}
  public static void gcspyDriverInit (Address functionAddress, Address driver, int id, Address serverName, Address driverName,
                               Address title, Address blockInfo, int tileNum,
                               Address unused, int mainSpace) {}
  public static void gcspyDriverInitOutput (Address functionAddress, Address driver) {}
  public static void gcspyDriverResize (Address functionAddress, Address driver, int size) {}
  public static void gcspyDriverSetTileNameRange (Address functionAddress, Address driver, int i, Address start, Address end) {}
  public static void gcspyDriverSetTileName (Address functionAddress, Address driver, int i, Address start, long value) {}
  public static void gcspyDriverSpaceInfo (Address functionAddress, Address driver, Address info) {}
  public static void gcspyDriverStartComm (Address functionAddress, Address driver) {}
  public static void gcspyDriverStream (Address functionAddress, Address driver, int id, int len) {}
  public static void gcspyDriverStreamByteValue (Address functionAddress, Address driver, byte value) {}
  public static void gcspyDriverStreamShortValue (Address functionAddress, Address driver, short value) {}
  public static void gcspyDriverStreamIntValue (Address functionAddress, Address driver, int value) {}
  public static void gcspyDriverSummary (Address functionAddress, Address driver, int id, int len) {}
  public static void gcspyDriverSummaryValue (Address functionAddress, Address driver, int value) {}

  public static void gcspyIntWriteControl (Address functionAddress, Address driver, int id, int tileNum) {}

  public static Address gcspyMainServerAddDriver(Address functionAddress, Address addr) { return null; }
  public static void gcspyMainServerAddEvent (Address functionAddress, Address server, int event, Address name) {}
  public static Address gcspyMainServerInit (Address functionAddress, int port, int len, Address name, int verbose) { return null; }
  public static int gcspyMainServerIsConnected (Address functionAddress, Address server, int event) { return 0; }
  public static Address gcspyMainServerOuterLoop (Address functionAddress) { return null; }; 
  public static void gcspyMainServerSafepoint (Address functionAddress, Address server, int event) {};
  public static void gcspyMainServerSetGeneralInfo (Address functionAddress, Address server, Address info) {};
  public static void gcspyMainServerStartCompensationTimer (Address functionAddress, Address server) {};
  public static void gcspyMainServerStopCompensationTimer (Address functionAddress, Address server) {};

  public static void gcspyStartserver (Address functionAddress, Address server, int wait, Address serverOuterLoop) {};
   
  public static void gcspyStreamInit (Address functionAddress, Address stream, int id, int dataType, Address name,
                               int minValue, int maxValue, int zeroValue, int defaultValue,
                               Address pre, Address post, int presentation, int paintStyle,
                               int maxStreamIndex, int red, int green, int blue) {}

  public static void gcspyFormatSize (Address functionAddress, Address buffer, int size) {}

  public static int gcspySprintf (Address functionAddress, Address str, Address format, Address value) { return 0; }
  //-#endif
}
