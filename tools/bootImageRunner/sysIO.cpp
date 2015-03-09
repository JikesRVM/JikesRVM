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
#include <fcntl.h> // fcntl
#include <string.h> // strerror
#include <unistd.h> // read, close, fsync, ...
#include <sys/stat.h> // stat data structure
#include <sys/ioctl.h> // ioctl

/**
 * Get file status.
 * Taken:   null terminated filename
 *          kind of info desired (see FileSystem.STAT_XXX)
 * Returned: status (-1=error)
 */
EXTERNAL int sysStat(char *name, int kind)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysStat %s %d\n", Me, name, kind);

    struct stat info;

    if (stat(name, &info))
        return -1; // does not exist, or other trouble

    switch (kind) {
    case FileSystem_STAT_EXISTS:
        return 1;                              // exists
    case FileSystem_STAT_IS_FILE:
        return S_ISREG(info.st_mode) != 0; // is file
    case FileSystem_STAT_IS_DIRECTORY:
        return S_ISDIR(info.st_mode) != 0; // is directory
    case FileSystem_STAT_IS_READABLE:
        return (info.st_mode & S_IREAD) != 0; // is readable by owner
    case FileSystem_STAT_IS_WRITABLE:
        return (info.st_mode & S_IWRITE) != 0; // is writable by owner
    case FileSystem_STAT_LAST_MODIFIED:
        return info.st_mtime;   // time of last modification
    case FileSystem_STAT_LENGTH:
        return info.st_size;    // length
    }
    return -1; // unrecognized request
}

/**
 * Check user's perms.
 * Taken:     null terminated filename
 *            kind of access perm to check for (see FileSystem.ACCESS_W_OK)
 * Returned:  0 on success (-1=error)
 */
EXTERNAL int sysAccess(char *name, int kind)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysAccess %s\n", Me, name);
    return access(name, kind);
}

/**
 * How many bytes can be read from file/socket without blocking?
 * Taken:     file/socket descriptor
 * Returned:  >=0: count
 *            -1: other error
 */
EXTERNAL int sysBytesAvailable(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: bytesAvailable %d\n", Me, fd);
    int count = 0;
    if (ioctl(fd, FIONREAD, &count) == -1)
    {
        return -1;
    }
    TRACE_PRINTF(SysTraceFile, "%s: available fd=%d count=%d\n", Me, fd, count);
    return count;
}

/**
 * Syncs a file.
 * Taken:     file/socket descriptor
 * Returned:  0: everything ok
 *            -1: error
 */
EXTERNAL int sysSyncFile(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: sync %d\n", Me, fd);
    if (fsync(fd) != 0) {
        // some kinds of files cannot be sync'ed, so don't print error message
        // however, do return error code in case some application cares
        return -1;
    }
    return 0;
}

/**
 * Reads one byte from file.
 * Taken:     file descriptor
 * Returned:  data read (-3: error, -2: operation would block, -1: eof, >= 0: valid)
 */
EXTERNAL int sysReadByte(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: readByte %d\n", Me, fd);
    unsigned char ch;
    int rc;

again:
    switch ( rc = read(fd, &ch, 1))
    {
    case  1:
        /*CONSOLE_PRINTF(SysTraceFile, "%s: read (byte) ch is %d\n", Me, (int) ch);*/
        return (int) ch;
    case  0:
        /*CONSOLE_PRINTF(SysTraceFile, "%s: read (byte) rc is 0\n", Me);*/
        return -1;
    default:
        /*CONSOLE_PRINTF(SysTraceFile, "%s: read (byte) rc is %d\n", Me, rc);*/
        if (errno == EAGAIN)
            return -2;  // Read would have blocked
        else if (errno == EINTR)
            goto again; // Read was interrupted; try again
        else
            return -3;  // Some other error
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
    TRACE_PRINTF(SysTraceFile, "%s: writeByte %d %c\n", Me, fd, ch);
again:
    int rc = write(fd, &ch, 1);
    if (rc == 1)
        return 0; // success
    else if (errno == EAGAIN)
        return -2; // operation would block
    else if (errno == EINTR)
        goto again; // interrupted by signal; try again
    else {
        CONSOLE_PRINTF(SysErrorFile, "%s: writeByte, fd=%d, write returned error %d (%s)\n", Me,
                fd, errno, strerror(errno));
        return -1; // some kind of error
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
    TRACE_PRINTF(SysTraceFile, "%s: read %d %p %d\n", Me, fd, buf, cnt);
again:
    int rc = read(fd, buf, cnt);
    if (rc >= 0)
        return rc;
    int err = errno;
    if (err == EAGAIN)
    {
        TRACE_PRINTF(SysTraceFile, "%s: read on %d would have blocked: needs retry\n", Me, fd);
        return -1;
    }
    else if (err == EINTR)
        goto again; // interrupted by signal; try again
    CONSOLE_PRINTF(SysTraceFile, "%s: read error %d (%s) on %d\n", Me,
            err, strerror(err), fd);
    return -2;
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
    TRACE_PRINTF(SysTraceFile, "%s: write %d %p %d\n", Me, fd, buf, cnt);
again:
    int rc = write(fd, buf, cnt);
    if (rc >= 0)
        return rc;
    int err = errno;
    if (err == EAGAIN)
    {
        TRACE_PRINTF(SysTraceFile, "%s: write on %d would have blocked: needs retry\n", Me, fd);
        return -1;
    }
    if (err == EINTR)
        goto again; // interrupted by signal; try again
    if (err == EPIPE)
    {
        TRACE_PRINTF(SysTraceFile, "%s: write on %d with nobody to read it\n", Me, fd);
        return -3;
    }
    CONSOLE_PRINTF(SysTraceFile, "%s: write error %d (%s) on %d\n", Me,
            err, strerror( err ), fd);
    return -2;
}

/**
 * Close file or socket.
 * Taken:     file/socket descriptor
 * Returned:  0: success
 *            -1: file/socket not currently open
 *            -2: i/o error
 */
static int sysClose(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: close %d\n", Me, fd);
    if ( -1 == fd ) return -1;
    int rc = close(fd);
    if (rc == 0) return 0; // success
    if (errno == EBADF) return -1; // not currently open
    return -2; // some other error
}

/**
 * Sets the close-on-exec flag for given file descriptor.
 * Taken:     the file descriptor
 * Returned:  0 if sucessful, nonzero otherwise
 */
EXTERNAL int sysSetFdCloseOnExec(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: setFdCloseOnExec %d\n", Me, fd);
    return fcntl(fd, F_SETFD, FD_CLOEXEC);
}
