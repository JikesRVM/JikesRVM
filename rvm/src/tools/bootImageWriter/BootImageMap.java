/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Hashtable;
import java.util.ArrayList;
import com.ibm.JikesRVM.*;

import org.vmmagic.unboxed.*;

/**
 * Correlate objects in host jdk with corresponding objects in target rvm
 * bootimage.
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public class BootImageMap extends BootImageWriterMessages
  implements BootImageWriterConstants
{
  /**
   * Key->Entry map
   */
  private static Hashtable keyToEntry;

  /**
   * objectId->Entry map
   */
  private static ArrayList objectIdToEntry;

  /**
   * Entry used to represent null object
   */
  private static Entry nullEntry;

  /**
   * Key for looking up map entry.
   */
  private static class Key {
    /**
     * JDK object.
     */
    Object jdkObject;

    /**
     * Constructor.
     * @param jdkObject the object to associate with the key
     */
    public Key(Object jdkObject) { this.jdkObject = jdkObject; }

    /**
     * Returns a hash code value for the key.
     * @return a hash code value for this key
     */
    public int hashCode() { return System.identityHashCode(jdkObject); }

    /**
     * Indicates whether some other key is "equal to" this one.
     * @param that the object with which to compare
     * @return true if this key is the same as the that argument;
     *         false otherwise
     */
    public boolean equals(Object that) {
      return (that instanceof Key) && jdkObject == ((Key)that).jdkObject;
    }
  }

  /**
   * Map entry associated with a key.
   */
  public static class Entry {
    /**
     * Unique id associated with a jdk/rvm object pair.
     */
    Address objectId;

    /**
     * JDK object.
     */
    Object jdkObject;

    /**
     * Offset of corresponding rvm object in bootimage, in bytes
     * (OBJECT_NOT_ALLOCATED --> hasn't been written to image yet)
     */
    Offset imageOffset;

    /**
     * Constructor.
     * @param objectId unique id
     * @param jdkObject the JDK object
     * @param imageOffset the offset of the object in the bootimage
     */
    public Entry(Address objectId, Object jdkObject, Offset imageOffset) {
      this.objectId    = objectId;
      this.jdkObject   = jdkObject;
      this.imageOffset = imageOffset;
    }
  }

  private static int idGenerator = 0;
  private static Address newId() {
      return Address.fromIntZeroExtend(idGenerator++);
  }

  /**
   * Prepare for use.
   */
  public static void init() {
    keyToEntry      = new Hashtable(5000);
    objectIdToEntry = new ArrayList(5000);
    // predefine "null" object
    objectIdToEntry.add(nullEntry = new Entry(newId(), null, Offset.zero()));
    // slot 0 reserved for "null" object entry
  }

  /**
   * Find or create map entry for a jdk/rvm object pair.
   * @param jdkObject JDK object
   * @return map entry for the given object
   */
  public static Entry findOrCreateEntry(Object jdkObject) {
    if (jdkObject == null)
      return nullEntry;

    Key   key   = new Key(jdkObject);
    Entry entry = (Entry) keyToEntry.get(key);
    if (entry == null) {
      entry = new Entry(newId(), jdkObject, OBJECT_NOT_ALLOCATED);
      keyToEntry.put(key, entry);
      objectIdToEntry.add(entry);
    }

    return entry;
  }

  /**
   * Get jdk object corresponding to an object id.
   * @param objectId object id
   * @return jdk object
   */
  public static Object getObject(int objectId) {
    return ((Entry) objectIdToEntry.get(objectId)).jdkObject;
  }

  /**
   * Get bootimage offset of an object.
   * @param jdkObject JDK object
   * @return offset of corresponding rvm object within bootimage, in bytes
   */
  public static Offset getImageOffset(Object jdkObject) {
    BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
    if (mapEntry.imageOffset.EQ(OBJECT_NOT_ALLOCATED))
      fail(jdkObject + " is not in bootimage");
    return mapEntry.imageOffset;
  }

  /**
   * Get bootimage address of an object.
   * @param bootImageAddress the starting address of the bootimage
   * @param jdkObject JDK object
   * @return address of corresponding rvm object within bootimage, in bytes
   *         or 0 if not present
   */
  public static Address getImageAddress(Address bootImageAddress, Object jdkObject) {
    BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
    if (mapEntry.imageOffset.EQ(OBJECT_NOT_ALLOCATED))
      return Address.zero();
    return bootImageAddress.add(mapEntry.imageOffset);
  }
}

