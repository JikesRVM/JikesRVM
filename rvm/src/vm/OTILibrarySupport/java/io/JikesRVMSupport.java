/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package java.io;

/**
 * Interface for Jikes RVM to access hidden implementation
 * fields and methods of classes in the <code>java.io</code>
 * package.
 *
 * @author David Hovemeyer
 */
public class JikesRVMSupport {
  /**
   * Create a <code>FileDescriptor</code> object.
   *
   * @param fd the underlying OS file descriptor
   * @param shared true if the OS file descriptor may possibly be shared with
   *   other processes, false if not
   * @return the new <code>FileDescriptor</code> object
   */
  public static FileDescriptor createFileDescriptor(int fd, boolean shared) {
    return new FileDescriptor(fd, shared);
  }

  /**
   * Modify an existing <code>FileDescriptor</code> object to refer
   * to given OS file descriptor.
   *
   * @param fdObj the <code>FileDescriptor</code> object
   * @param fd the OS file descriptor
   */
  public static void setFd(FileDescriptor fdObj, int fd) {
    fdObj.fd = fd;
  }

  /**
   * Get the underlying OS file descriptor associated with given
   * <code>FileDescriptor<code> object.
   *
   * @param fdObj the <code>FileDescriptor</code> object
   */
  public static int getFd(FileDescriptor fdObj) {
    return fdObj.fd;
  }

  /**
   * Add a reference to given <code>FileDescriptor</code> object.
   * Increments the reference count.
   *
   * @param fdObj the <code>FileDescriptor</code> object
   * @return the same object
   */
  public static FileDescriptor ref(FileDescriptor fd) {
    return fd.ref();
  }

  /**
   * Remove a reference to given <code>FileDescriptor</code> object.
   * Decrements the reference count.
   *
   * @param fdObj the <code>FileDescriptor</code> object
   */
  public static void deref(FileDescriptor fd) {
    fd.deref();
  }

  /**
   * Determine whether or not the given <code>FileDescriptor</code> object
   * is dead (meaning that its reference count has become zero).
   *
   * @param fdObj the <code>FileDescriptor</code> object
   */
  public static boolean dead(FileDescriptor fd) {
    return fd.dead();
  }
}
