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

#include <dlfcn.h> // dlopen, dlerror, dlsym
#include <errno.h> // errno

/**
 * Load dynamic library.
 * Returned:  a handler for this library, null if none loaded
 */
EXTERNAL void* sysDlopen(char *libname)
{
  void * libHandler;
  TRACE_PRINTF("%s: sysDlopen %s\n", Me, libname);
  do {
    libHandler = dlopen(libname, RTLD_LAZY|RTLD_GLOBAL);
  }
  while( (libHandler == 0 /*null*/) && (errno == EINTR) );
  if (libHandler == 0) {
    ERROR_PRINTF("%s: error loading library %s: %s\n", Me,
                 libname, dlerror());
  }

  return libHandler;
}

/** Look up symbol in dynamic library. */
EXTERNAL void* sysDlsym(Address libHandler, char *symbolName)
{
  TRACE_PRINTF("%s: sysDlsym %s\n", Me, symbolName);
  return dlsym((void *) libHandler, symbolName);
}
