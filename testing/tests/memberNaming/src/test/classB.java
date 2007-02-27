/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */

package test;

/**
 * @author Igor Pechtchanski
 */
public class classB extends classA implements interfaceB {
  public static void load() {}
  public void fum() { System.out.println("fum()"); }
}

