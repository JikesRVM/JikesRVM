/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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

