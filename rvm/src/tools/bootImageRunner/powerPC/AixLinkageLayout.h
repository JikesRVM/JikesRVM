/*
 * (C) Copyright IBM Corp. 2001
 */
// Layout of an AIX function linkage area.
//
struct AixLinkageLayout
   {
   int ip;         // value to put in IP register
   int toc;        // value to put in TOC register
   int environ;    // value to put in R11 (for PASCAL only)
   };
