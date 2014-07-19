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
package test.org.jikesrvm.opttests.bugs;

/**
 * Testcase for treatment of checkcasts in escape analysis.<p>
 *
 * The escape analysis assumed that checkcasts do not
 * cause any registers to escape. This is wrong.<p>
 *
 * Quote from the issue tracker:
 * "The analysis ignores that checkcast* instruction moves a reference from
 *  one pseudo register to another, similar to ref_move insn."
 */
public class RVM_957_Testcase {

  private static boolean escape_bug(RVM_957_A a, RVM_957_B b, RVM_957_C c) {
    RVM_957_Some some = new RVM_957_Some(new RVM_957_Tuple(a, b));
    RVM_957_Tuple tuple = (RVM_957_Tuple) some.get();
    return c.check((RVM_957_A)tuple._1(), (RVM_957_B)tuple._2());
  }

  public static void main(final String[] args) {
    RVM_957_Some some = new RVM_957_Some(new RVM_957_Tuple(new RVM_957_A(), new RVM_957_B()));
    System.out.println(some);
    escape_bug(new RVM_957_A(), new RVM_957_B(), new RVM_957_D());
    System.out.println("Bug is fixed!");
  }

}
