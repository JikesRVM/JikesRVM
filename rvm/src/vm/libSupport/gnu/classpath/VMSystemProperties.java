/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2005
 *
 * $Id$
 */
package gnu.classpath;

import java.util.Properties;
import com.ibm.JikesRVM.VM;     // for VM.sysWrite()
import com.ibm.JikesRVM.VM_CommandLineArgs;
import com.ibm.JikesRVM.VM_Configuration;

import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.classloader.VM_BootstrapClassLoader;

/**
 * Jikes RVM implementation of GNU Classpath's gnu.classpath.VMSystemProperties.
 * <P>
 * Library support interface of Jikes RVM.  See the Javadoc documentation for
 * GNU Classpath's reference implementation of VMSystemProperties -- for
 * copyright reasons, we cannot duplicate it here.
 *
 * @author Steven Augart
 * @date   7 January 2005 -- 9 January 2005
 */

public class VMSystemProperties {
  /** VMRuntime.insertSystemProperties is used by Classpath versions through
   *  Classpath  0.12.   Starting with Classpath 0.13, we use
   *  gnu.classpath.VMSystemProperties.preInit and
   *  gnu.classpath.VMSystemProperties.postInit.
   *
   */
  public static void preInit(Properties p) {
    p.put("java.version", "1.4.2"); /* This is a lie, of course -- we don't 
                                       really support all 1.4 features, such
                                       as assertions.  However, it is a  
                                       necessary lie, since Eclipse 3.0
                                       explicitly tests java.version and
                                       insists upon at least 1.4.1 to run. */
    p.put("java.vendor", "Jikes RVM Project");
    p.put("java.vm.vendor", "Jikes RVM Project");
    p.put("java.vendor.url", "http://oss.software.ibm.com");
    
    p.put("java.specification.name", "Java Platform API Specification");
    p.put("java.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.specification.version", "1.4");

    p.put("java.vm.specification.name", "Java Virtual Machine Specification");
    p.put("java.vm.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.vm.specification.version", "1.0");

    /* 48.0 brings us through Java version 1.4.  Java 1.5 bumps the class file
       format up to 49.0.  The changes for version 49.0 are described at
       <http://java.sun.com/docs/books/vmspec/2nd-edition/jvms-proposed-changes.html>;
       I don't have a reference to version 49.0 handy (yet).  
       --augart, 13 Sept 2004 */
    p.put("java.class.version", "48.0"); 

    p.put("file.separator", "/");
    p.put("path.separator", ":");
    p.put("line.separator", "\n");
        
    p.put("java.compiler", "JikesRVM");
    p.put("java.vm.version", "1.4.2");
    p.put("java.vm.name", "JikesRVM");
    p.put("file.encoding", "8859_1");
    p.put("java.io.tmpdir", "/tmp");
    p.put("gnu.cpu.endian", VM_Configuration.LittleEndian ? "little" : "big");
    

    String s;
    s = VM_BootstrapClassLoader.getBootstrapRepositories();
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
       VM_CommandLineArgs.lateProcessCommandLineArguments().  That won't occur
       until the VM is fully booted; too late for java.util.TimeZone, which
       reads this value when it runs its initializer.
    */
    s = VM_CommandLineArgs.getEnvironmentArg("user.timezone");
    s = (s == null ) ? "" : s;  // Maybe it's silly to set it to the empty
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
    s = VM_CommandLineArgs.getEnvironmentArg("java.ext.dirs");
    if (s == null) {
      s = "";
    } else {
      VM.sysWrite("Jikes RVM: Warning: You have explicitly set java.ext.dirs; that will not do anything under Jikes RVM");
    }
    
    s = (s == null ) ? "" : s;
    p.put("java.ext.dirs", s);
    

    /* We also set java.class.path in setApplicationRepositories().
     *  We'll treat setting the java.class.path property as essentially
     * equivalent to using the -classpath argument. */
    s = VM_CommandLineArgs.getEnvironmentArg("java.class.path");
    if (s != null) {
      p.put("java.class.path", s);
      VM_ClassLoader.stashApplicationRepositories(s);
    } else {
      p.put("java.class.path", VM_ClassLoader.getApplicationRepositories());
    }

    /* Now the rest of the special ones that we set on the command line.   Do
     * this just in case later revisions of GNU Classpath start to require
     * some of them in the boot process; otherwise, we could wait for them to
     * be set in VM_CommandLineArgs.lateProcessCommandLineArguments() */
    final String[] clProps = new String[] {"os.name", "os.arch", "os.version", "user.name", "user.home", "user.dir", "gnu.classpath.vm.shortname", "gnu.classpath.home.url", "java.home", "rvm.root", "rvm.build"};
    
    for (int i = 0; i < clProps.length; ++i ) {
      final String prop = clProps[i];
      s = VM_CommandLineArgs.getEnvironmentArg(prop);
      if (s != null) {
        p.put(prop, s);
      }
    }

    /* Tell GNU Classpath that we need the portable-native-sync
     * implementation.   This has been in Classpath since at least Classpath
     * 0.10. */
    p.put("gnu.classpath.awt.gtk.portable.native.sync", "true");
  }

  /** Set java.library.path.
   *
   * I wish I knew where to check in the source code to confirm that this
   * is, in fact, the process we actually follow.  I do not understand this
   * code.  I do not understand why we are adding something to
   * java.library.path.  --Steve Augart, 3/23/2004 XXX
   */
  private static void insertLibraryPath(Properties p) {
    String jlp = VM_CommandLineArgs.getEnvironmentArg("java.library.path");
    String snp = VM_CommandLineArgs.getEnvironmentArg("rvm.build");
    if (jlp == null) jlp = ".";
    p.put("java.library.path", snp + p.get("path.separator") +jlp);
  }


  /** Override the default SystemProperties code; insert the command-line
   * arguments.
   *
   * The following are set by the "runrvm" script before we go into the C
   * boot image runner, by passing them as command-line args with the -D flag:
   *
   * os.name, os.arch, os.version
   * user.name, user.home, user.dir
   * gnu.classpath.vm.shortname, gnu.classpath.home.url, 
   * java.home,
   * rvm.root, rvm.build
   *
   * We can look at them here via VM_CommandLineArgs.getEnvironmentArg().
   *
   * They will be automatically set for us by
   * VM_CommandLineArgs.lateProcessCommandLineArguments() if we do not handle
   * them here.  That won't occur until the VM is fully booted.  That's too
   * late for some classes, such as java.util.TimeZone, which will already be
   * initialized.
   *
   * In any case, this function isn't used in Jikes RVM.  Our boot sequence
   * is already handling this OK.
   */
  public static void postInit(Properties properties) {
    
  }

  

}
