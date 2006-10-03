/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package test;

/**
 * @author Igor Pechtchanski
 */
abstract class classA {
   public final void foo() { System.out.println("foo()"); }
   public int bar = 5;
   public static void fuz() { System.out.println("fuz()"); }
   public static int baz = 10;
   public void fur() { System.out.println("fur()"); }
}

