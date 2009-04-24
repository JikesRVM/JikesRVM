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

#ifndef _GCSPY_MAIN_SERVER_H_

#define _GCSPY_MAIN_SERVER_H_

#include "gcspy_gc_driver.h"
#include "gcspy_interpreter.h"

typedef struct {
  gcspy_command_stream_t interpreter;
} gcspy_main_server_t;


#define gcspy_mainServerAddDriver(server)  NULL
#define gcspy_mainServerAddEvent(server,event,name)
/*define gcspy_mainServerInit(server,port,len,name,verbose) NULL*/
#define gcspy_mainServerIsConnected(server,event) 1
/*#define gcspy_mainServerSafepoint(server,event)*/
#define gcspy_mainServerSetGeneralInfo(server,generalInfo)
#define gcspy_mainServerStartCompensationTimer(server)
#define gcspy_mainServerStopCompensationTimer(server)
#define gcspy_mainServerWaitForClient(server)

void
gcspy_mainServerInit (gcspy_main_server_t *server,
		      int port,
		      unsigned maxLen,
		      const char *name,
		      int verbose);

void
gcspy_mainServerMainLoop (gcspy_main_server_t *server);

void
gcspy_mainServerSafepoint (gcspy_main_server_t *server, unsigned event);

#endif // _GCSPY_MAIN_SERVER_H_

