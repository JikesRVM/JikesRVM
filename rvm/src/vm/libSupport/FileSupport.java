/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library file operations.
 *
 * @author Stephen Fink
 */

package com.ibm.JikesRVM.librarySupport;
import java.io.*;
import VM_FileSystem;

public class FileSupport {
  // options for open()
  public static final int OPEN_READ   = 0; // open for read/only  access
  public static final int OPEN_WRITE  = 1; // open for read/write access, create if doesn't already exist, 
                                           // truncate if already exists
  public static final int OPEN_MODIFY = 2; // open for read/write access, create if doesn't already exist
  public static final int OPEN_APPEND = 3; // open for read/write access, create if doesn't already exist, append writes

  // options for seek()
  public static final int SEEK_SET = 0;    // set i/o position to start of file plus "offset"
  public static final int SEEK_CUR = 1;    // set i/o position to current position plus "offset"
  public static final int SEEK_END = 2;    // set i/o position to end of file plus "offset"

  // options for stat()
  public static final int STAT_EXISTS        = 0;
  public static final int STAT_IS_FILE       = 1;
  public static final int STAT_IS_DIRECTORY  = 2;
  public static final int STAT_IS_READABLE   = 3;
  public static final int STAT_IS_WRITABLE   = 4;
  public static final int STAT_LAST_MODIFIED = 5;
  public static final int STAT_LENGTH        = 6;

  /**
   * Call <code>sync</code> on a file descriptor.
   *
   * @param fd The file descriptor
   * @return true if the operation succeeds, false otherwise
   */
  public static boolean sync(int fd) {
    return VM_FileSystem.sync(fd);
  }

  /**
   * Open a file.
   * @param fileName file name
   * @param how      access/creation mode (one of OPEN_XXX, above)
   * @return file descriptor (-1: not found or couldn't be created)
   */ 
  public static int open(String fileName, int how) {
    return VM_FileSystem.open(fileName,how);
  }

  /**
   * Close file.
   * @param fd file descriptor
   * @return 0: success
   *        -1: file not currently open
   *        -2: i/o error
   */ 
  public static int close(int fd) {
    return VM_FileSystem.close(fd);
  }

  /**
   * Get file status.
   * @param fileName file name
   * @param kind     kind of info desired (one of STAT_XXX, above)
   * @return desired info (-1 -> error)
   */ 
  public static int stat(String fileName, int kind) {
    return VM_FileSystem.stat(fileName,kind);
  }

  /**
   * How many bytes available to read from a socket or file?
   *
   * @param fd file descriptor
   */
  public static int bytesAvailable(int fd) {
    return VM_FileSystem.bytesAvailable(fd);
  }	   
  /**
   * List contents of a directory.
   * @param dirName directory name
   * @return names of files and subdirectories
   */ 
  public static String[] list(String dirName) {
    return VM_FileSystem.list(dirName);
  }

  /**
   * Delete a file.
   * @param fileName file name
   * @return true -- delete; false -- not delete
   */ 
  public static boolean delete(String fileName) {
    return VM_FileSystem.delete(fileName);
  }

  /**
   * Make a directory.
   * @param fileName file name
   * @return true -- created; false -- not created
   */ 
  public static boolean mkdir(String fileName) {
    return VM_FileSystem.mkdir(fileName);
  }

  /**
   * Rename a file
   * @param fromName from file name
   * @param toName to file name
   * @return true -- renamed; false -- not renamed
   */ 
  public static boolean rename(String fromName, String toName) {
    return VM_FileSystem.rename(fromName,toName);
  }

  /**
   * Change i/o position on file.
   * @param fd file descriptor
   * @param offset number of bytes by which to adjust position
   * @param whence how to interpret adjustment (one of SEEK_XXX, above)
   * @return new i/o position, as byte offset from start of file (-1: error)
   */ 
  public static int seek(int fd, int offset, int whence) {
    return VM_FileSystem.seek(fd,offset,whence);
  }

  /**
   * Read single byte from file.
   * @param fd file descriptor
   * @return byte that was read (< -1: i/o error, -1: eof, >= 0: data)
   */ 
  public static int readByte(int fd) {
    return VM_FileSystem.readByte(fd);
  }

  /**
   * Read multiple bytes from file or socket.
   * @param fd file or socket descriptor
   * @param buf buffer to be filled
   * @param off position in buffer
   * @param cnt number of bytes to read
   * @return number of bytes read (-2: error, -1: socket would have blocked)
   */ 
  public static int readBytes(int fd, byte buf[], int off, int cnt) {
    return VM_FileSystem.readBytes(fd,buf,off,cnt);
  }

  /**
   * Write multiple bytes to file or socket.
   * @param fd file or socket descriptor
   * @param buf buffer to be written
   * @param off position in buffer
   * @param cnt number of bytes to write
   * @return number of bytes written (-2: error, -1: socket would have blocked)
   */ 
  public static int writeBytes(int fd, byte buf[], int off, int cnt) {
    return VM_FileSystem.writeBytes(fd,buf,off,cnt);
  }

  /**
   * Write single byte to file.
   * @param fd file descriptor
   * @param b  byte to be written
   * @return  -1: i/o error
   */ 
  public static int writeByte(int fd, int b) {
    return VM_FileSystem.writeByte(fd,b);
  }
}
