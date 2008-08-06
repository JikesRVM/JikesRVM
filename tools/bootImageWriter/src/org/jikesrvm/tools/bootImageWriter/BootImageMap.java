/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.tools.bootImageWriter;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import org.vmmagic.unboxed.Address;

/**
 * Correlate objects in host jdk with corresponding objects in target rvm
 * bootimage.
 */
public class BootImageMap extends BootImageWriterMessages
  implements BootImageWriterConstants {
  /**
   * Key->Entry map
   */
  private static final Hashtable<Key,Entry> keyToEntry;

  /**
   * objectId->Entry map
   */
  private static final ArrayList<Entry> objectIdToEntry;

  /**
   * Entry used to represent null object
   */
  private static final Entry nullEntry;

  /**
   * Unique ID value
   */
  private static int idGenerator;

  /**
   * Create unique ID number
   */
  private static Address newId() {
      return Address.fromIntZeroExtend(idGenerator++);
  }

  /**
   * Prepare for use.
   */
  static {
    keyToEntry      =  new Hashtable<Key,Entry>(5000);
    objectIdToEntry =  new ArrayList<Entry>(5000);
    idGenerator = 0;
    // predefine "null" object
    nullEntry = new Entry(newId(), null, Address.zero());
    // slot 0 reserved for "null" object entry
    objectIdToEntry.add(nullEntry);
  }

  /**
   * Key for looking up map entry.
   */
  private static class Key {
    /**
     * JDK object.
     */
    final Object jdkObject;

    /**
     * Constructor.
     * @param jdkObject the object to associate with the key
     */
    public Key(Object jdkObject) { this.jdkObject = jdkObject; }

    /**
     * Returns a hash code value for the key.
     * @return a hash code value for this key
     */
    public int hashCode() { return jdkObject.hashCode(); }

    /**
     * Indicates whether some other key is "equal to" this one.
     * @param that the object with which to compare
     * @return true if this key is the same as the that argument;
     *         false otherwise
     */
    public boolean equals(Object obj) {
      if (obj instanceof Key) {
        Key that = (Key)obj;
        if (jdkObject == that.jdkObject) {
          return true;
        } else if (jdkObject instanceof String && that.jdkObject instanceof String) {
          return jdkObject.equals(that.jdkObject);
        } else if (jdkObject instanceof Integer && that.jdkObject instanceof Integer) {
          return jdkObject.equals(that.jdkObject);
        }
      }
      return false;
    }
  }

  /**
   * Map entry associated with a key.
   */
  public static class Entry {
    /**
     * Unique id associated with a jdk/rvm object pair.
     */
    final Address objectId;

    /**
     * JDK object.
     */
    final Object jdkObject;

    /**
     * Address of corresponding rvm object in bootimage
     * (OBJECT_NOT_ALLOCATED --> hasn't been written to image yet)
     */
    Address imageAddress;

    /**
     * Do we need space in the written object for an identity hash code
     */
    private boolean hasIdentityHashCode;

    /**
     * An identity hash code for this entry
     */
    private int identityHashCode;

    /**
     * Constructor.
     * @param objectId unique id
     * @param jdkObject the JDK object
     * @param imageAddress the address of the object in the bootimage
     */
    public Entry(Address objectId, Object jdkObject, Address imageAddress) {
      this.objectId     = objectId;
      this.jdkObject    = jdkObject;
      this.imageAddress = imageAddress;
    }

    /**
     * Mark the entry as requiring an identity hash code
     */
    public void setHashed(int identityHashCode) {
      this.hasIdentityHashCode = true;
      this.identityHashCode = identityHashCode;
    }

    /**
     * @return Does this entry require an identity hash code
     */
    public boolean requiresIdentityHashCode() {
      return hasIdentityHashCode;
    }

    /**
     * @return the identity hash code associated with this entry
     */
    public int getIdentityHashCode() {
      return identityHashCode;
    }
  }

  /**
   * Find or create map entry for a jdk/rvm object pair.
   * @param jdkObject JDK object
   * @return map entry for the given object
   */
  public static Entry findOrCreateEntry(Object jdkObject) {
    if (jdkObject == null)
      return nullEntry;

    // Avoid duplicates of some known "safe" classes
    jdkObject = BootImageObjectAddressRemapper.getInstance().intern(jdkObject);

    synchronized (BootImageMap.class) {
      Key key   = new Key(jdkObject);
      Entry entry = keyToEntry.get(key);
      if (entry == null) {
        entry = new Entry(newId(), jdkObject, OBJECT_NOT_ALLOCATED);
        keyToEntry.put(key, entry);
        objectIdToEntry.add(entry);
      }
      return entry;
    }
  }

  /**
   * Get jdk object corresponding to an object id.
   * @param objectId object id
   * @return jdk object
   */
  public static Object getObject(int objectId) {
    return objectIdToEntry.get(objectId).jdkObject;
  }

  /**
   * Get bootimage offset of an object.
   * @param jdkObject JDK object
   * @return offset of corresponding rvm object within bootimage, in bytes
   */
  public static Address getImageAddress(Object jdkObject, boolean fatalIfNotFound) {
    BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
    if (mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
      if (fatalIfNotFound) {
        fail(jdkObject + " is not in bootimage");
      } else {
        return Address.zero();
      }
    }
    return mapEntry.imageAddress;
  }

  /**
   * @return enumeration of all the entries
   */
  public static Enumeration<BootImageMap.Entry> elements() {
    return keyToEntry.elements();
  }
}

