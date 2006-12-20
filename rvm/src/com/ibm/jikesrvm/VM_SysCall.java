/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002,2003,2004
 */
//$Id$
package com.ibm.jikesrvm;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Support for lowlevel (ie non-JNI) invocation of C functions with static addresses.
 * 
 * All methods of this class have the following signature:
 * <pre>
 * public static <TYPE> NAME(<args to pass to sysNAME via native calling convention>)
 * </pre>
 * which will call the corresponding method in VM_SysCallMagic with the added
 * function address from the boot image.
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
@Uninterruptible public class VM_SysCall { 

  // lowlevel write to console
  public static void sysWriteChar(char v) {
     VM_SysCallMagic.sysWriteChar(VM_BootRecord.the_boot_record.sysWriteCharIP, v);
  } 
  public static void sysWrite(int value, int hexToo) {
     VM_SysCallMagic.sysWrite(VM_BootRecord.the_boot_record.sysWriteIP, value, hexToo);
  }
  public static void sysWriteLong(long value, int hexToo) {
     VM_SysCallMagic.sysWriteLong(VM_BootRecord.the_boot_record.sysWriteLongIP, value, hexToo);
  }
  public static void sysWriteDouble(double value, int postDecimalDigits) {
     VM_SysCallMagic.sysWriteDouble(VM_BootRecord.the_boot_record.sysWriteDoubleIP, value, postDecimalDigits);
  }

  // startup/shutdown
  public static void sysExit(int value) {
     VM_SysCallMagic.sysExit(VM_BootRecord.the_boot_record.sysExitIP, value);
  }
  public static int sysArg(int argno, byte[] buf, int buflen) {
     return VM_SysCallMagic.sysArg(VM_BootRecord.the_boot_record.sysArgIP, argno, buf, buflen);
  }

  // misc. info on the process -- used in startup/shutdown
  public static int sysGetenv(byte[] varName, byte[] buf, int limit) {
     return VM_SysCallMagic.sysGetenv(VM_BootRecord.the_boot_record.sysGetenvIP, varName, buf, limit);
  }

  // memory
  public static void sysCopy(Address dst, Address src, Extent cnt) {
     VM_SysCallMagic.sysCopy(VM_BootRecord.the_boot_record.sysCopyIP, dst, src, cnt);
  }
  public static void sysFill(Address dst, int pattern, Extent cnt) {
     VM_SysCallMagic.sysFill(VM_BootRecord.the_boot_record.sysFillIP, dst, pattern, cnt);
  }
  public static Address sysMalloc(int length) {
     return VM_SysCallMagic.sysMalloc(VM_BootRecord.the_boot_record.sysMallocIP, length);
  }
  public static void sysFree(Address location) {
     VM_SysCallMagic.sysFree(VM_BootRecord.the_boot_record.sysFreeIP, location);
  } 
  public static void sysZero(Address dst, Extent cnt) {
     VM_SysCallMagic.sysZero(VM_BootRecord.the_boot_record.sysZeroIP, dst, cnt);
  }
  public static void sysZeroPages(Address dst, int cnt) {
     VM_SysCallMagic.sysZeroPages(VM_BootRecord.the_boot_record.sysZeroPagesIP, dst, cnt);
  }
  public static void sysSyncCache(Address address, int size) {
     VM_SysCallMagic.sysSyncCache(VM_BootRecord.the_boot_record.sysSyncCacheIP, address, size);
  }

  // files
  public static int sysStat(byte[] name, int kind) {
     return VM_SysCallMagic.sysStat(VM_BootRecord.the_boot_record.sysStatIP, name, kind);
  }
  public static int sysList(byte[] name, byte[] buf, int limit) {
     return VM_SysCallMagic.sysList(VM_BootRecord.the_boot_record.sysListIP, name, buf, limit);
  }
  public static int sysOpen(byte[] name, int how) {
     return VM_SysCallMagic.sysOpen(VM_BootRecord.the_boot_record.sysOpenIP, name, how);
  }
  public static int sysUtime(byte[] fileName, int modTimeSec) {
     return VM_SysCallMagic.sysUtime(VM_BootRecord.the_boot_record.sysUtimeIP, fileName, modTimeSec);
  }
  public static int sysReadByte(int fd) {
     return VM_SysCallMagic.sysReadByte(VM_BootRecord.the_boot_record.sysReadByteIP, fd);
  }
  public static int sysWriteByte(int fd, int data) {
     return VM_SysCallMagic.sysWriteByte(VM_BootRecord.the_boot_record.sysWriteByteIP, fd, data);
  }
  public static int sysReadBytes(int fd, Address buf, int cnt) {
     return VM_SysCallMagic.sysReadBytes(VM_BootRecord.the_boot_record.sysReadBytesIP, fd, buf, cnt);
  }
  public static int sysWriteBytes(int fd, Address buf, int cnt) {
     return VM_SysCallMagic.sysWriteBytes(VM_BootRecord.the_boot_record.sysWriteBytesIP, fd, buf, cnt);
  }
  public static int sysSeek(int fd, int offset, int whence) {
     return VM_SysCallMagic.sysSeek(VM_BootRecord.the_boot_record.sysSeekIP, fd, offset, whence);
  }
  public static int sysClose(int fd) {
     return VM_SysCallMagic.sysClose(VM_BootRecord.the_boot_record.sysCloseIP, fd);
  }
  public static int sysDelete(byte[] name) {
     return VM_SysCallMagic.sysDelete(VM_BootRecord.the_boot_record.sysDeleteIP, name);
  }
  public static int sysRename(byte[] fromName, byte[] toName) {
     return VM_SysCallMagic.sysRename(VM_BootRecord.the_boot_record.sysRenameIP, fromName, toName);
  }
  public static int sysMkDir(byte[] name) {
     return VM_SysCallMagic.sysMkDir(VM_BootRecord.the_boot_record.sysMkDirIP, name);
  }
  public static int sysBytesAvailable(int fd) {
     return VM_SysCallMagic.sysBytesAvailable(VM_BootRecord.the_boot_record.sysBytesAvailableIP, fd);
  }
  public static int sysIsValidFD(int fd) {
     return VM_SysCallMagic.sysIsValidFD(VM_BootRecord.the_boot_record.sysIsValidFDIP, fd);
  }
  public static int sysLength(int fd) {
     return VM_SysCallMagic.sysLength(VM_BootRecord.the_boot_record.sysLengthIP, fd);
  }
  public static int sysSetLength(int fd, int len) {
     return VM_SysCallMagic.sysSetLength(VM_BootRecord.the_boot_record.sysSetLengthIP, fd, len);
  }
  public static int sysSyncFile(int fd) {
     return VM_SysCallMagic.sysSyncFile(VM_BootRecord.the_boot_record.sysSyncFileIP, fd);
  }
  public static int sysIsTTY(int fd) {
     return VM_SysCallMagic.sysIsTTY(VM_BootRecord.the_boot_record.sysIsTTYIP, fd);
  }
  public static int sysSetFdCloseOnExec(int fd) {
     return VM_SysCallMagic.sysSetFdCloseOnExec(VM_BootRecord.the_boot_record.sysSetFdCloseOnExecIP, fd);
  }
  public static int sysAccess(byte[] name, int kind) {
     return VM_SysCallMagic.sysAccess(VM_BootRecord.the_boot_record.sysAccessIP, name, kind);
  }

  // shm* - memory mapping
  public static int sysShmget(int key, int size, int flags) {
     return VM_SysCallMagic.sysShmget(VM_BootRecord.the_boot_record.sysShmgetIP, key, size, flags);
  }
  public static int sysShmctl(int shmid, int command) {
     return VM_SysCallMagic.sysShmctl(VM_BootRecord.the_boot_record.sysShmctlIP, shmid, command);
  }
  public static Address sysShmat(int shmid, Address addr, int flags) {
     return VM_SysCallMagic.sysShmat(VM_BootRecord.the_boot_record.sysShmatIP, shmid, addr, flags);
  }
  public static int sysShmdt(Address addr) {
     return VM_SysCallMagic.sysShmdt(VM_BootRecord.the_boot_record.sysShmdtIP, addr);
  }

  // mmap - memory mapping
  public static Address sysMMap(Address start, Extent length, int protection,
                                   int flags, int fd, Offset offset) {
     return VM_SysCallMagic.sysMMap(VM_BootRecord.the_boot_record.sysMMapIP, start, length, protection,
                                   flags, fd, offset);
  }
  public static Address sysMMapErrno(Address start, Extent length, int protection,
                                        int flags, int fd, Offset offset) {
     return VM_SysCallMagic.sysMMapErrno(VM_BootRecord.the_boot_record.sysMMapErrnoIP, start, length, protection,
                                        flags, fd, offset);
  }
  public static int sysMUnmap(Address start, Extent length) {
     return VM_SysCallMagic.sysMUnmap(VM_BootRecord.the_boot_record.sysMUnmapIP, start, length);
  }
  public static int sysMProtect(Address start, Extent length, int prot) {
     return VM_SysCallMagic.sysMProtect(VM_BootRecord.the_boot_record.sysMProtectIP, start, length, prot);
  }
  public static int sysMSync(Address start, Extent length, int flags) {
     return VM_SysCallMagic.sysMSync(VM_BootRecord.the_boot_record.sysMSyncIP, start, length, flags);
  }
  public static int sysMAdvise(Address start, Extent length, int advice) {
     return VM_SysCallMagic.sysMAdvise(VM_BootRecord.the_boot_record.sysMAdviseIP, start, length, advice);
  }
  public static int sysGetPageSize() {
     return VM_SysCallMagic.sysGetPageSize(VM_BootRecord.the_boot_record.sysGetPageSizeIP);
  }

  // threads
  public static int sysNumProcessors() {
     return VM_SysCallMagic.sysNumProcessors(VM_BootRecord.the_boot_record.sysNumProcessorsIP);
  }
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
     return VM_SysCallMagic.sysVirtualProcessorCreate(VM_BootRecord.the_boot_record.sysVirtualProcessorCreateIP, jtoc, 
                                              pr, 
                                              ip,
                                              fp);
  }
  /**
   * Bind execution of current virtual processor to specified physical cpu.
   * @param cpuid  physical cpu id (0, 1, 2, ...)
   */
  public static void sysVirtualProcessorBind(int cpuid) {
     VM_SysCallMagic.sysVirtualProcessorBind(VM_BootRecord.the_boot_record.sysVirtualProcessorBindIP, cpuid);
  }
  public static void sysVirtualProcessorYield() {
     VM_SysCallMagic.sysVirtualProcessorYield(VM_BootRecord.the_boot_record.sysVirtualProcessorYieldIP);
  }
  /**
   * Start interrupt generator for thread timeslicing.
   * The interrupt will be delivered to whatever virtual processor happens 
   * to be running when the timer expires.
   */
  public static void sysVirtualProcessorEnableTimeSlicing(int timeSlice) {
     VM_SysCallMagic.sysVirtualProcessorEnableTimeSlicing(VM_BootRecord.the_boot_record.sysVirtualProcessorEnableTimeSlicingIP, timeSlice);
  }
  public static int sysPthreadSelf() {
     return VM_SysCallMagic.sysPthreadSelf(VM_BootRecord.the_boot_record.sysPthreadSelfIP);
  }
  public static void sysPthreadSetupSignalHandling() {
     VM_SysCallMagic.sysPthreadSetupSignalHandling(VM_BootRecord.the_boot_record.sysPthreadSetupSignalHandlingIP);
  }
  public static int sysPthreadSignal(int pthread) {
     return VM_SysCallMagic.sysPthreadSignal(VM_BootRecord.the_boot_record.sysPthreadSignalIP, pthread);
  }
  public static void sysPthreadExit() {
     VM_SysCallMagic.sysPthreadExit(VM_BootRecord.the_boot_record.sysPthreadExitIP);
  }
  public static int sysPthreadJoin(int pthread) {
     return VM_SysCallMagic.sysPthreadJoin(VM_BootRecord.the_boot_record.sysPthreadJoinIP, pthread);
  }
  public static int sysStashVmProcessorInPthread(VM_Processor vmProcessor) {
     return VM_SysCallMagic.sysStashVmProcessorInPthread(VM_BootRecord.the_boot_record.sysStashVmProcessorInPthreadIP, vmProcessor);
  }

  // arithmetic 
  public static long sysLongDivide(long x, long y) {
     return VM_SysCallMagic.sysLongDivide(VM_BootRecord.the_boot_record.sysLongDivideIP, x, y);
  }
  public static long sysLongRemainder(long x, long y) {
     return VM_SysCallMagic.sysLongRemainder(VM_BootRecord.the_boot_record.sysLongRemainderIP, x, y);
  }
  public static float sysLongToFloat(long x) {
     return VM_SysCallMagic.sysLongToFloat(VM_BootRecord.the_boot_record.sysLongToFloatIP, x);
  }
  public static double sysLongToDouble(long x) {
     return VM_SysCallMagic.sysLongToDouble(VM_BootRecord.the_boot_record.sysLongToDoubleIP, x);
  }
  public static int sysFloatToInt(float x) {
     return VM_SysCallMagic.sysFloatToInt(VM_BootRecord.the_boot_record.sysFloatToIntIP, x);
  }
  public static int sysDoubleToInt(double x) {
     return VM_SysCallMagic.sysDoubleToInt(VM_BootRecord.the_boot_record.sysDoubleToIntIP, x);
  }
  public static long sysFloatToLong(float x) {
     return VM_SysCallMagic.sysFloatToLong(VM_BootRecord.the_boot_record.sysFloatToLongIP, x);
  }
  public static long sysDoubleToLong(double x) {
     return VM_SysCallMagic.sysDoubleToLong(VM_BootRecord.the_boot_record.sysDoubleToLongIP, x);
  }
  public static double sysDoubleRemainder(double x, double y) {
     if (VM.BuildForPowerPC)
       return VM_SysCallMagic.sysDoubleRemainder(VM_BootRecord.the_boot_record.sysDoubleRemainderIP, x, y);
     else if (VM.VerifyAssertions)
       VM._assert(VM.NOT_REACHED);
     return 0;
  }

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
  public static float sysPrimitiveParseFloat(byte[] buf) {
     return VM_SysCallMagic.sysPrimitiveParseFloat(VM_BootRecord.the_boot_record.sysPrimitiveParseFloatIP, buf);
  }

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
  public static int sysPrimitiveParseInt(byte[] buf) {
     return VM_SysCallMagic.sysPrimitiveParseInt(VM_BootRecord.the_boot_record.sysPrimitiveParseIntIP, buf);
  }

  /** Parse memory sizes passed as command-line arguments.
   */
  public static long sysParseMemorySize(byte[] sizeName, byte[] sizeFlag,
                                        byte[] defaultFactor, int roundTo,
                                        byte[] argToken, byte[] subArg) {
     return VM_SysCallMagic.sysParseMemorySize(VM_BootRecord.the_boot_record.sysParseMemorySizeIP, sizeName, sizeFlag,
                                        defaultFactor, roundTo,
                                        argToken, subArg);
  }

  // time
  public static long sysGetTimeOfDay() {
     return VM_SysCallMagic.sysGetTimeOfDay(VM_BootRecord.the_boot_record.sysGetTimeOfDayIP);
  }
  public static void sysNanosleep(long howLongNanos) {
     VM_SysCallMagic.sysNanosleep(VM_BootRecord.the_boot_record.sysNanosleepIP, howLongNanos);
  }

  // shared libraries
  public static Address sysDlopen(byte[] libname) {
     return VM_SysCallMagic.sysDlopen(VM_BootRecord.the_boot_record.sysDlopenIP, libname);
  }
  public static void sysDlclose() {
     VM_SysCallMagic.sysDlclose(VM_BootRecord.the_boot_record.sysDlcloseIP);
  }
  public static Address sysDlsym(Address libHandler, byte[] symbolName) {
     return VM_SysCallMagic.sysDlsym(VM_BootRecord.the_boot_record.sysDlsymIP, libHandler, symbolName);
  }
  public static void sysSlibclean() {
     VM_SysCallMagic.sysSlibclean(VM_BootRecord.the_boot_record.sysSlibcleanIP);
  }

  // network
  public static int sysNetSocketCreate(int isStream) {
     return VM_SysCallMagic.sysNetSocketCreate(VM_BootRecord.the_boot_record.sysNetSocketCreateIP, isStream);
  }
  public static int sysNetSocketPort(int fd) {
     return VM_SysCallMagic.sysNetSocketPort(VM_BootRecord.the_boot_record.sysNetSocketPortIP, fd);
  }
  public static int sysNetSocketSndBuf(int fd) {
     return VM_SysCallMagic.sysNetSocketSndBuf(VM_BootRecord.the_boot_record.sysNetSocketSndBufIP, fd);
  }
  public static int sysNetSocketFamily(int fd) {
     return VM_SysCallMagic.sysNetSocketFamily(VM_BootRecord.the_boot_record.sysNetSocketFamilyIP, fd);
  }
  public static int sysNetSocketLocalAddress(int fd) {
     return VM_SysCallMagic.sysNetSocketLocalAddress(VM_BootRecord.the_boot_record.sysNetSocketLocalAddressIP, fd);
  }
  public static int sysNetSocketBind(int fd, int family, int localAddress,
                                     int localPort) {
     return VM_SysCallMagic.sysNetSocketBind(VM_BootRecord.the_boot_record.sysNetSocketBindIP, fd, family, localAddress,
                                     localPort);
  }
  public static int sysNetSocketConnect(int fd, int family, int remoteAddress,
                                        int remotePort) {
     return VM_SysCallMagic.sysNetSocketConnect(VM_BootRecord.the_boot_record.sysNetSocketConnectIP, fd, family, remoteAddress,
                                        remotePort);
  }
  public static int sysNetSocketListen(int fd, int backlog) {
     return VM_SysCallMagic.sysNetSocketListen(VM_BootRecord.the_boot_record.sysNetSocketListenIP, fd, backlog);
  }
  public static int sysNetSocketAccept(int fd, java.net.SocketImpl connectionObject) {
     return VM_SysCallMagic.sysNetSocketAccept(VM_BootRecord.the_boot_record.sysNetSocketAcceptIP, fd, connectionObject);
  }
  public static int sysNetSocketLinger(int fd, int enable, int timeout) {
     return VM_SysCallMagic.sysNetSocketLinger(VM_BootRecord.the_boot_record.sysNetSocketLingerIP, fd, enable, timeout);
  }
  public static int sysNetSocketNoDelay(int fd, int enable) {
     return VM_SysCallMagic.sysNetSocketNoDelay(VM_BootRecord.the_boot_record.sysNetSocketNoDelayIP, fd, enable);
  }
  public static int sysNetSocketNoBlock(int fd, int enable) {
     return VM_SysCallMagic.sysNetSocketNoBlock(VM_BootRecord.the_boot_record.sysNetSocketNoBlockIP, fd, enable);
  }
  public static int sysNetSocketClose(int fd) {
     return VM_SysCallMagic.sysNetSocketClose(VM_BootRecord.the_boot_record.sysNetSocketCloseIP, fd);
  }
  public static int sysNetSocketShutdown(int fd, int how) {
     return VM_SysCallMagic.sysNetSocketShutdown(VM_BootRecord.the_boot_record.sysNetSocketShutdownIP, fd, how);
  }
  public static int sysNetSelect(int[] allFds, int rc, int wc, int ec) {
     return VM_SysCallMagic.sysNetSelect(VM_BootRecord.the_boot_record.sysNetSelectIP, allFds, rc, wc, ec);
  }

  // process management
  public static void sysWaitPids(Address pidArray, Address exitStatusArray,
                                 int numPids) {
     VM_SysCallMagic.sysWaitPids(VM_BootRecord.the_boot_record.sysWaitPidsIP, pidArray, exitStatusArray,
                                 numPids);
  }

  // system startup pthread sync. primitives
  public static void sysCreateThreadSpecificDataKeys() {
     VM_SysCallMagic.sysCreateThreadSpecificDataKeys(VM_BootRecord.the_boot_record.sysCreateThreadSpecificDataKeysIP);
  }
  public static void sysInitializeStartupLocks(int howMany) {
     VM_SysCallMagic.sysInitializeStartupLocks(VM_BootRecord.the_boot_record.sysInitializeStartupLocksIP, howMany);
  }
  public static void sysWaitForVirtualProcessorInitialization() {
     VM_SysCallMagic.sysWaitForVirtualProcessorInitialization(VM_BootRecord.the_boot_record.sysWaitForVirtualProcessorInitializationIP);
  } 
  public static void sysWaitForMultithreadingStart() {
     VM_SysCallMagic.sysWaitForMultithreadingStart(VM_BootRecord.the_boot_record.sysWaitForMultithreadingStartIP);
  } 

  public static Address gcspyDriverAddStream(Address driver, int id) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    return VM_SysCallMagic.gcspyDriverAddStream(VM_BootRecord.the_boot_record.gcspyDriverAddStreamIP,
        driver, id);
  }

  public static void gcspyDriverEndOutput (Address driver) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverEndOutput(VM_BootRecord.the_boot_record.gcspyDriverEndOutputIP,
        driver);
  }

  public static void gcspyDriverInit (
      Address driver, int id, Address serverName, Address driverName, 
      Address title, Address blockInfo, int tileNum, Address unused, int mainSpace) {
   if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
   VM_SysCallMagic.gcspyDriverInit(VM_BootRecord.the_boot_record.gcspyDriverInitIP,
        driver, id, serverName, driverName, title, blockInfo, tileNum, unused, mainSpace);
  }

  public static void gcspyDriverInitOutput (Address driver) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverInitOutput(VM_BootRecord.the_boot_record.gcspyDriverInitOutputIP,
        driver);
  }

  public static void gcspyDriverResize (Address driver, int size) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverResize(VM_BootRecord.the_boot_record.gcspyDriverResizeIP,
        driver, size);
  }

  public static void gcspyDriverSetTileNameRange (Address driver, int i, Address start, Address end) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverSetTileNameRange(VM_BootRecord.the_boot_record.gcspyDriverSetTileNameRangeIP,
        driver, i, start, end);
  }

  public static void gcspyDriverSetTileName(Address driver, int i, Address start, long value) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverSetTileName(VM_BootRecord.the_boot_record.gcspyDriverSetTileNameIP,
        driver, i, start, value);
  }

  public static void gcspyDriverSpaceInfo (Address driver, Address info) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverSpaceInfo(VM_BootRecord.the_boot_record.gcspyDriverSpaceInfoIP,
        driver, info);
  }
  public static void gcspyDriverStartComm (Address driver) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverStartComm(VM_BootRecord.the_boot_record.gcspyDriverStartCommIP,
        driver);
  }

  public static void gcspyDriverStream (Address driver, int id, int len) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverStream(VM_BootRecord.the_boot_record.gcspyDriverStreamIP,
        driver, id, len);
  }

  public static void gcspyDriverStreamByteValue (Address driver, byte value) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverStreamByteValue(VM_BootRecord.the_boot_record.gcspyDriverStreamByteValueIP,
        driver, value);
  }

  public static void gcspyDriverStreamShortValue (Address driver, short value) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverStreamShortValue(VM_BootRecord.the_boot_record.gcspyDriverStreamShortValueIP,
        driver, value);
  }

  public static void gcspyDriverStreamIntValue (Address driver, int value) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverStreamIntValue(VM_BootRecord.the_boot_record.gcspyDriverStreamIntValueIP,
        driver, value);
  }

  public static void gcspyDriverSummary (Address driver, int id, int len) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverSummary(VM_BootRecord.the_boot_record.gcspyDriverSummaryIP,
        driver, id, len);
  }

  public static void gcspyDriverSummaryValue (Address driver, int value) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyDriverSummaryValue(VM_BootRecord.the_boot_record.gcspyDriverSummaryValueIP,
        driver, value);
  }

  public static void gcspyIntWriteControl (Address driver, int id, int tileNum) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyIntWriteControl(VM_BootRecord.the_boot_record.gcspyIntWriteControlIP,
        driver, id, tileNum);
  }

  public static Address gcspyMainServerAddDriver(Address addr) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    return VM_SysCallMagic.gcspyMainServerAddDriver(VM_BootRecord.the_boot_record.gcspyMainServerAddDriverIP,
        addr);
  }

  public static void gcspyMainServerAddEvent (Address server, int event, Address name) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyMainServerAddEvent(VM_BootRecord.the_boot_record.gcspyMainServerAddEventIP,
        server, event, name);
  }

  public static Address gcspyMainServerInit (int port, int len, Address name, int verbose) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    return VM_SysCallMagic.gcspyMainServerInit(VM_BootRecord.the_boot_record.gcspyMainServerInitIP,
        port, len, name, verbose);
  }

  public static int gcspyMainServerIsConnected (Address server, int event) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    return VM_SysCallMagic.gcspyMainServerIsConnected(VM_BootRecord.the_boot_record.gcspyMainServerIsConnectedIP, 
        server, event);
  }

  public static Address gcspyMainServerOuterLoop () {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    return VM_SysCallMagic.gcspyMainServerOuterLoop(VM_BootRecord.the_boot_record.gcspyMainServerOuterLoopIP);
  }

  public static void gcspyMainServerSafepoint (Address server, int event) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyMainServerSafepoint(VM_BootRecord.the_boot_record.gcspyMainServerSafepointIP,
        server, event);
  }

  public static void gcspyMainServerSetGeneralInfo (Address server, Address info) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyMainServerSetGeneralInfo(VM_BootRecord.the_boot_record.gcspyMainServerSetGeneralInfoIP, 
        server, info);
  }

  public static void gcspyMainServerStartCompensationTimer (Address server) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyMainServerStartCompensationTimer( VM_BootRecord.the_boot_record.gcspyMainServerStartCompensationTimerIP, 
        server);
  }

  public static void gcspyMainServerStopCompensationTimer (Address server) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyMainServerStopCompensationTimer( VM_BootRecord.the_boot_record.gcspyMainServerStopCompensationTimerIP, 
	server);
  }

  public static void gcspyStartserver (Address server, int wait, Address serverOuterLoop) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyStartserver (VM_BootRecord.the_boot_record.gcspyStartserverIP, 
        server, wait, serverOuterLoop);
  }
   
  public static void gcspyStreamInit (Address stream, int id, int dataType, Address name,
                               int minValue, int maxValue, int zeroValue, int defaultValue,
                               Address pre, Address post, int presentation, int paintStyle,
                               int maxStreamIndex, int red, int green, int blue) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
     VM_SysCallMagic.gcspyStreamInit (VM_BootRecord.the_boot_record.gcspyStreamInitIP,
         stream, id, dataType, name, minValue, maxValue, zeroValue, defaultValue,
         pre, post, presentation, paintStyle, maxStreamIndex, red, green, blue);
  }

  public static void gcspyFormatSize (Address buffer, int size) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    VM_SysCallMagic.gcspyFormatSize(VM_BootRecord.the_boot_record.gcspyFormatSizeIP, 
        buffer, size);
  }

  public static int gcspySprintf (Address str, Address format, Address value) {
    if (VM.VerifyAssertions) VM._assert(VM_Configuration.BuildWithGCSpy);
    return VM_SysCallMagic.gcspySprintf(VM_BootRecord.the_boot_record.gcspySprintfIP, 
        str, format, value);
  }
}
