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

EXTERNAL void sysStackAlignmentTest() {
  TRACE_PRINTF("StackAlignmentTest\n");
#ifdef __x86_64__
#ifndef __SSE2__
#error "x64 builds must have SSE2 enabled"
#endif
  asm volatile("movapd (%rsp), %XMM0");
#endif
// Tests for platforms other than x64 are currently not implemented
}


EXTERNAL void sysArgumentPassingTest(long long firstLong, long long secondLong, long long thirdLong, long long fourthLong,
    long long fifthLong, long long sixthLong, long long seventhLong, long long eightLong, double firstDouble, double secondDouble,
  double thirdDouble, double fourthDouble, double fifthDouble, double sixthDouble, double seventhDouble,
  double eightDouble, int firstInt, long long ninthLong, const char * firstByteArray, double ninthDouble, Address firstAddress) {
  CONSOLE_PRINTF("First long %lld\n", firstLong);
  CONSOLE_PRINTF("Second long %lld\n", secondLong);
  CONSOLE_PRINTF("Third long %lld\n", thirdLong);
  CONSOLE_PRINTF("Fourth long %lld\n", fourthLong);
  CONSOLE_PRINTF("Fifth long %lld\n", fifthLong);
  CONSOLE_PRINTF("Sixth long %lld\n", sixthLong);
  CONSOLE_PRINTF("Seventh long %lld\n", seventhLong);
  CONSOLE_PRINTF("Eight long %lld\n", eightLong);
  CONSOLE_PRINTF("First double %f\n", firstDouble);
  CONSOLE_PRINTF("Second double %f\n", secondDouble);
  CONSOLE_PRINTF("Third double %f\n", thirdDouble);
  CONSOLE_PRINTF("Fourth double %f\n", fourthDouble);
  CONSOLE_PRINTF("Fifth double %f\n", fifthDouble);
  CONSOLE_PRINTF("Sixth double %f\n", sixthDouble);
  CONSOLE_PRINTF("Seventh double %f\n", seventhDouble);
  CONSOLE_PRINTF("Eight double %f\n", eightDouble);
  CONSOLE_PRINTF("First integer %d\n", firstInt);
  CONSOLE_PRINTF("Ninth long %lld\n", ninthLong);
  CONSOLE_PRINTF("First byte[] %s\n", firstByteArray);
  CONSOLE_PRINTF("Ninth double %f\n", ninthDouble);
  CONSOLE_PRINTF("First address %p\n", (void *)firstAddress);
}
