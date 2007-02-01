##
## GCspy - Makefile for the C test harness
## @author Richard Jones, 2005-6
##

UNAME=$(shell uname -s)
ifeq ($(UNAME), Linux)
	EXTRA_CC_FLAGS = -D_LINUX_
	EXTRA_LD_FLAGS = -lpthread
	DYNAMIC_FLAG = -shared
	LD = gcc
	LIB_SUFFIX = so
	CC = gcc
endif

ifeq ($(UNAME), Darwin)
	EXTRA_CC_FLAGS = -dynamic -D_LINUX_
	DYNAMIC_FLAG = -dynamiclib
	LIB_SUFFIX = dylib
	LD = cc
	CC = cc
	GCSPY_LIB = -Llib -lgcspy
endif

CC_FLAGS = $(EXTRA_CC_FLAGS)
LD_FLAGS = -L$(LIB_DIR) $(EXTRA_LD_FLAGS) -lgcspy -mt

OBJ_DIR = obj
LIB_DIR = lib

SRC_DIR = ./src
INCLUDE_DIR = ./include

LIB_NAME = libgcspy.$(LIB_SUFFIX)

all: dirs $(LIB_DIR)/$(LIB_NAME) 

dirs: 
	mkdir -p $(OBJ_DIR)
	mkdir -p $(LIB_DIR)

$(LIB_DIR)/$(LIB_NAME): $(OBJ_DIR)/gcspy_main_server.o
	$(LD) $(DYNAMIC_FLAG) -o $@ $(OBJ_DIR)/gcspy_main_server.o


$(OBJ_DIR)/%.o: $(SRC_DIR)/%.c \
                $(INCLUDE_DIR)/gcspy_color_db.h \
		$(INCLUDE_DIR)/gcspy_command_stream.h \
		$(INCLUDE_DIR)/gcspy_gc_driver.h \
		$(INCLUDE_DIR)/gcspy_gc_stream.h \
		$(INCLUDE_DIR)/gcspy_interpreter.h \
		$(INCLUDE_DIR)/gcspy_main_server.h \
		$(INCLUDE_DIR)/gcspy_utils.h
	$(CC) -c $(CC_FLAGS) -I$(INCLUDE_DIR) -o $@ $<

## Cleaning up stuff

clean:
	rm -rf $(OBJ_DIR)
	rm -rf $(LIB_DIR)
