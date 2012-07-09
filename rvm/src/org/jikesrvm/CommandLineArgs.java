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
package org.jikesrvm;

import java.io.File;
import java.util.Arrays;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.compilers.baseline.BaselineOptions;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.mm.mminterface.MemoryManager;

import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Command line option processing iwth arbitrary prefix support.
 */
public class CommandLineArgs {
  private static final boolean DEBUG = false;

  /**
   * Argument types
   */
  private enum PrefixType {
    /**
     * Invalid argument type
     */
    INVALID_ARG, // default
    /**
     * Application argument
     */
    APPLICATION_ARG,

    // -----------------------------------------------//
    // The following arguments are standard java.     //
    // -----------------------------------------------//
    CLASSPATH_ARG,
    ENVIRONMENT_ARG,
    VERBOSE_JNI_ARG,
    VERBOSE_CLS_ARG,
    JAR_ARG,
    JAVAAGENT_ARG,
    ENABLE_ASSERTION_ARG,
    ENABLE_SYSTEM_ASSERTION_ARG,
    DISABLE_ASSERTION_ARG,
    DISABLE_SYSTEM_ASSERTION_ARG,

    // -----------------------------------------------//
    // The following arguments are RVM-specific.      //
    // -----------------------------------------------//
    HELP_ARG,
    ARG,
    IRC_HELP_ARG,
    IRC_ARG,
    RECOMP_HELP_ARG,
    RECOMP_ARG,
    AOS_HELP_ARG,
    AOS_ARG,
    BASE_HELP_ARG,
    BASE_ARG,
    OPT_ARG,
    OPT_HELP_ARG,
    /* Silently ignored */
    VERIFY_ARG,
    GC_HELP_ARG,
    GC_ARG,
    BOOTCLASSPATH_P_ARG,
    BOOTCLASSPATH_A_ARG,
    BOOTSTRAP_CLASSES_ARG,
    AVAILABLE_PROCESSORS_ARG
  }

  /** Represent a single command line prefix */
  private static final class Prefix implements Comparable<Prefix> {
    /** The command line string e.g. "-X:irc:" */
    public final String value;
    /** A number that describes the type of the argument */
    public final PrefixType type;
    /** Number of arguments of this type seen */
    public int count = 0;

    /** Construct a prefix with the given argument string and type
     * @param v argument string
     * @param t type of prefix, must be non-null
     */
    public Prefix(String v, PrefixType t) {
      value = v;
      type = t;
      if (t == null) {
        throw new Error("Type of prefix should never be null");
      }
    }

    /** Sorting method for Comparable. Sort by string value */
    @Override
    public int compareTo(Prefix o) {
      return -value.compareTo(o.value);
    }
    /** Equals method to be consistent with Comparable */
    @Override
    public boolean equals(Object o) {
      if (o instanceof Prefix) {
        return value.equals(((Prefix)o).value);
      }
      return false;
    }
    /** Hashcode to be consistent with Comparable */
    @Override
    public int hashCode() {
      return value.hashCode();
    }
    /** Command line string representation of the prefix */
    @Override
    public String toString() {
      return value;
    }
  }

  /**
   * A catch-all prefix to find application name.
   */
  private static final Prefix app_prefix = new Prefix("", PrefixType.APPLICATION_ARG);

  /**
   * A list of possible prefixes for command line arguments.
   * Each argument will be classified by the prefix it matches.
   * One prefix can contain another.<p>
   *
   * Prefixes are normally matched with the start of the argument.
   * If the last character of the prefix is a '$', the prefix (without the
   * trailing '$') will be matched with the whole argument.
   * If the last character of the prefix is a ' ' (space), the prefix
   * (without the trailing ' ') will be matched with the whole argument,
   * and the next argument will be appended to the end of the one matching
   * the prefix, with a space in between.<p>
   *
   * The type will be used to classify the prefix.  Multiple entries CAN
   * have the same type.
   */
  private static final Prefix[] prefixes = {new Prefix("-classpath ", PrefixType.CLASSPATH_ARG),
                                            // Note: space is significant
                                            new Prefix("-cp ", PrefixType.CLASSPATH_ARG),
                                            // Note: space is significant
                                            new Prefix("-jar ", PrefixType.JAR_ARG),
                                            // Note: space is significant
                                            new Prefix("-javaagent:", PrefixType.JAVAAGENT_ARG),
                                            new Prefix("-D", PrefixType.ENVIRONMENT_ARG),
                                            new Prefix("-verbose:class$", PrefixType.VERBOSE_CLS_ARG),
                                            new Prefix("-verbose:jni$", PrefixType.VERBOSE_JNI_ARG),
                                            new Prefix("-verbose$", PrefixType.VERBOSE_CLS_ARG),

                                            new Prefix("-enableassertions:", PrefixType.ENABLE_ASSERTION_ARG),
                                            new Prefix("-ea:", PrefixType.ENABLE_ASSERTION_ARG),
                                            new Prefix("-enableassertions:", PrefixType.ENABLE_ASSERTION_ARG),
                                            new Prefix("-ea", PrefixType.ENABLE_ASSERTION_ARG),

                                            new Prefix("-enableassertions", PrefixType.ENABLE_ASSERTION_ARG),

                                            new Prefix("-esa:", PrefixType.ENABLE_SYSTEM_ASSERTION_ARG),
                                            new Prefix("-enablesystemassertions:", PrefixType.ENABLE_SYSTEM_ASSERTION_ARG),
                                            new Prefix("-esa", PrefixType.ENABLE_SYSTEM_ASSERTION_ARG),
                                            new Prefix("-enablesystemassertions", PrefixType.ENABLE_SYSTEM_ASSERTION_ARG),

                                            new Prefix("-disableassertions:", PrefixType.DISABLE_ASSERTION_ARG),
                                            new Prefix("-da:", PrefixType.DISABLE_ASSERTION_ARG),
                                            new Prefix("-disableassertions", PrefixType.DISABLE_ASSERTION_ARG),
                                            new Prefix("-da", PrefixType.DISABLE_ASSERTION_ARG),

                                            new Prefix("-disablesystemassertions:", PrefixType.DISABLE_SYSTEM_ASSERTION_ARG),
                                            new Prefix("-dsa:", PrefixType.DISABLE_SYSTEM_ASSERTION_ARG),
                                            new Prefix("-disablesystemassertions", PrefixType.DISABLE_SYSTEM_ASSERTION_ARG),
                                            new Prefix("-dsa", PrefixType.DISABLE_SYSTEM_ASSERTION_ARG),

                                            new Prefix("-Xbootclasspath/p:", PrefixType.BOOTCLASSPATH_P_ARG),
                                            new Prefix("-Xbootclasspath/a:", PrefixType.BOOTCLASSPATH_A_ARG),
                                            new Prefix("-X:vmClasses=", PrefixType.BOOTSTRAP_CLASSES_ARG),
                                            new Prefix("-X:availableProcessors=", PrefixType.AVAILABLE_PROCESSORS_ARG),
                                            new Prefix("-X:irc:help$", PrefixType.IRC_HELP_ARG),
                                            new Prefix("-X:irc$", PrefixType.IRC_HELP_ARG),
                                            new Prefix("-X:irc:", PrefixType.IRC_ARG),
                                            new Prefix("-X:recomp:help$", PrefixType.RECOMP_HELP_ARG),
                                            new Prefix("-X:recomp$", PrefixType.RECOMP_HELP_ARG),
                                            new Prefix("-X:recomp", PrefixType.RECOMP_ARG),
                                            new Prefix("-X:aos:help$", PrefixType.AOS_HELP_ARG),
                                            new Prefix("-X:aos$", PrefixType.AOS_HELP_ARG),
                                            new Prefix("-X:aos:", PrefixType.AOS_ARG),
                                            new Prefix("-X:gc:help$", PrefixType.GC_HELP_ARG),
                                            new Prefix("-X:gc$", PrefixType.GC_HELP_ARG),
                                            new Prefix("-X:gc:", PrefixType.GC_ARG),
                                            new Prefix("-X:base:help$", PrefixType.BASE_HELP_ARG),
                                            new Prefix("-X:base$", PrefixType.BASE_HELP_ARG),
                                            new Prefix("-X:base:", PrefixType.BASE_ARG),
                                            new Prefix("-X:opt:help$", PrefixType.OPT_HELP_ARG),
                                            new Prefix("-X:opt$", PrefixType.OPT_HELP_ARG),
                                            new Prefix("-X:opt:", PrefixType.OPT_ARG),
                                            new Prefix("-X:vm:help$", PrefixType.HELP_ARG),
                                            new Prefix("-X:vm$", PrefixType.HELP_ARG),
                                            new Prefix("-X:vm:", PrefixType.ARG),

                                            /* Silently ignored */
                                            new Prefix("-Xverify", PrefixType.VERIFY_ARG),

                                            app_prefix};

  static {
    Arrays.sort(prefixes);
    if (DEBUG) {
      for (int i = 0; i < prefixes.length; i++) {
        Prefix t = prefixes[i];
        VM.sysWrite("Prefix[" + i + "]: \"" + t.value + "\"; " + t.type + "\n");
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
  private static PrefixType[] arg_types;
  /**
   * The position of application class name.
   */
  private static int app_name_pos = -1;

  /**
   * Fetch arguments from program command line.
   */
  static void fetchCommandLineArguments() {
    if (args != null) {
      // if already been here...
      return;
    }
    ArgReader argRdr = new ArgReader();

    int numArgs = argRdr.numArgs();
    args = new String[numArgs];
    arg_types = new PrefixType[numArgs];

    for (int i = 0; i < numArgs; ++i) {
      String arg = argRdr.getArg(i);

      if (app_prefix.count > 0) {
        /* We're already into the application arguments.  Here's another
         * one. */
        args[i] = arg;
        arg_types[i] = PrefixType.APPLICATION_ARG;
        app_prefix.count++;
        continue;
      }

      // Note: This loop will never run to the end.
      for (Prefix p : prefixes) {
        String v = p.value;
        if (!matches(arg, v)) {
          continue;
        }
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
            VM.sysWriteln("vm: ", v, "needs an argument");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          args[i - 1] += argRdr.getArg(i);
          args[i] = null;
        }
        if (p == app_prefix) {
          app_name_pos = i;
        }
        break;
      }
    } // for (i = 0; i < numArgs...)
    /*
     * If no application is specified, set app_name_pos to numArgs (that is,
     * to one past the last item in the array of arguments) to ensure all
     * command-line arguments are processed.
     */
    if (app_name_pos == -1) {
      app_name_pos = numArgs;
    }
  }

  /**
   * Does the argument match the prefix?
   * @param arg argument
   * @param p prefix
   * @return true if argument "matches" the prefix, false otherwise
   */
  private static boolean matches(String arg, String p) {
    if (p.endsWith(" ")) {
      return arg.equals(p.substring(0, p.length() - 1)) || arg.startsWith(p);
    }
    if (p.endsWith("$")) {
      return arg.equals(p.substring(0, p.length() - 1));
    }
    return arg.startsWith(p);
  }

  /**
   * The real length of the prefix.
   * @param p prefix
   * @return real length of prefix
   */
  private static int length(String p) {
    if (p.endsWith("$") || p.endsWith(" ")) return p.length() - 1;
    return p.length();
  }

  /**
   * Find a Prefix object of a given type.
   * @param type given type
   * @return prefix if found, {@code null} otherwise
   */
  private static Prefix findPrefix(PrefixType type) {
    for (Prefix prefix : prefixes) if (prefix.type == type) return prefix;
    return null;
  }

  /**
   * Extract all command line arguments of a particular type.
   * Strips out the prefixes (if any).
   * !!TODO: cache results instead of constructing a new array each time.
   * @param prefix type of arguments to extract
   * @return array of arguments or null if type is invalid
   */
  public static String[] getArgs(PrefixType prefix) {
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
   * Extract command line arguments for the Java agent
   * @return Java agent arguments
   */
  public static String[] getJavaAgentArgs() {
    return CommandLineArgs.getArgs(CommandLineArgs.PrefixType.JAVAAGENT_ARG);
  }

  /**
   * Get all environment arguments as pairs of string of key followed by value
   * @return all environment arguments or {@code null}, if none were found
   */
  public static String[] getEnvironmentArgs() {
    if (!VM.runningVM) throw new IllegalAccessError("Environment variables can't be read in a non-running VM");
    return getArgs(PrefixType.ENVIRONMENT_ARG);
  }

  /**
   * Extract the first -D... command line argument that matches a given
   * variable, and return it.
   * @param variable the non-null variable to match
   * @return the environment arg, or null if there is none.
   */
  public static String getEnvironmentArg(String variable) {
    if (!VM.runningVM) throw new IllegalAccessError("Environment variables can't be read in a non-running VM");
    String[] allEnvArgs = getArgs(PrefixType.ENVIRONMENT_ARG);
    String prefix = variable + "=";
    if (allEnvArgs != null) {
      for (String allEnvArg : allEnvArgs) {
        if (allEnvArg.startsWith(prefix)) {
          return allEnvArg.substring(variable.length() + 1);
        }
      }
    }

    // There are some that we treat specially.
    if (variable.equals("java.home")) {
      return getRvmRoot();
    } else if (variable.equals("gnu.classpath.home.url")) {
      return "file:" + getRvmRoot();
    } else if (variable.equals("gnu.classpath.vm.shortname")) {
      return "JikesRVM";
    } else if (variable.equals("user.home")) {
      return getUserHome();
    } else if (variable.equals("user.dir")) {
      return getCWD();
    } else if (variable.equals("os.name")) {
      return getOsName();
    } else if (variable.equals("os.version")) {
      return getOsVersion();
    } else if (variable.equals("os.arch")) {
      return getOsArch();
    }
    // Ok, didn't find it.
    return null;
  }

  private static String getRvmRoot() {
    return null;
  }

  private static String getUserHome() {
    return null;
  }

  private static String getCWD() {
    return null;
  }

  private static String getOsName() {
    return null;
  }

  private static String getOsVersion() {
    return null;
  }

  private static String getOsArch() {
    return null;
  }

  /**
   * Extract the classes that should go through bootstrap classloader.
   * @return null if no such command line argument is given.
   */
  public static String getBootstrapClasses() {
    String[] vmClassesAll = getArgs(PrefixType.BOOTSTRAP_CLASSES_ARG);
    String[] prependClasses = getArgs(PrefixType.BOOTCLASSPATH_P_ARG);
    String[] appendClasses = getArgs(PrefixType.BOOTCLASSPATH_A_ARG);

    // choose the latest definition of -X:vmClasses
    String vmClasses = null;
    // could be specified multiple times, use last specification
    if (vmClassesAll.length > 0) {
      vmClasses = vmClassesAll[vmClassesAll.length - 1];
    }

    // concatenate all bootclasspath entries
    String result = vmClasses;

    for(int c = 0; c < prependClasses.length; c++) {
      result = prependClasses[c] + ":" + result;
    }

    for(int c = 0; c < appendClasses.length; c++) {
      result = result + ":" + appendClasses[c];
    }

    return result;
  }

  /**
   * Stage1 processing of virtual machine directives appearing in argument list.
   * We try to process as many classes of command line arguments as possible here.
   * Only those command line arguments that require a more or less
   * fully booted VM to handle are delayed until lateProcessCommandLineArguments.
   */
  static void earlyProcessCommandLineArguments() {
    for (int i = 0; i < app_name_pos; i++) {
      String arg = args[i];
      PrefixType type = arg_types[i];
      if (type == PrefixType.INVALID_ARG) continue;
      Prefix p = findPrefix(type);
      if (DEBUG) VM.sysWriteln(" CommandLineArgs.earlyProcessCLA(" + p + arg + " - " + type + ")");
      switch (type) {

        case CLASSPATH_ARG:
          // arguments of the form "-classpath a:b:c" or "-cp a:b:c"
          // We are experimentally processing this early so that we can have the
          // Application class loader complete for when
          // ClassLoader$StaticData's initializer is run.
          RVMClassLoader.stashApplicationRepositories(arg);
          i++; // skip second argument to classpath
          break;

        case JAR_ARG:
          // maybe also load classes on the classpath list in the manifest
          RVMClassLoader.stashApplicationRepositories(arg);
          i++; // skip second argument to jar
          break;

        case ENABLE_ASSERTION_ARG:
          // arguments of the form "-ea[:<packagename>...|:<classname>]"
          RVMClassLoader.stashEnableAssertionArg(arg);
          break;

        case ENABLE_SYSTEM_ASSERTION_ARG:
          // arguments of the form "-esa[:<packagename>...|:<classname>]"
          // TODO: currently just treat as -ea
          RVMClassLoader.stashEnableAssertionArg(arg);
          break;

        case DISABLE_ASSERTION_ARG:
          // arguments of the form "-da[:<packagename>...|:<classname>]"
          RVMClassLoader.stashDisableAssertionArg(arg);
          break;

        case DISABLE_SYSTEM_ASSERTION_ARG:
          // arguments of the form "-dsa[:<packagename>...|:<classname>]"
          // TODO: currently just treat as -da
          RVMClassLoader.stashDisableAssertionArg(arg);
          break;

        case VERBOSE_CLS_ARG:
          VM.verboseClassLoading = true;
          break;

        case VERBOSE_JNI_ARG:
          VM.verboseJNI = true;
          break;

        case AVAILABLE_PROCESSORS_ARG:
          RVMThread.availableProcessors = primitiveParseInt(arg);
          if (RVMThread.availableProcessors < 1) {
            VM.sysWrite("vm: ", p.value, " needs an argument that is at least 1");
            VM.sysWriteln(", but found ", arg);
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;

          // -------------------------------------------------------------------
          // GC options
          // -------------------------------------------------------------------
        case GC_HELP_ARG:  // -X:gc passed 'help' as an option
          MemoryManager.processCommandLineArg("help");
          break;
        case GC_ARG: // "-X:gc:arg" pass 'arg' as an option
          MemoryManager.processCommandLineArg(arg);
          break;

          // ----------------------------------------------------
          // Access initial runtime compiler (may be baseline or optimizing).
          // ----------------------------------------------------
        case IRC_HELP_ARG:
          RuntimeCompiler.processCommandLineArg("-X:irc:", "help");
          break;
        case IRC_ARG: // "-X:irc:arg"; pass 'arg' as an option
          RuntimeCompiler.processCommandLineArg("-X:irc:", arg);
          break;

          // --------------------------------------------------------------------
          // Access adaptive system's recompilation compilers
          // Currently this means the opt compiler, but in general we could be
          // talking to several different compilers used by AOS for recompilation.
          // --------------------------------------------------------------------
        case RECOMP_HELP_ARG:
          if (VM.BuildForAdaptiveSystem) {
            Controller.addOptCompilerOption("opt:help");
          } else {
            VM.sysWriteln("vm: nonadaptive configuration; -X:recomp is not a legal prefix in this configuration");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;
        case RECOMP_ARG:
          // "-X:recomp[?]:arg" process as 'opt[?]:arg' to opt
          // Note arg actually includes the optional opt level and :
          if (VM.BuildForAdaptiveSystem) {
            Controller.addOptCompilerOption("opt" + arg);
          } else {
            VM.sysWriteln("vm: nonadaptive configuration; -X:recomp is not a legal prefix in this configuration");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;

          // -------------------------------------------------------------------
          // Access adaptive optimization system
          // -------------------------------------------------------------------
        case AOS_HELP_ARG:  // -X:aos passed 'help' as an option
          if (VM.BuildForAdaptiveSystem) {
            Controller.processCommandLineArg("help");
          } else {
            VM.sysWrite("vm: nonadaptive configuration; -X:aos is not a legal prefix in this configuration\n");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;
        case AOS_ARG: // "-X:aos:arg" pass 'arg' as an option
          if (VM.BuildForAdaptiveSystem) {
            Controller.processCommandLineArg(arg);
          } else {
            VM.sysWrite("vm: nonadaptive configuration; -X:aos is not a legal prefix in this configuration\n");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;

          // ----------------------------------------------------
          // Access baseline compiler
          // ----------------------------------------------------
        case BASE_HELP_ARG:
          BaselineOptions.printHelp("-X:base:");
          break;
        case BASE_ARG: // "-X:base:arg"; pass 'arg' as an option
          BaselineCompiler.processCommandLineArg(p.value, arg);
          break;

          // ----------------------------------------------------
          // Access all 'logical' optimizing compilers
          // (both irc and recomp compilers)
          // ----------------------------------------------------
        case OPT_HELP_ARG:
          if (VM.BuildForAdaptiveSystem) {
            RuntimeCompiler.processOptCommandLineArg("-X:opt:", "help");
          } else {
            VM.sysWriteln("vm: You are not using a system that includes the optimizing compiler.");
            VM.sysWriteln("  Illegal command line argument prefix '-X:opt'");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;
        case OPT_ARG: // "-X:opt:arg"; pass 'arg' as an option
          if (VM.BuildForAdaptiveSystem) {
            RuntimeCompiler.processOptCommandLineArg("-X:opt:", arg);
            Controller.addOptCompilerOption("opt:" + arg);
          } else {
            VM.sysWriteln("vm: You are not using a system that includes the optimizing compiler.");
            VM.sysWriteln("  Illegal command line argument prefix '-X:opt'");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;

          // -------------------------------------------------------------------
          // Other arguments to the core VM
          // -------------------------------------------------------------------
        case HELP_ARG:  // -X:vm passed 'help' as an option
          Options.printHelp();
          break;
        case ARG: // "-X:vm:arg" pass 'arg' as an option
          if (!Options.process(arg)) {
            VM.sysWriteln("Unrecognized command line argument ", p.value, arg);
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          break;
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
    for (int i = 0; i < app_name_pos; i++) {
      String arg = args[i];
      PrefixType type = arg_types[i];
      if (type == PrefixType.INVALID_ARG) continue;
      Prefix p = findPrefix(type);
      if (DEBUG) VM.sysWriteln(" CommandLineArgs.processCLA(" + p + arg + " - " + type + ")");
      switch (type) {
        case ENVIRONMENT_ARG: // arguments of the form "-Dx=y"
        {
          int mid = arg.indexOf('=');
          if (mid == -1 || mid + 1 == arg.length()) {
            VM.sysWriteln("vm: bad property setting: \"", arg, "\"");
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          String name = arg.substring(0, mid);
          String value = arg.substring(mid + 1);
          System.getProperties().put(name, value);
        }
        break;

        case CLASSPATH_ARG:   // This is run in duplicate.
          // arguments of the form "-classpath a:b:c" or "-cp a:b:c"
          RVMClassLoader.setApplicationRepositories(arg);
          i++; // skip second argument to classpath
          break;

        case JAR_ARG:             // XXX This WILL BECOME  the second half of
          // handling JAR_ARG.  TODO
          // arguments of the form -jar <jarfile>
          java.util.jar.Manifest mf = null;
          try {
            java.util.jar.JarFile jf = new java.util.jar.JarFile(arg);
            mf = jf.getManifest();
          } catch (Exception e) {
            VM.sysWriteln("vm: IO Exception opening JAR file ", arg, ": ", e.getMessage());
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          if (mf == null) {
            VM.sysWriteln("The jar file is missing the manifest entry for the main class: ", arg);
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          String s = mf.getMainAttributes().getValue("Main-Class");
          if (s == null) {
            VM.sysWriteln("The jar file is missing the manifest entry for the main class: ", arg);
            VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
          }
          // maybe also load classes on the classpath list in the manifest
          RVMClassLoader.setApplicationRepositories(arg);

          args[i] = s;
          arg_types[i] = PrefixType.APPLICATION_ARG;
          app_prefix.count++;
          i++; // skip second argument to classpath
          break;
        case JAVAAGENT_ARG:
          /* Extract jar file from the -javaagent:<jar>[=options] form */
          int equalsPos = arg.indexOf("=");
          String jarPath;
          if (equalsPos != -1) {
            jarPath = arg.substring(0, equalsPos);
          } else {
            jarPath = arg;
          }
          String newClassPath = RVMClassLoader.getApplicationRepositories() + File.pathSeparator + jarPath;
          RVMClassLoader.setApplicationRepositories(newClassPath);
          break;
      }
    }

    // get application directives
    String[] arglist = getArgs(PrefixType.APPLICATION_ARG);

    // Debugging: write out application arguments
    if (DEBUG) {
      VM.sysWrite("VM.CommandLineArgs(): application arguments " + arglist.length + "\n");
      for (int i = 0; i < arglist.length; i++) {
        VM.sysWrite(i + ": \"" + arglist[i] + "\"\n");
      }
    }

    return arglist;
  }

  /**
   * Read the <code>argno</code>'th command line argument from the C argv
   * @param argno Number of argument sought
   * @param buf   Buffer to fill
   * @return number of bytes placed in buffer. -1 means buffer too small
   *         for argument to fit)
   */
  private static int sysArg(int argno, byte[] buf) {
    return sysCall.sysArg(argno, buf, buf.length);
  }

  /**
   * Primitive parsing of float/double values.
   * Done this way to enable us to parse command line arguments
   * early in VM booting before we are able to do the JNI call
   * that using {@code Double.valueOf} would require.
   * Does not support the full Java specification.
   * @param arg the float value to parse
   * @return value as float
   */
  public static float primitiveParseFloat(String arg) {
    byte[] b = stringToBytes("floating point", arg);
    return sysCall.sysPrimitiveParseFloat(b);
  }

  /**
   * Primitive parsing of byte/integer numeric values.
   * Done this way to enable us to parse command line arguments
   * early in VM booting before we are able call
   * {@code  Byte.parseByte} or {@code Integer.parseInt}.
   * @param arg the int or byte value to parse
   * @return value as int
   */
  public static int primitiveParseInt(String arg) {
    byte[] b = stringToBytes("integer or byte", arg);
    return sysCall.sysPrimitiveParseInt(b);
  }

  /**
   * Primitive parsing of memory sizes, with proper error handling,
   * and so on.
   * Works without needing Byte.parseByte or Integer.parseInt().
   *
   * At the moment, we have a maximum limit of an unsigned integer.  If
   *
   * @return Negative values on error.
   *      Otherwise, positive or zero values as bytes.
   * */
  public static long parseMemorySize(String sizeName, String sizeFlag, String defaultFactor, int roundTo,
                                     String fullArg, String subArg) {
    return sysCall.sysParseMemorySize(s2b(sizeName),
                                      s2b(sizeFlag),
                                      s2b(defaultFactor),
                                      roundTo,
                                      s2b(fullArg),
                                      s2b(subArg));
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
    @SuppressWarnings({"deprecation"})
    String getArg(int i) {
      int cnt;
      for (; ;) {
        cnt = sysArg(i, buf);
        if (cnt >= 0) {
          break;
        }
        buflen += 1024;
        buf = new byte[buflen];
      }
      if (VM.VerifyAssertions) VM._assert(cnt != -1);
      /*
       * Implementation note: Do NOT use the line below, which uses the
       * three-argument constructor for String, the one that respects the native
       * encoding (the platform's "default character set").
       *
       * Instead, we use the four-argument constructor, the one that takes a
       * HIBYTE parameter.
       *
       * 1) It is safe to do this; we *know* that all of the legal command-line
       * args use only characters within the ASCII character set.
       *
       * 2) The "default character set" version below will break. That is
       * because GNU Classpath's implementation of the
       * three-argument-constructor will fail if EncodingManager.getDecoder()
       * returns a null pointer. And EncodingManager.getDecoder() returns a null
       * pointer if it's called early on in the boot process (which the
       * default-character-set version below does).
       */
      //      return new String(buf, 0, cnt);
      return new String(buf, 0, 0, cnt);
    }

    int numArgs() {
      return sysArg(-1, buf);
    }
  }

  /** Convenience method for calling stringToBytes */
  private static byte[] s2b(String arg) {
    return stringToBytes(null, arg);
  }

  /**
   * Convert the string s (the "argument") to a null-terminated byte array.
   * This is used for converting arguments and for converting fixed
   * strings we pass down to lower commands.
   *
   * @param arg the argument to convert
   * @param argName text to print for error reporting.
   *
   * @return a byte array that represents <code>arg</code> as a
   * {@code null}-terminated C string. Returns {@code null} for a {@code null}
   * arg.
   */
  private static byte[] stringToBytes(String argName, String arg) {
    if (arg == null) {
      return null;
    }
    int len = arg.length();
    byte[] b = new byte[len + 1];

    for (int i = 0; i < len; i++) {
      char c = arg.charAt(i);
      if (c > 127) {
        VM.sysWrite("vm: Invalid character found in a");
        if (argName == null) {
          VM.sysWrite("n");
        } else {
          char v = argName.charAt(0);
          switch (v) {
            case'a':
            case'e':
            case'i':
            case'o':
            case'u':
              VM.sysWrite("n");
          }
          VM.sysWrite(" ", argName);
        }
        VM.sysWriteln(" argument: >", arg, "<");
        VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      }
      b[i] = (byte) c;
    }
    return b;
  }
}
