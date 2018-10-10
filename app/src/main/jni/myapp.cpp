#include <jni.h>
#include <dlfcn.h>
#include <stddef.h>
#include <fcntl.h>
#include <sys/system_properties.h>
#include <stdio.h>
#include <string>
#include "common.h"
#include "mylib.h"

void fk() {
    test();

    LOGV("0000");
}


