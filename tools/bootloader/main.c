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

/*
 * The initial loader of the VM.
 *
 * It deals with loading of the vm boot image into a memory segment,
 * basic processing of command line arguments, and branching to VM.boot.
 *
 * It also provides C runtime support for the virtual machine. The files
 * sys*.c contain the support services to match the entrypoints
 * declared by org.jikesrvm.runtime.Syscall .
 *
 */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <sys/signal.h>
#include <ctype.h>              // isspace()
#include <limits.h>             // UINT_MAX, ULONG_MAX, etc
#include <strings.h> /* bzero */
#include <libgen.h>  /* basename */
#include <sys/utsname.h>        // for uname(2)
#if (defined __linux__) || (defined (__SVR4) && defined (__sun))
#include <ucontext.h>
#include <signal.h>
#elif (defined __MACH__)
#include <sys/ucontext.h>
#include <signal.h>
#else
#include <sys/cache.h>
#include <sys/context.h>
// extern "C" char *sys_siglist[];
#endif

// Interface to VM data structures.
//
#include "bootloader.h"       /* Automatically generated for us by the build */

#include "sys.h"

void findMappable();
Extent determinePageSize();

// Definitions of constants for handling C command-line arguments

/* These definitions must remain in sync with nonStandardArgs, the array
 * immediately below. */
#define HELP_INDEX                    0
#define VERBOSE_INDEX                 HELP_INDEX + 1
#define VERBOSE_BOOT_INDEX            VERBOSE_INDEX + 1
#define MS_INDEX                      VERBOSE_BOOT_INDEX + 1
#define MX_INDEX                      MS_INDEX + 1
#define SYSLOGFILE_INDEX              MX_INDEX + 1
#define BOOTIMAGE_CODE_FILE_INDEX     SYSLOGFILE_INDEX + 1
#define BOOTIMAGE_DATA_FILE_INDEX     BOOTIMAGE_CODE_FILE_INDEX + 1
#define BOOTIMAGE_RMAP_FILE_INDEX     BOOTIMAGE_DATA_FILE_INDEX + 1
#define INDEX                      BOOTIMAGE_RMAP_FILE_INDEX + 1
#define GC_INDEX                      INDEX + 1
#define AOS_INDEX                     GC_INDEX + 1
#define IRC_INDEX                     AOS_INDEX + 1
#define RECOMP_INDEX                  IRC_INDEX + 1
#define BASE_INDEX                    RECOMP_INDEX + 1
#define OPT_INDEX                     BASE_INDEX + 1
#define VMCLASSES_INDEX               OPT_INDEX + 1
#define BOOTCLASSPATH_P_INDEX         VMCLASSES_INDEX + 1
#define BOOTCLASSPATH_A_INDEX         BOOTCLASSPATH_P_INDEX + 1
#define PROCESSORS_INDEX              BOOTCLASSPATH_A_INDEX + 1
#define VERBOSE_SIGNAL_HANDLING       PROCESSORS_INDEX + 1

#define numNonstandardArgs      VERBOSE_SIGNAL_HANDLING + 1

static const char* nonStandardArgs[numNonstandardArgs] = {
  "-X",
  "-X:verbose",
  "-X:verboseBoot=",
  "-Xms",
  "-Xmx",
  "-X:sysLogfile=",
  "-X:ic=",
  "-X:id=",
  "-X:ir=",
  "-X:vm",
  "-X:gc",
  "-X:aos",
  "-X:irc",
  "-X:recomp",
  "-X:base",
  "-X:opt",
  "-X:vmClasses=",
  "-Xbootclasspath/p:",
  "-Xbootclasspath/a:",
  "-X:availableProcessors=",
  "-X:verboseSignalHandling",
};

// a NULL-terminated list.
static const char* nonStandardUsage[] = {
  "  -X                         Print usage on nonstandard options",
  "  -X:verbose                 Print out additional lowlevel information",
  "  -X:verboseBoot=<number>    Print out messages while booting VM",
  "  -Xms<number><unit>         Initial size of heap",
  "  -Xmx<number><unit>         Maximum size of heap",
  "  -X:sysLogfile=<filename>   Write standard error message to <filename>",
  "  -X:ic=<filename>           Read boot image code from <filename>",
  "  -X:id=<filename>           Read boot image data from <filename>",
  "  -X:ir=<filename>           Read boot image ref map from <filename>",
  "  -X:vm:<option>             Pass <option> to virtual machine",
  "        :help                Print usage choices for -X:vm",
  "  -X:gc:<option>             Pass <option> on to GC subsystem",
  "        :help                Print usage choices for -X:gc",
  "  -X:aos:<option>            Pass <option> on to adaptive optimization system",
  "        :help                Print usage choices for -X:aos",
  "  -X:irc:<option>            Pass <option> on to the initial runtime compiler",
  "        :help                Print usage choices for -X:irc",
  "  -X:recomp:<option>         Pass <option> on to the recompilation compiler(s)",
  "        :help                Print usage choices for -X:recomp",
  "  -X:base:<option>           Pass <option> on to the baseline compiler",
  "        :help                print usage choices for -X:base",
  "  -X:opt:<option>            Pass <option> on to the optimizing compiler",
  "        :help                Print usage choices for -X:opt",
  "  -X:vmClasses=<path>        Load the org.jikesrvm.* and java.* classes",
  "                             from <path>, a list like one would give to the",
  "                             -classpath argument.",
  "  -Xbootclasspath/p:<cp>     (p)repend bootclasspath with specified classpath",
  "  -Xbootclasspath/a:<cp>     (a)ppend specified classpath to bootclasspath",
  "  -X:availableProcessors=<n> desired level of application parallelism (set",
  "                             -X:gc:threads to control gc parallelism)",
  "  -X:verboseSignalHandling   Print out information when handling software and",
  "                             hardware signals",
  NULL                         /* End of messages */
};

/*
 * What standard command line arguments are supported?
 */
static void
usage(void)
{
  CONSOLE_PRINTF("Usage: %s [-options] class [args...]\n", Me);
  CONSOLE_PRINTF("          (to execute a class)\n");
  CONSOLE_PRINTF("   or  %s [-options] -jar jarfile [args...]\n",Me);
  CONSOLE_PRINTF("          (to execute a jar file)\n");
  CONSOLE_PRINTF("\nwhere options include:\n");
  CONSOLE_PRINTF("    -cp -classpath <directories and zip/jar files separated by :>\n");
  CONSOLE_PRINTF("              set search path for application classes and resources\n");
  CONSOLE_PRINTF("    -D<name>=<value>\n");
  CONSOLE_PRINTF("              set a system property\n");
  CONSOLE_PRINTF("    -verbose[:class|:gc|:jni]\n");
  CONSOLE_PRINTF("              enable verbose output\n");
  CONSOLE_PRINTF("    -version  print version\n");
  CONSOLE_PRINTF("    -showversion\n");
  CONSOLE_PRINTF("              print version and continue\n");
  CONSOLE_PRINTF("    -fullversion\n");
  CONSOLE_PRINTF("              like version but with more information\n");
  CONSOLE_PRINTF("    -? -help  print this message\n");
  CONSOLE_PRINTF("    -X        print help on non-standard options\n");
  CONSOLE_PRINTF("    -javaagent:<jarpath>[=<options>]\n");
  CONSOLE_PRINTF("              load Java programming language agent, see java.lang.instrument\n");

  CONSOLE_PRINTF("    -ea[:[packagename]...|:classname] -enableassertions[:[packagename]...|:classname]\n");
  CONSOLE_PRINTF("              enable assertions, restricted to the provided packages and classes.\n");
  CONSOLE_PRINTF("              Applies to all if no packages and no classes are provided\n");
  CONSOLE_PRINTF("    -da[:[packagename]...|:classname] -disableassertions[:[packagename]...|:classname]\n");
  CONSOLE_PRINTF("              disable assertions, restricted to the provided packages and classes.\n");
  CONSOLE_PRINTF("              Applies to all if no packages and no classes are provided\n");
  CONSOLE_PRINTF("    -esa[:[packagename]...|:classname] -enablesystemassertions[:[packagename]...|:classname]\n");
  CONSOLE_PRINTF("              enable system assertions, restricted to the provided packages and classes.\n");
  CONSOLE_PRINTF("              Applies to all if no packages and no classes are provided\n");
  CONSOLE_PRINTF("    -dsa[:[packagename]...|:classname] -disablesystemassertions[:[packagename]...|:classname]\n");
  CONSOLE_PRINTF("              enable system assertions, restricted to the provided packages and classes.\n");
  CONSOLE_PRINTF("              Applies to all if no packages and no classes are provided\n");

  CONSOLE_PRINTF("\n For more information see http://jikesrvm.sourceforge.net\n");

  CONSOLE_PRINTF("\n");
}

/*
 * What nonstandard command line arguments are supported?
 */
static void
nonstandard_usage()
{
  const char * const *msgp = nonStandardUsage;
  CONSOLE_PRINTF("Usage: %s [options] class [args...]\n",Me);
  CONSOLE_PRINTF("          (to execute a class)\n");
  CONSOLE_PRINTF("where options include\n");
  for (; *msgp; ++msgp) {
    CONSOLE_PRINTF( "%s\n", *msgp);
  }
}

static void
shortVersion()
{
  CONSOLE_PRINTF( "%s %s\n",rvm_configuration, rvm_version);
}

static void
fullVersion()
{
  shortVersion();
  CONSOLE_PRINTF( "\thost config: %s\n\ttarget config: %s\n",
                  rvm_host_configuration, rvm_target_configuration);
  CONSOLE_PRINTF( "\theap default initial size: %u MiBytes\n",
                  heap_default_initial_size/(1024*1024));
  CONSOLE_PRINTF( "\theap default maximum size: %u MiBytes\n",
                  heap_default_maximum_size/(1024*1024));
}

/*
 * Identify all command line arguments that are VM directives.
 * VM directives are positional, they must occur before the application
 * class or any application arguments are specified.
 *
 * Identify command line arguments that are processed here:
 *   All heap memory directives. (e.g. -X:h).
 *   Any informational messages (e.g. -help).
 *
 * Input an array of command line arguments.
 * Return an array containing application arguments and VM arguments that
 *        are not processed here.
 * Side Effect  global varable JavaArgc is set.
 *
 * We reuse the array 'CLAs' to contain the return values.  We're
 * guaranteed that we will not generate any new command-line arguments, but
 * only consume them. So, n_JCLAs indexes 'CLAs', and it's always the case
 * that n_JCLAs <= n_CLAs, and is always true that n_JCLAs <= i (CLA index).
 *
 * By reusing CLAs, we avoid any unpleasantries with memory allocation.
 *
 * In case of trouble, we set fastExit.  We call exit(0) if no trouble, but
 * still want to exit.
 */
static const char ** processCommandLineArguments(JavaVMInitArgs *initArgs, const char *CLAs[], int n_CLAs)
{
  int n_JCLAs = 0;
  int startApplicationOptions = 0;
  int i;

  initArgs->nOptions = 0;
  // Note: can't use checkMalloc / sysMalloc here because boot record hasn't been read yet
  initArgs->options = (JavaVMOption *)malloc(sizeof(JavaVMOption) * n_CLAs);

  for (i = 0; i < n_CLAs; i++) {
    const char *token = CLAs[i];

    /* examining application options? */
    if (startApplicationOptions) {
      CLAs[n_JCLAs++]=token;
      continue;
    }
    /* pass on all command line arguments that do not start with a dash, '-'. */
    if (token[0] != '-') {
      CLAs[n_JCLAs++]=token;
      ++startApplicationOptions;
      continue;
    }

    /* we've not started processing application arguments, so argument
       is for the VM */
    initArgs->options[initArgs->nOptions].optionString = (char *)token;
    initArgs->options[initArgs->nOptions].extraInfo = NULL;
    initArgs->nOptions++;

    //   while (*argv && **argv == '-')    {
    if (STREQUAL(token, "-help") || STREQUAL(token, "-?") ) {
      usage();
      sysExit(0);
    }
    if (STREQUAL(token, nonStandardArgs[HELP_INDEX])) {
      nonstandard_usage();
      sysExit(0);
    }
    if (STREQUAL(token, nonStandardArgs[VERBOSE_INDEX])) {
      ++verbose;
      ++verboseSignalHandling;
      continue;
    }
    if (STREQUAL(token, nonStandardArgs[VERBOSE_SIGNAL_HANDLING])) {
      ++verboseSignalHandling;
      continue;
    }
    if (STRNEQUAL(token, nonStandardArgs[VERBOSE_BOOT_INDEX], 15)) {
      const char *subtoken = token + 15;
      errno = 0;
      char *endp;
      long vb = strtol(subtoken, &endp, 0);
      while (*endp && isspace(*endp)) // gobble trailing spaces
        ++endp;

      if (vb < 0) {
        ERROR_PRINTF("%s: \"%s\": You may not specify a negative verboseBoot value\n", Me, token);
        sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      } else if (errno == ERANGE
                 || vb > INT_MAX ) {
        ERROR_PRINTF("%s: \"%s\": too big a number to represent internally\n", Me, token);
        sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      } else if (*endp) {
        ERROR_PRINTF("%s: \"%s\": I don't recognize \"%s\" as a number\n", Me, token, subtoken);
        sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      }

      verboseBoot = vb;
      continue;
    }
    /*  Args that don't apply to us (from the Sun JVM); skip 'em. */
    if (STREQUAL(token, "-server"))
      continue;
    if (STREQUAL(token, "-client"))
      continue;
    if (STREQUAL(token, "-version")) {
      shortVersion();
      exit(0);
    }
    if (STREQUAL(token, "-fullversion")) {
      fullVersion();
      sysExit(0);
    }
    if (STREQUAL(token, "-showversion")) {
      shortVersion();
      continue;
    }
    if (STREQUAL(token, "-showfullversion")) {
      fullVersion();
      continue;
    }
    if (STREQUAL(token, "-findMappable")) {
      findMappable();
      sysExit(0);            // success, no?
    }
    if (STRNEQUAL(token, "-verbose:gc", 11)) {
      long level;         // a long, since we need to use strtol()
      if (token[11] == '\0') {
        level = 1;
      } else {
        /* skip to after the "=" in "-verbose:gc=<num>" */
        const char *subtoken = token + 12;
        errno = 0;
        char *endp;
        level = strtol(subtoken, &endp, 0);
        while (*endp && isspace(*endp)) // gobble trailing spaces
          ++endp;

        if (level < 0) {
          ERROR_PRINTF( "%s: \"%s\": You may not specify a negative GC verbose value\n", Me, token);
          ERROR_PRINTF( "%s: please specify GC verbose level as  \"-verbose:gc=<number>\" or as \"-verbose:gc\"\n", Me);
          sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        } else if (errno == ERANGE || level > INT_MAX ) {
          ERROR_PRINTF( "%s: \"%s\": Too big a number to represent internally\n", Me, token);
          ERROR_PRINTF( "%s: please specify GC verbose level as  \"-verbose:gc=<number>\" or as \"-verbose:gc\"\n", Me);
          sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        } else if (*endp) {
          ERROR_PRINTF( "%s: \"%s\": Didn't recognize \"%s\" as a number\n", Me, token, subtoken);
          ERROR_PRINTF( "%s: please specify GC verbose level as  \"-verbose:gc=<number>\" or as \"-verbose:gc\"\n", Me);
          sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
      }
      /* Canonicalize the argument, and pass it on to the heavy-weight
       * Java code that parses -X:gc:verbose */
      const size_t bufsiz = 20;
      char *buf = (char *) malloc(bufsiz);
      int ret = snprintf(buf, bufsiz, "-X:gc:verbose=%ld", level);
      if (ret < 0) {
        ERROR_PRINTF("%s: Internal error processing the argument"
                     " \"%s\"\n", Me, token);
        exit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
      }
      if ((unsigned) ret >= bufsiz) {
        ERROR_PRINTF( "%s: \"%s\": %ld is too big a number"
                      " to process internally\n", Me, token, level);
        sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      }

      CLAs[n_JCLAs++] = buf; // Leave buf allocated!
      continue;
    }

    if (STRNEQUAL(token, nonStandardArgs[MS_INDEX], 4)) {
      int fastExit = 0;
      const char *subtoken = token + 4;
      initialHeapSize
        = parse_memory_size("initial heap size", "ms", "", pageSize,
                            token, subtoken, &fastExit);
      if (fastExit)
        sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      continue;
    }

    if (STRNEQUAL(token, nonStandardArgs[MX_INDEX], 4)) {
      int fastExit = 0;
      const char *subtoken = token + 4;
      maximumHeapSize
        = parse_memory_size("maximum heap size", "mx", "", pageSize,
                            token, subtoken, &fastExit);
      if (fastExit)
        sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      continue;
    }

    if (STRNEQUAL(token, nonStandardArgs[SYSLOGFILE_INDEX],14)) {
      const char *subtoken = token + 14;
      FILE* ftmp = fopen(subtoken, "a");
      if (!ftmp) {
        ERROR_PRINTF( "%s: can't open SysTraceFile \"%s\": %s\n", Me, subtoken, strerror(errno));
        sysExit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      }
      CONSOLE_PRINTF( "%s: redirecting sysWrites to \"%s\"\n",Me, subtoken);
      SysTraceFile = ftmp;
      continue;
    }
    if (STRNEQUAL(token, nonStandardArgs[BOOTIMAGE_CODE_FILE_INDEX], 6)) {
      bootCodeFilename = (char *)(token + 6);
      continue;
    }
    if (STRNEQUAL(token, nonStandardArgs[BOOTIMAGE_DATA_FILE_INDEX], 6)) {
      bootDataFilename = (char *)(token + 6);
      continue;
    }
    if (STRNEQUAL(token, nonStandardArgs[BOOTIMAGE_RMAP_FILE_INDEX], 6)) {
      bootRMapFilename = (char *)(token + 6);
      continue;
    }

    //
    // All VM directives that are not handled here but in VM.java
    // must be identified.
    //

    // All VM directives that take one token
    if (STRNEQUAL(token, "-D", 2)
        || STRNEQUAL(token, nonStandardArgs[INDEX], 5)
        || STRNEQUAL(token, nonStandardArgs[GC_INDEX], 5)
        || STRNEQUAL(token, nonStandardArgs[AOS_INDEX],6)
        || STRNEQUAL(token, nonStandardArgs[IRC_INDEX], 6)
        || STRNEQUAL(token, nonStandardArgs[RECOMP_INDEX], 9)
        || STRNEQUAL(token, nonStandardArgs[BASE_INDEX],7)
        || STRNEQUAL(token, nonStandardArgs[OPT_INDEX], 6)
        || STREQUAL(token, "-verbose")
        || STREQUAL(token, "-verbose:class")
        || STREQUAL(token, "-verbose:gc")
        || STREQUAL(token, "-verbose:jni")
        || STRNEQUAL(token, "-javaagent:", 11)
        || STRNEQUAL(token, nonStandardArgs[VMCLASSES_INDEX], 13)
        || STRNEQUAL(token, nonStandardArgs[BOOTCLASSPATH_P_INDEX], 18)
        || STRNEQUAL(token, nonStandardArgs[BOOTCLASSPATH_A_INDEX], 18)
        || STRNEQUAL(token, nonStandardArgs[PROCESSORS_INDEX], 14)
        || STRNEQUAL(token, "-ea", 3)
        || STRNEQUAL(token, "-da", 3)
        || STRNEQUAL(token, "-esa", 4)
        || STRNEQUAL(token, "-dsa", 4)
        || STRNEQUAL(token, "-enableassertions", 17)
        || STRNEQUAL(token, "-disableassertions", 18)
        || STRNEQUAL(token, "-enablesystemassertions", 23)
        || STRNEQUAL(token, "-disablesystemassertions", 24))
    {
      CLAs[n_JCLAs++]=token;
      continue;
    }
    // All VM directives that take two tokens
    if (STREQUAL(token, "-cp") || STREQUAL(token, "-classpath")) {
      CLAs[n_JCLAs++]=token;
      token=CLAs[++i];
      CLAs[n_JCLAs++]=token;
      continue;
    }

    CLAs[n_JCLAs++]=token;
    ++startApplicationOptions; // found one that we do not recognize;
    // start to copy them all blindly
  } // for ()

  /* and set the count */
  JavaArgc = n_JCLAs;
  return CLAs;
}

/**
 * Parse command line arguments to find those arguments that
 *   1) affect the starting of the VM,
 *   2) can be handled without starting the VM, or
 *   3) contain quotes
 * then call createVM().
 */
int main(int argc, const char **argv)
{
  int vbufret, j, ret;
  JavaVMInitArgs initArgs;
  JavaVM *mainJavaVM;
  JNIEnv *mainJNIEnv;

  SysErrorFile = stderr;
  SysTraceFile = stdout;
  Me = strrchr(*argv, '/');
  if (Me == NULL) {
    Me = "RVM";
  } else {
    Me++;
  }

  ++argv, --argc;
  initialHeapSize = heap_default_initial_size;
  maximumHeapSize = heap_default_maximum_size;

  // Determine page size information early because
  // it's needed to parse command line options
  pageSize = (Extent) determinePageSize();
  if (pageSize <= 0) {
    ERROR_PRINTF("RunBootImage.main(): invalid page size %zu", pageSize);
    exit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
  }

  /*
   * Debugging: print out command line arguments.
   */
  if (TRACE) {
    TRACE_PRINTF("RunBootImage.main(): process %d command line arguments\n",argc);
    for (j = 0; j < argc; j++) {
      TRACE_PRINTF("\targv[%d] is \"%s\"\n",j, argv[j]);
     }
  }

  /* Initialize JavaArgc, JavaArgs and initArg */
  initArgs.version = JNI_VERSION_1_4;
  initArgs.ignoreUnrecognized = JNI_TRUE;
  JavaArgs = (char **)processCommandLineArguments(&initArgs, argv, argc);

  if (TRACE) {
    TRACE_PRINTF("RunBootImage.main(): after processCommandLineArguments: %d command line arguments\n", JavaArgc);
    for (j = 0; j < JavaArgc; j++) {
      TRACE_PRINTF("\tJavaArgs[%d] is \"%s\"\n", j, JavaArgs[j]);
    }
  }

  /* Verify heap sizes for sanity. */
  if (initialHeapSize == heap_default_initial_size &&
      maximumHeapSize != heap_default_maximum_size &&
      initialHeapSize > maximumHeapSize) {
    initialHeapSize = maximumHeapSize;
  }

  if (maximumHeapSize == heap_default_maximum_size &&
      initialHeapSize != heap_default_initial_size &&
      initialHeapSize > maximumHeapSize) {
    maximumHeapSize = initialHeapSize;
  }

  if (maximumHeapSize < initialHeapSize) {
    CONSOLE_PRINTF( "%s: maximum heap size %lu MiB is less than initial heap size %lu MiB\n",
                    Me, (unsigned long) maximumHeapSize/(1024*1024),
                    (unsigned long) initialHeapSize/(1024*1024));
    return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
  }

  TRACE_PRINTF("\nRunBootImage.main(): VM variable settings\n");
  TRACE_PRINTF("initialHeapSize %lu\nmaxHeapSize %lu\n"
           "bootCodeFileName \"%s\"\nbootDataFileName \"%s\"\n"
           "bootRmapFileName \"%s\"\n"
           "verbose %d\n",
           (unsigned long) initialHeapSize,
           (unsigned long) maximumHeapSize,
           bootCodeFilename, bootDataFilename, bootRMapFilename,
           verbose);

  if (!bootCodeFilename) {
    CONSOLE_PRINTF( "%s: please specify name of boot image code file using \"-X:ic=<filename>\"\n", Me);
    return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
  }

  if (!bootDataFilename) {
    CONSOLE_PRINTF( "%s: please specify name of boot image data file using \"-X:id=<filename>\"\n", Me);
    return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
  }

  if (!bootRMapFilename) {
    CONSOLE_PRINTF( "%s: please specify name of boot image ref map file using \"-X:ir=<filename>\"\n", Me);
    return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
  }

  ret = JNI_CreateJavaVM(&mainJavaVM, &mainJNIEnv, &initArgs);
  if (ret < 0) {
    ERROR_PRINTF("%s: Could not create the virtual machine; goodbye\n", Me);
    exit(EXIT_STATUS_MISC_TROUBLE);
  }
  return 0;
}


