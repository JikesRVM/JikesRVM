/* -*-coding: iso-8859-1 -*-
 *
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * Copyright ? IBM Corp 2003
 *
 */

/** Test whether printf() supports the %z modifier.  Just prints out 
 *  the result.
 *
 *  @author Steven Augart
 *  @date 20 October 2003
 */


#include <stdio.h>              /* printf() */
#include <stdlib.h>             /* exit() */

int
main(void)
{
    printf("An int is %zu bytes long.\n", sizeof (int));
    exit(0);
}
