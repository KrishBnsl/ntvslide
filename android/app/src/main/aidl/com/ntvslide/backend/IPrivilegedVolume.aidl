package com.ntvslide.backend;

/** Implemented by the Shizuku user service, which runs with shell privileges. */
interface IPrivilegedVolume {
    /** Transaction id reserved by Shizuku: called when the service should exit. */
    void destroy() = 16777114;

    /** UIDs of apps that currently have an active audio player. */
    int[] activeUids() = 1;

    /** Scales every active player of [uid] to [volume] (0.0 – 1.0). True if at least one player was reached. */
    boolean setVolume(int uid, float volume) = 2;
}
