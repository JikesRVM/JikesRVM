/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

/**
 * A virtual machine.
 * Implements VM_Uninterruptible to suppress thread switching in boot() and
 * sysCall() prologues.
 *
 * @author Derek Lieber (project start).
 * @date 21 Nov 1997 
 */
public class VM extends VM_Properties 
    implements VM_Constants, VM_Uninterruptible { 
  //----------------------------------------------------------------------//
  //                          Initialization.                             //
  //----------------------------------------------------------------------//

  /** 
   * Prepare vm classes for use by boot image writer.
   * @param classPath class path to be used by VM_ClassLoader
   * @param bootCompilerArgs command line arguments for the bootimage compiler
   */ 
  public static void initForBootImageWriter(String classPath, 
					    String[] bootCompilerArgs) 
    throws VM_PragmaInterruptible, ClassNotFoundException {
    writingBootImage = true;
    init(classPath, bootCompilerArgs);
  }

  /**
   * Prepare vm classes for use by tools.
   */
  public static void initForTool() 
    throws VM_PragmaInterruptible, ClassNotFoundException   {
    runningTool = true;
    init(System.getProperty("java.class.path"), null);
  }

  /**
   * Prepare vm classes for use by tools.
   * @param classpath class path to be used by VM_ClassLoader
   */
  public static void initForTool(String classpath) 
    throws VM_PragmaInterruptible, ClassNotFoundException {
    runningTool = true;
    init(classpath, null);
  }

  static int verbose = 0;  // Show progress of boot 

  /**
   * Begin vm execution.
   * The following machine registers are set by "C" bootstrap program 
   * before calling this method:
   *    JTOC_POINTER        - required for accessing globals
   *    FRAME_POINTER       - required for accessing locals
   *    THREAD_ID_REGISTER  - required for method prolog (stack overflow check)
   * @exception Exception
   */
  public static void boot() throws Exception, VM_PragmaLogicallyUninterruptible {
    VM.writingBootImage = false;
    VM.runningVM        = true;
    VM.runningAsSubsystem = false;

    sysWriteLockOffset = VM_Entrypoints.sysWriteLockField.getOffset();
    if (verbose >= 1) VM.sysWriteln("Booting");

    // Set up the current VM_Processor object.  The bootstrap program
    // has placed a pointer to the current VM_Processor in a special
    // register.
    if (verbose >= 1) VM.sysWriteln("Setting up current VM_Processor");
    VM_ProcessorLocalState.boot();

    // Finish thread initialization that couldn't be done in boot image.
    // The "stackLimit" must be set before any interruptible methods are called
    // because it's accessed by compiler-generated stack overflow checks.
    //
    if (verbose >= 1) VM.sysWriteln("Doing thread initialization");
    VM_Thread currentThread  = VM_Scheduler.threads[VM_Magic.getThreadId() >>> VM_ThinLockConstants.TL_THREAD_ID_SHIFT];
    currentThread.stackLimit = VM_Magic.objectAsAddress(currentThread.stack).add(STACK_SIZE_GUARD);
    VM_Processor.getCurrentProcessor().activeThreadStackLimit = currentThread.stackLimit;

    // get pthread_id from OS and store into vm_processor field
    // 
    if (!BuildForSingleVirtualProcessor)
      VM_Processor.getCurrentProcessor().pthread_id = 
        VM_SysCall.call0(VM_BootRecord.the_boot_record.sysPthreadSelfIP);

    VM.TraceClassLoading = (VM_BootRecord.the_boot_record.traceClassLoading == 1);   

    // Initialize memory manager's write barrier.
    // This must happen before any putfield or arraystore of object refs
    // because the buffer is accessed by compiler-generated write barrier code.
    //
    if (verbose >= 1) VM.sysWriteln("Setting up write barrier");
    VM_Interface.setupProcessor( VM_Processor.getCurrentProcessor() );

    // Initialize memory manager.
    //    This must happen before any uses of "new".
    //
    if (verbose >= 1) VM.sysWriteln("Setting up memory manager: bootrecord = ", VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record));
    VM_Interface.boot(VM_BootRecord.the_boot_record);

    // Reset the options for the baseline compiler to avoid carrying them over from
    // bootimage writing time.
    // 
    VM_BaselineCompiler.initOptions();

    // Create class objects for static synchronized methods in the bootimage.
    // This must happen before any static synchronized methods of bootimage classes
    // can be invoked.
    if (verbose >= 1) VM.sysWriteln("Creating class objects for static synchronized methods");
    createClassObjects();

    // Fetch arguments from program command line.
    //
    if (verbose >= 1) VM.sysWriteln("Fetching command-line arguments");
    VM_CommandLineArgs.fetchCommandLineArguments();

    // Initialize class loader.
    //
    if (verbose >= 1) VM.sysWriteln("Initializing class loader");
    String vmClasses = VM_CommandLineArgs.getVMClasses();
    VM_ClassLoader.boot(vmClasses);
    VM_SystemClassLoader.boot();

    // Initialize statics that couldn't be placed in bootimage, either 
    // because they refer to external state (open files), or because they 
    // appear in fields that are unique to Jikes RVM implementation of 
    // standard class library (not part of standard jdk).
    // We discover the latter by observing "host has no field" and 
    // "object not part of bootimage" messages printed out by bootimage 
    // writer.
    //

    if (verbose >= 1) VM.sysWriteln("Running various class initializers");
    java.lang.ref.JikesRVMSupport.setReferenceLock(new VM_Synchronizer());

    runClassInitializer("java.lang.Runtime");
    runClassInitializer("java.lang.System");
    runClassInitializer("java.io.File");
    runClassInitializer("java.lang.Boolean");
    runClassInitializer("java.lang.Byte");
    runClassInitializer("java.lang.Short");
    runClassInitializer("java.lang.Number");
    runClassInitializer("java.lang.Integer");
    runClassInitializer("java.lang.Long");
    runClassInitializer("java.lang.Float");
    runClassInitializer("java.lang.Character");
    runClassInitializer("gnu.java.io.EncodingManager");
    runClassInitializer("java.lang.Thread");
    runClassInitializer("java.lang.ThreadGroup");
    runClassInitializer("java.io.PrintWriter");
    runClassInitializer("gnu.java.lang.SystemClassLoader");
    runClassInitializer("java.lang.String");
    runClassInitializer("gnu.java.security.provider.DefaultPolicy");
    runClassInitializer("java.security.Policy");
    runClassInitializer("java.util.WeakHashMap");
    runClassInitializer("java.lang.ClassLoader");
    runClassInitializer("java.lang.Math");
    runClassInitializer("java.util.TimeZone");
    runClassInitializer("java.util.Locale");
    runClassInitializer("java.util.Calendar");
    runClassInitializer("java.util.GregorianCalendar");
    runClassInitializer("java.util.ResourceBundle");
    runClassInitializer("java.util.zip.ZipEntry");
    runClassInitializer("java.util.zip.Inflater");
    runClassInitializer("java.util.zip.DeflaterHuffman");
    runClassInitializer("java.util.zip.InflaterDynHeader");
    runClassInitializer("java.util.zip.InflaterHuffmanTree");
    runClassInitializer("gnu.java.locale.Calendar");
    runClassInitializer("java.util.Date");
    //-#if RVM_WITH_ALL_CLASSES
    runClassInitializer("java.util.jar.Attributes$Name");
    //-#endif

    // Process virtual machine directives.
    //
    if (verbose >= 1) VM.sysWriteln("Processing VM directives");
    String[] applicationArguments = VM_CommandLineArgs.processCommandLineArguments();
    if (applicationArguments.length == 0) {  
      VM.sysWrite("vm: please specify a class to execute\n");
      VM.sysExit(1);
    }

    // Allow Baseline compiler to respond to command line arguments
    // The baseline compiler ignores command line arguments until all are processed
    // otherwise printing may occur because of compilations ahead of processing the
    // method_to_print restriction
    //
    if (verbose >= 1) VM.sysWriteln("Compiler processing rest of boot options");
    VM_BaselineCompiler.postBootOptions();

    // Allow Collector to respond to command line arguments
    //
    if (verbose >= 1) VM.sysWriteln("Collector processing rest of boot options");
    VM_Interface.postBoot();

    if (verbose >= 1) VM.sysWriteln("Booting VM_Lock");
    VM_Lock.boot();
    
    // set up HPM
    //-#if RVM_WITH_HPM
    if (BuildForHPM) {
      // assume only one Java thread is executing!
      if(VM_HardwarePerformanceMonitors.verbose>=1)
	VM.sysWriteln("VM.boot() call VM_HardwarePerformanceMonitors.boot()");
      VM_HardwarePerformanceMonitors.boot();

      // set hpm program for current pthread.  Inherited by other, to be created, pthreads.
      if (VM_HardwarePerformanceMonitors.enabled()) {
	if (! VM_HardwarePerformanceMonitors.hpm_thread_group) {
	  if(VM_HardwarePerformanceMonitors.verbose>=1)
	    VM.sysWriteln("VM.boot()","call to sysHPMsetSettings() and sysHPMstartMyThread()\n");
	  VM_SysCall.call0(VM_BootRecord.the_boot_record.sysHPMsetProgramMyThreadIP);
	  VM_SysCall.call0(VM_BootRecord.the_boot_record.sysHPMstartMyThreadIP);
	}
	// start tracing
      }
    }
    //-#endif

    // Enable multiprocessing.
    // Among other things, after this returns, GC and dynamic class loading are enabled.
    // 
    if (verbose >= 1) VM.sysWriteln("Booting scheduler");
    VM_Scheduler.boot();

    // Create JNI Environment for boot thread.  At this point the boot thread can invoke native methods.
    if (verbose >= 1) VM.sysWriteln("Initializing JNI for boot thread");
    VM_Thread.getCurrentThread().initializeJNIEnv();


    //-#if RVM_WITH_HPM
    runClassInitializer("com.ibm.JikesRVM.Java2HPM");
    VM_HardwarePerformanceMonitors.setUpHPMinfo();
    //-#endif

    // Run class intializers that require fully booted VM
    if (verbose >= 1) VM.sysWriteln("Running late class initializers");
    runClassInitializer("java.io.FileDescriptor");
    runClassInitializer("java.lang.Double");
    runClassInitializer("java.util.PropertyPermission");
    runClassInitializer("com.ibm.JikesRVM.VM_Process");

    // Initialize java.lang.System.out, java.lang.System.err, java.lang.System.in
    VM_FileSystem.initializeStandardStreams();

    // Allow profile information to be read in from a file
    VM_EdgeCounts.boot();

    // Initialize compiler that compiles dynamically loaded classes.
    //
    if (VM.verbose >= 1) VM.sysWriteln("Initializing runtime compiler");
    VM_RuntimeCompiler.boot();

    // At this point, all of the virtual processors should be running,
    // and thread switching should be enabled.
    if (VM.verboseClassLoading) VM.sysWrite("[VM booted]\n");

    // Create main thread.
    // Work around class incompatibilities in boot image writer
    // (JDK's java.lang.Thread does not extend VM_Thread) [--IP].
    if (VM.verbose >= 1) VM.sysWriteln("Constructing mainThread");
    Thread      xx         = new MainThread(applicationArguments);
    VM_Address  yy         = VM_Magic.objectAsAddress(xx);
    VM_Thread   mainThread = (VM_Thread)VM_Magic.addressAsObject(yy);

    //-#if RVM_WITH_OSR
    // Notify application run start
    VM_Callbacks.notifyAppRunStart("VM", 0);
    //-#endif

    // Schedule "main" thread for execution.
    if (verbose >= 1) VM.sysWriteln("Starting main thread");
    mainThread.start();

    // Create one debugger thread.
    VM_Thread t = new DebuggerThread();
    t.start(VM_Scheduler.debuggerQueue);

    //-#if RVM_WITH_HPM
    if (VM_HardwarePerformanceMonitors.enabled()) {
      // IS THIS NEEDED?
      if (! VM_HardwarePerformanceMonitors.hpm_thread_group) {
	if(VM_HardwarePerformanceMonitors.verbose>=1)
	  VM.sysWrite(" VM.boot() call sysHPMresetMyThread()\n");
	VM_SysCall.call0(VM_BootRecord.the_boot_record.sysHPMresetMyThreadIP);
      }
    }
    //-#endif

    // End of boot thread. Relinquish control to next job on work queue.
    //
    if (VM.TraceThreads) VM_Scheduler.trace("VM.boot", "completed - terminating");
    VM_Thread.terminate();
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  private static VM_Class[] classObjects = new VM_Class[0];
  /**
   * Called by the compilers when compiling a static synchronized method
   * during bootimage writing.
   */
  public static void deferClassObjectCreation(VM_Class c) throws VM_PragmaInterruptible {
    for (int i=0; i<classObjects.length; i++) {
      if (classObjects[i] == c) return; // already recorded
    }
    VM_Class[] tmp = new VM_Class[classObjects.length+1];
    System.arraycopy(classObjects, 0, tmp, 0, classObjects.length);
    tmp[classObjects.length] = c;
    classObjects = tmp;
  }
  /**
   * Create the java.lang.Class objects needed for 
   * static synchronized methods in the bootimage.
   */
  private static void createClassObjects() throws VM_PragmaInterruptible {
    for (int i=0; i<classObjects.length; i++) {
      if (verbose >= 2) {
	VM.sysWrite(classObjects[i].toString()); 
	VM.sysWriteln();
      }
      classObjects[i].getClassForType();
    }
  }

  /**
   * Run <clinit> method of specified class, if that class appears 
   * in bootimage.
   * @param className
   */
  static void runClassInitializer(String className) throws VM_PragmaInterruptible {
    if (verbose >= 2) {
      sysWrite("running class intializer for ");
      sysWriteln( className );
    }

    VM_Atom  classDescriptor = 
      VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), classDescriptor);
    VM_Class cls = (VM_Class)tRef.peekResolvedType();
    if (cls != null && cls.isInBootImage()) {
      VM_Method clinit = cls.getClassInitializerMethod();
      clinit.compile();
      if (verbose >= 10) VM.sysWriteln("invoking method " + clinit);
      VM_Magic.invokeClassInitializer(clinit.getCurrentInstructions());
      cls.setAllFinalStaticJTOCEntries();
    }
  }

  //----------------------------------------------------------------------//
  //                         Execution environment.                       //
  //----------------------------------------------------------------------//

  /**
   * Verify a runtime assertion (die w/traceback if assertion fails).
   * Note: code your assertion checks as 
   * "if (VM.VerifyAssertions) VM._assert(xxx);"
   * @param b the assertion to verify
   */
  public static void _assert(boolean b) {
    _assert(b, null);
  }

  /**
   * Verify a runtime assertion (die w/message and traceback if 
   * assertion fails).   Note: code your assertion checks as 
   * "if (VM.VerifyAssertions) VM._assert(xxx,yyy);"
   * @param b the assertion to verify
   * @param message the message to print if the assertion is false
   */
  public static void _assert(boolean b, String message) {
    if (!VM.VerifyAssertions) {
      // somebody forgot to conditionalize their call to assert with
      // "if (VM.VerifyAssertions)"
      _assertionFailure("vm internal error: assert called when !VM.VerifyAssertions");
    }

    if (!b) _assertionFailure(message);
  }

  private static void _assertionFailure(String message) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline {
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
   * TODO: move this method to VM_Services.
   * @param number
   * @return a String with the hex representation of the integer
   */
  public static String intAsHexString(int number) throws VM_PragmaInterruptible {
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
   * Format a 64 bit number as "0x" followed by 16 hex digits.
   * Do this without referencing Integer or Character classes, 
   * in order to avoid dynamic linking.
   * TODO: move this method to VM_Services.
   * @param number
   * @return a String with the hex representation of the long
   */
  public static String longAsHexString(long number) throws VM_PragmaInterruptible {
    char[] buf   = new char[18];
    int    index = 18;
    while (--index > 1) {
      int digit = (int) (number & 0x000000000000000f);
      buf[index] = digit <= 9 ? (char)('0' + digit) : (char)('a' + digit - 10);
      number >>= 4;
    }
    buf[index--] = 'x';
    buf[index]   = '0';
    return new String(buf);
  }

  public static String addressAsHexString(VM_Address addr) throws VM_PragmaInterruptible {
     //-#if RVM_FOR_64_ADDR
     return longAsHexString(addr.toLong());
     //-#else
     return intAsHexString(addr.toInt());
     //-#endif
  }

  private static int sysWriteLock = 0;
  private static int sysWriteLockOffset = -1;

  private static void swLock() {
    if (sysWriteLockOffset == -1) return;
    while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), sysWriteLockOffset, 1)) 
      ;
  }

  private static void swUnlock() {
    if (sysWriteLockOffset == -1) return;
    VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), sysWriteLockOffset, 0);
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_Atom value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    value.sysWrite();
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_Member value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(value.getMemberRef());
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_MemberReference value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(value.getType().getName());
    write(".");
    write(value.getName());
    write(" ");
    write(value.getDescriptor());
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   */
  public static void write(String value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      for (int i = 0, n = value.length(); i < n; ++i) 
        write(value.charAt(i));
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    } else {
      System.err.print(value);
    }
  }

  /**
    * Low level print to console.
   * @param value	what is printed
   */
  public static void write(char value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.call1(VM_BootRecord.the_boot_record.sysWriteCharIP, value);
    else
      System.err.print(value);
  }


  /**
   * Low level print of double to console.  Can't pass doubles so printing code in Java.
   *
   * @param value   double to be printed
   * @param int     number of decimal places
   */
  public static void write(double value, int postDecimalDigits) 
    throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline 
    /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      boolean negative = (value < 0.0);
      value = (value < 0.0) ? (-value) : value;
      int ones = (int) value;
      int multiplier = 1;
      while (postDecimalDigits-- > 0)
	multiplier *= 10;
      int remainder = (int) (multiplier * (value - ones));
      if (negative) write("-");
      write(ones, false); 
      write(".");
      while (multiplier > 1) {
	multiplier /= 10;
	write(remainder / multiplier);
	remainder %= multiplier;
      }
    }
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value	what is printed
   */
  public static void write(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      int mode = (value < -(1<<20) || value > (1<<20)) ? 2 : 0; // hex only or decimal only
      VM_SysCall.call2(VM_BootRecord.the_boot_record.sysWriteIP, value, mode);
    } else {
      System.err.print(value);
    }
  }

  /**
   * Low level print to console.
   * @param value	what is printed, as hex only
   */
  public static void writeHex(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.call2(VM_BootRecord.the_boot_record.sysWriteIP, value, 2 /*just hex*/);
    else {
      System.err.print(Integer.toHexString(value));
    }
  }

  /**
   * Low level print to console.
   * @param value	what is printed, as hex only
   */
  public static void writeHex(long value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.call_I_L_I(VM_BootRecord.the_boot_record.sysWriteLongIP, value, 2 /*just hex*/);
    else {
      System.err.print(Long.toHexString(value));
    }
  }

  /**
   * Low level print to console.
   * @param value	what is printed, as hex only
   */
  public static void writeHex(VM_Address value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
  //-#if RVM_FOR_64_ADDR
    writeHex(value.toLong()); 
  //-#else
    writeHex(value.toInt()); 
  //-#endif
  }

  public static void writeHex(VM_Offset value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
  //-#if RVM_FOR_64_ADDR
    writeHex(value.toLong()); 
  //-#else
    writeHex(value.toInt()); 
  //-#endif
  }

  /**
   * Low level print to console.
   * @param value	what is printed, as int only
   */
  public static void writeInt(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.call2(VM_BootRecord.the_boot_record.sysWriteIP, value, 0 /*just decimal*/);
    else {
      System.err.print(value);
    }
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void write(int value, boolean hexToo) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.call2(VM_BootRecord.the_boot_record.sysWriteIP, value, hexToo?1:0);
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   */
  public static void write(long value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(value, true);
  }
  
  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void write(long value, boolean hexToo) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      int val1, val2;
      val1 = (int)(value>>32);
      val2 = (int)(value & 0xFFFFFFFF);
      VM_SysCall.call3(VM_BootRecord.the_boot_record.sysWriteLongIP, val1, val2, hexToo?1:0);
    } else
      System.err.print(value);
  }


  public static void writeField(int fieldWidth, String s) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(s);
    int len = s.length();
    while (fieldWidth > len++) write(" ");
  }

  /**
   * Low level print to console.
   * @param value	print value and left-fill with enough spaces to print at least fieldWidth characters
   */
  public static void writeField(int fieldWidth, int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    int len = 1, temp = value;
    if (temp < 0) { len++; temp = -temp; }
    while (temp >= 10) { len++; temp /= 10; }
    while (fieldWidth > len++) write(" ");
    if (runningVM) 
      VM_SysCall.call2(VM_BootRecord.the_boot_record.sysWriteIP, value, 0);
    else 
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value	print value and left-fill with enough spaces to print at least fieldWidth characters
   */
  public static void writeField(int fieldWidth, VM_Atom s) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    int len = s.length();
    while (fieldWidth > len++) write(" ");
    write(s);
  }

  public static void writeln () {
    write('\n');
  }

  public static void write (double d) {
    write(d, 2);
  }

  public static void write (VM_Address addr) { 
    writeHex(addr);
  }

  public static void write (VM_Offset offset) { 
    writeHex(offset);
  }

  public static void write (VM_Word w) {
    writeHex(w.toAddress());
  }

  public static void write (boolean b) {
    write(b ? "true" : "false");
  }

  /**
   * A group of multi-argument sysWrites with optional newline.  Externally visible methods.
   */
  public static void sysWrite(VM_Atom a)               throws VM_PragmaNoInline { swLock(); write(a); swUnlock(); }
  public static void sysWriteln(VM_Atom a)             throws VM_PragmaNoInline { swLock(); write(a); write("\n"); swUnlock(); }
  public static void sysWrite(VM_Member m)             throws VM_PragmaNoInline { swLock(); write(m); swUnlock(); }
  public static void sysWrite(VM_MemberReference mr)   throws VM_PragmaNoInline { swLock(); write(mr); swUnlock(); }
  public static void sysWriteln ()                     throws VM_PragmaNoInline { swLock(); write("\n"); swUnlock(); }
  public static void sysWrite(char c)                  throws VM_PragmaNoInline { write(c); }
  public static void sysWriteField (int w, int v)      throws VM_PragmaNoInline { swLock(); writeField(w, v); swUnlock(); }
  public static void sysWriteField (int w, String s)   throws VM_PragmaNoInline { swLock(); writeField(w, s); swUnlock(); }
  public static void sysWriteHex(int v)                throws VM_PragmaNoInline { swLock(); writeHex(v); swUnlock(); }
  public static void sysWriteHex(VM_Address v)         throws VM_PragmaNoInline { swLock(); writeHex(v); swUnlock(); }
  public static void sysWriteInt(int v)                throws VM_PragmaNoInline { swLock(); writeInt(v); swUnlock(); }
  public static void sysWriteLong(long v)              throws VM_PragmaNoInline { swLock(); write(v,false); swUnlock(); }
  public static void sysWrite   (double d, int p)      throws VM_PragmaNoInline { swLock(); write(d, p); swUnlock(); }
  public static void sysWrite   (double d)             throws VM_PragmaNoInline { swLock(); write(d); swUnlock(); }
  public static void sysWrite   (String s)             throws VM_PragmaNoInline { swLock(); write(s); swUnlock(); }
  public static void sysWrite   (VM_Address a)         throws VM_PragmaNoInline { swLock(); write(a); swUnlock(); }
  public static void sysWriteln (VM_Address a)         throws VM_PragmaNoInline { swLock(); write(a); writeln(); swUnlock(); }
  public static void sysWrite   (VM_Offset o)          throws VM_PragmaNoInline { swLock(); write(o); swUnlock(); }
  public static void sysWriteln (VM_Offset o)         throws VM_PragmaNoInline { swLock(); write(o); writeln(); swUnlock(); }
  public static void sysWrite   (VM_Word w)            throws VM_PragmaNoInline { swLock(); write(w); swUnlock(); }
  public static void sysWrite   (boolean b)            throws VM_PragmaNoInline { swLock(); write(b); swUnlock(); }
  public static void sysWrite   (int i)                throws VM_PragmaNoInline { swLock(); write(i); swUnlock(); }
  public static void sysWriteln (int i)                throws VM_PragmaNoInline { swLock(); write(i);   writeln(); swUnlock(); }
  public static void sysWriteln (double d)             throws VM_PragmaNoInline { swLock(); write(d);   writeln(); swUnlock(); }
  public static void sysWriteln (long l)               throws VM_PragmaNoInline { swLock(); write(l);   writeln(); swUnlock(); }
  public static void sysWriteln (boolean b)            throws VM_PragmaNoInline { swLock(); write(b);   writeln(); swUnlock(); }
  public static void sysWriteln (String s)             throws VM_PragmaNoInline { swLock(); write(s);   writeln(); swUnlock(); }
  public static void sysWrite   (String s, int i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); swUnlock(); }
  public static void sysWriteln (String s, int i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); writeln(); swUnlock(); }
  public static void sysWrite   (String s, boolean b)       throws VM_PragmaNoInline { swLock(); write(s);   write(b); swUnlock(); }
  public static void sysWriteln (String s, boolean b)       throws VM_PragmaNoInline { swLock(); write(s);   write(b); writeln(); swUnlock(); }
  public static void sysWrite   (String s, double d)        throws VM_PragmaNoInline { swLock(); write(s);   write(d); swUnlock(); }
  public static void sysWriteln (String s, double d)        throws VM_PragmaNoInline { swLock(); write(s);   write(d); writeln(); swUnlock(); }
  public static void sysWrite   (double d, String s)        throws VM_PragmaNoInline { swLock(); write(d);   write(s); swUnlock(); }
  public static void sysWriteln (double d, String s)        throws VM_PragmaNoInline { swLock(); write(d);   write(s); writeln(); swUnlock(); }
  public static void sysWrite   (String s, long i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); swUnlock(); }
  public static void sysWriteln (String s, long i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); writeln(); swUnlock(); }
  public static void sysWrite   (int i, String s)           throws VM_PragmaNoInline { swLock(); write(i);   write(s); swUnlock(); }
  public static void sysWriteln (int i, String s)           throws VM_PragmaNoInline { swLock(); write(i);   write(s); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2)      throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); swUnlock(); }
  public static void sysWriteln (String s1, String s2)      throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s, VM_Address a)    throws VM_PragmaNoInline { swLock(); write(s);   write(a); swUnlock(); }
  public static void sysWriteln (String s, VM_Address a)    throws VM_PragmaNoInline { swLock(); write(s);   write(a); writeln(); swUnlock(); }
  public static void sysWrite   (String s, VM_Offset o)    throws VM_PragmaNoInline { swLock(); write(s);   write(o); swUnlock(); }
  public static void sysWriteln (String s, VM_Offset o)    throws VM_PragmaNoInline { swLock(); write(s);   write(o); writeln(); swUnlock(); }
  public static void sysWrite   (String s, VM_Word w)       throws VM_PragmaNoInline { swLock(); write(s);   write(w); swUnlock(); }
  public static void sysWriteln (String s, VM_Word w)       throws VM_PragmaNoInline { swLock(); write(s);   write(w); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, VM_Address a)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(a); swUnlock(); }
  public static void sysWriteln (String s1, String s2, VM_Address a)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(a); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, int i)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i); swUnlock(); }
  public static void sysWriteln (String s1, String s2, int i)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i, String s2)  throws VM_PragmaNoInline { swLock(); write(s1);  write(i);  write(s2); swUnlock(); }
  public static void sysWriteln (String s1, int i, String s2)  throws VM_PragmaNoInline { swLock(); write(s1);  write(i);  write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); writeln(); swUnlock(); }
  public static void sysWrite   (int i1, String s, int i2)     throws VM_PragmaNoInline { swLock(); write(i1);  write(s);  write(i2); swUnlock(); }
  public static void sysWriteln (int i1, String s, int i2)     throws VM_PragmaNoInline { swLock(); write(i1);  write(s);  write(i2); writeln(); swUnlock(); }
  public static void sysWrite   (int i1, String s1, String s2) throws VM_PragmaNoInline { swLock(); write(i1);  write(s1); write(s2); swUnlock(); }
  public static void sysWriteln (int i1, String s1, String s2) throws VM_PragmaNoInline { swLock(); write(i1);  write(s1); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i1, String s2, int i2) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(i2); swUnlock(); }
  public static void sysWriteln (String s1, int i1, String s2, int i2) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(i2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i1, String s2, long l1) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(  l1); swUnlock(); }
  public static void sysWriteln (String s1, int i1, String s2, long l1) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(l1); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, double d, String s2)        throws VM_PragmaNoInline { swLock(); write(s1);   write(d); write(s2); swUnlock(); }
  public static void sysWriteln (String s1, double d, String s2)        throws VM_PragmaNoInline { swLock(); write(s1);   write(d); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, int i1, String s3) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i1); write(  s3); swUnlock(); }
  public static void sysWriteln (String s1, String s2, int i1, String s3) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i1); write(s3); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3, int i1) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(  i1); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3, int i1) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(i1); writeln(); swUnlock(); }
  public static void sysWrite   (int i, String s1, double d, String s2) throws VM_PragmaNoInline { swLock(); write(i); write(s1);  write(d); write(s2); swUnlock(); }
  public static void sysWriteln (int i, String s1, double d, String s2) throws VM_PragmaNoInline { swLock(); write(i); write(s1);  write(d); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3, int i1, String s4) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(i1); write(  s4); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3, int i1, String s4) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(i1); write(s4); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, VM_Address a1, String s2, VM_Address a2) throws VM_PragmaNoInline { swLock(); write(s1);  write(a1); write(s2); write(a2); swUnlock(); }
  public static void sysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2) throws VM_PragmaNoInline { swLock(); write(s1);  write(a1); write(s2); write(a2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, VM_Address a, String s2, int i) throws VM_PragmaNoInline { swLock(); write(s1);  write(a); write(s2); write(i); swUnlock(); }
  public static void sysWriteln   (String s1, VM_Address a, String s2, int i) throws VM_PragmaNoInline { swLock(); write(s1);  write(a); write(s2); write(i); writeln(); swUnlock(); }

  private static void showProc() { 
    VM_Processor p = VM_Processor.getCurrentProcessor();
    write("Proc "); 
    write(p.id);
    write(": ");
  }

  private static void showThread() { 
    write("Thread "); 
    write(VM_Thread.getCurrentThread().getIndex());
    write(": ");
  }

  public static void ptsysWriteln (String s)             throws VM_PragmaNoInline { swLock(); showProc(); showThread(); write(s); writeln(); swUnlock(); }

  public static void psysWriteln (VM_Address a)         throws VM_PragmaNoInline { swLock(); showProc(); write(a); writeln(); swUnlock(); }
  public static void psysWriteln (String s)             throws VM_PragmaNoInline { swLock(); showProc(); write(s); writeln(); swUnlock(); }
  public static void psysWriteln (String s, int i)             throws VM_PragmaNoInline { swLock(); showProc(); write(s); write(i); writeln(); swUnlock(); }
  public static void psysWriteln (String s, VM_Address a)      throws VM_PragmaNoInline { swLock(); showProc(); write(s); write(a); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3, String s4, VM_Address a4) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); write (s4); write(a4); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3, String s4, VM_Address a4, String s5, VM_Address a5) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); write (s4); write(a4); write(s5); write(a5); writeln(); swUnlock(); }

  /**
   * Exit virtual machine due to internal failure of some sort.
   * @param message  error message describing the problem
   */
  public static void sysFail(String message) throws VM_PragmaNoInline {
    // print a traceback and die
    VM_Scheduler.traceback(message);
    VM.shutdown(1);
  }

  /**
   * Exit virtual machine.
   * @param value  value to pass to host o/s
   */
  public static void sysExit(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline {
    if (runningVM) {
      VM_Wait.disableIoWait(); // we can't depend on thread switching being enabled
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
  public static void shutdown(int value) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    if (VM.runningAsSubsystem) {
      // Terminate only the system threads that belong to the VM
      VM_Scheduler.processorExit(value);
    } else {
      VM_SysCall.call1(VM_BootRecord.the_boot_record.sysExitIP, value);
    }
  }

  /**
   * Create a virtual processor (aka "unix kernel thread", "pthread").
   * @param jtoc  register values to use for thread startup
   * @param pr
   * @param ti
   * @param fp
   * @return virtual processor's o/s handle
   */
  static int sysVirtualProcessorCreate(VM_Address jtoc, VM_Address pr, 
                                       int ti, VM_Address fp) {
    return VM_SysCall.call_I_A_A_I_A(VM_BootRecord.the_boot_record.sysVirtualProcessorCreateIP,
                    jtoc, pr, ti, fp);
  }

  /**
   * Bind execution of current virtual processor to specified physical cpu.
   * @param cpuId  physical cpu id (0, 1, 2, ...)
   */
  static void sysVirtualProcessorBind(int cpuId) {
    VM_SysCall.call1(VM_BootRecord.the_boot_record.sysVirtualProcessorBindIP, cpuId);
  }

  /**
   * Yield execution of current virtual processor back to o/s.
   */
  public static void sysVirtualProcessorYield() {
    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    return;
    //-#else
    VM_SysCall.call0(VM_BootRecord.the_boot_record.sysVirtualProcessorYieldIP);
    //-#endif
  }

  /**
   * Start interrupt generator for thread timeslicing.
   * The interrupt will be delivered to whatever virtual processor happens 
   * to be running when the timer expires.
   */
  static void sysVirtualProcessorEnableTimeSlicing(int timeSlice) {
    VM_SysCall.call1(VM_BootRecord.the_boot_record.sysVirtualProcessorEnableTimeSlicingIP, timeSlice);
  }

  //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  //-#else

  //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  //-#else
  static void sysCreateThreadSpecificDataKeys() {
      VM_SysCall.call0(VM_BootRecord.the_boot_record.sysCreateThreadSpecificDataKeysIP);
  }
  //-#endif

  static void sysWaitForVirtualProcessorInitialization() {
    VM_SysCall.call0(VM_BootRecord.the_boot_record.sysWaitForVirtualProcessorInitializationIP);
  }

  static void sysWaitForMultithreadingStart() {
    VM_SysCall.call0(VM_BootRecord.the_boot_record.sysWaitForMultithreadingStartIP);
  }

  static void sysInitializeStartupLocks(int howMany) {
    VM_SysCall.call1(VM_BootRecord.the_boot_record.sysInitializeStartupLocksIP, howMany);
  }
  //-#endif

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
    throws VM_PragmaInterruptible, ClassNotFoundException {
    // create dummy boot record
    //
    VM_BootRecord.the_boot_record = new VM_BootRecord();

    // initialize type subsystem and classloader
    VM_ClassLoader.init(vmClassPath);

    // initialize JNI environment
    VM_JNIEnvironment.init();

    // initialize remaining subsystems needed for compilation
    //
    VM_Entrypoints.init();
    VM_OutOfLineMachineCode.init();
    if (writingBootImage) // initialize compiler that builds boot image
      VM_BootImageCompiler.init(bootCompilerArgs);
    VM_Runtime.init();
    VM_Scheduler.init();
    VM_Interface.init();
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
  public static void disableGC() throws VM_PragmaInline, VM_PragmaInterruptible  { 
    // current (non-gc) thread is going to be holding raw addresses, therefore we must:
    //
    // 1. make sure we have enough stack space to run until gc is re-enabled
    //    (otherwise we might trigger a stack reallocation)
    //    (We can't resize the stack if there's a native frame, so don't
    //     do it and hope for the best)
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
    if (VM_Magic.getFramePointer().sub(STACK_SIZE_GCDISABLED).LT(myThread.stackLimit) && !myThread.hasNativeStackFrame()) {
      VM_Thread.resizeCurrentStack(myThread.stack.length + (STACK_SIZE_GCDISABLED >> 2), null);
    }

    // 2.
    //
    VM_Processor.getCurrentProcessor().disableThreadSwitching();

    // 3.
    //
    if (VM.VerifyAssertions) {
      VM._assert(myThread.disallowAllocationsByThisThread == false); // recursion not allowed
      myThread.disallowAllocationsByThisThread = true;
    }
  }

  /**
   * enable GC
   */
  public static void enableGC() throws VM_PragmaInline { 
    if (VM.VerifyAssertions) {
      VM_Thread myThread = VM_Thread.getCurrentThread();
      // recursion not allowed
      VM._assert(myThread.disallowAllocationsByThisThread == true); 
      myThread.disallowAllocationsByThisThread = false;
    }
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  /**
   * Place to set breakpoints (called by compiled code).
   */
  public static void debugBreakpoint() throws VM_PragmaNoInline, VM_PragmaNoOptCompile {
    // no inline to make sure it doesn't disappear from callee
    // no opt compile to force a full prologue sequence (easier for debugger to grok)
  }
}
