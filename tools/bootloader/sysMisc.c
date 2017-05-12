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

#include <stdlib.h> // getenv
#include <errno.h> // errno
#include <limits.h> // UINT_MAX

/**
 * Access host o/s command line arguments.
 * Taken:    -1
 *           null
 * Returned: number of arguments
 *          /or/
 * Taken:    arg number sought
 *           buffer to fill
 * Returned: number of bytes written to buffer (-1: arg didn't fit, buffer too small)
 */
EXTERNAL int sysArg(int argno, char *buf, int buflen)
{
  int i;
  TRACE_PRINTF("%s: sysArg %d\n", Me, argno);
  if (argno == -1) { // return arg count
    return JavaArgc;
    /***********
    for (int i = 0;;++i)
     if (JavaArgs[i] == 0)
        return i;
    **************/
  } else { // return i-th arg
    const char *src = JavaArgs[argno];
    for (i = 0;; ++i)
    {
      if (*src == 0)
        return i;
      if (i == buflen)
        return -1;
      *buf++ = *src++;
    }
  }
  /* NOTREACHED */
}

/**
 * Copy SRC, a null-terminated string or a NULL pointer, into DEST, a buffer
 * with LIMIT characters capacity.   This is a helper function used by
 * sysGetEnv() and, later on, to be used by other functions returning strings
 * to Java.
 *
 * Handle the error handling for running out of space in BUF, in accordance
 * with the C '99 specification for snprintf() -- see sysGetEnv().
 *
 * Returned:  -2 if SRC is a NULL pointer.
 * Returned:  If enough space, the number of bytes copied to DEST.
 *
 *            If there is not enough space, we write what we can and return
 *            the # of characters that WOULD have been written to the final
 *            string BUF if enough space had been available, excluding any
 *            trailing '\0'.  This error handling is consistent with the C '99
 *            standard's behavior for the snprintf() system library function.
 *
 *            Note that this is NOT consistent with the behavior of most of
 *            the functions in this file that return strings to Java.
 *
 *            That should change with time.
 *
 *            This function will append a trailing '\0', if there is enough
 *            space, even though our caller does not need it nor use it.
 */
static int loadResultBuf(char * dest, int limit, const char *src)
{
  int i;
  if ( ! src )         // Is it set?
    return -2;      // Tell caller it was unset.

  for (i = 0;; ++i) {
    if ( i < limit ) // If there's room for the next char of the value ...
      dest[i] = src[i];   // ... write it into the destination buffer.
    if (src[i] == '\0')
      return i;      // done, return # of chars needed for SRC
  }
}

/**
 * Get the value of an enviroment variable.  (This refers to the C
 * per-process environment.)   Used, indirectly, by VMSystem.getenv()
 *
 * Taken:     VARNAME, name of the envar we want.
 *            BUF, a buffer in which to place the value of that envar
 *            LIMIT, the size of BUF
 * Returned:  See the convention documented in loadResultBuf().
 *            0: A return value of 0 indicates that the envar was set with a
 *             zero-length value.   (Distinguised from unset, see below)
 *            -2: Indicates that the envar was unset.  This is distinguished
 *            from a zero-length value (see above).
 */
EXTERNAL int sysGetenv(const char *varName, char *buf, int limit)
{
  TRACE_PRINTF("%s: sysGetenv %s\n", Me, varName);
  return loadResultBuf(buf, limit, getenv(varName));
}

/**
 * Parse memory sizes. Negative return values indicate errors.
 * Taken:     name of the memory area (one of ("initial heap", "maximum heap",
 *              "initial stack", "maximum stack")
 *            flag for size (e.g., "ms" or "mx" or "ss" or "sg" or "sx")
 *            default factor (e.g. "M" or "K")
 *            rounding target (e.g. to 4 or to PAGE_SIZE_BYTES)
 *            whole token (e.g. "-Xms200M" or "-Xms200")
 *            subtoken (e.g. "200M" or "200")
 * Returned   negative value for errors
 */
EXTERNAL jlong sysParseMemorySize(const char *sizeName, const char *sizeFlag,
                                  const char *defaultFactor, int roundTo,
                                  const char *token /* e.g., "-Xms200M" or "-Xms200" */,
                                  const char *subtoken /* e.g., "200M" or "200" */)
{
  int fastExit = 0;
  unsigned ret_uns;
  TRACE_PRINTF("%s: sysParseMemorySize %s\n", Me, token);
  ret_uns = parse_memory_size(sizeName, sizeFlag, defaultFactor,
                                       (Extent) roundTo, token, subtoken,
                                       &fastExit);
  if (fastExit)
    return -1;
  else
    return (jlong) ret_uns;
}

/** Return a # of bytes, rounded up to the next page size.  Setting fastExit
 *  means trouble or failure.  If we set fastExit we'll also return the value
 *  0U.
 *
 * NOTE: Given the context, we treat "MB" as having its
 * historic meaning of "MiB" (2^20), rather than its 1994 ISO
 * meaning, which would be a factor of 10^7.
 */
EXTERNAL Extent parse_memory_size(const char *sizeName, /*  "initial heap" or "maximum heap" or
                                            "initial stack" or "maximum stack"
                                        */
                  const char *sizeFlag, // "-Xms" or "-Xmx" or
                  // "-Xss" or "-Xsg" or "-Xsx"
                  const char *defaultFactor, // We now always default to bytes ("")
                  Extent roundTo,  // Round to PAGE_SIZE_BYTES or to 4.
                  const char *token /* e.g., "-Xms200M" or "-Xms200" */,
                  const char *subtoken /* e.g., "200M" or "200" */,
                  int *fastExit)
{
  // First, set the factor appropriately, and make sure there aren't extra
  // characters at the end of the line.
  const char *factorStr = defaultFactor;
  long double factor = 0.0;   // 0.0 is a sentinel meaning Unset
  long double tot_d;
  Extent tot;

  errno = 0;
  double userNum;
  char *endp;                 /* Should be const char *, but if we do that,
                                   then the C++ compiler complains about the
                                   prototype for strtold() or strtod().   This
                                   is probably a bug in the specification
                                   of the prototype. */
  userNum = strtod(subtoken, &endp);
  if (endp == subtoken) {
    CONSOLE_PRINTF( "%s: \"%s\": -X%s must be followed by a number.\n", Me, token, sizeFlag);
    *fastExit = 1;
  }



  if (*endp == '\0') {
    /* no suffix.  Along with the Sun JVM, we now assume Bytes by
       default. (This is a change from  previous Jikes RVM behaviour.)  */
    factor = 1.0;
  } else if (STREQUAL(endp, "pages") ) {
    factor = pageSize;
    /* Handle constructs like "M" and "K" */
  } else if ( endp[1] == '\0' ) {
    factorStr = endp;
  } else {
    CONSOLE_PRINTF( "%s: \"%s\": I don't recognize \"%s\" as a"
                    " unit of memory size\n", Me, token, endp);
    *fastExit = 1;
  }

  if (! *fastExit && factor == 0.0) {
    char e = *factorStr;
    if (e == 'g' || e == 'G') factor = 1024.0 * 1024.0 * 1024.0;
    else if (e == 'm' || e == 'M') factor = 1024.0 * 1024.0;
    else if (e == 'k' || e == 'K') factor = 1024.0;
    else if (e == '\0') factor = 1.0;
    else {
      CONSOLE_PRINTF( "%s: \"%s\": I don't recognize \"%s\" as a"
                      " unit of memory size\n", Me, token, factorStr);
      *fastExit = 1;
    }
  }

  // Note: on underflow, strtod() returns 0.
  if (!*fastExit) {
    if (userNum <= 0.0) {
      CONSOLE_PRINTF(
        "%s: You may not specify a %s %s (%f - %s);\n",
        Me, userNum < 0.0 ? "negative" : "zero", sizeName, userNum, subtoken);
      CONSOLE_PRINTF( "\tit just doesn't make any sense.\n");
      *fastExit = 1;
    }
  }

  if (!*fastExit) {
    if ( errno == ERANGE || userNum > (((long double) (UINT_MAX - roundTo))/factor) )
    {
      CONSOLE_PRINTF( "%s: \"%s\": out of range to represent internally\n", Me, subtoken);
      *fastExit = 1;
    }
  }

  if (*fastExit) {
    CONSOLE_PRINTF( "\tPlease specify %s as follows:\n", sizeName);
    CONSOLE_PRINTF( "\t    in bytes, using \"-X%s<positive number>\",\n", sizeFlag);
    CONSOLE_PRINTF( "\tor, in kilobytes, using \"-X%s<positive number>K\",\n", sizeFlag);
    CONSOLE_PRINTF( "\tor, in virtual memory pages of %zu bytes, using\n"
                    "\t\t\"-X%s<positive number>pages\",\n", pageSize,
                    sizeFlag);
    CONSOLE_PRINTF( "\tor, in megabytes, using \"-X%s<positive number>M\",\n", sizeFlag);
    CONSOLE_PRINTF( "\tor, in gigabytes, using \"-X%s<positive number>G\"\n", sizeFlag);
    CONSOLE_PRINTF( "  <positive number> can be a floating point value or a hex value like 0x10cafe0.\n");
    if (roundTo != 1) {
      CONSOLE_PRINTF( "  The # of bytes will be rounded up to a multiple of");
      if (roundTo == pageSize) CONSOLE_PRINTF( "\n  the virtual memory page size: ");
      CONSOLE_PRINTF( "%zu\n", roundTo);
    }
    return 0U;              // Distinguished value meaning trouble.
  }
  tot_d = userNum * factor;
  if (tot_d > (UINT_MAX - roundTo) || tot_d < 1) {
    ERROR_PRINTF("Unexpected memory size %Lf\n", tot_d);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }

  tot = (Extent) tot_d;
  if (tot % roundTo) {
    Extent newTot = tot + roundTo - (tot % roundTo);
    CONSOLE_PRINTF(
      "%s: Rounding up %s size from %zu bytes to %zu,\n"
      "\tthe next multiple of %zu bytes%s\n",
      Me, sizeName, tot, newTot, roundTo,
      roundTo == pageSize ?
      ", the virtual memory page size" : "");
    tot = newTot;
  }
  return tot;
}
