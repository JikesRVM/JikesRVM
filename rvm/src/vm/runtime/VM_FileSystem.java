/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Interface to filesystem of underlying operating system.
 * !!TODO: think about how i/o operations should interface with thread 
 * scheduler.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_FileSystem
   {
   // options for open()
   //
   public static final int OPEN_READ   = 0; // open for read/only  access
   public static final int OPEN_WRITE  = 1; // open for read/write access, create if doesn't already exist, truncate if already exists
   public static final int OPEN_MODIFY = 2; // open for read/write access, create if doesn't already exist
   public static final int OPEN_APPEND = 3; // open for read/write access, create if doesn't already exist, append writes
   
   // options for seek()
   //
   public static final int SEEK_SET = 0;    // set i/o position to start of file plus "offset"
   public static final int SEEK_CUR = 1;    // set i/o position to current position plus "offset"
   public static final int SEEK_END = 2;    // set i/o position to end of file plus "offset"
   
   // options for stat()
   //
   public static final int STAT_EXISTS        = 0;
   public static final int STAT_IS_FILE       = 1;
   public static final int STAT_IS_DIRECTORY  = 2;
   public static final int STAT_IS_READABLE   = 3;
   public static final int STAT_IS_WRITABLE   = 4;
   public static final int STAT_LAST_MODIFIED = 5;
   public static final int STAT_LENGTH        = 6;
   
   // Get file status.
   // Taken:    file name
   //           kind of info desired (one of STAT_XXX, above)
   // Returned: desired info (-1 -> error)
   //
   public static int 
   stat(String fileName, int kind)
      {
      // convert file name from unicode to filesystem character set
      // (assume file name is ascii, for now)
      //
      byte[] asciiName = new byte[fileName.length() + 1]; // +1 for null terminator
      fileName.getBytes(0, fileName.length(), asciiName, 0);
      
   // PIN(asciiName);
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      int rc = VM.sysCall2(bootRecord.sysStatIP, VM_Magic.objectAsAddress(asciiName), kind);
   // UNPIN(asciiName);

      if (VM.TraceFileSystem) VM.sysWrite("VM_FileSystem.stat: name=" + fileName + " kind=" + kind + " rc=" + rc + "\n");
      return rc;
      }
   
   // Open a file.
   // Taken:    file name
   //           access/creation mode (one of OPEN_XXX, above)
   // Returned: file descriptor (-1: not found or couldn't be created)
   //
   public static int 
   open(String fileName, int how)
      {
      // convert file name from unicode to filesystem character set
      // (assume file name is ascii, for now)
      //
      byte[] asciiName = new byte[fileName.length() + 1]; // +1 for null terminator
      fileName.getBytes(0, fileName.length(), asciiName, 0);
      
   // PIN(asciiName);
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      int fd = VM.sysCall2(bootRecord.sysOpenIP, VM_Magic.objectAsAddress(asciiName), how);
   // UNPIN(asciiName);

      if (VM.TraceFileSystem) VM.sysWrite("VM_FileSystem.open: name=" + fileName + " mode=" + how + " fd=" + fd + "\n");
      return fd;
      }

   // Read single byte from file.
   // Taken:    file descriptor
   // Returned: byte that was read (< -1: i/o error, -1: eof, >= 0: data)
   //
   public static int 
   readByte(int fd)
      {
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      return VM.sysCall1(bootRecord.sysReadByteIP, fd);
      }

   // Write single byte to file.
   // Taken:    file descriptor
   //           byte to be written
   // Returned: -1: i/o error
   //
   public static int 
   writeByte(int fd, int b)
      {
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      return VM.sysCall2(bootRecord.sysWriteByteIP, fd, b);
      }

   // Read multiple bytes from file or socket.
   // Taken:    file or socket descriptor
   //           buffer to be filled
   //           position in buffer
   //           number of bytes to read
   // Returned: number of bytes read (-2: error, -1: socket would have blocked)
   //
   public static int 
   readBytes(int fd, byte buf[], int off, int cnt)
      {
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
      int rc = VM.sysCall3(bootRecord.sysReadBytesIP, fd, VM_Magic.objectAsAddress(buf) + off, cnt);
   // UNPIN(buf);

      return rc;
      }

   // Write multiple bytes to file or socket.
   // Taken:    file or socket descriptor
   //           buffer to be written
   //           position in buffer
   //           number of bytes to write
   // Returned: number of bytes written (-2: error, -1: socket would have blocked)
   //
   public static int 
   writeBytes(int fd, byte buf[], int off, int cnt)
      {
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
      int rc = VM.sysCall3(bootRecord.sysWriteBytesIP, fd, VM_Magic.objectAsAddress(buf)  + off, cnt);
   // UNPIN(buf);

      return rc;
      }

   // Change i/o position on file.
   // Taken:    file descriptor
   //           number of bytes by which to adjust position
   //           how to interpret adjustment (one of SEEK_XXX, above)
   // Returned: new i/o position, as byte offset from start of file (-1: error)
   //
   public static int
   seek(int fd, int offset, int whence)
      {
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      return VM.sysCall3(bootRecord.sysSeekIP, fd, offset, whence);
      }

   // Close file.
   // Taken:    file descriptor
   // Returned:  0: success
   //           -1: file not currently open
   //           -2: i/o error
   //
   public static int
   close(int fd)
      {
      if (VM.TraceFileSystem) VM.sysWrite("VM_FileSystem.close: fd=" + fd + "\n");
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      return VM.sysCall1(bootRecord.sysCloseIP, fd);
      }
   
   // List contents of a directory.
   // Taken:    directory name
   // Returned: names of files and subdirectories
   //
   public static String[]
   list(String dirName)
      {
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
      for (int max = 1024;;)
         {
         asciiList = new byte[max];

      // PIN(asciiName);
      // PIN(asciiList);
         len = VM.sysCall3(bootRecord.sysListIP, VM_Magic.objectAsAddress(asciiName), VM_Magic.objectAsAddress(asciiList), max);
      // UNPIN(asciiName);
      // UNPIN(asciiList);
         
         if (len < max)
            break;
            
         // results didn't fit, try again with more space
         //
         max *= 2;
         }
            
      if (len <= 0)
         { // i/o error or empty directory
         return new String[0]; // !!TODO: does JDK return null or empty list for this case?
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
      for (int beg = 0, end = cnt = 0; beg < len; beg = end + 1)
         {
         for (end = beg; asciiList[end] != 0; ++end);
         names[cnt++] = new String(asciiList, 0, beg, end - beg);
         }
         
      return names;
      }

   // Get file status.
   // Taken:    file name
   // Returned: true -- delete; false -- not delete
   //
   public static boolean 
   delete(String fileName)
      {
      // convert file name from unicode to filesystem character set
      // (assume file name is ascii, for now)
      //
      byte[] asciiName = new byte[fileName.length() + 1]; // +1 for null terminator
      fileName.getBytes(0, fileName.length(), asciiName, 0);
      
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      int rc = VM.sysCall1(bootRecord.sysDeleteIP,
			   VM_Magic.objectAsAddress(asciiName));

      if (rc == 0) return true;
      else return false;
      }  // end delete

   // Rename a file
   // Taken:    from and to file names
   // Returned: true -- renamed; false -- not renamed
   //
   public static boolean 
   rename(String fromName, String toName)
       {
	   // convert file name from unicode to filesystem character set
	   // (assume file name is ascii, for now)
	   //
	   byte[] fromCharStar = new byte[ fromName.length() + 1];
	   fromName.getBytes(0, fromName.length(), fromCharStar, 0);
	   
	   byte[] toCharStar = new byte[ toName.length() + 1];
	   toName.getBytes(0, toName.length(), toCharStar, 0);
	   
	   VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	   int rc = VM.sysCall2(bootRecord.sysRenameIP,
				VM_Magic.objectAsAddress(fromCharStar),
				VM_Magic.objectAsAddress(toCharStar));
	   
	   if (rc == 0) return true;
	   else return false;
      }  // end rename


   // Make a directory.
   // Taken:    file name
   // Returned: true -- created; false -- not created
   //
   public static boolean 
   mkdir(String fileName)
      {
      // convert file name from unicode to filesystem character set
      // (assume file name is ascii, for now)
      //
      byte[] asciiName = new byte[fileName.length() + 1]; // +1 for null terminator
      fileName.getBytes(0, fileName.length(), asciiName, 0);      
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      int rc = VM.sysCall1(bootRecord.sysMkDirIP,
			   VM_Magic.objectAsAddress(asciiName));

      if (rc == 0) return true;
      else return false;
      }  // end mkdir

       public static boolean sync(int fd) {
	   VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	   return VM.sysCall1(bootRecord.sysSyncFileIP, fd) == 0;
       }

       public static int bytesAvailable(int fd) {
	   VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	   return VM.sysCall1(bootRecord.sysBytesAvailableIP, fd);
       }	   
}

