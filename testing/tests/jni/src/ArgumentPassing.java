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
class ArgumentPassing {

  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;


  public static native void
    setVerboseOff();

  // AIX frame can hold 6 integers in registers + 2 used for JNI

  /**
   * Static method: 6 ints fit in AIX registers
   */
  public static native int
    integerFitStatic(int val1, int val2, int val3, int val4, int val5, int val6);

  /**
   * Static method: 10 ints spill from AIX registers but fit in JVM register
   */
  public static native int
    integerSpillAIXStatic(int val1, int val2, int val3, int val4, int val5,
                       int val6, int val7, int val8, int val9, int val10);

  /**
   * Static method: 12 ints spill from both AIX registers and JVM register
   */
  public static native int
    integerSpillBothStatic(int val1, int val2, int val3, int val4, int val5,
                           int val6, int val7, int val8, int val9, int val10,
                           int val11, int val12);

  /**
   * Static method: 6 floats fit in both AIX registers and JVM register
   */
  public static native int
    floatFitStatic(float fval1, float fval2, float fval3, float fval4,
                   float fval5, float fval6);

  /**
   * Static method: 8 floats fit in both AIX registers and JVM register but have to be
   *                saved in spill area because only 6 GPRs can be reserved
   */
  public static native int
    floatFitStaticSave(float fval1, float fval2, float fval3, float fval4,
                       float fval5, float fval6, float fval7, float fval8);


  /**
   * Static method: 15 floats spill from AIX registers but fit in JVM registers
   */
  public static native int
    floatSpillAIXStatic(float fval1, float fval2, float fval3, float fval4,
                        float fval5, float fval6, float fval7, float fval8,
                        float fval9, float fval10, float fval11, float fval12,
                        float fval13, float fval14, float fval15);

  /**
   * Static method: 17 floats spill from both AIX registers and JVM registers
   */
  public static native int
    floatSpillBothStatic(float fval1, float fval2, float fval3, float fval4,
                         float fval5, float fval6, float fval7, float fval8,
                         float fval9, float fval10, float fval11, float fval12,
                         float fval13, float fval14, float fval15, float fval16,
                         float fval17);


  /**
   * Static method: 3 double fit in both AIX registers and JVM register
   */
  public static native int
    doubleFitStatic(double fval1, double fval2, double fval3);

  /**
   * Static method: 1 int and 3 double fit in both AIX registers and JVM register
   *                but force the last double to be saved to spill
   */
  public static native int
    doubleFitStaticStraddle(int val1, double fval2, double fval3, double fval4);

  /**
   * Static method: 5 doubles fit in both AIX registers and JVM register but have to be
   *                saved in spill area because only 6 GPRs can be reserved for the first
   *                3 doubles
   */
  public static native int
    doubleFitStaticSave(double fval1, double fval2, double fval3,
                        double fval4, double fval5);

  /**
   * Static method: 15 doubles spill from AIX registers but fit in JVM registers
   */
  public static native int
    doubleSpillAIXStatic(double fval1, double fval2, double fval3,
                         double fval4, double fval5, double fval6,
                         double fval7, double fval8, double fval9,
                         double fval10, double fval11, double fval12,
                         double fval13, double fval14, double fval15);

  /**
   * Static method: 17 doubles spill from both AIX registers and JVM registers
   */
  public static native int
    doubleSpillBothStatic(double fval1, double fval2, double fval3,
                         double fval4, double fval5, double fval6,
                         double fval7, double fval8, double fval9,
                         double fval10, double fval11, double fval12,
                         double fval13, double fval14, double fval15,
                         double fval16, double fval17);

  /*
   * Scenarios for long:
   *                          split     spill   split   spill
   *  JVM       AIX     fit     AIX     AIX     JVM     JVM
   *  r3        r3
   *  r4        r4
   *  r5        r5
   *  r6        r6
   *  r7        r7
   *  r8        r8
   *  r9        r9      L-hi
   *  r10       r10     L-lo    L-hi
   *  r11 . . . . . . . . . .   L-lo    L-hi
   *  r12 . . . . . . . . . .   . . . . L-lo    L-hi
   *  spill . . . . . . . . . . . . . . . . .   L-lo    L-hi
   *  spill . . . . . . . . . . . . . . . . .   . . . . L-lo
   */

  /**
   * Static method: 3 long fit in both AIX registers and JVM register
   */
  public static native int
    longFitStatic(long fval1, long fval2, long fval3);

  /**
   * Static method: 1 int and 3 long fit JVM registers but force the
   *                last long to be straddled in AIX, the lower word saved to spill
   */
  public static native int
    longFitStaticStraddle(int val1, long fval2, long fval3, long fval4);

  /**
   * Static method: 5 longs fit in JVM registers but spill in AIX registers
   */
  public static native int
    longSpillAIXStatic(long val1, long val2, long val3, long val4, long val5);

  /**
   * Static method: 7 longs spill from both AIX registers and JVM registers
   */
  public static native int
    longSpillBothStatic(long val1,  long val2,  long val3,
                        long val4,  long val5,  long val6,
                        long val7);

  /**
   * Static method: 3 ints + 3 floats fit both JVM and AIX registers
   */
  public static native int
    integerFloatFitStatic(int val1, float fval2, int val3,
                          float fval4, int val5, float fval6);

  /**
   * Static method: 4 ints + 4 floats fit both JVM and AIX registers
   *                but the 4th int and float are saved to AIX spill because
   *                no more GPR is available
   */
  public static native int
    integerFloatSpillStatic(float fval1, int val2, float fval3,
                            int val4, float fval5, int val6, float fval7, int val8);

  /**
   * Static method: 2 ints + 2 double fit both JVM and AIX registers
   */
  public static native int
    integerDoubleFitStatic(int val1, double fval2, int val3,
                           double fval4);

  /**
   * Static method: 3 ints + 2 double fit both JVM and AIX registers
   *                but the 3rd double is straddled between AIX register/spill
   */
  public static native int
    integerDoubleFitStaticStraddle(double fval1, int val2,
                                   int val3, int val4, double fval5);

  /**
   * Static method: 3 ints + 3 double fit both JVM and AIX registers
   *                but the 4th int and double are saved to AIX spill
   */
  public static native int
    integerDoubleSpillStatic(double fval1, int val2,
                             double fval3, int val4, double fval5, int val6);

  /**
   * Static method: 2 ints + 2 long fit in both JVM and AIX registers
   */
  public static native int
    integerLongFitStatic(int val1, long val2, int val3,
                         long val4);

  /**
   * Static method: 3 ints + 2 long fit in both JVM and AIX registers
   *                but the 2nd long straddle the AIX register/spill
   */
  public static native int
    integerLongFitStaticStraddle(int val1, long val2, int val3,
                                 int val4, long val5);

  /**
   * Static method: 3 ints + 3 long fit in both JVM and AIX registers
   *                but the 4th int and long are saved to AIX spill
   *                because no GPR is available.
   */
  public static native int
    integerLongSpillStatic(long val1, int val2, long val3,
                           int val4, long val5, int val6);

  /**
   * Static method: 2 floats + 2 doubles fit in both JVM and AIX registers
   */
  public static native int
    floatDoubleFitStatic(float fval1, double fval2,
                         float fval3, double fval4);

  /**
   * Static method: 3 floats + 3 doubles fit in both JVM and AIX registers
   *                but the 4th float and double are saved to AIX spill
   *                because no GPR is available to reserve
   */
  public static native int
    floatDoubleSpillStatic(double fval1, float fval2,
                           double fval3, float fval4, double fval5, float fval6);

  /**
   * Static method: 2 floats + 2 long fit in both JVM and AIX registers
   */
  public static native int
    floatLongFitStatic(float fval1, long val2, float fval3, long val4);

  /**
   * Static method: 2 floats + 2 long fit in both JVM and AIX registers
   *                but the 4th float and long are saved to AIX spill
   *                because no GPR is available to reserve
   */
  public static native int
    floatLongSpillStatic(long val1, float fval2, long val3,
                         float fval4, long val5, float fval6);

  /**
   * Static method: 2 long + 1 double fit in both JVM and AIX registers
   */
  public static native int
    doubleLongFitStatic(long val1, double fval2, long val3);

  /**
   * Static method: 3 long + 2 double fit in JVM registers
   *                but spill in AIX
   */
  public static native int
    doubleLongSpillStatic(long val1, double fval2,
                          long val3, double fval4, long val5);


  /**
   * Virtual method: 6 ints fit in AIX registers
   */
  public native int
    integerFitVirtual(int val1, int val2, int val3, int val4, int val5, int val6);

  /**
   * Virtual method:  9 ints + this spill from AIX registers but fit in JVM register
   */
  public native int
    integerSpillAIXVirtual(int val1, int val2, int val3, int val4, int val5,
                           int val6, int val7, int val8, int val9);

  /**
   * Virtual method:  9 ints + this spill from both AIX registers and JVM register
   */
  public native int
    integerSpillBothVirtual(int val1, int val2, int val3, int val4, int val5,
                            int val6, int val7, int val8, int val9, int val10,
                            int val11);

  /**
   * Virtual method: 6 floats fit in both AIX registers and JVM register
   */
  public native int
    floatFitVirtual(float fval1, float fval2, float fval3, float fval4,
                   float fval5, float fval6);

  /**
   * Virtual method: 8 floats fit in both AIX registers and JVM register but have to be
   *                saved in spill area because only 6 GPRs can be reserved
   */
  public native int
    floatFitVirtualSave(float fval1, float fval2, float fval3, float fval4,
                       float fval5, float fval6, float fval7, float fval8);


  /**
   * Virtual method: 15 floats spill from AIX registers but fit in JVM registers
   */
  public native int
    floatSpillAIXVirtual(float fval1, float fval2, float fval3, float fval4,
                        float fval5, float fval6, float fval7, float fval8,
                        float fval9, float fval10, float fval11, float fval12,
                        float fval13, float fval14, float fval15);

  /**
   * Virtual method: 17 floats spill from both AIX registers and JVM registers
   */
  public native int
    floatSpillBothVirtual(float fval1, float fval2, float fval3, float fval4,
                         float fval5, float fval6, float fval7, float fval8,
                         float fval9, float fval10, float fval11, float fval12,
                         float fval13, float fval14, float fval15, float fval16,
                         float fval17);


  /**
   * Virtual method: 3 double fit in both AIX registers and JVM register
   */
  public native int
    doubleFitVirtual(double fval1, double fval2, double fval3);

  /**
   * Virtual method: 1 int and 3 double fit in both AIX registers and JVM register
   *                but force the last double to be saved to spill
   */
  public native int
    doubleFitVirtualStraddle(int val1, double fval2, double fval3, double fval4);

  /**
   * Virtual method: 5 doubles fit in both AIX registers and JVM register but have to be
   *                saved in spill area because only 6 GPRs can be reserved for the first
   *                3 doubles
   */
  public native int
    doubleFitVirtualSave(double fval1, double fval2, double fval3,
                         double fval4, double fval5);

  /**
   * Virtual method: 15 doubles spill from AIX registers but fit in JVM registers
   */
  public native int
    doubleSpillAIXVirtual(double fval1, double fval2, double fval3,
                          double fval4, double fval5, double fval6,
                          double fval7, double fval8, double fval9,
                          double fval10, double fval11, double fval12,
                          double fval13, double fval14, double fval15);

  /**
   * Virtual method: 17 doubles spill from both AIX registers and JVM registers
   */
  public native int
    doubleSpillBothVirtual(double fval1, double fval2, double fval3,
                           double fval4, double fval5, double fval6,
                           double fval7, double fval8, double fval9,
                           double fval10, double fval11, double fval12,
                           double fval13, double fval14, double fval15,
                           double fval16, double fval17);

  /**
   * Virtual method: 3 long fit in both AIX registers and JVM register
   */
  public native int
    longFitVirtual(long fval1, long fval2, long fval3);

  /**
   * Virtual method: 1 int and 3 long fit JVM registers but force the
   *                last long to be straddled in AIX, the lower word saved to spill
   */
  public native int
    longFitVirtualStraddle(int val1, long fval2, long fval3, long fval4);

  /**
   * Virtual method: 5 longs fit in JVM registers but spill in AIX registers
   */
  public native int
    longSpillAIXVirtual(long val1, long val2, long val3, long val4, long val5);

  /**
   * Virtual method: 7 longs spill from both AIX registers and JVM registers
   */
  public native int
    longSpillBothVirtual(long val1,  long val2,  long val3,
                        long val4,  long val5,  long val6,
                        long val7);
  /**
   * Virtual method: 3 ints + 3 floats fit both JVM and AIX registers
   */
  public native int
    integerFloatFitVirtual(int val1, float fval2, int val3,
                           float fval4, int val5, float fval6);

  /**
   * Virtual method: 4 ints + 4 floats fit both JVM and AIX registers
   *                but the 4th int and float are saved to AIX spill because
   *                no more GPR is available
   */
  public native int
    integerFloatSpillVirtual(float fval1, int val2, float fval3,
                             int val4, float fval5, int val6, float fval7, int val8);

  /**
   * Virtual method: 2 ints + 2 double fit both JVM and AIX registers
   */
  public native int
    integerDoubleFitVirtual(int val1, double fval2, int val3, double fval4);

  /**
   * Virtual method: 3 ints + 2 double fit both JVM and AIX registers
   *                but the 3rd double is straddled between AIX register/spill
   */
  public native int
    integerDoubleFitVirtualStraddle(double fval1, int val2,
                                    int val3, int val4, double fval5);

  /**
   * Virtual method: 3 ints + 3 double fit both JVM and AIX registers
   *                but the 4th int and double are saved to AIX spill
   */
  public native int
    integerDoubleSpillVirtual(double fval1, int val2, double fval3,
                              int val4, double fval5, int val6);

  /**
   * Virtual method: 2 ints + 2 long fit in both JVM and AIX registers
   */
  public native int
    integerLongFitVirtual(int val1, long val2, int val3, long val4);

  /**
   * Virtual method: 3 ints + 2 long fit in both JVM and AIX registers
   *                but the 2nd long straddle the AIX register/spill
   */
  public native int
    integerLongFitVirtualStraddle(int val1, long val2, int val3,
                                  int val4, long val5);

  /**
   * Virtual method: 3 ints + 3 long fit in both JVM and AIX registers
   *                but the 4th int and long are saved to AIX spill
   *                because no GPR is available.
   */
  public native int
    integerLongSpillVirtual(long val1, int val2, long val3,
                            int val4, long val5, int val6);

  /**
   * Virtual method: 2 floats + 2 doubles fit in both JVM and AIX registers
   */
  public native int
    floatDoubleFitVirtual(float fval1, double fval2, float fval3, double fval4);

  /**
   * Virtual method: 3 floats + 3 doubles fit in both JVM and AIX registers
   *                but the 4th float and double are saved to AIX spill
   *                because no GPR is available to reserve
   */
  public native int
    floatDoubleSpillVirtual(double fval1, float fval2, double fval3,
                            float fval4, double fval5, float fval6);

  /**
   * Virtual method: 2 floats + 2 long fit in both JVM and AIX registers
   */
  public native int
    floatLongFitVirtual(float fval1, long val2, float fval3, long val4);

  /**
   * Virtual method: 2 floats + 2 long fit in both JVM and AIX registers
   *                but the 4th float and long are saved to AIX spill
   *                because no GPR is available to reserve
   */
  public native int
    floatLongSpillVirtual(long val1, float fval2, long val3,
                          float fval4, long val5, float fval6);

  /**
   * Virtual method: 2 long + 1 double fit in both JVM and AIX registers
   */
  public native int
    doubleLongFitVirtual(long val1, double fval2, long val3);

  /**
   * Virtual method: 3 long + 2 double fit in JVM registers
   *                but spill in AIX
   */
  public native int
    doubleLongSpillVirtual(long val1, double fval2,
                           long val3, double fval4, long val5);


  /**
   * Return value: check for 2 words returned
 */
  public static native long returnLong(long val1);
  public static native float returnFloat(float val1);
  public static native double returnDouble(double val1);


  //******************************************************************
  // Implementation:
  //

  // dummy constructor for test on virtual methods
  public ArgumentPassing() {

  }

  public static void main(String[] args) {
    int returnValue;

    System.loadLibrary("ArgumentPassing");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }


    //
    // Static methods

    returnValue = integerFitStatic(1, 3, 5, 7, 9, 11);
    checkTest(returnValue, "integerFitStatic");

    returnValue = integerSpillAIXStatic(1, 3, 5, 7, 9, 11, 13, 15, 17, 19);
    checkTest(returnValue, "integerSpillAIXStatic");

    returnValue = integerSpillBothStatic(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23);
    checkTest(returnValue, "integerSpillBothStatic");



    returnValue = floatFitStatic(0.1f, .25f, .50f, .75f, 1.0f, 1.25f);
    checkTest(returnValue, "floatFitStatic");

    returnValue = floatFitStaticSave(0.1f, 0.25f, 0.50f, 0.75f, 1.0f, 1.25f, 1.50f, 1.75f);
    checkTest(returnValue, "floatFitStaticSave");

    returnValue = floatSpillAIXStatic(0.1f, 0.25f, 0.50f, 0.75f, 1.0f, 1.25f, 1.50f, 1.75f,
                                      2.0f, 2.25f, 2.50f, 2.75f, 3.0f, 3.25f, 3.50f);
    checkTest(returnValue, "floatSpillAIXStatic");

    returnValue = floatSpillBothStatic(0.1f, 0.25f, 0.50f, 0.75f, 1.0f, 1.25f, 1.50f, 1.75f,
                                       2.0f, 2.25f, 2.50f, 2.75f, 3.0f, 3.25f, 3.50f, 3.75f,
                                       4.0f);
    checkTest(returnValue, "floatSpillBothStatic");



    returnValue = doubleFitStatic(0.1d, 0.25d, 0.50d);
    checkTest(returnValue, "doubleFitStatic");

    returnValue = doubleFitStaticStraddle(3, 0.1d, 0.25d, 0.50d);
    checkTest(returnValue, "doubleFitStaticStraddle");

    returnValue = doubleFitStaticSave(0.1d, 0.25d, 0.50d, .75d, 1.0d);
    checkTest(returnValue, "doubleFitStaticSave");

    returnValue = doubleSpillAIXStatic(0.1d, 0.25d, 0.50d, .75d, 1.0d, 1.25d, 1.50d, 1.75d,
                                       2.0d, 2.25d, 2.50d, 2.75d, 3.0d, 3.25d, 3.50d);
    checkTest(returnValue, "doubleSpillAIXStatic");

    returnValue = doubleSpillBothStatic(0.1d, 0.25d, 0.50d, .75d, 1.0d, 1.25d, 1.50d, 1.75d,
                                        2.0d, 2.25d, 2.50d, 2.75d, 3.0d, 3.25d, 3.50d, 3.75f,
                                        4.0f);
    checkTest(returnValue, "doubleSpillBothStatic");



    returnValue = longFitStatic(1L, 3L, 5L);
    checkTest(returnValue, "longFitStatic");

    returnValue = longFitStaticStraddle(1, 3L, 5L, 7L);
    checkTest(returnValue, "longFitStaticStraddle");

    returnValue = longSpillAIXStatic(1L, 3L, 5L, 7L, 9L);
    checkTest(returnValue, "longSpillAIXStatic");

    returnValue = longSpillBothStatic(1L, 3L, 5L, 7L, 9L, 11L, 13L);
    checkTest(returnValue, "longSpillBothStatic");



    returnValue = integerFloatFitStatic(1, 3.3f, 5, 7.7f, 9, 11.11f);
    checkTest(returnValue, "integerFloatFitStatic");

    returnValue = integerFloatSpillStatic(1.1f, 3, 5.5f, 7, 9.9f, 11, 13.13f, 15);
    checkTest(returnValue, "integerFloatSpillStatic");

    returnValue = integerDoubleFitStatic(1, 3.3d, 5, 7.7d);
    checkTest(returnValue, "integerDoubleFitStatic");

    returnValue = integerDoubleFitStaticStraddle(1.1d, 3, 5, 7, 9.9d);
    checkTest(returnValue, "integerDoubleFitStaticStraddle");

    returnValue = integerDoubleSpillStatic(1.1d, 3, 5.5d, 7, 9.9d, 11);
    checkTest(returnValue, "integerDoubleSpillStatic");

    returnValue = integerLongFitStatic(1, 3L, 5, 7L);
    checkTest(returnValue, "integerLongFitStatic");

    returnValue = integerLongFitStaticStraddle(1, 3L, 5, 7, 9L);
    checkTest(returnValue, "integerLongFitStaticStraddle");

    returnValue = integerLongSpillStatic(1L, 3, 5L, 7, 9L, 11);
    checkTest(returnValue, "integerLongSpillStatic");

    returnValue = floatDoubleFitStatic(1.1f, 3.3d, 5.5f, 7.7d);
    checkTest(returnValue, "floatDoubleFitStatic");

    returnValue = floatDoubleSpillStatic(1.1d, 3.3f, 5.5d, 7.7f, 9.9d, 11.11f);
    checkTest(returnValue, "floatDoubleSpillStatic");

    returnValue = floatLongFitStatic(1.1f, 3L, 5.5f, 7L);
    checkTest(returnValue, "floatLongFitStatic");

    returnValue = floatLongSpillStatic(1L, 3.3f, 5L, 7.7f, 9L, 11.11f);
    checkTest(returnValue, "floatLongSpillStatic");

    returnValue = doubleLongFitStatic(1L, 3.3d, 5L);
    checkTest(returnValue, "doubleLongFitStatic");

    returnValue = doubleLongSpillStatic(1L, 3.3d, 5L, 7.7d, 9L);
    checkTest(returnValue, "doubleLongSpillStatic");



    //*******************************************************
    // Virtual methods
    //
    ArgumentPassing testobj = new ArgumentPassing();

    returnValue = testobj.integerFitVirtual(2, 4, 6, 8, 10, 12);
    checkTest(returnValue, "integerFitVirtual");

    returnValue = testobj.integerSpillAIXVirtual(2, 4, 6, 8, 10, 12, 14, 16, 18);
    checkTest(returnValue, "integerSpillAIXVirtual");

    returnValue = testobj.integerSpillBothVirtual(2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22);
    checkTest(returnValue, "integerSpillBothVirtual");



    returnValue = testobj.floatFitVirtual(0.1f, .25f, .50f, .75f, 1.0f, 1.25f);
    checkTest(returnValue, "floatFitVirtual");

    returnValue = testobj.floatFitVirtualSave(0.1f, 0.25f, 0.50f, 0.75f, 1.0f, 1.25f, 1.50f, 1.75f);
    checkTest(returnValue, "floatFitVirtualSave");

    returnValue = testobj.floatSpillAIXVirtual(0.1f, 0.25f, 0.50f, 0.75f, 1.0f, 1.25f, 1.50f, 1.75f,
                                      2.0f, 2.25f, 2.50f, 2.75f, 3.0f, 3.25f, 3.50f);
    checkTest(returnValue, "floatSpillAIXVirtual");

    returnValue = testobj.floatSpillBothVirtual(0.1f, 0.25f, 0.50f, 0.75f, 1.0f, 1.25f, 1.50f, 1.75f,
                                       2.0f, 2.25f, 2.50f, 2.75f, 3.0f, 3.25f, 3.50f, 3.75f,
                                       4.0f);
    checkTest(returnValue, "floatSpillBothVirtual");



    returnValue = testobj.doubleFitVirtual(0.1d, 0.25d, 0.50d);
    checkTest(returnValue, "doubleFitVirtual");

    returnValue = testobj.doubleFitVirtualStraddle(3, 0.1d, 0.25d, 0.50d);
    checkTest(returnValue, "doubleFitVirtualStraddle");

    returnValue = testobj.doubleFitVirtualSave(0.1d, 0.25d, 0.50d, .75d, 1.0d);
    checkTest(returnValue, "doubleFitVirtualSave");

    returnValue = testobj.doubleSpillAIXVirtual(0.1d, 0.25d, 0.50d, .75d, 1.0d, 1.25d, 1.50d, 1.75d,
                                       2.0d, 2.25d, 2.50d, 2.75d, 3.0d, 3.25d, 3.50d);
    checkTest(returnValue, "doubleSpillAIXVirtual");

    returnValue = testobj.doubleSpillBothVirtual(0.1d, 0.25d, 0.50d, .75d, 1.0d, 1.25d, 1.50d, 1.75d,
                                        2.0d, 2.25d, 2.50d, 2.75d, 3.0d, 3.25d, 3.50d, 3.75f,
                                        4.0f);
    checkTest(returnValue, "doubleSpillBothVirtual");


    returnValue = testobj.longFitVirtual(1L, 3L, 5L);
    checkTest(returnValue, "longFitVirtual");

    returnValue = testobj.longFitVirtualStraddle(1, 3L, 5L, 7L);
    checkTest(returnValue, "longFitVirtualStraddle");

    returnValue = testobj.longSpillAIXVirtual(1L, 3L, 5L, 7L, 9L);
    checkTest(returnValue, "longSpillAIXVirtual");

    returnValue = testobj.longSpillBothVirtual(1L, 3L, 5L, 7L, 9L, 11L, 13L);
    checkTest(returnValue, "longSpillBothVirtual");



    returnValue = testobj.integerFloatFitVirtual(1, 3.3f, 5, 7.7f, 9, 11.11f);
    checkTest(returnValue, "integerFloatFitVirtual");

    returnValue = testobj.integerFloatSpillVirtual(1.1f, 3, 5.5f, 7, 9.9f, 11, 13.13f, 15);
    checkTest(returnValue, "integerFloatSpillVirtual");

    returnValue = testobj.integerDoubleFitVirtual(1, 3.3d, 5, 7.7d);
    checkTest(returnValue, "integerDoubleFitVirtual");

    returnValue = testobj.integerDoubleFitVirtualStraddle(1.1d, 3, 5, 7, 9.9d);
    checkTest(returnValue, "integerDoubleFitVirtualStraddle");

    returnValue = testobj.integerDoubleSpillVirtual(1.1d, 3, 5.5d, 7, 9.9d, 11);
    checkTest(returnValue, "integerDoubleSpillVirtual");

    returnValue = testobj.integerLongFitVirtual(1, 3L, 5, 7L);
    checkTest(returnValue, "integerLongFitVirtual");

    returnValue = testobj.integerLongFitVirtualStraddle(1, 3L, 5, 7, 9L);
    checkTest(returnValue, "integerLongFitVirtualStraddle");

    returnValue = testobj.integerLongSpillVirtual(1L, 3, 5L, 7, 9L, 11);
    checkTest(returnValue, "integerLongSpillVirtual");

    returnValue = testobj.floatDoubleFitVirtual(1.1f, 3.3d, 5.5f, 7.7d);
    checkTest(returnValue, "floatDoubleFitVirtual");

    returnValue = testobj.floatDoubleSpillVirtual(1.1d, 3.3f, 5.5d, 7.7f, 9.9d, 11.11f);
    checkTest(returnValue, "floatDoubleSpillVirtual");

    returnValue = testobj.floatLongFitVirtual(1.1f, 3L, 5.5f, 7L);
    checkTest(returnValue, "floatLongFitVirtual");

    returnValue = testobj.floatLongSpillVirtual(1L, 3.3f, 5L, 7.7f, 9L, 11.11f);
    checkTest(returnValue, "floatLongSpillVirtual");

    returnValue = testobj.doubleLongFitVirtual(1L, 3.3d, 5L);
    checkTest(returnValue, "doubleLongFitVirtual");

    returnValue = testobj.doubleLongSpillVirtual(1L, 3.3d, 5L, 7.7d, 9L);
    checkTest(returnValue, "doubleLongSpillVirtual");

    long actualLong = returnLong(0x12345678);
    int hi = (int) (actualLong >> 32);
    int lo = (int) (actualLong);
    returnValue = ((hi==0x00001234) && (lo==0x56780000)) ? 0 : 1;
    checkTest(returnValue, "returnLong");

    float actualFloat = returnFloat((float) (1.5));
    returnValue = (actualFloat==((float) 1.5)) ? 0 : 1;
    checkTest(returnValue, "returnFloat");

    double actualDouble = returnDouble(3.5);
    returnValue = (actualDouble==3.5) ? 0 : 1;
    checkTest(returnValue, "returnDouble");

    // Summarize

    if (allTestPass)
      System.out.println("PASS: ArgumentPassing");
    else
      System.out.println("FAIL: ArgumentPassing");


  }


  static void printVerbose(String str) {
    if (verbose)
      System.out.println(str);
  }

  static void checkTest(int returnValue, String testName) {
    if (returnValue==0) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }


}
