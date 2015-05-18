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

#include "sys.h"

#include <errno.h>
#include <string.h> // strerror
#include <unistd.h> // read, close, fsync, ...

/**
 * Reads one byte from file.
 * Taken:     file descriptor
 * Returned:  data read (-3: error, -2: operation would block, -1: eof, >= 0: valid)
 */
EXTERNAL int sysReadByte(int fd)
{
  TRACE_PRINTF("%s: sysReadByte %d\n", Me, fd);
  unsigned char ch;
  int rc;

  while (1) {
    rc = read(fd, &ch, 1);
    switch (rc) {
      case  1:
        /*CONSOLE_PRINTF("%s: read (byte) ch is %d\n", Me, (int) ch);*/
        return (int) ch;
      case  0:
        /*CONSOLE_PRINTF("%s: read (byte) rc is 0\n", Me);*/
        return -1;
      default:
        /*CONSOLE_PRINTF("%s: read (byte) rc is %d\n", Me, rc);*/
        if (errno == EAGAIN) {
          return -2;  // Read would have blocked
        } else if (errno == EINTR) {
          // Read was interrupted; try again
        } else {
          return -3;  // Some other error
        }
    }
  }
}

/**
 * Writes one byte to file.
 * Taken:     file descriptor
 *            data to write
 * Returned:  -2 operation would block, -1: error, 0: success
 */
EXTERNAL int sysWriteByte(int fd, int data)
{
  char ch = data;
  TRACE_PRINTF("%s: sysWriteByte %d %c\n", Me, fd, ch);
  while (1) {
    int rc = write(fd, &ch, 1);
    if (rc == 1) {
      return 0; // success
    } else if (errno == EAGAIN) {
      return -2; // operation would block
    } else if (errno == EINTR) {
      // interrupted by signal; try again
    } else {
      ERROR_PRINTF("%s: writeByte, fd=%d, write returned error %d (%s)\n", Me,
                   fd, errno, strerror(errno));
      return -1; // some kind of error
    }
  }
}

/**
 * Reads multiple bytes from file or socket.
 * Taken:     file or socket descriptor
 *            buffer to be filled
 *            number of bytes requested
 * Returned:  number of bytes delivered (-2: error, -1: socket would have blocked)
 */
EXTERNAL int sysReadBytes(int fd, char *buf, int cnt)
{
  TRACE_PRINTF("%s: sysReadBytes %d %p %d\n", Me, fd, buf, cnt);
  while (1) {
    int rc = read(fd, buf, cnt);
    if (rc >= 0)
      return rc;
    int err = errno;
    if (err == EAGAIN) {
      TRACE_PRINTF("%s: read on %d would have blocked: needs retry\n", Me, fd);
      return -1;
    } else if (err != EINTR) {
      ERROR_PRINTF("%s: read error %d (%s) on %d\n", Me,
                   err, strerror(err), fd);
      return -2;
    } else {
      // interrupted by signal; try again
    }
  }
}

/**
 * Writes multiple bytes to file or socket.
 * Taken:     file or socket descriptor
 *            buffer to be written
 *            number of bytes to write
 * Returned:  number of bytes written (-2: error, -1: socket would have blocked,
 *            -3 EPIPE error)
 */
EXTERNAL int sysWriteBytes(int fd, char *buf, int cnt)
{
  TRACE_PRINTF("%s: sysWriteBytes %d %p %d\n", Me, fd, buf, cnt);
  while (1) {
    int rc = write(fd, buf, cnt);
    if (rc >= 0)
      return rc;
    int err = errno;
    if (err == EAGAIN) {
      TRACE_PRINTF("%s: write on %d would have blocked: needs retry\n", Me, fd);
      return -1;
    } else if (err == EINTR) {
      // interrupted by signal; try again
    } else if (err == EPIPE) {
      TRACE_PRINTF("%s: write on %d with nobody to read it\n", Me, fd);
      return -3;
    } else {
      ERROR_PRINTF("%s: write error %d (%s) on %d\n", Me,
                   err, strerror( err ), fd);
      return -2;
    }
  }
}
