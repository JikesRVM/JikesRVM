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

#include "sys.h"

#include <string.h>
#include <stdlib.h>

#ifdef RVM_WITH_GCSPY
// GCspy

//NOTE It is the responsibility of the calling code to
//     check that server, driver etc are non-null.

EXTERNAL {
#include "gcspy_gc_stream.h"
#include "gcspy_main_server.h"
#include "gcspy_gc_driver.h"
#include "gcspy_color_db.h"
#include "gcspy_utils.h"
}

typedef void * (*pthread_start_routine_t)(void *);

static gcspy_main_server_t server;

// debugging
#define GCSPY_TRACE 0
#define GCSPY_TRACE_PRINTF(...) if(GCSPY_TRACE) CONSOLE_PRINTF(SysTraceFile,__VA_ARGS__)

static int stream_count = 0;
static int stream_len;

EXTERNAL gcspy_gc_stream_t * gcspyDriverAddStream (gcspy_gc_driver_t *driver, int id) {
  GCSPY_TRACE_PRINTF("gcspyDriverAddStream: driver=%x(%s), id=%d...",
                     driver, driver->name, id);
  gcspy_gc_stream_t *stream = gcspy_driverAddStream(driver, id);
  GCSPY_TRACE_PRINTF("stream=%x\n", stream);
  return stream;
}

EXTERNAL void gcspyDriverEndOutput (gcspy_gc_driver_t *driver) {
  int len;
  GCSPY_TRACE_PRINTF("gcspyDriverEndOutput: driver=%x(%s), len=%d, written=%d\n",
                     driver, driver->name, stream_len, stream_count);
  stream_count = 0;
  /*??*/
  gcspy_buffered_output_t *output =
    gcspy_command_stream_get_output(driver->interpreter);
  len = gcspy_bufferedOutputGetLen(output);
  GCSPY_TRACE_PRINTF("gcspyDriverEndOutput: interpreter has len=%d\n", len);
  gcspy_driverEndOutput(driver);
}

EXTERNAL void gcspyDriverInit (gcspy_gc_driver_t *driver, int id, char *serverName, char *driverName,
                               char *title, char *blockInfo, int tileNum,
                               char *unused, int mainSpace) {
  GCSPY_TRACE_PRINTF("gcspyDriverInit: driver=%x, id=%d, serverName=%s, driverName=%s, title=%s, blockInfo=%s, %d tiles, used=%s, mainSpace=%d\n",
                     driver, id, serverName, driverName,
                     title, blockInfo, tileNum,
                     unused, mainSpace);
  gcspy_driverInit(driver, id, serverName, driverName,
                   title, blockInfo, tileNum,
                   unused, mainSpace);
}

EXTERNAL void gcspyDriverInitOutput (gcspy_gc_driver_t *driver) {
  GCSPY_TRACE_PRINTF("gcspyDriverInitOutput: driver=%x(s)\n",
                     driver, driver->name);
  gcspy_driverInitOutput(driver);
}

EXTERNAL void gcspyDriverResize (gcspy_gc_driver_t *driver, int size) {
  GCSPY_TRACE_PRINTF("gcspyDriverResize: driver=%x(%s), size %d\n",
                     driver, driver->name, size);
  gcspy_driverResize(driver, size);
}

EXTERNAL void gcspyDriverSetTileName (gcspy_gc_driver_t *driver, int tile, char *format, long value) {
  char buffer[128];
  sprintf(buffer, format, value);
  GCSPY_TRACE_PRINTF("gcspyDriverSetTileName: driver=%x(%s), tile %d %s\n", driver, driver->name, tile, buffer);
  gcspy_driverSetTileName(driver, tile, buffer);
}

EXTERNAL void gcspyDriverSetTileNameRange (gcspy_gc_driver_t *driver, int tile, Address start, Address end) {
  char name[256];
  snprintf(name, sizeof name, "   [%p-%p)", start, end);
  gcspyDriverSetTileName(driver, tile, name, 0);
}

EXTERNAL void gcspyDriverSpaceInfo (gcspy_gc_driver_t *driver, char *spaceInfo) {
  GCSPY_TRACE_PRINTF("gcspyDriverSpaceInfo: driver=%x(%s), spaceInfo = +%s+(%x)\n", driver, driver->name, spaceInfo, spaceInfo);
  gcspy_driverSpaceInfo(driver, spaceInfo);
}

EXTERNAL void gcspyDriverStartComm (gcspy_gc_driver_t *driver) {
  GCSPY_TRACE_PRINTF("gcspyDriverStartComm: driver=%x(%s)\n", driver, driver->name);
  gcspy_driverStartComm(driver);
}

EXTERNAL void gcspyDriverStream (gcspy_gc_driver_t *driver, int id, int len) {
  GCSPY_TRACE_PRINTF("gcspyDriverStream: driver=%x(%s), id=%d(%s), len=%d\n",
                     driver, driver->name, id, driver->streams[id].name, len);
  stream_count = 0;
  stream_len = len;
  gcspy_driverStream(driver, id, len);
}

EXTERNAL void gcspyDriverStreamByteValue (gcspy_gc_driver_t *driver, int val) {
  GCSPY_TRACE_PRINTF("gcspyDriverStreamByteValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverStreamByteValue(driver, val);
}

EXTERNAL void gcspyDriverStreamShortValue (gcspy_gc_driver_t *driver, short val) {
  GCSPY_TRACE_PRINTF("gcspyDriverStreamShortValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverStreamShortValue(driver, val);
}

EXTERNAL void gcspyDriverStreamIntValue (gcspy_gc_driver_t *driver, int val) {
  GCSPY_TRACE_PRINTF("gcspyDriverStreamIntValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverStreamIntValue(driver, val);
}

EXTERNAL void gcspyDriverSummary (gcspy_gc_driver_t *driver, int id, int len) {
  GCSPY_TRACE_PRINTF("gcspyDriverSummary: driver=%x(%s), id=%d(%s), len=%d\n",
                     driver, driver->name, id, driver->streams[id].name, len);
  stream_count = 0;
  stream_len = len;
  gcspy_driverSummary(driver, id, len);
}

EXTERNAL void gcspyDriverSummaryValue (gcspy_gc_driver_t *driver, int val) {
  GCSPY_TRACE_PRINTF("gcspyDriverSummaryValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverSummaryValue(driver, val);
}

/* Note: passed driver but uses driver->interpreter */
EXTERNAL void gcspyIntWriteControl (gcspy_gc_driver_t *driver, int id, int len) {
  GCSPY_TRACE_PRINTF("gcspyIntWriteControl: driver=%x(%s), interpreter=%x, id=%d, len=%d\n", driver, driver->name, driver->interpreter, id, len);
  stream_count = 0;
  stream_len = len;
  gcspy_intWriteControl(driver->interpreter, id, len);
}

EXTERNAL gcspy_gc_driver_t * gcspyMainServerAddDriver (gcspy_main_server_t *server) {
  GCSPY_TRACE_PRINTF("gcspyMainServerAddDriver: server address = %x(%s), adding driver...", server, server->name);
  gcspy_gc_driver_t *driver = gcspy_mainServerAddDriver(server);
  GCSPY_TRACE_PRINTF("address = %d\n", driver);
  return driver;
}

EXTERNAL void gcspyMainServerAddEvent (gcspy_main_server_t *server, int event, const char *name) {
  GCSPY_TRACE_PRINTF("gcspyMainServerAddEvent: server address = %x(%s), event=%d, name=%s\n", server, server->name, event, name);
  gcspy_mainServerAddEvent(server, event, name);
}

EXTERNAL gcspy_main_server_t * gcspyMainServerInit (int port, int len, const char *name, int verbose) {
  GCSPY_TRACE_PRINTF("gcspyMainServerInit: server=%x, port=%d, len=%d, name=%s, verbose=%d\n", &server, port, len, name, verbose);
  gcspy_mainServerInit(&server, port, len, name, verbose);
  return &server;
}

EXTERNAL int gcspyMainServerIsConnected (gcspy_main_server_t *server, int event) {
  GCSPY_TRACE_PRINTF("gcspyMainServerIsConnected: server=%x, event=%d...", &server, event);
  int res = gcspy_mainServerIsConnected(server, event);
  if (res)
    GCSPY_TRACE_PRINTF("connected\n");
  else
    GCSPY_TRACE_PRINTF("not connected\n");
  return res;
}

typedef void gcspyMainServerOuterLoop_t(gcspy_main_server_t *);

EXTERNAL gcspyMainServerOuterLoop_t * gcspyMainServerOuterLoop () {
  /* return gcspy_mainServerOuterLoop;*/
  return gcspy_mainServerMainLoop;
}

EXTERNAL void gcspyMainServerSafepoint (gcspy_main_server_t *server, int event) {
  GCSPY_TRACE_PRINTF("gcspyMainServerSafepoint: server=%x, event=%d\n", &server, event);
  gcspy_mainServerSafepoint(server, event);
}

EXTERNAL void gcspyMainServerSetGeneralInfo (gcspy_main_server_t *server, char *generalInfo) {
  GCSPY_TRACE_PRINTF("gcspyMainServerSetGeneralInfo: server=%x, info=%s\n", &server, generalInfo);
  gcspy_mainServerSetGeneralInfo(server, generalInfo);
}

EXTERNAL void gcspyMainServerStartCompensationTimer (gcspy_main_server_t *server) {
  GCSPY_TRACE_PRINTF("gcspyMainServerStartCompensationTimer: server=%x\n", server);
  gcspy_mainServerStartCompensationTimer(server);
}

EXTERNAL void gcspyMainServerStopCompensationTimer (gcspy_main_server_t *server) {
  GCSPY_TRACE_PRINTF("gcspyMainServerStopCompensationTimer: server=%x\n", server);
  gcspy_mainServerStopCompensationTimer(server);
}

EXTERNAL void gcspyStartserver (gcspy_main_server_t *server, int wait, void *loop) {
//#ifndef __linux__
//  printf("I am not Linux!");
//     exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
//#endif __linux__
  GCSPY_TRACE_PRINTF("gcspyStartserver: starting thread, wait=%d\n", wait);
  pthread_t tid;
  int res = pthread_create(&tid, NULL,
                           (pthread_start_routine_t) loop,  server);
  if (res != 0) {
    ERROR_PRINTF("Couldn't create thread.\n");
    exit(EXIT_STATUS_MISC_TROUBLE);
  }

  if(wait) {
    GCSPY_TRACE_PRINTF("gcspy_mainServerWaitForClient: server=%x\n", server);
    gcspy_mainServerWaitForClient(server);
  }
}

EXTERNAL void gcspyStreamInit (gcspy_gc_stream_t *stream, int id, int dataType, char *streamName,
                               int minValue, int maxValue, int zeroValue, int defaultValue,
                               char *stringPre, char *stringPost, int presentation, int paintStyle,
                               int indexMaxStream, int red, int green, int blue) {
  gcspy_color_t colour;
  colour.red = (unsigned char) red;
  colour.green = (unsigned char) green;
  colour.blue = (unsigned char) blue;
  GCSPY_TRACE_PRINTF("gcspyStreamInit: stream=%x, id=%d, dataType=%d, streamName=\"%s\", min=%d, max=%d, zero=%d, default=%d, pre=\"%s\", post=\"%s\", presentation=%d, style=%d, maxIndex=%d, colour=%x<%d,%d,%d>\n",
                     stream, id, dataType, streamName,
                     minValue, maxValue, zeroValue, defaultValue,
                     stringPre, stringPost, presentation, paintStyle,
                     indexMaxStream, &colour, colour.red, colour.green, colour.blue);
  gcspy_streamInit(stream, id, dataType, streamName,
                   minValue, maxValue, zeroValue,defaultValue,
                   stringPre, stringPost, presentation, paintStyle,
                   indexMaxStream, &colour);
}

EXTERNAL void gcspyFormatSize (char *buffer, int size) {
  GCSPY_TRACE_PRINTF("gcspyFormatSize: size=%d...", size);
  strcpy(buffer, gcspy_formatSize(size));
  GCSPY_TRACE_PRINTF("buffer=%s\n", buffer);
}

EXTERNAL int gcspySprintf(char *str, const char *format, char *arg) {
  GCSPY_TRACE_PRINTF("sprintf: str=%x, format=%s, arg=%s\n", str, format, arg);
  int res = sprintf(str, format, arg);
  GCSPY_TRACE_PRINTF("sprintf: result=%s (%x)\n", str, str);
  return res;
}

#endif
