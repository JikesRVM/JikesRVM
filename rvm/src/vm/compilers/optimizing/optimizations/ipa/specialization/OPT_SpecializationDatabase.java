/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.*;
/**
 * Database to store multiple specialized versions for a given method.
 *
 * <p> The overall design is very similar to that of the
 * InvalidationDatabase (see OPT_InvalidationDatabase.java)
 * In this database, the key is the VM_Method object of the source method 
 * and the value is a method set. The method set is a list of
 * specialized versions of the method pointed by the key. Specialized
 * versions are represented by using the OPT_SpecializedMethod class.
 * There is no provision for removing/deleting method versions as classes 
 * are never unloaded and the ClassLoader.compiledMethods[] is never cleaned.
 *
 * @author Rajesh Bordawekar
 * @author Stephen Fink
 */

public final class OPT_SpecializationDatabase {

  /**
   * Drain the queue of methods waiting for specialized code
   * generation.
   */
  static void doDeferredSpecializations () {
    // prevent recursive entry to this method
    if (specializationInProgress)
      return;
    specializationInProgress = true;
    JDK2_Iterator methods = deferredMethods.iterator();
    while (methods.hasNext()) {
      OPT_SpecializedMethod m = (OPT_SpecializedMethod)methods.next();
      if (m.getCompiledMethod() == null) {
        m.compile();
        registerCompiledMethod(m);
      }
      deferredMethods.remove(m);
      // since we modified the set, reset the iterator.  
      // TODO: use a better abstraction 
      // (ModifiableSetIterator of some kind?)
      methods = deferredMethods.iterator();
    }
    specializationInProgress = false;
  }
  private static boolean specializationInProgress;
  private static JDK2_HashSet deferredMethods = new JDK2_HashSet();

  // write the new compiled method in the specialized method pool
  private static void registerCompiledMethod (OPT_SpecializedMethod m) {
    OPT_SpecializedMethodPool.registerCompiledMethod(m);
  }

  /**
   * Return an iteration of OPT_SpecializedMethods that represents
   * specialied compiled versions of the method pointed by VM_Method
   * @return null if no specialized versions
   */
  static JDK2_Iterator getSpecialVersions (VM_Method m) {
    MethodSet s = (MethodSet)specialVersionsHash.get(m);
    if (s == null) {
      return  null;
    } 
    else {
      return  s.iterator();
    }
  }

  /**
   * put your documentation comment here
   * @param m
   * @return 
   */
  static int getSpecialVersionCount (VM_Method m) {
    JDK2_Iterator versions = getSpecialVersions(m);
    int count = 0;
    if (versions != null) {
      while (versions.hasNext() && (versions.next() != null)) {
        count++;
      }
    }
    return  count;
  }

  /**
   * Record a new specialized method in this database.
   * Also remember that this method will need to be compiled later,
   * at the next call to <code> doDeferredSpecializations() </code>
   */
  static void registerSpecialVersion (OPT_SpecializedMethod spMethod) {
    VM_Method source = spMethod.getMethod();
    MethodSet s = findOrCreateMethodSet(specialVersionsHash, source);
    s.add(spMethod);
    deferredMethods.add(spMethod);
  }
  private static JDK2_HashMap specialVersionsHash = new JDK2_HashMap();

  /**
   * Look up the MethodSet corresponding to a given key in the database
   * If none found, create one.
   */
  private static MethodSet findOrCreateMethodSet (JDK2_HashMap hash, Object key) {
    MethodSet result = (MethodSet)hash.get(key);
    if (result == null) {
      result = new MethodSet(key);
      hash.put(key, result);
    }
    return  result;
  }

  /**
   * The following defines a set of methods that share a common "key"
   */
  static class MethodSet {
    Object key;

    /**
     * a set of OPT_SpecializedMethod
     */
    JDK2_HashSet methods = new JDK2_HashSet();

    MethodSet (Object key) {
      this.key = key;
    }

    void add (OPT_SpecializedMethod spMethod) {
      methods.add(spMethod);
    }

    public JDK2_Iterator iterator () {
      return  methods.iterator();
    }
  }
}



