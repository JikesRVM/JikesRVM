/*
 * (C) Copyright IBM Corp. 2001
 */

class RunCaffeine1 {

  public static void main(String[] args) {
    System.out.println("this should print \"String\" three times.");
    System.out.println("if you don't see this, the bug is still there");
    try {
    run();
    } catch (Exception e) {
      System.out.println("PANIC, bug is still alive");
    }
  } 

  static void run() {
    BenchmarkUnit bu;

    bu = new BenchmarkUnit(new StringAtom());
    runTest(bu);
    bu = new BenchmarkUnit(new StringAtom());
    runTest(bu);
    bu = new BenchmarkUnit(new StringAtom());
    runTest(bu);  
  }


  static void runTest(BenchmarkUnit benchmark) {
      System.out.println(benchmark.testName()+" score:\t"+benchmark.testScore());
  }
}
