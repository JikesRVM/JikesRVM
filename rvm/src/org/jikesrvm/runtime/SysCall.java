/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.jikesrvm.apt.annotations.GenerateImplementation;
import org.jikesrvm.apt.annotations.SysCallTemplate;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Word;
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
 * to a method of SysCall is uninterruptible.
 * <p>
 * NOTE: There must be a matching field NAMEIP in BootRecord.java
 *       for each method declared here.
 */
@Uninterruptible
@GenerateImplementation("org.jikesrvm.runtime.SysCallImpl")
public abstract class SysCall {

  /**
   * Actual implementation of the SysCall class
   */
  public static final SysCall sysCall;

  static {
    try {
      sysCall = (SysCall)Class.forName("org.jikesrvm.runtime.SysCallImpl").newInstance();
    } catch (final Exception e) {
      throw new Error(e);
    }
  }

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
  public abstract Address sysCalloc(int length);

  @SysCallTemplate
  public abstract void sysFree(Address location);

  @SysCallTemplate
  public abstract void sysZero(Address dst, Extent cnt);

  @SysCallTemplate
  public abstract void sysZeroPages(Address dst, int cnt);

  @SysCallTemplate
  public abstract void sysSyncCache(Address address, int size);

  /*
   * Interface to performance counters
   */
  @SysCallTemplate
  public abstract void sysPerfCtrInit(int metric);
  @SysCallTemplate
  public abstract void sysPerfCtrRead(byte[] name);
  @SysCallTemplate
  public abstract long sysPerfCtrReadMetric();
  @SysCallTemplate
  public abstract long sysPerfCtrReadCycles();

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
   * Create a native thread (aka "unix kernel thread", "pthread").
   * @param tr
   * @param ip
   * @param fp
   * @return native thread's o/s handle
   */
  @SysCallTemplate
  public abstract Word sysThreadCreate(Address tr, Address ip, Address fp);

  /**
   * Tells you if the current system supportes sysNativeThreadBind().
   * @return 1 if it's supported, 0 if it isn't
   */
  @SysCallTemplate
  public abstract int sysThreadBindSupported();

  @SysCallTemplate
  public abstract void sysThreadBind(int cpuId);

  @SysCallTemplate
  public abstract void sysThreadYield();

  @SysCallTemplate
  public abstract Word sysGetThreadId();

  @SysCallTemplate
  public abstract void sysSetupHardwareTrapHandler();

  // This implies that the RVMThread is somehow pinned, or else the
  // pthread key value gets moved.  (hence RVMThread is @NonMoving)
  @SysCallTemplate
  public abstract int sysStashVMThread(RVMThread vmThread);
  @SysCallTemplate
  public abstract void sysThreadTerminate();
  /**
   * Allocate the space for a pthread_mutex (using malloc) and initialize
   * it using pthread_mutex_init with the recursive mutex options.  Note:
   * it is perfectly OK for the C/C++ code that implements this syscall to
   * use some other locking mechanism (for example, on systems that don't
   * have recursive mutexes you could imagine the recursive feature to be
   * emulated).
   */
  @SysCallTemplate
  public abstract Word sysMonitorCreate();
  /**
   * Destroy the monitor pointed to by the argument and free its memory
   * by calling free.
   */
  @SysCallTemplate
  public abstract void sysMonitorDestroy(Word monitor);
  @SysCallTemplate
  public abstract void sysMonitorEnter(Word monitor);
  @SysCallTemplate
  public abstract void sysMonitorExit(Word monitor);
  @SysCallTemplate
  public abstract void sysMonitorTimedWaitAbsolute(Word monitor, long whenWakeupNanos);
  @SysCallTemplate
  public abstract void sysMonitorWait(Word monitor);
  @SysCallTemplate
  public abstract void sysMonitorBroadcast(Word monitor);
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
  public abstract long sysCurrentTimeMillis();

  @SysCallTemplate
  public abstract long sysNanoTime();

  @SysCallTemplate
  public abstract void sysNanoSleep(long howLongNanos);

  // shared libraries
  @SysCallTemplate
  public abstract Address sysDlopen(byte[] libname);

  @SysCallTemplate
  public abstract Address sysDlsym(Address libHandler, byte[] symbolName);

  // system startup pthread sync. primitives
  @SysCallTemplate
  public abstract void sysCreateThreadSpecificDataKeys();

  // system calls for alignment checking
  @SysCallTemplate
  public abstract void sysEnableAlignmentChecking();

  @SysCallTemplate
  public abstract void sysDisableAlignmentChecking();

  @SysCallTemplate
  public abstract void sysReportAlignmentChecking();

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

