package uk.iwaservice.squadtpconquest.conquest;

/** Lifecycle of a conquest round. */
public enum RoundState {
    /** No round running; a fresh /conquest start is accepted. */
    WAITING,
    /** Points/tickets already reset and teams teleported; counting down to IN_PROGRESS. */
    STARTING,
    /** Capture/ticket logic is running. */
    IN_PROGRESS,
    /** Winner decided; result title is showing, /conquest start is rejected. */
    ENDED
}
