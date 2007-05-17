/* -*-c++-*-
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones,. University of Kent, 2006
 */

/**
 * A testing stub for GCspy
 */


#include <stdio.h>
#include "gcspy_main_server.h"

void
gcspy_mainServerInit (gcspy_main_server_t *server, int port, unsigned maxLen,
		      const char *name, int verbose) {
    printf("GCspy server on port %d\n", port);
}

void
gcspy_mainServerMainLoop (gcspy_main_server_t *server) { }

void
gcspy_mainServerSafepoint (gcspy_main_server_t *server, unsigned event) {
    printf("GCspy safepoint for event %d\n", event);
}

