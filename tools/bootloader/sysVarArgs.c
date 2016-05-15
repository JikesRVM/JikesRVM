/*
 *  This file is part of the Metacircular Research Platform (MRP)
 *
 *      http://mrp.codehaus.org/
 *
 *  This file is licensed to you under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the license at:
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

#include "sys.h"
#include <stdarg.h>

/**
 * Creates copy of va_list that can be updated and worked upon
 */
EXTERNAL va_list* sysVaCopy(va_list ap)
{
  TRACE_PRINTF("%s: sysVaCopy\n", Me);
  va_list *ap_ = (va_list*)sysMalloc(sizeof(va_list));
  va_copy (*ap_, ap);
  return ap_;
}

/**
 * Ends use of va_list
 */
EXTERNAL void sysVaEnd(va_list *ap)
{
  TRACE_PRINTF("%s: sysVaEnd\n", Me);
  va_end(*ap);
  sysFree(ap);
}


/**
 * Reads jboolean var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jboolean sysVaArgJboolean(va_list *ap)
{
  jboolean result = (jboolean)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJboolean %d\n", Me, result);
  return result;
}

/**
 * Reads jbyte var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jbyte sysVaArgJbyte(va_list *ap)
{
  jbyte result = (jbyte)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJbyte %d\n", Me, result);
  return result;
}

/**
 * Reads jchar var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jchar sysVaArgJchar(va_list *ap)
{
  jchar result = (jchar)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJchar %d\n", Me, result);
  return result;
}

/**
 * Reads jshort var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jshort sysVaArgJshort(va_list *ap)
{
  jshort result = (jshort)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJshort %d\n", Me, result);
  return result;
}

/**
 * Reads jint var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jint sysVaArgJint(va_list *ap)
{
  jint result = va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJint %d\n", Me, result);
  return result;
}

/**
 * Reads jlong var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jlong sysVaArgJlong(va_list *ap)
{
  jlong result = va_arg(*ap, jlong);
  TRACE_PRINTF("%s: sysVaArgJlong %lld\n", Me, (long long int)result);
  return result;
}

/**
 * Reads jfloat var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jfloat sysVaArgJfloat(va_list *ap)
{
  jfloat result = (jfloat)va_arg(*ap, jdouble);
  TRACE_PRINTF("%s: sysVaArgJfloat %f\n", Me, result);
  return result;
}

/**
 * Reads jdouble var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jdouble sysVaArgJdouble(va_list *ap)
{
  jdouble result = va_arg(*ap, jdouble);
  TRACE_PRINTF("%s: sysVaArgJdouble %f\n", Me, result);
  return result;
}

/**
 * Reads jobject var arg
 *
 * Taken:       va_list [in,out] var arg list update to next entry after read
 * Returned:    argument
 */
EXTERNAL jobject sysVaArgJobject(va_list *ap)
{
  jobject result = va_arg(*ap, jobject);
  TRACE_PRINTF("%s: sysVaArgJobject %p\n", Me, (void *)result);
  return result;
}
