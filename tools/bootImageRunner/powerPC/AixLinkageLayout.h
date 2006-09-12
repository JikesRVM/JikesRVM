/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
// Layout of an AIX function linkage area.
//
// @author unascribed
//
#include <inttypes.h>
#ifdef RVM_FOR_32_ADDR
struct AixLinkageLayout
   {
   uint32_t ip;         // value to put in IP register
   uint32_t toc;        // value to put in TOC register
   uint32_t environ;    // value to put in R11 (for PASCAL only)
   };
#endif

#ifdef RVM_FOR_64_ADDR
struct AixLinkageLayout
   {
   uint64_t ip;         // value to put in IP register
   uint64_t toc;        // value to put in TOC register
   uint64_t environ;    // value to put in R11 (for PASCAL only)
   };
#endif
