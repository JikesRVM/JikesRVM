/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

// @author Stephen Fink

// Definitions of constants for handling C command-line arguments

#ifndef CMDLINE_H 
#define CMDLINE_H

static const int HELP_INDEX                    = 0;
static const int VERBOSE_INDEX                 = HELP_INDEX+1;
static const int INITIAL_HEAP_INDEX            = VERBOSE_INDEX+1;
static const int MS_INDEX                      = INITIAL_HEAP_INDEX+1;
static const int MX_INDEX                      = MS_INDEX+1;
static const int SYSLOGFILE_INDEX              = MX_INDEX+1;
static const int BOOTIMAGE_FILE_INDEX          = SYSLOGFILE_INDEX+1;
static const int TCL_INDEX                     = BOOTIMAGE_FILE_INDEX+1;
static const int GC_INDEX                      = TCL_INDEX+1;
static const int AOS_INDEX                     = GC_INDEX+1;
static const int IRC_INDEX                     = AOS_INDEX+1;
static const int RECOMP_INDEX                  = IRC_INDEX+1;
static const int BASE_INDEX                    = RECOMP_INDEX+1;
static const int OPT_INDEX                     = BASE_INDEX+1;
static const int PROF_INDEX                    = OPT_INDEX+1;
static const int VMCLASSES_INDEX               = PROF_INDEX+1;
static const int CPUAFFINITY_INDEX             = VMCLASSES_INDEX+1;
static const int PROCESSORS_INDEX              = CPUAFFINITY_INDEX+1;
static const int MEASURE_COMPILATION_INDEX     = PROCESSORS_INDEX+1;
static const int VERIFY_INDEX                  = MEASURE_COMPILATION_INDEX+1;


static const int numNonstandardArgs            = VERIFY_INDEX+1;

static const char* nonStandardArgs[numNonstandardArgs] = {
   "-X", 
   "-X:verbose",
   "-X:h=",
   "-Xms",
   "-Xmx",
   "-X:sysLogfile=",
   "-X:i=",
   "-X:traceClassLoading=",
   "-X:gc",
   "-X:aos",
   "-X:irc",
   "-X:recomp",
   "-X:base",
   "-X:opt",
   "-X:prof",
   "-X:vmClasses=",
   "-X:cpuAffinity=",
   "-X:processors=",
   "-X:measureCompilation=",
   "-X:verify="
};

// we add some lines due to multi-line messages below
static const int EXTRA_USAGE_LINES = 5;
static const int numNonStandardUsageLines= numNonstandardArgs + EXTRA_USAGE_LINES;
static const char* nonStandardUsage[numNonStandardUsageLines] = {
   "    -X                       print usage on nonstandard options", 
   "    -X:verbose               print out additional information for GC",
   "    -Xms<number>             initial size of heap in megabytes",
   "    -Xmx<number>             maximum size of heap in megabytes",
   "    -X:sysLogfile=<filename> write standard error message to <filename>",
   "    -X:i=<filename>          read boot image from <filename>",
   "    -X:traceClassLoading     produce a report on class loading activity",
   "    -X:gc:<option>           pass <option> on to GC subsystem",
   "          :help              print usage choices for -X:gc",
   "    -X:aos:<option>          pass <option> on to adaptive optimization system",
   "          :help              print usage choices for -X:aos",
   "    -X:irc:<option>          pass <option> on to the initial runtime compiler",
   "          :help              print usage choices for -X:irc",
   "    -X:recomp:<option>       pass <option> on to the recompilation compiler(s)",
   "          :help              print usage choices for -X:recomp",
   "    -X:base:<option>         pass <option> on to the baseline compiler",
   "          :help              print usage choices for -X:base",
   "    -X:opt:<option>          pass <option> on to the optimizing compiler",
   "          :help              print usage choices for -X:opt",
   "    -X:prof:<option}         pass <option> on to profiling subsystem",
   "    -X:vmClasses=<filename>  load classes from <filename>",
   "    -X:cpuAffinity=<number>  physical cpu to which 1st VP is bound",
   "    -X:processors=<number|\"all\">  no. of virtual processors",
   "    -X:measureCompilation=<boolean> produce a report on compilation time",
   "    -X:verify=<boolean>      verify bytecodes?"
};

#endif
