/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2003
 *
 * $Id$
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
