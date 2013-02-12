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
package org.jikesrvm.tools.bootImageWriter;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Queue;

import org.jikesrvm.classloader.TypeReference;
import org.vmmagic.unboxed.Address;

/**
 * Correlate objects in host JDK with corresponding objects in target RVM
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
  static final ArrayList<Entry> objectIdToEntry;

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
    nullEntry = new Entry(newId(), null);
    nullEntry.imageAddress = Address.zero();
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
    @Override
    public int hashCode() { return jdkObject.hashCode(); }

    /**
     * Indicates whether some other key is "equal to" this one.
     * @param that the object with which to compare
     * @return {@code true} if this key is the same as the that argument;
     *         {@code false} otherwise
     */
    @Override
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

    public static class LinkInfo {
      final Address addressToFixup;
      final boolean objField;
      final boolean root;
      final String rvmFieldName;
      final TypeReference rvmFieldType;
      final Object parent;
      LinkInfo(Address a, boolean o, boolean r, String rvmFieldName, TypeReference rvmFieldType, Object parent) {
        addressToFixup = a;
        objField = o;
        root = r;
        this.rvmFieldName = rvmFieldName;
        this.rvmFieldType = rvmFieldType;
        this.parent = parent;
      }
    }

    /**
     * A list of addresses where when this value is not OBJECT_NOT_ALLOCATED
     */
    private Queue<LinkInfo> linkingAddresses;

    private boolean pendingEntry;

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
    public Entry(Address objectId, Object jdkObject) {
      this.objectId     = objectId;
      this.jdkObject    = jdkObject;
      this.imageAddress = OBJECT_NOT_ALLOCATED;
    }

    boolean isPendingEntry() {
      return pendingEntry;
    }

    void setPendingEntry() {
      pendingEntry = true;
    }

    void clearPendingEntry() {
      pendingEntry = false;
    }

    /**
     * Store linking information for an unresolved field
     * @param toBeLinked the address that needs filling in when the field is resolved
     * @param objField true if this word is an object field (as opposed to a static, or tib, or some other metadata)
     * @param root Does this slot contain a possible reference into the heap? (objField must also be true)
     * @param rvmFieldName name of the field
     * @param rvmFieldType type of the field
     * @param parent the object containing the field
     */
    synchronized void addLinkingAddress(Address toBeLinked, boolean objField, boolean root, String rvmFieldName, TypeReference rvmFieldType, Object parent) {
      if (linkingAddresses == null) {
        linkingAddresses = new LinkedList<LinkInfo>();
      }
      linkingAddresses.add(new LinkInfo(toBeLinked, objField, root, rvmFieldName, rvmFieldType, parent));
    }

    /**
     * @return a queued linking address
     */
    synchronized LinkInfo removeLinkingAddress() {
      if (linkingAddresses == null) {
        return null;
      } else {
        if (linkingAddresses.peek() != null) {
          return linkingAddresses.remove();
        } else {
          return null;
        }
      }
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
   * Find or create map entry for a JDK/RVM object pair.
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
        entry = new Entry(newId(), jdkObject);
        keyToEntry.put(key, entry);
        objectIdToEntry.add(entry);
      }
      return entry;
    }
  }

  /**
   * Get JDK object corresponding to an object id.
   * @param objectId object id
   * @return JDK object
   */
  public static Object getObject(int objectId) {
    return objectIdToEntry.get(objectId).jdkObject;
  }

  /**
   * Get bootimage offset of an object.
   * @param jdkObject JDK object
   * @return offset of corresponding RVM object within bootimage, in bytes
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

