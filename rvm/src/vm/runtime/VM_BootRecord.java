/*
 * (C) Copyright IBM Corp 2001,2002
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

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
 * <p> See also: BootImageWriter.main(), VM_Magic.sysCall(), RunBootImage.C
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
    heapRanges = new int[2 * (1 + VM_Interface.getMaxHeaps())];
    // Indicate end of array with sentinel value
    heapRanges[heapRanges.length - 1] = -1;
    heapRanges[heapRanges.length - 2] = -1;
  }

  public void showHeapRanges() {
    for (int i=0; i<heapRanges.length / 2; i++) {
      VM.sysWrite(i, "  ");
      VM.sysWrite(heapRanges[2 * i], "  ");
      VM.sysWriteln(heapRanges[2 * i + 1], "  ");
    }
  }

  public void setHeapRange(int id, VM_Address start, VM_Address end) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(id < heapRanges.length - 2); 
    heapRanges[2 * id] = start.toInt();
    heapRanges[2 * id + 1] = end.toInt();
  }
  
  // The following fields are written when the virtual machine image
  // is generated (see BootImage.java), loaded (see RunBootImage.C),
  // or executed (see VM.java).
  //
  // If you add/remove/change fields here, be sure to change the 
  // corresponding code in RunBootImage.

  // RVM image
  //
  /**
   * address at which image is to be loaded into memory
   */
  public VM_Address bootImageStart;
  public VM_Address bootImageEnd;

  /**
   * size of various spaces in bytes
   */
  public int smallSpaceSize; 	    // Always present
  public int largeSpaceSize; 	    // Almost always present
  public int nurserySize;          // Present in generational collectors

  // int[] should be VM_Address[] but compiler erroneously emits barriers
  public int [] heapRanges;         // [start1, end1, ..., start_k, end_k, -1, -1]
                                    // C-style termination with sentinel values

  public int verboseGC;             // GC verbosity level 

  // Relocation not supported
  //
  // VM_Address relocaterAddress;    // address of first word of an array of offsets to all addresses in the image.
  // int relocaterLength;     


  // RVM startoff
  //
  public int tiRegister;          // value to place into TI register
  public int spRegister;          // value to place into SP register
  public int ipRegister;          // value to place into IP register
  public int tocRegister;         // value to place into TOC register

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
   * jtoc offset of VM_Scheduler.threads[]
   */
  public int threadsOffset;                  
  /**
   * jtoc offset of VM_Scheduler.debugRequested
   */
  int debugRequestedOffset;           
  /**
   * an external signal has been sent e.g. kill -signalnumber processid
   */
  int externalSignalFlag;             

  // Support for JNI Native functions
  //
  /**
   * jtoc offset of VM_Scheduler.attachThreadRequested
   */
  int attachThreadRequestedOffset;    
  /**
   * set when GC starts; reset at end
   */
  int globalGCInProgressFlag;        
  static final int GC_IN_PROGRESS = 1;   
  /**
   * used during GC and transfers to and from native processors
   */
  int lockoutProcessor; 


  // Host operating system entrypoints - see "sys.C"
  //

  //-#if RVM_FOR_POWERPC
  /**
   * value to place in TOC register when issuing "sys" calls
   */
  public int sysTOC;           
  /**
   * dummy function to pair with sysTOC
   */
  int sysIP;            
  //-#endif

  // startup/shutdown
  public int sysWriteCharIP;    
  public int sysWriteIP;            
  public int sysWriteLongIP;
  public int sysExitIP;                     
  public int sysArgIP;

  // memory
  public int sysCopyIP;         
  public int sysFillIP;
  public int sysMallocIP;
  public int sysFreeIP;
  public int sysZeroIP;
  public int sysZeroPagesIP;
  public int sysSyncCacheIP;

  // files
  public int sysStatIP;         
  public int sysListIP;
  public int sysOpenIP;                
  public int sysUtimeIP;                
  public int sysReadByteIP;            
  public int sysWriteByteIP;
  public int sysReadBytesIP;
  public int sysWriteBytesIP;
  public int sysSeekIP;
  public int sysCloseIP;
  public int sysDeleteIP;
  public int sysRenameIP;
  public int sysMkDirIP;
  public int sysBytesAvailableIP;
  public int sysSyncFileIP;
  public int sysIsTTYIP;
  public int sysSetFdCloseOnExecIP;
  
  public int sysAccessIP;
  // shm* - memory mapping
  public int sysShmgetIP;
  public int sysShmctlIP;
  public int sysShmatIP;
  public int sysShmdtIP;

  // mmap - memory mapping
  public int sysMMapIP;
  public int sysMMapNonFileIP;
  public int sysMMapGeneralFileIP;
  public int sysMMapDemandZeroFixedIP;
  public int sysMMapDemandZeroAnyIP;
  public int sysMUnmapIP;
  public int sysMProtectIP;
  public int sysMSyncIP;
  public int sysMAdviseIP;
  public int sysGetPageSizeIP;

  // threads
  public int sysNumProcessorsIP;
  public int sysVirtualProcessorCreateIP;
  public int sysVirtualProcessorBindIP;
  public int sysVirtualProcessorYieldIP;
  public int sysVirtualProcessorEnableTimeSlicingIP;
  public int sysPthreadSelfIP;
  public int sysPthreadSigWaitIP;
  public int sysPthreadSignalIP;
  public int sysPthreadExitIP;
  public int sysPthreadJoinIP;
  //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  //-#else
  public int sysStashVmProcessorIdInPthreadIP;
  //-#endif

  // arithmetic 
  int sysLongDivideIP;
  int sysLongRemainderIP;
  int sysLongToFloatIP;
  int sysLongToDoubleIP;
  int sysFloatToIntIP;
  int sysDoubleToIntIP;
  int sysFloatToLongIP;
  int sysDoubleToLongIP;
  //-#if RVM_FOR_POWERPC
  int sysDoubleRemainderIP;
  //-#endif

  // time
  int sysGetTimeOfDayIP;

  // shared libraries
  int sysDlopenIP;
  int sysDlcloseIP;
  int sysDlsymIP;
  int sysSlibcleanIP;

  // network
  public int sysNetLocalHostNameIP;
  public int sysNetRemoteHostNameIP;
  public int sysNetHostAddressesIP;
  public int sysNetSocketCreateIP;
  public int sysNetSocketPortIP;
  public int sysNetSocketFamilyIP;
  public int sysNetSocketLocalAddressIP;
  public int sysNetSocketBindIP;
  public int sysNetSocketConnectIP;
  public int sysNetSocketListenIP;
  public int sysNetSocketAcceptIP;
  public int sysNetSocketLingerIP;
  public int sysNetSocketNoDelayIP;
  public int sysNetSocketNoBlockIP;
  public int sysNetSocketCloseIP;
  public int sysNetSocketShutdownIP;
  public int sysNetSelectIP;

  // process management
  public int sysWaitPidsIP;

  public int sysSprintfIP;

  //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  //-#else
  // system startup pthread sync. primitives
  //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  //-#else
  public int sysCreateThreadSpecificDataKeysIP;
  //-#endif
  public int sysInitializeStartupLocksIP;
  public int sysWaitForVirtualProcessorInitializationIP;
  public int sysWaitForMultithreadingStartIP;
  //-#endif
  public int traceClassLoading;

  //-#if RVM_WITH_HPM
  // sysCall entry points to HPM
  public int sysHPMinitIP;
  public int sysHPMsetEventIP;
  public int sysHPMsetEventXIP;
  public int sysHPMsetModeIP;
  public int sysHPMsetSettingsIP;
  public int sysHPMstartCountingIP;
  public int sysHPMstopCountingIP;
  public int sysHPMresetCountersIP;
  public int sysHPMgetCountersIP;
  public int sysHPMgetCounterIP;
  public int sysHPMtestIP;
  //-#endif
}
