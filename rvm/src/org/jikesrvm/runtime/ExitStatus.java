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
package org.jikesrvm.runtime;

/** Exit status codes for the Jikes RVM virtual machine.
 *
 * These process exit status codes are used by the virtual machine whenever it
 * must exit with some failure condition.  By default, if all goes well, the
 * virtual machine will exit with status zero.
 */
public interface ExitStatus {
  /* Exit statuses, pending a better location.

     <p>Please keep this list in numerical order.

     <p>You might somewhere find uses of the explicit constant -1 as an exit
     status (it gets mapped to 255).  I think they're all dead, but it is
     possible that some have crept in. Please do not use -1 any more.  That's
     because on Cygwin, exiting with status -1 will not map to 255 -- instead
     (according to Brian Carlstrom) it gets mapped to status 0, and we
     certainly don't want to give a false impression of success!  Please
     replace it with {@link #EXIT_STATUS_MISC_TROUBLE}.
  */ int EXIT_STATUS_RECURSIVELY_SHUTTING_DOWN = 128;
  /* Note that XARGS uses status codes 123 through 127 specially.  You are
   * warned.  We keep out of the namespace from 129 upwards to 180 or so,
   * because Bash and other SH-compatible shells treat a command that dies
   * from an uncaught signal as if it had died with an exit status of 128 plus
   * the signal number.  For example, dying with SIGABRT (signal #6) gives an
   * exit status of 134.  */
  /** Traditionally the shell and xargs use status 127 to mean that
   * they were unable to find something to execute.
   * To quote the bash manpage, "If a command is found
   *  but is not executable, the return status is 126."
   * We shall adopt those customs here. --Steve Augart*/
  int EXIT_STATUS_EXECUTABLE_NOT_FOUND = 127;
  int EXIT_STATUS_COULD_NOT_EXECUTE = 126;
  int EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR = 125;
  int EXIT_STATUS_DUMP_STACK_AND_DIE = 124;
  int EXIT_STATUS_MAIN_THREAD_COULD_NOT_LAUNCH = 123;
  int EXIT_STATUS_MISC_TROUBLE = 122;
  int EXIT_STATUS_SYSFAIL = EXIT_STATUS_DUMP_STACK_AND_DIE;
  int EXIT_STATUS_SYSCALL_TROUBLE = 121;
  int EXIT_STATUS_TIMER_TROUBLE = EXIT_STATUS_SYSCALL_TROUBLE;
  int EXIT_STATUS_UNEXPECTED_CALL_TO_SYS = 120;
  int EXIT_STATUS_UNSUPPORTED_INTERNAL_OP = EXIT_STATUS_UNEXPECTED_CALL_TO_SYS;
  int EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION = 113;
  int EXIT_STATUS_OPT_COMPILER_FAILED = 101;
  /** same as OPT compiler */
  int EXIT_STATUS_JNI_COMPILER_FAILED = 101;
  int EXIT_STATUS_BOGUS_COMMAND_LINE_ARG = 100;
  int EXIT_STATUS_TOO_MANY_THROWABLE_ERRORS = 99;
  int EXIT_STATUS_TOO_MANY_OUT_OF_MEMORY_ERRORS = EXIT_STATUS_TOO_MANY_THROWABLE_ERRORS;
  int EXIT_STATUS_JNI_TROUBLE = 98;
  /** Used in 0005fProcess.C */
  int EXIT_STATUS_BAD_WORKING_DIR = EXIT_STATUS_JNI_TROUBLE;
  /** What exit status should we use after we have printed out a help message?
   *  Some common utilities exit with 1, some with 0.  Jikes RVM seems
   *  to be using 1, so let's keep doing so. */
  int EXIT_STATUS_PRINTED_HELP_MESSAGE = 1;
}
