/*
 * (C) Copyright IBM Corp 2001, 2002, 2003, 2004, 2005
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;
import java.lang.ref.Reference;
//-#if RVM_WITH_QUICK_COMPILER
import com.ibm.JikesRVM.quick.*;
//-#endif

/**
 * A virtual machine.
 *
 * @author Derek Lieber (project start).
 * @date 21 Nov 1997 
 *
 * @modified Steven Augart (to catch recursive shutdowns, 
 *                          such as when out of memory)
 * @date 10 July 2003
 */
public class VM extends VM_Properties 
  implements VM_Constants, VM_ExitStatus, Uninterruptible
{ 
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
    throws InterruptiblePragma 
  {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    if (VM.VerifyAssertions) VM._assert(!VM.runningTool);
    writingBootImage = true;
    init(classPath, bootCompilerArgs);
  }

  /**
   * Prepare vm classes for use by tools.
   */
  public static void initForTool() 
    throws InterruptiblePragma 
  {
    initForTool(System.getProperty("java.class.path"));
  }

  /**
   * Prepare vm classes for use by tools.
   * @param classpath class path to be used by VM_ClassLoader
   */
  public static void initForTool(String classpath) 
    throws InterruptiblePragma 
  {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    if (VM.VerifyAssertions) VM._assert(!VM.writingBootImage);
    runningTool = true;
    init(classpath, null);
  }

  /**
   * Begin vm execution.
   * Uninterruptible because we are not setup to execute a yieldpoint
   * or stackoverflow check in the prologue this early in booting.
   * 
   * The following machine registers are set by "C" bootstrap program 
   * before calling this method:
   *    JTOC_POINTER        - required for accessing globals
   *    FRAME_POINTER       - required for accessing locals
   *    THREAD_ID_REGISTER  - required for method prolog (stack overflow check)
   * @exception Exception
   */
  public static void boot() throws Exception, UninterruptibleNoWarnPragma {
    writingBootImage = false;
    runningVM        = true;
    runningAsSubsystem = false;
    verboseBoot = VM_BootRecord.the_boot_record.verboseBoot;
    singleVirtualProcessor = (VM_BootRecord.the_boot_record.singleVirtualProcessor != 0);
    
    sysWriteLockOffset = VM_Entrypoints.sysWriteLockField.getOffset();
    if (verboseBoot >= 1) VM.sysWriteln("Booting");

    // Set up the current VM_Processor object.  The bootstrap program
    // has placed a pointer to the current VM_Processor in a special
    // register.
    if (verboseBoot >= 1) VM.sysWriteln("Setting up current VM_Processor");
    VM_ProcessorLocalState.boot();

    // Finish thread initialization that couldn't be done in boot image.
    // The "stackLimit" must be set before any interruptible methods are called
    // because it's accessed by compiler-generated stack overflow checks.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Doing thread initialization");
    VM_Thread currentThread = VM_Processor.getCurrentProcessor().activeThread;
    currentThread.stackLimit = VM_Magic.objectAsAddress(currentThread.stack).add(STACK_SIZE_GUARD);
    currentThread.isBootThread = true;
    
    VM_Processor.getCurrentProcessor().activeThreadStackLimit = currentThread.stackLimit;
    currentThread.startQuantum(VM_Time.cycles());

    finishBooting();
  }

  /**
   * Complete the task of booting Jikes RVM.
   * Done in a secondary method mainly because this code
   * doesn't have to be uninterruptible and this is the cleanest
   * way to make that distinction.
   */
  private static void finishBooting() throws InterruptiblePragma {
    
    // get pthread_id from OS and store into vm_processor field
    // 
    if (!singleVirtualProcessor) {
      VM_SysCall.sysPthreadSetupSignalHandling();
      VM_Processor.getCurrentProcessor().pthread_id = 
        VM_SysCall.sysPthreadSelf();
    }

    // Set up buffer locks used by VM_Thread for logging and status dumping.
    //    This can happen at any point before we start running
    //    multi-threaded.  
    VM_Thread.boot();

    // Initialize memory manager's virtual processor local state.
    // This must happen before any putfield or arraystore of object refs
    // because the buffer is accessed by compiler-generated write barrier code.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Setting up write barrier");
    MM_Interface.setupProcessor(VM_Processor.getCurrentProcessor());
    
    // Initialize memory manager.
    //    This must happen before any uses of "new".
    //
    if (verboseBoot >= 1) VM.sysWriteln("Setting up memory manager: bootrecord = ", VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record));
    MM_Interface.boot(VM_BootRecord.the_boot_record);
    
    // Start calculation of cycles to millsecond conversion factor
    if (verboseBoot >= 1) VM.sysWriteln("Stage one of booting VM_Time");
    VM_Time.bootStageOne();

    // Reset the options for the baseline compiler to avoid carrying 
    // them over from bootimage writing time.
    // 
    if (verboseBoot >= 1) VM.sysWriteln("Initializing baseline compiler options to defaults");
    VM_BaselineCompiler.initOptions();

    //-#if RVM_WITH_QUICK_COMPILER
    // Reset the options for the quick compiler to avoid carrying 
    // them over from bootimage writing time.
    // 
    if (verboseBoot >= 1) VM.sysWriteln("Initializing quick compiler options to defaults");
    VM_QuickCompiler.initOptions();
    //-#endif

    // Create class objects for static synchronized methods in the bootimage.
    // This must happen before any static synchronized methods of bootimage 
    // classes can be invoked.
    if (verboseBoot >= 1) VM.sysWriteln("Creating class objects for static synchronized methods");
    createClassObjects();

    // Fetch arguments from program command line.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Fetching command-line arguments");
    VM_CommandLineArgs.fetchCommandLineArguments();

    // Process most virtual machine command line arguments.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Early stage processing of command line");
    VM_CommandLineArgs.earlyProcessCommandLineArguments();

    // Allow Memory Manager to respond to its command line arguments
    //
    if (verboseBoot >= 1) VM.sysWriteln("Collector processing rest of boot options");
    MM_Interface.postBoot();

    // Initialize class loader.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Initializing bootstrap class loader");
    String bootstrapClasses = VM_CommandLineArgs.getBootstrapClasses();
    VM_ClassLoader.boot();      // Wipe out cached application class loader
    VM_BootstrapClassLoader.boot(bootstrapClasses);
    VM_ApplicationClassLoader2.boot(".");

    // Complete calculation of cycles to millsecond conversion factor
    // Must be done before any dynamic compilation occurs.
    if (verboseBoot >= 1) VM.sysWriteln("Stage two of booting VM_Time");
    VM_Time.bootStageTwo();

    // Initialize statics that couldn't be placed in bootimage, either 
    // because they refer to external state (open files), or because they 
    // appear in fields that are unique to Jikes RVM implementation of 
    // standard class library (not part of standard jdk).
    // We discover the latter by observing "host has no field" and 
    // "object not part of bootimage" messages printed out by bootimage 
    // writer.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Running various class initializers");
    //-#if RVM_WITH_CLASSPATH_0_10 || RVM_WITH_CLASSPATH_0_11 || RVM_WITH_CLASSPATH_0_12
    //-#else
    runClassInitializer("gnu.classpath.SystemProperties"); // only in 0.13 and later
    //-#endif
    
    runClassInitializer("java.lang.Runtime");
    //-#if RVM_WITH_CLASSPATH_0_12
    // Classpath 0.12
    java.lang.JikesRVMSupport.javaLangSystemEarlyInitializers();
    //-#else  // 0.10, 0.11, 0.13, and post-0.13
    // In 0.10 and 0.11, requires ClassLoader and ApplicationClassLoader
    runClassInitializer("java.lang.System"); 
    //-#endif
    runClassInitializer("java.lang.Void");
    runClassInitializer("java.lang.Boolean");
    runClassInitializer("java.lang.Byte");
    runClassInitializer("java.lang.Short");
    runClassInitializer("java.lang.Number");
    runClassInitializer("java.lang.Integer");
    runClassInitializer("java.lang.Long");
    runClassInitializer("java.lang.Float");
    runClassInitializer("java.lang.Character");
    runClassInitializer("java.util.WeakHashMap"); // Need for ThreadLocal
    // Turn off security checks; about to hit EncodingManager.
    // Commented out because we haven't incorporated this into the CVS head
    // yet. 
    // java.security.JikesRVMSupport.turnOffChecks();
    runClassInitializer("java.lang.Thread");
    runClassInitializer("java.lang.ThreadGroup");

    /* We can safely allocate a java.lang.Thread now.  The boot
       thread (running right now, as a VM_Thread) has to become a full-fledged
       Thread, since we're about to encounter a security check:

       EncodingManager checks a system property, 
        which means that the permissions checks have to be working,
        which means that VMAccessController will be invoked,
        which means that ThreadLocal.get() will be called,
        which calls Thread.getCurrentThread().

        So the boot VM_Thread needs to be associated with a real Thread for
        Thread.getCurrentThread() to return. */
    VM.safeToAllocateJavaThread = true;
    VM_Scheduler.giveBootVM_ThreadAJavaLangThread();

    runClassInitializer("java.lang.ThreadLocal");
    // Possibly fix VMAccessController's contexts and inGetContext fields
    runClassInitializer("java.security.VMAccessController");
    

    runClassInitializer("java.io.File"); // needed for when we initialize the
                                         // system/application class loader.
    runClassInitializer("gnu.java.lang.SystemClassLoader");
    runClassInitializer("java.lang.String");
    runClassInitializer("java.lang.VMString");
    runClassInitializer("gnu.java.security.provider.DefaultPolicy");
    runClassInitializer("java.security.Policy");
    runClassInitializer("java.net.URL"); // needed for URLClassLoader
    /* Needed for ApplicationClassLoader, which in turn is needed by
       VMClassLoader.getSystemClassLoader()  */
    runClassInitializer("java.net.URLClassLoader"); 

    /* Used if we start up Jikes RVM with the -jar argument; that argument
     * means that we need a working -jar before we can return an
     * Application Class Loader. */
    runClassInitializer("gnu.java.net.protocol.jar.Connection$JarFileCache");

    // Calls System.getProperty().  However, the only thing it uses the
    // property for is to set the default protection domain, and THAT is only
    // used in defineClass.  So, since we haven't needed to call defineClass
    // up to this point, we are OK deferring its proper re-initialization.
    runClassInitializer("java.lang.ClassLoader"); 

    /* Here, we lie and pretend to have a working app class loader, since we
       need it in order to run other initializers properly! */
    //-#if RVM_WITH_CLASSPATH_0_10 || RVM_WITH_CLASSPATH_0_11
    //-#elif RVM_WITH_CLASSPATH_0_12
    java.lang.JikesRVMSupport.javaLangSystemLateInitializers();
    //-#else
    //    RVM_WITH_CLASSPATH_0_13 || RVM_WITH_CLASSPATH_CVS_HEAD
    /** This one absolutely requires that we have a working Application/System
        class loader, or at least a returnable one.  That, in turn, requires
        lots of things be set up for Jar.  */
    runClassInitializer("java.lang.ClassLoader$StaticData");
    //-#endif

    runClassInitializer("gnu.java.io.EncodingManager"); // uses System.getProperty
    runClassInitializer("java.io.PrintWriter"); // Uses System.getProperty
    runClassInitializer("java.lang.Math"); /* Load in the javalang library, so
                                              that Math's native trig functions
                                              work.  Still can't use them
                                              until JNI is set up. */ 
    runClassInitializer("java.util.SimpleTimeZone");
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

    if (verboseBoot >= 1) VM.sysWriteln("Booting VM_Lock");
    VM_Lock.boot();
    
    // set up HPM
    //-#if RVM_WITH_HPM
    if (BuildForHPM) {
      if (VM_HardwarePerformanceMonitors.enabled()) {
        // assume only one Java thread is executing!
        if(VM_HardwarePerformanceMonitors.verbose>=2)
          VM.sysWriteln("VM.boot() call VM_HardwarePerformanceMonitors.boot()");
        VM_HardwarePerformanceMonitors.boot();
      }
    }
    //-#endif

    // Enable multiprocessing.
    // Among other things, after this returns, GC and dynamic class loading are enabled.
    // 
    if (verboseBoot >= 1) VM.sysWriteln("Booting scheduler");
    VM_Scheduler.boot();

    VM.dynamicClassLoadingEnabled = true;
    // Create JNI Environment for boot thread.  
    // After this point the boot thread can invoke native methods.
    com.ibm.JikesRVM.jni.VM_JNIEnvironment.boot();
    if (verboseBoot >= 1) VM.sysWriteln("Initializing JNI for boot thread");
    VM_Thread.getCurrentThread().initializeJNIEnv();

    //-#if RVM_WITH_HPM
    runClassInitializer("com.ibm.JikesRVM.Java2HPM");
    VM_HardwarePerformanceMonitors.setUpHPMinfo();
    //-#endif

    // Run class intializers that require JNI
    if (verboseBoot >= 1) VM.sysWriteln("Running late class initializers");
    runClassInitializer("gnu.java.nio.channels.FileChannelImpl");
    runClassInitializer("java.io.FileDescriptor");
    runClassInitializer("java.lang.Double");
    runClassInitializer("java.util.PropertyPermission");
    runClassInitializer("com.ibm.JikesRVM.VM_Process");
    runClassInitializer("java.io.VMFile"); // Load libjavaio.so

    // Initialize java.lang.System.out, java.lang.System.err, java.lang.System.in
    VM_FileSystem.initializeStandardStreams();

    ///////////////////////////////////////////////////////////////
    // The VM is now fully booted.                               //
    // By this we mean that we can execute arbitrary Java code.  //
    ///////////////////////////////////////////////////////////////
    if (verboseBoot >= 1) VM.sysWriteln("VM is now fully booted");
    
    // Inform interested subsystems that VM is fully booted.
    VM.fullyBooted = true;
    MM_Interface.fullyBootedVM();
    VM_BaselineCompiler.fullyBootedVM();

    //-#if RVM_WITH_QUICK_COMPILER
    VM_QuickCompiler.fullyBootedVM();
    //-#endif

    // Allow profile information to be read in from a file
    // 
    VM_EdgeCounts.boot();

    // Initialize compiler that compiles dynamically loaded classes.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Initializing runtime compiler");
    VM_RuntimeCompiler.boot();

    // Process remainder of the VM's command line arguments.
    if (verboseBoot >= 1) VM.sysWriteln("Late stage processing of command line");
    String[] applicationArguments = VM_CommandLineArgs.lateProcessCommandLineArguments();

    if (VM.verboseClassLoading || verboseBoot >= 1) VM.sysWrite("[VM booted]\n");

    // set up JikesRVM socket I/O
    if (verboseBoot >= 1) VM.sysWriteln("Initializing socket factories");
    JikesRVMSocketImpl.boot();

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    if (verboseBoot >= 1) VM.sysWriteln("Initializing adaptive system");
    com.ibm.JikesRVM.adaptive.VM_Controller.boot();
    //-#endif


    // The first argument must be a class name.
    if (verboseBoot >= 1) VM.sysWriteln("Extracting name of class to execute");
    if (applicationArguments.length == 0) {
      pleaseSpecifyAClass();
    }
    if (applicationArguments.length > 0 && 
        ! VM_TypeDescriptorParsing.isJavaClassName(applicationArguments[0])) {
      VM.sysWrite("vm: \"");
      VM.sysWrite(applicationArguments[0]);
      VM.sysWrite("\" is not a legal Java class name.\n");
      pleaseSpecifyAClass();
    }


    if (verboseBoot >= 1) VM.sysWriteln("Initializing Application Class Loader");
    VM_ClassLoader.getApplicationClassLoader();
    VM_ClassLoader.declareApplicationClassLoaderIsReady();

    if (verboseBoot >= 1) VM.sysWriteln("Turning back on security checks.  Letting people see the ApplicationClassLoader.");
    // Turn on security checks again.
    // Commented out because we haven't incorporated this into the main CVS
    // tree yet. 
    // java.security.JikesRVMSupport.fullyBootedVM();

    //-#if RVM_WITH_CLASSPATH_0_10 || RVM_WITH_CLASSPATH_0_11
    //-#elif RVM_WITH_CLASSPATH_0_12
    java.lang.JikesRVMSupport.javaLangSystemLateInitializers();
    //-#else
    //    RVM_WITH_CLASSPATH_0_13 || RVM_WITH_CLASSPATH_CVS_HEAD
    /** This one absolutely requires that we have a working Application/System
        class loader, or at least a returnable one.  That, in turn, requires
        lots of things be set up for Jar.  */
    runClassInitializer("java.lang.ClassLoader$StaticData");
    //-#endif

    // Schedule "main" thread for execution.
    if (verboseBoot >= 2) VM.sysWriteln("Creating main thread");
    // Create main thread.
    if (verboseBoot >= 1) VM.sysWriteln("Constructing mainThread");
    Thread mainThread = new MainThread(applicationArguments);

    // Schedule "main" thread for execution.
    if (verboseBoot >= 1) VM.sysWriteln("Starting main thread");
    mainThread.start();

    if (verboseBoot >= 1) VM.sysWriteln("Starting debugger thread");
    // Create one debugger thread.
    VM_Thread t = new DebuggerThread();
    t.start(VM_Scheduler.debuggerQueue);

    //-#if RVM_WITH_HPM
    if (VM_HardwarePerformanceMonitors.enabled()) {
      // IS THIS NEEDED?
      if (!VM_HardwarePerformanceMonitors.thread_group) {
        if(VM_HardwarePerformanceMonitors.verbose>=2)
          VM.sysWrite(" VM.boot() call sysHPMresetMyThread()\n");
        VM_SysCall.sysHPMresetMyThread();
      }
    }
    //-#endif

    // End of boot thread.
    //
    if (VM.TraceThreads) VM_Scheduler.trace("VM.boot", "completed - terminating");
    if (verboseBoot >= 2) 
      VM.sysWriteln("Boot sequence completed; finishing boot thread");
    
    VM_Thread.terminate();
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  private static void pleaseSpecifyAClass() throws InterruptiblePragma {
    VM.sysWrite("vm: Please specify a class to execute.\n");
    VM.sysWrite("vm:   You can invoke the VM with the \"-help\" flag for usage information.\n");
    VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }


  private static VM_Class[] classObjects = new VM_Class[0];
  /**
   * Called by the compilers when compiling a static synchronized method
   * during bootimage writing.
   */
  public static void deferClassObjectCreation(VM_Class c) throws InterruptiblePragma {
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
  private static void createClassObjects() throws InterruptiblePragma {
    for (int i=0; i<classObjects.length; i++) {
      if (verboseBoot >= 2) {
        VM.sysWriteln(classObjects[i].toString()); 
      }
      classObjects[i].getClassForType();
    }
  }

  /**
   * Run <clinit> method of specified class, if that class appears 
   * in bootimage and actually has a clinit method (we are flexible to
   * allow one list of classes to work with different bootimages and 
   * different version of classpath (eg 0.05 vs. cvs head).
   * 
   * This method is called only while the VM boots.
   * 
   * @param className
   */
  static void runClassInitializer(String className) throws InterruptiblePragma {
    if (verboseBoot >= 2) {
      sysWrite("running class intializer for ");
      sysWriteln(className);
    }
    VM_Atom  classDescriptor = 
      VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(), classDescriptor);
    VM_Class cls = (VM_Class)tRef.peekResolvedType();
    if (cls != null && cls.isInBootImage()) {
      VM_Method clinit = cls.getClassInitializerMethod();
      if (clinit != null) {
        clinit.compile();
        if (verboseBoot >= 10) VM.sysWriteln("invoking method " + clinit);
        try {
          VM_Magic.invokeClassInitializer(clinit.getCurrentInstructions());
        } catch (Error e) {
          throw e;
        } catch (Throwable t) {
          ExceptionInInitializerError eieio
            = new ExceptionInInitializerError("Caught exception while invoking the class initializer for "
                                              +  className);
          eieio.initCause(t);
          throw eieio;
        }
        // <clinit> is no longer needed: reclaim space by removing references to it
        clinit.invalidateCompiledMethod(clinit.getCurrentCompiledMethod());
      } else {
        if (verboseBoot >= 10) VM.sysWriteln("has no clinit method ");
      } 
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
    _assert(b, null, null);
  }

  /**
   * Verify a runtime assertion (die w/message and traceback if 
   * assertion fails).   Note: code your assertion checks as 
   * "if (VM.VerifyAssertions) VM._assert(xxx,yyy);"
   * @param b the assertion to verify
   * @param message the message to print if the assertion is false
   */
  public static void _assert(boolean b, String message) {
    _assert(b, message, null);
  }

  public static void _assert(boolean b, String msg1, String msg2) {
    if (!VM.VerifyAssertions) {
      sysWriteln("vm: somebody forgot to conditionalize their call to assert with");
      sysWriteln("vm: if (VM.VerifyAssertions)");
      _assertionFailure("vm internal error: assert called when !VM.VerifyAssertions", null);
    }
    if (!b) _assertionFailure(msg1, msg2);
  }




  private static void _assertionFailure(String msg1, String msg2) 
    throws UninterruptibleNoWarnPragma, NoInlinePragma 
  {
    if (msg1 == null && msg2 == null)
      msg1 = "vm internal error at:";
    if (msg2 == null) {
      msg2 = msg1;
      msg1 = null;
    }
    if (VM.runningVM) {
      if (msg1 != null) {
        sysWrite(msg1);
      }
      sysFail(msg2);
    }
    throw new RuntimeException((msg1 != null ? msg1 : "") + msg2);
  }


  /**
   * Format a 32 bit number as "0x" followed by 8 hex digits.
   * Do this without referencing Integer or Character classes, 
   * in order to avoid dynamic linking.
   * TODO: move this method to VM_Services.
   * @param number
   * @return a String with the hex representation of the integer
   */
  public static String intAsHexString(int number) throws InterruptiblePragma {
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


  //-#if RVM_WITH_QUICK_COMPILER
  /**
   * Format a 64 bit number as "0x" followed by 16 hex digits.
   * Do this without referencing Long or Character classes, 
   * in order to avoid dynamic linking.
   * TODO: move this method to VM_Services.
   * @param number
   * @return a String with the hex representation of the long
   */
  public static String longAsHexString(long number) throws InterruptiblePragma {
    char[] buf   = new char[18];
    int    index = 18;
    while (--index > 1) {
      int digit = (int)(number & 0x000000000000000fL);
      buf[index] = digit <= 9 ? (char)('0' + digit) : (char)('a' + digit - 10);
      number >>= 4;
    }
    buf[index--] = 'x';
    buf[index]   = '0';
    return new String(buf);
  }
  //-#endif


  /**
   * Format a 32/64 bit number as "0x" followed by 8/16 hex digits.
   * Do this without referencing Integer or Character classes, 
   * in order to avoid dynamic linking.
   * TODO: move this method to VM_Services.
   * @param addr  The 32/64 bit number to format.
   * @return a String with the hex representation of an Address
   */
  public static String addressAsHexString(Address addr) throws InterruptiblePragma {
    int len = 2 + (BITS_IN_ADDRESS>>2);
    char[] buf   = new char[len];
    while (--len > 1) {
      int digit = addr.toInt() & 0x0F;
      buf[len] = digit <= 9 ? (char)('0' + digit) : (char)('a' + digit - 10);
      addr = addr.toWord().rshl(4).toAddress();
    }
    buf[len--] = 'x';
    buf[len]   = '0';
    return new String(buf);
  }

  private static int sysWriteLock = 0;
  private static Offset sysWriteLockOffset = Offset.max();

  private static void swLock() {
    if (sysWriteLockOffset.isMax()) return;
    while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), sysWriteLockOffset, 1)) 
      ;
  }

  private static void swUnlock() {
    if (sysWriteLockOffset.isMax()) return;
    VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), sysWriteLockOffset, 0);
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_Atom value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    value.sysWrite();
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_Member value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    write(value.getMemberRef());
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_MemberReference value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
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
  public static void write(String value) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (value == null) {
      write("null");
    } else {
      if (runningVM) {
        char[] chars = java.lang.JikesRVMSupport.getBackingCharArray(value);
        int numChars = java.lang.JikesRVMSupport.getStringLength(value);
        int offset = java.lang.JikesRVMSupport.getStringOffset(value);
        for (int i = 0; i<numChars; i++) 
          write(chars[offset+i]);
      } else {
        System.err.print(value);
      }
    }
  }

  /**
   * Low level print to console.
   * @param value character array that is printed
   * @param len number of characters printed
   */
  public static void write(char[] value, int len) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    for (int i = 0, n = len; i < n; ++i) {
      if (runningVM)
        /*  Avoid triggering a potential read barrier
         *
         *  TODO: Convert this to use org.mmtk.vm.Barriers.getArrayNoBarrier
         */  
        write(VM_Magic.getCharAtOffset(value, Offset.fromIntZeroExtend(i << LOG_BYTES_IN_CHAR)));
      else
        write(value[i]);
    }
  }

  /**
    * Low level print of a <code>char</code>to console.
   * @param value       The character to print
   */
  public static void write(char value) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWriteChar(value);
    else
      System.err.print(value);
  }


  /**
   * Low level print of <code>double</code> to console.
   *
   * @param value               <code>double</code> to be printed
   * @param postDecimalDigits   Number of decimal places
   */
  public static void write(double value, int postDecimalDigits) 
    throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWriteDouble(value, postDecimalDigits);
    else
      System.err.print(value);
  }

  /**
   * Low level print of an <code>int</code> to console.
   * @param value       what is printed
   */
  public static void write(int value) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      int mode = (value < -(1<<20) || value > (1<<20)) ? 2 : 0; // hex only or decimal only
      VM_SysCall.sysWrite(value, mode);
    } else {
      System.err.print(value);
    }
  }

  /**
   * Low level print to console.
   * @param value       What is printed, as hex only
   */
  public static void writeHex(int value) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWrite(value, 2 /*just hex*/);
    else {
      System.err.print(Integer.toHexString(value));
    }
  }

  /**
   * Low level print to console.
   * @param value       what is printed, as hex only
   */
  public static void writeHex(long value) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM){
      VM_SysCall.sysWriteLong(value, 2);
    } else {
      System.err.print(Long.toHexString(value));
    }
  }

  public static void writeDec(Word value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    //-#if RVM_FOR_64_ADDR
    write(value.toLong()); 
    //-#else
    write(value.toInt()); 
    //-#endif
  }

  public static void writeHex(Word value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    //-#if RVM_FOR_64_ADDR
    writeHex(value.toLong()); 
    //-#else
    writeHex(value.toInt()); 
    //-#endif
  }

  public static void writeHex(Address value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    writeHex(value.toWord()); 
  }

  public static void writeHex(ObjectReference value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    writeHex(value.toAddress().toWord());
  }

  public static void writeHex(Extent value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    writeHex(value.toWord()); 
  }

  public static void writeHex(Offset value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    writeHex(value.toWord()); 
  }

  /**
   * Low level print to console.
   * @param value       what is printed, as int only
   */
  public static void writeInt(int value) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWrite(value, 0 /*just decimal*/);
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
  public static void write(int value, boolean hexToo) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWrite(value, hexToo?1:0);
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   */
  public static void write(long value) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
    write(value, true);
  }
  
  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void write(long value, boolean hexToo) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    if (runningVM) 
      VM_SysCall.sysWriteLong(value, hexToo?1:0);
    else
      System.err.print(value);
  }


  public static void writeField(int fieldWidth, String s) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    write(s);
    int len = s.length();
    while (fieldWidth > len++) write(" ");
  }

  /**
   * Low level print to console.
   * @param value       print value and left-fill with enough spaces to print at least fieldWidth characters
   */
  public static void writeField(int fieldWidth, int value) throws LogicallyUninterruptiblePragma, NoInlinePragma /* don't waste code space inlining these --dave */ {
    int len = 1, temp = value;
    if (temp < 0) { len++; temp = -temp; }
    while (temp >= 10) { len++; temp /= 10; }
    while (fieldWidth > len++) write(" ");
    if (runningVM) 
      VM_SysCall.sysWrite(value, 0);
    else 
      System.err.print(value);
  }

  /**
   * Low level print of the {@link VM_Atom} <code>s</code> to the console.  
   * Left-fill with enough spaces to print at least <code>fieldWidth</code>
   * characters  
   * @param fieldWidth  Minimum width to print.
   * @param s       The {@link VM_Atom} to print.       
   */
  public static void writeField(int fieldWidth, VM_Atom s) throws NoInlinePragma /* don't waste code space inlining these --dave */ {
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

  public static void write (Word addr) { 
    writeHex(addr);
  }

  public static void write (Address addr) { 
    writeHex(addr);
  }

  public static void write (ObjectReference object) { 
    writeHex(object);
  }

  public static void write (Offset addr) { 
    writeHex(addr);
  }

  public static void write (Extent addr) { 
    writeHex(addr);
  }

  public static void write (boolean b) {
    write(b ? "true" : "false");
  }

  /**
   * A group of multi-argument sysWrites with optional newline.  Externally visible methods.
   */
  public static void sysWrite(VM_Atom a)               throws NoInlinePragma { swLock(); write(a); swUnlock(); }
  public static void sysWriteln(VM_Atom a)             throws NoInlinePragma { swLock(); write(a); write("\n"); swUnlock(); }
  public static void sysWrite(VM_Member m)             throws NoInlinePragma { swLock(); write(m); swUnlock(); }
  public static void sysWrite(VM_MemberReference mr)   throws NoInlinePragma { swLock(); write(mr); swUnlock(); }
  public static void sysWriteln ()                     throws NoInlinePragma { swLock(); write("\n"); swUnlock(); }
  public static void sysWrite(char c)                  throws NoInlinePragma { write(c); }
  public static void sysWriteField (int w, int v)      throws NoInlinePragma { swLock(); writeField(w, v); swUnlock(); }
  public static void sysWriteField (int w, String s)   throws NoInlinePragma { swLock(); writeField(w, s); swUnlock(); }
  public static void sysWriteHex(int v)                throws NoInlinePragma { swLock(); writeHex(v); swUnlock(); }
  public static void sysWriteHex(long v)               throws NoInlinePragma { swLock(); writeHex(v); swUnlock(); }
  public static void sysWriteHex(Address v)         throws NoInlinePragma { swLock(); writeHex(v); swUnlock(); }
  public static void sysWriteInt(int v)                throws NoInlinePragma { swLock(); writeInt(v); swUnlock(); }
  public static void sysWriteLong(long v)              throws NoInlinePragma { swLock(); write(v,false); swUnlock(); }
  public static void sysWrite   (double d, int p)      throws NoInlinePragma { swLock(); write(d, p); swUnlock(); }
  public static void sysWrite   (double d)             throws NoInlinePragma { swLock(); write(d); swUnlock(); }
  public static void sysWrite   (String s)             throws NoInlinePragma { swLock(); write(s); swUnlock(); }
  public static void sysWrite   (char [] c, int l)     throws NoInlinePragma { swLock(); write(c, l); swUnlock(); }
  public static void sysWrite   (Address a)         throws NoInlinePragma { swLock(); write(a); swUnlock(); }
  public static void sysWriteln (Address a)         throws NoInlinePragma { swLock(); write(a); writeln(); swUnlock(); }
  public static void sysWrite   (ObjectReference o) throws NoInlinePragma { swLock(); write(o); swUnlock(); }
  public static void sysWriteln (ObjectReference o)         throws NoInlinePragma { swLock(); write(o); writeln(); swUnlock(); }
  public static void sysWrite   (Offset o)          throws NoInlinePragma { swLock(); write(o); swUnlock(); }
  public static void sysWriteln (Offset o)          throws NoInlinePragma { swLock(); write(o); writeln(); swUnlock(); }
  public static void sysWrite   (Word w)            throws NoInlinePragma { swLock(); write(w); swUnlock(); }
  public static void sysWriteln (Word w)            throws NoInlinePragma { swLock(); write(w); writeln(); swUnlock(); }
  public static void sysWrite   (Extent e)          throws NoInlinePragma { swLock(); write(e); swUnlock(); }
  public static void sysWriteln (Extent e)          throws NoInlinePragma { swLock(); write(e); writeln(); swUnlock(); }
  public static void sysWrite   (boolean b)            throws NoInlinePragma { swLock(); write(b); swUnlock(); }
  public static void sysWrite   (int i)                throws NoInlinePragma { swLock(); write(i); swUnlock(); }
  public static void sysWriteln (int i)                throws NoInlinePragma { swLock(); write(i);   writeln(); swUnlock(); }
  public static void sysWriteln (double d)             throws NoInlinePragma { swLock(); write(d);   writeln(); swUnlock(); }
  public static void sysWriteln (long l)               throws NoInlinePragma { swLock(); write(l);   writeln(); swUnlock(); }
  public static void sysWriteln (boolean b)            throws NoInlinePragma { swLock(); write(b);   writeln(); swUnlock(); }
  public static void sysWriteln (String s)             throws NoInlinePragma { swLock(); write(s);   writeln(); swUnlock(); }
  public static void sysWrite   (String s, int i)           throws NoInlinePragma { swLock(); write(s);   write(i); swUnlock(); }
  public static void sysWriteln (String s, int i)           throws NoInlinePragma { swLock(); write(s);   write(i); writeln(); swUnlock(); }
  public static void sysWrite   (String s, boolean b)       throws NoInlinePragma { swLock(); write(s);   write(b); swUnlock(); }
  public static void sysWriteln (String s, boolean b)       throws NoInlinePragma { swLock(); write(s);   write(b); writeln(); swUnlock(); }
  public static void sysWrite   (String s, double d)        throws NoInlinePragma { swLock(); write(s);   write(d); swUnlock(); }
  public static void sysWriteln (String s, double d)        throws NoInlinePragma { swLock(); write(s);   write(d); writeln(); swUnlock(); }
  public static void sysWrite   (double d, String s)        throws NoInlinePragma { swLock(); write(d);   write(s); swUnlock(); }
  public static void sysWriteln (double d, String s)        throws NoInlinePragma { swLock(); write(d);   write(s); writeln(); swUnlock(); }
  public static void sysWrite   (String s, long i)           throws NoInlinePragma { swLock(); write(s);   write(i); swUnlock(); }
  public static void sysWriteln (String s, long i)           throws NoInlinePragma { swLock(); write(s);   write(i); writeln(); swUnlock(); }
  public static void sysWrite   (int i, String s)           throws NoInlinePragma { swLock(); write(i);   write(s); swUnlock(); }
  public static void sysWriteln (int i, String s)           throws NoInlinePragma { swLock(); write(i);   write(s); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2)      throws NoInlinePragma { swLock(); write(s1);  write(s2); swUnlock(); }
  public static void sysWriteln (String s1, String s2)      throws NoInlinePragma { swLock(); write(s1);  write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s, Address a)    throws NoInlinePragma { swLock(); write(s);   write(a); swUnlock(); }
  public static void sysWriteln (String s, Address a)    throws NoInlinePragma { swLock(); write(s);   write(a); writeln(); swUnlock(); }
  public static void sysWrite (String s, ObjectReference r)    throws NoInlinePragma { swLock(); write(s);   write(r); swUnlock(); }
  public static void sysWriteln (String s, ObjectReference r)    throws NoInlinePragma { swLock(); write(s);   write(r); writeln(); swUnlock(); }
  public static void sysWrite   (String s, Offset o)    throws NoInlinePragma { swLock(); write(s);   write(o); swUnlock(); }
  public static void sysWriteln (String s, Offset o)    throws NoInlinePragma { swLock(); write(s);   write(o); writeln(); swUnlock(); }
  public static void sysWrite   (String s, Word w)       throws NoInlinePragma { swLock(); write(s);   write(w); swUnlock(); }
  public static void sysWriteln (String s, Word w)       throws NoInlinePragma { swLock(); write(s);   write(w); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, Address a)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(a); swUnlock(); }
  public static void sysWriteln (String s1, String s2, Address a)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(a); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, int i)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(i); swUnlock(); }
  public static void sysWriteln (String s1, String s2, int i)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(i); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i, String s2)  throws NoInlinePragma { swLock(); write(s1);  write(i);  write(s2); swUnlock(); }
  public static void sysWriteln (String s1, int i, String s2)  throws NoInlinePragma { swLock(); write(s1);  write(i);  write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, Offset o, String s2)  throws NoInlinePragma { swLock(); write(s1);  write(o);  write(s2); swUnlock(); }
  public static void sysWriteln (String s1, Offset o, String s2)  throws NoInlinePragma { swLock(); write(s1);  write(o);  write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); writeln(); swUnlock(); }
  public static void sysWrite   (int i1, String s, int i2)     throws NoInlinePragma { swLock(); write(i1);  write(s);  write(i2); swUnlock(); }
  public static void sysWriteln (int i1, String s, int i2)     throws NoInlinePragma { swLock(); write(i1);  write(s);  write(i2); writeln(); swUnlock(); }
  public static void sysWrite   (int i1, String s1, String s2) throws NoInlinePragma { swLock(); write(i1);  write(s1); write(s2); swUnlock(); }
  public static void sysWriteln (int i1, String s1, String s2) throws NoInlinePragma { swLock(); write(i1);  write(s1); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3,String s4)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(s4); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3,String s4)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(s4); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3,String s4, String s5)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(s4); write(s5); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3,String s4, String s5)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(s4); write(s5); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i1, String s2, int i2) throws NoInlinePragma { swLock(); write(s1);  write(i1); write(s2); write(i2); swUnlock(); }
  public static void sysWriteln (String s1, int i1, String s2, int i2) throws NoInlinePragma { swLock(); write(s1);  write(i1); write(s2); write(i2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i1, String s2, long l1) throws NoInlinePragma { swLock(); write(s1);  write(i1); write(s2); write(  l1); swUnlock(); }
  public static void sysWriteln (String s1, int i1, String s2, long l1) throws NoInlinePragma { swLock(); write(s1);  write(i1); write(s2); write(l1); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, Offset o, String s2, int i) throws NoInlinePragma { swLock(); write(s1);  write(o); write(s2); write(i); swUnlock(); }
  public static void sysWriteln (String s1, Offset o, String s2, int i) throws NoInlinePragma { swLock(); write(s1);  write(o); write(s2); write(i); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, double d, String s2)        throws NoInlinePragma { swLock(); write(s1);   write(d); write(s2); swUnlock(); }
  public static void sysWriteln (String s1, double d, String s2)        throws NoInlinePragma { swLock(); write(s1);   write(d); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, int i1, String s3) throws NoInlinePragma { swLock(); write(s1);  write(s2); write(i1); write(  s3); swUnlock(); }
  public static void sysWriteln (String s1, String s2, int i1, String s3) throws NoInlinePragma { swLock(); write(s1);  write(s2); write(i1); write(s3); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3, int i1) throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(  i1); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3, int i1) throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(i1); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3,String s4, int i5, String s6)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(s4); write(i5); write(s6); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3,String s4, int i5, String s6)  throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(s4); write(i5); write(s6); writeln(); swUnlock(); }
  public static void sysWrite   (int i, String s1, double d, String s2) throws NoInlinePragma { swLock(); write(i); write(s1);  write(d); write(s2); swUnlock(); }
  public static void sysWriteln (int i, String s1, double d, String s2) throws NoInlinePragma { swLock(); write(i); write(s1);  write(d); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3, int i1, String s4) throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(i1); write(  s4); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3, int i1, String s4) throws NoInlinePragma { swLock(); write(s1);  write(s2); write(s3); write(i1); write(s4); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, Address a1, String s2, Address a2) throws NoInlinePragma { swLock(); write(s1);  write(a1); write(s2); write(a2); swUnlock(); }
  public static void sysWriteln   (String s1, Address a1, String s2, Address a2) throws NoInlinePragma { swLock(); write(s1);  write(a1); write(s2); write(a2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, Address a, String s2, int i) throws NoInlinePragma { swLock(); write(s1);  write(a); write(s2); write(i); swUnlock(); }
  public static void sysWriteln   (String s1, Address a, String s2, int i) throws NoInlinePragma { swLock(); write(s1);  write(a); write(s2); write(i); writeln(); swUnlock(); }

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

  public static void ptsysWriteln (String s)             throws NoInlinePragma { swLock(); showProc(); showThread(); write(s); writeln(); swUnlock(); }

  public static void ptsysWriteln(String s1, String s2, String s3, int i4, String s5, String s6)             throws NoInlinePragma { swLock(); showProc(); showThread(); write(s1); write(s2); write(s3); write(i4); write(s5); write(s6); writeln(); swUnlock(); }

  public static void ptsysWriteln(String s1, String s2, String s3, String s4, String s5, String s6, String s7, int i8, String s9, String s10, String s11, String s12, String s13)             throws NoInlinePragma { swLock(); showProc(); showThread(); write(s1); write(s2); write(s3); write(s4); write(s5); write(s6); write(s7); write(i8); write(s9); write(s10); write(s11); write(s12); write(s13); writeln(); swUnlock(); }

  public static void ptsysWriteln(String s1, String s2, String s3, String s4, String s5, String s6, String s7, int i8, String s9, String s10, String s11, String s12, String s13, int i14)             throws NoInlinePragma { swLock(); showProc(); showThread(); write(s1); write(s2); write(s3); write(s4); write(s5); write(s6); write(s7); write(i8); write(s9); write(s10); write(s11); write(s12); write(s13); write(i14); writeln(); swUnlock(); }
  
  public static void psysWrite    (char [] c, int l)     throws NoInlinePragma { swLock(); showProc(); write(c, l); swUnlock(); }

  public static void psysWriteln (Address a)         throws NoInlinePragma { swLock(); showProc(); write(a); writeln(); swUnlock(); }
  public static void psysWriteln (String s)             throws NoInlinePragma { swLock(); showProc(); write(s); writeln(); swUnlock(); }
  public static void psysWriteln (String s, int i)             throws NoInlinePragma { swLock(); showProc(); write(s); write(i); writeln(); swUnlock(); }
  public static void psysWriteln (String s, Address a)      throws NoInlinePragma { swLock(); showProc(); write(s); write(a); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, Address a1, String s2, Address a2) throws NoInlinePragma { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, Address a1, String s2, Address a2, String s3, Address a3) throws NoInlinePragma { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, Address a1, String s2, Address a2, String s3, Address a3, String s4, Address a4) throws NoInlinePragma { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); write (s4); write(a4); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, Address a1, String s2, Address a2, String s3, Address a3, String s4, Address a4, String s5, Address a5) throws NoInlinePragma { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); write (s4); write(a4); write(s5); write(a5); writeln(); swUnlock(); }
  

  /**
   * Exit virtual machine due to internal failure of some sort.
   * @param message  error message describing the problem
   */
  public static void sysFail(String message) throws NoInlinePragma {
    handlePossibleRecursiveCallToSysFail(message);

    // print a traceback and die
    VM_Scheduler.traceback(message);
    if (VM.runningVM)
      VM.shutdown(EXIT_STATUS_SYSFAIL);
    else
      VM.sysExit(EXIT_STATUS_SYSFAIL);
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Exit virtual machine due to internal failure of some sort.  This
   * two-argument form is  needed for us to call before the VM's Integer class
   * is initialized.   
   *
   * @param message  error message describing the problem
   * @param number  an integer to append to <code>message</code>.  
   */
  public static void sysFail(String message, int number) 
    throws NoInlinePragma 
  {
    handlePossibleRecursiveCallToSysFail(message, number);

    // print a traceback and die
    VM_Scheduler.traceback(message, number);
    if (VM.runningVM)
      VM.shutdown(EXIT_STATUS_SYSFAIL);
    else
      VM.sysExit(EXIT_STATUS_SYSFAIL);
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

//   /* This could be made public. */
//   private static boolean alreadyShuttingDown() {
//     return (inSysExit != 0) || (inShutdown != 0);
//   }

  public static boolean debugOOM = false; // debug out-of-memory exception. DEBUG
  public static boolean doEmergencyGrowHeap = !debugOOM; // DEBUG
  
  /**
   * Exit virtual machine.
   * @param value  value to pass to host o/s
   */
  public static void sysExit(int value) throws LogicallyUninterruptiblePragma, NoInlinePragma {
    handlePossibleRecursiveCallToSysExit();
    if (debugOOM) {
      sysWriteln("entered VM.sysExit(", value, ")");
    }
    if (VM_Options.stackTraceAtExit) {
      VM.sysWriteln("[Here is the context of the call to VM.sysExit(", value,
                    ")...:");
      VM.disableGC();
      VM_Scheduler.dumpStack();
      VM.enableGC();
      VM.sysWriteln("... END context of the call to VM.sysExit]");

    }

    if (runningVM) {
      VM_Wait.disableIoWait(); // we can't depend on thread switching being enabled
      VM_Callbacks.notifyExit(value);
      VM.shutdown(value);
    } else {
      System.exit(value);
    }
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Shut down the virtual machine.
   * Should only be called if the VM is running.
   * @param value  exit value
   */
  public static void shutdown(int value) {
    handlePossibleRecursiveShutdown();
    
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    if (VM.runningAsSubsystem) {
      // Terminate only the system threads that belong to the VM
      VM_Scheduler.processorExit(value);
    } else {
      VM_SysCall.sysExit(value);
    }
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  private static int inSysFail = 0;
  private static void handlePossibleRecursiveCallToSysFail(String message) {
    handlePossibleRecursiveExit("sysFail", ++inSysFail, message);
  }

  private static void handlePossibleRecursiveCallToSysFail(String message, 
                                                           int number) 
  {
    handlePossibleRecursiveExit("sysFail", ++inSysFail, message, number);
  }


  private static int inSysExit = 0;
  private static void handlePossibleRecursiveCallToSysExit() {
    handlePossibleRecursiveExit("sysExit", ++inSysExit);
  }

  private static int inShutdown = 0;
  /** Used only by VM.shutdown() */
  private static void handlePossibleRecursiveShutdown() {
    handlePossibleRecursiveExit("shutdown", ++inShutdown);
  }
  
  private static void handlePossibleRecursiveExit(String called, int depth) {
    handlePossibleRecursiveExit(called, depth, null);
  }

  private static void handlePossibleRecursiveExit(String called, int depth, 
                                                  String message) {
    handlePossibleRecursiveExit(called, depth, message, false, -9999999);
  }

  private static void handlePossibleRecursiveExit(String called, int depth, 
                                                  String message, int number) {
    handlePossibleRecursiveExit(called, depth, message, true, number);
  }


  /** @param called Name of the function called: "sysExit", "sysFail", or
   *    "shutdown".
   * @param depth How deep are we in that function?
   * @param message What message did it have?  null means this particular
   *    shutdown function  does not come with a message.
   * @param showNumber Print <code>number</code> following
   *    <code>message</code>? 
   * @param number Print this number, if <code>showNumber</code> is true. */
  private static void handlePossibleRecursiveExit(String called, int depth, 
                                                  String message,
                                                  boolean showNumber,
                                                  int number) 
  {
    /* We adjust up by nProcessorAdjust since we do not want to prematurely
       abort.  Consider the case where VM_Scheduler.numProcessors is greater
       than maxSystemTroubleRecursionDepth.  (This actually happened.)

       Possible change: Instead of adjusting by the # of processors, make the
       "depth" variable a per-processor variable. */
    int nProcessors = VM_Scheduler.numProcessors;
    int nProcessorAdjust = nProcessors - 1;
    if (depth > 1
        && (depth <= maxSystemTroubleRecursionDepth 
            + nProcessorAdjust
            + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite)) 
    {
      if (showNumber) {
        ptsysWriteln("VM.", called, "(): We're in a",
                     depth > nProcessors ? "n (unambiguously)" : " (likely)",
                     " recursive call to VM.", called, "(), ", depth, 
                     " deep\n",
                     message == null ? "" : "   ", 
                     message == null ? "" : called, 
                     message == null ? "": " was called with the message: ", 
                     message == null ? "" : message, 
                     number);
      } else {
        ptsysWriteln("VM.", called, "(): We're in a",
                     depth > nProcessors ? "n (unambiguously)" : " (likely)",
                     " recursive call to VM.", called, "(), ", depth, 
                     " deep\n",
                     message == null ? "" : "   ", 
                     message == null ? "" : called, 
                     message == null ? "": " was called with the message: ", 
                     message == null ? "" : message);
      }
    }
    if (depth > maxSystemTroubleRecursionDepth + nProcessorAdjust) {
      dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }



  /** Have we already called dieAbruptlyRecursiveSystemTrouble()?  
      Only for use if we're recursively shutting down!  Used by
      dieAbruptlyRecursiveSystemTrouble() only.  */

  private static boolean inDieAbruptlyRecursiveSystemTrouble = false;

  public static void dieAbruptlyRecursiveSystemTrouble() {
    if (! inDieAbruptlyRecursiveSystemTrouble) {
      inDieAbruptlyRecursiveSystemTrouble = true;
      sysWriteln("VM.dieAbruptlyRecursiveSystemTrouble(): Dying abruptly",
                    "; we're stuck in a recursive shutdown/exit.");
    }
    /* Emergency death. */
    VM_SysCall.sysExit(EXIT_STATUS_RECURSIVELY_SHUTTING_DOWN);
    /* And if THAT fails, go into an inf. loop.  Ugly, but it's better than
       returning from this function and leading to yet more cascading errors.
       and misleading error messages.   (To the best of my knowledge, we have
       never yet reached this point.)  */
    while (true)
      ;
  }


  /**
   * Yield execution of current virtual processor back to o/s.
   */
  public static void sysVirtualProcessorYield() {
    if (!VM_Properties.singleVirtualProcessor)
      VM_SysCall.sysVirtualProcessorYield();
  }

  //----------------//
  // implementation //
  //----------------//

  /**
   * Create class instances needed for boot image or initialize classes 
   * needed by tools.
   * @param bootstrapClasspath places where vm implemention class reside
   * @param bootCompilerArgs command line arguments to pass along to the 
   *                         boot compiler's init routine.
   */
  private static void init(String bootstrapClasspath, String[] bootCompilerArgs) 
    throws InterruptiblePragma
  {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);

    // create dummy boot record
    //
    VM_BootRecord.the_boot_record = new VM_BootRecord();

    // initialize type subsystem and classloader
    VM_ClassLoader.init(bootstrapClasspath);

    // initialize remaining subsystems needed for compilation
    //
    VM_OutOfLineMachineCode.init();
    if (writingBootImage) // initialize compiler that builds boot image
      VM_BootImageCompiler.init(bootCompilerArgs);
    VM_Runtime.init();
    VM_Scheduler.init();
    MM_Interface.init();
  }

  /**
   * The disableGC() and enableGC() methods are for use as guards to protect
   * code that must deal with raw object addresses in a collection-safe manner
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
  public static void disableGC()
    throws InlinePragma, InterruptiblePragma  
  {
    disableGC(false);           // Recursion is not allowed in this context.
  }
  
  /**
   * disableGC: Disable GC if it hasn't already been disabled.  This
   * enforces a stack discipline; we need it for the JNI Get*Critical and
   * Release*Critical functions.  Should be matched with a subsequent call to
   * enableGC().
   */
  public static void disableGC(boolean recursiveOK) 
    throws InlinePragma, InterruptiblePragma  
  {
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

    // 0. Sanity Check; recursion
    if (VM.VerifyAssertions) VM._assert(myThread.disableGCDepth >= 0);
    if (myThread.disableGCDepth++ > 0)
      return;                   // We've already disabled it.

    // 1.
    //
    if (VM_Magic.getFramePointer().sub(STACK_SIZE_GCDISABLED)
        .LT(myThread.stackLimit) 
        && !myThread.hasNativeStackFrame()) 
      {
      VM_Thread.resizeCurrentStack(myThread.stack.length + STACK_SIZE_GCDISABLED, null);
    }

    // 2.
    //
    VM_Processor.getCurrentProcessor().disableThreadSwitching();

    // 3.
    //
    if (VM.VerifyAssertions) {
      if (!recursiveOK)
        VM._assert(myThread.disallowAllocationsByThisThread == false); // recursion not allowed
      myThread.disallowAllocationsByThisThread = true;
    }
  }

  /**
   * enable GC; entry point when recursion is not OK.
   */
  public static void enableGC() throws InlinePragma { 
    enableGC(false);            // recursion not OK.
  }

  
  /**
   * enableGC(): Re-Enable GC if we're popping off the last
   * possibly-recursive {@link #disableGC} request.  This enforces a stack discipline;
   * we need it for the JNI Get*Critical and Release*Critical functions.
   * Should be matched with a preceding call to {@link #disableGC}.
   */
  public static void enableGC(boolean recursiveOK) 
    throws InlinePragma 
  { 
    VM_Thread myThread = VM_Thread.getCurrentThread();
    if (VM.VerifyAssertions) {
      VM._assert(myThread.disableGCDepth >= 1);
      VM._assert(myThread.disallowAllocationsByThisThread == true); 
    }
    --myThread.disableGCDepth;
    if (myThread.disableGCDepth > 0)
      return;
    
    // Now the actual work of re-enabling GC.
    myThread.disallowAllocationsByThisThread = false;
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  /**
   * Place to set breakpoints (called by compiled code).
   */
  public static void debugBreakpoint() throws NoInlinePragma, NoOptCompilePragma {
    // no inline to make sure it doesn't disappear from callee
    // no opt compile to force a full prologue sequence (easier for debugger to grok)
  }
}
