/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2003
 */

/**
 * Simple test to see if hijacking of select works.
 * 
 * @author Dave Grove
 */
class SelectTest {

  public static native void doit();

  public static void main(String args[]) {
    System.loadLibrary("SelectTest");
    doit();
  }
}
