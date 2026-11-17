#include <linux/err.h>
#include <linux/fs.h>
#include <linux/kernel.h>
#include <linux/stat.h>
#include <linux/string.h>
#include <linux/types.h>

#include "manager/hidden_manager.h"
#include "manager/manager_identity.h"
#include "compat/kernel_compat.h"
#include "klog.h" // IWYU pragma: keep

static struct ksu_manager_identity_record hidden_identity;
static bool hidden_identity_valid;

static bool valid_hidden_package(const char *package)
{
    int i;
    bool dot = false;

    if (!package[0])
        return false;

    for (i = 0; i < 256; i++) {
        char c = package[i];

        if (c == '\0')
            break;
        if (c == '.') {
            if (i == 0 || package[i - 1] == '.' || !package[i + 1])
                return false;
            dot = true;
            continue;
        }
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_'))
            return false;
        if ((i == 0 || package[i - 1] == '.') && !(c >= 'a' && c <= 'z'))
            return false;
    }

    if (i == 256)
        return false;

#ifdef KSU_PACKAGE_NAME
    /* The official manager is identified by its compiled-in certificate. */
    if (!strcmp(package, KSU_PACKAGE_NAME))
        return false;
#endif
#ifdef KSU_MANAGER_PACKAGE
    if (!strcmp(package, KSU_MANAGER_PACKAGE))
        return false;
#endif

    return dot;
}

static bool valid_certificate_sha256(const char *sha256)
{
    int i;

    for (i = 0; i < 64; i++) {
        char c = sha256[i];

        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
            return false;
    }

    return sha256[64] == '\0';
}

const struct ksu_manager_identity_record *ksu_hidden_manager_identity(void)
{
    return hidden_identity_valid ? &hidden_identity : NULL;
}

bool ksu_hidden_manager_load_identity(void)
{
    struct ksu_manager_identity_record identity;
    struct file *file;
    struct inode *inode;
    loff_t offset = 0;
    bool valid = false;

    hidden_identity_valid = false;

    file = ksu_filp_open_nonotify(KSU_MANAGER_IDENTITY_PATH,
                                  O_RDONLY | O_NOFOLLOW | O_NOATIME | O_NONBLOCK);
    if (IS_ERR(file)) {
        pr_info("hidden manager identity not available: %ld\n", PTR_ERR(file));
        return false;
    }

    inode = file_inode(file);
    if (!S_ISREG(inode->i_mode) || inode->i_uid.val != 0 || inode->i_gid.val != 0 ||
        (inode->i_mode & 07777) != 0600 || inode->i_nlink != 1 ||
        i_size_read(inode) != sizeof(identity)) {
        pr_warn("hidden manager identity has unexpected metadata\n");
        goto close;
    }

    if (ksu_kernel_read_compat(file, &identity, sizeof(identity), &offset) != sizeof(identity)) {
        pr_warn("hidden manager identity read failed\n");
        goto close;
    }

    if (identity.magic != KSU_MANAGER_IDENTITY_MAGIC ||
        identity.version != KSU_MANAGER_IDENTITY_VERSION ||
        identity.certificate_size < 1 || identity.certificate_size > 1024) {
        pr_warn("hidden manager identity header is invalid\n");
        goto close;
    }

    /* The record may be full; refuse it instead of silently truncating. */
    if (!memchr(identity.package, '\0', sizeof(identity.package)) ||
        !memchr(identity.certificate_sha256, '\0', sizeof(identity.certificate_sha256))) {
        pr_warn("hidden manager identity strings are not terminated\n");
        goto close;
    }

    if (!valid_hidden_package(identity.package) ||
        !valid_certificate_sha256(identity.certificate_sha256)) {
        pr_warn("hidden manager identity content is invalid\n");
        goto close;
    }

    memcpy(&hidden_identity, &identity, sizeof(hidden_identity));
    hidden_identity_valid = true;
    valid = true;
    pr_info("hidden manager identity loaded: %s size=%u\n", hidden_identity.package,
            hidden_identity.certificate_size);

close:
    filp_close(file, NULL);
    return valid;
}
