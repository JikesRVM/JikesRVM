/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

/*
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment and
 * branching to its startoff code. It also deals with interrupt and exception
 * handling.
 * The file "sys.C" contains the o/s support services required by the java
 * class libraries.
 *
 * @author Derek Lieber 03 Feb 1998
 * 17 Oct 2000 The system code (everything except command line parsing in main)
 *             are moved into libvm.C to accomodate the JNI call CreateJVM
 *             (Ton Ngo)
 * @modified Peter Sweeney 05 Jan 2001
 *	       Add support to recognize quotes in command line arguments,
 *	       standardize command line arguments with JDK 1.3.
 *	       Eliminate order dependence on command line arguments
 *	       To add a new VM directive, 
 *	       add the directive to processCommandLineArguments()
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <sys/signal.h>
#include <strings.h> /* bzero */
#include <libgen.h>     /* basename */
#ifdef __linux__
#include <asm/cache.h>
#include <ucontext.h>
#include <signal.h>
#else
#include <sys/cache.h>
#include <sys/context.h>
extern "C" char *sys_siglist[];
#endif
#include "RunBootImage.h"
#include "cmdLine.h"

// Interface to VM data structures.
//
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS

#ifdef RVM_FOR_IBM
#include <AixLinkageLayout.h>
#endif

#include <InterfaceDeclarations.h>

// Sink for messages relating to serious errors detected by C runtime.
//
extern FILE *SysErrorFile;

// Sink for trace messages produced by VM.sysWrite().
//
extern FILE *SysTraceFile;
extern int   SysTraceFd;

// Command line arguments to be passed to boot image.
//
extern char **	JavaArgs;
extern int	JavaArgc; 

// Emit trace information?
//
extern int lib_verbose;

// command line arguments

extern char *bootFilename;
extern char *me;

unsigned initialHeapSize;
unsigned maximumHeapSize;

extern unsigned traceClassLoading;

extern "C" int createJVM(int);
extern "C" void findMappable();

int DEBUG = 0;
char * emptyString = "";

/*
 * What standard command line arguments are supported?
 */
void usage() 
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
void nonstandard_usage() 
{
  fprintf(SysTraceFile,"Usage: %s [options] class [args...]\n",me);
  fprintf(SysTraceFile,"          (to execute a class)\n");
  fprintf(SysTraceFile,"where options include\n");
  for (int i=0; i<numNonStandardUsageLines; i++) {
     fprintf(SysTraceFile,nonStandardUsage[i]);
     fprintf(SysTraceFile,"\n");
  }
}

/**
 * Maximum length of token
 */
const int maxToken = 2048;

/**
 * Maximum number of tokens
 */
const int maxTokens = 256;

/*
 * function findAndRemoveQuotes
 * input:  token
 * output: boolean that is true if the string contains an open quote, otherwise
 *	   returns false.
 * A string contains an open quote if the string contains an odd number of 
 * double quotes.
 */
int findAndRemoveQuotes(char **tokenContainer) 
{
  char *token	   =*tokenContainer;
  int length       =strlen(token);
  int n_quotes     =0; 
  int n_backSlashes=0;
  char buffer[maxToken];
  int b_index=0;
  int i;
  for (i=0; i<length; i++) {
    if (token[i]=='"' && ((n_backSlashes%2)==0)) { 
      // found double quote, remove quote
      n_quotes++;
    } else {
      buffer[b_index++]=token[i];
      if (token[i]=='\\') {
	// found backslash, keep running count
	n_backSlashes++;
      } else {
	n_backSlashes=0;
      }
    }
  }
  buffer[b_index++]='\0';
  if(n_quotes) {
    // found at least one quote, update tokenContainer
    char *buf = (char*)malloc(b_index);
    for(i=0;i<b_index;i++) {
      buf[i]=buffer[i];
    }
    *tokenContainer = buf;
  }
  return (n_quotes%2);
}

/*
 * function stringAppendWithSpace
 *   given two strings, first and second, return a new string that consists
 *   of first, followed by a space, followed by second.
 */
char *stringAppendWithSpace(char *first, char *second) 
{
  if (second == "") {
  }
  int l_first  = strlen(first);
  int l_second = strlen(second);
  int length   = l_first+l_second+2;
  char *result = (char*)malloc(length);
  int i=0;
  for(; i<l_first; i++) {
    result[i]=first[i]; 
  }
  result[i++]=' ';
  for (int j=0; j<l_second; j++) {
    result[i++]=second[j];
  }
  result[i++]='\0';
  return result;
}

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
 * Return an array for Application arguments, and for VM arguments that 
 *        are not processed here.
 * Side Effect  global JavaArgc set.
 */
char ** 
processCommandLineArguments(char **CLAs, int n_CLAs, int *fastExit) 
{
  char *autoJCLAs[maxTokens];
  char **JCLAs;
  int n_JCLAs=0;
  int startApplicationOptions = 0;
  char *subtoken;
  int i;

  if ( n_CLAs > maxTokens )
    JCLAs = new char*[n_CLAs];
  else
    JCLAs = autoJCLAs;

  for (i=0; i<n_CLAs; i++) {

    char *token=CLAs[i];

    #ifdef __linux__
    if ( NULL == token ) token = emptyString;
    #endif

    subtoken='\0';

    // examining application options?
    if (startApplicationOptions == 1) {
      JCLAs[n_JCLAs++]=token;
      continue;
    }
    // pass on all command line arguments that do not start with a dash, '-'.
    if (token[0] != '-') {
      JCLAs[n_JCLAs++]=token;
      startApplicationOptions = 1;
      continue;
    }

    //   while (*argv && **argv == '-')    {
    if (!strcmp(token, "-help") || !strcmp(token, "-?")) {
      usage();
      *fastExit = 1;
      break;
    }
    if (!strcmp(token, nonStandardArgs[HELP_INDEX])) {
      nonstandard_usage();
      *fastExit = 1; break;
    }
    if (!strcmp(token, nonStandardArgs[VERBOSE_INDEX])) {
      ++lib_verbose;
      //      JCLAs[n_JCLAs++]=token;
      continue;
    }
    if (!strcmp(token, "-version")) {
      fprintf(SysTraceFile, "%s %s\n",rvm_configuration, rvm_version);
      *fastExit = 1; break;
    }
    if (!strcmp(token, "-fullversion")) {
      fprintf(SysTraceFile, "%s %s\n",rvm_configuration, rvm_version);
      fprintf(SysTraceFile, "configuration info:\n\thost %s\n\ttarget %s\n",
	      rvm_host_configuration, rvm_target_configuration);
      fprintf(SysTraceFile, "\theap default initial size: %d MBytes\n",
	      heap_default_initial_size/(1024*1024));
      fprintf(SysTraceFile, "\theap default maximum size: %d MBytes\n",
	      heap_default_maximum_size/(1024*1024));
      *fastExit = 1; break;
    }
    if (!strcmp(token, "-showversion")) {
      fprintf(SysTraceFile, "%s %s\n",rvm_configuration, rvm_version);
      continue;
    }
    if (!strcmp(token, "-findMappable")) {
      findMappable();
      *fastExit = 1; break;
    }
    if (!strncmp(token, "-verbose:gc", 11)) {
      int level;
      if (token[11] == 0) 
	level = 1;
      else {
	level = atoi(token+12); // skip the "=" in "-verbose:gc=<num?"
	if (level < 0) {
	  fprintf(SysTraceFile, "%s: please specify GC verbose level \"-verbose:gc=<number>\"\n", me);
	  *fastExit = 1; break;
	}
      }
      {
	char *buf = (char *) malloc(20);
	sprintf(buf, "-X:gc:verbose=%d", level);
	JCLAs[n_JCLAs++]=buf;
      }
      continue;
    }
    if (!strncmp(token, nonStandardArgs[INITIAL_HEAP_INDEX], 5)) {
      fprintf(SysTraceFile, "%s: Warning: -X:h=<number> is deprecated, please use -Xms and/or -Xmx\n", me);
      fprintf(SysTraceFile, "\tI am interpreting -X:h=H as if it was -XmsH.\n");
      fprintf(SysTraceFile, "\tFor a fixed heap size H, you must use both -XmsH and -XmxH\n");
      subtoken = token + 5;
      initialHeapSize = atoi(subtoken) * 1024 * 1024;
      if (initialHeapSize <= 0) {
	fprintf(SysTraceFile, "%s: please specify initial heap size (in megabytes) using \"-X:h=<number>\"\n", me);
	*fastExit = 1; break;
      }
      continue;
    }
    if (!strncmp(token, nonStandardArgs[MS_INDEX], 4)) {
      subtoken = token + 4;
      initialHeapSize = atoi(subtoken) * 1024 * 1024;
      if (initialHeapSize <= 0) {
	fprintf(SysTraceFile, "%s: please specify initial heap size (in megabytes) using \"-Xms<number>\"\n", me);
	*fastExit = 1; break;
      }
      continue;
    }
    if (!strncmp(token, nonStandardArgs[MX_INDEX], 4)) {
      subtoken = token + 4;
      maximumHeapSize = atoi(subtoken) * 1024 * 1024;
      if (maximumHeapSize <= 0) {
	fprintf(SysTraceFile, "%s: please specify maximum heap size (in megabytes) using \"-Xmx<number>\"\n", me);
	*fastExit = 1; break;
      }
      continue;
    }

    if (!strncmp(token, nonStandardArgs[SYSLOGFILE_INDEX],14)) {
      subtoken = token + 14;
      FILE* ftmp = fopen(subtoken, "a");
      if (!ftmp) {
	fprintf(SysTraceFile, "%s: can't open SysTraceFile \"%s\"\n", me, subtoken);
      } else {
	fprintf(SysTraceFile, "%s: redirecting sysWrites to \"%s\"\n",me, subtoken);
	SysTraceFile = ftmp;
#ifdef __linux__
	SysTraceFd   = ftmp->_fileno;
#else
	SysTraceFd   = ftmp->_file;
#endif
      }	
      continue;
    }
    if (!strncmp(token, nonStandardArgs[BOOTIMAGE_FILE_INDEX], 5)) {
      bootFilename = token + 5;
      continue;
    }

    if (!strncmp(token, nonStandardArgs[TCL_INDEX], 10)) {
      traceClassLoading = 1;
      continue;
    } 
    
    /*
     * JDK 1.3 standard command line arguments that are not supported.
     * TO DO: provide support
     */
    if (!strcmp(token, "-jar")) {
      fprintf(SysTraceFile, "%s: -jar is not supported\n", me);
      continue;
    }

    //
    // All VM directives that are not handled here but in VM.java
    //  must be identified.
    //

    // All VM directives that take one token
    if (!strncmp(token, "-D", 2) || !strncmp(token, nonStandardArgs[GC_INDEX], 5) ||
	!strncmp(token, nonStandardArgs[AOS_INDEX],6)   || 
        !strncmp(token, nonStandardArgs[IRC_INDEX], 6) ||
	!strncmp(token, nonStandardArgs[BASE_INDEX],7)  || 
        !strncmp(token, nonStandardArgs[OPT_INDEX], 6) ||
	!strncmp(token, nonStandardArgs[PROF_INDEX], 7)  ||
	!strcmp(token, "-verbose")    || !strcmp(token, "-verbose:class") ||
	!strcmp(token, "-verbose:gc") || !strcmp(token, "-verbose:jni") || 
	!strncmp(token, nonStandardArgs[VMCLASSES_INDEX], 13)  || 
	!strncmp(token, nonStandardArgs[CPUAFFINITY_INDEX], 15) ||
	!strncmp(token, nonStandardArgs[PROCESSORS_INDEX], 14)  ||
	!strncmp(token, nonStandardArgs[MEASURE_COMPILATION_INDEX], 22) ||  
	!strncmp(token, nonStandardArgs[VERIFY_INDEX], 10)  
	) {
      JCLAs[n_JCLAs++]=token;
      continue;
    }
    // All VM directives that take two tokens
    if (!strcmp(token, "-cp") || !strcmp(token, "-classpath")) {
      JCLAs[n_JCLAs++]=token;
      token=CLAs[++i];
      JCLAs[n_JCLAs++]=token;
      continue;
    }

    JCLAs[n_JCLAs++]=token;
    startApplicationOptions = 1;
  }
  
  // Copy only those command line arguments that are needed.
  char **Arguments = new char*[n_JCLAs];
  for (i=0; i<n_JCLAs; i++) {
    Arguments[i]=JCLAs[i];
  }

  /* and set the count */
  JavaArgc = n_JCLAs == 0 ? 0 : n_JCLAs - 1;

  if ( n_CLAs > maxTokens )
    free (JCLAs);

  return Arguments;
}

/*
 * Parse command line arguments to find those arguments that 
 *   1) affect the starting of the VM, 
 *   2) can be handled without starting the VM, or
 *   3) contain quotes
 * then call createJVM().
 *
 * Other command line arguments are handled by VM.java.
 *
 * TO DO:
 * Standardize all VM directives
 *   specified as one token with a name value pair specified as name=value.
 *   for example, -h, and -i. (Look in VM.java for others!)
 * Add support for all standard JDK 1.3 options; 
 *   for example, -jar.
 */
int
main(int argc, char **argv)
{
  SysErrorFile = stderr;
  SysTraceFile = stderr;
  SysTraceFd   = 2;
  
  me            = basename(*argv++);
  initialHeapSize = heap_default_initial_size;
  maximumHeapSize = heap_default_maximum_size;
  
  /*
   * Debugging: print out command line arguments.
   */
  if (DEBUG) {
    printf("RunBootImage.main(): process %d command line arguments\n",argc);
    for (int j=0; j<argc; j++) {
      printf("\targv[%d] is \"%s\"\n",j,*(argv+j));
    }
  }
  
  // call processCommandLineArguments().
  int fastBreak = 0;
  char **Arguments = processCommandLineArguments(argv, argc, &fastBreak);
  if (fastBreak==1) {
    return 1;
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
    return 1;
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
    return 1;
  }

  JavaArgs = Arguments;

  createJVM(0);
  
  // not reached
  //
  fprintf(SysErrorFile, "%s: unexpected return from vm startup thread\n", me);
  exit(-1);
}


