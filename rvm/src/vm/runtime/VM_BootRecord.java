/*
 * (C) Copyright IBM Corp. 2001
 */

// $Id$

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
  VM_Address bootImageStart;
  VM_Address bootImageEnd;

  /**
   * size of various spaces in bytes
   */
  int smallSpaceSize; 	    // Always present
  int largeSpaceSize; 	    // Almost always present
  int nurserySize;          // Present in generational collectors

  // int[] should be VM_Address[] but compiler erroneously emits barriers
  int [] heapRanges;         // [start1, end1, ..., start_k, end_k, -1, -1]
                             // C-style termination with sentinel values

  int verboseGC;             // GC verbosity level 

  // Relocation not supported
  //
  // VM_Address relocaterAddress;    // address of first word of an array of offsets to all addresses in the image.
  // int relocaterLength;     


  // RVM startoff
  //
  int tiRegister;          // value to place into TI register
  int spRegister;          // value to place into SP register
  int ipRegister;          // value to place into IP register
  int tocRegister;         // value to place into TOC register

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
  int processorsOffset;               
  /**
   * jtoc offset of VM_Scheduler.threads[]
   */
  int threadsOffset;                  
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
  int sysTOC;           
  /**
   * dummy function to pair with sysTOC
   */
  int sysIP;            
  //-#endif

  // startup/shutdown
  int sysWriteCharIP;    
  int sysWriteIP;            
  int sysWriteLongIP;
  int sysExitIP;                     
  int sysArgIP;

  // memory
  int sysCopyIP;         
  int sysFillIP;
  int sysMallocIP;
  int sysFreeIP;
  int sysZeroIP;
  int sysZeroPagesIP;
  int sysSyncCacheIP;

  // files
  int sysStatIP;         
  int sysListIP;
  int sysOpenIP;                
  int sysReadByteIP;            
  int sysWriteByteIP;
  int sysReadBytesIP;
  int sysWriteBytesIP;
  int sysSeekIP;
  int sysCloseIP;
  int sysDeleteIP;
  int sysRenameIP;
  int sysMkDirIP;
  int sysBytesAvailableIP;
  int sysSyncFileIP;

  // shm* - memory mapping
  int sysShmgetIP;
  int sysShmctlIP;
  int sysShmatIP;
  int sysShmdtIP;

  // mmap - memory mapping
  int sysMMapIP;
  int sysMMapNonFileIP;
  int sysMMapGeneralFileIP;
  int sysMMapDemandZeroFixedIP;
  int sysMMapDemandZeroAnyIP;
  int sysMUnmapIP;
  int sysMProtectIP;
  int sysMSyncIP;
  int sysMAdviseIP;
  int sysGetPageSizeIP;

  // threads
  int sysNumProcessorsIP;
  int sysVirtualProcessorCreateIP;
  int sysVirtualProcessorBindIP;
  int sysVirtualProcessorYieldIP;
  int sysVirtualProcessorEnableTimeSlicingIP;
  int sysPthreadSelfIP;
  int sysPthreadSigWaitIP;
  int sysPthreadSignalIP;
  int sysPthreadExitIP;
  int sysPthreadJoinIP;

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
  public int sysNetSelectIP;

  public int sysSprintfIP;

  //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  //-#else
  // system startup pthread sync. primitives
  public int sysInitializeStartupLocksIP;
  public int sysWaitForVirtualProcessorInitializationIP;
  public int sysWaitForMultithreadingStartIP;
  //-#endif
}
