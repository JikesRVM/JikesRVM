#!/bin/ksh
#
# (C) Copyright IBM Corp. 2001
#

ulimit -t $1
shift

exec "$@"
