ifndef TARGET_COMPILE
    $(error TARGET_COMPILE must select an AArch64 compiler)
endif

KP_DIR ?= ../..
OUTPUT_DIR ?= ../../../../out/test-modules
KP_ROOT := $(abspath $(KP_DIR))
OUT := $(abspath $(OUTPUT_DIR))
CC := $(TARGET_COMPILE)gcc
READELF := $(TARGET_COMPILE)readelf
NM := $(TARGET_COMPILE)nm

INCLUDE_DIRS := . include patch/include linux/include linux/arch/arm64/include linux/tools/arch/arm64/include
INCLUDES := $(foreach dir,$(INCLUDE_DIRS),-I$(KP_ROOT)/kernel/$(dir))
FIXTURE_FLAGS := -std=gnu11 -O2 -Wall -Wextra -Werror -ffreestanding -fno-builtin \
    -fno-common -fno-stack-protector -fno-pic -fno-pie -fno-plt \
    -fno-asynchronous-unwind-tables -fno-unwind-tables \
    -fno-tree-loop-distribute-patterns -mgeneral-regs-only -mno-outline-atomics -ffixed-x18
NAMES := echo no_ctl0 init_error exit_error
MODULES := $(foreach name,$(NAMES),$(OUT)/milksu-test-$(name).kpm)
HOST_CC ?= cc
HOST_TESTS := $(foreach name,$(NAMES),$(OUT)/host-$(name))

.PHONY: all inspect host-check
all: $(MODULES)

$(OUT):
	mkdir -p "$@"

$(OUT)/milksu-test-%.kpm: %.c test_module.h | $(OUT)
	$(CC) $(FIXTURE_FLAGS) $(INCLUDES) -c "$<" -o "$@"

$(OUT)/host-%: %.c test_module.h fixture_host.h fixture_host_test.c | $(OUT)
	$(HOST_CC) -std=gnu11 -Wall -Wextra -Werror -DFIXTURE_SOURCE=\"$*.c\" fixture_host_test.c -o "$@"

host-check: $(HOST_TESTS)
	@set -e; for binary in $(HOST_TESTS); do "$$binary"; done

inspect: all
	@for module in $(MODULES); do \
	    echo "$$module"; \
	    $(READELF) -h "$$module"; \
	    $(NM) -u "$$module"; \
	done
