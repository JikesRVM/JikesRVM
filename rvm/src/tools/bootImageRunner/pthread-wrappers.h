/* -*-coding: iso-8859-1 -*-
 *
 * (C) Copyright © IBM Corp 2004
 *
 * $Id$
 */

/** 
 * Define a bunch of stubs so that we never have any calls to real pthread
 * methods in our own sources.  This requires that we build shadowed versions
 * of sys.o and libvm.o.
 *
 * @author Steven Augart
 * @date   11 Feb 2005
 */
#ifndef _PTHREAD_WRAPPERS_H_
#define _PTHREAD_WRAPPERS_H_
/*
 * It is unfortunate that stdio.h on the GNU libc defines various
 * pthread types, such as pthread_t and pthread_mutex_t.  It means that we
 * can not define our own silly versions of those types below; that would be
 * the natural thing to do, but we can't.  
 * So, FIRST we include <pthread.h>, to define all of the types that the
 * function prototypes need, THEN we define macro versions of all of the
 * functions that we use. 
 *
 * Including <pthread.h> means that if we ignorantly fail to override some
 * pthread function or other, we may not immediately get a failure to compile.
 *
 * We won't even necessarily fail to link with -lpthread, because the GNU C
 * library includes its own versions of some of those stubs.  Ugly.
 */
#include <pthread.h>            /* Just use the normal version of this file if
                                 * we are not doing the
                                 * single-virtual-processor stuff. */  

#if defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR && RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
#include <stdio.h>

/* This is the approach that does not work: */
/* typedef int pthread_t; */
/* typedef int pthread_mutex_t; */
/* #define PTHREAD_MUTEX_INITIALIZER 0xCCCCCCC */


/*  This really should be a regular global function that is shared among files
 *  that include this.  Laziness.  FIXME */
static int
rvm_never_call(const char funcname[], const char filename[], int lineno)
{
    fprintf(stderr, "%s:%d: Internal error: the function %s was called\n", 
            filename, lineno, funcname);
    abort();
    return -1;
}


#define never_call(fname) (rvm_never_call(#fname, __FILE__, __LINE__), 0x0CCCCCCC)

#define pthread_attr_init(attr)                 \
    never_call(pthread_attr_init)
#define pthread_attr_setscope(attr, scope)      \
    never_call(pthread_attr_setscope)
#define pthread_cond_broadcast(cond)            \
    never_call(pthread_cond_broadcast)
#define pthread_cond_wait(cond,mutex)           \
    never_call(pthread_cond_wait)
#define pthread_create(thread, attr, start_routine, arg)    \
    never_call(pthread_create)
#define pthread_exit(retval)        \
    never_call(pthread_exit)
#define pthread_join(th, thread_return)     \
    never_call(pthread_join)
#define pthread_key_create(key, destr_function)         \
    never_call(pthread_key_create)
#define pthread_kill(thread, signo)                     \
    never_call(pthread_kill)
#define pthread_mutex_lock(arg)                         \
    never_call(pthread_mutex_lock)
#define pthread_mutex_unlock(arg)                       \
    never_call(pthread_mutex_unlock)
#define pthread_self()                                  \
                            never_call(pthread_self)
#define pthread_setspecific(key, ptr_val)               \
    never_call(pthread_setspecific)
/* only in syswrap.C, but include anyway. */
#define pthread_getspecific(key)        \
    ((void *) never_call(pthread_getspecific))
#define pthread_sigmask(how, newmask, oldmask)          \
    never_call(pthread_sigmask)
#define sigwait(input_set, sig)          \
    never_call(sigwait)
// sigthreadmask is AIX-specific:
#define sigthreadmask(sig, input_set, output_set)          \
    never_call(sigthreadmask)
         
#endif // #if defined RVM_FOR_SINGLE_VIRTUAL_PROCESSOR && RVM_FOR_SINGLE_VIRTUAL_PROCESSOR

#endif // #ifndef _PTHREAD_WRAPPERS_H_
