package dev.goodwy.rphone;

import android.os.ParcelFileDescriptor;

interface IShellService {
    ParcelFileDescriptor startRecording(
        String audioSource,
        String audioCodec,
        int audioBitRate,
        String serverPath,
        boolean isDebuggingModeEnabled
    ) = 2;

    void stopRecording() = 3;

    void destroy() = 16777114;
}
