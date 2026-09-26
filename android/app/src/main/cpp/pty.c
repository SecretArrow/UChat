/*
 * UChat — native PTY bridge.
 *
 * Provides a real pseudo-terminal so interactive TUI programs (bash, vim,
 * tmux, OpenCode, Claude Code, ...) behave correctly inside the Ubuntu
 * proot userspace. Every session is a forked child attached to a pty
 * slave; the parent keeps the master fd for I/O and TIOCSWINSZ resize.
 *
 * Apache-2.0 — (c) UChat contributors
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <android/log.h>

#define TAG "uchat-pty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef struct {
    pid_t pid;
    int master;
} PtySession;

static char last_error[512] = {0};

static void set_last_error(const char *msg) {
    snprintf(last_error, sizeof(last_error), "%s (errno=%d: %s)", msg, errno, strerror(errno));
}

static char **to_cstring_array(JNIEnv *env, jobjectArray arr) {
    jsize len = (*env)->GetArrayLength(env, arr);
    char **out = (char **) calloc((size_t) len + 1, sizeof(char *));
    if (out == NULL) return NULL;
    for (jsize i = 0; i < len; i++) {
        jstring js = (jstring) (*env)->GetObjectArrayElement(env, arr, i);
        const char *utf = (*env)->GetStringUTFChars(env, js, NULL);
        out[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, js, utf);
        (*env)->DeleteLocalRef(env, js);
    }
    return out;
}

static void free_cstring_array(char **arr) {
    if (arr == NULL) return;
    for (int i = 0; arr[i] != NULL; i++) free(arr[i]);
    free(arr);
}

/*
 * Opens a pty and forks a child running cmd[0] with argv/env.
 * Returns an opaque handle (non-zero) or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_uchat_android_linux_Pty_nativeOpenTerminal(
        JNIEnv *env, jobject thiz,
        jobjectArray cmd, jstring jcwd, jobjectArray jenv,
        jint rows, jint cols) {
    (void) thiz;

    const char *cwd = (*env)->GetStringUTFChars(env, jcwd, NULL);

    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) {
        set_last_error("posix_openpt failed");
        (*env)->ReleaseStringUTFChars(env, jcwd, cwd);
        return 0;
    }
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        set_last_error("grantpt/unlockpt failed");
        close(master);
        (*env)->ReleaseStringUTFChars(env, jcwd, cwd);
        return 0;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = rows > 0 ? rows : 24;
    ws.ws_col = cols > 0 ? cols : 80;
    ioctl(master, TIOCSWINSZ, &ws);

    char **argv = to_cstring_array(env, cmd);
    char **envp = to_cstring_array(env, jenv);
    if (argv == NULL || envp == NULL) {
        set_last_error("out of memory");
        free_cstring_array(argv);
        free_cstring_array(envp);
        close(master);
        (*env)->ReleaseStringUTFChars(env, jcwd, cwd);
        return 0;
    }

    pid_t pid = fork();
    if (pid < 0) {
        set_last_error("fork failed");
        free_cstring_array(argv);
        free_cstring_array(envp);
        close(master);
        (*env)->ReleaseStringUTFChars(env, jcwd, cwd);
        return 0;
    }

    if (pid == 0) {
        /* Child */
        const char *slave_path = ptsname(master);
        if (slave_path == NULL) _exit(126);
        int slave = open(slave_path, O_RDWR);
        if (slave < 0) _exit(126);

        setsid();
        ioctl(slave, TIOCSCTTY, 0);

        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        close(master);

        if (chdir(cwd) != 0) chdir("/");

        signal(SIGPIPE, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGHUP, SIG_DFL);

        execve(argv[0], argv, envp);
        _exit(127);
    }

    /* Parent */
    free_cstring_array(argv);
    free_cstring_array(envp);
    (*env)->ReleaseStringUTFChars(env, jcwd, cwd);

    PtySession *session = (PtySession *) malloc(sizeof(PtySession));
    if (session == NULL) {
        kill(pid, SIGKILL);
        waitpid(pid, NULL, 0);
        close(master);
        return 0;
    }
    session->pid = pid;
    session->master = master;
    last_error[0] = '\0';
    return (jlong) (intptr_t) session;
}

/* Blocking read; returns number of bytes read, -1 on EOF/child exit. */
JNIEXPORT jint JNICALL
Java_com_uchat_android_linux_Pty_nativeRead(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray buffer) {
    (void) thiz;
    PtySession *session = (PtySession *) (intptr_t) handle;
    if (session == NULL) return -1;

    jsize len = (*env)->GetArrayLength(env, buffer);
    if (len <= 0) return 0;
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);

    ssize_t n;
    do {
        n = read(session->master, bytes, (size_t) len);
    } while (n < 0 && errno == EINTR);

    (*env)->ReleaseByteArrayElements(env, buffer, bytes, 0);

    if (n < 0 && errno == EIO) return -1; /* child exited */
    if (n == 0) return -1;
    return (jint) n;
}

JNIEXPORT void JNICALL
Java_com_uchat_android_linux_Pty_nativeWrite(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray buffer, jint len) {
    (void) thiz;
    PtySession *session = (PtySession *) (intptr_t) handle;
    if (session == NULL || len <= 0) return;

    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    ssize_t written = 0;
    while (written < len) {
        ssize_t n = write(session->master, bytes + written, (size_t) (len - written));
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        written += n;
    }
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_uchat_android_linux_Pty_nativeResize(
        JNIEnv *env, jobject thiz, jlong handle, jint rows, jint cols) {
    (void) env; (void) thiz;
    PtySession *session = (PtySession *) (intptr_t) handle;
    if (session == NULL) return;

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = rows > 0 ? rows : 24;
    ws.ws_col = cols > 0 ? cols : 80;
    ioctl(session->master, TIOCSWINSZ, &ws);
    /* Forward SIGWINCH so the foreground process re-reads the size */
    kill(-session->pid, SIGWINCH);
}

/* Sends a signal to the whole process group of the session. */
JNIEXPORT void JNICALL
Java_com_uchat_android_linux_Pty_nativeSignal(
        JNIEnv *env, jobject thiz, jlong handle, jint sig) {
    (void) env; (void) thiz;
    PtySession *session = (PtySession *) (intptr_t) handle;
    if (session == NULL) return;
    if (kill(-session->pid, sig) != 0) kill(session->pid, sig);
}

/* Returns the child pid of the session. */
JNIEXPORT jlong JNICALL
Java_com_uchat_android_linux_Pty_nativeGetPid(
        JNIEnv *env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    PtySession *session = (PtySession *) (intptr_t) handle;
    if (session == NULL) return 0;
    return (jlong) session->pid;
}

/* Blocking waitpid; returns the raw wait status. */
JNIEXPORT jint JNICALL
Java_com_uchat_android_linux_Pty_nativeWaitFor(
        JNIEnv *env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    PtySession *session = (PtySession *) (intptr_t) handle;
    if (session == NULL) return -1;

    int status = 0;
    if (waitpid(session->pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

/* Closes the master fd and frees the handle (does not kill the child). */
JNIEXPORT void JNICALL
Java_com_uchat_android_linux_Pty_nativeClose(
        JNIEnv *env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    PtySession *session = (PtySession *) (intptr_t) handle;
    if (session == NULL) return;
    close(session->master);
    free(session);
}

JNIEXPORT jstring JNICALL
Java_com_uchat_android_linux_Pty_nativeLastError(JNIEnv *env, jobject thiz) {
    (void) thiz;
    return (*env)->NewStringUTF(env, last_error);
}
