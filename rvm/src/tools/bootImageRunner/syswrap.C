/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * Wrappers for blocking system calls.
 *
 * By linking the JikesRVM executable against this shared library,
 * we can define our own versions of blocking system calls (from the
 * platform's C library) which call back into the VM.  That way,
 * we can make native code play more nicely with Java threads.
 *
 * For example, we can prevent select() calls made from JNI code
 * from blocking the virtual processor.
 *
 * This also defines the JNI VM Invocation Interface calls.
 *
 * @author David Hovemeyer
 * @date 10 Jun 2002
 */

#define VERBOSE_WRAPPERS 0

#if !defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
# include <pthread.h>
#endif
#include <unistd.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <stdio.h>
#include <jni.h>

#include <sys/poll.h>

#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#include "InterfaceDeclarations.h"
#include "bootImageRunner.h"

/* Forward declaration of a function defined later on in this file.  Goes into
   the JNI dispatch table. */
extern jint GetEnv(JavaVM *, void **, jint);

#include "syswrap.h"

//////////////////////////////////////////////////////////////
// Private functions and data
//////////////////////////////////////////////////////////////

// FIXME: should not hardcode name of C library
//        should be determined by build system
// FIXME: this works for recent Linux, need to update for AIX
#define C_LIBRARY_NAME "libc.so.6"

// Pointers to actual syscall functions from C library.
static SelectFunc_t libcSelect;
static PollFunc_t libcPoll;

// Get a pointer to a symbol from the C library.
//
// Taken:
// symbolName - name of symbol
// pPtr - address of pointer variable in which to store
//        the address of the symbol
static void 
getRealSymbol(const char *symbolName, void **pPtr)
{
    static void *libcHandle;

    // FIXME: should handle errors
    if (*pPtr == 0) {
        if (libcHandle == 0)
            libcHandle = dlopen(C_LIBRARY_NAME, RTLD_LAZY);
        *pPtr = dlsym(libcHandle, symbolName);
    }
}

// Fish out an address stored in an instance field of an object.
static void *
getFieldAsAddress(void *objPtr, int fieldOffset)
{
    char *fieldAddress = ((char*) objPtr) + fieldOffset;
    return *((void**) fieldAddress);
}

// Get the JNI environment object from the VM_Processor.
JNIEnv *
getJniEnvFromVmProcessor(void *vmProcessorPtr)
{
    if (vmProcessorPtr == 0)
        return 0; // oops

    // Follow chain of pointers:
    // VM_Processor -> VM_Thread -> VM_JNIEnvironment -> thread's native JNIEnv
    void *vmThreadPtr =
        getFieldAsAddress(vmProcessorPtr, VM_Processor_activeThread_offset);
    void *jniEnvironment =
        getFieldAsAddress(vmThreadPtr, VM_Thread_jniEnv_offset);
    // Convert VM_JNIEnvironment to JNIEnv* expected by native code
    // by creating the appropriate interior pointer.
    void *jniEnv = ((char*)jniEnvironment + VM_JNIEnvironment_JNIExternalFunctions_offset);

    return (JNIEnv*) jniEnv;
}

// Arbitrarily consider anything longer than 1 second a "long" wait.
// We may want to adjust this so any non-zero wait is considered long.
static bool 
isLongWait(struct timeval *timeout)
{
    return timeout == 0 || timeout->tv_sec > 0 || timeout->tv_usec > 1000;
}

#if defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
// Address of the single VM_Processor object.
static VM_Address VmProcessor;
#endif

// Return the number of file descriptors which are set in given
// fd_set.
static int 
countFileDescriptors(fd_set *fdSet, int maxFd)
{
    int count = 0;
    for (int i = 0; i < maxFd; ++i) {
        if (FD_ISSET(i, fdSet))
            ++count;
    }
    return count;
}

// Transfer file descriptors from an fd_set to a Java array of integer.
static void 
addFileDescriptors(jint *elements, fd_set *fdSet, int maxFd)
{
    int count = 0;
    for (int i = 0; i < maxFd; ++i) {
        if (FD_ISSET(i, fdSet))
            elements[count++] = i;
    }
}

// Convert an fd_set into a Java array of integer containing the
// file descriptors which are set in the fd_set.
static jintArray 
fdSetToIntArray(JNIEnv *env, fd_set *fdSet, int maxFd)
{
    jintArray arr = 0;
    if (fdSet != 0) {
        int count = countFileDescriptors(fdSet, maxFd);
        arr = env->NewIntArray(count);
        jint *elements = env->GetIntArrayElements(arr, 0);
        addFileDescriptors(elements, fdSet, maxFd);
        env->ReleaseIntArrayElements(arr, elements, 0);
    }
    return arr;
}

// Scan through java array of file descriptors, noting
// the ones that are marked as ready and setting them
// in the given fd_set.
//
// Returns: count of file descriptors marked as ready
//
// TODO: check for FD_INVALID_BIT
static int 
updateStatus(JNIEnv *env, fd_set *fdSet, jintArray fdArray)
{
    int readyCount = 0;
    if (fdSet != 0) {
        // Clear the fd_set.
        FD_ZERO(fdSet);

        // Scan through the elements of the array
        jsize length = env->GetArrayLength(fdArray);
        jint *elements = env->GetIntArrayElements(fdArray, 0);
        for (jsize i = 0; i < length; ++i) {
            int fd = elements[i];
            if ((fd & VM_ThreadIOConstants_FD_READY_BIT) != 0) {
                fd &= VM_ThreadIOConstants_FD_MASK;
                FD_SET(fd, fdSet);
                ++readyCount;
            }
        }
        env->ReleaseIntArrayElements(fdArray, elements, 0);
    }
    return readyCount;
}

//////////////////////////////////////////////////////////////
// Accessors for C library functions
//////////////////////////////////////////////////////////////

// Get a pointer to the real select() call.
//
// Returned:
// Pointer to select() function in C library.
SelectFunc_t 
getLibcSelect(void)
{
    getRealSymbol("select", (void**) &libcSelect);
    return libcSelect;
}

//////////////////////////////////////////////////////////////
// Wrappers for C library functions
//////////////////////////////////////////////////////////////

// Wrapper for the select() system call.
// If the call might block for a long time, puts the Java thread on
// the VM_ThreadIOQueue, to avoid blocking the entire virtual processor.
//
// Taken:
// maxFd - value of highest-numbered file descriptor passed in,
//     plus 1
// readFdSet - set of read file descriptors to query
// writeFdSet - set of write file descriptors to query
// except - set of exception file descriptors to query
// timeout - how long to wait
//
// Returned:
// -1: on error (errno should be set)
//  0: if no file descriptors became ready prior to timeout
// >0: number of file descriptors that are ready
extern "C" int 
select(int maxFd, fd_set *readFdSet, fd_set *writeFdSet,
       fd_set *exceptFdSet, struct timeval *timeout)
{
    if (VERBOSE_WRAPPERS)
	fprintf(stderr, "Calling select wrapper\n");
    getRealSymbol("select", (void**) &libcSelect);

    // If timeout is short, just call real select().
    if (!isLongWait(timeout)) {
        return libcSelect(maxFd, readFdSet, writeFdSet, exceptFdSet, timeout);
    }

    // Get the JNIEnv from the VM_Processor object
    JNIEnv *env;
    GetEnv(NULL, (void**) &env, JNI_VERSION_1_1);

    // Build int arrays to describe file descriptor sets
    jintArray readArr = fdSetToIntArray(env, readFdSet, maxFd);
    jintArray writeArr = fdSetToIntArray(env, writeFdSet, maxFd);
    jintArray exceptArr = fdSetToIntArray(env, exceptFdSet, maxFd);

    // Figure out how many seconds to wait
    double totalWaitTime;
    if (timeout == 0)
        totalWaitTime = VM_ThreadEventConstants_WAIT_INFINITE;
    else {
        totalWaitTime = ((double) timeout->tv_sec);
        totalWaitTime += ((double) timeout->tv_usec) / 1000000.0;
    }

    // Call VM_Thread.ioWaitSelect()
    jclass vmWaitClass = env->FindClass("com/ibm/JikesRVM/VM_Wait");
    jmethodID ioWaitSelectMethod = env->GetStaticMethodID(vmWaitClass,
                                                          "ioWaitSelect", "([I[I[IDZ)V");
    env->CallStaticVoidMethod(vmWaitClass, ioWaitSelectMethod,
                              readArr, writeArr, exceptArr, totalWaitTime, (jboolean) 1);

    // TODO: should have return value from ioWaitSelect(), for returning errors

    // For each file descriptor set in the Java arrays,
    // mark the corresponding entry in the fd_sets (as appropriate).
    int readyCount = 0;
    readyCount += updateStatus(env, readFdSet, readArr);
    readyCount += updateStatus(env, writeFdSet, writeArr);
    readyCount += updateStatus(env, exceptFdSet, exceptArr);

    return readyCount;
}

// Wrapper for the poll() system call, which we re-implement using select
//
// Note: we are taking on faith the claim in the select_tut(2) man page 
// that exceptfds in select is really used for out-of-band data and not
// for exceptions.  See select_tut(2) for details.
//
// Addendum: Yes, this is correct.  Strictly, exceptfds is used for
// "High-Priority data", which is defined by each type of descriptor
// independently.  (For example, certain devices have a notion of exceptional
// or high-priority data, and their Linux drivers  behave appropriately).
// --Steve Augart
//
extern "C" int
poll(struct pollfd *ufds, long unsigned int nfds, int timeout) 
{
    if (VERBOSE_WRAPPERS)
	fprintf(stderr, "Calling poll wrapper\n");
    fd_set readfds, writefds, exceptfds;
    struct timeval tv;
    struct timeval *tv_ptr;
    int max_fd;
    int ready;

    getRealSymbol("poll", (void**) &libcPoll);

    // If timeout is short, just call real poll().
    if (timeout == 0 || timeout == 1) {
        return libcPoll(ufds, nfds, timeout);
    }

    FD_ZERO( &readfds );
    FD_ZERO( &writefds );
    FD_ZERO( &exceptfds );

    if (timeout < 0) 
        tv_ptr = NULL;
    else {
        tv.tv_sec = timeout / 1000;
        tv.tv_usec = (timeout - (tv.tv_sec*1000)) * 1000;
        tv_ptr = &tv;
    }

    max_fd = 0;

    for (unsigned i = 0; i < nfds; i++) {

        if (ufds[i].fd+1 > max_fd)
            max_fd = ufds[i].fd+1;

        if (ufds[i].events&POLLIN)
            FD_SET( ufds[i].fd, &readfds );

        if (ufds[i].events&POLLOUT)
            FD_SET( ufds[i].fd, &writefds );

        if (ufds[i].events&POLLPRI)
            FD_SET( ufds[i].fd, &exceptfds );
    }

    ready = select(max_fd, &readfds, &writefds, &exceptfds, &tv);
    
    for (unsigned i = 0; i < nfds; i++) {

        if (ufds[i].events&POLLIN && FD_ISSET( ufds[i].fd, &readfds ))
            ufds[i].revents |= POLLIN;

        if (ufds[i].events&POLLOUT && FD_ISSET( ufds[i].fd, &writefds ))
            ufds[i].revents |= POLLOUT;

        if (ufds[i].events&POLLPRI && FD_ISSET( ufds[i].fd, &exceptfds ))
            ufds[i].revents |= POLLPRI;
    }

    return ready;
}

//////////////////////////////////////////////////////////////
// JNI Invocation API functions
//////////////////////////////////////////////////////////////

/** Destroying the Java VM only makes sense if you can first attach it.  */
jint 
DestroyJavaVM(JavaVM UNUSED * vm) 
{
    fprintf(stderr, "unimplemented DestroyJavaVM");
    return 0;
}

jint 
AttachCurrentThread(JavaVM UNUSED * vm, JNIEnv UNUSED ** penv, /* JavaVMAttachArgs */ void UNUSED *args) 
{
    fprintf(stderr, "unimplemented AttachCurrentThread");
    return 0;
}

jint 
DetachCurrentThread(JavaVM UNUSED *vm) 
{
    fprintf(stderr, "unimplemented DetachCurrentThread");
    return 0;
}
 
jint 
GetEnv(JavaVM UNUSED *vm, void **penv, jint version) 
{ 
    if (version > JNI_VERSION_1_4)
        return JNI_EVERSION;

#if !defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
    // Return NULL if we are not on a VM pthread
    if (pthread_getspecific(IsVmProcessorKey) == NULL) {
        *penv = NULL;
        return -1;
    }
#endif

    // Get VM_Processor id.
    void* vmProcessor =
#if defined(RVM_FOR_SINGLE_VIRTUAL_PROCESSOR)
      VmProcessor;
#else
      pthread_getspecific(VmProcessorKey);
#endif

    // Get the JNIEnv from the VM_Processor object
    JNIEnv *env = getJniEnvFromVmProcessor(vmProcessor);
 
    *penv = env;

    return JNI_OK;
}

jint 
AttachCurrentThreadAsDaemon(JavaVM UNUSED * vm, JNIEnv UNUSED ** penv, /* JavaVMAttachArgs */ void UNUSED *args) 
{
    fprintf(stderr, "unimplemented AttachCurrentThreadAsDaemon");
    return 0;
}

struct JNIInvokeInterface_ externalJNIFunctions = {
    NULL,
    NULL,
    NULL,
    DestroyJavaVM,
    AttachCurrentThread,
    DetachCurrentThread,
    GetEnv,			// JNI 1.2
    AttachCurrentThreadAsDaemon	// JNI 1.4
};


VM_Address 
createJavaVM(void)
{
    JavaVM *theJikesRVM = (struct JavaVM_ *) malloc (sizeof(struct JavaVM_));
    theJikesRVM->functions = &externalJNIFunctions;

    return (VM_Address) theJikesRVM;
}

/*
 * Class:     VM_JNIFunctions
 * Method:    createJavaVM
 * Signature: ()I
 */
extern "C" JNIEXPORT jint JNICALL 
Java_com_ibm_JikesRVM_jni_VM_1JNIFunctions_createJavaVM(JNIEnv *, jclass)
{
    return createJavaVM();
}

