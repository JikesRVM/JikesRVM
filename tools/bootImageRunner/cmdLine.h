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

// Definitions of constants for handling C command-line arguments
// These are actually only included by one caller, RunBootImage.C

#ifndef CMDLINE_H
#define CMDLINE_H

/* These definitions must remain in sync with nonStandardArgs, the array
 * immediately below. */
static const int HELP_INDEX                    = 0;
static const int VERBOSE_INDEX                 = HELP_INDEX+1;
static const int VERBOSE_BOOT_INDEX            = VERBOSE_INDEX+1;
static const int MS_INDEX                      = VERBOSE_BOOT_INDEX+1;
static const int MX_INDEX                      = MS_INDEX+1;
static const int SYSLOGFILE_INDEX              = MX_INDEX+1;
static const int BOOTIMAGE_CODE_FILE_INDEX     = SYSLOGFILE_INDEX+1;
static const int BOOTIMAGE_DATA_FILE_INDEX     = BOOTIMAGE_CODE_FILE_INDEX+1;
static const int BOOTIMAGE_RMAP_FILE_INDEX     = BOOTIMAGE_DATA_FILE_INDEX+1;
static const int INDEX                      = BOOTIMAGE_RMAP_FILE_INDEX+1;
static const int GC_INDEX                      = INDEX+1;
static const int AOS_INDEX                     = GC_INDEX+1;
static const int IRC_INDEX                     = AOS_INDEX+1;
static const int RECOMP_INDEX                  = IRC_INDEX+1;
static const int BASE_INDEX                    = RECOMP_INDEX+1;
static const int OPT_INDEX                     = BASE_INDEX+1;
static const int VMCLASSES_INDEX               = OPT_INDEX+1;
static const int PROCESSORS_INDEX              = VMCLASSES_INDEX+1;

static const int numNonstandardArgs      = PROCESSORS_INDEX+1;

static const char* nonStandardArgs[numNonstandardArgs] = {
   "-X",
   "-X:verbose",
   "-X:verboseBoot=",
   "-Xms",
   "-Xmx",
   "-X:sysLogfile=",
   "-X:ic=",
   "-X:id=",
   "-X:ir=",
   "-X:vm",
   "-X:gc",
   "-X:aos",
   "-X:irc",
   "-X:recomp",
   "-X:base",
   "-X:opt",
   "-X:vmClasses=",
   "-X:availableProcessors=",
};

// a NULL-terminated list.
static const char* nonStandardUsage[] = {
   "  -X                         Print usage on nonstandard options",
   "  -X:verbose                 Print out additional lowlevel information",
   "  -X:verboseBoot=<number>    Print out messages while booting VM",
   "  -Xms<number><unit>         Initial size of heap",
   "  -Xmx<number><unit>         Maximum size of heap",
   "  -X:sysLogfile=<filename>   Write standard error message to <filename>",
   "  -X:ic=<filename>           Read boot image code from <filename>",
   "  -X:id=<filename>           Read boot image data from <filename>",
   "  -X:ir=<filename>           Read boot image ref map from <filename>",
   "  -X:vm:<option>             Pass <option> to virtual machine",
   "        :help                Print usage choices for -X:vm",
   "  -X:gc:<option>             Pass <option> on to GC subsystem",
   "        :help                Print usage choices for -X:gc",
   "  -X:aos:<option>            Pass <option> on to adaptive optimization system",
   "        :help                Print usage choices for -X:aos",
   "  -X:irc:<option>            Pass <option> on to the initial runtime compiler",
   "        :help                Print usage choices for -X:irc",
   "  -X:recomp:<option>         Pass <option> on to the recompilation compiler(s)",
   "        :help                Print usage choices for -X:recomp",
   "  -X:base:<option>           Pass <option> on to the baseline compiler",
   "        :help                print usage choices for -X:base",
   "  -X:opt:<option>            Pass <option> on to the optimizing compiler",
   "        :help                Print usage choices for -X:opt",
   "  -X:vmClasses=<path>        Load the org.jikesrvm.* and java.* classes",
   "                             from <path>, a list like one would give to the",
   "                             -classpath argument.",
   "  -Xbootclasspath/p:<cp>     (p)repend bootclasspath with specified classpath",
   "  -Xbootclasspath/a:<cp>     (a)ppend specified classpath to bootclasspath",
   "  -X:availableProcessors=<n> desired level of application parallelism (set",
   "                             -X:gc:threads to control gc parallelism)",
   NULL                         /* End of messages */
};

#endif
