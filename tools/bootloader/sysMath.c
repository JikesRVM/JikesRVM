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

#include <math.h>
#include <string.h> // strerror
#include <stdlib.h> // strtod, exit
#include <errno.h> // errno

#ifdef __x86_64__
#include <emmintrin.h>
#endif

double maxlong = 0.5 + (double)0x7fffffffffffffffLL;
double maxint  = 0.5 + (double)0x7fffffff;

EXTERNAL long long sysLongDivide(long long a, long long b)
{
  TRACE_PRINTF("%s: sysLongDivide %lld / %lld\n", Me, a, b);
  return a/b;
}

EXTERNAL long long sysLongRemainder(long long a, long long b)
{
  TRACE_PRINTF("%s: sysLongRemainder %lld %% %lld\n", Me, a, b);
  return a % b;
}

EXTERNAL double sysLongToDouble(long long a)
{
  TRACE_PRINTF("%s: sysLongToDouble %lld\n", Me, a);
  return (double)a;
}

EXTERNAL float sysLongToFloat(long long a)
{
  TRACE_PRINTF("%s: sysLongToFloat %lld\n", Me, a);
  return (float)a;
}

EXTERNAL int sysFloatToInt(float a)
{
  TRACE_PRINTF("%s: sysFloatToInt %f\n", Me, a);
  if (maxint <= a) return 0x7fffffff;
  if (a <= -maxint) return 0x80000000;
  if (a != a) return 0; // NaN => 0
  return (int)a;
}

EXTERNAL int sysDoubleToInt(double a)
{
  TRACE_PRINTF("%s: sysDoubleToInt %f\n", Me, a);
  if (maxint <= a) return 0x7fffffff;
  if (a <= -maxint) return 0x80000000;
  if (a != a) return 0; // NaN => 0
  return (int)a;
}

EXTERNAL long long sysFloatToLong(float a)
{
  TRACE_PRINTF("%s: sysFloatToLong %f\n", Me, a);
  if (maxlong <= a) return 0x7fffffffffffffffLL;
  if (a <= -maxlong) return 0x8000000000000000LL;
  return (long long)a;
}

EXTERNAL long long sysDoubleToLong(double a)
{
  TRACE_PRINTF("%s: sysDoubleToLong %f\n", Me, a);
  if (maxlong <= a) return 0x7fffffffffffffffLL;
  if (a <= -maxlong) return 0x8000000000000000LL;
  return (long long)a;
}

/**
 * Only used on PPC.
 */
EXTERNAL double sysDoubleRemainder(double a, double b)
{
  TRACE_PRINTF("%s: sysDoubleRemainder %f %% %f\n", Me, a, b);
  double tmp = remainder(a, b);
  if (a > 0.0) {
    if (b > 0.0) {
      if (tmp < 0.0) {
        tmp += b;
      }
    } else if (b < 0.0) {
      if (tmp < 0.0) {
        tmp -= b;
      }
    }
  } else {
    if (b > 0.0) {
      if (tmp > 0.0) {
        tmp -= b;
      }
    } else {
      if (tmp > 0.0) {
        tmp += b;
      }
    }
  }
  return tmp;
}

/**
 * Used to parse command line arguments that are doubles and floats early in
 * booting before it is safe to call Float.valueOf or Double.valueOf.
 *
 * This is only used in parsing command-line arguments, so we can safely
 * print error messages that assume the user specified this number as part
 * of a command-line argument.
 */
EXTERNAL float sysPrimitiveParseFloat(const char * buf)
{
  TRACE_PRINTF("%s: sysPrimitiveParseFloat %s\n", Me, buf);
  if (! buf[0] ) {
    ERROR_PRINTF("%s: Got an empty string as a command-line"
                 " argument that is supposed to be a"
                 " floating-point number\n", Me);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  char *end;         // This prototype is kinda broken.  It really
  // should be char *.  But isn't.
  errno = 0;
  float f = (float)strtod(buf, &end);
  if (errno) {
    ERROR_PRINTF("%s: Trouble while converting the"
                 " command-line argument \"%s\" to a"
                 " floating-point number: %s\n", Me, buf, strerror(errno));
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  if (*end != '\0') {
    ERROR_PRINTF("%s: Got a command-line argument that"
                 " is supposed to be a floating-point value,"
                 " but isn't: %s\n", Me, buf);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  return f;
}

/**
 * Used to parse command line arguments that are ints and bytes early in
 * booting before it is safe to call Integer.parseInt and Byte.parseByte.
 *
 * This is only used in parsing command-line arguments, so we can safely
 * print error messages that assume the user specified this number as part
 * of a command-line argument.
 */
EXTERNAL int sysPrimitiveParseInt(const char * buf)
{
  TRACE_PRINTF("%s: sysPrimitiveParseInt %s\n", Me, buf);
  if (! buf[0] ) {
    ERROR_PRINTF("%s: Got an empty string as a command-line"
                 " argument that is supposed to be an integer\n", Me);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  char *end;
  errno = 0;
  long l = strtol(buf, &end, 0);
  if (errno) {
    ERROR_PRINTF("%s: Trouble while converting the"
                 " command-line argument \"%s\" to an integer: %s\n",
                 Me, buf, strerror(errno));
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  if (*end != '\0') {
    ERROR_PRINTF("%s: Got a command-line argument that is supposed to be an integer, but isn't: %s\n", Me, buf);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  int32_t ret = l;
  if ((long) ret != l) {
    ERROR_PRINTF("%s: Got a command-line argument that is supposed to be an integer, but its value does not fit into a Java 32-bit integer: %s\n", Me, buf);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  return ret;
}

/**
 * Used to parse command line arguments that are longs early in
 * booting before it is safe to call Long.parseLong.
 *
 * This is only used in parsing command-line arguments, so we can safely
 * print error messages that assume the user specified this number as part
 * of a command-line argument.
 */
EXTERNAL long long int sysPrimitiveParseLong(const char * buf)
{
  TRACE_PRINTF("%s: sysPrimitiveParseLong %s\n", Me, buf);
  if (! buf[0] ) {
    ERROR_PRINTF("%s: Got an empty string as a command-line"
                 " argument that is supposed to be an integer\n", Me);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  char *end;
  errno = 0;
  unsigned long long int l = strtoull(buf, &end, 0);
  if (errno) {
    ERROR_PRINTF("%s: Trouble while converting the"
                 " command-line argument \"%s\" to an integer: %s\n",
                 Me, buf, strerror(errno));
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  if (*end != '\0') {
    ERROR_PRINTF("%s: Got a command-line argument that is supposed to be an integer, but isn't: %s\n", Me, buf);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  return l;
}

// VMMath

EXTERNAL double sysVMMathSin(double a) {
  TRACE_PRINTF("%s: sysVMMathSin %f\n", Me, a);
  return sin(a);
}

EXTERNAL double sysVMMathCos(double a) {
  TRACE_PRINTF("%s: sysVMMathCos %f\n", Me, a);
  return cos(a);
}

EXTERNAL double sysVMMathTan(double a) {
  TRACE_PRINTF("%s: sysVMMathTan %f\n", Me, a);
  return tan(a);
}

EXTERNAL double sysVMMathAsin(double a) {
  TRACE_PRINTF("%s: sysVMMathAsin %f\n", Me, a);
  return asin(a);
}

EXTERNAL double sysVMMathAcos(double a) {
  TRACE_PRINTF("%s: sysVMMathAcos %f\n", Me, a);
  return acos(a);
}

EXTERNAL double sysVMMathAtan(double a) {
  TRACE_PRINTF("%s: sysVMMathAtan %f\n", Me, a);
  return atan(a);
}

EXTERNAL double sysVMMathAtan2(double a, double b) {
  TRACE_PRINTF("%s: sysVMMathAtan2 %f %f\n", Me, a, b);
  return atan2(a, b);
}

EXTERNAL double sysVMMathCosh(double a) {
  TRACE_PRINTF("%s: sysVMMathCosh %f\n", Me, a);
  return cosh(a);
}

EXTERNAL double sysVMMathSinh(double a) {
  TRACE_PRINTF("%s: sysVMMathSinh %f\n", Me, a);
  return sinh(a);
}

EXTERNAL double sysVMMathTanh(double a) {
  TRACE_PRINTF("%s: sysVMMathTanh %f\n", Me, a);
  return tanh(a);
}

EXTERNAL double sysVMMathExp(double a) {
  TRACE_PRINTF("%s: sysVMMathExp %f\n", Me, a);
  return exp(a);
}

EXTERNAL double sysVMMathLog(double a) {
  TRACE_PRINTF("%s: sysVMMathLog %f\n", Me, a);
  return log(a);
}

EXTERNAL double sysVMMathSqrt(double a) {
  TRACE_PRINTF("%s: sysVMMathSqrt %f\n", Me, a);
  return sqrt(a);
}

EXTERNAL double sysVMMathPow(double a, double b) {
  TRACE_PRINTF("%s: sysVMMathPow %f %f\n", Me, a, b);
  return pow(a, b);
}

EXTERNAL double sysVMMathIEEEremainder(double a, double b) {
  TRACE_PRINTF("%s: sysVMMathIEEEremainder %f %f\n", Me, a, b);
  return remainder(a, b);
}

EXTERNAL double sysVMMathCeil(double a) {
  TRACE_PRINTF("%s: sysVMMathCeil %f\n", Me, a);
  return ceil(a);
}

EXTERNAL double sysVMMathFloor(double a) {
  TRACE_PRINTF("%s: sysVMMathFloor %f\n", Me, a);
  return floor(a);
}

EXTERNAL double sysVMMathRint(double a) {
  TRACE_PRINTF("%s: sysVMMathRint %f\n", Me, a);
  return rint(a);
}

EXTERNAL double sysVMMathCbrt(double a) {
  TRACE_PRINTF("%s: sysVMMathCbrt %f\n", Me, a);
  return cbrt(a);
}

EXTERNAL double sysVMMathExpm1(double a) {
  TRACE_PRINTF("%s: sysVMMathExpm1 %f\n", Me, a);
  return expm1(a);
}

EXTERNAL double sysVMMathHypot(double a, double b) {
  TRACE_PRINTF("%s: sysVMMathHypot %f %f\n", Me, a, b);
  return hypot(a, b);
}

EXTERNAL double sysVMMathLog10(double a) {
  TRACE_PRINTF("%s: sysVMMathLog10 %f\n", Me, a);
  return log10(a);
}

EXTERNAL double sysVMMathLog1p(double a) {
  TRACE_PRINTF("%s: sysVMMathLog1p %f\n", Me, a);
  return log1p(a);
}
