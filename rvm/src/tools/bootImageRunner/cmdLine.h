/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

// @author Stephen Fink

// Definitions of constants for handling C command-line arguments

#ifndef CMDLINE_H 
#define CMDLINE_H

static const int HELP_INDEX                    = 0;
static const int VERBOSE_INDEX                 = 1;
static const int SMALL_HEAP_INDEX              = 2;
static const int MX_INDEX                      = 3;
static const int LARGE_HEAP_INDEX              = 4;
static const int NURSERY_HEAP_INDEX            = 5;
static const int PERM_HEAP_INDEX               = 6;
static const int SYSLOGFILE_INDEX              = 7;
static const int BOOTIMAGE_FILE_INDEX          = 8;
static const int TCL_INDEX                     = 9;
static const int GC_INDEX                      = 10;
static const int AOS_INDEX                     = 11;
static const int IRC_INDEX                     = 12;
static const int BASE_INDEX                    = 13;
static const int OPT_INDEX                     = 14;
static const int PROF_INDEX                    = 15;
static const int VMCLASSES_INDEX               = 16;
static const int CPUAFFINITY_INDEX             = 17;
static const int PROCESSORS_INDEX              = 18;
static const int WBSIZE_INDEX                  = 19;
static const int MEASURE_COMPILATION_INDEX     = 20;
static const int MEASURE_CLASS_LOADING_INDEX   = 21;
static const int VERIFY_INDEX                  = 22;
static const int numNonstandardArgs            = 23;

static const char* nonStandardArgs[numNonstandardArgs] = {
   "-X", 
   "-X:verbose",
   "-X:h=",
   "-Xmx",
   "-X:lh=",
   "-X:nh=",
   "-X:ph=",
   "-X:sysLogfile=",
   "-X:i=",
   "-X:traceClassLoading=",
   "-X:gc",
   "-X:aos",
   "-X:irc",
   "-X:base",
   "-X:opt",
   "-X:prof",
   "-X:vmClasses=",
   "-X:cpuAffinity=",
   "-X:processors=",
   "-X:wbsize=",
   "-X:measureCompilation=",
   "-X:measureClassLoading=",
   "-X:verify="
};

// we currently add seven extra lines due to multi-line messages below
static const int EXTRA_USAGE_LINES = 7;
static const int numNonStandardUsageLines= numNonstandardArgs + EXTRA_USAGE_LINES;
static const char* nonStandardUsage[numNonStandardUsageLines] = {
   "    -X                       print usage on nonstandard options", 
   "    -X:verbose               print out additional information for GC",
   "    -X:h=<number>            megabytes of small object heap",
   "    -Xmx<number>             megabytes of small object heap",
   "    -X:lh=<number>           megabytes of large object heap",
   "    -X:nh=<number>           megabytes of nursery object heap",
   "    -X:ph=<number>           megabytes of permanent object heap",
   "    -X:sysLogfile=<filename> write standard error message to <filename>",
   "    -X:i=<filename>          read boot image from <filename>",
   "    -X:traceClassLoading     produce a report on class loading activity",
   "    -X:gc:<option>           pass <option> on to GC subsystem",
   "          :help              print usage choices for -X:gc",
   "    -X:aos:<option>          pass <option> on to adaptive optimization system",
   "          :help              print usage choices for -X:aos",
   "    -X:irc:<option>          pass <option> on to the initial runtime compiler",
   "          :help              print usage choices for -X:irc",
   "    -X:base:<option>         pass <option> on to the baseline compiler",
   "          :help              print usage choices for -X:base",
   "    -X:opt:<option>          pass <option> on to the optimizing compiler",
   "          :help              print usage choices for -X:opt",
   "    -X:prof:<option}         pass <option> on to profiling subsystem",
   "    -X:vmClasses=<filename>  load classes from <filename>",
   "    -X:cpuAffinity=<number>  physical cpu to which 1st VP is bound",
   "    -X:processors=<number|\"all\">  no. of virtual processors",
   "    -X:wbsize=<number>       deprecated ... should be deleted soon!",
   "    -X:measureCompilation=<boolean> produce a report on compilation time",
   "    -X:measureClassLoading=<integer> produce a report on class loading time",
   "                             1 - Dump at the end of run",
   "                             2 - Dump at every load",
   "    -X:verify=<true|false>   verify bytecodes?"
};


#endif
