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
void bootThread (void *pc, void *tr, void *fp, void *sp, void *jtoc)
{
  asm volatile ("MOV SP , %3\n" // stack
                "MOV R11, %2\n" // frame
                "MOV R9,  %1\n" // thread
                "MOV R10, %4\n" // jtoc
                "BX %0\n"
                : /* outs */
                : /* ins */
                  "r"(pc),
                  "r"(tr),
                  "r"(fp),
                  "r"(sp),
                  "r"(jtoc)
                );
}
