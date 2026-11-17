#ifndef __KSU_H_APK_V2_SIGN
#define __KSU_H_APK_V2_SIGN

#include <linux/types.h>
#include "ksu.h"

bool is_manager_apk(char *path, u8 *signature_index);
int get_pkg_from_apk_path(char *pkg, const char *path);

/* Verify an APK's v2 signing certificate against an explicit size + SHA-256 pair. */
bool ksu_apk_matches_certificate(char *path, unsigned size, const char *sha256);

bool is_dynamic_manager_apk(char *path, int *signature_index);

#endif