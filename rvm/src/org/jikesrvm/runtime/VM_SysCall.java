/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002,2003,2004
 */
package org.jikesrvm.runtime;

import org.jikesrvm.apt.annotations.GenerateImplementation;
import org.jikesrvm.apt.annotations.SysCallTemplate;
import org.jikesrvm.scheduler.VM_Processor;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;

/**
 * Support for lowlevel (ie non-JNI) invocation of C functions with
 * static addresses.
 *
 * All methods of this class have the following signature:
 * <pre>
 * public abstract <TYPE> NAME(<args to pass to sysNAME via native calling convention>)
 * </pre>
 * which will call the corresponding method in system call trampoline
 * with the added function address from the boot image.
 * <p>
 * NOTE: From the standpoint of the rest of the VM, an invocation
 * to a method of VM_SysCall is uninterruptible.
 * <p>
 * NOTE: There must be a matching field NAMEIP in VM_BootRecord.java
 *       for each method declared here.
 */
@Uninterruptible
@GenerateImplementation(generatedClass = "org.jikesrvm.runtime.VM_SysCallImpl")
public abstract class VM_SysCall {

  public static final VM_SysCall sysCall = VM_SysCallUtil.getImplementation(VM_SysCall.class);

  // lowlevel write to console
  @SysCallTemplate
  public abstract void sysConsoleWriteChar(char v);

  @SysCallTemplate
  public abstract void sysConsoleWriteInteger(int value, int hexToo);

  @SysCallTemplate
  public abstract void sysConsoleWriteLong(long value, int hexToo);

  @SysCallTemplate
  public abstract void sysConsoleWriteDouble(double value, int postDecimalDigits);

  // startup/shutdown
  @SysCallTemplate
  public abstract void sysExit(int value);

  @SysCallTemplate
  public abstract int sysArg(int argno, byte[] buf, int buflen);

  // misc. info on the process -- used in startup/shutdown
  @SysCallTemplate
  public abstract int sysGetenv(byte[] varName, byte[] buf, int limit);

  // memory
  @SysCallTemplate
  public abstract void sysCopy(Address dst, Address src, Extent cnt);

  @SysCallTemplate
  public abstract Address sysMalloc(int length);

  @SysCallTemplate
  public abstract void sysFree(Address location);

  @SysCallTemplate
  public abstract void sysZero(Address dst, Extent cnt);

  @SysCallTemplate
  public abstract void sysZeroPages(Address dst, int cnt);

  @SysCallTemplate
  public abstract void sysSyncCache(Address address, int size);

  // files
  @SysCallTemplate
  public abstract int sysStat(byte[] name, int kind);

  @SysCallTemplate
  public abstract int sysReadByte(int fd);

  @SysCallTemplate
  public abstract int sysWriteByte(int fd, int data);

  @SysCallTemplate
  public abstract int sysReadBytes(int fd, Address buf, int cnt);

  @SysCallTemplate
  public abstract int sysWriteBytes(int fd, Address buf, int cnt);

  @SysCallTemplate
  public abstract int sysBytesAvailable(int fd);

  @SysCallTemplate
  public abstract int sysSyncFile(int fd);

  @SysCallTemplate
  public abstract int sysSetFdCloseOnExec(int fd);

  @SysCallTemplate
  public abstract int sysAccess(byte[] name, int kind);

  // mmap - memory mapping
  @SysCallTemplate
  public abstract Address sysMMap(Address start, Extent length, int protection, int flags, int fd, Offset offset);

  @SysCallTemplate
  public abstract Address sysMMapErrno(Address start, Extent length, int protection, int flags, int fd, Offset offset);

  @SysCallTemplate
  public abstract int sysMProtect(Address start, Extent length, int prot);

  @SysCallTemplate
  public abstract int sysGetPageSize();

  // threads
  @SysCallTemplate
  public abstract int sysNumProcessors();

  /**
   * Create a virtual processor (aka "unix kernel thread", "pthread").
   * @param jtoc  register values to use for thread startup
   * @param pr
   * @param ip
   * @param fp
   * @return virtual processor's o/s handle
   */
  @SysCallTemplate
  public abstract int sysVirtualProcessorCreate(Address jtoc, Address pr, Address ip, Address fp);

  /**
   * Bind execution of current virtual processor to specified physical cpu.
   * @param cpuid  physical cpu id (0, 1, 2, ...)
   */
  @SysCallTemplate
  public abstract void sysVirtualProcessorBind(int cpuid);

  @SysCallTemplate
  public abstract void sysVirtualProcessorYield();

  /**
   * Start interrupt generator for thread timeslicing.
   * The interrupt will be delivered to whatever virtual processor happens
   * to be running when the timer expires.
   */
  @SysCallTemplate
  public abstract void sysVirtualProcessorEnableTimeSlicing(int timeSlice);

  @SysCallTemplate
  public abstract int sysPthreadSelf();

  @SysCallTemplate
  public abstract void sysPthreadSetupSignalHandling();

  @SysCallTemplate
  public abstract int sysPthreadSignal(int pthread);

  @SysCallTemplate
  public abstract void sysPthreadExit();

  @SysCallTemplate
  public abstract int sysPthreadJoin(int pthread);

  @SysCallTemplate
  public abstract int sysStashVmProcessorInPthread(VM_Processor vmProcessor);

  // arithmetic
  @SysCallTemplate
  public abstract long sysLongDivide(long x, long y);

  @SysCallTemplate
  public abstract long sysLongRemainder(long x, long y);

  @SysCallTemplate
  public abstract float sysLongToFloat(long x);

  @SysCallTemplate
  public abstract double sysLongToDouble(long x);

  @SysCallTemplate
  public abstract int sysFloatToInt(float x);

  @SysCallTemplate
  public abstract int sysDoubleToInt(double x);

  @SysCallTemplate
  public abstract long sysFloatToLong(float x);

  @SysCallTemplate
  public abstract long sysDoubleToLong(double x);

  @SysCallTemplate
  public abstract double sysDoubleRemainder(double x, double y);

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
  @SysCallTemplate
  public abstract float sysPrimitiveParseFloat(byte[] buf);

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
   */
  @SysCallTemplate
  public abstract int sysPrimitiveParseInt(byte[] buf);

  /** Parse memory sizes passed as command-line arguments.
   */
  @SysCallTemplate
  public abstract long sysParseMemorySize(byte[] sizeName, byte[] sizeFlag, byte[] defaultFactor, int roundTo,
                                          byte[] argToken, byte[] subArg);

  // time
  @SysCallTemplate
  public abstract long sysGetTimeOfDay();

  @SysCallTemplate
  public abstract void sysNanosleep(long howLongNanos);

  // shared libraries
  @SysCallTemplate
  public abstract Address sysDlopen(byte[] libname);

  @SysCallTemplate
  public abstract Address sysDlsym(Address libHandler, byte[] symbolName);

  // network
  @SysCallTemplate
  public abstract int sysNetSocketCreate(int isStream);

  @SysCallTemplate
  public abstract int sysNetSocketPort(int fd);

  @SysCallTemplate
  public abstract int sysNetSocketSndBuf(int fd);

  @SysCallTemplate
  public abstract int sysNetSocketFamily(int fd);

  @SysCallTemplate
  public abstract int sysNetSocketLocalAddress(int fd);

  @SysCallTemplate
  public abstract int sysNetSocketBind(int fd, int family, int localAddress, int localPort);

  @SysCallTemplate
  public abstract int sysNetSocketConnect(int fd, int family, int remoteAddress, int remotePort);

  @SysCallTemplate
  public abstract int sysNetSocketListen(int fd, int backlog);

  @SysCallTemplate
  public abstract int sysNetSocketAccept(int fd, java.net.SocketImpl connectionObject);

  @SysCallTemplate
  public abstract int sysNetSocketLinger(int fd, int enable, int timeout);

  @SysCallTemplate
  public abstract int sysNetSocketNoDelay(int fd, int enable);

  @SysCallTemplate
  public abstract int sysNetSocketNoBlock(int fd, int enable);

  @SysCallTemplate
  public abstract int sysNetSocketClose(int fd);

  @SysCallTemplate
  public abstract int sysNetSocketShutdown(int fd, int how);

  @SysCallTemplate
  public abstract int sysNetSelect(int[] allFds, int rc, int wc, int ec);

  // process management
  @SysCallTemplate
  public abstract void sysWaitPids(Address pidArray, Address exitStatusArray, int numPids);

  // system startup pthread sync. primitives
  @SysCallTemplate
  public abstract void sysCreateThreadSpecificDataKeys();

  @SysCallTemplate
  public abstract void sysInitializeStartupLocks(int howMany);

  @SysCallTemplate
  public abstract void sysWaitForVirtualProcessorInitialization();

  @SysCallTemplate
  public abstract void sysWaitForMultithreadingStart();

  @SysCallTemplate
  public abstract Address gcspyDriverAddStream(Address driver, int id);

  @SysCallTemplate
  public abstract void gcspyDriverEndOutput(Address driver);

  @SysCallTemplate
  public abstract void gcspyDriverInit(Address driver, int id, Address serverName, Address driverName, Address title,
                                       Address blockInfo, int tileNum, Address unused, int mainSpace);

  @SysCallTemplate
  public abstract void gcspyDriverInitOutput(Address driver);

  @SysCallTemplate
  public abstract void gcspyDriverResize(Address driver, int size);

  @SysCallTemplate
  public abstract void gcspyDriverSetTileNameRange(Address driver, int i, Address start, Address end);

  @SysCallTemplate
  public abstract void gcspyDriverSetTileName(Address driver, int i, Address start, long value);

  @SysCallTemplate
  public abstract void gcspyDriverSpaceInfo(Address driver, Address info);

  @SysCallTemplate
  public abstract void gcspyDriverStartComm(Address driver);

  @SysCallTemplate
  public abstract void gcspyDriverStream(Address driver, int id, int len);

  @SysCallTemplate
  public abstract void gcspyDriverStreamByteValue(Address driver, byte value);

  @SysCallTemplate
  public abstract void gcspyDriverStreamShortValue(Address driver, short value);

  @SysCallTemplate
  public abstract void gcspyDriverStreamIntValue(Address driver, int value);

  @SysCallTemplate
  public abstract void gcspyDriverSummary(Address driver, int id, int len);

  @SysCallTemplate
  public abstract void gcspyDriverSummaryValue(Address driver, int value);

  @SysCallTemplate
  public abstract void gcspyIntWriteControl(Address driver, int id, int tileNum);

  @SysCallTemplate
  public abstract Address gcspyMainServerAddDriver(Address addr);

  @SysCallTemplate
  public abstract void gcspyMainServerAddEvent(Address server, int event, Address name);

  @SysCallTemplate
  public abstract Address gcspyMainServerInit(int port, int len, Address name, int verbose);

  @SysCallTemplate
  public abstract int gcspyMainServerIsConnected(Address server, int event);

  @SysCallTemplate
  public abstract Address gcspyMainServerOuterLoop();

  @SysCallTemplate
  public abstract void gcspyMainServerSafepoint(Address server, int event);

  @SysCallTemplate
  public abstract void gcspyMainServerSetGeneralInfo(Address server, Address info);

  @SysCallTemplate
  public abstract void gcspyMainServerStartCompensationTimer(Address server);

  @SysCallTemplate
  public abstract void gcspyMainServerStopCompensationTimer(Address server);

  @SysCallTemplate
  public abstract void gcspyStartserver(Address server, int wait, Address serverOuterLoop);

  @SysCallTemplate
  public abstract void gcspyStreamInit(Address stream, int id, int dataType, Address name, int minValue, int maxValue,
                                       int zeroValue, int defaultValue, Address pre, Address post, int presentation,
                                       int paintStyle, int maxStreamIndex, int red, int green, int blue);

  @SysCallTemplate
  public abstract void gcspyFormatSize(Address buffer, int size);

  @SysCallTemplate
  public abstract int gcspySprintf(Address str, Address format, Address value);
}
