/*
 * (C) Copyright IBM Corp. 2002, 2003
 */
//$Id$

// @author Stephen Fink

// Definitions of constants for handling C command-line arguments
// These are actually only included by one caller, RunBootImage.C

#ifndef CMDLINE_H 
#define CMDLINE_H

/* These definitions must remain in sync with nonStandardArgs, the array
 * immediately below. */ 
static const int HELP_INDEX                    = 0;
static const int VERBOSE_INDEX                 = HELP_INDEX+1;
static const int VERBOSE_BOOT_INDEX            = VERBOSE_INDEX+1;
static const int INITIAL_HEAP_INDEX            = VERBOSE_BOOT_INDEX+1;
static const int MS_INDEX                      = INITIAL_HEAP_INDEX+1;
static const int MX_INDEX                      = MS_INDEX+1;
static const int SYSLOGFILE_INDEX              = MX_INDEX+1;
static const int BOOTIMAGE_FILE_INDEX          = SYSLOGFILE_INDEX+1;
static const int VM_INDEX                      = BOOTIMAGE_FILE_INDEX+1;
static const int GC_INDEX                      = VM_INDEX+1;
static const int AOS_INDEX                     = GC_INDEX+1;
static const int IRC_INDEX                     = AOS_INDEX+1;
static const int RECOMP_INDEX                  = IRC_INDEX+1;
static const int BASE_INDEX                    = RECOMP_INDEX+1;
static const int OPT_INDEX                     = BASE_INDEX+1;
static const int VMCLASSES_INDEX               = OPT_INDEX+1;
static const int CPUAFFINITY_INDEX             = VMCLASSES_INDEX+1;
static const int PROCESSORS_INDEX              = CPUAFFINITY_INDEX+1;


static const int numNonstandardArgs            = PROCESSORS_INDEX+1;

static const char* nonStandardArgs[numNonstandardArgs] = {
   "-X", 
   "-X:verbose",
   "-X:verboseBoot=",
   "-X:h=",
   "-Xms",
   "-Xmx",
   "-X:sysLogfile=",
   "-X:i=",
   "-X:vm",
   "-X:gc",
   "-X:aos",
   "-X:irc",
   "-X:recomp",
   "-X:base",
   "-X:opt",
   "-X:vmClasses=",
   "-X:cpuAffinity=",
   "-X:processors=",
};

// a NULL-terminated list.
static const char* nonStandardUsage[] = {
   "    -X                       print usage on nonstandard options", 
   "    -X:verbose               print out additional lowlevel information",
   "    -X:verboseBoot=<number>  print out messages while booting VM",
   "    -Xms<number>             initial size of heap in megabytes",
   "    -Xmx<number>             maximum size of heap in megabytes",
   "    -X:sysLogfile=<filename> write standard error message to <filename>",
   "    -X:i=<filename>          read boot image from <filename>",
   "    -X:vm:<option>           pass <option> to virtual machine",
   "          :help              print usage choices for -X:vm",
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
   "    -X:vmClasses=<path>      load the com.ibm.JikesRVM.* and java.* classes",
   "                             from <path>, a list like one would give to the",
   "                             -classpath argument."
   "    -X:cpuAffinity=<number>  physical cpu to which 1st VP is bound",
   "    -X:processors=<number|\"all\">  no. of virtual processors",
   NULL                         /* End of messages */
};

#endif
