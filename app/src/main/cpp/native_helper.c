#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/wait.h>
#include <errno.h>
#include <android/log.h>

#define TAG "NativeHelper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * Fork a child process that inherits a specific file descriptor.
 */
JNIEXPORT jint JNICALL
Java_org_bdcloud_clash_core_NativeHelper_forkExecWithFd(
    JNIEnv *env, jclass clazz,
    jint vpn_fd, jint child_fd,
    jstring jpath, jobjectArray jargs, jstring jlogPath) {

    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) return -1;

    const char *logPath = jlogPath ? (*env)->GetStringUTFChars(env, jlogPath, NULL) : NULL;

    int argc = (*env)->GetArrayLength(env, jargs);
    char **argv = (char **)calloc(argc + 1, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring jarg = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        const char *arg = (*env)->GetStringUTFChars(env, jarg, NULL);
        argv[i] = strdup(arg);
        (*env)->ReleaseStringUTFChars(env, jarg, arg);
    }
    argv[argc] = NULL;

    LOGD("Forking: %s (vpn_fd=%d -> child_fd=%d)", path, vpn_fd, child_fd);

    pid_t pid = fork();

    if (pid == 0) {
        // ── CHILD PROCESS ──

        // Duplicate VPN fd to the target fd number
        if (vpn_fd != child_fd) {
            dup2(vpn_fd, child_fd);
            close(vpn_fd);
        }

        // Redirect stdout+stderr to log file
        if (logPath) {
            int logfd = open(logPath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (logfd >= 0) {
                dup2(logfd, STDOUT_FILENO);
                dup2(logfd, STDERR_FILENO);
                close(logfd);
            }
        }

        // Execute tun2socks
        execv(path, argv);

        // If exec fails, write error to log
        if (logPath) {
            int logfd = open(logPath, O_WRONLY | O_CREAT | O_APPEND, 0644);
            if (logfd >= 0) {
                dprintf(logfd, "execv failed: %s (errno=%d)\n", strerror(errno), errno);
                close(logfd);
            }
        }
        _exit(127);
    }

    // ── PARENT PROCESS ──
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    if (logPath) (*env)->ReleaseStringUTFChars(env, jlogPath, logPath);
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);

    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        return -1;
    }

    LOGD("Child started with PID %d", pid);
    return pid;
}

/**
 * Wait for a child process and return exit info.
 * Returns: positive = exit code, negative = signal number (e.g., -11 = SIGSEGV)
 */
JNIEXPORT jint JNICALL
Java_org_bdcloud_clash_core_NativeHelper_waitForProcess(
    JNIEnv *env, jclass clazz, jint pid) {

    int status = 0;
    pid_t result = waitpid(pid, &status, 0);

    if (result < 0) {
        LOGE("waitpid(%d) failed: %s", pid, strerror(errno));
        return -999;
    }

    if (WIFEXITED(status)) {
        int code = WEXITSTATUS(status);
        LOGD("Process %d exited normally with code %d", pid, code);
        return code;
    }

    if (WIFSIGNALED(status)) {
        int sig = WTERMSIG(status);
        LOGE("Process %d killed by signal %d (%s)", pid, sig, strsignal(sig));
        return -(1000 + sig);  // e.g., -1011 = SIGSEGV (signal 11)
    }

    return -998;
}

/**
 * Send SIGTERM to a child process.
 */
JNIEXPORT void JNICALL
Java_org_bdcloud_clash_core_NativeHelper_killProcess(
    JNIEnv *env, jclass clazz, jint pid) {

    if (pid > 0) {
        kill(pid, SIGTERM);
        usleep(500000);
        kill(pid, SIGKILL);
        waitpid(pid, NULL, WNOHANG);
        LOGD("Killed process %d", pid);
    }
}
