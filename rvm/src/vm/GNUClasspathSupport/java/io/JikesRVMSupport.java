/*
 * Copyright IBM Corp 2002
 */
package java.io;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
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
	return new FileDescriptor(fd);
    }

    /**
     * Modify an existing <code>FileDescriptor</code> object to refer
     * to given OS file descriptor.
     *
     * @param fdObj the <code>FileDescriptor</code> object
     * @param fd the OS file descriptor
     */
    public static void setFd(FileDescriptor fdObj, int fd) {
	fdObj.setNativeFD( fd );
    }

    /**
     * Get the underlying OS file descriptor associated with given
     * <code>FileDescriptor<code> object.
     *
     * @param fdObj the <code>FileDescriptor</code> object
     */
    public static int getFd(FileDescriptor fdObj) {
	return fdObj.getNativeFD();
    }
    
}

