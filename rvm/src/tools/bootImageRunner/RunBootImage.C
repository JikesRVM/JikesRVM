/*
 * (C) Copyright IBM Corp 2001,2002
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

#include <stdio.h>
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
#ifdef __linux__
#include <asm/cache.h>
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

int DEBUG = 0;			// have to set this from a debugger

static bool strequal(const char *s1, const char *s2);
static bool strnequal(const char *s1, const char *s2, size_t n);

/*
 * What standard command line arguments are supported?
 */
static void 
usage(void) 
{
    fprintf(SysTraceFile,"Usage: rvm [-options] class [args...]\n");
    fprintf(SysTraceFile,"          (to execute a class)\n");
    //  fprintf(SysTraceFile,"   or  %s -jar [-options] jarfile [args...]\n",me);
    //  fprintf(SysTraceFile,"          (to execute a jar file)\n");
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

    fprintf(SysTraceFile,"    -jar      not supported\n");
    fprintf(SysTraceFile,"\n For more information look at URL: www.ibm.com/developerworks/oss/jikesrvm\n");

    fprintf(SysTraceFile,"\n");
}

/*
 * What nonstandard command line arguments are supported?
 */
static void 
nonstandard_usage() 
{
    fprintf(SysTraceFile,"Usage: %s [options] class [args...]\n",me);
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
    fprintf(SysTraceFile, "%s %s\n",rvm_configuration, rvm_version);
}

static void
fullVersion()
{
    shortVersion();
    fprintf(SysTraceFile, "configuration info:\n\thost %s\n\ttarget %s\n",
	    rvm_host_configuration, rvm_target_configuration);
    fprintf(SysTraceFile, "\theap default initial size: %u MBytes\n",
	    heap_default_initial_size/(1024*1024));
    fprintf(SysTraceFile, "\theap default maximum size: %u MBytes\n",
	    heap_default_maximum_size/(1024*1024));
}



// /**
//  * Maximum number of tokens
//  */
// static const int maxTokens = 256;

// /* This code is unused.  I am avoiding deleting it in case someone wants it 
//    later.  --Steven Augart, July 2003 */

// /**
//  * Maximum length of token
//  */
// const int maxToken = 2048;

// /*
//  * function findAndRemoveQuotes
//  * input:  token
//  * output: boolean that is true if the string contains an open quote, otherwise
//  *	   returns false.
//  * A string contains an open quote if the string contains an odd number of 
//  * double quotes.
//  */
// int findAndRemoveQuotes(char **tokenContainer) 
// {
//   char *token	   =*tokenContainer;
//   int length       =strlen(token);
//   int n_quotes     =0; 
//   int n_backSlashes=0;
//   char buffer[maxToken];
//   int b_index=0;
//   int i;
//   for (i=0; i<length; i++) {
//     if (token[i]=='"' && ((n_backSlashes%2)==0)) { 
//       // found double quote, remove quote
//       n_quotes++;
//     } else {
//       buffer[b_index++]=token[i];
//       if (token[i]=='\\') {
// 	// found backslash, keep running count
// 	n_backSlashes++;
//       } else {
// 	n_backSlashes=0;
//       }
//     }
//   }
//   buffer[b_index++]='\0';
//   if(n_quotes) {
//     // found at least one quote, update tokenContainer
//     char *buf = (char*)malloc(b_index);
//     for(i=0;i<b_index;i++) {
//       buf[i]=buffer[i];
//     }
//     *tokenContainer = buf;
//   }
//   return (n_quotes%2);
// }

// /* This code is unused.  I am avoiding deleting it in case someone wants it 
//    later.  --Steven Augart, July 2003 */
// /*
//  * function stringAppendWithSpace
//  *   given two strings, first and second, return a pointer to freshly
//  *   allocated memory containing a new string that consists
//  *   of first, followed by a space, followed by second.
//  */
// char *stringAppendWithSpace(char *first, char *second) 
// {
//   if (second == "") {
//   }
//   int l_first  = strlen(first);
//   int l_second = strlen(second);
//   int length   = l_first+l_second+2;
//   char *result = (char*)malloc(length);
//   int i=0;
//   for(; i<l_first; i++) {
//     result[i]=first[i]; 
//   }
//   result[i++]=' ';
//   for (int j=0; j<l_second; j++) {
//     result[i++]=second[j];
//   }
//   result[i++]='\0';
//   return result;
// }

/*
 * Identify all command line arguments that are VM directives.
 * VM directives are positional, they must occur before the application
 * class or any application arguments are specified.
 *
 * Identify command line arguments that are processed here:
 *   All heap memory directives. (e.g. -X:h).
 *   Any standard command line arguments that are not supported (e.g. -jar).
 *   Any informational messages (e.g. -help).
 *
 * Input an array of command line arguments.
 * Return an array containing application arguments and VM arguments that 
 *        are not processed here.
 * Side Effect  global varable JavaArgc is set.
 *
 * This is a bit sneaky -- we reuse the array 'CLAs'.  We're
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
	    *fastExit = 1;
	    break;
	}
	if (strequal(token, nonStandardArgs[HELP_INDEX])) {
	    nonstandard_usage();
	    *fastExit = 1; break;
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
		fprintf(SysTraceFile, "%s: \"%s\": You may not specify a negative verboseBoot value\n", me, token);
		*fastExit = 1; break;
	    } else if (errno == ERANGE || vb > INT_MAX ) {
		fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", me, token);
		*fastExit = 1; break;
	    } else if (*endp) {
		fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a number\n", me, token, subtoken);		
		*fastExit = 1; break;
	    }
	    
	    verboseBoot = vb;
	    continue;
	}
	if (strequal(token, "-version")) {
	    shortVersion();
	    // *fastExit = 1; break;
	    exit(0);
	}
	if (strequal(token, "-fullversion")) {
	    fullVersion();
	    // *fastExit = 1; break;
	    exit(0);
	}
	if (strequal(token, "-showversion")) {
	    shortVersion();
	    continue;
	}
	if (strequal(token, "-findMappable")) {
	    findMappable();
	    // *fastExit = 1; break;
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
		    fprintf(SysTraceFile, "%s: \"%s\": You may not specify a negative GC verbose value\n", me, token);
		    *fastExit = 1; 
		} else if (errno == ERANGE || level > INT_MAX ) {
		    fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", me, token);
		    *fastExit = 1;
		} else if (*endp) {
		    fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a number\n", me, token, subtoken);		
		    *fastExit = 1;
		}
		if (*fastExit) {
		    fprintf(SysTraceFile, "%s: please specify GC verbose level as  \"-verbose:gc=<number>\" or as \"-verbose:gc\"\n", me);
		    break;
		}
	    }
	    {
		// canonicalize the argument
		char *buf = (char *) malloc(20); 
		sprintf(buf, "-X:gc:verbose=%ld", level);
		CLAs[n_JCLAs++]=buf;
	    }
	    continue;
	}
	if (strnequal(token, nonStandardArgs[INITIAL_HEAP_INDEX], 5)) {
	    subtoken = token + 5;
	    fprintf(SysTraceFile, "%s: Warning: -X:h=<number> is deprecated; please use \"-Xms\" and/or \"-Xmx\".\n", me);
	    fprintf(SysTraceFile, "\tI am interpreting -X:h=%s as if it was -Xms%s.\n", subtoken, subtoken);
	    fprintf(SysTraceFile, "\tFor a fixed heap size H, you must use -XmsH -X:gc:variableSizeHeap=false\n");
	    goto set_initial_heap_size;
	}
	if (strnequal(token, nonStandardArgs[MS_INDEX], 4)) {
	    subtoken = token + 4;
	set_initial_heap_size:
	    long ihsMB;		// initial heap size in MB
	    errno = 0;
	    char *endp;
	    ihsMB = strtol(subtoken, &endp, 0);
	    if (ihsMB <= 0) {
		fprintf(SysTraceFile, "%s: You may not specify a %s initial heap size;", me, ihsMB < 0 ? "negative" : "zero");
		fprintf(SysTraceFile, "\tit just doesn't make any sense.\n");
		*fastExit = 1;
	    } else if (ihsMB > (int) (UINT_MAX / (1024U * 1024U ))
		       || errno == ERANGE) 
	    {
		fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", me, token);
		*fastExit = 1;
	    } else if (*endp) {
		fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a number\n", me, token, subtoken);		
		*fastExit = 1;
	    }
	    if (*fastExit) {
		fprintf(SysTraceFile, "\tPlease specify initial heap size (in megabytes) using \"-Xms<positive number>\"\n", me);
		break;
	    }
	    initialHeapSize = ihsMB * 1024U * 1024U;
	    continue;
	}
	if (strnequal(token, nonStandardArgs[MX_INDEX], 4)) {
	    subtoken = token + 4;
	    long mhsMB;		// maximum heap size in MB
	    errno = 0;
	    char *endp;
	    mhsMB = strtol(subtoken, &endp, 0);
	    if (mhsMB <= 0) {
		fprintf(SysTraceFile, "%s: You may not specify a %s maximum heap size;", me, mhsMB < 0 ? "negative" : "zero");
		fprintf(SysTraceFile, "\tit just doesn't make any sense.\n");
		*fastExit = 1;
	    } else if (mhsMB > (int) (UINT_MAX / (1024U * 1024U ))
		       || errno == ERANGE) 
	    {
		fprintf(SysTraceFile, "%s: \"%s\": too big a number to represent internally\n", me, token);
		*fastExit = 1;
	    } else if (*endp) {
		fprintf(SysTraceFile, "%s: \"%s\": I don't recognize \"%s\" as a number\n", me, token, subtoken);		
		*fastExit = 1;
	    }
	    if (*fastExit) {
		fprintf(SysTraceFile, "\tPlease specify maximum heap size (in megabytes) using \"-Xms<positive number>\"\n", me);
		break;
	    }
	    maximumHeapSize = mhsMB * 1024U * 1024U;
	    continue;
	}

	if (strnequal(token, nonStandardArgs[SYSLOGFILE_INDEX],14)) {
	    subtoken = token + 14;
	    FILE* ftmp = fopen(subtoken, "a");
	    if (!ftmp) {
		fprintf(SysTraceFile, "%s: can't open SysTraceFile \"%s\": %s\n", me, subtoken, strerror(errno));
		continue;
	    }
	    fprintf(SysTraceFile, "%s: redirecting sysWrites to \"%s\"\n",me, subtoken);
	    SysTraceFile = ftmp;
	    SysTraceFd = fileno(ftmp);
	    continue;
	}
	if (strnequal(token, nonStandardArgs[BOOTIMAGE_FILE_INDEX], 5)) {
	    bootFilename = token + 5;
	    continue;
	}

	/*
	 * JDK 1.3 standard command line arguments that are not supported.
	 * TO DO: provide support
	 */
	if (strequal(token, "-jar")) {
	    fprintf(SysTraceFile, "%s: -jar is not supported\n", me);
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
    } // for()

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
    SysErrorFile = stderr;
    SysTraceFile = stderr;
    SysTraceFd   = 2;
  
    me            = basename(*argv++);
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
		me, maximumHeapSize/(1024*1024), initialHeapSize/(1024*1024));
	return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    if(DEBUG){
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
	fprintf(SysTraceFile, "%s: please specify name of boot image file using \"-i<filename>\"\n", me);
	return EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
    }

    createJVM(0);
  
    // not reached
    //
    fprintf(SysErrorFile, "%s: unexpected return from vm startup thread\n", me);
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

