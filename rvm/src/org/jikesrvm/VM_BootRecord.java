/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002,2004
 */
package org.jikesrvm;

import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
 * <p> See also: BootImageWriter.main(), RunBootImage.C
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
 *                  |    <empty>    |
 *                  +---------------+
 *                  |     ...       |
 *                  +---------------+
 *                  |    <empty>    |
 *                  +---------------+
 *    spRegister ->      hi-mem
 * </pre>
 *
 * <P> The "ipRegister" field of the boot record points to the first word
 * of an array of machine instructions comprising
 * the virtual machine's startoff code -- see "VM.boot()".
 *
 * <P> The "tocRegister" field of the boot record points to an array of words
 * containing the static fields and method addresses of the virtual
 * machine image -- see "VM_Statics.slots[]".
 *
 * <P> The remaining fields of the boot record serve as a function linkage area
 * between services residing in the host operating system and services
 * residing in the virtual machine.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_BootRecord {
  /**
   * The following static field is initialized by the boot image writer.
   * It allows the virtual machine to address the boot record using normal 
   * field access instructions (the assembler bootstrap function, on the other
   * hand, simply addresses the boot record as the first object in 
   * the boot image).
   */ 
  public static VM_BootRecord the_boot_record;

  public VM_BootRecord() {
    int len = 2 * (1 + MM_Interface.getMaxHeaps());
    heapRanges = AddressArray.create(len);
    // Indicate end of array with sentinel value
    heapRanges.set(len -1, Address.fromIntSignExtend(-1));
    heapRanges.set(len -2, Address.fromIntSignExtend(-1));
  }

  public void showHeapRanges() {
    for (int i=0; i<heapRanges.length() / 2; i++) {
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
  // is generated (see BootImage.java), loaded (see RunBootImage.C),
  // or executed (see VM.java).
  //
  // If you add/remove/change fields here, be sure to change the 
  // corresponding code in RunBootImage.

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

  public AddressArray heapRanges; // [start1, end1, ..., start_k, end_k, -1, -1]
                                     // C-style termination with sentinel values
  /**
   * Verbosity level for booting
   * set by -X:verboseBoot=
   */
  int verboseBoot = 0;
  
  // RVM startoff
  //
  public int tiRegister;          // value to place into TI register
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
   * defined in Sys.C
   */
  public Address sysJavaVM;

  // Additional RVM entrypoints
  //
  /**
   * method id for inserting stackframes at site of hardware traps
   */
  int hardwareTrapMethodId;           
  /**
   * jtoc offset of VM_Runtime.deliverHardwareException()
   */
  Offset deliverHardwareExceptionOffset; 
  /**
   * jtoc offset of VM_Scheduler.dumpStackAndDie(I)
   */
  public Offset dumpStackAndDieOffset;          
  /**
   * jtoc offset of VM_Scheduler.processors[]
   */
  public Offset processorsOffset;               
  /**
   * jtoc offset of VM_Scheduler.debugRequested
   */
  Offset debugRequestedOffset;           
  /**
   * an external signal has been sent e.g. kill -signalnumber processid
   */
  int externalSignalFlag;             

  // Host operating system entrypoints - see "sys.C"
  //

  // lowlevel write to console
  public Address sysWriteCharIP;    
  public Address sysWriteIP;            
  public Address sysWriteLongIP;
  public Address sysWriteDoubleIP;
  
  // startup/shutdown
  public Address sysExitIP;                     
  public Address sysArgIP;

  // misc. info on the process -- used in startup/shutdown
  public Address sysGetenvIP;

  // memory
  public Address sysCopyIP;         
  public Address sysMallocIP;
  public Address sysFreeIP;
  public Address sysZeroIP;
  public Address sysZeroPagesIP;
  public Address sysSyncCacheIP;

  // files
  public Address sysStatIP;         
  public Address sysReadByteIP;            
  public Address sysWriteByteIP;
  public Address sysReadBytesIP;
  public Address sysWriteBytesIP;
  public Address sysBytesAvailableIP;
  public Address sysSyncFileIP;
  public Address sysSetFdCloseOnExecIP;
  
  public Address sysAccessIP;

  // mmap - memory mapping
  public Address sysMMapIP;
  public Address sysMMapErrnoIP;
  public Address sysMProtectIP;
  public Address sysGetPageSizeIP;

  // threads
  public Address sysNumProcessorsIP;
  public Address sysVirtualProcessorCreateIP;
  public Address sysVirtualProcessorBindIP;
  public Address sysVirtualProcessorYieldIP;
  public Address sysVirtualProcessorEnableTimeSlicingIP;
  public Address sysPthreadSelfIP;
  public Address sysPthreadSetupSignalHandlingIP;
  public Address sysPthreadSignalIP;
  public Address sysPthreadExitIP;
  public Address sysPthreadJoinIP;
  public Address sysStashVmProcessorInPthreadIP;

  // arithmetic 
  public Address sysLongDivideIP;
  public Address sysLongRemainderIP;
  public Address sysLongToFloatIP;
  public Address sysLongToDoubleIP;
  public Address sysFloatToIntIP;
  public Address sysDoubleToIntIP;
  public Address sysFloatToLongIP;
  public Address sysDoubleToLongIP;
  public Address sysDoubleRemainderIP;
  public Address sysPrimitiveParseFloatIP;
  public Address sysPrimitiveParseIntIP;
  public Address sysParseMemorySizeIP;

  // time
  Address sysGetTimeOfDayIP;
  Address sysNanosleepIP;

  // shared libraries
  Address sysDlopenIP;
  Address sysDlsymIP;

  // network
  public Address sysNetSocketSndBufIP;
  public Address sysNetSocketCreateIP;
  public Address sysNetSocketPortIP;
  public Address sysNetSocketFamilyIP;
  public Address sysNetSocketLocalAddressIP;
  public Address sysNetSocketBindIP;
  public Address sysNetSocketConnectIP;
  public Address sysNetSocketListenIP;
  public Address sysNetSocketAcceptIP;
  public Address sysNetSocketLingerIP;
  public Address sysNetSocketNoDelayIP;
  public Address sysNetSocketNoBlockIP;
  public Address sysNetSocketCloseIP;
  public Address sysNetSocketShutdownIP;
  public Address sysNetSelectIP;

  // process management
  public Address sysWaitPidsIP;

  // system startup pthread sync. primitives
  public Address sysCreateThreadSpecificDataKeysIP;
  public Address sysInitializeStartupLocksIP;
  public Address sysWaitForVirtualProcessorInitializationIP;
  public Address sysWaitForMultithreadingStartIP;

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
}
