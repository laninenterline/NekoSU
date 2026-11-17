/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Executes the actual fixture callbacks against host-only logging/usercopy. */
#define MILKSU_FIXTURE_HOST_TEST
#include FIXTURE_SOURCE

int main(void)
{
    const char *literal = "'single' \"double\" ; $(NOT_EXECUTED)\nline2";
    assert(fixture_init(literal, "host-acceptance", 0) == TEST_INIT_RESULT);
    assert(strstr(last_log, "MILKSU_KPM_TEST init"));
    assert(strstr(last_log, TEST_MODULE_NAME));
    assert(strstr(last_log, literal));
#if TEST_HAS_CONTROL
    char output[256];
    assert(fixture_control(literal, output, sizeof(output)) == 0);
    assert(!strncmp(output, "echo: ", 6));
    assert(!strcmp(output + 6, literal));
    assert(fixture_control("large", output, 2) == -ENOBUFS);
    char oversized[257];
    memset(oversized, 'x', sizeof(oversized) - 1);
    oversized[sizeof(oversized) - 1] = 0;
    assert(fixture_control(oversized, output, sizeof(output)) == -E2BIG);
#endif
    assert(fixture_exit(0) == TEST_EXIT_RESULT);
    assert(strstr(last_log, "MILKSU_KPM_TEST exit"));
    assert(log_count == (TEST_HAS_CONTROL ? 3u : 2u));
    printf("fixture callbacks: PASS %s init=%d exit=%d ctl0=%d\n",
           TEST_MODULE_NAME, TEST_INIT_RESULT, TEST_EXIT_RESULT, TEST_HAS_CONTROL);
    return 0;
}
