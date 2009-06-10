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
/****
 * Test loading a library and looking up a symbol
 */

#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <errno.h>


main(int argc, char **argv) {
  char *libName;
  char *symbolName;
  void *libHandler;
  void *symbolAddress;

  switch (argc) {
  case 3:
    libName = argv[1];
    symbolName = argv[2];
    /*  printf("Test loading library %s and looking for symbol %s\n", libName, symbolName); */
    break;
  default:
    printf("Load a library and look up a symbol\n");
    printf("Usage:\n   %s libname symbolname\n", argv[0]);
    return;
  }

  /* Load the library */

  do {
    libHandler = dlopen(libName, RTLD_NOW);
  } while( (libHandler == 0 /*null*/) && (errno == EINTR) );

  if (libHandler == 0) {
    if (errno == ENOEXEC)
      printf("Error loading library, %s\n", dlerror());
    else {
      switch (errno) {
      case EACCES:
        printf("Error loading library, cannot access because not an ordinary file, or permission denied\n");
        return 0;
      case EINVAL:
        printf("Error loading library, incorrect file header for the host machine\n");
        return 0;
      case ELOOP:
        printf("Error loading library, too many symbolic links in path name\n");
        return 0;
      case ENOEXEC:
        printf("Error loading library, problem in loading or resolving symbols, possibly invalid XCOFF header\n");
        return 0;
      case ENOMEM:
        printf("Error loading library, not enough memory\n");
        return 0;
      case ETXTBSY:
        printf("Error loading library, file currently open for writing by others\n");
        return 0;
      case ENAMETOOLONG:
        printf("Error loading library, path exceeded 1023 characters\n");
        return 0;
      case ENOENT:
        printf("Error loading library, bad library path\n");
        return 0;
      case ENOTDIR:
        printf("Error loading library, library path not a directory\n");
        return 0;
      case ESTALE:
        printf("Error loading library, file system unmounted\n");
        return 0;
      }

    }

  } else {
    printf("Library %s loaded successfully, handler = %d\n", libName, libHandler);

    /* Look up the symbol */
    symbolAddress = dlsym((void *) libHandler, symbolName);

    if (symbolAddress==0)
      printf("Symbol %s NOT found in library\n", symbolName);
    else
      printf("Symbol %s found at address 0x%8X\n", symbolName, symbolAddress);
  }

}
