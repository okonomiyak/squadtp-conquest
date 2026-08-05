package uk.iwaservice.squadtpconquest.conquest;

/** Round-scoped kill/death/assist/revive counters for one player. */
public final class PlayerScore {
    public int kills;
    public int deaths;
    public int assists;
    public int revives;
    /** Score already spent on call-ins this round (round-scoped only; lifetime scores don't track this). */
    public int spent;
}
