/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;

/**
 * This class holds the dependencies that define invalidation
 * requirements for the opt compiled methods.
 *
 * <p> Currently we only support 2 kinds of dependencies: 
 *   The set of compiled method id's that depend on a VM_Method 
 *   not being overridden.
 *   The set of compiled method id's that depend on a VM_Class 
 *   having no subclasses
 *
 * <p> TODO: tracking things by cmid is somewhat ugly, but doing otherwise 
 *   would require changes in VM_CompiledMethod.
 *   In particular, we would need to be able to construct a partial 
 *   VM_CompiledMethod early in compilation 
 *   and fill in its instruction array and compiler info at the end of 
 *   compilation.
 * 
 * <p> TODO: In the future, we should think about implementing a general 
 *       dependency mechanism.  
 *   See Chambers, Dean, Grove in ICSE-17 (1995) for one possible design 
 *   and pointers to related work.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
public final class OPT_InvalidationDatabase {

  ///////////////////// 
  // (1) Dependency on a particular VM_Method not being overridden. 
  /////////////////////
  /**
   * Return an iteration of CMID's (compiled method ids) 
   * that are dependent on the argument VM_Method not being overridden.
   * return null if no dependent methods.
   *
   * <p> NOTE: returns null instead of OPT_EmptyIterator.EMPTY as part of 
   * a delicate * dance to avoid recursive classloading. --dave.
   */
  public Iterator invalidatedByOverriddenMethod(VM_Method m) {
    MethodSet s = (MethodSet)nonOverriddenHash.get(m);
    return (s == null) ? null : s.iterator();
  }

  /**
   * Record that if a particular VM_Method method is ever overridden, then 
   * the VM_CompiledMethod encoded by the cmid must be invalidated.
   */
  public void addNotOverriddenDependency(VM_Method source, 
					 int dependent_cmid) {
    MethodSet s = findOrCreateMethodSet(nonOverriddenHash, source);
    s.add(dependent_cmid);
  }

  /**
   * Delete a NotOverriddenDependency. 
   * No effect if the dependency doesn't exist..
   */
  public void removeNotOverriddenDependency(VM_Method source, 
					    int dependent_cmid) {
    MethodSet s = (MethodSet)nonOverriddenHash.get(source);
    if (s != null) {
      s.remove(dependent_cmid);
    }
  }

  /**
   * Delete all NotOverridden dependencies on the argument VM_Method
   */
  public void removeNotOverriddenDependency(VM_Method source) {
    nonOverriddenHash.remove(source);
  }

  ///////////////////// 
  // (2) Dependency on a particular VM_Class not having any subclasses.
  /////////////////////
  /**
   * Return an iteration of CMID's of VM_CompiledMethods that are dependent
   * on the argument VM_Class not having any subclasses.
   * return null if no dependent methods.
   *
   * <p> NOTE: returns null instead of OPT_EmptyIterator.EMPTY as part of 
   * a delicate dance to avoid recursive classloading. --dave.
   */
  public Iterator invalidatedBySubclass(VM_Class m) {
    MethodSet s = (MethodSet)noSubclassHash.get(m);
    return (s == null) ? null : s.iterator();
  }

  /**
   * Record that if a particular VM_Class ever has a subclass, then 
   * the VM_CompiledMethod encoded by the cmid must be invalidated.
   */
  public void addNoSubclassDependency (VM_Class source, int dependent_cmid) {
    MethodSet s = findOrCreateMethodSet(noSubclassHash, source);
    s.add(dependent_cmid);
  }

  /**
   * Delete a NoSubclassDependency. No effect if the dependency doesn't exist..
   */
  public void removeNoSubclassDependency (VM_Class source, int dependent_cmid) {
    MethodSet s = (MethodSet)noSubclassHash.get(source);
    if (s != null) {
      s.remove(dependent_cmid);
    }
  }

  /**
   * Delete all NoSubclass dependencies on the argument VM_Class
   */
  public void removeNoSubclassDependency (VM_Class source) {
    noSubclassHash.remove(source);
  }

  /**
   * A mapping from VM_Method to MethodSet: holds the set of methods which
   * depend on a particular method being "final"
   */
  private HashMap nonOverriddenHash = new HashMap();                                   
  /**
   * A mapping from VM_Class to MethodSet: holds the set of methods which
   * depend on a particular class being "final"
   */
  private HashMap noSubclassHash = new HashMap();                     

  /**
   * Look up the MethodSet corresponding to a given key in the database.
   * If none found, create one.
   */
  private MethodSet findOrCreateMethodSet (HashMap hash, Object key) {
    MethodSet result = (MethodSet)hash.get(key);
    if (result == null) {
      result = new MethodSet(key);
      hash.put(key, result);
    }
    return result;
  }

  /**
   * The following defines a set of methods that share a common "key"
   */
  static class MethodSet {
    Object key;
    /**
     * a set of cmids (Integers)
     */ 
    HashSet methods = new HashSet();  

    MethodSet (Object key) {
      this.key = key;
    }

    void add (int cmid) {
      methods.add(new Integer(cmid));
    }

    void remove (int cmid) {
      methods.remove(new Integer(cmid));
    }

    public Iterator iterator () {
      return methods.iterator();
    }
  }
}



