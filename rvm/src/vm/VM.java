/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

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
    throws VM_ResolutionException, VM_PragmaInterruptible {
    writingBootImage = true;
    init(classPath, bootCompilerArgs);
  }

  /**
   * Prepare vm classes for use by tools.
   * @exception VM_ResolutionException
   */
  public static void initForTool() throws VM_ResolutionException, VM_PragmaInterruptible  {
    runningTool = true;
    LoadLocalVariableTables = true;  // make sure to load the local table
    init(System.getProperty("java.class.path"), null);
  }

  /**
   * Prepare vm classes for use by tools.
   * @param classpath class path to be used by VM_ClassLoader
   * @exception VM_ResolutionException
   */
  public static void initForTool(String classpath) throws VM_ResolutionException, VM_PragmaInterruptible {
    runningTool = true;
    LoadLocalVariableTables = true;  // make sure to load the local table
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
        VM.sysCall0(VM_BootRecord.the_boot_record.sysPthreadSelfIP);

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
    //-#if RVM_WITH_GNU_CLASSPATH
    java.lang.ref.JikesRVMSupport.setReferenceLock(new VM_Synchronizer());
    //-#else
    runClassInitializer("java.io.FileDescriptor");
    runClassInitializer("java.io.File");
    //-#endif

    runClassInitializer("java.lang.Runtime");
    //-#if RVM_WITH_GNU_CLASSPATH
    runClassInitializer("java.io.FileDescriptor");
    runClassInitializer("java.lang.System");
    runClassInitializer("java.io.File");
    //-#else
    runClassInitializer("java.lang.System");
    System.boot();
    //-#endif
    runClassInitializer("java.lang.Boolean");
    runClassInitializer("java.lang.Byte");
    runClassInitializer("java.lang.Short");
    //-#if RVM_WITH_GNU_CLASSPATH
    runClassInitializer("java.lang.Number");
    //-#endif
    runClassInitializer("java.lang.Integer");
    runClassInitializer("java.lang.Long");
    runClassInitializer("java.lang.Float");
    runClassInitializer("java.lang.Character");
    //-#if !RVM_WITH_GNU_CLASSPATH
    runClassInitializer("com.ibm.oti.io.CharacterConverter");
    runClassInitializer("java.util.Hashtable");
    runClassInitializer("java.lang.String");
    //-#endif
    //-#if RVM_WITH_GNU_CLASSPATH
    runClassInitializer("gnu.java.io.EncodingManager");
    runClassInitializer("java.lang.Thread");
    runClassInitializer("java.lang.ThreadGroup");
    runClassInitializer("java.io.PrintWriter");
    runClassInitializer("gnu.java.lang.SystemClassLoader");
    runClassInitializer("java.lang.String");
    runClassInitializer("gnu.java.security.provider.DefaultPolicy");
    runClassInitializer("java.security.Policy");
    //-#endif
    runClassInitializer("java.lang.ClassLoader");
    runClassInitializer("com.ibm.JikesRVM.librarySupport.ReflectionSupport");
    runClassInitializer("java.lang.Math");
    //-#if !RVM_WITH_GNU_CLASSPATH
    runClassInitializer("java.lang.RuntimePermission");
    //-#endif
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
    //-#if RVM_WITH_GNU_CLASSPATH
    runClassInitializer("java.util.Date");
    //-#endif
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

    // Now that we've processed the command line arguments, 
    // enable HPM if the command lines indicate that we should
    VM_HardwarePerformanceMonitors.boot();

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

    VM_Lock.boot();
    
    // Enable multiprocessing.
    // Among other things, after this returns, GC and dynamic class loading are enabled.
    // 
    VM_Scheduler.boot();

    // Create JNI Environment for boot thread.  At this point the boot thread can invoke native methods.
    VM_Thread.getCurrentThread().initializeJNIEnv();

    // Run class intializers that require fully booted VM
    runClassInitializer("java.lang.Double");
    //-#if !RVM_WITH_GNU_CLASSPATH
    runClassInitializer("com.ibm.oti.util.Msg");
    //-#endif

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

    // Schedule "main" thread for execution.
    mainThread.start();

    // Create one debugger thread.
    VM_Thread t = new DebuggerThread();
    t.start(VM_Scheduler.debuggerQueue);

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
	if (verbose >= 2) VM.sysWriteln( classObjects[i].getName() );
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
    VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor, VM_SystemClassLoader.getVMClassLoader()).asClass();
    if (cls.isInBootImage()) {
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
   * Low level print to console.
   * @param value  what is printed
   */
  public static void sysWrite(VM_Atom value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    value.sysWrite();
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void sysWrite(VM_Member value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
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
  public static void sysWrite(String value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      for (int i = 0, n = value.length(); i < n; ++i) {
        sysWrite(value.charAt(i));
      }
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    } else {
      System.err.print(value);
    }
  }

  /**
    * Low level print to console.
   * @param value	what is printed
   */
  public static void sysWrite(char value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      sysCall1(VM_BootRecord.the_boot_record.sysWriteCharIP, value);
    else
      System.err.print(value);
  }


  /**
   * Low level print to console.  Can't pass doubles yet so just print to 2 decimal places.
   * @param value   double to be printed
   *
   */
  public static void sysWrite(double value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      int ones = (int) value;
      int hundredths = (int) (100.0 * (value - ones));
      sysWrite(ones, false); 
      sysWrite(".");
      if (hundredths < 10)
        sysWrite("0");
      sysWrite(hundredths, false);
    }
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value	what is printed
   */
  public static void sysWrite(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      int mode = (value < -(1<<20) || value > (1<<20)) ? 2 : 0; // hex only or decimal only
      sysCall2(VM_BootRecord.the_boot_record.sysWriteIP, value, mode);
    } else {
      System.err.print(value);
    }
  }


  public static void sysWriteField(int fieldWidth, String s) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    sysWrite(s);
    int len = s.length();
    while (fieldWidth > len++) sysWrite(" ");
  }

  /**
   * Low level print to console.
   * @param value	print value and left-fill with enough spaces to print at least fieldWidth characters
   */
  public static void sysWriteField(int fieldWidth, int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    int len = 1, temp = value;
    if (temp < 0) { len++; temp = -temp; }
    while (temp >= 10) { len++; temp /= 10; }
    while (fieldWidth > len++) sysWrite(" ");
    if (runningVM) 
      sysCall2(VM_BootRecord.the_boot_record.sysWriteIP, value, 0);
    else 
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value	print value and left-fill with enough spaces to print at least fieldWidth characters
   */
  public static void sysWriteField(int fieldWidth, VM_Atom s) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    int len = s.length();
    while (fieldWidth > len++) sysWrite(" ");
    sysWrite(s);
  }

  /**
   * Low level print to console.
   * @param value	what is printed, as hex only
   */
  public static void sysWriteHex(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      sysCall2(VM_BootRecord.the_boot_record.sysWriteIP, value, 2 /*just hex*/);
    else {
      System.err.print(Integer.toHexString(value));
    }
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void sysWrite(int value, boolean hexToo) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      sysCall2(VM_BootRecord.the_boot_record.sysWriteIP, value, hexToo?1:0);
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   */
  public static void sysWrite(long value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    sysWrite(value, true);
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void sysWrite(long value, boolean hexToo) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      int val1, val2;
      val1 = (int)(value>>32);
      val2 = (int)(value & 0xFFFFFFFF);
      sysCall3(VM_BootRecord.the_boot_record.sysWriteLongIP, val1, val2, hexToo?1:0);
    } else
      System.err.print(value);
  }

  /**
   * A group of multi-argument sysWrites with optional newline.
   */
  public static void sysWriteln ()                     throws VM_PragmaNoInline { sysWrite("\n"); }
  public static void sysWrite   (VM_Address addr)      throws VM_PragmaNoInline { sysWriteHex(addr.toInt()); }
  public static void sysWriteln (VM_Address addr)      throws VM_PragmaNoInline { sysWrite(addr); sysWriteln(); }
  public static void sysWrite   (VM_Word word)         throws VM_PragmaNoInline { sysWriteHex(word.toInt()); }
  public static void sysWriteln (int i)                throws VM_PragmaNoInline { sysWrite(i);   sysWriteln(); }
  public static void sysWriteln (double d)             throws VM_PragmaNoInline { sysWrite(d);   sysWriteln(); }
  public static void sysWriteln (long l)               throws VM_PragmaNoInline { sysWrite(l);   sysWriteln(); }
  public static void sysWriteln (String s)             throws VM_PragmaNoInline { sysWrite(s);   sysWriteln(); }
  public static void sysWrite   (String s, int i)           throws VM_PragmaNoInline { sysWrite(s);   sysWrite(i); }
  public static void sysWriteln (String s, int i)           throws VM_PragmaNoInline { sysWrite(s);   sysWriteln(i); }
  public static void sysWrite   (String s, double d)        throws VM_PragmaNoInline { sysWrite(s);   sysWrite(d); }
  public static void sysWriteln (String s, double d)        throws VM_PragmaNoInline { sysWrite(s);   sysWriteln(d); }
  public static void sysWrite   (String s, long i)           throws VM_PragmaNoInline { sysWrite(s);   sysWrite(i); }
  public static void sysWriteln (String s, long i)           throws VM_PragmaNoInline { sysWrite(s);   sysWriteln(i); }
  public static void sysWrite   (int i, String s)           throws VM_PragmaNoInline { sysWrite(i);   sysWrite(s); }
  public static void sysWriteln (int i, String s)           throws VM_PragmaNoInline { sysWrite(i);   sysWriteln(s); }
  public static void sysWrite   (String s1, String s2)      throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(s2); }
  public static void sysWriteln (String s1, String s2)      throws VM_PragmaNoInline { sysWrite(s1);  sysWriteln(s2); }
  public static void sysWrite   (String s, VM_Address addr) throws VM_PragmaNoInline { sysWrite(s);   sysWriteHex(addr.toInt()); }
  public static void sysWriteln (String s, VM_Address addr) throws VM_PragmaNoInline { sysWrite(s);   sysWriteHex(addr.toInt()); sysWriteln(); }
  public static void sysWrite   (String s, VM_Word word) throws VM_PragmaNoInline { sysWrite(s);   sysWriteHex(word.toInt()); }
  public static void sysWriteln (String s, VM_Word word) throws VM_PragmaNoInline { sysWrite(s);   sysWriteHex(word.toInt()); sysWriteln(); }
  public static void sysWrite   (String s1, String s2, VM_Address a)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(s2); sysWrite(a); }
  public static void sysWriteln (String s1, String s2, VM_Address a)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(s2); sysWriteln(a); }
  public static void sysWrite   (String s1, String s2, int i)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(s2); sysWrite(i); }
  public static void sysWriteln (String s1, String s2, int i)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(s2); sysWriteln(i); }
  public static void sysWrite   (String s1, int i, String s2)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(i);  sysWrite(s2); }
  public static void sysWriteln (String s1, int i, String s2)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(i);  sysWriteln(s2); }
  public static void sysWrite   (String s1, String s2, String s3)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(s2); sysWrite(s3); }
  public static void sysWriteln (String s1, String s2, String s3)  throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(s2); sysWriteln(s3); }
  public static void sysWrite   (int i1, String s, int i2)     throws VM_PragmaNoInline { sysWrite(i1);  sysWrite(s);  sysWrite(i2); }
  public static void sysWriteln (int i1, String s, int i2)     throws VM_PragmaNoInline { sysWrite(i1);  sysWrite(s);  sysWriteln(i2); }
  public static void sysWrite   (int i1, String s1, String s2) throws VM_PragmaNoInline { sysWrite(i1);  sysWrite(s1); sysWrite(s2); }
  public static void sysWriteln (int i1, String s1, String s2) throws VM_PragmaNoInline { sysWrite(i1);  sysWrite(s1); sysWriteln(s2); }
  public static void sysWrite   (String s1, int i1, String s2, int i2) throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(i1); sysWrite(s2); sysWrite(i2); }
  public static void sysWriteln (String s1, int i1, String s2, int i2) throws VM_PragmaNoInline { sysWrite(s1);  sysWrite(i1); sysWrite(s2); sysWriteln(i2); }

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
    // SJF: I don't want this method inlined, since I use it as a
    // breakpoint for the jdp regression test.
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
      sysCall1(VM_BootRecord.the_boot_record.sysExitIP, value);
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
    return sysCall4(VM_BootRecord.the_boot_record.sysVirtualProcessorCreateIP,
                    jtoc.toInt(), pr.toInt(), ti, fp.toInt());
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
  public static void sysVirtualProcessorYield() {
    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    return;
    //-#else
    sysCall0(VM_BootRecord.the_boot_record.sysVirtualProcessorYieldIP);
    //-#endif
  }

  /**
   * Start interrupt generator for thread timeslicing.
   * The interrupt will be delivered to whatever virtual processor happens 
   * to be running when the timer expires.
   */
  static void sysVirtualProcessorEnableTimeSlicing(int timeSlice) {
    sysCall1(VM_BootRecord.the_boot_record.sysVirtualProcessorEnableTimeSlicingIP, timeSlice);
  }

  //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
  //-#else

  //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  //-#else
  static void sysCreateThreadSpecificDataKeys() {
      sysCall0(VM_BootRecord.the_boot_record.sysCreateThreadSpecificDataKeysIP);
  }
  //-#endif

  static void sysWaitForVirtualProcessorInitialization() {
    sysCall0(VM_BootRecord.the_boot_record.sysWaitForVirtualProcessorInitializationIP);
  }

  static void sysWaitForMultithreadingStart() {
    sysCall0(VM_BootRecord.the_boot_record.sysWaitForMultithreadingStartIP);
  }

  static void sysInitializeStartupLocks(int howMany) {
    sysCall1(VM_BootRecord.the_boot_record.sysInitializeStartupLocksIP, howMany);
  }
  //-#endif

  //-#if RVM_FOR_POWERPC
  /**
   * Make calls to host operating system services.
   * @param ip address of a function in sys.C 
   * @return integer value returned by function in sys.C
   */
  public static int sysCall0(int ip) throws VM_PragmaInline {
    return VM_Magic.sysCall0(ip, VM_BootRecord.the_boot_record.sysTOC);
  }

  /**
   * sysCall1
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return integer value returned by function in sys.C
   */
  public static int sysCall1(int ip, int p1) throws VM_PragmaInline {
    return VM_Magic.sysCall1(ip, VM_BootRecord.the_boot_record.sysTOC, p1);
  }

  /**
   * sysCall2
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall2(int ip, int p1, int p2) throws VM_PragmaInline {
    return  VM_Magic.sysCall2(ip, VM_BootRecord.the_boot_record.sysTOC, p1, p2);
  }

  /**
   * sysCall3
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @param p3
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall3(int ip, int p1, int p2, int p3) throws VM_PragmaInline {
    return  VM_Magic.sysCall3(ip, VM_BootRecord.the_boot_record.sysTOC, p1, p2, p3);
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
  public static int sysCall4(int ip, int p1, int p2, int p3, int p4) throws VM_PragmaInline {
    return VM_Magic.sysCall4(ip, VM_BootRecord.the_boot_record.sysTOC, p1, p2, p3, p4);
  }

  /**
   * sysCall_L_0
   * @param ip  address of a function in sys.C 
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_0(int ip) throws VM_PragmaInline {
    return VM_Magic.sysCall_L_0(ip, VM_BootRecord.the_boot_record.sysTOC);
  }

  /**
   * sysCall_L_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_I(int ip, int p1) throws VM_PragmaInline {
    return VM_Magic.sysCall_L_I(ip, VM_BootRecord.the_boot_record.sysTOC, p1);
  }

  /**
   * sysCallAD
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCallAD(int ip, int p1, double p2) throws VM_PragmaInline {
    return  VM_Magic.sysCallAD(ip, VM_BootRecord.the_boot_record.sysTOC, p1, p2);
  }

  //-#endif
  //-#if RVM_FOR_IA32
  /**
   * sysCall0
   * @param ip  address of a function in sys.C 
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall0(int ip) throws VM_PragmaInline {
    return  VM_Magic.sysCall0(ip);
  }

  /**
   * sysCall1
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall1(int ip, int p1) throws VM_PragmaInline {
    return  VM_Magic.sysCall1(ip, p1);
  }

  /**
   * sysCall2
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCall2(int ip, int p1, int p2) throws VM_PragmaInline {
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
  public static int sysCall3(int ip, int p1, int p2, int p3) throws VM_PragmaInline {
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
  public static int sysCall4(int ip, int p1, int p2, int p3, int p4) throws VM_PragmaInline {
    return VM_Magic.sysCall4(ip, p1, p2, p3, p4);
  }

  /**
   * sysCall_L_0
   * @param ip  address of a function in sys.C 
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_0(int ip) throws VM_PragmaInline {
    return VM_Magic.sysCall_L_0(ip);
  }

  /**
   * sysCall_L_I
   * @param ip  address of a function in sys.C 
   * @param p1
   * @return long value returned by function in sys.C
   */
  public static long sysCall_L_I(int ip, int p1) throws VM_PragmaInline {
    return  VM_Magic.sysCall_L_I(ip, p1);
  }

  /**
   * sysCallAD
   * @param ip  address of a function in sys.C 
   * @param p1
   * @param p2
   * @return  integer value returned by function in sys.C
   */
  public static int sysCallAD(int ip, int p1, double p2) throws VM_PragmaInline {
    return VM_Magic.sysCallAD(ip, p1, p2);
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
    throws VM_ResolutionException, VM_PragmaInterruptible {
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
      VM_ClassLoader.init(vmClassPath);
      VM_Class object       = VM_Type.JavaLangObjectType.asClass();
      VM_Class string       = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Ljava/lang/String;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
      VM_Class stringBuffer = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Ljava/lang/StringBuffer;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
      VM_Class vm           = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
      VM_Class runtime      = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Runtime;"), VM_SystemClassLoader.getVMClassLoader()).asClass();

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

      // VM_Scheduler.dumpStack();

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
  public static void debugBreakpoint() throws VM_PragmaNoInline {
    // the following forces this method to have a prologue.
    // In general, jdp cannot set breakpoints in opt methods that
    // have no prologues.
    VM_Magic.pragmaNoOptCompile();
  }
}
