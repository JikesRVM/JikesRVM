#! /bin/bash
# Expand macros for the Debian implementation.
[ $# = 2 ] || { echo >&2 "Usage: $0 SRC DEST" ; exit 2 ; }
src="$1"
dest="$2"
sed	-e 's,@USER_MANUAL@,@DOC_DIR,g'				\
	-e 's,@DOC_DIR@,/usr/share/doc/jikesrvm,g'		\
	-e 's,@MAN_DIR@,/usr/share/man,g'			\
	-e 's,@BIN_DIR@,/usr/bin,g'				\
	-e 's,@RVM_ROOT@,/usr/share/jikesrvm,'			\
	-e 's,@RVM_BUILD@,/usr/lib/jikesrvm,'			$src > $dest
	
