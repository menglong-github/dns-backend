package com.dns.resolution.utils;

import com.dns.common.utils.StringUtils;
import com.ibm.icu.text.IDNA;
import org.springframework.stereotype.Component;

@Component
public class IDNUtils {

    private IDNA idna = IDNA.getUTS46Instance(IDNA.DEFAULT);

    public String toASCII(String content) {
        IDNA.Info info = new IDNA.Info();
        if (content.contains("@")) {
            if (content.contentEquals("@")) {
                return "@";
            } else {
                String[] contentSection = StringUtils.splitByWholeSeparator(content, "@");
                if (contentSection.length != 2) {
                    return null;
                } else {
                    return idna.nameToASCII(contentSection[0], new StringBuilder(), info).toString().toLowerCase() + "@" + idna.nameToASCII(contentSection[1], new StringBuilder(), info).toString().toLowerCase();
                }
            }
        } else {
            StringBuilder contentBuilder = new StringBuilder();
            idna.nameToASCII(content, contentBuilder, info);
            if (info.hasErrors()) {
                return null;
            } else {
                return contentBuilder.toString().toLowerCase();
            }
        }
    }

}