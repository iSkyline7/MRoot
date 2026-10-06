#include <dirent.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define BLKROSET   0x125d
#define MODULES_DIR "/data/adb/modules"

static char g_ksud[512] = "/data/user_de/0/com.nzs.mroot/ksud";
static char g_prefs_path[512] = "/data/user_de/0/com.nzs.mroot/shared_prefs/dfroot.xml";

static void resolve_paths(void)
{
    char self[512];
    ssize_t len = readlink("/proc/self/exe", self, sizeof(self) - 1);
    if (len > 0) {
        self[len] = '\0';
        char *slash = strrchr(self, '/');
        if (slash) {
            *slash = '\0';
            snprintf(g_ksud, sizeof(g_ksud), "%s/ksud", self);
            snprintf(g_prefs_path, sizeof(g_prefs_path), "%s/shared_prefs/dfroot.xml", self);
        }
    }
}

static int pref_true(const char *buf, const char *key)
{
    char needle[64];
    snprintf(needle, sizeof(needle), "name=\"%s\"", key);
    char *p = strstr(buf, needle);
    if (!p) return 0;
    char *tag_end = strchr(p, '>');
    char *v = strstr(p, "value=\"true\"");
    return v && tag_end && v < tag_end;
}

static int read_prefs(char *su_manager, size_t su_manager_size, int *soft_reboot,
                      int *disable_modules)
{
    int fd = open(g_prefs_path, O_RDONLY);
    if (fd < 0) {
        fd = open("/data/user_de/0/com.nzs.mroot/shared_prefs/dfroot.xml", O_RDONLY);
    }
    if (fd < 0) {
        fd = open("/data/user_de/0/df.root/shared_prefs/dfroot.xml", O_RDONLY);
    }
    if (fd < 0) return -1;

    char buf[4096];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';

    char *p = strstr(buf, "name=\"su_manager\">");
    if (!p) return -1;
    p += strlen("name=\"su_manager\">");
    char *end = strchr(p, '<');
    if (!end) return -1;
    size_t len = end - p;
    if (len == 0 || len >= su_manager_size) return -1;
    memcpy(su_manager, p, len);
    su_manager[len] = '\0';

    *soft_reboot = pref_true(buf, "soft_reboot");
    *disable_modules = pref_true(buf, "disable_modules");

    return 0;
}

static void adopt_zygote_env(void)
{
    FILE *f = popen("pidof zygote64 zygote", "r");
    if (!f) return;
    int pid = 0;
    fscanf(f, "%d", &pid);
    pclose(f);
    if (!pid) return;

    char path[32];
    snprintf(path, sizeof(path), "/proc/%d/environ", pid);
    int fd = open(path, O_RDONLY);
    if (fd < 0) return;
    static char buf[16384];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    buf[n] = '\0';
    for (char *p = buf, *end = buf + n; p < end; p += strlen(p) + 1)
        putenv(p);
}

static int should_ro(const char *name)
{
    size_t len = strlen(name);
    if (!strcmp(name, "super"))  return 1;
    if (!strcmp(name, "misc"))   return 1;
    if (!strcmp(name, "steady")) return 1;
    if (len >= 2 && name[len - 2] == '_' &&
        (name[len - 1] == 'a' || name[len - 1] == 'b'))
        return 1;
    return 0;
}

static void set_partitions_ro(void)
{
    DIR *dir = opendir("/dev/block/by-name");
    if (!dir)
        return;

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (!should_ro(ent->d_name))
            continue;

        char path[128];
        snprintf(path, sizeof(path), "/dev/block/by-name/%s", ent->d_name);

        int fd = open(path, O_RDONLY);
        if (fd < 0)
            continue;

        struct stat st;
        if (fstat(fd, &st) == 0 && S_ISBLK(st.st_mode)) {
            int on = 1;
            ioctl(fd, BLKROSET, &on);
        }
        close(fd);
    }

    closedir(dir);
}

static int run(char *const argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        return -1;
    if (pid == 0) {
        execv(argv[0], argv);
        _exit(127);
    }
    int status;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

static void touch(const char *path)
{
    int fd = open(path, O_CREAT | O_WRONLY, 0666);
    if (fd >= 0)
        close(fd);
}

/* Mark every installed module disabled before ksud runs. A broken module
 * otherwise loads on the next boot and bootloops the device. */
static void disable_modules(void)
{
    DIR *dir = opendir(MODULES_DIR);
    if (!dir)
        return;

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (ent->d_name[0] == '.')
            continue;
        char path[256];
        snprintf(path, sizeof(path), MODULES_DIR "/%s/disable", ent->d_name);
        touch(path);
    }
    closedir(dir);
}

int main(void)
{
    resolve_paths();
    char su_manager[256];
    int soft_reboot, disable_mods;
    if (read_prefs(su_manager, sizeof(su_manager), &soft_reboot, &disable_mods) != 0) {
        touch("/dev/dfm6");
        return 1;
    }
    touch("/dev/dfm1");

    adopt_zygote_env();
    touch("/dev/dfm2");

    set_partitions_ro();
    touch("/dev/dfm3");

    if (disable_mods)
        disable_modules();

    const char *ksud_bin = g_ksud;
    if (access(ksud_bin, X_OK) != 0) {
        if (access("/data/user_de/0/com.nzs.mroot/ksud", X_OK) == 0)
            ksud_bin = "/data/user_de/0/com.nzs.mroot/ksud";
        else if (access("/data/user_de/0/df.root/ksud", X_OK) == 0)
            ksud_bin = "/data/user_de/0/df.root/ksud";
    }

    char **late_load;
    if (soft_reboot)
        late_load = (char *[]){ (char *)ksud_bin, "late-load", "--package-name", su_manager, "--soft-reboot", NULL };
    else
        late_load = (char *[]){ (char *)ksud_bin, "late-load", "--package-name", su_manager, NULL };
    if (run(late_load) == 0)
        touch("/dev/dfm4");
    else
        touch("/dev/dfm5");

    return 0;
}
