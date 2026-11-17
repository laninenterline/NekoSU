/* SPDX-License-Identifier: GPL-2.0-or-later */
/* TEST ONLY: exit returns -EIO; loader must retain its executable allocation. */
#define TEST_MODULE_NAME "milksu-test-exit-error"
#define TEST_INIT_RESULT 0
#define TEST_EXIT_RESULT (-EIO)
#define TEST_HAS_CONTROL 1
#include "test_module.h"
