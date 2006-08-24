/*
 * (C) Copyright IBM Corp. 2001
 * $Id$
 */
/*
 * @author unascribed
 */

#include <stdio.h>
#include "tNativeThreads.h"
#include <jni.h>
/* Header for class tNativeThreads */


/*
 * Class:     tNativeThreads
 * Method:    nativeFoo
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_tNativeThreads_nativeFoo
  (JNIEnv * env, jclass cls, jint cnt) {

  int i,j, sum1,sum2;
  jintArray myArray;

  i = 0; j = 0; sum1=0; sum2=0;

  /*
  myArray = (*env) -> NewIntArray(env, 11);
  printf("Java_tTango_nativeFoo: JNI call returns 0x%X\n", myArray);  
  */


  /*  printf("tNativeThreads.c: input = %d \n", cnt); */ 
  printf("******** %d ******\n",cnt); 
  /*sleep(10000000); */

  /***********
  for ( i = 0; i < 10000; i++){
    sum1 = sum1 += i;
    for ( j = 0; j < 5000; j++){
      sum2 = sum2 += j;
    }
  }
  **********/


#if _AIX43
   sched_yield();
#else
   pthread_yield();
#endif



  /*  printf("tNativeThreads.c: after loops: sum1 = %d , sum2 = %d \n", sum1,sum2); */

  printf("!!!!!!!! %d !!!!!!\n", cnt);  

  return cnt;
}



