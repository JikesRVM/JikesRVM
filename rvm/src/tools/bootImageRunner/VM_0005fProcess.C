/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
/*****************************************************************
 * JNI interface to manage processes.
 *
 * @author Julian Dolby
 * @author David Hovemeyer
 * @date May 20, 2002
 */

// Unix includes
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#if (defined __MACH__ )
#include <crt_externs.h>
#define environ (*_NSGetEnviron())
#else
extern char **environ;
#endif

// Java includes
#include <jni.h>

// generated class header
#include "com_ibm_JikesRVM_VM_0005fProcess.h"

// local stuff

// Enable debug print statements?
//#define DEBUG

//////////////////////////////////////////////////////////////
// Private functions and data
//////////////////////////////////////////////////////////////

// A class to represent arrays of malloc'ed C strings
// as used for argv[] and env[] arrays.  By making this
// a class whose instances are allocated on the stack,
// we can be sure that the memory is cleaned
// up properly regardness of how the code using
// the array returns or handles errors.  (In C++ terminology,
// this is an "auto pointer").
class StringArray {
private:
  int numStrings;
  char **array;

public:
  // Constructor
  StringArray(int numStrings_) {
    // Allocate the array to hold pointers to the strings.
    // Note that both argv[] and env[] must be terminated
    // by a null pointer.  Hence, we need to allocate space
    // for an additional pointer (in addition to the pointers
    // for the actual strings).
    numStrings = numStrings_;
    int arrayLength = numStrings_ + 1;
    array = (char**) malloc(sizeof(char*) * (arrayLength));

    // Set all string pointers to null.
    for (int i = 0; i < arrayLength; ++i)
      array[i] = 0;
  }

  // Destructor
  ~StringArray() {
    // Free array and strings, if allocated
    if (array != 0) {
      for (int i = 0; i < numStrings; ++i) {
        if (array[i] != 0)
          free(array[i]);
      }
      free(array);
    }
  }

  // Return the array.
  char **get() {
    return array;
  }

  // Set a string, which is assumed to have been
  // allocated using malloc().
  void setAndAdoptString(int num, char *str) {
    array[num] = str;
  }

  // Release the array and strings.
  // Called to indicate that they don't need to be
  // deallocated.
  void release() {
    array = 0;
  }
};

// Simple auto pointer class for a single malloc'ed string.
class StringPtr {
private:
  char *str;

public:
  StringPtr(char *stringToAdopt) { str = stringToAdopt; }

  ~StringPtr() {
    if (str != 0)
      free(str);
  }

  char *get() { return str; }

  void release() { str = 0; }
};

// Convert a Java string to a C string.
//
// Taken:
// env - the JNIEnv
// jstr - the Java String to be converted
//
// Returned: pointer to a newly malloc'ed C string
static char *convertString(JNIEnv *env, jstring jstr)
{
  jsize len = env->GetStringLength(jstr);
  const jchar *javaChars = env->GetStringChars(jstr, 0);

  char *str = (char*) malloc(len + 1);

  // FIXME: we really should do a more intelligent conversion here
  for (int i = 0; i < len; ++i)
    str[i] = (char) javaChars[i];
  str[len] = '\0'; // ensure string is nul-terminated

  env->ReleaseStringChars(jstr, javaChars);

  return str;
}

// Constants for pipe creation and management
const int INPUT = 0, OUTPUT = 1;

// Create a pipe, and set appropriate file descriptor
// field in the VM_Process object.
static void createPipe(int descriptors[2], JNIEnv* env,
  jclass processClassID, jobject self, const char* fieldName, int end)
{
  pipe(descriptors); // FIXME: should check for error
  jfieldID fieldID = env->GetFieldID(processClassID, fieldName, "I");
  env->SetIntField(self, fieldID, descriptors[end]);
#ifdef DEBUG
  fprintf(stderr, "using %d as %s\n", descriptors[end], fieldName);
#endif
}

// Close file descriptors returned from pipe().
static void closePipe(int descriptors[])
{
  close(descriptors[INPUT]);
  close(descriptors[OUTPUT]);
}

// FIXME:
// Presumably we should throw some sort of I/O error
// (from Runtime.exec()) if we can't change into the
// working directory the caller specified.
// Instead, we'll just return this value as the exit code.
// See the definition (in VM.java) of VM.exitStatusJNITrouble; if you change
// this value, change it there too.
const int EXIT_STATUS_JNI_TROUBLE = 98;
const int EXIT_STATUS_BAD_WORKING_DIR = EXIT_STATUS_JNI_TROUBLE;

//////////////////////////////////////////////////////////////
// Implementation of native methods
//////////////////////////////////////////////////////////////

/*
 * Class:     VM_0005fProcess
 * Method:    exec4
 * Signature: (Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_ibm_JikesRVM_VM_1Process_exec4
  (JNIEnv *env, 
   jobject self, 
   jstring programName,
   jobjectArray argvArguments,
   jobjectArray environment,
   jstring dirPathStr) 
{

  // Get the program name
  StringPtr programString(convertString(env, programName));
#ifdef DEBUG
  fprintf(stderr, "program name is %s\n", programString.get());
#endif

  // Build argv array
  jsize argvLen = env->GetArrayLength((jarray) argvArguments);
  StringArray argv(argvLen);
  for (int i = 0; i < argvLen; ++i) {
    jstring arg = (jstring) env->GetObjectArrayElement(argvArguments, i);
    char *str = convertString(env, arg);
#ifdef DEBUG
    fprintf(stderr, "arg %d is %s\n", i, str);
#endif
    argv.setAndAdoptString(i, str);
  }

  // Build environment array (if any)
  jsize envpLen = (environment != 0)
    ? env->GetArrayLength((jarray) environment)
    : 0;
  StringArray envp(envpLen);
  for (int i = 0; i < envpLen; ++i) {
    jstring arg = (jstring) env->GetObjectArrayElement(environment, i);
    char *str = convertString(env, arg);
#ifdef DEBUG
    fprintf(stderr, "env %d is %s\n", i, str);
#endif
    envp.setAndAdoptString(i, str);
  }

  // Get the directory path (if any)
  StringPtr dirPath(
    (dirPathStr != 0)
      ? convertString(env, dirPathStr)
      : 0
  );
#ifdef DEBUG
  fprintf(stderr, "working directory is %s\n",
    (dirPath.get() != 0) ? dirPath.get() : "unspecified, will use current");
#endif

  // Create pipes to communicate with child process.
  // FIXME: should handle errors in pipe creation

  jclass ProcessClassID = env->FindClass( "com/ibm/JikesRVM/VM_Process" );
  int inputPipe[2], outputPipe[2], errorPipe[2]; 
  createPipe(inputPipe, env, ProcessClassID, self, "inputDescriptor", OUTPUT);
  createPipe(outputPipe, env, ProcessClassID, self, "outputDescriptor", INPUT);
  createPipe(errorPipe, env, ProcessClassID, self, "errorDescriptor", INPUT);
    
  // do the exec
  pid_t fid = fork();
  if (fid == 0) {
    // child

    // If a working directory was specified, try to
    // make it the current directory.
    if (dirPath.get() != 0) {
      if (chdir(dirPath.get()) != 0) {
#ifdef DEBUG
        fprintf(stderr, "chdir() failed: %s\n", strerror(errno));
#endif
        exit(EXIT_STATUS_BAD_WORKING_DIR);
      }
    }

    // Attach pipes to stdin, stdout, stderr
    // FIXME: should handle errors
    dup2(inputPipe[INPUT], 0);
    dup2(outputPipe[OUTPUT], 1);       
    dup2(errorPipe[OUTPUT], 2);

    // Close the original file descriptors returned by pipe()
    closePipe(inputPipe);
    closePipe(outputPipe);
    closePipe(errorPipe);

    // Set environment for child process.
    if (environment != 0) {
      environ = envp.get();
    }
#if 0
    else {
      fprintf(stderr, "Current environment:\n");
      char **p = environ;
      while (*p != 0 ) {
        fprintf(stderr, "\t%s\n", *p);
        ++p;
      }
    }
#endif

    // Execute the program.
    // XXX See comment below on error handling.
    // int err = execvp(programString.get(), argv.get());
    (void) execvp(programString.get(), argv.get());
    // We get here only if an error occurred.
    
#ifdef DEBUG
    fprintf(stderr, "execvp() failed: %s\n", strerror(errno));
#endif

    programString.release();
    argv.release();
    envp.release();
    dirPath.release();

    // FIXME:
    // Unfortunately, it's difficult to convey an error code
    // back to the parent process to let it know that we couldn't
    // actually execute the program.  We could use shared memory
    // or a special pipe to send the error information.
    // For now, just exit with a non-zero status.
    /* However, traditionally the shell and xargs use status 127 to mean that
     * they were unable to find something to execute.
     * To quote the bash manpage, "If a command is found
     *  but is not executable, the return status is 126.¨
     * We shall adopt those customs here. --Steve Augart*/
    if (errno == ENOENT || errno == ENOTDIR)
        exit(127);
    exit(126);                  // couldn't be executed for some other reason.
  } else if (fid > 0) {
    // parent

    // Store child's pid
    jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
    env->SetIntField(self, pidFieldID, fid);
#ifdef DEBUG
    fprintf(stderr, "child process id is %d\n", fid);
#endif

    // Close unused ends of pipes
    close(inputPipe[INPUT]);    // input side of child's stdin
    close(outputPipe[OUTPUT]);  // output side of child's stdout
    close(errorPipe[OUTPUT]);   // output side of child's stderr

    // Note: memory for programName, argv, and envp will be cleaned
    // up automatically

#ifdef DEBUG
    fprintf(stderr, "done exec\n");
#endif

    return fid;
  }
  else {
    // An error occurred in fork()
#ifdef DEBUG
    fprintf(stderr, "fork() failed: %s\n", strerror(errno));
#endif

    // Close pipes
    closePipe(inputPipe);
    closePipe(outputPipe);
    closePipe(errorPipe);

    return -1;
  }
}

/*
 * Class:     VM_0005fProcess
 * Method:    destroyInternal
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_ibm_JikesRVM_VM_1Process_destroyInternal
  (JNIEnv *env, jobject self)
{
  // extract pid field from VM_Process object
  jclass ProcessClassID = env->GetObjectClass( self );
  jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
  int pid = env->GetIntField(self, pidFieldID);

  // send kill signal
  kill(pid, SIGTERM);
}
