/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

//-#if RVM_WITH_ADAPTIVE_SYSTEM
import com.ibm.JikesRVM.adaptive.VM_Controller;
import com.ibm.JikesRVM.opt.*;
//-#endif

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;

/**
 * Command line option processing.
 *
 * @author Peter Sweeney
 * @modified Igor Pechtchanski
 *      Arbitrary prefix support
 */
public class VM_CommandLineArgs { 
  private static final boolean DEBUG = false;

  private static final class Prefix {
    public String value;
    public int type;
    public Prefix(String v, int t) { value = v; type = t; }
    public int count = 0; // number of arguments of that type
  }

  // ---------------//
  // Argument types //
  // ---------------//

  /**
   * Invalid argument type
   */
  public static final int INVALID_ARG      =  0; // default
  /**
   * Application argument
   */
  public static final int APPLICATION_ARG  =  1;
  /**
   * First user argument
   */
  public static final int FIRST_USER_ARG   =  2;

  // -----------------------------------------------//
  // The following arguments are standard java.     //
  // -----------------------------------------------//
  public static final int CLASSPATH_ARG        =  2;
  public static final int ENVIRONMENT_ARG      =  3;
  public static final int VERBOSE_JNI_ARG      =  5;
  public static final int VERBOSE_CLS_ARG      =  6;
  public static final int JAR_ARG = 27;

  // -----------------------------------------------//
  // The following arguments are RVM-specific.      //
  // -----------------------------------------------//
  public static final int VM_CLASSES_ARG       =  7;
  public static final int CPUAFFINITY_ARG      =  8;
  public static final int PROCESSORS_ARG       =  9;
  public static final int VM_HELP_ARG          = 10;
  public static final int VM_ARG               = 11;
  public static final int IRC_HELP_ARG         = 12;
  public static final int IRC_ARG              = 13;
  public static final int RECOMP_HELP_ARG      = 14;
  public static final int RECOMP_ARG           = 15;
  public static final int AOS_HELP_ARG         = 16;
  public static final int AOS_ARG              = 17;
  public static final int BASE_HELP_ARG        = 18;
  public static final int BASE_ARG             = 19;
  public static final int OPT_ARG              = 20;
  public static final int OPT_HELP_ARG         = 21;
  public static final int VERIFY_ARG           = 22;
  public static final int GC_HELP_ARG          = 23;
  public static final int GC_ARG               = 24;
  public static final int HPM_HELP_ARG         = 25;
  public static final int HPM_ARG              = 26;

  /**
   * A catch-all prefix to find application name.
   */
  private static final Prefix app_prefix = new Prefix("", APPLICATION_ARG);

  /**
   * A list of possible prefixes for command line arguments.
   * Each argument will be classified by the prefix it matches.
   * One prefix can contain another.
   *
   * Prefixes are normally matched with the start of the argument.
   * If the last character of the prefix is a '$', the prefix (without the
   * trailing '$') will be matched with the whole argument.
   * If the last character of the prefix is a ' ' (space), the prefix
   * (without the trailing ' ') will be matched with the whole argument,
   * and the next argument will be appended to the end of the one matching
   * the prefix, with a space in between.
   *
   * The type will be used to classify the prefix.  Multiple entries CAN
   * have the same type.
   */
  private static final Prefix[] prefixes = {
    new Prefix("-classpath ",           CLASSPATH_ARG),  // Note: space is significant
    new Prefix("-cp ",                  CLASSPATH_ARG),  // Note: space is significant
    new Prefix("-jar ",                 JAR_ARG),  // Note: space is significant
    new Prefix("-D",                    ENVIRONMENT_ARG),
    new Prefix("-verbose:class$",       VERBOSE_CLS_ARG),
    new Prefix("-verbose:jni$",         VERBOSE_JNI_ARG),
    new Prefix("-verbose$",             VERBOSE_CLS_ARG),
    new Prefix("-X:vmClasses=",         VM_CLASSES_ARG),
    new Prefix("-X:cpuAffinity=",       CPUAFFINITY_ARG),
    new Prefix("-X:processors=",        PROCESSORS_ARG),
    new Prefix("-X:irc:help$",          IRC_HELP_ARG),
    new Prefix("-X:irc$",               IRC_HELP_ARG),
    new Prefix("-X:irc:",               IRC_ARG),
    new Prefix("-X:recomp:help$",       RECOMP_HELP_ARG),
    new Prefix("-X:recomp$",            RECOMP_HELP_ARG),
    new Prefix("-X:recomp",             RECOMP_ARG),
    new Prefix("-X:aos:help$",          AOS_HELP_ARG),
    new Prefix("-X:aos$",               AOS_HELP_ARG),
    new Prefix("-X:aos:",               AOS_ARG),
    new Prefix("-X:gc:help$",           GC_HELP_ARG),
    new Prefix("-X:gc$",                GC_HELP_ARG),
    new Prefix("-X:gc:",                GC_ARG),
    new Prefix("-X:base:help$",         BASE_HELP_ARG),
    new Prefix("-X:base$",              BASE_HELP_ARG),
    new Prefix("-X:base:",              BASE_ARG),
    new Prefix("-X:opt:help$",          OPT_HELP_ARG),
    new Prefix("-X:opt$",               OPT_HELP_ARG),
    new Prefix("-X:opt:",               OPT_ARG),
    new Prefix("-X:hpm:help$",          HPM_HELP_ARG),
    new Prefix("-X:hpm$",               HPM_HELP_ARG),
    new Prefix("-X:hpm:",               HPM_ARG),
    new Prefix("-X:vm:help$",           VM_HELP_ARG),
    new Prefix("-X:vm$",                VM_HELP_ARG),
    new Prefix("-X:vm:",                VM_ARG),
    app_prefix
  };

  static {
    // Bubble sort the prefixes (yeah, yeah, I know)
    // 
    // Besides, this is done only at boot image writing time, not at runtime.
    for (int i = 0; i < prefixes.length; i++)
      for (int j = i + 1; j < prefixes.length; j++)
        if (prefixes[j].value.compareTo(prefixes[i].value) >= 0) {
          Prefix t = prefixes[i];
          prefixes[i] = prefixes[j];
          prefixes[j] = t;
        }
    if (DEBUG) {
      for (int i = 0; i < prefixes.length; i++) {
        Prefix t = prefixes[i];
        VM.sysWrite("Prefix["+i+"]: \""+t.value+"\"; "+t.type+"\n");
      }
    }
  }

  /**
   * The command line arguments.
   */
  private static String[] args;
  /**
   * The types of each command line argument.
   */
  private static int[]    arg_types;
  /**
   * The position of application class name.
   */
  private static int      app_name_pos = -1;

  /**
   * Fetch arguments from program command line.
   */
  static void fetchCommandLineArguments() {
    if (args != null)           // if already been here...
      return;   
    ArgReader argRdr = new ArgReader();

    int numArgs = argRdr.numArgs();
    args = new String[numArgs];
    arg_types = new int[numArgs];

    for (int i = 0; i < numArgs; ++i) {
      String arg = argRdr.getArg(i);
      
      if (app_prefix.count > 0) {
        /* We're already into the application arguments.  Here's another
         * one. */ 
        args[i] = arg;
        arg_types[i] = APPLICATION_ARG;
        app_prefix.count++;
        continue;
      } 
      
      // Note: This loop will never run to the end.
      for (int j = 0; j < prefixes.length; j++) {
        Prefix p = prefixes[j];
        String v = p.value;
        if (!matches(arg, v)) 
          continue;
        // Chop off the prefix (which we've already matched) and store the
        // value portion of the string (the unique part) in args[i].  Store
        // information about the prefix itself in arg_types[i].   
        args[i] = arg.substring(length(v));
        if (DEBUG) {
          VM.sysWrite("length(v) = ");
          VM.sysWrite(length(v));

          VM.sysWrite("; v = \"");
          VM.sysWrite(v);
          VM.sysWriteln("\"");
          VM.sysWrite("args[");
          VM.sysWrite(i);
          VM.sysWrite("] = \"");
          VM.sysWrite(args[i]);
          VM.sysWrite("\"; arg = \"");
          VM.sysWrite(arg);
          VM.sysWriteln("\"");
        }
        
        arg_types[i] = p.type;
        p = findPrefix(p.type); // Find the canonical prefix for this type...
        p.count++;              // And increment the usage count for that
                                // canonical prefix.
        if (v.endsWith(" ")) {
          if (++i >= numArgs) { 
            VM.sysWrite("vm: " + v + "needs an argument\n"); 
            VM.sysExit(VM.exitStatusBogusCommandLineArg); 
          }
          args[ i - 1 ] += argRdr.getArg(i);
          args[i] = null;
        }
        if (p == app_prefix) 
          app_name_pos = i;
        break;
      }
    } // for (i = 0; i < numArgs...)
    /*
     * If no application is specified, set app_name_pos to numArgs (that is,
     * to one past the last item in the array of arguments) to ensure all
     * command-line arguments are processed.
     */
    if (app_name_pos == -1) 
      app_name_pos = numArgs;
  }

  /**
   * Does the argument match the prefix?
   * @param arg argument
   * @param p prefix
   * @return true if argument "matches" the prefix, false otherwise
   */
  private static boolean matches(String arg, String p) {
    if (p.endsWith(" "))
      return arg.equals(p.substring(0,p.length()-1)) || arg.startsWith(p);
    if (p.endsWith("$"))
      return arg.equals(p.substring(0,p.length()-1));
    return arg.startsWith(p);
  }

  /**
   * The real length of the prefix.
   * @param p prefix
   * @return real length of prefix
   */
  private static int length(String p) {
    if (p.endsWith("$") || p.endsWith(" ")) return p.length()-1;
    return p.length();
  }

  /**
   * Find a Prefix object of a given type.
   */
  private static Prefix findPrefix(int prefix) {
    for (int k = 0; k < prefixes.length; k++)
      if (prefixes[k].type == prefix) return prefixes[k];
    return null;
  }

  /**
   * Extract all command line arguments of a particular type.
   * Strips out the prefixes (if any).
   * !!TODO: cache results instead of constructing a new array each time.
   * @param prefix type of arguments to extract
   * @return array of arguments or null if type is invalid
   */
  public static String[] getArgs(int prefix) {
    String[] retarg = null;
    Prefix p = findPrefix(prefix);
    if (p != null) {
      retarg = new String[p.count];
      for (int i = 0, j = 0; i < args.length; i++) {
        if (arg_types[i] == prefix) {
          retarg[j++] = args[i];
        }
      }
    }
    return retarg;
  }

  /**
   * Extract the first -D... command line argument that matches a given
   * variable, and return it.
   * @return the environment arg, or null if there is none.
   */
  public static String getEnvironmentArg(String variable) {
    String[] allEnvArgs = getArgs(ENVIRONMENT_ARG);
    String prefix = variable + "=";
    if (allEnvArgs != null)
      for(int i = 0; i < allEnvArgs.length; i++) 
        if (allEnvArgs[i].startsWith(prefix))
          return allEnvArgs[i].substring(variable.length()+1);

    // There are some that we treat specially.
    if (variable.equals("rvm.root"))
      return getRvmRoot();
    else if (variable.equals("rvm.build"))
      return getRvmBuild();
    else if (variable.equals("java.home"))
      return getRvmRoot();
    else if (variable.equals("gnu.classpath.home.url"))
      return "file:" + getRvmBuild();
    else if (variable.equals("gnu.classpath.vm.shortname"))
      return "JikesRVM";
    else if (variable.equals("user.home"))
      return getUserHome();
    else if (variable.equals("user.dir"))
      return getCWD();
    else if (variable.equals("os.name"))
      return getOsName();
    else if (variable.equals("os.version"))
      return getOsVersion();
    else if (variable.equals("os.arch"))
      return getOsArch();
    // Ok, didn't find it.
    return null;
  }

  public static String getRvmRoot() {
    return null;
  }
  public static String getRvmBuild() {
    return null;
  }
  public static String getUserHome() {
    return null;
  }
  public static String getCWD() {
    return null;
  }
  public static String getOsName() {
    return null;
  }
  public static String getOsVersion() {
    return null;
  }
  public static String getOsArch() {
    return null;
  }
  
  /**
   * Extract the -X:vmClasses command line argument and return it.
   * @return null if no such command line argument is given.
   */
  static String getVMClasses() {
    String[] vmClassesAll = getArgs(VM_CLASSES_ARG);
    String vmClasses = null;
    // could be specified multiple times, use last specification
    if (vmClassesAll.length > 0)
      vmClasses = vmClassesAll[vmClassesAll.length - 1];
    return vmClasses;
  }

  /**
   * Stage1 processing of virtual machine directives appearing in argument list.
   * We try to process as many classes of command line arguments as possible here.
   * Only those command line arguments that require a more or less
   * fully booted VM to handle are delayed until lateProcessCommandLineArguments.
   */
  static void earlyProcessCommandLineArguments() {
    for (int i = 0; i<app_name_pos; i++) {
      String arg = args[i];
      int type = arg_types[i];
      if (type == INVALID_ARG) continue;
      Prefix p = findPrefix(type);
      if (DEBUG) VM.sysWrite(" VM_CommandLineArgs.earlyProcessCLA("+p.value+arg+")\n");
      switch (type) {
      case VERBOSE_CLS_ARG:
        VM.verboseClassLoading = true;
        break;
 
     case VERBOSE_JNI_ARG:
        VM.verboseJNI = true;
        break;

        // -------------------------------------------------//
        // Options needed by VM_Scheduler to boot correctly //
        // -------------------------------------------------//  
      case CPUAFFINITY_ARG:
        int cpuAffinity = 0;
        try { cpuAffinity = primitiveParseInt(arg); }
        catch (NumberFormatException e) { cpuAffinity = -1; }
        if (cpuAffinity < 0) {
          VM.sysWrite("vm: "+p.value+" needs a cpu number (0..N-1), but found '"+arg+"'\n");
          VM.sysExit(VM.exitStatusBogusCommandLineArg);
        }
        if (VM.BuildForSingleVirtualProcessor && cpuAffinity != 0) { 
          VM.sysWrite("vm: I wasn't compiled to support multiple processors\n");
          VM.sysExit(VM.exitStatusBogusCommandLineArg);
        }
        VM_Scheduler.cpuAffinity = cpuAffinity;
        break;

      case PROCESSORS_ARG: // "-X:processors=<n>" or "-X:processors=all"
        if (arg.equals("all")) {
          VM_Scheduler.numProcessors = VM_SysCall.sysNumProcessors();
        } else {
          VM_Scheduler.numProcessors = primitiveParseInt(arg);
        }
        if (VM_Scheduler.numProcessors < 1 ||
            VM_Scheduler.numProcessors > (VM_Scheduler.MAX_PROCESSORS-1)) {
          VM.sysWrite("vm: "+p.value+arg+" needs an argument between 1 and " + (VM_Scheduler.MAX_PROCESSORS-1) + " (inclusive)\n");
          VM.sysExit(VM.exitStatusBogusCommandLineArg);
        }
        if (VM.BuildForSingleVirtualProcessor &&
            VM_Scheduler.numProcessors != 1) {
          VM.sysWrite("vm: I wasn't compiled to support multiple processors\n");
          VM.sysExit(VM.exitStatusBogusCommandLineArg);
        }
        break;

        // -------------------------------------------------------------------
        // GC options
        // -------------------------------------------------------------------
      case GC_HELP_ARG:  // -X:gc passed 'help' as an option
        MM_Interface.processCommandLineArg("help");
        break;
      case GC_ARG: // "-X:gc:arg" pass 'arg' as an option
        MM_Interface.processCommandLineArg(arg);
        break;
        
        // ----------------------------------------------------
        // Access initial runtime compiler (may be baseline or optimizing).
        // ----------------------------------------------------
      case IRC_HELP_ARG:
        VM_RuntimeCompiler.processCommandLineArg("-X:irc:", "help");
        break;
      case IRC_ARG: // "-X:irc:arg"; pass 'arg' as an option
        VM_RuntimeCompiler.processCommandLineArg("-X:irc:", arg);
        break;

        // --------------------------------------------------------------------
        // Access adaptive system's recompilation compilers
        // Currently this means the opt compiler, but in general we could be
        // talking to several different compilers used by AOS for recompilation.
        // --------------------------------------------------------------------
      case RECOMP_HELP_ARG:
        //-#if RVM_WITH_ADAPTIVE_SYSTEM
        VM_Controller.addOptCompilerOption("opt:help");
        //-#else
        VM.sysWrite("vm: nonadaptive configuration; -X:recomp is not a legal prefix in this configuration\n");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
        //-#endif
        break;
      case RECOMP_ARG:
        // "-X:recomp[?]:arg" process as 'opt[?]:arg' to opt
        // Note arg actually includes the optional opt level and :
        //-#if RVM_WITH_ADAPTIVE_SYSTEM
        VM_Controller.addOptCompilerOption("opt"+arg);
        //-#else
        VM.sysWrite("vm: nonadaptive configuration; -X:recomp is not a legal prefix in this configuration\n");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
        //-#endif
        break;

        // -------------------------------------------------------------------
        // Access adaptive optimization system
        // -------------------------------------------------------------------
      case AOS_HELP_ARG:  // -X:aos passed 'help' as an option
        //-#if RVM_WITH_ADAPTIVE_SYSTEM
        VM_Controller.processCommandLineArg("help");
        //-#else
        VM.sysWrite("vm: nonadaptive configuration; -X:aos is not a legal prefix in this configuration\n");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
        //-#endif
        break;
      case AOS_ARG: // "-X:aos:arg" pass 'arg' as an option
        //-#if RVM_WITH_ADAPTIVE_SYSTEM
        VM_Controller.processCommandLineArg(arg);
        //-#else
        VM.sysWrite("vm: nonadaptive configuration; -X:aos is not a legal prefix in this configuration\n");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
        //-#endif
        break;

        // ----------------------------------------------------
        // Access baseline compiler
        // ----------------------------------------------------
      case BASE_HELP_ARG:
        VM_BaselineOptions.printHelp("-X:base:");
        break;
      case BASE_ARG: // "-X:base:arg"; pass 'arg' as an option
        VM_BaselineCompiler.processCommandLineArg(p.value,arg);
        break;

        // ----------------------------------------------------
        // Access all 'logical' optimizing compilers
        // (both irc and recomp compilers)
        // ----------------------------------------------------
      case OPT_HELP_ARG:
        //-#if RVM_WITH_ADAPTIVE_SYSTEM
        VM_RuntimeCompiler.processOptCommandLineArg("-X:opt:","help");
        //-#else
        VM.sysWrite("vm: You are not using a system that includes the optimizing compiler.");
        VM.sysWrite(" Illegal command line argument prefix '-X:opt'\n");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
        //-#endif
        break;
      case OPT_ARG: // "-X:opt:arg"; pass 'arg' as an option
        //-#if RVM_WITH_ADAPTIVE_SYSTEM
        VM_RuntimeCompiler.processOptCommandLineArg("-X:opt:", arg);
        VM_Controller.addOptCompilerOption("opt:"+arg);
        //-#else
        VM.sysWrite("vm: You are not using a system that includes the optimizing compiler.");
        VM.sysWrite(" Illegal command line argument prefix '-X:opt'\n");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
        //-#endif
        break;

        // -------------------------------------------------------------------
        // Other arguments to the core VM
        // -------------------------------------------------------------------
      case VM_HELP_ARG:  // -X:vm passed 'help' as an option
        VM_Options.printHelp();
        break;
      case VM_ARG: // "-X:vm:arg" pass 'arg' as an option
        if (!VM_Options.process(arg)) {
          VM.sysWriteln("Unrecognized command line argument "+p.value+arg);
          VM.sysExit(VM.exitStatusBogusCommandLineArg);
        }
        break;
        
        //-#if RVM_WITH_HPM
        // -------------------------------------------------------------------
        // HPM (Hardware Performance Monitor) arguments
        // -------------------------------------------------------------------
      case HPM_ARG: // "-X:hpm:<option>"
        VM_HardwarePerformanceMonitors.processArg(arg);
        break;
      case HPM_HELP_ARG:
        VM_HardwarePerformanceMonitors.printHelp();
        break;
        //-#else
      case HPM_ARG: // "-X:hpm:<option>"
      case HPM_HELP_ARG:
        VM.sysWriteln("-X:hpm command line arguments not supported.  Build system with RVM_WITH_HPM defined.");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
        break;
        //-#endif
      }
    }
  }

  /**
   * Stage2 processing of virtual machine directives appearing in argument list.
   * This function is responsible for processing the few 
   * command line arguments that need to be handled late in booting.
   * It also returns the application's command line arguments.
   * 
   * @return application arguments (first is application class name)
   * If no application arguments are specified on the command line, 
   * process commands anyway.
   */
  static String[] lateProcessCommandLineArguments() {
    for (int i = 0; i<app_name_pos; i++) {
      String arg = args[i];
      int type = arg_types[i];
      if (type == INVALID_ARG) continue;
      Prefix p = findPrefix(type);
      if (DEBUG) VM.sysWrite(" VM_CommandLineArgs.processCLA("+p.value+arg+")\n");
      switch (type) {
      case ENVIRONMENT_ARG: // arguments of the form "-Dx=y"
        { 
          int mid = arg.indexOf('=');
          if (mid == -1 || mid + 1 == arg.length()) {
            VM.sysWrite("vm: bad property setting: \""+arg+"\"\n");
            VM.sysExit(VM.exitStatusBogusCommandLineArg);
          }
          String name  = arg.substring(0, mid);
          String value = arg.substring(mid + 1);
          System.getProperties().put(name, value);
        }
        break;

      case CLASSPATH_ARG:
        // arguments of the form "-classpath a:b:c" or "-cp a:b:c"
        VM_ClassLoader.setApplicationRepositories(arg);
        break;

      case JAR_ARG:
        // arguments of the form -jar <jarfile>
        java.util.jar.Manifest mf = null;
        try {
          java.util.jar.JarFile jf = new java.util.jar.JarFile(arg);
          mf = jf.getManifest();
        } catch(Exception e) {
          VM.sysWriteln("IOE: " + e.getMessage());
          VM.sysExit(VM.exitStatusBogusCommandLineArg); 
        }
        String s = mf.getMainAttributes().getValue("Main-Class");
        if (s == null) {
          VM.sysWriteln("The jar file is missing the manifest entry for the main class: "+arg);
          VM.sysExit(VM.exitStatusBogusCommandLineArg);
        }
        // maybe also load classes on the classpath list in the manifest
        VM_ClassLoader.setApplicationRepositories(arg);

        args[i] = s;
        arg_types[i] = APPLICATION_ARG;
        app_prefix.count++;
      break;

      }
    }

    // get application directives
    String[] arglist = getArgs(APPLICATION_ARG);

    // Debugging: write out application arguments
    if (DEBUG) {
      VM.sysWrite("VM.CommandLineArgs(): application arguments "+ arglist.length+"\n");
      for (int i = 0; i < arglist.length; i++) {
        VM.sysWrite(i+": \""+arglist[i]+"\"\n");
      }
    }

    return arglist;
  }
  
  /**
   * Read the argno'th command line argument from the C argv
   * @param argno number of argument sought
   * @param buf[] buffer to fill
   * @return number of bytes placed in buffer. -1 means buffer too small 
   *         for argument to fit)
   */
  private static int sysArg(int argno, byte buf[]) {
    return VM_SysCall.sysArg(argno, buf, buf.length);
  }

  /**
   * Primitive parsing of float/double values.
   * Done this way to enable us to parse command line arguments
   * early in VM booting before we are able to do the JNI call
   * that using Double.valueOf would require.
   * Does not support the full Java spec.
   */
  public static float primitiveParseFloat(String arg) {
    int len = arg.length();
    byte[] b = new byte[len+1];
    for (int i = 0; i < len; i++) {
      char c = arg.charAt(i);
      if (c > 127) {
        VM.sysWriteln("vm: Invalid floating point argument ",arg);
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
      }
      b[i] = (byte)c;
    }
    return VM_SysCall.sysPrimitiveParseFloat(b);
  }

  /**
   * Primitive parsing of byte/inte values.
   * Done this way to enable us to parse command line arguments
   * early in VM booting before we are able call
   * Byte.parseByte or Integer.parseInt.
   */
  public static int primitiveParseInt(String arg) {
    int len = arg.length();
    byte[] b = new byte[len+1];
    for (int i = 0; i < len; i++) {
      char c = arg.charAt(i);
      if (c > 127) {
        VM.sysWriteln("vm: Invalid int/byte argument ",arg);
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
      }
      b[i] = (byte)c;
    }
    return VM_SysCall.sysPrimitiveParseInt(b);
  }

  private static final class ArgReader {
    //    int buflen = 10;              // for testing; small enough to force
                                // reallocation really soon.
    int buflen = 512;
    
    byte[] buf;                 // gets freed with the class instance.
    ArgReader() {
      buf = new byte[buflen];
    }

    /** Read argument # @param i
     * Assume arguments are encoded in the platform's 
     * "default character set". */ 
    String getArg(int i) {
      int cnt;
      for (;;) {
        cnt = sysArg(i, buf);
        if (cnt >= 0)
          break;
        buflen += 1024;
        buf = new byte[buflen];
      }
      if (VM.VerifyAssertions) VM._assert(cnt != -1); 
      /* Implementation note: Do NOT use the line below, which uses the
         three-argument constructor for String, the one that respects the
         native encoding (the platform's "default character set").

         Instead, we use the four-argument constructor, the one that takes a
         HIBYTE parameter.

         1) It is safe to do this; we *know* that all of the
         legal command-line args use only characters within the ASCII
         character set.

         2) The "default character set" version below will break.  That is
            because GNU Classpath's implementation of the
            three-argument-constructor will fail if
            EncodingManager.getDecoder() returns a null pointer.  And
            EncodingManager.getDecoder() returns a null pointer if it's
            called early on in the boot process (which the
            default-character-set version below does). */
      //      return new String(buf, 0, cnt);
      return new String(buf, 0, 0, cnt);
    }
    int numArgs() {
      return sysArg(-1, buf);
    }
  }


}
