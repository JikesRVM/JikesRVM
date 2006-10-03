/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

public class stack {

  public static void main(String arg[]) {
    int disk = 3;
    if (arg.length > 0) disk = Integer.parseInt(arg[0]);
    overflow(disk);
  }

  public static void overflow(int n) {
    if (n > 0)
       overflow(n-1);
  }
}
