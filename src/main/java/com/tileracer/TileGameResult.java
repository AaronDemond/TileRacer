package com.tileracer;

final class TileGameResult
{
    enum ScoreMode
    {
        NORMAL,
        SEQUENCE_HARDMODE,
        NON_SEQUENCE_HARDMODE;

        static ScoreMode fromSerializedValue(String value)
        {
            if (value == null || value.trim().isEmpty())
            {
                return NORMAL;
            }

            try
            {
                return ScoreMode.valueOf(value.trim());
            }
            catch (IllegalArgumentException ex)
            {
                return NORMAL;
            }
        }

        String displayName()
        {
            switch (this)
            {
                case SEQUENCE_HARDMODE:
                    return "sequence hardmode";
                case NON_SEQUENCE_HARDMODE:
                    return "non-sequence hardmode";
                case NORMAL:
                default:
                    return "normal";
            }
        }

        String nextButtonText()
        {
            switch (this)
            {
                case NORMAL:
                    return "Show sequence hardmode scores";
                case SEQUENCE_HARDMODE:
                    return "Show non-sequence hardmode scores";
                case NON_SEQUENCE_HARDMODE:
                default:
                    return "Show normal mode";
            }
        }

        ScoreMode next()
        {
            switch (this)
            {
                case NORMAL:
                    return SEQUENCE_HARDMODE;
                case SEQUENCE_HARDMODE:
                    return NON_SEQUENCE_HARDMODE;
                case NON_SEQUENCE_HARDMODE:
                default:
                    return NORMAL;
            }
        }
    }

    final int totalTicks;
    final ScoreMode scoreMode;

    TileGameResult(int totalTicks)
    {
        this(totalTicks, ScoreMode.NORMAL);
    }

    TileGameResult(int totalTicks, ScoreMode scoreMode)
    {
        this.totalTicks = totalTicks;
        this.scoreMode = scoreMode == null ? ScoreMode.NORMAL : scoreMode;
    }

    String formattedSeconds()
    {
        return String.format("%.1fs", totalTicks * 0.6);
    }

    boolean isHardModeScore()
    {
        return scoreMode != ScoreMode.NORMAL;
    }

    ScoreMode getScoreMode()
    {
        return scoreMode;
    }
}