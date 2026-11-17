/* SPDX-License-Identifier: GPL-2.0-or-later */
/* TEST ONLY: init returns -EINVAL; successful exit must release the instance. */
#define TEST_MODULE_NAME "milksu-test-init-error"
#define TEST_INIT_RESULT (-EINVAL)
#define TEST_EXIT_RESULT 0
#define TEST_HAS_CONTROL 1
#include "test_module.h"
