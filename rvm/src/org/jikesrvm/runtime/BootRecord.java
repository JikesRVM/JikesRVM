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

import org.jikesrvm.VM;
import org.jikesrvm.jni.FunctionTable;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;

/**
 * Information required to start the virtual machine and communicate
 * with the outside world.
 *
 * <p> The virtual machine image consists entirely of java objects.
 * The first java-object, the boot record, is the communication area between
 * the host operating system and the virtual machine. It consists of read-only
 * fields containing startup information used by the assembler bootstrap
 * loader, by the virtual machine's initializer methods, and by the virtual
 * machine's operating system call interface methods.
 *
 * <p> See also: BootImageWriter.main(), jvm.c
 *
 * <p>The boot record looks like this
 * (note that fields are layed out "backwards"):
 *
 * <pre>
 *                       lo-mem
 *                  +---------------+
 *                  |   fieldN-1    |
 *                  +---------------+
 *                  |     ...       |
 *                  +---------------+
 *                  |    field1     |
 *                  +---------------+
 *                  |    field0     |
 *                  +---------------+ \
 *                  |  tib pointer  |  |
 *                  +---------------+  | object header
 *                  |  lock word    |  |
 *                  +---------------+ /
 *                       hi-mem
 * </pre>
 *
 * The "spRegister" field of the boot record points to the word immediately
 * preceeding the top of a stack object (ie. it's ready to accept a "push"
 * instruction). The stack object is an array of words that looks like this:
 *
 * <pre>
 *                       lo-mem
 *                  +---------------+ \
 *                  |  tib pointer  |  |
 *                  +---------------+  | array
 *                  |  lock word    |  |   object
 *                  +---------------+  |      header
 *                  |    .length    |  |
 *                  +---------------+ /
 *                  |    &lt;empty&gt;    |
 *                  +---------------+
 *                  |     ...       |
 *                  +---------------+
 *                  |    &lt;empty&gt;    |
 *                  +---------------+
 *    spRegister -&gt;      hi-mem
 * </pre>
 *
 * <P> The "ipRegister" field of the boot record points to the first word
 * of an array of machine instructions comprising
 * the virtual machine's startoff code -- see "VM.boot()".
 *
 * <P> The "tocRegister" field of the boot record points to an array of words
 * containing the static fields and method addresses of the virtual
 * machine image -- see "Statics.slots[]".
 *
 * <P> The remaining fields of the boot record serve as a function linkage area
 * between services residing in the host operating system and services
 * residing in the virtual machine.
 */
public class BootRecord {
  /**
   * The following static field is initialized by the boot image writer.
   * It allows the virtual machine to address the boot record using normal
   * field access instructions (the assembler bootstrap function, on the other
   * hand, simply addresses the boot record as the first object in
   * the boot image).
   */
  @Entrypoint
  public static BootRecord the_boot_record;

  public BootRecord() {
    int len = 2 * (1 + MemoryManager.getMaxHeaps());
    heapRanges = AddressArray.create(len);
    // Indicate end of array with sentinel value
    heapRanges.set(len - 1, Address.fromIntSignExtend(-1));
    heapRanges.set(len - 2, Address.fromIntSignExtend(-1));
  }

  public void showHeapRanges() {
    for (int i = 0; i < heapRanges.length() / 2; i++) {
      VM.sysWrite(i, "  ");
      VM.sysWrite(heapRanges.get(2 * i));
      VM.sysWrite("  ", heapRanges.get(2 * i + 1));
      VM.sysWrite("  ");
    }
  }

  @Uninterruptible
  public void setHeapRange(int id, Address start, Address end) {
    if (VM.VerifyAssertions) VM._assert(id < heapRanges.length() - 2);
    heapRanges.set(2 * id, start);
    heapRanges.set(2 * id + 1, end);
  }

  // The following fields are written when the virtual machine image
  // is generated (see BootImage.java), loaded (see jvm.C),
  // or executed (see VM.java).
  //
  // If you add/remove/change fields here, be sure to change the
  // corresponding code in jvm.c

  /**
   * address at which image is to be loaded into memory
   */
  public Address bootImageDataStart;
  public Address bootImageDataEnd;
  public Address bootImageCodeStart;
  public Address bootImageCodeEnd;
  public Address bootImageRMapStart;
  public Address bootImageRMapEnd;

  /**
   * initial size of heap
   */
  public Extent initialHeapSize;

  /**
   * maximum size of heap
   */
  public Extent maximumHeapSize;

  /** size of a virtual memory page in bytes */
  public Extent bytesInPage;

  @Untraced // because bootloader code must be able to access it
  public AddressArray heapRanges; // [start1, end1, ..., start_k, end_k, -1, -1]
  // C-style termination with sentinel values
  /**
   * Verbosity level for booting
   * set by -X:verboseBoot=
   */
  public int verboseBoot = 0;

  /**
   * Print messages when delivering hardware exceptions to threads?
   * Set by -X:verboseSignalHandling
   */
  public int verboseSignalHandling = 0;

  // RVM startoff
  //
  public Address spRegister;   // value to place into SP register
  public Address ipRegister;   // value to place into IP register
  public Address tocRegister;  // value to place into JTOC register

  /**
   * flag to indicate RVM has completed booting and ready to run Java programs
   * added by Ton Ngo for JNI support
   */
  int bootCompleted;       // use for start up by JNI_CreateJavaVM

  /**
   * address of JavaVM, used by JNI_OnLoad and JNIEnv.GetJavaVM,
   * defined in jvm.c
   */
  public Address sysJavaVM;

  /**
   * Reference to JNI function table
   */
  @Untraced // because bootloader code must be able to access it
  public FunctionTable JNIFunctions;

  // Additional RVM entrypoints
  //
  /**
   * method id for inserting stackframes at site of hardware traps
   */
  int hardwareTrapMethodId;
  /**
   * jtoc offset of RuntimeEntrypoints.deliverHardwareException()
   */
  Offset deliverHardwareExceptionOffset;
  /**
   * jtoc offset of RVMThread.dumpStackAndDie(I)
   */
  public Offset dumpStackAndDieOffset;
  /**
   * jtoc offset of RVMThread.bootThread
   */
  public Offset bootThreadOffset;
  /**
   * jtoc offset of RVMThread.debugRequested
   */
  Offset debugRequestedOffset;
  /**
   * an external signal has been sent e.g. kill -signalnumber processid
   */
  @Entrypoint
  int externalSignalFlag;

  // Host operating system entrypoints - see "sys.cpp"
  //

  // lowlevel write to console
  public Address sysConsoleWriteCharIP;
  public Address sysConsoleWriteIntegerIP;
  public Address sysConsoleWriteLongIP;
  public Address sysConsoleWriteDoubleIP;
  public Address sysConsoleFlushErrorAndTraceIP;

  // startup/shutdown
  public Address sysExitIP;
  public Address sysArgIP;

  // misc. info on the process -- used in startup/shutdown
  public Address sysGetenvIP;

  // memory
  public Address sysCopyIP;
  public Address sysMemmoveIP;
  public Address sysMallocIP;
  public Address sysCallocIP;
  public Address sysFreeIP;
  public Address sysZeroNTIP;
  public Address sysZeroIP;
  public Address sysZeroPagesIP;
  public Address sysSyncCacheIP;

  // files
  public Address sysReadByteIP;
  public Address sysWriteByteIP;
  public Address sysReadBytesIP;
  public Address sysWriteBytesIP;

  // mmap - memory mapping
  public Address sysMMapIP;
  public Address sysMMapErrnoIP;
  public Address sysMProtectIP;

  // threads
  public Address sysNumProcessorsIP;
  public Address sysThreadBindSupportedIP;
  public Address sysThreadBindIP;
  public Address sysThreadCreateIP;
  public Address sysThreadYieldIP;
  public Address sysGetThreadIdIP;
  public Address sysStashVMThreadIP;
  public Address sysThreadTerminateIP;
  public Address sysGetThreadPriorityHandleIP;
  public Address sysGetThreadPriorityIP;
  public Address sysSetThreadPriorityIP;

  // monitors
  public Address sysMonitorCreateIP;
  public Address sysMonitorDestroyIP;
  public Address sysMonitorEnterIP;
  public Address sysMonitorExitIP;
  public Address sysMonitorTimedWaitAbsoluteIP;
  public Address sysMonitorWaitIP;
  public Address sysMonitorBroadcastIP;

  // arithmetic
  @Entrypoint
  public Address sysLongDivideIP;
  @Entrypoint
  public Address sysLongRemainderIP;
  @Entrypoint
  public Address sysLongToFloatIP;
  @Entrypoint
  public Address sysLongToDoubleIP;
  @Entrypoint
  public Address sysFloatToIntIP;
  @Entrypoint
  public Address sysDoubleToIntIP;
  @Entrypoint
  public Address sysFloatToLongIP;
  @Entrypoint
  public Address sysDoubleToLongIP;
  @Entrypoint
  public Address sysDoubleRemainderIP;
  public Address sysPrimitiveParseFloatIP;
  public Address sysPrimitiveParseIntIP;
  public Address sysPrimitiveParseLongIP;
  public Address sysParseMemorySizeIP;

  // time
  Address sysCurrentTimeMillisIP;
  Address sysNanoTimeIP;
  Address sysNanoSleepIP;

  // shared libraries
  Address sysDlopenIP;
  Address sysDlsymIP;

  // var args
  public Address sysVaCopyIP;
  public Address sysVaEndIP;
  public Address sysVaArgJbooleanIP;
  public Address sysVaArgJbyteIP;
  public Address sysVaArgJcharIP;
  public Address sysVaArgJshortIP;
  public Address sysVaArgJintIP;
  public Address sysVaArgJlongIP;
  public Address sysVaArgJfloatIP;
  public Address sysVaArgJdoubleIP;
  public Address sysVaArgJobjectIP;

  // VMMath
  public Address sysVMMathSinIP;
  public Address sysVMMathCosIP;
  public Address sysVMMathTanIP;
  public Address sysVMMathAsinIP;
  public Address sysVMMathAcosIP;
  public Address sysVMMathAtanIP;
  public Address sysVMMathAtan2IP;
  public Address sysVMMathCoshIP;
  public Address sysVMMathSinhIP;
  public Address sysVMMathTanhIP;
  public Address sysVMMathExpIP;
  public Address sysVMMathLogIP;
  public Address sysVMMathSqrtIP;
  public Address sysVMMathPowIP;
  public Address sysVMMathIEEEremainderIP;
  public Address sysVMMathCeilIP;
  public Address sysVMMathFloorIP;
  public Address sysVMMathRintIP;
  public Address sysVMMathCbrtIP;
  public Address sysVMMathExpm1IP;
  public Address sysVMMathHypotIP;
  public Address sysVMMathLog10IP;
  public Address sysVMMathLog1pIP;

  // system calls for alignment checking
  public Address sysEnableAlignmentCheckingIP;
  public Address sysDisableAlignmentCheckingIP;
  public Address sysReportAlignmentCheckingIP;

  /* FIXME: We *really* don't want all these syscalls here unconditionally --- need to push them out somehow */
  // GCspy entry points
  public Address gcspyDriverAddStreamIP;
  public Address gcspyDriverEndOutputIP;
  public Address gcspyDriverInitIP;
  public Address gcspyDriverInitOutputIP;
  public Address gcspyDriverResizeIP;
  public Address gcspyDriverSetTileNameRangeIP;
  public Address gcspyDriverSetTileNameIP;
  public Address gcspyDriverSpaceInfoIP;
  public Address gcspyDriverStartCommIP;
  public Address gcspyDriverStreamIP;
  public Address gcspyDriverStreamByteValueIP;
  public Address gcspyDriverStreamShortValueIP;
  public Address gcspyDriverStreamIntValueIP;
  public Address gcspyDriverSummaryIP;
  public Address gcspyDriverSummaryValueIP;

  public Address gcspyIntWriteControlIP;

  public Address gcspyMainServerAddDriverIP;
  public Address gcspyMainServerAddEventIP;
  public Address gcspyMainServerInitIP;
  public Address gcspyMainServerIsConnectedIP;
  public Address gcspyMainServerOuterLoopIP;
  public Address gcspyMainServerSafepointIP;
  public Address gcspyMainServerSetGeneralInfoIP;
  public Address gcspyMainServerStartCompensationTimerIP;
  public Address gcspyMainServerStopCompensationTimerIP;

  public Address gcspyStartserverIP;

  public Address gcspyStreamInitIP;

  public Address gcspyFormatSizeIP;
  public Address gcspySprintfIP;

  public Address sysStackAlignmentTestIP;
  public Address sysArgumentPassingTestIP;
  public Address sysArgumentPassingSeveralLongsAndSeveralDoublesIP;
  public Address sysArgumentPassingSeveralFloatsAndSeveralIntsIP;

  // perf event support
  public Address sysPerfEventInitIP;
  public Address sysPerfEventCreateIP;
  public Address sysPerfEventEnableIP;
  public Address sysPerfEventDisableIP;
  public Address sysPerfEventReadIP;

}
