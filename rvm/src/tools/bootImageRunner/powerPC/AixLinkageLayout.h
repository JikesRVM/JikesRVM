/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
// Layout of an AIX function linkage area.
//
// @author unascribed
//
struct AixLinkageLayout
   {
   int ip;         // value to put in IP register
   int toc;        // value to put in TOC register
   int environ;    // value to put in R11 (for PASCAL only)
   };
