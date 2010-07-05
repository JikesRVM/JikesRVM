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
package org.mmtk.harness.sanity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.mmtk.harness.vm.ObjectModel;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * An entry in the heap snapshot.
 */
public class HeapEntry implements Comparable<HeapEntry> {
  /* Immutable fields */
  /** A reference to the object */
  private final ObjectReference object;

  /** */
  private final int id;

  /* Mutable fields */
  /** */
  private boolean rootReachable = false;
  /** */
  private int refCount = 0;

  private final Set<ObjectReference> referrers = new HashSet<ObjectReference>();

  HeapEntry(ObjectReference object, int id) {
    this.object = object;
    this.id = id;
  }

  /**
   * Create an entry from an object reference - the constructor
   * inspects the object for any fields it wants to record.
   * @param object The object
   */
  HeapEntry(ObjectReference object) {
    this(object,ObjectModel.getId(object));
  }

  /** @return The ObjectReference */
  public ObjectReference getObject() {
    return object;
  }

  /** @return The object id */
  public int getId() {
    return id;
  }

  /** @return Is the object root reachable */
  public boolean isRootReachable() {
    return rootReachable;
  }

  /** @return the object reference count */
  public int getRefCount() {
    return refCount;
  }

  /** Increment the object reference count */
  public void incRefCount() { refCount++; }

  /** Set the object to be root reachable */
  public void setRootReachable() { this.rootReachable = true; }

  /**
   * Register an incoming pointer
   * @param referrer The object that points to this one
   *
   */
  public void addReferrer(ObjectReference referrer) {
    referrers.add(referrer);
  }

  /**  @return the set of objects that point to this one */
  public Set<ObjectReference> getReferrers() {
    return Collections.unmodifiableSet(referrers);
  }

  /** Eclipse-generated hashcode based on object and id */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + id;
    result = prime * result + ((object == null) ? 0 : object.hashCode());
    return result;
  }

  /** Eclipse-generated equals */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    HeapEntry other = (HeapEntry) obj;
    if (id != other.id)
      return false;
    if (!object.equals(other.object))
      return false;
    return true;
  }

  /**
   * @see Comparable#compareTo(Object)
   */
  @Override
  public int compareTo(HeapEntry o) {
    if (o == null)
      return -1;
    if (Integer.valueOf(id).compareTo(o.id) == 0) {
      Address address = object.toAddress();
      Address oAddress = o.object.toAddress();
      return address.LT(oAddress) ? -1 : address.EQ(oAddress) ? 0 : 1;
    }
    return 1;
  }

  /**
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    String result = String.format("Heap entry: id=%s, ",id,ObjectModel.getString(object));
    result += String.format("incoming pointers: %n");
    for (ObjectReference ref : getReferrers()) {
      result += String.format(" %s%n",ref);
    }
    return result;
  }
}
