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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.vm.VM;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * A table of live objects.  During GC we track where each object gets copied,
 * and ensure that only one collector ever copies an object.
 */
public final class ObjectTable {

  private static final String NEWLINE = System.getProperty("line.separator");

  private static int currentEpoch = 0;

  /**
   * TODO - increment this at an appropriate point
   */
  public static void newEpoch() {
    currentEpoch++;
  }

  /**
   * An object recording the copying of an object
   */
  static final class ObjectCopy {
    private ObjectCopy(int epoch, ObjectReference source, ObjectReference dest) {
      this.epoch = epoch;
      this.source = source;
      this.dest = dest;
    }
    private final int epoch;
    private final ObjectReference source;
    private final ObjectReference dest;

    @Override
    public String toString() {
      return String.format("copied from %s to %s in gc %d",source,dest,epoch);
    }

  }

  /**
   * An entry in the table, representing an object in the heap.
   */
  static final class Entry {
    Address start;
    int size;
    ObjectReference reference;
    private volatile ObjectReference copiedFrom;
    private final int birthEpoch = currentEpoch;
    private int deathEpoch = -1;
    private final int id;
    private final int site;

    private List<ObjectCopy> history = null;

    private Entry(ObjectReference reference, Address region, int size) {
      this.reference = reference;
      this.size = size;
      this.start = region;
      this.id = ObjectModel.getId(reference);
      this.site = ObjectModel.getSite(reference);
    }

    private void addHistory(ObjectReference dest) {
      if (history == null) {
        history = new ArrayList<ObjectCopy>();
      }
      history.add(new ObjectCopy(currentEpoch, reference, dest));
    }

    synchronized void copy(ObjectReference src, ObjectReference dest) {
      if (!this.reference.equals(src)) {
        throw new AssertionError("Attempt to copy "+src+" to "+dest+" twice!");
      }
      addHistory(dest);
      this.setCopiedFrom(reference);
      this.reference = dest;
      this.start = ObjectModel.getStartAddressFromObject(dest);
    }

    public void kill() {
      deathEpoch = currentEpoch;
    }

    public boolean isLive() {
      return deathEpoch < 0;
    }

    public void assertLive() {
      if (!isLive()) {
        throw new AssertionError("Object, "+toString()+" (now "+currentEpoch+")");

      }
    }

    public int getBirthEpoch() {
      return birthEpoch;
    }

    public void setCopiedFrom(ObjectReference copiedFrom) {
      this.copiedFrom = copiedFrom;
    }

    public ObjectReference getCopiedFrom() {
      return copiedFrom;
    }

    public int getId() {
      return id;
    }

    @Override
    public String toString() {
      return String.format("Object [id=%d, reference=%s, birthEpoch=%d, deathEpoch=%d, site=%d, copiedFrom=%s]",
          id, reference, birthEpoch,deathEpoch,site,copiedFrom);
    }

    public List<ObjectCopy> getHistory() {
      if (history == null)
        return Collections.emptyList();
      return history;
    }

    public String formatHistory() {
      StringBuilder result = new StringBuilder(toString());
      result.append(NEWLINE);
      for (ObjectCopy h : getHistory()) {
        result.append("  ");
        result.append(h.toString());
        result.append(NEWLINE);
      }
      return result.toString();
    }
  }

  private static final boolean VERBOSE = false;

  private final ConcurrentMap<ObjectReference,Entry> objects = new ConcurrentHashMap<ObjectReference,Entry>();

  private final Set<ObjectReference> copiedObjects = Collections.synchronizedSet(new HashSet<ObjectReference>());

  /**
   * Register allocation of an object
   * @param region The start of the allocated bytes
   * @param size The size of the region
   */
  public void alloc(Address region, int size) {
    // Can't hold a lock while doing anything that might block
    // in the deterministic scheduler.  Therefore we don't lock,
    // and rely on the fact that objects is a concurrent data structure
    ObjectReference object = VM.objectModel.getObjectFromStartAddress(region);
    objects.put(object,new Entry(object,region,size));
  }

  /**
   * Check whether an object reference is valid.  A reference is valid if it was
   * allocated at some point, or if it has been copied during the current GC.
   * <p>
   * This is imprecise, and will report some dead objects as being live, eg
   * in a generational collector.
   *
   * @param object The object
   * @return {@code true} if it's valid
   */
  public synchronized boolean isValid(ObjectReference object) {
    return object.isNull() || (objects.containsKey(object) && objects.get(object).isLive()) || copiedObjects.contains(object);
  }

  /**
   * Assert that an object reference is valid
   *
   * @param object The object to check
   */
  public void assertValid(ObjectReference object) {
    if (object.isNull()) return;
    if (objects.containsKey(object)) {
      objects.get(object).assertLive();
      return;
    }
    if (copiedObjects.contains(object))
      return;
    assert false : object + " is not a valid object reference";
  }

  /**
   * Adjust the object table for an object copy.
   *
   * Thread-safe because:
   * <ul>
   *  <li>objects is a concurrent map
   *  <li>Mutations of the entry are synchronized on the entry itself.
   *  <li>Attempts by two threads to copy an object simultaneously are
   *     errors that need to be detected.  This is detected by an
   *     assertion in the {@link Entry#copy(ObjectReference, ObjectReference)}
   *     method.
   * </ul>
   * @param src Source reference
   * @param dest Destination reference
   */
  public void copy(ObjectReference src, ObjectReference dest) {
    Entry entry = objects.get(src);
    if (entry == null) {
      entry = objects.get(dest);
      if (entry == null) {
        throw new AssertionError("Attempted to copy a nonexistent object, "+src);
      }
      throw new AssertionError("Attempt to copy object "+ObjectModel.getString(src)+" twice");
    }

    if (!entry.isLive()) {
      throw new AssertionError("Attempted to copy a dead object, "+src+", which died in collection "+entry.deathEpoch+" (now "+currentEpoch+")");
    }

    // entry.copy will detect data races and abort the second and subsequent
    // threads
    entry.copy(src, dest);

    // From here on, only one thread can be attempting to move
    // a given object, and the
    copiedObjects.add(src);
    objects.remove(src);
    objects.put(dest, entry);
  }

  /**
   * After the first pass of the sanity checker, we know exactly which objects are live.
   * Use this set to trim the set of valid objects after a full-heap collection.
   *
   * @param liveSet The known live objects
   */
  public void trimToLiveSet(Set<ObjectReference> liveSet) {
    int removed = 0;
    Iterator<ObjectReference> iterator = objects.keySet().iterator();
    while (iterator.hasNext()) {
      ObjectReference current = iterator.next();
      if (!liveSet.contains(current)) {
        Entry entry = objects.get(current);
        if (VERBOSE || ObjectModel.isWatched(current)) {
          Trace.printf(Item.SANITY,"Object death: %s",entry.formatHistory());
        }
        entry.kill();
      }
      removed++;
    }
    Trace.trace(Item.SANITY, "Trimmed %d dead objects from object table", removed);
  }

  /**
   * After a GC (major or minor) it's an error to refer to a copied object by its previous
   * location.
   */
  public synchronized void postGcCleanup() {
    copiedObjects.clear();
  }
}
