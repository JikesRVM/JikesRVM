/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$


package com.ibm.JikesRVM.librarySupport;
import java.io.*; // includes java.io.JikesRVMSupport
import java.io.*;
import java.io.JikesRVMSupport;
import com.ibm.JikesRVM.VM_FileSystem;
import com.ibm.JikesRVM.VM_Callbacks;

/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library file operations.
 *
 * @author Stephen Fink
 */
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

  // options for access()
  public static final int ACCESS_F_OK	= 00;
  public static final int ACCESS_R_OK	= 04;
  public static final int ACCESS_W_OK	= 02;
  public static final int ACCESS_X_OK	= 01;
  
  private static final String pathSeparator =
    System.getProperty("path.separator") == null ? ":" :
    System.getProperty("path.separator");
  private static final String separator =
    System.getProperty("file.separator") == null ? "/" :
    System.getProperty("file.separator");
  private static final char separatorChar = separator.charAt(0);
  private static final char pathSeparatorChar = pathSeparator.charAt(0);

  /**
   * Return the value for java.io.File.pathSeparator
   */
  public static String getPathSeparator() { return pathSeparator; }
  /**
   * Return the value for java.io.File.separator
   */
  public static String getSeparator() { return separator; }
  /**
   * Return the value for java.io.File.separatorChar
   */
  public static char getSeparatorChar() { return separatorChar; }
  /**
   * Return the value for java.io.File.pathSeparatorChar
   */
  public static char getPathSeparatorChar() { return pathSeparatorChar; }


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

//-#if RVM_WITH_GNU_CLASSPATH
//-#else
  /**
   * Close a FileInputStream.
   *
   * @exception 	java.io.IOException	If an error occurs attempting to close this FileInputStream.
   */
    public static void close(FileInputStream f) throws IOException {    
    FileDescriptor fd = f.fd;
    f.fd = null;
    if (fd != null) {
      JikesRVMSupport.deref(fd);
      if (JikesRVMSupport.dead(fd)) {
        int rc = FileSupport.close(JikesRVMSupport.getFd(fd));
        if (rc == -2)
          // !!TODO: what additional details to supply?
          throw new IOException(); 
      }
    }
    }

  /**
   * Close a FileOutputStream.  This implementation closes the underlying OS resources allocated
   * to represent this stream.
   *
   * @exception 	java.io.IOException	If an error occurs attempting to close this FileOutputStream.
   */
    
  public static void close(FileOutputStream f) throws IOException {
    FileDescriptor fd = f.fd;
    f.fd = null;
    if (fd != null) {
      JikesRVMSupport.deref(fd);
      if (JikesRVMSupport.dead(fd)) {
        int rc = FileSupport.close(JikesRVMSupport.getFd(fd));
        if (rc == -2)
          // !!TODO: what additional details to supply?
          throw new IOException(); 
      } else {
        FileSupport.sync(JikesRVMSupport.getFd(fd));
      }
    }  
    }

  /**
   * Frees any resources allocated to represent a FileOutputStream before it is garbage collected.
   *
   * @exception 	java.io.IOException	If an error occurs attempting to finalize this FileOutputStream.
   */
  public static void finalize(FileOutputStream f) throws IOException {
    if (f.fd != null) {
      int fd = JikesRVMSupport.getFd(f.fd);

      // in UNIX, fds 0, 1 and 2 tend to be special
      if (fd > 2) 
        close(f);
      else
        FileSupport.sync(fd);
    }
  }

  /**
   * Writes <code>count</code> <code>bytes</code> from the byte array
   * <code>buffer</code> starting at <code>offset</code> to this
   * FileOutputStream.
   *
   * @param		buffer		the buffer to be written
   * @param		offset		offset in buffer to get bytes
   * @param		count		number of bytes in buffer to write
   *
   * @throws 	java.io.IOException	If an error occurs attempting to write to this FileOutputStream.
   * @throws	java.lang.IndexOutOfBoundsException If offset or count are outside of bounds.
   * @throws	java.lang.NullPointerException If buffer is <code>null</code>.
   */
    
  public static void write(FileOutputStream f, byte[] buffer, int offset, int count) throws IOException {
    int fd = JikesRVMSupport.getFd(f.fd);
    int rc = FileSupport.writeBytes(fd, buffer, offset, count);
    if (rc != count)
      throw new IOException();
      }



    //-#endif
  /**
   * Get file status.
   * @param fileName file name
   * @param kind     kind of info desired (one of STAT_XXX, above)
   * @return desired info (-1 -> error)
   */ 
//-#if RVM_WITH_GNU_CLASSPATH
  public static int stat(String fileName, int kind) {
//-#else
  private static int stat(String fileName, int kind) {
//-#endif
    return VM_FileSystem.stat(fileName,kind);
  }

  /**
   * Get user's perms for a file.
   * @param fileName file name
   * @param kind     kind of access perm(s) to check for (ACCESS_W_OK,...)
   * @return 0 if access ok (-1 -> error)
   */ 
//-#if RVM_WITH_GNU_CLASSPATH
  public static int access(String fileName, int kind) {
//-#else
  private static int access(String fileName, int kind) {
//-#endif
    return VM_FileSystem.access(fileName, kind);
  }

  /**
   * Answers a boolean indicating whether or not the current context i
   * allowed to read this File.
   *
   * @return		<code>true</code> if this File can be read, <code>false</code> otherwise.
   *
   * @see			java.lang.SecurityManager#checkRead
   */
  public static boolean canRead(File f) {
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkRead(f.getPath());
      return access(f.getAbsolutePath(), ACCESS_R_OK) == 0;
  }


  /**
   * Answers a boolean indicating whether or not the current context is
   * allowed to write to this File.
   *
   * @return		<code>true</code> if this File can be written, <code>false</code> otherwise.
   *
   * @see			java.lang.SecurityManager#checkWrite
   */
  public static boolean canWrite(File f) {
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkWrite(f.getPath());
      return access(f.getAbsolutePath(), ACCESS_W_OK) == 0;
  }

  /**
   * Answers a boolean indicating whether or not this File can be found on the
   * underlying file system.
   *
   * @return		<code>true</code> if this File exists, <code>false</code> otherwise.
   *
   * @see			java.lang.SecurityManager#checkRead
   */
  public static boolean exists(File f) {
    if (f.getPath().length() == 0) return false;
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkRead(f.getPath());
      return access(f.getAbsolutePath(), ACCESS_F_OK) == 0;
  }

  /**
   * Answers if this File represents a <em>directory</em> on the underlying file system.
   *
   * @return		<code>true</code> if this File is a directory, <code>false</code> otherwise.
   *
   * @see			java.lang.SecurityManager#checkRead
   */
  public static boolean isDirectory(File f) {
    if (f.getPath().length() == 0) return false;
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkRead(f.getPath());
    return stat(f.getAbsolutePath(), STAT_IS_DIRECTORY) == 1;
  }

  /**
   * Answers if this File is an absolute pathname. Whether a pathname is absolute
   * is platform specific.  On UNIX it is if the path starts with the character '/',
   * on Windows it is absolute if either it starts with '\', '/', '\\' (to represent
   * a file server), or a letter followed by a colon.
   *
   * @return		<code>true</code> if this File is absolute, <code>false</code> otherwise.
   */
  public static boolean isAbsolute(File f) {
    final String path = f.getPath();
    return path == null ? false : path.charAt(0) == '/';
  }


  /**
   * Answers if this File represents a <em>file</em> on the underlying file system.
   *
   * @return		<code>true</code> if this File is a file, <code>false</code> otherwise.
   *
   * @see			java.lang.SecurityManager#checkRead
   */
  public static boolean isFile(File f) {
    if (f.getPath().length() == 0) return false;
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkRead(f.getPath());
    return stat(f.getAbsolutePath(), STAT_IS_FILE) == 1;
  }

  /**
   * Answers the time this File was last modified.
   *
   * @return		the time this File was last modified.
   *
   * @see			java.lang.SecurityManager#checkRead
   */
  public static long lastModified(File f) {
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkRead(f.getPath());
    return stat(f.getAbsolutePath(), STAT_LAST_MODIFIED);
  }

  /**
   * Set the last modified time for the File.
   *
   * @param file the file
   * @param time modification time in milliseconds past the epoch
   *
   * @return true if succeeded, false otherwise
   */
  public static boolean setLastModified(File file, long time) {
    if (time < 0)
      throw new IllegalArgumentException("negative modification time");
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkWrite(file.getPath());
    return VM_FileSystem.setLastModified(file.getPath(), time);
  }

  /**
   * Answers the length of this File in bytes.
   *
   * @return		the number of bytes in the file.
   *
   * @see			java.lang.SecurityManager#checkRead
   */
  public static long length(File f) {
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkRead(f.getPath());
    int rc = stat(f.getAbsolutePath(), STAT_LENGTH);
    if (rc < 0)
      return 0;
    return rc;
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
   * Answers an array of Strings representing the file names in the
   * directory represented by this File.  If this File is not a directory
   * the result is <code>null</code>.
   * <p>
   * The entries <code>.</code> and <code>..</code> representing current directory
   * and parent directory are not returned as part of the list.
   *
   * @return		an array of Strings or <code>null</code>.
   *
   * @see			#isDirectory
   * @see			java.lang.SecurityManager#checkRead
   */
  public static String[] list(File f) {
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      security.checkRead(f.getPath());
    if (!isDirectory(f))
      return null;
    return list(f.getAbsolutePath());
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
   * @return number of bytes read (-2: error)
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
   * @return number of bytes written (-2: error)
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


//-#if RVM_WITH_GNU_CLASSPATH
//-#else
  /**
   * Close a RandomAccessFile.
   *
   * @exception 	java.io.IOException	If an error occurs attempting to close this RandomAccessFile.
   */

  public static void close(RandomAccessFile f) throws IOException {
    FileDescriptor fd = f.fd;
    f.fd = null;
    if (fd != null) {
      JikesRVMSupport.deref(fd);
      if (JikesRVMSupport.dead(fd)) {
        int rc = close(JikesRVMSupport.getFd(fd));
        if (rc == -2)
          // !!TODO: what additional details to supply?
          throw new IOException(); 
      } else {
        sync( JikesRVMSupport.getFd(fd) );
      }
    }  
  }

  /**
   * Answers the current position within a RandomAccessFile.  All reads and writes
   * take place at the current file pointer position.
   *
   * @return		the current file pointer position.
   *
   * @exception 	java.io.IOException	If an error occurs attempting to get the file pointer position
   *									of this RandomAccessFile.
   */

  public static long getFilePointer(RandomAccessFile f) throws IOException {
    int curpos = seek(JikesRVMSupport.getFd(f.fd), 0, SEEK_CUR);
    if (curpos == -1)
      throw new IOException();
    return curpos;
  }

  /**
   * Answers the current length of a RandomAccessFile in bytes.
   *
   * @return		the current file length in bytes.
   *
   * @exception 	java.io.IOException	If an error occurs attempting to get the file length
   *									of this RandomAccessFile.
   */
  public static long length(RandomAccessFile f) throws IOException {
    int fd = JikesRVMSupport.getFd(f.fd);

    //!!TODO - probably an fstat() would be faster than three lseeks()...
    //!!TODO - also, this computation is not thread safe if
    //         multiple threads do read/write/seek on file simultaneously
    int oldpos = seek(fd, 0,      FileSupport.SEEK_CUR);
    int endpos = seek(fd, 0,      FileSupport.SEEK_END);
    int newpos = seek(fd, oldpos, FileSupport.SEEK_SET);

    if (oldpos == -1 || endpos == -1 || newpos != oldpos)
      throw new IOException();

    return endpos;
  }

  /**
   * Reads a single byte from a RandomAccessFile and returns the result as
   * an int.  The low-order byte is returned or -1 of the end of file was
   * encountered.
   *
   * @return 		the byte read or -1 if end of file.
   *
   * @exception 	java.io.IOException	If an error occurs attempting to read from this RandomAccessFile.
   *
   * @see 		#write(RandomAccessFile,byte[], int, int)
   * @see 		#write(RandomAccessFile,int)
   */

  public static int read(RandomAccessFile f) throws IOException {
    if (f.fd != null) {
      int rc = readByte(JikesRVMSupport.getFd(f.fd));
      if (rc < -1)	throw new IOException();
      else		return rc;
    }
    throw new IOException();
    }

  /**
   * Seeks to the position <code>pos</code> in a RandomAccessFile.  All read/write/skip
   * methods sent will be relative to <code>pos</code>.
   *
   * @param 		pos			the desired file pointer position
   *
   * @exception 	java.io.IOException 	If the stream is already closed or another IOException occurs.
   */
    
  public static void seek(RandomAccessFile f, long pos) throws IOException {
    if (seek(JikesRVMSupport.getFd(f.fd), (int)pos, SEEK_SET) == -1)
      throw new IOException();
  }
    
  /**
   * Writes <code>count</code> bytes from the byte array
   * <code>buffer</code> starting at <code>offset</code> to this
   * RandomAccessFile starting at the current file pointer..
   *
   * @param		buffer		the bytes to be written
   * @param		offset		offset in buffer to get bytes
   * @param		count		number of bytes in buffer to write
   *
   * @exception 	java.io.IOException	If an error occurs attempting to write to this RandomAccessFile.
   *
   * @see 		#read(RandomAccessFile)
   *
   * @exception	java.lang.ArrayIndexOutOfBoundsException If offset or count are outside of bounds.
   */
    
  public static void write(RandomAccessFile f, byte[] buffer, int offset, int count) throws IOException {
    if (f.fd != null) {
      int rc = writeBytes(JikesRVMSupport.getFd(f.fd), buffer, offset, count);
      if (rc != count) throw new IOException();
      return;
    }
    else throw new IOException();
    }

  /**
   * Writes the specified byte <code>oneByte</code> to this RandomAccessFile
   * starting at the current file pointer. Only the low order byte of
   * <code>oneByte</code> is written.
   *
   * @param		oneByte		the byte to be written
   *
   * @exception 	java.io.IOException	If an error occurs attempting to write to this RandomAccessFile.
   *
   * @see 		#read(RandomAccessFile)
   */
    
  public static void write(RandomAccessFile f, int oneByte) throws IOException {
    if (f.fd != null)
    {
      if (FileSupport.writeByte(JikesRVMSupport.getFd(f.fd), oneByte) == -1)
        throw new IOException();
      return;
    }
    else throw new IOException();
  }

  public static void deleteOnExit(final String fileName) {
      VM_Callbacks.addExitMonitor(new VM_Callbacks.ExitMonitor() {
	public void notifyExit(int code) {
	  VM_FileSystem.delete( fileName );
	}
     });
  }
  //-#endif

  /**
   * Registration hook for new FileDescriptor objects.
   * The library should call this from FileDescriptor's constructor(s).
   *
   * @param fd the file descriptor object
   * @param shared true if the file descriptor might be shared with
   *    another process
   */
  public static void onCreateFileDescriptor(FileDescriptor fd, boolean shared) {
    VM_FileSystem.onCreateFileDescriptor(JikesRVMSupport.getFd(fd), shared);
  }
}
