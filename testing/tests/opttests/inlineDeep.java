/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Use the following command to compile the program with full debug output:
 *
 *     jsh java optCompilerDriver +depgraph +ir +low +burs +regalloc fibo 
 *
 * @author unascribed
 */

public final class inlineDeep {

  public static void main(String args[]) {
    run();
  }

  static boolean run() {
    int i = recurs6(6, 6);
    System.out.println("inlineDeep returned: " + i);
    return true;
  }

  static int recurs6(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs5(j, n)+i;
    return k;
  }

  static int recurs5(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs4(j, n)+i;
    return k;
  }

  static int recurs4(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs3(j, n)+i;
    return k;
  }
  
  static int recurs3(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs2(j, n)+i;
    return k;
  }
  
  static int recurs2(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs1(j, n)+i;
    return k;
  }

  static int recurs1(int i, int m) {
    int j = i+m-1;
    int n = m - i;
    int k = recurs0(j, n)+i;
    return k;
  }

  static int recurs0(int i, int m) {
    int k = 1;
    return k;
  }



}
