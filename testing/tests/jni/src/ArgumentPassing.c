/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
/**
 * Native code for ArgumentPassing.java
 */

#include <stdio.h>
#include "ArgumentPassing.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     ArgumentPassing
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_ArgumentPassing_setVerboseOff
  (JNIEnv *env, jclass cls){
  verbose=0;
}


/*
 * Expect integer arguments:  1, 3, 5, 7, 9, 11
 * Class:     ArgumentPassing
 * Method:    integerFitStatic
 * Signature: (IIIIII)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerFitStatic
  (JNIEnv *env, jclass cls,
   jint val1, jint val2, jint val3, jint val4, jint val5, jint val6) {

  if (val1==1 && val2==3 && val3==5 && val4==7 && val5==9 && val6==11)
    return 0;
  else {
    if (verbose) {
      printf("> integerFitStatic in native: \n>   expect 1, 3, 5, 7, 9, 11 \n");
      printf(">   get %d, %d, %d, %d, %d, %d\n",
             val1, val2, val3,  val4, val5, val6);
    }
    return -1;
  }

}


/*
 * Expect integer arguments:  1, 3, 5, 7, 9, 11, 13, 15, 17, 19
 * Class:     ArgumentPassing
 * Method:    integerSpillAIXStatic
 * Signature: (IIIIIIIIII)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerSpillAIXStatic
  (JNIEnv *env, jclass cls,
   jint val1, jint val2, jint val3, jint val4, jint val5,
   jint val6, jint val7, jint val8, jint val9, jint val10) {

  if (val1==1  && val2==3  && val3==5  && val4==7  && val5==9 &&
      val6==11 && val7==13 && val8==15 && val9==17 && val10==19)
    return 0;
  else {
    if (verbose) {
      printf("> integerSpillAIXStatic in native: \n>   expect 1, 3, 5, 7, 9, 11, 13, 15, 17, 19 \n");
      printf(">   get %d, %d, %d, %d, %d, %d, %d, %d, %d, %d\n",
             val1, val2, val3,  val4, val5,
             val6, val7, val8,  val9, val10);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerSpillBothStatic
 * Signature: (IIIIIIIIIIII)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerSpillBothStatic
  (JNIEnv *env, jclass cls,
   jint val1, jint val2, jint val3, jint val4, jint val5,
   jint val6, jint val7, jint val8, jint val9, jint val10,
   jint val11, jint val12) {

  if (val1==1  && val2==3  && val3==5  && val4==7  && val5==9 &&
      val6==11 && val7==13 && val8==15 && val9==17 && val10==19 &&
      val11==21 && val12==23 )
    return 0;
  else {
    if (verbose) {
      printf("> integerSpillBothStatic in native: \n>   expect 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23 \n");
      printf(">   get %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d\n",
             val1, val2, val3,  val4, val5,
             val6, val7, val8,  val9, val10, val11, val12);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatFitStatic
 * Signature: (FFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatFitStatic
  (JNIEnv *env, jclass cls,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6) {

  /* NOTE:  in C, FP constants are always type double
   * which is 64 bits;  they need to be converted to type float
   * which is 32 bits before doing the comparison, otherwise
   * the lower bits may be different
   */

  if (fval1==(float)0.10 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0 && fval6==(float)1.25) {
    return 0;
  } else {
    if (verbose) {
      printf("> floatFitStatic in native: \n");
      printf(">   expect .10, .25, .50, .75, 1.0, 1.25 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatFitStaticSave
 * Signature: (FFFFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatFitStaticSave
  (JNIEnv *env, jclass cls,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6, jfloat fval7, jfloat fval8) {

  if (fval1==(float)0.1 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0 && fval6==(float)1.25 &&
      fval7==(float)1.50 && fval8==(float)1.75)
    return 0;
  else {
    if (verbose) {
      printf("> floatFitStaticSave in native: \n");
      printf(">   expect .1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8);
    }
    return -1;
  }


}


/*
 * Class:     ArgumentPassing
 * Method:    floatSpillAIXStatic
 * Signature: (FFFFFFFFFFFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatSpillAIXStatic
  (JNIEnv *env, jclass cls,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6, jfloat fval7, jfloat fval8,
   jfloat fval9, jfloat fval10, jfloat fval11, jfloat fval12,
   jfloat fval13, jfloat fval14, jfloat fval15) {

  if (fval1==(float)0.1 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0  && fval6==(float)1.25 &&
      fval7==(float)1.50 && fval8==(float)1.75 && fval9==(float)2.0 &&
      fval10==(float)2.25 && fval11==(float)2.50 && fval12==(float)2.75  &&
      fval13==(float)3.0 && fval14==(float)3.25 && fval15==(float)3.50 )
    return 0;
  else {
    if (verbose) {
      printf("> floatSpillAIXStatic in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15);
    }
    return -1;
  }

}



/*
 * Class:     ArgumentPassing
 * Method:    floatSpillBothStatic
 * Signature: (FFFFFFFFFFFFFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatSpillBothStatic
  (JNIEnv *env, jclass cls,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6, jfloat fval7, jfloat fval8,
   jfloat fval9, jfloat fval10, jfloat fval11, jfloat fval12,
   jfloat fval13, jfloat fval14, jfloat fval15, jfloat fval16,
   jfloat fval17) {

  if (fval1==(float)0.10 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0 && fval6==(float)1.25 &&
      fval7==(float)1.50 && fval8==(float)1.75 && fval9==(float)2.0 &&
      fval10==(float)2.25 && fval11==(float)2.50 && fval12==(float)2.75  &&
      fval13==(float)3.0 && fval14==(float)3.25 && fval15==(float)3.50 &&
      fval16==(float)3.75 && fval17==(float)4.0)
    return 0;
  else {
    if (verbose) {
      printf("> floatSpillBothStatic in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50, 3.75, 4.0 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15, fval16, fval17);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    doubleFitStatic
 * Signature: (DDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleFitStatic
  (JNIEnv *env, jclass cls, jdouble fval1, jdouble fval2, jdouble fval3) {

  if (fval1==0.1 && fval2==0.25 && fval3==0.50) {
    return 0;
  } else {
    if (verbose) {
      printf("> doubleFitStatic in native: \n");
      printf(">   expect 0.1, .25, .50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f \n", fval1, fval2, fval3);
    }
    return -1;
  }

}


/*
 * Class:     ArgumentPassing
 * Method:    doubleFitStaticStraddle
 * Signature: (IDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleFitStaticStraddle
  (JNIEnv *env, jclass cls, jint val1, jdouble fval2, jdouble fval3, jdouble fval4){

  if (val1== 3 && fval2==0.1 && fval3==0.25 && fval4==0.50) {
    return 0;
  } else {
    if (verbose) {
      printf("> doubleFitStaticStraddle in native: \n");
      printf(">   expect 0.1, .25, .50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f \n", fval2, fval3, fval4);
    }
    return -1;
  }
}


/*
 * Class:     ArgumentPassing
 * Method:    doubleFitStaticSave
 * Signature: (DDDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleFitStaticSave
  (JNIEnv *env, jclass cls, jdouble fval1, jdouble fval2, jdouble fval3,
   jdouble fval4, jdouble fval5) {
  if (fval1== 0.1 && fval2==0.25 && fval3==0.50 && fval4==0.75 && fval5==1.0) {
    return 0;
  } else {
    if (verbose) {
      printf("> doubleFitStaticSave in native: \n");
      printf(">   expect 0.1, 0.25, 0.50 , 0.75, 1.00\n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5);
    }
    return -1;
  }
}


/*
 * Class:     ArgumentPassing
 * Method:    doubleSpillAIXStatic
 * Signature: (DDDDDDDDDDDDDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleSpillAIXStatic
  (JNIEnv *env, jclass cls,
   jdouble fval1, jdouble fval2, jdouble fval3,
   jdouble fval4, jdouble fval5, jdouble fval6,
   jdouble fval7, jdouble fval8, jdouble fval9,
   jdouble fval10, jdouble fval11, jdouble fval12,
   jdouble fval13, jdouble fval14, jdouble fval15) {

  if (fval1==0.1 && fval2==0.25 && fval3==0.50 &&
      fval4==0.75 && fval5==1.0  && fval6==1.25 &&
      fval7==1.50 && fval8==1.75 && fval9==2.0 &&
      fval10==2.25 && fval11==2.50 && fval12==2.75  &&
      fval13==3.0 && fval14==3.25 && fval15==3.50 )
    return 0;
  else {
    if (verbose) {
      printf("> doubleSpillAIXStatic in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15);
    }
    return -1;
  }
}



/*
 * Class:     ArgumentPassing
 * Method:    doubleSpillBothStatic
 * Signature: (DDDDDDDDDDDDDDDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleSpillBothStatic
  (JNIEnv *env, jclass cls,
   jdouble fval1, jdouble fval2, jdouble fval3,
   jdouble fval4, jdouble fval5, jdouble fval6,
   jdouble fval7, jdouble fval8, jdouble fval9,
   jdouble fval10, jdouble fval11, jdouble fval12,
   jdouble fval13, jdouble fval14, jdouble fval15,
   jdouble fval16, jdouble fval17) {

  if (fval1==0.1 && fval2==0.25 && fval3==0.50 &&
      fval4==0.75 && fval5==1.0  && fval6==1.25 &&
      fval7==1.50 && fval8==1.75 && fval9==2.0 &&
      fval10==2.25 && fval11==2.50 && fval12==2.75  &&
      fval13==3.0 && fval14==3.25 && fval15==3.50 &&
      fval16==3.75 && fval17==4.00 )
    return 0;
  else {
    if (verbose) {
      printf("> doubleSpillBothStatic in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50, 3.75, 4.00 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15, fval16, fval17);
    }
    return -1;
  }
}



/*
 * Class:     ArgumentPassing
 * Method:    longFitStatic
 * Signature: (JJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longFitStatic
  (JNIEnv *env, jclass cls, jlong val1, jlong val2, jlong val3) {

  if (val1==1l && val2==3l && val3==5l)
    return 0;
  else {
    if (verbose) {
      printf("> longFitStatic in native: \n>   expect 1, 3, 5 \n");
      printf(">   get %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3);
    }
    return -1;
  }
}

/*
 * Class:     ArgumentPassing
 * Method:    longFitStaticStraddle
 * Signature: (IJJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longFitStaticStraddle
  (JNIEnv *env, jclass cls, jint val1, jlong val2, jlong val3, jlong val4) {

  if (val1==1 && val2==3l && val3==5l && val4==7l)
    return 0;
  else {
    if (verbose) {
      printf("> longFitStaticStraddle in native: \n>   expect 1, 3, 5, 7 \n");
      printf(">   get %d, %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3, (int) val4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    longSpillAIXStatic
 * Signature: (JJJJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longSpillAIXStatic
  (JNIEnv *env, jclass cls, jlong val1, jlong val2, jlong val3, jlong val4, jlong val5) {

  if (val1==1l && val2==3l && val3==5l && val4==7l && val5==9l)
    return 0;
  else {
    if (verbose) {
      printf("> longSpillAIXStatic in native: \n>   expect 1, 3, 5, 7, 9 \n");
      printf(">   get %d, %d, %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3,  (int) val4, (int) val5);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    longSpillBothStatic
 * Signature: (JJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longSpillBothStatic
  (JNIEnv *env, jclass cls, jlong val1, jlong val2, jlong val3, jlong val4, jlong val5,
   jlong val6, jlong val7) {

  if (val1==1l && val2==3l && val3==5l && val4==7l && val5==9l &&
      val6==11l && val7==13l )
    return 0;
  else {
    if (verbose) {
      printf("> longSpillBothStatic in native: \n>   expect 1, 3, 5, 7, 9, 11, 13 \n");
      printf(">   get %d, %d, %d, %d, %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3, (int) val4, (int) val5,
             (int) val6, (int) val7);
    }
    return -1;
  }
}


/*
 * Class:     ArgumentPassing
 * Method:    integerFloatFitStatic
 * Signature: (IFIFIF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerFloatFitStatic
  (JNIEnv *env, jclass cls,
   jint val1, jfloat fval2, jint val3,
   jfloat fval4, jint val5, jfloat fval6) {


  if (val1==1 && fval2==(float)3.3 && val3==5 && fval4==(float)7.7 &&
      val5==9 && fval6==(float)11.11)
    return 0;
  else {
    if (verbose) {
      printf("> integerFloatFitStatic in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7, 9, 11.11 \n");
      printf(">   get %d, %3.2f, %d, %3.2f, %d, %3.2f\n",
             val1, fval2, val3, fval4, val5, fval6);
    }
    return -1;
  }
}

/*
 * Class:     ArgumentPassing
 * Method:    integerFloatSpillStatic
 * Signature: (FIFIFIFI)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerFloatSpillStatic
  (JNIEnv *env, jclass cls,
   jfloat fval1, jint val2, jfloat fval3, jint val4,
   jfloat fval5, jint val6, jfloat fval7, jint val8) {

  if (fval1==(float)1.1 && val2==3  && fval3==(float)5.5 && val4==7 &&
      fval5==(float)9.9 && val6==11 && fval7==(float)13.13 && val8==15)
    return 0;
  else {
    if (verbose) {
      printf("> integerFloatSpillStatic in native: \n");
      printf(">   expect 1.1f, 3, 5.5f, 7, 9.9f, 11, 13.13f, 15\n");
      printf(">   get %3.2f, %d, %3.2f, %d, %3.2f, %d\n",
             fval1, val2, fval3, val4, fval5, val6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerDoubleFitStatic
 * Signature: (IDID)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerDoubleFitStatic
  (JNIEnv *env, jclass cls,
   jint val1, jdouble fval2, jint val3, jdouble fval4) {

  if (val1==1 && fval2==3.3 && val3==5 && fval4==7.7)
    return 0;
  else {
    if (verbose) {
      printf("> integerDoubleFitStatic in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7 \n");
      printf(">   get %d, %3.2f, %d, %3.2f\n",
             val1, fval2, val3, fval4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerDoubleFitStaticStraddle
 * Signature: (DIIID)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerDoubleFitStaticStraddle
  (JNIEnv *env, jclass cls,
   jdouble fval1, jint val2, jint val3, jint val4, jdouble fval5) {

  if (fval1==1.1 && val2==3 && val3==5 && val4==7 && fval5==9.9 )
    return 0;
  else {
    if (verbose) {
      printf("> integerDoubleFitStaticStraddle in native: \n");
      printf(">   expect 1.1, 3, 5, 7, 9.9 \n");
      printf(">   get %3.2f, %d, %d, %d, %3.2f\n",
             fval1, val2, val3,  val4, fval5);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerDoubleSpillStatic
 * Signature: (DIDIDI)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerDoubleSpillStatic
  (JNIEnv *env, jclass cls,
   jdouble fval1, jint val2, jdouble fval3, jint val4, jdouble fval5, jint val6) {

  if (fval1==1.1 && val2==3 && fval3==5.5 && val4==7 && fval5==9.9 && val6==11)
    return 0;
  else {
    if (verbose) {
      printf("> integerDoubleSpillStatic in native: \n");
      printf(">   expect 1.1, 3, 5.5, 7, 9.9, 11 \n");
      printf(">   get %3.2f, %d, %3.2f, %d, %3.2f, %d\n",
             fval1, val2, fval3,  val4, fval5, val6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerLongFitStatic
 * Signature: (IJIJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerLongFitStatic
  (JNIEnv *env, jclass cls,
   jint val1, jlong val2, jint val3, jlong val4) {

  if (val1==1 && val2==3 && val3==5 && val4==7)
    return 0;
  else {
    if (verbose) {
      printf(">  in native: \n");
      printf(">   expect 1, 3, 5, 7 \n");
      printf(">   get %d, %d, %d, %d\n",
             val1, (int) val2, val3, (int) val4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerLongFitStaticStraddle
 * Signature: (IJIIJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerLongFitStaticStraddle
  (JNIEnv *env, jclass cls,
   jint val1, jlong val2, jint val3, jint val4, jlong val5) {

  if (val1==1 && val2==3 && val3==5 && val4==7 && val5==9)
    return 0;
  else {
    if (verbose) {
      printf("> integerLongFitStaticStraddle in native: \n");
      printf(">   expect 1, 3, 5, 7, 9 \n");
      printf(">   get %d, %lld, %d, %d, %lld\n",
             val1, val2, val3,  val4, val5);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerLongSpillStatic
 * Signature: (JIJIJI)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerLongSpillStatic
  (JNIEnv *env, jclass cls,
   jlong val1, jint val2, jlong val3, jint val4, jlong val5, jint val6) {

  if (val1==1 && val2==3 && val3==5 && val4==7 && val5==9 && val6==11)
    return 0;
  else {
    if (verbose) {
      printf("> integerLongSpillStatic in native: \n");
      printf(">   expect 1, 3, 5, 7, 9, 11 \n");
      printf(">   get %d, %d, %d, %d, %d, %d\n",
             (int) val1, val2, (int) val3,  val4, (int) val5, val6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatDoubleFitStatic
 * Signature: (FDFD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatDoubleFitStatic
  (JNIEnv *env, jclass cls,
   jfloat fval1, jdouble fval2, jfloat fval3, jdouble fval4) {

  if (fval1==(float)1.1 && fval2==3.3 && fval3==(float)5.5 && fval4==7.7 )
    return 0;
  else {
    if (verbose) {
      printf("> floatDoubleFitStatic in native: \n");
      printf(">   expect 1.1, 3.3, 5.5, 7.7 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f\n",
             fval1, fval2, fval3, fval4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatDoubleSpillStatic
 * Signature: (DFDFDF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatDoubleSpillStatic
  (JNIEnv *env, jclass cls,
   jdouble fval1, jfloat fval2, jdouble fval3,
   jfloat fval4, jdouble fval5, jfloat fval6) {

  if (fval1==1.1 && fval2==(float)3.3 && fval3==5.5 &&
      fval4==(float)7.7 && fval5==9.9 && fval6==(float)11.11)
    return 0;
  else {
    if (verbose) {
      printf("> floatDoubleSpillStatic in native: \n");
      printf(">   expect 1.1, 3.3, 5.5, 7.7, 9.9, 11.11 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f\n",
             fval1, fval2, fval3, fval4, fval5, fval6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatLongFitStatic
 * Signature: (FJFJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatLongFitStatic
  (JNIEnv *env, jclass cls,
   jfloat fval1, jlong val2, jfloat fval3, jlong val4) {

  if (fval1==(float)1.1 && val2==3l && fval3==(float)5.5 && val4==7l )
    return 0;
  else {
    if (verbose) {
      printf("> floatLongFitStatic in native: \n");
      printf(">   expect 1.1, 3, 5.5, 7 \n");
      printf(">   get %3.2f, %d, %3.2f, %d\n",
             fval1, (int) val2, fval3, (int) val4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatLongSpillStatic
 * Signature: (JFJFJF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatLongSpillStatic
  (JNIEnv *env, jclass cls,
   jlong val1, jfloat fval2, jlong val3,
   jfloat fval4, jlong val5, jfloat fval6) {

  if (val1==1l && fval2==(float)3.3 && val3==5l &&
      fval4==(float)7.7 && val5==9l && fval6==(float)11.11)
    return 0;
  else {
    if (verbose) {
      printf("> floatLongSpillStatic in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7, 9, 11.11 \n");
      printf(">   get %d, %3.2f, %d, %3.2f, %d, %3.2f\n",
             (int) val1, fval2, (int) val3, fval4, (int) val5, fval6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    doubleLongFitStatic
 * Signature: (JDJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleLongFitStatic
  (JNIEnv *env, jclass cls,
   jlong val1, jdouble fval2, jlong val3) {

  if (val1==1l && fval2==3.3 && val3==5l )
    return 0;
  else {
    if (verbose) {
      printf("> doubleLongFitStatic in native: \n");
      printf(">   expect 1, 3.3, 5\n");
      printf(">   get %d, %3.2f, %d\n", (int) val1, fval2,(int)  val3);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    doubleLongSpillStatic
 * Signature: (JDJDJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleLongSpillStatic
  (JNIEnv *env, jclass cls,
   jlong val1, jdouble fval2, jlong val3, jdouble fval4, jlong val5) {

  if (val1==1l && fval2==3.3 && val3==5l && fval4==7.7 && val5==9l )
    return 0;
  else {
    if (verbose) {
      printf("> doubleLongSpillStatic in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7, 9 \n");
      printf(">   get %d, %3.2f, %d, %3.2f, %d\n",
             (int) val1, fval2, (int) val3, fval4, (int) val5);
    }
    return -1;
  }

}








/*****************************************************************************
 * Expect integer arguments:  2, 4, 6, 8, 10, 12
 * Class:     ArgumentPassing
 * Method:    integerFitVirtual
 * Signature: (IIIIII)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerFitVirtual
  (JNIEnv *env, jobject obj,
   jint val1, jint val2, jint val3, jint val4, jint val5, jint val6) {


  if (val1==2 && val2==4 && val3==6 && val4==8 && val5==10 && val6==12)
    return 0;
  else {
    if (verbose) {
      printf("> integerFitVirtual in native: \n>   expect 2, 4, 6, 8, 10, 12 \n");
      printf(">   get %d, %d, %d, %d, %d, %d\n",
             val1, val2, val3,  val4, val5, val6);
    }
    return -1;
  }

}


/*
 * Expect integer arguments:  2, 4, 6, 8, 10, 12, 14, 16, 18, 20
 * Class:     ArgumentPassing
 * Method:    integerSpillAIXVirtual
 * Signature: (IIIIIIIIII)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerSpillAIXVirtual
  (JNIEnv *env, jobject obj,
   jint val1, jint val2, jint val3, jint val4, jint val5,
   jint val6, jint val7, jint val8, jint val9) {

  if (val1==2 && val2==4 && val3==6 && val4==8 && val5==10 &&
      val6==12 && val7==14 && val8==16 && val9==18)
    return 0;
  else {
    if (verbose) {
      printf("> integerSpillAIXVirtual in native: \n>   expect 2, 4, 6, 8, 10, 12, 14, 16, 18\n");
      printf(">   get %d, %d, %d, %d, %d, %d, %d, %d, %d\n",
             val1, val2, val3,  val4, val5,
             val6, val7, val8,  val9);
    }
    return -1;
  }
}

/*
 * Class:     ArgumentPassing
 * Method:    integerSpillBothVirtual
 * Signature: (IIIIIIIIIII)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerSpillBothVirtual
  (JNIEnv *env, jobject obj,
   jint val1, jint val2, jint val3, jint val4, jint val5,
   jint val6, jint val7, jint val8, jint val9, jint val10, jint val11) {

  if (val1==2 && val2==4 && val3==6 && val4==8 && val5==10 &&
      val6==12 && val7==14 && val8==16 && val9==18 &&
      val10==20 && val11==22)
    return 0;
  else {
    if (verbose) {
      printf("> integerSpillBothVirtual in native: \n>   expect 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22\n");
      printf(">   get %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d\n",
             val1, val2, val3,  val4, val5,
             val6, val7, val8,  val9, val10,  val11);
    }
    return -1;

  }

}


/*
 * Class:     ArgumentPassing
 * Method:    floatFitVirtual
 * Signature: (FFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatFitVirtual
  (JNIEnv *env, jobject obj,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6) {

  /* NOTE:  in C, FP constants are always type double
   * which is 64 bits;  they need to be converted to type float
   * which is 32 bits before doing the comparison, otherwise
   * the lower bits may be different
   */

  if (fval1==(float)0.10 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0 && fval6==(float)1.25) {
    return 0;
  } else {
    if (verbose) {
      printf("> floatFitVirtual in native: \n");
      printf(">   expect .10, .25, .50, .75, 1.0, 1.25 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatFitVirtualSave
 * Signature: (FFFFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatFitVirtualSave
  (JNIEnv *env, jobject obj,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6, jfloat fval7, jfloat fval8) {

  if (fval1==(float)0.1 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0 && fval6==(float)1.25 &&
      fval7==(float)1.50 && fval8==(float)1.75)
    return 0;
  else {
    if (verbose) {
      printf("> floatFitVirtualSave in native: \n");
      printf(">   expect .1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8);
    }
    return -1;
  }


}


/*
 * Class:     ArgumentPassing
 * Method:    floatSpillAIXVirtual
 * Signature: (FFFFFFFFFFFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatSpillAIXVirtual
  (JNIEnv *env, jobject obj,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6, jfloat fval7, jfloat fval8,
   jfloat fval9, jfloat fval10, jfloat fval11, jfloat fval12,
   jfloat fval13, jfloat fval14, jfloat fval15) {

  if (fval1==(float)0.1 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0  && fval6==(float)1.25 &&
      fval7==(float)1.50 && fval8==(float)1.75 && fval9==(float)2.0 &&
      fval10==(float)2.25 && fval11==(float)2.50 && fval12==(float)2.75  &&
      fval13==(float)3.0 && fval14==(float)3.25 && fval15==(float)3.50 )
    return 0;
  else {
    if (verbose) {
      printf("> floatSpillAIXVirtual in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15);
    }
    return -1;
  }

}



/*
 * Class:     ArgumentPassing
 * Method:    floatSpillBothVirtual
 * Signature: (FFFFFFFFFFFFFFFFF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatSpillBothVirtual
  (JNIEnv *env, jobject obj,
   jfloat fval1, jfloat fval2, jfloat fval3, jfloat fval4,
   jfloat fval5, jfloat fval6, jfloat fval7, jfloat fval8,
   jfloat fval9, jfloat fval10, jfloat fval11, jfloat fval12,
   jfloat fval13, jfloat fval14, jfloat fval15, jfloat fval16,
   jfloat fval17) {

  if (fval1==(float)0.10 && fval2==(float)0.25 && fval3==(float)0.50 &&
      fval4==(float)0.75 && fval5==(float)1.0 && fval6==(float)1.25 &&
      fval7==(float)1.50 && fval8==(float)1.75 && fval9==(float)2.0 &&
      fval10==(float)2.25 && fval11==(float)2.50 && fval12==(float)2.75  &&
      fval13==(float)3.0 && fval14==(float)3.25 && fval15==(float)3.50 &&
      fval16==(float)3.75 && fval17==(float)4.0)
    return 0;
  else {
    if (verbose) {
      printf("> floatSpillBothVirtual in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50, 3.75, 4.0 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15, fval16, fval17);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    doubleFitVirtual
 * Signature: (DDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleFitVirtual
  (JNIEnv *env, jobject obj, jdouble fval1, jdouble fval2, jdouble fval3) {

  if (fval1==0.1 && fval2==0.25 && fval3==0.50) {
    return 0;
  } else {
    if (verbose) {
      printf("> doubleFitVirtual in native: \n");
      printf(">   expect 0.1, .25, .50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f \n", fval1, fval2, fval3);
    }
    return -1;
  }

}


/*
 * Class:     ArgumentPassing
 * Method:    doubleFitVirtualStraddle
 * Signature: (IDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleFitVirtualStraddle
  (JNIEnv *env, jobject obj, jint val1, jdouble fval2, jdouble fval3, jdouble fval4){

  if (val1== 3 && fval2==0.1 && fval3==0.25 && fval4==0.50) {
    return 0;
  } else {
    if (verbose) {
      printf("> doubleFitVirtualStraddle in native: \n");
      printf(">   expect 0.1, .25, .50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f \n", fval2, fval3, fval4);
    }
    return -1;
  }
}


/*
 * Class:     ArgumentPassing
 * Method:    doubleFitVirtualSave
 * Signature: (DDDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleFitVirtualSave
  (JNIEnv *env, jobject obj, jdouble fval1, jdouble fval2, jdouble fval3,
   jdouble fval4, jdouble fval5) {
  if (fval1== 0.1 && fval2==0.25 && fval3==0.50 && fval4==0.75 && fval5==1.0) {
    return 0;
  } else {
    if (verbose) {
      printf("> doubleFitVirtualSave in native: \n");
      printf(">   expect 0.1, 0.25, 0.50 , 0.75, 1.00\n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5);
    }
    return -1;
  }
}


/*
 * Class:     ArgumentPassing
 * Method:    doubleSpillAIXVirtual
 * Signature: (DDDDDDDDDDDDDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleSpillAIXVirtual
  (JNIEnv *env, jobject obj,
   jdouble fval1, jdouble fval2, jdouble fval3,
   jdouble fval4, jdouble fval5, jdouble fval6,
   jdouble fval7, jdouble fval8, jdouble fval9,
   jdouble fval10, jdouble fval11, jdouble fval12,
   jdouble fval13, jdouble fval14, jdouble fval15) {

  if (fval1==0.1 && fval2==0.25 && fval3==0.50 &&
      fval4==0.75 && fval5==1.0  && fval6==1.25 &&
      fval7==1.50 && fval8==1.75 && fval9==2.0 &&
      fval10==2.25 && fval11==2.50 && fval12==2.75  &&
      fval13==3.0 && fval14==3.25 && fval15==3.50 )
    return 0;
  else {
    if (verbose) {
      printf("> doubleSpillAIXVirtual in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15);
    }
    return -1;
  }
}



/*
 * Class:     ArgumentPassing
 * Method:    doubleSpillBothVirtual
 * Signature: (DDDDDDDDDDDDDDDDD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleSpillBothVirtual
  (JNIEnv *env, jobject obj,
   jdouble fval1, jdouble fval2, jdouble fval3,
   jdouble fval4, jdouble fval5, jdouble fval6,
   jdouble fval7, jdouble fval8, jdouble fval9,
   jdouble fval10, jdouble fval11, jdouble fval12,
   jdouble fval13, jdouble fval14, jdouble fval15,
   jdouble fval16, jdouble fval17) {

  if (fval1==0.1 && fval2==0.25 && fval3==0.50 &&
      fval4==0.75 && fval5==1.0  && fval6==1.25 &&
      fval7==1.50 && fval8==1.75 && fval9==2.0 &&
      fval10==2.25 && fval11==2.50 && fval12==2.75  &&
      fval13==3.0 && fval14==3.25 && fval15==3.50 &&
      fval16==3.75 && fval17==4.00 )
    return 0;
  else {
    if (verbose) {
      printf("> doubleSpillBothVirtual in native: \n");
      printf(">   expect 0.1, .25, .50, .75, 1.0, 1.25, 1.50, 1.75, 2.0, 2.25, 2.50, 2.75, 3.0, 3.25, 3.50, 3.75, 4.00 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f \n",
             fval1, fval2, fval3, fval4, fval5, fval6, fval7, fval8,
             fval9, fval10, fval11, fval12, fval13, fval14, fval15, fval16, fval17);
    }
    return -1;
  }
}



/*
 * Class:     ArgumentPassing
 * Method:    longFitVirtual
 * Signature: (JJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longFitVirtual
  (JNIEnv *env, jobject obj, jlong val1, jlong val2, jlong val3) {

  if (val1==1l && val2==3l && val3==5l)
    return 0;
  else {
    if (verbose) {
      printf("> longFitVirtual in native: \n>   expect 1, 3, 5 \n");
      printf(">   get %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3);
    }
    return -1;
  }
}

/*
 * Class:     ArgumentPassing
 * Method:    longFitVirtualStraddle
 * Signature: (IJJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longFitVirtualStraddle
  (JNIEnv *env, jobject obj, jint val1, jlong val2, jlong val3, jlong val4) {

  if (val1==1 && val2==3l && val3==5l && val4==7l)
    return 0;
  else {
    if (verbose) {
      printf("> longFitVirtualStraddle in native: \n>   expect 1, 3, 5, 7 \n");
      printf(">   get %d, %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3, (int) val4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    longSpillAIXVirtual
 * Signature: (JJJJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longSpillAIXVirtual
  (JNIEnv *env, jobject obj, jlong val1, jlong val2, jlong val3, jlong val4, jlong val5) {

  if (val1==1l && val2==3l && val3==5l && val4==7l && val5==9l)
    return 0;
  else {
    if (verbose) {
      printf("> longSpillAIXVirtual in native: \n>   expect 1, 3, 5, 7, 9 \n");
      printf(">   get %d, %d, %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3,  (int) val4, (int) val5);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    longSpillBothVirtual
 * Signature: (JJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_longSpillBothVirtual
  (JNIEnv *env, jobject obj, jlong val1, jlong val2, jlong val3, jlong val4, jlong val5,
   jlong val6, jlong val7) {

  if (val1==1l && val2==3l && val3==5l && val4==7l && val5==9l &&
      val6==11l && val7==13l )
    return 0;
  else {
    if (verbose) {
      printf("> longSpillBothVirtual in native: \n>   expect 1, 3, 5, 7, 9, 11, 13 \n");
      printf(">   get %d, %d, %d, %d, %d, %d, %d\n",
             (int) val1, (int) val2, (int) val3, (int) val4, (int) val5,
             (int) val6, (int) val7);
    }
    return -1;
  }
}



/*
 * Class:     ArgumentPassing
 * Method:    integerFloatFitVirtual
 * Signature: (IFIFIF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerFloatFitVirtual
  (JNIEnv *env, jobject obj,
   jint val1, jfloat fval2, jint val3,
   jfloat fval4, jint val5, jfloat fval6) {


  if (val1==1 && fval2==(float)3.3 && val3==5 &&
      fval4==(float)7.7 &&  val5==9 && fval6==(float)11.11)
    return 0;
  else {
    if (verbose) {
      printf("> integerFloatFitVirtual in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7, 9, 11.11 \n");
      printf(">   get %d, %3.2f, %d, %3.2f, %d, %3.2f\n",
             val1, fval2, val3, fval4, val5, fval6);
    }
    return -1;
  }
}

/*
 * Class:     ArgumentPassing
 * Method:    integerFloatSpillVirtual
 * Signature: (FIFIFIFI)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerFloatSpillVirtual
  (JNIEnv *env, jobject obj,
   jfloat fval1, jint val2, jfloat fval3, jint val4,
   jfloat fval5, jint val6, jfloat fval7, jint val8) {

  if (fval1==(float)1.1 && val2==3  && fval3==(float)5.5 && val4==7 &&
      fval5==(float)9.9 && val6==11 && fval7==(float)13.13 && val8==15)
    return 0;
  else {
    if (verbose) {
      printf("> integerFloatSpillVirtual in native: \n");
      printf(">   expect 1.1f, 3, 5.5f, 7, 9.9f, 11, 13.13f, 15\n");
      printf(">   get %3.2f, %d, %3.2f, %d, %3.2f, %d\n",
             fval1, val2, fval3, val4, fval5, val6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerDoubleFitVirtual
 * Signature: (IDID)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerDoubleFitVirtual
  (JNIEnv *env, jobject obj,
   jint val1, jdouble fval2, jint val3, jdouble fval4) {

  if (val1==1 && fval2==3.3 && val3==5 && fval4==7.7)
    return 0;
  else {
    if (verbose) {
      printf("> integerDoubleFitVirtual in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7 \n");
      printf(">   get %d, %3.2f, %d, %3.2f\n",
             val1, fval2, val3, fval4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerDoubleFitVirtualStraddle
 * Signature: (DIIID)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerDoubleFitVirtualStraddle
  (JNIEnv *env, jobject obj,
   jdouble fval1, jint val2, jint val3, jint val4, jdouble fval5) {

  if (fval1==1.1 && val2==3 && val3==5 && val4==7 && fval5==9.9 )
    return 0;
  else {
    if (verbose) {
      printf("> integerDoubleFitVirtualStraddle in native: \n");
      printf(">   expect 1.1, 3, 5, 7, 9.9 \n");
      printf(">   get %3.2f, %d, %d, %d, %3.2f\n",
             fval1, val2, val3,  val4, fval5);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerDoubleSpillVirtual
 * Signature: (DIDIDI)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerDoubleSpillVirtual
  (JNIEnv *env, jobject obj,
   jdouble fval1, jint val2, jdouble fval3, jint val4, jdouble fval5, jint val6) {

  if (fval1==1.1 && val2==3 && fval3==5.5 && val4==7 && fval5==9.9 && val6==11)
    return 0;
  else {
    if (verbose) {
      printf("> integerDoubleSpillVirtual in native: \n");
      printf(">   expect 1.1, 3, 5.5, 7, 9.9, 11 \n");
      printf(">   get %3.2f, %d, %3.2f, %d, %3.2f, %d\n",
             fval1, val2, fval3,  val4, fval5, val6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerLongFitVirtual
 * Signature: (IJIJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerLongFitVirtual
  (JNIEnv *env, jobject obj,
   jint val1, jlong val2, jint val3, jlong val4) {

  if (val1==1 && val2==3 && val3==5 && val4==7)
    return 0;
  else {
    if (verbose) {
      printf(">  in native: \n");
      printf(">   expect 1, 3, 5, 7 \n");
      printf(">   get %d, %d, %d, %d\n",
             val1, (int) val2, val3, (int) val4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerLongFitVirtualStraddle
 * Signature: (IJIIJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerLongFitVirtualStraddle
  (JNIEnv *env, jobject obj,
   jint val1, jlong val2, jint val3, jint val4, jlong val5) {

  if (val1==1 && val2==3 && val3==5 && val4==7 && val5==9)
    return 0;
  else {
    if (verbose) {
      printf("> integerLongFitVirtualStraddle in native: \n");
      printf(">   expect 1, 3, 5, 7, 9 \n");
      printf(">   get %d, %d, %d, %d, %d\n",
             val1, (int) val2, val3,  val4, (int) val5);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    integerLongSpillVirtual
 * Signature: (JIJIJI)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_integerLongSpillVirtual
  (JNIEnv *env, jobject obj,
   jlong val1, jint val2, jlong val3, jint val4, jlong val5, jint val6) {

  if (val1==1 && val2==3 && val3==5 && val4==7 && val5==9 && val6==11)
    return 0;
  else {
    if (verbose) {
      printf("> integerLongSpillVirtual in native: \n");
      printf(">   expect 1, 3, 5, 7, 9, 11 \n");
      printf(">   get %d, %d, %d, %d, %d, %d\n",
             (int) val1, val2, (int) val3,  val4, (int) val5, val6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatDoubleFitVirtual
 * Signature: (FDFD)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatDoubleFitVirtual
  (JNIEnv *env, jobject obj,
   jfloat fval1, jdouble fval2, jfloat fval3, jdouble fval4) {

  if (fval1==(float)1.1 && fval2==3.3 && fval3==(float)5.5 && fval4==7.7 )
    return 0;
  else {
    if (verbose) {
      printf("> floatDoubleFitVirtual in native: \n");
      printf(">   expect 1.1, 3.3, 5.5, 7.7 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f\n",
             fval1, fval2, fval3, fval4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatDoubleSpillVirtual
 * Signature: (DFDFDF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatDoubleSpillVirtual
  (JNIEnv *env, jobject obj,
   jdouble fval1, jfloat fval2, jdouble fval3,
   jfloat fval4, jdouble fval5, jfloat fval6) {

  if (fval1==1.1 && fval2==(float)3.3 && fval3==5.5 &&
      fval4==(float)7.7 && fval5==9.9 && fval6==(float)11.11)
    return 0;
  else {
    if (verbose) {
      printf("> floatDoubleSpillVirtual in native: \n");
      printf(">   expect 1.1, 3.3, 5.5, 7.7, 9.9, 11.11 \n");
      printf(">   get %3.2f, %3.2f, %3.2f, %3.2f, %3.2f, %3.2f\n",
             fval1, fval2, fval3, fval4, fval5, fval6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatLongFitVirtual
 * Signature: (FJFJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatLongFitVirtual
  (JNIEnv *env, jobject obj,
   jfloat fval1, jlong val2, jfloat fval3, jlong val4) {

  if (fval1==(float)1.1 && val2==3l && fval3==(float)5.5 && val4==7l )
    return 0;
  else {
    if (verbose) {
      printf("> floatLongFitVirtual in native: \n");
      printf(">   expect 1.1, 3, 5.5, 7 \n");
      printf(">   get %3.2f, %d, %3.2f, %d\n",
             fval1, (int) val2, fval3, (int) val4);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    floatLongSpillVirtual
 * Signature: (JFJFJF)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_floatLongSpillVirtual
  (JNIEnv *env, jobject obj,
   jlong val1, jfloat fval2, jlong val3,
   jfloat fval4, jlong val5, jfloat fval6) {

  if (val1==1l && fval2==(float)3.3 && val3==5l &&
      fval4==(float)7.7 && val5==9l && fval6==(float)11.11)
    return 0;
  else {
    if (verbose) {
      printf("> floatLongSpillVirtual in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7, 9, 11.11 \n");
      printf(">   get %d, %3.2f, %d, %3.2f, %d, %3.2f\n",
             (int) val1, fval2, (int) val3, fval4, (int) val5, fval6);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    doubleLongFitVirtual
 * Signature: (JDJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleLongFitVirtual
  (JNIEnv *env, jobject obj,
   jlong val1, jdouble fval2, jlong val3) {

  if (val1==1l && fval2==3.3 && val3==5l )
    return 0;
  else {
    if (verbose) {
      printf("> doubleLongFitVirtual in native: \n");
      printf(">   expect 1, 3.3, 5\n");
      printf(">   get %d, %3.2f, %d\n", (int) val1, fval2,(int)  val3);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    doubleLongSpillVirtual
 * Signature: (JDJDJ)I
 */
JNIEXPORT jint JNICALL Java_ArgumentPassing_doubleLongSpillVirtual
  (JNIEnv *env, jobject obj,
   jlong val1, jdouble fval2, jlong val3, jdouble fval4, jlong val5) {

  if (val1==1l && fval2==3.3 && val3==5l && fval4==7.7 && val5==9l )
    return 0;
  else {
    if (verbose) {
      printf("> doubleLongSpillVirtual in native: \n");
      printf(">   expect 1, 3.3, 5, 7.7, 9 \n");
      printf(">   get %d, %3.2f, %d, %3.2f, %d\n",
             (int) val1, fval2, (int) val3, fval4, (int) val5);
    }
    return -1;
  }

}

/*
 * Class:     ArgumentPassing
 * Method:    returnLong
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_ArgumentPassing_returnLong
(JNIEnv *env, jclass cls, jlong val1) {

  return val1<<16;
}

/*
 * Class:     ArgumentPassing
 * Method:    returnFloat
 * Signature: (F)F
 */
JNIEXPORT jfloat JNICALL Java_ArgumentPassing_returnFloat
  (JNIEnv *env, jclass cls, jfloat val1) {

  return val1;
}

/*
 * Class:     ArgumentPassing
 * Method:    returnDouble
 * Signature: (D)D
 */
JNIEXPORT jdouble JNICALL Java_ArgumentPassing_returnDouble
  (JNIEnv *env, jclass cls, jdouble val1){

  return val1;
}







