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
import org.jikesrvm.CommandLineArgs;
import org.jikesrvm.Configuration;

import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.BootstrapClassLoader;

/**
 * Jikes RVM implementation of GNU Classpath's gnu.classpath.VMSystemProperties.
 * <P>
 * Library support interface of Jikes RVM.  See the Javadoc documentation for
 * GNU Classpath's reference implementation of VMSystemProperties -- for
 * copyright reasons, we cannot duplicate it here.
 */

public class VMSystemProperties {
  /** VMRuntime.insertSystemProperties is used by Classpath versions through
   *  Classpath  0.12.   Starting with Classpath 0.13, we use
   *  gnu.classpath.VMSystemProperties.preInit and
   *  gnu.classpath.VMSystemProperties.postInit.
 */
  public static void preInit(Properties p) {
    p.put("java.version", "1.6.0"); /* This is a lie, of course -- we don't
                                       really support all 1.6 features, such
                                       as assertions.  However, it is a
                                       necessary lie, since Eclipse 3.0
                                       explicitly tests java.version and
                                       insists upon at least 1.4.1 to run. */
    p.put("java.vendor", "Jikes RVM Project");
    p.put("java.vm.vendor", "Jikes RVM Project");
    p.put("java.vendor.url", "http://jikesrvm.org");

    p.put("java.specification.name", "Java Platform API Specification");
    p.put("java.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.specification.version", "1.6");

    p.put("java.vm.specification.name", "Java Virtual Machine Specification");
    p.put("java.vm.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.vm.specification.version", "1.0");

    /* 50.0 brings us through Java version 1.6. */
    p.put("java.class.version", "50.0");

    p.put("file.separator", "/");
    p.put("path.separator", ":");
    p.put("line.separator", "\n");

    p.put("java.compiler", "JikesRVM");
    p.put("java.vm.version", "1.6.0");
    p.put("java.vm.name", "JikesRVM");
    p.put("file.encoding", "8859_1");
    p.put("java.io.tmpdir", "/tmp");
    p.put("gnu.cpu.endian", Configuration.LittleEndian ? "little" : "big");


    String s;
    s = BootstrapClassLoader.getBootstrapRepositories();
    p.put("java.boot.class.path", s);
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
    s = CommandLineArgs.getEnvironmentArg("user.timezone");
    s = (s == null) ? "" : s;   // Maybe it's silly to set it to the empty
                                // string.  Well, this should never succeed
                                // anyway, since we're always called by
                                // runrvm, which explicitly sets the value.
    p.put("user.timezone", s);

    /* java.library.path
       Now the library path.  This is the path used for system
       dynamically-loaded libraries, the things that end in ".so" on Linux. */
    insertLibraryPath(p);

    /* What should we do about java.ext.dirs?
       XXX TODO

       java.ext.dirs is allegedly mandatory, according to the API docs shipped
       with the Sun 1.4.2 JDK.

       Ridiculous, since we don't search it for anything, and since if the
       user were to set it it wouldn't do anything anyway.   We keep all of
       the extensions stored with the other bits of the JDK.   So, this would
       really need to be prepended to the list of VM classes, wouldn't it?  Or
       appended, perhaps? */
    s = CommandLineArgs.getEnvironmentArg("java.ext.dirs");
    if (s == null) {
      s = "";
    } else {
      VM.sysWrite("Jikes RVM: Warning: You have explicitly set java.ext.dirs; that will not do anything under Jikes RVM");
    }
    p.put("java.ext.dirs", s);


    /* We also set java.class.path in setApplicationRepositories().
     *  We'll treat setting the java.class.path property as essentially
     * equivalent to using the -classpath argument. */
    s = CommandLineArgs.getEnvironmentArg("java.class.path");
    if (s != null) {
      p.put("java.class.path", s);
      RVMClassLoader.stashApplicationRepositories(s);
    } else {
      p.put("java.class.path", RVMClassLoader.getApplicationRepositories());
    }

    if (VM.PortableNativeSync) {
      /* Enable portable native sync to support M-to-N threading with gtk peers */
      p.put("gnu.classpath.awt.gtk.portable.native.sync", "true");
    }

    /* Now the rest of the special ones that we set on the command line.   Do
     * this just in case later revisions of GNU Classpath start to require
     * some of them in the boot process; otherwise, we could wait for them to
     * be set in CommandLineArgs.lateProcessCommandLineArguments() */
    final String[] clProps = new String[] {"os.name", "os.arch", "os.version", "user.name", "user.home", "user.dir", "gnu.classpath.vm.shortname", "gnu.classpath.home.url", "java.home"};

    for (final String prop : clProps) {
      s = CommandLineArgs.getEnvironmentArg(prop);
      if (s != null) {
        p.put(prop, s);
      }
    }
  }

  /** Set java.library.path.<p>
   *
   * I wish I knew where to check in the source code to confirm that this
   * is, in fact, the process we actually follow.  I do not understand this
   * code.  I do not understand why we are adding something to
   * java.library.path.  --Steve Augart, 3/23/2004 XXX
   */
  private static void insertLibraryPath(Properties p) {
    String jlp = CommandLineArgs.getEnvironmentArg("java.library.path");
    String snp = CommandLineArgs.getEnvironmentArg("java.home");
    if (jlp == null) jlp = ".";
    p.put("java.library.path", snp + p.get("path.separator") +jlp);
  }


  /** Override the default SystemProperties code; insert the command-line
   * arguments.
   * <p>
   * The following are set by the "runrvm" script before we go into the C
   * boot image runner, by passing them as command-line args with the -D flag:
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
   */
  public static void postInit(Properties properties) {
  }
}
