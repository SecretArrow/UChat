package com.uchat.android.linux.install

/**
 * Pure resume decision for the installer (JVM-testable, no I/O).
 *
 * Why it exists: the first v1.5.0 release re-downloaded the whole ~30 MB rootfs on EVERY retry,
 * even when the failure happened at step 5+ (apt install) and the rootfs was already sitting
 * extracted on disk. On a metered mobile connection that is devastating — the user explicitly
 * complained about burning their data plan. Retries must NEVER repeat completed work.
 */
object InstallResume {

    /** What runInstall should do about the archive pipeline (steps 1-3). */
    data class Decision(
        /**
         * true = the rootfs is already extracted on disk; skip download+verify+extract entirely.
         */
        val skipArchive: Boolean,
        /** true = a fully downloaded archive exists; skip the download but still verify+extract. */
        val reuseArchive: Boolean,
        /** The step the run actually starts from (a fresh install always starts at step 1). */
        val fromStep: InstallStep,
    )

    /**
     * @param resumable the step a previous run failed at or was paused/killed in (null = no
     *   persisted state: fresh install, or a pre-1.7.0 process death mid-step).
     * @param archiveExists true when a downloaded rootfs archive is present on disk.
     * @param rootfsHasBash true when `ubuntuRoot/bin/bash` exists (i.e. extraction completed).
     */
    fun decide(
        resumable: InstallStep?,
        archiveExists: Boolean,
        rootfsHasBash: Boolean,
    ): Decision {
        if (resumable == null) {
            // No persisted step state. If the rootfs is already extracted, steps 1-3 MUST NOT
            // repeat: a process death during step 5+ (apt install — Android kills backgrounded
            // apps) used to fall through to a full ~30 MB re-download here, silently burning
            // the user's metered data. Jump to step 4 instead; initialize is idempotent.
            return if (rootfsHasBash) {
                Decision(
                    skipArchive = true,
                    reuseArchive = false,
                    fromStep = InstallStep.INITIALIZE_UBUNTU,
                )
            } else {
                Decision(
                    skipArchive = false,
                    reuseArchive = false,
                    fromStep = InstallStep.DOWNLOAD_ROOTFS,
                )
            }
        }
        val failed = resumable
        return when {
            // Failures at step 4+ mean the archive was fully extracted before the crash —
            // repeating steps 1-3 would only waste bandwidth and disk I/O.
            failed.id >= InstallStep.INITIALIZE_UBUNTU.id && rootfsHasBash ->
                Decision(skipArchive = true, reuseArchive = false, fromStep = failed)
            // Failures at verify/extract can reuse the already-downloaded archive when intact.
            failed.id >= InstallStep.VERIFY_CHECKSUM.id && archiveExists ->
                Decision(skipArchive = false, reuseArchive = true, fromStep = failed)
            // Anything else (fresh, unknown, missing archive, broken rootfs) = full pipeline.
            // The Downloader itself resumes partial downloads byte-wise, so even a step-1
            // restart continues from what already reached the disk.
            else ->
                Decision(
                    skipArchive = false,
                    reuseArchive = false,
                    fromStep = InstallStep.DOWNLOAD_ROOTFS
                )
        }
    }
}
