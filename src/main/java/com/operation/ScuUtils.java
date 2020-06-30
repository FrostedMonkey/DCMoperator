package com.operation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author : chenchen
 * @ClassName ScuUtils
 * @date : 2020-06-04 09:58
 * @Description TODO
 **/
public class ScuUtils {
    public static Properties loadProperties(String url, Properties p) throws IOException {
        if (p == null) {
            p = new Properties();
        }

        InputStream in = StreamUtils.openFileOrURL(url);

        try {
            p.load(in);
        } finally {
            SafeClose.close(in);
        }

        return p;
    }
    public static boolean updateAttributes(Attributes data, Attributes attrs, String uidSuffix) {
        if (attrs.isEmpty() && uidSuffix == null) {
            return false;
        } else {
            if (uidSuffix != null) {
                data.setString(2097165, VR.UI, data.getString(2097165) + uidSuffix);
                data.setString(2097166, VR.UI, data.getString(2097166) + uidSuffix);
                data.setString(524312, VR.UI, data.getString(524312) + uidSuffix);
            }

            data.update(Attributes.UpdatePolicy.OVERWRITE, attrs, (Attributes)null);
            return true;
        }
    }
    public static String[] toUIDs(String s) {
        if (s.equals("*")) {
            return new String[]{"*"};
        } else {
            String[] uids = StringUtils.split(s, ',');

            for(int i = 0; i < uids.length; ++i) {
                uids[i] = toUID(uids[i]);
            }

            return uids;
        }
    }

    public static String toUID(String uid) {
        uid = uid.trim();
        return !uid.equals("*") && !Character.isDigit(uid.charAt(0)) ? UID.forName(uid) : uid;
    }
}
