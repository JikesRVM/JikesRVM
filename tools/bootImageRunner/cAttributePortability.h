#ifndef C_ATTRIBUTE_PORTABILITY_H_INCLUDED
#define C_ATTRIBUTE_PORTABILITY_H_INCLUDED
/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2003
 */

/* This hair gets around hassles in GNU C 2.96, where the unused attribute is
   not only ignored, it triggers a warning message! */
#if defined __GNUC__ && (__GNUC__ >= 3) && ! defined UNUSED
  #define UNUSED __attribute__((unused))
  #define NONNULL(idx) __attribute__((nonnull(idx)))
#else 
  #define UNUSED
  #define NONNULL(idx)
#endif

#endif /* #ifndef C_ATTRIBUTE_PORTABILITY_H_INCLUDED */
