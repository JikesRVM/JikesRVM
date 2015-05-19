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
/*
 * Architecture specific thread code for PowerPC
 */

#include "sys.h"

/**
 * Transfer execution from C to Java for thread startup
 */
void bootThread (void *jtoc, void *tr, void *pc, void *fp)
{
  // Fixed register usage
  // OS:        |   Linux   |
  // Word size: | 64  | 32  |
  // Thread:    | R14 | R13 |
  // JTOC:      | R15 | R14 |
  asm volatile ("mr 1,  %3\n" // frame
#ifdef RVM_FOR_32_ADDR
                "mr 13, %1\n" // thread
                "mr 14, %0\n" // jtoc
#else
                "mr 14, %1\n" // thread
                "mr 2, %0\n" // jtoc
#endif // RVM_FOR_32_ADDR
                "mtlr %2\n"
                "blr    \n"
                : /* outs */
                : /* ins */
                  "r"(jtoc),
                  "r"(tr),
                  "r"(pc),
                  "r"(fp)
                );
}
