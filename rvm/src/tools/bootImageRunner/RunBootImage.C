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
 *	       Add support to recognize quotes in command line arguments,
 *	       standardize command line arguments with JDK 1.3.
 *	       Eliminate order dependence on command line arguments
 * @modified Steven Augart
 * @date 18 Aug 2003
 *	Cleaned up memory management.  Made the handling of numeric args
 *	robust. 
 */
#include "config.h"

#include <stdio.h>
#include <assert.h>		// assert()
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <sys/signal.h>
#include <ctype.h>		// isspace()
#include <limits.h>		// INT_MAX
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
#include "RunBootImage.h"	// Automatically generated for us by
				// jbuild.linkBooter 
#include "bootImageRunner.h"	// In rvm/src/tools/bootImageRunner
#include "cmdLine.h"		// Command line args.

// Interface to VM data structures.
//
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_GNU_CLASSPATH_VERSION

#ifdef RVM_FOR_IBM
#include <AixLinkageLayout.h>
#endif

#include <InterfaceDeclarations.h>


unsigned initialHeapSize;  /* Declared in bootImageRunner.h */
unsigned maximumHeapSize;  /* Declared in bootImageRunner.h */

int verboseBoot;		/* Declared in bootImageRunner.h */

/* See VM.exitStatusBogusCommandLineArg in VM.java.  
 * If you change this value, change it there too. */
const int EXIT_STATUS_BOGUS_COMMAND_LINE_ARG = 98;

static int DEBUG = 0;			// have to set this from a debugger

static bool strequal(const char *s1, const char *s2);
static bool strnequal(const char *s1, const char *s2, size_t n);
static unsigned int parse_heap_size(
    const char *sizeName, const char *sizeFlag, 
    const char *token, const char *subtoken, bool *fastExit);

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
    for (int i=0; i<numNonStandardUsageLines; i++) {
	fprintf(SysTraceFile,nonStandardUsage[i]);
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
    fprintf(SysTraceFile, "\theap default initial size: %u MBytes\n",
	    heap_default_initial_size/(1024*1024));
    fprintf(SysTraceFile, "\theap default maximum size: %u MBytes\n",
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
static char ** 
processCommandLineArguments(char *CLAs[], int n_CLAs, bool *fastExit) 
{
    int n_JCLAs = 0;
    bool startApplicationOptions = false;
    char *subtoken;

    for (int i = 0; i < n_CLAs; i++) {
	char *token = CLAs[i];
	subtoken = NULL;	// strictly, not needed.

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
	    } else if (errno == ERANGE || vb > INT_MAX ) {
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
	    exit(0);		// success, no?
	}
	if (strnequal(token, "-verbose:gc", 11)) {
	    long level;		// a long, since we need to use strtol()
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
	    /* Does the arg finish with an M or m?  If so, don't stick on
	     * another one. */
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
	    goto set_initial_heap_size;
	}

	if (strnequal(token, nonStandardArgs[MS_INDEX], 4)) {
	    subtoken = token + 4;
	set_initial_heap_size:
	    initialHeapSize 
		= parse_heap_size("initial", "ms", token, subtoken, fastExit);
	    continue;
	}

	if (strnequal(token, nonStandardArgs[MX_INDEX], 4)) {
	    subtoken = token + 4;
	    maximumHeapSize 
		= parse_heap_size("maximum", "mx", token, subtoken, fastExit);
	    continue;
	}

	if (strnequal(token, nonStandardArgs[SYSLOGFILE_INDEX],14)) {
	    subtoken = token + 14;
	    FILE* ftmp = fopen(subtoken, "a");
	    if (!ftmp) {
		fprintf(SysTraceFile, "%s: can't open SysTraceFile \"%s\": %s\n", Me, subtoken, strerror(errno));
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
main(int argc, char **argv)
{
    Me            = basename(*argv++);
    --argc;
    initialHeapSize = heap_default_initial_size;
    maximumHeapSize = heap_default_maximum_size;
  
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
	fprintf(SysTraceFile, "%s: maximum heap size %d is less than initial heap size %d\n", 
		Me, maximumHeapSize/(1024*1024), initialHeapSize/(1024*1024));
	return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    if (DEBUG){
	printf("\nRunBootImage.main(): VM variable settings\n");
	printf("initialHeapSize %d\nmaxHeapSize %d\nbootFileName |%s|\nlib_verbose %d\n",
	       initialHeapSize, maximumHeapSize, bootFilename, lib_verbose);
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
    assert(ret == 1);		// must be 1 (error status for this func.)
    
  
    fprintf(SysErrorFile, "%s: Could not create the Java Virtual Machine; goodbye\n", Me);
    exit(-1);
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


#if 0
static unsigned int parse_heap_sizeIntOnly(
    const char *sizeName, const char *sizeFlag, 
    const char *token, const char *subtoken, bool *fastExit);
#endif


/* TODO: Import BYTES_IN_PAGE from
   com.ibm.JikesRVM.memoryManagers.vmInterface.Constants */
static unsigned int
parse_heap_size(const char *sizeName, //  "initial" or "maximum"
                const char *sizeFlag, // "ms" or "mx"
		const char *token, const char *subtoken, bool *fastExit)
{
    const unsigned LOG_BYTES_IN_PAGE = 12;
    const unsigned BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
    
    char *endp;                 /* really should be const char *, but C++
                                   can't handle that kind of overloaded
                                   function. */ 
    long double factor = 1; /* multiplication factor; M for Megabytes, 
                               K for kilobytes, etc. */

    errno = 0;
    long double heapsz;
#ifdef HAVE_CXX_STRTOLD
        /* This gets around some nastiness in AIX 5.1, where <stdlib.h> only
           prototypes strtold() if we're using the 96 or 128 bit "long double"
           type.  Which is an option to the IBM Visual Age C compiler, but
           apparently not (yet) available for GCC.  */
    heapsz = strtold(subtoken, &endp);
#else
    heapsz = strtod(subtoken, &endp);
#endif

    // First, set the factor appropriately, and make sure there aren't extra
    // characters at the end of the line.
    if (*endp == '\0') {
	/* no suffix.  Here we differ from the Sun JVM, by assuming
           no suffix implies megabytes. (Historical compat. with previous
           Jikes RVM behaviour.)  The Sun JVM assumes no suffix implies
           bytes. */ 
	factor = 1024.0 * 1024.0;
    } else if (endp[1] == '\0') {
        char e = *endp;
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
	    factor = 1024.0 * 1024.0 * 1024.0 * 1024.0; // Terabytes
	else if (e == 'g' || e == 'G')
	    factor = 1024.0 * 1024.0 * 1024.0; // Gigabytes
	else if (e == 'm' || e == 'M')
	    factor = 1024.0 * 1024.0; // Megabytes
	else if (e == 'k' || e == 'K')
	    factor = 1024.0;	// kilobytes
        /* b for bytes.  Seems mnemonic, BUT I am mildly concerned because
           "dd" uses "b" to mean "blocks".   "dd" also uses "c" for
           "characters" to mean what we mean by bytes, so we'll at least make
           "c" legal syntax, right? */
	else if (e == 'b' || e == 'B' || e == 'c' || e == 'C' )
	    factor = 1.0;	// Bytes.  Not avail. in Sun JVM
	else {
	    goto bad_strtold;
	}
    } else {
    bad_strtold:
	fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a memory size\n", Me, token, subtoken);		
	*fastExit = true;
    }

    // Note: on underflow, strtod() returns 0.
    if (!*fastExit) {
        if (heapsz <= 0.0) {
	    fprintf(SysTraceFile, 
		    "%s: You may not specify a %s %s heap size;\n", 
		    Me, heapsz < 0.0 ? "negative" : "zero", sizeName);
	    fprintf(SysTraceFile, "\tit just doesn't make any sense.\n");
	    *fastExit = true;
        }
    } 

    if (!*fastExit) {
	if (errno == ERANGE 
            || heapsz > ((long double) (UINT_MAX - BYTES_IN_PAGE)/ factor)) 
        {
	// If message not already printed, print it.
	    fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", Me, subtoken);
	    *fastExit = true;
	}
    }

    if (*fastExit) {
	size_t namelen = strlen(sizeName);
	fprintf(SysTraceFile, "\tPlease specify %s heap size "
                "(in megabytes) using \"-X%s<positive number>M\",\n", 
                sizeName, sizeFlag);
	fprintf(SysTraceFile, "\t               %*.*s        "
                "or (in kilobytes) using \"-X%s<positive number>K\",\n",
                (int) namelen, (int) namelen, " ", sizeFlag);
	fprintf(SysTraceFile, "\t               %*.*s        "
                "or (in bytes) using \"-X%s<positive number>b\"\n",
                (int) namelen, (int) namelen, " ", sizeFlag);
	fprintf(SysTraceFile, "\t               %*.*s        "
                "or (in gigabytes) using \"-X%s<positive number>G\",\n",
                (int) namelen, (int) namelen, " ", sizeFlag);
#ifdef RVM_FOR_64_ADDR
	fprintf(SysTraceFile, "\t               %*.*s        "
                "or (in terabytes) using \"-X%s<positive number>t\"\n",
                (int) namelen, (int) namelen, " ", sizeFlag);
#endif // RVM_FOR_64_ADDR
        fprintf(SysTraceFile, "    If you specify floating point values,"
                " the # of bytes will be rounded up\n"
                " to a multiple of the virtual memory page size.\n");
	return 0U;		// Distinguished value meaning trouble.
    } 
    long double tot_d = heapsz * factor;
    assert(tot_d <= (UINT_MAX - BYTES_IN_PAGE));
    assert(tot_d >= 1);
    
    unsigned tot = (unsigned) tot_d;
    if (tot % BYTES_IN_PAGE) {
	unsigned newtot
            =  ((tot >> LOG_BYTES_IN_PAGE) + 1) << LOG_BYTES_IN_PAGE;
	
	fprintf(SysTraceFile, 
                "%s: Rounding up %s heap size from %u bytes to %u,\n"
		"\tthe next multiple of %u bytes\n", 
                Me, sizeName, tot, newtot, BYTES_IN_PAGE);
	tot = newtot;
    }
    return tot;
}

#if 0

/* TODO: Import BYTES_IN_PAGE from
   com.ibm.JikesRVM.memoryManagers.vmInterface.Constants */
static unsigned int
parse_heap_sizeIntOnly(const char *sizeName, const char *sizeFlag, 
		const char *token, const char *subtoken, bool *fastExit)
{
    const unsigned LOG_BYTES_IN_PAGE = 12;
    const unsigned BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
    
    char *endp;
    unsigned long factor = 1;	// multiplication factor; M for Megabytes, K
				// for kilobytes, etc.

    errno = 0;
    long heapsz = strtol(subtoken, &endp, 0);  // heap size number

    // First, set the factor appropriately, and make sure there aren't extra
    // characters at the end of the line.
    if (*endp == '\0') {
	// no suffix.  Here we differ from the Sun JVM, by assuming
	// megabytes. (Historical compat.)  The Sun JVM would specify
	// 1 here. 
	factor = 1024UL * 1024UL;
    } else if (endp[1] == '\0') {
	if (*endp == 'm' || *endp == 'M')
	    factor = 1024UL * 1024UL; // megabytes
	else if (*endp == 'k' || *endp == 'K')
	    factor = 1024UL;	// kilobytes
	else if (*endp == 'b' || *endp == 'B')
	    factor = 1UL;	// Bytes.  Not avail. in Sun JVM
	else {
	    goto bad_strtol;
	}
    } else {
    bad_strtol:
	fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a memory size\n", Me, token, subtoken);		
	*fastExit = true;
    }

    if (!*fastExit) {
	if (heapsz <= 0) {
	    fprintf(SysTraceFile, 
		    "%s: You may not specify a %s initial heap size;", 
		    Me, heapsz < 0 ? "negative" : "zero");
	    fprintf(SysTraceFile, "\tit just doesn't make any sense.\n");
	    *fastExit = true;
	}
    } 

    if (!*fastExit) {
	if ((unsigned long) heapsz > (UINT_MAX / factor) || errno == ERANGE)  {
	// If message not already printed, print it.
	    fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", Me, subtoken);
	    *fastExit = true;
	}
    }

    if (*fastExit) {
	size_t namelen = strlen(sizeName);
	fprintf(SysTraceFile, "\tPlease specify %s heap size "
                "(in megabytes) using \"-X%s<positive integer>M\",\n", 
                sizeName, sizeFlag);
	fprintf(SysTraceFile, "\t               %*.*s        "
                "or (in kilobytes) using \"-X%s<positive integer>K\",\n",
                (int) namelen, (int) namelen, " ", sizeFlag);
	fprintf(SysTraceFile, "\t               %*.*s        "
                "or (in bytes) using \"-X%s<positive integer>B\"\n",
                (int) namelen, (int) namelen, " ", sizeFlag);
	return 0U;		// Dummy.
    } 
    unsigned int tot = heapsz * factor;
    if (tot % BYTES_IN_PAGE) {
	unsigned newtot =  ((tot >> LOG_BYTES_IN_PAGE) + 1) << LOG_BYTES_IN_PAGE;
	
	fprintf(SysTraceFile, "%s: Rounding up %s heap size from %u to %u,"
		" the next multiple of %u bytes\n", Me, sizeName, tot, newtot, BYTES_IN_PAGE);
	tot = newtot;
    }
    return tot;
}
#endif
