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

#include "sys.h"

// alignment checking: hardware alignment checking variables and functions

#ifdef RVM_WITH_ALIGNMENT_CHECKING

volatile int numNativeAlignTraps;
volatile int numEightByteAlignTraps;
volatile int numBadAlignTraps;

volatile int numEnableAlignCheckingCalls = 0;
static volatile int numDisableAlignCheckingCalls = 0;

EXTERNAL void sysEnableAlignmentChecking() {
  TRACE_PRINTF("%s: sysEnableAlignmentChecking\n", Me);
  numEnableAlignCheckingCalls++;
  if (numEnableAlignCheckingCalls > numDisableAlignCheckingCalls) {
    asm("pushf\n\t"
        "orl $0x00040000,(%esp)\n\t"
        "popf");
  }
}

EXTERNAL void sysDisableAlignmentChecking() {
  TRACE_PRINTF("%s: sysDisableAlignmentChecking\n", Me);
  numDisableAlignCheckingCalls++;
  asm("pushf\n\t"
      "andl $0xfffbffff,(%esp)\n\t"
      "popf");
}

EXTERNAL void sysReportAlignmentChecking() {
  CONSOLE_PRINTF("\nAlignment checking report:\n\n");
  CONSOLE_PRINTF("# native traps (ignored by default):             %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF("# 8-byte access traps (ignored by default):      %d\n", numEightByteAlignTraps);
  CONSOLE_PRINTF("# bad access traps (throw exception by default): %d (should be zero)\n\n", numBadAlignTraps);
  CONSOLE_PRINTF("# calls to sysEnableAlignmentChecking():         %d\n", numEnableAlignCheckingCalls);
  CONSOLE_PRINTF("# calls to sysDisableAlignmentChecking():        %d\n\n", numDisableAlignCheckingCalls);
  CONSOLE_PRINTF("# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF("# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);

  // cause a native trap to see if traps are enabled
  volatile int dummy[2];
  volatile int prevNumNativeTraps = numNativeAlignTraps;
  *(int*)((char*)dummy + 1) = 0x12345678;
  int enabled = (numNativeAlignTraps != prevNumNativeTraps);

  CONSOLE_PRINTF("# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF("# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);
  CONSOLE_PRINTF("Current status of alignment checking:            %s (should be on)\n\n", (enabled ? "on" : "off"));
}

#else

EXTERNAL void sysEnableAlignmentChecking() { }
EXTERNAL void sysDisableAlignmentChecking() { }
EXTERNAL void sysReportAlignmentChecking() { }

#endif // RVM_WITH_ALIGNMENT_CHECKING
