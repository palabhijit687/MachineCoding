package urlshortener.encoder;

import java.security.SecureRandom;

/**
 * Random 7 char code. Not guessable, but the caller must handle collisions
 * (the service retries a few times before giving up).
 */
public class RandomCodeGenerator implements ShortCodeGenerator {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public RandomCodeGenerator() {
        this(7);
    }

    public RandomCodeGenerator(int length) {
        this.length = length;
    }

    @Override
    public String nextCode() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
