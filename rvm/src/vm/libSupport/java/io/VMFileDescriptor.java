/*
 * (C) Copyright IBM Corp 2002
 */
// $Id$
package java.io;

import com.ibm.JikesRVM.VM_FileSystem;

/**
 * FileDescriptor provider interface, implemented for Jikes RVM.
 *
 * @author Julian Dolby
 */
final class VMFileDescriptor {

  /**
    *  This method is called in the class initializer to do any require
    * native library initialization.  It is also responsible for initializing
    * the in, out, and err variables.
    */
    static void nativeInit() {

    }

  /**
    * Opens the specified file in the specified mode.  This can be done
    * in one of the specified modes:
    * <ul>
    * <li>r - Read Only
    * <li>rw - Read / Write
    * <li>ra - Read / Write - append to end of file
    * <li>rws - Read / Write - synchronous writes of data/metadata
    * <li>rwd - Read / Write - synchronous writes of data.
    *
    * @param path Name of the file to open
    * @param mode Mode to open
    *
    * @return The resulting file descriptor for the opened file, or -1
    * on failure (exception also signaled).
    *
    * @exception IOException If an error occurs.
    */
  static long nativeOpen(String path, String mode) throws IOException {
      int fd;
      if ("r".equals(mode))
          fd = VM_FileSystem.open(path, VM_FileSystem.OPEN_READ);
      else if ("w".equals(mode))
          fd = VM_FileSystem.open(path, VM_FileSystem.OPEN_WRITE);
      else if ("rw".equals(mode)||"rwd".equals(mode)||"rws".equals(mode))
          fd = VM_FileSystem.open(path, VM_FileSystem.OPEN_MODIFY);
      else if ("ra".equals(mode)||"a".equals(mode))
          fd = VM_FileSystem.open(path, VM_FileSystem.OPEN_APPEND);
      else
          fd = -1;

      if (fd == -1)
          throw new IOException("Could not create/open file " + path);
      else {
          VM_FileSystem.onCreateFileDescriptor(fd, false);
          return fd;
      }
  }

  /**
    * Closes this specified file descriptor
    * 
    * @param fd The native file descriptor to close
    *
    * @return The return code of the native close command.
    *
    * @exception IOException If an error occurs 
    */    
  static long nativeClose(long fd) throws IOException {
      return VM_FileSystem.close( (int)fd );
  }
 
  /**
    * Writes a single byte to the file
    *
    * @param fd The native file descriptor to write to
    * @param b The byte to write, encoded in the low eight bits
    *
    * @return The return code of the native write command
    *
    * @exception IOException If an error occurs
    */
  static long nativeWriteByte(long fd, int b) throws IOException {
      return VM_FileSystem.writeByte((int)fd, b);
  }

  /**
    * Writes a byte buffer to the file
    *
    * @param fd The native file descriptor to write to
    * @param buf The byte buffer to write from
    * @param int The offset into the buffer to start writing from
    * @param len The number of bytes to write.
    *
    * @return The return code of the native write command
    *
    * @exception IOException If an error occurs
    */
  static long nativeWriteBuf(long fd, byte[] buf, int offset, int len)
      throws IOException
  {
      return VM_FileSystem.writeBytes((int)fd, buf, offset, len);
  }

  /**
    * Reads a single byte from the file
    *
    * @param fd The native file descriptor to read from
    *
    * @return The byte read, in the low eight bits on a long, or -1
    * if end of file
    *
    * @exception IOException If an error occurs
    */
  static int nativeReadByte(long fd) throws IOException {
      return VM_FileSystem.readByte((int)fd);
  }

  /**
    * Reads a buffer of  bytes from the file
    *
    * @param fd The native file descriptor to read from
    * @param buf The buffer to read bytes into
    * @param offset The offset into the buffer to start storing bytes
    * @param len The number of bytes to read.
    *
    * @return The number of bytes read, or -1 if end of file.
    *
    * @exception IOException If an error occurs
    */
  static int nativeReadBuf(long fd, byte[] buf, int offset, int len) 
      throws IOException
  {
      int bytesRead = VM_FileSystem.readBytes((int)fd, buf, offset, len);
      if (bytesRead == 0 && len != 0)
          return -1; // EOF
      else
          return bytesRead;
  }

  /**
    * Returns the number of bytes available for reading
    *
    * @param fd The native file descriptor
    *
    * @return The number of bytes available for reading
    *
    * @exception IOException If an error occurs
    */
  static int nativeAvailable(long fd) throws IOException {
      return VM_FileSystem.bytesAvailable( (int)fd );
  }

  /**
    * Method to do a "seek" operation on the file
    * 
    * @param fd The native file descriptor 
    * @param offset The number of bytes to seek
    * @param whence The position to seek from, either
    *    SET (0) for the beginning of the file, CUR (1) for the 
    *    current position or END (2) for the end position.
    * @param stopAtEof <code>true</code> to ensure that there is no
    *    seeking past the end of the file, <code>false</code> otherwise.
    *
    * @return The new file position, or -1 if end of file.
    *
    * @exception IOException If an error occurs
    */
  static long nativeSeek(long fd, long offset, int whence, boolean stopAtEof)
    throws IOException 
  {
      return VM_FileSystem.seek((int)fd, (int)offset, whence);
  }

  /**
    * Returns the current position of the file pointer in the file
    *
    * @param fd The native file descriptor
    *
    * @exception IOException If an error occurs
    */
  static long nativeGetFilePointer(long fd) throws IOException {
      return nativeSeek((int)fd, 0L, FileDescriptor.CUR, false);
  }

  /**
    * Returns the length of the file in bytes
    *
    * @param fd The native file descriptor
    *
    * @return The length of the file in bytes
    *
    * @exception IOException If an error occurs
    */
  static long nativeGetLength(long fd) throws IOException {
      return VM_FileSystem.length( (int)fd );
  }

  /**
    * Sets the length of the file to the specified number of bytes
    * This can result in truncation or extension.
    *
    * @param fd The native file descriptor  
    *
    * @param len The new length of the file
    *
    * @exception IOException If an error occurs
    */
  static void nativeSetLength(long fd, long len) throws IOException {
      VM_FileSystem.setLength( (int)fd, (int)len );
  }

  /**
    * Tests a file descriptor for validity
    *
    * @param fd The native file descriptor
    *
    * @return <code>true</code> if the fd is valid, <code>false</code> 
    * otherwise
    *
    * @exception IOException If an error occurs
    */
  static boolean nativeValid(long fd) throws IOException {
      return VM_FileSystem.isValidFD( (int)fd );
  }

  /**
    * Flushes any buffered contents to disk
    *
    * @param fd The native file descriptor
    *
    * @exception IOException If an error occurs
    */
  static void nativeSync(long fd) throws SyncFailedException {
      VM_FileSystem.sync( (int)fd );
  }

}


