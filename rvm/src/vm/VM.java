/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A virtual machine.
 * Implements VM_Uninterruptible to suppress thread switching in boot() and
 * sysCall() prologues.
 *
 * @author Derek Lieber (project start).
 * @date 21 Nov 1997 
 */
public class VM extends VM_Properties implements VM_Constants, 
						 VM_Uninterruptible { 
  //-----------//
  // interface //
  //-----------//

  //----------------------------------------------------------------------//
  //                          Initialization.                             //
  //----------------------------------------------------------------------//

  /** 
   * Prepare vm classes for use by boot image writer.
   * @param classPath class path to be used by VM_ClassLoader
   * @param bootCompilerArgs command line arguments for the bootimage compiler
   */ 
  static void initForBootImageWriter(String classPath, 
				     String[] bootCompilerArgs) throws VM_ResolutionException {
    writingBootImage = true;
    init(classPath, bootCompilerArgs);
  }

  /**
   * Prepare vm classes for use by tools.
   * @exception VM_ResolutionException
   */
  static void initForTool() throws VM_ResolutionException {
    runningTool = true;
    LoadLocalVariableTables = true;  // make sure to load the local table
    init(System.getProperty("java.class.path"), null);
  }

  /**
   * Prepare vm classes for use by tools.
   * @param classpath class path to be used by VM_ClassLoader
   * @exception VM_ResolutionException
   */
  static void initForTool(String classpath) throws VM_ResolutionException {
    runningTool = true;
    LoadLocalVariableTables = true;  // make sure to load the local table
    init(classpath, null);
  }

  /**
   * Begin vm execution.
   * The following machine registers are set by "C" bootstrap program 
   * before calling this method:
   *    JTOC_POINTER        - required for accessing globals
   *    FRAME_POINTER       - required for accessing locals
   *    THREAD_ID_REGISTER  - required for method prolog (stack overflow check)
   * @exception Exception
   */
  public static void boot() throws Exception {
    VM.writingBootImage = false;
    VM.runningVM        = true;
    VM.runningAsSubsystem = false;

    // 1. Finish thread initialization that couldn't be done in boot image.
    //    The "stackLimit" must be set before any method calls, because it's accessed
    //    by compiler-generated stack overflow checks.
    //
    VM_Thread currentThread  = VM_Scheduler.threads[VM_Magic.getThreadId() >>> OBJECT_THREAD_ID_SHIFT];
    currentThread.stackLimit = VM_Magic.objectAsAddress(currentThread.stack) + STACK_SIZE_GUARD;

    // get pthread_id from OS and store into vm_processor field
    // 
    if (!BuildForSingleVirtualProcessor)
      VM_Processor.getCurrentProcessor().pthread_id = 
        VM.sysCall0(VM_BootRecord.the_boot_record.sysPthreadSelfIP);
    
     
    // 2. Initialize memory manager's write barrier.
    //    This must happen before any putfield or arraystore of object refs
    //    because the buffer is accessed by compiler-generated write barrier code.
    //
    if (VM_Collector.USES_WRITE_BARRIER) {
      VM_Collector.setupProcessor( VM_Processor.getCurrentProcessor() );
    }
     
    //-#if RVM_WITH_CONCURRENT_GC
    // call setupProcessor to initialize increment/decrement buffer
    // and set localEpoch to -1
    //
    // if reference counting set USES_WRITE_BARRIER this call to
    // setupProcessor would occur above...ie remove this ifdef
    //
    VM_Collector.setupProcessor( VM_Processor.getCurrentProcessor() );
    //-#endif
    
    // 3. Initialize memory manager.
    //    This must happen before any uses of "new".
    //
    VM_Collector.boot(VM_BootRecord.the_boot_record);
    
    VM.sysWrite("vm: booting\n");
    
    // 4. Reset timers, so they don't inherit values from boot image.
    //
    VM_Timer.reset();

    // 5. Fetch arguments from program command line.
    //
    VM_CommandLineArgs.fetchCommandLineArguments();

    // 6. Allow Collector to respond to command line arguments
    //
    VM_Collector.postBoot();

    // 7. Initialize class loader.
    //
    String vmClasses = VM_CommandLineArgs.getVMClasses();
    VM_ClassLoader.boot(vmClasses);

    //
    // At this point the virtual machine is running as a single thread 
    // that can perform dynamic compilation and linking (by compiler/linker 
    // that's part of boot image).  All that remains is to initialize the 
    // java class libraries, start up the thread subsystem, and launch
    // the user level "main" thread.
    //
     
    // Initialize statics that couldn't be placed in bootimage, either 
    // because they refer to external state (open files), or because they 
    // appear in fields that are unique to RVM implementation of 
    // standard class library (not part of standard jdk).
    // We discover the latter by observing "host has no field" and 
    // "object not part of bootimage" messages printed out by bootimage 
    // writer.
    //
    runClassInitializer("java.io.FileDescriptor");

    runClassInitializer("java.io.FileDescriptor");
    runClassInitializer("java.lang.Runtime");
    runClassInitializer("java.lang.System");
    System.boot();
    runClassInitializer("java.io.File");
    runClassInitializer("java.lang.Boolean");
    runClassInitializer("java.lang.Byte");
    runClassInitializer("java.lang.Short");
    runClassInitializer("java.lang.Integer");
    runClassInitializer("java.lang.Long");
    runClassInitializer("java.lang.Float");
    runClassInitializer("java.lang.Double");
    runClassInitializer("java.lang.Character");
    runClassInitializer("java.util.Hashtable");
    runClassInitializer("com.ibm.oti.io.CharacterConverter");
    runClassInitializer("java.lang.Class");
 
    // Initialize compiler.
    //
    VM_RuntimeCompiler.boot();
 
    
    // Process virtual machine directives.
    //
    String[] applicationArguments = VM_CommandLineArgs.processCommandLineArguments();
    if (applicationArguments.length == 0) {  
      VM.sysWrite("vm: please specify a class to execute\n");
      VM.sysExit(1);
    }

    // Work around class incompatibilities in boot image writer
    // (JDK's java.lang.Thread does not extend VM_Thread) [--IP].
    Thread    xx         = new MainThread(applicationArguments);
    int       yy         = VM_Magic.objectAsAddress(xx);
    VM_Thread mainThread = (VM_Thread)VM_Magic.addressAsObject(yy);

    // record the main thread and the name of the main application class.
    _mainApplicationClassName = applicationArguments[0];
    _mainThread = mainThread;
    
     
    //Ensure that all classes in the boot image that have static synchronized methods have their class objects loaded:
    (VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Thread;")).asClass()).getClassForType();
     
    // Begin multiprocessing.
    //
    VM_Scheduler.boot(mainThread);
    if (VM.VerifyAssertions) 
      VM.assert(VM.NOT_REACHED);
  }
   
  /**
   * Run <clinit> method of specified class, if that class appears 
   * in bootimage.
   * @param className
   */
  private static void runClassInitializer(String className) {
    VM_Atom  classDescriptor = 
       VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor).asClass();
    if (cls.isInBootImage()) {
      VM_Magic.invokeClassInitializer(cls.getClassInitializerMethod().getMostRecentlyGeneratedInstructions());
      cls.setAllFinalStaticJTOCEntries();
    }
  }
   
  //----------------------------------------------------------------------//
  //                         Execution environment.                       //
  //----------------------------------------------------------------------//
   
  /**
   * Verify a runtime assertion (die w/traceback if assertion fails).
   * Note: code your assertion checks as 
   * "if (VM.VerifyAssertions) VM.assert(xxx);"
   * @param b the assertion to verify
   */
  public static void assert(boolean b) {
    assert(b, null);
  }

  /**
   * Verify a runtime assertion (die w/message and traceback if 
   * assertion fails).   Note: code your assertion checks as 
   * "if (VM.VerifyAssertions) VM.assert(xxx,yyy);"
   * @param b the assertion to verify
   * @param message the message to print if the assertion is false
   */
  static void assert(boolean b, String message) {
    if (VM.VerifyAssertions == false) {
      // somebody forgot to conditionalize their call to assert with
      // "if (VM.VerifyAssertions)"
      _assertionFailure("vm internal error: assert called when !VM.VerifyAssertions");
    }
    
    if (!b) _assertionFailure(message);
  }

  private static void _assertionFailure(String message) {
    VM_Magic.pragmaNoInline(); // prevent opt compiler from inlining failure code.
    if (message == null) message = "vm internal error at:";
    if (VM.runningVM) {
      sysFail(message);
    }
    throw new RuntimeException(message);
  }


  /**
   * Format a 32 bit number as "0x" followed by 8 hex digits.
   * Do this without referencing Integer or Character classes, 
   * in order to avoid dynamic linking.
   * @param number
   * @return 
   */
  static String intAsHexString(int number) {
    char[] buf   = new char[10];
    int    index = 10;
    while (--index > 1) {
      int digit = number & 0x0000000f;
      buf[index] = digit <= 9 ? (char)('0' + digit) : (char)('a' + digit - 10);
      number >>= 4;
    }
    buf[index--] = 'x';
    buf[index]   = '0';
    return new String(buf);
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void sysWrite(VM_Atom value) {
    value.sysWrite();
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void sysWrite(VM_Member value) {
////  VM.sysWrite(value.getDeclaringClass().getName());
    VM.sysWrite(value.getDeclaringClass().getDescriptor());
    VM.sysWrite(".");
    VM.sysWrite(value.getName());
    VM.sysWrite(" ");
    VM.sysWrite(value.getDescriptor());
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   */
  public static void sysWrite(String value) {
    if (runningVM)
      for (int i = 0, n = value.length(); i < n; ++i)
        sysWrite(value.charAt(i));
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value	what is printed
   */
  public static void sysWrite(char value) {
    if (runningVM)
      sysCall1(VM_BootRecord.the_boot_record.sysWriteCharIP, value);
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value	what is printed
   */
  public static void sysWrite(int value) {
    sysWrite(value, true);
  }

  /**
   * Low level print to console.
   * @param value	what is printed, as hex only
   */
  public static void sysWriteHex(int value) {
    if (runningVM)
      sysCall2(VM_BootRecord.the_boot_record.sysWriteIP, value, 2 /*just hex*/);
    else
      System.err.print(value);
  }
   
  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void sysWrite(int value, boolean hexToo) {
    if (runningVM)
      sysCall2(VM_BootRecord.the_boot_record.sysWriteIP, value, hexToo?1:0);
    else
      System.err.print(value);
  }

  /**
   * Exit virtual machine due to internal failure of some sort.
   * @param message  error message describing the problem
   */
  public static void sysFail(String message) {
    // print a traceback and die
    VM_Scheduler.traceback(message);
    VM.shutdown(1);
  }
   
  /**
   * Exit virtual machine.
   * @param value  value to pass to host o/s
   */
  public static void sysExit(int value) {
    if (runningVM) {
      System.out.flush();
      System.err.flush();
      VM_Callbacks.notifyExit(value);
      VM.shutdown(value);
    } else {
      System.exit(value);
    }
  }

  /**
   * Shut down the virtual machine.
   * Should only be called if the VM is running.
   * @param value  exit value
   */
  static void shutdown(int value) {
    if (VM.VerifyAssertions) VM.assert(VM.runningVM);
    if (VM.runningAsSubsystem) {
      // Terminate only the system threads that belong to the VM
      VM_Scheduler.processorExit(value);
    } else
      sysCall1(VM_BootRecord.the_boot_record.sysExitIP, value);
  }

  /**
   * Create a virtual processor (aka "unix kernel thread", "pthread").
   * @param jtoc  register values to use for thread startup
   * @param pr
   * @param ti
   * @param fp
   * @return virtual processor's o/s handle
   */
  static int sysVirtualProcessorCreate(int jtoc, int pr, int ti, int fp) {
    return sysCall4(VM_BootRecord.the_boot_record.sysVirtualProcessorCreateIP,
		    jtoc, pr, ti, fp);
  }

  /**
   * Bind execution of current virtual processor to specified physical cpu.
   * @param cpuId  physical cpu id (0, 1, 2, ...)
   */
  static void sysVirtualProcessorBind(int cpuId) {
    sysCall1(VM_BootRecord.the_boot_record.sysVirtualProcessorBindIP, cpuId);
  }
      
  /**
   * Yield execution of current virtual processor back to o/s.
   */
  static void sysVirtualProcessorYield() {
    sysCall0(VM_BootRecord.the_boot_record.sysVirtualProcessorYieldIP);
  }

  /**
   * Start interrupt generator for thread timeslicing.
   * The interrupt will be delivered to whatever virtual processor happens 
   * to be running when the timer expires.
   */
  static void sysVirtualProcessorEnableTimeSlicing() {
    sysCall0(VM_BootRecord.the_boot_record.sysVirtualProcessorEnableTimeSlicingIP);
  }
   
  //-#if RVM_FOR_POWERPC
  /**
   * Make calls to host operating system services.
   * @param ip address of a function in sys.C 
   * @return integer value returned by function in sys.C
   */
  public static int sysCall0 (int ip) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall0(ip, VM_BootRecord.the_boot_record.sysTOC);
  }

  /**
   * sysCall1
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return integer value returned by function in sys.C
   */
  public static int sysCall1 (int ip, int p1) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall1(ip, VM_BootRecord.the_boot_record.sysTOC, 
        p1);
  }

  /**
   * sysCall2
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall2 (int ip, int p1, int p2) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall2(ip, VM_BootRecord.the_boot_record.sysTOC, 
        p1, p2);
  }

  /**
   * sysCall3
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall3 (int ip, int p1, int p2, int p3) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall3(ip, VM_BootRecord.the_boot_record.sysTOC, 
        p1, p2, p3);
  }

  /**
   * sysCall4
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall4 (int ip, int p1, int p2, int p3, int p4) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall4(ip, VM_BootRecord.the_boot_record.sysTOC, 
        p1, p2, p3, p4);
  }

  /**
   * sysCall_L_0
   * @param ip  address of a function in sys.C 
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_0 (int ip) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall_L_0(ip, VM_BootRecord.the_boot_record.sysTOC);
  }

  /**
   * sysCall_L_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_I (int ip, int p1) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall_L_I(ip, VM_BootRecord.the_boot_record.sysTOC, 
        p1);
  }

  /**
   * sysCallAD
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCallAD (int ip, int p1, double p2) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCallAD(ip, VM_BootRecord.the_boot_record.sysTOC, 
        p1, p2);
  }

  //-#endif
  //-#if RVM_FOR_IA32
  /**
   * sysCall0
   * @param ip  address of a function in sys.C 
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall0 (int ip) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall0(ip);
  }

  /**
   * sysCall1
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall1 (int ip, int p1) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall1(ip, p1);
  }

  /**
   * sysCall2
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall2 (int ip, int p1, int p2) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall2(ip, p1, p2);
  }

  /**
   * sysCall3
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall3 (int ip, int p1, int p2, int p3) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall3(ip, p1, p2, p3);
  }

  /**
   * sysCall4
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @param p4
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall4 (int ip, int p1, int p2, int p3, int p4) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall4(ip, p1, p2, p3, p4);
  }

  /**
   * sysCall_L_0
   * @param ip  address of a function in sys.C 
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_0 (int ip) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall_L_0(ip);
  }

  /**
   * sysCall_L_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_I (int ip, int p1) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCall_L_I(ip, p1);
  }

  /**
   * sysCallAD
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCallAD (int ip, int p1, double p2) {
    VM_Magic.pragmaInline();
    return  VM_Magic.sysCallAD(ip, p1, p2);
  }

  //-#endif

  /**
   * Find or create unique string instance.
   * !!TODO: probably doesn't belong in VM. Where should it go? --DL
   * @param spelling  desired spelling
   * @return unique string with that spelling See: java.lang.String.intern()
   */
  public static String findOrCreateString(String spelling) {
    //!!TODO: This pollutes the jtoc with strings that needn't be allocated statically.
    //        We need to keep a separate table for intern'd strings that
    //        don't originate from class constant pools. [--DL]
    try {
      VM_Atom atom = VM_Atom.findOrCreateUnicodeAtom(spelling);
      int     slot = VM_Statics.findOrCreateStringLiteral(atom);
      return  (String)VM_Magic.addressAsObject(VM_Statics.getSlotContentsAsInt(slot));
    } catch (java.io.UTFDataFormatException x) {
      throw new InternalError();
    }
  }
   
  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need 
   * to address their own fields and methods in the runtime virtual machine 
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "LVM_Runtime;"
   * @param memberName       member name       - something like "invokestatic"
   * @param memberDescriptor member descriptor - something like "()V"
   * @return description
   */
  static VM_Member getMember(String classDescriptor, String memberName, 
			     String memberDescriptor) {
    VM_Atom clsDescriptor = VM_Atom.findOrCreateAsciiAtom(classDescriptor);
    VM_Atom memName       = VM_Atom.findOrCreateAsciiAtom(memberName);
    VM_Atom memDescriptor = VM_Atom.findOrCreateAsciiAtom(memberDescriptor);
    try {
      VM_Class cls = VM_ClassLoader.findOrCreateType(clsDescriptor).asClass();
      cls.load();
      cls.resolve();
         
      VM_Member member;
      if ((member = cls.findDeclaredField(memName, memDescriptor)) != null)
        return member;
      if ((member = cls.findDeclaredMethod(memName, memDescriptor)) != null)
        return member;

      // The usual causes for VM.getMember() to fail are:
      //  1. you mispelled the class name, member name, or member signature
      //  2. the class containing the specified member didn't get compiled
      //
      VM.sysWrite("VM.getMember: can't find class="+classDescriptor+" member="+memberName+" desc="+memberDescriptor+"\n");
      if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    } catch (VM_ResolutionException e) {
      VM.sysWrite("VM.getMember: can't resolve class=" + classDescriptor+
		  " member=" + memberName + " desc=" + memberDescriptor + "\n");
      if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    }
    return null;
  }

   //----------------//
   // implementation //
   //----------------//
   
  /**
   * Create class instances needed for boot image or initialize classes 
   * needed by tools.
   * @param vmClassPath places where vm implemention class reside
   * @param bootCompilerArgs command line arguments to pass along to the 
   *                         boot compiler's init routine.
   */
  private static void init(String vmClassPath, String[] bootCompilerArgs) 
    throws VM_ResolutionException {
    // create dummy boot record
    //
    VM_BootRecord.the_boot_record = new VM_BootRecord();
      
    // initialize type subsystem - create type descriptions for java.lang.Object 
    // and the classes whose methods it calls. we do this in an order chosen to 
    // ensure that offset and size information needed by the compiler to 
    // perform "direct" (non-dynamically linked) calls is ready before any 
    // method calls are compiled.
    //
    VM_Statics.init();
    VM_AtomDictionary.init();
    VM_MagicNames.init();
    VM_ClassLoader.init(vmClassPath);
    VM_Class object       = VM_Type.JavaLangObjectType.asClass();
    VM_Class string       = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Ljava/lang/String;")).asClass();
    VM_Class stringBuffer = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Ljava/lang/StringBuffer;")).asClass();
    VM_Class vm           = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("LVM;")).asClass();
    VM_Class runtime      = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("LVM_Runtime;")).asClass();
     
    // initialization of reference maps locks for jsr processing
    VM_ReferenceMaps.init(); 

    // initialize JNI environment
    VM_JNIEnvironment.init();

    // load class descriptions
    //
    object.load();
    string.load();
    stringBuffer.load();
    vm.load();
    runtime.load();
      
    // generate size and offset information needed for compiling methods of java.lang.Object
    //
    object.resolve();
    string.resolve();
    stringBuffer.resolve();
    vm.resolve();
    runtime.resolve();
    // initialize remaining subsystems needed for compilation
    //
    VM_Entrypoints.init();
    VM_OutOfLineMachineCode.init();
    VM_Compiler.init();   // initialize compiler that lives in boot image
    if (writingBootImage) // initialize compiler that builds boot image
      VM_BootImageCompiler.init(bootCompilerArgs);
    VM_Runtime.init();
    VM_Scheduler.init();
    VM_Collector.init();

    if (runningTool)
      return;
  }

  /**
   * The following two methods are for use as guards to protect code that 
   * must deal with raw object addresses in a collection-safe manner 
   * (ie. code that holds raw pointers across "gc-sites").
   *
   * Authors of code running while gc is disabled must be certain not to 
   * allocate objects explicitly via "new", or implicitly via methods that, 
   * in turn, call "new" (such as string concatenation expressions that are 
   * translated by the java compiler into String() and StringBuffer() 
   * operations). Furthermore, to prevent deadlocks, code running with gc 
   * disabled must not lock any objects. This means the code must not execute 
   * any bytecodes that require runtime support (eg. via VM_Runtime) 
   * such as:
   *   - calling methods or accessing fields of classes that haven't yet 
   *     been loaded/resolved/instantiated
   *   - calling synchronized methods
   *   - entering synchronized blocks
   *   - allocating objects with "new"
   *   - throwing exceptions 
   *   - executing trap instructions (including stack-growing traps)
   *   - storing into object arrays, except when runtime types of lhs & rhs 
   *     match exactly
   *   - typecasting objects, except when runtime types of lhs & rhs 
   *     match exactly
   *
   * Recommendation: as a debugging aid, VM_Allocator implementations 
   * should test "VM_Thread.disallowAllocationsByThisThread" to verify that 
   * they are never called while gc is disabled.
   */
  public static void disableGC() { 
    VM_Magic.pragmaInline();
    // current (non-gc) thread is going to be holding raw addresses, therefore we must:
    //
    // 1. make sure we have enough stack space to run until gc is re-enabled
    //    (otherwise we might trigger a stack reallocation)
    //
    // 2. force all other threads that need gc to wait until this thread
    //    is done with the raw addresses
    //
    // 3. ensure that this thread doesn't try to allocate any objects
    //    (because an allocation attempt might trigger a collection that
    //    would invalidate the addresses we're holding)
    //

    VM_Thread myThread = VM_Thread.getCurrentThread();
      
    // 1.
    //
    if (VM_Magic.getFramePointer() - STACK_SIZE_GCDISABLED < myThread.stackLimit)
       VM_Thread.resizeCurrentStack(myThread.stack.length + (STACK_SIZE_GCDISABLED >> 2), null);

    // 2.
    //
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
      
    // 3.
    //
    if (VM.VerifyAssertions) {
      VM.assert(myThread.disallowAllocationsByThisThread == false); // recursion not allowed
      myThread.disallowAllocationsByThisThread = true;
    }
  }

  /**
   * enable GC
   */
  public static void enableGC() { 
    VM_Magic.pragmaInline();
    if (VM.VerifyAssertions) {
      VM_Thread myThread = VM_Thread.getCurrentThread();
      // recursion not allowed
      VM.assert(myThread.disallowAllocationsByThisThread == true); 
      myThread.disallowAllocationsByThisThread = false;
    }
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }
   
  private static String _mainApplicationClassName;
  private static VM_Thread _mainThread;

  /**
   * getMainMethod
   * @return 
   */
  public static VM_Method getMainMethod()  {
    if(VM.VerifyAssertions) VM.assert(_mainThread != null);
    return ((MainThread)_mainThread).getMainMethod();
  } 

  /**
   * Place to set breakpoints (called by compiled code).
   */
  public static void debugBreakpoint() {
    VM_Magic.pragmaNoInline();
  }
}
