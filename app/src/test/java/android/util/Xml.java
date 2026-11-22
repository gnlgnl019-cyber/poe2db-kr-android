package android.util;

import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;



/**
 * JVM 단위 테스트용 최소 구현. 기기에서는 플랫폼 android.util.Xml이 kxml2 구현을 돌려준다.
 */
public final class Xml {

    private Xml() {}

    public static XmlPullParser newPullParser() {
        KXmlParser parser = new KXmlParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        } catch (XmlPullParserException ignored) {
        }
        return parser;
    }

    public static XmlSerializer newSerializer() {
        return new KXmlSerializer();
    }

    public static String escapeAttributeValue(String value) {
        return value;
    }

    public static String escapeText(String value) {
        return value;
    }

    static {
        // KXmlParser가 기본 생성자에서 던지지 않는지 확인용 (정적 초기화 없음)
        try {
            new KXmlParser().setInput(new java.io.StringReader("<a/>"));
        } catch (XmlPullParserException e) {
            throw new IllegalStateException("kxml2 unavailable", e);
        }
    }
}
