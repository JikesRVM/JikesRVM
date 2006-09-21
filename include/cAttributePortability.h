#ifndef C_ATTRIBUTE_PORTABILITY_H_INCLUDED
#define C_ATTRIBUTE_PORTABILITY_H_INCLUDED
/*
 * (C) Copyright IBM Corp. 2003
 */
/* $Id$
 * @author Steven Augart 
 */

/* Unless we're running GCC 3.0 or greater, just turn off the 'unused',
   attributes, since GCC 2.96 complains about them, and other compilers will
   not even parse them.  */ 

//#if ! defined __GNUC__ || (__GNUC__ < 3)
//#define __attribute__(args)
//#endif

/* This hair gets around hassles in GNU C 2.96, where the unused attribute is
   not only ignored, it triggers a warning message! */
#if defined __GNUC__ && (__GNUC__ >= 3) && ! defined UNUSED
/* In GNU C, __attribute__((unused)) really means "possibly unused". */
#  define POSSIBLY_UNUSED UNUSED
#  define UNUSED __attribute__((unused))
#  ifdef RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
#    define UNUSED_SVP UNUSED
#  endif
#  define NONNULL(idx) __attribute__((nonnull(idx)))

// The __signal__ attribute is only relevant on GCC on the AVR processor.
// We don't (yet) work on the AVR, so this code will probably never be
// executed.  
#ifdef __avr__
#  define SIGNAL_ATTRIBUTE    __attribute__((__signal__))
#endif
#endif

#ifndef UNUSED_SVP
#  define UNUSED_SVP
#endif

#ifndef UNUSED
#  define UNUSED
#endif

#ifndef POSSIBLY_UNUSED
#  define POSSIBLY_UNUSED
#endif

#ifndef NONNULL
#  define NONNULL(idx)
#endif

#ifndef SIGNAL_ATTRIBUTE
#  define SIGNAL_ATTRIBUTE
#endif

    
#endif /* #ifndef C_ATTRIBUTE_PORTABILITY_H_INCLUDED */
