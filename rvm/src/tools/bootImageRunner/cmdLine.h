/*
 * (C) Copyright IBM Corp. 2002, 2003, 2004
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
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
static const int SS_INDEX                      = MX_INDEX+1;
static const int SG_INDEX                      = SS_INDEX+1;
static const int SX_INDEX                      = SG_INDEX+1;
static const int SYSLOGFILE_INDEX              = SX_INDEX+1;
#else
static const int SYSLOGFILE_INDEX              = MX_INDEX+1;
#endif /* RVM_WITH_FLEXIBLE_STACK_SIZES */
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
static const int SINGLE_VIRTUAL_PROCESSOR_INDEX= PROCESSORS_INDEX+1;

static const int numNonstandardArgs      = SINGLE_VIRTUAL_PROCESSOR_INDEX+1;

static const char* nonStandardArgs[numNonstandardArgs] = {
   "-X", 
   "-X:verbose",
   "-X:verboseBoot=",
   "-X:h=",
   "-Xms",
   "-Xmx",
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
   "-Xss",
   "-Xsg",
   "-Xsx",
#endif
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
   "-X:singleVirtualProcessor=", /* Leave it here, even if no support built
                                  * in, but suppress it from the help
                                  * message. */ 
};

// a NULL-terminated list.
static const char* nonStandardUsage[] = {
   "    -X                       Print usage on nonstandard options", 
   "    -X:verbose               Print out additional lowlevel information",
   "    -X:verboseBoot=<number>  Print out messages while booting VM",
   "    -Xms<number><unit>       Initial size of heap,"
   "    -Xmx<number><unit>       Maximum size of heap,"
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
   "    -Xss<number><unit>       Initial Java thread stack size,"
   "    -Xsg<number><unit>       Java thread stack growth increment,"
   "    -Xsx<number><unit>       Maximum Java thread stack size",
#endif
   "    -X:sysLogfile=<filename> Write standard error message to <filename>",
   "    -X:i=<filename>          Read boot image from <filename>",
   "    -X:vm:<option>           Pass <option> to virtual machine",
   "          :help              Print usage choices for -X:vm",
   "    -X:gc:<option>           Pass <option> on to GC subsystem",
   "          :help              Print usage choices for -X:gc",
   "    -X:aos:<option>          Pass <option> on to adaptive optimization system",
   "          :help              Print usage choices for -X:aos",
   "    -X:irc:<option>          Pass <option> on to the initial runtime compiler",
   "          :help              Print usage choices for -X:irc",
   "    -X:recomp:<option>       Pass <option> on to the recompilation compiler(s)",
   "          :help              Print usage choices for -X:recomp",
   "    -X:base:<option>         Pass <option> on to the baseline compiler",
   "          :help              print usage choices for -X:base",
   "    -X:opt:<option>          Pass <option> on to the optimizing compiler",
   "          :help              Print usage choices for -X:opt",
   "    -X:vmClasses=<path>      Load the com.ibm.JikesRVM.* and java.* classes",
   "                             from <path>, a list like one would give to the",
   "                             -classpath argument.",
   "    -X:cpuAffinity=<number>  physical cpu to which 1st VP is bound",
   "    -X:processors=<number|\"all\">  no. of virtual processors",
#ifdef RVM_WITH_SINGLE_VIRTUAL_PROCESSOR_SUPPORT
   "    -X:singleVirtualProcessor=<\"true\"|\"false\"|\"debian\"|\"multiboot\">",
   "                             Operate with a single virtual processor",
   "                             (default is \""
#ifdef RVM_FOR_MULTIBOOT_GLIBC
   "multiboot"
#elif defined RVM_FOR_DEBIAN_GLIBC
   "debian"
#elif defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
   "true"
#else
   "false"
#endif
   "\")",
#endif // #ifdef RVM_WITH_SINGLE_VIRTUAL_PROCESSOR_SUPPORT
   NULL                         /* End of messages */
};

#endif
