/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
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
