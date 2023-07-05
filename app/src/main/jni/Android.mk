# This variable indicates the location of the source files in the development tree.
# Here, the macro function my-dir, provided by the build system, returns the path of the current directory (the directory containing the Android.mk file itself).
LOCAL_PATH := $(call my-dir)



# The CLEAR_VARS variable points to a special GNU Makefile that clears many LOCAL_XXX variables for you,
# such as LOCAL_MODULE, LOCAL_SRC_FILES, and LOCAL_STATIC_LIBRARIES.
# Note that it does not clear LOCAL_PATH.
include $(CLEAR_VARS)

# the LOCAL_MODULE variable stores the name of the module that you wish to build. Use this variable once per module in your application.
# The build system, when it generates the final shared-library file, automatically adds the proper prefix and suffix to the name that you assign to LOCAL_MODULE.
# If your module's name already starts with lib, the build system does not prepend an additional lib prefix
#
LOCAL_MODULE := hello-jni

# The LOCAL_SRC_FILES variable must contain a list of C and/or C++ source files to build into a module.
LOCAL_SRC_FILES := ffmpeg.cpp

# specify a list of paths, relative to the NDK root directory, to add to the include search path when compiling all sources (C, C++ and Assembly)
# Define this variable before setting any corresponding inclusion flags via LOCAL_CFLAGS or LOCAL_CPPFLAGS.
LOCAL_C_INCLUDES := include

# LOCAL_CFLAGS

# LOCAL_CPPFLAGS

# This variable is the list of shared libraries modules on which this module depends at runtime.
# This information is necessary at link time, and to embed the corresponding information in the generated file.
# LOCAL_SHARED_LIBRARIES

# LOCAL_LDLIBS

# LOCAL_LDFLAGS

# The BUILD_SHARED_LIBRARY variable points to a GNU Makefile script that collects all the information you defined in LOCAL_XXX variables since the most recent include.
# This script determines what to build, and how to do it.
include $(BUILD_SHARED_LIBRARY)