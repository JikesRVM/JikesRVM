/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Interface to filesystem of underlying operating system.
 * !!TODO: think about how i/o operations should interface with thread 
 * scheduler.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_FileSystem extends com.ibm.JikesRVM.librarySupport.FileSupport {

  /**
   * Get file status.
   * @param fileName file name
   * @param kind     kind of info desired (one of STAT_XXX, above)
   * @return desired info (-1 -> error)
   */ 
  public static int stat(String fileName, int kind) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    byte[] asciiName = new byte[fileName.length() + 1]; //+1 for null terminator
    fileName.getBytes(0, fileName.length(), asciiName, 0);

    // PIN(asciiName);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int rc = VM.sysCall2(bootRecord.sysStatIP, 
                         VM_Magic.objectAsAddress(asciiName).toInt(), kind);
    // UNPIN(asciiName);

    if (VM.TraceFileSystem) VM.sysWrite("VM_FileSystem.stat: name=" + fileName + " kind=" + kind + " rc=" + rc + "\n");
    return rc;
  }

  /**
   * Open a file.
   * @param fileName file name
   * @param how      access/creation mode (one of OPEN_XXX, above)
   * @return file descriptor (-1: not found or couldn't be created)
   */ 
  public static int open(String fileName, int how) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    byte[] asciiName = new byte[fileName.length() + 1]; //+1 for null terminator
    fileName.getBytes(0, fileName.length(), asciiName, 0);

    // PIN(asciiName);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int fd = VM.sysCall2(bootRecord.sysOpenIP, 
                         VM_Magic.objectAsAddress(asciiName).toInt(), how);
    // UNPIN(asciiName);

    if (VM.TraceFileSystem) VM.sysWrite("VM_FileSystem.open: name=" + fileName + " mode=" + how + " fd=" + fd + "\n");
    return fd;
  }

  /**
   * Read single byte from file.
   * @param fd file descriptor
   * @return byte that was read (< -1: i/o error, -1: eof, >= 0: data)
   */ 
  public static int readByte(int fd) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall1(bootRecord.sysReadByteIP, fd);
  }

  /**
   * Write single byte to file.
   * @param fd file descriptor
   * @param b  byte to be written
   * @return  -1: i/o error
   */ 
  public static int writeByte(int fd, int b) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall2(bootRecord.sysWriteByteIP, fd, b);
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
    if (off < 0)
      throw new IndexOutOfBoundsException();

    // trim request to fit array
    // note: this behavior is the way the JDK does it (as of version 1.1.3)
    // whereas the language spec says to throw IndexOutOfBounds exception...
    //
    if (off + cnt > buf.length)
      cnt = buf.length - off;

    // PIN(buf);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int rc = VM.sysCall3(bootRecord.sysReadBytesIP, fd, 
                         VM_Magic.objectAsAddress(buf).add(off).toInt(), cnt);
    // UNPIN(buf);

    return rc;
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
    if (off < 0)
      throw new IndexOutOfBoundsException();

    // trim request to fit array
    // note: this behavior is the way the JDK does it (as of version 1.1.3)
    // whereas the language spec says to throw IndexOutOfBounds exception...
    //
    if (off + cnt > buf.length)
      cnt = buf.length - off;

    // PIN(buf);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM_Address start = VM_Magic.objectAsAddress(buf).add(off);
    int rc = VM.sysCall3(bootRecord.sysWriteBytesIP, fd, start.toInt(), cnt);
    // UNPIN(buf);

    return rc;
  }

  /**
   * Change i/o position on file.
   * @param fd file descriptor
   * @param offset number of bytes by which to adjust position
   * @param whence how to interpret adjustment (one of SEEK_XXX, above)
   * @return new i/o position, as byte offset from start of file (-1: error)
   */ 
  public static int seek(int fd, int offset, int whence) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysSeekIP, fd, offset, whence);
  }

  /**
   * Close file.
   * @param fd file descriptor
   * @return 0: success
   *        -1: file not currently open
   *        -2: i/o error
   */ 
  public static int close(int fd) {
    if (VM.TraceFileSystem) VM.sysWrite("VM_FileSystem.close: fd=" + fd + "\n");
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall1(bootRecord.sysCloseIP, fd);
  }

  /**
   * List contents of a directory.
   * @param dirName directory name
   * @return names of files and subdirectories
   */ 
  public static String[] list(String dirName) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;

    // convert directory name from unicode to filesystem character set
    // (assume directory name is ascii, for now)
    //
    byte[] asciiName = new byte[dirName.length() + 1]; // +1 for null terminator
    dirName.getBytes(0, dirName.length(), asciiName, 0);

    // fill buffer with list of null terminated names, resizing as needed to fit
    // (list will be in filesystem character set, assume ascii for now)
    //
    byte[] asciiList;
    int    len;
    for (int max = 1024;;) {
      asciiList = new byte[max];

      // PIN(asciiName);
      // PIN(asciiList);
      len = VM.sysCall3(bootRecord.sysListIP, 
                        VM_Magic.objectAsAddress(asciiName).toInt(), 
                        VM_Magic.objectAsAddress(asciiList).toInt(), max);
      // UNPIN(asciiName);
      // UNPIN(asciiList);

      if (len < max)
        break;

      // results didn't fit, try again with more space
      //
      max *= 2;
    }

    if (len <= 0) { // i/o error or empty directory
      return new String[0]; // !!TODO: does JDK return null or empty list 
      // for this case?
    }

    // pass 1: count names
    //
    int cnt = 0;
    for (int i = 0; i < len; ++i)
      if (asciiList[i] == 0)
        ++cnt;

    // pass 2: extract names
    //
    String names[] = new String[cnt];
    for (int beg = 0, end = cnt = 0; beg < len; beg = end + 1) {
      for (end = beg; asciiList[end] != 0; ++end);
      names[cnt++] = new String(asciiList, 0, beg, end - beg);
    }

    return names;
  }

  /**
   * Delete file.
   * @param fileName file name
   * @return true -- delete; false -- not delete
   */ 
  public static boolean delete(String fileName) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    //
    byte[] asciiName = new byte[fileName.length() + 1]; //+1 for null terminator
    fileName.getBytes(0, fileName.length(), asciiName, 0);

    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int rc = VM.sysCall1(bootRecord.sysDeleteIP,
                         VM_Magic.objectAsAddress(asciiName).toInt());

    if (rc == 0) return true;
    else return false;
  } 

  /**
   * Rename a file
   * @param fromName from file name
   * @param toName to file name
   * @returntrue -- renamed; false -- not renamed
   */ 
  public static boolean rename(String fromName, String toName) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    //
    byte[] fromCharStar = new byte[ fromName.length() + 1];
    fromName.getBytes(0, fromName.length(), fromCharStar, 0);

    byte[] toCharStar = new byte[ toName.length() + 1];
    toName.getBytes(0, toName.length(), toCharStar, 0);

    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int rc = VM.sysCall2(bootRecord.sysRenameIP,
                         VM_Magic.objectAsAddress(fromCharStar).toInt(),
                         VM_Magic.objectAsAddress(toCharStar).toInt());

    if (rc == 0) return true;
    else return false;
  } 


  /**
   * Make a directory.
   * @param fileName file name
   * @return true -- created; false -- not created
   */ 
  public static boolean mkdir(String fileName) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    //
    byte[] asciiName = new byte[fileName.length() + 1]; //+1 for null terminator
    fileName.getBytes(0, fileName.length(), asciiName, 0);      
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int rc = VM.sysCall1(bootRecord.sysMkDirIP,
                         VM_Magic.objectAsAddress(asciiName).toInt());
    return (rc == 0);
  } 

  public static boolean sync(int fd) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall1(bootRecord.sysSyncFileIP, fd) == 0;
  }

  public static int bytesAvailable(int fd) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall1(bootRecord.sysBytesAvailableIP, fd);
  }	   
}
