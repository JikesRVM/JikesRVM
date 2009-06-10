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
class RunCaffeine {

  static int run;

  public static void main(String[] args) {
    BenchmarkUnit bu;

    run = 1;

    bu = new BenchmarkUnit(new SieveAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LoopAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LogicAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new StringAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new FloatAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new MethodAtom());
    runTest(bu);

    run = 2;

    bu = new BenchmarkUnit(new SieveAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LoopAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LogicAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new StringAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new FloatAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new MethodAtom());
    runTest(bu);

  }


  static void runTest(BenchmarkUnit benchmark) {
    try {
      System.out.println(run + " " + benchmark.testName()+" score:\t"+benchmark.testScore());
    } catch (Throwable x) {
      x.printStackTrace(System.err);
    }
  }
}
