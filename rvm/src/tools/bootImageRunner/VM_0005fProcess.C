// Unix includes
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>
#include <malloc.h>
#include <string.h>
#include <strings.h>
#include <stdlib.h>

// Java includes
#include <jni.h>

// generated class header
#include "VM_0005fProcess.h"

// local stuff
bool DEBUG = false;

/*
 * Class:     VM_0005fProcess
 * Method:    isDead
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_VM_1Process_isDead
  (JNIEnv *env, jobject self)
{
  // extract pid field from VM_Process object
  jclass ProcessClassID = env->GetObjectClass( self );
  jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
  int pid = env->GetIntField(self, pidFieldID);

  // check status of Unix process
  int status = 0;
  pid_t ret = waitpid((pid_t)pid, &status, WNOHANG);
  
  // return to java whether or not job exited somehow
  if (WIFEXITED(status) || WIFSIGNALED(status))
    return (jboolean) 1;
  else
    return (jboolean) 0;
}

/*
 * Class:     VM_0005fProcess
 * Method:    exitValueInternal
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_VM_1Process_exitValueInternal
  (JNIEnv *env, jobject self)
{
  // extract pid field from VM_Process object
  jclass ProcessClassID = env->GetObjectClass( self );
  jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
  int pid = env->GetIntField(self, pidFieldID);

  // check status of Unix process
  int status = 0;
  pid_t ret = waitpid((pid_t)pid, &status, WNOHANG);
  
  // return to java the exit status
  return (jint) WEXITSTATUS(status);
}

/*
 * Class:     VM_0005fProcess
 * Method:    waitForInternal
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_VM_1Process_waitForInternal
  (JNIEnv *env, jobject self)
{
  // extract pid field from VM_Process object
  jclass ProcessClassID = env->GetObjectClass( self );
  jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
  int pid = env->GetIntField(self, pidFieldID);

  // check status of Unix process
  int status = 0;
  pid_t ret = waitpid((pid_t)pid, &status, 0);
  
  // return to java the exit status
  return (jint) WEXITSTATUS(status);
}

/*
 * Class:     VM_0005fProcess
 * Method:    exec2
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_VM_1Process_exec2
  (JNIEnv *env, 
   jobject self, 
   jstring programName,
   jobjectArray argvArguments)
{
    return Java_VM_1Process_exec3(env,
				  self, 
				  programName, 
				  argvArguments,
				  (jobjectArray)0);
}

/*
 * Class:     VM_0005fProcess
 * Method:    exec3
 * Signature: (Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_VM_1Process_exec3
  (JNIEnv *env, 
   jobject self, 
   jstring programName,
   jobjectArray argvArguments,
   jobjectArray environment) 
{
    jboolean jfalse = 0;

    // get program to exec
    if (DEBUG) fprintf(stderr, "finding program name\n");
    const jchar *rubbish = env->GetStringChars(programName, &jfalse);
    char programString[ env->GetStringLength(programName)+1 ];
    bzero( programString, env->GetStringLength(programName)+1);
    for(int i = 0; i < env->GetStringLength(programName); i++)
	programString[i] = (char) rubbish[i];
    if (DEBUG) fprintf(stderr, "found program name %s\n", programString);

    // clean up program name
    env->ReleaseStringChars(programName, rubbish);
    
    // build argv arguments
    int argc = env->GetArrayLength( (jarray)argvArguments );
    char *argv[ argc ];
    for(int i = 0; i < argc; i++) {
	jstring arg = (jstring) env->GetObjectArrayElement( argvArguments, i );
	argv[i] = (char *) malloc(env->GetStringLength(arg)+1);
	bzero(argv[i], env->GetStringLength(arg)+1);
	const jchar *rubbish = env->GetStringChars(arg, &jfalse);
	for(int j = 0; j < env->GetStringLength(arg); j++)
	    argv[i][j] = (char) rubbish[j];

	if (DEBUG) fprintf(stderr, "arg %d is %s\n", i, argv[i]);
	env->ReleaseStringChars(arg, rubbish);
    }

    // build env
    int envc = 0;
    char **envp = (char**)0;
    if (environment) {
	envc = env->GetArrayLength( (jarray)environment );
	envp = (char**) malloc( envc * sizeof(char *) );
	for(int i = 0; i < envc; i++) {
	    jstring arg = (jstring) env->GetObjectArrayElement(environment, i);
	    envp[i] = (char *) malloc(env->GetStringLength(arg)+1);
	    bzero(envp[i], env->GetStringLength(arg)+1);
	    const jchar *rubbish = env->GetStringChars(arg, &jfalse);
	    for(int j = 0; j < env->GetStringLength(arg); j++)
		envp[i][j] = (char) rubbish[j];
	    
	    if (DEBUG) fprintf(stderr, "env %d is %s\n", i, envp[i]);
	    env->ReleaseStringChars(arg, rubbish);
	}
    }

    // pipe for stdin
    jclass ProcessClassID = env->FindClass( "VM_Process" );
    int inputDes[2]; 
    pipe(inputDes);
    jfieldID inID = env->GetFieldID(ProcessClassID, "inputDescriptor", "I");
    env->SetIntField(self, inID, inputDes[1]);
    if (DEBUG) fprintf(stderr, "using %d as stdin\n", inputDes[1]);
    
    // pipe for stdout
    int outputDes[2]; 
    pipe(outputDes);
    jfieldID outID = env->GetFieldID(ProcessClassID, "outputDescriptor", "I");
    env->SetIntField(self, outID, outputDes[0]);
    if (DEBUG) fprintf(stderr, "using %d as stdout\n", outputDes[0]);
    
    // pipe for stderr
    int errorDes[2]; 
    pipe(errorDes);
    jfieldID errID = env->GetFieldID(ProcessClassID, "errorDescriptor", "I");
    env->SetIntField(self, errID, errorDes[0]);
    if (DEBUG) fprintf(stderr, "using %d as stderr\n", errorDes[0]);
    
    // do the exec
    pid_t fid = fork();
    if (fid == 0) {
	// child
	dup2(inputDes[0], 0);
	dup2(outputDes[1], 1);       
	dup2(errorDes[1], 2);
	execve(programString, argv, envp);
	exit(0);
    } else {
	// parent
	jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
	env->SetIntField(self, pidFieldID, fid);
	if (DEBUG) fprintf(stderr, "child process id is %d\n", fid);

	// cleanup
	for(int i = 0; i < argc; i++) free( argv[i] );
	if (envp) {
	    for(int i = 0; i < envc; i++) free( envp[i] );
	    free(envp);
	}

	if (DEBUG) fprintf(stderr, "done exec\n");

	return fid;
    }
}

/*
 * Class:     VM_0005fProcess
 * Method:    destroy
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_VM_1Process_destroy
  (JNIEnv *env, jobject self)
{
  // extract pid field from VM_Process object
  jclass ProcessClassID = env->GetObjectClass( self );
  jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
  int pid = env->GetIntField(self, pidFieldID);

  // send kill signal
  kill(pid, SIGTERM);
}










