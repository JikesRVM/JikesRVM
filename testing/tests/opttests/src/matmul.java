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
class matmul {

  // problem size
  static int m = 64;
  static int n = 64;
  static int p = 64;
  static int NSTEP = 100;

  static double[][] A = new double[m][n];
  static double[][] B = new double[n][p];
  static double[][] C = new double[m][p];

  // timer
  static double timer() {

    return System.currentTimeMillis()/1000.0;
  }

  // initialize
  static void initialize() {

    int i;
    int j;

    for (i=0;i<m;i++) {
      for (j=0;j<n;j++) {
        A[i][j] = 1.0;
      }
    }

    for (i=0;i<n;i++) {
      for (j=0;j<p;j++) {
        B[i][j] = 1.0;
      }
    }

    for (i=0;i<m;i++) {
      for (j=0;j<p;j++) {
        C[i][j] = 0.0;
      }
    }
  }

  // matrix multiply
  static void multipl() {

    int i;
    int j;
    int k;

    for (i=0;i<m;i++) {
      for (j=0;j<p;j++) {
        for (k=0;k<n;k++) {
          C[i][j] += A[i][k]*B[k][j];
        }
      }
    }
  }

  public static void main(String[] args) {

    System.out.println("matrix multiply:");
    System.out.print("  problem size = " + m + " x " + n + " x " + p);
    System.out.println(" with " + NSTEP + " repetitions");

    // initialize matrices A, B, C.
    initialize();

    // perform the multiplies.
    double etime = timer();
    for (int step=0; step<NSTEP; step++) {
      multipl();
    }
    etime = timer() - etime;

    // compute performance
    double flops = 2*m*n*p*NSTEP;
    double floprt = 0.0;
    if (etime > 0.0) floprt = 1.0e-06*flops/etime;
    System.out.println("  elapsed time    = " + etime);
    System.out.println("  MFLOPS          = " + floprt);

    // compute the error
    double err = 0;
    for (int i=0; i<m; i++) {
      for (int j=0; j<p; j++) {
        double delta = C[i][j] - (n*NSTEP);
        err += delta*delta;
      }
    }
    System.out.println("  error          = " + err);
  }
}




