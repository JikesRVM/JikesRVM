/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2005
 *
 * $Id$
 */
package gnu.classpath;

import java.util.Properties;

/** This is a stub version of <code>gnu.classpath.SystemProperties</code>, for
 * use when building Jikes RVM with GNU Classpath version 0.13 and later.
 * <P>
 * It is necessary because we need to tell the boot image writer's VM to use
 * Classpath's version of java.util.Locale.  (And THAT is necessary because
 * we need to write out some fields that are not present in the host JDK's
 * version of that class.)  However, starting in Classpath 0.13,
 * java.util.Locale began to use {@link
 * gnu.classpath.SystemProperties#getProperty}  instead of {@link
 * System#getProperty} to retrieve system properties.  This in turn was
 * pulling in too much of Classpath and Jikes RVM.  We stop that cycle by
 * defining {@link SystemProperties#getProperty} in terms of {@link
 * System#getProperty}.
 *
 * @author Steven Augart
 * @date 9 January 2005
 */

public class SystemProperties {
  public static String getProperty(String name) {
    return System.getProperty(name);
  }

//   public static String getProperty(String name, String default) {
//     return System.getProperty(name, default);
//   }
  public static String getProperty(String n, String d) {
    return System.getProperty(n, d);
  }

  public static Properties getProperties() {
    return System.getProperties();
  }
}
