/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

/**
 * Obtained from sys.C
 *
 * @author Peter F Sweeney
 * @date December 29, 2003
 */

// These exit status codes are defined here and in VM.java.
// If you change one of them here, or add any, add it there too.
// See VM.java; vm.exitStatusSyscallTrouble
const int EXIT_STATUS_SYSCALL_TROUBLE = 121;
// See VM.java; vm.exitStatusTimerTrouble
const int EXIT_STATUS_TIMER_TROUBLE = EXIT_STATUS_SYSCALL_TROUBLE;

const int EXIT_STATUS_UNSUPPORTED_INTERNAL_OP = 120;

// see VM.exitStatusUnexpectedCallToSys:
const int EXIT_STATUS_UNEXPECTED_CALL_TO_SYS
            = EXIT_STATUS_UNSUPPORTED_INTERNAL_OP;


