/*
 * (C) Copyright IBM Corp 2001,2002
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

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
    heapRanges = VM_AddressArray.create(len);
    // Indicate end of array with sentinel value
    heapRanges.set(len -1, VM_Address.fromIntSignExtend(-1));
    heapRanges.set(len -2, VM_Address.fromIntSignExtend(-1));
  }

  public void showHeapRanges() {
    for (int i=0; i<heapRanges.length() / 2; i++) {
      VM.sysWrite(i, "  ");
      VM.sysWrite(heapRanges.get(2 * i));
      VM.sysWrite("  ", heapRanges.get(2 * i + 1));
      VM.sysWrite("  ");
    }
  }

  public void setHeapRange(int id, VM_Address start, VM_Address end) throws VM_PragmaUninterruptible {
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
  public VM_Address bootImageStart;
  public VM_Address bootImageEnd;

  /**
   * initial size of heap
   */
  public int initialHeapSize;

  /**
   * maximum size of heap
   */
  public int maximumHeapSize;

  public VM_AddressArray heapRanges; // [start1, end1, ..., start_k, end_k, -1, -1]
                                     // C-style termination with sentinel values
  /**
   * Verbosity level for booting
   * set by -X:verboseBoot=
   */
  int verboseBoot = 0;
  
  
  // RVM startoff
  //
  public int tiRegister;          // value to place into TI register
  public VM_Address spRegister;   // value to place into SP register
  public VM_Address ipRegister;   // value to place into IP register
  public VM_Address tocRegister;  // value to place into JTOC register

  /**
   * flag to indicate RVM has completed booting and ready to run Java programs
   * added by Ton Ngo for JNI support
   */
  int bootCompleted;       // use for start up by JNI_CreateJavaVM

  // Additional RVM entrypoints
  //
  /**
   * method id for inserting stackframes at site of hardware traps
   */
  int hardwareTrapMethodId;           
  /**
   * jtoc offset of VM_Runtime.deliverHardwareException()
   */
  int deliverHardwareExceptionOffset; 
  /**
   * jtoc offset of VM_Scheduler.dumpStackAndDie(I)
   */
  int dumpStackAndDieOffset;          
  /**
   * jtoc offset of VM_Scheduler.processors[]
   */
  public int processorsOffset;               
  /**
   * jtoc offset of VM_Scheduler.debugRequested
   */
  int debugRequestedOffset;           
  /**
   * an external signal has been sent e.g. kill -signalnumber processid
   */
  int externalSignalFlag;             

  // Host operating system entrypoints - see "sys.C"
  //

  //-#if RVM_FOR_POWERPC
  /**
   * value to place in TOC register when issuing "sys" calls
   */
  public VM_Address sysTOC;           
  /**
   * dummy function to pair with sysTOC
   */
  VM_Address sysIP;            
  //-#endif

  // startup/shutdown
  public VM_Address sysWriteCharIP;    
  public VM_Address sysWriteIP;            
  public VM_Address sysWriteLongIP;
  public VM_Address sysExitIP;                     
  public VM_Address sysArgIP;

  // memory
  public VM_Address sysCopyIP;         
  public VM_Address sysFillIP;
  public VM_Address sysMallocIP;
  public VM_Address sysFreeIP;
  public VM_Address sysZeroIP;
  public VM_Address sysZeroPagesIP;
  public VM_Address sysSyncCacheIP;

  // files
  public VM_Address sysStatIP;         
  public VM_Address sysListIP;
  public VM_Address sysOpenIP;                
  public VM_Address sysUtimeIP;                
  public VM_Address sysReadByteIP;            
  public VM_Address sysWriteByteIP;
  public VM_Address sysReadBytesIP;
  public VM_Address sysWriteBytesIP;
  public VM_Address sysSeekIP;
  public VM_Address sysCloseIP;
  public VM_Address sysDeleteIP;
  public VM_Address sysRenameIP;
  public VM_Address sysMkDirIP;
  public VM_Address sysBytesAvailableIP;
  public VM_Address sysIsValidFDIP;
  public VM_Address sysLengthIP;
  public VM_Address sysSetLengthIP;
  public VM_Address sysSyncFileIP;
  public VM_Address sysIsTTYIP;
  public VM_Address sysSetFdCloseOnExecIP;
  
  public VM_Address sysAccessIP;
  // shm* - memory mapping
  public VM_Address sysShmgetIP;
  public VM_Address sysShmctlIP;
  public VM_Address sysShmatIP;
  public VM_Address sysShmdtIP;

  // mmap - memory mapping
  public VM_Address sysMMapIP;
  public VM_Address sysMMapErrnoIP;
  public VM_Address sysMUnmapIP;
  public VM_Address sysMProtectIP;
  public VM_Address sysMSyncIP;
  public VM_Address sysMAdviseIP;
  public VM_Address sysGetPageSizeIP;

  // threads
  public VM_Address sysNumProcessorsIP;
  public VM_Address sysVirtualProcessorCreateIP;
  public VM_Address sysVirtualProcessorBindIP;
  public VM_Address sysVirtualProcessorYieldIP;
  public VM_Address sysVirtualProcessorEnableTimeSlicingIP;
  public VM_Address sysPthreadSelfIP;
  public VM_Address sysPthreadSignalIP;
  public VM_Address sysPthreadExitIP;
  public VM_Address sysPthreadJoinIP;
  //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  public VM_Address sysStashVmProcessorInPthreadIP;
  //-#endif

  // arithmetic 
  public VM_Address sysLongDivideIP;
  public VM_Address sysLongRemainderIP;
  public VM_Address sysLongToFloatIP;
  public VM_Address sysLongToDoubleIP;
  public VM_Address sysFloatToIntIP;
  public VM_Address sysDoubleToIntIP;
  public VM_Address sysFloatToLongIP;
  public VM_Address sysDoubleToLongIP;
  //-#if RVM_FOR_POWERPC
  public VM_Address sysDoubleRemainderIP;
  //-#endif
  public VM_Address sysPrimitiveParseFloatIP;
  public VM_Address sysPrimitiveParseIntIP;

  // time
  VM_Address sysGetTimeOfDayIP;

  // shared libraries
  VM_Address sysDlopenIP;
  VM_Address sysDlcloseIP;
  VM_Address sysDlsymIP;
  VM_Address sysSlibcleanIP;

  // network
  public VM_Address sysNetLocalHostNameIP;
  public VM_Address sysNetRemoteHostNameIP;
  public VM_Address sysNetHostAddressesIP;
  public VM_Address sysNetSocketCreateIP;
  public VM_Address sysNetSocketPortIP;
  public VM_Address sysNetSocketFamilyIP;
  public VM_Address sysNetSocketLocalAddressIP;
  public VM_Address sysNetSocketBindIP;
  public VM_Address sysNetSocketConnectIP;
  public VM_Address sysNetSocketListenIP;
  public VM_Address sysNetSocketAcceptIP;
  public VM_Address sysNetSocketLingerIP;
  public VM_Address sysNetSocketNoDelayIP;
  public VM_Address sysNetSocketNoBlockIP;
  public VM_Address sysNetSocketCloseIP;
  public VM_Address sysNetSocketShutdownIP;
  public VM_Address sysNetSelectIP;

  // process management
  public VM_Address sysWaitPidsIP;

  //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  // system startup pthread sync. primitives
  //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  public VM_Address sysCreateThreadSpecificDataKeysIP;
  //-#endif
  public VM_Address sysInitializeStartupLocksIP;
  public VM_Address sysWaitForVirtualProcessorInitializationIP;
  public VM_Address sysWaitForMultithreadingStartIP;
  //-#endif

  //-#if RVM_WITH_HPM
  // sysCall entry points to HPM
  public VM_Address sysHPMinitIP;
  public VM_Address sysHPMsetEventIP;
  public VM_Address sysHPMsetEventXIP;
  public VM_Address sysHPMsetModeIP;
  public VM_Address sysHPMgetNumberOfCountersIP;
  public VM_Address sysHPMgetNumberOfEventsIP;
  public VM_Address sysHPMisBigEndianIP;
  public VM_Address sysHPMtestIP;
  public VM_Address sysHPMsetProgramMyThreadIP;
  public VM_Address sysHPMstartMyThreadIP;
  public VM_Address sysHPMstopMyThreadIP;
  public VM_Address sysHPMresetMyThreadIP;
  public VM_Address sysHPMgetCounterMyThreadIP;
  public VM_Address sysHPMsetProgramMyGroupIP;
  public VM_Address sysHPMstartMyGroupIP;
  public VM_Address sysHPMstopMyGroupIP;
  public VM_Address sysHPMresetMyGroupIP;
  public VM_Address sysHPMgetCounterMyGroupIP;
  public VM_Address sysHPMprintMyGroupIP;
  //-#endif

   //-#if RVM_WITH_GCSPY
   // GCspy entry points
   public VM_Address gcspyDriverAddStreamIP;
   public VM_Address gcspyDriverEndOutputIP;
   public VM_Address gcspyDriverInitIP;
   public VM_Address gcspyDriverInitOutputIP;
   public VM_Address gcspyDriverResizeIP;
   public VM_Address gcspyDriverSetTileNameIP;
   public VM_Address gcspyDriverSpaceInfoIP;
   public VM_Address gcspyDriverStartCommIP;
   public VM_Address gcspyDriverStreamIP;
   public VM_Address gcspyDriverStreamByteValueIP;
   public VM_Address gcspyDriverStreamShortValueIP;
   public VM_Address gcspyDriverStreamIntValueIP;
   public VM_Address gcspyDriverSummaryIP;
   public VM_Address gcspyDriverSummaryValueIP;

   public VM_Address gcspyIntWriteControlIP;

   public VM_Address gcspyMainServerAddDriverIP;
   public VM_Address gcspyMainServerAddEventIP;
   public VM_Address gcspyMainServerInitIP;
   public VM_Address gcspyMainServerIsConnectedIP;
   public VM_Address gcspyMainServerOuterLoopIP;
   public VM_Address gcspyMainServerSafepointIP;
   public VM_Address gcspyMainServerSetGeneralInfoIP;
   public VM_Address gcspyMainServerStartCompensationTimerIP;
   public VM_Address gcspyMainServerStopCompensationTimerIP;

   public VM_Address gcspyStartserverIP;
     
   public VM_Address gcspyStreamInitIP;

   public VM_Address gcspyFormatSizeIP;
   public VM_Address gcspySprintfIP;
   //-#endif

}
