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

// Interface to VM data structures.
//
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS

#if IBM_AIX
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

extern unsigned smallHeapSize;  // megs
extern unsigned largeHeapSize;  // megs
extern unsigned nurserySize;    // megs
extern unsigned permanentHeapSize;

extern unsigned traceClassLoading;

extern int verboseGC;

extern "C" int createJVM(int);

int DEBUG = 0;
char * emptyString = "";

/*
 * What command line arguments are supported?
 */
void usage() 
{
  fprintf(SysTraceFile,"Usage: %s [-options] class [args...]\n",me);
  fprintf(SysTraceFile,"          (to execute a class)\n");
  //  fprintf(SysTraceFile,"   or  %s -jar [-options] jarfile [args...]\n",me);
  //  fprintf(SysTraceFile,"          (to execute a jar file)\n");
  fprintf(SysTraceFile,"\nwhere options include:\n");
  fprintf(SysTraceFile,"    -cp -classpath <directories and zip/jar files separated by :>\n");
  fprintf(SysTraceFile,"              set search path for application classes and resources\n");
  fprintf(SysTraceFile,"    -D<name>=<value>\n");
  fprintf(SysTraceFile,"              set a system property\n");
  fprintf(SysTraceFile,"    -verbose[:class|:gc|:jni]\n");
  fprintf(SysTraceFile,"              enable verbose output (:jni not supported)\n");
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
  fprintf(SysTraceFile,"    -X:h=<number>   allocate <number> megabytes of small object heap (default=%d)\n",small_heap_default_size);
  fprintf(SysTraceFile,"    -Xmx<number>    allocate <number> megabytes of small object heap (default=%d)\n",small_heap_default_size);
  fprintf(SysTraceFile,"    -X:lh=<number>  allocate <number> megabytes of large object heap (default=10)\n");
  fprintf(SysTraceFile,"    -X:nh=<number>  allocate <number> megabytes of nursery object heap (default=10)\n");
  fprintf(SysTraceFile,"    -X:ph=<number>  allocate <number> megabytes of permanent object heap (default=0)\n");
  fprintf(SysTraceFile,"    -X:i=<filename> read boot image from <filename>\n");
  fprintf(SysTraceFile,"    -X:sysLogfile=<filename>\n");
  fprintf(SysTraceFile,"                    writes standard error message to <filename>\n");
  fprintf(SysTraceFile,"    -X:vmClasses=<filename>\n");
  fprintf(SysTraceFile,"                    load classes from <filename>\n");
  fprintf(SysTraceFile,"    -X:cpuAffinity=<number>\n");
  fprintf(SysTraceFile,"                    physical cpu to which first virtual processor is bound\n");
  fprintf(SysTraceFile,"    -X:processors=<number|\"all\">\n");
  fprintf(SysTraceFile,"                    number of processors to use on a multiprocessor or use all\n");
  fprintf(SysTraceFile,"    -X:measureCompilation=<boolean>\n");
  fprintf(SysTraceFile,"                    produce a report on compilation time\n");
  fprintf(SysTraceFile,"    -X:measureClassLoading=<integer>\n");
  fprintf(SysTraceFile,"                    produce a report on class loading time\n");
  fprintf(SysTraceFile,"                    1 - Dump at the end of run\n");
  fprintf(SysTraceFile,"                    2 - Dump at every load\n");
  fprintf(SysTraceFile,"    -X:traceClassLoading\n");
  fprintf(SysTraceFile,"                    produce a report on class loading activity\n");
  fprintf(SysTraceFile,"    -X:verbose      print out additional information for GC\n");
  fprintf(SysTraceFile,"    -X:aos[:help]   print options supported by adaptive optimization system when in an adaptive configuration\n");
  fprintf(SysTraceFile,"    -X:aos:<option> pass <option> on to the adaptive optimization system when in an adaptive configuration\n");
  fprintf(SysTraceFile,"    -X:irc[:help]   print options supported by the initial runtime compiler when in a nonadaptive configuration\n");
  fprintf(SysTraceFile,"    -X:irc:<option> pass <option> on to the initial runtime compiler when in a nonadaptive configuration\n");
  fprintf(SysTraceFile,"    -X:base[:help]  print options supported by the baseline compiler when in a nonadaptive configuration\n");
  fprintf(SysTraceFile,"    -X:base:<option> pass <option> on to the baseline compiler when in a nonadaptive configuration\n");
  fprintf(SysTraceFile,"    -X:opt[:help]   print options supported by the optimizing compiler when in a nonadaptive configuration\n");
  fprintf(SysTraceFile,"    -X:opt:<option> pass <option> on to the optimizing compiler when in a nonadaptive configuration\n");
  fprintf(SysTraceFile,"    -X:gc[:help]    print options supported by GCTk garbage collection toolkit\n");
  fprintf(SysTraceFile,"    -X:gc:<option>  pass <option> on to GCTk\n");
  fprintf(SysTraceFile,"    -X:measure_compilation=<true|false>  measure compilation times?\n");
  fprintf(SysTraceFile,"    -X:verify=<true|false>  verify bytecodes?\n");
  fprintf(SysTraceFile,"\n");
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
    if (!strcmp(token, "-X")) {
      nonstandard_usage();
      *fastExit = 1; break;
    }
    if (!strcmp(token, "-X:verbose")) {
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
      fprintf(SysTraceFile, "configurations: host %s\n\t target %s\n",
	      rvm_host_configuration, rvm_target_configuration);
      fprintf(SysTraceFile, "small heap default size: %d MBytes\n",
	      small_heap_default_size);
      *fastExit = 1; break;
    }
    if (!strcmp(token, "-showversion")) {
      fprintf(SysTraceFile, "%s %s\n",rvm_configuration, rvm_version);
      continue;
    }
    if (!strcmp(token, "-verbose:gc")) {
      verboseGC = 1;
      continue;
    }
    if (!strncmp(token, "-verbose:gc=",12)) {
      subtoken = token + 12;
      verboseGC = atoi(subtoken);
      if (verboseGC < 0) {
	fprintf(SysTraceFile, "%s: please specify GC verbose level \"-verbose:gc=<number>\"\n", me);
	*fastExit = 1; break;
      }
      continue;
    }
    if (!strncmp(token, "-X:h=", 5)) {
      subtoken = token + 5;
      smallHeapSize = atoi(subtoken) * 1024 * 1024;
      if (smallHeapSize <= 0) {
	fprintf(SysTraceFile, "%s: please specify small object heap size (in megabytes) using \"-X:h=<number>\"\n", me);
	*fastExit = 1; break;
      }
      // continue;
    }
    if (!strncmp(token, "-Xmx", 4)) {
      subtoken = token + 4;
      smallHeapSize = atoi(subtoken) * 1024 * 1024;
      if (smallHeapSize <= 0) {
	fprintf(SysTraceFile, "%s: please specify small object heap size (in megabytes) using \"-X:h=<number>\"\n", me);
	*fastExit = 1; break;
      }
      // continue;
    }
    if (!strncmp(token, "-X:lh=", 6)) {
      subtoken = token + 6;
      largeHeapSize = atoi(subtoken) * 1024 * 1024;
      if (largeHeapSize <= 0) {
	fprintf(SysTraceFile, "%s: please specify large object heap size (in megabytes) using \"-X:lh=<number>\"\n", me);
	*fastExit = 1; break;
      }
      // continue;
    }
    if (!strncmp(token, "-X:nh=", 6)) {
      subtoken = token + 6;
      nurserySize = atoi(subtoken) * 1024 * 1024;
      if (nurserySize <= 0) {
	fprintf(SysTraceFile, "%s: please specify nursery size (in megabytes) using \"-X:nh=<number>\"\n", me);
	*fastExit = 1; break;
      }
      // continue;
    }
    if (!strncmp(token, "-X:ph=", 6)) {
      subtoken = token + 6;
      permanentHeapSize = atoi(subtoken) * 1024 * 1024;
      if (permanentHeapSize <= 0) {
	fprintf(SysTraceFile, "%s: please specify permanent heap size (in megabytes) using \"-X:ph=<number>\"\n", me);
	*fastExit = 1; break;
      }
      // continue;
    } 
      
    if (!strncmp(token, "-X:sysLogfile=",14)) {
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
    if (!strncmp(token, "-X:i=", 5)) {
      bootFilename = token + 5;
      continue;
    }

    if (!strncmp(token, "-X:traceClassLoading", 10)) {
      traceClassLoading = 1;
      continue;
    } 
    
    /*
     * JDK 1.3 standard command line arguments that are not supported.
     * TO DO: provide support
     */
    if (!strcmp(token, "-verbose:jni")) {
      fprintf(SysTraceFile, "%s: -verbose:jni is not supported\n", me);
      continue;
    }
    if (!strcmp(token, "-jar")) {
      fprintf(SysTraceFile, "%s: -jar is not supported\n", me);
      continue;
    }

    //
    // All VM directives that are not handled here but in VM.java
    //  must be identified.
    //

    // All VM directives that take one token
    if (!strncmp(token, "-D", 2) || !strncmp(token, "-X:gc", 5) ||
	!strncmp(token, "-X:aos",6)   || !strncmp(token, "-X:irc", 6) ||
	!strncmp(token, "-X:base",7)  || !strncmp(token, "-X:opt", 6) ||
	!strncmp(token, "-X:prof",7)  ||
	!strcmp(token, "-verbose")    || !strcmp(token, "-verbose:class") ||
	!strcmp(token, "-verbose:gc") ||
	!strncmp(token, "-X:vmClasses=", 13)  || 
	!strncmp(token, "-X:cpuAffinity=", 15) ||
	!strncmp(token, "-X:processors=", 14)  ||
	!strncmp(token, "-X:wbsize=", 10)      ||
	!strncmp(token, "-X:measureCompilation=", 22) ||  
	!strncmp(token, "-X:measureClassLoading=", 23) ||     
	!strncmp(token, "-X:verify=", 10)      ||
	!strncmp(token, "-X:measureCompilation=", 22)      
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
 *   1) effect the starting of the VM, 
 *   2) can be handled without starting the VM, or
 *   3) contain quotes
 * then call createJVM().
 *
 * Other command line arguments are handled by VM.java.
 *
 * TO DO:
 * Standardize all VM directives
 *   specified as one token with a name value pair specified as name=value.
 *   for example, -h, -lh, and -i. (Look in VM.java for others!)
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
  bootFilename  = 0;
  smallHeapSize = small_heap_default_size*1024*1024; // 20 * 1024 * 1024; // megs
  // if large heap default size is 0 then 
  //  make large heap size at least 10 Meg or a percent of small heap
  // unsigned largeHeapSize = 10 * 1024 * 1024; // megs
  //
  largeHeapSize = 0;
  nurserySize   = 10 * 1024 * 1024; // megs
  permanentHeapSize = 0;
  
  /*
   * Debugging: print out command line arguments.
   */
  if (DEBUG) {
     printf("RunBootImage.main(): process %d command line arguments\n",argc);
    for (int j=0; j<argc; j++) {
      printf("\targv[%d] is \"%s\"\n",j,*(argv+j));
    }
  }
  
  // call process command line arguments.
  int fastBreak = 0;
  char **Arguments = processCommandLineArguments(argv, argc, &fastBreak);
  if (fastBreak==1) {
    return 1;
  }

  if(DEBUG){
    printf("\nRunBootImage.main(): VM variable settings\n");
    printf("smallHeapSize %d\nlargeHeapSize %d\nnurserySize %d\npermanentHeapSize %d\nbootFileName |%s|\nlib_verbose %d\n",
	   smallHeapSize,largeHeapSize,nurserySize, permanentHeapSize, bootFilename,
	   lib_verbose);
  }
  // Now find a reasonable value for large object heap
  // 
  if (largeHeapSize == 0) {
    largeHeapSize = smallHeapSize >> 2;
    if (largeHeapSize < (10 * 1024 * 1024))
      largeHeapSize = 10 * 1024 * 1024;
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

  // remember remaining command line arguments for later use by boot image
  //
  //   JavaArgs = argv;

  JavaArgs = Arguments;

  createJVM(0);
   
   // not reached
   //
  fprintf(SysErrorFile, "%s: unexpected return from vm startup thread\n", me);
  exit(1);
}


