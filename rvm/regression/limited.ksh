#!/bin/ksh
#
# (C) Copyright IBM Corp. 2001
#
# $Id$

ulimit -t $1
shift

exec "$@"
