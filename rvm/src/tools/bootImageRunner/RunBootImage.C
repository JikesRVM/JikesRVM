/*
 * (C) Copyright IBM Corp 2001,2002, 2003
 */
//$Id$

/*
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment,
 * basic processing of command line arguments, and branching to VM.boot. 
 * 
 * The file "sys.C" contains the o/s support services to match
 * the entrypoints declared by VM_SysCall.java
 *
 * @author Derek Lieber 03 Feb 1998
 * 17 Oct 2000 The system code (everything except command line parsing in main)
 *             are moved into libvm.C to accomodate the JNI call CreateJVM
 *             (Ton Ngo)
 * @modified Peter Sweeney 05 Jan 2001
 *             Add support to recognize quotes in command line arguments,
 *             standardize command line arguments with JDK 1.3.
 *             Eliminate order dependence on command line arguments
 * @modified Steven Augart
 * @date 18 Aug 2003
 *      Cleaned up memory management.  Made the handling of numeric args
 *      robust. 
 */
#include "config.h"

#include <stdio.h>
#include <assert.h>             // assert()
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
#if (defined __linux__)
  #include <asm/cache.h>
#endif
#if (defined __linux__) || (defined __MACH__)
#include <ucontext.h>
#include <signal.h>
#else
#include <sys/cache.h>
#include <sys/context.h>
// extern "C" char *sys_siglist[];
#endif
#include "RunBootImage.h"       // Automatically generated for us by
                                // jbuild.linkBooter 
#include "bootImageRunner.h"    // In rvm/src/tools/bootImageRunner
#include "cmdLine.h"            // Command line args.

// Interface to VM data structures.
//
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_GNU_CLASSPATH_VERSION
#define NEED_EXIT_STATUS_CODES  // Get EXIT_STATUS_BOGUS_COMMAND_LINE_ARG




#ifdef RVM_FOR_IBM
#include <AixLinkageLayout.h>
#endif

#include <InterfaceDeclarations.h>


uint64_t initialHeapSize;       /* Declared in bootImageRunner.h */
uint64_t maximumHeapSize;       /* Declared in bootImageRunner.h */
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
uint64_t initialStackSize;      /* Declared in bootImageRunner.h */
uint64_t stackGrowIncrement;      /* Declared in bootImageRunner.h */
uint64_t maximumStackSize;      /* Declared in bootImageRunner.h */
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES

int verboseBoot;                /* Declared in bootImageRunner.h */


static int DEBUG = 0;                   // have to set this from a debugger
static const unsigned BYTES_IN_PAGE = MMTk_Constants_BYTES_IN_PAGE;


static bool strequal(const char *s1, const char *s2);
static bool strnequal(const char *s1, const char *s2, size_t n);

/*
 * What standard command line arguments are supported?
 */
static void 
usage(void) 
{
    fprintf(SysTraceFile,"Usage: %s [-options] class [args...]\n", Me);
    fprintf(SysTraceFile,"          (to execute a class)\n");
    fprintf(SysTraceFile,"   or  %s [-options] -jar jarfile [args...]\n",Me);
    fprintf(SysTraceFile,"          (to execute a jar file)\n");
    fprintf(SysTraceFile,"\nwhere options include:\n");
    fprintf(SysTraceFile,"    -cp -classpath <directories and zip/jar files separated by :>\n");
    fprintf(SysTraceFile,"              set search path for application classes and resources\n");
    fprintf(SysTraceFile,"    -D<name>=<value>\n");
    fprintf(SysTraceFile,"              set a system property\n");
    fprintf(SysTraceFile,"    -verbose[:class|:gc|:jni]\n");
    fprintf(SysTraceFile,"              enable verbose output\n");
    fprintf(SysTraceFile,"    -version  print version\n");
    fprintf(SysTraceFile,"    -showversion\n");
    fprintf(SysTraceFile,"              print version and continue\n");
    fprintf(SysTraceFile,"    -fullversion\n");
    fprintf(SysTraceFile,"              like version but with more information\n");
    fprintf(SysTraceFile,"    -? -help  print this message\n");
    fprintf(SysTraceFile,"    -X        print help on non-standard options\n");

    fprintf(SysTraceFile,"\n For more information look at URL: www.ibm.com/developerworks/oss/jikesrvm\n");

    fprintf(SysTraceFile,"\n");
}

/*
 * What nonstandard command line arguments are supported?
 */
static void 
nonstandard_usage() 
{
    fprintf(SysTraceFile,"Usage: %s [options] class [args...]\n",Me);
    fprintf(SysTraceFile,"          (to execute a class)\n");
    fprintf(SysTraceFile,"where options include\n");
    for (const char * const *msgp = nonStandardUsage; *msgp; ++msgp) {
        fprintf(SysTraceFile, *msgp);
        fprintf(SysTraceFile,"\n");
    }
}

static void
shortVersion()
{
    fprintf(SysTraceFile, "%s %s using GNU Classpath %s\n",rvm_configuration, rvm_version, classpath_version);
}

static void
fullVersion()
{
    shortVersion();
    fprintf(SysTraceFile, "\tcvs timestamp: %s\n", rvm_cvstimestamp);
    fprintf(SysTraceFile, "\thost config: %s\n\ttarget config: %s\n",
            rvm_host_configuration, rvm_target_configuration);
    fprintf(SysTraceFile, "\theap default initial size: %u MiBytes\n",
            heap_default_initial_size/(1024*1024));
    fprintf(SysTraceFile, "\theap default maximum size: %u MiBytes\n",
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
static const char ** 
processCommandLineArguments(const char *CLAs[], int n_CLAs, bool *fastExit) 
{
    int n_JCLAs = 0;
    bool startApplicationOptions = false;
    const char *subtoken;

    for (int i = 0; i < n_CLAs; i++) {
        const char *token = CLAs[i];
        subtoken = NULL;        // strictly, not needed.

        // examining application options?
        if (startApplicationOptions) {
            CLAs[n_JCLAs++]=token;
            continue;
        }
        // pass on all command line arguments that do not start with a dash, '-'.
        if (token[0] != '-') {
            CLAs[n_JCLAs++]=token;
            ++startApplicationOptions;
            continue;
        }

        //   while (*argv && **argv == '-')    {
        if (strequal(token, "-help") || strequal(token, "--help") || strequal(token, "-?") ) {
            usage();
            *fastExit = true;
            break;
        }
        if (strequal(token, nonStandardArgs[HELP_INDEX])) {
            nonstandard_usage();
            *fastExit = true; break;
        }
        if (strequal(token, nonStandardArgs[VERBOSE_INDEX])) {
            ++lib_verbose;
            continue;
        }
        if (strnequal(token, nonStandardArgs[VERBOSE_BOOT_INDEX], 15)) {
            subtoken = token + 15;
            errno = 0;
            char *endp;
            long vb = strtol(subtoken, &endp, 0);
            while (*endp && isspace(*endp)) // gobble trailing spaces
                ++endp;

            if (vb < 0) {
                fprintf(SysTraceFile, "%s: \"%s\": You may not specify a negative verboseBoot value\n", Me, token);
                *fastExit = true; break;
            } else if (errno == ERANGE
                       || vb > INT_MAX ) {
                fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", Me, token);
                *fastExit = true; break;
            } else if (*endp) {
                fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a number\n", Me, token, subtoken);               
                *fastExit = true; break;
            }
            
            verboseBoot = vb;
            continue;
        }
        /*  Args that don't apply to us (from the Sun JVM); skip 'em. */
        if (strequal(token, "-server"))
            continue;
        if (strequal(token, "-client")) 
            continue;
        if (strequal(token, "-version")) {
            shortVersion();
            // *fastExit = true; break;
            exit(0);
        }
        if (strequal(token, "-fullversion")) {
            fullVersion();
            // *fastExit = true; break;
            exit(0);
        }
        if (strequal(token, "-showversion")) {
            shortVersion();
            continue;
        }
        if (strequal(token, "-showfullversion")) {
            fullVersion();
            continue;
        }
        if (strequal(token, "-findMappable")) {
            findMappable();
            // *fastExit = true; break;
            exit(0);            // success, no?
        }
        if (strnequal(token, "-verbose:gc", 11)) {
            long level;         // a long, since we need to use strtol()
            if (token[11] == '\0') {
                level = 1;
            } else {
                /* skip to after the "=" in "-verbose:gc=<num>" */
                subtoken = token + 12;
                errno = 0;
                char *endp;
                level = strtol(subtoken, &endp, 0); 
                while (*endp && isspace(*endp)) // gobble trailing spaces
                    ++endp;

                if (level < 0) {
                    fprintf(SysTraceFile, "%s: \"%s\": You may not specify a negative GC verbose value\n", Me, token);
                    *fastExit = true; 
                } else if (errno == ERANGE || level > INT_MAX ) {
                    fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", Me, token);
                    *fastExit = true;
                } else if (*endp) {
                    fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a number\n", Me, token, subtoken);           
                    *fastExit = true;
                }
                if (*fastExit) {
                    fprintf(SysTraceFile, "%s: please specify GC verbose level as  \"-verbose:gc=<number>\" or as \"-verbose:gc\"\n", Me);
                    break;
                }
            }
            // canonicalize the argument
            char *buf = (char *) malloc(20); 
            sprintf(buf, "-X:gc:verbose=%ld", level);
            CLAs[n_JCLAs++]=buf;
            continue;
        }

        if (strnequal(token, nonStandardArgs[INITIAL_HEAP_INDEX], 5)) {
            subtoken = token + 5;
            fprintf(SysTraceFile, "%s: Warning: -X:h=<number> is deprecated; please use \"-Xms\" and/or \"-Xmx\".\n", Me);
            /* Does the arg finish with a magnitude expression (a non-numeric
             * character)?  If so, don't stick on 
             * another one.   This is just parsing so that we generate a nicer
             * warning message, by the way -- nothing magic goes on here.  */
            size_t sublen = strlen(subtoken); // length of subtoken
            /* Avoid examining subtoken[-1], not that we actually would care,
               but I like the idea of explicitly setting megaChar to '\0'
               instead of to '=', which is what we'd get without the
               conditional operator here. */
            char megaChar = sublen > 0 ? subtoken[sublen - 1] : '\0';
            const char *megabytes;
            if (megaChar == 'm' || megaChar == 'M')
                megabytes = "";
            else
                megabytes = "M";
            fprintf(SysTraceFile, "\tI am interpreting -X:h=%s as if it was -Xms%s%s.\n", subtoken, subtoken, megabytes);
            fprintf(SysTraceFile, "\tTo set a fixed heap size H, you must use -XmsH -X:gc:variableSizeHeap=false\n");
            /* Go ahead and set the initial heap size, now. */
            // size. 
            token = nonStandardArgs[MS_INDEX];
        }

        if (strnequal(token, nonStandardArgs[MS_INDEX], 4)) {
            subtoken = token + 4;
            initialHeapSize 
                = parse_memory_size("initial heap size", "ms", "M", BYTES_IN_PAGE,
                                    token, subtoken, fastExit);
            continue;
        }

        if (strnequal(token, nonStandardArgs[MX_INDEX], 4)) {
            subtoken = token + 4;
            maximumHeapSize 
                = parse_memory_size("maximum heap size", "mx", "M", BYTES_IN_PAGE,
                                    token, subtoken, fastExit);
            continue;
        }

#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
        if (strnequal(token, nonStandardArgs[SS_INDEX], 4)) {
            subtoken = token + 4;
            initialStackSize
                = parse_memory_size("initial stack size", "ss", "K", 4, 
                                    token, subtoken, fastExit);
            continue;
        }

        if (strnequal(token, nonStandardArgs[SG_INDEX], 4)) {
            subtoken = token + 4;
            stackGrowIncrement
                = parse_memory_size("stack growth increment", "sg", "K", 4, 
                                    token, subtoken, fastExit);
            continue;
        }

        if (strnequal(token, nonStandardArgs[SX_INDEX], 4)) {
            subtoken = token + 4;
            maximumStackSize
                = parse_memory_size("maximum stack size", "sx", "K", 4,
                                    token, subtoken, fastExit);
            continue;
        }
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES

        if (strnequal(token, nonStandardArgs[SYSLOGFILE_INDEX],14)) {
            subtoken = token + 14;
            FILE* ftmp = fopen(subtoken, "a");
            if (!ftmp) {
                fprintf(SysTraceFile, "%s: can't open SysTraceFile \"%s\": %s\n", Me, subtoken, strerror(errno));
                *fastExit = true;
                continue;
            }
            fprintf(SysTraceFile, "%s: redirecting sysWrites to \"%s\"\n",Me, subtoken);
            SysTraceFile = ftmp;
            SysTraceFd = fileno(ftmp);
            continue;
        }
        if (strnequal(token, nonStandardArgs[BOOTIMAGE_FILE_INDEX], 5)) {
            bootFilename = token + 5;
            continue;
        }


        //
        // All VM directives that are not handled here but in VM.java
        // must be identified.
        //

        // All VM directives that take one token
        if (strnequal(token, "-D", 2) 
            || strnequal(token, nonStandardArgs[VM_INDEX], 5) 
            || strnequal(token, nonStandardArgs[GC_INDEX], 5) 
            || strnequal(token, nonStandardArgs[AOS_INDEX],6) 
            || strnequal(token, nonStandardArgs[IRC_INDEX], 6) 
            || strnequal(token, nonStandardArgs[RECOMP_INDEX], 9) 
            || strnequal(token, nonStandardArgs[BASE_INDEX],7)  
            || strnequal(token, nonStandardArgs[OPT_INDEX], 6) 
            || strequal(token, "-verbose")
            || strequal(token, "-verbose:class")
            || strequal(token, "-verbose:gc") 
            || strequal(token, "-verbose:jni") 
            || strnequal(token, nonStandardArgs[VMCLASSES_INDEX], 13)  
            || strnequal(token, nonStandardArgs[CPUAFFINITY_INDEX], 15) 
            || strnequal(token, nonStandardArgs[PROCESSORS_INDEX], 14)) 
        {
            CLAs[n_JCLAs++]=token;
            continue;
        }
        // All VM directives that take two tokens
        if (strequal(token, "-cp") || strequal(token, "-classpath")) {
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

/*
 * Parse command line arguments to find those arguments that 
 *   1) affect the starting of the VM, 
 *   2) can be handled without starting the VM, or
 *   3) contain quotes
 * then call createJVM().
 */
int
main(int argc, const char **argv)
{
    Me            = strrchr(*argv, '/') + 1;
    ++argv, --argc;
    initialHeapSize = heap_default_initial_size;
    maximumHeapSize = heap_default_maximum_size;

#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
    const unsigned stack_minimum_size         = 
        VM_Constants_STACK_SIZE_MIN;
    const unsigned stack_default_initial_size = 
        VM_Constants_STACK_SIZE_NORMAL_DEFAULT;
    const unsigned stack_grow_minimum_increment = 
        VM_Constants_STACK_SIZE_GROW_MIN;
    const unsigned stack_default_grow_increment = 
        VM_Constants_STACK_SIZE_GROW_DEFAULT;
    const unsigned stack_default_maximum_size = 
        VM_Constants_STACK_SIZE_MAX_DEFAULT;
    
    initialStackSize = stack_default_initial_size;
    stackGrowIncrement = stack_default_grow_increment;
    maximumStackSize = stack_default_maximum_size;
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES
  
    /*
     * Debugging: print out command line arguments.
     */
    if (DEBUG) {
        printf("RunBootImage.main(): process %d command line arguments\n",argc);
        for (int j=0; j<argc; j++) {
            printf("\targv[%d] is \"%s\"\n",j, argv[j]);
        }
    }
  
    // call processCommandLineArguments().
    bool fastBreak = false;
    // Sets JavaArgc
    JavaArgs = processCommandLineArguments(argv, argc, &fastBreak);
    if (fastBreak) {
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }

    if (DEBUG) {
        printf("RunBootImage.main(): after processCommandLineArguments: %d command line arguments\n", JavaArgc);
        for (int j = 0; j < JavaArgc; j++) {
            printf("\tJavaArgs[%d] is \"%s\"\n", j, JavaArgs[j]);
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
        fprintf(SysTraceFile, "%s: maximum heap size %lu MiB is less than initial heap size %lu MiB\n", 
                Me, (unsigned long) maximumHeapSize/(1024*1024), 
                (unsigned long) initialHeapSize/(1024*1024));
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
    /* Verify stack sizes for sanity. */
    if (initialStackSize == stack_default_initial_size &&
        maximumStackSize != stack_default_maximum_size &&
        initialStackSize > maximumStackSize) {
        initialStackSize = maximumStackSize;
    }

    if (maximumStackSize == stack_default_maximum_size &&
        initialStackSize != stack_default_initial_size &&
        initialStackSize > maximumStackSize) {
        maximumStackSize = initialStackSize;
    }

    if (maximumStackSize < initialStackSize) {
        fprintf(SysTraceFile, "%s: maximum stack size %lu KiB is less than initial stack size %lu KiB\n", 
                Me, (unsigned long) maximumStackSize/1024, 
                (unsigned long) initialStackSize/1024);
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    if (initialStackSize < stack_minimum_size) {
        fprintf(SysTraceFile, "%s: initial stack size %lu KiB is less than minimum stack size %lu KiB\n", 
                Me, (unsigned long) initialStackSize/1024, 
                (unsigned long) stack_minimum_size/1024);
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    if (stackGrowIncrement < stack_grow_minimum_increment) {
        fprintf(SysTraceFile, "%s: stack growth increment %lu KiB is less than minimum growth increment %lu KiB\n", 
                Me, (unsigned long) stackGrowIncrement/1024, 
                (unsigned long) stack_grow_minimum_increment/1024);
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES

    if (DEBUG){
        printf("\nRunBootImage.main(): VM variable settings\n");
        printf("initialHeapSize %lu\nmaxHeapSize %lu\n"
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
               "initialStackSize %lu\n"
               "stackGrowIncrement %lu\n"
               "maxStackSize %lu\n"
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES
               "bootFileName |%s|\nlib_verbose %d\n",
               (unsigned long) initialHeapSize, 
               (unsigned long) maximumHeapSize, 
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
               (unsigned long) initialStackSize, 
               (unsigned long) stackGrowIncrement,
               (unsigned long) maximumStackSize,
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES
               bootFilename, lib_verbose);
    }

    if (!bootFilename) {
#ifdef RVM_BOOTIMAGE
        bootFilename = RVM_BOOTIMAGE;
#endif
    }

    if (!bootFilename) {
        fprintf(SysTraceFile, "%s: please specify name of boot image file using \"-i<filename>\"\n", Me);
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    int ret = createJVM(0);
    assert(ret == 1);           // must be 1 (error status for this func.)
    
  
    fprintf(SysErrorFile, "%s: Could not create the Java Virtual Machine; goodbye\n", Me);
    exit(1);
}


static bool 
strequal(const char *s1, const char *s2)
{
    return strcmp(s1, s2) == 0;
}


static bool 
strnequal(const char *s1, const char *s2, size_t n)
{
    return strncmp(s1, s2, n) == 0;
}

/** Return a # of bytes, rounded up to the next page size.  Setting fastExit
 *  means trouble or failure.  If we set fastExit we'll also return the value
 *  0U.
 *
 * NOTE: Given the context, we treat "MB" as having its
 * historic meaning of "MiB" (2^20), rather than its 1994 ISO
 * meaning, which would be a factor of 10^7. 
 */
extern "C"
unsigned int
parse_memory_size(const char *sizeName, /*  "initial heap" or "maximum heap" or
                                            "initial stack" or "maximum stack" 
                                        */ 
                  const char *sizeFlag, // "-Xms" or "-Xmx" or
                                        // "-Xss" or "-Xsg" or "-Xsx" 
                  const char *defaultFactor, // "M" or "K" are used
                  unsigned roundTo,  // Round to PAGE_SIZE_BYTES or to 4.
                  const char *token /* e.g., "-Xms200M" or "-Xms200" */,
                  const char *subtoken /* e.g., "200M" or "200" */,
                  bool *fastExit)
{
    errno = 0;
    long double userNum;
    char *endp;                 /* Should be const char *, but if we do that,
                                   then the C++ compiler complains about the
                                   prototype for strtold() or strtod().   This
                                   is probably a bug in the specification
                                   of the prototype. */
#ifdef HAVE_CXX_STRTOLD
        /* This gets around some nastiness in AIX 5.1, where <stdlib.h> only
           prototypes strtold() if we're using the 96 or 128 bit "long double"
           type.  Which is an option to the IBM Visual Age C compiler, but
           apparently not (yet) available for GCC.  */

    userNum = strtold(subtoken, &endp);
#else
    userNum = strtod(subtoken, &endp);
#endif

    if (endp == subtoken) {
        fprintf(SysTraceFile, "%s: \"%s\": -X%s must be followed"
                " by a number.\n", Me, token, sizeFlag);
        *fastExit = true;
    }
    
    // First, set the factor appropriately, and make sure there aren't extra
    // characters at the end of the line.
    const char *factorStr = defaultFactor;
    long double factor = 0.0;   // 0.0 is a sentinel meaning Unset

    if (*endp == '\0') {
        /* no suffix.  Here, -Xms and -Xmx differ from the Sun JVM, by assuming
           megabytes -- historical compat. with previous
           Jikes RVM behaviour.  The Sun JVM assumes that no suffix implies
           bytes.   Other flags specify their own default factors. */ 
    } else if (strequal(endp, "c")) {
        /* The "dd" Unix utility has used "c" for "characters" to mean what we
           mean by bytes, so we go ahead and make "c" legal syntax.  This is
           handled specially, since we don't want to treat "cib" or "cB" as
           legal -- that would just be sick. */
        factor = 1.0;
    } else if (strequal(endp, "pages") || strequal(endp, "p")) {
        factor = BYTES_IN_PAGE;
    } else if (   /* Handle constructs like "M" and "K" */
                  endp[1] == '\0' 
                  /* Handle constructs like "MiB" or "MB".*/
               || strequal(endp + 2, "iB") || strequal(endp + 2, "ib") 
               || strequal(endp + 2, "B") || strequal(endp + 2, "b") ) {
        factorStr = endp;
    } else {
        fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a"
                " unit of memory size\n", Me, token, endp);          
        *fastExit = true;
    }

    if (! *fastExit && factor == 0.0) {
        char e = *factorStr;
        /* At this time, with our using a 32-bit quantity to indicate memory
         * size, we can't use T and are unlikely to use G.  But it doesn't
         * hurt to have the code in here, since a double is guaranteed to be
         * able to represent quantities of the magnitude 2^40, and this only
         * wastes a couple of instructions, once during the program run.  When
         * we go up to 64 bits, we'll be glad.  I think. --steve augart */
        if (e == 't' || e == 'T')
            /* We'll always recognize T, but we don't show it in the help
               message unless we're on a 64-bit platform, since it's not
               useful on a 32-bit platform. */
            factor = 1024.0 * 1024.0 * 1024.0 * 1024.0; // Tebibytes
        else if (e == 'g' || e == 'G')
            factor = 1024.0 * 1024.0 * 1024.0; // Gibibytes
        else if (e == 'm' || e == 'M')
            factor = 1024.0 * 1024.0; // Mebibytes
        else if (e == 'k' || e == 'K')
            factor = 1024.0;    // Kibibytes
        /* b for bytes.  Seems mnemonic.  Hotspot interprets the absence of a
           suffix to mean bytes, but that conflicts with historic Jikes RVM
           practice. 

           Potential Conflict: 
           "dd" uses "b" to mean "blocks", a factor of 512.  This makes sense
           in a context of a disk utility, but little sense in a context of
           VM memory sizes. */
        else if (e == 'b' || e == 'B') 
            factor = 1.0;
        else {
            fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a"
                    " unit of memory size\n", Me, token, factorStr);          
            *fastExit = true;
        }
    }

    // Note: on underflow, strtod() returns 0.
    if (!*fastExit) {
        if (userNum <= 0.0) {
            fprintf(SysTraceFile, 
                    "%s: You may not specify a %s %s;\n", 
                    Me, userNum < 0.0 ? "negative" : "zero", sizeName);
            fprintf(SysTraceFile, "\tit just doesn't make any sense.\n");
            *fastExit = true;
        }
    } 

    if (!*fastExit) {
        if (   errno == ERANGE 
            || userNum > ((long double) (UINT_MAX - roundTo)/factor)) 
        {
            fprintf(SysTraceFile, "%s: \"%s\": out of range"
                    " to represent internally\n", Me, subtoken);
            *fastExit = true;
        }
    }

    if (*fastExit) {
        fprintf(SysTraceFile, "\tPlease specify %s size as follows:\n"
                "\t   (in megabytes) using \"-X%s<positive number>M\",\n", 
                sizeName, sizeFlag);
        fprintf(SysTraceFile, 
                "\tor (in kilobytes) using \"-X%s<positive number>K\",\n",
                sizeFlag);
        fprintf(SysTraceFile, 
                "\tor (in bytes) using \"-X%s<positive number>B\"\n",
                sizeFlag);
        fprintf(SysTraceFile,
                "\tor (in gigabytes) using \"-X%s<positive number>G\",\n",
                sizeFlag);
        fprintf(SysTraceFile,
                "\tor (in virt. memory pages of %u bytes) using "
                "\"-X%s<positive number>pages\"", BYTES_IN_PAGE, sizeFlag);
#ifdef RVM_FOR_64_ADDR
        fprintf(SysTraceFile, ",\n");
        fprintf(SysTraceFile, 
                "\tor (in terabytes) using \"-X%s<positive number>T\"",
                sizeFlag);
#endif // RVM_FOR_64_ADDR
        fprintf(SysTraceFile, ".\n");
        fprintf(SysTraceFile,
                "\t<positive number> can be a floating point value.\n"
                "\tThe # of bytes will be rounded up\n"
                " to a multiple of ");
        if (roundTo == BYTES_IN_PAGE)
            fprintf(SysTraceFile, "the virtual memory page size: ");
        fprintf(SysTraceFile, "%u\n", roundTo);
        return 0U;              // Distinguished value meaning trouble.
    } 
    long double tot_d = userNum * factor;
    assert(tot_d <= (UINT_MAX - roundTo));
    assert(tot_d >= 1);
    
    unsigned tot = (unsigned) tot_d;
    if (tot % roundTo) {
        unsigned newTot = tot + roundTo - (tot % roundTo);
        fprintf(SysTraceFile, 
                "%s: Rounding up %s size from %u bytes to %u,\n"
                "\tthe next multiple of %u bytes%s\n", 
                Me, sizeName, tot, newTot, roundTo,
                roundTo == BYTES_IN_PAGE ?
                           ", the virtual memory page size" : "");
        tot = newTot;
    }
    return tot;
}

