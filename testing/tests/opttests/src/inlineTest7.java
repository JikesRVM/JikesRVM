/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */
public class inlineTest7 {

  public static void main(String[] args) {
    run();
  }
  
  public static void run() {
    inlineTest7 x = new inlineTest7();
    x.foo();
  }

  public void foo() {
    int i2 = 1;
    int k2 = 30;
    do {
      int k1 = k2 / 2;
      i2 = bar(i2+1, 25);
      k2 = k1;
    } while (k2 > 1);
  }

  public int bar(int i, int j) {
    if (i < j) 
      return i;
    else 
      return j;
  }
}
  
