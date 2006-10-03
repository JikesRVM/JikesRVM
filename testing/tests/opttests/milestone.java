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
 * Use the following command to compile the program with full debug output:
 *
 *  jsh java optCompilerDriver +depgraph +ir +low +burs +regalloc milestone 
 *
 * @author unascribed
 */

final class milestone
{
  static int add(int a, int b)
  {
    return a + b;
  }
}

