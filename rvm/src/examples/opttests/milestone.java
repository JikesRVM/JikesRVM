/*
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

