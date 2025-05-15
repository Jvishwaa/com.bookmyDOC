package com.bookmydoc.generator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class IdGenerator {
    public static String generateUserId(String prefix) {
        String datePart = new SimpleDateFormat("ddHHmm", Locale.getDefault()).format(new Date());
        String chars = "1234567890";

        StringBuilder randomPart = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i<=3; i++) {
            randomPart.append(chars.charAt(random.nextInt(chars.length())));
        }

        String id = "BMD" + prefix + "" + datePart + "" + randomPart;
        return id;
    }
}
