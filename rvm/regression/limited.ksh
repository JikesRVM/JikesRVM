#!/bin/ksh

ulimit -t $1
shift

exec "$@"
