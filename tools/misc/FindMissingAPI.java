/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp., 2002.
 *
 * ==========
 */

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

/**
 * Compares the APIs of two collection of classes <new> and <old> -- we compare
 * the contents of <old> to <new>.  First, the classes found in <new> not found
 * in <old> are reported -- these are denoted by <diff>.  Then from those classes, 
 * the fields, methods, and constructors found in <diff> not in <old> are reported.
 */
public class FindMissingAPI {
  
  public static void main(String[] args) {
    try {
      new FindMissingAPI().realMain(args);
    } catch (Throwable t) { t.printStackTrace(); }
  }

  public void realMain(String[] args) throws Exception {
    parse(args);    

    // Create two classLoaders from the new and old classpaths
    ClassFinder oldClassFinder = createClassFinder(oldClasspath);
    ClassFinder newClassFinder = createClassFinder(newClasspath);

    // Find all the classes from each path
    Map<String,Class<?>> oldClasses = oldClassFinder.allClasses();
    Map<String,Class<?>> newClasses = newClassFinder.allClasses();

    out("Found " + oldClasses.size() + " old classes");
    out("Found " + newClasses.size() + " new classes");

    // Find the public classes in newClasses not in oldClasses
    List<Class<?>> foundClasses = new ArrayList<Class<?>>();
    List<Class<?>> missingClasses = new ArrayList<Class<?>>();
    for (Iterator<String> it = newClasses.keySet().iterator(); it.hasNext();) {
      String newClassName = (String)it.next();
      Class<?> newClass = newClasses.get(newClassName);
      Class<?> oldClass = oldClasses.get(newClass.getName());
      (oldClass == null ? missingClasses : foundClasses).add(newClass);
    }
    report(missingClasses, "classes");

    List<Field> missingFields = new ArrayList<Field>();
    List<Method> missingMethods = new ArrayList<Method>();
    @SuppressWarnings("unchecked")
    List<Constructor> missingConstructors = new ArrayList<Constructor>();
    for (Class<?> newClass : foundClasses) { 
      Class<?> oldClass = oldClasses.get(newClass.getName());
      try {
        compare(missingFields, newClass.getFields(), oldClass.getFields(), Field.class);
      } catch (Throwable t) {}
      try {
        compare(missingMethods, newClass.getMethods(), oldClass.getMethods(), Method.class);
      } catch (Throwable t) {}
      try {
        compare(missingConstructors, newClass.getConstructors(), newClass.getConstructors(), Constructor.class);
      } catch (Throwable t) {}
    }
    report(missingFields, "fields");
    report(missingMethods, "methods");
    report(missingConstructors, "constructors");
  }

  private void report(Collection<?> things, String kind) {
    out("There " + tobe(things.size()) + " " + kind + " in new clases not in old classes");
    if (verbose && things.size() > 0) {
      out("Dumping missing " + kind);
      for (Iterator<?> it = things.iterator(); it.hasNext();) out("  " + it.next());
    }
  }

  private String tobe(int num) {
    return (num == 1 ? "is" : "are") + " " + num;
  }

  private Method equalMethod(Class<?> klass) throws Exception {
    Method m = getClass().getDeclaredMethod("equal", new Class[]{klass, klass});
    m.setAccessible(true);
    return m;
  }

  private <T extends Member>void compare(Collection<T> missing, T[] newMembers, T[] oldMembers, Class<T> klass) 
    throws Exception {
    Method method = equalMethod(klass);
    for (int i = 0; i < newMembers.length; i++) {
      boolean found = false;
      T newMember = newMembers[i];
      for (int j = 0; j < oldMembers.length; j++) {
        if (((Boolean)method.invoke(this, new Object[]{newMember, oldMembers[j]})).booleanValue()) {
          found = true;
          break;
        }
      }
      if (!found) missing.add(newMember);
    }
  }
  
  @SuppressWarnings("unused") // Invoked by reflection
  private boolean equal(Field f0, Field f1) {
    return memberEqual(f0, f1);
  }

  private boolean memberEqual(Member m0, Member m1) {
    return m0 == null ? m1 == null : m0.getName().equals(m1.getName());
  }

  @SuppressWarnings("unused") // Invoked by reflection
  private boolean equal(Method m0, Method m1) {
    return memberEqual(m0, m1) && equal(m0.getParameterTypes(), m1.getParameterTypes());
  }

  @SuppressWarnings("unused") // Invoked by reflection
  private boolean equal(Constructor<?> c0, Constructor<?> c1) {
    return memberEqual(c0, c1) && equal(c0.getParameterTypes(), c1.getParameterTypes());
  }

  @SuppressWarnings("unused") // Invoked by reflection
  private boolean equal(Class<?>[] cs0, Class<?>[] cs1) {
    if (cs0.length != cs1.length) {
      return false;
    }
    for (int i = 0; i < cs0.length; i++) {
      if (!equals(cs0[i], cs1[i])) {
        return false;
      }
    }
    return true;
  }

  private boolean equals(Class<?> c0, Class<?> c1) {
    return c0.getName().equals(c1.getName());
  }

  private void out(Object msg) { System.out.println(msg); }

  private ClassFinder createClassFinder(String classpath) {
    List<String> pathList = new ArrayList<String>();
    for (StringTokenizer t = new StringTokenizer(classpath, File.pathSeparator, false);
         t.hasMoreTokens();) {
      String path = t.nextToken().trim();
      pathList.add(path);
    }
    String[] paths = (String[])pathList.toArray(new String[]{});
    return new ClassFinder(paths);
  }

  private String oldClasspath = "/home/palm/EBShadow/support/lib/rvmrt.jar";
  private String newClasspath = "/usr/local/j2sdk1.4.0_01/jre/lib/rt.jar";
  private boolean verbose;

  private void parse(String[] args) {
    for (int i = 0; i < args.length;) {
      String arg = args[i++];
      if (arg.equals("-old")) {
        oldClasspath = args[i++];
      } else if (arg.equals("-new")) {
        newClasspath = args[i++];
      } else if (arg.equals("-verbose")) {
        verbose = true;
      }
    }

    if (oldClasspath == null) {
      throw new IllegalArgumentException("need an oldClasspath");
    }
    if (newClasspath == null) {
      throw new IllegalArgumentException("need a newClasspath");
    }
  }

  private static class ClassFinder {
  
    private final List<File> paths = new ArrayList<File>();
    private final URLClassLoader loader;

    ClassFinder(String[] paths) {
      this(paths, false);
    }

    ClassFinder(String[] paths, boolean useSystemPaths) {

      // Add the user classpaths
      for (int i = 0; i < paths.length; i++) {
        this.paths.addAll(separate(paths[i]));
      }

      // Add all properties that could be classpaths
      if (useSystemPaths) {
        Properties props = System.getProperties();
        for (Object keyObj : props.keySet()) {
          String key = (String)keyObj;
          String val = props.getProperty(key);
          if (val != null && val.indexOf("class") != -1 && val.indexOf("path") != -1) {
            this.paths.addAll(separate(val));
          }
        }
      }
      
      // Create the loader to which we delegate
      loader = new URLClassLoader(urls(this.paths), null);
    }

    private Class<?> klass(File path, String className) {
      if (className.indexOf("$") != -1) {
        return null;
      }
      try {
        return loader.loadClass(className);
      } catch (Throwable e) {} //who cares
      return null;
    }

    private Collection<File> findClassFiles(File dir) {
      Collection<File> files = new HashSet<File>();
      List<File> q = new ArrayList<File>();
      q.add(dir);
      while (!q.isEmpty()) {
        File file = q.remove(0);
        if (file == null) {
          continue;
        } else if (file.isDirectory()) {
          File[] list = file.listFiles();
          for (int i = 0; i < list.length; i++) {
            q.add(list[i]);
          }
        } else if (file.getName().endsWith(".class")) {
          files.add(file);
        }
      }
      return files;
    }

    private void maybeAdd(Class<?> c, Map<String,Class<?>> classes) {
      if (c == null) return;
      // This can throw an IllegalAccess error if a superclass
      // is a punk
      try {
        if (!Modifier.isPublic(c.getModifiers())) return;
      } catch (Throwable t) {
        return;
      }
      classes.put(c.getName(), c);
    }

    public Map<String,Class<?>> allClasses() throws Exception {
      Map<String,Class<?>> classes = new HashMap<String,Class<?>>();
      for (File path : paths) {
        if (path.isDirectory()) {
          Collection<File> classFiles = findClassFiles(path);
          for (File classFile : classFiles) {
            String className = classFile.getName();
            int iclass = className.indexOf(".class");
            className = className.substring(0, iclass);
            for (File trav = classFile.getParentFile(); 
                 trav != null && !trav.equals(path); 
                 trav = trav.getParentFile()) {
              className = trav.getName() + "." + className;
            }
            Class<?> c = klass(path, className);
            maybeAdd(c, classes);
          }
        } else if (isJarFile(path)) {
          try {
            JarFile jarFile = new JarFile(path);
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
              JarEntry jarEntry = e.nextElement();
              String jarEntryName = jarEntry.getName();
              if (jarEntryName.endsWith(".class")) {
                String className = jarEntryName.replace('/', '.');
                int iclass = className.indexOf(".class");
                className = className.substring(0, iclass);
                Class<?> c = klass(path, className);
                maybeAdd(c, classes);
              }
            }
          } catch (Exception e) {
            handle(e,"Trouble with jar " + path);
          }
        }
      }
      return classes;
    }

    private void handle(Throwable t, String s) { 
      if (!"".equals(s)) System.err.println(t + ":" + s);
      if (t != null) t.printStackTrace();
    }
    private void handle(Throwable t) { handle(t, ""); }
  
    private final URL[] urls(List<File> paths) {
      List<URL> urls = new ArrayList<URL>();
      for (File f : paths) {
        try {
          urls.add(f.toURL());
        } catch (Exception e) {
          handle(e);
        }
      }
      return (URL[])urls.toArray(new URL[]{});
    }
  
    private final List<File> separate(String path) {
      final List<File> list = new ArrayList<File>();
      if (path == null) {
        return list;
      }
      for (StringTokenizer t = new StringTokenizer(path, File.pathSeparator);
           t.hasMoreTokens();) {
        list.add(new File(t.nextToken().trim()));
      }
      return list;
    }
 
    private static boolean isJarFile(File file) {
      String name = file.getName();
      return name.endsWith(".zip") || name.endsWith(".jar");
    }
  }
}
