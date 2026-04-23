package com.filemover.domain.model;

public enum FileTransferStatus {
    RECEIVED,
    VALIDATING,
    VALIDATED,
    MOVING,
    COMPLETED,
    FAILED,
    TRANSFERRED
}
