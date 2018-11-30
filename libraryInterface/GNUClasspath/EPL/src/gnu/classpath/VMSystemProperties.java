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
package gnu.classpath;

import java.util.Properties;

import org.jikesrvm.VM;     // for VM.sysWrite()
import org.jikesrvm.classlibrary.JavaLangSupport;
import org.jikesrvm.Configuration;
import org.jikesrvm.runtime.CommandLineArgs;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.scheduler.Lock;

/**
 * Jikes RVM implementation of GNU Classpath's gnu.classpath.VMSystemProperties.
 * <P>
 * Library support interface of Jikes RVM.  See the Javadoc documentation for
 * GNU Classpath's reference implementation of VMSystemProperties -- for
 * copyright reasons, we cannot duplicate it here.
 */

public class VMSystemProperties {

  public static void preInit(Properties p) {
    JavaLangSupport.setupStandardJavaProperties(p);

    p.put("gnu.cpu.endian", Configuration.LittleEndian ? "little" : "big");

    /* Properties for JMX; this lets the implementation know which
     * features JikesRVM supports */
    p.put("gnu.java.compiler.name", "JikesRVM");
    if (VM.MeasureCompilation) {
      p.put("gnu.java.lang.management.CompilationTimeSupport", "true");
    }
    if (Lock.STATS) {
      p.put("gnu.java.lang.management.ThreadContentionSupport","true");
    }

    String s = BootstrapClassLoader.getBootstrapRepositories();
    /* sun.boot.class.path is not necessary, yes, but possibly useful; Steve
     * Augart has seen at least one piece of code on the web that reads
     * this. */
    p.put("sun.boot.class.path", s);

    /* user.timezone

       I (Steve Augart) started a discussion about this on classpath@gnu.org
       on 23 March 2003.  Summary: we define user.timezone specifically in
       order to pass that information to GNU Classpath's implementation of
       java.util.TimeZone, which initializes
       later on in the boot process.  It does not seem to be required by the
       spec, and it's the empty string in Blackdown 1.4.2.

       We have to do this here, because otherwise it wouldn't be set until
       CommandLineArgs.lateProcessCommandLineArguments().  That won't occur
       until the VM is fully booted; too late for java.util.TimeZone, which
       reads this value when it runs its initializer.
    */
    JavaLangSupport.setTimezoneProperties(p);

    if (VM.PortableNativeSync) {
      /* Enable portable native sync to support M-to-N threading with gtk peers */
      p.put("gnu.classpath.awt.gtk.portable.native.sync", "true");
    }

    /* Now the rest of the GNU Classpath specific ones that we set on the command line.   Do
     * this just in case later revisions of GNU Classpath start to require
     * some of them in the boot process; otherwise, we could wait for them to
     * be set in CommandLineArgs.lateProcessCommandLineArguments() */
    final String[] clProps = new String[] {"gnu.classpath.vm.shortname", "gnu.classpath.home.url"};

    for (final String prop : clProps) {
      s = CommandLineArgs.getEnvironmentArg(prop);
      if (s != null) {
        p.put(prop, s);
      }
    }
  }

  /**
   * Override the default SystemProperties code; insert the command-line
   * arguments.
   * <p>
   * The following are set by the "runrvm" script before we go into the C
   * bootloader, by passing them as command-line args with the -D flag:
   * <p>
   * os.name, os.arch, os.version
   * user.name, user.home, user.dir
   * gnu.classpath.vm.shortname, gnu.classpath.home.url,
   * java.home,
   * <p>
   * We can look at them here via CommandLineArgs.getEnvironmentArg().
   * <p>
   * They will be automatically set for us by
   * CommandLineArgs.lateProcessCommandLineArguments() if we do not handle
   * them here.  That won't occur until the VM is fully booted.  That's too
   * late for some classes, such as java.util.TimeZone, which will already be
   * initialized.
   * <p>
   * In any case, this function isn't used in Jikes RVM.  Our boot sequence
   * is already handling this OK.
   *
   * @param properties the properties to modify
   */
  public static void postInit(Properties properties) {
  }
}
