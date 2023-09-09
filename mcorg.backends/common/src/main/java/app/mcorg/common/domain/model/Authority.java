package app.mcorg.common.domain.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Authority {
    OWNER(10_000),
    ADMIN(5_000),
    PARTICIPANT(1_000);

    private final int rank;

    public boolean equalsOrHigher(Authority other) {
        return rank >= other.rank;
    }

    public boolean equalsOrLower(Authority other) {
        return rank <= other.rank;
    }
}
