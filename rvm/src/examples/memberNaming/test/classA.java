/*
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

