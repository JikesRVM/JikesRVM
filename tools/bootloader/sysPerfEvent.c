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

/*
 * Performance counter support using the linux perf event system.
 */

#ifdef RVM_WITH_PERFEVENT
#include <perfmon/pfmlib_perf_event.h>
#include <err.h>
#include <string.h> // strerror
#include <errno.h>
#endif

#ifndef RVM_WITH_PERFEVENT
  EXTERNAL void sysPerfEventInit(int events) {}
  EXTERNAL void sysPerfEventCreate(int id, const char *eventName) {}
  EXTERNAL void sysPerfEventEnable() {}
  EXTERNAL void sysPerfEventDisable() {}
  EXTERNAL void sysPerfEventRead(int id, long long *values) {}
#else
  static int enabled = 0;
  static int *perf_event_fds;
  static struct perf_event_attr *perf_event_attrs;

  EXTERNAL void sysPerfEventInit(int numEvents)
  {
    int i;
    TRACE_PRINTF("%s: sysPerfEventInit\n", Me);
    int ret = pfm_initialize();
    if (ret != PFM_SUCCESS) {
      errx(1, "error in pfm_initialize: %s", pfm_strerror(ret));
    }

    perf_event_fds = (int*)checkCalloc(numEvents, sizeof(int));
    if (!perf_event_fds) {
      errx(1, "error allocating perf_event_fds");
    }
    perf_event_attrs = (struct perf_event_attr *)checkCalloc(numEvents, sizeof(struct perf_event_attr));
    if (!perf_event_attrs) {
      errx(1, "error allocating perf_event_attrs");
    }
    for(i = 0; i < numEvents; i++) {
      perf_event_attrs[i].size = sizeof(struct perf_event_attr);
    }
    enabled = 1;
  }

  EXTERNAL void sysPerfEventCreate(int id, const char *eventName)
  {
    TRACE_PRINTF("%s: sysPerfEventCreate\n", Me);
    struct perf_event_attr *pe = (perf_event_attrs + id);
    int ret = pfm_get_perf_event_encoding(eventName, PFM_PLM3, pe, NULL, NULL);
    if (ret != PFM_SUCCESS) {
      errx(1, "error creating event %d '%s': %s\n", id, eventName, pfm_strerror(ret));
    }
    pe->read_format = PERF_FORMAT_TOTAL_TIME_ENABLED | PERF_FORMAT_TOTAL_TIME_RUNNING;
    pe->disabled = 1;
    pe->inherit = 1;
    perf_event_fds[id] = perf_event_open(pe, 0, -1, -1, 0);
    if (perf_event_fds[id] == -1) {
      err(1, "error in perf_event_open for event %d '%s'", id, eventName);
    }
  }

  EXTERNAL void sysPerfEventEnable()
  {
    TRACE_PRINTF("%s: sysPerfEventEnable\n", Me);
    if (enabled) {
      if (prctl(PR_TASK_PERF_EVENTS_ENABLE)) {
        err(1, "error in prctl(PR_TASK_PERF_EVENTS_ENABLE)");
      }
    }
  }

  EXTERNAL void sysPerfEventDisable()
  {
    TRACE_PRINTF("%s: sysPerfEventDisable\n", Me);
    if (enabled) {
      if (prctl(PR_TASK_PERF_EVENTS_DISABLE)) {
        err(1, "error in prctl(PR_TASK_PERF_EVENTS_DISABLE)");
      }
    }
  }

  EXTERNAL void sysPerfEventRead(int id, long long *values)
  {
    TRACE_PRINTF("%s: sysPerfEventRead\n", Me);
    size_t expectedBytes = 3 * sizeof(long long);
    int ret = read(perf_event_fds[id], values, expectedBytes);
    if (ret < 0) {
      err(1, "error reading event: %s", strerror(errno));
    }
    if ((size_t) ret != expectedBytes) {
      errx(1, "read of perf event did not return 3 64-bit values");
    }
  }
#endif
