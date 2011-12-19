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
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment,
 * basic processing of command line arguments, and branching to VM.boot.
 *
 * The file "sys.C" contains the o/s support services to match
 * the entrypoints declared by SysCall.java
 *
 * 17 Oct 2000 The system code (everything except command line parsing in main)
 *             are moved into libvm.C to accomodate the JNI call CreateJVM
 *             (Ton Ngo)
 *             Add support to recognize quotes in command line arguments,
 *             standardize command line arguments with JDK 1.3.
 *             Eliminate order dependence on command line arguments
 *      Cleaned up memory management.  Made the handling of numeric args
 *      robust.
 */
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
#include "RunBootImage.h"       // Automatically generated for us by
                                // jbuild.linkBooter
#include "bootImageRunner.h"    // In tools/bootImageRunner
#include "cmdLine.h"            // Command line args.

// Interface to VM data structures.
//
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_GNU_CLASSPATH_VERSION
#define NEED_EXIT_STATUS_CODES  // Get EXIT_STATUS_BOGUS_COMMAND_LINE_ARG

#include <InterfaceDeclarations.h>


uint64_t initialHeapSize;       /* Declared in bootImageRunner.h */
uint64_t maximumHeapSize;       /* Declared in bootImageRunner.h */

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
    fprintf(SysTraceFile,"    -javaagent:<jarpath>[=<options>]\n");
    fprintf(SysTraceFile,"              load Java programming language agent, see java.lang.instrument\n");

    fprintf(SysTraceFile,"\n For more information see http://jikesrvm.sourceforge.net\n");

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
    fprintf(SysTraceFile, "%s %s\n",rvm_configuration, rvm_version);
}

static void
fullVersion()
{
    shortVersion();
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
        if (strequal(token, "-help") || strequal(token, "-?") ) {
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
            /* Canonicalize the argument, and pass it on to the heavy-weight
             * Java code that parses -X:gc:verbose */
            const size_t bufsiz = 20;
            char *buf = (char *) malloc(bufsiz);
            int ret = snprintf(buf, bufsiz, "-X:gc:verbose=%ld", level);
            if (ret < 0) {
                fprintf(stderr, "%s: Internal error processing the argument"
                        " \"%s\"\n", Me, token);
                exit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
            }
            if ((unsigned) ret >= bufsiz) {
                fprintf(SysTraceFile, "%s: \"%s\": %ld is too big a number"
                        " to process internally\n", Me, token, level);
                *fastExit = true;
                break;
            }

            CLAs[n_JCLAs++]=buf; // Leave buf allocated!
            continue;
        }

        if (strnequal(token, nonStandardArgs[MS_INDEX], 4)) {
            subtoken = token + 4;
            initialHeapSize
                = parse_memory_size("initial heap size", "ms", "", BYTES_IN_PAGE,
                                    token, subtoken, fastExit);
            if (*fastExit)
                break;
            continue;
        }

        if (strnequal(token, nonStandardArgs[MX_INDEX], 4)) {
            subtoken = token + 4;
            maximumHeapSize
                = parse_memory_size("maximum heap size", "mx", "", BYTES_IN_PAGE,
                                    token, subtoken, fastExit);
            if (*fastExit)
                break;
            continue;
        }

        if (strnequal(token, nonStandardArgs[SYSLOGFILE_INDEX],14)) {
            subtoken = token + 14;
            FILE* ftmp = fopen(subtoken, "a");
            if (!ftmp) {
                fprintf(SysTraceFile, "%s: can't open SysTraceFile \"%s\": %s\n", Me, subtoken, strerror(errno));
                *fastExit = true;
                break;
                continue;
            }
            fprintf(SysTraceFile, "%s: redirecting sysWrites to \"%s\"\n",Me, subtoken);
            SysTraceFile = ftmp;
            SysTraceFd = fileno(ftmp);
            continue;
        }
        if (strnequal(token, nonStandardArgs[BOOTIMAGE_CODE_FILE_INDEX], 6)) {
            bootCodeFilename = token + 6;
            continue;
        }
        if (strnequal(token, nonStandardArgs[BOOTIMAGE_DATA_FILE_INDEX], 6)) {
            bootDataFilename = token + 6;
            continue;
        }
        if (strnequal(token, nonStandardArgs[BOOTIMAGE_RMAP_FILE_INDEX], 6)) {
            bootRMapFilename = token + 6;
            continue;
        }

        //
        // All VM directives that are not handled here but in VM.java
        // must be identified.
        //

        // All VM directives that take one token
        if (strnequal(token, "-D", 2)
            || strnequal(token, nonStandardArgs[INDEX], 5)
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
            || strnequal(token, "-javaagent:", 11)
            || strnequal(token, nonStandardArgs[VMCLASSES_INDEX], 13)
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
 * then call createVM().
 */
int
main(int argc, const char **argv)
{
    Me            = strrchr((char *)*argv, '/') + 1;
    ++argv, --argc;
    initialHeapSize = heap_default_initial_size;
    maximumHeapSize = heap_default_maximum_size;
    
    setvbuf(stdout,NULL,_IONBF,0);
    setvbuf(stderr,NULL,_IONBF,0);

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

    if (DEBUG){
        printf("\nRunBootImage.main(): VM variable settings\n");
        printf("initialHeapSize %lu\nmaxHeapSize %lu\n"
               "bootCodeFileName |%s|\nbootDataFileName |%s|\n"
               "bootRmapFileName |%s|\n"
               "lib_verbose %d\n",
               (unsigned long) initialHeapSize,
               (unsigned long) maximumHeapSize,
               bootCodeFilename, bootDataFilename, bootRMapFilename,
               lib_verbose);
    }

    if (!bootCodeFilename) {
        fprintf(SysTraceFile, "%s: please specify name of boot image code file using \"-X:ic=<filename>\"\n", Me);
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    if (!bootDataFilename) {
        fprintf(SysTraceFile, "%s: please specify name of boot image data file using \"-X:id=<filename>\"\n", Me);
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    if (!bootRMapFilename) {
        fprintf(SysTraceFile, "%s: please specify name of boot image ref map file using \"-X:ir=<filename>\"\n", Me);
        return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

#ifdef __MACH__
    // Initialize timer information on OS/X
    (void) mach_timebase_info(&timebaseInfo);
#endif

    int ret = createVM();
    if (ret == 1) {
	fprintf(SysErrorFile, "%s: Could not create the virtual machine; goodbye\n", Me);
	exit(EXIT_STATUS_MISC_TROUBLE);
    }
    return 0; // this thread dies, but VM keeps running
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
                  const char *defaultFactor, // We now always default to bytes ("")
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
    userNum = strtold(subtoken, &endp);
    if (endp == subtoken) {
        fprintf(SysTraceFile, "%s: \"%s\": -X%s must be followed by a number.\n", Me, token, sizeFlag);
        *fastExit = true;
    }

    // First, set the factor appropriately, and make sure there aren't extra
    // characters at the end of the line.
    const char *factorStr = defaultFactor;
    long double factor = 0.0;   // 0.0 is a sentinel meaning Unset

    if (*endp == '\0') {
        /* no suffix.  Along with the Sun JVM, we now assume Bytes by
           default. (This is a change from  previous Jikes RVM behaviour.)  */
        factor = 1.0;
    } else if (strequal(endp, "pages") ) {
        factor = BYTES_IN_PAGE;
    /* Handle constructs like "M" and "K" */
    } else if ( endp[1] == '\0' ) {
        factorStr = endp;
    } else {
        fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a"
                " unit of memory size\n", Me, token, endp);
        *fastExit = true;
    }

    if (! *fastExit && factor == 0.0) {
        char e = *factorStr;
        if (e == 'g' || e == 'G') factor = 1024.0 * 1024.0 * 1024.0;
        else if (e == 'm' || e == 'M') factor = 1024.0 * 1024.0;
        else if (e == 'k' || e == 'K') factor = 1024.0;
        else if (e == '\0') factor = 1.0;
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
        if ( errno == ERANGE || userNum > (((long double) (UINT_MAX - roundTo))/factor) )
        {
            fprintf(SysTraceFile, "%s: \"%s\": out of range to represent internally\n", Me, subtoken);
            *fastExit = true;
        }
    }

    if (*fastExit) {
        fprintf(SysTraceFile, "\tPlease specify %s as follows:\n", sizeName);
        fprintf(SysTraceFile, "\t    in bytes, using \"-X%s<positive number>\",\n", sizeFlag);
        fprintf(SysTraceFile, "\tor, in kilobytes, using \"-X%s<positive number>K\",\n", sizeFlag);
        fprintf(SysTraceFile, "\tor, in virtual memory pages of %u bytes, using\n"
                "\t\t\"-X%s<positive number>pages\",\n", BYTES_IN_PAGE,
                sizeFlag);
        fprintf(SysTraceFile, "\tor, in megabytes, using \"-X%s<positive number>M\",\n", sizeFlag);
        fprintf(SysTraceFile, "\tor, in gigabytes, using \"-X%s<positive number>G\"\n", sizeFlag);
        fprintf(SysTraceFile, "  <positive number> can be a floating point value or a hex value like 0x10cafe0.\n");
        if (roundTo != 1) {
            fprintf(SysTraceFile, "  The # of bytes will be rounded up to a multiple of");
            if (roundTo == BYTES_IN_PAGE) fprintf(SysTraceFile, "\n  the virtual memory page size: ");
            fprintf(SysTraceFile, "%u\n", roundTo);
        }
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

