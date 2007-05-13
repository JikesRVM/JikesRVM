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
 *
 */

#ifndef _GCSPY_GC_DRIVER_H_

#define _GCSPY_GC_DRIVER_H_

#include "gcspy_command_stream.h"
#include "gcspy_gc_stream.h"

typedef struct {
} gcspy_gc_driver_t;

#define gcspy_driverAddStream(driver,id)  NULL 
#define gcspy_driverEndOutput(driver)
#define gcspy_driverInit(driver,id,serverName,driverName,title,blockInfo,tileNum,unused,mainSpace)
#define gcspy_driverInitOutput(driver)
#define gcspy_driverResize(driver,size)
#define gcspy_driverSetTileName(driver,tile,buffer)
#define gcspy_driverSpaceInfo(driver,spaceInfo)
#define gcspy_driverStartComm(driver)
#define gcspy_driverStream(driver,id,len)
#define gcspy_driverStreamByteValue(driver,val)
#define gcspy_driverStreamShortValue(driver,val)
#define gcspy_driverStreamIntValue(driver,val)
#define gcspy_driverSummary(driver,id,len)
#define gcspy_driverSummaryValue(driver,val)

#endif // _GCSPY_GC_DRIVER_H_

