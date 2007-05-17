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

