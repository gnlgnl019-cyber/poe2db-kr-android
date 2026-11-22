package android.util;

/**
 * JVM 단위 테스트용 최소 구현. 기기에서는 플랫폼 android.util.Base64가 쓰인다.
 */
public final class Base64 {

    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int URL_SAFE = 8;

    private Base64() {}

    public static byte[] decode(String str, int flags) {
        String s = str.replaceAll("\\s", "");
        if ((flags & URL_SAFE) != 0) {
            s = s.replace('-', '+').replace('_', '/');
        }
        int pad = s.length() % 4;
        if (pad != 0) s = s + "====".substring(pad);
        return java.util.Base64.getDecoder().decode(s);
    }

    public static byte[] decode(byte[] input, int flags) {
        return decode(new String(input, java.nio.charset.StandardCharsets.US_ASCII), flags);
    }

    public static String encodeToString(byte[] input, int flags) {
        String out = (flags & URL_SAFE) != 0
            ? java.util.Base64.getUrlEncoder().encodeToString(input)
            : java.util.Base64.getEncoder().encodeToString(input);
        if ((flags & NO_PADDING) != 0) out = out.replace("=", "");
        if ((flags & NO_WRAP) == 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < out.length(); i += 76) {
                sb.append(out, i, Math.min(out.length(), i + 76)).append('\n');
            }
            out = sb.toString();
        }
        return out;
    }
}
