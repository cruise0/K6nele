package ee.ioc.phon.android.speak;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Constants {

    public enum State {INIT, RECORDING, LISTENING, TRANSCRIBING, ERROR}

    ;

    // When does the chunk sending start and what is its interval
    public static final int TASK_INTERVAL_SEND = 300;
    public static final int TASK_DELAY_SEND = 100;

    // We send more frequently in the IME
    public static final int TASK_INTERVAL_IME_SEND = 200;
    public static final int TASK_DELAY_IME_SEND = 100;

    // Check the volume 10 times a second
    public static final int TASK_INTERVAL_VOL = 100;
    // Wait for 1/2 sec before starting to measure the volume
    public static final int TASK_DELAY_VOL = 500;

    public static final int TASK_INTERVAL_STOP = 1000;
    public static final int TASK_DELAY_STOP = 1000;

    public static final String AUDIO_FILENAME = "audio.wav";

    public static final String DEFAULT_AUDIO_FORMAT = "audio/wav";
    public static final Set<String> SUPPORTED_AUDIO_FORMATS =
            new HashSet<String>(Arrays.asList(DEFAULT_AUDIO_FORMAT));
}