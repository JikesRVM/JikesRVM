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

// Layout of an AIX function linkage area.
//
//
#include <inttypes.h>
#ifdef RVM_FOR_32_ADDR
struct AixLinkageLayout
   {
   uint32_t ip;         // value to put in IP register
   uint32_t toc;        // value to put in TOC register
   uint32_t environ;    // value to put in R11 (for PASCAL only)
   };
#else
struct AixLinkageLayout
   {
   uint64_t ip;         // value to put in IP register
   uint64_t toc;        // value to put in TOC register
   uint64_t environ;    // value to put in R11 (for PASCAL only)
   };
#endif
