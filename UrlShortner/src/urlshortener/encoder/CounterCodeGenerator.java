package urlshortener.encoder;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counter + base62. Guaranteed unique, no collision check needed, no retry loop.
 *
 * Started the counter at a large offset so the very first code is 6 chars instead
 * of "1" - short codes look nicer and are harder to enumerate by hand.
 *
 * Downside: codes are sequential and guessable. If that matters I would either
 * XOR/permute the counter before encoding, or switch to RandomCodeGenerator.
 */
public class CounterCodeGenerator implements ShortCodeGenerator {

    private final AtomicLong counter;

    public CounterCodeGenerator() {
        this(56_800_235_584L); // 62^6, so every code is 7 chars
    }

    public CounterCodeGenerator(long start) {
        this.counter = new AtomicLong(start);
    }

    @Override
    public String nextCode() {
        return Base62Encoder.encode(counter.getAndIncrement());
    }
}
